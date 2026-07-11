"""Real order placement over merchant MCPs (Zepto + Swiggy Instamart).

Verified tool pipelines (from live tool discovery):

Zepto:
    search_products → select_saved_address → update_cart(deviceId, cartItems)
    → create_online_payment_order / create_upi_reserve_pay_order(confirmOrder=False)  [preview]
    → …(confirmOrder=True)  [places the REAL order, returns a payment link]
    → check_payment_status(orderId)

Instamart:
    get_addresses → search_products(addressId, query) → update_cart(selectedAddressId, items)
    → get_payment_options → checkout(addressId, paymentMethod, intentApp)  [places + returns pay ref]
    → check_payment_status(paasId, orderId)

`confirm=False` never charges — it returns the amount to pay. `confirm=True` places the real
order and returns whatever payment reference the merchant issues (link / UPI intent), which the
app then opens so the user authorises with their own UPI PIN.
"""

from __future__ import annotations

import json

DEVICE_ID = "shopmandate-app"


def _as_dict(x: object) -> dict:
    return x if isinstance(x, dict) else {}


def _first(d: dict, keys: list[str]):
    for k in keys:
        v = d.get(k)
        if v not in (None, "", []):
            return v
    return None


def _find_payment_ref(result: dict) -> dict:
    """Best-effort scan for a payment link / UPI intent + order id in a confirm response.

    The exact field names aren't documented; we look for common shapes and also return the
    raw result so the caller can see anything we missed on the first live confirm.
    """
    link = _first(result, [
        "paymentLink", "payment_link", "paymentUrl", "payment_url", "link", "url",
        "upiIntent", "upi_intent", "intentUrl", "upiUrl", "shortUrl",
    ])
    order_id = _first(result, ["orderId", "order_id", "id", "paasId", "transactionId"])
    # sometimes nested under "payment" / "data"
    if not link:
        for nest in ("payment", "data", "order", "result"):
            sub = _as_dict(result.get(nest))
            if sub:
                link = link or _first(sub, ["paymentLink", "paymentUrl", "link", "url", "upiIntent", "intentUrl"])
                order_id = order_id or _first(sub, ["orderId", "order_id", "id", "paasId"])
    return {"payment_link": link, "order_id": order_id}


async def list_addresses(m) -> list[dict]:
    """Normalized saved addresses for a merchant: [{id, label, line, lat, lng}]."""
    if m.id == "instamart":
        res = _as_dict(await m.mcp.call("get_addresses", {}))
        raw = res.get("addresses") or []
        return [{
            "id": a.get("id"), "label": _first(a, ["addressTag", "addressCategory"]) or "Address",
            "line": a.get("addressLine"), "lat": a.get("latitude"), "lng": a.get("longitude"),
        } for a in raw if isinstance(a, dict)]
    res = _as_dict(await m.mcp.call("list_saved_addresses", {}))
    raw = res.get("addresses") or []
    return [{
        "id": a.get("id"), "label": a.get("label") or "Address",
        "line": a.get("addressLine"), "lat": a.get("latitude"), "lng": a.get("longitude"),
    } for a in raw if isinstance(a, dict)]


def _pick_address(addrs: list[dict], address_id: str | None) -> dict | None:
    if not addrs:
        return None
    if address_id:
        for a in addrs:
            if a.get("id") == address_id:
                return a
    return addrs[0]


def _best_product(products: list[dict], budget_inr: int | None) -> dict | None:
    best = None
    for p in products:
        if not isinstance(p, dict):
            continue
        price_paise = p.get("price")
        if not isinstance(price_paise, (int, float)) or price_paise <= 0:
            continue
        inr = price_paise / 100
        if budget_inr is not None and inr > budget_inr:
            continue
        if best is None or price_paise < best.get("price", float("inf")):
            best = p
    return best


# ---------------- Zepto ----------------
async def zepto_prepare(m, query: str, budget_inr: int | None, qty: int, address_id: str | None) -> dict:
    """search → select address → add to cart → preview. Returns amount to pay (no charge)."""
    search = _as_dict(await m.mcp.call("search_products", {"query": query}))
    products = search.get("products") or []
    prod = _best_product(products, budget_inr)
    if not prod:
        return {"status": "no_product"}

    addr = _pick_address(await list_addresses(m), address_id)
    if not addr:
        return {"status": "no_address"}
    address_id = addr["id"]
    await m.mcp.call("select_saved_address", {"addressId": address_id})

    await m.mcp.call("update_cart", {
        "deviceId": DEVICE_ID,
        "replaceCart": True,
        "cartItems": [{
            "productVariantId": prod.get("productVariantId") or prod.get("id"),
            "storeProductId": prod.get("storeProductId"),
            "quantity": qty,
            "name": prod.get("name"),
            "price": prod.get("price"),
        }],
    })

    preview = _as_dict(await m.mcp.call("create_online_payment_order", {
        "confirmOrder": False, "userAddressId": address_id,
    }))
    to_pay_paise = preview.get("toPayAmount")
    item_paise = (prod.get("price") or 0) * qty
    delivery_paise = preview.get("deliveryFee") or 0
    return {
        "status": "ready",
        "store": m.name,
        "product": prod.get("name"),
        "qty": qty,
        "item_price_inr": int(round(item_paise / 100)),
        "delivery_fee_inr": int(round(delivery_paise / 100)),
        "to_pay_inr": int(round((to_pay_paise or (item_paise + delivery_paise)) / 100)),
        "address": addr,
        "address_id": address_id,
        "deliverable": preview.get("deliverable"),
        "preview": preview,
    }


