"""并发证据的只读关系校验；沿用L5业务oracle与canonical exporter。"""
import copy
import json
from collections import Counter
from decimal import Decimal
from pathlib import Path


def need(condition, reason):
    if not condition:
        raise ValueError(reason)


def verify_concurrent(proof):
    c = proof['concurrent']
    actors = c['actors']
    killed_count = 1 if c.get('fault', {}).get('family') in ('F2', 'F3') else 0
    need(len(actors) == proof['concurrency'] + 1 + killed_count, 'actor identity missing')
    need(len({a['pid'] for a in actors}) == len(actors), 'duplicate actor identity')
    need(c['cursorStart'] == [], 'cursor not initially empty')
    need(c['cursorBeforeRestart'] == c['cursorAfterRestart'], 'restart lost cursor')
    need(c['oldPid'] != c['newPid'] and c['oldExitMillis'] <= c['newStartMillis'], 'restart identity invalid')
    need(c['cursorAfterStep'][0]['revision'] == c['cursorAfterRestart'][0]['revision'] + 1, 'restart cursor did not advance')
    rows = sorted(proof['facts']['orders'], key=lambda r: (r['created_at'], r['order_id']))
    rank = {r['order_id']: i for i, r in enumerate(rows)}
    reservations = sorted([r for a in actors for r in a['reservations']], key=lambda r: r['revision'])
    need([r['revision'] for r in reservations] == list(range(1, len(reservations) + 1)), 'cursor revision gap or duplication')
    previous = -1
    wraps = 0
    observed = Counter()
    for reservation in reservations:
        ids = [r['order_id'] for r in reservation['selected']]
        need(reservation['limit'] in (40, 100) and 0 < len(ids) <= reservation['limit'] and len(set(ids)) == len(ids), 'public total limit violated')
        need(all(i in rank for i in ids), 'unknown candidate')
        need(reservation['cursor_order_id'] == ids[-1], 'cursor not last reserved candidate')
        indices = [rank[i] for i in ids]
        need(indices == sorted(indices, key=lambda i: (i <= previous, i)), 'invalid circular order')
        wraps += sum(right <= left for left, right in zip([previous] + indices, indices))
        previous = indices[-1]
        observed.update(ids)
    need(set(observed) == set(rank) and wraps > 0, 'candidate starvation or no cursor wrap')
    need(Counter(r['limit'] for r in reservations)[100] == 1, 'final baseline replay budget mismatch')
    need(c['cursorFinal'][0]['revision'] == len(reservations) and
         c['cursorFinal'][0]['cursor_order_id'] == reservations[-1]['cursor_order_id'], 'final cursor mismatch')
    intervals = []
    for actor in actors:
        need(actor['calls'] and all('error' not in call for call in actor['calls']), 'failed reconciliation')
        for call in actor['calls']:
            begin = max(call['startMillis'], c['producerStartMillis'])
            end = min(call['endMillis'], c['producerStopMillis'])
            if begin < end:
                intervals.extend([(begin, 1), (end, -1)])
    active = peak = 0
    for _, delta in sorted(intervals):
        active += delta
        peak = max(peak, active)
    need(peak == proof['concurrency'], 'concurrent reconciliation during production not proven')
    old_ids = {r['order_id'] for r in rows[:60]}
    venues = {v['ordId']: v for v in proof['venue']['data']}
    need(all(int(venues[r['external_order_id']]['uTime']) >= c['producerStopMillis'] for r in rows[:60]),
         'persistent order fill timestamp predates actual release')
    fairness = c['fairnessFacts']['orders']
    need(len([r for r in fairness if r['order_id'] in old_ids and r['status'] != 'FILLED']) == 60,
         'persistent older candidates missing')
    new_filled = [r for r in fairness if r['order_id'] not in old_ids and r['status'] == 'FILLED']
    need(new_filled, 'new candidates starved behind persistent prefix')
    need(any(s['trades'] > 0 and s['orders'] < len(rows) for s in proof['progress']), 'no progress while new work arrives')
    need(all(1 <= r['version'] <= 5 for r in rows), 'invalid order version growth')
    if proof['level'] == 'C3':
        works = proof['facts']['strategy_run_dispatch_work']
        need(len(works) == 12, 'mixed strategy missing')
        need(all(Decimal(str(w['quantity'])) == Decimal('0.1005') and
                 Decimal(str(w['effective_quantity'])) == Decimal('0.1') for w in works), 'effective contract mismatch')
    return dict(reconciliationActors=proof['concurrency'], overlappingCallsDuringProducer=peak,
                initialBacklog=60, newCandidatesGenerated=len(rows) - 60, finalActionable=0,
                cursorStartingPosition=None, iterations=len(reservations), wrapCount=wraps,
                candidatesObserved=len(observed), duplicateObservations=sum(observed.values()) - len(observed),
                newerFilledWithOlderPersistent=len(new_filled), restartSafe=True,
                cursorFinal=dict(revision=c['cursorFinal'][0]['revision'], order_id=c['cursorFinal'][0]['cursor_order_id']),
                producerSeconds=(c['producerStopMillis'] - c['producerStartMillis']) / 1000,
                candidateObservations=[dict(order_id=i, observations=n) for i, n in observed.items()],
                effectiveStrategyOrders=12 if proof['level'] == 'C3' else 0)


