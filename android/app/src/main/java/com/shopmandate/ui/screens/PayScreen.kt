package com.shopmandate.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmandate.ui.theme.AppSurface
import com.shopmandate.ui.theme.Brand
import com.shopmandate.ui.theme.Cta
import com.shopmandate.ui.theme.Ink
import com.shopmandate.ui.theme.InkMuted
import com.shopmandate.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

private data class UpiApp(val name: String, val color: Color)

private val UPI_APPS = listOf(
    UpiApp("GPay", Color(0xFF4285F4)),
    UpiApp("PhonePe", Color(0xFF5F259F)),
    UpiApp("Paytm", Color(0xFF00BAF2)),
    UpiApp("BHIM", Color(0xFFF37021)),
)

/**
 * Screen 8 — UPI payment (framed, no real money). Mirrors design/upi_payment/screen.png.
 * Adds the "Authorizing… / UPI PIN daalo" overlay that was missing in the Stitch export (build-fix #3).
 */
@Composable
fun PayScreen(
    onPaid: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var selected by remember { mutableStateOf("Paytm") }
    var authorizing by remember { mutableStateOf(false) }

    // Mock authorization delay, then complete.
    LaunchedEffect(authorizing) {
        if (authorizing) {
            delay(1900)
            onPaid()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        // ---- Bottom sheet ----
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = AppSurface,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(InkMuted.copy(alpha = 0.3f)),
                )
                Spacer(Modifier.height(20.dp))
                Text("Pay ₹1,800", color = Ink, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Spacer(Modifier.height(4.dp))
                Text("boAt Airdopes 141 – Wireless Earbuds", color = InkMuted, fontSize = 14.sp)

                Spacer(Modifier.height(22.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Pay securely via UPI", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UPI_APPS.forEach { app ->
                        UpiTile(
                            app = app,
                            selected = selected == app.name,
                            onClick = { selected = app.name },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Enter UPI ID row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Brand.copy(alpha = 0.04f),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Brand, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Enter UPI ID / Number", color = Ink, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkMuted)
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Secure line
                Surface(shape = RoundedCornerShape(14.dp), color = Brand.copy(alpha = 0.05f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Secure UPI payment", color = SuccessGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("aapki approval pe hi charge hoga.", color = InkMuted, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { authorizing = true },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Cta),
                ) {
                    Text("Pay ₹1,800 securely  →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ---- Authorizing overlay (build-fix #3) ----
        if (authorizing) {
            AuthorizingOverlay()
        }
    }
}

@Composable
private fun UpiTile(app: UpiApp, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Brand.copy(alpha = 0.04f),
        border = if (selected) BorderStroke(1.5.dp, Brand) else BorderStroke(1.dp, InkMuted.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(app.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = app.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                app.name,
                color = if (selected) Brand else Ink,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun AuthorizingOverlay() {
    val t = rememberInfiniteTransition(label = "pin")
    val fill by t.animateFloat(
        initialValue = 0f,
        targetValue = 6.99f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "fill",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppSurface,
            modifier = Modifier.padding(40.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Brand, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(20.dp))
                Text("Authorizing…", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("UPI PIN se approve karein", color = InkMuted, fontSize = 14.sp)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { i ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (i < fill.toInt()) Brand else InkMuted.copy(alpha = 0.2f)),
                        )
                    }
                }
            }
        }
    }
}
