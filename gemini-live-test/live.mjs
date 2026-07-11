// Gemini LIVE API (WebSocket) test — text in → spoken AUDIO out + transcription.
//
//   Usage:
//     GEMINI_API_KEY=xxx node live.mjs                      # default prompt
//     GEMINI_API_KEY=xxx node live.mjs "your question here" # custom prompt
//     GEMINI_API_KEY=xxx LIVE_MODEL=gemini-3.1-flash-live-preview node live.mjs
//
// Saves the model's spoken reply to reply.wav and prints the live transcript.
// (curl can't do this — the Live API is a WebSocket, so we use the SDK.)
import { GoogleGenAI, Modality } from '@google/genai';
import { writeFileSync } from 'node:fs';

const apiKey = process.env.GEMINI_API_KEY;
if (!apiKey) {
  console.error('Set GEMINI_API_KEY first:  GEMINI_API_KEY=your_key node live.mjs');
  process.exit(1);
}

const model = process.env.LIVE_MODEL || 'gemini-3.1-flash-live-preview';
const prompt = process.argv.slice(2).join(' ') || 'Explain how AI works in a few words';

const t0 = Date.now();
const log = (...a) => console.log(`[+${((Date.now() - t0) / 1000).toFixed(2)}s]`, ...a);
const queue = [];

// Wrap raw 24kHz/16-bit/mono PCM in a WAV header so the file is playable.
function wav(pcm, rate = 24000, ch = 1, bits = 16) {
  const h = Buffer.alloc(44);
  h.write('RIFF', 0); h.writeUInt32LE(36 + pcm.length, 4); h.write('WAVE', 8);
  h.write('fmt ', 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20);
  h.writeUInt16LE(ch, 22); h.writeUInt32LE(rate, 24);
  h.writeUInt32LE((rate * ch * bits) / 8, 28); h.writeUInt16LE((ch * bits) / 8, 32);
  h.writeUInt16LE(bits, 34); h.write('data', 36); h.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([h, pcm]);
}

const ai = new GoogleGenAI({ apiKey });

const session = await ai.live.connect({
  model,
  config: { responseModalities: [Modality.AUDIO], outputAudioTranscription: {} },
  callbacks: {
    onopen: () => log('WebSocket OPEN → connected to Live API'),
    onmessage: (m) => queue.push(m),
    onerror: (e) => log('ERROR:', e.message || e),
    onclose: (e) => log('CLOSED:', e.reason || '(clean)'),
  },
});

log(`model=${model}`);
log(`you: "${prompt}"`);
session.sendClientContent({
  turns: [{ role: 'user', parts: [{ text: prompt }] }],
  turnComplete: true,
});

const audio = [];
let transcript = '';
let done = false;
const deadline = Date.now() + 30_000;
while (!done && Date.now() < deadline) {
  if (queue.length === 0) { await new Promise((r) => setTimeout(r, 20)); continue; }
  const sc = queue.shift().serverContent;
  if (!sc) continue;
  for (const p of sc.modelTurn?.parts || []) {
    if (p.inlineData?.data) audio.push(Buffer.from(p.inlineData.data, 'base64'));
  }
  if (sc.outputTranscription?.text) transcript += sc.outputTranscription.text;
  if (sc.turnComplete) done = true;
}

const pcm = Buffer.concat(audio);
if (pcm.length) writeFileSync('reply.wav', wav(pcm));

log(`done=${done}  audio=${pcm.length} bytes (~${(pcm.length / 2 / 24000).toFixed(1)}s)  → reply.wav`);
console.log('\n=== gemini said (transcript) ===\n' + (transcript || '(no text)') + '\n================================');
if (pcm.length) console.log('▶  play it:  open reply.wav');
session.close();
process.exit(0);
