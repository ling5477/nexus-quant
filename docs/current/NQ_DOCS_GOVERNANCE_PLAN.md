# NQ Documentation Governance Inventory & Plan

任务：`NQ-DOCS-GOVERNANCE-INVENTORY-PLAN`

日期：2026-06-18

状态：**PASS / READY FOR REVIEW**

> 本轮为**只读盘点 + 规划**。**没有移动、删除、重命名任何文档**，未修改任何历史 freeze/review 文档的事实结论，未改 workflow / 代码 / 测试 / migration / 依赖。本文仅产出 documentation governance plan，不代表收口已完成。

---

## 0. 冻结事实（本轮口径，原样保留）

- **NQ GateK CI mainline = COMPLETED / ACCEPTED**。
- **Batch 1 = FROZEN / ACCEPTED**；**Batch 2 PostgreSQL/Flyway = FROZEN / ACCEPTED**；**Batch 3 no-outbound guard = FROZEN / ACCEPTED**；**Batch 4C artifact/log redaction = FROZEN / ACCEPTED**；**Batch 4F-A dependency-audit preflight = FROZEN / ACCEPTED**。
- **Batch 5A no-backend frontend E2E = FROZEN / ACCEPTED**。
- **Batch 5B-ENV runtime no-outbound = P1 SECURITY ENHANCEMENT / NOT STARTED**。
- **Batch 5B-SMOKE authenticated E2E = BLOCKED BY 5B-ENV**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**；Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- **LIVE = DISABLED；AI = NOT STARTED；DH runtime = NOT INTEGRATED；RealClient / real provider / real exchange adapter = NOT IMPLEMENTED**。

---

## 1. 结论

**NQ-DOCS-GOVERNANCE-INVENTORY-PLAN：PASS / READY FOR REVIEW**

- 文档治理盘点完成；目标结构、迁移映射、不可删除清单、实施批次均已规划，**未执行任何迁移**。
- 现有 `docs/DOC_RULES.md` 的三层意图（`current` = 当前事实 / `gates` = 冻结卷宗 / `archive` = 只读归档）正确，但**已出现可量化漂移**：GateJ 计划/过程文档在 `docs/current/` 与 `docs/gates/gate-j/` 重复（16 份 blob 完全一致）、`docs/README.md` 导航停留在 GateJ 口径且未含 GateK/CI、CI 22 份文档散落 `docs/current/` 根目录且无唯一 evidence index。
- 治理原则：**先建权威索引与迁移映射，再迁移/归档；历史链接先兼容/redirect，再目录收口；删除单独显式可审计批次，默认不删除；不通过删除证据实现"精简"。**

---

## 2. 全量文档清单（汇总 + 分类）

枚举范围：`git ls-files "*.md" "*.txt"`，排除 `node_modules/ target/ build/ dist/ test-results/`。

| 区域 | 数量 | 主分类 | 说明 |
| --- | --- | --- | --- |
| 仓库根 `README.md` / `AGENTS.md` / `CLAUDE.md` | 3 | CURRENT_CONTROL | 仓库级入口与代理规范 |
| `.github/pull_request_template.md` | 1 | CURRENT_CONTROL | PR 模板 |
| `.agents/**`（含 9 个 SKILL.md + 路由/MERGE_MAP/README） | 13 | CURRENT_CONTROL (tag: 工具链) | active skills 定义与路由，非业务事实 |
| `docs/README.md` / `docs/DOC_RULES.md` | 2 | CURRENT_CONTROL | 文档导航 + 规则（`docs/README.md` 有状态漂移，见 §4） |
| `docs/templates/**`（ADR/CHECKLIST/GATE_PLAN/WORK_ORDER） | 4 | CURRENT_CONTROL (tag: 模板) | 复用模板 |
| `docs/current/*.md`（根） | 74 | 混合（见 §2.1） | 当前控制 + 漂移热点 |
| `docs/current/frontend/**` | 15 | CURRENT_CONTROL / CANONICAL_BASELINE (tag: FRONTEND) | 设计系统/构建矩阵/设计令牌 |
| `docs/gates/**`（gate-a..j + README） | 152 | HISTORICAL_EVIDENCE | 冻结卷宗，只读 |
| `docs/archive/**`（gate-inputs / legacy-root-docs / rc1） | 22 | ARCHIVE_CANDIDATE（已在 archive） | 早期归档，不作当前依据 |
| `frontend/README.md` / `frontend/src/nq-design-system/README.md` | 2 | CURRENT_CONTROL (tag: FRONTEND) | 代码内 README |
| `research/py/README.md` / `research/py/datasets/README.md` | 2 | CURRENT_CONTROL (tag: RESEARCH) | 研究链 README |
| **合计（md/txt）** | **277** | | |

