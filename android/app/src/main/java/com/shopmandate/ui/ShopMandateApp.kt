package com.shopmandate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmandate.Screen
import com.shopmandate.ShopViewModel
import com.shopmandate.ui.screens.ApproveScreen
import com.shopmandate.ui.screens.CameraScreen
import com.shopmandate.ui.screens.ClarifyScreen
import com.shopmandate.ui.screens.ComparingScreen
import com.shopmandate.ui.screens.ConnectScreen
import com.shopmandate.ui.screens.HomeScreen
import com.shopmandate.ui.screens.OrdersScreen
import com.shopmandate.ui.screens.OtpScreen
import com.shopmandate.ui.screens.PayScreen
import com.shopmandate.ui.screens.ProfileScreen
import com.shopmandate.ui.screens.SuccessScreen
import com.shopmandate.ui.screens.VoiceScreen

/** Root composable: renders the current screen from [ShopViewModel], driven by real backend state. */
@Composable
fun ShopMandateApp(vm: ShopViewModel = viewModel()) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val connectedStores by vm.connectedStores.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val reorder by vm.reorderSuggestion.collectAsStateWithLifecycle()

    val intent by vm.intent.collectAsStateWithLifecycle()
    val clarifyQuestion by vm.clarifyQuestion.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val winner by vm.winner.collectAsStateWithLifecycle()
    val cart by vm.cart.collectAsStateWithLifecycle()
    val haggleSteps by vm.haggleSteps.collectAsStateWithLifecycle()
    val receipt by vm.receipt.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val visualB64 by vm.visualB64.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.Home -> HomeScreen(
                connectedStores = connectedStores,
                userName = profile.name,
                reorderSuggestion = reorder,
                onMicClick = vm::goVoice,
                onCameraClick = vm::goCamera,
                onSuggestionClick = vm::startText,   // tap chip → direct text search
                onConnectStores = vm::goConnect,
                onOrders = vm::goOrders,
                onProfile = vm::goProfile,
                onSettings = vm::goConnect,
                onReorder = vm::onReorder,
                onSubmitText = vm::startText,         // type pill → text search
            )
            Screen.Voice -> VoiceScreen(onStop = vm::onAudioCaptured, onCancel = vm::goHome)
            Screen.Camera -> CameraScreen(onCaptured = vm::onImageCaptured, onClose = vm::goHome)
            Screen.Clarify -> ClarifyScreen(
                product = intent?.product ?: "Product",
                budgetInr = intent?.budgetInr,
                qty = intent?.qty ?: 1,
                question = clarifyQuestion,
                onNext = vm::search,             // "Aage badho" → real store search
                onBack = vm::goVoice,
            )
            Screen.Comparing -> ComparingScreen(
                quotes = quotes,
                winner = winner,
                product = cart?.item ?: intent?.product ?: "Product",
                steps = haggleSteps,
                onNext = vm::goApprove,
                onBack = vm::goClarify,
                onSpeak = vm::speak,             // Gemini TTS voice-out
            )
            Screen.Approve -> ApproveScreen(
                productName = cart?.item ?: intent?.product ?: "Product",
                subtitle = buildSubtitle(cart?.color, cart?.warranty),
                priceInr = cart?.priceInr ?: winner?.priceInr ?: 0,
                qty = cart?.qty ?: intent?.qty ?: 1,
                reason = winner?.why ?: "Best value chuna",
                delivery = cart?.delivery ?: "jaldi",
                visualB64 = visualB64,
                onApprove = vm::goPay,
                onReject = vm::goComparing,
                onBack = vm::goComparing,
                onVisualize = vm::visualize,     // Nano Banana product image
            )
            Screen.Connect -> ConnectScreen(
                connectedStores = connectedStores,
                onConnectStore = vm::startConnect,
                onDone = vm::goHome,
                onBack = vm::goHome,
            )
            Screen.Otp -> OtpScreen(
                storeName = vm.connectingStore ?: "Store",
                onVerified = vm::onOtpVerified,
                onBack = vm::goConnect,
            )
            Screen.Pay -> PayScreen(
                amountInr = cart?.priceInr ?: winner?.priceInr ?: 0,
                product = cart?.item ?: intent?.product ?: "Product",
                onPay = vm::pay,                 // real backend payment (P3P/mock) → receipt
                onBack = vm::goApprove,
            )
            Screen.Success -> SuccessScreen(
                item = receipt?.item ?: cart?.item ?: "Order",
                priceInr = receipt?.priceInr ?: cart?.priceInr ?: 0,
                store = receipt?.store ?: winner?.store ?: "Store",
                orderId = receipt?.orderId ?: "#SM-0000",
                delivery = receipt?.delivery ?: "jaldi",
                onDone = vm::goHome,
                onTrack = vm::goOrders,
            )
            Screen.Orders ->
                if (orders.isEmpty()) OrdersScreen(onBack = vm::goHome)
                else OrdersScreen(orders = orders, onBack = vm::goHome)
            Screen.Profile -> ProfileScreen(
                name = profile.name,
                phone = profile.phone,
                connectedStores = connectedStores,
                onSave = vm::updateProfile,
                onManageStores = vm::goConnect,
                onBack = vm::goHome,
            )
        }

        loading?.let { LoadingOverlay(it) }
        error?.let { ErrorBanner(it, onDismiss = vm::clearError) }
    }
}

private fun buildSubtitle(color: String?, warranty: String?): String {
    val parts = listOfNotNull(
        color?.takeIf { it.isNotBlank() }?.let { "Color: $it" },
        warranty?.takeIf { it.isNotBlank() },
    )
    return if (parts.isEmpty()) "Best match for your request" else parts.joinToString(" • ")
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1A2E)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(16.dp))
                Text(message, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { onDismiss() },
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFB00020),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Kuch gadbad hui", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.size(4.dp))
                Text(message, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                Spacer(Modifier.size(6.dp))
                Text("Tap to dismiss", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
}
