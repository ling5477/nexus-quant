# GateO Historical Archive

本目录保存 GateO Public MarketData Controlled Outbound & Data Quality Runtime 的历史归档卷宗。这里是冻结后的历史证据位置，不是当前开发事实源；当前事实源仍以 `docs/current/` 为准。

## Archive state

- Archive task: `NQ-GATEO-ARCHIVE-CLOSEOUT`.
- Archive status: **IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT**。含义：`IMPLEMENTED`（已实施）、`SELF-REVIEWED`（已自审）、`READY TO COMMIT`（可提交前复核）。
- GateO final state: **FROZEN / ACCEPTED**。含义：`FROZEN`（已冻结）、`ACCEPTED`（已接受）。
- GateO freeze review: **PASS / ACCEPTED / CLOSED / READY FOR ARCHIVAL**。含义：`PASS`（通过）、`ACCEPTED`（已接受）、`CLOSED`（已关闭）、`READY FOR ARCHIVAL`（可归档）。
- Moved candidates: 9 / 9 GateO process and evidence documents.
- Physical archive: executed for GateO freeze / acceptance / plan / key evidence documents.
- Current authority documents remain in `docs/current/`.

## Boundary

- LIVE: **DISABLED**（关闭）。
- AI: **NOT STARTED**（未启动）。
- DH runtime: **NOT_INTEGRATED**（未集成）。
- RealClient / real provider: **NOT_IMPLEMENTED**（未实现）。
- real permission probe: **NOT_IMPLEMENTED**（未实现）。
- private trading adapter: **NOT_IMPLEMENTED**（未实现）。
- `DataOrigin.PUBLIC_OUTBOUND` runtime implementation: **OPTIONAL / NOT STARTED**（可选 / 未开始）。
- public marketdata readiness 不等于 trading authorization。

## Archived documents

- [GATEO_PLAN.md](GATEO_PLAN.md)
- [NQ_GATEO_FREEZE_REVIEW.md](NQ_GATEO_FREEZE_REVIEW.md)
- [NQ_GATEO_O2_DATA_QUALITY_CENTER_PLAN.md](NQ_GATEO_O2_DATA_QUALITY_CENTER_PLAN.md)
- [NQ_GATEO_O3_MARKETDATA_RUNTIME_READINESS_API_PLAN.md](NQ_GATEO_O3_MARKETDATA_RUNTIME_READINESS_API_PLAN.md)
- [NQ_GATEO_O4_MARKETDATA_QUALITY_UI_PLAN.md](NQ_GATEO_O4_MARKETDATA_QUALITY_UI_PLAN.md)
- [NQ_GATEO_O5_MANUAL_PUBLIC_OUTBOUND_SMOKE_PLAN.md](NQ_GATEO_O5_MANUAL_PUBLIC_OUTBOUND_SMOKE_PLAN.md)
- [NQ_GATEO_O5B_RUNNER_BINDING_PLAN.md](NQ_GATEO_O5B_RUNNER_BINDING_PLAN.md)
- [NQ_GATEO_O5D_DATAORIGIN_PUBLIC_OUTBOUND_DECISION.md](NQ_GATEO_O5D_DATAORIGIN_PUBLIC_OUTBOUND_DECISION.md)
- [NQ_GATEO_O5E_MANUAL_PUBLIC_OUTBOUND_SMOKE_FREEZE_REVIEW.md](NQ_GATEO_O5E_MANUAL_PUBLIC_OUTBOUND_SMOKE_FREEZE_REVIEW.md)

## Current authority kept in docs/current

- [../../current/README.md](../../current/README.md)
- [../../current/STATUS.md](../../current/STATUS.md)
- [../../current/FACT_SOURCE_INDEX.md](../../current/FACT_SOURCE_INDEX.md)
- [../../current/TESTING.md](../../current/TESTING.md)
- [../../current/WORKLOG.md](../../current/WORKLOG.md)
- [../../current/API.md](../../current/API.md)
- [../../current/DB_SCHEMA.md](../../current/DB_SCHEMA.md)

## Do-not-delete evidence

本 closeout 只移动上述 9 个 GateO 过程与证据文档，不删除历史证据，不新增 API，不新增 migration，不改 CI，不改 backend / frontend / research / scripts / deploy。若后续发现断链或归档遗漏，只能另起 bounded docs-only 修复任务；不得借归档任务启动 GateP implementation、LIVE、AI、DH runtime、RealClient、real provider、real permission probe 或 private trading adapter。

## Archive closeout

GateO archive closeout 已完成。除非后续发现真实断链或错误索引，不建议继续开启 GateO archive 任务；GateP 后续批次必须另起任务并重新声明 no-LIVE、no-AI、no-DH-runtime、no-real-provider、no-private-trading 和 no-credential 边界。
