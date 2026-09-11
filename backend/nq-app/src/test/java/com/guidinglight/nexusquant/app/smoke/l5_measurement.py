"""独立读取业务与Venue事实；负例仅修改副本，绝不写回业务数据库。"""
import copy
import json
import sys
from collections import Counter
from decimal import Decimal
from pathlib import Path
from synthetic_evidence import IdentityMapper, export, protected

ROOT_FIELDS = set('cleanup concurrency controllerPid correctnessRequiredUnresolved database databasePortReleased '
                  'drainSeconds elapsedSeconds expectedOrders expectedStrategyRuns facts finalBacklog initialBacklog '
                  'level observations ownedNqRemaining pgAppActive progress rawBytesBeforeProof resources result '
                  'scanIterations venue venuePid venuePortReleased'.split())


def validate_export_input(value):
    """聚合前拒绝凭证字段；不能通过选择输出字段把秘密或未知根字段静默丢掉。"""
    if isinstance(value, dict):
        for key, child in value.items():
            require(not protected(key), 'sensitive field is not qualification evidence')
            validate_export_input(child)
    elif isinstance(value, list):
        for child in value:
            validate_export_input(child)


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def unique(rows, field):
    values = [row[field] for row in rows]
    require(len(values) == len(set(values)), 'duplicate ' + field)
    return {row[field]: row for row in rows}


def verify(proof):
    validate_export_input(proof)
    kill = proof.get('level') == 'K1'
    concurrent = proof.get('level', '').startswith('C') or kill
    require(set(proof) == ROOT_FIELDS | ({'concurrent'} if concurrent else set()) and proof['result'] == 'MEASURED', 'incomplete or unknown evidence schema')
    facts = proof['facts']
    if kill:
        from l5_kill_oracle import verify_kill_contract
        # 拒绝身份先经完整原始事实检查，再单独验证已接受工作；不静默过滤异常订单。
        facts = verify_kill_contract(proof)
    expected = proof['expectedOrders'] + proof['expectedStrategyRuns']
    require((proof['level'], proof['concurrency'], proof['expectedOrders'], proof['expectedStrategyRuns']) in
            [('S1', 1, 120, 0), ('S2', 2, 120, 0), ('S3', 4, 240, 12),
             ('C1', 2, 120, 0), ('C2', 4, 240, 0), ('C3', 4, 228, 12), ('K1', 2, 120, 1)], 'unapproved scale')
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
    expected_clients = {'l5' + proof['database'][-20:] + f'{i:04d}' for i in range(1, proof['expectedOrders'] + 1)}
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
    if runs:
        require(sorted(Counter(r['strategy_id'] for r in runs.values()).values()) == ([1] if kill else [3] * 4), 'strategy dimensions')
    require(proof['initialBacklog'] == (60 if concurrent else proof['expectedOrders']) and proof['finalBacklog'] == 0, 'backlog not converged')
    require(proof['correctnessRequiredUnresolved'] == 0, 'unresolved external result')
    require(proof['cleanup'] and proof['ownedNqRemaining'] == 0 and proof['venuePortReleased']
            and proof['databasePortReleased'], 'cleanup incomplete')
    progress = proof['progress']
    require(len(progress) >= 3 and any(0 < s['orders'] < proof['expectedOrders'] for s in progress), 'no continuous measurement')
    require(any(s['trades'] > 0 for s in progress), 'no observed progress')
    require(progress[-1]['backlog'] == progress[-1]['correctnessRequiredUnresolved'] == progress[-1]['idleInTransaction'] == 0, 'final progress not quiescent')
    require(progress[-1]['transactions'] - progress[0]['transactions'] < 100000, 'transaction safety budget')
    require(all(0 <= s['venueExecutor']['queue'] <= 16 and 0 <= s['venueExecutor']['active'] <= 4
                and s['venueExecutor']['rejected'] == 0 for s in progress), 'venue executor loss or runaway')
    resources = proof['resources']
    pids = {r['pid'] for r in resources}
    fault = proof.get('concurrent', {}).get('fault', {})
    killed = {fault['oldPid']} if fault.get('family') in ('F2', 'F3') else set()
    require(not killed or (fault['oldDead'] and fault['oldPid'] != fault['newPid'] and fault['deathMillis'] <= fault['newStartMillis']), 'unproven process death')
    require(len(pids) == proof['concurrency'] + (1 if concurrent and not kill else 0) + len(killed), 'missing JVM measurement')
    for pid in pids:
        rows = [r for r in resources if r['pid'] == pid]
        require(len(rows) >= 3, 'insufficient samples')
        require(all(0 <= r['active'] <= r['max'] and 0 <= r['total'] <= r['max'] and 0 <= r['commandQueue'] <= (1 if concurrent else 0)
                    and 0 <= r['activeCommands'] <= 1 for r in rows), 'resource limit')
        if pid not in killed:
            require(rows[-1]['active'] == rows[-1]['waiting'] == rows[-1]['activeCommands'] == 0, 'pool not quiescent')
    if concurrent and not kill:
        from l5_concurrent_oracle import verify_concurrent
        verify_concurrent(proof)
    return result


