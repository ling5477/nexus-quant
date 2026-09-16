"""只读重建B证据；同代窗口及自然GC独立判定，短probe永不升级为正式接受。"""
import hashlib
import gzip
import json
import sys
from pathlib import Path
from l6_b_oracle import load_checkpoint
from l6_oracle import verify


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def bound_manifest(path, expected_sha):
    payload = Path(path).read_bytes()
    require(hashlib.sha256(payload).hexdigest() == expected_sha, 'frozen manifest identity')
    return json.loads(payload)


def verify_analysis_identity(directory, proof, manifest):
    frozen = bound_manifest(manifest, proof['manifestEntry']['sha256'])
    entry = read_json(directory/'candidate-entry.json')
    require(entry['HEAD'] == proof['HEAD'], 'candidate HEAD identity')
    inputs = entry['files']
    contract_paths = [p for p in inputs if p.endswith('/L6_B_RUNNER_CONTRACT_V2.json')]
    manifest_paths = [p for p in inputs if p.endswith('/L6_FORMAL_CALIBRATION_MANIFEST.json')]
    require(len(contract_paths) == len(manifest_paths) == 1, 'candidate contract inputs')
    require(inputs[contract_paths[0]] == proof['sha256'] and inputs[manifest_paths[0]] == proof['manifestEntry']['sha256'], 'candidate frozen input hashes')
    for name in ('l6_b_analyzer.py','l6_b_latency.py','l6_b_sampling.py','l6_b_oracle.py','l6_oracle.py','l5_measurement.py','synthetic_evidence.py'):
        paths = [p for p in inputs if p.endswith('/'+name)]
        require(len(paths) == 1 and hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest() == inputs[paths[0]], 'analysis dependency identity: '+name)
    return frozen


