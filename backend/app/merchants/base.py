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

    async def search_many(self, query: str, budget: int | None, limit: int = 5) -> list[Quote]:
        """Return up to `limit` product options (cheapest first). Default: wrap single search()."""
        q = await self.search(query, budget)
        return [q] if q else []


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
        self._address_id: str | None = None
        self._pass_address_inline = False  # true when search needs addressId inline (Instamart)
        self._address_ready = False  # resolve/select the address only once (latency)

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
        v = _first(p, ["sellingPrice", "finalPrice", "offerPrice", "price", "mrp"])
        # Some MCPs (Swiggy Instamart) nest the price: {"price": {"offerPrice": 212, "mrp": 232}}
        if isinstance(v, dict):
            v = _first(v, ["offerPrice", "sellingPrice", "finalPrice", "price", "mrp"])
        if not isinstance(v, (int, float)):
            try:
                v = float(str(v).replace("₹", "").replace(",", "").strip())
            except (ValueError, TypeError):
                return None
        return (v / 100) if self.price_in_paise else float(v)

    @staticmethod
    def _expand(products: list[dict]) -> list[dict]:
        """Flatten products that carry a `variations`/`variants` list (Instamart) into
        one candidate per variation, so each priced SKU is comparable."""
        out: list[dict] = []
        for p in products:
            variants = p.get("variations") or p.get("variants")
            if isinstance(variants, list) and variants:
                for v in variants:
                    if isinstance(v, dict):
                        out.append({**p, **v})  # variation fields (price/image/name) win
            else:
                out.append(p)
        return out

    async def _ensure_address(self, tools: list[str]) -> None:
        if self._address_ready:
            return  # already resolved once — skip the extra round-trip on every search
        addr_tool = _find_tool(tools, ["list_saved_addresses", "get_addresses", "list_addresses"])
        sel_tool = _find_tool(tools, ["select_saved_address", "select_address"])
        if not addr_tool:
            return
        addrs = _as_list(await self.mcp.call(addr_tool, {}), ["addresses", "data", "result", "savedAddresses"])
        if not addrs:
            return
        aid = _first(addrs[0], ["id", "addressId", "address_id"])
        self._address_id = str(aid) if aid else None
        if sel_tool and aid:
            await self.mcp.call(sel_tool, {"addressId": aid})  # server-side selection (Zepto)
        else:
            self._pass_address_inline = True  # no select tool → pass addressId in search (Instamart)
        self._address_ready = True

    def _to_quote(self, p: dict, price: float, query: str) -> Quote:
        name = str(_first(p, ["name", "product_name", "displayName", "title"]) or query)
        img = _first(p, ["imageUrl", "image", "img"])
        return Quote(store=self.name, product_name=name, price_inr=int(round(price)),
                     delivery=self.delivery, in_stock=True, image_url=img if isinstance(img, str) else None)

    async def search_many(self, query: str, budget: int | None, limit: int = 5) -> list[Quote]:
        if not self.connected():
            return []  # not linked yet — skip (connect explicitly via /connect, no OAuth popup mid-search)
        tools = await self.tools()
        await self._ensure_address(tools)
        search_tool = _find_tool(tools, ["search_products", "search_multiple", "search"],
                                 avoid=["restaurant", "menu"])
        if not search_tool:
            return []
        args: dict = {"query": query}
        if self._pass_address_inline and self._address_id:
            args["addressId"] = self._address_id
        res = await self.mcp.call(search_tool, args)
        products = self._expand(_as_list(res, ["products", "results", "items", "data"]))
        priced: list[tuple[dict, float]] = []
        for p in products:
            price = self._price(p)
            if price is None or price <= 0:
                continue
            if budget is not None and price > budget:
                continue
            priced.append((p, price))
        priced.sort(key=lambda x: x[1])
        # de-dup by product name so we don't show the same item twice
        seen: set[str] = set()
        out: list[Quote] = []
        for p, price in priced:
            q = self._to_quote(p, price, query)
            key = q.product_name.lower().strip()
            if key in seen:
                continue
            seen.add(key)
            out.append(q)
            if len(out) >= limit:
                break
        return out

    async def search(self, query: str, budget: int | None) -> Quote | None:
        many = await self.search_many(query, budget, limit=1)
        return many[0] if many else None
