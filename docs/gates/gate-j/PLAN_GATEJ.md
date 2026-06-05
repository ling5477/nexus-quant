# GateJ 规划：Paper Trading 稳定运行

## 1. 背景

GateI 已完成虚拟币量化 V1 主链，Paper Trading 已具备创建、启动、停止、监控、曲线、复盘、异常停机最小闭环。当前 Paper Trading 可以手动创建和操作单次运行，但尚不具备连续调度、自动心跳、日报生成、告警通知、异常恢复和稳定性验收能力。

GateJ 的目标不是扩业务，而是验证 Paper Trading 能否稳定持续运行，为 GateK AI 信号接入建立稳定性门槛。

## 2. 目标

- Paper run 可按计划连续运行（定时调度）。
- Paper run 可定时产生运行快照（心跳记录）。
- Paper run 可生成日报（每日运行摘要）。
- Paper run 异常可被记录、告警、恢复。
- Paper run 可执行失败重试。
- Paper run 可连续运行至少 7 天作为验收目标。
- GateJ 完成后才允许进入 GateK AI 信号接入规划。

## 3. 不做范围

- 不接 AI。
- 不做 AI 信号。
- 不做 AI 自动交易。
- 不做 AI Paper Trading。
- 不做真实 LIVE 下单。
- 不调用真实交易所下单接口。
- 不做美股/A 股。
- 不做合约全量。
- 不做高频。
- 不做复杂因子平台。
- 不改交易核心状态机。
- 不改策略核心算法。
- 不改回测核心算法。

## 4. 子阶段拆分

### 4.1 GateJ-1：Paper run 调度与连续运行

**背景**：当前 Paper run 只能手动创建和启动，无法按计划自动触发。

**目标**：实现 Paper run 定时调度、触发记录和运行心跳。

**做什么**：
- 新增 `paper_run_schedules` 表，支持 cron 表达式调度。
- 新增 `paper_run_schedule_fires` 表，记录每次调度触发。
- 新增 `paper_run_heartbeats` 表，记录运行心跳。
- 新增调度 CRUD API。
- 新增心跳查询 API。
- 前端新增调度管理和心跳查看入口。
- 新增 E2E smoke。

**不做什么**：
- 不做日报。
- 不做告警。
- 不做恢复。
- 不做稳定性验收。
- 不接 AI。

**输入**：GateI completed 的 Paper Trading 运行闭环。

**输出**：Paper run 可按 cron 调度连续运行，心跳可查询。

**API 影响**：新增 Schedule API（CRUD + run-once）、Heartbeat API（list）。

**DB 影响**：新增 `paper_run_schedules`、`paper_run_schedule_fires`、`paper_run_heartbeats`。

**前端影响**：`/paper-trading` 新增调度计划 Tab、调度触发记录 Tab、心跳 Tab。

**测试要求**：
- 后端单元测试覆盖 schedule CRUD、fire record、heartbeat record。
- E2E smoke 覆盖调度创建、心跳查询。
- `mvn test` 通过、`npm run build` 通过、`npm run test:e2e` 通过。

**验收标准**：
- 调度可创建、启用、禁用、暂停。
- 调度触发有记录。
- 心跳可查询。
- 后端测试通过。
- 前端 build 通过。
- E2E 通过。

**回滚策略**：回退 migration、删除新增 API 和前端入口、恢复到 GateI completed 状态。

### 4.2 GateJ-2：运行监控、日报、告警

**背景**：Paper run 可连续运行后，需要日报和告警来监控运行健康度。

**目标**：实现日报生成、告警事件记录和确认。

**做什么**：
- 新增 `paper_run_daily_reports` 表。
- 新增 `paper_run_alerts` 表。
- 新增日报生成和查询 API。
- 新增告警查询和确认 API。
- 前端新增日报 Tab 和告警 Tab。
- 新增 E2E smoke。

**不做什么**：
- 不做恢复。
- 不做稳定性验收。
- 不做外部通知（邮件、Slack、钉钉）。
- 不接 AI。

**输入**：GateJ-1 completed 的调度与心跳基础。

**输出**：Paper run 日报可生成和查询，告警可记录和确认。

**API 影响**：新增 Daily Report API（generate + list）、Alert API（list + ack）。

**DB 影响**：新增 `paper_run_daily_reports`、`paper_run_alerts`。

**前端影响**：`/paper-trading` 新增日报 Tab、告警 Tab。

**测试要求**：
- 后端单元测试覆盖 daily report generate/list、alert create/list/ack。
- E2E smoke 覆盖日报生成、告警查看和确认。
- `mvn test` 通过、`npm run build` 通过、`npm run test:e2e` 通过。