> 完整逐文件路径见 `git ls-files`；本文对**治理热点 `docs/current/` 根 74 份**逐项分类（§2.1），其余区域按目录类整体分类（`docs/gates/**` 全部 HISTORICAL_EVIDENCE；`docs/archive/**` 全部 ARCHIVE_CANDIDATE-already-archived）。

### 2.1 `docs/current/` 根 74 份分类矩阵

**A. CURRENT_CONTROL（当前唯一权威，保留在 current）**

| 文档 | 域 | 备注 |
| --- | --- | --- |
| `README.md` | 当前入口 | 权威入口（已含 GateK/CI 口径） |
| `STATUS.md` | 项目总状态 | 权威总状态 |
| `ROADMAP.md` | 路线图 | 权威路线 |
| `TESTING.md` | 测试与验证 | 权威验证记录 |
| `WORKLOG.md` | 工作日志 | 权威工作日志 |
| `ARCHITECTURE.md` | 架构 | 权威架构（GateK baseline review 承接） |
| `MODULES.md` | 模块边界 | 权威模块 |
| `API.md` | API | 权威 API（含 1 处 malformed 链接，见 §4） |
| `DB_SCHEMA.md` | 数据库现状 | 权威 schema（含 1 处 malformed 链接，见 §4） |
| `RUNBOOK.md` | 运行手册 | 权威 runbook（blob 与 gate-j 相同 = 自 GateJ 未更新，仍为当前手册，非删除项） |
| `CODEX_PROJECT_INSTRUCTIONS.md` | 代理项目规范 | 当前控制 |
| `FRONTEND_DESIGN_SYSTEM.md` | 前端设计系统 | CANONICAL_BASELINE (tag: FRONTEND) |

**B. CANONICAL_BASELINE（已冻结基线，当前权威依据，保留）**

| 文档 | 域 | 状态 |
| --- | --- | --- |
| `GATEK_PLAN.md` | GateK 规划 | 当前 Gate 规划基线 |
| `GATEK_ARCHITECTURE_BASELINE_REVIEW.md` | 架构基线 | accepted（P2 follow-up 由 ARCHITECTURE/MODULES 承接） |
| `DB_SCHEMA_GOVERNANCE_PLAN.md` / `DB_SCHEMA_GOVERNANCE_REVIEW.md` | 数据库治理 | DATABASE 基线 |
| `CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md` | 凭证治理 | CREDENTIAL 冻结基线（权威） |
| `CREDENTIAL_*`（其余 9 份：ACTIVE_*/ENABLE_/PERMISSION_PROBE_*/REVOCATION_*/ROTATE_） | 凭证治理 | CREDENTIAL 过程+基线（review/plan/design） |
| `NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md` | NQ-DH 合同 | INTEGRATION 冻结契约（权威，不可删） |
| `NQ_DH_INTEGRATION0_SECURITY_POLICY.md` | NQ-DH 安全 | INTEGRATION 安全边界（权威，不可删） |
| `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md` | NQ-DH 验收 | safety gate CLOSED/ACCEPTED |
| `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md` | NQ-DH 测试 | 合同测试设计 |
| `NQ_DH_CODEX_PLUGIN_WORKFLOW.md` / `NQ_DH_CODEX_TASK_TEMPLATES.md` / `NQ_DH_WORKFLOW_ROUTER_SKILL.md` | NQ-DH 工作流 | INTEGRATION 工作流参考 |
| `FRONTEND_DESIGN_SYSTEM.md`（见 A） | — | — |

**C. CI 域（22 份，CANONICAL_BASELINE + HISTORICAL_EVIDENCE 混合，CI 收口目标，tag: CI/SECURITY）** — 见 §6.1 CI 专项收口。

