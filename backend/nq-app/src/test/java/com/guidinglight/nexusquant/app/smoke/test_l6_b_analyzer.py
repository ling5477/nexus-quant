"""防止跨代低值、重复GC及缺失数据制造稳定结论。"""
import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from l6_b_analyzer import generation_series, trend, progress, bound_manifest, resources, verify_analysis_identity


class RestartAggregationTest(unittest.TestCase):
    def test_analysis_dependency_and_head_are_bound(self):
        with tempfile.TemporaryDirectory() as folder:
            directory=Path(folder)
            manifest=directory/'manifest.json'
            manifest.write_text('{"noiseBands":{}}',encoding='utf-8')
            sha=hashlib.sha256(manifest.read_bytes()).hexdigest()
            proof={'HEAD':'h','sha256':'c','manifestEntry':{'sha256':sha}}
            inputs={'inputs/L6_B_RUNNER_CONTRACT.json':'c','inputs/L6_FORMAL_CALIBRATION_MANIFEST.json':sha}
            for name in ('l6_b_analyzer.py','l6_b_oracle.py','l6_oracle.py','l5_measurement.py','synthetic_evidence.py'):
                inputs['code/'+name]=hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest()
            entry=directory/'candidate-entry.json'
            entry.write_text(json.dumps({'HEAD':'h','files':inputs}),encoding='utf-8')
            verify_analysis_identity(directory,proof,manifest)
            with self.assertRaises(ValueError):verify_analysis_identity(directory,{**proof,'HEAD':'other'},manifest)
            inputs['code/l6_b_analyzer.py']='0'*64
            entry.write_text(json.dumps({'HEAD':'h','files':inputs}),encoding='utf-8')
            with self.assertRaises(ValueError):verify_analysis_identity(directory,proof,manifest)

    def test_replaced_noise_manifest_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'manifest.json'
            payload = b'{"noiseBands":{"heap":[{"range":1}]}}'
            path.write_bytes(payload)
            expected = hashlib.sha256(payload).hexdigest()
            self.assertEqual(bound_manifest(path, expected)['noiseBands']['heap'][0]['range'], 1)
            path.write_bytes(payload.replace(b':1', b':999999999'))
            with self.assertRaises(ValueError):bound_manifest(path, expected)

    def test_growth_is_not_cleared_by_new_pid(self):
        self.assertEqual(trend([10, 20, 30], 31, 2), 'LEAK_SUSPECT')
        self.assertEqual(trend([1, 1, 1], 1, 2), 'STABLE_WITHIN_OBSERVED_WINDOWS')
        self.assertEqual(trend([10, 20, 30], 11, 2), 'STABLE_WITHIN_OBSERVED_WINDOWS')
        self.assertEqual(trend([10, 20], 10, 2), 'INCONCLUSIVE')

    def test_natural_gc_identity_and_transition(self):
        registry = [{'logicalActor':'NQ-0','generation':0,'pid':11,'startTimestamp':'a'},
                    {'logicalActor':'NQ-1','generation':0,'pid':12,'startTimestamp':'b'}]
        def row(t):
            sources = {}
            for i, identity in enumerate(registry):
                sources['nq'+str(i)]={'values':{**identity,'lifecycle':'RUNNING','processAlive':True,
                    'measurementScope':'JVM_AND_LIFECYCLE','jvmUptimeMillis':t+5000,
                    'tickFailed':0,'acquisitionTimeoutDelta':0,'gc':[{'name':'young','lastGcStatus':'MEASURED',
                    'lastGc':{'id':3,'endUptimeMillis':9000,'heapUsedAfterGc':40}}]}}
            return {'elapsedMillis':t,'sources':sources}
        rows = [row(10000), row(20000)]
        groups, events = generation_series(rows, registry)
        self.assertEqual(len(groups), 2)
        self.assertTrue(all(len(v)==1 and v[0]['eventElapsedMillis']==4000 for v in events.values()))
        transition = copy.deepcopy(rows)
        transition[1]['sources']['nq0']['values'].update(lifecycle='DOWN',measurementScope='LIFECYCLE_ONLY')
        self.assertEqual(len(generation_series(transition, registry)[0][('NQ-0',0,11)]),1)
        transition[1]['sources']['nq0']['values']['heapUsed']=0
        with self.assertRaises(ValueError):generation_series(transition, registry)
        rows[1]['sources']['nq0']['values']['pid']=999
        with self.assertRaises(ValueError):generation_series(rows, registry)

    def test_duplicate_generation_and_empty_business_window_rejected(self):
        with self.assertRaises(ValueError):generation_series([], [{'logicalActor':'NQ-0','generation':0,'pid':11}]*2)
        with self.assertRaises(ValueError):progress([],[],[],[])

    def test_full_generation_windows_and_old_pid_growth_remain_visible(self):
        registry = [{'logicalActor':'NQ-'+str(a),'generation':g,'pid':10+a*10+g,'startTimestamp':str(a)+str(g)}
                    for a,g in ((0,0),(1,0),(0,1),(1,1),(0,2))]
        rows=[]
        fields=('threads','active','idle','pending','poolMax','commandQueue.queueSize','metricsExecutor.queueSize')
        noise={'resources':[{'source':'nq'+str(a),'field':'.'+f,'maxMinusMin':0} for a in (0,1) for f in fields],
               'heap':[{'owner':'nq'+str(a),'collector':'young','range':2} for a in (0,1)]}
        noise['resources'] += [{'source':s,'field':'.'+f,'maxMinusMin':0} for s, fs in
                                {'os':['handles'],'venue':['active','queue'],'postgres':['appConnections','databaseConnections','idleInTransaction']}.items() for f in fs]
        for t in range(0,10_800_000,10000):
            sources={'os':{'values':{'processes':[{'Id':90,'HandleCount':10},{'Id':91,'HandleCount':10}]}},
                     'venue':{'values':{'active':0,'queue':0}},'postgres':{'values':{'appConnections':20,'databaseConnections':22,'idleInTransaction':0}}}
            for a in (0,1):
                g=(0 if t<2_400_000 else 1 if t<7_200_000 else 2) if a==0 else (0 if t<4_800_000 else 1)
                identity=next(x for x in registry if x['logicalActor']=='NQ-'+str(a) and x['generation']==g)
                value={**identity,'lifecycle':'RUNNING','processAlive':True,'measurementScope':'JVM_AND_LIFECYCLE',
                       'jvmUptimeMillis':t+5000,'heapUsed':100,'heapCommitted':200,'heapMax':500,'threads':24,'peakThreads':24,
                       'active':0,'idle':10,'pending':0,'poolMax':10,'tickFailed':0,'acquisitionTimeoutDelta':0,
                       'commandQueue':{'queueSize':0},'metricsExecutor':{'queueSize':0},
                       'candidateAge':{'eligibleCandidateCount':0,'oldestCandidateAgeMillis':None},
                       'gc':[{'name':'young','lastGcStatus':'MEASURED','lastGc':{'id':t//10000,'endUptimeMillis':t+5000,'heapUsedAfterGc':40}}]}
                sources['nq'+str(a)]={'values':value}
                sources['os']['values']['processes'].append({'Id':identity['pid'],'HandleCount':10})
            rows.append({'elapsedMillis':t,'phase':'DRAIN' if t>=10_200_000 else 'ACTIVE','sources':sources})
        self.assertEqual(resources(rows, registry, noise)['findings'], [])
        for row in rows:
            v=row['sources']['nq0']['values']
            if v['generation']==0:
                v['gc'][0]['lastGc']['heapUsedAfterGc'] += 10*(row['elapsedMillis']//600_000)
        self.assertIn('NQ-0/g0/heap: LEAK_SUSPECT',resources(rows, registry, noise)['findings'])


if __name__ == '__main__':
    unittest.main()
