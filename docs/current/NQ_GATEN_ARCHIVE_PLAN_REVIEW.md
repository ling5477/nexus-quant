# NQ-GATEN-ARCHIVE-PLAN-REVIEW

## Status

**PASS / PLAN REVIEW ONLY / READY FOR MOVE BATCH**

含义：`PASS`（通过）、`PLAN REVIEW ONLY`（仅计划审查，不执行移动或删除）、`READY FOR MOVE BATCH`（可进入后续物理归档移动批次）。本文件只审查 `docs/current/NQ_GATEN_POST_CURRENT_ARCHIVE_INVENTORY.md` 中的 GateN archive move 计划，不移动文件、不删除文件、不新增 `docs/gates/gate-n/**` 文件、不改代码、不改 API、不改 migration、不改 CI。

## Current Archive Baseline

- GateN：**FINALIZED / FROZEN / ACCEPTED / CLOSED / TAGGED**，即最终定版、已冻结、已接受、已关闭并已打 tag。
- GateN release tag：`nq-gaten-freeze` 已推送到 `origin`。
- GateN release/tag closeout：**COMPLETED**（已完成）。
- GateN post-current archive inventory：**PASS / INVENTORY ONLY**，即盘点通过且只做候选清单。
- GateN physical archive：**NOT EXECUTED**，即尚未执行物理归档。
- `docs/gates/gate-n/**`：**NOT CREATED**，即尚未创建。
- LIVE：**DISABLED**，即未开启。
- AI：**NOT STARTED**，即未启动。
- DH runtime：**NOT_INTEGRATED**，即未集成运行时。
- RealClient / real provider：**NOT_IMPLEMENTED**，即未实现。
- real permission probe：**NOT_IMPLEMENTED**，即未实现。
- public marketdata readiness 只表示 public marketdata 诊断就绪度，不等于 trading authorization（交易授权）。

## Review Target

本轮审查目标是确认是否可以进入 `NQ-GATEN-PHYSICAL-ARCHIVE-MOVE-BATCH`。审查输入为：

- `docs/current/NQ_GATEN_POST_CURRENT_ARCHIVE_INVENTORY.md`。
- 11 个 GateN process docs 的 move candidates。
- current 事实源保留清单。
- root/current/gates 索引与引用更新需求。
- no-real / no-LIVE / no-AI / no-DH / no-real-provider 边界。

本轮结论：可以进入后续 move batch，但 move batch 必须单独授权，并且只能移动本文件批准的 11 个 GateN 过程文档。

## Proposed Move List

以下 11 个文件均为 GateN 过程文档或阶段证据，可在后续 move batch 中移动到 `docs/gates/gate-n/**`。本轮未移动。

| Current path | Proposed target | Batch | Decision | Reason |
| --- | --- | --- | --- | --- |
| `docs/current/NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md` | `docs/gates/gate-n/NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md` | Batch 1 | APPROVED | GateN stage baseline 已 finalized/tagged；current 只需保留摘要和 archive pointer。 |
| `docs/current/NQ_GATEN_FREEZE_REVIEW.md` | `docs/gates/gate-n/freeze/NQ_GATEN_FREEZE_REVIEW.md` | Batch 1 | APPROVED | GateN freeze evidence 已被 release/tag closeout 消费。 |
| `docs/current/NQ_GATEN_RELEASE_TAG_AND_ARCHIVE.md` | `docs/gates/gate-n/freeze/NQ_GATEN_RELEASE_TAG_AND_ARCHIVE.md` | Batch 1 | APPROVED | release/tag closeout 是 GateN historical archive evidence；current 只保留 tag 摘要。 |
| `docs/current/NQ_GATEN_EXCHANGE_DOCS_AND_ADAPTER_RECONCILIATION.md` | `docs/gates/gate-n/planning/NQ_GATEN_EXCHANGE_DOCS_AND_ADAPTER_RECONCILIATION.md` | Batch 2 | APPROVED | GateN-0 reconciliation baseline，已被 GateN-1..freeze 消费。 |
| `docs/current/NQ_GATEN_PUBLIC_MARKETDATA_CONTRACT_PLAN_REVIEW.md` | `docs/gates/gate-n/planning/NQ_GATEN_PUBLIC_MARKETDATA_CONTRACT_PLAN_REVIEW.md` | Batch 2 | APPROVED | GateN-1 contract plan review，已被 GateN-2..freeze 消费。 |
| `docs/current/NQ_GATEN_FAKE_SERVER_NO_EGRESS_TEST_PLAN.md` | `docs/gates/gate-n/planning/NQ_GATEN_FAKE_SERVER_NO_EGRESS_TEST_PLAN.md` | Batch 2 | APPROVED | GateN-2 test plan baseline，已被 GateN-3..freeze 消费。 |
| `docs/current/NQ_GATEN_PUBLIC_MARKETDATA_ADAPTER_SKELETON_PLAN_REVIEW.md` | `docs/gates/gate-n/planning/NQ_GATEN_PUBLIC_MARKETDATA_ADAPTER_SKELETON_PLAN_REVIEW.md` | Batch 2 | APPROVED | GateN-3 skeleton plan review；adapter skeleton 仍 NOT_IMPLEMENTED，文件是 historical plan evidence。 |
| `docs/current/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_PLAN_REVIEW.md` | `docs/gates/gate-n/planning/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_PLAN_REVIEW.md` | Batch 2 | APPROVED | GateN-4 fixture smoke plan review，已被 implementation 和 freeze 消费。 |
| `docs/current/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_PLAN_REVIEW.md` | `docs/gates/gate-n/frontend/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_PLAN_REVIEW.md` | Batch 2 | APPROVED | GateN-5 UI display plan review，已被 implementation 和 freeze 消费。 |
| `docs/current/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_IMPLEMENTATION_PLAN.md` | `docs/gates/gate-n/testing/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_IMPLEMENTATION_PLAN.md` | Batch 3 | APPROVED | GateN-4 deterministic fixture smoke implementation record / test evidence。 |
| `docs/current/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_IMPLEMENTATION_PLAN.md` | `docs/gates/gate-n/frontend/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_IMPLEMENTATION_PLAN.md` | Batch 3 | APPROVED | GateN-5 runtime UI sandbox/source display implementation record。 |

