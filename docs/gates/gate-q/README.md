# GateQ Historical Archive

本目录保存 GateQ 策略验证与 Paper / Shadow readiness baseline 的冻结、release tag、CI 证据和归档索引。这里是冻结后的历史证据位置，不是当前实现入口；当前事实入口仍以 `docs/current/` 的摘要文档为准。

## Archive state

- Archive task: `NQ-GATEQ-RELEASE-TAG-AND-ARCHIVE`.
- Archive status: **PASS / COMPLETED / RELEASE TAG PUSHED**。含义：`PASS`（通过）、`COMPLETED`（已完成）、`RELEASE TAG PUSHED`（release tag 已推送）。
- GateQ final state: **FROZEN / ACCEPTED / TAGGED**。含义：`FROZEN`（已冻结）、`ACCEPTED`（已接受）、`TAGGED`（已打 tag）。
- Release tag: `nq-gateq-freeze`.
- Tag message: `NexusQuant GateQ freeze: strategy validation and paper shadow readiness baseline`.
- CI preflight evidence: GitHub Actions `NQ CI Baseline` run `28763029176`，`status=completed`、`conclusion=success`、`headSha=9c8cbfe740751a1896cd6afdd04d1b9141531b10`。
- Physical archive: executed for GateQ closeout / readiness / plan evidence, GateQ-0..6 evidence matrix, API/frontend evidence index, testing summary, and boundary statement.
- Current authority documents remain in `docs/current/` as concise summaries and archive pointers.

## Archived documents

- [GATEQ_FREEZE_CLOSEOUT.md](GATEQ_FREEZE_CLOSEOUT.md)
- [GATEQ_FREEZE_READINESS_REVIEW.md](GATEQ_FREEZE_READINESS_REVIEW.md)
- [GATEQ_PLAN.md](GATEQ_PLAN.md)
- [GATEQ_BATCH_0_6_EVIDENCE_MATRIX.md](GATEQ_BATCH_0_6_EVIDENCE_MATRIX.md)
- [GATEQ_TESTING_EVIDENCE_SUMMARY.md](GATEQ_TESTING_EVIDENCE_SUMMARY.md)
- [GATEQ_API_AND_FRONTEND_EVIDENCE_INDEX.md](GATEQ_API_AND_FRONTEND_EVIDENCE_INDEX.md)
- [GATEQ_BOUNDARY_STATEMENT.md](GATEQ_BOUNDARY_STATEMENT.md)

## GateQ completed scope

- GateQ-0：GateQ Plan / Shadow Live readiness planning 已完成。
- GateQ-1：Strategy Evaluation Gate read-only baseline 已完成。
- GateQ-2：Paper vs Shadow Run read-only model / DTO baseline 已完成。
- GateQ-3：Shadow Live no-side-effect preview skeleton 已完成。
- GateQ-4：Python Evaluation Artifact Java Binding Contract 已完成。
- GateQ-5：Frontend Paper / Shadow Comparison View 已完成。
- GateQ-6：Strategy Lifecycle Trace View Enhancement 已完成。

## Boundary

- LIVE: **DISABLED**（关闭）。
- AI: **NOT STARTED**（未开始）。
- DH runtime: **NOT INTEGRATED**（未集成）。
- Integration-1: **NOT STARTED / mock-test-support only where applicable**（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient: **NOT IMPLEMENTED**（未实现）。
- real provider: **NOT IMPLEMENTED**（未实现）。
- private trading adapter: **NOT IMPLEMENTED**（未实现）。
- real permission probe: **NOT IMPLEMENTED**（未实现）。
- Shadow Live trading: **NOT ENABLED**（未启用）。
- Python ML ready: **NO**（否）。
- Python live execution ready: **NO**（否）。

## What GateQ does not mean

- Strategy Evaluation Gate 不代表 trading authorization。
- Paper vs Shadow Comparison 不代表 trading authorization。
- Shadow Live Preview 不代表 live execution ready。
- Python Artifact Binding Preview 不代表 ML ready 或 live execution ready。
- `/strategies/validation` 不代表交易台、AI 决策中心、实盘控制台或 Shadow Live 执行入口。
- GateQ `FROZEN / ACCEPTED / TAGGED` 只冻结只读验证、只读对照、no-side-effect preview、binding preview contract 与前端证据展示基线，不授权真实交易、LIVE、AI / DH runtime、real provider、private trading、permission probe 或 Shadow run 写侧 fact source。

## Current authority kept in docs/current

- [../../current/README.md](../../current/README.md)
- [../../current/STATUS.md](../../current/STATUS.md)
- [../../current/ROADMAP.md](../../current/ROADMAP.md)
- [../../current/TESTING.md](../../current/TESTING.md)
- [../../current/WORKLOG.md](../../current/WORKLOG.md)
- [../../current/FACT_SOURCE_INDEX.md](../../current/FACT_SOURCE_INDEX.md)
- [../../current/API.md](../../current/API.md)
- [../../current/DB_SCHEMA.md](../../current/DB_SCHEMA.md)

## Next stage

GateQ release tag and archive 完成后，下一阶段只能进入 **GateR PLAN / NOT STARTED**（GateR 仅规划 / 未开始）。不得在本归档线内启动 GateR implementation、LIVE、AI、DH runtime、RealClient、real provider、private trading adapter、real permission probe 或 Shadow Live runner。
