"""只读同一DB快照与owned Venue事实；完整链为关系集合基数，不累加重放次数。"""
import json
import sys
from collections import Counter
from decimal import Decimal
from pathlib import Path


def count(proof):
    f, venue = proof['facts'], proof['venue']
    for table, key in [('orders', 'order_id'), ('trades', 'trade_id'), ('events', 'event_id'),
                       ('ledger_entries', 'entry_id'), ('ledger_entries', 'idempotency_key')]:
        ids = [row[key] for row in f[table]]
        if len(ids) != len(set(ids)):
            raise ValueError('duplicate durable identity: ' + table)
    if len(f['orders']) > 3000:
        raise ValueError('bounded storage counter input exceeded')
    number = lambda row, key: Decimal(str(row[key]))
    requests = Counter(e['client'] for e in venue['events'] if e['type'] == 'REQUEST_RECEIVED')
    if any(n > 1 for n in requests.values()):
        raise ValueError('duplicate venue mutation')
    completed = []
    ledger_complete = []
    for o in f['orders']:
        trades = [t for t in f['trades'] if t['order_id'] == o['order_id']]
        if len(trades) > 1:
            raise ValueError('duplicate Trade for single-fill fixture')
        if not trades:
            continue
        t = trades[0]
        events = [e for e in f['events'] if e['payload']['trade_id'] == t['trade_id']]
        entries = [e for e in f['ledger_entries'] if e['ref_id'] == t['trade_id']]
        if len(events) > 1 or len(entries) > 4:
            raise ValueError('duplicate event/accounting')
        if len(entries) != 4:
            continue
        if sorted(number(e, 'delta') for e in entries) != list(map(Decimal, ['-10', '-0.01', '0.01', '10'])):
            raise ValueError('ledger amount mismatch')
        if not all(e['account_id'] == o['account_id'] and e['currency'] == 'USDT' for e in entries):
            raise ValueError('ledger ownership mismatch')
        if sorted(number(e, 'balance_after') for e in entries) != list(map(Decimal, ['-10', '-0.01', '0', '0'])):
            raise ValueError('ledger balance mismatch')
        entry_ids = {e['entry_id'] for e in entries}
        if Counter(e['entry_id'] for e in f['ledger_events'] if e['entry_id'] in entry_ids) != Counter({key: 1 for key in entry_ids}):
            continue
        ledger_complete.append(o)
        matches = [v for v in venue['data'] if v['clOrdId'] == o['client_order_id']]
        if len(matches) != 1 or len(events) != 1 or o['status'] != 'FILLED':
            continue
        v, e = matches[0], events[0]['payload']
        if not (v['state'] == 'filled' and v['ordId'] == o['external_order_id'] == t['external_order_id']
                and t['exchange_trade_id'] == 'b0-fill-' + v['ordId']
                and number(v, 'accFillSz') == number(o, 'qty') == number(t, 'qty') == Decimal('0.1')
                and number(t, 'price') == Decimal('100') and number(t, 'fee') == Decimal('0.01')
                and t['trade_env'] == o['trade_env'] == e['trade_env'] == 'SIM'
                and t['account_id'] == o['account_id'] == e['account_id']
                and t['symbol'] == o['symbol'] == e['symbol'] == 'BTC-USDT'
                and e['external_order_id'] == v['ordId'] and e['client_order_id'] == o['client_order_id']
                and e['fee_currency'] == t['fee_currency'] == 'USDT'
                and e['order_id'] == o['order_id'] and e['exchange_trade_id'] == t['exchange_trade_id']
                and all(number(e, k) == number(t, k) for k in ('qty', 'price', 'fee'))
                and requests[o['client_order_id']] == 1):
            raise ValueError('canonical fill/event lineage mismatch')
        runs = [r for r in f['strategy_runs'] if r['strategy_run_id'] == o['strategy_run_id'] and r['status'] == 'SUCCEEDED']
        work = [w for w in f['strategy_run_dispatch_work'] if w['strategy_run_id'] == o['strategy_run_id']
                and w['client_order_id'] == o['client_order_id'] and number(w, 'effective_quantity') == number(o, 'qty')]
        authority = [a for a in f['ordinary_place_authorities'] if a['order_id'] == o['order_id'] and a['state'] == 'MAY_HAVE_ESCAPED']
        if len(runs) == len(work) == len(authority) == 1:
            completed.append(o)
    # Position/Snapshot是账户聚合；按所有已入账链重建，不把Trade数量当投影证明。
    accounts = {o['account_id'] for o in ledger_complete}
    proven_accounts = set()
    for account in accounts:
        n = sum(o['account_id'] == account for o in ledger_complete)
        positions = [p for p in f['positions'] if p['account_id'] == account]
        snapshots = [s for s in f['account_snapshots'] if s['account_id'] == account]
        if (len(positions) != 1 or positions[0]['symbol'] != 'BTC-USDT'
                or number(positions[0], 'avg_price') != Decimal('100')
                or number(positions[0], 'available_qty') != Decimal(n) / 10
                or number(positions[0], 'frozen_qty') != 0
                or number(positions[0], 'qty') != Decimal(n) / 10 or len(snapshots) != 2 * n):
            continue
        valid = True
        for currency, expected in [('BTC', Decimal(n) / 10), ('USDT', Decimal(0))]:
            rows = [s for s in snapshots if s['currency'] == currency]
            latest = max(rows, key=lambda s: s['snapshot_id']) if rows else None
            valid &= bool(latest) and len(rows) == n and all(number(latest, key) == expected for key in ('balance', 'available')) and number(latest, 'frozen') == 0
        if valid:
            proven_accounts.add(account)
    ids = sorted(o['order_id'] for o in completed if o['account_id'] in proven_accounts)
    return {'orders': len(f['orders']), 'terminalOrders': sum(o['status'] == 'FILLED' for o in f['orders']),
            'fills': sum(v['state'] == 'filled' for v in venue['data']), 'trades': len(f['trades']),
            'TradeExecuted': len(f['events']), 'ledgerEntries': len(f['ledger_entries']),
            'fullChainCompleted': len(ids), 'fullChainOrderIds': ids}


if __name__ == '__main__':
    path = Path(sys.argv[1])
    if path.stat().st_size > 32 * 1024 * 1024:
        raise ValueError('bounded storage counter file exceeded')
    print(json.dumps(count(json.loads(path.read_text(encoding='utf-8')))))
