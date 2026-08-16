# GateY-6D exact pilot scope prerequisite materialization Security Review — attempt-01

## 审查结论

`FAIL / GATEY_6D_SECURITY_REVIEW_REJECTED / P0_0 / P1_1_OPEN / P1_3_CLOSED / UNTRUSTED_PREREQUISITE_OBSERVATION_AUTHORITY / EXACT_PILOT_SCOPE_NOT_MATERIALIZED / EXECUTION_INTENT_0 / OKX_CALL_0 / EXCHANGE_MUTATION_0 / LIVE_DISABLED / NOT_READY_TO_COMMIT`

本轮独立审查确认 control-plane 没有交易执行可达性，并以最小改动关闭三处授权问题；但 prerequisite observation 的动态事实仍由 `OPERATOR` 请求直接提供，未从可信 SoR 或可验证 attestation 重读。该缺口可使伪造事实进入 durable preflight，因此审查拒绝，authority 保持 `IMPLEMENTED|PENDING_REVIEW`（已实现 / 待独立评审）。

## 基线与范围

- task：`NQ-GATEY-6D-EXACT-PILOT-SCOPE-PREREQUISITE-MATERIALIZATION-SECURITY-REVIEW`；NQ-only / LIVE control-plane / authorization boundary。
- branch=`dev`；`HEAD == origin/dev == bc35edb60370aee367ab40853201e1f249179b83`；baseline CI=`31933158234 / completed / success`；进入审查时 staged=`0`、dirty files=`24`。
- authority before/after：accepted=`GateY-6C / ACCEPTED|CI_GREEN`；work=`GateY-6D / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；next action 保持本 Security Review。
- 审查覆盖 API/controller/application port、authority resolver、transaction service、V40 repository usage、API exception mapping、focused/全量测试与 trading reachability。
- 不改 V40、migration/schema、frontend、research、scripts、deploy、CI/governance；不访问生产、真实 credential 或交易所。

## Auth、ownership 与最小修复

- authenticated actor 仅由 `GatewayAuthFacade` token username 再解析服务端 `AuthUserProfile.userId`；request 不接受 creator、approver、role、owner 或 credential material。
- account 与 credential 均按 authenticated owner + exact ID 查询；release/risk/runtime reference 由正式 repository/server-owned config 重读并 constant-time 比较；`latest/current/HEAD` 拒绝。
- 已关闭 P1 `PREFLIGHT_CROSS_ACCOUNT_IDOR`：preflight 现在要求 `session.ownerId == actor.userId`，跨 owner 与不存在统一为 `LIVE_SESSION_NOT_FOUND`，不进入 stored-fact preflight。
- 已关闭 P1 `MATERIALIZATION_REPLAY_ROLE_REVOCATION_BYPASS`：首次创建与 existing-session replay 均在 transaction 内重新锁定并校验当前 `OPERATOR` 角色；角色撤销后不能重放或追加 observation set。
- 已关闭 P1 `APPROVAL_UNAUTHORIZED_RESOURCE_PROBE`：service 在读取 session/scope 前校验 `LIVE_APPROVER`；transaction 内校验保留，creator 自审批、scope/hash mismatch、future/expired/超 window approval 继续 fail closed。

## Server-side authority、canonical hash 与 atomicity

- account=`OKX/LIVE/ACTIVE`；credential exact owner/account/type/status/readiness/IP 且 withdraw=`false`；release admission revision/digest、RiskLimitSet 全字段与 server runtime identities/digests 均 exact compare。
- canonical `pilotScopeHash` 由服务端基于 session、release/risk/account/credential/symbol/window/runtime bindings 重建；客户端 hash 只作 expected value，mismatch 在写入前拒绝。
- `LiveSession + PilotScopeBinding + complete ObservationSet` 位于同一 `TransactionTemplate` 写事务；scope/observation repository 的 exact replay、different-payload conflict、deferred complete-set constraint 与 PostgreSQL 并发测试均通过。
- approval 固定 `pilot-scope.v1 + pilotScopeId + pilotScopeHash`；legacy approval 不能满足 preflight。preflight 在 `REPEATABLE READ` 中只读取 durable scope、exact valid approval 与 latest complete observation set，结果仅为 eligibility。

## Credential、API error 与 architecture boundary

- API/DTO/log/exception/evidence 只出现 credential reference/脱敏 readiness enum；未发现 API key、secret、passphrase、signature、raw encrypted material 或真实值。
- `ApiExceptionHandler` 对 not-found、role/authorization、idempotency/conflict 与 fail-closed domain error 返回稳定 404/403/409/422；当前 LiveControl exception message 为固定枚举/常量文本，不含 SQL、stack trace 或 credential material。
- API 只依赖 core application interface；JDBC 与 SoR adapter 留在 `nq-infra`；ArchUnit 通过。未新增 SQL/migration、application DTO 到其他 domain 的反向依赖或 provider/network import。

## P1：trusted observation authority 未建立

`PilotScopeMaterializationRequest` 允许 `OPERATOR` 直接提交 instrument items、maker/taker fee、`availableBalance`、clock skew、observation identity、balance/clock digest 与 `observedAt`。`JdbcPilotScopeAuthorityResolver` 只重读 scope/runtime source contract，没有重读或验证这些动态 observation facts；`PilotScopeControlPlaneService` 随后以 server-owned worker identity 作为 recorder，并仅对请求内容重算 payload hash。

该边界不能证明“请求值来自声明 source”：

1. 任意 digest/identity 与伪造 balance/skew 可作为完整 durable observation set 写入；hash 自洽只证明存储后未被篡改，不证明来源真实性。
2. `pilotScopeHash` 按 V40 contract 不包含 balance snapshot digest、clock sync digest、实际 balance/skew 或 observation set identity。
3. approval 只绑定 scope；existing-session materialization replay 允许追加新的 complete observation set，preflight 又选择 latest complete set。因此 `OPERATOR` 可在不重新审批的情况下把伪造的“余额充足 / clock 正常”事实变成 eligible preflight。

当前没有 ExecutionIntent/provider/worker/LIVE 可达性，故定级 P1 而非 P0；但它直接破坏 future first-order preflight 的 fail-closed authority，阻断 review acceptance。整改必须引入可信 SoR/attestation verification，并证明 source identity、observation identity、digest 与动态值同源；不得用默认值、fixture、纯请求 hash 或无来源常量冒充真实 observation。

## Trading reachability

- dirty production Java 无 execution/provider/OKX/network import；关键词命中仅为否定注释、固定 `executionIntentCount=0` response 与 credential withdraw capability 拒绝检查。
- tracked added lines 中 mutation keyword 命中=`0`；V40/migration、frontend/research/scripts/deploy/`.github` diff=`0`。
- task-created `ExecutionIntent/ExecutionReceipt`=`0/0`；credential access=`0`；OKX call=`0`；PLACE/CANCEL/TRANSFER/WITHDRAW=`0/0/0/0`；worker/real-provider start=`0/0`；LIVE enable/kill disengage=`0/0`。
- `EXACT_PILOT_SCOPE=NOT_MATERIALIZED`；`FIRST_REAL_ORDER=NOT_AUTHORIZED`；`MICRO_LIVE=NOT_AUTHORIZED`；LIVE=`DISABLED`；kill switch=`ENGAGED`。

## 验证

| Command / check | Result | Scope / warning |
| --- | --- | --- |
| GateY-6D controller/service/resolver focused | PASS（通过） | 20/20 modules；13 tests；failures/errors/skipped=`0/0/0` |
| PostgreSQL + GateY-2/4/6C + ArchUnit focused | PASS（通过） | disposable PostgreSQL 17.7；23/23 modules；137 tests；failures/errors=`0/0`；10 个既有 Windows stable-handle skip |
| V39→V40 / V1→V40 / rollback | PASS（通过） | V39→V40=`88ms`；no-fake-backfill、canonical parity、approval、幂等/并发、role-revoked replay PASS；lock-timeout rollback=`5059ms` |
| GateY-4 deployment boundary script | PASS（通过） | 6/6：delegate-release/linux-root/identity/no-start/no-secret/no-network |
| `mvn -f backend/pom.xml test` | PASS（通过） | 23/23 modules；1508 tests；failures/errors/skipped=`0/0/47`；52.052s |
| diff/static boundary | PASS（通过） | staged=`0`；migration/frontend/research/scripts/deploy/CI diff=`0`；credential/mutation reachability未发现正向路径 |

PostgreSQL focused 使用本轮 disposable container 与随机本地端口/临时密码，未连接本机 5432、生产库或真实数据；容器已停止并自动删除。Mockito dynamic-agent、SLF4J NOP、deprecation/unchecked、LF→CRLF 与既有 conditional skips 为非阻断 warning。

文档验证中首次无参调用 `check-doc-links.ps1` 因缺 mandatory `-Roots` 在扫描前退出 1；按脚本 contract 改为 `-Roots @('README.md','docs/current')` 后检查 336 links，14 个既有 historical ledger warnings、0 errors，最终 `PASS / DOC_LINKS_VALID`。该调用错误未写成通过。

## Findings

### P0

- 无。

### P1

- OPEN：`UNTRUSTED_PREREQUISITE_OBSERVATION_AUTHORITY`，见上文；阻断 acceptance/commit handoff。
- CLOSED：`PREFLIGHT_CROSS_ACCOUNT_IDOR`。
- CLOSED：`MATERIALIZATION_REPLAY_ROLE_REVOCATION_BYPASS`。
- CLOSED：`APPROVAL_UNAUTHORIZED_RESOURCE_PROBE`。

### P2

- 无。

### P3

- 无。根 `CLAUDE.md` 的旧 Gate/skill 文字不参与 current authority，且不在本任务允许范围，未修改。

## Authority、回滚与下一步

- review decision：`REJECTED / P1_OPEN / AUTHORITY_UNCHANGED / NOT_READY_TO_COMMIT`（审查拒绝 / P1 未关闭 / authority 不变 / 不可提交）。
- machine authority 保持 `GateY-6D / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；next action 保持本 Security Review，完成 trusted observation authority remediation 后重跑。
- 不 stage、commit、push、deploy，不给出 `git add` 授权；推荐 commit message 仅在后续 P1 关闭并复审通过后仍可使用：`feat(gatey): implement exact pilot scope materialization`。
- 回滚本 review 的最小修复时，可按文件逐项反向应用当前 diff；禁止使用 `reset --hard` 或覆盖其他 GateY-6D dirty implementation。审查 evidence/docs 可单独删除/反向 patch，但不得把 rejected 事实改写为 accepted。
