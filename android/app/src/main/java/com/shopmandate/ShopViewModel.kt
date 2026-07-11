package com.shopmandate

import android.app.Application
import android.media.MediaPlayer
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmandate.capture.LiveSession
import com.shopmandate.net.ApiClient
import com.shopmandate.net.ApiService
import com.shopmandate.net.Cart
import com.shopmandate.net.ClarifyAnswers
import com.shopmandate.net.ClarifyRequest
import com.shopmandate.net.ConnectStartRequest
import com.shopmandate.net.ConnectVerifyRequest
import com.shopmandate.net.Intent
import com.shopmandate.net.OrderDto
import com.shopmandate.net.PayRequest
import com.shopmandate.net.Quote
import com.shopmandate.net.Receipt
import com.shopmandate.net.SayRequest
import com.shopmandate.net.StartRequest
import com.shopmandate.net.StartResponse
import com.shopmandate.net.VisualizeRequest
import com.shopmandate.net.Winner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** The flow state machine. Nav is a sealed [Screen] switched on in ShopMandateApp. */
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
    data object Live : Screen
}

data class UserProfile(val name: String, val phone: String)

class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private var api: ApiService = ApiClient.create(getApplication())
    /** Call after changing the base URL in the dev dialog. */
    fun refreshApi() { api = ApiClient.create(getApplication()) }

    // ---- navigation ----
    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _connectedStores = MutableStateFlow<Set<String>>(emptySet())
    val connectedStores: StateFlow<Set<String>> = _connectedStores.asStateFlow()

    var connectingStore: String? = null
        private set

    private val _profile = MutableStateFlow(UserProfile(name = "Utsav", phone = "9876543210"))
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _reorderSuggestion = MutableStateFlow<String?>("boAt Airdopes 141 — pichli baar ₹1,800 mein")
    val reorderSuggestion: StateFlow<String?> = _reorderSuggestion.asStateFlow()

    // ---- backend-driven state ----
    var sessionId: String? = null
        private set
    private val _intent = MutableStateFlow<Intent?>(null)
    val intent: StateFlow<Intent?> = _intent.asStateFlow()
    private val _transcript = MutableStateFlow<String?>(null)
    val transcript: StateFlow<String?> = _transcript.asStateFlow()
    private val _clarifyQuestion = MutableStateFlow<String?>(null)
    val clarifyQuestion: StateFlow<String?> = _clarifyQuestion.asStateFlow()
    private val _quotes = MutableStateFlow<List<Quote>>(emptyList())
    val quotes: StateFlow<List<Quote>> = _quotes.asStateFlow()
    private val _winner = MutableStateFlow<Winner?>(null)
    val winner: StateFlow<Winner?> = _winner.asStateFlow()
    private val _cart = MutableStateFlow<Cart?>(null)
    val cart: StateFlow<Cart?> = _cart.asStateFlow()
    private val _haggleSteps = MutableStateFlow<List<String>>(emptyList())
    val haggleSteps: StateFlow<List<String>> = _haggleSteps.asStateFlow()
    private val _receipt = MutableStateFlow<Receipt?>(null)
    val receipt: StateFlow<Receipt?> = _receipt.asStateFlow()
    private val _orders = MutableStateFlow<List<OrderDto>>(emptyList())
    val orders: StateFlow<List<OrderDto>> = _orders.asStateFlow()
    private val _loading = MutableStateFlow<String?>(null)
    val loading: StateFlow<String?> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _visualB64 = MutableStateFlow<String?>(null)
    val visualB64: StateFlow<String?> = _visualB64.asStateFlow()
    private val _researchNote = MutableStateFlow<String?>(null)
    val researchNote: StateFlow<String?> = _researchNote.asStateFlow()

    // ---- live voice session ----
    private var live: LiveSession? = null
    private var liveNewUserTurn = true
    private val _liveConnected = MutableStateFlow(false)
    val liveConnected: StateFlow<Boolean> = _liveConnected.asStateFlow()
    private val _liveStatus = MutableStateFlow("Connecting…")
    val liveStatus: StateFlow<String> = _liveStatus.asStateFlow()
    private val _liveUserText = MutableStateFlow("")
    val liveUserText: StateFlow<String> = _liveUserText.asStateFlow()
    private val _liveAgentText = MutableStateFlow("")
    val liveAgentText: StateFlow<String> = _liveAgentText.asStateFlow()
    private val _liveQuotes = MutableStateFlow<List<Quote>>(emptyList())
    val liveQuotes: StateFlow<List<Quote>> = _liveQuotes.asStateFlow()

    // ---- nav helpers (used by screens) ----
    fun goHome() { _screen.value = Screen.Home }
    fun goVoice() { _screen.value = Screen.Voice }
    fun goCamera() { _screen.value = Screen.Camera }
    fun goClarify() { _screen.value = Screen.Clarify }
    fun goComparing() { _screen.value = Screen.Comparing }
    fun goApprove() { _screen.value = Screen.Approve }
    fun goConnect() { _screen.value = Screen.Connect }
    fun goPay() { _screen.value = Screen.Pay }
    fun goSuccess() { _screen.value = Screen.Success }
    fun goOrders() { loadOrders(); _screen.value = Screen.Orders }
    fun goProfile() { _screen.value = Screen.Profile }
    fun onReorder() { _screen.value = Screen.Approve }
    fun clearError() { _error.value = null }

    fun updateProfile(name: String, phone: String) {
        _profile.value = UserProfile(name = name.ifBlank { _profile.value.name }, phone = phone)
    }

    // captured multimodal input (base64)
    var capturedImageB64: String? = null
        private set
    var capturedAudioB64: String? = null
        private set

    // ---- backend flow ----
    fun startText(text: String) = job("Samajh raha hoon…") {
        applyStart(api.start(StartRequest(inputType = "text", text = text, userName = _profile.value.name)))
    }

    fun onAudioCaptured(b64: String) {
        if (b64.isBlank()) { _error.value = "Recording nahi hui — mic permission check karo"; return }
        capturedAudioB64 = b64
        // Tell Gemini the real format (raw AAC), else it defaults to audio/wav and misreads.
        val payload = if (b64.startsWith("data:")) b64 else "data:audio/aac;base64,$b64"
        job("Sun raha hoon…") {
            applyStart(api.start(StartRequest(inputType = "voice", audioB64 = payload, userName = _profile.value.name)))
        }
    }

    fun onImageCaptured(b64: String) {
        if (b64.isBlank()) { _error.value = "Photo capture nahi hui"; return }
        capturedImageB64 = b64
        val payload = if (b64.startsWith("data:")) b64 else "data:image/jpeg;base64,$b64"
        job("Dekh raha hoon…") {
            applyStart(api.start(StartRequest(inputType = "photo", imageB64 = payload, userName = _profile.value.name)))
        }
    }

    private fun applyStart(r: StartResponse) {
        sessionId = r.sessionId
        _intent.value = r.parsedIntent
        _transcript.value = r.transcript
        _clarifyQuestion.value = r.clarifyingQuestion
        _screen.value = Screen.Clarify   // Clarify screen shows parsed intent + (if any) the question
    }

    fun clarify(type: String?, budgetInr: Int?) = job("…") {
        val id = sessionId ?: return@job
        val r = api.clarify(id, ClarifyRequest(ClarifyAnswers(type = type, budgetInr = budgetInr)))
        _intent.value = r.parsedIntent
    }

    /** Apply the clarify choice (brand/budget) then immediately search — one smooth step. */
    fun submitClarify(type: String?, budgetInr: Int?) = job("Stores compare kar raha hoon…") {
        val id = sessionId ?: return@job
        if (type != null || budgetInr != null) {
            val r = api.clarify(id, ClarifyRequest(ClarifyAnswers(type = type, budgetInr = budgetInr)))
            _intent.value = r.parsedIntent
        }
        val s = api.search(id)
        _quotes.value = s.quotes
        _winner.value = s.winner
        _cart.value = s.cart
        _haggleSteps.value = s.steps
        _screen.value = Screen.Comparing
    }

    fun search() = job("Stores compare kar raha hoon…") {
        val id = sessionId ?: return@job
        val r = api.search(id)
        _quotes.value = r.quotes
        _winner.value = r.winner
        _cart.value = r.cart
        _haggleSteps.value = r.steps
        _screen.value = Screen.Comparing
    }

    fun startConnect(store: String) = job(null) {
        connectingStore = store
        api.connectStart(store, ConnectStartRequest(phone = _profile.value.phone))
        _screen.value = Screen.Otp
    }

    fun onOtpVerified() = job(null) {
        val store = connectingStore ?: return@job
        val r = api.connectVerify(store, ConnectVerifyRequest(otp = "123456"))
        _connectedStores.value = r.connectedStores.toSet()
        connectingStore = null
        _screen.value = Screen.Connect
    }

    fun pay(upiApp: String = "gpay") = job("Payment ho raha hai…") {
        val id = sessionId ?: return@job
        val r = api.pay(id, PayRequest(upiApp = upiApp))
        _receipt.value = r.receipt
        _screen.value = Screen.Success
    }

    fun loadOrders() = job(null) { _orders.value = api.orders().orders }

    // ---- wow-factors ----
    /** Agent speaks the given line (Hinglish TTS). */
    fun speak(text: String) = job(null) { playWav(api.say(SayRequest(text)).audioB64) }

    /** Generate a Nano-Banana product visual for the current cart/intent. */
    fun visualize() = job(null) {
        val product = _cart.value?.item ?: _intent.value?.product ?: return@job
        _visualB64.value = api.visualize(VisualizeRequest(product = product)).imageB64
    }

    /** Deep-research "best value" one-liner. */
    fun loadResearch() = job(null) {
        val id = sessionId ?: return@job
        _researchNote.value = api.research(id).note
    }

    // ---- live voice ----
    fun startLive() {
        _liveConnected.value = false
        _liveStatus.value = "Connecting…"
        _liveUserText.value = ""
        _liveAgentText.value = ""
        _liveQuotes.value = emptyList()
        liveNewUserTurn = true
        live?.stop()
        live = LiveSession(::onLiveEvent).also {
            it.start(ApiClient.liveWsUrl(getApplication()), _profile.value.name)
        }
        _screen.value = Screen.Live
    }

    fun stopLive() {
        live?.stop()
        live = null
        _screen.value = Screen.Home
    }

    private fun onLiveEvent(e: LiveSession.LiveEvent) {
        when (e) {
            is LiveSession.LiveEvent.Connected -> {
                _liveConnected.value = e.connected
                if (e.connected) _liveStatus.value = "Bolo… sun raha hoon 🎙️"
            }
            is LiveSession.LiveEvent.Transcript -> {
                if (e.role == "user") {
                    if (liveNewUserTurn) {
                        _liveUserText.value = ""
                        _liveAgentText.value = ""
                        liveNewUserTurn = false
                    }
                    _liveUserText.value += e.text
                    _liveStatus.value = "Sun raha hoon…"
                } else {
                    _liveAgentText.value += e.text
                    _liveStatus.value = "Bol raha hoon…"
                }
            }
            is LiveSession.LiveEvent.Quotes -> {
                _liveQuotes.value = ApiClient.parseQuotes(e.rawJson)
            }
            LiveSession.LiveEvent.TurnComplete -> {
                liveNewUserTurn = true
                _liveStatus.value = "Bolo… 🎙️"
            }
            is LiveSession.LiveEvent.Failure -> {
                _liveStatus.value = "Dikkat: ${e.message}"
                _liveConnected.value = false
            }
        }
    }

    // ---- infra ----
    private var player: MediaPlayer? = null

    private fun playWav(b64: String) {
        try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val f = File(getApplication<Application>().cacheDir, "say.wav")
            f.writeBytes(bytes)
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(f.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {
            // audio is best-effort
        }
    }

    private fun job(loadingMsg: String?, block: suspend () -> Unit) = viewModelScope.launch {
        _error.value = null
        if (loadingMsg != null) _loading.value = loadingMsg
        try {
            block()
        } catch (e: Exception) {
            _error.value = e.message ?: "Kuch galat hua — backend chal raha hai?"
        }
        _loading.value = null
    }

    override fun onCleared() {
        player?.release()
        live?.stop()
        super.onCleared()
    }
}
