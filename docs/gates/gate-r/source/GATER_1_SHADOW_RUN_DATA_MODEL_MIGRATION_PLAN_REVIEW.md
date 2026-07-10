# NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW

最终状态：`PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。

本文是 GateR-1 planning/review-only 文档，只审查 Shadow Run 数据模型、状态机、候选表结构、索引、约束、JSONB 脱敏、回滚策略和后续 migration 实施方案。不新增 migration，不修改历史 migration，不改 Java，不新增 API，不新增前端页面，不新增测试，不启动 Shadow runner。

## 1. Review Decision

结论：`PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。

- GateR-2 可以进入 Shadow Run local fact model / repository implementation 的准备阶段，但只能在另起 implementation 任务后执行。
- GateR-2 不得直接创建真实交易路径，不得写真实账户、资金、订单、ledger，不得调用 private endpoint，不得读取 credential material。
- 本轮建议采用 4 表最小模型：`shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`。
- `API.md` 与 `DB_SCHEMA.md` 本轮不更新为当前事实；只有后续 migration 真正落地并验证通过后，`DB_SCHEMA.md` 才能记录为当前 schema。

## 2. Current Baseline

- 当前分支：`dev`。
- 本地 `HEAD` 与 `origin/dev` 对齐：`175e2e00bd68a6240c6d2c8633c9ff0c3d5cddcc`。
- 最新 CI：GitHub Actions `NQ CI Baseline` run `28771006007`，`status=completed`（已完成），`conclusion=success`（成功），headSha 为当前 HEAD。
- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateR-0：`PLAN READY / NOT IMPLEMENTED`（计划已就绪 / 未实现）。
- GateR 当前阶段：Shadow Run 运行化与 Paper / Shadow 一致性评估规划。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient、real provider、private trading adapter、real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow Run / Shadow runner / Shadow run tables：`NOT IMPLEMENTED`（未实现）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。

## 3. Existing Schema Inventory

