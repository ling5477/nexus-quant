# GateY Minimal Live Pilot Evidence Summary

## Exact scope

- Account scope：单账户、单 credential reference。
- Venue/instrument：OKX Spot / BTC-USDT。
- Order：BUY LIMIT，pilot cap `<= 10 USDT`。
- Control：人工受控、exactly-one PLACE、完整 reconciliation、无自动第二单。

## Execution and durable facts

- PLACE=1，PLACE retry=0，CANCEL=0。
- Order=`FILLED / LIVE`。
- Intent=`RECONCILED`，Receipt=`QUERY_CONFIRMED`。
- Trade=1，Ledger entries=4。
- Lease=`CLOSED`，activeLease=0。
- Session=`LIVE_RECONCILED`，Authority=`CLOSED`。
- Kill=`ENGAGED`，LIVE=false，runtime stopped。
- Transfer=0，Withdraw=0，Attempt-02=`NOT_CREATED`，Second PLACE=`NOT_EXECUTED`。

## Release and CI

Production pilot release=`8e3dd0cf6104eb85f36a0e434ca51ea9d903705a`，manifest SHA-256=`d49ca03a39df8e7de15a2bb03651381ce4c1df8db1682d63e285fdd37b61e046`。Final authority/document baseline=`65caaf7fd3038658b0f4f24566efd2960e606d43`，exact-head CI run=`32981327378 / completed / success / 10 jobs / bad=0`。

## Accepted limitation

`Order.externalOrderId=NULL`，但 receipt、Trade venue identity、reconciliation 与 ledger 已通过，P0=0/P1=0。该 P2 不阻断 freeze，也不允许修改生产 order 或补造事实。完整 durable clientOrderId 只保留在 production DB，未写入本 archive。
