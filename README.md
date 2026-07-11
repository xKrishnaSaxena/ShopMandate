# 🛍️ ShopMandate

**A voice-first AI shopping agent for India.** Just talk — the agent understands what you
want, searches real quick-commerce stores, compares prices, and places a real order paid over
UPI. No app-hopping, no typing, no menus.

> _"Bhaiya, ek charger chahiye"_ → agent searches Zepto + Swiggy, picks the cheapest, adds to
> cart, asks "aur kuch chahiye?", and takes you to address + UPI payment — all by voice.

Built for the **Google DeepMind Bangalore Hackathon** · Problem Statement **PS2 — Autonomous
Orchestration with Managed Agents**.

---

## ✨ What makes it real

Everything below is **live and working — not mocked**:

| Capability | What actually happens |
|---|---|
| 🎙️ **Live voice agent** | Real-time, multi-turn Hinglish conversation powered by **Gemini Live**. You speak, the agent speaks back — like a phone call. |
| 🔌 **Real store integration** | Connects to the **official Zepto & Swiggy Instamart MCP servers** (Model Context Protocol) — real inventory, real prices. |
| 🔐 **Real secure login** | You connect a store via its **own OAuth page** (your mobile + OTP, on the store's domain). Nothing is faked. |
| 💰 **Live price comparison** | The first item is searched across **all connected stores**; the agent picks the best deal. |
| 🛒 **Multi-item cart** | Add several items in one order by voice; after each, the agent asks "aur kuch chahiye?". |
| 📍 **Real order + UPI payment** | Builds a real cart, previews the real amount, places a **real order**, and opens the merchant's **real UPI payment link** — you approve with your UPI PIN. |

---

## 🧠 The clever bits

- **Same-store lock** — the first item compares every connected store, but once you pick from
  one store, the rest of the cart is locked to it. A single order can't ship from two stores,
  so the agent quietly enforces this.
- **Half-duplex voice** — the mic mutes while the agent is speaking, so room noise / echo never
  cuts the agent off mid-sentence.
- **Phone-aware connect** — the number you set in Settings is passed as an OAuth `login_hint` so
  the store's login page can pre-fill it.
- **Rate-limit resilient** — MCP calls retry with exponential backoff + jitter, so a busy store
  doesn't fail your order.

---

## 🏗️ Architecture

```
┌─────────────────────────┐        REST + WebSocket        ┌──────────────────────────┐
│   Android app            │  ───────────────────────────► │   FastAPI backend        │
│   (Kotlin + Compose)     │                                │   (Python)               │
│                          │  ◄─────────────────────────── │                          │
│  • Live voice screen     │                                │  • Gemini (Live/Flash)   │
│  • Camera / photo        │                                │  • Orchestrator          │
│  • Connect / Pay / Cart  │                                │  • MCP merchant clients  │
└─────────────────────────┘                                └───────────┬──────────────┘
                                                                        │  MCP (OAuth 2.1)
                                                          ┌─────────────┴─────────────┐
                                                          │  Zepto MCP   Swiggy MCP    │
                                                          │  (real quick-commerce)     │
                                                          └────────────────────────────┘
```

- **Frontend:** Native Android — Kotlin, Jetpack Compose, CameraX, Retrofit, OkHttp (WebSocket
  for live audio). A sealed-class screen state machine drives the whole flow.
- **Backend:** Python + FastAPI. Talks to Google **Gemini** (`google-genai`) and to the
  merchant **MCP** servers.
- **AI models (Gemini):**
  | Role | Model |
  |---|---|
  | Live voice agent | `gemini-3.1-flash-live-preview` |
  | Intent + multimodal (voice/photo → what to buy) | `gemini-3.5-flash` |
  | Product image ("dekho kaisa lagega") | `gemini-3.1-flash-lite-image` (Nano Banana) |
  | Text-to-speech | `gemini-3.1-flash-tts-preview` |

---

## 🔄 The user flow

1. **Connect stores (once)** — tap Connect → browser opens the store's login → enter mobile +
   OTP → connected. Home unlocks.
2. **Talk** — tap the mic and say what you want ("ek charger chahiye 200 ke andar").
3. **Agent searches** — hits every connected store, speaks the cheapest deal.
4. **Add to cart** — say "haan le lo" → item added, agent asks "aur kuch chahiye?".
5. **Checkout** — say "bas" → pick a delivery address → review the bill → **Pay over UPI**.
6. **Done** — real order placed, receipt saved to Orders.

---

## 🚀 Running it

### Backend
```bash
cd backend
# .env holds GOOGLE_API_KEY + model names (gitignored)
uv run uvicorn app.main:app --host 0.0.0.0 --port 5055
```
Health check: `curl http://localhost:5055/api/health`

### Android app
1. Open `android/` in Android Studio.
2. Set the backend base URL (tap the "ShopMandate" logo 10× on Home to open the hidden dev
   dialog — defaults to the emulator host).
3. Build & run on a device/emulator (needs mic + internet).

See [`backend/README.md`](backend/README.md) for the full API reference.

---

## 📁 Repository layout

```
hackathon/
├── android/          # Native Android app (Kotlin + Jetpack Compose)
├── backend/          # FastAPI backend — Gemini + MCP orchestration
│   └── app/
│       ├── main.py         # API routes
│       ├── live.py         # Gemini Live voice bridge (/ws/live)
│       ├── orchestrator.py # session + flow logic
│       └── merchants/      # MCP clients, ordering, store registry
├── product.md        # Full product spec
└── README.md         # You are here
```

---

## 🔒 Responsible use

ShopMandate connects only to stores that ship **official MCP servers** and only via each store's
**own OAuth flow** — the user logs in on the store's real page and approves access. We do not
scrape, spoof clients, or move money outside the user's own UPI app. Orders and payments are
real and user-authorised.

---

## 🛣️ Roadmap (Phase 2)

- **AP2 (Agent Payments Protocol)** — cryptographically signed Intent → Cart → Payment mandate
  chain for verifiable, auditable agent purchases.
- **A2A negotiation** — agent-to-agent price haggling across stores.
- Multilingual voice (Kannada, Hindi, Tamil) via Gemini Live Translate.