只读盘点 `backend/nq-infra/src/main/resources/db/migration/**` 后，当前 Flyway migration 版本从 `V1` 到 `V31`。未发现 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` 或其他 `shadow_*` 表已落地。

相关已落地表域：

| 表域 | 现有表 | 复用方式 |
| --- | --- | --- |
| Strategy version | `strategy_versions` | Shadow Run 必须引用，但不复用为 Shadow 主状态表。 |
| Dataset | `marketdata_datasets`、`marketdata_dataset_coverage` | Shadow Run 必须引用 dataset 和质量事实；输入快照仍需另存 checksum / source / capturedAt。 |
| Evaluation | `backtest_eval_reports` | Shadow Run 可引用评估报告；不改写评估状态。 |
| Publish | `backtest_publish_records` | Shadow Run 可引用发布 trace；不把 publish 写成 live enable。 |
| Paper | `paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`、`paper_trading_positions` | Paper run 是 comparison 输入；Shadow 不复用 Paper 表存储自身状态。 |
| Paper monitor | `paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events`、`paper_run_daily_reports`、`paper_run_alerts`、`paper_run_recovery_events`、`paper_run_stability_checks` | 可作为对照证据来源；不得被 Shadow 写入或改写。 |
| Audit / event | `audit_logs`、`event_store`、`credential_audit_logs` | 已有跨域审计能力，但 Shadow Run 仍建议独立 append-only event 表，避免把 Shadow 状态机塞进通用审计 payload。 |
| Trading / account / ledger | `orders`、`trades`、`positions`、`account_snapshots`、`ledger_entries`、`ledger_events` | 禁止作为 Shadow Run 写入目标。 |

## 4. Existing Related Tables

现有 Paper / strategy / dataset / evaluation / publish 表必须作为只读输入事实源：

- `strategy_versions.status` 当前允许 `DRAFT / ACTIVE / ARCHIVED`，GateQ read model 只接受 `ACTIVE`。
- `marketdata_datasets.status` 当前允许 `CREATED / READY / INVALID / ARCHIVED`，质量字段 `quality_status` 允许 `OK / GAP_DETECTED / INCOMPLETE / INVALID`。
- `backtest_eval_reports.evaluation_status` 当前以 `SUCCEEDED / FAILED` 为主要口径。
- `backtest_publish_records.publish_status` 当前以 `SUCCEEDED / FAILED` 为主要口径。
- `paper_trading_runs.status` 当前允许 `CREATED / RUNNING / STOPPED / FAILED`，`trade_env` 必须限定 `SIM` 才能进入 Shadow comparison。
- `trade_replay_records` 已保存 Paper run 决策、risk、market snapshot JSONB，但语义属于 Paper 复盘，不应复用为 Shadow decision trace 主表。

当前 `JdbcPaperShadowComparisonFactRepository` 只读查询 `strategy_versions`、`marketdata_datasets`、`backtest_eval_reports`、`backtest_publish_records`、`paper_trading_runs`，生产路径固定返回 Shadow fact `NOT_IMPLEMENTED`。这证明后续若要持久化 Shadow fact，需要新增独立本地 fact 表，而不是误写现有 Paper 表。

## 5. Shadow Run Data Model Recommendation

Shadow Run 需要新表。

原因：

- Shadow Run 有独立状态机、事件审计、快照和 consistency report 生命周期；现有 Paper 表只表达 SIM/Paper 运行事实。
- 复用 Paper 表会混淆 Paper execution 与 Shadow no-side-effect replay，容易被误解为 Paper 订单、Paper 风控或 Paper equity 已授权真实交易。
- 复用 `event_store` / `audit_logs` 作为主事实会把查询结构压进 JSONB，不利于状态机约束、非法流转保护和索引。
- 独立表可以用结构化外键连接 `strategy_versions`、`marketdata_datasets`、`backtest_eval_reports`、`backtest_publish_records`、`paper_trading_runs`，同时用 CHECK / UNIQUE / version 字段保护状态和幂等。

推荐采用 4 表模型，不采用 GateR-0 的 5 表拆法：

- 将 `shadow_input_snapshots` 与 `shadow_decision_traces` 合并为 `shadow_run_snapshots`，通过 `snapshot_type` 区分 `INPUT_MARKETDATA`、`STRATEGY_DECISION`、`RISK_PREFLIGHT`、`ORDER_INTENT_PREVIEW`。
- 合并后仍保留结构化 `sequence_no`、`captured_at`、`checksum`、`source`、`schema_version`，足够支撑 GateR-2 最小 repository 与 GateR-5 consistency report。
- 如果 GateR 后期出现高频 trace 量级或 detail replay 性能瓶颈，可在后续单独 migration 中拆出 `shadow_decision_traces`，当前不提前扩表。

## 6. Candidate Tables

| 表 | 目的 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| `shadow_runs` | Shadow Run 主表 | 必须 | 保存 run identity、输入引用、状态、no-side-effect policy、追踪字段和乐观锁。 |
| `shadow_run_events` | append-only 事件表 | 必须 | 保存状态流转、阻断、失败、非法流转尝试和审计事件。 |
| `shadow_run_snapshots` | 快照表 | 必须 | 保存输入行情、策略决策、风险预检、订单意图预览四类脱敏快照。 |
| `shadow_consistency_reports` | Paper vs Shadow 一致性报告表 | 必须 | 保存 comparisonStatus、metricDelta、divergenceReasons、limitations 和生成时间。 |

不建议首批新增表：

- 不新增 `shadow_orders`：Shadow Run 不能生成真实订单，订单意图应保存在 `shadow_run_snapshots(snapshot_type='ORDER_INTENT_PREVIEW')`。
- 不新增 `shadow_positions` 或 `shadow_balances`：会误导为真实持仓或账户余额。
- 不新增 `shadow_ledger_entries`：Shadow Run 禁止 ledger mutation。
- 不新增 `shadow_replay_jobs`：GateR-2 首批 repository 不需要 replay job；后续如实现 replay 可另起任务。

## 7. Candidate Columns

### `shadow_runs`

| 字段 | 类型建议 | 结构化原因 |
| --- | --- | --- |
| `shadow_run_id` | `VARCHAR(64)` PK | 业务主键，建议格式 `shr-<uuid>`。 |
| `strategy_version_id` | `VARCHAR(64)` NOT NULL FK | 必须结构化引用 `strategy_versions`。 |
| `dataset_id` | `UUID` NOT NULL FK | 必须结构化引用 `marketdata_datasets`。 |
| `evaluation_id` | `VARCHAR(64)` NULL FK | 可引用 `backtest_eval_reports.eval_report_id`。 |
| `publish_id` | `VARCHAR(64)` NULL FK | 可引用 `backtest_publish_records.publish_record_id`。 |
| `paper_run_id` | `VARCHAR(64)` NULL FK | 可引用 `paper_trading_runs.paper_run_id`，用于 consistency report。 |
| `status` | `VARCHAR(32)` NOT NULL | 状态机核心字段，必须 CHECK。 |
| `run_window_start` / `run_window_end` | `TIMESTAMPTZ` NOT NULL | 输入窗口必须可索引、可校验。 |
| `side_effect_policy` | `VARCHAR(64)` NOT NULL | 建议固定 `NO_SIDE_EFFECT_LOCAL_ONLY`，不放 JSONB。 |
| `no_order_submission` | `BOOLEAN` NOT NULL DEFAULT TRUE | 边界字段，必须结构化。 |
| `no_credential_access` | `BOOLEAN` NOT NULL DEFAULT TRUE | 边界字段，必须结构化。 |
| `no_private_endpoint` | `BOOLEAN` NOT NULL DEFAULT TRUE | 边界字段，必须结构化。 |
| `no_ledger_mutation` | `BOOLEAN` NOT NULL DEFAULT TRUE | 边界字段，必须结构化。 |
| `no_account_mutation` | `BOOLEAN` NOT NULL DEFAULT TRUE | 边界字段，必须结构化。 |
| `no_external_private_io` | `BOOLEAN` NOT NULL DEFAULT TRUE | 边界字段，必须结构化。 |
| `authorization_boundary` | `VARCHAR(64)` NOT NULL | 建议固定 `DIAGNOSTIC_ONLY` / `REPLAY_ONLY` / `REVIEW_ONLY`。 |
| `diagnostic_only` / `replay_only` / `review_only` | `BOOLEAN` NOT NULL DEFAULT TRUE | 边界字段，便于 UI 明确展示。 |
| `request_id` | `VARCHAR(128)` NOT NULL | 创建请求幂等与审计。 |
| `idempotency_key` | `VARCHAR(160)` NOT NULL | 创建幂等唯一键。 |
| `trace_id` | `VARCHAR(128)` NOT NULL | 日志、事件、快照和 report 追踪。 |
| `blockers_json` | `JSONB` NOT NULL DEFAULT `'[]'::jsonb` | 阻断列表可 JSONB，但不得存敏感材料。 |
| `warnings_json` | `JSONB` NOT NULL DEFAULT `'[]'::jsonb` | 警告列表可 JSONB。 |
| `next_steps_json` | `JSONB` NOT NULL DEFAULT `'[]'::jsonb` | 后续动作说明可 JSONB。 |
| `created_by` / `updated_by` | `VARCHAR(128)` NULL | 内部用户或系统主体，不保存凭证。 |
| `created_at` / `updated_at` | `TIMESTAMPTZ` NOT NULL | 审计时间。 |
| `prechecked_at` / `started_at` / `finished_at` | `TIMESTAMPTZ` NULL | 状态时间点。 |
| `failure_code` / `failure_message` | `VARCHAR(128)` / `TEXT` NULL | 失败分类和脱敏摘要。 |
| `version` | `BIGINT` NOT NULL DEFAULT 0 | 乐观锁，防止并发覆盖和终态回写。 |

### `shadow_run_events`

| 字段 | 类型建议 | 结构化原因 |
| --- | --- | --- |
| `event_id` | `VARCHAR(64)` PK | 业务主键，建议格式 `she-<uuid>`。 |
| `shadow_run_id` | `VARCHAR(64)` NOT NULL FK | 归属 run。 |
| `event_type` | `VARCHAR(48)` NOT NULL | CHECK：状态流转、阻断、失败、非法流转尝试等。 |
| `from_status` / `to_status` | `VARCHAR(32)` NULL | 记录状态变更前后值。 |
| `reason_code` | `VARCHAR(128)` NULL | 结构化原因。 |
| `message` | `TEXT` NULL | 脱敏摘要，不存 raw payload。 |
| `metadata_json` | `JSONB` NOT NULL DEFAULT `'{}'::jsonb` | 只保存脱敏上下文。 |
| `request_id` | `VARCHAR(128)` NULL | 事件来源请求。 |
| `trace_id` | `VARCHAR(128)` NOT NULL | 追踪。 |
| `created_by` | `VARCHAR(128)` NULL | 事件主体。 |
| `created_at` | `TIMESTAMPTZ` NOT NULL | append-only 时间。 |

### `shadow_run_snapshots`

| 字段 | 类型建议 | 结构化原因 |
| --- | --- | --- |
| `snapshot_id` | `VARCHAR(64)` PK | 业务主键，建议格式 `shs-<uuid>`。 |
| `shadow_run_id` | `VARCHAR(64)` NOT NULL FK | 归属 run。 |
| `snapshot_type` | `VARCHAR(48)` NOT NULL | CHECK：`INPUT_MARKETDATA` / `STRATEGY_DECISION` / `RISK_PREFLIGHT` / `ORDER_INTENT_PREVIEW`。 |
| `sequence_no` | `BIGINT` NOT NULL DEFAULT 0 | 同 run 内顺序，便于 replay。 |
| `source` | `VARCHAR(128)` NOT NULL | 本地事实来源，例如 dataset、paper、strategy。 |
| `captured_at` | `TIMESTAMPTZ` NOT NULL | 快照事实时间。 |
| `schema_version` | `VARCHAR(32)` NOT NULL | 快照结构版本。 |
| `checksum` | `VARCHAR(128)` NOT NULL | 快照内容校验，不存 raw private payload。 |
| `snapshot_json` | `JSONB` NOT NULL DEFAULT `'{}'::jsonb` | 脱敏本地快照。 |
| `trace_id` | `VARCHAR(128)` NOT NULL | 追踪。 |
| `created_at` | `TIMESTAMPTZ` NOT NULL | 写入时间。 |

### `shadow_consistency_reports`

| 字段 | 类型建议 | 结构化原因 |
| --- | --- | --- |
| `report_id` | `VARCHAR(64)` PK | 业务主键，建议格式 `scr-<uuid>`。 |
| `shadow_run_id` | `VARCHAR(64)` NOT NULL FK | 归属 Shadow run。 |
| `paper_run_id` | `VARCHAR(64)` NOT NULL FK | 对照 Paper run。 |
| `strategy_version_id` | `VARCHAR(64)` NOT NULL FK | 便于按策略版本查询。 |
| `dataset_id` | `UUID` NOT NULL FK | 便于按数据集查询。 |
| `evaluation_id` / `publish_id` | `VARCHAR(64)` NULL FK | 追溯评估与发布。 |
| `comparison_status` | `VARCHAR(48)` NOT NULL | CHECK，不能含授权语义。 |
| `metric_delta_json` | `JSONB` NOT NULL DEFAULT `'{}'::jsonb` | 指标差异结构可扩展。 |
| `divergence_reasons_json` | `JSONB` NOT NULL DEFAULT `'[]'::jsonb` | 偏离原因列表。 |
| `limitations_json` | `JSONB` NOT NULL DEFAULT `'[]'::jsonb` | 不可比、缺失、样本限制。 |
| `generated_at` | `TIMESTAMPTZ` NOT NULL | 报告生成时间。 |
| `generated_by` | `VARCHAR(128)` NULL | 内部主体。 |
| `report_version` | `INTEGER` NOT NULL DEFAULT 1 | 支持再生成，但不覆盖历史。 |
| `request_id` / `trace_id` | `VARCHAR(128)` NOT NULL | 幂等与追踪。 |
| `created_at` | `TIMESTAMPTZ` NOT NULL | 写入时间。 |

## 8. Candidate Enums

### Shadow Run status

- `CREATED`（已创建）：本地 Shadow Run 请求已记录，未预检。
- `PRECHECKING`（预检中）：正在执行只读预检。
- `READY`（就绪）：预检通过，可进入无副作用运行。
- `RUNNING`（运行中）：本地无副作用运行中。
- `STOP_REQUESTED`（停止请求中）：停止已请求，等待本地 runner 收口。
- `STOPPED`（已停止）：已停止，终态。
- `COMPLETED`（已完成）：已完成，终态。
- `BLOCKED`（已阻断）：预检或边界阻断，终态。
- `FAILED`（失败）：运行失败，终态。
- `CANCELLED`（已取消）：启动前取消，终态。

### Event type

建议首批 CHECK：

```text
CREATED
PRECHECK_STARTED
PRECHECK_PASSED
PRECHECK_BLOCKED
RUN_STARTED
STOP_REQUESTED
STOPPED
COMPLETED
FAILED
CANCELLED
ILLEGAL_STATE_TRANSITION_ATTEMPT
SNAPSHOT_CAPTURED
CONSISTENCY_REPORT_GENERATED
```

### Snapshot type

```text
INPUT_MARKETDATA
STRATEGY_DECISION
RISK_PREFLIGHT
ORDER_INTENT_PREVIEW
```

### Comparison status

```text
CONSISTENT
DIVERGED
PARTIAL
NOT_COMPARABLE
MISSING_PAPER
MISSING_SHADOW
BLOCKED
FAILED
```

这些枚举均只表达诊断和复盘状态，不得扩展 `LIVE_READY`、`TRADE_APPROVED`、`AUTHORIZED` 等放行语义。

## 9. Candidate Indexes

`shadow_runs`：

- PK：`shadow_run_id`。
- UNIQUE：`idempotency_key`。
- INDEX：`(strategy_version_id, created_at DESC)`。
- INDEX：`(dataset_id, created_at DESC)`。
- INDEX：`(paper_run_id, created_at DESC)`，仅当 `paper_run_id IS NOT NULL` 时可做 partial index。
- INDEX：`(status, updated_at DESC)`。
- INDEX：`(trace_id)`。
- INDEX：`(request_id)`。
- INDEX：`(run_window_start, run_window_end)`。

`shadow_run_events`：

- PK：`event_id`。
- INDEX：`(shadow_run_id, created_at ASC)`。
- INDEX：`(event_type, created_at DESC)`。
- INDEX：`(trace_id)`。
- UNIQUE 可选：`(shadow_run_id, event_type, request_id)` WHERE `request_id IS NOT NULL`，用于重复 command 事件去重。

`shadow_run_snapshots`：

- PK：`snapshot_id`。
- INDEX：`(shadow_run_id, snapshot_type, captured_at ASC)`。
- UNIQUE：`(shadow_run_id, snapshot_type, sequence_no)`。
- INDEX：`(checksum)`。
- 首批不建议加 JSONB GIN index，避免鼓励业务查询依赖任意 JSON 字段。

`shadow_consistency_reports`：

- PK：`report_id`。
- INDEX：`(shadow_run_id, generated_at DESC)`。
- INDEX：`(paper_run_id, generated_at DESC)`。
- INDEX：`(strategy_version_id, generated_at DESC)`。
- INDEX：`(comparison_status, generated_at DESC)`。
- UNIQUE：`(shadow_run_id, report_version)`，避免覆盖历史 report。

## 10. Candidate Foreign Keys

建议 FK 使用默认 `NO ACTION` / `RESTRICT` 语义，不级联删除 Shadow facts：

- `shadow_runs.strategy_version_id` -> `strategy_versions.strategy_version_id`。
- `shadow_runs.dataset_id` -> `marketdata_datasets.dataset_id`。
- `shadow_runs.evaluation_id` -> `backtest_eval_reports.eval_report_id`，可空。
- `shadow_runs.publish_id` -> `backtest_publish_records.publish_record_id`，可空。
- `shadow_runs.paper_run_id` -> `paper_trading_runs.paper_run_id`，可空。
- `shadow_run_events.shadow_run_id` -> `shadow_runs.shadow_run_id`。
- `shadow_run_snapshots.shadow_run_id` -> `shadow_runs.shadow_run_id`。
- `shadow_consistency_reports.shadow_run_id` -> `shadow_runs.shadow_run_id`。
- `shadow_consistency_reports.paper_run_id` -> `paper_trading_runs.paper_run_id`。
- `shadow_consistency_reports.strategy_version_id` -> `strategy_versions.strategy_version_id`。
- `shadow_consistency_reports.dataset_id` -> `marketdata_datasets.dataset_id`。
- `shadow_consistency_reports.evaluation_id` -> `backtest_eval_reports.eval_report_id`，可空。
- `shadow_consistency_reports.publish_id` -> `backtest_publish_records.publish_record_id`，可空。

不建议 FK 到 `orders`、`trades`、`positions`、`account_snapshots`、`ledger_entries`、`ledger_events` 或 credential 表。Shadow Run 不应依赖真实交易/账户/账本事实。

## 11. JSONB Policy

必须结构化的字段：

- 所有 ID：`shadow_run_id`、`strategy_version_id`、`dataset_id`、`evaluation_id`、`publish_id`、`paper_run_id`、`report_id`。
- 状态和类型：`status`、`event_type`、`snapshot_type`、`comparison_status`。
- 时间：`created_at`、`updated_at`、`captured_at`、`generated_at`、`run_window_start`、`run_window_end`。
- 追踪与幂等：`trace_id`、`request_id`、`idempotency_key`、`version`。
- 边界：`side_effect_policy`、`authorization_boundary`、`no_order_submission`、`no_credential_access`、`no_private_endpoint`、`no_ledger_mutation`、`no_account_mutation`、`no_external_private_io`、`diagnostic_only`、`replay_only`、`review_only`。

可以 JSONB 的字段：

- `blockers_json`：阻断原因列表。
- `warnings_json`：警告列表。
- `next_steps_json`：后续动作列表。
- `metadata_json`：事件脱敏元数据。
- `snapshot_json`：本地脱敏快照。
- `metric_delta_json`：指标差异。
- `divergence_reasons_json`：偏离原因。
- `limitations_json`：不可比或限制说明。

JSONB 写入必须在 JDBC SQL 中使用 `CAST(? AS jsonb)`。JSONB 字段注释必须写明用途、结构边界和敏感信息禁入规则。

## 12. Sensitive Data Prohibition

以下字段名、内容或语义禁止出现在 Shadow Run 表结构、JSONB payload、comment、API DTO、日志或报告中：

```text
tradingReady
liveReady
authorizedForTrading
tradeApproved
liveApproved
realOrderId
realAccountBalance
realPosition
apiKey
secret
passphrase
token
privateKey
rawSignature
rawPrivateRequest
rawPrivateResponse
credentialMaterial
decryptedPayload
encryptedPayload 的真实值
private endpoint payload
```

允许并建议结构化保存的边界字段：

```text
sideEffectPolicy
noOrderSubmission
noCredentialAccess
noPrivateEndpoint
noLedgerMutation
noAccountMutation
noExternalPrivateIO
authorizationBoundary
diagnosticOnly
replayOnly
reviewOnly
```

## 13. State Machine Mapping

建议状态流转：

```text
CREATED -> PRECHECKING -> READY -> RUNNING -> COMPLETED
RUNNING -> STOP_REQUESTED -> STOPPED
PRECHECKING -> BLOCKED
CREATED / PRECHECKING / READY / RUNNING / STOP_REQUESTED -> FAILED
CREATED / READY / BLOCKED -> CANCELLED
```

终态：

- `STOPPED`
- `COMPLETED`
- `BLOCKED`
- `FAILED`
- `CANCELLED`

允许重试：

- `BLOCKED`、`FAILED` 不建议在同一 `shadow_run_id` 上重写回 `PRECHECKING` 或 `RUNNING`，应通过新建 run 或 `retry_of_shadow_run_id` 未来字段来保留原始事实。
- `STOPPED` 可允许后续 replay/report 读取，但不应重新运行同一 run。
- `READY` 可进入 `RUNNING`；`RUNNING` 可进入 `STOP_REQUESTED`、`COMPLETED`、`FAILED`。

不允许覆盖：

- 任意终态不得被覆盖回非终态。
- `COMPLETED` 不得重新写回 `RUNNING`。
- `FAILED` 不得被无条件覆盖为 `COMPLETED`。
- `CANCELLED` 不得启动。

推荐实现保护：

- `shadow_runs.version` 做 optimistic locking。
- 状态更新 SQL 必须包含 `WHERE shadow_run_id = ? AND status = ? AND version = ?`。
- 状态更新前在 Java service 中使用 transition map 校验。
- DB 层用 `status` CHECK 保证允许值，但状态流转规则由 service + optimistic locking 保护。
- 非法流转必须写入 `shadow_run_events(event_type='ILLEGAL_STATE_TRANSITION_ATTEMPT')`，不得更新 `shadow_runs.status`。

## 14. Idempotency / Traceability

创建 Shadow Run：

- `idempotency_key` 必填，唯一。
- 建议来源：`SHA-256(strategyVersionId + datasetId + evaluationId + publishId + paperRunId + runWindowStart + runWindowEnd + requestId 或 caller supplied idempotency key)`。
- 重复创建请求返回同一 `shadow_run_id`，不得创建第二条 run。

命令与事件：

- 每个状态命令必须携带 `request_id` 与 `trace_id`。
- `shadow_run_events` 记录 `request_id`、`trace_id`、`from_status`、`to_status` 和 `reason_code`。
- 快照与 report 必须复用同一 `trace_id`，便于从 run -> event -> snapshot -> report 串联复盘。

追溯链：

```text
strategy_versions
  -> marketdata_datasets
  -> backtest_eval_reports
  -> backtest_publish_records
  -> paper_trading_runs
  -> shadow_runs
  -> shadow_run_events
  -> shadow_run_snapshots
  -> shadow_consistency_reports
