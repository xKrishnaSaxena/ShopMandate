package com.shopmandate.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.graphics.asImageBitmap
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
 * Screen 5 — Approve cart. The user sees EXACTLY what they're buying + why, then approves.
 * Content scrolls; the Pay CTA stays pinned at the bottom so it's always reachable.
 */
@Composable
fun ApproveScreen(
    productName: String = "boAt Airdopes 141 –\nWireless Earbuds",
    subtitle: String = "Color: Bold Black • 1 Year Warranty",
    priceInr: Int = 1800,
    qty: Int = 1,
    reason: String = "Store B chuna — ₹150 sasta + jaldi delivery",
    delivery: String = "kal shaam tak",
    itemPriceInr: Int? = null,
    deliveryFeeInr: Int? = null,
    totalInr: Int? = null,
    addressLabel: String? = null,
    addressLine: String? = null,
    onChangeAddress: () -> Unit = {},
    onApprove: () -> Unit = {},
    onReject: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val priceText = "₹${"%,d".format(priceInr)}"
    val payText = "₹${"%,d".format(totalInr ?: priceInr)}"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- Top bar (fixed) ----
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
                modifier = Modifier.size(24.dp).clickable { onBack() },
            )
            Spacer(Modifier.weight(1f))
            Text("ShopMandate", color = Brand, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Settings, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        }

        // ---- Scrollable content ----
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Text("Please confirm", color = Ink, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Spacer(Modifier.height(4.dp))
            Text("Review your selection before paying.", color = InkMuted, fontSize = 15.sp, textAlign = TextAlign.Center)

            Spacer(Modifier.height(20.dp))

            // ---- Product info card (text only) ----
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.10f)),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(productName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, color = InkMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(priceText, color = Ink, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50), color = Brand.copy(alpha = 0.08f)) {
                            Text("Qty: $qty", color = Brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
                        Text(reason, color = Cta, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Cta, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delivery: $delivery", color = Cta, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ---- Bill breakdown (transparent: item + delivery = total) ----
            if (itemPriceInr != null && deliveryFeeInr != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = AppSurface,
                    border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.10f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        BillRow("Item total", "₹$itemPriceInr")
                        Spacer(Modifier.height(8.dp))
                        BillRow("Delivery fee", "₹$deliveryFeeInr")
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(InkMuted.copy(alpha = 0.12f)))
                        Spacer(Modifier.height(10.dp))
                        BillRow("To pay", payText, bold = true)
                    }
                }
            }

            // ---- Delivery address ----
            if (addressLine != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Brand.copy(alpha = 0.05f),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = Brand, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Deliver to" + (addressLabel?.let { " · $it" } ?: ""), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(addressLine, color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        TextButton(onClick = onChangeAddress) {
                            Text("Change", color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // ---- CTA (fixed at bottom) ----
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
            Text("Approve & Pay $payText", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReject) {
            Text("Nahi, kuch aur dikhao", color = InkMuted, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BillRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (bold) Ink else InkMuted, fontSize = if (bold) 16.sp else 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        Text(value, color = Ink, fontSize = if (bold) 18.sp else 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

/** Decode a base64 PNG/JPEG into a Compose ImageBitmap (best-effort). Shared with ClarifyScreen. */
internal fun decodeB64(b64: String): androidx.compose.ui.graphics.ImageBitmap? = try {
    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (e: Exception) {
    null
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ApproveScreenPreview() {
    ApproveScreen()
}
