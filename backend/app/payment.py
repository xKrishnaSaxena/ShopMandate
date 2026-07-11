"""UPI payment record (§9.4).

No money is moved here — that happens on-device via a real UPI deep link
(upi://pay) in the user's own UPI app, where they authorise with their PIN.
This just mints the order id + receipt once the app reports the UPI txn done.
"""

from __future__ import annotations

import random

from .models import Cart, Receipt


async def charge(
    cart: Cart,
    upi_app: str,
    audit_chain: dict[str, str],
    upi_txn_id: str | None = None,
) -> Receipt:
    order_id = f"SM-{random.randint(1000, 9999)}"
    paid_via = f"UPI · {upi_app}" + (f" · {upi_txn_id}" if upi_txn_id else "")
    return Receipt(
        order_id=order_id,
        item=cart.item,
        price_inr=cart.price_inr,
        store=cart.store,
        delivery=cart.delivery,
        paid_via=paid_via,
        audit_chain=audit_chain,
    )
