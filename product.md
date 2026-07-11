# ShopMandate — Master Build Spec

> **Read this fully before writing code.** This is the single source of truth for building ShopMandate.
> It is written so any engineer or AI agent can pick up any part and build it without extra context.
>
> Companion docs: `design/stitch-prompt.md` (screen prompts), `design/*/` (Stitch exports = visual truth).

---

## 1. What we're building (in one paragraph)

**ShopMandate** is a **voice- and photo-first shopping assistant for India**. An ordinary user **speaks**
("mujhe ₹2000 ke andar acche wireless earbuds chahiye") or **points their camera** at a product. An agent
figures out *what* they want, *how much* they'll spend, asks a clarifying question if needed, makes **two
stores compete on price**, shows the user the exact cart + why it chose that store, and completes a
**UPI-framed payment** ending in a clean receipt. Prices are in **₹**, copy is friendly **Hinglish**.

**Target problem statement:** **PS2 — Autonomous Orchestration with Managed Agents (iAPI).** The backbone is
multi-tool / multi-agent orchestration (intent → search across stores → compare/negotiate → order). Multimodal
input (PS1) is a strong secondary flavour.

---

## 2. Scope — Phase 1 (build now) vs Phase 2 (deferred)

| Area | Phase 1 (NOW) | Phase 2 (later) |
|------|---------------|-----------------|
| Input | Voice + photo + text → intent | Real-time Gemini Live (interrupt, continuous camera) |
| Intent | Gemini multimodal → structured intent JSON | AP2 **Intent Mandate** (user-signed) |
| Stores | 2 mock store agents, ₹ catalogs, compare + light haggle | Full A2A agent-to-agent negotiation |
| Cart | Winner cart shown for approval | AP2 **Cart Mandate** (merchant-signed) |
| Payment | **Mock UPI** (framed, no real money) | AP2 **Payment Mandate** (device-signed) + real PSP |
| Receipt | Receipt + light "Verifiable receipt" teaser | Full Intent→Cart→Payment signed audit chain |
| Real stores | Own mock MCP backbone (+ optional 1 real Zepto demo) | Partner integration |

**Golden rule:** Phase 1 must run **end-to-end on mocks** with zero external dependencies except the Gemini
API. Everything else (AP2, A2A, real MCP, real UPI) is Phase 2 and must NOT block Phase 1.

