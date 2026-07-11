"""Enabled merchants: real MCP quick-commerce (Zepto, Swiggy Instamart) + mock stores.

Search gathers a quote from every merchant that can fulfil the query (real ones only
respond once connected); negotiation then compares across all of them.
"""

from __future__ import annotations

from .base import Merchant, MockMerchant, RealMerchant

EARBUDS_IMG = "https://m.media-amazon.com/images/I/61u48FEssdL._SX679_.jpg"

# Mock catalogs keyed by query (canonical demo + a couple of quick-commerce items).
_MOCK_A = {
    "wireless earbuds": {"product_name": "boAt Airdopes 141 – Wireless Earbuds", "price_inr": 1950,
                         "delivery": "kal", "image_url": EARBUDS_IMG},
    "atta 5kg": {"product_name": "Aashirvaad Atta 5kg", "price_inr": 260, "delivery": "aaj"},
}
_MOCK_B = {
    "wireless earbuds": {"product_name": "boAt Airdopes 141 – Wireless Earbuds", "price_inr": 1800,
                         "delivery": "aaj", "image_url": EARBUDS_IMG},
    "atta 5kg": {"product_name": "Aashirvaad Atta 5kg", "price_inr": 245, "delivery": "aaj"},
}
_MOCK_BLINKIT = {
    "wireless earbuds": {"product_name": "boAt Airdopes 141 – Wireless Earbuds", "price_inr": 1870,
                         "delivery": "10 min", "image_url": EARBUDS_IMG},
}


def _build() -> list[Merchant]:
    return [
        RealMerchant("zepto", "Zepto", "https://mcp.zepto.co.in/mcp",
                     "tools:read tools:write", price_in_paise=True, delivery="~10 min"),
        RealMerchant("instamart", "Swiggy Instamart", "https://mcp.swiggy.com/im",
                     "mcp:tools mcp:resources", price_in_paise=False, delivery="~15 min"),
        MockMerchant("blinkit", "Blinkit", _MOCK_BLINKIT),
        MockMerchant("store_a", "Store A", _MOCK_A),
        MockMerchant("store_b", "Store B", _MOCK_B),
    ]


MERCHANTS: list[Merchant] = _build()


def by_id(mid: str) -> Merchant | None:
    return next((m for m in MERCHANTS if m.id == mid), None)


def status() -> list[dict]:
    return [{"id": m.id, "name": m.name, "real": m.real, "connected": m.connected()} for m in MERCHANTS]
