package com.shopmandate.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Screen 5 — Approve cart. Mirrors design/approve_pay/screen.png.
 * The user sees EXACTLY what they're buying + why Store B, then approves.
 */
@Composable
fun ApproveScreen(
    onApprove: () -> Unit = {},
    onReject: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Brand,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("ShopMandate", color = Brand, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Settings, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("Please confirm", color = Ink, fontWeight = FontWeight.Bold, fontSize = 30.sp)
        Spacer(Modifier.height(4.dp))
        Text("Review your selection before paying.", color = InkMuted, fontSize = 15.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(20.dp))

        // ---- Hero product card ----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.10f)),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Brand.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Headphones, contentDescription = null, tint = InkMuted.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                }
                Column(Modifier.padding(20.dp)) {
                    Text("boAt Airdopes 141 –\nWireless Earbuds", color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Color: Bold Black • 1 Year Warranty", color = InkMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("₹1,800", color = Ink, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50), color = Brand.copy(alpha = 0.08f)) {
                            Text("Qty: 1", color = Brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Reason strip ----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Cta.copy(alpha = 0.08f),
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = Cta, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Store B chuna — ₹150 sasta + jaldi delivery", color = Cta, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Cta, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delivery: kal shaam tak", color = Cta, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ---- CTA ----
        Button(
            onClick = onApprove,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Cta),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Approve & Pay ₹1,800", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReject) {
            Text("Nahi, kuch aur dikhao", color = InkMuted, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ApproveScreenPreview() {
    ApproveScreen()
}
