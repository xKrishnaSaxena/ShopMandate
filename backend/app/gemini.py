"""Gemini multimodal intent extraction (§9.2) — google-genai, structured JSON output.

Model mix: PRO for multimodal (audio/image), FLASH for text-only parsing.
Uses the Gemini Developer API (GOOGLE_API_KEY only — NOT Vertex).
"""

from __future__ import annotations

import base64
import os

from google import genai
from google.genai import types
from pydantic import BaseModel

from .models import Intent

FLASH_MODEL = os.environ.get("FLASH_MODEL", "gemini-flash-latest")
PRO_MODEL = os.environ.get("PRO_MODEL", "gemini-pro-latest")
IMAGE_MODEL = os.environ.get("IMAGE_MODEL", "gemini-2.5-flash-image")  # Nano Banana
TTS_MODEL = os.environ.get("TTS_MODEL", "gemini-2.5-flash-preview-tts")
TTS_VOICE = os.environ.get("TTS_VOICE", "Kore")

_client: genai.Client | None = None


def client() -> genai.Client:
    global _client
    if _client is None:
        _client = genai.Client(api_key=os.environ["GOOGLE_API_KEY"])
    return _client


SYSTEM = (
    "You are a shopping intent parser for Indian users. Input may be Hinglish or Kannada. "
    "Extract product, budget in INR, quantity, and constraints. If the product TYPE or BUDGET is "
    "ambiguous or missing, set needs_clarification=true and ask ONE short Hinglish question. "
    "Never invent a product the user did not mention. For audio, also echo the transcript."
)


class GeminiIntent(BaseModel):
    """Structured schema Gemini fills (§9.2)."""

    transcript: str = ""
    product: str = ""
    category: str = ""
    budget_inr: int | None = None
    qty: int = 1
    constraints: list[str] = []
    language: str = "hi-IN"
    needs_clarification: bool = False
    clarifying_question: str | None = None


def _to_intent(g: GeminiIntent) -> Intent:
    lang = g.language if g.language in ("hi-IN", "kn-IN", "en-IN") else "hi-IN"
    return Intent(
        product=g.product,
        category=g.category,
        budget_inr=g.budget_inr,
        qty=g.qty or 1,
        constraints=g.constraints,
        language=lang,  # type: ignore[arg-type]
        transcript=g.transcript,
        needs_clarification=g.needs_clarification,
        clarifying_question=g.clarifying_question,
    )


def _config() -> types.GenerateContentConfig:
    return types.GenerateContentConfig(
        system_instruction=SYSTEM,
        response_mime_type="application/json",
        response_schema=GeminiIntent,
        temperature=0.2,
    )


def _decode(b64: str) -> tuple[bytes, str | None]:
    """Return (raw_bytes, mime) — supports optional `data:<mime>;base64,` prefix."""
    mime = None
    if b64.startswith("data:"):
        header, b64 = b64.split(",", 1)
        mime = header[5:].split(";")[0] or None
    return base64.b64decode(b64), mime


def _parsed(resp: types.GenerateContentResponse) -> Intent:
    g = resp.parsed
    if not isinstance(g, GeminiIntent):
        g = GeminiIntent.model_validate_json(resp.text or "{}")
    return _to_intent(g)


def extract_intent_from_text(text: str, language_hint: str | None) -> Intent:
    resp = client().models.generate_content(
        model=FLASH_MODEL,
        contents=f"User said (language hint {language_hint or 'auto'}): {text}",
        config=_config(),
    )
    return _parsed(resp)


def extract_intent_from_audio(audio_b64: str, language_hint: str | None) -> Intent:
    raw, mime = _decode(audio_b64)
    resp = client().models.generate_content(
        model=PRO_MODEL,
        contents=[
            types.Part.from_bytes(data=raw, mime_type=mime or "audio/wav"),
            types.Part.from_text(
                text=f"Transcribe and parse this shopping request (language hint {language_hint or 'auto'})."
            ),
        ],
        config=_config(),
    )
    return _parsed(resp)


class ChatReply(BaseModel):
    """One conversational turn from the clarify-chat agent (structured)."""

    reply: str = ""                       # 1-2 short Hinglish sentences
    product: str | None = None            # updated only if the user changed it
    budget_inr: int | None = None
    qty: int | None = None
    constraints: list[str] = []           # FULL updated list (not a delta)
    suggestions: list[str] = []           # 2-4 very short tappable quick-replies
    ready_to_search: bool = False         # user signalled "let's proceed"
    show_product_image: bool = False      # user asked to see / visualize the product


