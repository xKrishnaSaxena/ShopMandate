package com.shopmandate.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Real-time DUPLEX voice client for the backend `/api/live` bridge.
 *
 * - Streams mic PCM (16 kHz / 16-bit / mono) up over a WebSocket as binary frames.
 * - Plays the agent's PCM (24 kHz) reply as it streams down (AudioTrack, MODE_STREAM).
 * - Surfaces live captions ("you" / "agent") + [State] via callbacks.
 * - Barge-in: on an "interrupted" control frame, playback is flushed immediately.
 *
 * Needs RECORD_AUDIO permission. OkHttp (already on the classpath via Retrofit) does the WS.
 */
class LiveClient(
    baseUrl: String, // e.g. http://10.0.2.2:5055/  → ws://10.0.2.2:5055/api/live
    private val onYou: (String) -> Unit,
    private val onAgent: (String) -> Unit,
    private val onState: (State) -> Unit,
) {
    enum class State { CONNECTING, LISTENING, SPEAKING, CLOSED, ERROR }

    private val wsUrl = baseUrl
        .replace("https://", "wss://")
        .replace("http://", "ws://")
        .trimEnd('/') + "/api/live"

    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // keep the socket open indefinitely
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    @Volatile private var running = false
    private var micThread: Thread? = null

    fun start() {
        onState(State.CONNECTING)
        ws = http.newWebSocket(Request.Builder().url(wsUrl).build(), Listener())
    }

    fun stop() {
        running = false
        runCatching { ws?.send(JSONObject().put("type", "end").toString()) }
        micThread?.interrupt(); micThread = null
        releaseAudio()
        runCatching { ws?.close(1000, "bye") }
        ws = null
        onState(State.CLOSED)
    }

    /** Optionally kick off the turn with a text line (e.g. a suggestion) instead of speaking. */
    fun sendText(text: String) {
        runCatching { ws?.send(JSONObject().put("type", "text").put("text", text).toString()) }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            startPlayback()
            startMic(webSocket)
            onState(State.LISTENING)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val pcm = bytes.toByteArray() // 24 kHz PCM chunk
            track?.write(pcm, 0, pcm.size)
            onState(State.SPEAKING)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (o.optString("type")) {
                "you" -> onYou(o.optString("text"))
                "agent" -> onAgent(o.optString("text"))
                "interrupted" -> flushPlayback()          // user barged in
                "turn_complete" -> onState(State.LISTENING)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onState(State.ERROR)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onState(State.CLOSED)
        }
    }

    private fun startMic(webSocket: WebSocket) {
        val min = AudioRecord.getMinBufferSize(
            IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, IN_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096),
        )
        recorder = rec
        running = true
        rec.startRecording()
        micThread = thread(name = "live-mic") {
            val chunk = ByteArray(2048)
            while (running) {
                val n = try { rec.read(chunk, 0, chunk.size) } catch (_: Exception) { -1 }
                if (n > 0) webSocket.send(chunk.copyOf(n).toByteString())
            }
        }
    }

    private fun startPlayback() {
        val min = AudioTrack.getMinBufferSize(
            OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val t = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(OUT_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            maxOf(min, 8192), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track = t
        t.play()
    }

    private fun flushPlayback() {
        runCatching { track?.pause(); track?.flush(); track?.play() }
    }

    private fun releaseAudio() {
        runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null
        runCatching { track?.stop() }; runCatching { track?.release() }; track = null
    }

    private companion object {
        const val IN_RATE = 16000   // Gemini Live input
        const val OUT_RATE = 24000  // Gemini Live output
    }
}
