# Strategy Release Aggregate / Idempotency Prototype

状态：`TEST-ONLY PROTOTYPE / NON-AUTHORITATIVE`（仅测试原型 / 非权威实现）。

固定边界：

```text
NO DATABASE
NO PRODUCTION SERVICE
NOT SHADOW EXECUTION
NOT TRADING AUTHORIZATION
DO NOT MERGE INTO DEV BEFORE GATEW ACCEPT
```

## 1. 目标与非目标

本原型在 `nq-core` test source 内冻结未来 GateX Strategy Release 的最小行为合同：

- 聚合锚点与生命周期；
- `publishId` business identity；
- command action idempotency 与 request fingerprint；
- `expectedVersion` 乐观锁；
- append-only release event；
- artifact verification result 与 release lifecycle 的 fail-closed 衔接；
- credential、Shadow、LIVE 与交易授权禁止语义。

本原型不实现 production repository/service、Spring Bean、API、Flyway/JDBC、真实 artifact IO、
Shadow Session、scheduler、runner、交易所访问或交易写侧。它不修改现有 Publish、Shadow Run 或交易状态机。

## 2. 现有 Publish 与 Strategy Release 的关系

Strategy Release 是对现有 `backtest_publish_records` publish 主链的 `EXTEND`（扩展），不是第二套
publish source of truth。

- `publishId` 对应现有 `publish_record_id`；
- `releaseId` 是 `publishId` / `publish_record_id` 的领域别名，用于 test-only aggregate 表达，
  不是独立 UUID，也不是未来 `strategy_releases` 表主键；
- 现有 publish record 已绑定 backtest、evaluation 与 strategy version facts；
- `backtest_publish_records.publish_record_id` 是主键；
- production publish service 对同一 backtest run 复用既有 publish record；
- release repository 必须按 `publishId` 保持最多一个 Strategy Release。

本原型没有新增 production port、表或 migration。

## 3. 聚合字段与不可变锚点

`StrategyReleaseAggregatePrototype` 保存：

```text
releaseId
publishId
strategyVersionId
datasetId
evaluationId
manifestSchemaVersion
artifactDigest
state
version
createdAt
updatedAt
verifiedAt
publishedAt
retiredAt
```

以下字段在创建后不可变：

```text
releaseId
publishId
strategyVersionId
datasetId
evaluationId
manifestSchemaVersion
artifactDigest
createdAt
```

聚合使用不可变 Java record。状态变化产生新快照；repository 在保存时再次校验全部不可变锚点。

## 4. 生命周期

原型直接复用 `StrategyReleaseLifecyclePrototypeTest` 已定义的 `StrategyReleaseState`，没有创建第二套枚举：

```text
DRAFT
CANDIDATE
VERIFIED
PUBLISHED
REJECTED
RETIRED
```

允许转换：

| From | To |
|---|---|
| `DRAFT` | `CANDIDATE`, `REJECTED` |
| `CANDIDATE` | `VERIFIED`, `REJECTED` |
| `VERIFIED` | `PUBLISHED`, `REJECTED` |
| `PUBLISHED` | `RETIRED` |

`REJECTED` 与 `RETIRED` 是终态，不得恢复。未声明转换一律 fail-closed。

语义边界：

- `VERIFIED` 只表示不可变 verification result 满足完整性 gate，不表示交易批准；
- `PUBLISHED` 表示 release 工件已冻结并可作为未来 Shadow 候选；
- `PUBLISHED` 不启动 Shadow Run，不表示 LIVE ready 或交易授权；
- 状态机不包含 `SHADOW_ACTIVE`、`LIVE_ACTIVE`、`APPROVED_FOR_TRADING`、
  `READY_TO_TRADE` 或 `EXECUTING`。

## 5. Artifact verification gate

Service 只消费不可变 `ArtifactVerificationResultPrototype`：

```text
status
artifactDigest
verifiedSizeBytes
findingCodes
```

Service 不读取文件、不解析绝对路径、不重新计算摘要。进入 `VERIFIED` 必须同时满足：

