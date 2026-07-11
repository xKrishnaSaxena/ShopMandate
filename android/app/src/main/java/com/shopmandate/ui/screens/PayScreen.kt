package com.shopmandate.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Screen 8 — Place the REAL order + pay. Tapping "Pay" calls the merchant's confirm
 * (order/confirm), which opens the merchant-issued UPI payment link in the user's UPI
 * app. The user authorises with their PIN there; the app polls status → Success.
 */
@Composable
fun PayScreen(
    amountInr: Int = 0,
    itemPriceInr: Int? = null,
    deliveryFeeInr: Int? = null,
    product: String = "Order",
    addressLabel: String? = null,
    addressLine: String? = null,
    awaiting: Boolean = false,
    onConfirm: () -> Unit = {},
    onPaidManually: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val amountText = "₹${"%,d".format(amountInr)}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = InkMuted,
                        modifier = Modifier.size(24.dp).clickable { onBack() },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Pay $amountText", color = Ink, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Spacer(Modifier.height(4.dp))
                Text(product, color = InkMuted, fontSize = 14.sp)

                // ---- Breakdown ----
                if (itemPriceInr != null && deliveryFeeInr != null) {
                    Spacer(Modifier.height(18.dp))
                    PayRow("Item total", "₹$itemPriceInr")
                    Spacer(Modifier.height(6.dp))
                    PayRow("Delivery fee", "₹$deliveryFeeInr")
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(InkMuted.copy(alpha = 0.12f)))
                    Spacer(Modifier.height(8.dp))
                    PayRow("To pay", amountText, bold = true)
                }

                // ---- Address ----
                if (addressLine != null) {
                    Spacer(Modifier.height(16.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = Brand.copy(alpha = 0.05f), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = Brand, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Deliver to" + (addressLabel?.let { " · $it" } ?: ""), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(addressLine, color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = Brand.copy(alpha = 0.05f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Real UPI payment", color = SuccessGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("aapke UPI app mein PIN daal ke approve karein.", color = InkMuted, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onConfirm,
                    enabled = !awaiting,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Cta),
                ) {
                    Text("Order place karo · Pay $amountText  →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (awaiting) {
            AwaitingPayment(onPaidManually)
        }
    }
}

@Composable
private fun PayRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (bold) Ink else InkMuted, fontSize = if (bold) 16.sp else 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        Text(value, color = Ink, fontSize = if (bold) 18.sp else 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun AwaitingPayment(onPaidManually: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = AppSurface, modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Brand, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(20.dp))
                Text("Order placed ✓", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "UPI app khul gaya — PIN daal ke payment complete karein.",
                    color = InkMuted, fontSize = 14.sp,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onPaidManually, modifier = Modifier.fillMaxWidth()) {
                    Text("Payment ho gaya ✓", color = Brand, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
