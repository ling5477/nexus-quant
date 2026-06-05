# GateJ DB 规划

本文只做 DB 规划，不新增 migration。所有表结构为草案，实现时可微调。

## 通用规则

- 所有新增表必须有 `COMMENT ON TABLE`。
- 所有新增字段必须有 `COMMENT ON COLUMN`。
- JSONB 字段必须说明用途和边界，不保存密钥、token、cookie。
- 状态字段必须有 CHECK 约束或等价约束。
- 不修改历史 migration。
- 审计字段统一包含 `created_at`（NOT NULL DEFAULT now()）。

---

## 1. paper_run_schedules

**用途**：Paper run 调度计划，定义 cron 表达式和调度状态。

### 字段草案

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| schedule_id | BIGSERIAL | PK | 调度计划 ID |
| paper_run_id | BIGINT | NOT NULL, FK → paper_trading_runs | 关联 Paper run |
| schedule_name | VARCHAR(256) | NOT NULL | 调度名称 |
| cron_expr | VARCHAR(128) | NOT NULL | cron 表达式 |
| status | VARCHAR(32) | NOT NULL, CHECK | ENABLED / DISABLED / PAUSED |
| timezone | VARCHAR(64) | NOT NULL DEFAULT 'UTC' | 时区 |
| next_fire_time | TIMESTAMPTZ | NULL | 下次触发时间 |
| last_fire_time | TIMESTAMPTZ | NULL | 上次触发时间 |
| created_by | VARCHAR(512) | NOT NULL | 创建人 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 创建时间 |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 更新时间 |
| request_json | JSONB | NULL | 调度创建请求快照，不保存密钥 |

### 状态允许值

- `ENABLED`：调度启用，按 cron 触发。
- `DISABLED`：调度禁用，不触发。
- `PAUSED`：调度暂停，可恢复。

### 唯一约束

- 无强唯一约束（同 paper_run_id 可有多个 schedule）。

### 索引

- `idx_paper_run_schedules_run_id`：`(paper_run_id)`
- `idx_paper_run_schedules_status`：`(status)`
- `idx_paper_run_schedules_next_fire`：`(next_fire_time)` WHERE status = 'ENABLED'

### JSONB 用途

- `request_json`：保存调度创建时的请求参数快照，用于审计和排障。不保存密钥、token、cookie。

### 幂等策略

- 创建不幂等，同 paper_run_id 可创建多个 schedule。
- 状态变更幂等，重复设置相同状态不报错。

---

## 2. paper_run_schedule_fires

**用途**：调度触发记录，每次调度触发产生一条记录。

### 字段草案

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| fire_id | BIGSERIAL | PK | 触发记录 ID |
| schedule_id | BIGINT | NOT NULL, FK → paper_run_schedules | 关联调度计划 |
| paper_run_id | BIGINT | NOT NULL, FK → paper_trading_runs | 关联 Paper run |
| status | VARCHAR(32) | NOT NULL, CHECK | RUNNING / SUCCEEDED / FAILED / SKIPPED |
| fired_at | TIMESTAMPTZ | NOT NULL | 触发时间 |
| finished_at | TIMESTAMPTZ | NULL | 完成时间 |
| duration_ms | BIGINT | NULL | 执行耗时（毫秒） |
| result_json | JSONB | NULL | 执行结果快照 |
| error_message | TEXT | NULL | 错误信息 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 创建时间 |

### 状态允许值

- `RUNNING`：正在执行。
- `SUCCEEDED`：执行成功。
- `FAILED`：执行失败。
- `SKIPPED`：跳过（如上次未完成）。

### 索引

- `idx_schedule_fires_schedule_id_fired`：`(schedule_id, fired_at DESC)`
- `idx_schedule_fires_run_id`：`(paper_run_id)`
- `idx_schedule_fires_status`：`(status)`

### JSONB 用途

- `result_json`：保存本次触发的执行结果摘要，不保存密钥。

### 幂等策略

- 每次触发产生新记录，不幂等。

---

## 3. paper_run_heartbeats

**用途**：Paper run 心跳记录，定期记录运行健康状态。

