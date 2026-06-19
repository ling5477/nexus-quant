# NQ Docs Current Cleanup — Round 1 Implementation

任务：`NQ-DOCS-CURRENT-LEANUP-R1-IMPLEMENTATION`

日期：2026-06-19

执行轮次：**Round 1 / 3**（R1 implementation → R2 review → R3 final freeze）。不扩展为新的 G1～G6 长链路。

结论：**NQ-DOCS-CURRENT-LEANUP-R1-IMPLEMENTATION：PASS / READY FOR REVIEW**（含 3 个 `BLOCKED_PER_FILE`，按规则保留在 current）。

---

## 1. 目标与边界

把 `docs/current` 从“安全保留态”收敛为“真正 current 控制态”：减少 current 根目录文件数量，把过程证据、治理 review/freeze、旧路径 compatibility stub 移出 current，并更新仓库内部链接到 canonical 位置。

- 不删除历史正文；移出一律用 `git mv` 保留历史。
- 不否定 G1～G6 final freeze（历史基线）；本轮是在其之后的 current 物理瘦身。
- 存在 fragment 入链或无法安全改链的文件标记 `BLOCKED_PER_FILE`，保留在 current。
- 不修改 canonical GateJ（`docs/gates/gate-j/**`）、canonical CI evidence（`docs/evidence/ci/**`）、G1 五份冻结对象正文、backend/frontend/research/scripts/deploy/migration/测试/依赖、`.github/workflows/ci.yml`。

---

## 2. current before / after 统计

| 指标 | 数量 |
| --- | ---: |
| `docs/current` 根目录文件数 before | 96 |
| `docs/current` 根目录 Markdown 文件数 before | 96 |
| `docs/current` 根目录 TXT 文件数 before | 0 |
| 移出 current 的文件数（总） | 51 |
| ├ governance evidence moved | 17 |
| ├ GateJ stub moved | 14 |
| └ CI stub moved | 20 |
| 保留在 current 的文件数（含新增报告） | 46 |
| 其中 BLOCKED（保留在 current） | 3 |
| 本轮新增 current 文件 | 1（本报告） |
| `docs/current` 根目录文件数 after | 46 |
| `docs/current` 根目录 Markdown 文件数 after | 46 |
| `docs/current` 根目录 TXT 文件数 after | 0 |

核算：96 − 51 moved + 1 new report = 46。

---

## 3. 保留在 current 的文件清单（46）

### 当前控制入口（10）
README.md, STATUS.md, ROADMAP.md, TESTING.md, WORKLOG.md, API.md, DB_SCHEMA.md, ARCHITECTURE.md, MODULES.md, RUNBOOK.md

### 当前治理权威基线（5）
NQ_DOCS_GOVERNANCE_PLAN.md, NQ_DOCS_AUTHORITY_INDEX.md, NQ_DOCS_EVIDENCE_INDEX.md, NQ_DOCS_MIGRATION_MAP.md, NQ_DOCS_G1_IMPLEMENTATION.md

### 当前 CI authority（2）
NQ_CI_BASELINE_PLAN.md, NQ_CI_SECURITY_GUARD_PLAN.md

### BLOCKED GateJ stub（3，保留在 current）
GATEJ_API_PLAN.md, GATEJ_DB_PLAN.md, GATEJ_TEST_PLAN.md（原因见 §7）

### 其他保留的当前控制 / 规划 / 审计文档（25）
- GateK：GATEK_PLAN.md, GATEK_ARCHITECTURE_BASELINE_REVIEW.md
- 前端 / 后端契约：FRONTEND_DESIGN_SYSTEM.md, BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md
- Codex workflow：CODEX_PROJECT_INSTRUCTIONS.md, NQ_DH_CODEX_PLUGIN_WORKFLOW.md, NQ_DH_CODEX_TASK_TEMPLATES.md, NQ_DH_WORKFLOW_ROUTER_SKILL.md
- DB 治理：DB_SCHEMA_GOVERNANCE_PLAN.md, DB_SCHEMA_GOVERNANCE_REVIEW.md
- Credential 治理（8）：CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md, CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md, CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md, CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md, CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md, CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md, CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md, CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md, CREDENTIAL_REVOCATION_GOVERNANCE_REVIEW.md, CREDENTIAL_ROTATE_GOVERNANCE_REVIEW.md
- NQ-DH Integration-0（4）：NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md, NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md, NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md, NQ_DH_INTEGRATION0_SECURITY_POLICY.md
- 测试隔离审计：NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md
- 本报告：NQ_DOCS_CURRENT_CLEANUP_R1_IMPLEMENTATION.md

> 说明：上述非权威基线类文档（credential / DB 治理 / DH Integration-0 / test isolation / GateK / 前端）本轮按 “current authority 不确定 → 先保留” 原则留在 current，标记 `RETAINED_UNCLEAR_AUTHORITY`，待 R2/R3 或后续单独 review 时按 Authority Index 判定是否进一步归档。R1 不强行移出。

---

## 4. 移到 `docs/evidence/governance/` 的文件清单（17）

