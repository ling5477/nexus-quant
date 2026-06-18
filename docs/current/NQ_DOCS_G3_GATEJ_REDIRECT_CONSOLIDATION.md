# NQ Documentation Governance — G3 GateJ Redirect-First Consolidation

任务：`NQ-DOCS-GOVERNANCE-G3-GATEJ-REDIRECT-FIRST-CONSOLIDATION`

日期：2026-06-19

状态：**G3 = IMPLEMENTED / READY FOR REVIEW**

任务类型：DOCUMENTATION_GOVERNANCE_IMPLEMENTATION + GATEJ_DUPLICATE_CONSOLIDATION + LINK_COMPATIBILITY_PRESERVATION

> 本轮将 `docs/current/` 中 17 份已冻结为 `NON_AUTHORITATIVE / FUTURE_SUPERSEDE_CANDIDATE` 的 GateJ byte-identical 副本，**就地**收敛为 redirect-first 兼容文档（保留路径、不删除、不移动、不重命名）。**权威全文永久保留在 `docs/gates/gate-j/`**，本轮未改写 gate-j。**RUNBOOK 未触碰；9 份 DIVERGED 未做 redirect/stub 处理；G1 五份冻结对象未修改；G4~G6 未开始。**

---

## 1. 结论

**`NQ-DOCS-GOVERNANCE-G3-GATEJ-REDIRECT-FIRST-CONSOLIDATION：PASS / READY FOR REVIEW`**

- 17 / 17 候选 = **REDIRECT_STUB_CREATED**；0 BLOCKED_PER_FILE。
- 先决核验全过：blob-identity 17/17、canonical 存在 17/17、**0 个 `#fragment` 入链**、Authority Index / Migration Map 仍标 `NON_AUTHORITATIVE / FUTURE_SUPERSEDE_CANDIDATE / G3`（G1 冻结对象未改）。

## 2. 先决核验结果

- **Blob-identity（转换前 / HEAD 基线）**：每个 `docs/current/<f>` 与 `docs/gates/gate-j/<f>` 的 HEAD blob 完全一致（见 §4 blob 列）；转换后 current 已成为 stub，canonical gate-j blob 保持不变。
- **Canonical 存在**：17/17 `docs/gates/gate-j/<f>` 均存在。
- **入链 / fragment 扫描**（`git grep`）：
  - **`<name>.md#` 片段入链 = 0**（全仓无任何指向候选的 heading fragment 链接）→ **无文件因 fragment 被 BLOCKED**。
  - 其余引用均为**纯文本提及**或**纯相对链接**（无 fragment），转换为 stub 后路径仍存在并 redirect 到 canonical，链接可解析。
  - 两处 **活跃 DIVERGED 文档**的相对链接：`docs/current/API.md:233` → `./GATEJ_API_PLAN.md`、`docs/current/DB_SCHEMA.md:375` → `./GATEJ_DB_PLAN.md`，均无 fragment；转换后落在 stub 并 redirect 到 canonical（正是 redirect-first 兼容目的）。按禁令**未修改**这两份 DIVERGED 文档。
  - `docs/README.md` / 仓库根 `README.md` 中的导航路径提及（本轮不允许修改）仍指向现存 stub 路径，可正常导航。

## 3. Redirect-first stub 规范符合性

每个 stub 均：保留原始 H1；含 `Historical GateJ record — non-authoritative compatibility path` 区块；`Status: NON_AUTHORITATIVE / SUPERSEDED_BY_CANONICAL_GATEJ_RECORD`；`Governance: G3 redirect-first consolidation` 说明；相对链接 `../gates/gate-j/<f>`；**未**保留历史正文、**未**复制/摘要/重写结论、**未**产生第二份 current authority、**未**把 canonical 写成 archive/deleted、**未**加 HTML/JS/外链/自动跳转；旧路径保留（无 `git mv`/删除/重命名）。每份 12 行。

## 4. 17 份逐文件结果

