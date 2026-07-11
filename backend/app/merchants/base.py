"""Merchant adapters — a pluggable interface over real MCP merchants and mock ones.

Real merchants (Zepto, Swiggy Instamart) are quick-commerce MCPs: ensure an address,
search products, map the cheapest in-budget hit to a Quote. Mock merchants use a catalog.
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from ..models import Quote
from .mcp_client import MCPMerchant


def _as_list(data: object, keys: list[str]) -> list[dict]:
    if isinstance(data, list):
        return [x for x in data if isinstance(x, dict)]
    if isinstance(data, dict):
        for k in keys:
            v = data.get(k)
            if isinstance(v, list):
                return [x for x in v if isinstance(x, dict)]
    return []


def _first(d: dict, keys: list[str]):
    for k in keys:
        v = d.get(k)
        if v not in (None, ""):
            return v
    return None


def _find_tool(tools: list[str], wants: list[str], avoid: list[str] | None = None) -> str | None:
    avoid = avoid or []
    for w in wants:
        for t in tools:
            if w in t and not any(a in t for a in avoid):
                return t
    return None


class Merchant(ABC):
    id: str
    name: str
    real: bool

    @abstractmethod
    def connected(self) -> bool: ...

    @abstractmethod
    async def search(self, query: str, budget: int | None) -> Quote | None: ...


_ALIASES = {
    "earbuds": "wireless earbuds", "earphones": "wireless earbuds", "tws": "wireless earbuds",
    "airdopes": "wireless earbuds", "headphone": "wireless earbuds", "buds": "wireless earbuds",
    "atta": "atta 5kg", "flour": "atta 5kg", "wheat": "atta 5kg",
    "charger": "phone charger", "adapter": "phone charger",
}


class MockMerchant(Merchant):
    real = False

    def __init__(self, id: str, name: str, catalog: dict[str, dict]) -> None:
        self.id = id
        self.name = name
        self.catalog = catalog  # query-key -> {product_name, price_inr, delivery, image_url}

    def connected(self) -> bool:
        return True

    def _key(self, query: str) -> str | None:
        q = query.lower().strip()
        if q in self.catalog:
            return q
        for token, key in _ALIASES.items():
            if token in q and key in self.catalog:
                return key
        for key in self.catalog:  # loose substring overlap
            if key in q or any(w in q for w in key.split()):
                return key
        return None

    async def search(self, query: str, budget: int | None) -> Quote | None:
        key = self._key(query)
        row = self.catalog.get(key) if key else None
        return Quote(store=self.name, **row) if row else None


class RealMerchant(Merchant):
    """Generic quick-commerce MCP adapter (Zepto / Swiggy Instamart)."""

    real = True

    def __init__(self, id: str, name: str, server_url: str, scope: str,
                 price_in_paise: bool = True, delivery: str = "~10 min") -> None:
        self.id = id
        self.name = name
        self.mcp = MCPMerchant(id, server_url, scope)
        self.price_in_paise = price_in_paise
        self.delivery = delivery
        self._tools: list[str] | None = None

    def connected(self) -> bool:
        return self.mcp.connected()

    async def tools(self) -> list[str]:
        if self._tools is None:
            self._tools = await self.mcp.list_tools()
        return self._tools

    async def connect(self) -> bool:
        """Trigger the OAuth consent (browser mobile+OTP) and cache the token."""
        await self.tools()  # list_tools forces the OAuth flow if no token yet
        return self.connected()

    async def tool_defs(self) -> list[dict]:
        """Full tool definitions (for discovering cart/checkout/order/payment tools)."""
        if not self.connected():
            return []
        return await self.mcp.list_tool_defs()

    def _price(self, p: dict) -> float | None:
        v = _first(p, ["sellingPrice", "price", "finalPrice", "offerPrice", "mrp"])
        if not isinstance(v, (int, float)):
            try:
                v = float(str(v).replace("₹", "").replace(",", "").strip())
            except (ValueError, TypeError):
                return None
        return (v / 100) if self.price_in_paise else float(v)

    async def _ensure_address(self, tools: list[str]) -> str | None:
        """Pick the first saved address. Zepto needs it *selected* (select_saved_address);
        Instamart needs the id passed into search_products. Returns the address id (or None)."""
        addr_tool = _find_tool(tools, ["list_saved_addresses", "get_addresses", "list_addresses"])
        if not addr_tool:
            return None
        addrs = _as_list(await self.mcp.call(addr_tool, {}), ["addresses", "data", "result"])
        if not addrs:
            return None
        aid = _first(addrs[0], ["id", "addressId", "address_id"])
        sel_tool = _find_tool(tools, ["select_saved_address", "select_address"])
        if aid and sel_tool:
            await self.mcp.call(sel_tool, {"addressId": aid})
        return aid if isinstance(aid, str) else None

    async def search(self, query: str, budget: int | None) -> Quote | None:
        if not self.connected():
            return None  # not linked yet — skip (connect explicitly via /connect, no OAuth popup mid-search)
        tools = await self.tools()
        aid = await self._ensure_address(tools)
        search_tool = _find_tool(tools, ["search_products", "search_multiple", "search"],
                                 avoid=["restaurant", "menu"])
        if not search_tool:
            return None
        # Instamart's search_products REQUIRES the addressId inline (Zepto takes just the query,
        # and selects the address separately) — without it Instamart returns 0 products.
        args = {"query": query}
        if aid and self.id == "instamart":
            args["addressId"] = aid
        res = await self.mcp.call(search_tool, args)
        products = _as_list(res, ["products", "results", "items", "data"])
        best: tuple[dict, float] | None = None
        for p in products:
            price = self._price(p)
            if price is None or price <= 0:
                continue
            if budget is not None and price > budget:
                continue
            if best is None or price < best[1]:
                best = (p, price)
        if best is None:
            return None
        p, price = best
        name = str(_first(p, ["name", "product_name", "displayName", "title"]) or query)
        img = _first(p, ["imageUrl", "image", "img"])
        return Quote(store=self.name, product_name=name, price_inr=int(round(price)),
                     delivery=self.delivery, in_stock=True, image_url=img if isinstance(img, str) else None)
