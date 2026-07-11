# gemini-live-test

Minimal **Gemini Live API** (WebSocket) tester. Text in → spoken audio out +
live transcription. Plain `curl` can't do this (Live API is a WebSocket), so
this uses the `@google/genai` SDK.

## Run

```bash
cd ~/gemini-live-test

# default prompt
GEMINI_API_KEY=your_key node live.mjs

# custom prompt
GEMINI_API_KEY=your_key node live.mjs "Tell me a fun fact about biryani"

# play the spoken reply
open reply.wav
```

## Notes

- On this API key the available Live models are all **native-audio** (no TEXT
  output), so the script requests `AUDIO` + transcription. Override with
  `LIVE_MODEL=... node live.mjs` if you get a text-capable Live model later.
- Available Live models on this key: `gemini-3.1-flash-live-preview`,
  `gemini-2.5-flash-native-audio-latest`, `gemini-3.5-live-translate-preview`.
- **Security:** never hardcode the key. Pass it via `GEMINI_API_KEY`. Rotate any
  key you've pasted into a chat/terminal history.
