package com.shopmandate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shopmandate.ui.ShopMandateApp
import com.shopmandate.ui.theme.ShopMandateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShopMandateTheme {
                ShopMandateApp()
            }
        }
    }
}
