"""Reusable Python MCP client with OAuth 2.1 + PKCE (mirrors the TS zepto/src/oauth.ts).

Connects to any MCP server behind OAuth (Zepto / Swiggy). On first use it opens the
merchant's consent page in a browser (mobile + OTP) and caches the token per merchant.
"""

from __future__ import annotations

import asyncio
import json
import os
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from mcp import ClientSession
from mcp.client.auth import OAuthClientProvider, TokenStorage
from mcp.client.streamable_http import streamablehttp_client
from mcp.shared.auth import OAuthClientInformationFull, OAuthClientMetadata, OAuthToken
from mcp.types import CallToolResult

REDIRECT_PORT = int(os.environ.get("MCP_REDIRECT_PORT", "8970"))
REDIRECT_URI = f"http://localhost:{REDIRECT_PORT}/callback"
TOKENS_DIR = Path(__file__).resolve().parent.parent.parent / ".mcp-tokens"


class FileTokenStorage(TokenStorage):
    """Per-merchant token + client-registration cache (JSON file)."""

    def __init__(self, name: str) -> None:
        TOKENS_DIR.mkdir(exist_ok=True)
        self.path = TOKENS_DIR / f"{name}.json"
        self._d: dict = json.loads(self.path.read_text()) if self.path.exists() else {}

    def _save(self) -> None:
        self.path.write_text(json.dumps(self._d))
        os.chmod(self.path, 0o600)

    async def get_tokens(self) -> OAuthToken | None:
        t = self._d.get("tokens")
        return OAuthToken(**t) if t else None

    async def set_tokens(self, tokens: OAuthToken) -> None:
        self._d["tokens"] = tokens.model_dump(mode="json")
        self._save()

    async def get_client_info(self) -> OAuthClientInformationFull | None:
        c = self._d.get("client")
        return OAuthClientInformationFull(**c) if c else None

    async def set_client_info(self, info: OAuthClientInformationFull) -> None:
        self._d["client"] = info.model_dump(mode="json")
        self._save()

    def has_token(self) -> bool:
        return bool(self._d.get("tokens"))


async def _redirect_handler(url: str) -> None:
    print(f"\n→ [MCP OAuth] opening consent in browser — enter mobile + OTP:\n  {url}\n")
    webbrowser.open(url)


async def _callback_handler() -> tuple[str, str | None]:
    """One-shot localhost server that catches the OAuth redirect (code, state)."""
    loop = asyncio.get_event_loop()
    fut: asyncio.Future = loop.create_future()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802
            q = parse_qs(urlparse(self.path).query)
            code = q.get("code", [None])[0]
            state = q.get("state", [None])[0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.end_headers()
            self.wfile.write(b"<h2 style='font-family:system-ui'>Connected. Close this tab.</h2>")
            if not fut.done():
                loop.call_soon_threadsafe(fut.set_result, (code, state))

        def log_message(self, *_args) -> None:  # silence
            pass

    srv = HTTPServer(("localhost", REDIRECT_PORT), Handler)
    threading.Thread(target=srv.handle_request, daemon=True).start()
    try:
        code, state = await fut
    finally:
        srv.server_close()
    if not code:
        raise RuntimeError("OAuth callback returned no code")
    return code, state


def _client_metadata(scope: str) -> OAuthClientMetadata:
    return OAuthClientMetadata(
        redirect_uris=[REDIRECT_URI],  # type: ignore[list-item]
        token_endpoint_auth_method="none",
        grant_types=["authorization_code", "refresh_token"],
        response_types=["code"],
        scope=scope,
        client_name="shopmandate",
    )


def result_json(res: CallToolResult) -> object:
    """Prefer structuredContent, else parse the joined text blocks."""
    if getattr(res, "structuredContent", None):
        return res.structuredContent
    text = "\n".join(
        c.text for c in (res.content or []) if getattr(c, "type", None) == "text" and getattr(c, "text", None)
    )
    try:
        return json.loads(text)
    except (ValueError, TypeError):
        return text


class MCPMerchant:
    """A merchant backed by a real MCP server (OAuth-cached, per-call session)."""

    def __init__(self, name: str, server_url: str, scope: str) -> None:
        self.name = name
        self.server_url = server_url
        self.scope = scope
        self.storage = FileTokenStorage(name)

    def connected(self) -> bool:
        return self.storage.has_token()

    async def call(self, tool: str, args: dict | None = None) -> object:
        provider = OAuthClientProvider(
            server_url=self.server_url,
            client_metadata=_client_metadata(self.scope),
            storage=self.storage,
            redirect_handler=_redirect_handler,
            callback_handler=_callback_handler,
        )
        async with streamablehttp_client(self.server_url, auth=provider) as (read, write, _):
            async with ClientSession(read, write) as session:
                await session.initialize()
                res = await session.call_tool(tool, args or {})
                return result_json(res)

    async def list_tools(self) -> list[str]:
        provider = OAuthClientProvider(
            server_url=self.server_url,
            client_metadata=_client_metadata(self.scope),
            storage=self.storage,
            redirect_handler=_redirect_handler,
            callback_handler=_callback_handler,
        )
        async with streamablehttp_client(self.server_url, auth=provider) as (read, write, _):
            async with ClientSession(read, write) as session:
                await session.initialize()
                tools = await session.list_tools()
                return [t.name for t in tools.tools]