**Hard cuts / non-goals:** no scraping or real checkout of Flipkart/Amazon; no real money movement; agents run
**server-side** (not on-device); **no custom crypto** (Phase 2 reuses AP2's built-in signing).

---

## 3. Canonical demo scenario (use these exact values everywhere)

To keep every screen consistent (this fixes the product-drift bug found in the Stitch export):

- **User says:** *"mujhe ₹2000 ke andar acche wireless earbuds chahiye"*
- **Product (canonical):** **boAt Airdopes 141 — Wireless Earbuds**, Color: Bold Black, 1 Year Warranty, Qty 1
- **Store A** quote: **₹1,950**, delivery **kal** (tomorrow), in stock
- **Store B** quote: **₹1,800**, delivery **aaj** (today), in stock → **WINNER**
- **Why B wins:** "₹150 sasta + jaldi delivery"
- **Payment:** UPI (framed) → **Order #SM-4821**, delivery "kal shaam tak"

⚠️ **Every screen (compare, approve, pay, success) must show the SAME product name + image + price.** Do not
let the compare screen show over-ear headphones or the pay screen say "Noise Cancelling Earbuds PRO".

---

## 4. End-to-end user flow (10 screens → API calls)

Flow: **Home → Voice/Camera → Clarify → Compare → Approve → [Connect+OTP, first time only] → UPI Pay → Receipt**

| # | Screen (design folder) | What happens | Backend call |
|---|------------------------|--------------|--------------|
| 1 | `home_voice_first` | User taps mic / camera / types | — |
| 2 | `voice_listening` | Capture audio, show live-ish transcript | `POST /session/start` (voice) |
| 3 | `camera_search` | Capture photo of product | `POST /session/start` (photo) |
| 4 | `clarification` | Show parsed intent + ask 1 question, quick-reply chips | `POST /session/{id}/clarify` |
| 5 | `comparing_stores` | Store A vs B, price + "₹150 sasta", winner | `POST /session/{id}/search` |
| 6 | `approve_pay` | Show exact cart + "why B" + Approve | (uses cart from search) |
| 7 | `connect_store_phone` | One-time: enter mobile → OTP | `POST /session/{id}/connect/start` |
| 8 | `otp_verification` | Enter OTP → connected | `POST /session/{id}/connect/verify` |
| 9 | `upi_payment` | Choose UPI app → pay → "authorizing" | `POST /session/{id}/pay` |
| 10 | `order_success` | Receipt + order id + verifiable-receipt teaser | (uses pay response) |

**Notes**
- Screens 7–8 (connect + OTP) are a **one-time account-linking** step. After the first order, skip straight
  from Approve → Pay. Gate with a `store_connected` flag in session/localStorage.
- Screen 9 has two visual states: the UPI-app chooser, then an **"Authorizing… / UPI PIN daalo"** overlay
  (mock) before success. This overlay was missing in the Stitch export — add it in the build.

---

## 5. Architecture

```
┌───────────────────────────────────────────────────────────────┐
│  ANDROID APP  (Kotlin + Jetpack Compose)                       │
│  • mic + camera capture (CameraX / MediaRecorder)             │
│  • 10 screens driven by a flow state machine (sealed class)   │
│  • calls backend over REST/JSON (Retrofit + Coroutines)       │
│  • (Phase 2) holds signing key in Android Keystore            │
└───────────────────────────────┬───────────────────────────────┘
                                │  HTTP/JSON  (contract §8)
                                ▼
┌───────────────────────────────────────────────────────────────┐
│  BACKEND  (Node.js + TypeScript + Express)                     │
│  ORCHESTRATOR / SHOPPING AGENT                                 │
│   • audio/image/text → Gemini multimodal → structured Intent  │
│   • runs clarification logic                                  │
│   • calls Store A + Store B tools, compares, light-haggles    │
│   • builds Cart, handles connect/OTP (mock), payment (mock)   │
│                                                               │
│   ├─ Gemini client (@google/genai) — multimodal intent       │
│   ├─ Store Agent A (mock ₹ catalog) — search/quote/counter   │
│   ├─ Store Agent B (mock ₹ catalog) — search/quote/counter   │
│   ├─ Mock UPI payment service       — authorize → receipt    │
│   └─ Mock store-connect (OTP)       — phone → otp → connected │
└───────────────────────────────────────────────────────────────┘
   (Phase 2 adds: AP2 mandates, A2A negotiation, real Zepto/Swiggy MCP, real PSP)
```

---

## 6. Tech stack (concrete choices)

| Layer | Choice | Why |
|-------|--------|-----|
| App | **Kotlin + Jetpack Compose** (single-activity) | Native Android; fast to pixel-match Stitch screens; declarative UI |
| UI components | Compose + Material 3 (custom-themed) | Build custom-styled components from the design tokens (§10.3) |
| Navigation / flow | Sealed-class **screen state machine** in a ViewModel | Flow is a sequence, not deep-link routing |
| State / async | **ViewModel + StateFlow + Coroutines** | Standard, testable, drives UI reactively |
| Networking | **Retrofit + OkHttp + kotlinx.serialization** | Typed REST client for the §8 contract |
| Animation | Compose animation APIs | Pulsing mic, waveform, transitions, OTP/PIN dots |
| Media capture | **CameraX** (photo), **MediaRecorder/AudioRecord** (mic) | Native capture → base64 for §8 |
| Permissions | `CAMERA`, `RECORD_AUDIO` (runtime) | Required for capture |
| Min SDK | 26+ (target latest stable) | Modern APIs, wide device coverage |
| Backend | **Node.js 20+ + TypeScript + Express** | Team preference; language-agnostic to the app via REST |
| Validation | **Zod** | Schema validation, mirrors the §9.5 models |
| AI | **Gemini Flash (latest multimodal)** via `@google/genai` | Audio transcription + parse + image understand. **Verify current model id** (e.g. `gemini-2.5-flash`) in Google AI Studio |
| Dev reload | `tsx` (or `ts-node-dev`) | Hot reload for TS |
| Package mgmt | `npm`/`pnpm` (backend) · Gradle (Android) | Standard |

**Framework-agnostic note:** Phase 1 uses a lean **Express/TS** orchestrator with Gemini function-calling for
tool orchestration. AP2's reference implementation is **Python** — so for Phase 2 you can either adopt Google
**ADK / iAPI Managed Agents + AP2 + A2A** (running the AP2 mandate-signing bits as a **separate Python
service**) or stay in TS and call AP2 over its APIs. The app ↔ backend REST contract (§8) doesn't change
either way.

---

## 7. Repository structure

```
shopmandate/
├── BUILD.md                     ← this file
├── design/                      ← Stitch exports (visual source of truth)
│   ├── stitch-prompt.md
│   ├── home_voice_first/ (code.html, screen.png)
│   ├── voice_listening/ ...      (10 screens total)
│   └── shopmandate/DESIGN.md
├── android/                     ← Kotlin + Jetpack Compose app
│   ├── app/src/main/
│   │   ├── java/com/shopmandate/
│   │   │   ├── MainActivity.kt          ← single activity, sets Compose content
│   │   │   ├── ShopViewModel.kt         ← flow state machine + session state (StateFlow)
│   │   │   ├── net/ApiService.kt        ← Retrofit interface for §8
│   │   │   ├── net/Dtos.kt              ← @Serializable request/response models (§8)
│   │   │   ├── ui/theme/                ← Color/Type/Theme from design tokens (§10.3)
│   │   │   ├── ui/screens/              ← one composable per screen (§10.2)
│   │   │   ├── ui/components/           ← MicButton, Waveform, StoreCard, OtpInput, UpiSheet…
│   │   │   └── capture/                 ← CameraX + audio capture → base64
│   │   ├── res/                         ← icons, drawables (earbuds image, store logos)
│   │   └── AndroidManifest.xml          ← CAMERA + RECORD_AUDIO + INTERNET perms
│   └── build.gradle.kts
└── backend/                     ← Node + TypeScript
    ├── package.json
    ├── tsconfig.json
    ├── .env                     ← GOOGLE_API_KEY=...
    └── src/
        ├── index.ts             ← Express app + routes (§8)
        ├── models.ts            ← Zod schemas / types (§9.5)
        ├── orchestrator.ts      ← Shopping Agent logic
        ├── gemini.ts            ← multimodal intent extraction (§9.2)
        ├── stores.ts            ← Store A/B mock agents + catalogs (§9.3)
        ├── negotiation.ts       ← compare + light haggle (§9.3)
        ├── payment.ts           ← mock UPI service (§9.4)
        └── connect.ts           ← mock store-connect/OTP (§9.4)
```

---

## 8. Client ↔ Server API contract (frozen — build against this)

Base URL: `/api`. All bodies JSON. `session_id` is a server-generated UUID returned by `/session/start`.

### 8.1 `POST /api/session/start`
Start a session from voice / photo / text.
```jsonc
// Request
{
  "input_type": "voice" | "photo" | "text",
  "audio_b64": "…",          // if voice (webm/opus or wav)
  "image_b64": "…",          // if photo (jpeg/png)
  "text": "…",               // if text
  "language_hint": "hi-IN"   // optional: hi-IN | kn-IN | en-IN
}
// Response
{
  "session_id": "uuid",
  "status": "intent_ready" | "need_clarification",
  "transcript": "mujhe ₹2000 ke andar acche wireless earbuds chahiye", // if voice
  "parsed_intent": { "product": "wireless earbuds", "category": "audio",
                     "budget_inr": 2000, "qty": 1, "constraints": [], "language": "hi-IN" },
  "clarifying_question": "wireless hi chahiye ya wired bhi chalega?" // if need_clarification
}
```

### 8.2 `POST /api/session/{id}/clarify`
Answer the clarifying question (from quick-reply chips).
```jsonc
// Request
{ "answers": { "type": "wireless", "budget_inr": 2000 } }
// Response
{ "status": "intent_ready", "parsed_intent": { …updated… } }
```

### 8.3 `POST /api/session/{id}/search`
Run both stores, compare, pick winner, build cart.
```jsonc
// Response
{
  "status": "awaiting_approval",
  "quotes": [
    { "store": "Store A", "product_name": "boAt Airdopes 141 – Wireless Earbuds",
      "price_inr": 1950, "delivery": "kal", "in_stock": true, "image_url": "…" },
    { "store": "Store B", "product_name": "boAt Airdopes 141 – Wireless Earbuds",
      "price_inr": 1800, "delivery": "aaj", "in_stock": true, "image_url": "…" }
  ],
  "winner": { "store": "Store B", "price_inr": 1800, "why": "₹150 sasta + jaldi delivery" },
  "cart": {
    "item": "boAt Airdopes 141 – Wireless Earbuds", "color": "Bold Black",
    "warranty": "1 Year", "qty": 1, "price_inr": 1800, "store": "Store B",
    "delivery": "kal shaam tak"
  }
}
```

### 8.4 `POST /api/session/{id}/connect/start`  (one-time)
```jsonc
// Request
{ "phone": "9876543210" }
// Response
{ "status": "otp_sent", "masked_phone": "+91 98XXX-XX21" }
```

### 8.5 `POST /api/session/{id}/connect/verify`  (one-time)
```jsonc
// Request
{ "otp": "123456" }          // mock: accept "123456" (or any 6 digits)
// Response
{ "status": "connected", "store": "Zepto" }
```

### 8.6 `POST /api/session/{id}/pay`
```jsonc
// Request
{ "method": "upi", "upi_app": "gpay" | "phonepe" | "paytm" | "bhim", "upi_id": null }
// Response (returned after a short mock "authorizing" delay)
{
  "status": "complete",
  "order_id": "SM-4821",
  "receipt": {
    "order_id": "SM-4821", "item": "boAt Airdopes 141 – Wireless Earbuds",
    "price_inr": 1800, "store": "Store B", "delivery": "kal shaam tak",
    "paid_via": "UPI · gpay",
    "audit_chain": { "intent": "…", "cart": "…", "payment": "…" } // Phase-2 placeholder ids
  }
}
```
Frontend shows the "Authorizing… / UPI PIN daalo" overlay while awaiting this response.

---

## 9. Backend design (build each module)

### 9.1 Orchestrator / Shopping Agent (`orchestrator.ts`)
Owns the session lifecycle and sequences the tools:
1. `start` → call Gemini (`gemini.py`) to get transcript + structured intent. If a required field
   (type/budget) is missing or ambiguous → `need_clarification` with a question.
2. `clarify` → merge answers into intent → `intent_ready`.
3. `search` → call `stores.get_quote(intent)` for A and B → `negotiation.decide()` → cart.
4. `connect/*` → delegate to `connect.py` (mock).
5. `pay` → `payment.charge()` → build receipt.
Keep session state in an in-memory `Map` keyed by `session_id` (a real DB is overkill for the hackathon).

### 9.2 Gemini multimodal (`gemini.ts`)
Use `@google/genai`. Two functions:
- `extract_intent_from_audio(audio_b64, language_hint) -> Intent` — transcribe + parse in one call.
- `identify_from_image(image_b64) -> Intent` — identify the product and infer intent.

Use a **structured response schema** so output is reliable JSON:
```jsonc
// Gemini response schema (Intent)
{
  "transcript": "string (echo of what was heard, if audio)",
  "product": "string",           // e.g. "wireless earbuds"
  "category": "string",          // e.g. "audio", "grocery"
  "budget_inr": "integer|null",
  "qty": "integer",              // default 1
  "constraints": ["string"],     // e.g. ["wireless", "under ₹2000"]
  "language": "string",          // hi-IN | kn-IN | en-IN
  "needs_clarification": "boolean",
  "clarifying_question": "string|null"
}
```
System instruction (paraphrase): *"You are a shopping intent parser for Indian users. Input may be Hinglish
or Kannada. Extract product, budget in INR, quantity, and constraints. If the product type or budget is
ambiguous, set needs_clarification=true and ask ONE short Hinglish question. Never invent a product the user
didn't mention."*

### 9.3 Store agents + negotiation (`stores.ts`, `negotiation.ts`)
Two mock stores with ₹ catalogs. Each store exposes `get_quote(intent) -> Quote | None` (None = out of stock).

**Mock catalog (seed data):**
```jsonc
{
  "wireless earbuds": {
    "Store A": { "product_name": "boAt Airdopes 141 – Wireless Earbuds", "price_inr": 1950,
                 "delivery": "kal", "in_stock": true },
    "Store B": { "product_name": "boAt Airdopes 141 – Wireless Earbuds", "price_inr": 1800,
                 "delivery": "aaj", "in_stock": true }
  },
  "atta 5kg":       { "Store A": { "price_inr": 260, "delivery": "aaj" },
                      "Store B": { "price_inr": 245, "delivery": "aaj" } },
  "phone charger":  { "Store A": { "price_inr": 499, "delivery": "kal" },
                      "Store B": { "price_inr": 549, "delivery": "aaj" } }
}
```

**Negotiation logic (Phase 1 = light haggle):**
```
quotes = [A.get_quote(intent), B.get_quote(intent)]         # drop None (out of stock)
loser, winner = higher_price, lower_price
# one counter round: ask loser to match; loser counters partway but not below winner
loser.counter = max(winner.price + 50, loser.price - 100)   # e.g. A: 1950 -> 1900, still > 1800
winner = min(all current prices)                             # B still wins at 1800
# budget guard: if winner.price > intent.budget_inr -> status "over_budget", ask user
why = f"₹{loser.price - winner.price} sasta + {winner.delivery} delivery"
```
Expose the negotiation steps in the response (or a log) so the compare screen can animate them.
**Error-recovery beat (optional, high-impact):** let one store return `in_stock:false` for a query and have
the orchestrator silently fall back to the other — proves it's a real agent.

### 9.4 Mock UPI payment (`payment.ts`) + store connect (`connect.ts`)
- `payment.charge(cart, upi_app) -> Receipt`: sleep ~1.5s (simulated authorization), return receipt with a
  generated order id (`SM-####`). **No real money. Framed as UPI.**
- `connect.start_otp(phone) -> masked_phone`; `connect.verify(otp) -> bool` (accept `123456` or any 6 digits).
  This mirrors the real Zepto/Swiggy OAuth-OTP shape so Phase-2 swap-in is trivial.

### 9.5 Data models (`models.ts`, Zod)
`Intent`, `Quote`, `Cart`, `Order`, `Receipt`, `Session` — Zod schemas mirroring the JSON shapes in §8
(infer TS types with `z.infer`). Keep them minimal.

---

## 10. Frontend design (Android / Compose)

### 10.1 Flow state machine (`ShopViewModel.kt`)
```kotlin
sealed interface Screen {
  data object Home; data object Voice; data object Camera; data object Clarify
  data object Comparing; data object Approve; data object Connect; data object Otp
  data object Pay; data object Success
}
```
The ViewModel exposes `StateFlow<Screen>` + `session` + `storeConnected`; a single root `@Composable`
`when`-switches on the current screen. Advance on user actions / API responses. Skip `Connect` + `Otp` when
`storeConnected == true`.

### 10.2 Screens → components (mirror `design/*/screen.png` pixel-for-pixel)
| Screen | Key components |
|--------|----------------|
| home | `MicButton` (pulsing gradient), `CameraButton`, suggestion `Chip`s, text pill. **Remove the broken bottom nav** (raw "shopping_bag"/"person" text) or add real icons |
| voice | `Waveform` (Framer Motion), live `Transcript`, stop button |
| camera | `<video>` viewfinder, shutter, gallery/flip, detected-item chip |
| clarify | intent chips (editable), question bubble, quick-reply `Chip`s, budget chips, "Aage badho" |
| comparing | two `StoreCard`s, animated price, "₹150 sasta" badge, "vs", status log |
| approve | hero `ProductCard`, "why B" strip, "Approve & Pay ₹1,800", secondary link |
| connect | official-store badges, `+91` phone input, "OTP bhejo" |
| otp | `OtpInput` (6 boxes, auto-advance), resend timer, "Verify & Connect", "Zepto connected" state |
| pay | `UpiSheet` (GPay/PhonePe/Paytm/BHIM tiles), UPI-ID row, secure line, **+ authorizing/PIN overlay** |
| success | check animation, `ReceiptCard`, "Verifiable receipt" collapsible (Phase-2 teaser), Track/"Ho gaya" |

Use `design/<screen>/screen.png` (+ `code.html` for spacing/colors) as the visual reference, but build each
screen as a Compose `@Composable`, injecting the canonical product (§3) and the missing states.

### 10.3 Design tokens (`ui/theme/Color.kt`, `Type.kt`)
Express as Compose `Color(0xFF……)`:
```
brand   #4F32E7   (indigo)      cta     #FF6A3D   (coral-saffron)
bg      #FFF8F3   (warm white)  surface #FFFFFF
success #1FB57A   text          #1A1626  text-muted #6B6577
shape: cards 24.dp rounded, buttons full-pill · font: Poppins / Plus Jakarta Sans (bundle in res/font)
soft low shadows (Compose elevation / shadow)
```

### 10.4 Media capture (`capture/`)
- Mic: `MediaRecorder` (or `AudioRecord`) → write to file/bytes → base64 → `/session/start` (voice). Drive the
  `Waveform` composable from amplitude (poll `MediaRecorder.getMaxAmplitude()` or use `Visualizer`).
- Camera: **CameraX** `ImageCapture` → capture → downscale → JPEG base64 → `/session/start` (photo).
- Request `CAMERA` / `RECORD_AUDIO` runtime permissions before capture (Accompanist Permissions or the
  Activity Result API).

---

## 11. Real MCP integration (Zepto / Swiggy) — optional, Phase-2, read the constraints

All three (Zepto/Zomato/Swiggy) ship **official** MCP servers, BUT:
- **Swiggy & Zomato explicitly ban third-party apps** built on top and only whitelist known clients'
  (Claude/Cursor/VSCode) OAuth redirects → a custom app can't complete OAuth. **Do not wire these behind
  ShopMandate's own OAuth — it's a ToS/DQ risk.**
- **Orders are REAL, not sandbox.** Swiggy orders can't be cancelled.
- **Zepto** is the most permissive (OAuth via Indian mobile + OTP). Optional live "it's real" demo moment:
  place ONE small **personal-use** Zepto order via its MCP, kept behind a feature flag. Frame everything else.

Keep `connect.py` and the store tools shaped like the real MCP (search / cart / place_order + OTP OAuth) so a
future swap is a config change, not a rewrite.

---

## 12. Setup & run

**Backend (Node + TypeScript)**
```bash
cd backend
npm install express cors zod @google/genai
npm install -D typescript tsx @types/express @types/cors @types/node
echo "GOOGLE_API_KEY=your_key" > .env      # provisioned on hackathon day
npx tsx watch src/index.ts                 # runs on http://localhost:8000
```

**Android app (Kotlin + Compose)**
```
1. Open the android/ folder in Android Studio (latest stable).
2. Set the backend base URL in ApiService.kt:
   - Emulator        → http://10.0.2.2:8000     (10.0.2.2 = the host machine from inside the emulator)
   - Physical device → http://<laptop-LAN-ip>:8000   (phone + laptop on the same Wi-Fi)
3. Run on an emulator or a device that has a camera + mic.
```
For local HTTP (non-HTTPS) during dev, allow cleartext to your dev host via a
`res/xml/network_security_config.xml` referenced from the manifest (or `usesCleartextTraffic` for dev only).

---

## 13. Build order & milestones

1. **Scaffold** both apps + the frozen API contract (§8) with **stubbed** responses (hard-coded canonical
   scenario). Frontend + backend can then progress in parallel. ✅ unblocks everyone.
2. **App screens (Compose)** — build all 10 against stubs, pixel-matching `design/*/`. Apply the 3 fixes (§14).
3. **Backend real logic (TS)** — Gemini intent (voice + photo), store catalogs + negotiation, mock pay/connect.
4. **Integrate** — swap stubs for real endpoints; test the full flow end-to-end on the canonical scenario.
5. **Polish** — animations (waveform, authorizing overlay, compare ticker), Hinglish copy, error-recovery beat.
6. **Freeze + record** the 60-sec demo video. No new code after freeze.

---

## 14. Design-consistency fixes to apply during build (do NOT forget)

1. **One product identity** across compare → approve → pay → success: **boAt Airdopes 141 – Wireless Earbuds**,
   same image, ₹1,800 (Store B). Fix the compare screen (currently headphones) and pay screen (currently
   "Noise Cancelling Earbuds PRO").
2. **Home bottom nav** renders raw icon names ("shopping_bag", "person") — remove the tab bar or add real icons.
3. **Add the UPI "Authorizing… / UPI PIN daalo" overlay** state on the pay screen before success.

---

## 15. Demo script (60 sec)

1. Open app → tap mic → *"mujhe ₹2000 ke andar acche wireless earbuds chahiye"* (or point camera).
2. Agent shows understanding → asks "wireless ya wired?" → user taps "Wireless".
3. Compare screen: Store A ₹1,950 **vs** Store B ₹1,800 — "₹150 sasta", B wins.
4. Approve screen → tap **Approve & Pay ₹1,800**.
5. (First time) connect store → OTP → connected.
6. UPI sheet → pick GPay → authorizing → **Order confirmed! #SM-4821**.
7. Tap "Verifiable receipt" → tease the Intent→Cart→Payment chain (Phase 2).

**Honesty line for judges:** "This is a working prototype of the flow — mock stores and a UPI-framed payment.
The stores map to real official MCP servers (Zepto/Swiggy) and the payment layer to AP2's signed mandates,
which is our Phase 2."

---

## 16. Judge Q&A quick reference

- **Is it a chatbot?** No — it senses (voice/photo), decides (compare/negotiate), acts (order), and checks
  (budget guard, out-of-stock fallback). Show the error-recovery beat.
- **Real money?** No — UPI is framed; the flow is production-shaped; real PSP + AP2 signing is Phase 2.
- **Just a scraper?** No — no scraping; stores are agents/tools; real stores expose official MCP servers.
- **What's novel?** Multimodal intent + multi-store agent orchestration + (Phase 2) verifiable-intent payments.
