# NQ Documentation Authority Index（权威入口索引）

任务：`NQ-DOCS-GOVERNANCE-G1-AUTHORITY-EVIDENCE-INDEX`

日期：2026-06-18

状态：**G1 = IMPLEMENTED / READY FOR REVIEW**

> 本索引为每个领域指定**唯一当前权威来源（Current Authority）**、辅证来源（Supporting）和历史证据来源（Historical Evidence）。
>
> 规则：
> - 每个领域**只有一个**“当前唯一权威”。
> - 历史证据可有多个，但**不替代** current control —— 仅供追溯，不得作为当前开发依据。
> - 本轮**不移动 / 删除 / 重命名**任何文件；本索引仅建立指针。
> - 历史证据中处于 `docs/gates/**` 的为冻结快照，**只读、不可改写**。

---

## 权威矩阵（14 领域）

| 领域 | 唯一当前权威（Current Authority） | 辅证（Supporting） | 历史证据（Historical Evidence，不替代 current） |
| --- | --- | --- | --- |
| 项目总状态 | `docs/current/STATUS.md` | `docs/current/README.md` | `docs/gates/gate-*/STATUS.md`（各 Gate 冻结快照） |
| 当前工作入口 | `docs/current/README.md` | `AGENTS.md`、`CLAUDE.md`、`docs/README.md`（导航，G2 修漂移） | `docs/gates/gate-*/README.md` |
| 路线图 | `docs/current/ROADMAP.md` | `docs/current/STATUS.md`（项目路线段） | `docs/gates/gate-*/ROADMAP.md`、`docs/archive/legacy-root-docs/ROADMAP.md` |
| 测试与验证 | `docs/current/TESTING.md` | `docs/current/STATUS.md`（验证基线段） | `docs/gates/gate-*/TESTING.md` |
| 工作日志 | `docs/current/WORKLOG.md` | — | `docs/gates/gate-*/WORKLOG.md`、`docs/gates/gate-*/WORK.md` |
| CI 当前状态 | `docs/current/STATUS.md`（CI 段） | `docs/current/NQ_CI_BASELINE_PLAN.md`、`docs/current/NQ_DOCS_EVIDENCE_INDEX.md`（CI evidence 入口） | 各 `NQ_CI_*_FREEZE_REVIEW.md` 内 immutable run 记录 |
| CI baseline | `docs/current/NQ_CI_BASELINE_PLAN.md` | `docs/current/NQ_CI_POSTGRES_FLYWAY_*_PLAN.md`（Batch 2 系列） | `docs/current/NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` 等 plan/freeze（未来 G4 归位 `docs/evidence/ci/`） |
| CI security guard | `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md` | `docs/current/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`、`docs/current/NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` | `NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`、`NQ_CI_LOG_REDACTION_PROOF_PLAN.md`、`NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md`、`docs/current/FULL_SECURITY_AUDIT_REPORT.md`（权威副本在 gate-j）、`NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md` |
| GateJ | `docs/gates/gate-j/`（冻结卷宗，28 份） | `docs/gates/gate-j/FREEZE_SUMMARY.md`、`docs/gates/gate-j/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md` | `docs/current/` 内 17 份 GateJ blob-identical 副本（**NON_AUTHORITATIVE / FUTURE_SUPERSEDE_CANDIDATE / G3**，见 `NQ_DOCS_MIGRATION_MAP.md` §1E） |
| GateK | `docs/current/GATEK_PLAN.md` | `docs/current/GATEK_ARCHITECTURE_BASELINE_REVIEW.md`、`docs/current/ARCHITECTURE.md`、`docs/current/MODULES.md`（P2 follow-up 承接） | — |
| 数据库治理 | `docs/current/DB_SCHEMA.md` | `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`、`docs/current/DB_SCHEMA_GOVERNANCE_REVIEW.md` | `docs/gates/gate-*/DB_SCHEMA.md`（快照）、`docs/archive/legacy-root-docs/DB_SCHEMA.md` |
| 凭证治理 | `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md` | `CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md`、`CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md` | 其余 `CREDENTIAL_*`（7 份过程 review/design：active/enable/rotate/probe-design/probe-code-api/revocation-review/uniqueness-review） |
| NQ-DH integration | `docs/current/NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md` | `NQ_DH_INTEGRATION0_SECURITY_POLICY.md`、`NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md` | `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`、DH 仓 `docs/current/NQ_DH_INTEGRATION_SECURITY_AUDIT_REPORT.md`（外部，只读引用） |
| 前端工作流 | `docs/current/FRONTEND_DESIGN_SYSTEM.md` | `docs/current/frontend/NQ_DESIGN_TOKENS_V2.md`、`docs/current/frontend/NQ_FRONTEND_BUILD_MATRIX.md` | `frontend/README.md`、`frontend/src/nq-design-system/README.md`、`docs/current/frontend/ref/nq-design-system/README.md` |

---

## 单一权威自检

- 上表每个领域的 “唯一当前权威” 列**均为单一文件**，无并列。
- “CI 当前状态” 与 “CI baseline” 是**不同领域**：前者权威为 `STATUS.md` CI 段（运行状态事实），后者权威为 `NQ_CI_BASELINE_PLAN.md`（CI 结构基线），不构成同领域多权威。
- GateJ 的当前权威是**冻结卷宗目录** `docs/gates/gate-j/`；`docs/current/` 内 17 份同名 blob-identical 文件**不是权威**，仅为 FUTURE_SUPERSEDE_CANDIDATE，G3 redirect 后从 current 移除（权威副本永久保留在 gate-j）。
- 历史证据列内的 `docs/gates/**` / `docs/archive/**` 文件**只读、不可改写、不替代** current control。

## 与 governance 文档的关系

- 工作流/路由权威：`docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md`（插件路由）、`NQ_DH_WORKFLOW_ROUTER_SKILL.md`（router skill 源规格）、`NQ_DH_CODEX_TASK_TEMPLATES.md`（模板）、`CODEX_PROJECT_INSTRUCTIONS.md`。
- 证据索引：见 `docs/current/NQ_DOCS_EVIDENCE_INDEX.md`。
- 逐文件迁移映射：见 `docs/current/NQ_DOCS_MIGRATION_MAP.md`。
- 本索引不改变任何冻结事实；G2 才处理 `docs/README.md` 的状态/导航漂移；G3~G6 未开始。
