# NQ-GATEY-6D-PILOT-SCOPE-PREREQUISITE-FACT-MODEL-FORWARD-MIGRATION-WORK-ORDER — attempt-01

## Task Classification

- ownership：NQ-only。
- type：`DOCUMENTATION / MIGRATION_DESIGN / DATA_MODEL_CONTRACT / SECURITY_BOUNDARY`。
- lifecycle：GateY-6D blocker remediation；只冻结 forward migration 合同，不推进 current authority。
- result：
  `PASS / GATEY_6D_FORWARD_MIGRATION_CONTRACT_DEFINED / DURABLE_PILOT_SCOPE_MODEL_DEFINED / PREREQUISITE_FACT_MODEL_DEFINED / CANONICAL_PILOT_SCOPE_HASH_DEFINED / NO_FAKE_BACKFILL / NO_EXECUTION_INTENT / NO_EXCHANGE_MUTATION / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`
  （通过 / 可进入提交前复核）。

## Starting Baseline

- branch=`dev`；起始 worktree clean、staged/unstaged/untracked=`0/0/0`。
- `HEAD == origin/dev == 016d652f3b57ce3282f3efd03bdd0dfdcd7c5ae4`。
- exact-head CI：run `31923811316 / completed / success`，`headSha` 与 HEAD 精确一致。
- authority：accepted=`GateY-6C / ACCEPTED|CI_GREEN`；work=`GateY-6D / NOT_STARTED`；`STATUS.md` 本轮不修改。
- safety：LIVE=`DISABLED`、kill switch=`ENGAGED`、`FIRST_REAL_ORDER=NOT_AUTHORIZED`。
- migration inventory：正式 Flyway migration 最高版本为 `V39__gate_y2_live_session_fact_model.sql`，故下一候选版本冻结为
  V40；本轮 SQL 文件新增/修改=`0`。

## Scope and Exclusions

- allowed：本 evidence、GateY-6 work order 的一处 forward 事实修正、`TESTING.md`、`WORKLOG.md`、GateY evidence index。
- excluded：backend/frontend/research/scripts/deploy/`.github`、migration SQL、Java、`STATUS.md`
  、governance/CI、credential/OKX、LiveSession/approval/ExecutionIntent 创建、PLACE/CANCEL/TRANSFER/WITHDRAW、LIVE enable 与
  kill disengage。
- architecture：`livecontrol` 拥有 pilot scope binding；marketdata 只提供 market observation source
  fact；account/strategy/risk 继续拥有 account/credential、release、runtime risk；不复制 order/trade/position/ledger
  SoR；JDBC 仍留在 infra。

## Confirmed V39 Gaps

V39 的 `live_sessions` 只持有 release/risk/account/credential/symbol/capital/window 与 `approval-scope.v1`；
`LiveSessionApprovalScopeEncoder` 也只编码这些字段。它不能 durable、可回读、可重建地表达以下 mandatory facts：

- `instrumentMetadataDigest` 与 instrument observation identity/`observedAt`；
- `feeScheduleDigest`、fee tier/evidence class/`observedAt`；
- `balanceSnapshotDigest`、balance snapshot identity/`observedAt`；
- `clockSyncObservationDigest`、clock `observedAt`、signed timestamp source 与 maximum tolerated skew；
- endpoint policy version/digest；provider contract identity/artifact digest；worker identity/release digest；
- versioned canonical `pilotScopeHash` 与 approval 对 exact pilot scope 的外键绑定。

V34 的 `instrument_catalog` 是可覆盖的 current venue-rule source，缺少 mandatory minimum order value，也不是 session-bound
append-only historical observation owner，因此不能充当 GateY-6D prerequisite ledger。不可逆 digest 只能作为完整 typed fact
的校验与绑定值，不能继续作为唯一事实来源。

## Chosen Minimal Schema

选择方案 2：独立 `pilot_scope_bindings` + typed prerequisite observations。首版仅新增三张表，并扩展 `operator_approvals`
两列；不向 `live_sessions` 塞入 15 个以上 prerequisite 字段。