1. release 当前为 `CANDIDATE`；
2. manifest schema 为 `strategy-release-manifest.v1`；
3. verification status 为 `VERIFIED`；
4. verification digest 与聚合 `artifactDigest` 完全一致；
5. `verifiedSizeBytes > 0`；
6. `findingCodes` 不含任何现有 verifier finding。

`UNKNOWN`、`REJECTED`、digest mismatch、unsupported schema、空/非法 digest、无效 size 或任一
blocking finding 均 fail-closed。verifier 的 `VERIFIED` 绝不映射为 `tradingAuthorized`、
`liveReady` 或 `shadowStarted`。

## 6. Business identity

Business identity 为现有 publish anchor：`publishId`。

- 同一 `publishId` 与完全相同不可变锚点重复创建：返回已有 release，不新增聚合、事件或 version；
- 同一 `publishId` 绑定不同锚点：`BUSINESS_IDENTITY_CONFLICT`；
- 同一 `releaseId` 绑定不同 publish anchor：`RELEASE_ID_CONFLICT`。

该决定来自现有 publish contract 与 production schema/service，不是本轮临时发明的唯一键。

## 7. Command idempotency

所有 command 包含：

```text
actionId
releaseId 或 publishId
expectedVersion
```

Repository 维护 repository-instance scoped 的全局 `actionId` receipt：

- 相同 `actionId` + 相同 `releaseId` + 相同 fingerprint：在 version 校验前返回首次 result 对象；
- replay 不写第二个 release、不追加第二个 event、不增加 version；
- 相同 `actionId` + 不同 fingerprint：`IDEMPOTENCY_CONFLICT`；
- `actionId` 跨 release 复用：`IDEMPOTENCY_CONFLICT`；
- 不同 `actionId` 对已达到的相同目标状态执行相同 payload：返回当前成功快照，不追加 event、不增加 version；
- 不同 `actionId` 对已达到的相同目标状态提交不同 payload：`STATE_PAYLOAD_CONFLICT`。

成功结果、version/业务冲突和非法请求结果均可作为首次 receipt 稳定重放。对已绑定 action 的
idempotency conflict 不覆盖首次 receipt。

## 8. Request fingerprint

Fingerprint 使用 UTF-8、长度前缀字段 canonicalization 和 lowercase SHA-256。

创建 fingerprint 覆盖：

```text
command type
releaseId
publishId
strategyVersionId
datasetId
evaluationId
manifestSchemaVersion
artifactDigest
expectedVersion
```

状态 command fingerprint 覆盖 target state、`releaseId`、`expectedVersion`；verification command
还覆盖 status、digest、verified size 和排序后的 finding codes。

Fingerprint 不保存 credential、原始 artifact、路径或私有 request/response。

## 9. Optimistic version

- 创建使用 `expectedVersion = 0`，初始聚合 version 为 `0`；
- 每个成功状态变化要求 `current.version == expectedVersion`；
- 成功后 `version = expectedVersion + 1`；
- version mismatch 返回 `VERSION_CONFLICT`；
- 不 silently overwrite，不自动重试，不修改聚合，不追加 release event；
- 已成功 action 的 replay 先于 version 校验，因此后续 version 变化不影响首次结果重放。

Repository 的 `saveWithExpectedVersion` 再次校验当前 version、不可变锚点和严格单次递增。
Repository 还为每个 `releaseId + completed state` 保存首次成功 payload fingerprint，用于区分
“相同状态幂等成功”和“相同状态、不同语义”。

## 10. Append-only events

成功事件：

```text
RELEASE_CREATED
RELEASE_MARKED_CANDIDATE
ARTIFACT_VERIFIED
RELEASE_PUBLISHED
RELEASE_REJECTED
RELEASE_RETIRED
```

每个 event 保存：

```text
eventId
releaseId
actionId
eventType
fromStatus
toStatus
artifactDigest
versionBefore
versionAfter
occurredAt
```