### 字段草案

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| heartbeat_id | BIGSERIAL | PK | 心跳 ID |
| paper_run_id | BIGINT | NOT NULL, FK → paper_trading_runs | 关联 Paper run |
| heartbeat_time | TIMESTAMPTZ | NOT NULL | 心跳时间 |
| status | VARCHAR(32) | NOT NULL | 心跳状态（HEALTHY / DEGRADED / UNHEALTHY） |
| last_event_time | TIMESTAMPTZ | NULL | 最近事件时间 |
| last_order_time | TIMESTAMPTZ | NULL | 最近订单时间 |
| last_trade_time | TIMESTAMPTZ | NULL | 最近成交时间 |
| lag_seconds | BIGINT | NULL | 延迟秒数 |
| summary_json | JSONB | NULL | 心跳摘要 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 创建时间 |

### 索引

- `idx_heartbeats_run_id_time`：`(paper_run_id, heartbeat_time DESC)`

### JSONB 用途

- `summary_json`：保存心跳时刻的运行摘要（如当前持仓数、未完成订单数），不保存密钥。

### 幂等策略

- 每次心跳产生新记录，不幂等。

---

## 4. paper_run_daily_reports

**用途**：Paper run 日报，每日运行摘要。

### 字段草案

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| report_id | BIGSERIAL | PK | 日报 ID |
| paper_run_id | BIGINT | NOT NULL, FK → paper_trading_runs | 关联 Paper run |
| report_date | DATE | NOT NULL | 报告日期 |
| status | VARCHAR(32) | NOT NULL | 日报状态（GENERATED / FAILED） |
| total_equity | NUMERIC(20,8) | NULL | 当日总权益 |
| daily_pnl | NUMERIC(20,8) | NULL | 当日盈亏 |
| daily_return | NUMERIC(12,8) | NULL | 当日收益率 |
| max_drawdown | NUMERIC(12,8) | NULL | 当日最大回撤 |
| order_count | INT | NOT NULL DEFAULT 0 | 当日订单数 |
| trade_count | INT | NOT NULL DEFAULT 0 | 当日成交数 |
| alert_count | INT | NOT NULL DEFAULT 0 | 当日告警数 |
| risk_reject_count | INT | NOT NULL DEFAULT 0 | 当日风控拒绝数 |
| report_json | JSONB | NULL | 日报详细数据 |
| generated_at | TIMESTAMPTZ | NOT NULL | 生成时间 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 创建时间 |

### 唯一约束

- `uq_daily_reports_run_date`：`(paper_run_id, report_date)` 保证同一天只有一份日报。

### 索引

- `idx_daily_reports_run_id_date`：`(paper_run_id, report_date DESC)`

### JSONB 用途

- `report_json`：保存日报详细数据（如各交易对盈亏明细、风控事件列表），不保存密钥。

### 幂等策略

- 按 `(paper_run_id, report_date)` 幂等：重复生成覆盖已有记录（UPSERT）。

---

## 5. paper_run_alerts

**用途**：Paper run 告警事件。

### 字段草案

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| alert_id | BIGSERIAL | PK | 告警 ID |
| paper_run_id | BIGINT | NOT NULL, FK → paper_trading_runs | 关联 Paper run |
| alert_type | VARCHAR(64) | NOT NULL | 告警类型 |
| severity | VARCHAR(16) | NOT NULL, CHECK | LOW / MEDIUM / HIGH / CRITICAL |
| status | VARCHAR(16) | NOT NULL, CHECK | OPEN / ACKED / RESOLVED |
| title | VARCHAR(512) | NOT NULL | 告警标题 |
| message | TEXT | NULL | 告警详情 |
| source | VARCHAR(128) | NULL | 告警来源 |
| event_snapshot_json | JSONB | NULL | 事件快照 |
| acknowledged_by | VARCHAR(512) | NULL | 确认人 |
| acknowledged_at | TIMESTAMPTZ | NULL | 确认时间 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 创建时间 |

### severity 允许值

- `LOW`：低优先级。
- `MEDIUM`：中优先级。
- `HIGH`：高优先级。
- `CRITICAL`：紧急。