async def zepto_confirm(m, address_id: str, rail: str = "online") -> dict:
    """Places the REAL order (confirmOrder=True). `rail`: 'online' (payment link) or 'upi_reserve'."""
    tool = "create_upi_reserve_pay_order" if rail == "upi_reserve" else "create_online_payment_order"
    result = _as_dict(await m.mcp.call(tool, {"confirmOrder": True, "userAddressId": address_id}))
    ref = _find_payment_ref(result)
    status = _as_dict(await m.mcp.call("check_payment_status", {"orderId": ref["order_id"]})) if ref["order_id"] else {}
    return {"status": "placed", "store": m.name, **ref, "payment_status": status, "raw": result}


# ---------------- Instamart ----------------
async def instamart_prepare(m, query: str, budget_inr: int | None, qty: int, address_id: str | None) -> dict:
    addr = _pick_address(await list_addresses(m), address_id)
    if not addr:
        return {"status": "no_address"}
    address_id = addr["id"]

    search = _as_dict(await m.mcp.call("search_products", {"addressId": address_id, "query": query}))
    products = search.get("products") or search.get("results") or search.get("items") or []
    prod = _best_product(products, budget_inr)
    if not prod:
        return {"status": "no_product"}

    await m.mcp.call("update_cart", {
        "selectedAddressId": address_id,
        "items": [{
            "spinId": prod.get("spinId"),
            "skuId": prod.get("skuId"),
            "quantity": qty,
        }],
    })
    cart = _as_dict(await m.mcp.call("get_cart", {}))
    item_paise = (prod.get("price") or 0) * qty
    delivery_paise = _first(cart, ["deliveryFee", "delivery_fee"]) or 0
    total_paise = _first(cart, ["toPayAmount", "grandTotal", "totalAmount"]) or (item_paise + delivery_paise)
    return {
        "status": "ready",
        "store": m.name,
        "product": _first(prod, ["name", "displayName", "productName"]),
        "qty": qty,
        "item_price_inr": int(round(item_paise / 100)) if item_paise else None,
        "delivery_fee_inr": int(round(delivery_paise / 100)) if delivery_paise else None,
        "to_pay_inr": int(round(total_paise / 100)) if total_paise else None,
        "address": addr,
        "address_id": address_id,
        "cart": cart,
    }


async def instamart_confirm(m, address_id: str, intent_app: str = "gpay://upi/") -> dict:
    """Places the REAL Instamart order via checkout (UPI). Returns the pay reference."""
    result = _as_dict(await m.mcp.call("checkout", {
        "addressId": address_id, "paymentMethod": "UPI", "intentApp": intent_app,
    }))
    ref = _find_payment_ref(result)
    status = {}
    if ref["order_id"]:
        status = _as_dict(await m.mcp.call("check_payment_status", {
            "paasId": ref["order_id"], "orderId": ref["order_id"], "addressId": address_id,
        }))
    return {"status": "placed", "store": m.name, **ref, "payment_status": status, "raw": result}


async def payment_status(m, order_id: str) -> dict:
    """Poll the merchant for payment/order status. Best-effort 'paid' detection over the raw blob."""
    if m.id == "instamart":
        res = _as_dict(await m.mcp.call("check_payment_status", {"paasId": order_id, "orderId": order_id}))
    else:
        res = _as_dict(await m.mcp.call("check_payment_status", {"orderId": order_id}))
    blob = json.dumps(res).upper()
    paid = any(k in blob for k in ("PAID", "SUCCESS", "CONFIRMED", "COMPLETED", "DELIVERED"))
    return {"paid": paid, "raw": res}


