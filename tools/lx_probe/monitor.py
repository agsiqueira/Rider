"""Bounded foreground monitoring or offline replay; never controls the garage."""
import argparse
from datetime import datetime
import json
import time
import uuid

from ignition import IgnitionTracker
from live_kombi import run


class SerialSampler:
    """Own one host connection across reads; release it on exit or transport loss."""
    def __init__(self, open_port, probe=run):
        self.open_port = open_port
        self.probe = probe
        self.port = None

    def close(self):
        if self.port is not None:
            port, self.port = self.port, None
            port.close()

    def __call__(self):
        try:
            if self.port is None:
                self.port = self.open_port()
                self.port.reset_input_buffer()
            result = self.probe(self.port, "foreground-monitor")
            result["synthetic"] = False
            if result.get("status") in ("transport_error", "stopped") or result.get("protocol_closed") is False:
                self.close()
            return result
        except (OSError, ValueError) as exc:
            # Clear the handle even when port setup or closing fails.
            try:
                self.close()
            except OSError:
                pass
            return {"status": "host_error", "error": str(exc),
                    "ignition": "unknown", "wake_up_line": "unknown", "synthetic": False}

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        self.close()


def monitor(sample, emit, samples=12, interval=5, clock=time.monotonic, sleep=time.sleep, stop_on_off=True, test_mode="on-off"):
    tracker = IgnitionTracker()
    failures = 0
    for index in range(samples):
        result = sample()
        state = tracker.update(result, clock())
        emit({"sample": index + 1, "test_mode": test_mode, "observation": result, "inference": state})
        if test_mode == "off-on" and state["event"] == "ignition_on":
            return "confirmed_on_transition"
        if test_mode != "off-on" and stop_on_off and state["ignition"] == "off":
            return "confirmed_off"
        # No automatic writes after an incomplete prompt or failed protocol closure.
        if (result.get("status") == "stopped" or (result.get("protocol_closed") is False and result.get("status") != "transport_error")
                or result.get("status") in ("identity_unconfirmed", "invalid_response", "invalid_value")):
            return "review_required"
        if result.get("status") == "decoded":
            failures = 0
        else:
            failures += 1
        if index + 1 < samples:
            sleep(min(30, interval * 2 ** min(failures, 3)))
    return "sample_limit"


def format_status(item):
    if "monitor_stopped" in item:
        return "Stopped: " + item["monitor_stopped"]
    state = item.get("inference", {})
    prefix = "Sample " + str(item["sample"]) + ": " if "sample" in item else "Demo: "
    value, event = state.get("ignition", "unknown"), state.get("event")
    if item.get("test_mode") == "off-on":
        if event == "ignition_on":
            return "\nIGNITION ON TRANSITION CONFIRMED. Test complete.\n"
        if value == "off":
            return "\nIGNITION OFF CONFIRMED — TURN THE KEY ON NOW. Keep the engine OFF.\n"
        if value == "on":
            return prefix + "ON confirmed; no new on-transition observed. Turn key OFF to establish the baseline."
        if item.get("observation", {}).get("wake_up_line") == "off":
            return prefix + "OFF detected (1/2). Keep the key OFF until confirmed."
        if item.get("observation", {}).get("wake_up_line") == "on":
            return prefix + "ON detected (1/2). Keep the key ON, engine OFF."
    if event == "state_established" and value == "on":
        return "\nIGNITION ON CONFIRMED — TURN THE KEY OFF NOW.\n"
    if event == "ignition_off":
        return "\nIGNITION OFF CONFIRMED.\n"
    if value == "on":
        return prefix + "ON confirmed. Waiting for key OFF."
    if value == "off":
        return prefix + "OFF confirmed."
    observation = item.get("observation", {})
    line = observation.get("wake_up_line", "unknown")
    if observation.get("status") == "decoded" and line in ("on", "off"):
        return prefix + line.upper() + " detected (1/2). Keep the key " + line.upper() + "."
    error = observation.get("adapter_error") or observation.get("error") or observation.get("status")
    if error:
        return prefix + "State unknown. " + str(error)
    return prefix + "Waiting for confirmation."


def emit_status(item, log, json_output=False):
    if log is not None:
        log.write(json.dumps(item) + "\n")
        log.flush()
    print(json.dumps(item) if json_output else format_status(item), flush=True)
    if not json_output and "TURN THE KEY" in format_status(item) and item.get("inference", {}).get("event") is not None:
        # Best-effort native Windows notification; the visible prompt is authoritative.
        try:
            import winsound
            winsound.MessageBeep()
        except (ImportError, RuntimeError, OSError):
            pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--port")
    mode.add_argument("--replay", help="JSON array of observations with elapsed_seconds; no hardware access")
    parser.add_argument("--test", choices=("on-off", "off-on"), default="on-off", help="Direction of the attended transition test")
    parser.add_argument("--baud", type=int, default=115200)
    parser.add_argument("--samples", type=int, default=12)
    parser.add_argument("--interval", type=float, default=5)
    parser.add_argument("--continue-after-off", action="store_true", help="Keep the bounded session running after confirmed off")
    parser.add_argument("--json", action="store_true", help="Print raw JSON instead of readable status")
    parser.add_argument("--log", help="New JSONL output file; default is an automatically named file for live runs")
    args = parser.parse_args()
    if not 2 <= args.samples <= 60 or not 5 <= args.interval <= 30:
        parser.error("Use 2–60 samples and a 5–30 second interval")
    emit = lambda item: emit_status(item, None, args.json)
    if args.replay:
        with open(args.replay, encoding="utf-8-sig") as source:
            items = json.load(source)
        tracker = IgnitionTracker()
        for item in items:
            emit({"synthetic_replay": True, "inference": tracker.update(item, item["elapsed_seconds"])})
        return 0
    log = None
    try:
        import serial
        path = args.log or ("lx-monitor-" + datetime.now().strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:6] + ".jsonl")
        log = open(path, "x", encoding="utf-8")
        if not args.json:
            print("Full diagnostic log: " + path, flush=True)
            instruction = ("Start with ignition OFF. Wait for the TURN THE KEY ON prompt; keep engine OFF."
                           if args.test == "off-on" else "Keep ignition ON, engine OFF. Wait for the TURN THE KEY OFF prompt.")
            print(instruction, flush=True)
        emit = lambda item: emit_status(item, log, args.json)
        with SerialSampler(lambda: serial.Serial(args.port, args.baud, timeout=0.1, write_timeout=2)) as sample:
            reason = monitor(sample, emit, args.samples, args.interval, stop_on_off=not args.continue_after_off, test_mode=args.test)
        emit({"monitor_stopped": reason})
        return 0 if reason != "review_required" else 1
    except KeyboardInterrupt:
        emit({"monitor_stopped": "operator_interrupt"})
        return 130
    except OSError as exc:
        # A failed log write must not be retried through the same failed stream.
        print("Stopped: " + str(exc), flush=True)
        return 1
    except ImportError as exc:
        parser.exit(1, str(exc) + "\n")
    finally:
        if log is not None:
            log.close()


if __name__ == "__main__":
    raise SystemExit(main())
