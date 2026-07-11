"""Shopping Agent orchestrator (§9.1) — session lifecycle + tool sequencing.

In-memory session store keyed by session_id (a real DB is overkill for the hackathon).
"""

from __future__ import annotations

import asyncio
import uuid
from dataclasses import dataclass, field
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse

from . import connect, gemini, negotiation, payment
from .merchants import ordering, registry
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
    history: list[dict] = field(default_factory=list)   # clarify-chat turns: {role, text}


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
    }


def _needs_clarification(i: Intent) -> bool:
    # Ask only when the model flagged it AND a required field is genuinely missing.
    return bool(i.needs_clarification) and (not i.product or i.budget_inr is None)


def _default_suggestions(i: Intent) -> list[str]:
    """Feature-forward quick-reply chips shown under the agent's message."""
    chips: list[str] = []
    if i.budget_inr is None:
        chips.append("Budget ₹1500 rakho")
    chips += [
        "Log kya bolte hain? ⭐",
        "Sasta similar dikhao",
        "Har hafte mangwa do 🔁",
        "Price gire to lelo 🔔",
    ]
    return chips[:4]


def _opener(i: Intent, clarify_needed: bool, user_name: str | None) -> str:
    """First agent bubble on the Clarify chat screen."""
    if clarify_needed and i.clarifying_question:
        return i.clarifying_question
    name = (user_name or "").split(" ")[0]
    hi = f"{name}, " if name else ""
    prod = i.product or "ye"
    return f"{hi}samajh gaya — {prod} chahiye. Kuch aur batana hai ya aage badhein?"


# ---- §8.1 start ----
def start(req: StartReq) -> dict:
    if req.input_type == "voice" and req.audio_b64:
        intent = gemini.extract_intent_from_audio(req.audio_b64, req.language_hint)
    elif req.input_type == "photo" and req.image_b64:
        intent = gemini.identify_from_image(req.image_b64)
    else:
        intent = gemini.extract_intent_from_text(req.text or "", req.language_hint)

    sid = str(uuid.uuid4())
    session = Session(id=sid, intent=intent)
    clarify_needed = _needs_clarification(intent)
    opener = _opener(intent, clarify_needed, req.user_name)
    session.history.append({"role": "agent", "text": opener})
    SESSIONS[sid] = session
    return {
        "session_id": sid,
        "status": "need_clarification" if clarify_needed else "intent_ready",
        "transcript": intent.transcript,
        "parsed_intent": _intent_public(intent),
        "clarifying_question": intent.clarifying_question if clarify_needed else None,
        "agent_opener": opener,
        "suggestions": _default_suggestions(intent),
    }


# ---- clarify-chat (multi-turn agent) ----
def chat(session_id: str, message: str, user_name: str | None) -> dict:
    """One conversational turn. Reads intent + short history, returns reply + intent updates."""
    s = _get(session_id)
    i = s.intent or Intent()
    s.history.append({"role": "user", "text": message})
    try:
        r = gemini.chat_turn(i, s.history, message, user_name)
    except Exception as e:  # never break the chat on a model hiccup
        print(f"[chat] {e}")
        reply = "Thoda phir se bolo — main sun raha hoon."
        s.history.append({"role": "agent", "text": reply})
        return {"reply": reply, "parsed_intent": _intent_public(i),
                "suggestions": _default_suggestions(i), "ready": False,
                "show_product_image": False}

    if r.product:
        i.product = r.product
    if r.budget_inr is not None:
        i.budget_inr = r.budget_inr
    if r.qty is not None and r.qty > 0:
        i.qty = r.qty
    if r.constraints:
        i.constraints = r.constraints
    if r.ready_to_search:
        i.needs_clarification = False
    s.intent = i
    s.history.append({"role": "agent", "text": r.reply})
    return {
        "reply": r.reply,
        "parsed_intent": _intent_public(i),
        "suggestions": r.suggestions or _default_suggestions(i),
        "ready": r.ready_to_search,
        "show_product_image": r.show_product_image,
    }