拒绝“扩展 `live_sessions` + observation 表”，原因是它会把 immutable scope 与 refreshable observations 混在 lifecycle row
中，扩大 V39 update guard、历史兼容和 silent mutation 风险，并重复现有 release/risk/account/credential facts。拒绝每种
prerequisite 单独建表，因为会增加 join、事务与完整集约束复杂度而不增加首版安全性。

### 1. `pilot_scope_bindings`

| Column                                                            | Type / nullability                         | Contract                                                                                                 |
|-------------------------------------------------------------------|--------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `pilot_scope_id`                                                  | `UUID NOT NULL`                            | PK，不复用                                                                                               |
| `session_id`                                                      | `UUID NOT NULL`                            | FK → `live_sessions(session_id)`；`UNIQUE`；一个 session 最多一个 immutable pilot scope                  |
| `scope_schema_version`                                            | `VARCHAR(64) NOT NULL`                     | 固定 `pilot-scope.v1`                                                                                    |
| `instrument_metadata_digest`                                      | `VARCHAR(64) NOT NULL`                     | exact symbol constraint set digest                                                                       |
| `instrument_source_identity` / `instrument_source_schema_version` | `VARCHAR(128/64) NOT NULL`                 | immutable source contract                                                                                |
| `instrument_maximum_age_ms`                                       | `BIGINT NOT NULL`                          | `1..300000`，实际值进入 scope                                                                            |
| `fee_schedule_digest`                                             | `VARCHAR(64) NOT NULL`                     | exact fee constraint digest                                                                              |
| `fee_tier` / `fee_evidence_class`                                 | `VARCHAR(64/32) NOT NULL`                  | evidence class 仅 `OBSERVED_PRIVATE` 或 `ESTIMATED_PUBLIC`；第一单 eligibility 必须为 `OBSERVED_PRIVATE` |
| `fee_source_identity` / `fee_source_schema_version`               | `VARCHAR(128/64) NOT NULL`                 | immutable fee source contract                                                                            |
| `fee_maximum_age_ms`                                              | `BIGINT NOT NULL`                          | `1..3600000`                                                                                             |
| `balance_source_identity` / `balance_source_schema_version`       | `VARCHAR(128/64) NOT NULL`                 | exact private balance source contract                                                                    |
| `balance_maximum_age_ms`                                          | `BIGINT NOT NULL`                          | `1..10000`                                                                                               |
| `clock_source_identity` / `clock_source_schema_version`           | `VARCHAR(128/64) NOT NULL`                 | exact clock observation source contract                                                                  |
| `clock_maximum_age_ms`                                            | `BIGINT NOT NULL`                          | `1..60000`                                                                                               |
| `signed_timestamp_source`                                         | `VARCHAR(64) NOT NULL`                     | 首版固定 `NTP_DISCIPLINED_SYSTEM_CLOCK`                                                                  |
| `maximum_tolerated_skew_ms`                                       | `BIGINT NOT NULL`                          | `0..1000`；实际阈值进入 scope                                                                            |
| `endpoint_policy_version` / `endpoint_policy_digest`              | `VARCHAR(64/64) NOT NULL`                  | exact typed method/path/operation/order-type policy                                                      |
| `provider_contract_identity` / `provider_artifact_digest`         | `VARCHAR(128/64) NOT NULL`                 | provider contract 与 immutable artifact                                                                  |
| `worker_identity` / `worker_release_digest`                       | `VARCHAR(128/64) NOT NULL`                 | admitted worker 与 immutable release                                                                     |
| `pilot_scope_hash`                                                | `VARCHAR(64) NOT NULL`                     | lowercase SHA-256 of `pilot-scope.v1` canonical bytes                                                    |
| `created_by` / `created_at`                                       | `BIGINT NOT NULL` / `TIMESTAMPTZ NOT NULL` | FK → `users(id)`；审计字段，均不进入 scope hash                                                          |

