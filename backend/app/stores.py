"""Two mock store agents with ₹ catalogs (§9.3)."""

from __future__ import annotations

from .models import Intent, Quote

EARBUDS_IMG = "https://m.media-amazon.com/images/I/61u48FEssdL._SX679_.jpg"

# Seed catalog: key -> { store -> fields }. Canonical demo = "wireless earbuds".
CATALOG: dict[str, dict[str, dict]] = {
    "wireless earbuds": {
        "Store A": {"product_name": "boAt Airdopes 141 – Wireless Earbuds", "price_inr": 1950,
                    "delivery": "kal", "in_stock": True, "image_url": EARBUDS_IMG},
        "Store B": {"product_name": "boAt Airdopes 141 – Wireless Earbuds", "price_inr": 1800,
                    "delivery": "aaj", "in_stock": True, "image_url": EARBUDS_IMG},
    },
    "atta 5kg": {
        "Store A": {"product_name": "Aashirvaad Atta 5kg", "price_inr": 260, "delivery": "aaj", "in_stock": True},
        "Store B": {"product_name": "Aashirvaad Atta 5kg", "price_inr": 245, "delivery": "aaj", "in_stock": True},
    },
    "phone charger": {
        "Store A": {"product_name": "65W USB-C Charger", "price_inr": 499, "delivery": "kal", "in_stock": True},
        "Store B": {"product_name": "65W USB-C Charger", "price_inr": 549, "delivery": "aaj", "in_stock": True},
    },
}

_ALIASES = {
    "earbuds": "wireless earbuds", "earphones": "wireless earbuds", "tws": "wireless earbuds",
    "airdopes": "wireless earbuds", "headphones": "wireless earbuds",
    "atta": "atta 5kg", "flour": "atta 5kg", "wheat": "atta 5kg",
    "charger": "phone charger", "adapter": "phone charger",
}


def _match_key(intent: Intent) -> str | None:
    p = (intent.product or "").lower()
    if p in CATALOG:
        return p
    for token, key in _ALIASES.items():
        if token in p:
            return key
    # fall back to the canonical demo product so the flow always has something
    return "wireless earbuds" if intent.category in ("audio", "") else None


def get_quote(store: str, intent: Intent) -> Quote | None:
    key = _match_key(intent)
    if not key:
        return None
    row = CATALOG.get(key, {}).get(store)
    if not row or not row.get("in_stock", True):
        return None
    return Quote(store=store, **row)


def all_quotes(intent: Intent) -> list[Quote]:
    return [q for q in (get_quote("Store A", intent), get_quote("Store B", intent)) if q is not None]
