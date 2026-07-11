package com.shopmandate

import android.content.Intent
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

    // singleTask: the OAuth deep link (shopmandate://oauth/done) resumes this existing
    // instance instead of creating a new one — the ViewModel + Compose state survive, and
    // the app simply comes back to the front. The connect coroutine finishes on its own.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
