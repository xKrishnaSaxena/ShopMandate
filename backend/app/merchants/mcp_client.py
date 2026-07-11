"""Reusable Python MCP client with OAuth 2.1 + PKCE (mirrors the TS zepto/src/oauth.ts).

Connects to any MCP server behind OAuth (Zepto / Swiggy). On first use it opens the
merchant's consent page in a browser (mobile + OTP) and caches the token per merchant.
"""

from __future__ import annotations

import asyncio
import json
import os
import random
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import httpx

from mcp import ClientSession
from mcp.client.auth import OAuthClientProvider, TokenStorage
from mcp.client.streamable_http import streamablehttp_client
from mcp.shared.auth import OAuthClientInformationFull, OAuthClientMetadata, OAuthToken
from mcp.types import CallToolResult

REDIRECT_PORT = int(os.environ.get("MCP_REDIRECT_PORT", "8970"))
REDIRECT_URI = f"http://localhost:{REDIRECT_PORT}/callback"


def _has_429(exc: BaseException) -> bool:
    """True if `exc` (or anything nested in its ExceptionGroup / cause chain) is an HTTP 429."""
    seen: set[int] = set()
    stack: list[BaseException] = [exc]
    while stack:
        e = stack.pop()
        if id(e) in seen:
            continue
        seen.add(id(e))
        if isinstance(e, httpx.HTTPStatusError):
            resp = getattr(e, "response", None)
            if resp is not None and resp.status_code == 429:
                return True
        subs = getattr(e, "exceptions", None)  # ExceptionGroup / TaskGroup
        if subs:
            stack.extend(subs)
        for attr in ("__cause__", "__context__"):
            nested = getattr(e, attr, None)
            if nested is not None:
                stack.append(nested)
    return False
TOKENS_DIR = Path(__file__).resolve().parent.parent.parent / ".mcp-tokens"


# ---- App-driven OAuth (browser opens on the PHONE; callback returns via FastAPI) ----
_APP_PENDING: dict[str, asyncio.Future] = {}


def app_redirect_uri() -> str | None:
    """Loopback redirect (RFC 8252). The phone's browser redirects to localhost ON THE
    DEVICE, where the app catches the code and POSTs it to /api/oauth/complete. Zepto &
    Swiggy only allow localhost redirects (ngrok/LAN/custom domains are rejected), so this
    is the only redirect that passes their DCR check. Port must match the app's loopback."""
    port = os.environ.get("OAUTH_LOOPBACK_PORT", "8971")
    return f"http://localhost:{port}/callback"


def resolve_app_callback(code: str, state: str) -> bool:
    """Called by FastAPI GET /api/oauth/callback when the phone's browser redirects back."""
    fut = _APP_PENDING.get(state)
    if fut and not fut.done():
        fut.get_loop().call_soon_threadsafe(fut.set_result, (code, state))
        return True
    return False


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

    async def _retry(self, fn, tries: int = 6):
        """Run an MCP action, retrying on HTTP 429 with exponential backoff + jitter. The MCP
        client raises inside an anyio TaskGroup, so a 429 surfaces as an ExceptionGroup — unwrap
        it and, if it never clears, raise ONE clean message instead of the opaque TaskGroup text.

        6 tries with capped backoff (~1,2,4,6,6s + jitter ≈ up to ~20s) — enough for a payment
        confirm to ride out a transient store rate-limit instead of failing the whole order."""
        delay = 1.0
        for i in range(tries):
            try:
                return await fn()
            except BaseException as e:  # noqa: BLE001 — includes the TaskGroup ExceptionGroup
                if _has_429(e):
                    if i < tries - 1:
                        await asyncio.sleep(delay + random.uniform(0, 0.5))  # jitter: de-sync parallel retries
                        delay = min(delay * 2, 6.0)
                        continue
                    raise RuntimeError("store abhi busy hai (rate limit) — thodi der baad try karo") from e
                raise

    async def call(self, tool: str, args: dict | None = None) -> object:
        async def _do():
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
        return await self._retry(_do)

    async def list_tools(self) -> list[str]:
        async def _do():
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
        return await self._retry(_do)

    async def list_tool_defs(self) -> list[dict]:
        """Full tool definitions (name + description + input schema) for discovery —
        used to find the cart / checkout / order / payment tools a merchant exposes."""
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
                return [
                    {
                        "name": t.name,
                        "description": getattr(t, "description", "") or "",
                        "input_schema": getattr(t, "inputSchema", None),
                    }
                    for t in tools.tools
                ]

    async def app_connect(self, on_auth_url) -> bool:
        """OAuth where the auth URL is handed to `on_auth_url` (the phone opens it in a
        browser) and the callback comes back via FastAPI /api/oauth/callback →
        resolve_app_callback(). Returns True once a token is cached."""
        redirect_uri = app_redirect_uri()
        if not redirect_uri:
            raise RuntimeError("PUBLIC_BASE_URL not set (needed for phone OAuth redirect)")
        holder: dict = {"state": None}

        async def redirect_handler(url: str) -> None:
            holder["state"] = parse_qs(urlparse(url).query).get("state", [None])[0]
            _APP_PENDING[holder["state"]] = asyncio.get_event_loop().create_future()
            await on_auth_url(url)

        async def callback_handler():
            code, st = await _APP_PENDING[holder["state"]]
            _APP_PENDING.pop(holder["state"], None)
            return code, st

        metadata = OAuthClientMetadata(
            redirect_uris=[redirect_uri],  # type: ignore[list-item]
            token_endpoint_auth_method="none",
            grant_types=["authorization_code", "refresh_token"],
            response_types=["code"],
            scope=self.scope,
            client_name="shopmandate",
        )
        provider = OAuthClientProvider(
            server_url=self.server_url, client_metadata=metadata, storage=self.storage,
            redirect_handler=redirect_handler, callback_handler=callback_handler,
        )
        async with streamablehttp_client(self.server_url, auth=provider) as (read, write, _):
            async with ClientSession(read, write) as session:
                await session.initialize()
                await session.list_tools()
        return self.connected()
