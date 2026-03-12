# ADR-003：REST-first，WS 只做加速

## 决策
GateD 继续坚持 REST-first，WS 只做加速与更快的事件通知，不作为唯一事实来源。

## 原因
- WS 天生脆，适合提速，不适合独自扛真相
- REST reconcile 与 recovery 才是长期兜底

## 影响
- 断连与鉴权失败时必须触发受限窗口 reconcile
- Trade / ledger 幂等必须能抵御 WS + REST 并发回报

