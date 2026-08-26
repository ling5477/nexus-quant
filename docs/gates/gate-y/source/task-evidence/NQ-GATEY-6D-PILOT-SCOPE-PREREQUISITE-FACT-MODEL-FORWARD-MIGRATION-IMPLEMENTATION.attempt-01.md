# NQ-GATEY-6D-PILOT-SCOPE-PREREQUISITE-FACT-MODEL-FORWARD-MIGRATION-IMPLEMENTATION — attempt-01

## Task classification

- ownership：NQ-only。
- type：`CODE_CHANGE / FORWARD_MIGRATION / DATA_MODEL / SECURITY_BOUNDARY`。
- level：L 级高风险 migration 实现；属于 GateY-6D canonical materialization 的 subordinate blocker remediation，不是新 Gate/Batch。
- result：`PASS / V40_IMPLEMENTED / PILOT_SCOPE_FACT_MODEL_IMPLEMENTED / PREREQUISITE_OBSERVATION_MODEL_IMPLEMENTED / JAVA_POSTGRES_CANONICAL_PARITY_PASS / NO_FAKE_BACKFILL / V39_TO_V40_PASS / V1_TO_V40_PASS / EXECUTION_INTENT_0 / OKX_CALL_0 / EXCHANGE_MUTATION_0 / LIVE_DISABLED / KILL_ENGAGED / PENDING_INDEPENDENT_MIGRATION_SECURITY_REVIEW`。

## Starting baseline

- branch=`dev`；起始 worktree clean、staged empty。
- `HEAD == origin/dev == 3544b70e877dde40908e369baaed8f1b312cfa30`。
- `NQ CI Baseline` run `31925787001`：`completed / success`，exact-head 与基线匹配。
- authority before/after 均为 accepted=`GateY-6C / ACCEPTED|CI_GREEN`，work=`GateY-6D / NOT_STARTED / NONE / NOT_RUN`；LIVE=`DISABLED`，kill switch=`ENGAGED`。
- 实现严格服从已冻结的 `NQ-GATEY-6D-PILOT-SCOPE-PREREQUISITE-FACT-MODEL-FORWARD-MIGRATION-WORK-ORDER.attempt-01.md`；未重新设计 schema，未修改 V1～V39。

## Scope and exclusions

- migration：新增 `V40__gate_y6d_pilot_scope_prerequisite_fact_model.sql`。
- core：新增 immutable pilot scope、typed prerequisite observation、canonical encoder、freshness/preflight policy 与 repository port；扩展 approval domain 的 schema/versioned pilot binding。
- infra：新增 JDBC repository 与 materialization/approval/refresh/preflight 四类事务编排；扩展既有 approval JDBC persistence。
- tests：新增 canonical/freshness、migration contract、PostgreSQL parity/no-fake-backfill/idempotency/concurrency/compatibility 回归，并最小修正三个 PostgreSQL fixture 对最新 migration 的 target/search-path 兼容。
- docs：本 evidence、`DB_SCHEMA.md`、`TESTING.md`、`WORKLOG.md` 与 GateY evidence index。
- excluded：frontend、research Python、scripts、deploy、`.github`、HTTP API、provider/worker runtime、credential material、真实 pilot、真实 approval、真实 LiveSession、ExecutionIntent/Receipt 创建、交易所网络与任何交易 mutation。

## V40 schema implementation

### Tables and approval extension

1. `pilot_scope_bindings`
   - 每个 `live_sessions.session_id` 最多一个 immutable scope；`pilot_scope_id` 为 UUID 主键。
   - 固定 `scope_schema_version='pilot-scope.v1'`；保存 instrument/fee/balance/clock source identity、schema version、freshness ceiling、fee evidence、timestamp/skew、endpoint policy、provider artifact、worker release 与 canonical `pilot_scope_hash`。
   - digest 均为 64 位 lowercase SHA-256；identity/version 非空；maximum-age 与 skew按冻结 ceiling 约束。
   - `(session_id, pilot_scope_id, pilot_scope_hash)` 唯一键供 approval exact composite FK 使用。
2. `pilot_prerequisite_observations`
   - append-only typed facts，固定四种 variant：`INSTRUMENT_METADATA / FEE_SCHEDULE / BALANCE_SNAPSHOT / CLOCK_SYNC`。
   - `(pilot_scope_id, observation_type, observation_identity)` 强制 source identity 幂等；`(pilot_scope_id, observation_set_id, observation_type)` 强制每 set 每 type 唯一。
   - variant CHECK 确保只填充对应 digest/fee/balance/clock typed 字段，不允许以通用 JSONB 伪造事实。
3. `pilot_instrument_observation_items`
   - `(observation_id, symbol)` 主键；composite FK 要求父 observation type 必须为 `INSTRUMENT_METADATA`。
   - symbol、trading status、tick/lot/minimum amount/value 与 USDT currency 均有格式、范围和允许值 CHECK。
