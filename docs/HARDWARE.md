# Verified X4 Pro hardware and SolarOS setup

Observed 2026-11-13.

| Property | Value |
|---|---|
| Device | XTEINK X4 Pro |
| MCU | ESP32-S3 |
| PSRAM / flash | 8 MB / 16 MB |
| Firmware | SolarOS 4.17.10 Full |
| Display | `display0`, driver `x4pro`, 800×480 |
| MicroPython REPL | Working, 512 KiB heap |

## Verified commands

```text
display list
display mode display0
display mode display0 refresh=partial
setterm brightness
setterm brightness 30
session list
session create files display0
python
shutdown
```

## Verified graphics REPL

```python
from solaros import gfx
gfx.begin("display0")
try:
    gfx.clear(gfx.WHITE)
    gfx.color(gfx.BLACK)
    gfx.fill_circle(gfx.width() // 2, gfx.height() // 2, 90)
    gfx.present()
finally:
    gfx.end()
```

A centered black circle appeared on the physical e-paper screen.

## Sessions and refresh

`session list` showed a local shell on `display0` and a remote shell on `telnet0`. A native Files app launched on `display0` through `session create files display0`. Both graphics and Files showed full-screen flashing roughly every 30 seconds under `refresh=auto`; `refresh=partial` stopped it.

## Persistent settings

The existing microSD `/.shell/startup` was edited to apply:

```text
setterm brightness 30
display mode display0 refresh=partial
```

Both values were confirmed after reboot. The startup file also holds private network and Telnet settings; never commit it.

## Safe shutdown

`shutdown` displayed SolarOS “Powered off” and disconnected the remote shell. E-paper retains its image without power.

## Documentation

The external SolarOS manual was updated to match firmware 4.17.10. Relevant topics: `man python`, `man python.gfx`, `man python.input`, `man sessions.apps`, `man command.display`, `man command.setterm`, `man shutdown`.

Do not assume Python application session launching or remote file transfer has been validated yet.
