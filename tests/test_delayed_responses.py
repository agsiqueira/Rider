import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools/lx_probe'))
from live_kombi import run, WAKE_FRAME
from monitor import monitor
from test_live_kombi import FakePort


class DelayedResponseTests(unittest.TestCase):
    def test_identity_pending_is_not_mismatch_and_blocks_wake(self):
        port = FakePort(ident='660 F1 03 7F 22 78 FF FF FF')
        result = run(port, 'synthetic')
        self.assertEqual(result['status'], 'pending')
        self.assertEqual(result['stage'], 'identity')
        self.assertNotIn(WAKE_FRAME, port.commands)
        self.assertTrue(result['protocol_closed'])

    def test_identical_identity_replies_remain_ambiguous(self):
        frame = '660 F1 06 62 F1 50 0F 88 00'
        port = FakePort(ident=frame + '\r' + frame)
        result = run(port, 'synthetic')
        self.assertEqual(result['status'], 'ambiguous_response')
        self.assertNotIn(WAKE_FRAME, port.commands)
        self.assertEqual(result['ignition'], 'unknown')

    def test_pending_only_stops_before_next_sample(self):
        outputs, delays = [], []
        result = {'status': 'pending', 'stage': 'identity', 'protocol_closed': True}
        reason = monitor(lambda: result, outputs.append, 12, 5, lambda: 0, delays.append)
        self.assertEqual(reason, 'response_pending_unresolved')
        self.assertEqual(len(outputs), 1)
        self.assertEqual(delays, [])
        self.assertEqual(outputs[0]['inference']['ignition'], 'unknown')

    def test_ambiguity_stops_before_next_sample(self):
        outputs = []
        result = {'status': 'ambiguous_response', 'protocol_closed': True}
        self.assertEqual(monitor(lambda: result, outputs.append, 12, 5, lambda: 0), 'review_required')
        self.assertEqual(len(outputs), 1)


if __name__ == '__main__':
    unittest.main()
