"""只读校准原始文件；输出可追溯观测，不冻结速率或noise threshold。"""
import json
import sys
from pathlib import Path
from l6_oracle import verify


def require(condition, message):
    if not condition:
        raise ValueError(message)


def series(rows):
    require(rows, 'empty measurement series')
    groups = {}
    growth = []
    gc_events = []
    seen_gc = set()
    previous = None
    for index, row in enumerate(rows):
        require(row['sampleIndex'] == index and row['status'] == 'MEASURED', 'sampling gap/missing')
        require(0 <= row['elapsedMillis'] - index * 10000 <= 2000, 'sampling cadence')
        require(row['collectionMillis'] <= 8000, 'collection overrun')
        sources = row['sources']
        require(set(sources) == {'nq0', 'nq1', 'venue', 'postgres', 'os', 'files', 'ownership'}, 'missing collector')
        for source, observation in sources.items():
            require(observation['status'] == 'MEASURED', 'mandatory measurement missing')
            require(observation['sampleIndex'] == index and observation['sampleToken'] == row['sampleToken'], 'stale sample')
            require(observation['availability'], 'missing availability')
            for key, state in observation['availability'].items():
                require(state in ('MEASURED', 'NOT_APPLICABLE') and key in observation['values'], 'mandatory field missing')
                require(state != 'NOT_APPLICABLE' or key in ('handles', 'fd'), 'invalid exemption')
            def collect(value, path):
                if isinstance(value, (int, float)) and not isinstance(value, bool):
                    groups.setdefault((row['phase'], source, path), []).append((index, value))
                elif isinstance(value, dict):
                    for key, item in value.items():
                        collect(item, path + '.' + key)
            collect(observation['values'], '')
        for actor in ('nq0', 'nq1'):
            value = sources[actor]['values']
            for gc in value['gc']:
                require(gc['count'] >= 0 and gc['timeMillis'] >= 0, 'GC unavailable')
                require(gc['lastGcStatus'] in ('NO_GC_OBSERVED', 'MEASURED'), 'GC post-collection unavailable')
                if gc['lastGcStatus'] == 'MEASURED':
                    event = gc['lastGc']
                    key = (value['pid'], gc['name'], event['id'])
                    if key not in seen_gc:
                        gc_events.append({'actor': actor, 'pid': value['pid'], 'collector': gc['name'],
                                          'observedSampleIndex': index, 'observedPhase': row['phase'], **event})
                        seen_gc.add(key)
        current = {'orders': sources['postgres']['values']['orders'],
                   'audit': sources['postgres']['values']['auditRows'],
                   'event': sources['postgres']['values']['eventRows'],
                   'logBytes': sources['files']['values']['logBytes']}
        new_orders = current['orders'] - previous['orders'] if previous else None
        for key in ('audit', 'event', 'logBytes'):
            delta = current[key] - previous[key] if previous else None
            require(delta is None or delta >= 0, 'unexplained growth counter regression')
            growth.append({'sampleIndex': index, 'phase': row['phase'], 'resource': key,
                           'absolute': current[key], 'delta': delta, 'ordersDelta': new_orders,
                           'perOrderNormalizedDelta': delta / new_orders if new_orders and new_orders > 0 else None,
                           'normalizationStatus': 'MEASURED' if new_orders and new_orders > 0 else 'NO_NEW_ORDERS_OR_BASELINE'})
        previous = current
    bands = [{'phase': phase, 'source': source, 'field': key, 'sampleCount': len(values),
              'min': min(v for _, v in values), 'max': max(v for _, v in values),
              'maxMinusMin': max(v for _, v in values) - min(v for _, v in values),
              'sampleIndices': [i for i, _ in values], 'frozenNoiseBand': False}
             for (phase, source, key), values in groups.items()]
    return {'mode': 'CALIBRATION', 'resourceObservations': bands, 'gcPostCollectionObservations': gc_events,
            'gcCaveat': 'Observation phase is not event phase; use GC uptime and count identity. No GC event means unavailable low-water calibration, never zero.',
            'growth': growth, 'formalCalibrationAccepted': False, 'L6Accepted': False}


def analyze(directory):
    directory = Path(directory)
    require(sum(p.stat().st_size for p in directory.iterdir() if p.is_file()) < 1024**3, 'raw budget')
    run = json.loads((directory / 'calibration-run.json').read_text(encoding='utf-8-sig'))
    require(run['mode'] == 'CALIBRATION' and run['result'] in ('CALIBRATION_SMOKE_MEASURED', 'CALIBRATION_RAW_MEASURED_PENDING_ACCEPTANCE'), 'not completed calibration')
    require(not run['formalCalibrationAccepted'] and not run['L6Accepted'], 'mode/acceptance confusion')
    require(run['cleanup'] == 'PASS' and run['ownedSurvivors'] == 0, 'cleanup failure')
    rate = run['rateEvidence']
    require(rate['rawRateEvidenceValid'] and rate['eligibleFullChainCompletions'] > 0, 'no healthy rate evidence')
    require(run['mandatoryMissingCount'] == run['samplingViolations'] == 0, 'missing measurements')
    rows = [json.loads(line) for line in (directory / 'resource-samples.ndjson').read_text(encoding='utf-8').splitlines()]
    require(len(rows) == (run['warmupRequiredSeconds'] + run['measurementRequiredSeconds']) // 10, 'sample coverage')
    result = series(rows)
    checkpoints = sorted(directory.glob('checkpoint-*.json'))
    require(len(checkpoints) == run['checkpointCount'], 'checkpoint coverage')
    for file in checkpoints:
        verify(json.loads(file.read_text(encoding='utf-8-sig')))
    result['checkpointCount'] = len(checkpoints)
    result['sampleCount'] = len(rows)
    result['canonicalManifestCreated'] = False
    (directory / 'calibration-analysis.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    return {'mode': 'CALIBRATION', 'sampleCount': len(rows), 'checkpoints': len(checkpoints), 'formalCalibrationAccepted': False}


if __name__ == '__main__':
    print(json.dumps(analyze(sys.argv[1])))