```

## 15. Paper vs Shadow Consistency Persistence

`shadow_consistency_reports` 只能表示复盘和一致性评估，不得表示交易授权。

必须结构化保存：

- `comparison_status`。
- `shadow_run_id`。
- `paper_run_id`。
- `strategy_version_id`。
- `dataset_id`。
- `evaluation_id`。
- `publish_id`。
- `generated_at`。
- `request_id`。
- `trace_id`。

可以 JSONB 保存：

- `metric_delta_json`：收益、回撤、exposure、turnover、signal count 等差异。
- `divergence_reasons_json`：输入差异、策略决策差异、risk preflight 差异、order intent preview 差异。
- `limitations_json`：Paper 仍在运行、Shadow 缺快照、指标不可比、样本不足等。

必须禁止：

- 不保存真实订单 ID。
- 不保存真实账户余额。
- 不保存真实持仓。
- 不保存 private endpoint request/response。
- 不把一致性通过写成 `tradeApproved`、`liveApproved` 或 `authorizedForTrading`。

## 16. Migration Versioning Plan

当前最高 migration 为 `V31__schema_credential_permission_probe.sql`。如果 GateR-2 或后续 implementation 任务在当前 HEAD 基础上落地 Shadow Run schema，建议使用：

```text
V32__gate_r_shadow_run_fact_model.sql
```

若在实施前已有新的 migration 合入，必须重新执行 `git ls-files backend/nq-infra/src/main/resources/db/migration`，选择下一个连续版本号，不得修改历史 migration。

未来 migration 必须包含：

- 所有新增表 `COMMENT ON TABLE`。
- 所有新增字段 `COMMENT ON COLUMN`。
- `status`、`event_type`、`snapshot_type`、`comparison_status` 的 CHECK 约束。
- 必要 FK、UNIQUE 和查询索引。
- JSONB 字段敏感信息禁入注释。
- 不含数据 backfill，除非后续单独 review 证明必要。

## 17. Rollback / Recovery Plan

本轮未执行 migration，因此当前回滚方式是删除本 review 文档与 current docs 指针。

后续若 migration 已执行：

- 不修改历史 migration 回滚。
- 若尚无生产数据，可用后续新 migration 反向 drop，顺序为：
  1. `shadow_consistency_reports`
  2. `shadow_run_snapshots`
  3. `shadow_run_events`
  4. `shadow_runs`
- 若已有 Shadow Run 数据，不允许直接 drop；必须先冻结写入、导出审计数据、确认没有外部引用，再用新 migration 标记 deprecated 或归档。
- Repository 层必须有 feature flag 或 profile guard，使回滚期间 Shadow Run 写路径可 fail-closed。
- 不得用 rollback 修改真实账户、资金、订单、ledger 或 credential 表。

## 18. DB_SCHEMA.md Update Plan

本轮不修改 `docs/current/DB_SCHEMA.md`，因为没有新增真实表、字段、索引或 migration。

GateR-2 或 schema implementation 完成后，`DB_SCHEMA.md` 才能追加当前事实：

- migration 版本号。
- 4 张 Shadow Run 表的用途、主键、外键、状态枚举、JSONB 边界。
- no-side-effect 字段和敏感信息禁入规则。
- rollback / retention / append-only 语义。

不得在 migration 未落地前把候选 schema 写成当前 DB 事实。

## 19. API.md Impact

本轮不修改 `docs/current/API.md`，因为没有新增 API。

后续 GateR-2 只做 local fact model / repository implementation 时，仍不应更新 `API.md` 为 HTTP API 当前事实。只有后续单独 API 任务真正新增 controller 并验证通过，才可记录 Shadow Run API。

候选 API 名称仍只能作为后续计划，不得写成已实现。

## 20. Testing Plan for GateR-2

GateR-2 最低验证建议：

- Flyway migration smoke：验证 `V32` 可从空库和当前 schema 回放。
- Repository insert/read tests：覆盖创建 run、重复 idempotency key、读取 detail。
- 状态机 tests：覆盖合法流转、非法流转、terminal state 不可覆盖、optimistic locking 冲突。
- Event tests：覆盖 append-only event、非法流转 event、traceId/requestId 保存。
- Snapshot tests：覆盖 4 类 `snapshot_type`、checksum、schemaVersion、JSONB CAST。
- Sensitive payload tests：拒绝或脱敏 `apiKey`、`secret`、`token`、`privateKey`、raw private payload。
- Consistency report tests：覆盖 `CONSISTENT`、`DIVERGED`、`NOT_COMPARABLE`、missing Paper、missing Shadow。
- Forbidden diff guard：确认未改真实订单、账户、ledger、credential、private adapter 或 LIVE 配置。

## 21. Security / LIVE / AI / DH Boundary

- Shadow Run 只允许本地无真实交易副作用 fact model。
- Shadow Run 不允许真实下单、撤单、转账、提现。
- Shadow Run 不允许调用 private endpoint。
- Shadow Run 不允许读取 credential material。
- Shadow Run 不允许修改真实账户、资金、订单或 ledger。
- Shadow Run 不允许调用真实交易所写接口。
- Shadow Run 不允许开启 LIVE。
- Shadow Run 不允许接 AI runtime。
- Shadow Run 不允许接 DH runtime。
- DH 不允许启动 Paper Run 或 Shadow Run，不允许访问 credential material，不允许修改 NQ 交易状态。
- Paper vs Shadow consistency report 不代表 trading authorization，不代表 LIVE ready。

## 22. P0 / P1 / P2 / P3 Findings

### P0

- 无。

### P1

- 无。

### P2

- GateR-0 候选模型曾列出 5 个对象：`shadow_runs`、`shadow_run_events`、`shadow_input_snapshots`、`shadow_decision_traces`、`shadow_consistency_reports`。本 review 建议 GateR-2 首批采用 4 表，将 input / decision / risk / order intent 合并进 `shadow_run_snapshots`。这是范围收敛，不阻塞 GateR-2。

### P3

- `AGENTS.md` 的早期阶段段落仍包含 GateJ/GateK 旧口径；本轮按 `docs/current/README.md`、`STATUS.md`、`ROADMAP.md`、`GATER_PLAN.md` 与 root `README.md` 的 GateQ/GateR 当前事实执行。该 P3 不在本轮允许修改范围内。

## 23. GateR-2 Entry Criteria

GateR-2 可以进入 implementation 的条件：

- 接受本 review 的 4 表最小模型。
- GateR-2 任务明确允许新增 migration 和 repository，并明确禁止 API、frontend、runner、LIVE、AI、DH runtime、RealClient、real provider、private adapter、真实 permission probe。
- GateR-2 先重新确认 `HEAD` 与 `origin/dev` 对齐、工作区 clean、最新 CI success。
- GateR-2 重新确认最高 migration 版本，并选择正确下一版本。
- GateR-2 必须补充 migration / repository / 状态机 / JSONB sensitive field regression tests。

不满足上述条件时，不得进入 implementation。

## 24. Final Verdict

`NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`

Shadow Run 需要新增独立本地事实表。推荐最小 4 表方案。现有 strategy、dataset、evaluation、publish 和 Paper 表只作为只读输入事实源，不作为 Shadow Run 主状态、事件、快照或 report 的存储位置。

下一步只能进入单独 GateR-2 Shadow Run local fact model / repository implementation。GateR-2 仍不得启动 Shadow runner，不得新增 API，不得新增前端页面，不得开启 LIVE，不得接 AI / DH runtime，不得触达真实交易或 credential material。
