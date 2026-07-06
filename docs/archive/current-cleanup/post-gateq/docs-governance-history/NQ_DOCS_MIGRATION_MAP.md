# NQ Documentation Migration Map（逐文件迁移映射）

任务：`NQ-DOCS-GOVERNANCE-G1-AUTHORITY-EVIDENCE-INDEX`

日期：2026-06-18

状态：**G1 = IMPLEMENTED / READY FOR REVIEW**

> 本文为**逐文件 / 等效逐文件迁移映射**，覆盖全部 **278 份**盘点基线 Markdown/TXT，外加 **5 份**治理线增量文档（reconcile 见 §0）。**本轮没有移动、删除、重命名或归档任何文件**；所有 `target location` / `migration batch` 均为**未来建议**，须在对应 Gate（G2~G6）单独执行。
>
> 禁止口径：本文不出现 `DELETE NOW`；`ARCHIVE_CANDIDATE` 一律不等于“可立即删除”。

---

## 0. 计数基线与 reconcile（P2-1 收敛）

以 `git ls-files`（排除 `node_modules/ target/ build/ dist/ test-results/`）实测为准：

| 区域 | 盘点基线（P2-1 canonical） | 说明 |
| --- | --- | --- |
| 全量 md/txt | **278** | 旧计划 “277” 已废弃；§2 旧表逐行求和 “290” 已废弃（均不再保留） |
| `docs/current` 根 | **75** | — |
| `docs/current/frontend` | **3** | 旧计划 “15” 已废弃 |
| `docs/gates` | **152** | — |
| `docs/archive` | **21** | 旧计划 “22” 已废弃 |
| `docs/templates` | **4** | — |
| `.agents` | **13** | — |
| scattered（repo root 3 + `.github` 1 + `docs` 根 2 + frontend 代码 2 + research 2） | **10** | — |
| **基线合计** | **278** | 75 + 3 + 152 + 21 + 4 + 13 + 10 = 278 ✓ |

**治理线增量文档（不计入 278 基线，additive，全部 INDEX_AS_CURRENT_CONTROL / RETAIN_IN_PLACE）**：

| path | 何时加入 | 备注 |
| --- | --- | --- |
| `docs/current/NQ_DOCS_GOVERNANCE_PLAN.md` | 盘点提交时 | **已计入 278 基线**（见 §1F） |
| `docs/current/NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md` | 盘点后提交（+1） | additive；HEAD 现为 279 |
| `docs/current/NQ_DOCS_AUTHORITY_INDEX.md` | G1 本轮新增 | additive |
| `docs/current/NQ_DOCS_EVIDENCE_INDEX.md` | G1 本轮新增 | additive |
| `docs/current/NQ_DOCS_MIGRATION_MAP.md` | G1 本轮新增（本文） | additive |
| `docs/current/NQ_DOCS_G1_IMPLEMENTATION.md` | G1 本轮新增 | additive |

reconcile：基线 278 + 增量 5（review + 4 份 G1 doc，`NQ_DOCS_GOVERNANCE_PLAN.md` 本身已在 278 内）= **283**（G1 完成后工作树 md/txt 总数）。G1 开始时 HEAD = 279（278 + review）。

字段定义：`path` / `主分类` / `domain tags` / `authority level` / `lifecycle` / `recommended action` / `target location` / `migration batch` / `link compatibility strategy` / `retention rationale`。

`recommended action` 取值域（仅此 5 种，无 `DELETE NOW`）：
`RETAIN_IN_PLACE` / `INDEX_AS_CURRENT_CONTROL` / `FUTURE_MOVE_CANDIDATE` / `FUTURE_ARCHIVE_CANDIDATE` / `FUTURE_SUPERSEDE_CANDIDATE`。

---

## 1. `docs/current` 根（76 = 75 基线 + 1 review 增量；G1 后 80）

### 1A. 当前唯一权威活文档（11）

