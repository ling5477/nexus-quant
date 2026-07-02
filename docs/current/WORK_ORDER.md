# NexusQuant 当前工单

> NQ 当前主线：GateO current work line remains separate and is not overwritten by this P0 factsource rebase
> NQ-DH 集成文档线：NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN / COMPLETED / PLAN ONLY / NOT IMPLEMENTED
> 下一集成文档任务：NQ-DH-I1-P1-A-CONTRACT-SCHEMA-FIXTURE-PLAN-REVIEW / NOT STARTED

## 1. 当前工单结论

`NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN` 已完成 planning-only 收口。本工单记录 NQ 侧 Integration-1 contract dry-run 规划线，不覆盖 GateO 当前主线，不启动 Integration-1 implementation 或 runtime。

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
docs/current/NQ_DH_INTEGRATION1_DRYRUN_PLAN.md
docs/current/NQ_DH_INTEGRATION1_DRYRUN_PLAN_REBASEN.md
```

后续如启动 P1-A，仍只能是 contract schema / fixture plan review；必须单独授权，并继续保持 no runtime、no real HTTP、no provider、no LIVE。

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
ALLOW_I1_P1_A_CONTRACT_SCHEMA_FIXTURE_PLAN_REVIEW: YES
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
NQ-DH-I1-P1-A-CONTRACT-SCHEMA-FIXTURE-PLAN-REVIEW / NOT STARTED
```

下一步只能 review / refine dry-run schema、fixture catalog、forbidden field list、mock-only validation 和 no-side-effect 测试计划；不得直接实现 runtime。implementation 前必须先完成独立 implementation review，确认 no runtime、no provider、no LIVE、no credential、no order / risk / ledger / Paper Run mutation。
