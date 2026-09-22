# GateAUDIT Phase7-B Historical Projection Baseline Verification

2026-09-22（Asia/Shanghai）。结论：**PASS / PHASE7_B_HISTORICAL_PROJECTION_BASELINE_VERIFIED / POSITION_PROJECTION_MATCH / LATEST_ACCOUNT_SNAPSHOT_MATCH / HISTORICAL_PROJECTION_REPAIR_NOT_REQUIRED / HISTORICAL_PROJECTION_OBLIGATION_CLOSED / MANDATORY_CLOSURE_0 / OTHER_RESIDUALS_UNCHANGED / P0_0 / P1_0 / READY_FOR_DELIVERY**。

本文固化 Phase7-B 的脱敏 baseline verification。它关闭的是历史投影核对义务，不执行数据修复，不改写 Phase7-A inventory 或历史失败证据，不创建 archive/freeze/tag，也不授权生产、LIVE、真实 provider 或资金操作。immutable dump 与 raw oracle output 保持 Git ignored / not tracked；本文不包含原始生产 row、业务 ID 或 credential。

## 1. Candidate 与 source identity

| Field | Value |
| --- | --- |
| Verification candidate HEAD | `9af8d16782d8ad5ae97d115f7e1e47a9ba2688c2` |
| Branch / upstream at entry | `audit/post-gatey-agent-baseline` / `origin/audit/post-gatey-agent-baseline` |
| Historical release | `8e3dd0cf6104eb85f36a0e434ca51ea9d903705a` |
| Source PostgreSQL / Flyway | `16.14 / V46 / success 46 / failure 0` |
| Immutable source directory | `backend/nq-infra/target/phase7-b-historical-source/20260922T144253Z/` |
| Selected source tables | `flyway_schema_history / orders / trades / ledger_entries / positions / account_snapshots` |

Artifact integrity 在任何 PG16 restore 前重新计算，并在独立审查结束时再次核对：

| Artifact | Bytes | SHA-256 | Result |
| --- | ---: | --- | --- |
| `production-v46-schema.dump` | 739945 | `66d35ac81dd6eb189be050131cd21d0af5170c3a1ba759ba2f8cadf88de94af0` | MATCH / read-only |
| `production-v46-selected-data.dump` | 6895 | `28e27025a9b7e74b5b223d2c39f9a3cb766d0bf0e5a8b95c80573c1fef6e8e49` | MATCH / read-only |
| `source-manifest.json` | 3325 | `9e0e360fcd9546fbbe3dd5600b3fbde044904e51ac1e15724dc0c2a9cc8e6681` | MATCH / read-only |

```text
SOURCE_ARTIFACT_SHA_VALID=true
SOURCE_DUMP_SHA_VALID=true
SOURCE_SCHEMA_SHA_VALID=true
```

## 2. PostgreSQL 16 offline restore

正式比较使用全新 disposable PostgreSQL 16 container；没有应用 runtime、外部 writer 或网络访问。恢复顺序为 schema dump 后 selected-data dump，未执行 Flyway migrate/repair 或任何 projection writer。

| Field | Result |
| --- | --- |
| PostgreSQL | `16.15 (Debian 16.15-1.pgdg13+2)` |
| Image identity | `postgres@sha256:a3b7f434b2dc57ce85a67e171163eb8ab1a1ebcb39d27484661f26b1dfbe30d6` |
| Container network | `none` |
| Database | disposable `phase7b_verify_v2` |
| Transaction mode | `REPEATABLE READ / READ ONLY` |
| Flyway | `V46 / success 46 / failure 0` |
| Row counts | `46 / 3 / 1 / 4 / 1 / 2` |
| Count order | `flyway / orders / trades / ledger / positions / snapshots` |
| Acquisition count match | `true` |
| Teardown | container stopped and removed |

```text
PG16_OFFLINE_RESTORE=true
FLYWAY_V46=true
ROW_COUNTS_MATCH_ACQUISITION=true
```

## 3. Independent source-derived oracle

Oracle 在 target read 前冻结；source-only phase 仅读取 `orders / trades / ledger_entries`，写出并关闭 source-only artifact 后，才读取 `positions / account_snapshots` 做 comparison。它不调用 production `TradeLedgerPostingService.postTrade(...)`，也不以 target 值反推 expected。

