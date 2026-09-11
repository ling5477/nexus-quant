"""以真实 run 原始事实作为正例，验证 oracle、导出拒绝边界和身份双射。"""
import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from l5_measurement import main, mutation_negatives, verify
from synthetic_evidence import IdentityMapper, export

PROOFS = [json.loads(Path(p).read_text(encoding='utf-8')) for p in sys.argv[1:]]


class L5MeasurementTest(unittest.TestCase):
    def test_actual_runs_and_oracle_mutations(self):
        self.assertEqual(3, len(PROOFS))
        for proof in PROOFS:
            with self.subTest(level=proof['level']):
                self.assertEqual(proof['expectedOrders'] + proof['expectedStrategyRuns'], len(verify(proof)))
                self.assertGreaterEqual(len(mutation_negatives(proof)), 17)

    def test_export_rejects_secret_fields_without_writing_canonical_output(self):
        for field in ('apiKey', 'token', 'Authorization'):
            with self.subTest(field=field), tempfile.TemporaryDirectory(prefix='nq-l5-export-') as directory:
                proof = copy.deepcopy(PROOFS[0])
                proof['facts']['orders'][0][field] = 'sentinel-must-be-rejected'
                raw = Path(directory) / 'raw-proof.json'
                raw.write_text(json.dumps(proof), encoding='utf-8')
                before = raw.read_bytes()
                with self.assertRaises(ValueError):
                    main(raw)
                self.assertEqual(before, raw.read_bytes())
                self.assertFalse(raw.with_name('summary.json').exists())

    def test_unknown_fields_and_incomplete_run_are_rejected(self):
        for field, value in [('unknownEvidence', 'unclassified'), ('result', 'FAIL')]:
            proof = copy.deepcopy(PROOFS[0])
            proof[field] = value
            with self.assertRaises(ValueError):
                verify(proof)

    def test_identity_mapping_is_bijective_preserves_source_and_joins(self):
        for proof in PROOFS:
            rows = verify(proof)
            original = copy.deepcopy(rows)
            mapper = IdentityMapper('B5-L5', int(proof['level'][1:]))
            mapped = export({'rows': rows}, mapper)['rows']
            self.assertEqual(original, rows)
            self.assertEqual(len(mapper.identities), len(set(mapper.identities.values())))
            self.assertEqual(len(rows), len({row['order_id'] for row in mapped}))
            for source, target in zip(rows, mapped):
                for field, kind in [('order_id', 'ORDER'), ('trade_id', 'TRADE'), ('event_id', 'EVENT'),
                                    ('client_order_id', 'CLIENT'), ('external_order_id', 'VENUE'), ('exchange_trade_id', 'FILL')]:
                    self.assertEqual(mapper.identities[kind, source[field]], target[field])


if __name__ == '__main__':
    unittest.main(argv=[sys.argv[0]])