CHAT_SYSTEM = (
    "You are ShopMandate's shopping assistant for Indian users — warm, quick, and "
    "conversational in Hinglish (Roman Hindi + English). NEVER robotic, never long. "
    "You are helping the user lock down WHAT to buy before the app makes stores compete on price.\n\n"
    "You can, contextually:\n"
    "1. Refine product, budget (₹ INR), quantity and constraints from what the user says.\n"
    "2. REVIEWS: when asked 'log kya bolte hain' / reviews / ratings, give ONE punchy honest "
    "Hinglish line with a star rating and the real trade-off, e.g. '4.2⭐ · sound accha, mic "
    "average, battery solid'. Base it on typical real-world sentiment for such a product.\n"
    "3. SMART SUBSTITUTION: if an item may be costly or out of stock, proactively suggest a "
    "cheaper/similar alternative and ask if it's OK — e.g. 'wo mehnga hai, ye ₹50 sasta similar "
    "hai — theek?'. If the user agrees, update product to the alternative.\n"
    "4. RECURRING / SUBSCRIPTION reorder ('har hafte doodh-atta mangwa do'): confirm the cadence "
    "in your reply and add a constraint like 'subscription:weekly'.\n"
    "5. PRICE-DROP / RESTOCK AUTO-BUY ('earbuds ₹1500 ho jaye to auto-buy'): confirm the cap in "
    "your reply and add a constraint like 'autobuy<=1500'.\n\n"
    "RULES:\n"
    "- Reply in 1-2 short Hinglish sentences. Emojis sparingly (0-1).\n"
    "- Update product/budget_inr/qty ONLY when the user clearly changes them; otherwise leave them "
    "null so the existing value is kept.\n"
    "- constraints: return the FULL updated list (keep existing ones, add new).\n"
    "- suggestions: 2-4 VERY short (2-4 words) tappable Hinglish quick-replies the user might tap "
    "next. Make them contextual — mix feature actions ('Log kya bolte hain?', 'Sasta similar', "
    "'Har hafte mangwa do', 'Price gire to lelo') with the obvious next step ('Aage badho').\n"
    "- Set ready_to_search=true when the user signals they are done / says proceed / 'aage badho'.\n"
    "- Set show_product_image=true only if the user asks to SEE or visualize the product."
)


def chat_turn(intent: Intent, history: list[dict], message: str, user_name: str | None) -> "ChatReply":
    """One clarify-chat turn: read the intent + short history, return reply + intent updates."""
    convo = "\n".join(f"{h.get('role')}: {h.get('text')}" for h in history[-8:])
    ctx = (
        f"User's name: {user_name or 'dost'}\n"
        f"Current understanding — product: {intent.product or '?'}; "
        f"budget_inr: {intent.budget_inr if intent.budget_inr is not None else 'none'}; "
        f"qty: {intent.qty}; constraints: {intent.constraints}\n\n"
        f"Conversation so far:\n{convo or '(none)'}\n\n"
        f"User's new message: {message}"
    )
    resp = client().models.generate_content(
        model=FLASH_MODEL,
        contents=ctx,
        config=types.GenerateContentConfig(
            system_instruction=CHAT_SYSTEM,
            response_mime_type="application/json",
            response_schema=ChatReply,
            temperature=0.5,
        ),
    )
    r = resp.parsed
    if not isinstance(r, ChatReply):
        r = ChatReply.model_validate_json(resp.text or "{}")
    return r


def value_note(summary: str) -> str:
    """Deep-research-lite: a short Hinglish 'why this is the best value' line for the winning quote."""
    resp = client().models.generate_content(
        model=FLASH_MODEL,
        contents=(
            "You are a savvy Indian shopping advisor. Given these store quotes, write ONE short, "
            "confident Hinglish line explaining why the cheapest pick is the best value "
            "(price + delivery). Max 18 words, no preamble.\n\n" + summary
        ),
    )
    return (resp.text or "").strip()


def _pcm_to_wav(pcm: bytes, rate: int = 24000) -> bytes:
    import io
    import wave
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)  # 16-bit
        w.setframerate(rate)
        w.writeframes(pcm)
    return buf.getvalue()


def gen_visual(prompt: str, image_b64: str | None = None) -> bytes:
    """Nano Banana: generate a clean product / try-on image (optionally 'like this' from a photo)."""
    parts: list[types.Part] = []
    if image_b64:
        raw, mime = _decode(image_b64)
        parts.append(types.Part.from_bytes(data=raw, mime_type=mime or "image/jpeg"))
    parts.append(types.Part.from_text(text=prompt))
    resp = client().models.generate_content(
        model=IMAGE_MODEL,
        contents=parts,
        config=types.GenerateContentConfig(response_modalities=["IMAGE"]),
    )
    for p in resp.candidates[0].content.parts:
        data = getattr(p, "inline_data", None)
        if data and data.data:
            return data.data
    raise RuntimeError("no image generated")


def say(text: str) -> bytes:
    """Gemini TTS → WAV bytes (Hinglish voice-out)."""
    resp = client().models.generate_content(
        model=TTS_MODEL,
        contents=text,
        config=types.GenerateContentConfig(
            response_modalities=["AUDIO"],
            speech_config=types.SpeechConfig(
                voice_config=types.VoiceConfig(
                    prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name=TTS_VOICE)
                )
            ),
        ),
    )
    pcm = resp.candidates[0].content.parts[0].inline_data.data
    return _pcm_to_wav(pcm)


def identify_from_image(image_b64: str) -> Intent:
    raw, mime = _decode(image_b64)
    resp = client().models.generate_content(
        model=PRO_MODEL,
        contents=[
            types.Part.from_bytes(data=raw, mime_type=mime or "image/jpeg"),
            types.Part.from_text(
                text="Identify the product in this image and infer what the user wants to buy."
            ),
        ],
        config=_config(),
    )
    return _parsed(resp)
