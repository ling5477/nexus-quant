# GateJ API 规划

本文只做 API 规划，不实现接口。所有 endpoint 为草案，实现时可微调。

## 1. Paper Run Schedule API

用于 Paper run 定时调度、启停调度、查询调度。

### Endpoints

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/paper-trading/schedules` | 查询调度计划列表 |
| POST | `/api/paper-trading/schedules` | 创建调度计划 |
| GET | `/api/paper-trading/schedules/{scheduleId}` | 查询调度计划详情 |
| PATCH | `/api/paper-trading/schedules/{scheduleId}/status` | 变更调度状态 |
| POST | `/api/paper-trading/schedules/{scheduleId}/run-once` | 手动触发一次调度 |

### GET /api/paper-trading/schedules

Request:
- `paperRunId` (optional): 按 Paper run 过滤
- `status` (optional): ENABLED / DISABLED / PAUSED
- `page` (optional, default 0)
- `size` (optional, default 20)

Response:
```json
{
  "content": [{
    "scheduleId": "long",
    "paperRunId": "long",
    "scheduleName": "string",
    "cronExpr": "string",
    "status": "ENABLED|DISABLED|PAUSED",
    "timezone": "string",
    "nextFireTime": "ISO datetime",
    "lastFireTime": "ISO datetime|null",
    "createdBy": "string",
    "createdAt": "ISO datetime"
  }],
  "totalElements": "long",
  "page": "int",
  "size": "int"
}
```

### POST /api/paper-trading/schedules

Request:
```json
{
  "paperRunId": "long (required)",
  "scheduleName": "string (required)",
  "cronExpr": "string (required, valid cron)",
  "timezone": "string (optional, default UTC)",
  "requestJson": "object (optional, 调度触发时的额外参数快照)"
}
```

Response: 201 Created，返回完整 schedule 对象。

### GET /api/paper-trading/schedules/{scheduleId}

Response: 单个 schedule 对象。

### PATCH /api/paper-trading/schedules/{scheduleId}/status

Request:
```json
{
  "status": "ENABLED|DISABLED|PAUSED"
}
```

Response: 200，返回更新后的 schedule 对象。

### POST /api/paper-trading/schedules/{scheduleId}/run-once

Request: 无 body。

Response: 200，返回本次触发的 fire 记录。

### 权限要求

- 所有 Schedule API 需要登录态。
- 操作范围限定在当前用户可访问的 Paper run。

### 分页要求

- GET 列表支持 `page` + `size` 分页。

### 幂等要求

- POST 创建不幂等（同 paperRunId 可创建多个 schedule）。
- PATCH status 幂等（重复设置相同状态返回 200）。
- POST run-once 不幂等（每次触发产生新 fire 记录）。

### 错误码

- 400: 参数校验失败（cron 表达式无效、paperRunId 不存在）。
- 404: scheduleId 不存在。
- 409: 状态冲突（如 DISABLED 状态下 run-once）。

### 不做范围

- 不做调度执行引擎（第一版 run-once 手动触发，后续接入 Spring Scheduler）。
- 不做外部通知。
- 不接 AI。

---

## 2. Paper Run Heartbeat API

用于记录和查询运行心跳。

### Endpoints

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/paper-trading/runs/{paperRunId}/heartbeats` | 查询心跳列表 |
| POST | `/api/paper-trading/runs/{paperRunId}/heartbeats/run-once` | 手动触发一次心跳记录 |

### GET /api/paper-trading/runs/{paperRunId}/heartbeats

Request:
- `page` (optional, default 0)
- `size` (optional, default 20)

Response:
```json
{
  "content": [{
    "heartbeatId": "long",
    "paperRunId": "long",
    "heartbeatTime": "ISO datetime",
    "status": "string",
    "lastEventTime": "ISO datetime|null",
    "lastOrderTime": "ISO datetime|null",
    "lastTradeTime": "ISO datetime|null",
    "lagSeconds": "long|null",
    "summaryJson": "object|null",
    "createdAt": "ISO datetime"
  }],
  "totalElements": "long",
  "page": "int",
  "size": "int"
}
```

### POST /api/paper-trading/runs/{paperRunId}/heartbeats/run-once

