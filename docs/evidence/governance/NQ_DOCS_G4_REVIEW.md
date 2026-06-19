# NQ Documentation Governance — G4 CI Evidence Routing Review

任务：`NQ-DOCS-GOVERNANCE-G4-CI-EVIDENCE-ROUTING-REVIEW`

日期：2026-06-19

被审对象：G4 implementation commit `783bfa68`（20 个 CI evidence canonical target + 20 个 old-path compatibility stub + 2 个 current authority retained + CI evidence / baseline index + G4 implementation report）。

任务类型：DOCUMENTATION_GOVERNANCE_REVIEW + CI_EVIDENCE_CANONICALIZATION_AUDIT + COMPATIBILITY_PATH_VERIFICATION

> 本轮为**只读审查 + review evidence 落盘**。未修改 20 个 source stub、20 个 canonical evidence、两份 current authority、`CI_BASELINE_INDEX.md`、G1 五份冻结对象、G2/G3 冻结对象、`docs/gates/**`、`docs/archive/**`、`.agents/**`、`templates/**`、workflow、代码、测试、migration、依赖。仅新增本文并更新 `STATUS.md` / `TESTING.md` / `WORKLOG.md` 记录 review 结论。

---

## 审查结论

**结论：`NQ-DOCS-GOVERNANCE-G4-CI-EVIDENCE-ROUTING-REVIEW：PASS / ACCEPTED`**

- **G4 CI evidence routing = ACCEPTED / READY FOR FREEZE REVIEW**。
- 22 个候选均可追溯至 `NQ_DOCS_MIGRATION_MAP.md` §1D。
- 20 / 20 routed canonical evidence 与 G4 implementation commit 父提交中的原 source blob 一致。
- 2 / 2 current authority 完整保留，状态为预期的 `BLOCKED_PER_FILE / CURRENT_AUTHORITY`。
- P0 = 0；P1 = 0；P2 = 0；P3 = 0。

---

## 22 个候选状态

| # | 文件 | 状态 |
| --- | --- | --- |
| 1 | `NQ_CI_BASELINE_PLAN.md` | BLOCKED_PER_FILE / CURRENT_AUTHORITY |
| 2 | `NQ_CI_SECURITY_GUARD_PLAN.md` | BLOCKED_PER_FILE / CURRENT_AUTHORITY |
| 3 | `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` | REDIRECT_STUB_CREATED |
| 4 | `NQ_CI_POSTGRES_FLYWAY_PLAN.md` | REDIRECT_STUB_CREATED |
| 5 | `NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md` | REDIRECT_STUB_CREATED |
| 6 | `NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md` | REDIRECT_STUB_CREATED |
| 7 | `NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md` | REDIRECT_STUB_CREATED |
| 8 | `NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md` | REDIRECT_STUB_CREATED |
| 9 | `NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md` | REDIRECT_STUB_CREATED |
| 10 | `NQ_CI_LOG_REDACTION_PROOF_PLAN.md` | REDIRECT_STUB_CREATED |
| 11 | `NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md` | REDIRECT_STUB_CREATED |
| 12 | `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md` | REDIRECT_STUB_CREATED |
| 13 | `NQ_CI_DEPENDENCY_AUDIT_PLAN.md` | REDIRECT_STUB_CREATED |
| 14 | `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md` | REDIRECT_STUB_CREATED |
| 15 | `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md` | REDIRECT_STUB_CREATED |
| 16 | `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md` | REDIRECT_STUB_CREATED |
| 17 | `NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md` | REDIRECT_STUB_CREATED |
| 18 | `NQ_CI_FRONTEND_E2E_PLAN.md` | REDIRECT_STUB_CREATED |
| 19 | `NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md` | REDIRECT_STUB_CREATED |
| 20 | `NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md` | REDIRECT_STUB_CREATED |
| 21 | `NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md` | REDIRECT_STUB_CREATED |
| 22 | `NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md` | REDIRECT_STUB_CREATED |

---

## Source → Canonical Target

20 个 routed 文件均满足：

```text
docs/current/<filename>      -> non-authoritative compatibility stub
docs/evidence/ci/<filename>  -> canonical historical evidence
```

逐项 blob 核验使用：