- 主分类：CURRENT_CONTROL
- domain tags：项目状态 / 架构 / 模块 / API / DB / 路线 / 测试 / 工作日志 / 运行手册 / 代理规范
- authority level：**CURRENT_AUTHORITY（primary）**
- lifecycle：ACTIVE（`RUNBOOK.md` = ACTIVE，自 GateJ 未更新但仍为当前手册）
- recommended action：`INDEX_AS_CURRENT_CONTROL`
- target location：`docs/current/`（不变）
- migration batch：`NONE`
- link compatibility strategy：N/A（不移动）
- retention rationale：当前事实唯一真源；其中 9 份与 gate-j 快照 DIVERGED，属分层事实（current 活文档 vs 冻结快照），不得当重复删除；`RUNBOOK.md` blob 与 gate-j 相同但为当前手册（**retain-in-place，第 18 份 blob-identical，但不纳入 superseded 去重**）。

```text
API.md  ARCHITECTURE.md  DB_SCHEMA.md  MODULES.md  README.md  ROADMAP.md
STATUS.md  TESTING.md  WORKLOG.md            # 9 份 DIVERGED（分层事实）
RUNBOOK.md                                   # blob-identical 第18份，retain-in-place
CODEX_PROJECT_INSTRUCTIONS.md
```

### 1B. 当前生效基线（GateK 规划 + 治理 / 集成基线）（22）

- 主分类：CANONICAL_BASELINE
- domain tags：GateK / 数据库治理 / 凭证治理 / NQ-DH integration / NQ-DH workflow / 前端设计系统
- authority level：**CURRENT_BASELINE_AUTHORITY**
- lifecycle：ACTIVE_BASELINE（已冻结但仍为当前依据）
- recommended action：`INDEX_AS_CURRENT_CONTROL`
- target location：`docs/current/`（可选未来 `docs/current/governance/`，纯组织，G5 可选）
- migration batch：`NONE`（G5 可选组织化，不移出 current）
- link compatibility strategy：N/A（如 G5 组织化则 authority index 同步）
- retention rationale：当前 Gate 规划与治理 / 集成基线，current 仍直接依赖；不可删除。

```text
GATEK_PLAN.md  GATEK_ARCHITECTURE_BASELINE_REVIEW.md
DB_SCHEMA_GOVERNANCE_PLAN.md  DB_SCHEMA_GOVERNANCE_REVIEW.md
CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md  CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md
CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md  CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md
CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md  CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md
CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md  CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md
CREDENTIAL_REVOCATION_GOVERNANCE_REVIEW.md  CREDENTIAL_ROTATE_GOVERNANCE_REVIEW.md
NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md  NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md
NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md  NQ_DH_INTEGRATION0_SECURITY_POLICY.md
NQ_DH_CODEX_PLUGIN_WORKFLOW.md  NQ_DH_CODEX_TASK_TEMPLATES.md  NQ_DH_WORKFLOW_ROUTER_SKILL.md
FRONTEND_DESIGN_SYSTEM.md
```

### 1C. current 保留的历史证据（计划 / review）（2）

- 主分类：HISTORICAL_EVIDENCE
- domain tags：回测 API 规划 / 测试隔离·安全
- authority level：SUPPORTING_EVIDENCE
- lifecycle：HISTORICAL（保留在 current）
- recommended action：`RETAIN_IN_PLACE`
- target location：`docs/current/`（未来 G5 可选移入 `docs/evidence/`）
- migration batch：`NONE`
- link compatibility strategy：N/A
- retention rationale：尚未成 Gate 快照的当前阶段证据，保留可追溯。

```text
BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md
NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md
```

### 1D. CI 过程 / 基线 / 冻结证据（22 份 NQ_CI_*）→ 未来归位