- 当前/基线类：`NQ_CI_BASELINE_PLAN.md`（CI 基线与状态，权威）、`NQ_CI_SECURITY_GUARD_PLAN.md`（安全边界总入口）。
- 冻结 review/proof（HISTORICAL_EVIDENCE，不可删）：`NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`、`NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md`、`NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md`、`NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md`、`NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md`、`NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md`、`NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md`、`NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md`。
- 实施/计划过程（HISTORICAL_EVIDENCE）：`NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md`、`NQ_CI_FRONTEND_E2E_PLAN.md`、`NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`、`NQ_CI_LOG_REDACTION_PROOF_PLAN.md`、`NQ_CI_NO_OUTBOUND_GUARD_PLAN.md`、`NQ_CI_DEPENDENCY_AUDIT_PLAN.md`、`NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`、`NQ_CI_POSTGRES_FLYWAY_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md`。
- backlog 入口：4F-B~4F-F 在 `NQ_CI_DEPENDENCY_AUDIT_PLAN.md` / `NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md`；5B-ENV / 5B-SMOKE 在 `NQ_CI_FRONTEND_E2E_PLAN.md` / `NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md`。

**D. DUPLICATE_OR_SUPERSEDED_CANDIDATE（GateJ 过程/计划文档，与 `docs/gates/gate-j/` blob 完全一致，16 份）**

```
AUDIT_FIX_REPORT.md                       DOC_CLEAN_REPORT.md
FULL_SECURITY_AUDIT_REPORT.md             GATEJ_API_PLAN.md
GATEJ_DB_PLAN.md                          GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md
GATEJ_FREEZE_DEPLOYMENT.md                GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md
GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md    GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md
GATEJ_FRONTEND_PLAN.md                    GATEJ_TEST_PLAN.md
GATEJ_WORK_ORDER.md                       PLAN_GATEJ.md
PRE_FREEZE_AUDIT_FIX_PLAN.md              PRE_FREEZE_AUDIT_REPORT.md
REPO_SIZE_AUDIT_REPORT.md（注：与 gate-j 同名 blob 一致）
```

> 这 16 份违反 `docs/README.md` §"已完成 Gate 的计划文档只保留在 `docs/gates/gate-x/`，不在 `docs/current/` 重复"。GateJ 已 COMPLETED 并冻结于 `gate-j/`，current 副本是 superseded duplicate。**它们不是删除目标，而是归档收口目标**（gate-j 已持有同 blob 权威副本；current 侧应在建立 redirect index 后移除重复，单独可审计批次执行）。

**E. 其余当前过程文档（HISTORICAL_EVIDENCE / CURRENT_CONTROL，保留）**

| 文档 | 分类 | 备注 |
| --- | --- | --- |
| `BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md` | HISTORICAL_EVIDENCE | GateI/J 期 API 计划 |
| `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md` | HISTORICAL_EVIDENCE (tag: TESTING/SECURITY) | 测试隔离 review |
| `GATEJ_*`（已在 D 列） | — | — |

---

## 3. 权威入口矩阵（单一真源）

| 域 | 唯一权威来源 | 漂移/重复来源（需收敛） |
| --- | --- | --- |
| 项目总状态 | `docs/current/STATUS.md` | `docs/README.md` 当前边界段（停留 GateJ）、`docs/gates/gate-j/STATUS.md`（快照，正常） |
| 当前工作入口 | `docs/current/README.md` | `docs/README.md` 导航（GateJ 计划列为 current 入口） |
| 路线图 | `docs/current/ROADMAP.md` | `docs/archive/legacy-root-docs/ROADMAP.md`、各 gate ROADMAP（快照） |
| 测试与验证 | `docs/current/TESTING.md` | gate-*/TESTING.md（快照，正常） |
| 工作日志 | `docs/current/WORKLOG.md` | gate-*/WORKLOG.md（快照，正常） |
| CI 基线与状态 | `docs/current/NQ_CI_BASELINE_PLAN.md` + `STATUS.md` CI 段 | 22 份 NQ_CI_* 分散，无唯一 evidence index（见 §6.1） |
| 安全边界 | `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md` + `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md` | redaction/no-outbound 散落多份（正常证据，需 index） |
| GateJ | `docs/gates/gate-j/`（冻结卷宗） | `docs/current/` 16 份 GateJ 重复（§2.1 D） |
| GateK | `docs/current/GATEK_PLAN.md` + `GATEK_ARCHITECTURE_BASELINE_REVIEW.md` | 无重大漂移 |
| 数据库治理 | `docs/current/DB_SCHEMA.md` + `DB_SCHEMA_GOVERNANCE_PLAN.md`/`_REVIEW.md` | gate-*/DB_SCHEMA.md（快照） |
| 凭证治理 | `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`（+9 份过程） | 无重复，但缺凭证治理 index |
| NQ-DH integration | `docs/current/NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md` + `_SECURITY_POLICY.md` + `_ACCEPTANCE_REPORT.md` | 无重复 |
| 前端工作流 | `docs/current/FRONTEND_DESIGN_SYSTEM.md` + `docs/current/frontend/**` | `frontend/README.md`（代码内，正常） |

