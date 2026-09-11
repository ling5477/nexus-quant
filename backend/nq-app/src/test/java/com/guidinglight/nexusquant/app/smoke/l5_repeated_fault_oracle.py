"""复用既有业务重建，对故障时点、请求身份和恢复进度追加失败封闭检查。"""
import copy
import json
import sys
from pathlib import Path
from l5_measurement import verify, require


def verify_fault(proof):
    verify(proof)
    fault = proof['concurrent']['fault']
    if 'targetPlan' in proof['concurrent']:
        verify_target(proof)
    require(all(0 <= s['ownedNq'] <= 4 and 0 <= s['appConnections'] <= 40 for s in proof['progress']), 'owned resource accumulation')
    if fault['family']=='F3':
        require(proof['concurrent']['proxyRemaining']==0 and proof['concurrent']['proxyPortReleased'], 'proxy cleanup missing')
    if fault['family'] in ('F2', 'F3'):
        return verify_death(proof, fault)
    require(fault['family'] == 'F1', 'unsupported fault family')
    require(fault['mode'] in ('B1_ACCEPTED_TIMEOUT', 'B1_LOST_ACK'), 'unsupported venue fault')
    before, during = fault['before'], fault['during']
    start, end = fault['startMillis'], fault['endMillis']
    require(start <= before['timeMillis'] < during['timeMillis'] <= end, 'invalid fault interval')
    require(end - start >= 1800, 'real caller uncertainty not observed')
    width = proof['concurrency']
    clients = {'l5' + proof['database'][-20:] + f'{i:04d}' for i in range(fault['base'], fault['base'] + width)}
    venue = fault['venueDuring']
    events = venue['events']
    require({e['client'] for e in events if e['type'] == 'VENUE_ACCEPTED'} >= clients, 'fault acceptance missing')
    require(venue['places'] - venue['delivered'] == width, 'response withholding not proven')
    final_events = proof['venue']['events']
    for client in clients:
        accepted = next(e for e in final_events if e['type'] == 'VENUE_ACCEPTED' and e['client'] == client)
        require(any(e['type'] == 'QUERY_ORDER' and e['client'] == client and e['nanoTime'] > accepted['nanoTime'] for e in final_events), 'query-first recovery missing')
    if fault['mode'] == 'B1_LOST_ACK':
        require({e['client'] for e in events if e['type'] == 'ACK_GENERATED'} >= clients, 'lost ACK missing')
        require(any(e['type'] == 'ACK_DROPPED' for e in final_events), 'response closure missing')
    samples = proof['progress']
    require(any(s['timeMillis'] < start for s in samples) and any(start <= s['timeMillis'] <= end for s in samples), 'sampling gap around fault')
    require(any(s['timeMillis'] > end and s['trades'] > during['trades'] and s['terminal'] > during['terminal'] for s in samples), 'no business progress after fault')
    require(samples[-1]['backlog'] == 0, 'residual actionable backlog')
    return {'family':'F1', 'mode':fault['mode'], 'affectedOrders':width, 'queryFirst':True,
            'faultDurationSeconds':(end-start)/1000, 'finalActionable':0, 'businessOracle':'PASS'}


