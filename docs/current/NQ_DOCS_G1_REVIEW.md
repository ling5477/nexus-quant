# NQ Documentation Governance — G1 Authority/Evidence Index Review

任务：`NQ-DOCS-GOVERNANCE-G1-AUTHORITY-EVIDENCE-INDEX-REVIEW`

日期：2026-06-18

被审对象：`NQ_DOCS_AUTHORITY_INDEX.md`、`NQ_DOCS_EVIDENCE_INDEX.md`、`NQ_DOCS_MIGRATION_MAP.md`、`NQ_DOCS_G1_IMPLEMENTATION.md` + G1 更新的 7 份文档

任务类型：DOCUMENTATION_GOVERNANCE_REVIEW + AUTHORITY_MODEL_REVIEW + EVIDENCE_PRESERVATION_REVIEW + MIGRATION_MAP_AUDIT

> 本轮为**只读评审**。**没有移动、删除、重命名、归档任何文档**，未改 `docs/gates/**`/`docs/archive/**` 冻结正文或链接，未改 `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、测试、依赖。仅新增本文并更新 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md`。

---

## 审查结论

**结论：`NQ-DOCS-GOVERNANCE-G1-AUTHORITY-EVIDENCE-INDEX-REVIEW：PASS / ACCEPTED`**

- **NQ Docs Governance Plan = ACCEPTED AS IMPLEMENTATION BASELINE**。
- **G1 authority/evidence index = ACCEPTED / READY FOR FREEZE REVIEW**。
- **G2 ~ G6 = NOT STARTED**。
- P0 = 0；P1 = 0；P2 = 0；P3 = 3（均为信息性/by-design，不阻塞）。
- 283 份当前治理对象（278 基线 + 5 增量）**全部获得唯一、无冲突的治理处理，零 orphan**。
- P2-1 / P2-2 / P2-3 收敛**无权威冲突、无证据遗漏、无路径孤儿、无未来迁移风险**。

允许进入 **G1 freeze review**。

---

## 1. 计数与治理范围（git 实测，逐项核验）

| 区域 | 要求基线 | git 实测（HEAD `c3a2cf83`） | 结论 |
| --- | --- | --- | --- |
| 全量 md/txt（工作树） | 283 | **283** | ✓ |
| 盘点基线 | 278 | 278（= 283 − 5 增量） | ✓ |
| `docs/current` 根 | 75（基线）/ 80（含增量） | **80**（75 + 5 增量） | ✓ |
| `docs/current/frontend` | 3 | **3** | ✓ |
| `docs/archive` | 21 | **21** | ✓ |
| `docs/gates` | 152 | **152** | ✓ |
| `docs/templates` | 4 | **4** | ✓ |
| `.agents` | 13 | **13** | ✓ |
| scattered | 10 | **10** | ✓ |

- 基线自洽：75 + 3 + 10 + 152 + 21 + 13 + 4 = **278** ✓（`NQ_DOCS_MIGRATION_MAP.md` §5）。
- 工作树自洽：基线 278 + 增量 5 = **283** ✓。

**283 份唯一治理处理（baseline + delta）**：采用 “278 基线映射 + 5 增量文档清单”。5 份 delta 文档（全部 INDEX_AS_CURRENT_CONTROL / RETAIN_IN_PLACE，`migration_batch = NONE`）：

```text
docs/current/NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md   （盘点后提交，+1 → HEAD 279）
docs/current/NQ_DOCS_AUTHORITY_INDEX.md          （G1 新增）
docs/current/NQ_DOCS_EVIDENCE_INDEX.md           （G1 新增）
docs/current/NQ_DOCS_MIGRATION_MAP.md            （G1 新增）
docs/current/NQ_DOCS_G1_IMPLEMENTATION.md        （G1 新增）
```

- **零 orphan**：migration map §1~§4 覆盖 278 基线全部路径，§0/§1F 列出 5 份增量，合计 283 全覆盖。
- **无冲突主分类 / recommended action**：CI 文档仅在 §1D（FUTURE_MOVE_CANDIDATE/G4）；GateJ superseded 仅在 §1E（FUTURE_SUPERSEDE_CANDIDATE/G3）；治理/集成基线仅在 §1B；DIVERGED+RUNBOOK+CODEX 仅在 §1A；gates/archive/.agents/templates 仅在 §4。无任一路径出现两个相互矛盾的分类或 action。`FULL_SECURITY_AUDIT_REPORT.md` current 副本单一归类 §1E（superseded），其安全证据指针指向 gate-j 权威副本，不构成分类冲突。
- **旧口径**：`277`/`290`/`16 IDENTICAL`/`16 份` 在 5 份治理文档中**无任何当前事实残留**；仅余 (a) `NQ_DOCS_GOVERNANCE_PLAN.md:226` 的 CI run-id `27750279096`/`27750976632` 子串（事实引用，不可改），(b) `MIGRATION_MAP`/`G1_IMPLEMENTATION` 中显式 “已废弃 / 不再保留 / 不再出现” 的订正说明。两者均符合任务豁免口径。

