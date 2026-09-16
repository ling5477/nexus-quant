"""按纳秒重放绝对槽身份；缺槽是显式事实，不能替换成旧测量。"""
INTERVAL = 10_000_000_000
CONTRACT = {'schemaVersion': 1, 'intervalNanos': INTERVAL,
            'validity': 'TARGET_LE_START_LT_DEADLINE_AND_COMPLETION_LE_DEADLINE',
            'jitterObservationNanos': 2_000_000_000, 'sustainedInvalidSlots': 3,
            'isolatedGapDisposition': 'PENDING_FINAL_EVALUATION', 'acceptanceRequiresAllSlotsValid': True}


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def verify_rows(rows, contract, expected=1080):
    require(contract == CONTRACT, 'sampling authority drift')
    require(len(rows) == expected, 'logical sampling slot coverage')
    tokens = set()
    identity = rows[0]['sampleToken'].rsplit(':',1)[0] if rows else None
    measured = []
    counts = {'valid': 0, 'jitter': 0, 'overrun': 0, 'missed': 0}
    last_end = None
    last_overrun = False
    invalid = 0
    for i, row in enumerate(rows):
        target, deadline = i*INTERVAL, (i+1)*INTERVAL
        require(row['sampleIndex'] == i and row['sampleType'] == 'PERIODIC', 'slot identity')
        require(row['scheduledElapsedNanos'] == target and row['scheduledElapsedMillis'] == target/1e6, 'scheduled target')
        require(row['slotDeadlineNanos'] == deadline and row['slotDeadlineMillis'] == deadline/1e6, 'slot deadline')
        token = row['sampleToken']
        require(token not in tokens and token == identity+':'+str(i), 'duplicate sampling token')
        tokens.add(token)
        if row['status'] == 'MISSED_SLOT':
            require(row['slotStatus'] == 'MISSED_SLOT' and row['sources'] == {}, 'fabricated missed measurement')
            require(all(row[k] is None for k in ('actualStartElapsedNanos','actualStartElapsedMillis',
                                               'completionElapsedNanos','completionElapsedMillis','collectionMillis','sampledAt')),
                    'missed slot has invented timestamps')
            reason = row['reason']
            observed = row['detectedElapsedNanos']
            if reason == 'PREVIOUS_SLOT_OVERRUN':
                require(last_overrun and last_end == observed and target <= observed, 'unexplained overrun skip')
            else:
                require(reason in ('SCHEDULER_PASSED_COMPLETE_SLOT','RUN_ENDED_WITHOUT_SLOT') and observed >= deadline, 'unexplained missed slot')
            counts['missed'] += 1
            invalid += 1
        else:
            require(row['status'] == 'MEASURED', 'mandatory collection failure')
            begin, end = row['actualStartElapsedNanos'], row['completionElapsedNanos']
            require(target <= begin < deadline and end >= begin, 'sample start or completion invalid')
            require(row['elapsedMillis'] == begin//1_000_000, 'actual start identity')
            for key, value in {'actualStartElapsedMillis': begin/1e6, 'completionElapsedMillis': end/1e6,
                               'startLagMillis': (begin-target)/1e6, 'periodicSamplerLatenessMillis': (begin-target)/1e6,
                               'collectionMillis': (end-begin)/1e6, 'slotSlackMillis': (deadline-end)/1e6}.items():
                require(abs(row[key]-value) < .000001, 'sampling time arithmetic: '+key)
            if last_end is not None:
                require(begin >= last_end, 'overlapping sample collection')
                require(not last_overrun or target > last_end, 'catch-up after overrun')
            require(row['schedulerAttemptElapsedNanos'] <= begin and row['samplerMonitorWaitNanos'] >= 0
                    and row['lifecycleLockWaitNanos'] >= 0, 'sampling lock timing')
            require('controllerBefore' in row and 'controllerAfter' in row, 'controller diagnostic missing')
            source_end = begin
            for source in row['sources'].values():
                require(source['status'] == 'MEASURED', 'mandatory source unavailable')
                for key in ('sampleIndex','sampleToken','sampledAt','elapsedMillis','phase'):
                    require(source[key] == row[key], 'stale source identity')
                require(source_end <= source['collectionStartElapsedNanos'] <= source['collectionEndElapsedNanos'] <= end, 'collector time sequence')
                source_end = source['collectionEndElapsedNanos']
            if end > deadline:
                expected_status = 'SLOT_OVERRUN'; counts['overrun'] += 1; invalid += 1
                require(row['acceptanceImpact'] == 'PENDING_FINAL_EVALUATION', 'overrun acceptance impact')
            else:
                late = begin-target > CONTRACT['jitterObservationNanos']
                expected_status = 'VALID_WITH_JITTER' if late else 'VALID'
                counts['valid'] += 1; counts['jitter'] += int(late); invalid = 0
                require(row['acceptanceImpact'] == ('OBSERVATION' if late else 'NONE'), 'jitter disposition')
            require(row['slotStatus'] == expected_status, 'slot validity classification')
            measured.append(row); last_end = end; last_overrun = end > deadline
        require(invalid < CONTRACT['sustainedInvalidSlots'], 'sustained sampling degradation')
    return {'status': 'ACCEPTED' if not counts['missed'] and not counts['overrun'] else 'COMPLETED_NOT_ACCEPTED',
            'logicalSlots': len(rows), 'measuredSamples': len(measured), **counts,
            'maxStartLagMillis': max((r['startLagMillis'] for r in measured), default=None),
            'maxCollectionMillis': max((r['collectionMillis'] for r in measured), default=None),
            'minimumSlotSlackMillis': min((r['slotSlackMillis'] for r in measured), default=None)}
