"""从已接受原始样本计算L6-A容量；只输出合同，不运行或修改数据库。"""
import hashlib
import json
import math
import sys
import zipfile
from fractions import Fraction
from pathlib import Path

MIB = 1048576
RUN = '40c5ba3a-ea8b-4040-82de-f4ac49cff476'
MODEL = 'ORDER_TIME_ENVELOPE_V1'


def ceil(value):
    return math.ceil(value)


def exposure(interval, start, end, producer_end=3000):
    """每个潜在slot即时形成订单；包含一单位空载背景，得到库存时间上界。"""
    slots = range(ceil(Fraction(producer_end * 10**9, interval)))
    times = [i * interval for i in slots]
    points = sorted({start * 10**9, end * 10**9,
                     *(t for t in times if start * 10**9 < t < end * 10**9)})
    return sum(Fraction(y-x, 10**9) * (1 + sum(t <= x for t in times))
               for x, y in zip(points, points[1:]))


def calculate(root, manifest_relative, bundle_relative):
    root = Path(root)
    bundle = root / bundle_relative
    if bundle.stat().st_size > 4 * MIB:
        raise ValueError('bounded replay archive required')
    with zipfile.ZipFile(bundle) as archive:
        expected_names = {'raw-manifest.json', 'accepted-summary.json', 'resources.ndjson', 'phase-boundaries/resources.ndjson', 'proof.json', 'pacing.ndjson'}
        if len(archive.namelist()) != 6 or set(archive.namelist()) != expected_names:
            raise ValueError('exact replay entries required')
        if sum(x.file_size for x in archive.infolist()) > 64 * MIB:
            raise ValueError('bounded replay expansion required')
        raw_manifest = json.loads(archive.read('raw-manifest.json'))
        hashes = {x['path']: x['sha256'] for x in raw_manifest['files']}
        raw = {}
        for name in ('resources.ndjson', 'phase-boundaries/resources.ndjson', 'proof.json', 'pacing.ndjson'):
            data = archive.read(name)
            if hashlib.sha256(data).hexdigest() != hashes[name]:
                raise ValueError('source raw SHA mismatch')
            raw[name] = data
        accepted = json.loads(archive.read('accepted-summary.json'))
    if not accepted['storageCalibrationAccepted'] or accepted['runId'] != RUN:
        raise ValueError('accepted storage source required')
    p = json.loads(raw['proof.json'])
    a = [json.loads(x) for x in raw['resources.ndjson'].splitlines()]
    b = {x['boundaryType']: x for x in map(json.loads, raw['phase-boundaries/resources.ndjson'].splitlines())}
    if len(a) != 210 or len(b) != 5 or p['result'] != 'STORAGE_CALIBRATION_MEASURED_PENDING_QUALIFICATION':
        raise ValueError('complete calibration required')
    if p['runId'] != RUN or p['ownedSurvivors'] != 0 or p['cleanup'] != 'PASS':
        raise ValueError('source identity or cleanup mismatch')
    if any(x['status'] != 'MEASURED' or x['sampleIndex'] != i or x['scheduledElapsedMillis'] != i*10000
           or x['sampleType'] != 'PERIODIC' for i, x in enumerate(a)):
        raise ValueError('complete accepted cadence required')
    value = lambda row: row['sources']['postgres']['values']
    used = lambda row: value(row)['pgTmpfsUsedBytes']
    for row in a + list(b.values()):
        v = value(row)
        if row['status'] != 'MEASURED' or v['pgTmpfsUsedBytes'] + v['pgTmpfsFreeBytes'] != v['pgTmpfsCapacityBytes'] or v['pgTmpfsCapacityBytes'] != 256*MIB:
            raise ValueError('source storage measurement invalid')
    for key, millis in {'WARMUP_END': 300000, 'MEASUREMENT_START': 300000, 'MEASUREMENT_END': 1500000, 'DRAIN_START': 1500000, 'DRAIN_END': 2100000}.items():
        if b[key]['scheduledElapsedMillis'] != millis:
            raise ValueError('source phase boundary invalid')
    # 订单只增不减，左端库存积分低估源暴露量，从而使bytes/库存秒比率偏保守。
    def observed_exposure(start, end):
        return sum((1 + value(a[t//10])['orders']) * 10 for t in range(start, end, 10))
    if any(value(y)['orders'] < value(x)['orders'] for x, y in zip(a, a[1:])):
        raise ValueError('non-monotonic source inventory')
    ms, me, ds, de = (b[k] for k in ('MEASUREMENT_START', 'MEASUREMENT_END', 'DRAIN_START', 'DRAIN_END'))
    grid = {i*10: a[i] for i in range(31, 150)} | {300: ms, 1500: me}
    windows = []
    for start in range(300, 901, 10):
        growth = used(grid[start+600]) - used(grid[start])
        denominator = observed_exposure(start, start+600)
        windows.append((growth, Fraction(growth, denominator), start))
    rates = sorted(x[1] for x in windows)
    active_rate = rates[-1]
    p95_rate = rates[ceil(Fraction(95 * len(rates), 100))-1]
    warm_growth = used(b['WARMUP_END']) - used(a[0])
    active_growth = used(me) - used(ms)
    drain_growth = used(de) - used(ds)
    warm_rate = Fraction(warm_growth, observed_exposure(0, 300))
    drain_rate = Fraction(drain_growth, observed_exposure(1500, 2100))
    phase_rate = Fraction(active_growth, observed_exposure(300, 1500))
    interval = json.loads((root / manifest_relative).read_bytes())['pacingIntervalNanos']
    if interval != 7117650000 or p['manifestEntry']['sha256'] != hashlib.sha256((root / manifest_relative).read_bytes()).hexdigest():
        raise ValueError('manifest identity drift')
    chains = value(me)['fullChainCompleted'] - value(ms)['fullChainCompleted']
    total = ceil(Fraction(3000 * 10**9, interval))
    warm_chains = ceil(Fraction(600 * 10**9, interval))
    active_chains = total - warm_chains
    formal = [exposure(interval, x, y) for x, y in [(0, 600), (600, 3000), (3000, 3600)]]
    # 使用整数上取整的同源比率供Java逐段积分；不是新的magic容量值。
    coefficients = {'warmupBytesPerOrderSecond': ceil(warm_rate),
                    'activeBytesPerOrderSecond': ceil(active_rate),
                    'drainBytesPerOrderSecond': ceil(drain_rate)}
    growths = [ceil(formal[i] * rate) for i, rate in enumerate(coefficients.values())]
    baseline = used(a[0])
    projected = baseline + sum(growths)
    reserve = coefficients['activeBytesPerOrderSecond'] * (1 + total) * 600
    capacity = ceil(Fraction(projected + reserve, MIB)) * MIB
    per_chain_naive = Fraction(active_growth, chains) * active_chains
    exposure_adjustment = (formal[1] / active_chains) / Fraction(observed_exposure(300, 1500), chains)
    adjusted_chain = per_chain_naive * exposure_adjustment
    phase_cross = phase_rate * formal[1]
    if adjusted_chain != phase_cross or active_rate < phase_rate or active_rate < p95_rate:
        raise ValueError('L6_PG_TMPFS_CAPACITY_MODEL_INCONSISTENT')
    return {
        'schemaVersion': 1, 'status': 'ACCEPTED', 'scope': 'L6_A_60MIN',
        'sourceHead': p['HEAD'], 'sourceStorageCalibrationRun': RUN,
        'sourceArtifactHashes': {Path(bundle_relative).as_posix(): hashlib.sha256(bundle.read_bytes()).hexdigest()},
        'sourceRawArchiveSha256': raw_manifest['archiveSha256'],
        'manifestSha256': p['manifestEntry']['sha256'],
        'observed': {'peakUsedBytes': max(used(x) for x in a + list(b.values())),
                     'startUsedBytes': baseline, 'maxRolling10MinuteGrowthBytes': max(x[0] for x in windows),
                     'drainGrowthBytes': drain_growth, 'measurementGrowthBytes': active_growth,
                     'measurementFullChainDelta': chains},
        'projection': {'model': MODEL, **coefficients, 'formalSeconds': [600, 2400, 600],
                       'maximumOrders': total, 'warmupMaximumOrders': warm_chains,
                       'activeMaximumOrders': active_chains, 'pacingIntervalNanos': interval,
                       'formalStartBaselineBytes': baseline,
                       'projectedWarmupGrowthBytes': growths[0],
                       'projected40minActiveGrowthBytes': growths[1],
                       'projected10minDrainGrowthBytes': growths[2],
                       'projectedFormalEndPeakBytes': projected, 'reserveBytes': reserve,
                       'backlogBytesPerChain': ceil(Fraction(active_growth, chains)),
                       'requiredCapacityBytes': capacity,
                       'reserveRationale': 'One additional 600s worst normalized healthy window at maximum formal inventory, outside the scheduled 3600s projection; conditional uncertainty allowance, not duplicated scheduled growth.',
                       'crossChecks': {'windows': len(windows), 'maxRateFraction': str(active_rate),
                                       'p95RateFraction': str(p95_rate), 'phaseRateFraction': str(phase_rate),
                                       'naivePerChainActiveBytes': ceil(per_chain_naive),
                                       'inventoryTimeAdjustedPerChainActiveBytes': ceil(adjusted_chain),
                                       'inventoryTimeAdjustedPhaseActiveBytes': ceil(phase_cross),
                                       'rollingUpperToPhaseRatio': str(active_rate / phase_rate),
                                       'reason': 'Per-chain naive extrapolation omits repeated audit over larger inventory and longer time. Inventory-time normalization reconciles chain and phase projections exactly. Rolling upper exceeds average because WAL allocation is bursty; upper and p95 are compared without smoothing away bursts.'}},
        'hostMemory': {'maximumFraction': 0.60, 'nqHeapEachBytes': 512*MIB,
                       'venueHeapBytes': 256*MIB, 'controllerHeapBytes': 512*MIB,
                       'mavenHeapBytes': 512*MIB, 'nativeAndToolsBudgetBytes': 2048*MIB,
                       'pgNonTmpfsBudgetBytes': 768*MIB,
                       'accounting': 'PG cgroup maximum = non-tmpfs allowance + tmpfs maximum; sum this cgroup budget once, not again with tmpfs. Heap plus explicit native/tools allowance is a conservative budget, not observed RSS.'},
        'samplingIntervalSeconds': 10,
        'limitations': ['L6B_CAPACITY_REQUIRES_SEPARATE_PROJECTION',
                        'Order-time upper envelope assumes accepted healthy workload semantics; runtime guard rejects newly observed higher comparable-window growth.',
                        'Native/tools budget is a conservative planning allowance, not an exact physical RSS measurement.',
                        'No claim of an unconditional storage bound under unobserved faults.']}


if __name__ == '__main__':
    print(json.dumps(calculate(*sys.argv[1:4]), ensure_ascii=False, indent=2))
