"""FastAPI app + frozen §8 routes."""

from __future__ import annotations

import os
from pathlib import Path

from fastapi import FastAPI, HTTPException
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
from .models import (  # noqa: E402
    ClarifyReq,
    ConnectStartReq,
    ConnectVerifyReq,
    PayReq,
    StartReq,
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
