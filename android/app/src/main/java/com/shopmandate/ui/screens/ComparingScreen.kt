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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingDown
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import coil.compose.AsyncImage
import com.shopmandate.net.Quote
import com.shopmandate.net.Winner

private const val PRODUCT = "boAt Airdopes 141 – Wireless Earbuds"

private fun isFast(delivery: String): Boolean =
    listOf("min", "aaj", "today", "instant").any { delivery.contains(it, ignoreCase = true) }

/**
 * Screen 4 — Comparing stores. Mirrors design/comparing_stores/screen.png.
 * Store A (muted, ₹1,950) vs Store B (winner, ₹1,800, "₹150 sasta").
 * Tap the winning Store B card to proceed.
 */
@Composable
fun ComparingScreen(
    quotes: List<Quote> = emptyList(),
    winner: Winner? = null,
    product: String = PRODUCT,
    steps: List<String> = emptyList(),
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    onSpeak: (String) -> Unit = {},
) {
    // Auto voice-out the agent's reasoning once (Gemini TTS wow-factor).
    var spoken by remember { mutableStateOf(false) }
    LaunchedEffect(winner) {
        val w = winner
        if (w != null && !spoken) {
            spoken = true
            onSpeak("${w.store} sabse achha deal de raha hai. ${w.why}")
        }
    }

    val maxPrice = quotes.maxOfOrNull { it.priceInr } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.Sync, contentDescription = null, tint = Brand, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (winner != null) "Best deal mil gaya!" else "Best deal dhoond raha hoon…",
            color = Brand,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
        )

        // ---- Live A2A haggle transcript ----
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Brand.copy(alpha = 0.05f),
            ) {
                Column(Modifier.padding(14.dp)) {
                    steps.forEach { s ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Text("•", color = Brand, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(s, color = InkMuted, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- Ranked product cards (cheapest/best first, highlighted) ----
        quotes.forEachIndexed { i, q ->
            val isWinner = winner != null && winner.store == q.store && winner.priceInr == q.priceInr
            if (i == 1) {
                // divider label between the best pick and the alternatives
                Spacer(Modifier.height(6.dp))
                Text(
                    "Baaki options",
                    color = InkMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
            StoreCard(
                storeName = q.store,
                productName = q.productName.ifBlank { product },
                imageUrl = q.imageUrl,
                priceText = "₹${"%,d".format(q.priceInr)}",
                strikethrough = false,
                delivery = "Delivery: ${q.delivery}",
                fast = isFast(q.delivery),
                highlighted = isWinner,
                savingBadge = if (isWinner && maxPrice > q.priceInr) "₹${maxPrice - q.priceInr} sasta" else null,
                onClick = if (isWinner) onNext else null,   // best pick proceeds; others are for comparison
            )
            if (i < quotes.lastIndex) Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(24.dp))

        // ---- Winner status pill ----
        if (winner != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = AppSurface,
                border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.15f)),
                modifier = Modifier.clickable { onNext() },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("${winner.store} ne behtar offer diya", color = Ink, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("${winner.store} pe tap karke aage badho", color = InkMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StoreCard(
    storeName: String,
    productName: String = PRODUCT,
    imageUrl: String? = null,
    priceText: String,
    strikethrough: Boolean,
    delivery: String,
    fast: Boolean,
    highlighted: Boolean,
    savingBadge: String?,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth()
    val clickModifier = if (onClick != null) base.clickable { onClick() } else base
    Surface(
        modifier = clickModifier,
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) Cta.copy(alpha = 0.04f) else AppSurface,
        border = if (highlighted) {
            BorderStroke(2.dp, Cta)
        } else {
            BorderStroke(1.dp, InkMuted.copy(alpha = 0.12f))
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            // Store row + saving badge
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (highlighted) Cta.copy(alpha = 0.15f) else Brand.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = if (highlighted) Cta else Brand,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(storeName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (savingBadge != null) {
                    Surface(shape = RoundedCornerShape(50), color = Cta) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.TrendingDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(savingBadge, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // Real product image (Zepto/store CDN), else placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = productName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Filled.Headphones, contentDescription = null, tint = InkMuted.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(productName, color = InkMuted, fontSize = 12.sp)

            Spacer(Modifier.height(10.dp))
            Text(
                priceText,
                color = if (highlighted) Cta else InkMuted,
                fontWeight = FontWeight.Bold,
                fontSize = if (highlighted) 26.sp else 20.sp,
                textDecoration = if (strikethrough) TextDecoration.LineThrough else TextDecoration.None,
            )

            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(50), color = Brand.copy(alpha = 0.06f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (fast) Icons.Filled.Bolt else Icons.Filled.LocalShipping,
                        contentDescription = null,
                        tint = if (fast) Cta else InkMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(delivery, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ComparingScreenPreview() {
    ComparingScreen()
}
