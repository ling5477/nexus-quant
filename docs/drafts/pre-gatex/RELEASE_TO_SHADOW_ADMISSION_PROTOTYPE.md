# Release-to-Shadow Admission Prototype

状态：`TEST-ONLY PROTOTYPE / NON-AUTHORITATIVE`（仅测试原型 / 非权威实现）。

固定边界：

```text
NO DATABASE
NO SHADOW RUN CREATION
NO RUNNER
NOT TRADING AUTHORIZATION
DO NOT MERGE INTO DEV BEFORE GATEW ACCEPT
```

## 1. 目标与复用决策

本原型只把不可变的 Strategy Release、artifact verification、validation evidence 与申请的 Shadow
binding 做 fail-closed 判断，成功时仅返回未来 GateX 的 `ShadowRunCreationPlanPrototype`。它不调用
repository、runner、scheduler、RiskGate、文件、网络或交易所，也不创建 event、snapshot、report 或
`shadow_runs` 行。

复用既有 `shadow_runs`，不创建 `shadow_sessions` 或其他平行 session aggregate。现有 Shadow Run 已承载
strategy version、dataset、evaluation、`publish_id`、窗口、授权边界、六项无副作用策略、幂等键与 trace；
唯一尚未落地的 release provenance 主事实是 `artifact_digest`。

## 2. 身份与 provenance

唯一 release identity 为：

```text
publishRecordId = backtest_publish_records.publish_record_id = shadow_runs.publish_id
```

它是 `VARCHAR(128)` business identity，不新增独立 `releaseId` 或 UUID。既有 test aggregate 中的
`releaseId` 只能是 `publishId` 的兼容领域别名；本 admission 若发现两者不同即以
`PUBLISH_ANCHOR_MISMATCH` fail-closed。

完整 GateX provenance 固定为：

```text
publishRecordId + artifactDigest
```

| Binding mode | admission |
| --- | --- |
| `LEGACY_UNBOUND` | `BLOCKED` |
| `LEGACY_PUBLISH_ONLY` | `BLOCKED` |
| `RELEASE_BOUND` | 可继续判断 |

历史 run 不补造 digest，也不能通过本准入路径。

## 3. 输入与结果

输入复用：

- `StrategyReleaseAggregatePrototype`：publish、strategy version、dataset、evaluation、manifest schema、
  digest 与既有 `DRAFT/CANDIDATE/VERIFIED/PUBLISHED/REJECTED/RETIRED` lifecycle；仅 `PUBLISHED` 可继续。
- `ArtifactVerificationResultPrototype`：`VERIFIED/REJECTED/UNKNOWN`、digest、verified size 与 findings。
- 真实 `StrategyValidationDecision`：`APPROVED`、`REJECTED`、`NEEDS_REVIEW`、`BLOCKED`、`NO_EVIDENCE`、
  `STALE_EVIDENCE`；未新增近义枚举。
- `ShadowRunReleaseBindingPrototype` 与 `RequestedShadowBindingPrototype`：请求锚点、窗口、授权边界、
  六项无副作用策略、action/trace。

结果只有 `ADMITTED`、`BLOCKED`、`UNKNOWN`。`BLOCKED` 优先于同时存在的 UNKNOWN，避免已知拒绝被不确定
事实掩盖。两者都没有 Creation Plan。所有结果固定为：

```text
diagnosticOnly=true
noSideEffect=true
notTradingAuthorization=true
liveDisabled=true
shadowRunCreated=false
shadowRunStarted=false
orderSubmitted=false
```

## 4. 准入与 finding 规则

`ADMITTED` 必须同时满足：release=`PUBLISHED`、binding=`RELEASE_BOUND`、三个 publish anchor 一致、
verification=`VERIFIED` 且 digest/size/findings 有效、validation=`APPROVED`、strategy/dataset/evaluation
一致、schema=`strategy-release-manifest.v1`、`windowEnd > windowStart`、boundary 为
`DIAGNOSTIC_ONLY` 或 `REVIEW_ONLY`，并且六项 no-side-effect flag 全为 true。

`REJECTED` verification、非 approved validation、mismatch、非发布 release、非法 schema/window/boundary
或任一 false policy 返回 `BLOCKED`。verification=`UNKNOWN`、`NO_EVIDENCE`、`STALE_EVIDENCE` 或缺少必要
事实返回 `UNKNOWN`。结果分别以 `blockers`、`unknowns`、`warnings` 输出脱敏 finding code；warning 固定包括
`ADMISSION_NOT_TRADING_AUTHORIZATION`。

## 5. Creation Plan 与幂等键

仅 `ADMITTED` 生成 plan，字段为 `publishRecordId`、`artifactDigest`、strategy/dataset/evaluation、窗口、
授权边界、side-effect policy、`shadowRunIdempotencyKey` 与 `traceId`。它不包含独立 release ID、credential、
余额、持仓、真实订单 ID、private endpoint、artifact 内容或绝对路径。

key 使用 UTF-8、固定字段顺序、四字节长度前缀和 SHA-256（64 位小写）。它覆盖 publish/digest、strategy、
dataset、evaluation、窗口、boundary、六项 policy 与 manifest schema；明确排除 `actionId`、`traceId`、
credential 和路径。64 位 key 可映射到现有 `shadow_runs.idempotency_key VARCHAR(160)`。

## 6. 与现有状态机及未来 schema 的边界

Admission 位于 ShadowRunStateMachine 之前：`ADMITTED` 不创建 `CREATED` 状态，也不推进任何状态机。
正式 GateX 需要先完成 forward-only `shadow_runs.artifact_digest VARCHAR(64)` migration，并在原子创建路径
一同写入 `publish_id`、`artifact_digest` 与 idempotency key。当前 production schema 不能执行此 plan。

正式 production 前置条件仍包括 GateW 接受、独立 migration/锁表审查、真实 DB 并发幂等测试、artifact verifier
安全审查、tenant/authorization/API review，以及 release/Shadow 创建事务与审计边界设计。
