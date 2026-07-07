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
GateR-0 planning completed
  ↓
GateR-1 migration plan review completed
  ↓
GateR-2..8 completed / pushed / CI green
  ↓
GateR freeze closeout
```

## 当前阶段

- GateQ final state: `FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag: `nq-gateq-freeze`。
- GateQ archive pointer: `docs/gates/gate-q/README.md`。
- GateP final state: `FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateO 及更早 Gate：只作为历史证据读取。
- GateR: `READY FOR FREEZE CLOSEOUT / NOT FROZEN / NOT ACCEPTED`（可进入冻结收口 / 未冻结 / 未接受）。
- GateR-1: `NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。
- GateR-2: Shadow Run local fact model / `V32` / repository 已完成并接受。
- GateR-3: Shadow Run runner skeleton 已完成；不是 scheduler 或后台 runner。
- GateR-4: decision trace / risk snapshot / order intent preview 已完成。
- GateR-5: shadow consistency report service 已完成。
- GateR-6: Shadow Run read-only API 已完成；没有写接口。
- GateR-7: Shadow Run detail / replay view 已完成；没有执行按钮。
- GateR-8: Shadow Run list / entrypoint 已完成并 push；最新 GitHub Actions run `28845427780`（`NQ CI Baseline`）为 `success`（成功）。

## 下一步规则

下一步只能进入 GateR freeze closeout。该 closeout 只做 freeze readiness 收口、证据矩阵和边界确认，不新增 GateR-9，不新增功能，不新增 migration，不新增 API，不新增页面，不启动 scheduler 或后台 runner，不开启 LIVE，不接 AI / DH runtime，不实现 RealClient、real provider、private trading adapter 或 real permission probe。

## 当前边界

- LIVE: `DISABLED`（关闭）。
- AI: `NOT STARTED`（未开始）。
- DH runtime: `NOT INTEGRATED`（未集成）。
- Integration-1: `NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe: `NOT IMPLEMENTED`（未实现）。
- Shadow Live runner: `NOT STARTED`（未开始）。
- Shadow Run local fact table / record: `IMPLEMENTED AS LOCAL DIAGNOSTIC FACT ONLY`（仅作为本地诊断事实已实现）。
- Shadow Run read-only API: `IMPLEMENTED`（已实现）；写接口仍禁止。
- Shadow Run frontend list / detail / replay view: `IMPLEMENTED`（已实现）；执行按钮仍禁止。
- Shadow Run runner skeleton: `IMPLEMENTED`（已实现）；scheduler、后台 runner 和 Shadow Live runner 仍未启动。

## 当前不做

- 不把 GateR 写成 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- 不新增 GateR-9 或新功能。
- 不启动 scheduler、后台 runner 或 Shadow Live runner。
- 不新增 Shadow Run 写接口或执行按钮。
- 不开启 LIVE。
- 不接 AI runtime。
- 不接 DH runtime。
- 不实现 RealClient、real provider、private trading adapter 或 real permission probe。
- 不把 readiness / preview / comparison / archive closeout 写成 trading authorization。
- 不新增后端、前端、research、scripts、deploy、workflow、migration、API、页面或测试。