约束与索引冻结如下：

- PK `pk_pilot_scope_bindings`；unique `uq_pilot_scope_bindings_session`。
- 为 approval exact FK 提供 `uq_pilot_scope_bindings_approval(session_id, pilot_scope_id, pilot_scope_hash)`。
- 所有 digest 使用 `^[0-9a-f]{64}$`；identity/version 必须 `btrim(value) <> ''` 且有长度上限；maximum-age/skew 使用上表
  hard ceiling。
- `trg_pilot_scope_bindings_immutable` 拒绝所有 `UPDATE/DELETE`。
- insert guard 锁定并核验 session 为 `APPROVAL_PENDING`，且不存在 approval 或 ExecutionIntent；已批准/已执行 session 不能补挂
  scope。
- `session_id` 是唯一 materialization identity：same session + same canonical payload/hash 返回既有 `pilot_scope_id`；same
  session + different payload/hash 返回 `PILOT_SCOPE_MATERIALIZATION_CONFLICT`。scope 任一 immutable input 变化必须创建新
  session + 新 scope，不能覆盖旧行。

### 2. `pilot_prerequisite_observations`

该表 append-only，每个完整 `observation_set_id` 对同一 `pilot_scope_id` 恰好包含四类 observation。

共同列：

- `observation_id UUID PK`、`pilot_scope_id UUID NOT NULL FK`、`observation_set_id UUID NOT NULL`；
- `observation_type VARCHAR(32) NOT NULL`，仅 `INSTRUMENT_METADATA / FEE_SCHEDULE / BALANCE_SNAPSHOT / CLOCK_SYNC`；
- `observation_schema_version VARCHAR(64) NOT NULL`，按 type 固定为 `instrument-metadata-observation.v1`、
  `fee-schedule-observation.v1`、`balance-snapshot-observation.v1` 或 `clock-sync-observation.v1`；
- `observation_identity VARCHAR(128) NOT NULL`、`source_identity VARCHAR(128) NOT NULL`、
  `source_schema_version VARCHAR(64) NOT NULL`；
- `observed_at TIMESTAMPTZ NOT NULL`、`recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()`、
  `recorder_identity VARCHAR(128) NOT NULL`；
- `observation_payload_hash VARCHAR(64) NOT NULL`，以 `prerequisite-observation-envelope.v1` 覆盖
  type/schema、identity、source、
  `observedAt` 与全部 typed payload，用于 retry payload equality；
- typed digest：`instrument_metadata_digest`、`fee_schedule_digest`、`balance_snapshot_digest`、
  `clock_sync_observation_digest`，每行按 type 恰好一个非 NULL。

typed variant columns：

| Type                  | Mandatory typed facts                                                                                                                                                                     |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `INSTRUMENT_METADATA` | `instrument_metadata_digest`；明细由 item 表持有                                                                                                                                          |
| `FEE_SCHEDULE`        | `fee_schedule_digest`、`fee_tier`、`fee_evidence_class`、`maker_fee_rate NUMERIC(20,12)`、`taker_fee_rate NUMERIC(20,12)`、`fee_loss_treatment='INCLUDE_IN_DAILY_LOSS_AND_CAPITAL_USAGE'` |
| `BALANCE_SNAPSHOT`    | `balance_snapshot_digest`、`balance_currency='USDT'`、`available_balance NUMERIC(38,8) >= 0`；digest 不是唯一余额事实                                                                     |
| `CLOCK_SYNC`          | `clock_sync_observation_digest`、`signed_timestamp_source`、`observed_skew_ms BIGINT`；source 必须与 scope 一致                                                                           |

约束与行为：

- `uq_pilot_observation_source_identity(pilot_scope_id, observation_type, source_identity, observation_identity)` 保证
  stable source identity 唯一；`uq_pilot_observation_set_type(pilot_scope_id, observation_set_id, observation_type)`
  保证每组每类至多一条。