- 主分类：CANONICAL_BASELINE（baseline 类）+ HISTORICAL_EVIDENCE（plan/review/freeze 类）
- domain tags：CI / SECURITY / DATABASE
- authority level：`NQ_CI_BASELINE_PLAN.md` + `NQ_CI_SECURITY_GUARD_PLAN.md` = **CURRENT_CI_AUTHORITY（pointer）**；其余 = CI evidence
- lifecycle：baseline ACTIVE_BASELINE；freeze/review/impl HISTORICAL
- recommended action：`FUTURE_MOVE_CANDIDATE`
- target location：`docs/evidence/ci/`（历史 plan/review/freeze）+ `docs/baselines/CI_BASELINE_INDEX.md`（索引指针）
- migration batch：**G4**
- move precondition：`docs/baselines/CI_BASELINE_INDEX.md` 已建 + 逐文件 redirect 映射 + `STATUS.md`/`WORKLOG.md` 内相对引用同步
- link compatibility strategy：baselines index redirect；旧路径保留在 index redirect 列
- retention rationale：CI Batch 1~5A / 4C / 4F-A 的 plan/review/first-run/freeze/proof，**不可删除**；`NQ_CI_BASELINE_PLAN.md` + `NQ_CI_SECURITY_GUARD_PLAN.md` 在归位后由 CI_BASELINE_INDEX 继续指向为权威。

```text
NQ_CI_BASELINE_PLAN.md            NQ_CI_SECURITY_GUARD_PLAN.md          # 归位后仍为权威 pointer
NQ_CI_NO_OUTBOUND_GUARD_PLAN.md
NQ_CI_POSTGRES_FLYWAY_PLAN.md     NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md
NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md  NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md      NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md
NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md  NQ_CI_LOG_REDACTION_PROOF_PLAN.md
NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md  NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md
NQ_CI_DEPENDENCY_AUDIT_PLAN.md    NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md
NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md  NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md
NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md
NQ_CI_FRONTEND_E2E_PLAN.md        NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md
NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md  NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md
NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md
```

### 1E. GateJ superseded 收敛候选（17）→ 未来去重（**evidence 不丢**）

- 主分类：DUPLICATE_OR_SUPERSEDED_CANDIDATE
- domain tags：GateJ 过程 / 计划 / freeze 报告 / 审计报告
- authority level：**NON_AUTHORITATIVE**（权威副本在 `docs/gates/gate-j/`）
- lifecycle：SUPERSEDED（与 gate-j 冻结副本 **blob 完全一致**）
- recommended action：`FUTURE_SUPERSEDE_CANDIDATE`
- target location：从 `docs/current/` 移除；**权威副本永久保留在 `docs/gates/gate-j/`**
- migration batch：**G3**
- move precondition：`docs/README.md` / authority index 内 GateJ→gate-j redirect 已建 + current 内部引用更新；逐文件 `git mv` / 移除可逆
- link compatibility strategy：redirect index 将 GateJ 主题指向 `docs/gates/gate-j/`；移除后不再有指向 current 副本的链接
- retention rationale：**blob-identical 权威副本已存在于 `docs/gates/gate-j/`，证据永不丢失；移除 current 重复副本 ≠ 删除证据**。`RUNBOOK.md` 虽同为 blob-identical，但属当前手册（见 1A），**不在本列**。

```text
AUDIT_FIX_REPORT.md                    DOC_CLEAN_REPORT.md
FULL_SECURITY_AUDIT_REPORT.md          GATEJ_API_PLAN.md
GATEJ_DB_PLAN.md                       GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md
GATEJ_FREEZE_DEPLOYMENT.md             GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md
GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md
GATEJ_FRONTEND_PLAN.md                 GATEJ_TEST_PLAN.md
GATEJ_WORK_ORDER.md                    PLAN_GATEJ.md
PRE_FREEZE_AUDIT_FIX_PLAN.md           PRE_FREEZE_AUDIT_REPORT.md
REPO_SIZE_AUDIT_REPORT.md
```

> 注：`FULL_SECURITY_AUDIT_REPORT.md` 同时是安全基线“不可删除”项——含义为**其权威副本（gate-j）永不删除**；current 重复副本仍可在 G3 redirect 后移除，两者不矛盾。

### 1F. 治理线 meta-docs（6 = 1 基线内 + 5 增量）