def patch_intent(session_id: str, patch: dict) -> dict:
    """Direct edit of the parsed intent from the tap-to-edit chips (product / budget / qty)."""
    s = _get(session_id)
    i = s.intent or Intent()
    if patch.get("product"):
        i.product = str(patch["product"]).strip()
    if "budget_inr" in patch:                       # None = user cleared the budget
        b = patch["budget_inr"]
        i.budget_inr = int(b) if b is not None else None
    if patch.get("qty") is not None:
        try:
            i.qty = max(1, int(patch["qty"]))
        except (TypeError, ValueError):
            pass
    s.intent = i
    return {"status": "ok", "parsed_intent": _intent_public(i)}


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
        # `type` is a variant constraint (e.g. "Wireless"), never the product itself —
        # don't fabricate a product from it. The product comes from what the user asked.
        t = str(a["type"])
        if t not in i.constraints:
            i.constraints.append(t)
    for k, v in a.items():
        if k in ("qty",) and v is not None:
            try:
                i.qty = int(v)  # type: ignore[arg-type]
            except (TypeError, ValueError):
                pass
    i.needs_clarification = False
    s.intent = i
    return {"status": "intent_ready", "parsed_intent": _intent_public(i)}


async def _gather_quotes(intent: Intent, only: set[str] | None = None) -> list[Quote]:
    query = (intent.product or "").strip()
    if not query:
        return []  # no product yet — nothing to search (don't fabricate a query)

    async def one(m) -> Quote | None:
        try:
            return await m.search(query, intent.budget_inr)
        except Exception as e:  # a flaky merchant must not sink the whole search
            print(f"[merchant {m.id}] search failed: {e}")
            return None

    # `only` locks the search to a single store once the user has committed to one
    # (multi-item cart must ship from one merchant). None = compare all stores.
    merchants = [m for m in registry.MERCHANTS if only is None or m.id in only]
    results = await asyncio.gather(*(one(m) for m in merchants))
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


async def _real_connected(mid: str):
    m = registry.by_id(mid)
    if m is None or not getattr(m, "real", False):
        return None, {"status": "unknown_or_mock", "id": mid}
    if not m.connected():
        return None, {"status": "not_connected", "id": mid}
    return m, None


async def order_prepare(mid: str, query: str, budget_inr: int | None, qty: int,
                        address_id: str | None) -> dict:
    """Real cart build + order preview on a merchant (no charge). Returns amount to pay."""
    m, err = await _real_connected(mid)
    if err:
        return err
    try:
        return await ordering.prepare(m, query, budget_inr, qty, address_id)
    except Exception as e:
        return {"status": "error", "detail": str(e)}


async def order_prepare_cart(mid: str, items: list[dict], address_id: str | None) -> dict:
    """Multi-item cart preview on one merchant (Live voice cart). `items`: [{query, budget_inr?, qty?}]."""
    m, err = await _real_connected(mid)
    if err:
        return err
    if not items:
        return {"status": "no_product"}
    try:
        return await ordering.prepare_cart(m, items, address_id)
    except Exception as e:
        return {"status": "error", "detail": str(e)}


async def merchant_addresses(mid: str) -> dict:
    """Saved delivery addresses for a connected merchant (for the pre-order address picker)."""
    m, err = await _real_connected(mid)
    if err:
        return err
    try:
        return {"status": "ok", "id": mid, "name": m.name, "addresses": await ordering.list_addresses(m)}
    except Exception as e:
        return {"status": "error", "detail": str(e)}


async def order_confirm(mid: str, address_id: str, rail: str = "online",
                        intent_app: str = "gpay://upi/", meta: dict | None = None) -> dict:
    """Places the REAL order (charges via the merchant's payment flow). Returns pay reference.

    `meta` is the breakdown from order/prepare (product, item/delivery/total, address) so the
    order history keeps the full, transparent record of what was paid and where it ships.
    """
    from datetime import date
    m, err = await _real_connected(mid)
    if err:
        return err
    try:
        res = await ordering.confirm(m, address_id, rail=rail, intent_app=intent_app)
    except Exception as e:
        return {"status": "error", "detail": str(e)}
    # Record locally for order history once placed (with the full transparent breakdown).
    if res.get("status") == "placed" and res.get("order_id"):
        meta = meta or {}
        addr = meta.get("address") or {}
        _ORDERS.append({
            "product": meta.get("product") or res.get("product", "Order"),
            "store": res.get("store", m.name),
            "qty": meta.get("qty", 1),
            "item_price_inr": meta.get("item_price_inr"),
            "delivery_fee_inr": meta.get("delivery_fee_inr"),
            "price_inr": meta.get("to_pay_inr", 0),           # total paid
            "address_label": addr.get("label"),
            "address_line": addr.get("line"),
            "order_id": str(res["order_id"]),
            "date": date.today().isoformat(),
            "status": "placed", "delivered": False,
        })
    return res