| Field | Value |
| --- | --- |
| Algorithm version | `phase7b-historical-projection-oracle-v2` |
| Oracle SHA-256 | `f7b24939dc2eaa0885a741d307893858b7cdddf6ba98e9afb55ea66975678869` |
| Exact arithmetic | Python `Decimal`; float/double 未使用 |
| NumericPolicy | scale=`8`; PRICE/QTY=`DOWN`; AMOUNT/FEE=`HALF_UP` |
| Historical/current compatibility | Position 与 NumericPolicy 对本次 single-Trade source 兼容 |
| Comparison keys | Position=`account_id + symbol`; Snapshot=`account_id + currency` |
| Latest Snapshot authority | `PARTITION BY account_id,currency ORDER BY snapshot_id DESC` |
| Source identity ambiguity | `0` |
| Projection application-order ambiguity | `0` |
| Target independent | `true` |

Position 由 executed Trade qty、Order side、base-currency fee 与 exact decimal 计算；`available_qty=qty`、`frozen_qty=0`。BUY 使用既有 weighted average 语义，SELL 在正持仓时保持均价、非正持仓时归零。Snapshot 对已有 Position-based asset 使用 source-derived Position 聚合，其余 currency 使用 Ledger delta 聚合。

Source integrity 独立检查结果：

```text
duplicate order/trade/exchange identity/idempotency groups = 0/0/0/0
null exchange_trade_id / missing order = 0/0
trade account mismatch / symbol mismatch = 0/0
orphan/non-TRADE ledger = 0/0
ledger account mismatch / invalid direction = 0/0
expected trade ledger rows / actual rows = 4/4
ledger missing / extra / value mismatch = 0/0/0
SOURCE_IDENTITY_AMBIGUITY=0
```

唯一 `Trade.trade_env` historical residual row count=`1`。该字段没有参与 Position/Snapshot oracle 输入，Order/Trade/Ledger 关联及经济值完整，因此准确处置为：

```text
Trade.trade_env residual = NOT_A_PROJECTION_MISMATCH
```

## 4. Source-only expected digests

脱敏 source-only output 只保留 count、canonical aggregate digest 与逐行 key/value digest，不保存原始生产 row 或 ID。

| Projection | Expected rows | Expected SHA-256 |
| --- | ---: | --- |
| Position | 1 | `0455c07d184aa0c12697bf89d185808998680bf6fb52907e228542192be22c1b` |
| latest Account Snapshot | 2 | `cf909919fbbc607f261417c8c3bc855ac0d33934e06dab0d5c0f5c82b136e0ea` |

## 5. Exact comparison

```text
RESULT=MATCH

POSITION_EXPECTED_ROWS=1
POSITION_ACTUAL_ROWS=1
POSITION_MISSING=0
POSITION_EXTRA=0
POSITION_QTY_MISMATCH=0
POSITION_AVAILABLE_MISMATCH=0
POSITION_FROZEN_MISMATCH=0
POSITION_AVG_PRICE_MISMATCH=0

SNAPSHOT_EXPECTED_LATEST_ROWS=2
SNAPSHOT_ACTUAL_LATEST_ROWS=2
SNAPSHOT_MISSING=0
SNAPSHOT_EXTRA=0
SNAPSHOT_BALANCE_MISMATCH=0
SNAPSHOT_AVAILABLE_MISMATCH=0
SNAPSHOT_FROZEN_MISMATCH=0
LATEST_ORDERING_MISMATCH=0
TS_VS_SNAPSHOT_ID_ORDER_DIVERGENCE=0
```

因此没有触发 repair path。不存在 `UPDATE / INSERT / DELETE / TRUNCATE / replay / projection rebuild / manual correction / Flyway`。

## 6. Invalid attempt preserved

`20260922T150147Z` v1 attempt 原样保留并明确为无效：`PGOPTIONS` 中带空格的 isolation 参数被错误拆分，连接初始化失败，source rows read=`0`、target rows read=`0`、result=`NONE`。v2 使用显式 `BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY` 后从头执行。

v1 不是 MATCH、PASS 或部分 comparison，不能被后续成功追认；本任务未删除或覆盖该失败证据。

## 7. Independent REVIEW_ONLY verification

独立审查者在不修改 oracle、comparison、source dump、repository 或 index 的条件下，重新计算 source/formal artifact hashes、source integrity SQL、两个 expected digest 和全部 comparison count；审查前后 index fingerprint 相同，stage=`0`，reviewer file mutations=`0`。

