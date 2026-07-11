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
    "Never invent a product the user did not mention. For audio, also echo the transcript. "
    "ALWAYS produce 2-4 short, product-SPECIFIC quick_reply options (Hinglish, max 3 words each) that "
    "directly answer your clarifying_question — e.g. for atta: ['Aashirvaad','Fortune','Koi bhi sasta']; "
    "for earbuds: ['Wireless','Wired bhi ok']; for a phone: ['5G','Camera best','Battery best']. "
    "Set budget_relevant=true only when a rupee budget is meaningful for this product "
    "(true for electronics/appliances, usually false for small groceries)."
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
    quick_replies: list[str] = []
    budget_relevant: bool = True


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
        quick_replies=g.quick_replies[:4],
        budget_relevant=g.budget_relevant,
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
