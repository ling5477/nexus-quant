"""只读验证真实进程快照；不连接数据库、不制造或修改业务事实。"""
import copy
import json
import sys
from decimal import Decimal
from pathlib import Path


def one(rows):
    assert len(rows) == 1
    return rows[0]


def dec(value):
    return Decimal(str(value))


def verify(d):
    assert d['result'] == 'PASS' and d['databaseAbsent'] is True
    assert d['schemaVersion'] == '51' and d['postgresVersion'].startswith('16.')
    assert len({d[k] for k in ['controllerPid', 'nqPid', 'restartPid', 'successorPid', 'venuePid']}) == 5
    f = d['final']; v = d['venue']; o = one(f['orders']); t = one(f['trades'])
    a = one(f['ordinary_place_authorities']); e = one(f['tradeExecuted'])
    assert f == d['afterReplay']
    assert o['status'] == 'FILLED' and o['version'] == 4 and o['trade_env'] == 'SIM'
    assert a['order_id'] == t['order_id'] == o['order_id']
    assert a['state'] == 'MAY_HAVE_ESCAPED'
    assert v['pid'] == d['venuePid'] and v['placeRequests'] == v['places'] == 1 and v['cancels'] == 0
    fill = one(v['fills']); wire = v['order']
    assert o['client_order_id'] == wire['clOrdId'] == e['key_value']
    assert o['exchange_order_id'] == wire['ordId'] == fill['ordId'] == t['exchange_order_id']
    assert fill['tradeId'] == t['exchange_trade_id']
    assert dec(o['qty']) == dec(t['qty']) == dec(wire['sz']) == dec(fill['fillSz']) == dec(wire['accFillSz']) == 10
    assert dec(o['price']) == dec(t['price']) == dec(wire['px']) == dec(fill['fillPx']) == 100
    assert dec(t['fee']) == abs(dec(fill['fee'])) == Decimal('.01')
    assert t['fee_currency'] == fill['feeCcy'] == 'USDT'
    assert t['trade_env'] == 'SIM' and t['account_id'] == o['account_id']
    payload = e['payload_json']['payload']
    for key in ['order_id', 'trade_id', 'qty', 'price', 'fee', 'fee_currency', 'exchange_trade_id', 'trade_env', 'account_id']:
        assert payload[key] == t[key]
    assert payload['client_order_id'] == o['client_order_id']
    assert e['event_id'] == e['payload_json']['event_id'] and e['event_type'] == 'TradeExecuted'
    expected = {t['trade_id'] + ':LEDGER:' + suffix: value for suffix, value in
                [('1', Decimal('-1000')), ('2', Decimal('1000')), ('FEE_1', Decimal('-.01')), ('FEE_2', Decimal('.01'))]}
    assert len(f['ledger_entries']) == 4 and len({x['entry_id'] for x in f['ledger_entries']}) == 4
    for x in f['ledger_entries']:
        amount = expected.pop(x['idempotency_key'])
        assert dec(x['delta']) == amount and x['currency'] == 'USDT'
        assert x['direction'] == ('DEBIT' if amount < 0 else 'CREDIT')
        assert x['ref_id'] == t['trade_id'] and x['ref_type'] == 'TRADE' and x['account_id'] == o['account_id']
    assert not expected
    if d['scenario'] != 'ORDINARY_RESTART':
        r = one(f['strategy_runs']); w = one(f['strategy_run_dispatch_work']); schedule = one(f['strategy_schedules'])
        assert r['status'] == 'SUCCEEDED'
        assert r['strategy_run_id'] == w['strategy_run_id'] == o['strategy_run_id'] == t['strategy_run_id']
        assert w['client_order_id'] == o['client_order_id']
        assert dec(w['quantity']) == Decimal('10.0005') and dec(w['price']) == Decimal('100.005')
        assert dec(w['effective_quantity']) == dec(o['qty']) and dec(w['effective_price']) == dec(o['price'])
        assert r['admission_schedule_id'] == schedule['schedule_job_id'] == 'b5'
        assert r['strategy_id'] == schedule['strategy_id'] == 'b5-strategy'
        assert r['account_id'] == schedule['account_id'] == w['account_id'] == o['account_id']
        assert sum(x['new_status'] == 'SUCCEEDED' for x in f['b5_run_transitions']) == 1
        before = d['atCut']['strategy_runs']
        if before:
            old = one(before)
            for key in ['strategy_run_id', 'admission_due_at', 'admission_schedule_id', 'request_id']:
                assert r[key] == old[key]
        else:
            assert d['scenario'] == 'PRE_ADMISSION_DEATH'
        after = one(d['afterSuccessors']['strategy_runs'])
        assert after['strategy_run_id'] == r['strategy_run_id']
        assert d['afterSuccessors']['strategy_schedules'] == f['strategy_schedules']
    else:
        assert not f['strategy_runs'] and not f['strategy_run_dispatch_work']
        assert one(d['atCut']['orders'])['order_id'] == o['order_id']


def main():
    paths = sorted(Path(sys.argv[1]).glob(sys.argv[2] if len(sys.argv) > 2 else '*/raw-proof.json'))
    assert len(paths) == 13
    for p in paths:
        verify(json.loads(p.read_text(encoding='utf-8-sig'), parse_float=Decimal))
    # 突变负例验证 oracle 会拒绝身份、重复账务与金额损坏；不修改原始输入。
    sample = json.loads(paths[0].read_text(encoding='utf-8-sig'), parse_float=Decimal)
    mutations = [lambda d: d['final']['orders'][0].update(client_order_id='wrong'),
                 lambda d: d['final']['ledger_entries'][0].update(delta=999),
                 lambda d: d['final']['tradeExecuted'].append(copy.deepcopy(d['final']['tradeExecuted'][0])),
                 lambda d: d['venue'].update(placeRequests=2),
                 lambda d: d['final']['strategy_runs'][0].update(strategy_run_id='wrong')]
    for mutate in mutations:
        d = copy.deepcopy(sample); mutate(d)
        # 同时更新 replay 副本，确保拒绝来自业务 oracle，而非仅快照不相等。
        d['afterReplay'] = copy.deepcopy(d['final'])
        try:
            verify(d)
        except (AssertionError, KeyError):
            continue
        raise AssertionError('mutated proof accepted')
    print(json.dumps({'proofs': len(paths), 'passed': len(paths), 'mutationNegativesRejected': len(mutations)}))


if __name__ == '__main__':
    main()