```text
REVIEW_RESULT=MATCH_ACCEPTED
P0=0
P1=0
P2=0
DISPOSITION_ALLOWED=CLOSED_BY_BASELINE_VERIFICATION / REPAIR_NOT_REQUIRED
```

独立审查同时确认：PG16 restore identity 与 6 表 count 匹配 acquisition manifest；oracle source-only/target-read 边界成立；Decimal/NumericPolicy 与 single-Trade historical semantics 兼容；`Trade.trade_env` residual 不构成 projection mismatch。

## 8. Mandatory residual closure

Phase7-A canonical inventory 的 17 行保持原顺序与 identity。只改变第一行的 Phase7 disposition；其余 16 行不重分类、不删除、不伪装 CLOSED。

| Residual / obligation | Phase7-B final disposition | Change |
| --- | --- | --- |
| `HISTORICAL_PROJECTION_REPAIR_REQUIRED` | `CLOSED_BY_BASELINE_VERIFICATION / REPAIR_NOT_REQUIRED` | mandatory obligation closed |
| P5-F005 platform attestation | `DEFERRED_NON_BLOCKING` | unchanged |
| ordinary concurrent INSERT loser | `NON_BLOCKING_RESIDUAL` | unchanged |
| wildcard-import residual | `NON_BLOCKING_RESIDUAL` | unchanged |
| GateY `Order.externalOrderId=NULL` | `NON_BLOCKING_RESIDUAL` | unchanged |
| pre-B0 F3 restore proof identity | `NON_BLOCKING_RESIDUAL` | unchanged |
| pre-B0 F4 SBOM array shape | `NON_BLOCKING_RESIDUAL` | unchanged |
| pre-B0 F5 Java shadow classification | `NON_BLOCKING_RESIDUAL` | unchanged |
| pre-B0 F6 manual seed SQL scope | `NON_BLOCKING_RESIDUAL` | unchanged |
| typed compatibility rows / P1-1 / PB1 | `RETIRED` | unchanged |
| intent-worker rows / PB2 | `DORMANT` | unchanged |
| 14 historical inactive scenarios | `FUTURE_OBLIGATION` | unchanged |
| scheduler/lease/leader、Venue restart、超出 envelope 的规模/长期/扩展故障 | `FUTURE_OBLIGATION` | unchanged |
| historical FAIL/BLOCKED/remediation；5421ms cause=`UNKNOWN` | `HISTORICAL_ONLY` | unchanged |
| legacy Phase3 IDs F-005/F-011 | `RETIRED` | unchanged |
| AntD deprecation warning | `OBSERVATION` | unchanged |
| JS bundle-size warning | `OBSERVATION` | unchanged |

```text
RESIDUAL_ROWS_BEFORE=17
MANDATORY_BEFORE=1
RESIDUAL_ROWS_AFTER=17
MANDATORY_AFTER=0
OTHER_RESIDUAL_RECLASSIFICATIONS=0
```

Phase7-A inventory 是 immutable historical evidence，本文不就地改写其当时的 OPEN/MUST_CLOSE 事实。Phase7-B final disposition 由本文和随后 current authority synchronization 记录。

## 9. Safety、scope 与 delivery state

```text
PRODUCTION_CONNECTIONS=0
PRODUCTION_MUTATIONS=0
REMOTE_BACKUP_ACCESS=0
EXCHANGE_CALLS=0
LIVE_ACTIONS=0
CREDENTIAL_READS=0
SOURCE_ARTIFACT_MUTATIONS=0
APPLICATION_RUNTIME_STARTS=0
```

Full Maven、frontend E2E、L6、load/stress/soak、真实 exchange 与生产部署均 `NOT_RUN / NOT_REQUIRED`：本批是离线历史投影验证与 docs evidence，业务 runtime delta=`0`、migration delta=`0`。

本 evidence 在 commit 前状态为 `READY_FOR_DELIVERY`；不得预填不存在的 commit、CI run 或 `CI_GREEN`。只有本文件的 exact-head CI 成功后，才允许在独立 authority synchronization commit 中把 Phase7-B 写为 `ACCEPTED / CI_GREEN`，并将唯一 next action 推进为 `NQ-GATEAUDIT-PHASE7-C-READINESS-REPOSITORY-AUDIT`。该 Phase7-C action 必须保持 `type=AUDIT / compatible=true`。