- variant CHECK 要求当前 type 的 mandatory 列全部非 NULL，其他 type 专用列全部 NULL；fee rate 限制在 `[-1,1]`，所有 hash 为
  lowercase SHA-256。
- insert guard 验证 source/schema/recorder 与 immutable scope contract 匹配；instrument/fee digest、fee tier/evidence
  class 与 scope 精确相等；balance currency 与 risk quote currency 相等；clock source匹配且
  `abs(observed_skew_ms) <= maximum_tolerated_skew_ms`。
- timestamp guard 要求 `observed_at <= recorded_at + maximum_tolerated_skew_ms`；freshness 不通过删除历史，而是在一个 DB
  `decision_at` 下按 `observed_at + <type>_maximum_age_ms >= decision_at` fail closed。
- deferred constraint trigger `trg_pilot_observation_set_complete` 在 commit 前验证每个新增 set 恰好四类且 instrument
  items 完整。部分 observation set 不得提交。
- `trg_pilot_prerequisite_observations_append_only` 拒绝所有 `UPDATE/DELETE`；索引
  `(pilot_scope_id, observation_type, observed_at DESC, observation_id)` 与 `(pilot_scope_id, observation_set_id)` 支持
  deterministic fresh-set lookup。
- same source identity + same `observation_payload_hash` 为幂等，repository 返回原 `observation_id`；same identity +
  different hash 为 `PREREQUISITE_OBSERVATION_IDENTITY_CONFLICT`。数据库 unique 负责并发裁决，应用不得吞 unique violation
  后盲写新 identity。

### 3. `pilot_instrument_observation_items`

| Column                                          | Type / nullability            | Contract                                                               |
|-------------------------------------------------|-------------------------------|------------------------------------------------------------------------|
| `observation_id` / `observation_type`           | `UUID / VARCHAR(32) NOT NULL` | composite FK → observation；type 固定 `INSTRUMENT_METADATA`            |
| `symbol`                                        | `VARCHAR(64) NOT NULL`        | canonical uppercase `BASE-USDT`                                        |
| `trading_status`                                | `VARCHAR(16) NOT NULL`        | `LIVE / SUSPEND / PREOPEN / TEST`；first-order preflight 只接受 `LIVE` |
| `tick_size` / `lot_size` / `minimum_order_size` | `NUMERIC(38,18) NOT NULL`     | 均 `> 0`                                                               |
| `minimum_order_value`                           | `NUMERIC(38,18) NOT NULL`     | `> 0`，不得由不可回读 hash 替代                                        |
| `minimum_order_value_currency`                  | `VARCHAR(16) NOT NULL`        | 首版固定 `USDT`                                                        |

- PK `(observation_id, symbol)`；composite FK 与 type CHECK 防止 item 挂到非 instrument observation。
- deferred validation 要求每个 instrument observation 恰好 1–2 行、symbol 排序去重后的集合与 parent scope 对应
  `live_sessions.symbol_allowlist` 完全相等，并重建 `instrument_metadata_digest` 校验。
- `trg_pilot_instrument_observation_items_append_only` 拒绝 `UPDATE/DELETE`；父 observation/scope 删除均 `RESTRICT`。

## Canonical `pilotScopeHash`

`pilotScopeHash = lowercaseHex(SHA-256(UTF-8(canonical pilot-scope.v1 JSON)))`。实现必须提供 Java encoder 与 PostgreSQL
reconstruction function，并用 golden vectors 证明 bytes 完全相同；数据库 insert guard 拒绝 supplied hash 与 reconstruction
不一致。

字段顺序固定为：

