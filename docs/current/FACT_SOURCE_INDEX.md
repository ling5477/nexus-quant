# Current Fact Source Index

本索引定义 current authority 分层，不复制动态阶段值。

## 1. Current Authority

1. [STATUS.md](STATUS.md)：唯一 machine current authority。
2. Git、代码、测试与 CI：能力与验证事实。
3. [ROADMAP.md](ROADMAP.md)：下一允许动作，不覆盖 STATUS。
4. [GOVERNANCE_WORKFLOW.md](GOVERNANCE_WORKFLOW.md)：通用 lifecycle/checker 说明，不决定 current Gate。

冲突时输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`；历史材料不得覆盖 current authority。

## 2. Active Current Document Set

以下文件可以表达current facts，但只有 `STATUS.md` 决定current stage：

- root `README.md`、本目录 `README.md`、`STATUS.md`、`ROADMAP.md`、`FACT_SOURCE_INDEX.md`；
- `API.md`、`DB_SCHEMA.md`、`ARCHITECTURE.md`、`MODULES.md`、`RUNBOOK.md`、`GOVERNANCE_WORKFLOW.md`；
- `TESTING.md`、`WORKLOG.md` 仅作为append-only evidence ledger。

`GATEV_PLAN.md`、`GATEW_PLAN.md`、`NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md` 与 `docs/current/evidence/**` 均为 `HISTORICAL / NON_AUTHORITATIVE / RETAIN_IN_PLACE`。保留原路径是为了不改写append-only历史链接；其中阶段、状态和下一动作均为当时快照，不参与current authority、runtime routing、Skill routing或Phase4/5 disposition。

## 3. Capability Owners

- [API.md](API.md)：已实现 HTTP API 与边界。
- [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 schema/migration。
- [ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md)：架构与模块职责。
- [RUNBOOK.md](RUNBOOK.md)：当前运行手册。
- [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md)：前端设计系统参考。
- [ROADMAP.md](ROADMAP.md)：Phase5 inventory finding seed、capability disposition与下一允许workstream；finding登记不表示implementation或acceptance。

## 4. Evidence Ledgers

- [TESTING.md](TESTING.md)：append-only 验证证据。
- [WORKLOG.md](WORKLOG.md)：append-only 工作证据。

旧条目只表示历史执行，不参与 current stage 判定。

- [F009 post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F009_POST_CI_AUTHORITY_ACCEPTANCE.md)：F009 immutable technical pair、失败delivery/remediation链、当时的Phase5 finding reconciliation与Phase6延期依据（历史接受快照）。分类为`ACCEPTED_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`；STATUS仍为唯一current authority，ROADMAP拥有remaining actions。

- [F001 post-remote acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F001_POST_REMOTE_AUTHORITY_ACCEPTANCE.md)：F001正式closure、独立remote effective-rule readback、Phase5 closure与Phase6 readiness推导；引用上一remote mutation证据，分类为`ACCEPTED_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`，不替代F009 immutable technical pair。

- [Phase6 L4 plan post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE6_L4_PLAN_POST_CI_AUTHORITY_TRANSITION_TO_C1.md)：接受固定plan/reproduction pair=`d79408228ce31c97802afbb674eb2e3d0a2e7bfd / 34071672665`，保留failed delivery=`378de657ac33b9f9fd666288d489181ac0147b2e / 34038345304`；分类为`ACCEPTED_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`。原[MD计划](../audit/evidence/GATEAUDIT_PHASE6_L4_FAILURE_MATRIX_PLAN.md)与[JSON矩阵](../audit/evidence/GATEAUDIT_PHASE6_L4_FAILURE_MATRIX_PLAN.json)是immutable technical delivery snapshot；其当时next-task文字不替代当前STATUS/ROADMAP。当时canonical P1=2；当前P1数量、C1/C2边界与DAG由STATUS/ROADMAP表达，当时L4资格未接受；后续L4/L5/L6接受见下列最终索引。

- [C1 post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE6_L4_C1_POST_CI_AUTHORITY_TRANSITION_TO_C2.md)：保留implementation、独立review、failed delivery和accepted exact-head CI，正式关闭P1-2并打开C2/P1-3入口；分类为`ACCEPTED_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`。该历史时点canonical blocking P1=1；后续C2与L4/L5/L6已接受，当前数量由STATUS表达，技术身份与authority synchronization commit分层；STATUS仍是唯一machine authority。

- [Phase6 L4/L5/L6 final acceptance](../audit/evidence/GATEAUDIT_PHASE6_L5_L6_FINAL_ACCEPTANCE.md)：mandatory矩阵、四组technical/CI身份及本地raw hash/index；`ACCEPTED_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`。该 evidence 原时点为 Phase6 ACCEPTED/COMPLETE、Phase7 NOT_STARTED；当前 Phase7 已进入 `IN_PROGRESS / NOT_FROZEN`，STATUS 是唯一 current authority，ROADMAP 解释当前下一动作。历史证据中的 NOT_STARTED/NOT_ACCEPTED 仅代表原时点。

- [前端本地化与错误目录](../error-catalog/README.md)：本批实现范围、兼容错误身份和本地化契约；[验证记录](../error-catalog/VERIFICATION.md)绑定 technical acceptance pair=`1b4c87129f2a79e13e379aa56501042ddd5bd42f / 35684433673`，9/9 SUCCESS；不是第二份运行时 catalog 或 current authority。

- [NQ Console Visual System V3 acceptance](../audit/evidence/GATEAUDIT_FRONTEND_CONSOLE_VISUAL_SYSTEM_V3_ACCEPTANCE.md)：绑定 technical acceptance pair=`07453f8b16e798bd580070a3727aa9eb7e88a193 / 35720426791`，9/9 SUCCESS，并索引[前端视觉系统](FRONTEND_DESIGN_SYSTEM.md)；分类为 `ACCEPTED_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`。AntD deprecation 与 JS bundle-size warning 保持 observation；STATUS 仍是唯一 current machine authority。

- [Phase7 final baseline plan](../audit/evidence/GATEAUDIT_PHASE7_FINAL_BASELINE_PLAN.md)：`ACCEPTED_PLAN / NON_RUNTIME_AUTHORITY`，定义 final acceptance matrix、residual disposition、archive/freeze/tag sequence，并把 UI V3 作为 accepted final-baseline input；其原始接受时点为 `PHASE7_NOT_STARTED`，当前 Phase7 已进入 `IN_PROGRESS / NOT_FROZEN`，但仍未冻结。

- [Phase7-A final baseline inventory](../audit/evidence/GATEAUDIT_PHASE7_A_FINAL_BASELINE_INVENTORY.md)：固定 source HEAD/tree，汇总 21 行 acceptance owner、17 组 immutable technical/CI binding、candidate ancestry、17 行 residual、historical projection Phase7-B handoff、release-branch promotion requirement 与 B～F historical taxonomy preflight；分类为 `PHASE7_INVENTORY_EVIDENCE / NON_RUNTIME_AUTHORITY`。Phase7-A immutable pair 仍为 `baa01f0f0034bb46a24f9fe8f62acf60bb56e3f6 / 35729125034`；后续 taxonomy normalization pair=`8868edb248b614e360377317c9c17e8f1d7d8404 / 35734048380 / 9 of 9 SUCCESS`，不替代 Phase7-A acceptance。它不执行 projection repair、archive/freeze/tag，也不覆盖 STATUS machine authority。

- [Phase7-B historical projection baseline verification](../audit/evidence/GATEAUDIT_PHASE7_B_HISTORICAL_PROJECTION_BASELINE_VERIFICATION.md)：固定 immutable V46 source artifact hashes、PG16 offline restore identity、source-only Decimal oracle、Position/latest Snapshot exact comparison、无效 v1 attempt、独立 `REVIEW_ONLY` 与 17 行 residual closure；分类为 `ACCEPTED_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`。Immutable pair=`fea0f1ce228ac7079a7873393daba2d1294fec58 / 35748394188 / 9 of 9 SUCCESS`；结论=`CLOSED_BY_BASELINE_VERIFICATION / REPAIR_NOT_REQUIRED / MANDATORY_AFTER_0 / OTHER_RESIDUAL_RECLASSIFICATIONS_0`。raw dump/oracle output 不入 Git，本文不包含原始生产 row/ID 或 credential；STATUS 仍是唯一 current authority。

- [Phase7-C freeze readiness review](../audit/evidence/GATEAUDIT_FREEZE_READINESS_REVIEW.md)：绑定 reviewed candidate=`e0fa7f7ff4fa6d0201adf5147056ec0e8a25abdc / tree 6fb0db732ea54775fdcc9e4823e36afc0146126a`、START/END fingerprint、13-file current convergence、Phase7-A/B immutable reuse、8 mandatory + 5 conditional archive role matrix、live release/tag/dev facts与 clean-candidate validators；分类为 `ACCEPTED_REVIEW_EVIDENCE / NON_RUNTIME_AUTHORITY`。Immutable pair=`82f1afc43664a327eea2fcfd7046bcef099e621b / 35761394744 / 9 of 9 SUCCESS`；结论=`PASS / GATEAUDIT_FREEZE_READY / PRETAG_ARCHIVE_AUTHORIZED / P0_0 / P1_0`，只授权 Phase7-D，不授权 promotion/tag/freeze/release。STATUS 仍是唯一 current authority。

- [GateAUDIT strict canonical archive](../gates/gate-audit/README.md)：13 个独立 substantive role（8 mandatory + 5 conditional）汇总 final acceptance matrix、Phase7 governance closure、完整 residual、边界与各领域证据；分类为 `PRETAG_CANONICAL_ARCHIVE / NON_RUNTIME_AUTHORITY`。Phase7-D immutable pair=`4800ab1d9407eeb527182328263c0bae9e6c3087 / 35803472376 / 9 of 9 SUCCESS`，pre-tag archive errors=`0`。Archive 内 `TO_BE_BOUND_BY_GIT / PENDING_DELIVERY / TAG_PENDING / NOT_CREATED` 保留非自引用生成时语义；当前接受身份由 STATUS 绑定。GateAUDIT 仍为 `IN_PROGRESS / NOT_FROZEN`，`nq-gateaudit-freeze` 未创建，Phase7-E/promotion/tag/release 未执行。

- [Original Scope Traceability Reconciliation](../audit/evidence/GATEAUDIT_ORIGINAL_SCOPE_TRACEABILITY_RECONCILIATION.md)：39 项分类、未分类 0；L07 logging protection 实现缺口与 S10 SQL ownership/duplication 签收缺口两项仍待核销。分类为 `CURRENT_AUDIT_EVIDENCE / NON_RUNTIME_AUTHORITY`。STATUS 路由下一独立 logging implementation，Phase7-E 资格尚未确认。
- [Logging sensitive-data negative proof](../audit/evidence/GATEAUDIT_LOGGING_SENSITIVE_DATA_NEGATIVE_PROOF_AUDIT.md)：在 `prod` profile 的 Spring Boot/Logback console encoder 最终 bytes 中，13/13 synthetic canary 可见，包括 Throwable nested cause；安全身份仍保留。分类为 `BLOCKED_AUDIT_EVIDENCE / NON_RUNTIME_AUTHORITY`；不证明真实凭证泄露，不替代后续 implementation/CI acceptance。

## 5. Agent / Governance

- 根 `AGENTS.md`：仓库级入口。
- `.agents/README.md` 与 `.agents/skills/**`：唯一 active Skill 集合。
- `scripts/docs/agent-workflow-policy.json`：machine routing policy。
- `scripts/docs/governance-workflow-contract.json`：machine lifecycle/authority/evidence/release contract。
- [Repository Audit Bootstrap Charter](../audit/AUDIT_BOOTSTRAP_CHARTER.md)：由 machine policy 声明的全仓审计中立入口。
- [GateAUDIT-0C R2 independent review acceptance evidence](../audit/evidence/GATEAUDIT_0C_R2_INDEPENDENT_REVIEW_ACCEPTANCE.md)：GateAUDIT-0C execution/review evidence，分类为 `HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`，不属于 current machine authority。
- [GateAUDIT-0C R3 Skill capability completion evidence](../audit/evidence/GATEAUDIT_0C_R3_SKILL_CAPABILITY_COMPLETION.md)：GateAUDIT-0C implementation/capability evidence，分类为 `HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`，不是 independent review，不属于 current machine authority。
- [GateAUDIT-0C R3 final independent review evidence](../audit/evidence/GATEAUDIT_0C_R3_FINAL_INDEPENDENT_REVIEW_ACCEPTANCE.md)：GateAUDIT-0C R3 final independent review evidence，分类为 `HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`，不属于 current machine authority。
- [GateAUDIT-0C CI-failure remediation independent review evidence](../audit/evidence/GATEAUDIT_0C_R3_CI_FAILURE_REMEDIATION_REVIEW_ACCEPTANCE.md)：GateAUDIT-0C CI-failure remediation independent review evidence，分类为 `HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`，不属于 current machine authority。
- [GateAUDIT-0C R3 doc-link Linux remediation review evidence](../audit/evidence/GATEAUDIT_0C_R3_DOC_LINK_LINUX_REMEDIATION_REVIEW_ACCEPTANCE.md)：GateAUDIT-0C R3 doc-link Linux remediation review evidence，分类为 `HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`，不属于 current machine authority。

`.agents/history/**`、`.agents.audit-subject/**`、旧 Skill/checker 自我声明均为 non-authoritative audit/history input。

## 6. Frozen Evidence

- GateY：[../gates/gate-y/README.md](../gates/gate-y/README.md)。
- 其他 frozen Gate：`docs/gates/gate-*`。
- 通用历史：`docs/archive/**` 与 Gate 内 `source/**`。

这些内容只读追溯，不覆盖 `STATUS.md`，不得为 current 收口改写历史正文。