async def order_status(mid: str, order_id: str) -> dict:
    """Poll merchant payment/order status (after the payment link is paid)."""
    m, err = await _real_connected(mid)
    if err:
        return err
    if not order_id:
        return {"status": "error", "detail": "order_id required"}
    try:
        return {"status": "ok", **await ordering.payment_status(m, order_id)}
    except Exception as e:
        return {"status": "error", "detail": str(e)}


async def merchant_call(mid: str, tool: str, args: dict | None) -> dict:
    """DEV-ONLY passthrough: call any MCP tool on a connected merchant and return the raw
    result. Used to observe real response shapes while building the order pipeline."""
    m = registry.by_id(mid)
    if m is None or not getattr(m, "real", False):
        return {"status": "unknown_or_mock", "id": mid}
    if not m.connected():
        return {"status": "not_connected", "id": mid}
    try:
        result = await m.mcp.call(tool, args or {})  # type: ignore[attr-defined]
    except Exception as e:
        return {"status": "error", "tool": tool, "detail": str(e)}
    return {"status": "ok", "tool": tool, "result": result}


async def merchant_tools(mid: str) -> dict:
    """Discovery: dump a connected merchant's full tool list (names + input schemas)
    so we can wire the real add-to-cart → checkout → place-order → pay pipeline."""
    m = registry.by_id(mid)
    if m is None or not getattr(m, "real", False):
        return {"status": "unknown_or_mock", "id": mid}
    if not m.connected():
        return {"status": "not_connected", "id": mid, "name": m.name}
    try:
        defs = await m.tool_defs()  # type: ignore[attr-defined]
    except Exception as e:
        return {"status": "error", "id": mid, "detail": str(e)}
    return {"status": "ok", "id": mid, "name": m.name, "count": len(defs), "tools": defs}


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


# ---- app-driven browser OAuth (phone opens the real consent URL) ----
def _with_login_hint(url: str, phone: str | None) -> str:
    """Attach the user's phone (from Settings) to the store's OAuth login page as a
    standard OIDC `login_hint`, so the store pre-fills that number for the OTP. Unknown
    params are ignored by OAuth servers, so this is safe even if the store doesn't use it."""
    digits = "".join(ch for ch in (phone or "") if ch.isdigit())[-10:]
    if len(digits) != 10:
        return url
    parsed = urlparse(url)
    q = dict(parse_qsl(parsed.query))
    q.setdefault("login_hint", digits)
    return urlunparse(parsed._replace(query=urlencode(q)))


async def oauth_start(store: str, phone: str | None = None) -> dict:
    m = registry.by_id(store)
    if not m or not getattr(m, "real", False):
        return {"status": "unknown_or_mock", "store": store}
    if m.connected():  # token already cached — no browser needed
        _CONNECTED_STORES.add(store)
        return {"status": "connected", "store": store,
                "connected_stores": sorted(_CONNECTED_STORES)}

    loop = asyncio.get_event_loop()
    url_fut: asyncio.Future = loop.create_future()

    async def on_url(url: str) -> None:
        if not url_fut.done():
            url_fut.set_result(url)

    async def run() -> None:
        try:
            ok = await m.mcp.app_connect(on_url)  # type: ignore[attr-defined]
            if ok:
                _CONNECTED_STORES.add(store)
        except Exception as e:
            print(f"[oauth {store}] {e}")
            if not url_fut.done():
                url_fut.set_exception(e)

    asyncio.create_task(run())
    try:
        url = await asyncio.wait_for(url_fut, timeout=30)
    except Exception as e:
        return {"status": "error", "store": store, "detail": str(e)}
    return {"status": "auth_required", "store": store, "auth_url": _with_login_hint(url, phone)}


def oauth_status(store: str) -> dict:
    m = registry.by_id(store)
    connected = bool(m and m.connected())
    if connected:
        _CONNECTED_STORES.add(store)
    return {"store": store, "connected": connected,
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


# ---- §8.6 pay ----
async def pay(session_id: str, req: PayReq) -> dict:
    """Record the order after a real UPI payment.

    The actual money movement happens on-device: the app fires a UPI deep link
    (upi://pay) that opens the user's own UPI app, where they enter their PIN.
    Once that succeeds the app calls this to persist the order + return a receipt.
    """
    s = _get(session_id)
    if not s.decision or not s.decision.cart:
        return {"status": "no_cart"}
    cart = s.decision.cart
    audit = {k: f"{k}-{session_id[:8]}" for k in ("intent", "cart", "payment")}

    receipt = await payment.charge(cart, req.upi_app, audit, upi_txn_id=req.upi_txn_id)
    _record_order(cart, receipt.order_id)
    return {"status": "complete", "order_id": receipt.order_id, "receipt": receipt.model_dump()}
