"""独立合成槽证据，验证接受边界和拒绝伪造、补拍、持续退化。"""
import copy
import unittest
from l6_b_sampling import CONTRACT, INTERVAL, verify_rows


def measured(i, lag=0, cost=700_000_000):
    target=i*INTERVAL;begin=target+lag;end=begin+cost
    state='SLOT_OVERRUN' if end>target+INTERVAL else 'VALID_WITH_JITTER' if lag>2_000_000_000 else 'VALID'
    r={'sampleIndex':i,'sampleType':'PERIODIC','sampleToken':'run:'+str(i),'status':'MEASURED','slotStatus':state,
       'scheduledElapsedNanos':target,'scheduledElapsedMillis':target/1e6,'slotDeadlineNanos':target+INTERVAL,
       'slotDeadlineMillis':(target+INTERVAL)/1e6,'actualStartElapsedNanos':begin,'actualStartElapsedMillis':begin/1e6,
       'elapsedMillis':begin//1_000_000,'completionElapsedNanos':end,'completionElapsedMillis':end/1e6,
       'startLagMillis':lag/1e6,'periodicSamplerLatenessMillis':lag/1e6,'collectionMillis':cost/1e6,
       'slotSlackMillis':(target+INTERVAL-end)/1e6,'sampledAt':'now','phase':'ACTIVE',
       'acceptanceImpact':'PENDING_FINAL_EVALUATION' if state=='SLOT_OVERRUN' else 'OBSERVATION' if state=='VALID_WITH_JITTER' else 'NONE',
       'schedulerAttemptElapsedNanos':begin,'samplerMonitorWaitNanos':0,'lifecycleLockWaitNanos':0,
       'controllerBefore':{},'controllerAfter':{}}
    r['sources']={'fixture':{k:r[k] for k in ('sampleIndex','sampleToken','sampledAt','elapsedMillis','phase')}}
    r['sources']['fixture'].update(status='MEASURED',collectionStartElapsedNanos=begin,collectionEndElapsedNanos=end)
    return r


def missing(i, detected, reason='SCHEDULER_PASSED_COMPLETE_SLOT'):
    r=measured(i)
    r.update(status='MISSED_SLOT',slotStatus='MISSED_SLOT',sources={},reason=reason,detectedElapsedNanos=detected)
    for k in ('actualStartElapsedNanos','actualStartElapsedMillis','completionElapsedNanos','completionElapsedMillis','collectionMillis','sampledAt'):r[k]=None
    return r


class Sampling(unittest.TestCase):
    def test_5421_jitter_is_valid_and_absolute_targets_are_preserved(self):
        rows=[measured(0,5_421_000_000),measured(1)]
        result=verify_rows(rows,CONTRACT,2)
        self.assertEqual(result['status'],'ACCEPTED');self.assertEqual(result['jitter'],1)
        self.assertEqual(result['minimumSlotSlackMillis'],3879)

    def test_overrun_skip_is_completed_not_accepted_and_no_catch_up(self):
        first=measured(0,5_421_000_000,5_000_000_000)
        rows=[first,missing(1,first['completionElapsedNanos'],'PREVIOUS_SLOT_OVERRUN'),measured(2)]
        self.assertEqual(verify_rows(rows,CONTRACT,3)['status'],'COMPLETED_NOT_ACCEPTED')
        with self.assertRaises(ValueError):verify_rows([first,measured(1,500_000_000),measured(2)],CONTRACT,3)

    def test_missed_has_no_old_values_and_sustained_loss_rejects(self):
        rows=[measured(0),missing(1,22_500_000_000),measured(2,2_500_000_000)]
        self.assertEqual(verify_rows(rows,CONTRACT,3)['missed'],1)
        bad=copy.deepcopy(rows);bad[1]['actualStartElapsedNanos']=10_000_000_000
        with self.assertRaises(ValueError):verify_rows(bad,CONTRACT,3)
        with self.assertRaises(ValueError):verify_rows([missing(i,35_000_000_000) for i in range(3)],CONTRACT,3)

    def test_forged_time_stale_source_and_duplicate_identity_reject(self):
        for field,value in [('completionElapsedMillis',9000),('scheduledElapsedMillis',1),('slotStatus','VALID_WITH_JITTER')]:
            r=measured(0);r[field]=value
            with self.assertRaises(ValueError):verify_rows([r],CONTRACT,1)
        r=measured(0);r['sources']['fixture']['sampleToken']='old'
        with self.assertRaises(ValueError):verify_rows([r],CONTRACT,1)
        with self.assertRaises(ValueError):verify_rows([measured(0),measured(0)],CONTRACT,2)

    def test_exact_completion_boundary_and_collection_failure(self):
        self.assertEqual(verify_rows([measured(0,cost=INTERVAL)],CONTRACT,1)['status'],'ACCEPTED')
        self.assertEqual(verify_rows([measured(0,cost=INTERVAL+1)],CONTRACT,1)['status'],'COMPLETED_NOT_ACCEPTED')
        r=measured(0);r['sources']['fixture']['status']='UNAVAILABLE'
        with self.assertRaises(ValueError):verify_rows([r],CONTRACT,1)
        with self.assertRaises(ValueError):verify_rows([measured(0)],{**CONTRACT,'intervalNanos':2*INTERVAL},1)


if __name__=='__main__':unittest.main()