def mutation_negatives(proof):
    mutations = {
        'duplicate_place': lambda p: p['venue'].__setitem__('places', p['venue']['places'] + 1),
        'duplicate_trade': lambda p: p['facts']['trades'].append(copy.deepcopy(p['facts']['trades'][0])),
        'missing_event': lambda p: p['facts']['events'].pop(),
        'ledger_amount': lambda p: p['facts']['ledger_entries'][0].__setitem__('delta', 999),
        'lost_order': lambda p: p['facts']['orders'].pop(),
        'lineage': lambda p: p['facts']['ordinary_place_authorities'][0].__setitem__('state', 'NOT_ARMED'),
        'backlog': lambda p: p.__setitem__('finalBacklog', 1),
        'measurement_gap': lambda p: p.__setitem__('resources', []),
        'cleanup': lambda p: p.__setitem__('cleanup', False),
        'unapproved_scale': lambda p: p.__setitem__('concurrency', 8),
        'event_payload': lambda p: p['facts']['events'][0]['payload'].__setitem__('qty', 999),
        'balance': lambda p: p['facts']['ledger_entries'][0].__setitem__('balance_after', 999),
        'position': lambda p: p['facts']['positions'][0].__setitem__('qty', 999),
        'ledger_event': lambda p: p['facts']['ledger_events'].pop(),
        # 强杀进程已经不存在；连接池未释放反例必须修改正常收尾JVM的最后采样。
        'pool_not_released': lambda p: next(r for r in reversed(p['resources'])
            if r['pid'] != p.get('concurrent', {}).get('fault', {}).get('oldPid')).__setitem__('active', 1),
        'venue_rejection': lambda p: p['progress'][-1]['venueExecutor'].__setitem__('rejected', 1),
        'missing_progress': lambda p: p.__setitem__('progress', []),
    }
    if proof['expectedStrategyRuns']:
        mutations['orphan_run'] = lambda p: p['facts']['strategy_runs'][0].__setitem__('status', 'RUNNING')
        if proof['expectedStrategyRuns'] > 1:
            mutations['duplicate_window'] = lambda p: p['facts']['strategy_runs'][0].update(
                {k: p['facts']['strategy_runs'][1][k] for k in ('admission_schedule_id', 'admission_due_at')})
    for name, mutation in mutations.items():
        changed = copy.deepcopy(proof)
        mutation(changed)
        try:
            verify(changed)
        except (ValueError, KeyError):
            continue
        raise ValueError('oracle accepted mutation: ' + name)
    return list(mutations)


