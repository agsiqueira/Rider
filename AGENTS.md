# Codex instructions for RIDER

## Product

RIDER **is** the motorcycle, not a generic assistant. Use a calm first-person machine voice, and clearly distinguish observation from inference. Focus on proactive contextual intelligence, longitudinal memory, maintenance diagnostics, meaningful reminders, voice, and deliberate controls. **Never add navigation cues** or design RIDER as a live instrument cluster.

The detachable XTEINK X4 Pro UI is monochrome, 800×480 landscape, and must favor large glove-friendly controls. Garage, Voice, Bike Status, and Memories may be initial placeholders; never imply a placeholder works. The Samsung S24 Ultra will eventually handle richer intelligence and BLE bridging. EPMR remains a separate app.

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

- `session create files display0` successfully launched Files on the physical display while Telnet stayed connected. `session create python display0 /apps/rider.py` is **not yet tested**; check supported launch semantics.
- `setterm brightness 30` and `display mode display0 refresh=partial` were persisted in `/.shell/startup` and verified after reboot. In auto mode the screen flashed roughly every 30 seconds; partial mode stopped it.
- `shutdown` cleanly powers off.
- MicroPython REPL exits with Ctrl+]. Do not assume standard CPython libraries, `input()`, or `execfile()`.

## Reference topics on the device

`man python`, `man python.gfx`, `man python.input`, `man python.system`, `man sessions.apps`, `man command.display`.

Verify text drawing, shape APIs, and input event signatures before implementing. Use `gfx.end()` in a `finally` block. Prefer event-driven redraws, not busy repaint loops.

## Engineering

1. Build a minimal screen with a mockable graphics interface and local tests.
2. Establish a Windows-friendly deployment workflow. Verify file-transfer capabilities before automating them.
3. Test direct Python script launch from storage, then a dedicated foreground display session.
4. Document each tested command and limitation.
5. Use incremental pull requests and hardware-test checklists.

## Security and safety

- No firmware flashing, formatting, or destructive storage operations for normal iteration.
- Never read, print, commit, or upload `/.shell/startup` credentials, private Wi-Fi/Telnet passwords, device backups, or access tokens.
- Telnet is temporary and plaintext; seek an authenticated encrypted alternative.
- Do not hardcode device IPs or credentials in scripts.
