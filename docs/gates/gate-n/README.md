# GateN Historical Archive

本目录保存 GateN Public MarketData / Exchange Sandbox 的历史归档卷宗。这里是冻结后的历史证据位置，不是当前开发事实源；当前事实源仍以 `docs/current/` 为准。

## Archive state

- Archive status: **PASS / ARCHIVE MOVE BATCH / READY TO COMMIT**。含义：`PASS`（通过）、`ARCHIVE MOVE BATCH`（物理归档移动批次已执行）、`READY TO COMMIT`（本轮文档归档变更可进入提交前复核）。
- GateN final state: **FINALIZED / FROZEN / ACCEPTED / CLOSED / TAGGED**。含义：`FINALIZED`（最终定版）、`FROZEN`（已冻结）、`ACCEPTED`（已接受）、`CLOSED`（已关闭）、`TAGGED`（已打 tag）。
- Release tag: `nq-gaten-freeze`.
- Moved candidates: 11 / 11 approved GateN process documents.
- Physical archive: executed for the approved GateN process documents only.
- Current authority documents remain in `docs/current/`.

## Boundary

- LIVE: **DISABLED**（关闭）。
- AI: **NOT STARTED**（未启动）。
- DH runtime: **NOT_INTEGRATED**（未集成）。
- RealClient / real provider: **NOT_IMPLEMENTED**（未实现）。
- real permission probe: **NOT_IMPLEMENTED**（未实现）。
- fake-server runtime: **NOT_IMPLEMENTED**（未实现）。
- production adapter / API / runtime implementation: **NOT STARTED**（未启动）。
- public marketdata readiness 不等于 trading authorization。

## Archived documents

Root:

- [NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md](NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md)

Planning:

- [planning/NQ_GATEN_EXCHANGE_DOCS_AND_ADAPTER_RECONCILIATION.md](planning/NQ_GATEN_EXCHANGE_DOCS_AND_ADAPTER_RECONCILIATION.md)
- [planning/NQ_GATEN_PUBLIC_MARKETDATA_CONTRACT_PLAN_REVIEW.md](planning/NQ_GATEN_PUBLIC_MARKETDATA_CONTRACT_PLAN_REVIEW.md)
- [planning/NQ_GATEN_FAKE_SERVER_NO_EGRESS_TEST_PLAN.md](planning/NQ_GATEN_FAKE_SERVER_NO_EGRESS_TEST_PLAN.md)
- [planning/NQ_GATEN_PUBLIC_MARKETDATA_ADAPTER_SKELETON_PLAN_REVIEW.md](planning/NQ_GATEN_PUBLIC_MARKETDATA_ADAPTER_SKELETON_PLAN_REVIEW.md)

Testing:

- [testing/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_PLAN_REVIEW.md](testing/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_PLAN_REVIEW.md)
- [testing/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_IMPLEMENTATION_PLAN.md](testing/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_IMPLEMENTATION_PLAN.md)

Frontend:

- [frontend/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_PLAN_REVIEW.md](frontend/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_PLAN_REVIEW.md)
- [frontend/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_IMPLEMENTATION_PLAN.md](frontend/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_IMPLEMENTATION_PLAN.md)

Freeze:

- [freeze/NQ_GATEN_FREEZE_REVIEW.md](freeze/NQ_GATEN_FREEZE_REVIEW.md)
- [freeze/NQ_GATEN_RELEASE_TAG_AND_ARCHIVE.md](freeze/NQ_GATEN_RELEASE_TAG_AND_ARCHIVE.md)

## Current authority kept in docs/current

- [../../current/README.md](../../current/README.md)
- [../../current/STATUS.md](../../current/STATUS.md)
- [../../current/ROADMAP.md](../../current/ROADMAP.md)
- [../../current/TESTING.md](../../current/TESTING.md)
- [../../current/WORKLOG.md](../../current/WORKLOG.md)
- [../../current/NQ_NEXT_PHASE_PLAN.md](../../current/NQ_NEXT_PHASE_PLAN.md)
- [../../current/NQ_GATEN_POST_CURRENT_ARCHIVE_INVENTORY.md](../../current/NQ_GATEN_POST_CURRENT_ARCHIVE_INVENTORY.md)
- [../../current/NQ_GATEN_ARCHIVE_PLAN_REVIEW.md](../../current/NQ_GATEN_ARCHIVE_PLAN_REVIEW.md)

## Do-not-delete evidence

本批次只移动上述 11 个批准候选，不删除任何历史证据，不新增 redirect stub，不改写 moved docs 的历史正文证据。若后续需要清理 residual，只能先做单独 inventory / review，不得把历史过程记录当成可删除内容。

## Next task

推荐下一步：`NQ-GATEN-ARCHIVE-CLOSEOUT`，只做归档完成核对、引用残留分类和 current / gates 索引复核；不得再次移动未批准文件，不得新增代码、API、migration、CI workflow 或 runtime 能力。
