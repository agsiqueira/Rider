# RIDER X4 Pro E-Paper Visual Standard (approved November 2026)

Source of truth: RIDER Figma file https://www.figma.com/design/tKn2vEcNpEaffoDJ2rlzoR

## Approved appearance
- Display native portrait resolution: **480 x 800 px**. Export Figma frames as PNG at 1x, full bleed, no decorative device border or white margins.
- Use the user-approved **Presence B / Smooth Grayscale** style as the reference: Figma frame **51:49** (reinforced text, photographic contrast 0.32, highlights +0.03, shadows -0.04, gentle layer blur radius 0.65px).
- **Pure black #000000** for interface text, MIXED dot logo, header, status icons, dividers, and icon strokes. Full opacity.
- **Pure white #FFFFFF** for the pale/white UI background and organic droplet shapes; do not use #FAFAFA-to-#F2F2F2 gradients there.
- Reinforce thin regular text with a **0.5 px centered black stroke** when necessary. Keep original font families, sizes, positions and composition; do not make text illegible or override contrast for inverse buttons.
- Preserve continuous-tone grayscale photographs, using gentle contrast and smoothing rather than visible grayscale banding/posterization. Do **not** quantize photograph to 4/8 levels or impose a global binary threshold.
- The curved boundary between white shape and photograph should be as crisp as possible; preserve vector geometry. Do not blur the white vector shapes. At export, raster anti-aliasing may remain; evaluate on hardware.
- Retain native design's organic shapes, mountain-road imagery, MIXED identity. Never replace with a generic dashboard.
- The X4 Pro/SolarOS can render pure black and white well, but grayscale is visibly dithered. The user evaluated on hardware: pure black icons and logo looked excellent; reinforced lettering improved; image B preferred over A; binary text pixel-processing experiment showed no meaningful improvement over Figma.
- Preserve original Figma frames; create editable production variants instead.

## Current Figma production variants
- Presence B approved visual reference: 51:49
- Communication: 56:2
- Memory: 56:55
- Attention: 56:111
- Parked Home: 56:163
- Parked Action: 56:226

These five are **style-matched studies**, not yet hardware-validated individually. Preserve dark button treatments and ensure button-label contrast when adapting.

## Device deployment
- Copy 480x800 PNGs to /rider/ on SolarOS via WinSCP FTP (local trusted network).
- Start foreground Python graphics session with `session create python display0 /rider/rider_presence_test.py` for Presence test, with script's expected filename `/rider/presence.png`.
- SolarOS Python graphics: `from solaros import gfx,image`; `gfx.begin('display0')`, `image.open(path)`, `image.draw(handle,0,0)`, `gfx.present()`.
- Keep reader and existing startup intact. No live instrument-cluster design and no navigation cues; focus on proactive context, memory, diagnostics, reminders and deliberate controls.

## Future image workflow
Figma editable master -> duplicate full-bleed 480x800 production frame -> apply solid black UI / solid white regions / reinforced thin text -> preserve continuous grayscale scenery with moderate contrast and light blur -> PNG 1x -> WinSCP -> test on X4 Pro -> iterate based on hardware, not desktop alone.
