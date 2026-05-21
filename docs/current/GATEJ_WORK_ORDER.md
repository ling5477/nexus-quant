# GateJ 工作单（Work Order）

本文拆分 GateJ 的子工作单，每个子工作单独立开工，不允许夹带其他子阶段或 AI。

---

## GateJ-1-WO：Paper run 调度与连续运行

### 背景

GateI completed 的 Paper run 只能手动创建、启动、停止，无法按计划自动连续运行。GateJ-1 引入调度计划、调度触发记录和心跳能力。

### 目标

- 实现 Paper run 调度计划 CRUD。
- 实现调度触发记录。
- 实现 Paper run 心跳记录与查询。
- 提供前端调度/心跳入口。
- 通过 GateJ-1 E2E smoke。

### 范围

- 新增 migration：`paper_run_schedules`、`paper_run_schedule_fires`、`paper_run_heartbeats`。
- 新增后端 domain / port / service / API：
  - `PaperRunScheduleService`
  - `PaperRunHeartbeatService`
  - `PaperRunStableOperationController`（建议路径，可调整）。
- 新增 JDBC 实现：3 个 repository。
- 新增前端：
  - `frontend/src/api/paper-trading-stable.ts`
  - 调度计划 Tab
  - 调度触发记录 Tab
  - 心跳 Tab
- 新增 E2E：`paper-schedule-smoke`、`paper-heartbeat-smoke`。

### 不做范围

- 不做日报。
- 不做告警。
- 不做恢复。
- 不做稳定性验收。
- 不做调度执行引擎自动触发（第一版只支持 run-once 手动触发；后续若需要 Spring Scheduler 自动调度，单独评估）。
- 不接 AI。
- 不调用真实 LIVE 下单。

### 影响文件

后端：
- 新增 `backend/nq-infra/src/main/resources/db/migration/V23__gate_j1_paper_run_schedule.sql`（建议版本号，按实际可用版本号取最近未占用版本）。
- 新增 `backend/nq-research/.../research/domain/paper/PaperRunSchedule.java` 等 domain。
- 新增 `backend/nq-research/.../research/domain/paper/port/PaperRunScheduleRepository.java` 等 port。
- 新增 `backend/nq-research/.../research/application/paper/PaperRunScheduleService.java`。
- 新增 `backend/nq-infra/.../research/infra/paper/jdbc/JdbcPaperRunScheduleRepository.java` 等 JDBC。
- 新增 `backend/nq-api/.../paper/api/web/...` controller。
- 修改 `backend/nq-api/.../paper/api/web/PaperTradingController.java`（如需复用现有路径）。

