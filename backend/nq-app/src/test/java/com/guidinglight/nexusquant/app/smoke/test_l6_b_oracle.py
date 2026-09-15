"""前缀hash、增量日志与压缩边界的负例；真实业务关系另由跨进程probe验证。"""
import gzip
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from l6_b_oracle import load_checkpoint


class PrefixEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name)
        self.event = {'sequence': 1, 'type': 'REQUEST_RECEIVED', 'client': 'SYNTH-B-CLIENT', 'nanoTime': 1}
        self.line = (json.dumps(self.event) + '\n').encode()
        self.journal = self.directory / 'venue-events.ndjson'
        self.journal.write_bytes(self.line)
        self.value = {'mode': 'FORMAL_L6_B', 'venue': {'boundedDelayApplied': 0,
                      'eventJournal': {'path': self.journal.name, 'eventCount': 1,
                                       'bytes': len(self.line), 'sha256': hashlib.sha256(self.line).hexdigest()}}}
        self.checkpoint = self.directory / 'checkpoint.json.gz'

    def tearDown(self):
        self.temporary.cleanup()

    def save(self):
        self.checkpoint.write_bytes(gzip.compress(json.dumps(self.value).encode()))

    def test_later_append_preserves_prior_complete_prefix(self):
        self.save()
        self.journal.write_bytes(self.line + b'{"sequence":2}\n')
        self.assertEqual(load_checkpoint(self.checkpoint)['venue']['events'], [self.event])

    def test_tampered_prefix_rejected(self):
        self.save()
        self.journal.write_bytes(self.line.replace(b'REQUEST_RECEIVED', b'REQUEST_REJECTED'))
        with self.assertRaises(ValueError):
            load_checkpoint(self.checkpoint)

    def test_truncated_prefix_rejected(self):
        self.save()
        self.journal.write_bytes(self.line[:-1])
        with self.assertRaises(ValueError):
            load_checkpoint(self.checkpoint)

    def test_non_owned_path_rejected(self):
        self.value['venue']['eventJournal']['path'] = '../venue-events.ndjson'
        self.save()
        with self.assertRaises(ValueError):
            load_checkpoint(self.checkpoint)

    def test_ambiguous_event_sequence_rejected(self):
        altered = self.line.replace(b'"sequence": 1', b'"sequence": 2')
        self.journal.write_bytes(altered)
        reference = self.value['venue']['eventJournal']
        reference.update(bytes=len(altered), sha256=hashlib.sha256(altered).hexdigest())
        self.save()
        with self.assertRaises(ValueError):
            load_checkpoint(self.checkpoint)

    def test_missing_delay_completion_rejected(self):
        self.value['venue']['boundedDelayApplied'] = 1
        self.save()
        with self.assertRaises(ValueError):
            load_checkpoint(self.checkpoint)


if __name__ == '__main__':
    unittest.main()