```powershell
$pre = "783bfa68^"
git show "$pre`:docs/current/<filename>" | git hash-object --stdin
git hash-object "docs/evidence/ci/<filename>"
```

结果：`ROUTED_OK=20/20`。未发现截断、摘要、改写、历史结论变化或额外内容。

---

## Old-path Compatibility Stubs

20 个 `docs/current/<filename>` 均通过：

- 文件仍存在。
- 保留原始 H1。
- 包含 Historical CI evidence / non-authoritative compatibility path 说明。
- 相对链接为 `../evidence/ci/<filename>`。
- 包含 `NON_AUTHORITATIVE / SUPERSEDED_BY_CI_EVIDENCE_RECORD`。
- 包含 G4 CI evidence routing 说明。
- 未保留完整旧正文、长摘要、审计结论复制或第二份权威全文。
- 未使用 HTML redirect、JavaScript、meta refresh、脚本跳转或外链。

普通旧路径入链仍可解析至保留的 compatibility stub。

---

## 入链与 Fragment

- 20 个 old source path 的 `#fragment` 入链 = 0。
- 未发现 `PARTIAL / BLOCKED_PER_FILE / FRAGMENT_COMPATIBILITY_RISK`。
- 未通过修改旧历史链接或删除 source path 规避 fragment 风险。

---

## Current Authority Protection

- `docs/current/NQ_CI_BASELINE_PLAN.md` 与 `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md` 与 `783bfa68^` blob 一致，完整正文保留。
- 两者未移动、未替换、未 stub 化、未降级、未写为 superseded。
- `docs/baselines/CI_BASELINE_INDEX.md` 指向这两份 current authority，并声明自身是导航索引；不复制正文，不取代 `docs/current/STATUS.md` 的 current-status authority，不把 5B-ENV、5B-SMOKE、4F-B～4F-F、static assertion 写成 completed。

---

## 冻结与范围保护

- G1 五份冻结对象 diff = 0。
- G2 semantic baseline 未被弱化。
- G3 的 17 个 GateJ stub、RUNBOOK、9 份 DIVERGED 文件未被触碰。
- `docs/gates/**`、`docs/archive/**`、`.agents/**`、`templates/**` zero drift。
- CI workflow、backend、frontend、research、scripts、deploy、migration、测试、依赖 zero drift。
- G5～G6 未开始。

---

## Findings

### P0 / P1 / P2 / P3

- 无。

---

## Validation

已执行：

```text
git status --short
git branch --show-current
git log --oneline -20
Migration Map §1D candidate trace
20 routed pre-routing blob vs target blob loop
20 old-path stub template loop
2 current authority blob/protection check
docs/evidence/ci NQ_CI file count check
CI_BASELINE_INDEX semantic check
docs/evidence/ci/README.md semantic check
git grep -nE "(<20 routed names>)\.md#" -- .
git diff --check
git diff --name-status
git diff -- <G1 five frozen objects>
git diff -- docs/gates docs/archive .agents templates
git diff -- .github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration"
git diff -- GateJ 17 stubs / RUNBOOK / strict DIVERGED current docs
```

结果：

```text
ROUTED_OK=20/20
AUTHORITY_RETAINED 2/2
EVIDENCE_NQ_CI_FILE_COUNT=20
CI_BASELINE_INDEX_PASS
CI_EVIDENCE_README_PASS
FRAGMENT_HITS=0
G1_DIFF_EMPTY
DOCS_GATES_ARCHIVE_AGENTS_TEMPLATES_DIFF_EMPTY
PROTECTED_CODE_DIFF_EMPTY
GATEJ_RUNBOOK_DIVERGED_DIFF_EMPTY
```

未运行后端 / 前端 / Python 测试：本轮为 docs-only review，不修改代码、workflow、migration、依赖或运行时逻辑。

---

## 风险与回滚

- 风险：低。review 只新增本文并追加 current-control 记录；不改 G4 implementation 文件、不改 canonical evidence、不改 current authority。
- 回滚：删除本文，并 revert `STATUS.md` / `TESTING.md` / `WORKLOG.md` 的本轮 review 追加段即可；G4 implementation commit `783bfa68` 不受影响。

---

## 状态结论（原样）

```text
G1 authority/evidence index = FROZEN / ACCEPTED
G2 current-control drift repair = FROZEN / ACCEPTED
G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED
G4 CI evidence routing = ACCEPTED / READY FOR FREEZE REVIEW
G5～G6 = NOT STARTED
NQ GateK CI mainline = COMPLETED / ACCEPTED
Batch 5A = FROZEN / ACCEPTED
Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```
