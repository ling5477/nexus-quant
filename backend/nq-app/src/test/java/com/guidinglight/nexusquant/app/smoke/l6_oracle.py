"""L6 按完整快照复用 L5 关系不变量；不创建或修改业务事实。"""
import json, sys
from pathlib import Path
from collections import Counter
from decimal import Decimal
from l5_measurement import validate_export_input, require, unique

def verify(proof):
    validate_export_input(proof)
    require(set(proof)=={'facts','venue','expectedStrategyRuns'}, 'unknown checkpoint schema')
    facts=proof['facts'];expected=proof['expectedStrategyRuns']
    require(0 < expected <= 240, 'L6 finite order budget')
    orders = unique(facts['orders'], 'order_id')
    trades = unique(facts['trades'], 'trade_id')
    authorities = unique(facts['ordinary_place_authorities'], 'order_id')
    entries = unique(facts['ledger_entries'], 'entry_id')
    unique(facts['ledger_entries'], 'idempotency_key')
    unique(facts['events'], 'event_id')
    runs = unique(facts['strategy_runs'], 'strategy_run_id')
    work = unique(facts['strategy_run_dispatch_work'], 'strategy_run_id')
    venue = proof['venue']
    venue_orders = unique(venue['data'], 'clOrdId')
    require(len(orders) == expected == len(trades) == len(authorities) == len(venue_orders), 'lost logical work')
    require(len(runs) == proof['expectedStrategyRuns'] == len(work), 'strategy work missing')
    require(venue['places'] == expected and venue['dropped'] == venue['delayed'] == 0, 'duplicate mutation or injected fault')
    requests = Counter(e['client'] for e in venue['events'] if e['type'] == 'REQUEST_RECEIVED')
    require(all(requests[key] == 1 for key in venue_orders), 'per identity duplicate PLACE')
    require(not any(e['type'].startswith('CANCEL') for e in venue['events']), 'unexpected cancel')
    events = Counter(e['payload']['trade_id'] for e in facts['events'])
    logical = set()
    expected_clients = {'' for i in range(0)}
    require({o['client_order_id'] for o in orders.values() if not o.get('strategy_run_id')} == expected_clients,
            'logical command identity set mismatch')
    result = []
    for order in orders.values():
        oid = order['order_id']
        require(order['status'] == 'FILLED' and order['trade_env'] == 'SIM' and order['version'] >= 1, 'order not terminal')
        require(authorities[oid]['state'] == 'MAY_HAVE_ESCAPED', 'V49 lineage')
        vo = venue_orders[order['client_order_id']]
        require(vo['state'] == 'filled' and vo['ordId'] == order['external_order_id'], 'venue identity')
        require(Decimal(vo['accFillSz']) == Decimal(str(order['qty'])) == Decimal('0.1'), 'fill quantity')
        linked = [t for t in trades.values() if t['order_id'] == oid]
        require(len(linked) == 1, 'duplicate or missing Trade')
        trade = linked[0]
        require(trade['external_order_id'] == vo['ordId'] and trade['exchange_trade_id'] == 'b0-fill-' + vo['ordId'], 'fill lineage')
        require(trade['trade_env'] == 'SIM' and trade['account_id'] == order['account_id'], 'trade environment/account')
        require(Decimal(str(trade['qty'])) == Decimal('0.1') and Decimal(str(trade['price'])) == Decimal('100')
                and Decimal(str(trade['fee'])) == Decimal('0.01'), 'trade amount')
        tid = trade['trade_id']
        require(events[tid] == 1, 'duplicate or missing TradeExecuted')
        event = next(e for e in facts['events'] if e['payload']['trade_id'] == tid)
        require(event['payload']['order_id'] == oid and event['payload']['exchange_trade_id'] == trade['exchange_trade_id'], 'event lineage')
        for field in ('qty', 'price', 'fee'):
            require(Decimal(str(event['payload'][field])) == Decimal(str(trade[field])), 'event payload amount')
        ledger = [e for e in entries.values() if e['ref_id'] == tid]
        require(len(ledger) == 4, 'duplicate or missing ledger')
        require(sorted(Decimal(str(e['delta'])) for e in ledger) == list(map(Decimal, ['-10', '-0.01', '0.01', '10'])), 'ledger amount')
        require(all(e['account_id'] == order['account_id'] and e['currency'] == 'USDT' for e in ledger), 'ledger identity')
        require(sorted(Decimal(str(e['balance_after'])) for e in ledger) == list(map(Decimal, ['-10', '-0.01', '0', '0'])), 'ledger balance chain')
        run_id = order.get('strategy_run_id')
        if run_id:
            run = runs[run_id]
            require(run['status'] == 'SUCCEEDED' and run_id in work, 'durable orphan')
            require(work[run_id]['client_order_id'] == order['client_order_id']
                    and Decimal(str(work[run_id]['effective_quantity'])) == Decimal(str(order['qty'])), 'durable work binding')
            key = (run['admission_schedule_id'], run['admission_due_at'])
            require(key not in logical, 'duplicate window')
            logical.add(key)
        result.append({'order_id': oid, 'client_order_id': order['client_order_id'], 'account_id': order['account_id'],
                       'strategy_run_id': run_id, 'strategy_id': runs[run_id]['strategy_id'] if run_id else None,
                       'strategyWindow': runs[run_id]['admission_due_at'] if run_id else None,
                       'status': order['status'], 'version': order['version'], 'authority': authorities[oid]['state'],
                       'external_order_id': vo['ordId'], 'exchange_trade_id': trade['exchange_trade_id'],
                       'trade_id': tid, 'event_id': event['event_id'],
                       'ledger': [{'entry_id': e['entry_id'], 'delta': e['delta']} for e in ledger],
                       'placeCount': 1, 'cancelCount': 0, 'backlog': 0})
    require(len(facts['events']) == expected and len(entries) == expected * 4, 'extra accounting')
    require(Counter(e['entry_id'] for e in facts['ledger_events']) == Counter({key: 1 for key in entries}), 'ledger event fanout')
    require(len(facts['positions']) == 1 and Decimal(str(facts['positions'][0]['qty'])) == Decimal(expected) / 10,
            'position lost or duplicated accounting')
    require(len(facts['account_snapshots']) == 2 * expected, 'account snapshot fanout')
    for currency, balance in [('BTC', Decimal(expected) / 10), ('USDT', Decimal(0))]:
        snapshots = [s for s in facts['account_snapshots'] if s['currency'] == currency]
        latest = max(snapshots, key=lambda s: s['snapshot_id'])
        require(Decimal(str(latest['balance'])) == balance, 'account balance inconsistent')
    require(len({(r['admission_schedule_id'],r['admission_due_at']) for r in runs.values()})==expected, 'duplicate schedule window')
    require(set(r['strategy_id'] for r in runs.values())=={'l6-strategy-1','l6-strategy-2'}, 'missing strategy')
    require(all(Decimal(str(w['quantity'])) != Decimal(str(w['effective_quantity'])) for w in work.values()), 'effective coverage missing')
    return {'orders':expected,'trades':len(trades),'ledger':len(entries),'position':str(Decimal(expected)/10),'duplicates':0,'orphans':0}

if __name__=='__main__':
    print(json.dumps(verify(json.loads(Path(sys.argv[1]).read_text(encoding='utf-8-sig')))))
