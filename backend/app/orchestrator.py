"""Shopping Agent orchestrator (§9.1) — session lifecycle + tool sequencing.

In-memory session store keyed by session_id (a real DB is overkill for the hackathon).
"""

from __future__ import annotations

import asyncio
import uuid
from dataclasses import dataclass

from . import connect, gemini, negotiation, p3p, payment
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
    mobile: str | None = None            # set once a P3P mandate is approved
    preauth: dict | None = None          # {"product": str, "max_price_inr": int}


SESSIONS: dict[str, Session] = {}
_CONNECTED_STORES: set[str] = set()   # store-scoped connections (Android uses these)
_ORDERS: list[dict] = []              # order history for GET /api/orders


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
        "quick_replies": i.quick_replies,
        "budget_relevant": i.budget_relevant,
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
        # always surface the question + options so the (always-shown) clarify screen is dynamic
        "clarifying_question": intent.clarifying_question,
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
        import re
        choice = str(a["type"]).strip()
        low = choice.lower()
        # a budget-range quick-reply ("Under 1500", "1500 se 3000", "Premium range")
        is_budgetish = bool(re.search(r"\d", choice)) or any(
            w in low for w in ("premium", "budget", "best offer", "best deal", "sasta")
        )
        nums = [int(x.replace(",", "")) for x in re.findall(r"\d[\d,]*", choice)]
        if is_budgetish and nums:
            i.budget_inr = max(nums)  # "1500 se 3000" -> 3000; "Under 1500" -> 1500
        elif not is_budgetish:
            # brand / type qualifier -> refine the actual search query
            if low not in (i.product or "").lower():
                i.product = f"{choice} {i.product}".strip()
            if choice not in i.constraints:
                i.constraints.append(choice)
    for k, v in a.items():
        if k in ("qty",) and v is not None:
            try:
                i.qty = int(v)  # type: ignore[arg-type]
            except (TypeError, ValueError):
                pass
    i.needs_clarification = False
    s.intent = i
    return {"status": "intent_ready", "parsed_intent": _intent_public(i)}


async def _gather_quotes(intent: Intent, per_store: int = 4, cap: int = 6) -> list[Quote]:
    query = (intent.product or "").strip() or "wireless earbuds"

    async def many(m) -> list[Quote]:
        try:
            return await m.search_many(query, intent.budget_inr, limit=per_store)
        except Exception as e:  # a flaky merchant must not sink the whole search
            print(f"[merchant {m.id}] search failed: {e}")
            return []

    groups = await asyncio.gather(*(many(m) for m in registry.MERCHANTS))
    quotes = [q for g in groups for q in g]
    # cheapest first, capped — winner is the best deal, the rest are alternatives / top-N
    quotes.sort(key=lambda q: q.price_inr)
    return quotes[:cap]


# ---- merchants ----
def merchants_status() -> dict:
    return {"merchants": registry.status()}


async def connect_merchant(mid: str) -> dict:
    m = registry.by_id(mid)
    if m is None or not getattr(m, "real", False):
        return {"status": "unknown_or_mock", "id": mid}
    ok = await m.connect()  # type: ignore[attr-defined]
    return {"status": "connected" if ok else "failed", "id": mid, "name": m.name}


def _decision_from_dict(d: dict) -> Decision:
    from .models import Cart, Winner
    return Decision(
        status=d.get("status", "no_stock"),
        quotes=[Quote(**q) for q in d.get("quotes", [])],
        winner=Winner(**d["winner"]) if d.get("winner") else None,
        cart=Cart(**d["cart"]) if d.get("cart") else None,
        steps=d.get("steps", []),
        clarifying_question=d.get("clarifying_question"),
    )


async def _agentic_decision(intent: Intent) -> Decision:
    """Prefer the ADK Managed-Agent orchestration; fall back to deterministic gather+negotiate."""
    try:
        from . import adk_agents
        d = await adk_agents.run_shopping(intent)
        if d:
            return _decision_from_dict(d)
    except Exception as e:
        print(f"[ADK fallback] {e}")
    return negotiation.decide(intent, await _gather_quotes(intent))


