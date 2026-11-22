import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("probe", Path(__file__).resolve().parents[1] / "tools/lx_probe/probe.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class ProbeTests(unittest.TestCase):
    def test_only_adapter_commands_and_unknown_ignition(self):
        result = probe.collect(probe.DemoPort(), "key-on")
        self.assertEqual([o["command"] for o in result["observations"]], ["ATI", "STI", "STIX", "ATRV"])
        self.assertEqual(result["ignition"], "unknown")
        self.assertEqual(result["kombi_wake_up_line"], "unknown")
        self.assertEqual(result["observations"][2]["status"], "unsupported")
        self.assertEqual(result["observations"][0]["lines"], ["ELM327 DEMO"])

    def test_write_and_diagnostic_commands_rejected_before_transport(self):
        class NoWrite:
            def write(self, data):
                raise AssertionError("Must not reach transport")
        for command in ("04", "22F190", "ATZ", "ATSH6F1", "ATMA", "ATI\r04"):
            with self.subTest(command=command), self.assertRaises(ValueError):
                probe.query(NoWrite(), command)

    def test_missing_prompt_stops(self):
        with self.assertRaises(TimeoutError):
            probe.query(probe.DemoPort(), "ATI", timeout=0)

    def test_oversized_response_stops(self):
        class Oversized:
            def write(self, data):
                pass
            def read(self, size):
                return b"x" * (probe.MAX_RESPONSE + 1)
        with self.assertRaises(ValueError):
            probe.query(Oversized(), "ATI")


if __name__ == "__main__":
    unittest.main()
