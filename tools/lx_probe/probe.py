"""Bounded adapter-only LX probe; never sends vehicle diagnostic payloads."""
import argparse
from datetime import datetime, timezone
import json
import sys
import time

COMMANDS = ("ATI", "STI", "STIX", "ATRV")
MAX_RESPONSE = 4096


def query(port, command, timeout=4.0):
    if command not in COMMANDS:
        raise ValueError("Only adapter identity and voltage queries are allowed")
    port.write((command + "\r").encode("ascii"))
    deadline = time.monotonic() + timeout
    response = bytearray()
    while time.monotonic() < deadline:
        chunk = port.read(1)
        if chunk:
            response.extend(chunk)
            if len(response) > MAX_RESPONSE:
                raise ValueError("Adapter response exceeded limit")
            if chunk == b">":
                text = response[:-1].decode("ascii", errors="replace")
                lines = [line.strip() for line in text.replace("\r", "\n").splitlines()]
                lines = [line for line in lines if line and line.upper() != command]
                return {"command": command, "lines": lines,
                        "status": "unsupported" if "?" in lines else "received"}
    raise TimeoutError("No complete adapter prompt; stopped without retry")


def collect(port, label):
    result = {"schema_version": 1, "scope": "adapter_only",
              "timestamp_utc": datetime.now(timezone.utc).isoformat(),
              "label": label, "ignition": "unknown", "kombi_wake_up_line": "unknown",
              "observations": []}
    for command in COMMANDS:
        result["observations"].append(query(port, command))
    return result


class DemoPort:
    """Synthetic responses for exercising the tool without hardware."""
    def __init__(self):
        self.pending = bytearray()

    def write(self, data):
        command = data.decode("ascii").strip()
        responses = {"ATI": "ELM327 DEMO", "STI": "STN DEMO",
                     "STIX": "?", "ATRV": "12.4V"}
        self.pending.extend((command + "\r" + responses[command] + "\r>").encode("ascii"))

    def read(self, size):
        chunk = bytes(self.pending[:size])
        del self.pending[:size]
        return chunk


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--demo", action="store_true")
    mode.add_argument("--port", help="Already paired Bluetooth serial port, e.g. COM7")
    parser.add_argument("--baud", type=int, default=115200)
    parser.add_argument("--label", default="unlabeled", help="Operator note, not a measured state")
    args = parser.parse_args()
    try:
        if args.demo:
            result = collect(DemoPort(), args.label)
            result["synthetic"] = True
        else:
            import serial
            with serial.Serial(args.port, args.baud, timeout=0.1, write_timeout=2) as port:
                result = collect(port, args.label)
            result["synthetic"] = False
        print(json.dumps(result, indent=2))
        return 0
    except (ImportError, OSError, ValueError, TimeoutError) as exc:
        print("Probe stopped: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