---

## 4. 重复 / 状态漂移 / 命名漂移 / 链接风险

### 4.1 重复（DUPLICATE_OR_SUPERSEDED）
- **16 份 GateJ 过程/计划文档**在 `docs/current/` 与 `docs/gates/gate-j/` blob 完全一致（§2.1 D）。`DIVERGED` 的 9 份（`API/ARCHITECTURE/DB_SCHEMA/MODULES/README/ROADMAP/STATUS/TESTING/WORKLOG`）是 current 活文档 vs gate-j 快照，**属正常分层，非重复**。

### 4.2 状态漂移
- `docs/README.md`：导航把 `docs/current/PLAN_GATEJ.md`、`docs/current/GATEJ_WORK_ORDER.md` 列为"当前 GateJ 规划/工作单"；"当前边界"段仅到 `Next: GateK-PLAN`，**未含 GateK CI mainline COMPLETED、Batch 1~5A、5B-ENV/5B-SMOKE、4F backlog**。与 `docs/current/STATUS.md`（权威，已更新）不一致。
- `docs/DOC_RULES.md`：规则本身正确，但未声明 CI 文档分层归属（CI 证据该进 `current` 还是后续 `evidence/`），需在收口时补充。

### 4.3 命名漂移
- GateJ 命名混用：`PLAN_GATEJ.md` vs `GATEJ_API_PLAN.md` / `GATEJ_WORK_ORDER.md`（前缀顺序不一致）。GateH/I 同样混用（`PLAN_GATEH.md` vs `GATEH_API_PLAN.md`）。属历史命名，不在本轮重命名。
- CI 文档前缀统一为 `NQ_CI_*`（一致），但 batch 编号嵌在不同位置（`_BATCH_4C_`、`_2B_`、`_5A_`），index 时需规范排序键。

### 4.4 链接风险（6 处 broken markdown 链接）
| 文件 | 链接 | 性质 |
| --- | --- | --- |
| `docs/current/API.md` | `/docs/gates/gate-i/GATEI_API_PLAN.md` | malformed 前导 `/`（目标存在，链接从 FS 根解析失败）；current 活文档，可在 docs-only 修复批次内修正 |
| `docs/current/DB_SCHEMA.md` | `/docs/gates/gate-i/GATEI_DB_PLAN.md` | 同上 |
| `docs/gates/gate-h/API.md` | `./GATEI_API_PLAN.md` | 跨 gate 相对链接，gate-h 目录无此文件；**位于冻结快照，事实不可改**，用 redirect index 处理 |
| `docs/gates/gate-h/DB_SCHEMA.md` | `./GATEI_DB_PLAN.md` | 同上（冻结） |
| `docs/gates/gate-j/API.md` | `./GATEI_API_PLAN.md` | 同上（冻结） |
| `docs/gates/gate-j/DB_SCHEMA.md` | `./GATEI_DB_PLAN.md` | 同上（冻结） |

> 2 处 current malformed 链接可在后续 docs-only 批次修复；4 处在冻结 gate-h/gate-j 快照内，**不修改冻结事实**，通过 evidence index / redirect 说明。

---

## 5. 目标结构提案（不创建、不迁移）

提议在现有三层基础上**显式细分 evidence 层 + 索引**（保持 `current` / `gates` / `archive` 不破坏）：

