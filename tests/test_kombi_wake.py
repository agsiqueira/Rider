import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("kombi_wake", Path(__file__).resolve().parents[1] / "tools/lx_probe/kombi_wake.py")
wake = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wake)


class WakeTests(unittest.TestCase):
    def test_documented_read_request(self):
        self.assertEqual(wake.REQUEST, bytes.fromhex("22 E1 1C"))

    def test_both_boolean_values_without_ignition_inference(self):
        for value, state in ((0, "off"), (1, "on")):
            result = wake.decode(bytes.fromhex("62 E1 1C") + bytes([value]))
            self.assertEqual(result["wake_up_line"], state)
            self.assertEqual(result["ignition"], "unknown")

    def test_unavailable_negative_and_pending_are_unknown(self):
        for payload, status in ((b"", "unavailable"), (bytes.fromhex("7F 22 31"), "negative_response"), (bytes.fromhex("7F 22 78"), "pending")):
            result = wake.decode(payload)
            self.assertEqual(result["status"], status)
            self.assertEqual(result["wake_up_line"], "unknown")

    def test_wrong_service_identifier_length_and_enum_rejected(self):
        for text in ("62 E1 1D 01", "6E E1 1C 01", "62 E1 1C", "62 E1 1C 01 00", "62 E1 1C FF", "60 04 62 E1 1C 01", "7F 22"):
            self.assertEqual(wake.decode(bytes.fromhex(text))["wake_up_line"], "unknown")


if __name__ == "__main__":
    unittest.main()
