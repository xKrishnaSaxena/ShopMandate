# ShopMandate — Stitch Design Prompts (Phase 1)

Voice & photo shopping assistant for India. Flow: user **bolke ya photo** se batata hai → agent
samajhta hai → stores compare → user approve → **UPI payment** → receipt.
(AP2 cryptographic signing = Phase 2, isliye yahan sirf ek halka "verifiable receipt" teaser hai.)

---

## How to use Stitch (2-min read)

1. Open **stitch.withgoogle.com**. For a polished, pleasing UI use **Experimental / Pro mode** (fewer
   generations but much better looking). Standard/Flash mode is faster for quick iterations.
2. **Paste the MASTER PROMPT (Section 0) first.** It sets the design system + generates the Home screen.
3. Then add screens one at a time: paste each **Screen prompt (1–8)**. Stitch keeps the style consistent
   across screens automatically once the system is set.
4. To fix a screen, just tell it in plain English ("make the mic button bigger", "warmer background").
5. **Camera/photo:** you can also drop a reference image into Stitch to steer a screen's look.
6. When happy → **Export**: use "Copy code" (front-end code) AND take a screenshot/PNG of each screen.
   Put everything in a `/design` folder (details in the last section).

---

## 0. MASTER PROMPT — paste this FIRST

> Design a **mobile app (Android, portrait)** called **ShopMandate** — a voice-and-photo shopping
> assistant for everyday Indian users who would rather **speak or point their camera** than type or tap
> through menus. All prices are in **Indian Rupees (₹)** and the microcopy is friendly **Hinglish**
> (Roman script), e.g. "Kya chahiye aaj?".
>
> **Design system — apply to every screen:**
> - **Mood:** warm, friendly, trustworthy, effortless, modern. "Apple-clean meets Indian-warmth."
>   Lots of whitespace, calm, premium but approachable. Nothing cold or corporate.
> - **Colors:**
>   - Brand / primary: deep indigo **#4F32E7**
>   - CTA / accent: warm coral-saffron **#FF6A3D**
>   - Background (light): warm off-white **#FFF8F3**
>   - Cards / surfaces: white **#FFFFFF** with soft, low shadows
>   - Success: green **#1FB57A**
>   - Text primary **#1A1626**, secondary grey **#6B6577**
> - **Typography:** a rounded, friendly sans-serif (Poppins / Plus Jakarta Sans / Inter). Large, confident
>   headings; generous line-height; short lines.
> - **Shape:** large rounded corners (cards 24px, buttons full-pill), soft shadows, big touch targets.
> - **Components:** full-width pill CTA buttons, quick-reply chips, product cards, a breathing mic button
>   with a waveform, store-comparison cards, a UPI bottom-sheet.
> - **Iconography:** rounded, minimal, friendly.
>
> **Start by designing the HOME screen:**
> - Top: a small warm greeting "Namaste 👋" + a big friendly headline **"Kya chahiye aaj?"**.
> - Center hero: a large circular **mic button** (indigo→coral gradient) with a soft pulsing/breathing
>   ring, label under it **"Bolke batao"**.
> - Just below/beside it: a secondary **camera button** (outline, rounded), label **"Ya photo dikhao"**.
> - Bottom: a slim, optional text input pill "…ya type karo" (de-emphasised — voice/photo is the hero).
> - Optional: 2–3 recent/suggested chips like "Wireless earbuds", "Doodh 2L", "Phone cover".
> - Keep it airy and inviting, not busy.

---

## 1. Home (voice-first landing)
> Refine the HOME screen: make the **mic button the clear hero** — big, centered, indigo→coral gradient,
> a gentle pulsing ring that suggests "tap to speak". Greeting "Namaste 👋" small on top, then bold
> **"Kya chahiye aaj?"**. Under the mic: **"Bolke batao"**. Secondary rounded **camera** button labelled
> **"Ya photo dikhao"**. A thin de-emphasised text pill at the very bottom "…ya type karo". Add 3 soft
> suggestion chips: "Wireless earbuds", "Atta 5kg", "Phone charger". Warm off-white background, plenty of
> breathing room.