Request: 无 body。

Response: 200，返回本次心跳记录。

### 权限要求

- 需要登录态。
- paperRunId 必须属于当前用户可访问范围。

### 分页要求

- GET 列表支持 `page` + `size` 分页，按 `heartbeat_time DESC` 排序。

### 幂等要求

- POST run-once 不幂等（每次产生新心跳记录）。

### 错误码

- 404: paperRunId 不存在。
- 409: Paper run 非 RUNNING 状态时 run-once 返回冲突。

### 不做范围

- 不做自动心跳定时器（第一版手动触发，后续接入定时任务）。
- 不接 AI。

---

## 3. Paper Run Daily Report API

用于生成和查询日报。

### Endpoints

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/paper-trading/runs/{paperRunId}/daily-reports` | 查询日报列表 |
| POST | `/api/paper-trading/runs/{paperRunId}/daily-reports/generate` | 生成一份日报 |

### GET /api/paper-trading/runs/{paperRunId}/daily-reports

Request:
- `reportDate` (optional): 按日期过滤
- `page` (optional, default 0)
- `size` (optional, default 20)

Response:
```json
{
  "content": [{
    "reportId": "long",
    "paperRunId": "long",
    "reportDate": "date",
    "status": "string",
    "totalEquity": "decimal|null",
    "dailyPnl": "decimal|null",
    "dailyReturn": "decimal|null",
    "maxDrawdown": "decimal|null",
    "orderCount": "int",
    "tradeCount": "int",
    "alertCount": "int",
    "riskRejectCount": "int",
    "reportJson": "object|null",
    "generatedAt": "ISO datetime",
    "createdAt": "ISO datetime"
  }],
  "totalElements": "long",
  "page": "int",
  "size": "int"
}
```

### POST /api/paper-trading/runs/{paperRunId}/daily-reports/generate

Request:
```json
{
  "reportDate": "date (optional, default today)"
}
```

Response: 201 Created，返回生成的日报对象。

### 权限要求

- 需要登录态。

### 分页要求

- GET 列表支持分页，按 `report_date DESC` 排序。

### 幂等要求

- POST generate 按 `(paperRunId, reportDate)` 幂等：同一天重复生成覆盖已有记录。

### 错误码

- 404: paperRunId 不存在。
- 400: reportDate 格式无效。

### 不做范围

- 不做自动日报定时生成（第一版手动触发）。
- 不做日报推送通知。
- 不接 AI。

---

## 4. Paper Run Alert API

用于告警事件查询和确认。

### Endpoints

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/paper-trading/runs/{paperRunId}/alerts` | 查询告警列表 |
| PATCH | `/api/paper-trading/runs/{paperRunId}/alerts/{alertId}/ack` | 确认告警 |

### GET /api/paper-trading/runs/{paperRunId}/alerts

Request:
- `severity` (optional): LOW / MEDIUM / HIGH / CRITICAL
- `status` (optional): OPEN / ACKED / RESOLVED
- `page` (optional, default 0)
- `size` (optional, default 20)

Response:
```json
{
  "content": [{
    "alertId": "long",
    "paperRunId": "long",
    "alertType": "string",
    "severity": "LOW|MEDIUM|HIGH|CRITICAL",
    "status": "OPEN|ACKED|RESOLVED",
    "title": "string",
    "message": "string|null",
    "source": "string|null",
    "eventSnapshotJson": "object|null",
    "acknowledgedBy": "string|null",
    "acknowledgedAt": "ISO datetime|null",
    "createdAt": "ISO datetime"
  }],
  "totalElements": "long",
  "page": "int",
  "size": "int"
}
```

### PATCH /api/paper-trading/runs/{paperRunId}/alerts/{alertId}/ack

Request: 无 body（acknowledgedBy 从登录态获取）。

Response: 200，返回更新后的 alert 对象。

### 权限要求

- 需要登录态。

### 分页要求

- GET 列表支持分页，按 `created_at DESC` 排序。

### 幂等要求

- PATCH ack 幂等（重复确认返回 200，不改变已确认状态）。

### 错误码

- 404: paperRunId 或 alertId 不存在。
- 409: alert 已处于 RESOLVED 状态时不允许 ack。

