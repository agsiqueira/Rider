import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools/lx_probe'))
from monitor import SerialSampler, monitor
from live_kombi import run
from test_live_kombi import FakePort


class ManagedPort(FakePort):
    def __init__(self):
        super().__init__()
        self.closed = 0
        self.resets = 0

    def close(self):
        self.closed += 1

    def reset_input_buffer(self):
        self.resets += 1
        self.pending.clear()


class SerialSamplerTests(unittest.TestCase):
    def test_two_real_probe_runs_share_one_host_connection(self):
        port, opens = ManagedPort(), []
        def connect():
            opens.append(True)
            return port
        with SerialSampler(connect) as sample:
            self.assertEqual(sample()['wake_up_line'], 'on')
            self.assertEqual(sample()['wake_up_line'], 'on')
            self.assertEqual(port.closed, 0)
        self.assertEqual(len(opens), 1)
        self.assertEqual(port.resets, 1)
        self.assertEqual(port.closed, 1)
        self.assertEqual(port.commands.count('ATPC'), 2)

    def test_can_error_keeps_host_connection(self):
        port = ManagedPort()
        with SerialSampler(lambda: port, lambda p, label: {'status': 'adapter_error', 'protocol_closed': True}) as sample:
            sample()
            sample()
            self.assertEqual(port.closed, 0)
            self.assertEqual(port.resets, 1)
        self.assertEqual(port.closed, 1)

    def test_transport_failure_releases_handle_and_reconnects(self):
        ports = [ManagedPort(), ManagedPort()]
        pending = iter(ports)
        outcomes = iter([{'status': 'transport_error', 'protocol_closed': False}, {'status': 'decoded', 'wake_up_line': 'on', 'identity_matches_candidate': True, 'protocol_closed': True}])
        ticks = iter((0, 10))
        emitted, sleeps = [], []
        with SerialSampler(lambda: next(pending), lambda p, label: next(outcomes)) as sample:
            self.assertEqual(monitor(sample, emitted.append, 2, 5, lambda: next(ticks), sleeps.append), 'sample_limit')
            self.assertEqual(ports[0].closed, 1)
            self.assertEqual(ports[1].closed, 0)
        self.assertEqual(ports[1].closed, 1)
        self.assertEqual(sleeps, [10])
        self.assertTrue(all(item['inference']['ignition'] == 'unknown' for item in emitted))

    def test_open_timeout_is_unknown_and_next_attempt_can_connect(self):
        port = ManagedPort()
        attempts = []
        def connect():
            attempts.append(True)
            if len(attempts) == 1:
                raise OSError('The semaphore timeout period has expired')
            return port
        with SerialSampler(connect) as sample:
            first = sample()
            self.assertEqual(first['status'], 'host_error')
            self.assertEqual(first['ignition'], 'unknown')
            self.assertEqual(sample()['status'], 'decoded')
        self.assertEqual(port.closed, 1)

    def test_missing_prompt_is_terminal_not_an_automatic_reconnect(self):
        port = ManagedPort()
        emitted, sleeps = [], []
        with SerialSampler(lambda: port, lambda p, label: {'status': 'stopped', 'protocol_closed': False}) as sample:
            reason = monitor(sample, emitted.append, 12, 5, lambda: 0, sleeps.append)
            self.assertEqual(reason, 'review_required')
            self.assertEqual(port.closed, 1)
            self.assertEqual(sleeps, [])

    def test_operator_interrupt_releases_host_connection(self):
        port = ManagedPort()
        with self.assertRaises(KeyboardInterrupt):
            with SerialSampler(lambda: port) as sample:
                sample()
                raise KeyboardInterrupt()
        self.assertEqual(port.closed, 1)

    def test_physical_read_error_is_transport_failure_not_can_error(self):
        class BrokenPort(ManagedPort):
            def read(self, size):
                raise OSError('Bluetooth link lost')
        result = run(BrokenPort(), 'test')
        self.assertEqual(result['status'], 'transport_error')
        self.assertEqual(result['ignition'], 'unknown')


if __name__ == '__main__':
    unittest.main()
