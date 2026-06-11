# Roadmap

## 总路线

```text
DOC-CLEAN / BASELINE-FIX completed
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI：虚拟币量化 V1 完整闭环 completed
  ↓
GateJ：Paper Trading 稳定运行 completed
  ↓
GateK：AI 信号接入规划 ← NEXT
  ↓
GateL：AI Paper Trading
  ↓
GateM：AI 小资金 LIVE
  ↓
GateN：美股适配
  ↓
GateO：A 股适配
```

## 当前阶段

- DOC-CLEAN completed。
- BASELINE-FIX completed。
- GateH completed。
- GateI completed。
- GateJ-PLAN completed。
- GateJ-1-WO completed。
- GateJ-2-WO completed。
- GateJ-3-WO completed。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- GateJ completed。
- Next: GateK-PLAN。
- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。

## 路线原则

- 先把虚拟币做成完整 V1（GateI 已完成）。
- GateH 已完成交易工作台、历史行情接入和 dataset 绑定。
- GateI 已完成虚拟币量化 V1 完整闭环（策略版本、发布、回测追溯、评估增强、Paper Trading 运行闭环、风控回写、资金曲线、持仓曲线、交易复盘、异常停机）。
- GateJ 已完成 Paper Trading 稳定运行验收。
- GateK-PLAN 才允许开始规划 AI 信号接入，不能直接实现 AI 功能。
- GateL 进入 AI Paper Trading。
- GateM 才允许 AI 小资金 LIVE。
- 美股/A 股复用虚拟币 V1 沉淀的通用底座。

## 当前边界

- Current stage: GateJ completed。
- Next: GateK-PLAN。
- NQ / DH 三轮只读审计已完成；DH not integrated；GateK implementation not started；LIVE disabled。
- Integration-0 allowed only as contract / mock / documentation work line, not runtime integration；它是独立文档与契约工作线，不等于 GateK 实现，也不是真实集成。
- 不接入 AI。
- 不做 AI 信号。
- 不做 AI Paper Trading。
- 不做真实 LIVE 下单。
- 不接入 DH。
- 不新增多交易所扩展。
- 不做美股/A 股。
- 不做合约全量。
- 不做高频。
- 不做复杂因子平台。
- UI/UX professionalism remains post-freeze remediation。
