"""FastAPI app + frozen §8 routes."""

from __future__ import annotations

import os
from pathlib import Path

from fastapi import FastAPI, HTTPException, WebSocket
from fastapi.middleware.cors import CORSMiddleware

# Load backend/.env (GOOGLE_API_KEY=...) before importing modules that read env.
_env = Path(__file__).resolve().parent.parent / ".env"
if _env.exists():
    for line in _env.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())

from . import orchestrator  # noqa: E402
import base64  # noqa: E402

from . import gemini  # noqa: E402
from .models import (  # noqa: E402
    ClarifyReq,
    ConnectStartReq,
    ConnectVerifyReq,
    PayReq,
    SayReq,
    StartReq,
    VisualizeReq,
)

app = FastAPI(title="ShopMandate backend")
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"],
)


def _session(fn):
    try:
        return fn()
    except KeyError:
        raise HTTPException(status_code=404, detail="unknown session_id")


@app.get("/api/health")
def health() -> dict:
    return {"ok": True, "flash": os.environ.get("FLASH_MODEL", "gemini-flash-latest")}


@app.websocket("/ws/live")
async def ws_live(ws: WebSocket) -> None:
    """Real-time voice shopping agent (Gemini Live)."""
    await ws.accept()
    from . import live
    try:
        await live.bridge(ws)
    except Exception as e:  # noqa: BLE001
        print(f"[ws_live] {e}")
        try:
            await ws.close()
        except Exception:
            pass


@app.post("/api/session/start")
def session_start(req: StartReq) -> dict:
    return orchestrator.start(req)


@app.post("/api/session/{sid}/clarify")
def session_clarify(sid: str, req: ClarifyReq) -> dict:
    return _session(lambda: orchestrator.clarify(sid, req))


@app.post("/api/session/{sid}/search")
async def session_search(sid: str) -> dict:
    try:
        return await orchestrator.search(sid)
    except KeyError:
        raise HTTPException(status_code=404, detail="unknown session_id")


@app.get("/api/merchants")
def merchants() -> dict:
    return orchestrator.merchants_status()


# ---- store-scoped connect + orders (Android app contract) ----
@app.post("/api/connect/{store}/start")
def store_connect_start(store: str, req: ConnectStartReq) -> dict:
    return orchestrator.store_connect_start(store, req.phone)


@app.post("/api/connect/{store}/verify")
def store_connect_verify(store: str, req: ConnectVerifyReq) -> dict:
    return orchestrator.store_connect_verify(store, req.otp)


# ---- app-driven browser OAuth (phone opens the real consent page) ----
@app.post("/api/connect/{store}/oauth/start")
async def store_oauth_start(store: str) -> dict:
    return await orchestrator.oauth_start(store)


@app.get("/api/connect/{store}/status")
def store_connect_status(store: str) -> dict:
    return orchestrator.oauth_status(store)


@app.get("/api/oauth/callback")
def oauth_callback(code: str = "", state: str = ""):
    from fastapi.responses import HTMLResponse

    from .merchants import mcp_client
    ok = mcp_client.resolve_app_callback(code, state)
    msg = "Connected! Ab app pe wapas jao." if ok else "Session expire ho gaya — dobara try karo."
    return HTMLResponse(
        f"<body style='font-family:system-ui;text-align:center;padding-top:80px'>"
        f"<h2>{msg}</h2></body>"
    )


@app.post("/api/oauth/complete")
def oauth_complete(body: dict):
    """The app's loopback catcher delivers the OAuth (code, state) here → resume token exchange."""
    from .merchants import mcp_client
    ok = mcp_client.resolve_app_callback(body.get("code", ""), body.get("state", ""))
    return {"ok": ok}


@app.get("/api/orders")
def orders() -> dict:
    return orchestrator.orders()


# ---- Wow-factors ----
@app.post("/api/visualize")
def visualize(req: VisualizeReq) -> dict:
    """Nano Banana: generate a clean product/try-on image (from a name or a 'like this' photo)."""
    if req.image_b64:
        prompt = ("Identify the product in this image, then generate a clean, e-commerce style product "
                  f"photo of a similar item{(' ' + req.style) if req.style else ' on a plain white background'}. "
                  "Studio lighting, high detail, no text or watermark.")
    else:
        prompt = (f"Clean e-commerce product photo of {req.product or 'the product'}"
                  f"{(', ' + req.style) if req.style else ', on a plain white background'}. "
                  "Studio lighting, high detail, no text or watermark.")
    try:
        png = gemini.gen_visual(prompt, req.image_b64)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"image gen failed: {e}")
    return {"image_b64": base64.b64encode(png).decode(), "mime": "image/png"}


