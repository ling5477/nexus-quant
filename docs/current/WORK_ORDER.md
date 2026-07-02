# NexusQuant 当前工单

> NQ 当前主线：GateO current work line remains separate and is not overwritten by this P0 factsource rebase
> NQ-DH 集成文档线：NQ-DH-I1-P0-FACTSOURCE-REBASE-CONTINUE / CLOSED / ACCEPTED
> 下一集成文档任务：NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN / NOT STARTED

## 1. 当前工单结论

`NQ-DH-I1-P0-FACTSOURCE-REBASE-CONTINUE` 已完成 docs-only / factsource-only 收口。本工单记录 NQ 侧 Integration-1 dry-run 文档线，不覆盖 GateO 当前主线，不启动 Integration-1 implementation 或 runtime。

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
I1-P1 contract dry-run plan: NOT STARTED.
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
docs/current/NQ_DH_INTEGRATION1_DRYRUN_PLAN_REBASEN.md
```

P1 如启动，仍只能是 plan-only / contract-dryrun-plan；必须单独授权，并继续保持 no runtime、no real HTTP、no provider、no LIVE。

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
ALLOW_I1_P1_CONTRACT_PLAN: YES
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
NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN / NOT STARTED
```

P1 只能规划 dry-run request / response 合同、禁止字段、安全 header、no-outbound/no-live-trade 约束和 mock-only validation；不得实现 runtime。
