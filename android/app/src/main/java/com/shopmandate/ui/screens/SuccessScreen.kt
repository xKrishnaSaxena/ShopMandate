package com.shopmandate.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmandate.ui.theme.AppBg
import com.shopmandate.ui.theme.AppSurface
import com.shopmandate.ui.theme.Brand
import com.shopmandate.ui.theme.Cta
import com.shopmandate.ui.theme.Ink
import com.shopmandate.ui.theme.InkMuted
import com.shopmandate.ui.theme.SuccessGreen

/**
 * Screen 9 — Success / receipt. Mirrors design/order_success/screen.png.
 * The "Verifiable receipt" section teases the Phase-2 AP2 Intent→Cart→Payment audit chain.
 */
@Composable
fun SuccessScreen(
    onDone: () -> Unit = {},
    onTrack: () -> Unit = {},
) {
    var shown by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val checkScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "check",
    )
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(checkScale)
                .clip(CircleShape)
                .background(SuccessGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text("Order confirmed! 🎉", color = Brand, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Thanks for shopping with ShopMandate.", color = InkMuted, fontSize = 15.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(28.dp))
        // ---- Receipt card ----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.10f)),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("boAt Airdopes", color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(Modifier.weight(1f))
                    Text("₹1,800", color = Brand, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storefront, contentDescription = null, tint = InkMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Store B", color = InkMuted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = InkMuted.copy(alpha = 0.12f))
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Order ID", color = InkMuted, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Text("#SM-4821", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Delivery", color = InkMuted, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(50), color = Cta.copy(alpha = 0.12f)) {
                        Text("kal shaam", color = Cta, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // ---- Verifiable receipt (Phase-2 teaser) ----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Brand.copy(alpha = 0.06f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Verifiable receipt", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = InkMuted, modifier = Modifier.rotate(chevron))
                }
                if (expanded) {
                    Spacer(Modifier.height(12.dp))
                    ChainRow("1. Intent Mandate", "#im-4821 · user-signed")
                    ChainRow("2. Cart Mandate", "#cm-4821 · Store B-signed")
                    ChainRow("3. Payment Mandate", "#pm-4821 · device-signed")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tamper-evident chain (AP2 — Phase 2).",
                        color = InkMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onTrack,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Cta),
        ) {
            Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Track order", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onDone) {
            Text("Ho gaya", color = Brand, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChainRow(label: String, id: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(id, color = InkMuted, fontSize = 12.sp)
        }
        Icon(Icons.Filled.Lock, contentDescription = null, tint = InkMuted, modifier = Modifier.size(14.dp))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SuccessScreenPreview() {
    SuccessScreen()
}