```
docs/
  README.md                 # 总导航（补 GateK/CI 口径）
  DOC_RULES.md              # 规则（补 CI/evidence 分层声明）
  current/                  # 仅当前事实 + 当前 Gate 规划（GateK）
    README.md STATUS.md ROADMAP.md TESTING.md WORKLOG.md
    ARCHITECTURE.md MODULES.md API.md DB_SCHEMA.md RUNBOOK.md
    GATEK_*.md
    governance/             # 仍活跃的治理基线（credential / db-schema / NQ-DH integration0）
    frontend/               # 现有前端基线（保留）
  baselines/                # 当前生效的"已冻结但仍是当前依据"的基线索引（CI baseline / security guard / redaction）
    CI_BASELINE_INDEX.md    # 新增索引（指向 current CI 文档与 gates 证据）
  evidence/                 # 过程证据（plan/review/freeze/implementation 历史），按域分子目录
    ci/                     # 22 份 NQ_CI_* 的历史 plan/review/freeze 证据归位
    gatej/ ...              # 与 gates/gate-j 协调（避免二次重复）
  gates/                    # 冻结卷宗（不动）
  archive/                  # 只读归档（不动）
  templates/                # 模板（不动）
```

> `evidence/` 与 `gates/` 的边界：`gates/` 是**整 Gate 冻结快照**（不动）；`evidence/` 收纳**当前阶段（GateK CI）尚未成 Gate 快照的过程文档**。迁移时必须先在 `baselines/CI_BASELINE_INDEX.md` 与各 README 建立 redirect 映射，再物理移动，避免悬挂链接。

### 5.1 迁移映射（示例，本轮不执行）

| 源（current） | 目标 | 触发条件 |
| --- | --- | --- |
| 16 份 GateJ 重复（§2.1 D） | 移除 current 副本，权威保留 `docs/gates/gate-j/`（同 blob 已存在） | 先在 `docs/README.md` 加 GateJ→gate-j redirect，再单独 docs-only 批次移除 |
| 22 份 `NQ_CI_*` | `docs/evidence/ci/`（历史 plan/review/freeze）+ `docs/baselines/CI_BASELINE_INDEX.md`（索引指针） | 先建 index，再移动，旧路径在 index 列 redirect |
| current 活文档（STATUS/ROADMAP/...） | 留在 `current/`（不动） | — |
| credential/db/NQ-DH 治理基线 | `current/governance/`（可选，纯组织） | 仅在 index 完成后 |

---

## 6. CI 文档专项收口方案

### 6.1 CI evidence index（缺失，需新增——后续批次，本轮不建）

建议新增 `docs/baselines/CI_BASELINE_INDEX.md` 作为 CI 唯一证据索引，至少含：

| 区块 | 指向 |
| --- | --- |
| 当前 CI status | `docs/current/STATUS.md` CI 段（权威） |
| CI baseline | `docs/current/NQ_CI_BASELINE_PLAN.md` |
| CI security guard | `NQ_CI_SECURITY_GUARD_PLAN.md` + `..._BATCH_4C_FREEZE_REVIEW.md` + `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` + `NQ_CI_LOG_REDACTION_PROOF_*` |
| CI evidence index（batch→证据） | Batch1 baseline / Batch2(`POSTGRES_FLYWAY_*`) / Batch3(no-outbound) / Batch4C(redaction freeze+proof) / Batch4F-A(dependency preflight freeze) / Batch5A(`FRONTEND_E2E_5A_*`：plan→impl→first-run→freeze) |
| Batch 1~5A 历史 plan/review/freeze | §2.1 C 列出的 NQ_CI_* 文档 + run id（`27750279096` / `27750976632` 等已记录于 freeze review） |
| backlog 入口 | 4F-B~4F-F：`NQ_CI_DEPENDENCY_AUDIT_PLAN.md` / `..._BATCH_4F_PLAN_REVIEW.md`（OPTIONAL BACKLOG / NOT STARTED）；5B-ENV/5B-SMOKE：`NQ_CI_FRONTEND_E2E_PLAN.md` / `..._5A_FREEZE_REVIEW.md`（P1 / BLOCKED） |

索引建立前**不得移动**任何 NQ_CI_* 文档（否则 STATUS/WORKLOG 内的相对引用悬挂）。

---

## 7. 不可删除清单（绝对保留的冻结证据）

以下默认**永不删除**，迁移时只移动并在 index 留 redirect：

