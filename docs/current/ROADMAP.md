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
GateK：规划 / 架构 / 产品化 / 部署化 / 可观测性 / 安全边界收口 ← NEXT
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
- GateK implementation not started。
- AI not started。
- DH integration not started / not connected to NQ。
- LIVE disabled。
- Multi-exchange expansion not started。

## 路线原则

- 先把虚拟币做成完整 V1（GateI 已完成）。
- GateH 已完成交易工作台、历史行情接入和 dataset 绑定。
- GateI 已完成虚拟币量化 V1 完整闭环（策略版本、发布、回测追溯、评估增强、Paper Trading 运行闭环、风控回写、资金曲线、持仓曲线、交易复盘、异常停机）。
- GateJ 已完成 Paper Trading 稳定运行验收。
- GateK-PLAN 用于规划 GateJ 后的事实源收口、架构与测试基线、前端产品化、CI / 可观测性 / 部署基线、安全 hardening 和 Integration-0 只读登记；不能直接实现 AI、DH runtime、LIVE、真实交易所扩展或真实 adapter。
- `NQ_CI_BASELINE_PLAN.md` 已作为 CI baseline 文档落档；Batch 1 已 implemented / first green confirmed，Batch 2 PostgreSQL / Flyway hardening 已完成并冻结，Batch 3 no-outbound guard 当前为 IMPLEMENTED / PENDING FIRST CI RUN，Batch 4 security guard / secret scan 与 Batch 5 frontend E2E hardening 仍 PENDING。
- `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` 已作为 Batch 3 plan / implementation baseline 落档；Batch 3B 已实现最小 workflow / test-scope no-outbound guard，但尚未取得 GitHub Actions first-run evidence，不得冻结。
- GateL 进入 AI Paper Trading。
- GateM 才允许 AI 小资金 LIVE。
- 美股/A 股复用虚拟币 V1 沉淀的通用底座。

## 当前边界

- Current stage: GateJ completed。
- Next: GateK-PLAN。
- NQ / DH 三轮只读审计已完成；DH not integrated；GateK implementation not started；AI not started；LIVE disabled。
- Integration-0 allowed only as contract / mock / documentation work line, not runtime integration；它是独立文档与契约工作线，不等于 GateK 实现，也不是真实集成。
- NQ-DH Integration-0 契约冻结已完成（contract / mock / docs）；下一步只允许 mock / contract test 设计或安全文档固化，禁止真实联调；真实通道必须等 Integration-1 并先修复 DH P1-4 残留（rate limit / memory cap / replay nonce 持久化）。
- NQ-DH Integration-0 mock / contract test 详细矩阵（15 项）已设计完成，contract test 代码已实现并通过 implementation review；**Integration-0 safety gate CLOSED / ACCEPTED**（见 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`）。
- 下一步只允许 Integration-1 planning-only audit / DH P1-4 residual fix planning / GateK-PLAN 文档规划；禁止直接 Integration-1 实现、真实只读通道、真实 HTTP、RealClient、Provider、LIVE、AI 自动交易。
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
