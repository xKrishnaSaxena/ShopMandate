package com.shopmandate.ui.screens

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmandate.ui.DevUrlDialog
import com.shopmandate.ui.theme.AppBg
import com.shopmandate.ui.theme.AppSurface
import com.shopmandate.ui.theme.Brand
import com.shopmandate.ui.theme.Cta
import com.shopmandate.ui.theme.Ink
import com.shopmandate.ui.theme.InkMuted
import com.shopmandate.ui.theme.ShopMandateTheme
import com.shopmandate.ui.theme.SuccessGreen

/**
 * Screen 1 — Home (voice-first landing). Mirrors design/home_voice_first/screen.png.
 *
 * GATING: shopping is locked until stores are connected. When [connected] is false the
 * mic/camera/chips are disabled and a "connect stores first" banner is shown.
 * The broken bottom nav from the Stitch export is intentionally omitted (build-fix #2).
 */
@Composable
fun HomeScreen(
    connectedStores: Set<String> = emptySet(),
    userName: String = "",
    reorderSuggestion: String? = null,
    onMicClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onSuggestionClick: (String) -> Unit = {},
    onConnectStores: () -> Unit = {},
    onOrders: () -> Unit = {},
    onProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
    onReorder: () -> Unit = {},
    onSubmitText: (String) -> Unit = {},
) {
    val connected = connectedStores.isNotEmpty()
    val firstName = userName.trim().split(" ").firstOrNull().orEmpty()
    var logoTaps by remember { mutableStateOf(0) }
    var showDevDialog by remember { mutableStateOf(false) }
    var showTypeDialog by remember { mutableStateOf(false) }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse),
        label = "scale",
    )

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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brand.copy(alpha = 0.12f))
                    .clickable { onProfile() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = "Profile", tint = Brand)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "ShopMandate",
                color = Brand,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.clickable {
                    logoTaps++
                    if (logoTaps >= 10) {
                        showDevDialog = true
                        logoTaps = 0
                    }
                },
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.ReceiptLong,
                contentDescription = "Orders",
                tint = Ink,
                modifier = Modifier.size(24.dp).clickable { onOrders() },
            )
            Spacer(Modifier.width(16.dp))
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Ink,
                modifier = Modifier.size(24.dp).clickable { onSettings() },
            )
        }

        Spacer(Modifier.height(40.dp))
        Text(
            if (firstName.isNotBlank()) "Namaste, $firstName 👋" else "Namaste 👋",
            color = InkMuted,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text("Kya chahiye aaj?", color = Ink, fontWeight = FontWeight.Bold, fontSize = 32.sp)

        Spacer(Modifier.height(20.dp))

        // ---- Connection state ----
        if (connected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(SuccessGreen))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (connectedStores.size == 1) "1 store connected" else "${connectedStores.size} stores connected",
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            if (reorderSuggestion != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReorder() },
                    shape = RoundedCornerShape(16.dp),
                    color = Brand.copy(alpha = 0.06f),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Brand.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, tint = Brand, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Wapas order karein?", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(reorderSuggestion, color = InkMuted, fontSize = 13.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkMuted)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        } else {
            // Connect-first banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Cta.copy(alpha = 0.08f),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Cta, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Pehle apne stores connect karo", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Tabhi voice/photo se shopping shuru hogi.", color = InkMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onConnectStores,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Cta),
                    ) {
                        Text("Stores connect karo  →", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ---- Mic hero (disabled until connected) ----
        Box(
            modifier = Modifier
                .size(176.dp)
                .scale(scale)
                .alpha(if (connected) 1f else 0.35f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Brand, Cta)))
                .clickable { if (connected) onMicClick() else onConnectStores() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Bolke batao",
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (connected) "Bolke batao" else "🔒 Pehle connect karo",
            color = if (connected) Brand else InkMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )

        Spacer(Modifier.height(28.dp))
        // ---- Camera button ----
        OutlinedButton(
            onClick = onCameraClick,
            enabled = connected,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.35f)),
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = Ink)
            Spacer(Modifier.width(8.dp))
            Text("Ya photo dikhao", color = Ink)
        }

        Spacer(Modifier.height(28.dp))
        // ---- Suggestion chips (only when connected) ----
        if (connected) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Wireless earbuds", "Atta 5kg", "Phone charger").forEach { s ->
                    SuggestionChip(
                        onClick = { onSuggestionClick(s) },
                        label = { Text(s, fontSize = 13.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Brand.copy(alpha = 0.06f),
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        // ---- Bottom "type" pill → opens text input ----
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clickable { if (connected) showTypeDialog = true else onConnectStores() },
            shape = RoundedCornerShape(50),
            color = AppSurface,
            border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = InkMuted)
                Spacer(Modifier.width(10.dp))
                Text("…ya type karo", color = InkMuted, fontSize = 15.sp)
            }
        }

        if (showDevDialog) {
            DevUrlDialog(onDismiss = { showDevDialog = false })
        }
        if (showTypeDialog) {
            TypeInputDialog(
                onSubmit = { text ->
                    showTypeDialog = false
                    onSubmitText(text)
                },
                onDismiss = { showTypeDialog = false },
            )
        }
    }
}

@Composable
private fun TypeInputDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kya chahiye?", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                placeholder = { Text("e.g. wireless earbuds under 2000") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSubmit(text.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
            ) {
                Text("Search  →", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = InkMuted) }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true, name = "Home – not connected")
@Composable
private fun HomeNotConnectedPreview() {
    ShopMandateTheme { HomeScreen(connectedStores = emptySet()) }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home – connected")
@Composable
private fun HomeConnectedPreview() {
    ShopMandateTheme { HomeScreen(connectedStores = setOf("Zepto", "Swiggy")) }
}