- 主分类：CURRENT_CONTROL（governance meta）
- domain tags：documentation governance
- authority level：governance process（非业务事实）
- lifecycle：ACTIVE
- recommended action：`INDEX_AS_CURRENT_CONTROL`
- target location：`docs/current/`（不变）
- migration batch：`NONE`
- link compatibility strategy：N/A
- retention rationale：治理计划 / 评审 / 索引 / 映射；当前阶段控制文档。

```text
NQ_DOCS_GOVERNANCE_PLAN.md             # 计入 278 基线
NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md      # additive (+1, HEAD 279)
NQ_DOCS_AUTHORITY_INDEX.md             # additive (G1)
NQ_DOCS_EVIDENCE_INDEX.md              # additive (G1)
NQ_DOCS_MIGRATION_MAP.md               # additive (G1，本文)
NQ_DOCS_G1_IMPLEMENTATION.md           # additive (G1)
```

> §1 计数核对：1A(11)+1B(22)+1C(2)+1D(22)+1E(17)+1F(6)=**80** = current 根 75 基线 + 1 review + 4 G1。基线占比 = 1A11+1B22+1C2+1D22+1E17+1F(plan)1 = **75** ✓。

---

## 2. `docs/current/frontend`（3）

- 主分类：CANONICAL_BASELINE
- domain tags：FRONTEND / design system / build matrix / design tokens
- authority level：CURRENT_BASELINE_AUTHORITY（前端）
- lifecycle：ACTIVE_BASELINE
- recommended action：`RETAIN_IN_PLACE`
- target location：`docs/current/frontend/`（不变）
- migration batch：`NONE`
- link compatibility strategy：N/A
- retention rationale：当前前端设计系统基线（见 `[[nq-frontend-design-system-v1-v2]]` 口径），保留。

```text
docs/current/frontend/NQ_DESIGN_TOKENS_V2.md
docs/current/frontend/NQ_FRONTEND_BUILD_MATRIX.md
docs/current/frontend/ref/nq-design-system/README.md
```

---

## 3. Scattered 当前控制文档（10）

- 主分类：CURRENT_CONTROL
- authority level：CURRENT_AUTHORITY（各自领域：仓库入口 / 代理规范 / PR 模板 / 文档导航·规则 / 前端代码 README / 研究链 README）
- lifecycle：ACTIVE
- recommended action：`INDEX_AS_CURRENT_CONTROL`（`docs/README.md`/`docs/DOC_RULES.md`）/ `RETAIN_IN_PLACE`（其余）
- target location：原路径（不变）
- migration batch：`NONE`（`docs/README.md`/`docs/DOC_RULES.md` 的状态/导航漂移与 P3 规则矛盾留待 **G2**，本轮仅新增治理入口）
- link compatibility strategy：N/A
- retention rationale：仓库级与子系统级当前入口/规范，不移动。

```text
README.md  AGENTS.md  CLAUDE.md                      # repo root
.github/pull_request_template.md
docs/README.md  docs/DOC_RULES.md                    # 导航/规则（G2 修漂移）
frontend/README.md  frontend/src/nq-design-system/README.md
research/py/README.md  research/py/datasets/README.md
```

---

## 4. RETAIN_IN_PLACE 批量区（冻结卷宗 / 归档 / 工具链 / 模板）（190）

以下全部统一属性，**本轮与后续 G2~G6 均不移动**：

```text
recommended_action = RETAIN_IN_PLACE
migration_batch    = NONE
move_precondition  = NOT_APPLICABLE
link_compatibility = N/A（不移动，不改冻结快照链接）
```

### 4A. `docs/gates/**`（152）— HISTORICAL_EVIDENCE（冻结卷宗，只读）

retention rationale：所有已完成 Gate 的冻结卷宗，含 gate-j 内 17 份 superseded 权威副本 + 9 份 DIVERGED 快照 + FREEZE_SUMMARY/ADR；**绝对不可删除、不可改写文本、不可改链接**（gate-h/gate-j 内 4 处历史 `./GATEI_*` 失效链接仅由 evidence index redirect 说明，不改快照）。按子目录计数：README 1 / gate-a 11 / gate-b 11 / gate-c 13 / gate-d 21 / gate-e 15 / gate-f 12 / gate-g 9 / gate-h 17 / gate-i 14 / gate-j 28 = 152。完整清单：

