package com.shopmandate.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmandate.net.AddressDto
import com.shopmandate.ui.theme.AppBg
import com.shopmandate.ui.theme.AppSurface
import com.shopmandate.ui.theme.Brand
import com.shopmandate.ui.theme.Ink
import com.shopmandate.ui.theme.InkMuted

/**
 * Address picker shown before placing a real order — the user chooses which saved delivery
 * address the order ships to. Addresses come from the merchant's MCP (GET /merchants/{id}/addresses).
 */
@Composable
fun AddressScreen(
    addresses: List<AddressDto> = emptyList(),
    selectedId: String? = null,
    onSelect: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Delivery address", color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Order kahan bhejein?", color = InkMuted, fontSize = 13.sp)
            }
        }

        if (addresses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Koi saved address nahi mila", color = InkMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(addresses) { addr ->
                    AddressCard(addr, selected = addr.id == selectedId, onClick = { addr.id?.let(onSelect) })
                }
            }
        }
    }
}

@Composable
private fun AddressCard(addr: AddressDto, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Brand.copy(alpha = 0.06f) else AppSurface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Brand else InkMuted.copy(alpha = 0.12f)),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = Brand, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(addr.label ?: "Address", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(addr.line ?: "", color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = Brand, modifier = Modifier.size(22.dp))
            }
        }
    }
}