```text
schemaVersion, sessionId, ownerId, exchangeAccountId, venue,
strategyReleaseId, releaseArtifactDigest, releaseAdmissionRevision,
riskLimitSetId, riskLimitSetDigest, credentialReference,
symbolAllowlist, capitalCap, executionWindowStart, executionWindowEnd,
instrumentMetadataDigest, instrumentSourceIdentity, instrumentSourceSchemaVersion,
instrumentMaximumAgeMs, feeScheduleDigest, feeTier, feeEvidenceClass,
feeSourceIdentity, feeSourceSchemaVersion, feeMaximumAgeMs,
balanceSourceIdentity, balanceSourceSchemaVersion, balanceMaximumAgeMs,
clockSourceIdentity, clockSourceSchemaVersion, clockMaximumAgeMs,
signedTimestampSource, maximumToleratedSkewMs,
endpointPolicyVersion, endpointPolicyDigest,
providerContractIdentity, providerArtifactDigest,
workerIdentity, workerReleaseDigest
```

Normalization contract：

- JSON key/order 固定，不依赖 serializer；string 使用现有 canonical JSON escaping；UTF-8 无 BOM、无额外空白。
- symbol 必须 uppercase、排序、去重，1–2 个；重复或非 canonical 顺序在入库前拒绝。
- `capitalCap` 沿用 V39 money contract：`NUMERIC(38,8)`，固定 8 位小数的 quoted plain string；整数不加前导零。
- Instant 统一 UTC，固定 6 位微秒 `yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ`；超过微秒精度拒绝。
- hash/digest 固定 lowercase 64 hex；UUID lowercase canonical；数值 identity/threshold 使用无引号十进制整数。
- instrument item 的 `NUMERIC(38,18)` 与 fee rate 不直接进入 pilot scope；其各自 observation encoder 使用
  `stripTrailingZeros().toPlainString()`（零固定 `0`），digest 再进入 scope。
- `pilot_scope_id`、`created_by/created_at`、observation ID/set ID/identity、`observedAt`、`recordedAt`、
  `balanceSnapshotDigest`、`clockSyncObservationDigest` 与实际 skew/balance 不进入 scope hash。

Fresh observation refresh 只要 instrument/fee constraint digest、source contract 与 immutable scope 全部不变，就不要求重新
approval；stale/insufficient/unknown 只拒绝 preflight并追加新 observation set。若 refresh 揭示
instrument/fee/policy/provider/worker constraint 变化，旧 scope 不接受该 observation，必须新 session + 新 scope + 新
approval。

## Approval Compatibility

`operator_approvals.scope_hash` 不 supersede，也不无版本改义。V40 候选扩展：

- `scope_schema_version VARCHAR(64) NOT NULL`：历史行确定性标记 `approval-scope.v1`；
- `pilot_scope_id UUID NULL`：历史行为 NULL；新 pilot approval 必须非 NULL；
- CHECK：`approval-scope.v1 + pilot_scope_id IS NULL`，或 `pilot-scope.v1 + pilot_scope_id IS NOT NULL`，没有第三种组合；
- composite FK `(session_id, pilot_scope_id, scope_hash)` →
  `pilot_scope_bindings(session_id, pilot_scope_id, pilot_scope_hash)`；
- pilot approval insert trigger 强制 session仍为`APPROVAL_PENDING`、`created_by <> approver_id`、
  `expires_at <= execution_window_end`、release/risk digest 与 session 精确相等；有 pilot scope 的 session 禁止新增
  legacy-schema approval。

迁移历史 approval 时不得触发 V39 append-only guard：采用 `ADD COLUMN ... NOT NULL DEFAULT 'approval-scope.v1'` 后在同一
migration `DROP DEFAULT` 的 metadata-only 路径，不执行历史行 `UPDATE`；implementation 必须在目标 PostgreSQL
版本验证该语义与锁时长。GateY-6D approval query 必须显式过滤 `scope_schema_version='pilot-scope.v1'` 并按 exact composite
FK；历史 `approval-scope.v1` 永远不能满足 pilot authorization。

## Transaction and Freshness Model

1. materialization transaction：创建全新的 `APPROVAL_PENDING` LiveSession，随后写 `pilot_scope_bindings`、一个完整
   observation set 与 instrument items；deferred constraints 在同一 commit 校验。任何一步失败全部回滚。不得在该事务创建
   approval 或调用外部 API。
