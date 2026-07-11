package com.shopmandate

import android.app.Application
import android.content.Intent as AndroidIntent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopmandate.capture.OAuthLoopback
import com.shopmandate.capture.LiveSession
import com.shopmandate.net.ApiClient
import com.shopmandate.net.ApiService
import com.shopmandate.net.Cart
import com.shopmandate.net.ClarifyAnswers
import com.shopmandate.net.ClarifyRequest
import com.shopmandate.net.ConnectStartRequest
import com.shopmandate.net.ConnectVerifyRequest
import com.shopmandate.net.Intent
import com.shopmandate.net.OAuthCompleteRequest
import com.shopmandate.net.AddressDto
import com.shopmandate.net.CartLineRequest
import com.shopmandate.net.LiveCartItem
import com.shopmandate.net.OrderPrepareCartRequest
import com.shopmandate.net.OrderConfirmMeta
import com.shopmandate.net.OrderConfirmRequest
import com.shopmandate.net.OrderDto
import com.shopmandate.net.OrderPrepareRequest
import com.shopmandate.net.OrderPrepareResponse
import com.shopmandate.net.OrderStatusRequest
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    data object Address : Screen
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

    // Store id currently being connected (drives the spinner on the Connect screen).
    private val _connecting = MutableStateFlow<String?>(null)
    val connecting: StateFlow<String?> = _connecting.asStateFlow()

    private val _profile = MutableStateFlow(UserProfile(name = "Ravi Kumar", phone = "9876543210"))
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    // Derived from the user's latest real order (see loadOrders). Null until there's history.
    private val _reorderSuggestion = MutableStateFlow<String?>(null)
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
    // multi-item cart built during the Live call (Phase B)
    private val _liveCart = MutableStateFlow<List<LiveCartItem>>(emptyList())
    val liveCart: StateFlow<List<LiveCartItem>> = _liveCart.asStateFlow()
    private val _liveCartTotal = MutableStateFlow(0)
    val liveCartTotal: StateFlow<Int> = _liveCartTotal.asStateFlow()
    private val _liveCartStore = MutableStateFlow<String?>(null)   // locked merchant id ("zepto"/"instamart")
    private val _liveOrders = MutableStateFlow<List<OrderDto>>(emptyList())   // past order/cart history
    val liveOrders: StateFlow<List<OrderDto>> = _liveOrders.asStateFlow()

    fun startLive() {
        _liveConnected.value = false
        _liveStatus.value = "Connecting…"
        _liveUserText.value = ""
        _liveAgentText.value = ""
        _liveQuotes.value = emptyList()
        _liveCart.value = emptyList()
        _liveCartTotal.value = 0
        _liveCartStore.value = null
        _liveOrders.value = emptyList()
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
            is LiveSession.LiveEvent.Cart -> {
                val ev = ApiClient.parseLiveCart(e.rawJson)
                _liveCart.value = ev.items
                _liveCartTotal.value = ev.totalInr
                _liveCartStore.value = ev.store
                _liveStatus.value = "Cart: ${ev.items.size} item · ₹${"%,d".format(ev.totalInr)}"
            }
            is LiveSession.LiveEvent.Checkout -> {
                val ev = ApiClient.parseLiveCart(e.rawJson)
                goCartCheckout(ev.store ?: _liveCartStore.value, ev.items.ifEmpty { _liveCart.value })
            }
            is LiveSession.LiveEvent.Orders -> {
                _liveOrders.value = ApiClient.parseOrders(e.rawJson)
                _liveStatus.value = "Pichle ${_liveOrders.value.size} order"
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

    // ---- real MCP order pipeline ----
    private val _orderPrep = MutableStateFlow<OrderPrepareResponse?>(null)
    val orderPrep: StateFlow<OrderPrepareResponse?> = _orderPrep.asStateFlow()
    private val _addresses = MutableStateFlow<List<AddressDto>>(emptyList())
    val addresses: StateFlow<List<AddressDto>> = _addresses.asStateFlow()
    private val _selectedAddressId = MutableStateFlow<String?>(null)
    val selectedAddressId: StateFlow<String?> = _selectedAddressId.asStateFlow()
    private val _awaitingPayment = MutableStateFlow(false)
    val awaitingPayment: StateFlow<Boolean> = _awaitingPayment.asStateFlow()
    private var lastOrderId: String? = null

    /** Map a winning store name ("Zepto", "Swiggy Instamart") → its merchant id.
     *  A Live-cart lock (already a merchant id) takes precedence. */
    private fun merchantId(): String? {
        _liveCartStore.value?.let { return it }
        val s = (_cart.value?.store ?: _winner.value?.store ?: "").lowercase()
        return when {
            "zepto" in s -> "zepto"
            "instamart" in s || "swiggy" in s -> "instamart"
            else -> null
        }
    }

    // ---- nav helpers (used by screens) ----
    fun goHome() { _screen.value = Screen.Home }
    fun goVoice() { _screen.value = Screen.Voice }
    fun goCamera() { _screen.value = Screen.Camera }
    fun goClarify() { _screen.value = Screen.Clarify }
    fun goComparing() { _screen.value = Screen.Comparing }
    fun goApprove() { _awaitingPayment.value = false; _screen.value = Screen.Approve; prepareOrder() }
    fun goConnect() { _screen.value = Screen.Connect }
    fun goPay() { _screen.value = Screen.Pay }
    fun goSuccess() { _screen.value = Screen.Success }
    fun goOrders() { loadOrders(); _screen.value = Screen.Orders }
    fun goProfile() { _screen.value = Screen.Profile }
    fun onReorder() { _screen.value = Screen.Approve }
    fun clearError() { _error.value = null }

    /** Logical back for each screen (UI back arrows + system back gesture). */
    fun goBack() {
        _screen.value = when (_screen.value) {
            Screen.Comparing -> Screen.Clarify
            Screen.Approve -> Screen.Comparing
            Screen.Address -> Screen.Approve
            Screen.Otp -> Screen.Connect
            Screen.Pay -> Screen.Approve
            Screen.Voice, Screen.Camera, Screen.Clarify, Screen.Connect,
            Screen.Orders, Screen.Profile, Screen.Success, Screen.Live -> Screen.Home
            Screen.Home -> Screen.Home
        }
    }

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

    fun search() = job("Stores compare kar raha hoon…") {
        val id = sessionId ?: return@job
        val r = api.search(id)
        _quotes.value = r.quotes
        _winner.value = r.winner
        _cart.value = r.cart
        _haggleSteps.value = r.steps
        _screen.value = Screen.Comparing
    }

    /**
     * Real browser OAuth: get the consent URL from the backend, open it in the phone's
     * default browser (user enters mobile + OTP on the store's own page), then poll until
     * the backend has the token. No in-app OTP screen.
     */
    fun startConnect(store: String) {
        connectingStore = store
        _connecting.value = store
        _error.value = null
        viewModelScope.launch {
            try {
                val r = api.oauthStart(store)
                if (r.status == "connected") {
                    _connectedStores.value = r.connectedStores.toSet()
                    _connecting.value = null
                    return@launch
                }
                val url = r.authUrl ?: run {
                    _error.value = r.detail ?: "Connect start fail hua"
                    _connecting.value = null
                    return@launch
                }
                val catcher = async { OAuthLoopback.await() }   // listen BEFORE the redirect
                openBrowser(url)                                // browser -> mobile + OTP
                val cb = catcher.await()
                val code = cb?.code
                if (code == null) {
                    _error.value = "OAuth cancel/timeout - dobara try karo"
                    _connecting.value = null
                    return@launch
                }
                api.oauthComplete(OAuthCompleteRequest(code, cb.state ?: ""))
                for (i in 0 until 30) {                          // backend does the token exchange
                    delay(1500)
                    val s = try { api.connectStatus(store) } catch (e: Exception) { continue }
                    if (s.connected) {
                        _connectedStores.value = s.connectedStores.toSet()
                        connectingStore = null
                        _connecting.value = null
                        return@launch
                    }
                }
                _connecting.value = null
                _error.value = "Connect timeout - dobara try karo"
            } catch (e: Exception) {
                _error.value = e.message ?: "Connect fail hua"
                _connecting.value = null
            }
        }
    }

    private fun openBrowser(url: String) {
        val i = AndroidIntent(AndroidIntent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(AndroidIntent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(i)
    }

    fun onOtpVerified() = job(null) {
        val store = connectingStore ?: return@job
        val r = api.connectVerify(store, ConnectVerifyRequest(otp = "123456"))
        _connectedStores.value = r.connectedStores.toSet()
        connectingStore = null
        _screen.value = Screen.Connect
    }

    /** Called AFTER the real UPI payment succeeds on-device — records the order + shows the receipt. */
    fun pay(upiApp: String = "UPI", upiTxnId: String? = null) = job("Order confirm ho raha hai…") {
        val id = sessionId ?: return@job
        val r = api.pay(id, PayRequest(upiApp = upiApp, upiTxnId = upiTxnId))
        _receipt.value = r.receipt
        _screen.value = Screen.Success
    }

    fun loadOrders() = job(null) {
        val fetched = api.orders().orders
        _orders.value = fetched
        // Reorder hint on Home = the most recent real order (null if no history yet).
        _reorderSuggestion.value = fetched.firstOrNull()?.let {
            "${it.product} — pichli baar ₹${"%,d".format(it.priceInr)} mein"
        }
    }

    // ---- real order pipeline (Zepto / Instamart via MCP) ----
    /** Build the real cart on the merchant + preview breakdown (item + delivery + total). No charge. */
    fun prepareOrder(addressId: String? = null) = job("Cart bana raha hoon…") {
        val mid = merchantId() ?: run { _orderPrep.value = null; return@job }
        val product = _cart.value?.item ?: _intent.value?.product ?: return@job
        val qty = _cart.value?.qty ?: _intent.value?.qty ?: 1
        val r = api.orderPrepare(mid, OrderPrepareRequest(
            query = product, budgetInr = _intent.value?.budgetInr, qty = qty,
            addressId = addressId ?: _selectedAddressId.value,
        ))
        _orderPrep.value = if (r.status == "ready") r else null
        (r.addressId ?: r.address?.id)?.let { _selectedAddressId.value = it }
        if (r.status != "ready") _error.value = r.detail ?: "Order preview fail hua (${r.status})"
    }

    /** Open the address picker (loads the merchant's saved delivery addresses). */
    fun goAddressPicker() = job(null) {
        val mid = merchantId() ?: return@job
        _addresses.value = api.addresses(mid).addresses
        _screen.value = Screen.Address
    }

    /** User picked a delivery address → re-preview on that address, back to Approve.
     *  A Live multi-item cart uses the cart-preview path; a single item the normal one. */
    fun selectAddress(id: String) {
        _selectedAddressId.value = id
        _screen.value = Screen.Approve
        if (_liveCart.value.isNotEmpty()) prepareCartOrder(id) else prepareOrder(id)
    }

    /** Live checkout: end the voice call, lock to the chosen store, load its addresses. */
    private fun goCartCheckout(storeId: String?, items: List<LiveCartItem>) = job("Cart taiyaar kar raha hoon…") {
        live?.stop(); live = null
        _liveCartStore.value = storeId
        _liveCart.value = items
        val mid = storeId ?: merchantId() ?: run {
            _error.value = "Store samajh nahi aaya"; _screen.value = Screen.Home; return@job
        }
        _addresses.value = api.addresses(mid).addresses
        _screen.value = Screen.Address
    }

    /** Build the real multi-item cart on the locked store + preview breakdown. No charge. */
    fun prepareCartOrder(addressId: String? = null) = job("Cart bana raha hoon…") {
        val mid = merchantId() ?: run { _orderPrep.value = null; return@job }
        val items = _liveCart.value.map { CartLineRequest(query = it.query) }
        if (items.isEmpty()) { _orderPrep.value = null; return@job }
        val r = api.orderPrepareCart(mid, OrderPrepareCartRequest(
            items = items, addressId = addressId ?: _selectedAddressId.value,
        ))
        _orderPrep.value = if (r.status == "ready") r else null
        (r.addressId ?: r.address?.id)?.let { _selectedAddressId.value = it }
        if (r.status != "ready") _error.value = r.detail ?: "Cart preview fail hua (${r.status})"
    }

    /** Place the REAL order → open the merchant's payment link → poll status → Success. */
    fun confirmOrder() = job("Order place kar raha hoon…") {
        val mid = merchantId() ?: return@job
        val prep = _orderPrep.value ?: return@job
        val addrId = _selectedAddressId.value ?: prep.address?.id ?: return@job
        val meta = OrderConfirmMeta(
            product = prep.product, qty = prep.qty, itemPriceInr = prep.itemPriceInr,
            deliveryFeeInr = prep.deliveryFeeInr, toPayInr = prep.toPayInr, address = prep.address,
        )
        val r = api.orderConfirm(mid, OrderConfirmRequest(addressId = addrId, rail = "online", meta = meta))
        if (r.status == "placed" || r.paymentLink != null) {
            lastOrderId = r.orderId
            _awaitingPayment.value = true
            if (r.paymentLink != null) openBrowser(r.paymentLink)
            pollPaymentStatus(mid, r.orderId)
        } else {
            _error.value = r.detail ?: "Order place nahi hua (${r.status})"
        }
    }

    private fun pollPaymentStatus(mid: String, orderId: String?) {
        if (orderId == null) return
        viewModelScope.launch {
            repeat(40) {                       // ~2 min
                delay(3000)
                val s = try { api.orderStatus(mid, OrderStatusRequest(orderId)) } catch (e: Exception) { null }
                if (s?.paid == true) { finishOrder(); return@launch }
            }
        }
    }

    /** Payment done (poll said paid, or user tapped "ho gaya") → receipt + Success + refresh history. */
    fun finishOrder() {
        if (_screen.value == Screen.Success) return
        val prep = _orderPrep.value
        _receipt.value = Receipt(
            orderId = lastOrderId ?: "SM-${(1000..9999).random()}",
            item = prep?.product ?: _cart.value?.item ?: "Order",
            priceInr = prep?.toPayInr ?: _cart.value?.priceInr ?: 0,
            store = prep?.store ?: _winner.value?.store ?: "Store",
            delivery = "jaldi",
            paidVia = "UPI",
        )
        _awaitingPayment.value = false
        _screen.value = Screen.Success
        loadOrders()
    }

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