4. `operator_approvals`
   - 仅新增 `scope_schema_version` 与 `pilot_scope_id`。
   - `scope_schema_version` 使用 `NOT NULL DEFAULT 'approval-scope.v1'` 完成历史兼容后在同一 migration 立即 `DROP DEFAULT`；未执行批量 `UPDATE`。
   - pilot approval 通过 `(session_id, pilot_scope_id, scope_hash)` exact composite FK 绑定 immutable scope；legacy approval 保持 `pilot_scope_id=NULL`，不能用于 pilot preflight。

### Database-enforced invariants

- functions 11 个：UTC instant/decimal canonicalization、pilot payload/hash/reconstruction、scope insert guard、observation insert guard、instrument digest、observation payload hash、complete-set validation、approval insert guard。
- triggers 8 个：scope canonical insert guard 与 UPDATE/DELETE deny；observation insert guard 与 append-only；instrument item append-only；两个 deferred complete-set constraint trigger；pilot approval insert guard。
- deferred validation 在 commit 前要求 exact 四类 observation complete set、instrument item symbol 集与 scope/session symbols 完全一致，并重建 instrument/observation digest。
- DB insert guard 重建 `pilot-scope.v1` canonical payload/hash，拒绝 supplied hash mismatch；不依赖 `jsonb::text`、Map iteration order、serializer 默认 key order、locale 或 timezone 默认行为。
- scope late-binding 到已 approved/executed session、self approval、approval expiry 超过 execution window、schema/FK mismatch 均由数据库或同事务应用边界 fail-closed。
- migration timeout 保持 `SET LOCAL lock_timeout='5s'`、`SET LOCAL statement_timeout='60s'`。
- V40 SHA-256：`1c0e486db0f3db4cdf250cb99ab0ed1e289f42d1ed522981272ee8b4c4da25e3`。

## Java/JDBC and transaction model

- `PilotScopeBinding` 与 sealed `PilotPrerequisiteObservation` hierarchy 只表达 durable typed facts；未新建 account/credential/release/risk/order/trade/position/ledger SoR。
- `PilotScopeCanonicalEncoder` 固定字段顺序、UTF-8、UTC instant、8 位 decimal 与 canonical uppercase symbol；golden digest=`be8cdd5153a053e10ed629d5b3932755b4e36cba31394ebf6e5c16f59d846741`。
- `PilotObservationCanonicalEncoder` 固定 instrument item 排序与四类 payload 编码；fresh observation identity/audit fields 被明确排除在 scope hash 之外。
- `PilotScopeFreshnessPolicy` fail-closed 处理 future/stale observation、非 LIVE instrument、insufficient balance、非 private observed fee、clock skew 与 source mismatch。
- `JdbcPilotScopeRepository` 由 `nq-infra` 实现 core port；同 identity+同 payload 返回既有事实，同 identity+不同 payload 返回 typed conflict；并发最终由 DB unique/row locking 裁决。
- `PilotScopeFactTransactionService` 保持四类事务：materialization 同事务写 session+scope+完整 set；approval 独立 approver 事务；refresh 只 append 新完整 set；preflight 使用 `REPEATABLE READ` 与单一 DB `decision_at` 选择 exact complete set。
- livecontrol 继续拥有 pilot binding；marketdata 只拥有 source observation 事实；account/strategy/risk 原 ownership 不变；JDBC 仅位于 infra；未增加跨 domain application DTO 依赖。

## PostgreSQL validation

- disposable PostgreSQL=`17.7`，database=`nq_gatey6d`，loopback port=`55440`；未连接生产数据库。
- V39→V40 upgrade：PASS；实测约 `70ms`，最后一次 focused rerun=`72ms`。历史 session/approval count 与原字段 fingerprint 不变；历史 approval schema=`approval-scope.v1`、`pilot_scope_id=NULL`；三张新表历史行数均为 0。
- V1→V40 full replay + `Flyway.validate`：PASS。
- no-fake-backfill：PASS；未创建历史 scope/observation/item，未制造 digest/source/observedAt。
- Java/PostgreSQL canonical payload 与 hash byte-for-byte parity：PASS；DB reconstruction 与 supplied digest guard 均通过。
- failure/rollback：持有 `operator_approvals` 冲突锁后，V40 在约 5 秒 bounded `lock_timeout` 失败；最后一次实测=`5079ms`。Flyway 明确记录 transaction rollback，检查确认不存在 partial V40 tables/columns/history row。
- idempotency/concurrency：scope retry 同 payload单一 winner；observation retry 同 payload单一完整 set；scope/observation identity conflict 均 fail-closed。
- approval compatibility：legacy/new matrix、exact composite binding、self approval、expiry>window、legacy approval不能用于 pilot 均通过。
- negative constraints：scope immutability、late binding、observation variant、incomplete set、instrument exact set/digest、future/stale、insufficient balance、skew ceiling 均通过。
- lock-window 数字只描述 localhost disposable 小 fixture，不外推 production SLA；production migration 未授权。

