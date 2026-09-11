"""Kill 时序与拒绝身份逐项校验；既有 L5 oracle 继续证明完整已接受账务。"""
import copy
from decimal import Decimal
from datetime import datetime
from l5_measurement import require, unique
from synthetic_evidence import export


def verify_kill_contract(p):
    c = p['concurrent']; k = c['kill']; f = p['facts']
    require(set(c) == {'kill', 'cursorStart', 'cursorFinal', 'producerStartMillis', 'producerStopMillis', 'actors'}, 'unknown Kill orchestration')
    initial, durable, final = k['initialState'], k['durableState'], k['finalState']
    require(initial['status'] == 'DISENGAGED' and durable['status'] == 'ENGAGED' and final == durable, 'Kill state drift')
    require(durable['version'] == initial['version'] + 1 and durable['source'] == 'OPERATOR_ENGAGE'
            and durable['reason_code'] == 'L5_LOAD_STOP' and durable['trace_id'] == 'l5-kill-transition', 'noncanonical transition')
    require(k['controlResult'] == 'L5_KILL ENGAGED ' + str(durable['version']), 'wrong control result')
    require(k['lastAcceptedResponseMillis'] <= k['beforeSample']['timeMillis'] <= k['requestMillis']
            <= k['responseMillis'] <= k['durableReadMillis'] < k['releaseExistingFillsMillis'], 'ambiguous admission timeline')
    updated = datetime.fromisoformat(durable['updated_at']).timestamp() * 1000
    require(k['requestMillis'] <= updated <= k['responseMillis'] + 1, 'transition timestamp outside canonical call')
    events = k['events']
    # V35 的安全初始化事件保持原样；fixture 仅在启动前将状态设为可执行，不伪造 release 事件。
    bootstrap = [e for e in events if e['source'] == 'FLYWAY_MIGRATION']
    transitions = [e for e in events if e['source'] == 'OPERATOR_ENGAGE']
    require(len(events) == 2 and len(bootstrap) == len(transitions) == 1, 'unexpected Kill transitions')
    require(bootstrap[0]['from_status'] is None and bootstrap[0]['to_status'] == 'ENGAGED'
            and bootstrap[0]['state_version'] == 1 and bootstrap[0]['reason_code'] == 'DEFAULT_SAFE_BOOTSTRAP', 'bootstrap identity changed')
    e = transitions[0]
    require(e['from_status'] == 'DISENGAGED' and e['to_status'] == 'ENGAGED'
            and e['state_version'] == durable['version'] and e['trace_id'] == durable['trace_id'], 'Kill durable event missing')
    before = unique(k['beforeFacts']['orders'], 'order_id')
    require(len(before) == 121 and all(o['status'] in ('ACCEPTED', 'FILLED') for o in before.values()), 'pre-Kill admission not proven')
    before_venue = unique(k['beforeVenue']['data'], 'clOrdId')
    require(set(before_venue) == {o['client_order_id'] for o in before.values()} and k['beforeVenue']['places'] == 121, 'pre-Kill Venue identity gap')
    require(k['beforeSample']['orders'] == 121 and k['beforeSample']['trades'] > 0
            and k['beforeSample']['actionable'] > 0 and k['beforeCursor'][0]['revision'] > 0, 'empty transition workload')
    pre_runs = k['beforeFacts']['strategy_runs']
    require(len(pre_runs) == 1 and pre_runs[0]['status'] != 'SUCCEEDED', 'strategy recovery not exercised')
    require(len(k['beforeFacts']['strategy_run_dispatch_work']) == 1, 'pre-Kill durable strategy missing')
    commands = k['newCommands']
    require([n['identity'] for n in commands] == [121, 122, 123, 124], 'new command identity coverage')
    orders = unique(f['orders'], 'order_id'); rejected = set()
    for n in commands:
        require(k['durableReadMillis'] < n['submittedMillis'] <= n['completedMillis']
                and n['result'] == 'L5_PLACE RISK_REJECTED', 'new mutation admitted or timing unknown')
        client = 'l5' + p['database'][-20:] + f"{n['identity']:04d}"
        matches = [o for o in orders.values() if o['client_order_id'] == client]
        require(len(matches) == 1 and matches[0]['status'] == 'RISK_REJECTED', 'missing durable rejection')
        o = matches[0]; rejected.add(o['order_id'])
        require(o['order_id'] not in before and o['external_order_id'] is None, 'pre/post identity alias')
        risks = [r for r in k['riskEvents'] if r['scope_id'] == o['order_id']]
        require(len(risks) == 1 and risks[0]['decision'] == 'REJECT'
                and risks[0]['reason'] == 'KILL_SWITCH_TRIGGERED', 'wrong RiskGate rejection')
        authorities = [a for a in f['ordinary_place_authorities'] if a['order_id'] == o['order_id']]
        require(len(authorities) == 1 and authorities[0]['state'] == 'NOT_ARMED'
                and authorities[0]['decided_at'] is None, 'rejected V49 admission')
        require(not any(t['order_id'] == o['order_id'] for t in f['trades']), 'rejected command traded')
        require(not any(v['clOrdId'] == client for v in p['venue']['data']), 'post-Kill new Venue mutation')
        require(not any(v.get('client') == client for v in p['venue']['events']), 'post-Kill new Venue call')
    require(set(orders) == set(before) | rejected and not set(before) & rejected, 'lost or unclassified command')
    require({r['scope_id'] for r in k['riskEvents'] if r['decision'] == 'ALLOW'} == set(before), 'pre-Kill risk admission identity gap')
    progress = p['progress']
    require(any(s['timeMillis'] < k['requestMillis'] and s['trades'] > 0 and s['actionable'] > 0 for s in progress), 'no pre-transition progress')
    require(any(k['requestMillis'] <= s['timeMillis'] <= k['releaseExistingFillsMillis'] for s in progress), 'transition sampling gap')
    post = [s for s in progress if s['timeMillis'] > k['durableReadMillis']]
    require(post and all(s['kill']['status'] == 'ENGAGED' and s['kill']['version'] == durable['version'] for s in post), 'Kill released during run')
    require(post[-1]['trades'] > k['beforeSample']['trades'] and post[-1]['ledger'] > k['beforeSample']['ledger']
            and post[-1]['backlog'] == 0 and post[-1]['runsSucceeded'] == 1, 'recovery stalled under Kill')
    # 这些live订单仅由 ENGAGE 后的最后一次 FILL 释放；查询必须晚于它们的真实成交序列。
    pending_clients = {v['clOrdId'] for v in before_venue.values() if v['state'] == 'live'}
    require(len(pending_clients) > 0, 'no existing Venue work under Kill')
    for client in pending_clients:
        fills = [e for e in p['venue']['events'] if e['type'] == 'FILL' and e['client'] == client]
        require(len(fills) == 1 and fills[0]['sequence'] > max(e['sequence'] for e in k['beforeVenue']['events']), 'existing fill lineage')
        require(any(e['type'] == 'QUERY_FILLS' and e.get('ordId') == before_venue[client]['ordId']
                    and e['sequence'] > fills[0]['sequence'] for e in p['venue']['events']), 'no query of post-Kill existing fill')
    actors = c['actors']; require(len(actors) == 2 and len({a['pid'] for a in actors}) == 2, 'actor identity missing')
    require({n['pid'] for n in commands} == {a['pid'] for a in actors}, 'rejection not observed across JVMs')
    calls = [call for a in actors for call in a['calls']]
    require(all('error' not in call for call in calls), 'reconciliation error')
    require(any(call['endMillis'] < k['requestMillis'] and call['newTrades'] > 0 for call in calls), 'no reconciliation before Kill')
    require(any(call['startMillis'] > k['durableReadMillis'] and call['newTrades'] > 0 for call in calls), 'no reconciliation after Kill')
    reservations = [r for a in actors for r in a['reservations']]
    require(all(0 < len(r['selected']) <= r['limit'] <= 100 for r in reservations), 'unbounded candidate batch')
    require(set(before) <= {x['order_id'] for r in reservations for x in r['selected']}, 'existing work not observed')
    # 数量从实际Trade与Order.side/base fee重建，不能只比较预设总量。
    qty = sum((Decimal(str(t['qty'])) * (1 if orders[t['order_id']]['side'] == 'BUY' else -1)
               - (Decimal(str(t['fee'])) if t['fee_currency'] == 'BTC' else 0) for t in f['trades']), Decimal(0))
    require(len(f['positions']) == 1 and Decimal(str(f['positions'][0]['qty'])) == qty
            and Decimal(str(f['positions'][0]['available_qty'])) == qty, 'independent Position reconstruction')
    btc = max((s for s in f['account_snapshots'] if s['currency'] == 'BTC'), key=lambda s:s['snapshot_id'])
    require(Decimal(str(btc['balance'])) == qty, 'latest Snapshot reconstruction')
    return dict(f, orders=[o for o in f['orders'] if o['order_id'] not in rejected],
                ordinary_place_authorities=[a for a in f['ordinary_place_authorities'] if a['order_id'] not in rejected])