def verify_recovery_deadline(event):
    require(event['recoveryStartedElapsedNanos'] <= event['recoveryCompletedElapsedNanos'] < event['recoveryDeadlineElapsedNanos'], 'restart recovery total deadline')
    recovery_bound = min(600, max(20, 2*((event['ordersBefore']+99)//100)*10))*1_000_000_000
    require(event['recoveryDeadlineElapsedNanos']-event['recoveryStartedElapsedNanos'] == recovery_bound, 'restart recovery bound identity')


def ndjson(path, cap):
    rows = []
    with Path(path).open(encoding='utf-8') as stream:
        for line in stream:
            require(len(rows) < cap and len(line) < 2_000_000, 'bounded NDJSON')
            rows.append(json.loads(line))
    return rows


def at(value, field):
    for key in field.strip('.').split('.'):
        value = value[key]
    require(isinstance(value, (int, float)) and not isinstance(value, bool), 'missing numeric ' + field)
    return value


def trend(lows, tail, noise):
    # 三窗口累计增长超过冻结噪声后，必须由同一PID的末段恢复；不能用新代清除。
    require(noise >= 0, 'noise unavailable')
    if len(lows) < 3 or tail is None:
        return 'INCONCLUSIVE'
    for i in range(len(lows) - 2):
        a, b, c = lows[i:i + 3]
        if a < b < c and c - a > noise and tail > a + noise:
            return 'LEAK_SUSPECT'
    return 'STABLE_WITHIN_OBSERVED_WINDOWS'


def generation_series(rows, registry):
    known = {(x['logicalActor'], x['generation'], x['pid']): x for x in registry}
    require(len(known) == len(registry) and len({x['pid'] for x in registry}) == len(registry), 'generation ambiguity')
    grouped, events, seen = {}, {}, set()
    last_generation = {}
    for row in rows:
        for source in ('nq0', 'nq1'):
            value = row['sources'][source]['values']
            key = (value['logicalActor'], value['generation'], value['pid'])
            require(key in known and value['startTimestamp'] == known[key]['startTimestamp'], 'unregistered generation')
            require(value['generation'] >= last_generation.get(source, 0), 'generation regression')
            last_generation[source] = value['generation']
            if value['lifecycle'] != 'RUNNING':
                require(value['measurementScope'] == 'LIFECYCLE_ONLY' and 'heapUsed' not in value, 'transition fabricated JVM metrics')
                continue
            require(value['processAlive'] and value['measurementScope'] == 'JVM_AND_LIFECYCLE', 'running lifecycle')
            grouped.setdefault(key, []).append((row['elapsedMillis'], value, row))
            require(value['tickFailed'] == 0 and value['acquisitionTimeoutDelta'] == 0, 'scheduler/pool failure')
            for gc in value['gc']:
                require(gc['lastGcStatus'] in ('MEASURED', 'NO_GC_OBSERVED'), 'missing GC')
                if gc['lastGcStatus'] != 'MEASURED':
                    continue
                event = gc['lastGc']
                identity = (value['pid'], gc['name'], event['id'])
                if identity in seen:
                    continue
                seen.add(identity)
                elapsed = row['elapsedMillis'] - value['jvmUptimeMillis'] + event['endUptimeMillis']
                require(elapsed <= row['elapsedMillis'], 'GC future timestamp')
                events.setdefault(key, []).append({**event, 'collector': gc['name'], 'eventElapsedMillis': elapsed})
    return grouped, events


def resources(rows, registry, noise):
    groups, gc = generation_series(rows, registry)
    bands = {(b['source'], b['field']): b['maxMinusMin'] for b in noise['resources']}
    heap_bands = {(b['owner'], b['collector']): b['range'] for b in noise['heap']}
    results, findings = [], []
    for key, values in groups.items():
        actor, generation, pid = key
        source = 'nq' + actor[-1]
        # 初代用canonical warmup；后代初始化单列600秒，避免启动低谷污染同负载比较。
        begin = 600_000 if generation == 0 else values[0][0] + 600_000
        end = min(10_200_000, values[-1][0] + 10_000)
        windows = []
        lo = begin
        while lo + 600_000 <= end:
            part = [(t, v, r) for t, v, r in values if lo <= t < lo + 600_000]
            require(len(part) >= 59, 'generation window cadence coverage')
            windows.append((lo, lo + 600_000, part))
            lo += 600_000
        detail = {'logicalActor': actor, 'generation': generation, 'pid': pid,
                  'comparableWindows': len(windows), 'initializationUntilMillis': begin,
                  'naturalGc': gc.get(key, []), 'metrics': []}
        detail['observedRanges'] = {field: {'min':min(at(v,field) for _,v,_ in values), 'max':max(at(v,field) for _,v,_ in values)}
                                    for field in ('heapUsed','heapCommitted','heapMax','threads','peakThreads','active','idle','pending','commandQueue.queueSize','metricsExecutor.queueSize','candidateAge.eligibleCandidateCount')}
        ages = [v['candidateAge']['oldestCandidateAgeMillis'] for _,v,_ in values if v['candidateAge']['oldestCandidateAgeMillis'] is not None]
        detail['maximumObservedOldestCandidateAgeMillis'] = max(ages, default=None)
        detail['oldestAgeEmptySemantics'] = 'null means no actionable candidate; never a missing measurement converted to zero'
        if len(windows) < 3:
            findings.append(f'{actor}/g{generation}: INCONCLUSIVE_WINDOW_COVERAGE')
        tail_values = [(t, v, r) for t, v, r in values if t >= max(begin, values[-1][0] - 590_000)]
        for field in ('.threads', '.active', '.idle', '.pending', '.poolMax', '.commandQueue.queueSize', '.metricsExecutor.queueSize'):
            lows = [min(at(v, field) for _, v, _ in part) for _, _, part in windows]
            tail = min((at(v, field) for _, v, _ in tail_values), default=None)
            band = bands[(source, field)]
            status = trend(lows, tail, band)
            detail['metrics'].append({'field': field, 'windowLows': lows, 'tailLow': tail, 'noise': band, 'status': status})
            if status != 'STABLE_WITHIN_OBSERVED_WINDOWS':
                findings.append(f'{actor}/g{generation}/{field}: {status}')
        handles = []
        for _, _, part in windows:
            counters = [p.get('HandleCount', p.get('fd')) for _, _, row in part
                        for p in row['sources']['os']['values']['processes'] if p.get('Id', p.get('pid')) == pid]
            require(counters and all(v is not None for v in counters), 'per-PID OS counter missing')
            handles.append(min(counters))
        counter_tail = [p.get('HandleCount', p.get('fd')) for _, _, row in tail_values
                        for p in row['sources']['os']['values']['processes'] if p.get('Id', p.get('pid')) == pid]
        # 采用已冻结总handles噪声，记录为保守的逐PID容差，绝不跨PID比较绝对值。
        band = bands[('os', '.handles')]
        status = trend(handles, min(counter_tail) if counter_tail else None, band)
        detail['metrics'].append({'field': 'ownedOsCounter', 'windowLows': handles, 'noise': band, 'status': status})
        if status != 'STABLE_WITHIN_OBSERVED_WINDOWS':
            findings.append(f'{actor}/g{generation}/handles: {status}')
        for (owner, collector), band in heap_bands.items():
            if owner != source:
                continue
            natural = [e for e in gc.get(key, []) if e['collector'] == collector]
            lows = [min((e['heapUsedAfterGc'] for e in natural if lo <= e['eventElapsedMillis'] < hi), default=None)
                    for lo, hi, _ in windows]
            tail = min((e['heapUsedAfterGc'] for e in natural if e['eventElapsedMillis'] >= max(begin, values[-1][0] - 590_000)), default=None)
            status = 'INCONCLUSIVE' if any(v is None for v in lows) else trend(lows, tail, band)
            detail['metrics'].append({'field': 'naturalPostGcHeap', 'collector': collector, 'windowLows': lows, 'tailLow': tail, 'noise': band, 'status': status})
            if status != 'STABLE_WITHIN_OBSERVED_WINDOWS':
                findings.append(f'{actor}/g{generation}/heap: {status}')
        results.append(detail)
    require(len(groups) == 5, 'all five generations must be measured')
    persistent = []
    nq_pids = {g['pid'] for g in registry}
    other_pids = {p.get('Id',p.get('pid')) for r in rows for p in r['sources']['os']['values']['processes']} - nq_pids
    require(len(other_pids) == 2, 'persistent controller/Venue process identity')
    for pid in sorted(other_pids):
        def counters(part):
            values = [p.get('HandleCount',p.get('fd')) for r in part for p in r['sources']['os']['values']['processes'] if p.get('Id',p.get('pid')) == pid]
            require(len(values) == len(part) and all(v is not None for v in values), 'persistent PID OS coverage')
            return values
        lows = [min(counters([r for r in rows if lo <= r['elapsedMillis'] < lo+600_000])) for lo in range(600_000,10_200_000,600_000)]
        tail = min(counters([r for r in rows if r['phase'] == 'DRAIN']))
        status = trend(lows,tail,bands[('os','.handles')])
        persistent.append({'source':'ownedPersistentPid','pid':pid,'field':'osCounter','windowLows':lows,'tailLow':tail,'status':status})
        if status != 'STABLE_WITHIN_OBSERVED_WINDOWS':
            findings.append(f'pid{pid}/handles: {status}')
    for source, fields in {'venue': ['active', 'queue'], 'postgres': ['appConnections', 'databaseConnections', 'idleInTransaction']}.items():
        for field in fields:
            lows = [min(at(r['sources'][source]['values'], field) for r in rows if lo <= r['elapsedMillis'] < lo+600_000)
                    for lo in range(600_000, 10_200_000, 600_000)]
            tail = min(at(r['sources'][source]['values'], field) for r in rows if r['phase'] == 'DRAIN')
            status = trend(lows, tail, bands[(source, '.'+field)])
            persistent.append({'source': source, 'field': field, 'windowLows': lows, 'tailLow': tail, 'status': status})
            if status != 'STABLE_WITHIN_OBSERVED_WINDOWS':
                findings.append(f'{source}/{field}: {status}')
    return {'generations': results, 'persistentResources': persistent, 'findings': findings}


def progress(rows, points, scans, pacing):
    windows = []
    for lo in range(600_000, 10_200_000, 600_000):
        hi = lo + 600_000
        eligible = {s['logicalOrderId'] for s in pacing if s['decision'] == 'EMITTED' and lo <= s['actualElapsed']/1e6 < hi}
        complete = set().union(*(set(p['fullChainOrderIds']) for p in points if lo <= p['completedObservedElapsedNanos']/1e6 < hi))
        require(eligible & complete, f'active window {lo}: no new complete business chain')
        count = sum(s['scan']['scannedCount'] for s in scans if lo <= s['elapsedNanos']/1e6 < hi)
        require(count > 0, 'no strategy scan')
        part = [r for r in rows if lo <= r['elapsedMillis'] < hi]
        by_pid = {}
        for r in part:
            for source in ('nq0', 'nq1'):
                v = r['sources'][source]['values']
                if v['lifecycle'] == 'RUNNING':
                    by_pid.setdefault(v['pid'], []).append(v)
        ticks = sum(v[-1]['tickCompleted'] - v[0]['tickCompleted'] for v in by_pid.values())
        validation = sum(v[-1]['observations']['validation_refresh']['totals'].get('ATTEMPT', 0) - v[0]['observations']['validation_refresh']['totals'].get('ATTEMPT', 0) for v in by_pid.values())
        revision = lambda r: max((x['revision'] for x in r['sources']['postgres']['values']['cursor']), default=0)
        cursor = revision(part[-1]) - revision(part[0])
        require(ticks > 0 and validation > 0 and cursor > 0, 'scheduler/reconciliation progress missing')
        windows.append({'fromMillis': lo, 'toMillis': hi, 'newFullChains': len(eligible & complete), 'scanned': count,
                        'recoveryTicks': ticks, 'validationAttempts': validation, 'cursorAdvances': cursor})
    return windows


def verify_strategy_barrier(directory, event):
    def snapshot(name, digest):
        path = directory/name
        require(path.parent == directory and hashlib.sha256(path.read_bytes()).hexdigest() == digest, 'restart strategy snapshot identity')
        return json.loads(gzip.decompress(path.read_bytes()))['facts']['strategy_runs']
    before = snapshot(event['beforeSnapshot'], event['beforeSnapshotSha256'])
    recovered = event['recoveredFullChain']
    after = snapshot(recovered['checkpointPath'], recovered['checkpointSha256'])
    active = {r['strategy_run_id'] for r in before if r['status'] in ('CREATED','DISPATCHING','RUNNING')}
    require(active and active == {r['strategy_run_id'] for r in event['activeRunsBefore']}, 'pre-restart active owner binding')
    require(event['strategyRunBarrier'] == 'CONVERGED' and event['activeRunsAfter'] == [], 'strategy recovery barrier missing')
    terminal = {r['strategy_run_id'] for r in after if r['status'] == 'SUCCEEDED'}
    require(active <= terminal, 'pre-restart StrategyRun not converged')


def analyze(directory, manifest):
    directory = Path(directory)
    proof = read_json(directory/'proof.json')
    frozen_manifest = verify_analysis_identity(directory, proof, manifest)
    require(proof['mode'] == 'FORMAL_L6_B' and not proof['probe'], 'formal B only')
    require(proof['result'] == 'L6_B_180MIN_MEASURED_PENDING_QUALIFICATION', 'incomplete runtime')
    require(proof['actualEndElapsedNanos'] >= 10_800_000_000_000, 'duration incomplete')
    require(proof['candidateUnchanged'] and proof['cleanup'] == 'PASS' and proof['ownedSurvivors'] == proof['ownedNqSurvivors'] == 0, 'identity/cleanup')
    rows = ndjson(directory/'resources.ndjson', 1092)
    from l6_b_sampling import verify_rows as verify_sampling
    sampling = verify_sampling(rows, proof['samplingContract'])
    rows = [row for row in rows if row['status'] == 'MEASURED']
    for row in rows:
        require(set(row['sources']) == {'nq0','nq1','venue','postgres','os','files','ownership'}, 'mandatory collectors')
        for observation in row['sources'].values():
            require(observation['status'] == 'MEASURED' and observation['sampleToken'] == row['sampleToken'], 'mandatory stale observation')
            require(all(k in observation['values'] and (v == 'MEASURED' or k in ('fd','handles') and v == 'NOT_APPLICABLE') for k,v in observation['availability'].items()), 'mandatory field availability')
    points = ndjson(directory/'business-progress.ndjson', 190)
    require(points and points[-1]['phase'] == 'FINAL', 'full final checkpoint')
    for point in points:
        path = directory/point['checkpointPath']
        require(path.parent == directory and hashlib.sha256(path.read_bytes()).hexdigest() == point['checkpointSha256'], 'snapshot hash')
        require(verify(load_checkpoint(path)) == point['oracle'], 'business oracle replay')
    continuity = ndjson(directory/'continuity.ndjson', 20)
    require(len(continuity) == 12, 'restart continuity boundary coverage')
    for item in continuity:
        require(item['status'] == 'PRESERVED' and all(item[k] == proof[k] for k in ('container','databaseIdentity','postgresStartedAt','venuePid')), 'same PG/Venue identity')
    require(len(proof['restarts']) == 3, 'restart count')
    timing_findings = []
    for i, event in enumerate(proof['restarts']):
        require(event['index'] == i and event['plannedSeconds'] == (2400,4800,7200)[i], 'restart schedule')
        require(event['plannedSeconds'] <= event['startedElapsedNanos']/1e9, 'early restart')
        if event['startedElapsedNanos']/1e9 >= event['plannedSeconds']+45:
            timing_findings.append('RESTART_PLANNED_WINDOW_MISSED:'+str(i))
        require(event['result'] == 'RECOVERED' and event['backlogBefore'] > 0 and event['backlogAfter'] == 0, 'restart recovery')
        verify_recovery_deadline(event)
        require(event['oldPid'] != event['newPid'] and event['freshChainAfterRecovery']['oracle']['orders'] > event['ordersBefore'], 'fresh generation progress')
        verify_strategy_barrier(directory, event)
    require(proof['admission']['busySinceNanos'] == -1, 'final admission barrier unresolved')
    require(proof['venueFinal']['boundedDelayApplied'] == 1 and proof['final']['backlog'] == 0 and proof['newOrdersDuringDrain'] == 0, 'delay/drain')
    budget = proof['hardBudgets']
    require(budget['status'] == 'SUFFICIENT' and budget['finalTransactionDelta'] <= budget['transactionHardCap'], 'hard budgets')
    require(read_json(directory/'evidence-exit.json')['status'] == 'PASS', 'raw exit bound')
    stability = resources(rows, proof['generations'], frozen_manifest['noiseBands'])
    windows = progress(rows, points, ndjson(directory/'scheduler.ndjson', 5000), ndjson(directory/'pacing.ndjson', 1600))
    growth = []
    for lo in range(0, 10_800_000, 600_000):
        part = [r for r in rows if lo <= r['elapsedMillis'] < lo+600_000]
        summary = {'fromMillis':lo, 'toMillis':lo+600_000}
        for source, fields in {'postgres':['orders','backlog','auditRows','eventRows','pgTmpfsUsedBytes','pgWalAllocatedBytes','pgDatabaseSizeBytes'], 'files':['logBytes','ownedTempBytes','ownedTempFileCount']}.items():
            for field in fields:
                seq = [r['sources'][source]['values'][field] for r in part]
                summary[source+'.'+field] = {'start':seq[0], 'end':seq[-1], 'min':min(seq), 'max':max(seq), 'delta':seq[-1]-seq[0]}
        growth.append(summary)
    from l6_b_latency import analyze as analyze_latency
    latency = analyze_latency(directory)
    return {'status':'ACCEPTED' if not stability['findings'] and not timing_findings and sampling['status']=='ACCEPTED' else 'COMPLETED_NOT_ACCEPTED',
            'reconcileLatency':latency, 'sampling':sampling, 'timingFindings':timing_findings,
            'runId':proof['runId'], 'HEAD':proof['HEAD'], 'fullDurationSeconds':proof['actualEndElapsedNanos']/1e9,
            'sampleCount':len(rows), 'checkpointCount':len(points), 'businessWindows':windows,
            'resourceStability':stability, 'durableAndEvidenceGrowth':growth,
            'growthInterpretation':'审计/event/日志为有界留存量；按新业务量和库存模型解释，不能仅用正斜率判泄漏。',
            'finalOracle':proof['final']['oracle'], 'hardBudgets':budget}


if __name__ == '__main__':
    try:
        result = analyze(sys.argv[1], sys.argv[2])
    except Exception as failure:
        result = {'status':'NOT_ACCEPTED', 'failure':str(failure)}
    Path(sys.argv[3]).write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k in ('status','failure','runId','sampleCount','checkpointCount')}))
    sys.exit(0 if result['status'] == 'ACCEPTED' else 1)