## 2. GateJ 去重模型（核验通过）

- **blob-identical = 18**：`git hash-object` 比对 `docs/current` 根 vs `docs/gates/gate-j` 同名 = 18 IDENTICAL / 9 DIVERGED（独立复跑确认）。
- **FUTURE_SUPERSEDE_CANDIDATE = 17**：`MIGRATION_MAP` §1E 代码块去重后 **17 个唯一 `.md`**（无重复、无遗漏、可追溯）：
  `AUDIT_FIX_REPORT` · `DOC_CLEAN_REPORT` · `FULL_SECURITY_AUDIT_REPORT` · `GATEJ_API_PLAN` · `GATEJ_DB_PLAN` · `GATEJ_FREEZE_ACCEPTANCE_TEMPLATE` · `GATEJ_FREEZE_DEPLOYMENT` · `GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT` · `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT` · `GATEJ_FREEZE_UI_UX_SMOKE_REPORT` · `GATEJ_FRONTEND_PLAN` · `GATEJ_TEST_PLAN` · `GATEJ_WORK_ORDER` · `PLAN_GATEJ` · `PRE_FREEZE_AUDIT_FIX_PLAN` · `PRE_FREEZE_AUDIT_REPORT` · `REPO_SIZE_AUDIT_REPORT`。
- **`docs/current/RUNBOOK.md`**：`MIGRATION_MAP` §1A 与 `G1_IMPLEMENTATION` §2 明确 = blob-identical **第 18 份** / `INDEX_AS_CURRENT_CONTROL` / `RETAIN_IN_PLACE` / **不纳入 G3 supersede 集合**。✓
- **9 份 DIVERGED 分层事实**（全部保留，未标记为重复/删除/替代候选）：`API` · `ARCHITECTURE` · `DB_SCHEMA` · `MODULES` · `README` · `ROADMAP` · `STATUS` · `TESTING` · `WORKLOG`。✓
- 18 = 17 superseded + 1 RUNBOOK；与 9 DIVERGED 不重叠；自洽。

## 3. Authority index（核验通过）

- **14 领域**：项目总状态 / 当前工作入口 / 路线图 / 测试与验证 / 工作日志 / CI 当前状态 / CI baseline / CI security guard / GateJ / GateK / 数据库治理 / 凭证治理 / NQ-DH integration / 前端工作流。
- **每领域唯一 current authority，无并列**。一个文件可锚定多个领域（如 `STATUS.md` 同时为“项目总状态”权威与“CI 当前状态”权威）——属一对多，**非同领域多权威**，不违反规则。“CI 当前状态”（`STATUS.md` CI 段）与 “CI baseline”（`NQ_CI_BASELINE_PLAN.md`）为不同领域，已显式区分。
- current control / 辅证 / 历史证据三层差异清晰；历史证据明确**不替代** current control。
- GateJ 权威 = `docs/gates/gate-j/`（冻结卷宗）；current 17 份过程副本明确为 **non-authoritative future supersede candidates**。RUNBOOK 例外（current control）**不与** GateJ 权威口径冲突（未被列为 GateJ 权威，也未被列为 supersede）。✓

## 4. Evidence index（核验通过）

- 9 类必需入口齐全：① GateJ freeze ② GateK CI mainline ③ CI Batch 1~5A plan/review/first-run/freeze ④ Batch 4C redaction ⑤ 4F-A preflight ⑥ Backlog/residual（5B-ENV / 5B-SMOKE / 4F-B~4F-F / static workflow assertion）⑦ 数据库治理 ⑧ credential governance ⑨ NQ-DH Integration-0 合同与安全边界。
- **只索引/链接，不复制、不改写冻结结论**（§开头与§边界声明两处声明）。
- **optional backlog 未被误写为 completed / current required gate**：5B-ENV = P1 / NOT STARTED；5B-SMOKE = BLOCKED BY 5B-ENV；4F-B~4F-F = OPTIONAL BACKLOG / NOT STARTED；static assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED（grep 复核无 completed 误标）。✓

## 5. Migration map（核验通过）