**验收标准**：
- 日报可生成并查询。
- 告警可记录、查看、确认。
- 后端测试通过。
- 前端 build 通过。
- E2E 通过。

**回滚策略**：回退 migration、删除新增 API 和前端入口、恢复到 GateJ-1 completed 状态。

### 4.3 GateJ-3：异常恢复、失败重试、运行稳定性

**背景**：Paper run 连续运行中可能遇到异常，需要恢复和重试机制。

**目标**：实现异常恢复记录、失败重试和稳定性验收结构。

**做什么**：
- 新增 `paper_run_recovery_events` 表。
- 新增 `paper_run_stability_checks` 表。
- 新增恢复和重试 API。
- 新增稳定性验收生成和查询 API。
- 前端新增恢复事件 Tab 和稳定性验收 Tab。
- 新增 E2E smoke。

**不做什么**：
- 不做连续运行验收执行（由 GateJ-FREEZE 执行）。
- 不接 AI。
- 不做外部通知。

**输入**：GateJ-2 completed 的日报与告警基础。

**输出**：Paper run 异常恢复有记录，失败重试有记录，稳定性验收结构就绪。

**API 影响**：新增 Recovery API（list + recover + retry）、Stability Check API（generate + list）。

**DB 影响**：新增 `paper_run_recovery_events`、`paper_run_stability_checks`。

**前端影响**：`/paper-trading` 新增恢复事件 Tab、稳定性验收 Tab。

**测试要求**：
- 后端单元测试覆盖 recovery event create/list、stability check generate/list。
- E2E smoke 覆盖恢复事件查看、稳定性验收生成。
- `mvn test` 通过、`npm run build` 通过、`npm run test:e2e` 通过。

**验收标准**：
- 恢复事件可记录和查询。
- 失败重试可触发和记录。
- 稳定性验收可生成和查询。
- 后端测试通过。
- 前端 build 通过。
- E2E 通过。

**回滚策略**：回退 migration、删除新增 API 和前端入口、恢复到 GateJ-2 completed 状态。

### 4.4 GateJ-FREEZE：连续运行验收与冻结

**背景**：GateJ-1/2/3 功能就绪后，需要实际执行连续运行验收。

**目标**：Paper run 连续运行至少 7 天，验收通过后冻结 GateJ。

**做什么**：
- 执行 1 小时短验收。
- 执行 24 小时中验收。
- 执行 7 天稳定性验收。
- 记录验收结果到 `paper_run_stability_checks`。
- 冻结 GateJ 文档到 `docs/gates/gate-j/`。
- 同步 README、AGENTS、CLAUDE。

**不做什么**：
- 不新增功能代码。
- 不接 AI。
- 不进入 GateK 实现。

**输入**：GateJ-3 completed 的全部功能就绪。

**输出**：GateJ completed，freeze snapshot 归档，Next: GateK-PLAN。

**API 影响**：无新增。

**DB 影响**：无新增。

**前端影响**：无新增。

**测试要求**：
- 全量回归：`mvn test`、`npm run build`、`npm run test:e2e`、Python pytest/mypy/ruff。
- 连续运行验收结果记录。

**验收标准**：
- 1 小时短验收通过。
- 24 小时中验收通过。
- 7 天稳定性验收通过。
- 后端测试通过。
- 前端 build 通过。
- E2E 通过。
- GateJ freeze snapshot 归档完成。

**回滚策略**：验收失败则记录失败原因，修复后重新验收；不回退功能代码。

## 5. 完成标准

- Paper run 可连续调度运行。
- 运行调度有状态记录。
- 运行心跳可查询。
- 运行日报可查询。
- 告警事件可查询。
- 失败重试有记录。
- 异常恢复有记录。
- emergency stop 仍可生效。
- 连续运行验收结果可归档。
- 后端测试通过。
- 前端 build 通过。
- E2E 通过。
- GateJ 不包含 AI。
- GateJ 完成后 Next 是 GateK-PLAN，不是直接 AI 实现。

## 6. 风险

- 连续运行 7 天依赖本地环境稳定性（PostgreSQL、网络、进程不中断）。
- 调度依赖后端进程持续运行；如果后端重启，调度状态需要恢复。
- 心跳间隔和告警阈值需要在实现时确定合理默认值。

## 7. 进入 GateK 条件

- GateJ 全部子阶段完成。
- 7 天稳定性验收通过。
- GateJ freeze snapshot 归档。
- GateK-PLAN 只能规划 AI 信号接入，不能直接实现 AI 功能。
