"""Experimental two-read KOMBI probe; fixed candidate BMW CAN profile only."""
import argparse
from datetime import datetime, timezone
import json
import re
import time

from kombi_wake import decode

# 11-bit / 500 kbit, tester F1, candidate ECU 60. Manual extended-address SFs.
SETUP = ("ATE0", "ATL0", "ATS1", "ATH1", "ATD0", "ATCAF0", "ATCFC0",
         "ATCEA", "ATTP6", "ATCSM0", "ATAT0", "ATST64", "ATSH6F1", "ATCRA660")
IDENT_FRAME = "600322F150000000"
WAKE_FRAME = "600322E11C000000"
ALLOWED = frozenset(SETUP + ("STI", "ATPC", IDENT_FRAME, WAKE_FRAME))
EXPECTED_ID = bytes.fromhex("62 F1 50 0F 88 00")


class AdapterError(ValueError):
    def __init__(self, message):
        super().__init__(message)
        self.code = message.lower().replace(" ", "_").replace("?", "unsupported_command")


ADAPTER_ERRORS = frozenset(("CAN ERROR", "BUS ERROR", "BUS BUSY", "BUFFER FULL",
                            "DATA ERROR", "STOPPED", "UNABLE TO CONNECT", "?"))


def exchange(port, command, transcript, timeout=4):
    if command not in ALLOWED:
        raise ValueError("Command outside fixed probe allowlist")
    entry = {"command": command, "raw": "", "complete": False}
    transcript.append(entry)
    if port.write((command + "\r").encode("ascii")) != len(command) + 1:
        raise OSError("Incomplete serial write; stopping")
    deadline = time.monotonic() + timeout
    data = bytearray()
    while time.monotonic() < deadline:
        chunk = port.read(1)
        if chunk:
            data.extend(chunk)
            entry["raw"] = data.decode("ascii", errors="replace")
            if len(data) > 4096:
                raise ValueError("Oversized adapter response")
            if chunk == b">":
                entry["complete"] = True
                lines = [v.strip() for v in entry["raw"][:-1].replace("\r", "\n").splitlines()]
                return [v for v in lines if v and v.upper() != command]
    raise TimeoutError("Adapter prompt missing; no retries")


def parse_single_frame(lines):
    # Never search for payload bytes inside arbitrary text or accept another sender.
    for line in lines:
        if line.upper() in ADAPTER_ERRORS:
            raise AdapterError(line.upper())
    if lines == ["NO DATA"]:
        return b""
    if not 1 <= len(lines) <= 9:
        raise ValueError("Expected at most eight pending frames and one final frame")
    payloads = [_parse_frame(line) for line in lines]
    pending = bytes.fromhex("7F 22 78")
    if any(payload != pending for payload in payloads[:-1]):
        raise ValueError("Conflicting responses: only response-pending may precede a final reply")
    return payloads[-1]


def _parse_frame(line):
    text = "".join(line.split()).upper()
    if not re.fullmatch(r"660(?:[0-9A-F]{2}){3,8}", text):
        raise ValueError("Expected CAN source 660 with DLC display off")
    data = bytes.fromhex(text[3:])
    if data[0] != 0xF1 or not 1 <= data[1] <= 6:
        raise ValueError("Wrong recipient or unsupported ISO-TP frame")
    length = data[1]
    if len(data) < 2 + length:
        raise ValueError("Truncated CAN payload")
    return data[2:2 + length]


def run(port, label):
    result = {"schema_version": 2, "scope": "kombi_two_reads",
              "timestamp_utc": datetime.now(timezone.utc).isoformat(), "label": label,
              "profile": "candidate_11bit_500k_tester_F1_ecu_60",
              "profile_validation": "user_tested_single_bike",
              "validation_reference": "docs/LX_LIVE_VALIDATION.md", "ignition": "unknown",
              "wake_up_line": "unknown", "status": "incomplete", "transcript": []}
    log = result["transcript"]
    configured = False
    try:
        identity = exchange(port, "STI", log)
        if len(identity) != 1 or not re.fullmatch(r"STN1155 v[0-9.]+", identity[0]):
            raise ValueError("Expected LX STN1155 firmware ID; no vehicle requests sent")
        for command in SETUP:
            configured = True
            if exchange(port, command, log) != ["OK"]:
                raise ValueError("Adapter setup failed at " + command)
        ident = parse_single_frame(exchange(port, IDENT_FRAME, log))
        result["ident_response_uds"] = ident.hex(" ").upper()
        if ident != EXPECTED_ID:
            result["status"] = "identity_unconfirmed"
            return result
        result["identity_matches_candidate"] = True
        payload = parse_single_frame(exchange(port, WAKE_FRAME, log))
        result.update(decode(payload))
        return result
    except AdapterError as exc:
        result.update(status="adapter_error", adapter_error=exc.code, error=str(exc))
        return result
    except TimeoutError as exc:
        result.update(status="stopped", error=str(exc))
        return result
    except OSError as exc:
        result.update(status="transport_error", error=str(exc))
        return result
    except ValueError as exc:
        result.update(status="stopped", error=str(exc))
        return result
    finally:
        if configured and log[-1]["complete"]:
            try:
                result["protocol_closed"] = exchange(port, "ATPC", log) == ["OK"]
            except (OSError, ValueError, TimeoutError) as exc:
                result["protocol_closed"] = False
                result["cleanup_error"] = str(exc)
        elif configured:
            result["protocol_closed"] = False
            result["cleanup_error"] = "Prompt missing; skipped cleanup command on unsynchronized stream"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", required=True)
    parser.add_argument("--baud", type=int, default=115200)
    parser.add_argument("--label", required=True, help="Operator note, not measured ignition")
    args = parser.parse_args()
    try:
        import serial
        with serial.Serial(args.port, args.baud, timeout=0.1, write_timeout=2) as port:
            port.reset_input_buffer()
            result = run(port, args.label)
        result["synthetic"] = False
    except (ImportError, OSError, ValueError) as exc:
        result = {"status": "host_error", "error": str(exc), "ignition": "unknown",
                  "wake_up_line": "unknown", "synthetic": False}
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "decoded" and result.get("protocol_closed") else 1


if __name__ == "__main__":
    raise SystemExit(main())
