package com.shopmandate.ui.screens

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmandate.capture.AudioCapture
import com.shopmandate.capture.WithPermission
import com.shopmandate.ui.theme.Cta
import kotlinx.coroutines.delay

/**
 * Screen 2 — Voice listening with REAL mic capture. The waveform reacts to live
 * amplitude; on stop, the recording is encoded to base64 and handed to [onStop]
 * (the ViewModel stores it, ready to POST to /session/start).
 */
@Composable
fun VoiceScreen(
    onStop: (audioB64: String) -> Unit = {},
    onCancel: () -> Unit = {},
) {
    WithPermission(
        permission = Manifest.permission.RECORD_AUDIO,
        rationale = "Bolke batane ke liye mic access chahiye.",
    ) {
        VoiceContent(onStop = onStop, onCancel = onCancel)
    }
}

@Composable
private fun VoiceContent(
    onStop: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val capture = remember { AudioCapture(context) }
    var level by remember { mutableStateOf(0f) }
    var stopped by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        runCatching { capture.start() }
        onDispose { if (!stopped) capture.stopQuietly() }
    }

    // Poll mic amplitude → drives the waveform.
    LaunchedEffect(Unit) {
        while (true) {
            level = (capture.amplitude() / 20000f).coerceIn(0f, 1f)
            delay(70)
        }
    }

    val stopAndReturn = {
        if (!stopped) {
            stopped = true
            onStop(capture.stopToBase64() ?: "")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3A24B0), Color(0xFF4F32E7), Color(0xFF31198F)),
                ),
            )
            .systemBarsPadding()
            .padding(24.dp),
    ) {
        // Top status pill
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Cta))
                Spacer(Modifier.width(10.dp))
                Text("Sun raha hoon…", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        // Center: live waveform + hint
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Waveform(level)
            Spacer(Modifier.height(40.dp))
            Text(
                "Bolke batao — ho jaaye to ⏹ dabao",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Bottom: stop + cancel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Cta)
                    .clickable { stopAndReturn() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Tap to cancel",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.clickable {
                    if (!stopped) {
                        stopped = true
                        capture.stopQuietly()
                    }
                    onCancel()
                },
            )
        }
    }
}

@Composable
private fun Waveform(level: Float) {
    val weights = listOf(0.5f, 0.85f, 1f, 0.75f, 0.6f)
    val animated by animateFloatAsState(targetValue = level, label = "amp")
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        weights.forEachIndexed { i, w ->
            val h = (16f + animated * 60f * w).coerceIn(16f, 78f)
            Box(
                modifier = Modifier
                    .width(11.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == 2) Color.White else Cta.copy(alpha = 0.9f)),
            )
        }
    }
}
