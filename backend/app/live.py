"""Gemini Live API bridge — real-time DUPLEX voice for the shopping agent (Phase 2).

The app opens a WebSocket and streams mic PCM (16 kHz, 16-bit, mono) up; we relay it into a
Gemini **Live** native-audio session and stream the agent's spoken reply (24 kHz PCM) + live
transcripts back down. Barge-in / interrupt is native to the Live model.

Reuses the same Developer API key as the rest of the backend (`GOOGLE_API_KEY`).
Verified against google-genai 2.11.0 (client.aio.live.connect / send_realtime_input / receive).
"""

from __future__ import annotations

import os

from google import genai
from google.genai import types

# Native-audio Live models on this key: gemini-3.1-flash-live-preview,
# gemini-2.5-flash-native-audio-latest, gemini-3.5-live-translate-preview.
LIVE_MODEL = os.environ.get("LIVE_MODEL", "gemini-3.1-flash-live-preview")

# Audio contract (Gemini Live): input 16 kHz PCM, output 24 kHz PCM (16-bit mono).
INPUT_RATE = 16000
OUTPUT_RATE = 24000
INPUT_MIME = f"audio/pcm;rate={INPUT_RATE}"

SYSTEM = (
    "You are ShopMandate's voice shopping assistant for Indian users. "
    "Speak natural, warm, casual Hinglish in short conversational turns (1-2 sentences). "
    "Help the user find a product within their budget: understand what they want, ask ONE "
    "short question if the product or budget is unclear, suggest options, and confirm the pick. "
    "Be snappy and friendly, like a helpful dukaandar. Prices are in rupees (₹). "
    "Never read out long lists; keep it human and quick."
)


def _client() -> genai.Client:
    # Developer API (GOOGLE_API_KEY), same as gemini.py — NOT Vertex.
    return genai.Client(api_key=os.environ["GOOGLE_API_KEY"])


def config() -> types.LiveConnectConfig:
    return types.LiveConnectConfig(
        response_modalities=[types.Modality.AUDIO],
        input_audio_transcription=types.AudioTranscriptionConfig(),
        output_audio_transcription=types.AudioTranscriptionConfig(),
        system_instruction=types.Content(parts=[types.Part(text=SYSTEM)]),
    )


def connect():
    """Async context manager for a live session:  `async with live.connect() as session:`"""
    return _client().aio.live.connect(model=LIVE_MODEL, config=config())
