"""Shopping Agent orchestrator (§9.1) — session lifecycle + tool sequencing.

In-memory session store keyed by session_id (a real DB is overkill for the hackathon).
"""

from __future__ import annotations

import asyncio
import uuid
from dataclasses import dataclass

from . import connect, gemini, negotiation, payment
from .merchants import registry
from .models import (
    ClarifyReq,
    Intent,
    PayReq,
    Quote,
    StartReq,
)
from .negotiation import Decision


@dataclass
class Session:
    id: str
    intent: Intent | None = None
    decision: Decision | None = None
    connected: bool = False


SESSIONS: dict[str, Session] = {}


def _get(session_id: str) -> Session:
    s = SESSIONS.get(session_id)
    if s is None:
        raise KeyError(session_id)
    return s


def _intent_public(i: Intent) -> dict:
    return {
        "product": i.product,
        "category": i.category,
        "budget_inr": i.budget_inr,
        "qty": i.qty,
        "constraints": i.constraints,
        "language": i.language,
    }


def _needs_clarification(i: Intent) -> bool:
    # Ask only when the model flagged it AND a required field is genuinely missing.
    return bool(i.needs_clarification) and (not i.product or i.budget_inr is None)


# ---- §8.1 start ----
def start(req: StartReq) -> dict:
    if req.input_type == "voice" and req.audio_b64:
        intent = gemini.extract_intent_from_audio(req.audio_b64, req.language_hint)
    elif req.input_type == "photo" and req.image_b64:
        intent = gemini.identify_from_image(req.image_b64)
    else:
        intent = gemini.extract_intent_from_text(req.text or "", req.language_hint)

    sid = str(uuid.uuid4())
    SESSIONS[sid] = Session(id=sid, intent=intent)
    clarify_needed = _needs_clarification(intent)
    return {
        "session_id": sid,
        "status": "need_clarification" if clarify_needed else "intent_ready",
        "transcript": intent.transcript,
        "parsed_intent": _intent_public(intent),
        "clarifying_question": intent.clarifying_question if clarify_needed else None,
    }


# ---- §8.2 clarify ----
def clarify(session_id: str, req: ClarifyReq) -> dict:
    s = _get(session_id)
    i = s.intent or Intent()
    a = req.answers
    if "budget_inr" in a and a["budget_inr"] is not None:
        try:
            i.budget_inr = int(a["budget_inr"])  # type: ignore[arg-type]
        except (TypeError, ValueError):
            pass
    if "type" in a and a["type"]:
        t = str(a["type"])
        if t not in i.constraints:
            i.constraints.append(t)
        if not i.product:
            i.product = f"{t} earbuds" if "wire" in t else t
    for k, v in a.items():
        if k in ("qty",) and v is not None:
            try:
                i.qty = int(v)  # type: ignore[arg-type]
            except (TypeError, ValueError):
                pass
    i.needs_clarification = False
    s.intent = i
    return {"status": "intent_ready", "parsed_intent": _intent_public(i)}


async def _gather_quotes(intent: Intent) -> list[Quote]:
    query = (intent.product or "").strip() or "wireless earbuds"

    async def one(m) -> Quote | None:
        try:
            return await m.search(query, intent.budget_inr)
        except Exception as e:  # a flaky merchant must not sink the whole search
            print(f"[merchant {m.id}] search failed: {e}")
            return None

    results = await asyncio.gather(*(one(m) for m in registry.MERCHANTS))
    return [q for q in results if q is not None]


# ---- merchants ----
def merchants_status() -> dict:
    return {"merchants": registry.status()}


async def connect_merchant(mid: str) -> dict:
    m = registry.by_id(mid)
    if m is None or not getattr(m, "real", False):
        return {"status": "unknown_or_mock", "id": mid}
    ok = await m.connect()  # type: ignore[attr-defined]
    return {"status": "connected" if ok else "failed", "id": mid, "name": m.name}


# ---- §8.3 search ----
async def search(session_id: str) -> dict:
    s = _get(session_id)
    intent = s.intent or Intent()
    quotes = await _gather_quotes(intent)
    decision = negotiation.decide(intent, quotes)
    s.decision = decision

    body: dict = {
        "status": decision.status,
        "quotes": [q.model_dump() for q in decision.quotes],
        "steps": decision.steps,
    }
    if decision.status == "awaiting_approval":
        body["winner"] = decision.winner.model_dump() if decision.winner else None
        body["cart"] = decision.cart.model_dump() if decision.cart else None
    elif decision.status == "over_budget":
        body["clarifying_question"] = decision.clarifying_question
    return body


# ---- §8.4 / §8.5 connect ----
def connect_start(session_id: str, phone: str) -> dict:
    _get(session_id)
    return {"status": "otp_sent", "masked_phone": connect.start_otp(phone)}


def connect_verify(session_id: str, otp: str) -> dict:
    s = _get(session_id)
    if not connect.verify(otp):
        return {"status": "invalid_otp"}
    s.connected = True
    return {"status": "connected", "store": "Zepto"}


# ---- §8.6 pay ----
async def pay(session_id: str, req: PayReq) -> dict:
    s = _get(session_id)
    if not s.decision or not s.decision.cart:
        return {"status": "no_cart"}
    intent_id = f"intent-{session_id[:8]}"
    cart_id = f"cart-{session_id[:8]}"
    payment_id = f"payment-{session_id[:8]}"
    receipt = await payment.charge(
        s.decision.cart, req.upi_app,
        {"intent": intent_id, "cart": cart_id, "payment": payment_id},
    )
    return {"status": "complete", "order_id": receipt.order_id, "receipt": receipt.model_dump()}