创建事件以 `DRAFT -> DRAFT`、`version -1 -> 0` 表达首次快照。事件按 repository 内追加顺序返回，
使用 `List.copyOf` 提供不可变快照。

审计选择：

- 非法生命周期转换追加脱敏 `ILLEGAL_TRANSITION_REJECTED`，version 不变；
- artifact verification gate 拒绝追加脱敏 `ARTIFACT_VERIFICATION_REJECTED`，version 不变；
- version conflict 不追加 event，因为未获得预期聚合版本；
- idempotency/business identity conflict 不追加 event，因为 action 或 business identity 已存在冲突；
- 所有冲突结果都不伪装成成功状态事件。

该选择与既有 lifecycle prototype 对非法转换留审计事实、Shadow optimistic conflict 在更新前失败的风格一致。

## 11. Conflict taxonomy

| Code | 含义 |
|---|---|
| `IDEMPOTENCY_CONFLICT` | action 已绑定不同 release 或 fingerprint |
| `BUSINESS_IDENTITY_CONFLICT` | publish anchor 已绑定不同 release anchors |
| `RELEASE_ID_CONFLICT` | release ID 已绑定不同 publish anchor |
| `VERSION_CONFLICT` | expected version 与 current version 不一致 |
| `STATE_PAYLOAD_CONFLICT` | 已完成目标状态与新 command payload 语义不同 |
| `RELEASE_NOT_FOUND` | release 不存在 |
| `RELEASE_NOT_VERIFIED` | 未验证即请求 publish |
| `RELEASE_TERMINAL_STATE_LOCKED` | 终态恢复请求 |
| `RELEASE_ILLEGAL_STATE_TRANSITION` | 其他未声明转换 |
| `VERIFICATION_UNKNOWN` | verifier 无法安全给出结论 |
| `VERIFICATION_REJECTED` | verifier 明确拒绝 |
| `ARTIFACT_DIGEST_MISMATCH` | verified digest 与 manifest digest 不一致 |
| `UNSUPPORTED_MANIFEST_SCHEMA` | schema 未列入 allowlist |
| `VERIFIED_SIZE_INVALID` | verified size 非正数 |
| `BLOCKING_VERIFICATION_FINDING` | verification findings 含阻断项 |

## 12. 敏感字段边界

聚合、receipt 和 event 禁止保存：

```text
credential
apiKey
secret
passphrase
token
privateKey
raw artifact content
absolute file path
private request/response
真实账户、订单、余额
```

所有 result 固定：

```text
diagnosticOnly = true
notTradingAuthorization = true
liveDisabled = true
```

## 13. 与 Shadow Run 的边界

Strategy Release 与现有 Shadow Run 是两套独立生命周期。本原型：

- 不创建或修改 Shadow Run；
- 不创建 Shadow Session；
- 不启动 runner/scheduler；
- 不访问 credential 或 private endpoint；
- 不写账户、订单、余额、ledger；
- 不把 `PUBLISHED` 转换为 Shadow started 或 LIVE authorized。

## 14. 正式 GateX repository/service 前置条件

1. GateW 已接受，current authority 明确允许 GateX 开始；
2. 独立审查 production port、transaction boundary、tenant/authorization 与 API contract；
3. 完成 migration、唯一约束、online DDL、历史行语义、回滚和 retention 审查；
4. production repository 以数据库唯一约束和 conditional update 保证并发幂等/乐观锁；
5. action receipt、非法尝试审计和 release event 的持久化边界原子化；
6. verifier 完成 Windows reparse/junction、trusted root provisioning 和稳定句柄安全审查；
7. 增加并发、事务回滚、故障注入和真实数据库集成测试；
8. 保持 PAPER/LIVE 隔离以及 `diagnosticOnly`、`notTradingAuthorization`、`liveDisabled` 固定边界。

在以上条件满足前：

```text
NO PRODUCTION INTEGRATION
NO DEV MERGE
NO SHADOW EXECUTION
LIVE REMAINS DISABLED
```