def main(path):
    proof = json.loads(path.read_text(encoding='utf-8'))
    rows = verify(proof)
    negatives = mutation_negatives(proof)
    identity = IdentityMapper('B5-L5K' if proof['level'] == 'K1' else 'B5-L5C' if proof['level'].startswith('C') else 'B5-L5', int(proof['level'][1:]))
    canonical = export({'rows': rows}, identity)['rows']
    # 验证导出双射与原始身份零泄漏；不用统一遮盖代替join关系。
    text = json.dumps(canonical)
    require(all(raw not in text for (_, raw) in identity.identities), 'raw identity escaped')
    require(len(set(identity.identities.values())) == len(identity.identities), 'identity aliasing')
    samples = proof['resources']
    summary = {k: proof[k] for k in ('level', 'concurrency', 'expectedOrders', 'expectedStrategyRuns',
               'initialBacklog', 'finalBacklog', 'scanIterations', 'drainSeconds', 'elapsedSeconds', 'cleanup')}
    summary.update(result='PASS', oracleMutationNegatives=negatives, rows=canonical,
                   peakBacklog=max([s['backlog'] for s in proof['progress']] + [proof['initialBacklog']]),
                   progressSamples=len(proof['progress']), resourceSamples=len(samples),
                   poolMax=max(s['max'] for s in samples), activePeak=max(s['active'] for s in samples),
                   waitingPeak=max(s['waiting'] for s in samples), threadsPeak=max(s['threads'] for s in samples))
    summary['correctnessRequiredUnresolved'] = proof['correctnessRequiredUnresolved']
    summary['businessCounts'] = {key: len(value) for key, value in proof['facts'].items()}
    summary['processed'] = len(rows)
    summary['jvms'] = [{'identity': f'JVM:{index + 1}', 'samples': len(rs), 'activePeak': max(r['active'] for r in rs),
                         'totalPeak': max(r['total'] for r in rs), 'poolMax': rs[-1]['max'],
                         'waitingPeak': max(r['waiting'] for r in rs), 'activeFinal': rs[-1]['active'],
                         'waitingFinal': rs[-1]['waiting'], 'threadsInitial': rs[0]['threads'],
                         'threadsFinal': rs[-1]['threads'], 'threadsPeak': max(r['threads'] for r in rs),
                         'commandsCompleted': rs[-1]['completedCommands']}
                        for index, pid in enumerate(sorted({r['pid'] for r in samples}))
                        for rs in [[r for r in samples if r['pid'] == pid]]]
    summary['progressCheckpoints'] = [proof['progress'][i] for i in sorted(set([0, len(proof['progress']) // 4,
        len(proof['progress']) // 2, 3 * len(proof['progress']) // 4, len(proof['progress']) - 1]))]
    summary['venueExecutor'] = {k: proof['venue'][k] for k in ('executorActive', 'executorQueue', 'executorCompleted', 'executorRejected', 'executorQueueCapacity')}
    summary['transactionDelta'] = proof['progress'][-1]['transactions'] - proof['progress'][0]['transactions']
    summary['resourcesReleased'] = {'nq': 0, 'venue': 0, 'ownedContainers': 0,
                                    'venuePortReleased': proof['venuePortReleased'], 'databasePortReleased': proof['databasePortReleased']}
    if proof['level'].startswith('C'):
        from l5_concurrent_oracle import verify_concurrent, concurrent_negatives
        summary['concurrent'] = export(verify_concurrent(proof), identity)
        summary['concurrentMutationNegatives'] = concurrent_negatives(proof)
    if proof['level'] == 'K1':
        from l5_kill_oracle import compact, kill_negatives
        summary['kill'] = compact(proof, identity)
        summary['killMutationNegatives'] = kill_negatives(proof)
    path.with_name('summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print('L5_ORACLE_PASS ' + proof['level'], flush=True)


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == '--failure':
        from l5_concurrent_oracle import export_failure
        export_failure(Path(sys.argv[2]))
    else:
        main(Path(sys.argv[1]))
