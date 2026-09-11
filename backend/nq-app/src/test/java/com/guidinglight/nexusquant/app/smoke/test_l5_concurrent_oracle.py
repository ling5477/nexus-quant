"""复用真实并发run，拒绝伪并发、游标/公平性失真及旧的合成成交时间错误。"""
import copy
import json
import sys
import unittest
import tempfile
from pathlib import Path
from l5_concurrent_oracle import verify_concurrent, concurrent_negatives, export_failure
from l5_measurement import verify
from decimal import Decimal

PROOFS = [json.loads(Path(p).read_text(encoding='utf-8')) for p in sys.argv[1:]]


class ConcurrentOracleTest(unittest.TestCase):
    def test_actual_runs_and_mutations(self):
        # 本批在C1真实持仓丢失后停止；离线检查失败事实不等于qualification通过。
        self.assertEqual({'C1'}, {p['level'] for p in PROOFS})
        for proof in PROOFS:
            actual = verify_concurrent(proof)
            self.assertEqual(proof['concurrency'], actual['overlappingCallsDuringProducer'])
            self.assertGreaterEqual(len(concurrent_negatives(proof)), 8)

    def test_business_oracle_rejects_observed_position_loss(self):
        for proof in PROOFS:
            expected = sum(Decimal(str(t['qty'])) for t in proof['facts']['trades'])
            actual = Decimal(str(proof['facts']['positions'][0]['qty']))
            self.assertLess(actual, expected)
            with self.assertRaisesRegex(ValueError, 'position lost or duplicated accounting'):
                verify(proof)

    def test_fill_timestamps_cannot_reuse_place_time(self):
        proof = copy.deepcopy(PROOFS[0])
        for order in proof['venue']['data']:
            order['uTime'] = str(proof['concurrent']['producerStartMillis'] - 1)
        with self.assertRaisesRegex(ValueError, 'fill timestamp'):
            verify_concurrent(proof)

    def test_failure_export_preserves_raw_identity_and_rejects_secrets(self):
        with tempfile.TemporaryDirectory(prefix='nq-l5c-failure-') as directory:
            raw = Path(directory) / 'raw-proof.json'
            raw.write_text(json.dumps(PROOFS[0]), encoding='utf-8')
            before = raw.read_bytes()
            export_failure(raw)
            self.assertEqual(before, raw.read_bytes())
            output = raw.with_name('failure-summary.json')
            text = output.read_text(encoding='utf-8')
            result = json.loads(text)
            self.assertEqual('FAIL', result['result'])
            self.assertEqual(120, len({r['trade_id'] for r in result['trades']}))
            for trade in PROOFS[0]['facts']['trades']:
                self.assertNotIn(trade['trade_id'], text)
                self.assertNotIn(trade['order_id'], text)
            output.unlink()
            proof = copy.deepcopy(PROOFS[0])
            proof['facts']['orders'][0]['Authorization'] = 'sentinel-must-be-rejected'
            raw.write_text(json.dumps(proof), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'sensitive field'):
                export_failure(raw)
            self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main(argv=[sys.argv[0]])
