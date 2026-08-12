# NQ-GATEY-2-LIVE-SESSION-FACT-MODEL-IMPLEMENTATION — attempt-01

## Task classification

- ownership：NQ-only。
- type：`BACKEND_IMPLEMENTATION + FLYWAY_MIGRATION + LIVE_CONTROL_PLANE_FACT_MODEL + STATE_MACHINE + JDBC_REPOSITORY + POSTGRESQL_TEST + ARCHITECTURE_HYGIENE`。
- level：L 级高风险实现。
- result：`PASS / GATEY_2_FACT_MODEL_IMPLEMENTED / MIGRATION_CREATED / POSTGRESQL_GREEN / ARCHITECTURE_HYGIENE_CHECKED / PENDING_INDEPENDENT_REVIEW / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED`。

## Starting baseline

- branch=`dev`；起始 worktree clean、staged empty。
- `HEAD == origin/dev == 2217d28ff184d3ca38a1b76bea194fa462586599`。
- `NQ CI Baseline` run `31583487794`：`completed / success`，10 jobs，bad=0，head SHA 精确匹配。
- authority before：GateY-1=`ACCEPTED|CI_GREEN`；GateY-2=`NOT_STARTED / NONE / NOT_RUN`；next action=`NQ-GATEY-2-LIVE-SESSION-FACT-MODEL-IMPLEMENTATION`；LIVE=`DISABLED`，kill switch=`ENGAGED`。
- 扫描 migration 后最高为 V38，因此选择 `V39__gate_y2_live_session_fact_model.sql`；未修改 V1～V38。

## Scope and files

- production code：`backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/**`、`backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/**`。
- migration：`backend/nq-infra/src/main/resources/db/migration/V39__gate_y2_live_session_fact_model.sql`。
- tests：`backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/**`、`backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/LiveSessionFactModelMigrationContractTest.java`、`backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/LiveSessionFactModelPostgresIntegrationTest.java`。
- docs：本 evidence、GateY evidence index、STATUS、ROADMAP、DB_SCHEMA、TESTING、WORKLOG。
- excluded：frontend、research Python、deployment、CI workflow、scripts、HTTP Controller、exchange adapter、worker/provider、credential material、历史 migration。

## Architecture ownership decision

- `LiveSession` owner：`nq-core` 的 `livecontrol` bounded context；只拥有 LIVE control-plane aggregate，不拥有 order/trade/position/ledger/risk decision。
- `OperatorApproval` owner：同一 `livecontrol` context 的 immutable human decision fact；不等于 venue permission、kill-switch release 或 LIVE authorization。
- `RiskLimitSet` owner：同一 context 的 immutable/versioned rule definition；运行期 risk decision 继续由既有 `nq-risk` / `risk_events` owner 承担。
- Repository port ownership：`nq-core` domain port。
- JDBC ownership：`nq-infra` adapter；JDBC 只持久化/锁定事实，不决定状态迁移。
- transaction orchestration：`LiveSessionControlService` application service；所有事务均为短 DB transaction，无外部 HTTP。
- new module dependencies：无；未增加 Maven module 或 dependency direction。
- ArchUnit impact：既有规则覆盖，无需新增 guardrail；`ModuleBoundaryArchTest` 6/6、`PackageBoundaryArchTest` 6/6。

## Migration implementation

- six tables：`risk_limit_sets`、`live_sessions`、`live_session_events`、`operator_approvals`、`execution_intents`、`execution_receipts`。
- FK：真实 ID 类型与现有 schema 一致；audited FK 全为 `ON UPDATE RESTRICT / ON DELETE RESTRICT`。
- constraints/indexes：状态/金额/窗口/precision/digest CHECK；单 non-terminal session partial unique；session event sequence、intent attempt、intent business/idempotency/client-order 约束和查询索引按 GateY-1 合同落地。
- triggers：RiskLimitSet immutable；approval/session event/receipt append-only；session/intent mutable aggregate 受 state/version/immutable-field trigger 约束。
- comments：六表及字段均有中文业务语义和敏感数据禁入说明。
- timeout contract：保留 `SET LOCAL lock_timeout='5s'`、`SET LOCAL statement_timeout='60s'`。
- migration effect：无 historical backfill、无现有大表 rewrite；本地 PASS 不表示 production deployment authorized。

## Java and JDBC implementation

