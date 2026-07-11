package com.shopmandate.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File

/** Records mic audio to a temp file (AAC/MP4) and exposes it as base64 + live amplitude. */
class AudioCapture(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        stopQuietly() // safety: no leftover recorder
        val file = File.createTempFile("rec_", ".m4a", context.cacheDir)
        outputFile = file
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
    }

    /** Current peak amplitude (0..32767), or 0 if not recording. */
    fun amplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (_: Exception) {
        0
    }

    /** Stops and returns the recorded audio as base64 (or null on failure). */
    fun stopToBase64(): String? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile?.readBytes()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (_: Exception) {
            stopQuietly()
            null
        }
    }

    /** Release without producing output (e.g. on screen dispose). */
    fun stopQuietly() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
    }
}