- 每组条目含全部 10 字段：`path`（code-block 等效逐文件清单）/ 主分类 / domain tags / authority level / lifecycle / recommended action / target location / migration batch / link compatibility strategy / retention rationale。grep 确认各字段在 ~10–12 组中一致出现。
- `docs/gates/` / `docs/archive/` / `.agents/` / `docs/templates/`（§4，190 份）统一 `RETAIN_IN_PLACE` / `migration_batch = NONE` / `move_precondition = NOT_APPLICABLE`。✓
- **无 `DELETE NOW`**：全部出现均为否定语境（“无 DELETE NOW”/“不出现 DELETE NOW”）。✓
- `FUTURE_ARCHIVE_CANDIDATE` / `FUTURE_SUPERSEDE_CANDIDATE` 明确不等于可删除（§4B `ARCHIVE_CANDIDATE` 标注 already-archived / RETAIN_IN_PLACE；§1E supersede 明确权威副本保留 gate-j、移除 ≠ 删除证据）。✓
- G3/G4/G5 迁移前置条件明确：redirect/index 已存在 + 逐文件映射完成 + 回滚清单具备 + 历史证据未改写（`MIGRATION_MAP` §1D/§1E move precondition + 实施纪律；`G1_IMPLEMENTATION` §5）。✓

## 6. G1 边界（核验通过）

- 本轮（及 G1 commit `c3a2cf83` / review commit `581e9aaa`）**没有移动、删除、重命名、归档任何文档**：`git diff --name-only e3b12e33..c3a2cf83` 仅含 `docs/current/*` + `docs/README.md` + `docs/DOC_RULES.md`（12 份 docs）。
- **未改 `docs/gates/**` / `docs/archive/**` 冻结正文或历史链接**：上述 diff 对 `docs/gates docs/archive .github/workflows/ci.yml backend frontend research scripts deploy templates .agents` **为空**。
- G1 **未提前处理**（已分别归属，且 `G1_IMPLEMENTATION` §5 明确）：
  - `docs/README.md` “不重复” vs “迁移或复制” 规则矛盾 → **G2**。
  - `docs/README.md` GateJ 导航与 GateK/CI 状态漂移 → **G2**。
  - `docs/current` `API.md`/`DB_SCHEMA.md` malformed 前导 `/` 链接 → **G2**。
  - GateJ 17 份候选实际收敛 → **G3**。
  - CI evidence 实际目录归位 → **G4**（目录收口 → **G5**）。

---

## Findings

### P0 / P1 / P2

- 无。

### P3（信息性 / by-design，不阻塞，供 freeze review 参考）

- **P3-1**：`MIGRATION_MAP` §1D 的 22 份 CI 文档 `target location = docs/evidence/ci/`、§5 引用 `docs/baselines/CI_BASELINE_INDEX.md` —— 这些目标目录**当前尚不存在**，属 FUTURE_MOVE_CANDIDATE 的预期状态，且由 `move_precondition`（先建 index/redirect）门控，非缺陷。freeze review 不应据此判 broken path。
- **P3-2**：authority index 把 DH 仓 `docs/current/NQ_DH_INTEGRATION_SECURITY_AUDIT_REPORT.md` 列为历史证据，已标注 “外部，只读引用”；该文件不在 NQ 278 盘点内（正确排除），仅作跨仓指针。
- **P3-3**：任务建议 grep `277|290` 会命中 CI run-id `27750279096` 子串与 “已废弃” 订正说明；freeze review 应按任务豁免口径视为预期，非旧口径残留。

---

## 检查文件 / 修改文件 / 验证 / 风险 / 回滚

- **检查文件（只读）**：4 份 G1 新增文档 + 7 份 G1 更新文档；`docs/README.md`、`docs/DOC_RULES.md`；`git ls-files` 全量枚举；`docs/current` 根 ↔ `gate-j` blob 比对；`docs/gates`/`docs/archive`/`.agents`/`templates` 计数；两条 governance commit 的 `git diff --name-only`。
- **修改文件（本轮）**：新增 `docs/current/NQ_DOCS_G1_REVIEW.md`；更新 `docs/current/README.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`（仅追加评审记录）。
- **验证**：docs-only，无构建/测试；HEAD 工作树 283 / 基线 278 / 各分区计数一致；18-17-RUNBOOK-9 模型一致；governance commit 对禁止范围 diff 为空；`git diff --check` clean。
- **风险**：零迁移、零代码、零不可逆操作；最坏情况仅本评审文档文字需微调。
- **回滚**：删除 `NQ_DOCS_G1_REVIEW.md` 并 revert 4 份 current 文档的本轮追加段即可完全回滚。

---

## 状态结论（原样）

- **NQ Docs Governance Plan = ACCEPTED AS IMPLEMENTATION BASELINE**。
- **G1 authority/evidence index = ACCEPTED / READY FOR FREEZE REVIEW**。
- **G2 ~ G6 = NOT STARTED**。
- **NQ GateK CI mainline = COMPLETED / ACCEPTED**。
- **Batch 5A = FROZEN / ACCEPTED**。
- **Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**；Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- **LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现**。

## 本轮变更声明

- 本轮**没有移动、删除、重命名、归档任何文档**，未改写任何冻结快照文本或历史链接。
- 未改 `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、测试、依赖。
- 未启动 G2~G6、5B-ENV、5B-SMOKE、4F-B~4F-F，未开启 LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。
- 仅新增本评审文档并在 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md` 追加评审记录。