def verify_target(proof):
    plan = proof['concurrent']['targetPlan']
    f = proof['concurrent']['fault']
    require(plan['qualificationRunId'] == proof['database'], 'wrong qualification run')
    require(plan['client_order_id'] == 'l5' + proof['database'][-20:] + '0085', 'wrong logical order')
    require(plan['exchange_trade_id'] == 'b0-fill-b0-venue-85' and plan['trace_id'] == 'l5-trace-85', 'wrong planned fill')
    require(plan['expectedTradeKey'] == 'OKX / b0-fill-b0-venue-85', 'wrong derived trade key')
    require(plan['boundary'] == 'TRADE_AND_REQUIRED_EVENT_COMMITTED_BEFORE_LEDGER', 'wrong boundary')
    require(f['oldPid'] == plan['ownerPid'] and f['oldDead'] and f['oldPid'] != f['newPid'], 'wrong owner death')
    require(f['faultKills'] == 1, 'wrong fault kill count')
    require([h['state'] for h in f['handshake']] == ['ARMED', 'TARGET_MATCHED', 'DURABLE_BOUNDARY_CONFIRMED', 'FAULT_READY', 'FAULT_INJECTED', 'RECOVERY_RELEASED'], 'wrong handshake')
    times = [h['timeMillis'] for h in f['handshake']]
    require(times == sorted(times) and times[0] == plan['armedMillis'] < proof['concurrent']['consumerStartMillis'], 'late arming')
    require(f['gateStartMillis'] <= times[1] <= times[3] <= f['deathMillis'] <= times[4] <= f['gateEndMillis'] <= times[5], 'wrong gate timing')
    require(0 < f['gateEndMillis'] - f['gateStartMillis'] < 20000, 'unbounded exclusion gate')
    require(plan['beforeArm'] == plan['afterArm'] and f['gateBefore'] == f['gateAfter'] and f['atCut'] == f['barrierAfter'], 'business mutation by harness')
    facts = f['atCut']
    matches = [t for t in facts['trades'] if t['exchange_trade_id'] == plan['exchange_trade_id']]
    require(len(matches) == 1, 'target trade not uniquely durable')
    t = matches[0]
    require(t['trade_id'] == f['targetTradeId'] and t['order_id'] == f['targetOrderId'] and f['targetFillId'] == plan['exchange_trade_id'], 'wrong actual identity')
    require(any(o['order_id'] == t['order_id'] and o['client_order_id'] == plan['client_order_id'] for o in facts['orders']), 'wrong target order')
    require(sum(e['payload']['trade_id'] == t['trade_id'] for e in facts['events']) == 1, 'target required event absent')
    require(not any(e['ref_id'] == t['trade_id'] for e in facts['ledger_entries']), 'target ledger already applied')
    require(sum(e['ref_id'] == t['trade_id'] for e in proof['facts']['ledger_entries']) == 4, 'target recovery not converged')
    require(proof['concurrent']['targetCleanup'] == {'ownedNq':0, 'ownedVenue':0, 'ownedPg':0}, 'target cleanup missing')


def verify_death(proof, fault):
    require(fault['oldDead'] and fault['oldPid'] != fault['newPid'], 'owned replacement missing')
    require(fault['startMillis'] < fault['deathMillis'] <= fault['newStartMillis'] <= fault['endMillis'], 'invalid death interval')
    before, during = fault['before'], fault['during']
    require(before['timeMillis'] < during['timeMillis'] <= fault['deathMillis'], 'missing cut sampling')
    facts = fault['atCut']
    require(facts['orders'] and facts['ordinary_place_authorities'], 'missing durable cut')
    if fault['family'] == 'F2':
        require(fault['mode'] in ('AFTER_VENUE_ACCEPTED','AFTER_TRADE_COMMIT','V51_B'), 'unknown process boundary')
        if fault['mode'] == 'AFTER_VENUE_ACCEPTED':
            client = 'l5' + proof['database'][-20:] + '0085'
            order = next(o for o in facts['orders'] if o['client_order_id'] == client)
            authority = next(a for a in facts['ordinary_place_authorities'] if a['order_id'] == order['order_id'])
            require(authority['state'] == 'MAY_HAVE_ESCAPED', 'external ambiguity not durable')
            require(any(e['type'] == 'ACK_GENERATED' and e['client'] == client for e in fault['venueDuring']['events']), 'lost response boundary absent')
        elif fault['mode'] == 'AFTER_TRADE_COMMIT':
            require(facts['trades'] and facts['events'], 'trade commit not durable')
        else:
            require(facts['strategy_run_dispatch_work'] and facts['strategy_runs'], 'durable strategy preparation absent')
    else:
        require(fault['mode'] in ('BEFORE_PAUSE','AFTER_PAUSE'), 'unsupported database cut')
        require(fault['target'] in ('ACK','LEDGER'), 'unknown transaction target')
        boundary = 'COMMIT_NOT_FORWARDED' if fault['mode']=='BEFORE_PAUSE' else 'SERVER_COMMIT_CONFIRMED_RESPONSE_WITHHELD'
        require(fault['wireBoundary'].endswith(boundary), 'wire outcome not proven')
        if fault['target']=='ACK':
            client = 'l5' + proof['database'][-20:] + '0085'
            order = next(o for o in facts['orders'] if o['client_order_id']==client)
            require(order['status']==('SENT' if fault['mode']=='BEFORE_PAUSE' else 'ACCEPTED'), 'durable commit outcome mismatch')
        else:
            require(facts['ledger_entries'], 'ledger commit not durable')
    samples = proof['progress']
    require(any(s['timeMillis'] < fault['startMillis'] for s in samples), 'no pre-fault measurement')
    require(any(fault['startMillis'] <= s['timeMillis'] <= fault['endMillis'] for s in samples), 'no fault measurement')
    require(any(s['timeMillis'] > fault['endMillis'] and s['trades']>during['trades'] and s['orders']>during['orders'] for s in samples), 'no recovered forward progress')
    require(samples[-1]['backlog']==0, 'residual backlog')
    return {'family':fault['family'],'mode':fault['mode'],'newPidVerified':True,'finalActionable':0,'businessOracle':'PASS'}


