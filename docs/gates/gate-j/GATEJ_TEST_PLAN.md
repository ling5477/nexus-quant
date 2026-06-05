# GateJ 测试规划

本文只做测试规划，不写测试实现。

## 1. 后端单元测试

覆盖范围：

### GateJ-1 单元测试

- `PaperRunScheduleServiceTest`
  - schedule create 正常创建
  - schedule create 参数校验（cron 无效、paperRunId 不存在）
  - schedule list 按 paperRunId 过滤
  - schedule status update（ENABLED → DISABLED → PAUSED）
  - schedule run-once 正常触发
  - schedule run-once 状态冲突（DISABLED 时拒绝）
- `PaperRunHeartbeatServiceTest`
  - heartbeat run-once 正常记录
  - heartbeat run-once 非 RUNNING 状态拒绝
  - heartbeat list 按 paperRunId 分页

### GateJ-2 单元测试

- `PaperRunDailyReportServiceTest`
  - daily report generate 正常生成
  - daily report generate 幂等（同日覆盖）
  - daily report list 按 paperRunId 分页
- `PaperRunAlertServiceTest`
  - alert create 正常写入
  - alert list 按 severity/status 过滤
  - alert ack 正常确认
  - alert ack 幂等（重复确认不报错）
  - alert ack RESOLVED 状态拒绝

### GateJ-3 单元测试

- `PaperRunRecoveryServiceTest`
  - recover 正常记录
  - recover 状态冲突（STOPPED 时拒绝）
  - retry-failed-step 正常记录
  - recovery event list 按 status 过滤
- `PaperRunStabilityCheckServiceTest`
  - stability check generate 正常生成
  - stability check generate 幂等（同窗口覆盖）
  - stability check list 按 status 过滤
  - stability check 参数校验（end <= start 拒绝）

## 2. 后端集成测试

覆盖范围：

- Flyway migration validate：确认新增 migration 可正常应用。
- JDBC repository：确认 7 张新表的 CRUD 操作正确。
- API controller smoke：确认所有新增 endpoint 返回正确 HTTP 状态码。

## 3. 前端 build

```powershell
Set-Location frontend
npm run build
```

- TypeScript 编译通过。
- Vite 构建通过。
- 不引入新的编译错误。

## 4. E2E 测试矩阵

### paper-schedule-smoke

- 打开 `/paper-trading`。
- 进入调度计划 Tab。
- 创建调度计划。
- 验证调度出现在列表中。
- 禁用调度。
- 验证状态变为 DISABLED。

### paper-heartbeat-smoke

- 打开 `/paper-trading`。
- 选择一个 RUNNING 的 Paper run。
- 进入心跳 Tab。
- 触发一次心跳。
- 验证心跳记录出现在列表中。

### paper-daily-report-smoke

- 打开 `/paper-trading`。
- 选择一个 Paper run。
- 进入日报 Tab。
- 生成一份日报。
- 验证日报出现在列表中。

### paper-alert-smoke

- 打开 `/paper-trading`。
- 选择一个 Paper run。
- 进入告警 Tab。
- 验证告警列表可加载（空态或有数据）。
- 如有 OPEN 告警，执行确认操作。
- 验证状态变为 ACKED。

### paper-recovery-smoke

- 打开 `/paper-trading`。
- 选择一个 Paper run。
- 进入恢复事件 Tab。
- 触发一次恢复。
- 验证恢复事件出现在列表中。

### paper-stability-check-smoke

- 打开 `/paper-trading`。
- 选择一个 Paper run。
- 进入稳定性验收 Tab。
- 生成一次稳定性验收。
- 验证验收结果出现在列表中。

## 5. 本地启动验证

### 环境要求

- PostgreSQL 5432 可用。
- backend local profile 启动成功。
- `/actuator/health` 返回 UP。
- Flyway migration 全部应用。

### 验证步骤

1. 启动 PostgreSQL（本地或 docker-compose）。
2. `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local`
3. 确认 `/actuator/health` 返回 UP。
4. 确认 Flyway 版本到最新。
5. 准备 Paper run fixture（通过 E2E fixture 链路或手动 API 调用）。
6. `npm run test:e2e`

## 6. 连续运行验收

### 1 小时短验收

- Paper run 连续运行 1 小时。
- 心跳每 5 分钟记录一次（至少 12 条）。
- 无 CRITICAL 告警。
- 无 FAILED 调度触发。
- 生成稳定性验收，状态为 PASSED。

### 24 小时中验收

- Paper run 连续运行 24 小时。
- 心跳每 5 分钟记录一次（至少 288 条）。
- 日报可生成。
- 无 CRITICAL 告警。
- 失败触发 ≤ 2 次。
- 在线率 ≥ 99%。
- 生成稳定性验收，状态为 PASSED。

### 7 天稳定性验收

- Paper run 连续运行 7 天。
- 心跳每 5 分钟记录一次（至少 2016 条）。
- 日报每天生成（7 份）。
- 无 CRITICAL 告警。
- 失败触发 ≤ 5 次。
- 恢复成功率 ≥ 90%。
- 在线率 ≥ 99%。
- 生成稳定性验收，状态为 PASSED。

### 验收失败处理

- 记录失败原因到 `paper_run_stability_checks`（status = FAILED）。
- 分析根因，修复后重新验收。
- 不允许把 FAILED 写成 PASSED。

### 验收通过冻结

- 7 天验收 PASSED 后，执行 GateJ-FREEZE。
- 冻结 GateJ 文档到 `docs/gates/gate-j/`。
- 同步 README、AGENTS、CLAUDE。
- Next: GateK-PLAN。

## 7. 回归测试命令

每轮必须保留的验证命令：

```powershell
# 后端
mvn -f backend/pom.xml test

# 前端
Set-Location frontend
npm run build
npm run test:e2e

# Python
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

如果只改文档，可以不跑全量测试，但必须在 WORKLOG.md / TESTING.md 中写清未跑原因。
