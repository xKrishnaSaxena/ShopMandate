package com.shopmandate.net

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Retrofit interface for the backend contract (product.md §8). */
interface ApiService {

    @POST("api/session/start")
    suspend fun start(@Body body: StartRequest): StartResponse

    @POST("api/session/{id}/clarify")
    suspend fun clarify(@Path("id") sessionId: String, @Body body: ClarifyRequest): ClarifyResponse

    @POST("api/session/{id}/chat")
    suspend fun chat(@Path("id") sessionId: String, @Body body: ChatRequest): ChatResponse

    @POST("api/session/{id}/intent")
    suspend fun patchIntent(@Path("id") sessionId: String, @Body body: IntentPatchRequest): IntentPatchResponse

    @POST("api/session/{id}/search")
    suspend fun search(@Path("id") sessionId: String): SearchResponse

    @POST("api/connect/{store}/start")
    suspend fun connectStart(@Path("store") store: String, @Body body: ConnectStartRequest): ConnectStartResponse

    @POST("api/connect/{store}/verify")
    suspend fun connectVerify(@Path("store") store: String, @Body body: ConnectVerifyRequest): ConnectVerifyResponse

    @POST("api/session/{id}/pay")
    suspend fun pay(@Path("id") sessionId: String, @Body body: PayRequest): PayResponse

    @GET("api/orders")
    suspend fun orders(): OrdersResponse

    // ---- real MCP order pipeline ----
    @GET("api/merchants/{mid}/addresses")
    suspend fun addresses(@Path("mid") mid: String): AddressesResponse

    @POST("api/merchants/{mid}/order/prepare")
    suspend fun orderPrepare(@Path("mid") mid: String, @Body body: OrderPrepareRequest): OrderPrepareResponse

    @POST("api/merchants/{mid}/order/prepare_cart")
    suspend fun orderPrepareCart(@Path("mid") mid: String, @Body body: OrderPrepareCartRequest): OrderPrepareResponse

    @POST("api/merchants/{mid}/order/confirm")
    suspend fun orderConfirm(@Path("mid") mid: String, @Body body: OrderConfirmRequest): OrderConfirmResponse

    @POST("api/merchants/{mid}/order/status")
    suspend fun orderStatus(@Path("mid") mid: String, @Body body: OrderStatusRequest): OrderStatusResponse

    // ---- wow-factors ----
    @POST("api/say")
    suspend fun say(@Body body: SayRequest): SayResponse

    @POST("api/visualize")
    suspend fun visualize(@Body body: VisualizeRequest): VisualizeResponse

    @GET("api/session/{id}/research")
    suspend fun research(@Path("id") sessionId: String): ResearchResponse

    // ---- app-driven browser OAuth (real store connect) ----
    @POST("api/connect/{store}/oauth/start")
    suspend fun oauthStart(@Path("store") store: String, @Body body: OAuthStartRequest): OAuthStartResponse

    @GET("api/connect/{store}/status")
    suspend fun connectStatus(@Path("store") store: String): ConnectStatusResponse

    @POST("api/oauth/complete")
    suspend fun oauthComplete(@Body body: OAuthCompleteRequest): OAuthCompleteResponse
}
