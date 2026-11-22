import io
import json
from pathlib import Path
import sys
import unittest
from contextlib import redirect_stdout
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools/lx_probe'))
from monitor import format_status, emit_status


class MonitorOutputTests(unittest.TestCase):
    def test_initial_on_prompts_operator(self):
        self.assertIn('TURN THE KEY OFF NOW', format_status({'inference': {'ignition': 'on', 'event': 'state_established'}}))

    def test_recovery_does_not_claim_new_transition(self):
        self.assertNotIn('TURN THE KEY OFF NOW', format_status({'inference': {'ignition': 'on', 'event': 'state_recovered'}}))

    def test_one_off_read_does_not_claim_confirmed_off(self):
        result = format_status({'sample': 4, 'observation': {'status': 'decoded', 'wake_up_line': 'off'}, 'inference': {'ignition': 'unknown'}})
        self.assertIn('OFF detected (1/2)', result)
        self.assertNotIn('OFF CONFIRMED', result)

    def test_full_transcript_is_logged_but_not_printed(self):
        item = {'sample': 1, 'observation': {'status': 'host_error', 'error': 'timeout', 'transcript': [{'raw': 'diagnostic-bytes'}]}, 'inference': {'ignition': 'unknown'}}
        log, console = io.StringIO(), io.StringIO()
        with redirect_stdout(console):
            emit_status(item, log)
        self.assertEqual(json.loads(log.getvalue()), item)
        self.assertNotIn('diagnostic-bytes', console.getvalue())
        self.assertIn('timeout', console.getvalue())

    def test_off_on_mode_prompts_on_and_never_prompts_off_after_success(self):
        item = {'test_mode': 'off-on', 'inference': {'ignition': 'off', 'event': 'state_established'}}
        self.assertIn('TURN THE KEY ON NOW', format_status(item))
        item['inference'] = {'ignition': 'on', 'event': 'ignition_on'}
        self.assertIn('TRANSITION CONFIRMED', format_status(item))
        self.assertNotIn('TURN THE KEY OFF', format_status(item))

    def test_explicit_json_output_preserves_old_format(self):
        item = {'monitor_stopped': 'confirmed_off'}
        console = io.StringIO()
        with redirect_stdout(console):
            emit_status(item, None, True)
        self.assertEqual(json.loads(console.getvalue()), item)


if __name__ == '__main__':
    unittest.main()
