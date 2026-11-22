# LX diagnostic prototypes

For the first live KOMBI test, follow [WINDOWS_TEST.md](WINDOWS_TEST.md).
The experimental live tool is separate from the adapter-only probe below.

This CPython tool queries adapter identity and supply voltage. It does **not**
read KOMBI, detect ignition, or use EdiabasLib yet. See
[research and remaining definition work](../../docs/OBD_RESEARCH.md).

## Offline check

From the repository root:

```powershell
python tools/lx_probe/probe.py --demo
python -m unittest discover -s tests -v
```

## Live check on a Windows computer paired to the LX

Close MotoScan and any other adapter clients. Pair the LX through Windows
Bluetooth settings and find its outgoing serial COM port. This script does not
perform pairing or choose a port automatically. If Windows does not expose a
working Bluetooth serial port, stop here; an Android RFCOMM transport will be
needed instead. The LX connection is Bluetooth Classic SPP, not BLE GATT.

```powershell
py -m pip install -r tools/lx_probe/requirements.txt
py tools/lx_probe/probe.py --port COM7 --label key-on-engine-off > lx-key-on.json
```

Replace COM7 with the actual port. The baud default is 115200; `--baud` can match
the host serial configuration. This default has not been validated on your LX.
Repeat with `--label key-off` if the adapter remains accessible. Run each probe
manually while parked; this tool has no continuous polling. Keep the JSON as
observations, not proof of ignition detection. A successful run means four
prompt-terminated replies arrived, not that every command is supported or that
the adapter has been authenticated as an LX. `?` is recorded as unsupported.

A missing prompt or oversized reply stops the run without retries. Errors go
to stderr and return exit code 1; only completed probes produce JSON. Check
`$LASTEXITCODE` before using a redirected file. Do not infer ignition from
adapter voltage, Bluetooth connection state, or a timeout.

Hardware checklist:

- [ ] Confirm actual host port and connection to the LX.
- [ ] Capture non-synthetic identity/voltage replies.
- [ ] Verify MotoScan can reconnect after the probe exits.
- [ ] Resolve KOMBI variant and read-only definition before adding ECU traffic.

## Offline KOMBI decoder

A published X_KOMBI wake-up-line definition is now documented in the research.
The separate decoder accepts reassembled UDS payloads, not raw ELM output:

```powershell
python tools/lx_probe/kombi_wake.py --uds "62 E1 1C 01"
```

The example is synthetic. This does not send a request or validate the definition
against your bike. The decoder always keeps ignition unknown until the physical
relationship has been validated. Exit status is 0 only for a decoded line value.
