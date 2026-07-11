package com.shopmandate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmandate.net.DevConfig

/**
 * Hidden dev dialog to view/change the backend base URL. Opened by tapping the
 * "ShopMandate" logo 10×. Save + restart the app → every request uses the new URL.
 */
@Composable
fun DevUrlDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = remember { DevConfig.getBaseUrl(context) }
    var url by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backend URL (dev)") },
        text = {
            Column {
                Text("Current: $current", fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Base URL") },
                    placeholder = { Text("http://192.168.x.x:8000/") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Save karke app RESTART karo — poora app yahi URL use karega.",
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                DevConfig.setBaseUrl(context, url)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