1. **所有 `docs/gates/**` 冻结卷宗**（gate-a..j，152 份），含 gate-j 内 GateJ 16 份权威副本、各 FREEZE_SUMMARY、ADR。
2. **CI Batch 1~5A 的 freeze/review/proof 证据**：`NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`、`NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md`、`NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md`、`NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md`、`NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md`、`NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md`、`NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md`、`NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md`、`NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md` 及对应 plan 文档。
3. **安全策略与 redaction 基线**：`NQ_CI_SECURITY_GUARD_PLAN.md`、`NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`、`NQ_CI_LOG_REDACTION_PROOF_PLAN.md`、`NQ_CI_NO_OUTBOUND_GUARD_PLAN.md`、`FULL_SECURITY_AUDIT_REPORT.md`、`NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md`。
4. **数据库与 credential governance 基线**：`DB_SCHEMA_GOVERNANCE_PLAN.md`/`_REVIEW.md`、全部 `CREDENTIAL_*`（10 份，含 freeze review）。
5. **NQ-DH 合同与安全边界**：`NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`、`_SECURITY_POLICY.md`、`_ACCEPTANCE_REPORT.md`、`_CONTRACT_TEST_PLAN.md`。
6. **有未关闭 P2/P3 residual 的记录**：`GATEK_ARCHITECTURE_BASELINE_REVIEW.md`（P2 follow-up）、各 5A first-run/freeze review（P3 runner Node20→24 警告、preview 绑定可观测性）、`NQ_CI_FRONTEND_E2E_PLAN.md`（P1 5B no-outbound runtime enforcement、P2 dev-server/preview proxy 等）。

> §2.1 D 的 16 份 current GateJ 重复**可在收口批次从 current 移除**（gate-j 已持同 blob 权威副本），但**不得删除 gate-j 内副本**；移除前须建 redirect。

---

## 8. 后续实施批次（每批输入/输出/可回滚；默认不删除）

| 批次 | 输入 | 输出 | 回滚 |
| --- | --- | --- | --- |
| **G1 索引先行（仅新增）** | 本计划 + 现有文档 | 新增 `docs/baselines/CI_BASELINE_INDEX.md`；更新 `docs/README.md`/`docs/DOC_RULES.md` 含 GateK/CI 口径与 evidence 分层声明 | 删除新增 index + revert 两个导航文件（纯新增，零风险） |
| **G2 漂移修复（docs-only，不移动）** | §4.2/§4.4 | 修复 `docs/README.md` 状态漂移；修复 `docs/current/{API,DB_SCHEMA}.md` 2 处 malformed 链接；冻结快照 4 处链接仅在 index 标注，不改快照 | revert 受影响文件 |
| **G3 GateJ 重复收敛** | §2.1 D 16 份 | 在 redirect 就绪后，从 `docs/current/` 移除 16 份重复（权威保留 gate-j）；更新 current 内引用 | `git mv`/恢复（保留 gate-j 副本，单文件可逆） |
| **G4 CI evidence 归位** | 22 份 NQ_CI_* | 按目标树移动历史 plan/review/freeze 到 `docs/evidence/ci/`，baseline 索引留 redirect；保留 current 内 baseline 指针 | 逐文件 `git mv` 回退 |
| **G5 目录收口** | G1~G4 完成 | 物理建立 `baselines/`/`evidence/` 分层，统一 README 导航 | 目录回退 + 链接还原 |
| **G6 删除（单独、显式、可审计；默认空）** | 仅在确认某文档已被 index 完整替代且 0 入链 | 经显式审计后删除（默认不执行；本轮不规划任何删除项） | git 历史可恢复 |

实施纪律：
- **先索引/映射，再移动**；**历史链接先 redirect 兼容，再目录收口**。
- 删除必须单独显式批次，逐项列出"已被何处替代 + 0 入链证明"，默认不删除；**不通过删除历史证据实现精简**。
- 文档治理**不与**业务代码、`.github/workflows/ci.yml`、LIVE、AI、DH runtime、real provider 混做。
- 每批结束更新 `STATUS.md`/`WORKLOG.md`/`TESTING.md`，并以 `git diff --check` + 禁止范围 `git diff` 为空收尾。

---

## 9. 本轮变更声明

- **本轮没有移动、删除或重命名任何文档。**
- 未修改任何历史 freeze/review 文档的事实结论。
- 未改 `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、测试、依赖。
- 仅新增 `docs/current/NQ_DOCS_GOVERNANCE_PLAN.md` 并更新 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md`，记录 "documentation governance plan ready"，未宣称收口完成。

## 10. 状态结论（原样）

- **NQ GateK CI mainline = COMPLETED / ACCEPTED**。
- **Batch 5A = FROZEN / ACCEPTED**。
- **Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**。
- **LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现**。