## 2. Voice listening (live)
> Add a **VOICE LISTENING** screen (full-screen state after tapping the mic). Dark-warm or indigo-tinted
> immersive background. Center: an **animated waveform** reacting to the voice. Top label **"Sun raha
> hoon…"**. Below the waveform, show the **live transcription** appearing as Hinglish text, e.g.
> *"mujhe ₹2000 ke andar acche wireless earbuds chahiye"*. A big round **stop/done** button at the bottom
> and a small "tap to cancel". It should feel alive and responsive, like the app is truly listening.

## 3. Camera capture
> Add a **CAMERA** screen. Full-screen camera viewfinder with rounded corners and a subtle frame. Top
> hint pill **"Jo chahiye uspe camera point karo"**. A large round **shutter** button at bottom center,
> a small **gallery** icon on the left, a **flip camera** icon on the right. After capture, show a small
> chip overlaying the detected item, e.g. **"Dikha: Wireless earbuds jaisa"** with a checkmark and a
> **"Ye hi chahiye"** confirm button. Keep controls big and thumb-friendly.

## 4. Understanding + clarifying question
> Add an **UNDERSTANDING** screen. At top, a friendly assistant avatar/bubble showing what it understood
> as a neat card: **"Wireless earbuds • Budget ₹2000 • Qty 1"** (each as a small editable chip). Below,
> a warm clarifying question in a chat bubble: **"Ek baat — wireless hi chahiye ya wired bhi chalega?"**
> with **quick-reply chips**: "Wireless", "Wired bhi ok". Add a second row of budget chips: "₹1500",
> "₹2000", "₹2500", "Koi budget nahi". A primary pill button **"Aage badho"**. Feels like a smart,
> respectful assistant confirming intent — not a form.

## 5. Comparing stores (the fun part)
> Add a **COMPARING STORES** screen. Top status line with a small spinner: **"Best deal dhoond raha
> hoon…"**. Show **two store cards side by side (or stacked)**: **Store A** and **Store B**, each with a
> product thumbnail, store name, a **price in ₹ that looks like it's updating** (Store A ₹1,950, Store B
> ₹1,800), and a small tag ("Delivery: kal" / "Delivery: aaj"). Highlight the cheaper one with a subtle
> **"₹150 sasta" badge** in coral. A little animated "vs" in the middle to convey competition. Bottom: a
> live log line like **"Store B ne behtar offer diya ✓"**. Make it feel like a live negotiation, exciting.

## 6. Recommendation — Approve cart
> Add an **APPROVE** screen — the key decision moment. A big hero **product card**: image, name
> **"boAt Airdopes 141 — Wireless Earbuds"**, big price **"₹1,800"**, delivery **"kal shaam tak"**.
> A highlighted reason strip in coral: **"Store B chuna — ₹150 sasta + jaldi delivery"**. Small details
> list (color, warranty, qty 1). At bottom, a large full-width pill CTA **"Approve & Pay ₹1,800"** in
> coral, and a secondary text button **"Nahi, kuch aur dikhao"**. Make it reassuring and clear —
> the user sees EXACTLY what they're buying and why.

## 7. UPI Payment (framing)
> Add a **PAYMENT** screen as a **UPI bottom-sheet** style. Top: **"Pay ₹1,800"** big and clear, with the
> item name small underneath. A row of **UPI app options as selectable tiles**: **Google Pay, PhonePe,
> Paytm, BHIM** (rounded icons + labels). A "UPI ID" alternative row. A small trust line with a lock icon:
> **"Secure UPI payment — aapki approval pe hi charge hoga"**. Large pill CTA **"Pay ₹1,800 securely"** in
> coral. Then show a second state: an **"Authorizing…"** overlay with a UPI-style PIN dots animation and
> text **"UPI PIN se approve karein"** (mock). Clean, familiar, trustworthy — like a real Indian UPI flow.

