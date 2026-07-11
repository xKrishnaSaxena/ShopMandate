# ShopMandate backend (Python · FastAPI · Gemini · MCP)

The brain of ShopMandate. A FastAPI service that:
- runs the **Gemini Live** voice agent (`/ws/live`),
- understands voice/photo/text intent via **Gemini**,
- and orchestrates **real orders** on the official **Zepto & Swiggy Instamart MCP servers**.

The Android app talks to these endpoints only (REST + one WebSocket).

## Modules (`app/`)
- `main.py` — FastAPI routes.
- `live.py` — **Gemini Live** WebSocket bridge: streams mic audio ↔ agent audio, and exposes the
  voice agent's tools (`search_products`, `add_to_cart`, `checkout`) with a per-session cart +
  same-store lock.
- `orchestrator.py` — session lifecycle, intent flow, store connect, quote gathering, orders.
- `gemini.py` — multimodal intent (voice/photo/text) + TTS + product-image generation.
- `merchants/`
  - `mcp_client.py` — reusable **MCP client** (OAuth 2.1 + PKCE + Dynamic Client Registration),
    per-merchant token cache, 429 retry with backoff.
  - `base.py` — merchant adapter: search a store → cheapest in-budget `Quote`.
  - `ordering.py` — real cart build + order preview + confirm (Zepto & Instamart tool pipelines).
  - `registry.py` — enabled merchants (Zepto, Swiggy Instamart).
- `negotiation.py` — compare quotes + pick a winner · `connect.py` / `payment.py` — legacy helpers.

## Models (from `.env`)
| Env var | Default | Used for |
|---|---|---|
| `LIVE_MODEL` | `gemini-3.1-flash-live-preview` | real-time voice agent |
| `FLASH_MODEL` | `gemini-3.5-flash` | text intent |
| `PRO_MODEL` | `gemini-3.5-flash` | audio/photo (multimodal) intent |
| `IMAGE_MODEL` | `gemini-3.1-flash-lite-image` | product image (Nano Banana) |
| `TTS_MODEL` | `gemini-3.1-flash-tts-preview` | speech out |

## Run
```bash
cd backend
# .env holds GOOGLE_API_KEY + the model names above (gitignored)
uv run uvicorn app.main:app --host 0.0.0.0 --port 5055
```
Base URL: `http://<host>:5055/api` · health: `GET /api/health`

## Key endpoints

### Voice agent
| Method | Path | Notes |
|---|---|---|
| WS | `/ws/live` | Gemini Live bridge. Binary frames = PCM audio; text frames = transcript / quotes / cart / checkout events. |

### Intent (voice / photo / text)
| Method | Path | Body |
|---|---|---|
| POST | `/api/session/start` | `{input_type, text?/audio_b64?/image_b64?, user_name?}` |
| POST | `/api/session/{id}/chat` | `{message}` — multi-turn clarify |
| POST | `/api/session/{id}/search` | — → quotes + winner + cart |

### Connect a store (real browser OAuth)
| Method | Path | Body / Notes |
|---|---|---|
| POST | `/api/connect/{store}/oauth/start` | `{phone?}` → `{auth_url}` (opens the store's login; phone becomes `login_hint`) |
| GET  | `/api/oauth/callback` | loopback catcher the phone's browser redirects to (RFC 8252) |
| POST | `/api/oauth/complete` | `{code, state}` — resumes the token exchange |
| GET  | `/api/connect/{store}/status` | `{connected}` |
| GET  | `/api/merchants` | connect state of every store |

### Real order pipeline (Zepto / Instamart via MCP)
| Method | Path | Body |
|---|---|---|
| GET  | `/api/merchants/{id}/addresses` | saved delivery addresses |
| POST | `/api/merchants/{id}/order/prepare` | `{query, budget_inr?, qty?, address_id?}` — build cart + preview (no charge) |
| POST | `/api/merchants/{id}/order/prepare_cart` | `{items:[{query,...}], address_id?}` — multi-item cart preview |
| POST | `/api/merchants/{id}/order/confirm` | `{address_id, meta?}` — places the **real order**, returns the UPI payment link |
| POST | `/api/merchants/{id}/order/status` | `{order_id}` — poll payment status |
| GET  | `/api/orders` | order history |

### Extras
| Method | Path | Notes |
|---|---|---|
| POST | `/api/say` | Gemini TTS (Hinglish voice-out) |
| POST | `/api/visualize` | Nano Banana product image |
| GET  | `/api/merchants/{id}/tools` · POST `/api/merchants/{id}/call` | MCP tool discovery / dev passthrough |

`audio_b64` / `image_b64` may be raw base64 or a `data:<mime>;base64,…` URI.

## How connect + order actually work
- **Connect (RFC 8252 loopback OAuth):** the backend registers a client with the store's MCP
  (Dynamic Client Registration), returns the store's consent URL. The phone opens it, the user
  logs in with mobile + OTP on the store's own page, and the redirect is caught by a loopback
  listener on the device → posted back to `/api/oauth/complete` → token cached per merchant.
- **Order:** `search_products` → `select/pass address` → `update_cart` → preview
  (`confirmOrder=false`, no charge) → confirm (`confirmOrder=true`, real order + UPI link) →
  poll status. The app opens the returned link so the user pays with their own UPI PIN.

## Notes
- Tokens are cached under `.mcp-tokens/` (gitignored). Delete them to force a fresh connect.
- Stores rate-limit aggressively; MCP calls retry on HTTP 429 with capped exponential backoff.
