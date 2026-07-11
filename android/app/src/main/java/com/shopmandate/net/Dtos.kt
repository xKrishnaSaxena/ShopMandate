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
    val priceInr: Int,
    val orderId: String,
    val date: String,
    val status: String,
    val delivered: Boolean,
)

@Serializable
data class OrdersResponse(
    val orders: List<OrderDto> = emptyList(),
)
