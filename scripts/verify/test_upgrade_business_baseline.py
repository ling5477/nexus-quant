"""比较器的反例覆盖：丢失、重复、关联变化和精度变化均不得报告一致。"""
import copy
import unittest

import upgrade_business_baseline as baseline


def snapshot():
    raw = {name: {'columns': ['id', 'value'], 'primaryKey': ['id'], 'rows': []}
           for name in baseline.TABLES}
    raw['ledger_entries']['rows'] = ['{"id": "entry-1", "value": 100.00000001}']
    raw['backtest_runs']['rows'] = ['{"id": "run-1", "value": "dataset-1"}']
    return {'format': baseline.FORMAT, 'sourceVersion': '1.0.0',
            'schema': [{'version': '1', 'checksum': 123, 'success': True}],
            'tables': baseline.fingerprint(raw)}


class UpgradeBusinessBaselineTest(unittest.TestCase):
    def test_same_snapshot_and_appended_migration_match(self):
        before = snapshot()
        after = copy.deepcopy(before)
        after['schema'].append({'version': '2', 'checksum': 456, 'success': True})
        after['sourceVersion'] = '1.0.1'
        self.assertEqual('MATCH', baseline.compare(before, after)['result'])

    def test_loss_duplicate_and_link_change_are_detected(self):
        before = snapshot()
        for kind in ('missing', 'added', 'changed'):
            with self.subTest(kind=kind):
                after = copy.deepcopy(before)
                table = after['tables']['backtest_runs']
                key = next(iter(table['rows']))
                if kind == 'missing':
                    table['rows'].clear()
                    table['count'] = 0
                elif kind == 'added':
                    table['rows']['["run-duplicate"]'] = table['rows'][key]
                    table['count'] = 2
                else:
                    table['rows'][key] = baseline.digest('{"id":"run-1","value":"wrong-dataset"}')
                result = baseline.compare(before, after)
                self.assertEqual('DIFFERENT', result['result'])
                self.assertTrue(result['changes'][0][kind])

    def test_eight_decimal_money_difference_is_not_rounded_away(self):
        before = snapshot()
        after = copy.deepcopy(before)
        key = next(iter(after['tables']['ledger_entries']['rows']))
        after['tables']['ledger_entries']['rows'][key] = baseline.digest(
            '{"id": "entry-1", "value": 100.00000002}')
        self.assertEqual('DIFFERENT', baseline.compare(before, after)['result'])

    def test_schema_checksum_change_or_failed_migration_is_detected(self):
        for field, value in (('checksum', 999), ('success', False)):
            before, after = snapshot(), snapshot()
            after['schema'][0][field] = value
            self.assertEqual('MIGRATION_HISTORY_DRIFT', baseline.compare(before, after)['changes'][0]['kind'])

    def test_incomplete_or_inconsistent_baseline_is_rejected(self):
        for mutate in (lambda b: b['tables'].pop('trades'),
                       lambda b: b['tables']['trades'].update(count=1)):
            damaged = snapshot()
            mutate(damaged)
            with self.assertRaises(ValueError):
                baseline.compare(snapshot(), damaged)

    def test_duplicate_primary_key_and_over_limit_input_are_rejected(self):
        raw = {name: {'columns': ['id'], 'primaryKey': ['id'], 'rows': []}
               for name in baseline.TABLES}
        for rows in (['{"id":1}', '{"id":1}'], ['{"id":1}'] * (baseline.LIMIT + 1)):
            raw['orders']['rows'] = rows
            with self.assertRaises(ValueError):
                baseline.fingerprint(raw)

    def test_unsafe_identifier_is_rejected(self):
        with self.assertRaises(ValueError):
            baseline.identifier('orders; DROP TABLE trades')


if __name__ == '__main__':
    unittest.main()
