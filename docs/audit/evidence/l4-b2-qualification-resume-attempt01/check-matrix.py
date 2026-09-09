from pathlib import Path
import json,re,sys,hashlib
out=Path(sys.argv[1]);root=Path.cwd();log=(out/'targeted.log').read_text(encoding='utf-8',errors='replace');m=re.search(r'B2_MATRIX_CONTROLLER pid=(\d+) evidence=(.+)',log)
if not m:raise SystemExit('matrix not started')
folder=Path(m[2].strip());proofs=[]
for p in sorted(folder.glob('*/proof.json')):
 d=json.loads(p.read_text(encoding='utf-8'));assert d['result']=='PASS'; db=d['finalDb'];o=db['orders'][0]
 assert d['afterRecovery']==d['afterReplay'];assert len(db['trades'])==len(d['finalVenue']['fills'])
 assert all(t['trade_env']==d['orderEnvironment'] for t in db['trades'])
 proofs.append({'scenario':d['scenario'],'environment':d['orderEnvironment'],'repeat':d['repeat'],'pid':d['nqPid'],'restartPid':d.get('restartPid'),'venuePid':d['venuePid'],'database':d['database'],'status':o['status'],'version':o['version'],'executedQty':d['executedQty'],'remainingQty':d['remainingQty'],'trades':len(db['trades']),'ledger':len(db['ledger_entries']),'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
(out/'matrix-summary.json').write_text(json.dumps(proofs,indent=2),encoding='utf-8');print('verified proofs',len(proofs));print('last',proofs[-1] if proofs else {})
