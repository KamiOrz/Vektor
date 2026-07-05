---
name: Vektor v7.0
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#20201f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353535'
  on-surface: '#e5e2e1'
  on-surface-variant: '#cfc4c5'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#988e90'
  outline-variant: '#4c4546'
  surface-tint: '#c6c6c6'
  primary: '#c6c6c6'
  on-primary: '#303030'
  primary-container: '#000000'
  on-primary-container: '#757575'
  inverse-primary: '#5e5e5e'
  secondary: '#c6c6c7'
  on-secondary: '#2f3131'
  secondary-container: '#454747'
  on-secondary-container: '#b4b5b5'
  tertiary: '#2ae500'
  on-tertiary: '#053900'
  tertiary-container: '#000000'
  on-tertiary-container: '#168800'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c6'
  on-primary-fixed: '#1b1b1b'
  on-primary-fixed-variant: '#474747'
  secondary-fixed: '#e2e2e2'
  secondary-fixed-dim: '#c6c6c7'
  on-secondary-fixed: '#1a1c1c'
  on-secondary-fixed-variant: '#454747'
  tertiary-fixed: '#79ff5b'
  tertiary-fixed-dim: '#2ae500'
  on-tertiary-fixed: '#022100'
  on-tertiary-fixed-variant: '#095300'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353535'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.2'
  headline-sm:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: '1.2'
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: '1'
    letterSpacing: 0.05em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '500'
    lineHeight: '1'
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: '1.2'
spacing:
  base: 4px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 32px
  safe-area: 24px
---

## Brand & Style
The design system is built on the philosophy of **Pure Functionality**. It operates as a high-performance utility, stripping away visual noise to prioritize the content stream and system efficiency. The aesthetic is a hybrid of **High-Contrast Minimalism** and **Modern Utility**, designed specifically for low-light environments and energy conservation on OLED displays.

The target audience consists of power users who value speed, precision, and an immersive viewing experience. The UI should feel like a sophisticated instrument—invisible when content is playing, and surgically precise when interaction is required. The emotional response is one of absolute focus, technical reliability, and cinematic immersion.

## Colors
The palette is strictly functional. 

- **Pitch Black (#000000):** Used for all primary backgrounds to ensure infinite contrast and maximum energy savings. 
- **Absolute White (#FFFFFF):** Reserved for high-priority typography and primary iconography to ensure immediate legibility.
- **Neon Green (#39FF14):** The "Active State" color. It is used sparingly for status indicators (Online/Live), focus rings, and playback progress.
- **Subtle Grey (#1A1A1A):** Used for secondary surfaces like drawer backgrounds and dividers where pure black would obscure structural hierarchy.

Transparency is used extensively on overlays (80-90% opacity) with a high-intensity background blur to maintain context without sacrificing readability.

## Typography
This design system utilizes **Inter** for all functional and content-based text to ensure maximum readability at varying distances. **JetBrains Mono** is introduced for labels and technical metadata (bitrate, channel numbers, timestamps) to reinforce the "utility instrument" aesthetic.

All typography should be rendered with high anti-aliasing. Labels should frequently use uppercase and increased tracking to differentiate them from body content. Headlines use tight letter spacing for a dense, modern look.

## Layout & Spacing
The layout follows a **Fluid Grid** model based on a 4px baseline shift. 

- **Full-Screen Focus:** The primary view is always the 16:9 video container or the camera viewfinder for scanning.
- **Bottom Drawer:** Channel lists and settings reside in a bottom-anchored drawer that occupies 40% to 80% of the vertical height depending on the interaction state.
- **Overlays:** Control overlays (Play/Pause/Seek) are context-sensitive and fade out after 2 seconds of inactivity.
- **Safe Zones:** Crucial UI elements (clock, status, current channel) are pinned to the corners with a 24px safe-area margin to ensure visibility on all display types.

## Elevation & Depth
In this design system, depth is communicated through **translucency and blur** rather than traditional shadows. 

1. **Base Layer:** Pure Black (#000000). 
2. **Interaction Layer:** Surfaces like the channel drawer use a semi-transparent dark grey with a `20px` background blur effect. This creates a "smoked glass" appearance that maintains the sensation of the video playing behind the UI.
3. **Focus State:** Elements do not lift; instead, they are outlined with a 1px Neon Green border or a high-contrast white fill. 
4. **Outlines:** Use 1px solid borders for structural definition between dark elements. Avoid soft shadows entirely.

## Shapes
The shape language is primarily **Sharp (0px)**. Rectilinear forms reinforce the precision-tool aesthetic.

- **Primary Elements:** All buttons, input fields, and video containers have 90-degree corners.
- **Subtle Exception:** The top edge of the bottom drawer uses a `4px` (Soft) radius to provide a tactile hint for the pull-up gesture.
- **Status Indicators:** Small status pips (e.g., "Live") remain circular to contrast against the rigid grid.

## Components
- **Buttons:** Ghost-style by default. 1px white border with white text. Active/Pressed state fills the button with Neon Green and changes text to Black.
- **Channel List:** High-density list items. Active channel is indicated by a Neon Green vertical bar on the left edge (2px wide).
- **Seek Bar:** A 2px thin white line. The progress is indicated by Neon Green. The "handle" is a simple 1px vertical tick.
- **Input Fields (M3U/URL):** Sharp-edged boxes with 1px grey borders, turning White upon focus. Monospaced font for URL entry.
- **Chips/Badges:** Minimalist boxes for "4K", "HD", or "5.1" audio. Text is JetBrains Mono, 10px, uppercase.
- **Bottom Drawer:** Draggable surface with a 32px wide horizontal "grabber" line at the top center.
- **Scanning View:** Full-screen camera with a thin Neon Green horizontal "laser" line scanning vertically to provide visual feedback during QR or stream link recognition.