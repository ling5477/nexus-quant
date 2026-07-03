# NexusQuant 当前工单

> NQ 当前主线：GateO current work line remains separate and is not overwritten by this P0 factsource rebase
> NQ-DH 集成文档线：NQ-DH-I1-M0-CONTRACT-GAP-CLOSE-WO / COMPLETED / WORK_ORDER_ONLY / CONTRACT_GAP_CLOSED / NOT IMPLEMENTED
> 下一集成文档任务：NQ-DH-I1-M1-DH-DRYRUN-CONTRACT-ENTRY-MOCK-WO / NOT STARTED

## 1. 当前工单结论

`NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN`、`NQ-DH-I1-P2-CONTRACT-FIXTURES-PLAN` 与 `NQ-DH-I1-P3-DRYRUN-IMPLEMENTATION-READINESS-PLAN` 已完成 planning-only 收口；`NQ-DH-I1-P4-IMPLEMENTATION-GATE-REVIEW-FIX` 已完成 docs-only gate-fix；`NQ-DH-I1-DRYRUN-MOCK-IMPLEMENTATION-WO` 已完成 work-order-only 批次拆分；`NQ-DH-I1-M0-CONTRACT-GAP-CLOSE-WO` 已完成 contract gap close work order。本工单记录 NQ 侧 Integration-1 contract dry-run、fixtures、readiness、gate-fix、dry-run mock WO 与 M0 gap close 规划线，不覆盖 GateO 当前主线，不启动 Integration-1 implementation 或 runtime。P1 canonical 计划为 `docs/current/NQ_DH_INTEGRATION1_DRYRUN_CONTRACT_PLAN.md`；P2 canonical 计划为 `docs/current/NQ_DH_INTEGRATION1_CONTRACT_FIXTURES_PLAN.md`；P3 canonical 计划为 `docs/current/NQ_DH_INTEGRATION1_DRYRUN_IMPLEMENTATION_READINESS_PLAN.md`；dry-run mock WO 为 `docs/current/NQ_DH_INTEGRATION1_DRYRUN_MOCK_IMPLEMENTATION_WO.md`；M0 工单为 `docs/current/NQ_DH_INTEGRATION1_M0_CONTRACT_GAP_CLOSE_WO.md`；旧 `docs/current/NQ_DH_INTEGRATION1_DRYRUN_PLAN.md` 仅作为 P1 初稿 / residual reference。

当前 Integration-1 dry-run 前置条件固定为：

```text
NQ GateN + DH Stage4 Decision Pipeline MVP CLOSED
```

不得写成：

```text
NQ GateN + DH GateK CLOSED
```

## 2. 当前状态

```text
NQ Integration-1 rebase input: GateN no-real public marketdata / exchange sandbox frozen baseline.
DH prerequisite: DH Stage4 Decision Pipeline MVP CLOSED / ACCEPTED.
Integration-1 dry-run plan baseline: ACCEPTED.
I1-P0 factsource rebase: CLOSED / ACCEPTED.
I1-P1 contract dry-run plan: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
I1-P2 contract fixtures plan: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
I1-P3 dry-run implementation readiness plan: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
I1-P4 implementation gate review fix: COMPLETED / DOCS-ONLY / GATE-FIX.
I1 dry-run mock implementation work order: COMPLETED / WORK_ORDER_ONLY / NOT IMPLEMENTED.
I1-M0 contract gap close work order: COMPLETED / WORK_ORDER_ONLY / CONTRACT_GAP_CLOSED / NOT IMPLEMENTED.
Next concrete action: NQ-DH-I1-M1-DH-DRYRUN-CONTRACT-ENTRY-MOCK-WO / NOT STARTED.
Integration-1 implementation: NOT STARTED.
Integration-1 runtime: NOT STARTED.
Runtime integration: NOT STARTED.
Real HTTP: NOT STARTED.
Real provider: NOT STARTED.
DH integrated: NO.
AI / Agent runtime: NOT STARTED.
LangGraph runtime: NOT STARTED.
LIVE: DISABLED.
```

## 3. 允许范围

```text
docs/current/README.md
docs/current/STATUS.md
docs/current/ROADMAP.md
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/WORK_ORDER.md
docs/current/NQ_DH_INTEGRATION1_DRYRUN_CONTRACT_PLAN.md
docs/current/NQ_DH_INTEGRATION1_DRYRUN_PLAN_REBASEN.md
docs/current/NQ_DH_INTEGRATION1_CONTRACT_FIXTURES_PLAN.md
docs/current/NQ_DH_INTEGRATION1_DRYRUN_IMPLEMENTATION_READINESS_PLAN.md
docs/current/NQ_DH_INTEGRATION1_DRYRUN_MOCK_IMPLEMENTATION_WO.md
docs/current/NQ_DH_INTEGRATION1_M0_CONTRACT_GAP_CLOSE_WO.md
```

后续如启动 P4，仍只能是 implementation gate review；必须单独授权，并继续保持 no runtime、no real HTTP、no provider、no LIVE。

## 4. 禁止范围

```text
backend/**
frontend/**
research/**
scripts/**
deploy/**
.github/**
contracts/**
golden_cases/**
任何 migration
任何 API / Controller
任何真实 HTTP / WebSocket
任何真实 DH runtime 或 NQ runtime integration
任何 RealClient / real provider / real permission probe
任何 credential / token / cookie / API secret / passphrase 读取或输出
任何 LIVE、下单、撤单、转账、提现、订单状态、风控、账本或 Paper Run mutation
```

## 5. Readiness decision

```text
ALLOW_I1_P0_CLOSE: YES
ALLOW_I1_P1_CONTRACT_PLAN: YES / COMPLETED / PLAN ONLY
ALLOW_I1_P2_CONTRACT_FIXTURES_PLAN_CLOSE: YES
ALLOW_I1_P3_DRYRUN_IMPLEMENTATION_READINESS_PLAN_CLOSE: YES
ALLOW_I1_P4_IMPLEMENTATION_GATE_REVIEW_FIX_CLOSE: YES
ALLOW_I1_P4_RETRY: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_WORK_ORDER: YES
ALLOW_I1_M0_CONTRACT_GAP_CLOSE_WO: YES / COMPLETED
ALLOW_I1_M1_DH_DRYRUN_CONTRACT_ENTRY_MOCK_WO: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_CODE: NO
ALLOW_P1_IMPLEMENTATION_FROM_THIS_TASK: NO
ALLOW_INTEGRATION1_DRYRUN_IMPLEMENTATION: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 6. 下一步

```text
NQ-DH-I1-M1-DH-DRYRUN-CONTRACT-ENTRY-MOCK-WO / NOT STARTED
```

下一步只能输出 DH M1 dry-run contract entry mock work order / review；NQ 侧只消费 M1 结果，不得创建真实 client，不得真实 HTTP，不得直接实现 runtime，不得修改 schema/contracts/golden_cases/fixture JSON。implementation 前必须先完成独立 implementation review，确认 no runtime、no provider、no LIVE、no credential、no order / risk / ledger / Paper Run mutation。
