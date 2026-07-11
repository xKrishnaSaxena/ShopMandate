package com.shopmandate.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmandate.ChatMsg
import com.shopmandate.ui.theme.AppBg
import com.shopmandate.ui.theme.AppSurface
import com.shopmandate.ui.theme.Brand
import com.shopmandate.ui.theme.Ink
import com.shopmandate.ui.theme.InkMuted

/**
 * Screen 3 — an interactive chat with the shopping agent. The user can type / tap
 * quick-replies to refine intent, ask for reviews, set up recurring reorder or a
 * price-drop auto-buy, and edit the parsed chips inline before comparing stores.
 */
@Composable
fun ClarifyScreen(
    product: String = "Product",
    budgetInr: Int? = null,
    qty: Int = 1,
    messages: List<ChatMsg> = emptyList(),
    suggestions: List<String> = emptyList(),
    thinking: Boolean = false,
    onSend: (String) -> Unit = {},
    onEditIntent: (String?, Int?, Int?) -> Unit = { _, _, _ -> },
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the newest message in view (including the typing indicator).
    LaunchedEffect(messages.size, thinking) {
        val count = messages.size + if (thinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .systemBarsPadding()
            .imePadding(),
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
                    .background(Brand.copy(alpha = 0.12f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand)
            }
            Spacer(Modifier.width(12.dp))
            Text("ShopMandate", color = Brand, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Ink)
        }

        // ---- Editable understanding chips (pinned) ----
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = Brand.copy(alpha = 0.06f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("Ye samajh aaya · tap to edit", color = InkMuted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditChip(product.ifBlank { "Product" }) { editing = true }
                    EditChip(budgetInr?.let { "₹${"%,d".format(it)}" } ?: "No budget") { editing = true }
                    EditChip("Qty $qty") { editing = true }
                }
            }
        }

        // ---- Chat transcript ----
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(messages) { _, m ->
                if (m.role == "user") UserBubble(m) else AgentBubble(m)
            }
            if (thinking) {
                item { TypingBubble() }
            }
        }

        // ---- Quick-reply suggestions ----
        if (suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { s ->
                    SuggestionChip(s) { onSend(s) }
                }
            }
        }

        // ---- Input bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message… (type karo)", color = InkMuted, fontSize = 15.sp) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
            )
            Spacer(Modifier.width(8.dp))
            val canSend = input.isNotBlank()
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Brand else Brand.copy(alpha = 0.3f))
                    .clickable(enabled = canSend) {
                        onSend(input.trim()); input = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White,
                    modifier = Modifier.size(22.dp))
            }
        }

        // ---- CTA ----
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Brand),
        ) {
            Text("Aage badho  →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(10.dp))
    }

    if (editing) {
        EditIntentDialog(
            product = product,
            budgetInr = budgetInr,
            qty = qty,
            onDismiss = { editing = false },
            onSave = { p, b, q -> onEditIntent(p, b, q); editing = false },
        )
    }
}

@Composable
private fun AgentBubble(m: ChatMsg) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brand.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Brand, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = Brand.copy(alpha = 0.06f),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(m.text, color = Ink, fontSize = 16.sp)
                m.genImageB64?.let { b64 ->
                    decodeB64(stripDataUri(b64))?.let { img ->
                        Spacer(Modifier.height(8.dp))
                        Image(
                            bitmap = img,
                            contentDescription = "Product preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(m: ChatMsg) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = Brand,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                m.imageB64?.let { b64 ->
                    decodeB64(stripDataUri(b64))?.let { img ->
                        Image(
                            bitmap = img,
                            contentDescription = "Your photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Text(m.text, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brand.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Brand, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = Brand.copy(alpha = 0.06f)) {
            Text("soch raha hoon…", color = InkMuted, fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = AppSurface,
        border = BorderStroke(1.dp, Brand.copy(alpha = 0.4f)),
    ) {
        Text(text, color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

@Composable
private fun EditChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, color = Ink, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = InkMuted, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun EditIntentDialog(
    product: String,
    budgetInr: Int?,
    qty: Int,
    onDismiss: () -> Unit,
    onSave: (String?, Int?, Int?) -> Unit,
) {
    var p by remember { mutableStateOf(product) }
    var b by remember { mutableStateOf(budgetInr?.toString() ?: "") }
    var q by remember { mutableStateOf(qty.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    p.trim().ifBlank { product },
                    b.trim().toIntOrNull(),                 // blank → null → "No budget"
                    q.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
                )
            }) { Text("Save", color = Brand, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkMuted) } },
        title = { Text("Edit details", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = p, onValueChange = { p = it },
                    label = { Text("Product") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = b, onValueChange = { b = it.filter(Char::isDigit) },
                    label = { Text("Budget ₹ (khaali = koi budget nahi)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = q, onValueChange = { q = it.filter(Char::isDigit) },
                    label = { Text("Quantity") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** Strip an optional `data:<mime>;base64,` prefix so BitmapFactory gets raw base64. */
private fun stripDataUri(b64: String): String =
    if (b64.startsWith("data:")) b64.substringAfter(",", b64) else b64

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ClarifyScreenPreview() {
    ClarifyScreen(
        product = "Wireless earbuds",
        budgetInr = 2000,
        qty = 1,
        messages = listOf(
            ChatMsg("user", "earbuds chahiye 2000 ke andar"),
            ChatMsg("agent", "Samajh gaya! Gaming ke liye chahiye ya calls ke liye?"),
        ),
        suggestions = listOf("Log kya bolte hain? ⭐", "Sasta similar", "Har hafte mangwa do 🔁"),
    )
}