2. approval transaction：独立 authenticated approver 锁定 session + scope，重建 hash并核验状态/expiry/creator separation 后
   append approval；不更新 scope，不创建 intent。
3. refresh transaction：只 append 一个新的完整 observation set；同 identity retry按 payload hash收敛；不得覆盖旧
   observation。
4. preflight transaction：以单一 `transaction_timestamp()`/`decision_at` 在 `REPEATABLE READ` 快照内选定 exact complete
   `observation_set_id`，检查四类 freshness、instrument `LIVE`、balance sufficient、clock skew、fee evidence class与
   immutable digest一致；返回 exact observation IDs。任一 NULL/stale/conflict/insufficient均 fail closed。
5. scope变化：V39 session scope与 pilot binding均 immutable，故不允许原 session 原地回到新 hash；创建新 session/scope自然使旧
   approval无法通过 composite FK/query。

## Migration and Backfill Policy

- candidate：`V40__gate_y6d_pilot_scope_prerequisite_fact_model.sql`；只允许 upgrade/forward-only，设置 bounded
  `lock_timeout` 与 `statement_timeout`。
- V12 已引入 `pgcrypto`；V40 可复用其 SHA-256 能力，但必须由 Java/DB golden parity 验证 canonical bytes，不得仅验证 hash
  格式。
- historical `live_sessions` 不创建 `pilot_scope_bindings`，不生成 observation，不推导 digest，不补 `pilot_scope_id`；统一语义为
  `NOT_MATERIALIZED`。
- 唯一历史 backfill 是 existing approval 的真实 schema label `approval-scope.v1`；`pilot_scope_id` 保持 NULL。不存在 fake
  prerequisite、zero digest、placeholder source 或 fabricated `observedAt`。
- nullable staging 仅允许 `operator_approvals.pilot_scope_id` 用于历史兼容；三张新表的 mandatory 字段全部 NOT NULL。新
  materialization 任一字段缺失即 fail closed。
- migration 不修改 V1–V39；失败回滚当前未提交 transaction。上线后发现问题只创建 V41+ forward remediation，不执行
  destructive downgrade，不删除历史事实。
- 所有新表与全部新字段必须有中文 `COMMENT ON TABLE/COLUMN`；状态/enum、敏感边界与 retention/delete policy 的 comment 不得省略。
- migration implementation 前必须在 disposable PostgreSQL 证明 V39→V40 upgrade、从空库全量 replay、approval table lock
  window、failure rollback 与 checksum stability。

## Implementation File Scope

下一任务允许的最小范围：

- `backend/nq-infra/src/main/resources/db/migration/V40__gate_y6d_pilot_scope_prerequisite_fact_model.sql`；
- `nq-core` 的 pilot scope/typed observation domain、canonical encoder、repository port 与 freshness policy；
- `nq-infra` 的 JDBC repository/row mapper、PostgreSQL canonical reconstruction 与 transaction implementation；
- `nq-app`/infra PostgreSQL integration tests及必要 ArchUnit regression。

不允许在该任务接入 credential、OKX transport、real provider runtime、worker/scheduler
start、ExecutionIntent、PLACE/CANCEL、funds movement、LIVE 或 GateY-6E；`DB_SCHEMA.md` 只有 migration 实际落地并通过验证后才能同步为
current fact。

## Required Migration Tests

- empty DB V1→V40 与真实 V39 fixture→V40 upgrade；历史 session/approval count与原字段逐值不变；历史 approval schema为
  `approval-scope.v1`且pilot scope为 NULL。
- no-fake-backfill assertion：historical pilot scope/observation/item rows均为 0。
- scope hash Java/PostgreSQL golden parity、字段逐一 mutation hash变化、excluded fresh fields变化
  hash不变、symbol/time/decimal normalization拒绝用例。
- scope/session exact FK、single scope per session、immutable update/delete、approved/executed session late-binding
  rejection。