def mutate_cut(proof, field, value):
    # 同步两个快照，迫使负例触达精确边界检查，而非仅被快照差异拒绝。
    for snapshot in ('atCut', 'barrierAfter'):
        proof['concurrent']['fault'][snapshot][field] = copy.deepcopy(value)


def fault_negatives(proof):
    mutations = {
        'missing_fault': lambda p: p['concurrent'].pop('fault'),
        'resource_accumulation': lambda p: p['progress'][-1].__setitem__('appConnections', 41),
        'owned_limit': lambda p: p['progress'][0].__setitem__('ownedNq', 5),
        'forward_progress_loss': lambda p: p['concurrent']['fault'].__setitem__('endMillis', p['progress'][-1]['timeMillis']+1),
        'residual_backlog': lambda p: p.__setitem__('finalBacklog', 1),
        'duplicate_mutation': lambda p: p['venue'].__setitem__('places', p['venue']['places']+1),
    }
    if proof['concurrent']['fault']['family']!='F1':
        mutations['old_process_alive'] = lambda p: p['concurrent']['fault'].__setitem__('oldDead', False)
    if 'targetPlan' in proof['concurrent']:
        mutations.update({
            'wrong_owner': lambda p: p['concurrent']['targetPlan'].__setitem__('ownerPid', -1),
            'wrong_fill': lambda p: p['concurrent']['targetPlan'].__setitem__('exchange_trade_id', 'other'),
            'wrong_trade': lambda p: p['concurrent']['fault'].__setitem__('targetTradeId', 'other'),
            'missing_target_trade': lambda p: mutate_cut(p, 'trades', []),
            'missing_target_event': lambda p: mutate_cut(p, 'events', []),
            'ledger_before_kill': lambda p: mutate_cut(p, 'ledger_entries', [{'ref_id':p['concurrent']['fault']['targetTradeId']}]),
            'business_mutation': lambda p: p['concurrent']['fault']['barrierAfter'].__setitem__('orders', []),
            'gate_mutation': lambda p: p['concurrent']['fault']['gateAfter'].__setitem__('orders', []),
            'arming_mutation': lambda p: p['concurrent']['targetPlan']['afterArm'].__setitem__('orders', ['fabricated']),
            'handshake_reordered': lambda p: p['concurrent']['fault']['handshake'].reverse(),
            'late_arming': lambda p: p['concurrent']['targetPlan'].__setitem__('armedMillis', 9999999999999),
            'arbitrary_kill': lambda p: p['concurrent']['fault'].__setitem__('faultKills', 2),
            'residual_target_resource': lambda p: p['concurrent']['targetCleanup'].__setitem__('ownedNq', 1),
        })
    for name, mutation in mutations.items():
        changed = copy.deepcopy(proof)
        mutation(changed)
        try:
            verify_fault(changed)
        except (ValueError, KeyError):
            continue
        raise ValueError('fault oracle accepted mutation '+name)
    return list(mutations)


if __name__ == '__main__':
    proof = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
    result = verify_fault(proof)
    result['negativeRejections'] = fault_negatives(proof)
    print(json.dumps(result))
