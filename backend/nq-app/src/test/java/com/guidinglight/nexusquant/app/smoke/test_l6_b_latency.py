import copy
import unittest
import json
import tempfile
from pathlib import Path
from l6_b_latency import distribution, verify_rows, read_events


class LatencyTest(unittest.TestCase):
    def test_reader_uses_frozen_budgets_and_rejects_excess_or_missing_events(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'events.ndjson'
            events = self.rows()
            path.write_text(''.join(json.dumps(row)+'\n' for row in events), encoding='utf-8')
            size = path.stat().st_size
            self.assertEqual(events, read_events(path, 4, 4415, size))
            for calls, cap, raw in ((4, 3, size), (4, 4415, size-1), (3, 4415, size), (5, 4415, size)):
                with self.assertRaises(ValueError):
                    read_events(path, calls, cap, raw)

    def rows(self):
        events = []
        for index, start in enumerate([0, 6_000_000_000, 17_000_000_000, 23_000_000_000]):
            row = dict(commandId=str(index+1), actor=index % 2, generation=0, pid=index % 2+100,
                       dispatchElapsedNanos=start, state='DISPATCHED')
            events.append(dict(row))
            row.update(state='SUCCESS', completionElapsedNanos=start+6_000_000_000, latencyMillis=6000,
                       executionBoundSeconds=45, responseBoundSeconds=75,
                       reply=dict(commandId=str(index+1), result='SUCCESS', candidateCount=100,
                                  childStartNanos=start, childCompletionNanos=start+6_000_000_000))
            for name in ('before', 'after'):
                row[name] = dict(scope='LATEST_INDEPENDENT_SAMPLE_NOT_COMMAND_TIME_SNAPSHOT', sampleIndex=0,
                                 actor=dict(candidateAge={}), pg=dict(backlog=0))
            events.append(row)
        return events

    def test_bounded_slow_is_observation(self):
        result = verify_rows(self.rows(), 4)
        self.assertEqual(4, distribution(result)['over5Seconds'])

    def test_rejects_missing_late_misattributed_and_catch_up(self):
        source = self.rows()
        with self.assertRaises(ValueError):
            verify_rows(source[:-1], 4)
        for change in ('identity', 'catchup', 'timeout', 'failure'):
            rows = copy.deepcopy(source)
            if change == 'identity':
                rows[3]['reply']['commandId'] = '1'
            elif change == 'catchup':
                rows[4]['dispatchElapsedNanos'] -= 1
                rows[5]['dispatchElapsedNanos'] -= 1
                rows[5]['latencyMillis'] += .000001
            elif change == 'timeout':
                rows[1]['reply']['childCompletionNanos'] = 46_000_000_000
            else:
                rows[1]['state'] = 'FAILED'
            with self.subTest(change=change), self.assertRaises(ValueError):
                verify_rows(rows, 4)


if __name__ == '__main__':
    unittest.main()
