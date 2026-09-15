"""逐命令关联与完成后间隔证明；采样上下文保留采样年龄，不冒充失败瞬间快照。"""
import json
import math
import sys
from pathlib import Path


def require(value, message):
    if not value:
        raise ValueError(message)


def distribution(rows):
    values = sorted(row['latencyMillis'] for row in rows)
    if not values:
        return {'count': 0}
    return {'count': len(values), 'over5Seconds': sum(v > 5000 for v in values),
            **{name: values[max(0, math.ceil(len(values)*p)-1)]
               for name, p in [('p50Millis', .5), ('p95Millis', .95), ('p99Millis', .99), ('maxMillis', 1)]}}


def verify_rows(events, expected_calls):
    require(len(events) == expected_calls*2 and expected_calls > 0, 'latency event coverage')
    completed = []
    for index in range(0, len(events), 2):
        dispatch, result = events[index:index+2]
        require(dispatch['state'] == 'DISPATCHED' and result['state'] == 'SUCCESS', 'command did not succeed')
        for key in ('commandId', 'actor', 'generation', 'pid', 'dispatchElapsedNanos'):
            require(dispatch[key] == result[key], 'command identity drift')
        require(result['commandId'] == str(index//2+1), 'command sequence')
        require(result['actor'] == (index//2) % 2, 'actor round sequence')
        reply = result['reply']
        require(reply['commandId'] == result['commandId'] and reply['result'] == 'SUCCESS', 'reply identity')
        require(0 <= reply['candidateCount'] <= 100, 'candidate bound')
        require(0 <= reply['childCompletionNanos']-reply['childStartNanos'] <= 45_000_000_000, 'execution bound')
        require(result['executionBoundSeconds'] == 45 and result['responseBoundSeconds'] == 75, 'timeout hierarchy')
        latency = (result['completionElapsedNanos']-result['dispatchElapsedNanos'])/1e6
        require(0 <= latency <= 75000 and abs(latency-result['latencyMillis']) < .001, 'controller latency')
        if completed:
            gap = result['dispatchElapsedNanos']-completed[-1]['completionElapsedNanos']
            require(gap >= (5_000_000_000 if result['actor'] == 0 else 0), 'overlap or catch-up')
        for key in ('before', 'after'):
            require(result[key]['scope'] == 'LATEST_INDEPENDENT_SAMPLE_NOT_COMMAND_TIME_SNAPSHOT', 'context scope')
            require('sampleIndex' in result[key] and 'candidateAge' in result[key]['actor'] and 'backlog' in result[key]['pg'], 'latency context missing')
        completed.append(result)
    return completed


def read_events(path, expected_calls, call_cap, raw_cap):
    require(0 < expected_calls <= call_cap, 'reconciliation call budget')
    require(0 < path.stat().st_size <= raw_cap, 'latency raw evidence budget')
    events = []
    with path.open(encoding='utf-8') as stream:
        for line in stream:
            require(len(events) < 2*expected_calls and len(line) < 2_000_000, 'latency event budget')
            events.append(json.loads(line))
    require(len(events) == 2*expected_calls, 'latency event coverage')
    return events


def analyze(directory):
    directory = Path(directory)
    proof = json.loads((directory/'proof.json').read_text(encoding='utf-8-sig'))
    path = directory/'reconcile-latency.ndjson'
    events = read_events(path, proof['reconciliationCalls'], proof['hardBudgets']['reconciliationCallsCap'],
                         proof['hardBudgets']['rawHardCap'])
    rows = verify_rows(events, proof['reconciliationCalls'])
    points = [json.loads(line) for line in (directory/'business-progress.ndjson').read_text(encoding='utf-8').splitlines()]
    slow = []
    for index, row in enumerate(rows):
        if row['latencyMillis'] <= 5000:
            continue
        item = dict(row)
        item['nextSuccessfulCommandId'] = rows[index+1]['commandId'] if index+1 < len(rows) else None
        subsequent = next((p for p in points if p['completedObservedElapsedNanos'] > row['completionElapsedNanos']), None)
        item['subsequentBusinessOracle'] = subsequent['oracle'] if subsequent else None
        slow.append(item)
    windows = []
    for lo in range(0, math.ceil(proof['actualEndElapsedNanos']/600_000_000_000)):
        part = [r for r in rows if lo*600_000_000_000 <= r['dispatchElapsedNanos'] < (lo+1)*600_000_000_000]
        windows.append({'fromSeconds': lo*600, **distribution(part)})
    return {'status': 'PASS', 'distribution': distribution(rows), 'windows': windows,
            'drain': distribution([r for r in rows if r['phase'] == 'DRAIN']), 'slowCalls': slow,
            'nonOverlap': True, 'noCatchUp': True, 'protocolSynchronized': True}


if __name__ == '__main__':
    try:
        result = analyze(sys.argv[1])
    except Exception as error:
        result = {'status': 'FAIL', 'failure': str(error)}
    Path(sys.argv[2]).write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k in ('status', 'failure', 'distribution', 'drain')}))
    sys.exit(0 if result['status'] == 'PASS' else 1)
