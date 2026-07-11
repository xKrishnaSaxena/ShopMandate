package com.shopmandate.net

import kotlinx.serialization.Serializable

// Property names are camelCase; the JSON is snake_case (mapped by JsonNamingStrategy in ApiClient).
// These mirror product.md §8.

// ---- POST /session/start ----
@Serializable
data class StartRequest(
    val inputType: String,          // "voice" | "photo" | "text"
    val audioB64: String? = null,
    val imageB64: String? = null,
    val text: String? = null,
    val languageHint: String? = null,
    val userName: String? = null,   // for greeting / personalization
)

@Serializable
data class Intent(
    val product: String = "",
    val category: String = "",
    val budgetInr: Int? = null,
    val qty: Int = 1,
    val constraints: List<String> = emptyList(),
    val language: String = "en-IN",
)

@Serializable
data class ReorderSuggestion(
    val matchedOrderId: String? = null,
    val text: String = "",
)

@Serializable
data class StartResponse(
    val sessionId: String,
    val status: String,             // "intent_ready" | "need_clarification"
    val transcript: String? = null,
    val parsedIntent: Intent? = null,
    val clarifyingQuestion: String? = null,
    val reorderSuggestion: ReorderSuggestion? = null,
    val agentOpener: String? = null,          // first agent bubble in the clarify chat
    val suggestions: List<String> = emptyList(),
)

// ---- POST /session/{id}/chat (multi-turn clarify agent) ----
@Serializable
data class ChatRequest(
    val message: String,
    val userName: String? = null,
)

@Serializable
data class ChatResponse(
    val reply: String = "",
    val parsedIntent: Intent? = null,
    val suggestions: List<String> = emptyList(),
    val ready: Boolean = false,
    val showProductImage: Boolean = false,
)

// ---- POST /session/{id}/intent (tap-to-edit chips) ----
@Serializable
data class IntentPatchRequest(
    val product: String? = null,
    val budgetInr: Int? = null,
    val qty: Int? = null,
)

@Serializable
data class IntentPatchResponse(
    val status: String = "",
    val parsedIntent: Intent? = null,
)

// ---- POST /session/{id}/clarify ----
@Serializable
data class ClarifyAnswers(
    val type: String? = null,
    val budgetInr: Int? = null,
)

@Serializable
data class ClarifyRequest(val answers: ClarifyAnswers)

@Serializable
data class ClarifyResponse(
    val status: String,
    val parsedIntent: Intent? = null,
)

// ---- POST /session/{id}/search ----
@Serializable
data class Quote(
    val store: String,
    val productName: String,
    val priceInr: Int,
    val delivery: String,
    val inStock: Boolean = true,
    val imageUrl: String? = null,
)

@Serializable
data class Winner(
    val store: String,
    val priceInr: Int,
    val why: String,
)

@Serializable
data class Cart(
    val item: String,
    val color: String? = null,
    val warranty: String? = null,
    val qty: Int = 1,
    val priceInr: Int,
    val store: String,
    val delivery: String,
)

@Serializable
data class SearchResponse(
    val status: String,
    val quotes: List<Quote> = emptyList(),
    val winner: Winner? = null,
    val cart: Cart? = null,
    val steps: List<String> = emptyList(),   // live A2A haggle transcript
)

// ---- wow-factors ----
@Serializable
data class SayRequest(val text: String)

@Serializable
data class SayResponse(val audioB64: String, val mime: String = "audio/wav")

@Serializable
data class VisualizeRequest(
    val product: String? = null,
    val imageB64: String? = null,
    val style: String? = null,
)

@Serializable
data class VisualizeResponse(val imageB64: String, val mime: String = "image/png")

@Serializable
data class ResearchResponse(val note: String, val quotesConsidered: Int = 0)

// live voice bridge event: {"type":"quotes","quotes":[...]}
@Serializable
data class LiveQuotesEvent(val quotes: List<Quote> = emptyList())

// ---- Live voice multi-item cart (Phase B) ----
@Serializable
data class LiveCartItem(
    val query: String = "",
    val store: String = "",       // merchant id the cart is locked to ("zepto" / "instamart")
    val name: String = "",
    val priceInr: Int = 0,
)

/** {"type":"cart"|"checkout","items":[...],"total_inr":N,"store":"zepto"} */
@Serializable
data class LiveCartEvent(
    val items: List<LiveCartItem> = emptyList(),
    val totalInr: Int = 0,
    val store: String? = null,
)

// ---- POST /connect/{store}/start & /verify ----
@Serializable
data class ConnectStartRequest(val phone: String)

