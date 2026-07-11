package com.shopmandate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** Root composable: renders the current screen from [ShopViewModel]. */
@Composable
fun ShopMandateApp(vm: ShopViewModel = viewModel()) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val connectedStores by vm.connectedStores.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val reorder by vm.reorderSuggestion.collectAsStateWithLifecycle()
    when (screen) {
        Screen.Home -> HomeScreen(
            connectedStores = connectedStores,
            userName = profile.name,
            reorderSuggestion = reorder,
            onMicClick = vm::goVoice,
            onCameraClick = vm::goCamera,
            onSuggestionClick = { vm.goVoice() },
            onConnectStores = vm::goConnect,
            onOrders = vm::goOrders,
            onProfile = vm::goProfile,
            onSettings = vm::goConnect,   // gear → manage stores (Connect)
            onReorder = vm::onReorder,
        )
        Screen.Voice -> VoiceScreen(onStop = vm::onAudioCaptured, onCancel = vm::goHome)
        Screen.Camera -> CameraScreen(onCaptured = vm::onImageCaptured, onClose = vm::goHome)
        Screen.Clarify -> ClarifyScreen(onNext = vm::goComparing, onBack = vm::goVoice)
        Screen.Comparing -> ComparingScreen(onNext = vm::goApprove, onBack = vm::goClarify)
        Screen.Approve -> ApproveScreen(
            onApprove = vm::goPay,   // stores already connected upfront
            onReject = vm::goComparing,
            onBack = vm::goComparing,
        )
        Screen.Connect -> ConnectScreen(
            connectedStores = connectedStores,
            onConnectStore = vm::startConnect,   // each store → its own OTP
            onDone = vm::goHome,
            onBack = vm::goHome,
        )
        Screen.Otp -> OtpScreen(
            storeName = vm.connectingStore ?: "Store",
            onVerified = vm::onOtpVerified,      // connects that store, returns to list
            onBack = vm::goConnect,
        )
        Screen.Pay -> PayScreen(onPaid = vm::goSuccess, onBack = vm::goApprove)
        Screen.Success -> SuccessScreen(onDone = vm::goHome, onTrack = vm::goOrders)
        Screen.Orders -> OrdersScreen(onBack = vm::goHome)
        Screen.Profile -> ProfileScreen(
            name = profile.name,
            phone = profile.phone,
            connectedStores = connectedStores,
            onSave = vm::updateProfile,
            onManageStores = vm::goConnect,
            onBack = vm::goHome,
        )
    }
}
