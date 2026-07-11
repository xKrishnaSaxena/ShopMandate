"""Google ADK Managed Agents (§ PS2 theme) — a Shopping Agent that autonomously
orchestrates Store-Agent tools (search across real + mock merchants) then negotiates.

Tools do the real work and persist results to ADK session state; the endpoint reads
the decision back from state, so the §8 REST contract stays deterministic + green.
"""

from __future__ import annotations

import asyncio

from google.adk.agents import LlmAgent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.adk.tools.tool_context import ToolContext
from google.genai import types

from . import negotiation
from .merchants import registry
from .models import Intent, Quote

MODEL = "gemini-flash-latest"
APP = "shopmandate"


async def search_stores(query: str, tool_context: ToolContext) -> str:
    """Search every store (real MCP + mock) for `query` and record their quotes."""
    budget = tool_context.state.get("budget")

    async def one(m):
        try:
            return await m.search(query, budget)
        except Exception as e:  # a flaky store must not sink the round
            print(f"[store {m.id}] {e}")
            return None

    quotes = [q for q in await asyncio.gather(*(one(m) for m in registry.MERCHANTS)) if q]
    tool_context.state["quotes"] = [q.model_dump() for q in quotes]
    if not quotes:
        return "No store has this in stock."
    return "Quotes — " + "; ".join(f"{q.store}: ₹{q.price_inr} ({q.delivery})" for q in quotes)


async def negotiate(tool_context: ToolContext) -> str:
    """Compare the recorded quotes, run one haggle round, and pick the winner."""
    raw = tool_context.state.get("quotes") or []
    quotes = [Quote(**q) for q in raw]
    dec = negotiation.decide(Intent(budget_inr=tool_context.state.get("budget")), quotes)
    tool_context.state["decision"] = {
        "status": dec.status,
        "quotes": [q.model_dump() for q in dec.quotes],
        "winner": dec.winner.model_dump() if dec.winner else None,
        "cart": dec.cart.model_dump() if dec.cart else None,
        "steps": dec.steps,
        "clarifying_question": dec.clarifying_question,
    }
    if dec.winner:
        return f"Winner {dec.winner.store} at ₹{dec.winner.price_inr} — {dec.winner.why}"
    return dec.status


shopping_agent = LlmAgent(
    name="shopping_agent",
    model=MODEL,
    description="Finds a product across stores, makes them compete, and picks the best deal.",
    instruction=(
        "You are ShopMandate's Shopping Agent for Indian users. "
        "Given a product + budget you MUST: (1) call search_stores(query) once, "
        "(2) then call negotiate() once. "
        "Finally reply in ONE short Hinglish line: winning store, ₹price, and why it won."
    ),
    tools=[search_stores, negotiate],
)

_sessions = InMemorySessionService()
_runner = Runner(agent=shopping_agent, app_name=APP, session_service=_sessions)


async def run_shopping(intent: Intent) -> dict | None:
    """Drive the ADK Shopping Agent for one turn; return the recorded decision dict."""
    uid = "user"
    sid = f"shop-{abs(hash((intent.product, intent.budget_inr)))}"
    await _sessions.create_session(
        app_name=APP, user_id=uid, session_id=sid,
        state={"budget": intent.budget_inr, "product": intent.product},
    )
    msg = types.Content(
        role="user",
        parts=[types.Part(text=f"Buy: {intent.product or 'the requested item'}; budget ₹{intent.budget_inr or 'any'}")],
    )
    async for _ in _runner.run_async(user_id=uid, session_id=sid, new_message=msg):
        pass
    sess = await _sessions.get_session(app_name=APP, user_id=uid, session_id=sid)
    return (sess.state or {}).get("decision") if sess else None