- `LiveSession`：initial state、optimistic version、approval scope hash、execution window、terminal state、scope mutation/approval invalidation、`nextEventSequence`。
- `OperatorApproval`：exact scope/release/risk binding、expiry、`LIVE_APPROVER` role、creator≠approver、stale version/scope fail-closed、并发审批单一合法结果。
- authentication/RBAC：session creator 与 approval actor 使用独立 `AuthenticatedLiveControlActor`，命令不能自报 identity/role；service 在同一短事务通过 core authorization port 实时锁定并校验 enabled `users + user_roles + roles`。创建要求 actor=`created_by` 且当前具备 `OPERATOR`；审批要求当前具备 `LIVE_APPROVER`，审计 role snapshot 只在校验后生成。V39 不 seed 角色，未配置/撤销/禁用均 fail-closed，且 migration 保持既有 `roles/user_roles` count/hash 不变。
- GateY-2 transition boundary：application service 只运行化 risk set/session 创建与 approval/rejection；通用 START/ACTIVATE/RESUME 等 application transition 入口未暴露，避免在 GateY-3/4 runtime risk、credential、kill-switch orchestration 实现前绕过 hard gates。完整状态机在 domain 中仅作为合同 validator。
- `RiskLimitSet`：全程 `BigDecimal`；`NUMERIC(38,8)` 语义；`RoundingMode.UNNECESSARY`；独立 deterministic `risk-limit-set.v1` canonical encoder。golden digest=`75ef817c87d74807998a38c55127dfb7a8a5e396e4dab02f1ccdbb3ff0719137`。
- approval scope：独立 deterministic `approval-scope.v1` encoder，不依赖通用 JSON serializer 字段顺序。
- event ordering：锁定 session row → 读取 `next_event_sequence` → 原子 increment → append event → 同一短事务提交；未使用 `MAX(sequence_no)+1`。
- create reference guard：锁定并核对 account owner、`exchange_code='OKX'`、`trade_env='LIVE'`、exact credential-account reference、release identity quartet/digest/revision、risk ID/digest；SQL 只读取常量/非敏感事实，不读取 credential material，也不宣称 TRADE permission 或真实 permission probe。
- valid approval query：必须同时匹配当前 session 的 `scope_hash`、`release_digest`、`risk_limit_set_digest`、decision 和 expiry。
- ExecutionIntent/Receipt：仅 schema/constraints；worker dispatch、PLACE/CANCEL provider、unknown-result recovery、partial fill、blind retry、credential decrypt、permission probe 均未实现。

## Validation

| Command / evidence | Result |
| --- | --- |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra -am -DskipTests compile` | `BUILD SUCCESS` |
| 定向 `LiveSessionDomainTest,RiskLimitSetCanonicalEncoderTest,LiveSessionFactModelMigrationContractTest` | 8 tests，0 failure/error/skip |
| disposable PostgreSQL 17.7 `LiveSessionFactModelPostgresIntegrationTest` | 1 test，0 failure/error/skip；V1→V38、V38→V39、Flyway validate、六表/trigger/comment/历史 fingerprint/JDBC/负向引用/可信 creator/实时 RBAC/并发全部通过 |
| `mvn -f backend/pom.xml -pl nq-core,nq-risk,nq-infra -am test` | `BUILD SUCCESS`；`nq-infra` 117 tests / 0 failures / 4 skipped |
| `mvn -f backend/pom.xml test`（默认本机 5432） | exit 1；既有数据库 V38 checksum mismatch 导致 3 个 local-context test 无法启动；未 repair 或写入该库 |
| 全后端 test + disposable PostgreSQL 17.7 + 最小 legacy account fixture | `BUILD SUCCESS`；`nq-app` 270 tests / 0 failures / 0 errors / 27 skipped；ArchUnit 12/12 |
| cleanup | 临时 PostgreSQL 已停止；端口 55439 无 listener；`artifacts/gatey2-postgres-temp` 已删除；既有 5432 未修改 |

全量 skipped 为既有显式环境/可选 integration tests；warning 为既有 SLF4J provider、Mockito dynamic-agent、unchecked/deprecation 编译提示。本轮 CI=`NOT_RUN`。

## Findings and residuals

- P0：0。
- P1：0；实现阶段自审发现的 approval digest binding、session frozen-reference validation、可伪造 approver role/identity、creator identity 与通用 transition hard-gate bypass 五项缺口已最小修复并由 PostgreSQL/边界检查覆盖。
- P2：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`、`FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`、`LEGACY_ORDER_ACCOUNT_IDENTITY_BRIDGE` 继续保留。V39 local PASS 不可外推生产锁窗口；stable handle 与 legacy order bridge 尚未关闭，因此 execution runtime 仍阻断。
- P3：0。
- deferred：`reconciliation_cases` 仍需 GateY-3 用真实必要性证明；本轮不扩大六表模型。

## Boundary confirmation

- LIVE=`DISABLED`；kill switch=`ENGAGED`。
- credential material access=`0`；exchange call=`0`；permission probe=`0`。
- real order/cancel/transfer/withdraw=`0/0/0/0`；trading side effect=`0`。
- real provider/private trading=`NOT_IMPLEMENTED`。
- 未 stage、commit、push、PR 或 tag；production migration 未执行。

## Authority after and decision

```text
accepted_batch=GateY-1
accepted_batch_status=ACCEPTED|CI_GREEN

work_batch=GateY-2
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN

next_action=NQ-GATEY-2-LIVE-SESSION-FACT-MODEL-MIGRATION-SECURITY-REVIEW
```

Final decision：`PASS / GATEY_2_FACT_MODEL_IMPLEMENTED / MIGRATION_CREATED / POSTGRESQL_GREEN / ARCHITECTURE_HYGIENE_CHECKED / PENDING_INDEPENDENT_REVIEW / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED`。

唯一下一动作：`NQ-GATEY-2-LIVE-SESSION-FACT-MODEL-MIGRATION-SECURITY-REVIEW`。

推荐 commit message（仅供 review 接受后使用）：`feat(gatey): add live session control-plane fact model`。
