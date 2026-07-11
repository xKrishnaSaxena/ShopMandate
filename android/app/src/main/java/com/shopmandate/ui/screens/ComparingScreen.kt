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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private const val PRODUCT = "boAt Airdopes 141 – Wireless Earbuds"

/**
 * Screen 4 — Comparing stores. Mirrors design/comparing_stores/screen.png.
 * Store A (muted, ₹1,950) vs Store B (winner, ₹1,800, "₹150 sasta").
 * Tap the winning Store B card to proceed.
 */
@Composable
fun ComparingScreen(
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
) {
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
            "Best deal dhoond raha hoon…",
            color = Brand,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
        )

        Spacer(Modifier.height(28.dp))

        StoreCard(
            storeName = "Store A",
            priceText = "₹1,950",
            strikethrough = true,
            delivery = "Delivery: kal",
            fast = false,
            highlighted = false,
            savingBadge = null,
        )

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brand),
            contentAlignment = Alignment.Center,
        ) {
            Text("VS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))

        StoreCard(
            storeName = "Store B",
            priceText = "₹1,800",
            strikethrough = false,
            delivery = "Delivery: aaj",
            fast = true,
            highlighted = true,
            savingBadge = "₹150 sasta",
            onClick = onNext,
        )

        Spacer(Modifier.height(24.dp))

        // ---- Winner status pill ----
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
                Text("Store B ne behtar offer diya", color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Store B pe tap karke aage badho", color = InkMuted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StoreCard(
    storeName: String,
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
            // Product image placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Headphones, contentDescription = null, tint = InkMuted.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text(PRODUCT, color = InkMuted, fontSize = 12.sp)

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