## Proposed Keep List

以下文件必须保留在 `docs/current`，不得作为 GateN physical archive move 对象：

| Path | Decision | Reason |
| --- | --- | --- |
| `docs/current/README.md` | KEEP | 当前事实入口，只能改写为 archive pointer。 |
| `docs/current/STATUS.md` | KEEP | 当前状态权威，保留 GateN final/tag/no-real 摘要。 |
| `docs/current/ROADMAP.md` | KEEP | 当前路线权威，保留 GateN closed/tagged 与下一阶段未启动事实。 |
| `docs/current/TESTING.md` | KEEP | append-only 测试记录，后续只追加 move batch 记录。 |
| `docs/current/WORKLOG.md` | KEEP | append-only 工作记录，后续只追加 move batch 记录。 |
| `docs/current/NQ_NEXT_PHASE_PLAN.md` | KEEP | cross-phase route authority，不属于 GateN 单阶段证据卷宗。 |
| `docs/current/NQ_GATEN_POST_CURRENT_ARCHIVE_INVENTORY.md` | KEEP | archive inventory 输入和审计依据，至少保留到 archive closeout。 |
| `docs/current/NQ_GATEN_ARCHIVE_PLAN_REVIEW.md` | KEEP | 本轮 plan review 审批记录，至少保留到 archive closeout。 |

## Proposed New Archive Index

后续 move batch 需要创建 `docs/gates/gate-n/README.md`，但本轮不得创建。建议 archive index 至少包含：

- GateN archive identity：GateN Public MarketData / Exchange Sandbox no-real baseline。
- Final state：`FINALIZED / FROZEN / ACCEPTED / CLOSED / TAGGED`。
- Release tag：`nq-gaten-freeze`。
- Boundary summary：LIVE disabled、AI not started、DH runtime not integrated、RealClient / real provider / real permission probe not implemented。
- Evidence groups：
  - root：stage-level baseline。
  - `freeze/`：freeze review 与 release/tag closeout。
  - `planning/`：GateN-0..4 planning / contract / fake-server / skeleton / fixture plan review。
  - `testing/`：deterministic fixture smoke implementation record。
  - `frontend/`：runtime UI sandbox/source display plan and implementation evidence。
- Source preservation rule：移动历史证据，不删除、不改写 freeze/tag-bound facts、不新增 redirect stub，除非后续单独批准。
- Current pointer：`docs/current/README.md` 只保留 GateN summary 和 archive pointer。

## Required Reference Updates

后续 move batch 必须同步以下引用，避免 current 链接断裂：

