# Windows LX test update (v7)

The basic read path has now passed the manual on/off/on and idle/reconnect tests
on your bike. See [the live validation record](../../docs/LX_LIVE_VALIDATION.md).
The new foreground monitor and transition inference have **local tests only**.
The older first-test instructions below remain available for new hosts.

## v7 delayed-response handling

Both attended transition directions have now passed on the tested bike.
No new bike test is requested for this update. Pending-only identity replies
are shown as pending and end with response_pending_unresolved, not an identity
mismatch. Duplicate final replies are ambiguous_response and require review.
The monitor sends no subsequent diagnostic read in either case. Keep the saved
log if either status occurs; do not repeatedly rerun to force a confirmation.
Pending-then-final replies within one complete adapter exchange remain accepted.

## Off-to-on test reference

1. Extract v6 into a fresh folder and open PowerShell in its Rider folder.
2. Close MotoScan. Keep the bike parked and the engine off.
3. To make the off baseline readable before the network goes idle, briefly turn
   the ignition on, then off. Run immediately afterward:

```powershell
py tools/lx_probe/monitor.py --port COM10 --test off-on --samples 12 --interval 5
```

4. Wait for **IGNITION OFF CONFIRMED — TURN THE KEY ON NOW.** Turn the key on
   and leave the engine off.
5. The expected result is **IGNITION ON TRANSITION CONFIRMED**, then
   stopped=confirmed_on_transition. Upload the new lx-monitor-*.jsonl file.

Do not leave the key off for five minutes before starting: the earlier test
showed the diagnostic read may be unavailable then. An unavailable baseline
remains unknown and cannot establish an off-to-on edge. Starting already on
also cannot pass this test. No garage commands or speech actions are attached.
The same attempt limits, connection reuse, raw logging, and stop-on-malformed
response rules apply. The former --continue-after-off option remains available
for general manual testing; it is not needed with --test off-on.

## v5: readable live status and automatic logging

The live command now prints one short status per sample. When the initial on
state is confirmed, it prints a separate, prominent prompt:

**IGNITION ON CONFIRMED — TURN THE KEY OFF NOW.**

A best-effort Windows notification sound accompanies that prompt. Subsequent
on samples say "ON confirmed. Waiting for key OFF." After you switch off,
it shows "OFF detected (1/2). Keep the key OFF.", then confirms off and stops.
The full raw JSON transcript is saved automatically to a uniquely named
lx-monitor-*.jsonl file in the working directory; its name is printed at startup.
Send that file after the run. No copying of scrolling console JSON is necessary.
--json restores raw JSON console output; --log selects a new filename. Existing
files are never overwritten. If a log file cannot be opened, no live probe starts.
Replay mode does not create a log file. The v4 pending-reply fix is included.

Use the same command:

```powershell
py tools/lx_probe/monitor.py --port COM10 --samples 12 --interval 5
```

## v4: pending-then-final reply support

Your v3 log established ignition on and then stopped because the cluster sent
response-pending followed by its final on-value in one adapter reply. v4 accepts
that valid sequence. It does not ignore conflicting final replies or pending
messages for another service. Pending alone remains unknown. Windows connection
reuse from v3 is retained. This fix has local regression tests using synthetic pending/final
replies; the next live run still needs validation.

For the next attended run, wait for inferred state_established/on, then switch
the key off while the monitor is still running. The earlier v3 capture contains
no off-value, so the live transition remains to be demonstrated.

## v3: Windows connection lifecycle fix

The first v2 monitor run read the bike successfully at samples 1 and 3, but
sample 2 failed reopening COM10 with Windows error 121 (semaphore timeout).
The interruption correctly prevented confirmation. Repeated host port opening
is a plausible contributor, not a proven Windows root cause. v3 reuses the
serial connection instead of closing/reopening it between successful samples.
The serial timeout fix is locally tested; a new live run is still required.

Extract the latest package to a fresh folder, stop any previous monitor with Ctrl+C, and keep
MotoScan closed. Use the same live command below; once state_established/on
appears, turn ignition off. Look for ignition_off then confirmed_off.

## Updated one-shot probe

From the extracted Rider directory:

```powershell
py tools/lx_probe/live_kombi.py --port COM10 --label manual-check
```

CAN ERROR is now returned as status=adapter_error, adapter_error=can_error,
with unknown ignition and wake-up line, rather than a misleading frame error.
The two-request allowlist and identity gate are unchanged.

## Offline transition demonstration (no bike connection)

```powershell
py tools/lx_probe/monitor.py --replay tools/lx_probe/fixtures/replay_demo.json
```

This explicitly synthetic sequence demonstrates initial off establishment,
on-transition, lost communication, and recovery without a duplicate on-event.

## Optional next live session

Close MotoScan and use the parked bike. Start with ignition on, engine off:

```powershell
py tools/lx_probe/monitor.py --port COM10 --samples 12 --interval 5 > lx-monitor.jsonl
```

Each saved JSONL line contains a raw observation and a separate inferred state/event.
The console shows short status messages by default.
This is a foreground diagnostic session, not a background service. It keeps one
Windows Bluetooth serial connection open between samples. Each sample still
identifies the cluster, reads the line, and closes the vehicle protocol with ATPC.
The host connection is released on exit or actual transport failure; bounded
reconnect attempts follow transport failure. A CAN ERROR alone does not reopen
the Windows port. Default limit is 12 attempts; command-line
limit is 60. Successful reads have a five-second pause; failed access backs off
up to 30 seconds. Ctrl+C stops the session. No continuous or indefinite mode exists.
Serial I/O may itself take additional time beyond the pauses.

