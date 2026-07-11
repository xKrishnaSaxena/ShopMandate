package com.shopmandate.ui.screens

import android.Manifest
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shopmandate.capture.WithPermission
import com.shopmandate.net.LiveCartItem
import com.shopmandate.net.Quote

/**
 * Live voice shopping — a real-time call with the Gemini-Live agent. The user just talks;
 * the agent searches real stores and speaks back, and product cards stream in live.
 */
@Composable
fun LiveScreen(
    status: String,
    connected: Boolean,
    userText: String,
    agentText: String,
    quotes: List<Quote>,
    cart: List<LiveCartItem>,
    cartTotal: Int,
    onStop: () -> Unit,
) {
    WithPermission(
        permission = Manifest.permission.RECORD_AUDIO,
        rationale = "Live baat karne ke liye mic access chahiye.",
    ) {
        LiveContent(status, connected, userText, agentText, quotes, cart, cartTotal, onStop)
    }
}

@Composable
private fun LiveContent(
    status: String,
    connected: Boolean,
    userText: String,
    agentText: String,
    quotes: List<Quote>,
    cart: List<LiveCartItem>,
    cartTotal: Int,
    onStop: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (connected) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF2A1A8A), Color(0xFF4F32E7), Color(0xFF241574))),
            )
            .systemBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // status pill
        Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.14f)) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (connected) Color(0xFF37E39B) else Color(0xFFFFC24B)))
                Spacer(Modifier.width(8.dp))
                Text(status, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // agent orb
        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF7A5CFF), Color(0xFF37E39B)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
        }

        Spacer(Modifier.height(24.dp))

        // transcript
        if (userText.isNotBlank()) {
            Bubble(label = "Aap", text = userText, align = Alignment.End, bg = Color.White.copy(alpha = 0.16f))
            Spacer(Modifier.height(10.dp))
        }
        if (agentText.isNotBlank()) {
            Bubble(label = "ShopMandate", text = agentText, align = Alignment.Start, bg = Color.White.copy(alpha = 0.10f))
        }
        if (userText.isBlank() && agentText.isBlank()) {
            Text(
                "Bolo — \"mujhe wireless earbuds chahiye 2000 ke andar\"",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        // live product cards
        if (quotes.isNotEmpty()) {
            Text(
                "Live deals ${quotes.size}",
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(quotes) { q -> LiveQuoteCard(q) }
            }
            Spacer(Modifier.height(16.dp))
        }

        // running cart (multi-item) — grows as the user adds things by voice
        if (cart.isNotEmpty()) {
            CartBar(cart, cartTotal)
            Spacer(Modifier.height(16.dp))
        }

        // end call
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFE5484D))
                .clickable { onStop() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "End", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Baat khatam karo", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

@Composable
private fun CartBar(cart: List<LiveCartItem>, total: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.16f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = Color(0xFF9DFFD4), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cart · ${cart.size} item", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("₹${"%,d".format(total)}", color = Color(0xFF9DFFD4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            cart.forEach { item ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("•", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.name.ifBlank { item.query },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.priceInr > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("₹${"%,d".format(item.priceInr)}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "\"aur kuch chahiye?\" — bolo, ya \"bas itna hi\" bolke checkout karo",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun Bubble(label: String, text: String, align: Alignment.Horizontal, bg: Color) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = bg) {
            Text(
                text,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun LiveQuoteCard(q: Quote) {
    Surface(
        modifier = Modifier.width(140.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!q.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = q.imageUrl,
                        contentDescription = q.productName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(q.store, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                q.productName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text("₹${"%,d".format(q.priceInr)}", color = Color(0xFF9DFFD4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
