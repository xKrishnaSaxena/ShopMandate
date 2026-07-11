"""Gemini Live bridge — real-time voice shopping agent.

A FastAPI WebSocket relays audio between the Android app and a Gemini Live session
(`gemini-2.5-flash-native-audio-latest`). The Live agent can call `search_products`,
which runs our real merchant search (Zepto / Swiggy Instamart / mocks); the resulting
quotes are pushed to the app so product cards appear on screen while the agent speaks.

Wire protocol (app <-> backend):
  app -> backend : binary frames  = PCM16 mono 16kHz mic audio
                   text  frames  = {"type":"text","text":...} | {"type":"end"}
  backend -> app : binary frames  = PCM16 mono 24kHz agent audio (play as received)
                   text  frames  = {"type":"transcript","role":"user"|"agent","text":...}
                                   {"type":"quotes","quotes":[...]}
                                   {"type":"turn_complete"} | {"type":"error","detail":...}
"""

from __future__ import annotations

import asyncio
import json
import os

from google import genai
from google.genai import types

from . import orchestrator
from .models import Intent

LIVE_MODEL = os.environ.get("LIVE_MODEL", "gemini-2.5-flash-native-audio-latest")
INPUT_RATE = 16000   # app mic → Gemini
OUTPUT_RATE = 24000  # Gemini → app speaker

_SEARCH_TOOL = {
    "function_declarations": [
        {
            "name": "search_products",
            "description": (
                "Search every connected Indian quick-commerce store (Zepto, Swiggy Instamart, "
                "Blinkit and more) for a product and get LIVE prices. Call this the moment the "
                "user names something to buy."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "product to search, e.g. 'wireless earbuds'"},
                    "budget_inr": {"type": "integer", "description": "optional max budget in rupees"},
                },
                "required": ["query"],
            },
        }
    ]
}

_SYSTEM = (
    "You are ShopMandate's live shopping voice agent for Indian users. This is a fast phone call — "
    "be extremely brief. Rules: "
    "(1) The MOMENT the user names a product, say a 2-word filler like 'Dekhta hoon' and immediately "
    "CALL search_products — do not wait. "
    "(2) After results, reply in ONE short Hinglish sentence (max ~12 words): only the single cheapest "
    "deal — store aur price. Do NOT read out a list. "
    "(3) End with a 3-word nudge like 'Order kar doon?'. "
    "(4) Never invent prices — always use search_products. Keep every reply tiny; speed matters more than detail."
)


def _config() -> types.LiveConnectConfig:
    # Default automatic VAD — reliably detects the user's speech (START_SENSITIVITY_LOW was
    # so strict that even loud speech never registered). Keep only a slightly longer silence
    # window so a brief mid-sentence pause doesn't cut the turn off in a noisy room.
    return types.LiveConnectConfig(
        response_modalities=["AUDIO"],
        system_instruction=_SYSTEM,
        tools=[_SEARCH_TOOL],
        input_audio_transcription=types.AudioTranscriptionConfig(),
        output_audio_transcription=types.AudioTranscriptionConfig(),
        realtime_input_config=types.RealtimeInputConfig(
            automatic_activity_detection=types.AutomaticActivityDetection(
                silence_duration_ms=800,
            ),
        ),
    )


async def _do_search(query: str, budget: int | None) -> list:
    intent = Intent(product=(query or "").strip(), budget_inr=budget)
    try:
        return await orchestrator._gather_quotes(intent)
    except Exception as e:  # a search failure must not kill the call
        print(f"[live search] {e}")
        return []


async def bridge(ws) -> None:
    """Relay a FastAPI WebSocket <-> a Gemini Live session until either side closes."""
    client = genai.Client(api_key=os.environ["GOOGLE_API_KEY"])
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
                    if fc.name == "search_products":
                        args = dict(fc.args or {})
                        print(f"[live tool] search_products({args})")
                        quotes = await _do_search(args.get("query", ""), args.get("budget_inr"))
                        print(f"[live tool] -> {len(quotes)} quotes: "
                              + ", ".join(f"{q.store} ₹{q.price_inr}" for q in quotes[:4]))
                        await ws.send_text(json.dumps(
                            {"type": "quotes", "quotes": [q.model_dump() for q in quotes]}
                        ))
                        summary = "; ".join(f"{q.store} ₹{q.price_inr} ({q.delivery})" for q in quotes[:4]) \
                            or "kuch nahi mila"
                        responses.append(types.FunctionResponse(
                            id=fc.id, name=fc.name, response={"result": summary}
                        ))
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