# ---- §8.3 search ----
async def search(session_id: str) -> dict:
    s = _get(session_id)
    intent = s.intent or Intent()
    decision = await _agentic_decision(intent)
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


# ---- store-scoped connect (what the Android app calls) ----
def store_connect_start(store: str, phone: str) -> dict:
    return {"status": "otp_sent", "store": store, "masked_phone": connect.start_otp(phone)}


def store_connect_verify(store: str, otp: str) -> dict:
    if not connect.verify(otp):
        return {"status": "invalid_otp", "store": store,
                "connected_stores": sorted(_CONNECTED_STORES)}
    _CONNECTED_STORES.add(store)
    return {"status": "connected", "store": store,
            "connected_stores": sorted(_CONNECTED_STORES)}


def orders() -> dict:
    return {"orders": list(reversed(_ORDERS))}


def _record_order(cart, order_id: str) -> None:
    from datetime import date
    _ORDERS.append({
        "product": cart.item, "store": cart.store, "price_inr": cart.price_inr,
        "order_id": order_id, "date": date.today().isoformat(),
        "status": "confirmed", "delivered": False,
    })


# ---- P3P mandate + pre-auth (edge #1) ----
async def create_mandate(session_id: str, mobile: str, cap_inr: int) -> dict:
    """One-time human approval of a UPI spend cap; after this the agent pays autonomously."""
    s = _get(session_id)
    s.mobile = mobile
    res = await p3p.create_mandate(mobile, cap_inr)
    return {"status": "mandate_active", **res, "remaining_inr": p3p.balance(mobile)}


def set_preauth(session_id: str, product: str, max_price_inr: int) -> dict:
    """Human-not-present rule: 'auto-buy <product> if price <= ₹X'."""
    s = _get(session_id)
    s.preauth = {"product": product, "max_price_inr": max_price_inr}
    return {"status": "preauth_set", **s.preauth,
            "needs_mandate": s.mobile is None}


async def run_preauth(session_id: str) -> dict:
    """Agent checks the rule against live store prices; buys autonomously if it matches."""
    s = _get(session_id)
    if not s.preauth:
        return {"status": "no_preauth"}
    intent = Intent(product=s.preauth["product"], budget_inr=s.preauth["max_price_inr"])
    decision = await _agentic_decision(intent)
    s.decision = decision
    if decision.status != "awaiting_approval" or not decision.cart:
        return {"status": "no_match", "steps": decision.steps}
    if decision.cart.price_inr > s.preauth["max_price_inr"]:
        return {"status": "above_limit", "found_inr": decision.cart.price_inr}
    # matches → agent captures WITHOUT a second human step (mandate already approved)
    return await pay(session_id, PayReq(upi_app="gpay"), auto=True)


# ---- §8.6 pay ----
async def pay(session_id: str, req: PayReq, auto: bool = False) -> dict:
    s = _get(session_id)
    if not s.decision or not s.decision.cart:
        return {"status": "no_cart"}
    cart = s.decision.cart
    audit = {k: f"{k}-{session_id[:8]}" for k in ("intent", "cart", "payment")}

    # P3P path: if a mandate is approved, the agent captures within the cap (agentic).
    if s.mobile and p3p.mandate_for(s.mobile):
        cap = await p3p.capture(s.mobile, cart.price_inr, cart.store, f"SM-{session_id[:4]}")
        if cap.get("status") == "captured":
            receipt = await payment.charge(cart, "P3P · UPI ReservePay", audit)
            _record_order(cart, receipt.order_id)
            body = {"status": "complete", "order_id": receipt.order_id,
                    "receipt": {**receipt.model_dump(), "paid_via": f"P3P · {cap['rail']}",
                                "p3p": cap}}
            body["autonomous"] = auto  # bought without a second human step
            return body
        return {"status": cap.get("status", "p3p_failed"), "p3p": cap}

    # fallback: mock UPI (framed)
    receipt = await payment.charge(cart, req.upi_app, audit)
    _record_order(cart, receipt.order_id)
    return {"status": "complete", "order_id": receipt.order_id, "receipt": receipt.model_dump()}