`git mv`，正文未改写。新增导航 `docs/evidence/governance/README.md`（只索引不复制正文）。

NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md, NQ_DOCS_G1_REVIEW.md, NQ_DOCS_G1_FREEZE_REVIEW.md, NQ_DOCS_G2_CURRENT_CONTROL_REPAIR.md, NQ_DOCS_G2_REVIEW.md, NQ_DOCS_G2_FREEZE_REVIEW.md, NQ_DOCS_G3_GATEJ_REDIRECT_CONSOLIDATION.md, NQ_DOCS_G3_REVIEW.md, NQ_DOCS_G3_FREEZE_REVIEW.md, NQ_DOCS_G4_CI_EVIDENCE_ROUTING.md, NQ_DOCS_G4_REVIEW.md, NQ_DOCS_G4_FREEZE_REVIEW.md, NQ_DOCS_G5_DIRECTORY_CLOSURE_PREFLIGHT.md, NQ_DOCS_G5_PREFLIGHT_REVIEW.md, NQ_DOCS_G5_FREEZE_REVIEW.md, NQ_DOCS_G6_DEFAULT_EMPTY_DELETION_REVIEW.md, NQ_DOCS_GOVERNANCE_FINAL_FREEZE_REVIEW.md

---

## 5. 移到 `docs/evidence/compatibility/gatej-current-stubs/` 的文件清单（14）

`git mv` + 修正 stub 自身 canonical 相对链接 `../gates/gate-j/X.md` → `../../../gates/gate-j/X.md`（深度补偿，目标可解析）。新增导航 README。

AUDIT_FIX_REPORT.md, DOC_CLEAN_REPORT.md, FULL_SECURITY_AUDIT_REPORT.md, GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md, GATEJ_FREEZE_DEPLOYMENT.md, GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md, GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md, GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md, GATEJ_FRONTEND_PLAN.md, GATEJ_WORK_ORDER.md, PLAN_GATEJ.md, PRE_FREEZE_AUDIT_FIX_PLAN.md, PRE_FREEZE_AUDIT_REPORT.md, REPO_SIZE_AUDIT_REPORT.md

---

## 6. 移到 `docs/evidence/compatibility/ci-current-stubs/` 的文件清单（20）

`git mv` + 修正 stub 自身 canonical 相对链接 `../evidence/ci/X.md` → `../../ci/X.md`（深度补偿，目标可解析）。新增导航 README。

NQ_CI_NO_OUTBOUND_GUARD_PLAN.md, NQ_CI_POSTGRES_FLYWAY_PLAN.md, NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md, NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md, NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md, NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md, NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md, NQ_CI_LOG_REDACTION_PROOF_PLAN.md, NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md, NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md, NQ_CI_DEPENDENCY_AUDIT_PLAN.md, NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md, NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md, NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md, NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md, NQ_CI_FRONTEND_E2E_PLAN.md, NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md, NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md, NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md, NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md

---

## 7. BLOCKED 文件清单与原因（3）

| 文件 | 标记 | 入链来源 | 原因 |
| --- | --- | --- | --- |
| `docs/current/GATEJ_API_PLAN.md` | `BLOCKED_PER_FILE / DIVERGED_INBOUND_LINK` | `docs/current/API.md:233` → `[GATEJ_API_PLAN.md](./GATEJ_API_PLAN.md)` | 入链位于受保护 DIVERGED 活文档 API.md（本轮不在允许改写集合）；无法安全改链 |
| `docs/current/GATEJ_DB_PLAN.md` | `BLOCKED_PER_FILE / DIVERGED_INBOUND_LINK` | `docs/current/DB_SCHEMA.md:375` → `[GATEJ_DB_PLAN.md](./GATEJ_DB_PLAN.md)` | 入链位于受保护 DIVERGED 活文档 DB_SCHEMA.md；无法安全改链 |
| `docs/current/GATEJ_TEST_PLAN.md` | `BLOCKED_PER_FILE / DIVERGED_INBOUND_LINK` | `docs/current/TESTING.md:3552` → `[GATEJ_TEST_PLAN.md](./GATEJ_TEST_PLAN.md)` | 入链位于 DIVERGED 活文档 TESTING.md；R1 保守不改写其入口链接（仅按 §F 追加状态记录），保留在 current |

> 这 3 个文件的 canonical 全文仍在 `docs/gates/gate-j/`，其当前 current stub 入链均能正常解析（文件未移动），无 broken link。R2 review 复核是否在受控前提下改写这 3 处 DIVERGED 入口链接并移出，R1 不强行处理。
>
> 无任何 `FRAGMENT_COMPATIBILITY_RISK`：三组移出对象的 `<file>.md#` fragment 入链全仓为 0（见 §9）。

---

## 8. 内部链接重写规则

