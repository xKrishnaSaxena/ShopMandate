"""Mock UPI payment (§9.4) — framed as UPI, no real money."""

from __future__ import annotations

import asyncio
import random

from .models import Cart, Receipt


async def charge(cart: Cart, upi_app: str, audit_chain: dict[str, str]) -> Receipt:
    await asyncio.sleep(1.5)  # simulated authorization
    order_id = f"SM-{random.randint(1000, 9999)}"
    return Receipt(
        order_id=order_id,
        item=cart.item,
        price_inr=cart.price_inr,
        store=cart.store,
        delivery=cart.delivery,
        paid_via=f"UPI · {upi_app}",
        audit_chain=audit_chain,
    )
