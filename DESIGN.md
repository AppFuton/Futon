---
name: Onyx Gold
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#3a3939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353534'
  on-surface: '#e5e2e1'
  on-surface-variant: '#d8c3ae'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#a08d7a'
  outline-variant: '#534434'
  surface-tint: '#ffb963'
  primary: '#ffcc92'
  on-primary: '#472a00'
  primary-container: '#fea628'
  on-primary-container: '#6a4000'
  inverse-primary: '#865300'
  secondary: '#f8b993'
  on-secondary: '#4c260b'
  secondary-container: '#673c1f'
  on-secondary-container: '#e4a883'
  tertiary: '#d4dd58'
  on-tertiary: '#303300'
  tertiary-container: '#b8c13e'
  on-tertiary-container: '#494d00'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffddb9'
  primary-fixed-dim: '#ffb963'
  on-primary-fixed: '#2b1700'
  on-primary-fixed-variant: '#663e00'
  secondary-fixed: '#ffdbc8'
  secondary-fixed-dim: '#f8b993'
  on-secondary-fixed: '#321300'
  on-secondary-fixed-variant: '#673c1f'
  tertiary-fixed: '#e2eb64'
  tertiary-fixed-dim: '#c5ce4b'
  on-tertiary-fixed: '#1b1d00'
  on-tertiary-fixed-variant: '#464a00'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
  velvet-black: '#0D0D0D'
  warm-obsidian: '#121110'
  warm-ivory: '#F1DFD5'
  warm-clay: '#A48C7F'
  manga-gold: '#FEA628'
  espresso-container: '#673C1F'
  amoled-black: '#000000'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: '800'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  title-md:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0.01em
  title-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.007em
  body-base:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: '0'
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: '0'
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
  stat-huge:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.03em
  headline-md-mobile:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '700'
    lineHeight: 28px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  margin-mobile: 16px
  margin-desktop: 24px
  gutter: 8px
---

## Brand & Style

This design system is a high-fidelity implementation of Material Design 3 (M3) tailored for an immersive, premium dark-mode media experience. It balances the structured utility of a content-heavy reader with the "luxurious" tactile feel of high-end consumer hardware.

The aesthetic is defined by **Warm Minimalism**. We avoid sterile pure blacks in favor of deep, velvet-toned obsidian neutrals that provide a sophisticated backdrop for vibrant manga artwork. The emotional response should be one of "focused luxury"—where the interface recedes to highlight content but feels substantial and responsive when engaged.

Key characteristics include:
- **Depth through Tone:** Utilizing the M3 tonal surface system to create hierarchy without heavy drop shadows.
- **High-Contrast Accents:** Using a singular, vibrant gold brand color for all primary interactions and progress indicators.
- **Fluid Responsiveness:** Smooth transitions between library views and immersive reading modes.

## Colors

The color system is built on a foundation of **Velvet Black** to minimize eye strain and maximize the pop of color manga covers. 

- **Primary (Manga Gold):** Used for CTA buttons, active navigation states, and critical progress indicators. 
- **Secondary (Espresso):** Provides a muted, warm container for secondary actions and toggles, preventing the UI from feeling too "loud."
- **Typography:** We use **Warm Ivory** for primary text instead of pure white to reduce glare, and **Warm Clay** for secondary metadata to create a natural hierarchy.
- **Surfaces:** Containers use the `warm-obsidian` hex to distinguish elevated panels from the base background. For devices with OLED displays, the system allows for a "True Black" override using `#000000`.

## Typography

This design system uses **Inter** for its neutral, highly legible characteristics. The type scale is intentionally bold to counteract the "thinning" effect of light text on dark backgrounds.

- **Display & Headlines:** Use tight letter spacing and heavy weights to create a strong editorial feel for manga titles.
- **Body & Labels:** Prioritize legibility. Labels utilize increased letter spacing (`0.05em`) to remain clear at very small sizes, such as on manga tags or chapter numbers.
- **Reading Comfort:** Body text is set with a generous `1.5x` line height to ensure descriptions and logs are easy to scan.

## Layout & Spacing

The layout is built on a **fluid grid system** that adapts to the high-density requirements of a media library.

- **Grid Model:** Manga covers use a responsive grid with a preferred card width of `120dp`. Columns scale dynamically based on screen width.
- **Rhythm:** An `8px` base unit governs all spacing. Vertical spacing between sections uses `24px` to provide clear separation.
- **Manga Reader Layout:** In the reader view, the layout transitions to a "No Grid" model, allowing content to be truly edge-to-edge. Vertical margins between pages in Webtoon mode are set to `24px` to create a physical "breathing room" between story beats.
- **Safe Areas:** Mobile layouts respect a `16px` outer margin, while tablet and desktop layouts expand to `24px` to maintain visual balance on larger displays.

## Elevation & Depth

We utilize **Tonal Layers** as the primary method of communicating depth, adhering to Material Design 3 principles.

- **Surface Tiers:**
  - **Level 0 (Background):** Velvet Black (`#0D0D0D`).
  - **Level 1 (Cards/Panels):** Warm Obsidian (`#121110`).
  - **Level 2 (Dialogs/Menus):** A lighter tint of obsidian or a very low-opacity white overlay (5-8%).
- **Interactive Depth:** We avoid heavy drop shadows. Instead, elevated states are signaled by subtle `1px` inner outlines in **Warm Clay** at 15% opacity. 
- **AMOLED Mode:** When active, tonal layers are flattened. Hierarchy is conveyed solely through high-contrast strokes and typography weights to preserve battery life and prevent "ghosting."

## Shapes

The shape language is **Soft-Rounded**, providing a modern and friendly feel that contrasts with the "serious" dark color palette.

- **Standard Containers:** Use a `0.5rem` (8px) radius.
- **Large Components (Manga Covers):** Use a `1rem` (16px) radius to emphasize the "object-like" quality of book covers.
- **Tactile Inputs:** Large sliders and buttons use the `rounded-xl` or pill-shaped (`full`) setting to maximize touch-target friendliness.
- **Indicators:** Circular progress bubbles are perfectly rounded to `9999px` to stand out as distinct UI overlays.

## Components

### Media Cards
Manga covers must maintain a strict **18:13 aspect ratio**. Use `centerCrop` for images. Titles below the card should be limited to 2 lines with an ellipsis.

### Buttons & Chips
- **Primary Buttons:** Solid **Manga Gold** fill with **Pure Black** text. Minimum height `56dp` for flagship actions.
- **Chips:** Outlined with a `1px` Warm Clay border. Active states switch to a gold tinted background at 20% opacity.

### Lists & Progress
- **Chapter Items:** Horizontal layout with a large touch target (`48dp` min height). Use Warm Ivory for chapter names and Warm Clay for timestamps.
- **Progress Bubbles:** Floating `32dp` bubbles. Use a `4dp` gold stroke for the progress arc. When 100% complete, replace the percentage text with a gold check icon.

### Inputs & Sliders
- **Tactile Sliders:** Thick `40dp` tracks in Espresso with a `48dp` thumb. This "fat" slider style makes it easier to adjust brightness or font size during one-handed reading.

### Bottom Navigation
Clean, borderless icons. The active state is indicated by a Manga Gold icon and a small pill-shaped indicator behind it.