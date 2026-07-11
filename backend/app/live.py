"""Gemini Live bridge — real-time voice shopping agent (multi-item cart).

A FastAPI WebSocket relays audio between the Android app and a Gemini Live session.
The Live agent can call three tools:
  - search_products : real merchant search → quotes pushed to the app as product cards.
  - add_to_cart     : the user chose an item → added to a running cart.
  - checkout        : the user is done → app moves to address + payment.

Same-store lock (important): the FIRST item searches EVERY connected store so the agent
can compare prices. The moment the user adds an item from one store, that store is locked
for the rest of the cart — later searches hit ONLY that store, so the whole order ships
from a single merchant (a multi-store cart cannot be checked out together).

Wire protocol (app <-> backend):
  app -> backend : binary frames  = PCM16 mono 16kHz mic audio
                   text  frames  = {"type":"hello","name":...} | {"type":"text","text":...} | {"type":"end"}
  backend -> app : binary frames  = PCM16 mono 24kHz agent audio (play as received)
                   text  frames  = {"type":"transcript","role":"user"|"agent","text":...}
                                   {"type":"quotes","quotes":[...]}
                                   {"type":"cart","items":[...],"total_inr":N,"store":"zepto"}
                                   {"type":"checkout","store":"zepto","items":[...],"total_inr":N}
                                   {"type":"turn_complete"} | {"type":"error","detail":...}
"""

from __future__ import annotations

import asyncio
import json
import os

from google import genai
from google.genai import types

from . import orchestrator
from .merchants import registry
from .models import Intent

LIVE_MODEL = os.environ.get("LIVE_MODEL", "gemini-2.5-flash-native-audio-latest")
INPUT_RATE = 16000   # app mic → Gemini
OUTPUT_RATE = 24000  # Gemini → app speaker

_TOOLS = [
    {
        "function_declarations": [
            {
                "name": "search_products",
                "description": (
                    "Search connected Indian quick-commerce stores (Zepto, Swiggy Instamart) for a "
                    "product and get LIVE prices. Call this the moment the user names something to buy. "
                    "For the FIRST item it searches every store so you can compare; after the user has "
                    "added an item, it automatically searches only their chosen store."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {"type": "string", "description": "product to search, e.g. 'wireless earbuds'"},
                        "budget_inr": {"type": "integer", "description": "optional max budget in rupees"},
                    },
                    "required": ["query"],
                },
            },
            {
                "name": "add_to_cart",
                "description": (
                    "Add the product the user just chose to their cart. Call this when the user picks an "
                    "item (e.g. 'haan Zepto waala le lo'). After adding, ask in one short Hinglish line "
                    "whether they want anything else."
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {"type": "string", "description": "the same search term used in search_products for this item"},
                        "store": {"type": "string", "description": "the store the user chose, e.g. 'Zepto' or 'Swiggy Instamart'"},
                        "name": {"type": "string", "description": "product name for display"},
                        "price_inr": {"type": "integer", "description": "price in rupees of the chosen item"},
                    },
                    "required": ["query", "store"],
                },
            },
            {
                "name": "checkout",
                "description": (
                    "Finish shopping and move to address + payment. Call this ONLY when the user says they "
                    "don't want anything more (e.g. 'bas', 'itna hi', 'nahi')."
                ),
                "parameters": {"type": "object", "properties": {}},
            },
            {
                "name": "get_order_history",
                "description": (
                    "Fetch the user's PAST orders (order / cart history). Call this whenever the user asks "
                    "about previous orders or purchase history — e.g. 'cart history', 'pichla order', "
                    "'mera order dikhao', 'last order kya tha'. Returns recent orders with product, store, "
                    "price and date."
                ),
                "parameters": {"type": "object", "properties": {}},
            },
        ]
    }
]

_SYSTEM = (
    "You are ShopMandate's live shopping voice agent for Indian users. This is a fast phone call — "
    "be extremely brief, one short Hinglish sentence per reply. Flow: "
    "(1) The MOMENT the user names a product, say a 2-word filler ('Dekhta hoon') and immediately CALL "
    "search_products. "
    "(2) After results, tell the single cheapest deal in ONE line — store, product aur price. Do NOT read a list. "
    "(3) When the user picks an item ('haan wahi', 'Zepto waala'), CALL add_to_cart, then ask 'Aur kuch chahiye?'. "
    "(4) If they name another product, CALL search_products again (it auto-limits to the same store now). "
    "(5) When they say nothing more ('bas', 'itna hi', 'nahi'), CALL checkout. "
    "(6) If the user asks about past orders or cart history ('pichla order', 'cart history', 'mera order "
    "dikhao'), CALL get_order_history and tell the latest one or two in ONE short Hinglish line. "
    "(7) Never invent prices — always use search_products. Everything from one store only. Keep every reply tiny."
)