### status 允许值

- `OPEN`：未处理。
- `ACKED`：已确认。
- `RESOLVED`：已解决。

### 索引

- `idx_alerts_run_id_created`：`(paper_run_id, created_at DESC)`
- `idx_alerts_status`：`(status)`
- `idx_alerts_severity`：`(severity)`

### JSONB 用途

- `event_snapshot_json`：保存告警触发时的上下文快照（如心跳延迟、异常堆栈摘要），不保存密钥。

### 幂等策略

- 告警创建不幂等（同类型可产生多条）。
- ack 幂等（重复确认不改变状态）。

---

## 6. paper_run_recovery_events

**用途**：恢复和重试事件记录。

### 字段草案

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| recovery_event_id | BIGSERIAL | PK | 恢复事件 ID |
| paper_run_id | BIGINT | NOT NULL, FK → paper_trading_runs | 关联 Paper run |
| recovery_type | VARCHAR(64) | NOT NULL | 恢复类型（MANUAL_RECOVER / RETRY_FAILED_STEP / AUTO_RECOVER） |
| status | VARCHAR(32) | NOT NULL, CHECK | STARTED / SUCCEEDED / FAILED / SKIPPED |
| reason | TEXT | NULL | 恢复原因 |
| request_json | JSONB | NULL | 恢复请求参数 |
| result_json | JSONB | NULL | 恢复结果 |
| started_at | TIMESTAMPTZ | NOT NULL | 开始时间 |
| finished_at | TIMESTAMPTZ | NULL | 完成时间 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 创建时间 |

### status 允许值

- `STARTED`：恢复开始。
- `SUCCEEDED`：恢复成功。
- `FAILED`：恢复失败。
- `SKIPPED`：跳过。

### 索引

- `idx_recovery_events_run_id_created`：`(paper_run_id, created_at DESC)`
- `idx_recovery_events_status`：`(status)`

### JSONB 用途

- `request_json`：保存恢复请求参数，不保存密钥。
- `result_json`：保存恢复结果摘要，不保存密钥。

### 幂等策略

- 每次恢复/重试产生新记录，不幂等。

---

## 7. paper_run_stability_checks

**用途**：连续运行验收结果。

### 字段草案

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| stability_check_id | BIGSERIAL | PK | 验收 ID |
| paper_run_id | BIGINT | NOT NULL, FK → paper_trading_runs | 关联 Paper run |
| check_window_start | TIMESTAMPTZ | NOT NULL | 验收窗口开始 |
| check_window_end | TIMESTAMPTZ | NOT NULL | 验收窗口结束 |
| status | VARCHAR(16) | NOT NULL, CHECK | PASSED / FAILED / PARTIAL |
| uptime_ratio | NUMERIC(5,4) | NOT NULL | 在线率 |
| heartbeat_count | INT | NOT NULL DEFAULT 0 | 心跳数 |
| alert_count | INT | NOT NULL DEFAULT 0 | 告警数 |
| failed_fire_count | INT | NOT NULL DEFAULT 0 | 失败触发数 |
| recovery_count | INT | NOT NULL DEFAULT 0 | 恢复次数 |
| report_count | INT | NOT NULL DEFAULT 0 | 日报数 |
| summary_json | JSONB | NULL | 验收摘要 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 创建时间 |

### status 允许值

- `PASSED`：验收通过。
- `FAILED`：验收失败。
- `PARTIAL`：部分通过。

### 唯一约束

- `uq_stability_checks_run_window`：`(paper_run_id, check_window_start, check_window_end)` 保证同窗口只有一条验收记录。

### 索引

- `idx_stability_checks_run_id_created`：`(paper_run_id, created_at DESC)`
- `idx_stability_checks_status`：`(status)`

### JSONB 用途

- `summary_json`：保存验收详细统计（如每日在线率明细、告警分布），不保存密钥。

### 幂等策略

- 按 `(paper_run_id, check_window_start, check_window_end)` 幂等：相同窗口重复生成覆盖已有记录（UPSERT）。
