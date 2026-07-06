# Roadmap

## 总路线

```text
DOC-CLEAN / BASELINE-FIX completed
  ↓
GateH completed
  ↓
GateI completed
  ↓
GateJ completed
  ↓
GateK finalized / frozen / tagged
  ↓
GateL completed as No-Real Exchange / MarketData Readiness
  ↓
GateM finalized / frozen / accepted / tagged
  ↓
GateN finalized / frozen / accepted / closed / tagged
  ↓
GateO frozen / accepted
  ↓
GateP frozen / accepted / tagged / archived
  ↓
GateQ frozen / accepted / tagged / archived
  ↓
GateR PLAN READY / NOT IMPLEMENTED
  ↓
GateR-1 MIGRATION PLAN READY / NOT IMPLEMENTED
```

## 当前阶段

- GateQ final state: `FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag: `nq-gateq-freeze`。
- GateQ archive pointer: `docs/gates/gate-q/README.md`。
- GateP final state: `FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateO 及更早 Gate：只作为历史证据读取。
- GateR: `NQ-GATER-PLAN-SHADOW-RUN-OPERATIONALIZATION：PLAN READY / NOT IMPLEMENTED`（计划已就绪 / 未实现）。
- GateR-1: `NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。

## 下一步规则

下一步只能进入单独的 GateR-2 Shadow Run local fact model / repository implementation。GateR-2 可基于 GateR-1 review 的 4 表方案准备 migration 和 repository，但仍必须另起任务、明确 allowed files、补充 migration review evidence 和回归验证；不得把 GateR-1 review 直接解释为 migration 已实现或 Shadow runner 已启动。

## 当前边界

- LIVE: `DISABLED`（关闭）。
- AI: `NOT STARTED`（未开始）。
- DH runtime: `NOT INTEGRATED`（未集成）。
- Integration-1: `NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe: `NOT IMPLEMENTED`（未实现）。
- Shadow Live runner: `NOT STARTED`（未开始）。
- Shadow run 写侧 fact source / local fact table / record: `NOT IMPLEMENTED`（未实现）。
- Shadow Run migration: `NOT IMPLEMENTED`（未实现）。

## 当前不做

- 不启动 GateR-2 implementation，除非后续单独授权。
- 不把 GateR planning 写成 GateR implementation。
- 不把 GateR-1 migration plan 写成 migration implemented。
- 不创建 Shadow Run table 或 Shadow Run record。
- 不开启 LIVE。
- 不接 AI runtime。
- 不接 DH runtime。
- 不实现 RealClient、real provider、private trading adapter 或 real permission probe。
- 不把 readiness / preview / comparison / archive closeout 写成 trading authorization。
- 不新增后端、前端、research、scripts、deploy、workflow、migration、API、页面或测试。
