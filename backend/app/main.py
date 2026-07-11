"""FastAPI app + frozen §8 routes."""

from __future__ import annotations

import os
from pathlib import Path

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
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

from . import gemini, live, p3p  # noqa: E402
from .models import (  # noqa: E402
    ClarifyReq,
    ConnectStartReq,
    ConnectVerifyReq,
    MandateReq,
    PayReq,
    PreauthReq,
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


@app.get("/api/orders")
def orders() -> dict:
    return orchestrator.orders()


# ---- Pine Labs P3P: agentic payment (edge #1) ----
@app.get("/api/p3p/status")
def p3p_status() -> dict:
    return {"mode": p3p.mode()}


# ---- Gemini Live: real-time DUPLEX voice (Phase 2) ----
@app.websocket("/api/live")
async def live_ws(ws: WebSocket) -> None:
    """Bridge the app's mic PCM (16k) ↔ a Gemini Live native-audio session (24k out + transcripts).

    Uplink: binary frames = raw 16kHz PCM mic chunks; text frames = {"type":"text"|"end", ...}.
    Downlink: binary frames = 24kHz PCM to play; text frames = {"type":"you"|"agent"|"interrupted"
    |"turn_complete", "text"?}.
    """
    import asyncio
    import json

    from google.genai import types as gt

    await ws.accept()
    try:
        async with live.connect() as session:
            async def uplink() -> None:
                while True:
                    msg = await ws.receive()
                    if msg.get("type") == "websocket.disconnect":
                        return
                    if (chunk := msg.get("bytes")) is not None:
                        await session.send_realtime_input(
                            audio=gt.Blob(data=chunk, mime_type=live.INPUT_MIME)
                        )
                    elif (text := msg.get("text")) is not None:
                        data = json.loads(text)
                        if data.get("type") == "text" and data.get("text"):
                            await session.send_client_content(
                                turns=gt.Content(role="user", parts=[gt.Part(text=data["text"])]),
                                turn_complete=True,
                            )
                        elif data.get("type") == "end":
                            await session.send_realtime_input(audio_stream_end=True)

            async def downlink() -> None:
                async for message in session.receive():
                    if message.data:  # 24kHz PCM audio chunk
                        await ws.send_bytes(message.data)
                    sc = message.server_content
                    if not sc:
                        continue
                    if sc.input_transcription and sc.input_transcription.text:
                        await ws.send_text(json.dumps({"type": "you", "text": sc.input_transcription.text}))
                    if sc.output_transcription and sc.output_transcription.text:
                        await ws.send_text(json.dumps({"type": "agent", "text": sc.output_transcription.text}))
                    if sc.interrupted:  # user barged in — tell the app to stop playback
                        await ws.send_text(json.dumps({"type": "interrupted"}))
                    if sc.turn_complete:
                        await ws.send_text(json.dumps({"type": "turn_complete"}))

            up = asyncio.create_task(uplink())
            down = asyncio.create_task(downlink())
            _, pending = await asyncio.wait({up, down}, return_when=asyncio.FIRST_COMPLETED)
            for task in pending:
                task.cancel()
    except WebSocketDisconnect:
        pass
    except Exception as e:  # a Live hiccup shouldn't crash the worker
        print(f"[live] session error: {e}")
        try:
            await ws.close()
        except Exception:
            pass


# ---- Wow-factors ----
@app.post("/api/visualize")
def visualize(req: VisualizeReq) -> dict:
    """Nano Banana: generate a clean product/try-on image (from a name or a 'like this' photo)."""
    if req.image_b64:
        prompt = ("Identify the product in this image, then generate a clean, e-commerce style product "
                  f"photo of a similar item{(' ' + req.style) if req.style else ' on a plain white background'}. "
                  "Studio lighting, high detail, no text or watermark.")
    else:
        prompt = (f"Clean e-commerce product photo of {req.product or 'wireless earbuds'}"
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


@app.post("/api/session/{sid}/mandate")
async def create_mandate(sid: str, req: MandateReq) -> dict:
    try:
        return await orchestrator.create_mandate(sid, req.mobile, req.cap_inr)
    except KeyError:
        raise HTTPException(status_code=404, detail="unknown session_id")


@app.post("/api/session/{sid}/preauth")
def set_preauth(sid: str, req: PreauthReq) -> dict:
    return _session(lambda: orchestrator.set_preauth(sid, req.product, req.max_price_inr))


@app.post("/api/session/{sid}/preauth/run")
async def run_preauth(sid: str) -> dict:
    try:
        return await orchestrator.run_preauth(sid)
    except KeyError:
        raise HTTPException(status_code=404, detail="unknown session_id")


@app.post("/api/merchants/{mid}/connect")
async def merchant_connect(mid: str) -> dict:
    return await orchestrator.connect_merchant(mid)


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