| # | current 路径（stub） | canonical target | HEAD blob（转换前 current == canonical，canonical 转换后保持不变） | fragment 入链 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 1 | `docs/current/AUDIT_FIX_REPORT.md` | `docs/gates/gate-j/AUDIT_FIX_REPORT.md` | `5cf8778d…07a` | 0 | REDIRECT_STUB_CREATED |
| 2 | `docs/current/DOC_CLEAN_REPORT.md` | `docs/gates/gate-j/DOC_CLEAN_REPORT.md` | `360aecf6…a81` | 0 | REDIRECT_STUB_CREATED |
| 3 | `docs/current/FULL_SECURITY_AUDIT_REPORT.md` | `docs/gates/gate-j/FULL_SECURITY_AUDIT_REPORT.md` | `3aa7337d…2ca` | 0 | REDIRECT_STUB_CREATED |
| 4 | `docs/current/GATEJ_API_PLAN.md` | `docs/gates/gate-j/GATEJ_API_PLAN.md` | `7185b394…a1c` | 0 | REDIRECT_STUB_CREATED |
| 5 | `docs/current/GATEJ_DB_PLAN.md` | `docs/gates/gate-j/GATEJ_DB_PLAN.md` | `6e6f0f4c…56a` | 0 | REDIRECT_STUB_CREATED |
| 6 | `docs/current/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md` | `docs/gates/gate-j/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md` | `7d46d6ed…c38` | 0 | REDIRECT_STUB_CREATED |
| 7 | `docs/current/GATEJ_FREEZE_DEPLOYMENT.md` | `docs/gates/gate-j/GATEJ_FREEZE_DEPLOYMENT.md` | `dc5db475…0d4` | 0 | REDIRECT_STUB_CREATED |
| 8 | `docs/current/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md` | `docs/gates/gate-j/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md` | `0e52e9e0…2a4` | 0 | REDIRECT_STUB_CREATED |
| 9 | `docs/current/GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md` | `docs/gates/gate-j/GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md` | `4be56a7c…adc` | 0 | REDIRECT_STUB_CREATED |
| 10 | `docs/current/GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md` | `docs/gates/gate-j/GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md` | `8401c82e…6ec` | 0 | REDIRECT_STUB_CREATED |
| 11 | `docs/current/GATEJ_FRONTEND_PLAN.md` | `docs/gates/gate-j/GATEJ_FRONTEND_PLAN.md` | `0ca65616…5fa` | 0 | REDIRECT_STUB_CREATED |
| 12 | `docs/current/GATEJ_TEST_PLAN.md` | `docs/gates/gate-j/GATEJ_TEST_PLAN.md` | `f2d22d5b…9dc` | 0 | REDIRECT_STUB_CREATED |
| 13 | `docs/current/GATEJ_WORK_ORDER.md` | `docs/gates/gate-j/GATEJ_WORK_ORDER.md` | `cb0915e6…102` | 0 | REDIRECT_STUB_CREATED |
| 14 | `docs/current/PLAN_GATEJ.md` | `docs/gates/gate-j/PLAN_GATEJ.md` | `79e7fcac…17c` | 0 | REDIRECT_STUB_CREATED |
| 15 | `docs/current/PRE_FREEZE_AUDIT_FIX_PLAN.md` | `docs/gates/gate-j/PRE_FREEZE_AUDIT_FIX_PLAN.md` | `b249b39e…515` | 0 | REDIRECT_STUB_CREATED |
| 16 | `docs/current/PRE_FREEZE_AUDIT_REPORT.md` | `docs/gates/gate-j/PRE_FREEZE_AUDIT_REPORT.md` | `946cf431…b23` | 0 | REDIRECT_STUB_CREATED |
| 17 | `docs/current/REPO_SIZE_AUDIT_REPORT.md` | `docs/gates/gate-j/REPO_SIZE_AUDIT_REPORT.md` | `287c4c3e…b91` | 0 | REDIRECT_STUB_CREATED |

转换后核验：17/17 canonical gate-j blob 与 HEAD 一致（**PRES_OK**，权威 byte-for-byte 保留）；17/17 current stub 已与 canonical 不同（**STUB_DIFFERS**）；每份 stub 含 `../gates/gate-j/<f>` 链接与 superseded status 行。

## 5. 未触碰 / 边界证明

- **RUNBOOK 未修改**：`docs/current/RUNBOOK.md` 不在处理集（第 18 份 blob-identical，RETAIN_IN_PLACE / current-control 手册）。working-tree diff = 0。
- **9 份 DIVERGED 未做 redirect/stub 处理**：`API.md`、`ARCHITECTURE.md`、`DB_SCHEMA.md`、`MODULES.md`、`README.md`、`ROADMAP.md` working-tree diff = 0；`STATUS.md`、`TESTING.md`、`WORKLOG.md` 仅按本任务“允许更新”范围追加 G3 状态、验证与工作日志记录，不作为 duplicate/supersede 对象处理。
- **G1 五份冻结对象 diff = 0**：`NQ_DOCS_GOVERNANCE_PLAN.md` / `NQ_DOCS_AUTHORITY_INDEX.md` / `NQ_DOCS_EVIDENCE_INDEX.md` / `NQ_DOCS_MIGRATION_MAP.md` / `NQ_DOCS_G1_IMPLEMENTATION.md`。
- **`docs/gates/**` diff = 0**（含 gate-j 权威卷宗）；`docs/archive/**` / `.agents/**` / `templates/**` diff = 0。
- **未删除、移动、重命名任何文件**：`git status --short` 显示 17 个候选 stub、允许更新的 `STATUS.md` / `TESTING.md` / `WORKLOG.md` 与新增 G3 报告；`git diff --name-status` 对已跟踪文件仅显示 `M`（无 D/R）。
- `.github/workflows/ci.yml` / backend / frontend / research / scripts / deploy / migration diff = 0。
- **G4 ~ G6 未开始**；Batch 5B-ENV / 5B-SMOKE / 4F-B~4F-F 未启动；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter 未开启、未接入、未实现。

## 6. Findings / 风险 / 回滚

- **P0 / P1 / P2**：无。
- **P3-1（line-ending，信息性）**：新 stub 以 LF 写入，Windows 工作树触发 git `LF will be replaced by CRLF` 提示，属仓库级 autocrlf 归一化提示，非内容错误；`git diff --check` clean。
- **风险**：内容覆盖型变更，但每份权威全文 byte-for-byte 保留在 `docs/gates/gate-j/`，且 git 历史保留旧 current 全文，完全可逆；零代码、零迁移移动。
- **回滚**：`git checkout HEAD -- docs/current/<f>`（逐文件）或 revert 本轮 commit 即恢复 17 份 current 全文；canonical 与 G1 冻结对象不受影响。

## 7. 状态结论（原样）

```text
G1 authority/evidence index = FROZEN / ACCEPTED
G2 current-control drift repair = FROZEN / ACCEPTED
G3 GateJ redirect-first consolidation = IMPLEMENTED / READY FOR REVIEW
G4～G6 = NOT STARTED
NQ GateK CI mainline = COMPLETED / ACCEPTED
Batch 5A = FROZEN / ACCEPTED
Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```