@app.post("/api/say")
def say(req: SayReq) -> dict:
    """Gemini TTS: Hinglish voice-out of the agent's reply (returns WAV base64)."""
    try:
        wav = gemini.say(req.text)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"tts failed: {e}")
    return {"audio_b64": base64.b64encode(wav).decode(), "mime": "audio/wav"}


@app.get("/api/session/{sid}/haggle")
async def haggle_stream(sid: str):
    """Live A2A haggle: stream the negotiation steps one-by-one (SSE) for the compare animation."""
    import asyncio
    import json as _json

    from fastapi.responses import StreamingResponse

    s = orchestrator.SESSIONS.get(sid)
    steps = list((s.decision.steps if s and s.decision else []) or [])

    async def gen():
        for st in steps:
            yield f"data: {_json.dumps({'step': st})}\n\n"
            await asyncio.sleep(0.9)
        winner = s.decision.winner.model_dump() if s and s.decision and s.decision.winner else None
        yield f"data: {_json.dumps({'done': True, 'winner': winner})}\n\n"

    return StreamingResponse(gen(), media_type="text/event-stream")


@app.get("/api/session/{sid}/research")
def research(sid: str) -> dict:
    """Deep-research-lite: a Hinglish 'best value' one-liner over the current quotes."""
    s = orchestrator.SESSIONS.get(sid)
    if not s or not s.decision or not s.decision.quotes:
        raise HTTPException(status_code=404, detail="no quotes; run /search first")
    summary = " · ".join(f"{q.store}: ₹{q.price_inr} ({q.delivery})" for q in s.decision.quotes)
    try:
        note = gemini.value_note(summary)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"research failed: {e}")
    return {"note": note, "quotes_considered": len(s.decision.quotes)}


@app.post("/api/merchants/{mid}/connect")
async def merchant_connect(mid: str) -> dict:
    return await orchestrator.connect_merchant(mid)


@app.get("/api/merchants/{mid}/tools")
async def merchant_tools(mid: str) -> dict:
    """Discovery: full tool defs of a connected merchant (to build the order pipeline)."""
    return await orchestrator.merchant_tools(mid)


@app.post("/api/merchants/{mid}/call")
async def merchant_call(mid: str, body: dict) -> dict:
    """DEV-ONLY: call an arbitrary MCP tool to observe real response shapes."""
    return await orchestrator.merchant_call(mid, body.get("tool", ""), body.get("args"))


@app.get("/api/merchants/{mid}/addresses")
async def merchant_addresses(mid: str) -> dict:
    """Saved delivery addresses (for the pre-order address picker)."""
    return await orchestrator.merchant_addresses(mid)


@app.post("/api/merchants/{mid}/order/prepare")
async def order_prepare(mid: str, body: dict) -> dict:
    """Build the real cart + preview the order (no charge). body: {query, budget_inr?, qty?, address_id?}"""
    return await orchestrator.order_prepare(
        mid, body.get("query", ""), body.get("budget_inr"),
        int(body.get("qty", 1) or 1), body.get("address_id"),
    )


@app.post("/api/merchants/{mid}/order/prepare_cart")
async def order_prepare_cart(mid: str, body: dict) -> dict:
    """Multi-item cart preview (Live voice). body: {items:[{query, budget_inr?, qty?}], address_id?}"""
    return await orchestrator.order_prepare_cart(
        mid, body.get("items") or [], body.get("address_id"),
    )


@app.post("/api/merchants/{mid}/order/confirm")
async def order_confirm(mid: str, body: dict) -> dict:
    """Place the REAL order — charges via the merchant. body: {address_id, rail?, intent_app?, meta?}"""
    return await orchestrator.order_confirm(
        mid, body.get("address_id", ""),
        body.get("rail", "online"), body.get("intent_app", "gpay://upi/"),
        body.get("meta"),
    )


@app.post("/api/merchants/{mid}/order/status")
async def order_status(mid: str, body: dict) -> dict:
    """Poll payment/order status after the payment link is opened. body: {order_id}"""
    return await orchestrator.order_status(mid, body.get("order_id", ""))


@app.post("/api/session/{sid}/connect/start")
def connect_start(sid: str, req: ConnectStartReq) -> dict:
    return _session(lambda: orchestrator.connect_start(sid, req.phone))


@app.post("/api/session/{sid}/connect/verify")
def connect_verify(sid: str, req: ConnectVerifyReq) -> dict:
    return _session(lambda: orchestrator.connect_verify(sid, req.otp))


@app.post("/api/session/{sid}/pay")
async def session_pay(sid: str, req: PayReq) -> dict:
    try:
        return await orchestrator.pay(sid, req)
    except KeyError:
        raise HTTPException(status_code=404, detail="unknown session_id")
