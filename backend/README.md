# ShopMandate backend (Python · FastAPI · google-genai)

Implements the **frozen §8 API contract** from `../product.md`. Language-agnostic to the app (REST) —
the Android frontend talks to these endpoints only.

## Modules (§9)
- `app/gemini.py` — multimodal intent (audio/image/text) via Gemini, structured JSON output.
  Model mix: `PRO_MODEL` (multimodal) + `FLASH_MODEL` (text). Developer API (GOOGLE_API_KEY, not Vertex).
- `app/stores.py` — 2 mock stores + ₹ catalog · `app/negotiation.py` — compare + 1 haggle round + budget guard
- `app/payment.py` — mock UPI charge → receipt · `app/connect.py` — mock OTP (accepts `123456` / any 6 digits)
- `app/orchestrator.py` — session lifecycle (in-memory) · `app/main.py` — FastAPI routes

## Run
```bash
cd backend
# .env already holds GOOGLE_API_KEY + FLASH_MODEL/PRO_MODEL (gitignored)
uv run uvicorn app.main:app --host 0.0.0.0 --port 5055 --reload
```
Base URL: `http://<host>:5055/api`  (8080/8090 were busy on this machine — pick any free port).

## Endpoints (§8)
| Method | Path | Body |
|---|---|---|
| GET  | `/api/health` | — |
| POST | `/api/session/start` | `{input_type, text?/audio_b64?/image_b64?, language_hint?}` |
| POST | `/api/session/{id}/clarify` | `{answers:{type?,budget_inr?,qty?}}` |
| POST | `/api/session/{id}/search` | — → quotes, steps, winner, cart |
| POST | `/api/session/{id}/connect/start` | `{phone}` |
| POST | `/api/session/{id}/connect/verify` | `{otp}` |
| POST | `/api/session/{id}/pay` | `{method:"upi", upi_app}` → receipt |

`audio_b64` / `image_b64` may be raw base64 or a `data:<mime>;base64,…` URI.

## Quick smoke test
```bash
B=http://localhost:5055/api
SID=$(curl -s -X POST $B/session/start -H 'Content-Type: application/json' \
  -d '{"input_type":"text","text":"mujhe 2000 ke andar wireless earbuds chahiye"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["session_id"])')
curl -s -X POST $B/session/$SID/search | python3 -m json.tool
curl -s -X POST $B/session/$SID/pay -H 'Content-Type: application/json' -d '{"method":"upi","upi_app":"gpay"}'
```

## Phase 2 (later)
Swap `connect.py`/`payment.py`/`stores.py` for the **real Zepto MCP + AP2 mandate** service already built
in `../../zepto/` (real order + UPI Juspay link + signed Intent→Cart→Payment chain). The OTP/connect shapes
here mirror the real OAuth-OTP so the swap is trivial.
