package com.shopmandate.net

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the [ApiService]. Base URL comes from [DevConfig] (overridable at runtime via the
 * hidden dev dialog — tap the "ShopMandate" logo 10×). Defaults to the emulator host.
 */
object ApiClient {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
        encodeDefaults = true
        explicitNulls = false
    }

    /** Uses the currently-configured base URL (from DevConfig). */
    fun create(context: Context): ApiService = create(DevConfig.getBaseUrl(context))

    /** ws:// URL for the Live voice bridge, derived from the configured base URL. */
    fun liveWsUrl(context: Context): String =
        DevConfig.getBaseUrl(context)
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') + "/ws/live"

    /** Parse a {"type":"quotes","quotes":[...]} Live event into typed quotes. */
    fun parseQuotes(text: String): List<Quote> = try {
        json.decodeFromString<LiveQuotesEvent>(text).quotes
    } catch (e: Exception) {
        emptyList()
    }

    fun create(baseUrl: String): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC, not BODY — audio/image payloads are huge base64 and BODY logging stalls the call.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            // Gemini multimodal (audio transcription, image identify, TTS, Nano-Banana) is slow.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