def concurrent_negatives(proof):
    mutations = {
        'cursor_revision': lambda p: p['concurrent']['actors'][0]['reservations'][0].__setitem__('revision', 9999),
        'cursor_last_key': lambda p: p['concurrent']['actors'][0]['reservations'][0].__setitem__('cursor_order_id', 'unknown'),
        'total_limit': lambda p: p['concurrent']['actors'][0]['reservations'][0].__setitem__('limit', 80),
        'restart_identity': lambda p: p['concurrent'].__setitem__('newPid', p['concurrent']['oldPid']),
        'no_overlap': lambda p: p['concurrent'].__setitem__('producerStartMillis', p['concurrent']['producerStopMillis']),
        'actor_failure': lambda p: p['concurrent']['actors'][0]['calls'][0].__setitem__('error', 'failure'),
        'version_runaway': lambda p: p['facts']['orders'][0].__setitem__('version', 9999),
        'fairness_lost': lambda p: p['concurrent'].__setitem__('fairnessFacts', {'orders': []}),
    }
    if proof['level'] == 'C3':
        mutations['effective_mismatch'] = lambda p: p['facts']['strategy_run_dispatch_work'][0].__setitem__('effective_quantity', 999)
    for name, mutation in mutations.items():
        changed = copy.deepcopy(proof)
        mutation(changed)
        try:
            verify_concurrent(changed)
        except (ValueError, KeyError):
            continue
        raise ValueError('concurrent oracle accepted ' + name)
    return list(mutations)


def export_failure(path):
    """失败原始事实只生成明确FAIL的取证摘要，不走成功资格导出路径。"""
    from l5_measurement import validate_export_input, verify
    from synthetic_evidence import IdentityMapper, export
    proof = json.loads(path.read_text(encoding='utf-8'))
    validate_export_input(proof)
    try:
        verify(proof)
    except ValueError as error:
        need(str(error) == 'position lost or duplicated accounting', 'unexpected failure classification')
    else:
        raise ValueError('failure evidence must contain observed position loss')
    facts = proof['facts']
    orders = {r['order_id']: r for r in facts['orders']}
    mapper = IdentityMapper('B5-L5C', 1)
    links = [{'order_id': t['order_id'], 'trade_id': t['trade_id'], 'exchange_trade_id': t['exchange_trade_id'],
              'qty': t['qty'], 'side': orders[t['order_id']]['side'], 'fee_currency': t['fee_currency']} for t in facts['trades']]
    cursor = verify_concurrent(proof)
    canonical = export({'trades': links, 'cursor': cursor}, mapper)
    text = json.dumps(canonical)
    need(all(raw not in text for _, raw in mapper.identities), 'raw identity leaked')
    snapshots = sorted((s for s in facts['account_snapshots'] if s['currency'] == 'BTC'), key=lambda s: (s['ts'], s['snapshot_id']))
    expected = sum(Decimal(str(t['qty'])) for t in facts['trades'])
    actual = Decimal(str(facts['positions'][0]['qty']))
    result = dict(result='FAIL', finding='P1_L5_CONCURRENT_POSITION_LOST_UPDATE',
        level=proof['level'], businessCounts={k: len(v) for k, v in facts.items()},
        expectedPosition=str(expected), actualPosition=str(actual), missingQuantity=str(expected - actual),
        latestBtcSnapshot=str(snapshots[-1]['balance']),
        orderStatuses=dict(Counter(r['status'] for r in facts['orders'])),
        orderVersions=dict(Counter(r['version'] for r in facts['orders'])),
        venuePlaces=proof['venue']['places'], initialBacklog=proof['initialBacklog'],
        peakBacklog=max(s['backlog'] for s in proof['progress']), finalBacklog=proof['finalBacklog'],
        finalUnresolved=proof['correctnessRequiredUnresolved'], elapsedSeconds=proof['elapsedSeconds'],
        drainSeconds=proof['drainSeconds'], cleanup=proof['cleanup'],
        progressSamples=len(proof['progress']), resourceSamples=len(proof['resources']),
        portsReleased=proof['venuePortReleased'] and proof['databasePortReleased'],
        concurrent=canonical['cursor'], trades=canonical['trades'])
    result['jvms'] = []
    for index, pid in enumerate(sorted({r['pid'] for r in proof['resources']})):
        rows = [r for r in proof['resources'] if r['pid'] == pid]
        result['jvms'].append(dict(identity='JVM:' + str(index + 1), samples=len(rows),
            poolMax=rows[-1]['max'], activePeak=max(r['active'] for r in rows), waitingPeak=max(r['waiting'] for r in rows),
            activeFinal=rows[-1]['active'], idleFinal=rows[-1]['idle'], waitingFinal=rows[-1]['waiting'],
            threadsInitial=rows[0]['threads'], threadsPeak=max(r['threads'] for r in rows), threadsFinal=rows[-1]['threads'],
            queueInitial=rows[0]['commandQueue'], queuePeak=max(r['commandQueue'] for r in rows), queueFinal=rows[-1]['commandQueue']))
    path.with_name('failure-summary.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('FAILURE_EVIDENCE_EXPORTED / NOT_QUALIFIED')