### 不做范围

- 不做告警自动创建规则引擎（第一版由心跳/日报/恢复流程内部写入）。
- 不做外部通知推送。
- 不接 AI。

---

## 5. Paper Run Recovery API

用于异常恢复和失败重试。

### Endpoints

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/paper-trading/runs/{paperRunId}/recovery-events` | 查询恢复事件列表 |
| POST | `/api/paper-trading/runs/{paperRunId}/recover` | 触发一次恢复 |
| POST | `/api/paper-trading/runs/{paperRunId}/retry-failed-step` | 重试失败步骤 |

### GET /api/paper-trading/runs/{paperRunId}/recovery-events

Request:
- `status` (optional): STARTED / SUCCEEDED / FAILED / SKIPPED
- `page` (optional, default 0)
- `size` (optional, default 20)

Response:
```json
{
  "content": [{
    "recoveryEventId": "long",
    "paperRunId": "long",
    "recoveryType": "string",
    "status": "STARTED|SUCCEEDED|FAILED|SKIPPED",
    "reason": "string|null",
    "requestJson": "object|null",
    "resultJson": "object|null",
    "startedAt": "ISO datetime",
    "finishedAt": "ISO datetime|null",
    "createdAt": "ISO datetime"
  }],
  "totalElements": "long",
  "page": "int",
  "size": "int"
}
```

### POST /api/paper-trading/runs/{paperRunId}/recover

Request:
```json
{
  "reason": "string (optional, 恢复原因说明)"
}
```

Response: 200，返回恢复事件记录。

### POST /api/paper-trading/runs/{paperRunId}/retry-failed-step

Request:
```json
{
  "reason": "string (optional, 重试原因说明)"
}
```

Response: 200，返回重试事件记录。

### 权限要求

- 需要登录态。

### 分页要求

- GET 列表支持分页，按 `created_at DESC` 排序。

### 幂等要求

- POST recover / retry-failed-step 不幂等（每次产生新事件记录）。

### 错误码

- 404: paperRunId 不存在。
- 409: Paper run 状态不允许恢复/重试（如已 STOPPED）。

### 不做范围

- 不做自动恢复策略引擎。
- 不接 AI。

---

## 6. GateJ Stability Acceptance API

用于连续运行验收结果记录和查询。

### Endpoints

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/paper-trading/runs/{paperRunId}/stability-checks` | 查询稳定性验收列表 |
| POST | `/api/paper-trading/runs/{paperRunId}/stability-checks/generate` | 生成一次稳定性验收 |

### GET /api/paper-trading/runs/{paperRunId}/stability-checks

Request:
- `status` (optional): PASSED / FAILED / PARTIAL
- `page` (optional, default 0)
- `size` (optional, default 20)

Response:
```json
{
  "content": [{
    "stabilityCheckId": "long",
    "paperRunId": "long",
    "checkWindowStart": "ISO datetime",
    "checkWindowEnd": "ISO datetime",
    "status": "PASSED|FAILED|PARTIAL",
    "uptimeRatio": "decimal",
    "heartbeatCount": "int",
    "alertCount": "int",
    "failedFireCount": "int",
    "recoveryCount": "int",
    "reportCount": "int",
    "summaryJson": "object|null",
    "createdAt": "ISO datetime"
  }],
  "totalElements": "long",
  "page": "int",
  "size": "int"
}
```

### POST /api/paper-trading/runs/{paperRunId}/stability-checks/generate

Request:
```json
{
  "checkWindowStart": "ISO datetime (required)",
  "checkWindowEnd": "ISO datetime (required)"
}
```

Response: 201 Created，返回生成的稳定性验收对象。

### 权限要求

- 需要登录态。

### 分页要求

- GET 列表支持分页，按 `created_at DESC` 排序。

### 幂等要求

- POST generate 按 `(paperRunId, checkWindowStart, checkWindowEnd)` 幂等：相同窗口重复生成覆盖已有记录。

### 错误码

- 404: paperRunId 不存在。
- 400: checkWindowEnd <= checkWindowStart。

### 不做范围

- 不做自动验收触发。
- 不接 AI。
