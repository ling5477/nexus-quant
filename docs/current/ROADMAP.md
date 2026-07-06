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
GateR PLAN / NOT STARTED
```

## 当前阶段

- GateQ final state: `FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag: `nq-gateq-freeze`。
- GateQ archive pointer: `docs/gates/gate-q/README.md`。
- GateP final state: `FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateO 及更早 Gate：只作为历史证据读取。
- GateR: `PLAN / NOT STARTED`（规划 / 未开始）。

## 下一步规则

下一步只能由用户另起 GateR planning 任务并明确授权。默认不新增 `GATER_PLAN.md`、不启动 implementation、不新增 API、不新增 migration、不改 CI、不新增页面、不新增测试。

## 当前边界

- LIVE: `DISABLED`（关闭）。
- AI: `NOT STARTED`（未开始）。
- DH runtime: `NOT INTEGRATED`（未集成）。
- Integration-1: `NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe: `NOT IMPLEMENTED`（未实现）。
- Shadow Live runner / Shadow run 写侧 fact source: `NOT STARTED`（未开始）。

## 当前不做

- 不启动 GateR implementation。
- 不开启 LIVE。
- 不接 AI runtime。
- 不接 DH runtime。
- 不实现 RealClient、real provider、private trading adapter 或 real permission probe。
- 不把 readiness / preview / comparison / archive closeout 写成 trading authorization。
- 不新增后端、前端、research、scripts、deploy、workflow、migration、API、页面或测试。