| 场景 | 规则 | 本轮处理 |
| --- | --- | --- |
| governance evidence 入链 | 旧 `docs/current/<file>` → `docs/evidence/governance/<file>` | 全仓无指向这些文件的 markdown 超链接（仅 prose/backtick 提及）；导航统一改由 `docs/evidence/governance/README.md` 承载。`docs/README.md` 治理证据块改指新目录。governance 文件正文未改写（§B）。 |
| GateJ stub 入链 | 直接指向 canonical `docs/gates/gate-j/<file>` | 14 个可移出 stub 自身 canonical 链接已深度补偿为 `../../../gates/gate-j/<file>`；`docs/README.md` 内 PLAN_GATEJ / GATEJ_WORK_ORDER / DOC_CLEAN_REPORT 指针改向 gate-j canonical + 兼容归档位置。3 个 BLOCKED stub 入链（API.md / DB_SCHEMA.md / TESTING.md）未改，文件留 current，链接仍解析。 |
| CI stub 入链 | 直接指向 canonical `docs/evidence/ci/<file>` | 20 个 stub 自身 canonical 链接已深度补偿为 `../../ci/<file>`；全仓无其他 markdown 超链接指向 CI stub。 |
| current/README | 重写为真正 current 入口页（§E） | 已重写：列出当前控制入口 / 治理权威 / CI authority / 历史证据位置（gate-j、evidence/ci、evidence/governance、evidence/compatibility）。 |
| docs/README | 记录瘦身 + 新证据位置 | 已更新历史证据位置、治理证据归档指针、R1 状态。 |

> prose/backtick 文件名提及（非 markdown 超链接）属历史 as-of-time 记录，不逐条改写（尤其 STATUS / WORKLOG 追加式日志），R1 不动其历史条目，避免破坏 append-only 日志。

---

## 9. fragment 入链检查结果

| 集合 | `<file>.md#` fragment 入链数 |
| --- | ---: |
| governance evidence（17） | 0 |
| GateJ stub（17，含 BLOCKED） | 0 |
| CI stub（20） | 0 |

无 fragment 风险被忽略；无文件因 fragment 触发 `BLOCKED_PER_FILE`。

---

## 10. 禁止范围 diff 结果

| 范围 | diff |
| --- | --- |
| `docs/gates/**`（canonical GateJ） | empty |
| `docs/evidence/ci/**`（canonical CI evidence） | empty |
| G1 五份冻结对象正文 | empty |
| `.github/workflows/ci.yml` | empty |
| `backend / frontend / research / scripts / deploy` | empty |
| `backend/**/db/migration` | empty |

git rename 检测：17 pure rename（governance，零正文改动）+ 34 rename+modify（stub，仅 1 行 canonical 链接深度补偿）+ 2 modified（docs/README.md、docs/current/README.md）+ 新增（3 README + 本报告 + STATUS/TESTING/WORKLOG 追加）。无 delete-without-rename。

---

## 11. 风险与回滚方案

风险：低。全部移出为 `git mv`（保留历史），无正文删除；stub 自链接深度补偿后目标可解析；canonical 与 G1 冻结对象未触碰；无代码 / workflow / migration 改动。

主要残留（交 R2 review）：

- 3 个 BLOCKED GateJ stub 仍在 current（DIVERGED 入链）。
- 17 个 governance evidence 文件正文内若干 **inline-code 形式**的相对路径示例（如 `` `](../gates/gate-i/...)` ``）随目录深度变化语义上偏移，但均在 backtick 代码片段内、非渲染超链接，按 §B “不改写正文” 保留原样。
- STATUS / WORKLOG 历史日志中对已移出文件的 prose 提及未逐条改写（append-only 历史记录）。

回滚边界：

```powershell
# 撤销本轮（未提交时）
git restore --staged docs/
git restore docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md docs/README.md docs/current/README.md
# 移回 git mv 的文件（如已 mv 但未提交，restore --staged 后工作区仍在新路径，需手动 mv 回或 checkout）
git checkout -- docs/
# 删除新增文件
Remove-Item -LiteralPath docs/current/NQ_DOCS_CURRENT_CLEANUP_R1_IMPLEMENTATION.md -Force
Remove-Item -LiteralPath docs/evidence/governance/README.md -Force
Remove-Item -Recurse -LiteralPath docs/evidence/compatibility -Force
```

如已提交：`git revert <r1-commit>`（单 commit 可整体回滚 rename + link rewrite + 新增）。

---

## 12. 剩余轮次

```text
Round 1 = IMPLEMENTATION（本轮，IMPLEMENTED / READY FOR REVIEW）
Round 2 = REVIEW
Round 3 = FINAL FREEZE
```

不再扩展为新的 G1～G6 长链路；不开 G7/G8。R2 复核移动正确性、链接完整性、BLOCKED 处理与证据链；R3 做最终冻结。

```text
NQ Docs Current Cleanup = IMPLEMENTED / READY FOR REVIEW
Round = 1 / 3
Round 2 = REVIEW
Round 3 = FINAL FREEZE
docs/current = PHYSICALLY REDUCED (96 -> 46 root .md)
No historical evidence deleted
No code/workflow/migration changed
G1-G6 governance baseline remains historical reference
NQ GateK CI mainline = COMPLETED / ACCEPTED
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```
