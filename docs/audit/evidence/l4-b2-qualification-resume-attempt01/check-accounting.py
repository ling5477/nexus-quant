from pathlib import Path
from decimal import Decimal as D
import json,sys
out=Path(sys.argv[1]);rows=json.loads((out/'matrix-summary.json').read_text());results=[]
for row in rows:
 p=Path(row['path']);d=json.loads(p.read_text(),parse_float=D);db=d['finalDb'];fills=d['finalVenue']['fills'];entries=db['ledger_entries'];qty=sum((D(f['fillSz']) for f in fills),D(0))
 assert qty==D(d['executedQty']);assert D(10)-qty==D(d['remainingQty'])
 assert len({t['exchange_trade_id'] for t in db['trades']})==len(fills)
 if fills:
  position=db['positions'][0];assert D(str(position['qty']))==qty;assert D(str(position['available_qty']))==qty;assert D(str(position['avg_price']))==100
  latest=max((s for s in db['account_snapshots'] if s['currency']=='BTC'),key=lambda s:s['snapshot_id']);assert D(str(latest['balance']))==qty;assert D(str(latest['available']))==qty;assert D(str(latest['frozen']))==0
  assert all(D(str(s['balance']))==0 for s in db['account_snapshots'] if s['currency']=='USDT')
 else:assert len(entries)==0 and len(db['positions'])==0
 for fill in fills:
  trade=next(t for t in db['trades'] if t['exchange_trade_id']==fill['tradeId']);ts=[e for e in entries if e['ref_id']==trade['trade_id']]
  amount=D(fill['fillPx'])*D(fill['fillSz']);fee=abs(D(fill['fee']));prefix=trade['trade_id']+':LEDGER:'
  expected={prefix+'1':-amount,prefix+'2':amount}
  if fee:expected.update({prefix+'FEE_1':-fee,prefix+'FEE_2':fee})
  assert {e['idempotency_key']:D(str(e['delta'])) for e in ts}==expected
  assert all(e['currency']==fill['feeCcy']=='USDT' and e['direction']==('DEBIT' if D(str(e['delta']))<0 else 'CREDIT') for e in ts)
  assert sum((D(str(e['delta'])) for e in ts),D(0))==0
 assert sum((D(str(e['delta'])) for e in entries),D(0))==0
 results.append({'scenario':d['scenario'],'environment':d['orderEnvironment'],'repeat':d['repeat'],'basePositionNet':str(qty),'quoteLedgerNet':'0','feeTotal':str(sum((abs(D(f['fee'])) for f in fills),D(0))),'perFillEntriesAndAccountProjections':'PASS'})
(out/'accounting-verification.json').write_text(json.dumps(results,indent=2),encoding='utf-8');print('accounting proofs checked',len(results))
