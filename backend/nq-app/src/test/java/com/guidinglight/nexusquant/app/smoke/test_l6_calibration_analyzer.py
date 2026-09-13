"""离线反例仅修改测试副本，不生成正式校准或修改原始数据库。"""
import copy
import json
import unittest
from pathlib import Path
from l6_calibration_analyzer import series
from l6_oracle import verify


def sample(index, orders, gc_id=1):
    values = {'nq0': {'pid': 1, 'heapUsed': 99, 'gc': [{'name': 'test', 'count': gc_id, 'timeMillis': 1,
                       'lastGcStatus': 'MEASURED', 'lastGc': {'id': gc_id, 'startUptimeMillis': 5,
                       'endUptimeMillis': 6, 'durationMillis': 1, 'heapUsedAfterGc': 12}}]},
              'nq1': {'pid': 2, 'gc': [{'name': 'test', 'count': 0, 'timeMillis': 0, 'lastGcStatus': 'NO_GC_OBSERVED'}]},
              'venue': {'queue': 0}, 'postgres': {'orders': orders, 'auditRows': orders * 4, 'eventRows': orders},
              'os': {'handles': 10 + index, 'fd': 'NOT_APPLICABLE_WINDOWS'},
              'files': {'logBytes': 100 + index * 10, 'ownedTempFileCount': 2, 'ownedTempBytes': 10 + index},
              'ownership': {'ownedProcessCount': 4, 'ownedContainerCount': 1}}
    token = 'UNIT_TEST_ONLY:' + str(index)
    return {'sampleIndex': index, 'sampleToken': token, 'elapsedMillis': index * 10000, 'phase': 'MEASUREMENT',
            'collectionMillis': 1, 'status': 'MEASURED', 'sources': {
                key: {'sampleIndex': index, 'sampleToken': token, 'status': 'MEASURED', 'values': value,
                      'availability': {field: 'NOT_APPLICABLE' if field == 'fd' else 'MEASURED' for field in value}}
                for key, value in values.items()}}


class CalibrationAnalyzerTest(unittest.TestCase):
    def test_gc_low_water_and_growth_keep_source_indices_and_zero_denominator(self):
        result = series([sample(0, 1), sample(1, 1), sample(2, 3, 2)])
        self.assertEqual([12, 12], [x['heapUsedAfterGc'] for x in result['gcPostCollectionObservations']])
        self.assertEqual([0, 2], [x['observedSampleIndex'] for x in result['gcPostCollectionObservations']])
        logs = [x for x in result['growth'] if x['resource'] == 'logBytes']
        self.assertIsNone(logs[1]['perOrderNormalizedDelta'])
        self.assertEqual(5, logs[2]['perOrderNormalizedDelta'])
        fields = {x['field'] for x in result['resourceObservations']}
        self.assertTrue({'.handles', '.ownedTempBytes', '.logBytes'}.issubset(fields))
        self.assertFalse(result['formalCalibrationAccepted'])

    def test_missing_or_stale_measurement_rejects(self):
        missing = sample(0, 1)
        missing['sources']['files']['availability']['logBytes'] = 'UNAVAILABLE'
        with self.assertRaises(ValueError): series([missing])
        gap = sample(1, 1)
        with self.assertRaises(ValueError): series([gap])
        stale = sample(0, 1)
        stale['sources']['os']['sampleToken'] = 'OLD'
        with self.assertRaises(ValueError): series([stale])

    def test_duplicate_accounting_and_partial_chain_are_rejected_by_shared_oracle(self):
        source = Path(__file__).resolve().parents[6] / 'resources/l6-calibration/checkpoint.json'
        proof = json.loads(source.read_text(encoding='utf-8'))
        verify(proof)
        proof['mode'] = 'CALIBRATION'
        verify(proof)
        for field in ('ledger_entries', 'trades', 'events'):
            bad = copy.deepcopy(proof)
            bad['facts'][field].append(copy.deepcopy(bad['facts'][field][0]))
            with self.assertRaises(ValueError): verify(bad)
        bad = copy.deepcopy(proof)
        bad['facts']['ledger_entries'].pop()
        with self.assertRaises(ValueError): verify(bad)


if __name__ == '__main__':
    unittest.main()