def compact(p, identity):
    k = p['concurrent']['kill']
    orders = {o['client_order_id']: o for o in p['facts']['orders']}
    rejected = []
    for n in k['newCommands']:
        o = orders['l5' + p['database'][-20:] + f"{n['identity']:04d}"]
        rejected.append(dict(n, order_id=o['order_id'], client_order_id=o['client_order_id']))
    return export({'from': 'DISENGAGED', 'to': 'ENGAGED', 'version': k['durableState']['version'],
                   'transitionTimestamp': k['durableState']['updated_at'], 'requestMillis': k['requestMillis'],
                   'durableReadMillis': k['durableReadMillis'], 'newCommands': rejected,
                   'preAdmitted': 121, 'preSample': k['beforeSample'], 'postSample': k['afterSample'],
                   'newVenueMutations': 0, 'killTransitionEvents': 1, 'bootstrapEvents': 1, 'strategyRunsRecovered': 1,
                   'positionReconstruction': '12.1', 'snapshotBTC': '12.1', 'snapshotUSDT': '0'}, identity)


def kill_negatives(p):
    from l5_measurement import verify
    changes = {
        'new_admission': lambda q:q['concurrent']['kill']['newCommands'][0].update(result='L5_PLACE ACCEPTED'),
        'ambiguous_admission': lambda q:q['concurrent']['kill']['newCommands'][0].update(submittedMillis=0),
        'kill_released': lambda q:q['concurrent']['kill']['finalState'].update(status='DISENGAGED'),
        'missing_transition_event': lambda q:q['concurrent']['kill'].update(events=[]),
        'empty_pre_work': lambda q:q['concurrent']['kill']['beforeSample'].update(trades=0),
        'no_existing_backlog': lambda q:q['concurrent']['kill']['beforeSample'].update(actionable=0),
        'missing_command': lambda q:q['concurrent']['kill']['newCommands'].pop(),
        'wrong_risk_reason': lambda q:next(r for r in q['concurrent']['kill']['riskEvents'] if r['decision']=='REJECT').update(reason='RATE_LIMIT_EXCEEDED'),
        'venue_for_rejected': lambda q:q['venue']['events'].append({'type':'REQUEST_RECEIVED','client':'l5'+q['database'][-20:]+'0121'}),
        'no_post_progress': lambda q:q['progress'][-1].update(trades=0),
        'reconcile_stalled': lambda q:[call.update(newTrades=0) for a in q['concurrent']['actors'] for call in a['calls']],
        'duplicate_kill_event': lambda q:q['concurrent']['kill']['events'].append(copy.deepcopy(q['concurrent']['kill']['events'][0])),
        'rejected_v49_armed': lambda q:next(a for a in q['facts']['ordinary_place_authorities']
            if a['order_id'] in {o['order_id'] for o in q['facts']['orders'] if o['status']=='RISK_REJECTED'}).update(state='MAY_HAVE_ESCAPED'),
        'bootstrap_rewritten': lambda q:next(e for e in q['concurrent']['kill']['events'] if e['source']=='FLYWAY_MIGRATION').update(to_status='DISENGAGED'),
        'missing_recovery_queries': lambda q:q['venue'].update(events=[e for e in q['venue']['events'] if e['type']!='QUERY_FILLS']),
        'pre_admission_unproven': lambda q:q['concurrent']['kill']['beforeFacts']['orders'][0].update(status='SENT'),
        'secret_not_hidden': lambda q:q.update(credential='sentinel'),
    }
    for name, mutation in changes.items():
        q=copy.deepcopy(p); mutation(q)
        try: verify(q)
        except (ValueError, KeyError): continue
        raise ValueError('Kill oracle accepted mutation: '+name)
    return list(changes)