def _config() -> types.LiveConnectConfig:
    return types.LiveConnectConfig(
        response_modalities=["AUDIO"],
        system_instruction=_SYSTEM,
        tools=_TOOLS,
        input_audio_transcription=types.AudioTranscriptionConfig(),
        output_audio_transcription=types.AudioTranscriptionConfig(),
        realtime_input_config=types.RealtimeInputConfig(
            automatic_activity_detection=types.AutomaticActivityDetection(
                silence_duration_ms=800,
            ),
        ),
    )


async def _do_search(query: str, budget: int | None, only: set[str] | None) -> list:
    intent = Intent(product=(query or "").strip(), budget_inr=budget)
    try:
        return await orchestrator._gather_quotes(intent, only=only)
    except Exception as e:  # a search failure must not kill the call
        print(f"[live search] {e}")
        return []


async def bridge(ws) -> None:
    """Relay a FastAPI WebSocket <-> a Gemini Live session until either side closes."""
    client = genai.Client(api_key=os.environ["GOOGLE_API_KEY"])

    # ---- per-session cart state ----
    cart: list[dict] = []            # [{query, store(id), name, price_inr}]
    state: dict = {"chosen_store": None}   # merchant id, e.g. "zepto" — locks after first add

    def _cart_total() -> int:
        return sum(int(i.get("price_inr") or 0) for i in cart)

    async with client.aio.live.connect(model=LIVE_MODEL, config=_config()) as session:

        async def uplink() -> None:
            """App mic/text → Gemini."""
            audio_total = 0
            audio_chunks = 0
            try:
                while True:
                    msg = await ws.receive()
                    if msg.get("type") == "websocket.disconnect":
                        return
                    data = msg.get("bytes")
                    if data is not None:
                        audio_total += len(data)
                        audio_chunks += 1
                        if audio_chunks % 25 == 0:  # ~ every 25 mic frames
                            print(f"[live mic] {audio_chunks} chunks, {audio_total} bytes received from app")
                        await session.send_realtime_input(
                            audio=types.Blob(data=data, mime_type=f"audio/pcm;rate={INPUT_RATE}")
                        )
                        continue
                    text = msg.get("text")
                    if text is not None:
                        try:
                            payload = json.loads(text)
                        except (ValueError, TypeError):
                            continue
                        kind = payload.get("type")
                        if kind == "hello":
                            name = (payload.get("name") or "").strip()
                            who = f" {name}" if name else ""
                            await session.send_client_content(
                                turns={"role": "user", "parts": [{"text": (
                                    f"[system] The call just connected. Greet the user{who} warmly in ONE short "
                                    f"Hinglish line and ask what they want to shop for today "
                                    f"(e.g. 'Namaste{who}! Aaj kya chahiye?'). Do NOT call any tool yet."
                                )}]},
                                turn_complete=True,
                            )
                        elif kind == "text" and payload.get("text"):
                            await session.send_client_content(
                                turns={"role": "user", "parts": [{"text": payload["text"]}]},
                                turn_complete=True,
                            )
                        elif kind == "end":
                            return
            except Exception as e:  # noqa: BLE001
                print(f"[live uplink] {type(e).__name__}: {e}")

        async def _search(fc) -> types.FunctionResponse:
            args = dict(fc.args or {})
            only = {state["chosen_store"]} if state["chosen_store"] else None
            scope = state["chosen_store"] or "all stores"
            print(f"[live tool] search_products({args}) scope={scope}")
            quotes = await _do_search(args.get("query", ""), args.get("budget_inr"), only)
            print(f"[live tool] -> {len(quotes)} quotes: "
                  + ", ".join(f"{q.store} ₹{q.price_inr}" for q in quotes[:4]))
            await ws.send_text(json.dumps(
                {"type": "quotes", "quotes": [q.model_dump() for q in quotes]}
            ))
            summary = "; ".join(
                f"{q.store}: {q.product_name} ₹{q.price_inr} ({q.delivery})" for q in quotes[:4]
            ) or "kuch nahi mila"
            return types.FunctionResponse(id=fc.id, name=fc.name, response={"result": summary})

        async def _add_to_cart(fc) -> types.FunctionResponse:
            args = dict(fc.args or {})
            mid = registry.resolve_store_id(args.get("store", "")) or state["chosen_store"]
            if mid is None:
                return types.FunctionResponse(id=fc.id, name=fc.name, response={
                    "result": "Store samajh nahi aaya — kaunse store se lena hai?"})
            # same-store lock
            if state["chosen_store"] and mid != state["chosen_store"]:
                locked = registry.by_id(state["chosen_store"])
                locked_name = locked.name if locked else state["chosen_store"]
                return types.FunctionResponse(id=fc.id, name=fc.name, response={
                    "result": f"Ek hi store se order ho sakta hai. Baaki {locked_name} se hi lena hoga."})
            if state["chosen_store"] is None:
                state["chosen_store"] = mid   # LOCK to this store for the rest of the cart
            item = {
                "query": (args.get("query") or "").strip(),
                "store": mid,
                "name": args.get("name") or args.get("query") or "Item",
                "price_inr": int(args.get("price_inr") or 0),
            }
            cart.append(item)
            print(f"[live tool] add_to_cart -> {item['name']} @ {mid}; cart={len(cart)} total ₹{_cart_total()}")
            await ws.send_text(json.dumps({
                "type": "cart", "items": cart, "total_inr": _cart_total(), "store": state["chosen_store"],
            }))
            return types.FunctionResponse(id=fc.id, name=fc.name, response={
                "result": f"Cart mein daal diya ({len(cart)} item, total ₹{_cart_total()}). Aur kuch chahiye?"})

        async def _checkout(fc) -> types.FunctionResponse:
            total = _cart_total()
            print(f"[live tool] checkout -> {len(cart)} items, ₹{total}, store={state['chosen_store']}")
            await ws.send_text(json.dumps({
                "type": "checkout", "store": state["chosen_store"], "items": cart, "total_inr": total,
            }))
            if not cart:
                return types.FunctionResponse(id=fc.id, name=fc.name, response={
                    "result": "Cart khaali hai — pehle kuch chuniye."})
            return types.FunctionResponse(id=fc.id, name=fc.name, response={
                "result": f"Theek hai! {len(cart)} item, total ₹{total}. Address aur payment pe le chalta hoon."})

        async def _order_history(fc) -> types.FunctionResponse:
            items = orchestrator.orders().get("orders", [])
            print(f"[live tool] get_order_history -> {len(items)} orders")
            await ws.send_text(json.dumps({"type": "orders", "orders": items}))
            if not items:
                return types.FunctionResponse(id=fc.id, name=fc.name, response={
                    "result": "Abhi tak koi pichla order nahi hai."})
            summary = "; ".join(
                f"{o.get('product', 'item')} — {o.get('store', '')} ₹{o.get('price_inr', 0)} ({o.get('date', '')})"
                for o in items[:5]
            )
            return types.FunctionResponse(id=fc.id, name=fc.name, response={
                "result": f"{len(items)} pichle order: {summary}"})

        _DISPATCH = {"search_products": _search, "add_to_cart": _add_to_cart,
                     "checkout": _checkout, "get_order_history": _order_history}

        async def _handle(r) -> None:
            if r.data:
                await ws.send_bytes(r.data)

            sc = r.server_content
            if sc:
                it = getattr(sc, "input_transcription", None)
                if it and it.text:
                    print(f"[live user] {it.text!r}")
                    await ws.send_text(json.dumps({"type": "transcript", "role": "user", "text": it.text}))
                ot = getattr(sc, "output_transcription", None)
                if ot and ot.text:
                    await ws.send_text(json.dumps({"type": "transcript", "role": "agent", "text": ot.text}))
                if getattr(sc, "turn_complete", False):
                    print("[live] turn_complete")
                    await ws.send_text(json.dumps({"type": "turn_complete"}))

            if r.tool_call:
                responses = []
                for fc in r.tool_call.function_calls:
                    handler = _DISPATCH.get(fc.name)
                    if handler is None:
                        print(f"[live tool] unknown tool {fc.name!r}")
                        continue
                    responses.append(await handler(fc))
                if responses:
                    await session.send_tool_response(function_responses=responses)

        async def downlink() -> None:
            """Gemini → app. `session.receive()` completes per TURN, so re-enter it
            in a loop to keep the whole multi-turn conversation alive."""
            try:
                while True:
                    async for r in session.receive():
                        await _handle(r)
            except Exception as e:  # noqa: BLE001 — connection closed / session end
                print(f"[live downlink] {type(e).__name__}: {e}")

        up = asyncio.create_task(uplink())
        down = asyncio.create_task(downlink())
        done, pending = await asyncio.wait({up, down}, return_when=asyncio.FIRST_COMPLETED)
        for t in pending:
            t.cancel()
