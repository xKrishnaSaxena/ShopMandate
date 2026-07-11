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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmandate.ui.theme.AppBg
import com.shopmandate.ui.theme.AppSurface
import com.shopmandate.ui.theme.Brand
import com.shopmandate.ui.theme.Ink
import com.shopmandate.ui.theme.InkMuted

/**
 * Screen 3 — Understanding + clarifying question. Mirrors design/clarification/screen.png.
 * Shows what the agent parsed (editable chips) and asks ONE question before proceeding.
 */
@Composable
fun ClarifyScreen(
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var type by remember { mutableStateOf("Wireless") }
    var budget by remember { mutableStateOf("₹2000") }

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
                    .background(Brand.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Brand)
            }
            Spacer(Modifier.width(12.dp))
            Text("ShopMandate", color = Brand, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Ink)
        }

        Spacer(Modifier.height(20.dp))

        // ---- Understanding card ----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Brand.copy(alpha = 0.06f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Here is what I understood so far. Tap to edit.",
                    color = InkMuted,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(12.dp))
                EditChip("Wireless earbuds")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditChip("Budget ₹2000")
                    EditChip("Qty 1")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- Question bubble ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brand.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Brand, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = Brand.copy(alpha = 0.06f)) {
                Text(
                    "Ek baat — wireless hi chahiye ya wired bhi chalega?",
                    color = Ink,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- TYPE ----
        SectionLabel("TYPE")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChoiceChip("Wireless", selected = type == "Wireless") { type = "Wireless" }
            ChoiceChip("Wired bhi ok", selected = type == "Wired") { type = "Wired" }
        }

        Spacer(Modifier.height(20.dp))

        // ---- ADJUST BUDGET ----
        SectionLabel("ADJUST BUDGET")
        Spacer(Modifier.height(8.dp))
        val options = listOf("₹1500", "₹2000", "₹2500", "Koi budget nahi ∞")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { label ->
                        SelectablePill(
                            text = label,
                            selected = budget == label,
                            onClick = { budget = label },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ---- Bottom: hint + CTA ----
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tap or just say your choice", color = InkMuted, fontSize = 14.sp)
        }
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Brand),
        ) {
            Text("Aage badho  →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(text, color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, color = Ink, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = InkMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Brand else Brand.copy(alpha = 0.06f),
    ) {
        Text(
            text,
            color = if (selected) Color.White else Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun SelectablePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Brand.copy(alpha = 0.08f) else AppSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) Brand else InkMuted.copy(alpha = 0.18f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text,
                color = if (selected) Brand else Ink,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 15.sp,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ClarifyScreenPreview() {
    ClarifyScreen()
}
