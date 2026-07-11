"""Pydantic models mirroring the frozen §8 API contract."""

from __future__ import annotations

from typing import Literal
from pydantic import BaseModel, Field

Language = Literal["hi-IN", "kn-IN", "en-IN"]


class Intent(BaseModel):
    product: str = ""
    category: str = ""
    budget_inr: int | None = None
    qty: int = 1
    constraints: list[str] = Field(default_factory=list)
    language: Language = "hi-IN"
    transcript: str = ""
    needs_clarification: bool = False
    clarifying_question: str | None = None


class Quote(BaseModel):
    store: str
    product_name: str
    price_inr: int
    delivery: str
    in_stock: bool = True
    image_url: str | None = None


class Cart(BaseModel):
    item: str
    color: str = "Bold Black"
    warranty: str = "1 Year"
    qty: int = 1
    price_inr: int
    store: str
    delivery: str


class Winner(BaseModel):
    store: str
    price_inr: int
    why: str


class Receipt(BaseModel):
    order_id: str
    item: str
    price_inr: int
    store: str
    delivery: str
    paid_via: str
    audit_chain: dict[str, str]


# ---- request bodies ----
class StartReq(BaseModel):
    input_type: Literal["voice", "photo", "text"]
    audio_b64: str | None = None
    image_b64: str | None = None
    text: str | None = None
    language_hint: Language | None = None


class ClarifyReq(BaseModel):
    answers: dict[str, object]


class ConnectStartReq(BaseModel):
    phone: str


class ConnectVerifyReq(BaseModel):
    otp: str


class PayReq(BaseModel):
    method: Literal["upi"] = "upi"
    upi_app: str = "upi"                # which UPI app the user paid from (label only)
    upi_id: str | None = None           # optional payer VPA (if entered manually)
    upi_txn_id: str | None = None       # txn id returned by the UPI app, if any


class VisualizeReq(BaseModel):
    product: str | None = None
    image_b64: str | None = None   # "find one like this" reference photo
    style: str | None = None       # e.g. "on a wooden table", "worn by a person"


class SayReq(BaseModel):
    text: str