- root `README.md`：把 GateN 长文档入口压缩为 `docs/gates/gate-n/README.md` archive pointer，同时保留 GateN final/tag/no-real 摘要。
- `docs/current/README.md`：移除 11 个 GateN 过程文档 direct current links，改为 GateN archive pointer；保留 `NQ_NEXT_PHASE_PLAN.md`、inventory、plan review。
- `docs/current/STATUS.md`：保留 GateN 状态摘要；如存在 direct process-doc links，改为 archive pointer。
- `docs/current/ROADMAP.md`：保留 GateN finalized/tagged 与 next phase not started；如存在 direct process-doc links，改为 archive pointer。
- `docs/current/TESTING.md`：不重写 append-only 历史；在顶部追加 move batch validation 记录和 archive pointer。
- `docs/current/WORKLOG.md`：不重写 append-only 历史；在顶部或尾部追加 move batch 记录和 archive pointer。
- `docs/current/NQ_NEXT_PHASE_PLAN.md`：GateN baseline documents 清单需从 current paths 改为 archive paths或 archive README pointer，且继续保留 no-real / no-LIVE / no-AI / no-DH / no-real-provider 边界。
- `docs/gates/README.md`：GateN entry 从 current release/tag closeout 指向 `gate-n/README.md`，并注明 archive move batch status。
- `docs/gates/gate-n/README.md`：新增时必须列出全部 moved evidence 和 no-real boundary。

## Do-Not-Move Candidates

- `docs/current/NQ_NEXT_PHASE_PLAN.md`：跨阶段 planning authority，不能移入单一 GateN archive。
- `docs/current/README.md`、`STATUS.md`、`ROADMAP.md`：current authority，不移动。
- `docs/current/TESTING.md`、`WORKLOG.md`：append-only current logs，不移动。
- `docs/current/NQ_GATEN_POST_CURRENT_ARCHIVE_INVENTORY.md`：archive governance input，不在本批移动。
- `docs/current/NQ_GATEN_ARCHIVE_PLAN_REVIEW.md`：archive governance approval record，不在本批移动。
- root `README.md`：项目入口，不移动。
- `docs/gates/README.md`：历史卷宗总入口，不移动。

## Do-Not-Delete Evidence

后续 move batch 只能移动和改写索引，不得删除以下证据：

- 全部 11 个 GateN process docs。
- `docs/current/NQ_GATEN_POST_CURRENT_ARCHIVE_INVENTORY.md`。
- `docs/current/NQ_GATEN_ARCHIVE_PLAN_REVIEW.md`。
- `docs/current/NQ_NEXT_PHASE_PLAN.md` 的 GateN closeout and boundary state。
- root `README.md` 与 `docs/current/README.md` 的 GateN summary。
- `docs/current/STATUS.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md` 中的 GateN 状态、测试和工作记录。
- `docs/gates/README.md` 的 GateN archive 状态。

## Cross-Phase And Path Risk Review

- Move candidates 只包含 GateN 过程文档，不包含 GateM/GateK/GateL/GateJ 文档。
- `NQ_NEXT_PHASE_PLAN.md` 同时承载 GateM 后下一阶段选择和 GateN closeout state，应保留在 current。
- `NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md` 兼具 stage baseline 和 historical evidence；移动前必须确保 current README / STATUS / ROADMAP 保留 GateN summary。
- `NQ_GATEN_FREEZE_REVIEW.md` 不建议保留在 current；应通过 current README 指向 archive。
- Windows 路径风险：目标目录统一使用小写 `docs/gates/gate-n/`，避免 `GateN`、`gate-N` 或大小写混用。
- 文件名大小写风险：所有移动保持原文件名不变，只改目录；不得新增同名大小写变体。
- Markdown 链接风险：先创建 archive README 和目标目录，再移动文件并改写引用；移动后必须用 `rg` 复查 current links。

## Rollback Strategy

后续 move batch 若在未提交前需要回滚，应优先使用反向 `git mv` 恢复移动文件，并用 `git restore` 恢复索引文件。示例命令必须在实际 move batch 的 cwd `F:\project\nexus-quant` 下执行：

