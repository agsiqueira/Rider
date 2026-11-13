# RIDER

**Road Intelligence, Diagnostics, Experience & Recollection**

RIDER is a motorcycle-centered experiential intelligence companion for a 2014 BMW R1200RT. RIDER speaks as the motorcycle in the first person ("I", "my"), with grounded, longitudinal memory and contextual awareness.

## Scope

- Proactive contextual intelligence and meaningful reminders
- Longitudinal motorcycle memory and maintenance/diagnostic reasoning
- Voice interaction and deliberate controls, including Garage
- Distinguish observed facts, hypotheses, and unknowns
- No navigation cues or continuous instrument-cluster telemetry

## Architecture

- **XTEINK X4 Pro + SolarOS**: detachable 800×480 monochrome e-paper interface; MicroPython app
- **Samsung S24 Ultra**: planned native Android companion for intelligence, local state, voice, and BLE
- **EPMR**: related but separate application; share infrastructure only when useful

## Verified device baseline

SolarOS **4.17.10 Full** on X4 Pro ESP32-S3 (8 MB PSRAM, 16 MB flash). The onboard display is `display0` (800×480). Remote Telnet, MicroPython REPL, direct graphics, and native display app launching are working. The startup script now applies `setterm brightness 30` and `display mode display0 refresh=partial`, verified after reboot. The `shutdown` command works.

See [docs/HARDWARE.md](docs/HARDWARE.md) for details and [AGENTS.md](AGENTS.md) for Codex guidance.

## First milestone

Implement a minimal standalone SolarOS MicroPython RIDER home screen and a safe, repeatable Windows edit/deploy/test workflow. Confirm undocumented API signatures against the on-device manual before using them. No firmware flashing required.
