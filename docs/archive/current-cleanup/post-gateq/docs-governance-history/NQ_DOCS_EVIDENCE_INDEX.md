# NQ Documentation Evidence Index（历史证据索引）

任务：`NQ-DOCS-GOVERNANCE-G1-AUTHORITY-EVIDENCE-INDEX`

日期：2026-06-18

状态：**G1 = IMPLEMENTED / READY FOR REVIEW**

> 本索引**只建立入口与链接，不复制任何冻结文档内容**，不改写冻结事实，不改冻结快照链接。
> immutable run id / commit / blob 等细节以各 freeze review 原文为准；本索引仅给指针。
> 本轮**不移动 / 删除 / 重命名**任何文件。

---

## 1. GateJ freeze 证据

| 入口 | 位置 |
| --- | --- |
| GateJ 冻结卷宗（权威） | `docs/gates/gate-j/`（28 份，只读） |
| GateJ freeze 总结 | `docs/gates/gate-j/FREEZE_SUMMARY.md` |
| GateJ-FREEZE 最终验收（30m/1h/24h/7d passed） | `docs/gates/gate-j/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md` |
| GateJ-FREEZE UI/UX smoke（Functional PASS / UI-UX FAIL，post-freeze remediation） | `docs/gates/gate-j/GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md` |
| pre-freeze 审计 + 修复 | `docs/gates/gate-j/PRE_FREEZE_AUDIT_REPORT.md`、`PRE_FREEZE_AUDIT_FIX_PLAN.md`、`AUDIT_FIX_REPORT.md`、`FULL_SECURITY_AUDIT_REPORT.md` |

> `docs/current/` 内 17 份 GateJ 同名副本为 blob-identical NON_AUTHORITATIVE 重复（`NQ_DOCS_MIGRATION_MAP.md` §1E，FUTURE_SUPERSEDE_CANDIDATE / G3）；权威以 `docs/gates/gate-j/` 为准。

## 2. GateK CI mainline 证据

| 入口 | 位置 | 状态 |
| --- | --- | --- |
| CI baseline 总文档 | `docs/current/NQ_CI_BASELINE_PLAN.md` | mainline **COMPLETED / ACCEPTED** |
| CI 当前状态总览 | `docs/current/STATUS.md`（CI 段） | — |
| GateK 架构基线 review | `docs/current/GATEK_ARCHITECTURE_BASELINE_REVIEW.md` | ACCEPTED WITH P2 FOLLOW-UP |
| GateK 规划 | `docs/current/GATEK_PLAN.md` | planning-only，implementation not started |

## 3. CI Batch 1~5A plan / review / first-run / freeze 证据

| Batch | plan / review / freeze 入口 | 状态 |
| --- | --- | --- |
| Batch 1 baseline | `NQ_CI_BASELINE_PLAN.md` | first green confirmed |
| Batch 2A~2E PostgreSQL/Flyway | `NQ_CI_POSTGRES_FLYWAY_PLAN.md`、`_2B_PLAN.md`、`_2C_PLAN.md`、`_2D_PLAN.md`、`_2E_PLAN.md` | **FROZEN / ACCEPTED** |
| Batch 3 no-outbound guard | `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` | **FROZEN / ACCEPTED** |
| Batch 4C redaction（见 §4） | — | **FROZEN / ACCEPTED** |
| Batch 4F-A preflight（见 §5） | — | **FROZEN / ACCEPTED** |
| Batch 5 plan + plan review | `NQ_CI_FRONTEND_E2E_PLAN.md`、`NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md` | plan ACCEPTED AS BASELINE |
| Batch 5A impl / first-run / freeze | `NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md`、`NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md`、`NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md` | **FROZEN / ACCEPTED** |

## 4. CI Batch 4C artifact / log redaction 证据

| 入口 | 位置 | 状态 |
| --- | --- | --- |
| 4C overall freeze review | `docs/current/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md` | overall **FROZEN / ACCEPTED** |
| 4C artifact/log redaction plan | `docs/current/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md` | 4C-B pre-upload artifact gate FROZEN |
| 4C-C log redaction proof plan | `docs/current/NQ_CI_LOG_REDACTION_PROOF_PLAN.md` | FROZEN / ACCEPTED |
| 4C-C log redaction proof freeze review | `docs/current/NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md` | FROZEN / ACCEPTED（14 类 pattern 真实值命中 = 0） |
| security guard 总入口 | `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md` | — |

> Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。

## 5. Batch 4F-A dependency-audit preflight 证据

| 入口 | 位置 | 状态 |
| --- | --- | --- |
| 4F dependency audit plan | `docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md` | plan ACCEPTED |
| 4F plan review（历史） | `docs/current/NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md` | ACCEPTED AS BASELINE |
| 4F-A preflight | `docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md` | — |
| 4F-A preflight review | `docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md` | — |
| 4F-A freeze review（权威） | `docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md` | **FROZEN / ACCEPTED**；Python local audit **NOT READY** |

## 6. Backlog / residual 入口（**未完成，不得写成 completed**）

| 项 | 状态 | 入口 |
| --- | --- | --- |
| Batch 5B-ENV runtime no-outbound | **P1 SECURITY ENHANCEMENT / NOT STARTED** | `NQ_CI_FRONTEND_E2E_PLAN.md`、`NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md` |
| Batch 5B-SMOKE authenticated E2E | **BLOCKED BY 5B-ENV** | `NQ_CI_FRONTEND_E2E_PLAN.md` |
| Batch 4F-B ~ 4F-F | **OPTIONAL BACKLOG / NOT STARTED** | `NQ_CI_DEPENDENCY_AUDIT_PLAN.md`、`NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md` |
| Static workflow assertion | **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED** | `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md` |
| residual（P2/P3） | 见各 review | `GATEK_ARCHITECTURE_BASELINE_REVIEW.md`（P2）、5A first-run/freeze review（P3 runner Node20→24、preview 观测性）、`NQ_CI_FRONTEND_E2E_PLAN.md`（P1 runtime no-outbound、P2 dev-server/preview proxy） |

## 7. 数据库治理基线

| 入口 | 位置 |
| --- | --- |
| 当前 schema 事实 | `docs/current/DB_SCHEMA.md` |
| schema 治理 plan / review | `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md`、`docs/current/DB_SCHEMA_GOVERNANCE_REVIEW.md` |
| 历史 schema 快照 | `docs/gates/gate-*/DB_SCHEMA.md`（只读） |

## 8. credential governance 基线

| 入口 | 位置 | 状态 |
| --- | --- | --- |
| 冻结复核（权威） | `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md` | Batch 5-G FROZEN |
| permission probe 冻结结论 | `docs/current/CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md` | guarded baseline，no real adapter |
| revocation / rotate / enable / active material 过程 | `CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`、`_REVOCATION_GOVERNANCE_REVIEW.md`、`_ROTATE_GOVERNANCE_REVIEW.md`、`_ENABLE_GOVERNANCE_REVIEW.md`、`_ACTIVE_MATERIAL_SELECTION_REVIEW.md`、`_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md` | — |
| probe design / code-api-test design | `CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`、`_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md` | 历史设计证据 |

## 9. NQ-DH Integration-0 合同与安全边界

| 入口 | 位置 | 状态 |
| --- | --- | --- |
| 契约冻结主文档（权威） | `docs/current/NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md` | contract-only，未实现集成 |
| 安全策略 | `docs/current/NQ_DH_INTEGRATION0_SECURITY_POLICY.md` | — |
| safety gate 验收 | `docs/current/NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md` | **CLOSED / ACCEPTED** |
| contract test 设计 | `docs/current/NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md` | INT0-T01..T15 |

> DH runtime = NOT INTEGRATED；Integration-1 前置 = DH P1-4 residual（rate limit / memory cap / replay nonce persistence）。

---

## 边界声明

- 本索引仅建立链接与入口，**未复制冻结内容、未改写冻结事实、未改冻结快照链接**。
- gate-h / gate-j 冻结快照内 4 处历史 `./GATEI_*` 失效链接**不在本轮修改**；G2/redirect index 仅作说明，不改快照（见 `NQ_DOCS_MIGRATION_MAP.md` §4A）。
- CI evidence 的物理归位（`docs/evidence/ci/` + `docs/baselines/CI_BASELINE_INDEX.md`）属 **G4**，本轮未执行。
- backlog 项一律未完成，未写成 completed。
