# Publish Anchor 与 Artifact Digest 合同

状态：`TEST-ONLY PROTOTYPE / NON-AUTHORITATIVE`（仅测试原型 / 非权威）。

固定边界：

```text
NO PRODUCTION MIGRATION
NO DATABASE EXECUTION
NO SHADOW RUN CREATION
NOT TRADING AUTHORIZATION
DO NOT MERGE INTO DEV BEFORE GATEW ACCEPT
```

## 1. 目标与唯一身份

本合同关闭 `RELEASE_ID_TYPE_CONFLICT`：未来 Strategy Release 不建立独立 UUID 主键或第二个
release ID。唯一 canonical identity 是：

```text
publishRecordId = releaseAnchorId = backtest_publish_records.publish_record_id (VARCHAR(128))
```

`shadow_runs.publish_id` 已是兼容类型并已有外键，因此继续作为 Shadow Run 的唯一发布/release
锚点。`artifact_digest` 只补齐冻结 artifact provenance，不改变发布身份。

## 2. 现有事实与最小 delta

- `backtest_publish_records.publish_record_id` 是 `VARCHAR(128)` 主键；同一 backtest run 唯一。
- `shadow_runs.publish_id` 是 `VARCHAR(128)`，外键指向该 publish record。
- `shadow_runs.idempotency_key` 是唯一创建幂等键；同一 publish/artifact 不构成全局唯一。
- production 中不存在 `strategy_releases` 表；不得新增 `shadow_runs.release_id`、
  `shadow_runs.release_publish_id` 或 `strategy_releases.id UUID`。

正式 GateX 的唯一 schema delta 候选是 nullable `shadow_runs.artifact_digest VARCHAR(64)`，并增加：

```sql
artifact_digest IS NULL OR artifact_digest ~ '^[0-9a-f]{64}$'
artifact_digest IS NULL OR publish_id IS NOT NULL
```

第二个约束只保证有 digest 必有 publish anchor；它刻意允许已有 `publish_id` 但没有 digest 的
legacy 行。

## 3. Binding mode

| Mode | publishId | artifactDigest | 含义 | 可进入未来 admission |
| --- | --- | --- | --- | --- |
| `LEGACY_UNBOUND` | null | null | 历史 run，无发布与工件 provenance | 否 |
| `LEGACY_PUBLISH_ONLY` | 非空 | null | 已有发布锚点，但历史上未记录冻结 digest | 否 |
| `RELEASE_BOUND` | 非空 | 64 位小写 SHA-256 | GateX 完整 release provenance | 是 |

`publishId=null` 且 `artifactDigest` 非空是非法状态。`RELEASE_BOUND` 仅表示 provenance 已完整，
不表示 Shadow Run 已创建/启动、策略可交易、风险已通过或 LIVE 已授权。

## 4. 不可变性与 legacy 兼容

创建 Shadow Run 后，`publish_id`、`artifact_digest` 和派生 binding mode 必须不可修改。不得先创建
legacy run 再补 publish/digest，或在运行后将其重绑定到其他 artifact。

历史数据保持原值：不得从 snapshot checksum、JSON payload、publish 状态或其他表猜测 digest，不做
虚假 backfill。legacy run 仍可只读查询与回放，但必须显示为不完整 provenance，且不得进入新的
Release-to-Shadow admission。

## 5. 索引、外键与幂等

复用既有 `fk_shadow_runs_publish`，删除语义保持 `RESTRICT / NO ACTION`，不得 cascade 删除审计事实。
若正式 GateX 前仍未有索引，候选新增 `(publish_id, created_at DESC)` 支持有界 provenance 查询。

不得增加 `UNIQUE(publish_id, artifact_digest)`：同一冻结 artifact 可以生成多个不同 Shadow Run。
`idempotency_key` 继续是唯一创建去重边界。

## 6. 正式 GateX 顺序与前置条件

1. 扩展 `backtest_publish_records`，落地 release lifecycle 与 manifest 必需事实。
2. 落地 artifact verification facts，确认 canonical digest。
3. 新增 `shadow_runs.artifact_digest` 和两个 nullable-safe CHECK。
4. 核验/保留既有 publish FK，按实际查询需要建立索引。
5. 更新 production domain、runner command 和 JDBC repository，使新 release-bound 创建原子写入
   `publish_id`、`artifact_digest` 与 `idempotency_key`。
6. 之后才可接入 fail-closed Release-to-Shadow admission。

前置条件包括 GateW 已接受、独立 migration/锁表审查、真实数据库兼容测试、并发幂等测试与安全审查。
以上均未在本原型实现。

## 7. 安全边界

本合同不保存 credential、token、账户、余额、订单、私有 endpoint payload、原始 artifact 内容或绝对
路径。所有 binding mode 固定为 `diagnosticOnly=true`、`notTradingAuthorization=true`、
`liveDisabled=true` 的 test-only 语义。