- observation四 variant CHECK、完整 set deferred trigger、instrument exact symbol set、digest mismatch、future
  timestamp、stale/insufficient/skew fail-close。
- same identity/same payload retry返回同一事实；same identity/different payload conflict；并发 materialization/observation
  retry仅一个 winner。
- legacy/new approval matrix、composite FK、creator≠approver、expiry≤window、legacy approval不能满足pilot query、scope变化后旧
  approval不可复用。
- migration lock/statement timeout、transaction rollback、Flyway checksum、table/column `COMMENT` 完整性；full backend与相关
  ArchUnit回归。

## Validation

| Command / check                                                  | Result                                          | Scope / known warnings                                                                              |
|------------------------------------------------------------------|-------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| Git/origin/current CI baseline                                   | PASS（通过）                                    | `dev` clean；`HEAD == origin/dev == 016d652f...`；CI `31923811316 / completed / success` exact-head |
| `scripts/docs/check-current-authority.ps1`                       | PASS（通过）                                    | `errors=0 / CURRENT_AUTHORITY_CONSISTENT`；`STATUS.md` unchanged                                    |
| `scripts/docs/check-doc-links.ps1 -Roots README.md,docs/current` | PASS WITH WARNINGS（通过并有 warning）          | `324 checked / 14 historical warnings / 0 errors`；均为既有 ledger 历史路径                         |
| `git diff --check`                                               | PASS（通过）                                    | exit=`0`；仅 Git for Windows LF→CRLF informational warning                                          |
| dirty allowlist / forbidden-area diff                            | PASS（通过）                                    | changed=`5/5` allowed；backend/frontend/research/scripts/deploy/.github/migration=`0`               |
| positive authorization pattern scan                              | PASS（通过）                                    | 新增 diff/evidence 中 positive authorization hits=`0`                                               |
| IDE Markdown format/problems                                     | PASS WITH RECOVERED CHURN（通过，已收回 churn） | bulk reformat 一度机械改写四个大文件；恢复精确 clean HEAD 后仅重放小 patch；最终 5 文件 errors=`0`  |
| Maven/frontend/Python/PostgreSQL                                 | NOT RUN（未运行）                               | docs/design-only；产品与 SQL diff=0；V40 implementation task 才运行，非阻断                         |

已知 warning：14 条 doc-link warning 为既有 append-only 历史引用；Git for Windows 提示未来 LF→CRLF，不是 whitespace
error。第一次并行 IDE problems 调用长时间无输出后终止，单次 reformat retry成功，顺序 problems检查 5 文件均为 0；formatter
churn 已完全收口，无用户基线改动被覆盖。

## Findings and Risk

- P0=0：未创建 migration、session、approval、intent，未触达 credential、OKX、exchange mutation、LIVE 或 kill switch。
- P1=0：mandatory durable fields、scope/observation ownership、approval version compatibility、canonical
  hash、transaction/idempotency与no-fake-backfill均已冻结，无安全关键字段留给 implementation 自行决定。
- P2=2：V40 尚未实现，PostgreSQL lock window与Java/DB canonical parity尚未实测；这两项阻断 migration acceptance，但不阻断本合同完成。
- P3=0：无。

## Authority After and Next Action

- `STATUS.md` unchanged：GateY-6C=`ACCEPTED|CI_GREEN`；GateY-6D=`NOT_STARTED`。
- LIVE=`DISABLED`、kill=`ENGAGED`、`FIRST_REAL_ORDER=NOT_AUTHORIZED`；GateY-6D exact scope仍未 materialize。
- 本 blocker remediation 成功后的唯一下一任务：

```text
NQ-GATEY-6D-PILOT-SCOPE-PREREQUISITE-FACT-MODEL-FORWARD-MIGRATION-IMPLEMENTATION
```

该任务才允许创建 V40、domain/repository/JDBC 与 PostgreSQL integration tests；仍不授权
credential/OKX、ExecutionIntent、exchange mutation、LIVE、kill disengage 或 GateY-6E。