```text
docs/gates/README.md
docs/gates/gate-a/{ARCHITECTURE,CONTRACTS,DB_SCHEMA,DECISIONS,EVOLUTION_RULES,GATE_A_CHECKLIST,MODULES,NUMERIC_POLICY,RECOVERY_RUNBOOK,ROADMAP,WORK}.md
docs/gates/gate-b/{ARCHITECTURE,CONTRACTS,DB_SCHEMA,DECISIONS,EVOLUTION_RULES,GATE_B_CHECKLIST,MODULES,NUMERIC_POLICY,RECOVERY_RUNBOOK,ROADMAP,WORK}.md
docs/gates/gate-c/{ARCHITECTURE,CONTRACTS,DB_SCHEMA,DECISIONS,EVOLUTION_RULES,GATE_C_CHECKLIST,MODULES,NUMERIC_POLICY,PR_SPLIT_PLAN,RECOVERY_RUNBOOK,ROADMAP,SOURCES,WORK}.md
docs/gates/gate-d/{ARCHITECTURE,COMPENSATION_SYNC,CONTRACTS,DB_SCHEMA,DECISIONS,EVOLUTION_RULES,FREEZE_SUMMARY,GATE_D_CHECKLIST,MODULES,NUMERIC_POLICY,PR_SPLIT_PLAN,README,RECOVERY_RUNBOOK,RISK_RULES,SOURCES,STATE_MACHINE,TEST_CASES,WORK}.md
docs/gates/gate-d/adr/{ADR-001-unified-execution-entry,ADR-002-risk-before-execution,ADR-003-rest-first-ws-accelerated}.md
docs/gates/gate-e/{ARCHITECTURE,CONTRACTS,DB_SCHEMA,DECISIONS,EVOLUTION_RULES,GATE_E_CANDIDATES,GATE_E_CHECKLIST,MODULES,PR_SPLIT_PLAN,README,SOURCES,STATE_MACHINE,TEST_CASES,WORK}.md
docs/gates/gate-e/adr/README.md
docs/gates/gate-f/{ARCHITECTURE,CONTRACTS,DB_SCHEMA,DECISIONS,GATE_F_CHECKLIST,MODULES,PR_SPLIT_PLAN,README,SOURCES,STATE_MACHINE,TEST_CASES,WORK}.md
docs/gates/gate-g/{ARCHITECTURE,CONTRACTS,GATE_G_CHECKLIST,MODULES,PR_SPLIT_PLAN,README,SOURCES,TEST_CASES,WORK}.md
docs/gates/gate-h/{API,DB_SCHEMA,GATEH_API_PLAN,GATEH_DB_PLAN,GATEH_FRONTEND_PLAN,GATEH_TEST_PLAN,GATEH_WORK_ORDER,GATE_H_CHECKLIST,PLAN_GATEH,PR_SPLIT_PLAN,README,ROADMAP,SOURCES,STATUS,TESTING,WORK,WORKLOG}.md
docs/gates/gate-i/{API,DB_SCHEMA,FREEZE_SUMMARY,GATEI_API_PLAN,GATEI_DB_PLAN,GATEI_FRONTEND_PLAN,GATEI_TEST_PLAN,GATEI_WORK_ORDER,PLAN_GATEI,README,ROADMAP,STATUS,TESTING,WORKLOG}.md
docs/gates/gate-j/{API,ARCHITECTURE,AUDIT_FIX_REPORT,DB_SCHEMA,DOC_CLEAN_REPORT,FREEZE_SUMMARY,FULL_SECURITY_AUDIT_REPORT,GATEJ_API_PLAN,GATEJ_DB_PLAN,GATEJ_FREEZE_ACCEPTANCE_TEMPLATE,GATEJ_FREEZE_DEPLOYMENT,GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT,GATEJ_FREEZE_FIX_SECOND_PASS_REPORT,GATEJ_FREEZE_UI_UX_SMOKE_REPORT,GATEJ_FRONTEND_PLAN,GATEJ_TEST_PLAN,GATEJ_WORK_ORDER,MODULES,PLAN_GATEJ,PRE_FREEZE_AUDIT_FIX_PLAN,PRE_FREEZE_AUDIT_REPORT,README,REPO_SIZE_AUDIT_REPORT,ROADMAP,RUNBOOK,STATUS,TESTING,WORKLOG}.md
```

