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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.delay

/**
 * Screen 7 — OTP verification for a SPECIFIC store ([storeName]). Each store has its own OTP.
 * Verifying connects that store and returns to the connect list (in ShopViewModel).
 */
@Composable
fun OtpScreen(
    storeName: String = "Store",
    onVerified: () -> Unit = {},
    onBack: () -> Unit = {},
    onResend: () -> Unit = {},
) {
    var otp by remember { mutableStateOf("") }
    var autoRead by remember { mutableStateOf(false) }

    // Simulated SMS auto-read (mock — no real SMS is sent). Fills the OTP after a moment,
    // like Android's SMS auto-fill. Real SMS User Consent API = a Phase-2 swap.
    LaunchedEffect(Unit) {
        delay(1500)
        if (otp.isBlank()) {
            otp = "123456"
            autoRead = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Ink,
            modifier = Modifier.size(24.dp).clickable { onBack() },
        )

        Spacer(Modifier.height(24.dp))
        // Which store are we connecting?
        Surface(shape = RoundedCornerShape(50), color = Brand.copy(alpha = 0.08f)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = Brand, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connecting $storeName", color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("OTP daalo", color = Ink, fontWeight = FontWeight.Bold, fontSize = 32.sp)
        Spacer(Modifier.height(8.dp))
        Text("$storeName ne +91 98XXX-XX21 par code bheja hai", color = InkMuted, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Number badlo",
            color = Brand,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onBack() },
        )

        Spacer(Modifier.height(28.dp))
        // ---- 6-box OTP field ----
        BasicTextField(
            value = otp,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otp = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(6) { i ->
                        OtpBox(
                            char = otp.getOrNull(i)?.toString() ?: "",
                            focused = i == otp.length,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
        )

        Spacer(Modifier.height(14.dp))
        // ---- Auto-read status ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (autoRead) Icons.Filled.CheckCircle else Icons.Filled.Sms,
                contentDescription = null,
                tint = if (autoRead) SuccessGreen else Brand,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (autoRead) "OTP auto-read ho gaya ✓" else "SMS se OTP auto-read ho raha hai…",
                color = if (autoRead) SuccessGreen else Brand,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = InkMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Resend in 0:24", color = InkMuted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "Resend OTP",
                color = InkMuted.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.clickable { onResend() },
            )
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onVerified,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Cta),
        ) {
            Text("Verify & Connect  →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OtpBox(char: String, focused: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) Brand else InkMuted.copy(alpha = 0.25f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(char, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OtpScreenPreview() {
    OtpScreen(storeName = "Zepto")
}