```powershell
git mv docs/gates/gate-n/NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md docs/current/NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md
git mv docs/gates/gate-n/freeze/NQ_GATEN_FREEZE_REVIEW.md docs/current/NQ_GATEN_FREEZE_REVIEW.md
git mv docs/gates/gate-n/freeze/NQ_GATEN_RELEASE_TAG_AND_ARCHIVE.md docs/current/NQ_GATEN_RELEASE_TAG_AND_ARCHIVE.md
git mv docs/gates/gate-n/planning/NQ_GATEN_EXCHANGE_DOCS_AND_ADAPTER_RECONCILIATION.md docs/current/NQ_GATEN_EXCHANGE_DOCS_AND_ADAPTER_RECONCILIATION.md
git mv docs/gates/gate-n/planning/NQ_GATEN_PUBLIC_MARKETDATA_CONTRACT_PLAN_REVIEW.md docs/current/NQ_GATEN_PUBLIC_MARKETDATA_CONTRACT_PLAN_REVIEW.md
git mv docs/gates/gate-n/planning/NQ_GATEN_FAKE_SERVER_NO_EGRESS_TEST_PLAN.md docs/current/NQ_GATEN_FAKE_SERVER_NO_EGRESS_TEST_PLAN.md
git mv docs/gates/gate-n/planning/NQ_GATEN_PUBLIC_MARKETDATA_ADAPTER_SKELETON_PLAN_REVIEW.md docs/current/NQ_GATEN_PUBLIC_MARKETDATA_ADAPTER_SKELETON_PLAN_REVIEW.md
git mv docs/gates/gate-n/planning/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_PLAN_REVIEW.md docs/current/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_PLAN_REVIEW.md
git mv docs/gates/gate-n/frontend/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_PLAN_REVIEW.md docs/current/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_PLAN_REVIEW.md
git mv docs/gates/gate-n/testing/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_IMPLEMENTATION_PLAN.md docs/current/NQ_GATEN_MARKETDATA_SANDBOX_FIXTURE_SMOKE_IMPLEMENTATION_PLAN.md
git mv docs/gates/gate-n/frontend/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_IMPLEMENTATION_PLAN.md docs/current/NQ_GATEN_RUNTIME_UI_SANDBOX_SOURCE_DISPLAY_IMPLEMENTATION_PLAN.md
git restore --worktree -- README.md docs/current/README.md docs/current/STATUS.md docs/current/ROADMAP.md docs/current/TESTING.md docs/current/WORKLOG.md docs/current/NQ_NEXT_PHASE_PLAN.md docs/gates/README.md
```

如果 move batch 新建了 `docs/gates/gate-n/README.md` 或空目录，删除新建 archive index / 空目录属于破坏性清理，必须在 move batch 中单独确认后执行；不得在本轮执行。

## Validation Commands For Move Batch

后续 move batch 至少需要执行：

```powershell
git status --short
git diff --check
git diff --stat
git diff --name-status
git diff -- README.md docs/current docs/gates
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- "backend/**/db/migration"
rg "NQ_GATEN|GateN|GATEN|nq-gaten-freeze|public marketdata|sandbox|no-real|archive|docs/gates/gate-n|LIVE|AI|DH runtime|RealClient|real provider|trading authorization" README.md docs/current docs/gates
```

后续 move batch 不需要运行 Maven、frontend build/E2E 或 Python pytest/mypy/ruff，除非实际改动超出文档移动和索引更新范围。

## P0/P1/P2/P3 Findings

### P0

- 无。

### P1

- 无。

### P2

- GateN physical archive 尚未执行；11 个 GateN process docs 仍位于 `docs/current`，造成 current-folder noise。该问题不影响 GateN freeze/tag correctness，但应通过后续 move batch 收口。
- move batch 若不先创建 `docs/gates/gate-n/README.md` 和改写 current pointers，会造成 discoverability / broken-link 风险。

### P3

- `TESTING.md` 与 `WORKLOG.md` 是 append-only 历史，移动后仍会保留旧 current path 语境；不建议重写历史。
- 历史 process docs 中的 `Recommended next task` 或 `READY TO COMMIT` 文案保留为历史状态；archive README 应说明这些不是当前执行授权。

## Boundary Confirmation

本轮未移动文件、未删除文件、未创建 `docs/gates/gate-n/**`，未改 `backend/**`、`frontend/**`、`research/**`、`scripts/**`、`deploy/**`、`.github/**` 或 `backend/**/db/migration/**`。未新增 API、migration、页面、E2E、CI workflow、真实 HTTP / WebSocket、adapter skeleton、fake-server runtime、RealClient、real provider、private trading adapter 或 real permission probe。未调用真实交易所 API，未读取或输出 credential material，未开启 LIVE，未接 AI runtime，未接 DH runtime，未下单、撤单、转账或提现。

## Final Decision

**PASS / PLAN REVIEW ONLY / READY FOR MOVE BATCH**

结论：可以进入 `NQ-GATEN-PHYSICAL-ARCHIVE-MOVE-BATCH`。后续 move batch 只允许移动本文件批准的 11 个 GateN process docs，并同步指定引用；不得移动 current authority docs，不得删除历史证据，不得新增实现或 runtime 能力。

## Recommended Next Task

```text
NQ-GATEN-PHYSICAL-ARCHIVE-MOVE-BATCH
```

该任务应创建 `docs/gates/gate-n/README.md` 和必要子目录，移动本 review 批准的 11 个文件，改写 current/root/gates 引用，并运行本文件列出的验证命令。

## Commit Recommendation

```text
docs(gaten): review physical archive move plan
```