It requires two matching reads to establish state. Switching off after the
initial on state lets it observe ignition_off and stop. It also stops immediately
for identity mismatch, malformed data, unsynchronized transport, or failed
protocol closure. CAN errors and host-open failures can retry only within the
explicit attempt limit. Do not infer sleep or battery behavior from this loop;
repeated reads may affect the network's sleep state.

To test both edges within one bounded session, add --continue-after-off. That
explicit option continues querying after confirmed off until the attempt limit,
so use it only during the attended test. There are no garage or voice actions.
A reconnected on state is labeled recovery, not automatically a new ignition event.

---

## Original first Windows LX test

This is an experimental read-only diagnostic probe for your Preh KOMBI candidate.
It is ready for a first hardware test, not hardware-validated. Live validation is summarized in docs/LX_LIVE_VALIDATION.md.

## 1. Prepare

Keep the motorcycle parked, ignition on, engine off. Close MotoScan and other
adapter clients. Pair the LX with Windows, then locate its **outgoing Bluetooth
serial COM port** in Bluetooth settings / Device Manager. A Bluetooth pairing
alone does not guarantee a working COM port. If there is no outgoing port, stop
and report that; do not choose an unrelated serial device.

Install Python 3 if needed. Extract the supplied ZIP, open PowerShell in its
`Rider` directory (containing README.md), and run:

```powershell
py -m pip install -r tools/lx_probe/requirements.txt
py -m serial.tools.list_ports
```

Use the COM number Windows assigned to the LX in the commands below; COM7 is an
example. The baud setting defaults to 115200 and has not been validated on your
host. `--baud` can match the serial configuration if necessary.

## 2. Confirm adapter access

```powershell
py tools/lx_probe/probe.py --port COM7 --label key-on-engine-off > lx-adapter.json
$LASTEXITCODE
Get-Content lx-adapter.json
```

Exit code must be 0 and the output must contain actual, non-synthetic adapter
replies. If it fails, stop here and share the error; this step sends no ECU
requests. The identity check in the next step expects STN1155, the LX identifier
listed by OBD Solutions. Unexpected firmware identity is a stop for review,
not evidence that your adapter is defective.

## 3. Perform one bounded KOMBI test

```powershell
py tools/lx_probe/live_kombi.py --port COM7 --label key-on-engine-off > lx-kombi-key-on.json
$LASTEXITCODE
Get-Content lx-kombi-key-on.json
```

The script first checks the adapter firmware, configures a fixed candidate CAN
profile, then performs at most these two diagnostic requests:

| Read | UDS request | Required response |
|---|---|---|
| Cluster definition identity | 22 F1 50 | 62 F1 50 0F 88 00 |
| Wake-up line, only after matching identity | 22 E1 1C | 62 E1 1C 00 or 62 E1 1C 01 |

Only service 0x22 reads are exposed. No arbitrary requests, fault clearing,
actuators, coding, security access, session switching, service reset, or repeated
polling are implemented. Adapter configuration changes runtime communication
settings; the script attempts ATPC to close the protocol afterward. It does
not restore every previous adapter setting. Reconnect through MotoScan to let
that app configure its own connection, then verify MotoScan still works.
If a prompt is missing, cleanup is skipped to avoid sending more bytes on an
unsynchronized stream; disconnect and reconnect before another attempt.

## 4. Share the result before further tests

Send `lx-adapter.json` and `lx-kombi-key-on.json`, or paste their contents.
The probe does not request VIN or ECU serial number. Do not repeat indefinitely
on failure or change addresses/identifiers by trial and error.

A `decoded` status and `wake_up_line: on` would be the expected key-on result,
but this is the hypothesis being tested. `ignition` deliberately stays `unknown`.
`identity_unconfirmed`, `negative_response`, `pending`, `unavailable`, or `stopped`
are useful evidence and do not mean ignition off. Check both status and
`protocol_closed`; exit code 0 requires decoded data and successful closure.

After reviewing the first key-on result, we can compare key-off and delayed
sleep behavior. A successful read alone does not establish ignition detection.

## Transport assumptions and limits

- Candidate: 11-bit CAN, 500 kbit/s, tester address F1, ECU address 60.
- TX arbitration ID 6F1, RX 660, extended addressing carried explicitly in
  manually constructed ISO-TP single frames. Complete read frames:
  `60 03 22 F1 50 00 00 00` and `60 03 22 E1 1C 00 00 00`.
- Uses temporary protocol selection ATTP6 and disables automatic formatting,
  extended-address insertion, automatic flow control, and DLC printing; frames
  are constructed by this script. No auto protocol search is requested.
- EdiabasLib's source supports this framing scheme; the exact bike profile is
  still a candidate. We have not obtained or executed X_KOMBI.PRG. The strict
  identity reply is a candidate format that may fail and require adjustment
  based on captured evidence. A matching ID is necessary, not sufficient proof
  of software compatibility.
- Only single-frame responses are accepted. Header mismatch, extra replies,
  multi-frame replies, truncated data, and unsupported values stop decoding.
- The raw adapter transcript is retained in JSON, including setup failures.
- The program is independent of EdiabasLib and uses the published read definition
  plus a small source-derived transport; it is not an EdiabasLib integration.

Local verification:

```powershell
py -m unittest discover -s tests -v
```

See [research and sources](../../docs/OBD_RESEARCH.md).
