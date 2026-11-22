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

## Motorcycle diagnostics investigation

See [EdiabasLib/KOMBI research](docs/OBD_RESEARCH.md) and the
[adapter-only LX probe](tools/lx_probe/README.md). The prototype queries adapter
identity and voltage only; An offline KOMBI wake-up-line decoder is also available from a published X_KOMBI
definition; live variant identification, transport setup, and hardware validation remain pending.

An experimental, bounded Windows KOMBI read test is now available. Follow
[Windows test instructions](tools/lx_probe/WINDOWS_TEST.md). It checks candidate
ECU identity before reading the wake-up line; hardware validation is pending.

## Validated LX read path

The Windows/LX two-read path passed manual on/off/on and idle/reconnect checks on
the user's bike. See [live evidence](docs/LX_LIVE_VALIDATION.md). A bounded foreground
monitor adds debounced transition inference and reconnect handling; this new layer
has local tests only. Follow [v6 instructions](tools/lx_probe/WINDOWS_TEST.md).

The v3 foreground monitor reuses its Windows Bluetooth serial handle between
samples to avoid unnecessary COM-port reopen attempts. See the live validation
record for the observed v2 error and the unverified Windows fix.

The v4 parser accepts a validated response-pending prelude before the final
single-frame response, with synthetic regression tests reproducing the observed reply pattern.

The v5 console shows readable state prompts and saves full diagnostics to a
uniquely named JSONL file automatically. Pending-then-final response handling
and persistent Windows serial connection support are included.

The v5 attended on-to-off transition passed on the bike. v6 adds a dedicated
--test off-on mode that requires an observed transition before reporting success.
