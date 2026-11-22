import importlib.util
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools/lx_probe"))
import live_kombi as live


class FakePort:
    def __init__(self, ident="660 F1 06 62 F1 50 0F 88 00", wake="660 F1 04 62 E1 1C 01 00 00", setup_failure=None):
        self.commands = []
        self.pending = bytearray()
        self.ident, self.wake = ident, wake
        self.setup_failure = setup_failure

    def write(self, value):
        command = value.decode().strip()
        self.commands.append(command)
        answer = "OK"
        if command == "STI":
            answer = "STN1155 v4.3.0"
        elif command == live.IDENT_FRAME:
            answer = self.ident
        elif command == live.WAKE_FRAME:
            answer = self.wake
        elif command == self.setup_failure:
            answer = "?"
        self.pending.extend((answer + "\r>").encode())
        return len(value)

    def read(self, size):
        value = bytes(self.pending[:size])
        del self.pending[:size]
        return value


class LiveProbeTests(unittest.TestCase):
    def test_only_two_fixed_reads_and_cleanup(self):
        port = FakePort()
        result = live.run(port, "key-on")
        self.assertEqual(result["wake_up_line"], "on")
        self.assertEqual(result["ignition"], "unknown")
        self.assertEqual(result["profile_validation"], "user_tested_single_bike")
        self.assertEqual(port.commands, ["STI", *live.SETUP, live.IDENT_FRAME, live.WAKE_FRAME, "ATPC"])
        self.assertTrue(result["protocol_closed"])

    def test_wrong_identity_blocks_wake_read(self):
        for ident in ("NO DATA", "660 F1 06 62 F1 50 0F 88 10", "660 F1 03 7F 22 31"):
            port = FakePort(ident=ident)
            self.assertEqual(live.run(port, "test")["status"], "identity_unconfirmed")
            self.assertNotIn(live.WAKE_FRAME, port.commands)
            self.assertEqual(port.commands[-1], "ATPC")

    def test_failed_setup_blocks_all_vehicle_reads(self):
        for command in live.SETUP:
            port = FakePort(setup_failure=command)
            self.assertEqual(live.run(port, "test")["status"], "stopped")
            self.assertNotIn(live.IDENT_FRAME, port.commands)
            self.assertNotIn(live.WAKE_FRAME, port.commands)

    def test_other_senders_recipients_multiframes_and_truncation_rejected(self):
        for text in ("661 F1 04 62 E1 1C 01", "660 F2 04 62 E1 1C 01", "660 F1 10 62 E1 1C 01", "660 F1 04 62 E1 1C", "660 8 F1 04 62 E1 1C 01 00 00", "CAN ERROR"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                live.parse_single_frame([text])
        with self.assertRaises(ValueError):
            live.parse_single_frame(["660 F1 04 62 E1 1C 00", "660 F1 04 62 E1 1C 01"])

    def test_negative_pending_and_no_data_never_mean_off(self):
        for reply in ("NO DATA", "660 F1 03 7F 22 78", "660 F1 03 7F 22 31"):
            result = live.run(FakePort(wake=reply), "test")
            self.assertEqual(result["wake_up_line"], "unknown")

    def test_can_error_is_explicit_and_blocks_wake_read(self):
        port = FakePort(ident="CAN ERROR")
        result = live.run(port, "key-off-after-5min")
        self.assertEqual(result["status"], "adapter_error")
        self.assertEqual(result["adapter_error"], "can_error")
        self.assertEqual(result["wake_up_line"], "unknown")
        self.assertNotIn(live.WAKE_FRAME, port.commands)
        self.assertTrue(result["protocol_closed"])

    def test_synthetic_pending_then_final_decodes_on(self):
        raw = '660 F1 03 7F 22 78 FF FF FF\r660 F1 04 62 E1 1C 01 FF FF'
        result = live.run(FakePort(wake=raw), 'synthetic-regression')
        self.assertEqual(result['status'], 'decoded')
        self.assertEqual(result['wake_up_line'], 'on')
        self.assertTrue(result['protocol_closed'])

    def test_repeated_pending_and_final_off(self):
        pending = '660 F1 03 7F 22 78 FF FF FF'
        result = live.run(FakePort(wake=pending + '\r' + pending + '\r660 F1 04 62 E1 1C 00 FF FF'), 'test')
        self.assertEqual(result['wake_up_line'], 'off')

    def test_pending_only_remains_unknown(self):
        result = live.run(FakePort(wake='660 F1 03 7F 22 78 FF FF FF'), 'test')
        self.assertEqual(result['status'], 'pending')
        self.assertEqual(result['wake_up_line'], 'unknown')

    def test_pending_cannot_hide_wrong_sender_or_conflicting_final(self):
        pending = '660 F1 03 7F 22 78 FF FF FF'
        final = '660 F1 04 62 E1 1C 01 FF FF'
        for lines in ([pending, '661 F1 04 62 E1 1C 01'],
                      ['660 F1 03 7F 2E 78 FF FF FF', final],
                      ['660 F1 03 7F 22 31 FF FF FF', final],
                      [final, pending], [pending, final, final],
                      [pending] * 9 + [final]):
            with self.subTest(lines=lines), self.assertRaises(ValueError):
                live.parse_single_frame(lines)

    def test_wrong_adapter_blocks_setup(self):
        class OtherAdapter(FakePort):
            def write(self, value):
                self.commands.append(value.decode().strip())
                self.pending.extend(b"STN2255 v4.3.0\r>")
                return len(value)
        port = OtherAdapter()
        self.assertEqual(live.run(port, "test")["status"], "stopped")
        self.assertEqual(port.commands, ["STI"])

    def test_lost_prompt_skips_cleanup_on_unsynchronized_stream(self):
        class LostPrompt(FakePort):
            def read(self, size):
                if self.commands[-1] == live.IDENT_FRAME:
                    raise TimeoutError("Simulated lost prompt")
                return super().read(size)
        port = LostPrompt()
        result = live.run(port, "test")
        self.assertEqual(result["status"], "stopped")
        self.assertFalse(result["protocol_closed"])
        self.assertNotIn("ATPC", port.commands)
        self.assertNotIn(live.WAKE_FRAME, port.commands)

    def test_arbitrary_commands_cannot_reach_port(self):
        port = FakePort()
        for command in ("04", "ATZ", "60032EE11C000000", "STI\r04"):
            with self.assertRaises(ValueError):
                live.exchange(port, command, [])
        self.assertEqual(port.commands, [])


if __name__ == "__main__":
    unittest.main()
