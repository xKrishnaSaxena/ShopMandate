---
name: ShopMandate
colors:
  surface: '#fdf7ff'
  surface-dim: '#dfd6ed'
  surface-bright: '#fdf7ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f8f1ff'
  surface-container: '#f3eaff'
  surface-container-high: '#ede4fc'
  surface-container-highest: '#e7dff6'
  on-surface: '#1d1929'
  on-surface-variant: '#474556'
  inverse-surface: '#322e3f'
  inverse-on-surface: '#f5eeff'
  outline: '#787587'
  outline-variant: '#c8c4d9'
  surface-tint: '#563bed'
  primary: '#3600cd'
  on-primary: '#ffffff'
  primary-container: '#4f32e7'
  on-primary-container: '#cfc8ff'
  inverse-primary: '#c6bfff'
  secondary: '#ae3104'
  on-secondary: '#ffffff'
  secondary-container: '#fe693c'
  on-secondary-container: '#601600'
  tertiary: '#004b2f'
  on-tertiary: '#ffffff'
  tertiary-container: '#006541'
  on-tertiary-container: '#60e6a6'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e4dfff'
  primary-fixed-dim: '#c6bfff'
  on-primary-fixed: '#160066'
  on-primary-fixed-variant: '#3d10d7'
  secondary-fixed: '#ffdbd1'
  secondary-fixed-dim: '#ffb59f'
  on-secondary-fixed: '#3b0a00'
  on-secondary-fixed-variant: '#862200'
  tertiary-fixed: '#76fbb9'
  tertiary-fixed-dim: '#57de9f'
  on-tertiary-fixed: '#002112'
  on-tertiary-fixed-variant: '#005234'
  background: '#fdf7ff'
  on-background: '#1d1929'
  surface-variant: '#e7dff6'
typography:
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  caption:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  container-padding: 20px
  gutter: 16px
  stack-sm: 12px
  stack-md: 24px
  stack-lg: 40px
---

## Brand & Style
The design system centers on "Apple-clean meets Indian-warmth." It is built for an effortless, voice-first shopping experience that feels like a trusted personal assistant. The aesthetic is rooted in **Modern Minimalism** with a **Tactile** edge—utilizing high-quality whitespace to reduce cognitive load for everyday users. The emotional response is one of calm reliability and premium accessibility. Every interaction should feel soft and forgiving, moving away from "tech-heavy" interfaces toward a conversational, human-centric environment.

## Colors
The palette balances the authority of **Deep Indigo** with the vibrant energy of **Warm Coral-Saffron**.
- **Primary (Deep Indigo):** Used for key branding elements, primary navigation, and active states to instill trust.
- **Accent/CTA (Warm Coral-Saffron):** Reserved for high-conversion actions and the primary voice interface to feel inviting and energetic.
- **Background (Warm Off-white):** A soft, creamy base that reduces eye strain and provides a more premium feel than pure white.
- **Surface (White):** Used for cards and modals to create clear separation from the background.
- **Functional Colors:** Emerald Green handles success states and price drops, ensuring clarity in the shopping flow.

## Typography
**Plus Jakarta Sans** is the sole typeface, chosen for its friendly, open apertures and modern geometric structure. 
- **Headlines:** Set with tight letter-spacing and bold weights to provide a confident "Apple-style" hierarchy.
- **Body Text:** Uses generous line-height (1.5x minimum) to ensure readability for Hinglish microcopy, which often includes longer word structures.
- **Scale:** High contrast between headlines and body text helps users scan information quickly during a voice-based interaction.

## Elevation & Depth
This design system uses **Tonal Layers** combined with **Ambient Shadows**. 
- **Levels:** Depth is conveyed through subtle color shifts and shadows rather than harsh borders.
- **Shadow Profile:** Shadows are extremely soft (Blur: 20-30px) with very low opacity (5-8%) and a slight tint of the Primary color (#4F32E7) to prevent them from looking "dirty."
- **Interaction:** Cards should appear to "lift" slightly on tap, increasing shadow spread to provide tactile feedback to the user.

## Shapes
The shape language is "Hyper-Rounded." 
- **Containers:** All primary cards and bottom sheets use a 24px corner radius to evoke a friendly, non-threatening feel.
- **Actionable Elements:** Buttons and chips always use a full-pill (999px) radius.
- **Touch Targets:** Minimum touch target size is 48x48px, but primary actions are encouraged to be 56px or taller for accessibility.

## Components
- **Microphone Button:** The center-piece. A large, circular floating action button with a Coral-Saffron gradient and a live waveform animation that pulses during active listening.
- **Full-Width Pill Buttons:** Used for "Buy Now" or "Add to Cart." They occupy the full width of their container minus padding.
- **Product Cards:** Feature high-quality imagery with a subtle 1px inner border and the standard soft shadow. Price and "Best Match" labels are prominently displayed.
- **Comparison Cards:** A side-scrolling horizontal list that highlights price differences across different stores (e.g., Blinkit vs. Amazon).
- **Quick-Reply Chips:** Small, pill-shaped outlines or light-tinted backgrounds (e.g., #4F32E7 at 10% opacity) that allow users to tap common voice follow-ups.
- **UPI Bottom-Sheet:** A clean, white surface that slides from the bottom, presenting saved VPA IDs in a simple list with large, tappable radio buttons.
- **Iconography:** Use a 2px stroke weight with rounded caps and joins. Avoid sharp angles.