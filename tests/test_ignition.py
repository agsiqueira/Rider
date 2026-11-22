import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools/lx_probe'))
from ignition import IgnitionTracker
from monitor import monitor


def observation(value='on', **extra):
    return dict(status='decoded', identity_matches_candidate=True, protocol_closed=True,
                wake_up_line=value, **extra)


class IgnitionTests(unittest.TestCase):
    def test_boot_requires_two_reads_and_is_not_an_on_event(self):
        tracker = IgnitionTracker()
        self.assertEqual(tracker.update(observation(), 0)['ignition'], 'unknown')
        self.assertEqual(tracker.update(observation(), 5)['event'], 'state_established')
        self.assertIsNone(tracker.update(observation(), 10)['event'])

    def test_two_read_edges_and_no_duplicate_events(self):
        tracker = IgnitionTracker()
        results = [tracker.update(observation(v), i * 5) for i, v in enumerate(('off', 'off', 'on', 'on', 'on', 'off', 'off'))]
        self.assertEqual([r['event'] for r in results], [None, 'state_established', None, 'ignition_on', None, None, 'ignition_off'])

    def test_noise_does_not_create_edge(self):
        tracker = IgnitionTracker()
        for i, v in enumerate(('on', 'on', 'off', 'on', 'on')):
            result = tracker.update(observation(v), i * 5)
            self.assertNotIn(result['event'], ('ignition_on', 'ignition_off'))

    def test_disconnect_never_off_and_reconnect_same_no_edge(self):
        tracker = IgnitionTracker()
        tracker.update(observation(), 0)
        tracker.update(observation(), 5)
        result = tracker.update({'status': 'adapter_error', 'adapter_error': 'can_error'}, 10)
        self.assertEqual(result['ignition'], 'unknown')
        self.assertEqual(result['last_confirmed'], 'on')
        self.assertEqual(tracker.update(observation(), 15)['ignition'], 'unknown')
        self.assertEqual(tracker.update(observation(), 20)['event'], 'state_recovered')

    def test_changed_state_after_gap_not_precise_edge(self):
        tracker = IgnitionTracker()
        tracker.update(observation('off'), 0)
        tracker.update(observation('off'), 5)
        tracker.update({'status': 'host_error'}, 10)
        tracker.update(observation(), 15)
        self.assertEqual(tracker.update(observation(), 20)['event'], 'change_observed_after_gap')

    def test_stale_read_resets_confirmation(self):
        tracker = IgnitionTracker()
        tracker.update(observation(), 0)
        self.assertEqual(tracker.update(observation(), 20)['ignition'], 'unknown')
        self.assertEqual(tracker.update(observation(), 25)['event'], 'state_established')

    def test_no_untrusted_or_unclosed_confirmation(self):
        for override in ({'identity_matches_candidate': False}, {'protocol_closed': False}, {'status': 'pending'}):
            tracker = IgnitionTracker()
            value = observation()
            value.update(override)
            self.assertEqual(tracker.update(value, 0)['ignition'], 'unknown')
            self.assertEqual(tracker.update(value, 5)['ignition'], 'unknown')

    def test_duplicate_and_out_of_order_time_rejected(self):
        tracker = IgnitionTracker()
        tracker.update(observation(), 5)
        for now in (5, 4, float('nan')):
            with self.assertRaises(ValueError):
                tracker.update(observation(), now)

    def test_bounded_reconnect_backoff_and_recovery(self):
        values = iter([{'status': 'host_error'}] * 2 + [observation(), observation()])
        ticks = iter((0, 10, 30, 35))
        sleeps, outputs = [], []
        self.assertEqual(monitor(lambda: next(values), outputs.append, 4, 5, lambda: next(ticks), sleeps.append), 'sample_limit')
        self.assertEqual(sleeps, [10, 20, 5])
        self.assertEqual(outputs[-1]['inference']['event'], 'state_established')

    def test_stops_after_confirmed_off(self):
        outputs = []
        ticks = iter((0, 5))
        self.assertEqual(monitor(lambda: observation('off'), outputs.append, 12, 5, lambda: next(ticks), lambda _: None), 'confirmed_off')
        self.assertEqual(len(outputs), 2)

    def test_explicit_continue_after_off_observes_on_edge(self):
        values = iter([observation('off'), observation('off'), observation(), observation()])
        ticks = iter((0, 5, 10, 15))
        outputs = []
        reason = monitor(lambda: next(values), outputs.append, 4, 5,
                         lambda: next(ticks), lambda _: None, stop_on_off=False)
        self.assertEqual(reason, 'sample_limit')
        self.assertEqual(outputs[-1]['inference']['event'], 'ignition_on')

    def test_off_on_mode_confirms_once_then_stops(self):
        values = iter([observation('off'), observation('off'), observation(), observation(), observation()])
        ticks = iter((0, 5, 10, 15))
        outputs = []
        reason = monitor(lambda: next(values), outputs.append, 12, 5,
                         lambda: next(ticks), lambda _: None, test_mode='off-on')
        self.assertEqual(reason, 'confirmed_on_transition')
        self.assertEqual(len(outputs), 4)
        self.assertEqual([o['inference']['event'] for o in outputs].count('ignition_on'), 1)

    def test_off_on_mode_does_not_pass_when_started_already_on(self):
        ticks = iter((0, 5, 10))
        outputs = []
        reason = monitor(lambda: observation(), outputs.append, 3, 5,
                         lambda: next(ticks), lambda _: None, test_mode='off-on')
        self.assertEqual(reason, 'sample_limit')
        self.assertNotIn('ignition_on', [o['inference']['event'] for o in outputs])

    def test_off_on_mode_does_not_invent_edge_across_gap(self):
        values = iter([observation('off'), observation('off'), {'status': 'host_error'}, observation(), observation()])
        ticks = iter((0, 5, 10, 20, 25))
        outputs = []
        reason = monitor(lambda: next(values), outputs.append, 5, 5,
                         lambda: next(ticks), lambda _: None, test_mode='off-on')
        self.assertEqual(reason, 'sample_limit')
        self.assertEqual(outputs[-1]['inference']['event'], 'change_observed_after_gap')

    def test_synthetic_on_off_frames_reproduce_expected_events(self):
        from live_kombi import parse_single_frame
        from kombi_wake import decode
        tracker = IgnitionTracker()
        events = []
        for index, bit in enumerate(('01', '01', '00', '00')):
            value = observation()
            value.update(decode(parse_single_frame(['660 F1 04 62 E1 1C ' + bit + ' FF FF'])))
            inferred = tracker.update(value, index * 5)
            events.append(inferred['event'])
        self.assertEqual(events, [None, 'state_established', None, 'ignition_off'])

    def test_unsynchronized_transport_stops_without_retry(self):
        outputs, sleeps = [], []
        reason = monitor(lambda: {'status': 'stopped', 'protocol_closed': False}, outputs.append, 12, 5, lambda: 0, sleeps.append)
        self.assertEqual(reason, 'review_required')
        self.assertEqual(sleeps, [])


if __name__ == '__main__':
    unittest.main()