## Test evidence

| Command / suite | Result |
| --- | --- |
| `PilotScopeCanonicalEncoderTest` + `PilotScopeFreshnessPolicyTest` | 5/5 PASS；golden、全 immutable field mutation、excluded-field invariant、symbol/time/decimal normalization 与 freshness fail-closed |
| `PilotScopePrerequisiteFactModelMigrationContractTest` | 4/4 PASS；三表合同、DB invariant/canonical reconstruction、comments、V40 checksum |
| `ModuleBoundaryArchTest` + `PackageBoundaryArchTest` | 20/20 PASS |
| `LiveSessionFactModelPostgresIntegrationTest`（required PostgreSQL） | 3/3 PASS；V39→V40、V1→V40、rollback、parity、no-backfill、constraints、idempotency/concurrency、approval compatibility |
| final full `mvn -f backend/pom.xml ... test` | 23/23 modules `BUILD SUCCESS`；`nq-app` 284 tests / 0 failures / 0 errors / 24 existing conditional skips |
| import/style 后 focused reactor rerun | 23/23 modules `BUILD SUCCESS`；5+4+3 tests 全 PASS；重新编译 nq-core/nq-infra/nq-app 与依赖模块 |

首次 focused rerun 因 PowerShell 将含 JDBC URL 的 `-D` 参数误解析为 Maven plugin 坐标而在测试启动前 exit 1；改用 PowerShell stop-parsing 后同一测试集合 exit 0。该命令转义失败不属于代码或测试失败，未修改数据库 schema。

## Diff and boundary counters

- migration diff：`V40=1`；V1～V39 diff=`0`。
- backend production/test diff：仅 `nq-core`、`nq-infra`、`nq-app`；frontend/research/scripts/deploy/`.github` diff=`0`。
- governance：`STATUS.md` / `ROADMAP.md` diff=`0`；machine authority 不变。
- added-line/exact-pattern scan：ExecutionIntent insert/new=`0`，ExecutionReceipt insert/new=`0`，PLACE/CANCEL/TRANSFER/WITHDRAW=`0/0/0/0`，OKX call/reference=`0`，credential access=`0`，worker start=`0`，real-provider wiring=`0`，LIVE enable=`0`，kill disengage=`0`。
- migration contract 中两处 `assertFalse("INSERT INTO execution_...")` 是负向断言；migration COMMENT 中“不是 real-provider wiring”是边界说明，均不计为能力或调用。
- credential material、外部网络、生产 DB、真实 LiveSession/pilot/approval、stage/commit/push/tag/deploy=`0`。

## Findings and residuals

- P0：0。
- P1：0。
- P2：1；production lock window/target scale 尚未测量，disposable 约 70ms upgrade 与约 5s timeout 不能外推生产。独立 migration/security review 必须复核 DDL locks、trigger/check completeness、canonical parity 与 rollback semantics。
- P3：0。
- residual：`IMPLEMENTED / PENDING_INDEPENDENT_MIGRATION_SECURITY_REVIEW`；尚未 commit，exact-head CI 尚未运行；不 materialize 真实 pilot，不创建 independent approval。

## Authority and decision

`docs/current/STATUS.md` 与 `ROADMAP.md` 不修改；machine authority 继续：

```text
accepted_batch=GateY-6C
accepted_batch_status=ACCEPTED|CI_GREEN

work_batch=GateY-6D
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN

live=DISABLED
kill_switch=ENGAGED
```

Final decision：`PASS / V40_IMPLEMENTED / PILOT_SCOPE_FACT_MODEL_IMPLEMENTED / PREREQUISITE_OBSERVATION_MODEL_IMPLEMENTED / JAVA_POSTGRES_CANONICAL_PARITY_PASS / NO_FAKE_BACKFILL / V39_TO_V40_PASS / V1_TO_V40_PASS / EXECUTION_INTENT_0 / OKX_CALL_0 / EXCHANGE_MUTATION_0 / LIVE_DISABLED / KILL_ENGAGED / PENDING_INDEPENDENT_MIGRATION_SECURITY_REVIEW`。

唯一下一动作：`NQ-GATEY-6D-PILOT-SCOPE-PREREQUISITE-FACT-MODEL-FORWARD-MIGRATION-SECURITY-REVIEW`。

推荐 commit message（仅供独立 review 接受后使用）：`feat(gatey): add pilot scope prerequisite fact model`。
