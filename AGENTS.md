# Codex instructions for RIDER

## Product

RIDER **is** the motorcycle, not a generic assistant. Use a calm first-person machine voice, and clearly distinguish observation from inference. Focus on proactive contextual intelligence, longitudinal memory, maintenance diagnostics, meaningful reminders, voice, and deliberate controls. **Never add navigation cues** or design RIDER as a live instrument cluster.

The detachable XTEINK X4 Pro UI is monochrome, with 800×480 landscape and a verified 480×800 portrait prototype, and must favor large glove-friendly controls. Use Garage and Memories for Parked Home. Use the physical Home button to initiate voice and listen automatically after a RIDER question. Label simulated and placeholder behavior accurately. The Samsung S24 Ultra will eventually handle richer intelligence and BLE bridging. EPMR remains a separate app.

## Confirmed SolarOS environment

- X4 Pro, ESP32-S3, SolarOS **4.17.10 Full**, 8 MB PSRAM, 16 MB flash.
- Display target `display0`, driver `x4pro`, 800×480.
- `python` starts a working MicroPython REPL; `python /apps/demo.py arg1` executes a stored script.
- `import solaros`, `solaros.version()`, and `from solaros import gfx` verified.
- Port-shell graphics require an explicit ready display target. Verified sequence:

```python
from solaros import gfx
gfx.begin("display0")
try:
    w, h = gfx.width(), gfx.height()
    gfx.clear(gfx.WHITE)
    gfx.color(gfx.BLACK)
    gfx.fill_circle(w // 2, h // 2, 90)
    gfx.present()
finally:
    gfx.end()
```

- `session create files display0` successfully launched Files on the physical display while Telnet stayed connected. `session create python display0 /rider/rider_ble_two_way.py` was verified from PuTTY over Telnet. The portrait artwork rendered and Go delivered `CONFIRM` to nRF Connect. A direct Python launch in the port shell drew the artwork but did not record the Go tap. Use the dedicated display session for touch testing.
- `setterm brightness 30` and `display mode display0 refresh=partial` were persisted in `/.shell/startup` and verified after reboot. In auto mode the screen flashed roughly every 30 seconds; partial mode stopped it.
- `shutdown` cleanly powers off.
- MicroPython REPL exits with Ctrl+]. Do not assume standard CPython libraries, `input()`, or `execfile()`.

## Reference topics on the device

`man python`, `man python.gfx`, `man python.input`, `man python.system`, `man session`, `man command.display`.

Verify text drawing, shape APIs, and input event signatures before implementing. Use `gfx.end()` in a `finally` block. Prefer event-driven redraws, not busy repaint loops.

## Codex’s role

- Use the [development roadmap](docs/development-roadmap.md) to select the current milestone and its exit criteria. Preserve the verified BLE baseline and use the Figma screens as the interface reference.
- Implement in small, reviewable changes. Codex owns code, local checks, simulations, and deployment instructions. Alex operates the bike, X4 Pro, phone, headset, and garage and reports physical results.
- Develop against simulated key-on and a simulated opener until the corresponding hardware checks are ready. Keep Phase 1 diagnostics read-only. Confirm uncertain SolarOS APIs using the on-device manuals.
- Report what passed locally, what used simulated inputs, and what Alex verified on hardware. Include repeatable hardware steps and expected results in each relevant pull request.
- Use PuTTY for remote SolarOS commands and WinSCP for file transfer. Ask Alex for the specific command output or device observation when needed.

## Security and safety

- No firmware flashing, formatting, or destructive storage operations for normal iteration.
- Never read, print, commit, or upload `/.shell/startup` credentials, private Wi-Fi/Telnet passwords, device backups, or access tokens.
- Telnet is temporary and plaintext; seek an authenticated encrypted alternative.
- Do not hardcode device IPs or credentials in scripts.