# ---------------- multi-item cart (Live voice: several items, one store) ----------------
async def zepto_prepare_cart(m, items: list[dict], address_id: str | None) -> dict:
    """Build a REAL Zepto cart from several items (one search each) → single preview."""
    addr = _pick_address(await list_addresses(m), address_id)
    if not addr:
        return {"status": "no_address"}
    address_id = addr["id"]
    await m.mcp.call("select_saved_address", {"addressId": address_id})

    cart_items, lines, item_paise = [], [], 0
    for it in items:
        search = _as_dict(await m.mcp.call("search_products", {"query": it.get("query", "")}))
        prod = _best_product(search.get("products") or [], it.get("budget_inr"))
        if not prod:
            continue
        qty = int(it.get("qty", 1) or 1)
        cart_items.append({
            "productVariantId": prod.get("productVariantId") or prod.get("id"),
            "storeProductId": prod.get("storeProductId"),
            "quantity": qty,
            "name": prod.get("name"),
            "price": prod.get("price"),
        })
        item_paise += (prod.get("price") or 0) * qty
        lines.append({"name": prod.get("name"), "qty": qty,
                      "price_inr": int(round((prod.get("price") or 0) / 100))})
    if not cart_items:
        return {"status": "no_product"}

    await m.mcp.call("update_cart", {"deviceId": DEVICE_ID, "replaceCart": True, "cartItems": cart_items})
    preview = _as_dict(await m.mcp.call("create_online_payment_order", {
        "confirmOrder": False, "userAddressId": address_id,
    }))
    to_pay_paise = preview.get("toPayAmount")
    delivery_paise = preview.get("deliveryFee") or 0
    return {
        "status": "ready", "store": m.name, "items": lines,
        "product": lines[0]["name"] + (f" +{len(lines) - 1} aur" if len(lines) > 1 else ""),
        "item_price_inr": int(round(item_paise / 100)),
        "delivery_fee_inr": int(round(delivery_paise / 100)),
        "to_pay_inr": int(round((to_pay_paise or (item_paise + delivery_paise)) / 100)),
        "address": addr, "address_id": address_id,
        "deliverable": preview.get("deliverable"), "preview": preview,
    }


async def instamart_prepare_cart(m, items: list[dict], address_id: str | None) -> dict:
    """Build a REAL Instamart cart from several items (one search each) → single cart total."""
    addr = _pick_address(await list_addresses(m), address_id)
    if not addr:
        return {"status": "no_address"}
    address_id = addr["id"]

    im_items, lines, item_paise = [], [], 0
    for it in items:
        search = _as_dict(await m.mcp.call("search_products", {"addressId": address_id, "query": it.get("query", "")}))
        products = search.get("products") or search.get("results") or search.get("items") or []
        prod = _best_product(products, it.get("budget_inr"))
        if not prod:
            continue
        qty = int(it.get("qty", 1) or 1)
        im_items.append({"spinId": prod.get("spinId"), "skuId": prod.get("skuId"), "quantity": qty})
        item_paise += (prod.get("price") or 0) * qty
        lines.append({"name": _first(prod, ["name", "displayName", "productName"]), "qty": qty,
                      "price_inr": int(round((prod.get("price") or 0) / 100))})
    if not im_items:
        return {"status": "no_product"}

    await m.mcp.call("update_cart", {"selectedAddressId": address_id, "items": im_items})
    cart = _as_dict(await m.mcp.call("get_cart", {}))
    delivery_paise = _first(cart, ["deliveryFee", "delivery_fee"]) or 0
    total_paise = _first(cart, ["toPayAmount", "grandTotal", "totalAmount"]) or (item_paise + delivery_paise)
    return {
        "status": "ready", "store": m.name, "items": lines,
        "product": lines[0]["name"] + (f" +{len(lines) - 1} aur" if len(lines) > 1 else ""),
        "item_price_inr": int(round(item_paise / 100)) if item_paise else None,
        "delivery_fee_inr": int(round(delivery_paise / 100)) if delivery_paise else None,
        "to_pay_inr": int(round(total_paise / 100)) if total_paise else None,
        "address": addr, "address_id": address_id, "cart": cart,
    }


# ---------------- dispatch ----------------
async def prepare(m, query: str, budget_inr: int | None = None, qty: int = 1, address_id: str | None = None) -> dict:
    if m.id == "instamart":
        return await instamart_prepare(m, query, budget_inr, qty, address_id)
    return await zepto_prepare(m, query, budget_inr, qty, address_id)


async def prepare_cart(m, items: list[dict], address_id: str | None = None) -> dict:
    if m.id == "instamart":
        return await instamart_prepare_cart(m, items, address_id)
    return await zepto_prepare_cart(m, items, address_id)


async def confirm(m, address_id: str, **kw) -> dict:
    if m.id == "instamart":
        return await instamart_confirm(m, address_id, kw.get("intent_app", "gpay://upi/"))
    return await zepto_confirm(m, address_id, kw.get("rail", "online"))
