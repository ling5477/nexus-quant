# GateP Historical Archive

本目录保存 GateP 真实数据质量与交易准备阶段的冻结、release tag、CI 证据和归档索引。这里是冻结后的历史证据位置，不是当前实现入口；当前事实入口仍以 `docs/current/` 的摘要文档为准。

## Archive state

- Archive task: `NQ-GATEP-RELEASE-TAG-AND-ARCHIVE`.
- Archive status: **PASS / COMPLETED / RELEASE TAG PUSHED**。含义：`PASS`（通过）、`COMPLETED`（已完成）、`RELEASE TAG PUSHED`（release tag 已推送）。
- GateP final state: **FROZEN / ACCEPTED / TAGGED**。含义：`FROZEN`（已冻结）、`ACCEPTED`（已接受）、`TAGGED`（已打 tag）。
- Release tag: `nq-gatep-freeze`.
- Tag object: `ae94f7a47a3e7604efe061bf9be9ed48d2b98aa9`.
- Tagged commit: `3650714ae9cd441e59eb5b09c605a14bbc9998dc` (`chore(gatep): freeze baseline and stabilize research quality gate`).
- CI evidence: GitHub Actions `NQ CI Baseline` run `28714258374`，`status=completed`、`conclusion=success`、`headSha=3650714ae9cd441e59eb5b09c605a14bbc9998dc`。
- Physical archive: executed for GateP closeout/readiness evidence and GateP Batch 1-6A evidence indexes.
- Current authority documents remain in `docs/current/` as concise summaries and archive pointers.

## Archived documents

- [GATEP_FREEZE_CLOSEOUT_REVIEW.md](GATEP_FREEZE_CLOSEOUT_REVIEW.md)
- [GATEP_FREEZE_READINESS_REVIEW.md](GATEP_FREEZE_READINESS_REVIEW.md)
- [GATEP_FREEZE_CLOSEOUT_EVIDENCE_INDEX.md](GATEP_FREEZE_CLOSEOUT_EVIDENCE_INDEX.md)
- [GATEP_BATCH_1_6A_EVIDENCE_MATRIX.md](GATEP_BATCH_1_6A_EVIDENCE_MATRIX.md)
- [GATEP_TESTING_EVIDENCE_SUMMARY.md](GATEP_TESTING_EVIDENCE_SUMMARY.md)

## Boundary

- LIVE: **DISABLED**（关闭）。
- AI: **NOT STARTED**（未开始）。
- DH runtime: **NOT INTEGRATED**（未集成）。
- Integration-1: **NOT STARTED / mock-test-support only where applicable**（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient: **NOT IMPLEMENTED**（未实现）。
- real provider: **NOT IMPLEMENTED**（未实现）。
- private trading adapter: **NOT IMPLEMENTED**（未实现）。
- real permission probe: **NOT IMPLEMENTED**（未实现）。
- Data Quality diagnostic、Permission Readiness、Risk Preflight 与 public marketdata readiness 都不等于 trading authorization。
- Python Research offline foundation 不等于 ML ready，也不等于 live execution ready。

## What GateP does not mean

- 不代表可实盘。
- 不代表真实交易所私有接口完成。
- 不代表真实权限探活完成。
- 不代表 AI runtime 或 DH runtime 接入。
- 不代表 Python ML ready。
- 不代表 Python live execution ready。
- 不代表 `DataOrigin.PUBLIC_OUTBOUND`、Permission Readiness 或 Risk Preflight 已获得交易授权。

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

GateP release tag and archive 已完成。下一阶段只能进入 **GateQ PLAN / NOT STARTED**（GateQ 仅规划 / 未开始），不得在本归档线内启动 GateQ implementation、LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe。
