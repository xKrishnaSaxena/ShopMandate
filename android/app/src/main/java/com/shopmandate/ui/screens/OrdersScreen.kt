package com.shopmandate.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.shopmandate.net.OrderDto

/**
 * Orders — user's past orders (history). Entry point: Home top-bar receipt icon,
 * and Success screen's "Track order". Real orders come from GET /api/orders;
 * when there are none we show an empty state (no fake/mock history).
 */
@Composable
fun OrdersScreen(
    orders: List<OrderDto> = emptyList(),
    onBack: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .systemBarsPadding(),
    ) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brand.copy(alpha = 0.08f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Brand,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Mere Orders", color = Ink, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(
                    if (orders.size == 1) "1 order" else "${orders.size} orders",
                    color = InkMuted,
                    fontSize = 13.sp,
                )
            }
        }

        if (orders.isEmpty()) {
            EmptyOrders()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(orders) { order -> OrderCard(order) }
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptyOrders() {
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brand.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Brand, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Abhi koi order nahi", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Jab aap kuch khareedenge, aapka order yahan dikhega.",
            color = InkMuted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun OrderCard(order: OrderDto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brand.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Brand, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (order.qty > 1) "${order.product}  ×${order.qty}" else order.product,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storefront, contentDescription = null, tint = InkMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${order.store} · ${order.date}", color = InkMuted, fontSize = 12.sp)
                }
                if (order.addressLine != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = InkMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            order.addressLabel?.let { "$it · ${order.addressLine}" } ?: order.addressLine,
                            color = InkMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                StatusChip(order.status, order.delivered)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${order.priceInr}", color = Brand, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (order.itemPriceInr != null && order.deliveryFeeInr != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "₹${order.itemPriceInr} + ₹${order.deliveryFeeInr} del",
                        color = InkMuted, fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(order.orderId, color = InkMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StatusChip(status: String, delivered: Boolean) {
    val color = if (delivered) SuccessGreen else Cta
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(
            status,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OrdersScreenPreview() {
    OrdersScreen()
}