## 8. Success / Receipt
> Add a **SUCCESS** screen. A satisfying green **checkmark** animation at top, big **"Order confirmed! 🎉"**.
> A clean **receipt card**: item name, **₹1,800**, Store B, **Order ID #SM-4821**, **"Delivery: kal shaam"**.
> Below, a subtle collapsible section titled **"Verifiable receipt"** showing a small 3-step chain
> **Intent → Cart → Payment**, each with a tiny check + lock icon (kept light and elegant — this hints at
> the cryptographic audit trail). Bottom: two buttons — primary **"Track order"**, secondary **"Ho gaya"**.
> Warm, celebratory but trustworthy.

## 9. Connect store account (OAuth + OTP)

**When does this appear?** This is a **one-time** step to link the user's real store account (Zepto /
Swiggy) via their **official OAuth** — the store sends an **OTP to an Indian mobile number**. Show it
either at **first launch (onboarding)** or **right before the FIRST real order** (after *Approve*, before
*Payment*). Once connected, it's skipped for future orders.

### 9a. Connect store (phone number)
> Add a **CONNECT STORE** screen, shown once to link the user's real store account (e.g. Zepto / Swiggy)
> via official OAuth. Friendly header **"Apna store account connect karo"** with subtext **"Order place
> karne ke liye ek baar login — official Zepto/Swiggy connection, fully secure."** Show small
> official-looking store badges (Zepto, Swiggy) with an **"Official partner"** tag and a **lock icon**.
> A phone input with a fixed **+91** prefix and a 10-digit mobile number field. Large coral pill CTA
> **"OTP bhejo"**. A trust line under it: **"Number sirf store login ke liye — hum save nahi karte."**
> Clean, minimal, reassuring.

### 9b. OTP verification
> Add an **OTP VERIFICATION** screen. Header **"OTP daalo"** and subtext **"+91 98XXX-XX21 par code bheja
> hai"** with a small **"Number badlo"** link. A row of **6 rounded OTP input boxes**, auto-advancing, the
> first one focused/active. A **"Resend OTP"** link with a countdown timer (e.g. "Resend in 0:24", greyed
> until it hits zero). Large coral pill CTA **"Verify & Connect"**. Then show a **success state**: a green
> check with **"Zepto connected ✓"** that auto-continues to the next step. Familiar and fast — exactly
> like a standard Indian OTP login, trustworthy and effortless.

---

## Vibe swaps (optional — paste to change the whole look)

- **A) Premium fintech (default is close to this):** "Make it feel like a premium, trustworthy fintech
  app — deep indigo + coral, generous whitespace, soft shadows."
- **B) Warm desi bazaar:** "Shift to a warm Indian marketplace feel — saffron/terracotta + cream, subtle
  geometric Indian motifs, still clean and modern, not cluttered."
- **C) Playful & friendly:** "Make it playful and friendly for first-time smartphone users — bigger text,
  bigger buttons, rounder shapes, cheerful greens and corals, very simple."
- **D) Dark mode:** "Add a dark theme variant — deep plum-charcoal #151221 background, same indigo/coral
  accents, high contrast."

---

## What to export for me (so I can build the app end-to-end)

Put all of this in a `/design` folder in the repo:
1. **Front-end code** from Stitch — use **"Copy code" / export** on each screen (HTML/CSS or the code it
   gives). Save as `design/code/<screen-name>/`.
2. **Screenshots (PNG)** of every screen — `design/screens/01-home.png`, `02-voice.png`, etc.
   (These are the source of truth for pixel-matching.)
3. **Figma link** if you export to Figma (optional but helpful).
4. A one-line note per screen if anything differs from this doc.

Once that folder is in the repo, tell me and I'll pick the build stack (likely a mobile-web React app for
speed, styled to pixel-match these screens) and wire it to the agent + UPI-framed payment flow.
