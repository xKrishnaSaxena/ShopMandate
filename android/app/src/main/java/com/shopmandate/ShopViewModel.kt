package com.shopmandate

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The flow state machine. Nav is a sealed [Screen] switched on in ShopMandateApp.
 */
sealed interface Screen {
    data object Home : Screen
    data object Voice : Screen
    data object Camera : Screen
    data object Clarify : Screen
    data object Comparing : Screen
    data object Approve : Screen
    data object Connect : Screen
    data object Otp : Screen
    data object Pay : Screen
    data object Success : Screen
    data object Orders : Screen
    data object Profile : Screen
}

/** Basic user profile. `name` is (later) passed to the agent so it addresses the user by name. */
data class UserProfile(val name: String, val phone: String)

class ShopViewModel : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    // Each store connects SEPARATELY (its own OAuth + OTP, e.g. Zepto vs Swiggy).
    // Shopping unlocks once at least one store is connected.
    private val _connectedStores = MutableStateFlow<Set<String>>(emptySet())
    val connectedStores: StateFlow<Set<String>> = _connectedStores.asStateFlow()

    // Which store's OTP is currently being entered (shown on the OTP screen).
    var connectingStore: String? = null
        private set

    // User profile — greet by name on Home, and (later) pass name to the agent in /session/start.
    private val _profile = MutableStateFlow(UserProfile(name = "Ravi Kumar", phone = "9876543210"))
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    // Reorder suggestion from order memory (backend will set this; seeded for the demo).
    private val _reorderSuggestion = MutableStateFlow<String?>("boAt Airdopes 141 — pichli baar ₹1,800 mein")
    val reorderSuggestion: StateFlow<String?> = _reorderSuggestion.asStateFlow()

    fun goHome() { _screen.value = Screen.Home }
    fun goVoice() { _screen.value = Screen.Voice }
    fun goCamera() { _screen.value = Screen.Camera }
    fun goClarify() { _screen.value = Screen.Clarify }
    fun goComparing() { _screen.value = Screen.Comparing }
    fun goApprove() { _screen.value = Screen.Approve }
    fun goConnect() { _screen.value = Screen.Connect }
    fun goPay() { _screen.value = Screen.Pay }
    fun goSuccess() { _screen.value = Screen.Success }
    fun goOrders() { _screen.value = Screen.Orders }
    fun goProfile() { _screen.value = Screen.Profile }
    fun onReorder() { _screen.value = Screen.Approve } // 1-tap reorder → straight to the cart

    fun updateProfile(name: String, phone: String) {
        _profile.value = UserProfile(name = name.ifBlank { _profile.value.name }, phone = phone)
    }

    // Captured multimodal input (base64), ready to POST to /session/start when the backend is wired.
    var capturedImageB64: String? = null
        private set
    var capturedAudioB64: String? = null
        private set
    var capturedText: String? = null
        private set

    fun onImageCaptured(b64: String) {
        capturedImageB64 = b64
        capturedAudioB64 = null
        capturedText = null
        goClarify()
    }

    fun onAudioCaptured(b64: String) {
        capturedAudioB64 = b64
        capturedImageB64 = null
        capturedText = null
        goClarify()
    }

    fun onTextInput(text: String) {
        capturedText = text
        capturedAudioB64 = null
        capturedImageB64 = null
        goClarify()
    }

    /** Start connecting a specific store → that store's own OTP. */
    fun startConnect(store: String) {
        connectingStore = store
        _screen.value = Screen.Otp
    }

    /** OTP verified for [connectingStore]: mark it connected, return to the connect list. */
    fun onOtpVerified() {
        connectingStore?.let { store ->
            _connectedStores.value = _connectedStores.value + store
        }
        connectingStore = null
        _screen.value = Screen.Connect
    }
}