### 4B. `docs/archive/**`（21）— ARCHIVE_CANDIDATE（已归档，**非可立即删除**）

主分类：ARCHIVE_CANDIDATE（already-archived）；authority level：NON_AUTHORITATIVE / 只读历史；lifecycle：ARCHIVED。
retention rationale：早期归档，不作当前依据，但保留历史可追溯；**已在 archive，不需移动，不可删除**。

```text
docs/archive/gate-inputs/{GATEF_INPUTS,GATEG_INPUTS,GATEH_PRE_1_TRADING_ANTI_CORRUPTION,GATEH_PRE_2_MARKETDATA_INSTRUMENTS,GATEH_PRE_3_FRONTEND_IA,GATE_CHECKLIST,WORK_TEMPLATE}.md
docs/archive/legacy-root-docs/{ARCHITECTURE,CONTRACTS,DB_SCHEMA,DECISIONS,EVOLUTION_RULES,GATE_A_CHECKLIST,MODULES,NUMERIC_POLICY,RECOVERY_RUNBOOK,ROADMAP,WORK}.md
docs/archive/rc1/{RC1_7_PACKAGE_MAPPING,RC1_CHECKLIST,REFACTOR_BATCH_RC1}.md
```

### 4C. `.agents/**`（13）— CURRENT_CONTROL（工具链 / skills 路由）

主分类：CURRENT_CONTROL（tag: 工具链）；authority level：CURRENT_AUTHORITY（agent 路由，非业务事实）；lifecycle：ACTIVE。
retention rationale：active skills 定义与路由，当前生效，不移动。

```text
.agents/{AGENTS.frontend-skill-routing,MERGE_MAP,README}.md
.agents/optional-skills/README.md
.agents/skills/{db-schema-migration-review,frontend-antd-page-builder,frontend-product-ui-design,frontend-quality-regression,java-backend-maintenance,java-backend-regression-tests,nq-dh-workflow-router,python-ops-tooling,ui-visual-system-polish}/SKILL.md
```

### 4D. `docs/templates/**`（4）— CURRENT_CONTROL（模板）

主分类：CURRENT_CONTROL（tag: 模板）；authority level：CURRENT_AUTHORITY（复用模板）；lifecycle：ACTIVE。
retention rationale：ADR / CHECKLIST / GATE_PLAN / WORK_ORDER 复用模板，保留。

```text
docs/templates/{ADR,CHECKLIST,GATE_PLAN,WORK_ORDER}.md
```

---

## 5. 覆盖性验证

| 段 | 文件数 | 累计 |
| --- | --- | --- |
| §1 docs/current 根 | 80（含 5 增量；基线 75） | — |
| §2 docs/current/frontend | 3 | — |
| §3 scattered | 10 | — |
| §4A docs/gates | 152 | — |
| §4B docs/archive | 21 | — |
| §4C .agents | 13 | — |
| §4D docs/templates | 4 | — |
| **基线小计（278）** | 75 + 3 + 10 + 152 + 21 + 13 + 4 = **278** | ✓ |
| **+ 治理增量** | 5（review + 4 G1 doc） | **283** = 工作树 G1 后总数 |

复算命令（PowerShell 见 `NQ_DOCS_G1_IMPLEMENTATION.md`；bash）：

```bash
git ls-files "*.md" "*.txt" | grep -vE '^(node_modules|target|build|dist|test-results)/' | wc -l
```

`recommended action` 全表只用 5 种允许取值；**无 `DELETE NOW`**；`ARCHIVE_CANDIDATE`（§4B）标注为 already-archived / RETAIN_IN_PLACE，非可立即删除。
