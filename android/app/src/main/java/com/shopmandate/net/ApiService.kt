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
}
