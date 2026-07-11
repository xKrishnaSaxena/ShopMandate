package com.shopmandate.capture

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
 * A real-time voice session with the backend Gemini-Live bridge (`/ws/live`).
 *
 * Streams mic audio (PCM16 mono 16 kHz) up over a WebSocket and plays the agent's
 * audio (PCM16 mono 24 kHz) as it arrives. Transcript + live product quotes come back
 * as JSON text frames and are surfaced through [onEvent].
 *
 * Requires RECORD_AUDIO permission (the Live screen is wrapped in WithPermission).
 */
class LiveSession(private val onEvent: (LiveEvent) -> Unit) {

    sealed interface LiveEvent {
        data class Connected(val connected: Boolean) : LiveEvent
        data class Transcript(val role: String, val text: String) : LiveEvent
        data class Quotes(val rawJson: String) : LiveEvent
        data object TurnComplete : LiveEvent
        data class Failure(val message: String) : LiveEvent
    }

    private var ws: WebSocket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var micThread: Thread? = null

    @Volatile private var running = false

    fun start(wsUrl: String, userName: String = "") {
        if (running) return
        running = true
        val client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)  // long-lived stream
            .build()
        ws = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onEvent(LiveEvent.Connected(true))
                // ask the agent to greet first
                webSocket.send(JSONObject().put("type", "hello").put("name", userName).toString())
                startAudio(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handleText(text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val pcm = bytes.toByteArray()
                try { track?.write(pcm, 0, pcm.size) } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(LiveEvent.Failure(t.message ?: "connection failed"))
                stop()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                stop()
            }
        })
    }

    /** Send a typed message into the live conversation (fallback / test path). */
    fun sendText(text: String) {
        ws?.send(JSONObject().put("type", "text").put("text", text).toString())
    }

    private fun handleText(text: String) {
        try {
            val o = JSONObject(text)
            when (o.optString("type")) {
                "transcript" -> onEvent(LiveEvent.Transcript(o.optString("role"), o.optString("text")))
                "quotes" -> onEvent(LiveEvent.Quotes(text))
                "turn_complete" -> onEvent(LiveEvent.TurnComplete)
                "error" -> onEvent(LiveEvent.Failure(o.optString("detail")))
            }
        } catch (_: Exception) {
        }
    }

    private fun startAudio(webSocket: WebSocket) {
        // ---- playback (24 kHz) ----
        val outMin = AudioTrack.getMinBufferSize(
            OUTPUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(OUTPUT_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            maxOf(outMin, OUTPUT_RATE),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        ).apply { play() }

        // ---- capture (16 kHz, VOICE_COMMUNICATION → built-in echo cancel) ----
        val inMin = AudioRecord.getMinBufferSize(
            INPUT_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(inMin, INPUT_RATE * 2),
            )
        } catch (e: Exception) {
            onEvent(LiveEvent.Failure("mic init failed: ${e.message}"))
            return
        }
        record = rec
        try { rec.startRecording() } catch (e: Exception) {
            onEvent(LiveEvent.Failure("mic start failed: ${e.message}")); return
        }
        micThread = thread(name = "live-mic") {
            val buf = ByteArray(2048)
            while (running) {
                val n = try { rec.read(buf, 0, buf.size) } catch (_: Exception) { -1 }
                if (n > 0) webSocket.send(buf.toByteString(0, n))
            }
        }
    }

    fun stop() {
        if (!running && ws == null) return
        running = false
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        record = null
        track = null
        ws = null
        onEvent(LiveEvent.Connected(false))
    }

    private companion object {
        const val INPUT_RATE = 16000
        const val OUTPUT_RATE = 24000
    }
}
