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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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

/**
 * Profile / account screen. Name + phone are editable and stored in ShopViewModel.
 * The name will (later) be passed to the agent so it addresses the user by name.
 * Opened from the Home top-bar avatar icon.
 */
@Composable
fun ProfileScreen(
    name: String,
    phone: String,
    connectedStores: Set<String> = emptySet(),
    onSave: (String, String) -> Unit = { _, _ -> },
    onManageStores: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var nameField by remember { mutableStateOf(name) }
    var phoneField by remember { mutableStateOf(phone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
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
            Text("Mera Account", color = Ink, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }

        Spacer(Modifier.height(16.dp))
        // ---- Avatar + live name ----
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brand.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Brand, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(nameField.ifBlank { "—" }, color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("+91 $phoneField", color = InkMuted, fontSize = 14.sp)
        }

        Spacer(Modifier.height(28.dp))
        // ---- Name ----
        FieldLabel("NAAM")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = nameField,
            onValueChange = { nameField = it },
            placeholder = { Text("Aapka naam", color = InkMuted) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Brand, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Agent aapko is naam se bulaega.", color = InkMuted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))
        // ---- Phone ----
        FieldLabel("PHONE")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = phoneField,
            onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) phoneField = it },
            prefix = { Text("+91  ", color = Ink, fontWeight = FontWeight.Bold) },
            placeholder = { Text("98765 43210", color = InkMuted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        // ---- Connected stores ----
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, InkMuted.copy(alpha = 0.12f)),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Connected stores", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Manage →",
                        color = Brand,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { onManageStores() },
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (connectedStores.isEmpty()) {
                    Text("Abhi koi store connected nahi.", color = InkMuted, fontSize = 13.sp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        connectedStores.forEach { store ->
                            Surface(shape = RoundedCornerShape(50), color = SuccessGreen.copy(alpha = 0.10f)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(store, color = SuccessGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                onSave(nameField.trim(), phoneField)
                onBack()
            },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Cta),
        ) {
            Text("Save karo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = InkMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ProfileScreenPreview() {
    ProfileScreen(name = "Ravi Kumar", phone = "9876543210", connectedStores = setOf("Zepto"))
}
