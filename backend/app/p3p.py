"""Pine Labs P3P agentic payment (§ edge #1).

Model: the user approves a UPI mandate ONCE (a spend cap); after that the agent
captures payments autonomously within that cap — no per-transaction human auth.
This is India's first live agentic UPI rail (P3P + Grantex delegated identity).

Modes:
- **live**  — real P3P via the Node `p3p-server-sdk` sidecar (set P3P_SIDECAR_URL) +
  Grantex agent identity (GRANTEX_AGENT_ID / GRANTEX_API_KEY).
- **stub**  — default; simulates mandate + capture with the exact P3P shape so the
  flow + UI are demoable before Pine Labs sandbox creds arrive. Flip by setting the env.
"""

from __future__ import annotations

import os
import time
import uuid

import httpx

P3P_ENABLED = os.environ.get("P3P_ENABLED", "true").lower() == "true"
SIDECAR_URL = os.environ.get("P3P_SIDECAR_URL")  # e.g. http://localhost:5099
GRANTEX_AGENT_ID = os.environ.get("GRANTEX_AGENT_ID")
LIVE = bool(SIDECAR_URL and GRANTEX_AGENT_ID)

# In-memory mandates (one per user for the demo).
_MANDATES: dict[str, dict] = {}


def mode() -> str:
    return "live" if LIVE else ("stub" if P3P_ENABLED else "off")


async def create_mandate(mobile: str, max_amount_inr: int, validity_days: int = 30) -> dict:
    """Set up a UPI ReservePay mandate (spend cap). One-time human approval."""
    mid = f"mnd_{uuid.uuid4().hex[:12]}"
    if LIVE:
        async with httpx.AsyncClient(timeout=30) as c:
            r = await c.post(f"{SIDECAR_URL}/mandate", json={
                "mobile": mobile, "amountPaise": max_amount_inr * 100,
                "validityDays": validity_days, "agentId": GRANTEX_AGENT_ID,
            })
            r.raise_for_status()
            data = r.json()
            mid = data.get("mandateId", mid)
    _MANDATES[mobile] = {
        "mandate_id": mid, "mobile": mobile, "cap_inr": max_amount_inr,
        "spent_inr": 0, "created": time.time(), "rail": "UPI ReservePay (P3P)",
    }
    return {"mandate_id": mid, "cap_inr": max_amount_inr, "mode": mode()}


def mandate_for(mobile: str) -> dict | None:
    return _MANDATES.get(mobile)


def balance(mobile: str) -> int:
    m = _MANDATES.get(mobile)
    return (m["cap_inr"] - m["spent_inr"]) if m else 0


async def capture(mobile: str, amount_inr: int, merchant: str, order_id: str) -> dict:
    """Agent-initiated capture within the mandate cap — no human step (that was the mandate)."""
    m = _MANDATES.get(mobile)
    if not m:
        return {"status": "no_mandate"}
    if amount_inr > (m["cap_inr"] - m["spent_inr"]):
        return {"status": "over_cap", "remaining_inr": m["cap_inr"] - m["spent_inr"]}

    receipt_id = f"p3p_{uuid.uuid4().hex[:10]}"
    if LIVE:
        async with httpx.AsyncClient(timeout=30) as c:
            r = await c.post(f"{SIDECAR_URL}/capture", json={
                "mandateId": m["mandate_id"], "amountPaise": amount_inr * 100,
                "merchant": merchant, "orderId": order_id, "agentId": GRANTEX_AGENT_ID,
            })
            r.raise_for_status()
            receipt_id = r.json().get("receiptId", receipt_id)

    m["spent_inr"] += amount_inr
    return {
        "status": "captured", "receipt_id": receipt_id, "rail": m["rail"],
        "mandate_id": m["mandate_id"], "amount_inr": amount_inr,
        "remaining_inr": m["cap_inr"] - m["spent_inr"], "mode": mode(),
        "grantex_agent": GRANTEX_AGENT_ID or "grantex:stub-agent",
    }
