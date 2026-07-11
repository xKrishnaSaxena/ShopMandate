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


class MockMerchant(Merchant):
    real = False

    def __init__(self, id: str, name: str, catalog: dict[str, dict]) -> None:
        self.id = id
        self.name = name
        self.catalog = catalog  # query-key -> {product_name, price_inr, delivery, image_url}

    def connected(self) -> bool:
        return True

    async def search(self, query: str, budget: int | None) -> Quote | None:
        row = self.catalog.get(query.lower())
        if not row:
            return None
        return Quote(store=self.name, **row)


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

    def _price(self, p: dict) -> float | None:
        v = _first(p, ["sellingPrice", "price", "finalPrice", "offerPrice", "mrp"])
        if not isinstance(v, (int, float)):
            try:
                v = float(str(v).replace("₹", "").replace(",", "").strip())
            except (ValueError, TypeError):
                return None
        return (v / 100) if self.price_in_paise else float(v)

    async def _ensure_address(self, tools: list[str]) -> None:
        addr_tool = _find_tool(tools, ["list_saved_addresses", "get_addresses", "list_addresses"])
        sel_tool = _find_tool(tools, ["select_saved_address", "select_address"])
        if not addr_tool:
            return
        addrs = _as_list(await self.mcp.call(addr_tool, {}), ["addresses", "data", "result"])
        if addrs and sel_tool:
            aid = _first(addrs[0], ["id", "addressId", "address_id"])
            if aid:
                await self.mcp.call(sel_tool, {"addressId": aid})

    async def search(self, query: str, budget: int | None) -> Quote | None:
        if not self.connected():
            return None  # not linked yet — skip (connect explicitly via /connect, no OAuth popup mid-search)
        tools = await self.tools()
        await self._ensure_address(tools)
        search_tool = _find_tool(tools, ["search_products", "search_multiple", "search"],
                                 avoid=["restaurant", "menu"])
        if not search_tool:
            return None
        res = await self.mcp.call(search_tool, {"query": query})
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
