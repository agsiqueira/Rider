"""Decode an already reassembled UDS payload; does not open an LX connection."""
import argparse
import json

REQUEST = bytes.fromhex("22 E1 1C")
JOB = {"sgbd_candidate": "X_KOMBI", "job": "STATUS_LESEN",
       "arguments": "ARG;WECKLEITUNG_DIGITAL_MR",
       "result": "STAT_WECKLEITUNG_DIGITAL_EIN"}


def decode(payload):
    result = {"request_uds": REQUEST.hex(" ").upper(),
              "response_uds": payload.hex(" ").upper(),
              "wake_up_line": "unknown", "ignition": "unknown"}
    if not payload:
        result["status"] = "unavailable"
    elif len(payload) == 3 and payload[:2] == bytes.fromhex("7F 22"):
        result["status"] = "pending" if payload[2] == 0x78 else "negative_response"
        result["negative_response_code"] = f"0x{payload[2]:02X}"
    elif len(payload) != 4 or payload[:3] != bytes.fromhex("62 E1 1C"):
        result["status"] = "invalid_response"
    elif payload[3] not in (0, 1):
        result["status"] = "invalid_value"
        result["raw_value"] = payload[3]
    else:
        result.update(status="decoded", raw_value=payload[3],
                      wake_up_line="on" if payload[3] else "off")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--uds", required=True, help="Hex UDS payload only, no CAN headers or ISO-TP framing; empty means unavailable")
    args = parser.parse_args()
    try:
        result = decode(bytes.fromhex(args.uds))
    except ValueError:
        parser.error("--uds must contain hexadecimal bytes")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "decoded" else 1


if __name__ == "__main__":
    raise SystemExit(main())