@Serializable
data class ConnectStartResponse(
    val status: String,
    val store: String,
    val maskedPhone: String,
)

@Serializable
data class OAuthStartRequest(
    val phone: String? = null,        // from Settings — pre-fills the store's OTP login
)

@Serializable
data class OAuthStartResponse(
    val status: String,               // "auth_required" | "connected" | "error" | "unknown_or_mock"
    val store: String = "",
    val authUrl: String? = null,      // open this in the phone browser (mobile + OTP)
    val connectedStores: List<String> = emptyList(),
    val detail: String? = null,
)

@Serializable
data class ConnectStatusResponse(
    val store: String,
    val connected: Boolean = false,
    val connectedStores: List<String> = emptyList(),
)

@Serializable
data class OAuthCompleteRequest(val code: String, val state: String)

@Serializable
data class OAuthCompleteResponse(val ok: Boolean = false)

@Serializable
data class ConnectVerifyRequest(val otp: String)

@Serializable
data class ConnectVerifyResponse(
    val status: String,
    val store: String,
    val connectedStores: List<String> = emptyList(),
)

// ---- POST /session/{id}/pay ----
@Serializable
data class PayRequest(
    val method: String = "upi",
    val upiApp: String? = null,
    val upiId: String? = null,
    val upiTxnId: String? = null,
)

@Serializable
data class AuditChain(
    val intent: String? = null,
    val cart: String? = null,
    val payment: String? = null,
)

@Serializable
data class Receipt(
    val orderId: String,
    val item: String,
    val priceInr: Int,
    val store: String,
    val delivery: String,
    val paidVia: String? = null,
    val auditChain: AuditChain? = null,
)

@Serializable
data class PayResponse(
    val status: String,
    val orderId: String,
    val receipt: Receipt? = null,
)

// ---- GET /orders ----
@Serializable
data class OrderDto(
    val product: String,
    val store: String,
    val priceInr: Int,                 // total paid (item + delivery)
    val orderId: String,
    val date: String,
    val status: String,
    val delivered: Boolean,
    val qty: Int = 1,
    val itemPriceInr: Int? = null,     // item subtotal
    val deliveryFeeInr: Int? = null,   // delivery fee (shown separately)
    val addressLabel: String? = null,  // e.g. "Home", "Office"
    val addressLine: String? = null,   // full delivery address
)

@Serializable
data class OrdersResponse(
    val orders: List<OrderDto> = emptyList(),
)

// ---- real MCP order pipeline (Zepto / Instamart) ----
@Serializable
data class AddressDto(
    val id: String? = null,
    val label: String? = null,
    val line: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class AddressesResponse(
    val status: String = "",
    val name: String? = null,
    val addresses: List<AddressDto> = emptyList(),
)

@Serializable
data class OrderPrepareRequest(
    val query: String,
    val budgetInr: Int? = null,
    val qty: Int = 1,
    val addressId: String? = null,
)

@Serializable
data class OrderPrepareResponse(
    val status: String,
    val store: String? = null,
    val product: String? = null,
    val qty: Int = 1,
    val itemPriceInr: Int? = null,
    val deliveryFeeInr: Int? = null,
    val toPayInr: Int? = null,
    val address: AddressDto? = null,
    val addressId: String? = null,
    val deliverable: Boolean? = null,
    val items: List<PrepareLine> = emptyList(),   // multi-item cart breakdown (Live voice)
    val detail: String? = null,
)

@Serializable
data class PrepareLine(
    val name: String? = null,
    val qty: Int = 1,
    val priceInr: Int? = null,
)

@Serializable
data class CartLineRequest(
    val query: String,
    val budgetInr: Int? = null,
    val qty: Int = 1,
)

@Serializable
data class OrderPrepareCartRequest(
    val items: List<CartLineRequest>,
    val addressId: String? = null,
)

@Serializable
data class OrderConfirmMeta(
    val product: String? = null,
    val qty: Int = 1,
    val itemPriceInr: Int? = null,
    val deliveryFeeInr: Int? = null,
    val toPayInr: Int? = null,
    val address: AddressDto? = null,
)

@Serializable
data class OrderConfirmRequest(
    val addressId: String,
    val rail: String = "online",
    val intentApp: String = "gpay://upi/",
    val meta: OrderConfirmMeta? = null,
)

@Serializable
data class OrderConfirmResponse(
    val status: String,
    val store: String? = null,
    val orderId: String? = null,
    val paymentLink: String? = null,
    val detail: String? = null,
)

@Serializable
data class OrderStatusRequest(val orderId: String)

@Serializable
data class OrderStatusResponse(
    val status: String,
    val paid: Boolean = false,
    val detail: String? = null,
)