前端：
- 新增 `frontend/src/api/paper-trading-stable.ts`（或在现有 `paper-trading.ts` 内分组）。
- 新增 hooks 和 query keys。
- 修改 `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：新增 3 个 Tab。
- 新增 `frontend/tests/e2e/paper-schedule-smoke.spec.ts`、`paper-heartbeat-smoke.spec.ts`。

文档：
- 更新 `docs/current/API.md`、`DB_SCHEMA.md`、`TESTING.md`、`STATUS.md`、`WORKLOG.md`。

### API 变化

- 新增 `GET / POST / PATCH /api/paper-trading/schedules/**`。
- 新增 `POST /api/paper-trading/schedules/{scheduleId}/run-once`。
- 新增 `GET / POST /api/paper-trading/runs/{paperRunId}/heartbeats[/run-once]`。

### DB 变化

- 新增 3 张表。
- 所有新增表 / 字段 COMMENT 必须补齐。
- 状态字段 CHECK 约束：`paper_run_schedules.status`、`paper_run_schedule_fires.status`。

### 前端变化

- `/paper-trading` 详情抽屉新增 3 个 Tab。
- 全部走 Axios + TanStack Query。

### 测试要求

- 后端 `mvn -f backend/pom.xml test` 通过。
- 新增单元测试：`PaperRunScheduleServiceTest`、`PaperRunHeartbeatServiceTest`。
- 前端 `npm run build` 通过。
- E2E `npm run test:e2e` 通过；新增 2 个 smoke 全部通过。

### 验收标准

- 调度可创建、启用、禁用、暂停。
- 调度 run-once 可触发并写入 fire 记录。
- 心跳可手动触发并写入 heartbeat 记录。
- 后端测试通过。
- 前端 build 通过。
- E2E 通过。
- 不接 AI。

### 回滚策略

- 回退 migration（DROP TABLE 3 张表）。
- 删除新增 API、service、repository、前端 Tab。
- 恢复到 GateI completed 状态。

---

## GateJ-2-WO：运行监控、日报、告警

### 背景

GateJ-1 completed 后，Paper run 可连续运行。GateJ-2 引入日报和告警能力，建立监控基础。

### 目标

- 实现 Paper run 日报生成与查询。
- 实现 Paper run 告警事件查询与确认。
- 提供前端日报/告警入口。
- 通过 GateJ-2 E2E smoke。

### 范围

- 新增 migration：`paper_run_daily_reports`、`paper_run_alerts`。
- 新增后端 domain / port / service / API：
  - `PaperRunDailyReportService`
  - `PaperRunAlertService`
- 新增 JDBC 实现：2 个 repository。
- 新增前端：
  - 日报 Tab（表格/描述卡，不引入图表库）
  - 告警 Tab（支持过滤、确认）
- 新增 E2E：`paper-daily-report-smoke`、`paper-alert-smoke`。

### 不做范围

- 不做恢复。
- 不做稳定性验收。
- 不做外部通知（邮件、Slack、钉钉）。
- 不引入图表库。
- 不接 AI。

### 影响文件

后端：
- 新增 `backend/nq-infra/src/main/resources/db/migration/V24__gate_j2_paper_run_monitor.sql`（建议版本号）。
- 新增 domain / port / service / JDBC / controller / DTO。

前端：
- 新增 hooks 和 query keys。
- 修改 `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：新增 2 个 Tab。
- 新增 E2E spec。

文档：
- 更新 `docs/current/API.md`、`DB_SCHEMA.md`、`TESTING.md`、`STATUS.md`、`WORKLOG.md`。

### API 变化

- 新增 `GET / POST /api/paper-trading/runs/{paperRunId}/daily-reports[/generate]`。
- 新增 `GET / PATCH /api/paper-trading/runs/{paperRunId}/alerts[/{alertId}/ack]`。

### DB 变化

- 新增 2 张表。
- 所有新增表 / 字段 COMMENT 必须补齐。
- `paper_run_alerts.severity / status` CHECK 约束。
- `paper_run_daily_reports` 唯一约束 `(paper_run_id, report_date)`。

### 前端变化

- `/paper-trading` 详情抽屉新增 2 个 Tab。
- 第一版无图表库依赖。

### 测试要求

- 后端 `mvn test` 通过。
- 新增单元测试：`PaperRunDailyReportServiceTest`、`PaperRunAlertServiceTest`。
- 前端 build 通过。
- E2E 通过；新增 2 个 smoke 全部通过。

### 验收标准

- 日报可生成并查询。
- 日报按日幂等。
- 告警可记录、查看、确认。
- ack 幂等。
- 后端测试通过。
- 前端 build 通过。
- E2E 通过。
- 不接 AI。

### 回滚策略

- 回退 migration（DROP TABLE 2 张表）。
- 删除新增 API、service、repository、前端 Tab。
- 恢复到 GateJ-1 completed 状态。

---

## GateJ-3-WO：异常恢复、失败重试、运行稳定性

### 背景

GateJ-2 completed 后，Paper run 已具备日报和告警。GateJ-3 引入恢复、重试和稳定性验收结构，为 GateJ-FREEZE 验收做准备。

### 目标

- 实现 Paper run 恢复事件记录。
- 实现 Paper run 失败重试。
- 实现 Paper run 稳定性验收生成与查询。
- 提供前端恢复/稳定性验收入口。
- 通过 GateJ-3 E2E smoke。

### 范围

- 新增 migration：`paper_run_recovery_events`、`paper_run_stability_checks`。
- 新增后端 domain / port / service / API：
  - `PaperRunRecoveryService`
  - `PaperRunStabilityCheckService`
- 新增 JDBC 实现：2 个 repository。
- 新增前端：
  - 恢复事件 Tab
  - 稳定性验收 Tab
- 新增 E2E：`paper-recovery-smoke`、`paper-stability-check-smoke`。

### 不做范围

- 不执行连续运行验收（由 GateJ-FREEZE 执行）。
- 不做自动恢复策略引擎。
- 不接 AI。

### 影响文件

后端：
- 新增 `backend/nq-infra/src/main/resources/db/migration/V25__gate_j3_paper_run_recovery_stability.sql`（建议版本号）。
- 新增 domain / port / service / JDBC / controller / DTO。

前端：
- 新增 hooks 和 query keys。
- 修改 `frontend/src/pages/paper-trading/PaperTradingPage.tsx`：新增 2 个 Tab。
- 新增 E2E spec。

文档：
- 更新 `docs/current/API.md`、`DB_SCHEMA.md`、`TESTING.md`、`STATUS.md`、`WORKLOG.md`。

### API 变化

- 新增 `GET / POST /api/paper-trading/runs/{paperRunId}/recovery-events`。
- 新增 `POST /api/paper-trading/runs/{paperRunId}/recover`。
- 新增 `POST /api/paper-trading/runs/{paperRunId}/retry-failed-step`。
- 新增 `GET / POST /api/paper-trading/runs/{paperRunId}/stability-checks[/generate]`。

### DB 变化

- 新增 2 张表。
- 所有新增表 / 字段 COMMENT 必须补齐。
- `paper_run_recovery_events.status / paper_run_stability_checks.status` CHECK 约束。
- `paper_run_stability_checks` 唯一约束 `(paper_run_id, check_window_start, check_window_end)`。

### 前端变化

- `/paper-trading` 详情抽屉新增 2 个 Tab。

### 测试要求

- 后端 `mvn test` 通过。
- 新增单元测试：`PaperRunRecoveryServiceTest`、`PaperRunStabilityCheckServiceTest`。
- 前端 build 通过。
- E2E 通过；新增 2 个 smoke 全部通过。

### 验收标准

- 恢复事件可记录和查询。
- 失败重试可触发和记录。
- 稳定性验收可生成和查询。
- 后端测试通过。
- 前端 build 通过。
- E2E 通过。
- 不接 AI。

### 回滚策略

- 回退 migration（DROP TABLE 2 张表）。
- 删除新增 API、service、repository、前端 Tab。
- 恢复到 GateJ-2 completed 状态。

---

## GateJ-FREEZE：连续运行验收与冻结

### 背景

GateJ-1/2/3 completed 后，所有功能就绪。GateJ-FREEZE 执行实际连续运行验收，确认 Paper Trading 可稳定连续运行 7 天。

### 目标

- 完成 1 小时短验收。
- 完成 24 小时中验收。
- 完成 7 天稳定性验收。
- 验收通过后冻结 GateJ。
- Next: GateK-PLAN。

### 范围

- 启动一个长期 Paper run。
- 配置调度计划。
- 周期性触发心跳、日报、稳定性验收。
- 记录验收结果。
- 冻结 GateJ 文档到 `docs/gates/gate-j/`。
- 同步 README、AGENTS、CLAUDE。

### 不做范围

- 不新增功能代码。
- 不新增 migration。
- 不新增 API。
- 不新增前端页面实现。
- 不接 AI。
- 不进入 GateK 实现。

### 影响文件

- 新增 `docs/gates/gate-j/`（README + FREEZE_SUMMARY + 全部 GateJ 文档归档副本）。
- 修改 `docs/current/STATUS.md`、`ROADMAP.md`、`WORKLOG.md`、`TESTING.md`。
- 修改 `README.md`、`AGENTS.md`、`CLAUDE.md`。

### API 变化

- 无新增。

### DB 变化

- 无新增。

### 前端变化

- 无新增。

### 测试要求

- 全量回归：`mvn test`、`npm run build`、`npm run test:e2e`、Python pytest/mypy/ruff。
- 1 小时短验收：在线率 100%、无 CRITICAL 告警、无 FAILED 调度触发。
- 24 小时中验收：在线率 ≥ 99%、失败触发 ≤ 2 次。
- 7 天稳定性验收：在线率 ≥ 99%、失败触发 ≤ 5 次、恢复成功率 ≥ 90%。

### 验收标准

- 1 小时验收 PASSED。
- 24 小时验收 PASSED。
- 7 天验收 PASSED。
- 全量回归通过。
- GateJ freeze snapshot 归档完成。
- README、AGENTS、CLAUDE 同步完成。
- Next: GateK-PLAN（不直接进入 AI 实现）。

### 回滚策略

- 验收失败：记录失败原因到 `paper_run_stability_checks`（status = FAILED），分析根因，修复后重新验收，不允许把 FAILED 写成 PASSED。
- 不回退已完成的 GateJ-1/2/3 功能代码。

---

## 总开工顺序

```
GateJ-1-WO（调度 + 心跳）
  ↓
GateJ-2-WO（日报 + 告警）
  ↓
GateJ-3-WO（恢复 + 稳定性结构）
  ↓
GateJ-FREEZE（连续运行验收 + 冻结）
  ↓
GateJ completed
  ↓
GateK-PLAN（AI 信号接入规划）
```

每个 WO 必须在前一个 WO 完成审查/提交后单独开工，不允许夹带。
