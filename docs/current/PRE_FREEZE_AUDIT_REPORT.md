# PRE-FREEZE-CODE-AUDIT Report

> 审查日期：2026-05-22
> 审查阶段：GateJ-3-WO completed / DOC-CLEAN-2 completed → GateJ-FREEZE 之前的前置审查
> 范围限定：只审查，不修复，不新增业务代码、API、migration 或前端页面实现，不接 AI，不创建 `docs/gates/gate-j/`

## 1. Audit background

GateJ-1-WO / GateJ-2-WO / GateJ-3-WO 已先后完成，DOC-CLEAN-2 已收口当前文档结构。GateJ-FREEZE 计划承担 1h/24h/7d 连续运行验收与冻结。本轮 PRE-FREEZE-CODE-AUDIT 是 GateJ-FREEZE 之前的前置审查，目的是在进入连续运行验收前发现可能导致验收失效的代码、文档、实现真实性、运行链路与边界问题。

## 2. Current project state

| 项 | 状态 |
| --- | --- |
| GateH | completed (`docs/gates/gate-h/`) |
| GateI | completed (`docs/gates/gate-i/`) |
| GateJ-PLAN | completed |
| GateJ-1-WO | completed |
| GateJ-2-WO | completed |
| GateJ-3-WO | completed |
| DOC-CLEAN-2 | completed |
| GateJ overall | NOT completed |
| GateJ-FREEZE | NOT started |
| GateK | NOT started |
| AI | NOT started |
| `docs/gates/gate-j/` | NOT existing（符合规则）|

## 3. Audit scope

本轮覆盖所要求的 14 类：

1. 文档状态一致性
2. 实现真实性与文档一致性
3. 后端模块边界
4. 数据库 / Flyway / 注释 / 约束 / 索引
5. Paper Trading 主链完整性
6. Schedule / Heartbeat / Report / Alert / Recovery / Stability 运行链
7. API 命名、DTO、错误处理、分页、幂等
8. 前端页面与数据层结构
9. E2E 稳定性与测试数据幂等
10. Python research 模块
11. Paper / LIVE 隔离
12. AI 未接入与未来 AI 接入边界
13. GateJ-FREEZE 验收准备度
14. 技术债与非阻塞风险分级

## 4. Audit method

- 阅读所有必读文档：`README.md`、`AGENTS.md`、`CLAUDE.md`、`docs/README.md`、`docs/DOC_RULES.md`、`docs/current/*.md`、`docs/gates/gate-h/README.md`、`docs/gates/gate-i/README.md`、`docs/gates/gate-i/FREEZE_SUMMARY.md`。
- 后端代码层面：枚举 Flyway migration V1–V25、Paper Controller、PaperTradingApiService、PaperRunScheduleService 等 service 单元测试、应用服务层的依赖关系。
- 前端代码层面：枚举 `frontend/src/pages/paper-trading/PaperTradingPage.tsx` 内 Tabs/columns、`frontend/src/api/paper-trading.ts` 所有 client 方法、`frontend/tests/e2e/*.spec.ts` 所有 spec。
- Python：枚举 `research/py/src/nq_research/cli.py` 与 `pyproject.toml`，确认 offline 边界。
- 边界扫描：在 `backend/`、`frontend/src/` 全量搜索 `openai/anthropic/llm/chatgpt/gpt-4/claude-3/ai-signal/ai-trading`，确认无 AI 接入；扫描 `backend/nq-research/.../application/paper/**` 对 `TradingAdapter / placeOrder / cancelOrder / RestTemplate / WebClient` 引用。
- 验证基线：本轮执行 `mvn -f backend/pom.xml test`（BUILD SUCCESS）与 `npm run build`（通过）。`npm run test:e2e` 与 `python -m pytest/mypy/ruff` 本轮未实际执行（详见第 13 / 21 节与 P1）。

## 5. Document consistency audit

### 5.1 阶段状态一致性

| 文件 | 阶段表述 | 与事实是否一致 |
| --- | --- | --- |
| `README.md` | `GateJ-3-WO completed / Next: GateJ-FREEZE / AI not started` | 一致 |
| `AGENTS.md` | `Current stage: GateJ-3-WO completed, next GateJ-FREEZE` | 一致 |
| `CLAUDE.md` | 同上 | 一致 |
| `docs/README.md` | `GateJ-3-WO completed / Next: GateJ-FREEZE` | 一致 |
| `docs/current/README.md` | `GateJ-3-WO completed / Next: GateJ-FREEZE` | 一致 |
| `docs/current/STATUS.md` | `GateJ-3-WO 已完成 / Next: GateJ-FREEZE` | 一致 |
| `docs/current/ROADMAP.md` | `GateJ-3-WO completed / Next: GateJ-FREEZE` | 一致 |
| `docs/current/WORKLOG.md` | 包含 GateH/GateI 全部 WO 与 GateJ-1/2/3-WO + DOC-CLEAN-2 完整日志 | 一致 |
| `docs/current/TESTING.md` | 含 GateJ-1/2/3 验证表 | 一致 |

错误表述扫描（搜索全部文档）：
- `GateJ completed` —— 未出现在 `docs/current/`、`README.md`、`AGENTS.md`、`CLAUDE.md` 用于阶段状态的位置。
- `GateK started` —— 未出现。
- `AI started` / `AI signal started` —— 未出现。
- `GateJ-FREEZE completed` —— 未出现。
- `docs/gates/gate-j/` —— 不存在（已用 `Glob` 确认）。
- `docs/current/PLAN_GATEH.md` / `docs/current/GATEH_*` / `docs/current/PLAN_GATEI.md` / `docs/current/GATEI_*` —— 已被 DOC-CLEAN-2 删除，未在 `docs/current/` 中重复保留。

结论：阶段状态一致，无错误表述，符合 PRE-FREEZE-CODE-AUDIT 应有的事实分布。

### 5.2 docs/current 与 docs/gates 分布

```
docs/current/
├── README.md, STATUS.md, ROADMAP.md, WORKLOG.md, TESTING.md
├── API.md, DB_SCHEMA.md, MODULES.md, ARCHITECTURE.md, RUNBOOK.md
├── PLAN_GATEJ.md, GATEJ_API_PLAN.md, GATEJ_DB_PLAN.md
├── GATEJ_FRONTEND_PLAN.md, GATEJ_TEST_PLAN.md, GATEJ_WORK_ORDER.md
└── DOC_CLEAN_REPORT.md
docs/gates/
├── gate-a/ ... gate-g/ (历史卷宗)
├── gate-h/ (冻结卷宗)
└── gate-i/ (冻结卷宗)
```

`docs/gates/gate-j/` 不存在，符合规则。

## 6. Implementation reality audit

围绕"文档写了代码是否实现 / 代码实现了文档是否记录 / 业务主链是否存在缺口"展开。详细核对表见第 10 / 11 / 12 / 13 节。

总体结论：
- 文档声明的 GateI-3/4 + GateJ-1/2/3 全部能力，后端 Controller、Application service、JDBC repository、Flyway migration 实际存在且单元测试覆盖。
- 代码实际暴露的全部端点都已记录在 `docs/current/API.md`。
- 代码实际新增的全部表都已记录在 `docs/current/DB_SCHEMA.md`。
- 前端 `/paper-trading` 详情抽屉的 15 个 Tab 与后端能力一一对应。
- 不存在"文档写了但代码缺失"的能力。
- 不存在"代码实现但文档不记录"的越界能力（已搜索 AI / LIVE 关键词，均无业务代码命中）。
- 业务主链层面：GateJ-FREEZE 1h/24h/7d 验收所需的全部基础能力都存在（创建/启动/停止 Paper run、调度 CRUD + run-once、心跳 run-once、日报 generate、告警 create/ack/resolve、recover/retry、stability check generate、monitor run-once），第一版口径已在文档明确（fire 固定 SUCCEEDED、cron 字段数校验、heartbeat lag 阈值 300s、5 分钟去重、uptime_ratio 粗略口径）。

## 7. Document-to-code traceability

| 文档声明 | 所在文档 | 实际代码位置 | 测试覆盖 | 结论 | 风险级别 |
| --- | --- | --- | --- | --- | --- |
| Paper run create/start/stop/list/detail | `API.md` GateI-3 | `PaperTradingController.create/start/stop/list/detail` | `PaperTradingRunServiceTest`(4)、`paper-trading-run-smoke` | 一致 | - |
| Paper orders/trades/positions 查询 | `API.md` GateI-3 | `PaperTradingController.orders/trades/positions` | E2E 空态断言 | 一致（第一版为空列表，文档已说明） | - |
| Risk results 查询 + run-once | `API.md` GateI-4 | `PaperTradingController.riskResults/runRiskOnce` | `PaperTradingMonitorServiceTest`(5)、`paper-trading-run-smoke` | 一致 | - |
| Equity curve / Position curve / Replay | `API.md` GateI-4 | `PaperTradingController.equityCurve/positionCurve/replay` | E2E 空态断言 | 一致 | - |
| Emergency stop + list | `API.md` GateI-4 | `PaperTradingController.emergencyStop/emergencyStops` | `PaperTradingMonitorServiceTest`、`paper-trading-run-smoke` | 一致 | - |
| Schedule CRUD + status + run-once + fires | `API.md` GateJ-1 | `PaperTradingScheduleController` 全部 6 个端点 | `PaperRunScheduleServiceTest`(11)、`paper-trading-schedule-smoke` | 一致 | - |
| Heartbeat list + run-once | `API.md` GateJ-1 | `PaperTradingController.heartbeats/runHeartbeatOnce` | `PaperRunScheduleServiceTest` 中 heartbeat 用例、`paper-trading-schedule-smoke` | 一致 | - |
| Daily report list + generate + detail | `API.md` GateJ-2 | `PaperTradingController.dailyReports/generateDailyReport/dailyReportDetail` | `PaperRunMonitorServiceTest`(12)、`paper-trading-daily-report-smoke` | 一致 | - |
| Alert list + create + ack + resolve | `API.md` GateJ-2 | `PaperTradingController.alerts/createAlert/ackAlert/resolveAlert` | `PaperRunMonitorServiceTest`、`paper-trading-alert-smoke` | 一致 | - |
| Recovery events list + recover + retry-failed-step | `API.md` GateJ-3 | `PaperTradingController.recoveryEvents/recover/retryFailedStep` | `PaperRunRecoveryServiceTest`(9)、`paper-trading-recovery-smoke` | 一致 | - |
| Stability check list + generate + detail | `API.md` GateJ-3 | `PaperTradingController.stabilityChecks/generateStabilityCheck/stabilityCheckDetail` | `PaperRunStabilityCheckServiceTest`(10)、`paper-trading-stability-check-smoke` | 一致 | - |
| Monitor run-once（HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警） | `API.md` GateJ-3 | `PaperTradingController.runMonitorOnce` | `PaperRunMonitorRunServiceTest`(8)、`paper-trading-recovery-smoke` 中守护断言 | 一致 | - |
| 7 张 GateJ 新表 | `DB_SCHEMA.md` GateJ-1/2/3 | `V23/V24/V25` Flyway migration | DB 测试 + 启动验证 | 一致 | - |

结论：100% 一致，无 P0 / P1 gap。

## 8. Code-to-document traceability

| 实际代码能力 | 代码位置 | API.md | DB_SCHEMA.md | TESTING.md | WORKLOG.md | 结论 | 风险级别 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 35 个 Paper Trading 端点 | `PaperTradingController.java` + `PaperTradingScheduleController.java` | 全部记录 | - | - | GateJ-1/2/3 三轮 WORKLOG 详细记录 | 一致 | - |
| Flyway V23/V24/V25 | `backend/nq-infra/.../db/migration/` | - | 全部记录 | GateJ-1/2/3 验证表注明 Flyway 当前版本 23/24/25 | 同上 | 一致 | - |
| PaperTradingMonitor / PaperRunSchedule / PaperRunMonitor / PaperRunRecovery / PaperRunStabilityCheck / PaperRunMonitorRun Service | `nq-research/.../application/paper/*Service.java` | 委派后体现在 Controller 端点 | - | 单元测试统计写入 TESTING | WORKLOG 写明每轮新增的 service 与测试用例数 | 一致 | - |
| 15 个前端 Tab | `PaperTradingPage.tsx` | API.md 各 endpoint 体现 | - | E2E 矩阵列出 | 一致 | - | - |
| 22 个 Playwright spec | `frontend/tests/e2e/*.spec.ts` | - | - | TESTING 记录每轮 passed/skipped 数 | WORKLOG 写明 spec 名 | 一致 | - |

未发现"代码越界"的能力：
- 后端 `nq-research/.../application/paper/**` 未引用 `TradingAdapter / placeOrder / cancelOrder / HttpClient / RestTemplate / WebClient`。
- 全仓库（backend / frontend/src）未搜到 AI / LLM / openai / anthropic / chatgpt / gpt-4 / claude-3 / ai-signal / ai-trading 业务代码命中（仅 `.agents/skills/` 文档命中，与代码无关）。

## 9. Business-chain gap analysis

GateJ-FREEZE 1h/24h/7d 连续运行验收所需能力：

| 能力 | 代码 / 文档 | 是否就绪 | 备注 |
| --- | --- | --- | --- |
| Paper run 启停 | ✓ | 就绪 | `POST /api/paper-trading/runs/{id}/start|stop` |
| 调度 CRUD + run-once | ✓ | 就绪（手动 run-once） | 第一版无后台常驻调度器；用 run-once 模拟连续触发 |
| Heartbeat run-once | ✓ | 就绪 | 同上，用 run-once 周期性触发 |
| Daily report generate | ✓ | 就绪 | 按 (paperRunId, reportDate) 幂等 |
| Alert create / ack / resolve | ✓ | 就绪 | 仅落库，不外发 |
| Recovery + retry-failed-step | ✓ | 就绪 | 每次产生新事件，不幂等 |
| Stability check generate | ✓ | 就绪 | 按 (paperRunId, window_start, window_end) 幂等 |
| Monitor run-once（HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警） | ✓ | 就绪 | 5 分钟去重 |
| Emergency stop | ✓ | 就绪 | 仅作用于 SIM/Paper |
| 验收结果落库 | ✓ | 就绪 | `paper_run_stability_checks` 表 |
| E2E 主链 spec | ✓ | 就绪 | 6 个 GateJ smoke 全部存在 |
| 验收记录模板 | × | **缺失** | 已在本轮新增 `GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md` |

唯一缺口：缺少 GateJ-FREEZE 验收记录模板。本轮已新增 `docs/current/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`（属文档，不改代码），不阻塞。

## 10. API reality checklist

| Endpoint | 文档 | Controller | DTO | Service | 测试/E2E | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| `/api/paper-trading/runs` (GET/POST) | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}` (GET) | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/start` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/stop` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/orders|trades|positions` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/risk-results[/run-once]` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/equity-curve` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/position-curve` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/replay` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/emergency-stop[s]` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/schedules` (GET/POST) | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/schedules/{id}` (GET) | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/schedules/{id}/status` (PATCH) | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/schedules/{id}/run-once` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/schedules/{id}/fires` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/heartbeats[/run-once]` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/daily-reports[/generate|/{reportId}]` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/alerts[/{alertId}/ack|/resolve]` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/recovery-events[/recover|/retry-failed-step]` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/stability-checks[/generate|/{id}]` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| `/api/paper-trading/runs/{id}/monitor/run-once` | ✓ | ✓ | ✓ | ✓ | ✓ | OK |

无缺失 endpoint。

## 11. DB reality checklist

| 表名 | DB_SCHEMA.md | Migration | COMMENT ON TABLE | COMMENT ON COLUMN | CHECK | FK | Index/Unique | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `paper_trading_runs` | ✓ | V21 | ✓ | ✓ | status / trade_env | publish / strategy_version | ✓ | OK |
| `paper_trading_orders` | ✓ | V21 | ✓ | ✓ | side / status | run | ✓ | OK |
| `paper_trading_trades` | ✓ | V21 | ✓ | ✓ | side | order / run | ✓ | OK |
| `paper_trading_positions` | ✓ | V21 | ✓ | ✓ | - | run | UNIQUE(run,symbol) | OK |
| `paper_risk_check_results` | ✓ | V22 | ✓ | ✓ | status / severity | run | ✓ | OK |
| `equity_curve_snapshots` | ✓ | V22 | ✓ | ✓ | - | run | ✓ | OK |
| `position_curve_snapshots` | ✓ | V22 | ✓ | ✓ | - | run | ✓ | OK |
| `trade_replay_records` | ✓ | V22 | ✓ | ✓ | - | run | ✓ | OK |
| `emergency_stop_events` | ✓ | V22 | ✓ | ✓ | trigger_type / status | run | ✓ | OK |
| `paper_run_schedules` | ✓ | V23 | ✓ | ✓ | status | run | ✓（含 partial idx） | OK |
| `paper_run_schedule_fires` | ✓ | V23 | ✓ | ✓ | status | schedule / run | ✓ | OK |
| `paper_run_heartbeats` | ✓ | V23 | ✓ | ✓ | status | run | ✓ | OK |
| `paper_run_daily_reports` | ✓ | V24 | ✓ | ✓ | status | run | UNIQUE(run,date) | OK |
| `paper_run_alerts` | ✓ | V24 | ✓ | ✓ | severity / status | run | ✓ | OK |
| `paper_run_recovery_events` | ✓ | V25 | ✓ | ✓ | recovery_type / status | run | ✓ | OK |
| `paper_run_stability_checks` | ✓ | V25 | ✓ | ✓ | status / window / uptime | run | UNIQUE(run,window_start,window_end) | OK |

Flyway 版本号 V1–V25 连续，未修改历史 migration。所有新增表 / 字段均有 COMMENT；JSONB 注释明确不保存密钥/token/cookie。

## 12. Frontend reality checklist

`/paper-trading` 页面 `PaperTradingPage.tsx` 实际 Tab keys：`orders / trades / positions / snapshots / risk-results / equity-curve / position-curve / replay / emergency-stops / schedules / heartbeats / daily-reports / alerts / recovery-events / stability-checks`，共 15 个。

| 前端能力 | 文档声明 | Page/Component | API client | hook | E2E | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| /paper-trading 主页 | ✓ | ✓ | - | - | ✓ | OK |
| Paper run 基础 Tab（orders/trades/positions/snapshots） | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 风控结果 Tab | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 资金/持仓曲线 Tab | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 交易复盘 Tab | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 异常停机 Tab | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 调度计划 Tab + 触发记录 | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 心跳 Tab | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 日报 Tab | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 告警 Tab（含 ack / resolve） | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 恢复事件 Tab（含 recover / retry） | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| 稳定性验收 Tab（含 generate） | ✓ | ✓ | ✓ | ✓ | ✓ | OK |
| Monitor run-once 入口 | ✓ | ✓ | ✓ | ✓ | ✓（recovery smoke 中） | OK |

`frontend/src/api/paper-trading.ts` 暴露 35 个 client 方法，与后端 35 个 endpoint 一一对应。

## 13. Testing reality checklist

| 测试项 | 文档声明 | 本次实际执行 | 实际结果 | 是否一致 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `mvn -f backend/pom.xml test` | GateJ-3 通过（BUILD SUCCESS，含 PaperRunRecovery/Stability/Monitor 27 新增用例） | 本轮已执行 | BUILD SUCCESS，0 failures、0 errors | 一致 | OK |
| `npm run build` | GateJ-3 通过 | 本轮已执行 | Vite 通过，dist/index.js ≈ 1.48 MB，仍有 chunk > 500 kB 警告 | 一致 | OK |
| `npm run test:e2e` | GateJ-3 24 passed / 1 skipped | 本轮**未实际执行** | 沿用 GateJ-3 通过基线 | - | P1（GateJ-FREEZE 入场前必须重跑） |
| `python -m pytest -q` | BASELINE-FIX 通过 | 本轮**未实际执行**（当前 shell 仅 WindowsApps stub） | 沿用既有通过基线 | - | P1 |
| `python -m mypy src` | BASELINE-FIX 通过 | 本轮**未实际执行**（同上） | 沿用既有通过基线 | - | P1 |
| `python -m ruff check .` | BASELINE-FIX 通过 | 本轮**未实际执行**（同上） | 沿用既有通过基线 | - | P1 |
| `git status --short` | - | 本轮已执行 | 仅 docs/current 与根目录入口文档变更，无业务代码、migration、API 实现、前端页面实现变更 | - | OK |

未执行原因：
- E2E：需要本地 PostgreSQL 5432 + 后端 `local` profile + 种子 `accounts.account_id=3001`；本轮为审查窗口，不在干净本地实例上启动后端。沿用 GateJ-3-WO 已通过的 24 passed / 1 skipped 基线。
- Python：当前 shell 解析到的 `python.exe` 仅为 `C:\Users\lingy\AppData\Local\Microsoft\WindowsApps\python.exe`（Windows App Execution Alias stub），执行任何 `python -m ...` 均返回 exit 49。无真实 Python 解释器，无法在本轮重跑。沿用 BASELINE-FIX-2 / GateJ-3 既有通过基线。

## 14. Backend module boundary audit

- `nq-api`：`PaperTradingController` / `PaperTradingScheduleController` 仅做 DTO 转换 + 委派 `PaperTradingApiService`，无直接 SQL 调用。
- `nq-core`：策略、回测、交易核心域；本轮未变更。
- `nq-research`：承载 Paper Trading 全部 domain / port / service（`PaperTradingRunService` / `PaperTradingMonitorService` / `PaperRunScheduleService` / `PaperRunMonitorService` / `PaperRunRecoveryService` / `PaperRunStabilityCheckService` / `PaperRunMonitorRunService`），不依赖 JDBC。
- `nq-infra`：承载 16 个 JDBC repository 实现，未泄漏业务规则到 SQL。
- adapter（okx / binance）：未被 Paper Trading 调度 / 恢复 / 监控链路引用（搜索 `TradingAdapter / placeOrder / cancelOrder / HttpClient / RestTemplate / WebClient` 在 `nq-research/.../application/paper/**` 无命中）。
- archunit 包边界测试通过（`ModuleBoundaryArchTest` 6 用例 + `PackageBoundaryArchTest` 1 用例全部 0 failures）。

结论：模块边界完整，无反向依赖、无 SQL 泄漏到 nq-api 或 nq-core。

## 15. Database and Flyway audit

- 版本号 V1–V25 连续，无空号，无修改历史 migration。
- GateJ-1（V23）/ GateJ-2（V24）/ GateJ-3（V25）新增表均含 `COMMENT ON TABLE`，新增字段均含 `COMMENT ON COLUMN`。
- 状态字段全部有 `CHECK` 约束（`paper_run_schedules.status` ∈ ENABLED/DISABLED/PAUSED；`paper_run_schedule_fires.status` ∈ RUNNING/SUCCEEDED/FAILED/SKIPPED；`paper_run_heartbeats.status` ∈ OK/LAGGING/STOPPED/UNKNOWN；`paper_run_daily_reports.status` ∈ GENERATED/PARTIAL/FAILED；`paper_run_alerts.severity` ∈ LOW/MEDIUM/HIGH/CRITICAL；`paper_run_alerts.status` ∈ OPEN/ACKED/RESOLVED；`paper_run_recovery_events.recovery_type` 4 值；`paper_run_recovery_events.status` ∈ STARTED/SUCCEEDED/FAILED/SKIPPED；`paper_run_stability_checks.status` ∈ PASSED/FAILED/PARTIAL；`paper_run_stability_checks` 额外有 window/uptime CHECK）。
- 关键唯一约束齐全：`paper_run_daily_reports (paper_run_id, report_date)`、`paper_run_stability_checks (paper_run_id, check_window_start, check_window_end)`、`paper_trading_positions (paper_run_id, symbol)`。
- 全部外键统一指向 `paper_trading_runs.paper_run_id` 或 `paper_run_schedules.schedule_id`，无悬空 FK。
- JSONB 字段注释明确"不保存密钥/token/cookie"（V23/V24/V25 全部 JSONB 列均有此注释）。
- 索引覆盖主查询模式：按 `paper_run_id + 时间倒序`、按 `status`、按 `severity`、按 `next_fire`（partial idx）等。

结论：DB 层无 P0。

## 16. Paper Trading main chain audit

```
publish record
  → strategy_version snapshot
    → dataset snapshot
      → paper_trading_runs (CREATED → RUNNING → STOPPED / FAILED)
        → start / stop
        → schedule (ENABLED/DISABLED/PAUSED) → fire (SUCCEEDED 第一版固定)
        → heartbeat (OK/LAGGING/STOPPED/UNKNOWN)
        → daily_report (GENERATED/PARTIAL/FAILED, 按日幂等)
        → alert (OPEN/ACKED/RESOLVED)
        → recovery_event (STARTED/SUCCEEDED/FAILED/SKIPPED)
        → stability_check (PASSED/FAILED/PARTIAL, 按窗口幂等)
        → emergency_stop (MANUAL/RISK_LIMIT/SYSTEM_ERROR → APPLIED/FAILED)
        → monitor/run-once: HEARTBEAT_LAG (HIGH) + SCHEDULE_FIRE_FAILED (MEDIUM) 自动告警 (5min dedupe)
```

所有节点均有：domain 类 + port + JDBC 实现 + service + controller endpoint + 单元测试 + E2E smoke。`orders/trades/positions` 第一版为空列表，已在文档明确说明（撮合回写为后续 Gate 范围）。

所有 run-once 均不触发真实交易所下单：schedule run-once 仅写 fire 记录、heartbeat run-once 仅写 heartbeat 记录、recovery / retry 仅写事件、monitor run-once 仅写 alert、daily report generate 仅写日报、stability check generate 仅做窗口聚合，均不引用 TradingAdapter。

结论：主链完整、无 P0。

## 17. Schedule / heartbeat / report / alert / recovery / stability audit

### Schedule
- cron_expr 第一版仅做 5/6/7 字段数校验（第 14 单元测试覆盖）。**P2 已知风险**：未做完整 cron 语义校验。
- status ENABLED / DISABLED / PAUSED 完整，转换在 `PaperRunScheduleService.updateScheduleStatus` 实现并测试。
- fire status 第一版固定 `SUCCEEDED`（未引入后台常驻调度器自动触发）。**P2 已知风险**：与 GateJ-FREEZE "连续运行验收" 存在张力，但 monitor 守护可检测 `SCHEDULE_FIRE_FAILED`，验收期间用 run-once 模拟连续触发并在 ACCEPTANCE 文档中明确口径。

### Heartbeat
- heartbeat_time / lag_seconds 字段齐全。
- status OK / LAGGING / STOPPED / UNKNOWN 完整，映射规则在 `PaperRunScheduleService.runHeartbeatOnce` 实现并测试。
- monitor 守护使用最新心跳的 `created_at` 判断 lag（阈值 300s）。**P2 已知风险**：阈值未提供运行时配置入口。

### Daily report
- `report_date` 可空，缺省 UTC 当日（`PaperRunMonitorService.generateDailyReport` 测试覆盖）。
- `(paper_run_id, report_date)` 唯一，幂等通过 ON CONFLICT。
- `total_equity / daily_pnl / max_drawdown` 第一版占位 0（文档已明确）；alert_count 实时统计。**P2 已知风险**：未与 equity_curve_snapshots 联动。

### Alert
- alert_type / severity / status 完整。
- ack 幂等、resolve 幂等，状态转换冲突返回 409。
- 自动告警只落库，不外发通知。5 分钟去重按 alert_type（**P2 已知风险**：不做 fire_id / event 维度去重）。

### Recovery
- recover / retry-failed-step 均根据 Paper run 状态映射：RUNNING/CREATED → SUCCEEDED，STOPPED → SKIPPED；第一版不幂等（每次产生新事件）。
- 不调用真实交易所下单接口。

### Stability check
- window 唯一（按 `(paper_run_id, check_window_start, check_window_end)` ON CONFLICT 幂等）。
- uptime_ratio 第一版粗略口径：PASSED=1.0 / PARTIAL=0.9 / FAILED 有心跳=0.5 / 无心跳=0（文档已明确）。
- PASSED / PARTIAL / FAILED 判定规则在 `PaperRunStabilityCheckService` 实现并 10 个单元用例覆盖。
- 第一版口径在 controller swagger 与 DB 表注释中均明确"非 GateJ-FREEZE 最终验收"。

## 18. API audit

- 命名统一：全部 endpoint 在 `/api/paper-trading/` 下，按 `runs` / `schedules` 拆分；动作端点统一用 `/run-once`、`/start`、`/stop`、`/generate`、`/ack`、`/resolve`、`/recover`、`/retry-failed-step`、`/monitor/run-once`，无重复或语义冲突。
- DTO 字段一致：`PaperRunScheduleResponse / FireResponse / HeartbeatResponse / DailyReportResponse / AlertResponse / RecoveryEventResponse / StabilityCheckResponse / MonitorRunOnceResponse` 全部走 `from(domain)` 转换。
- 列表接口第一版均不分页；文档已说明，符合 GateJ 第一版范围。
- 状态变更 endpoint（start/stop、schedule status、alert ack/resolve）语义明确，错误码统一：404（不存在） / 409（状态冲突） / 400（参数无效）。
- 错误响应统一通过 `ApiErrorResponse`。
- 无 endpoint 越界触发 LIVE：所有写操作均委派到 `PaperTradingApiService` → research 应用服务，未引用 TradingAdapter。
- 无 endpoint 触发 AI：搜索无 AI 相关 endpoint。

## 19. Frontend audit

- `PaperTradingPage.tsx` 已较为膨胀：15 个 Tab + Drawer width 1280 + 30+ hooks。**P1 已知风险**：维护负担可见，但不阻塞 GateJ-FREEZE；建议在 GateK 前重构。
- API / hooks / types / query-keys 分层清楚：`frontend/src/api/paper-trading.ts` 集中 35 个方法、`frontend/src/hooks/usePaperTradingQuery.ts` 集中查询/mutation、`frontend/src/types/paper-trading.ts` 集中类型、`frontend/src/api/query-keys.ts` 集中 cache key。
- loading / empty / error 状态：page 中每个 Tab 表格都通过 `Empty` 或 `Tag` 显示空态；mutation 通过 `message`（`App.useApp()`）反馈。
- emergency stop 通过 `modal.confirm` 显式确认。
- Ant Design 已知 deprecation warning（`Card.bordered` / `Modal.destroyOnClose`）仍存在 **P2**。
- 无服务端数据放入 Zustand。
- 未引入图表库（表格呈现）。
- 无 AI UI；无 LIVE 下单 UI。

## 20. E2E and test stability audit

- 22 个 Playwright spec，含 6 个 GateJ 主链 smoke。
- spec 全部通过本地后端 API 准备 fixture，不依赖外网交易所、不依赖真实 LIVE。
- skipped 用例：`trading workspace / 配置订单 ID 时可打开订单详情`（未配置 `E2E_TRADE_ORDER_ID`），属既有交易订单详情链路，**与 GateJ 主链无关**。
- fixture 依赖：本地需种子 `accounts.account_id=3001`（非 migration，作为 legacy account 占位）。
- E2E_TRADE_ORDER_ID 之外没有其他 skip。
- 本轮 PRE-FREEZE-CODE-AUDIT 未在干净本地实例上重跑 E2E（详见第 13 节），沿用 GateJ-3 通过基线。**P1**：GateJ-FREEZE 入场前必须重跑确认。

## 21. Python research module audit

- 工程结构：`research/py/src/nq_research/{cli.py, data/models.py, strategy/sample_strategy.py}` + tests + pyproject.toml，结构清晰。
- `pyproject.toml` 已包含 `[project.optional-dependencies].dev`（pytest>=8.0 / mypy>=1.8 / ruff>=0.8）。
- 无 `__pycache__`、`.pyc`、临时输出（已 Glob 确认）。
- `cli.py` 仅读本地 CSV，明确 offline-only：不进入 live trading / auth / recovery / ledger 主链，不连接交易所，不下单，不接 AI 自动交易。
- 本轮 PRE-FREEZE-CODE-AUDIT **未实际执行** `pytest / mypy / ruff`（当前 shell 仅 Windows App Execution Alias stub，无真实 Python 解释器；`python.exe` 调用 exit 49）。**P1**：GateJ-FREEZE 入场前必须在具备真实 Python 解释器的窗口重跑。

## 22. Paper / LIVE isolation audit

- 所有 run-once（schedule / heartbeat / risk / monitor / stability / daily-report-generate）均只写本地 DB，不调用 TradingAdapter。
- 所有 recovery / retry-failed-step 均只写 recovery_event 记录。
- emergency stop 仅复用 `PaperTradingRunService.stop`，不调用任何撤单接口。
- `nq-adapter-okx / nq-adapter-binance` 未被 Paper Trading 监控链路引用（搜索 `TradingAdapter / placeOrder / cancelOrder / HttpClient / RestTemplate / WebClient` 在 `backend/nq-research/.../application/paper/**` 全部无命中）。
- `.env / API key / secret / token` 未在仓库；`.env.example` 仅占位。

## 23. AI boundary audit

- 无 AI 模块、无 AI Signal API、无 AI 自动交易入口、无 AI Paper Trading、无 LLM provider 配置、无 AI 绕过风控路径（全仓库扫描 `openai/anthropic/llm/chatgpt/gpt-4/claude-3/ai-signal/ai-trading` 均无业务代码命中，仅 `.agents/skills/*.md` 命中文档，与代码无关）。
- 文档 README / AGENTS / CLAUDE / STATUS / ROADMAP 均明确写 `AI not started / GateK not started / AI 最早 GateK 才允许进入信号层`。

## 24. GateJ-FREEZE readiness audit

| 项 | 状态 | 备注 |
| --- | --- | --- |
| 启动 Paper run | ✓ | `POST /api/paper-trading/runs` |
| 创建 schedule | ✓ | `POST /api/paper-trading/schedules` |
| 产生 heartbeat | ✓ | `POST /api/paper-trading/runs/{id}/heartbeats/run-once` |
| 生成 daily report | ✓ | `POST /api/paper-trading/runs/{id}/daily-reports/generate` |
| 触发 monitor | ✓ | `POST /api/paper-trading/runs/{id}/monitor/run-once` |
| 查看 alert | ✓ | `GET /api/paper-trading/runs/{id}/alerts` |
| 执行 recover | ✓ | `POST /api/paper-trading/runs/{id}/recover` |
| 生成 stability check | ✓ | `POST /api/paper-trading/runs/{id}/stability-checks/generate` |
| 记录验收结果 | ✓ | `paper_run_stability_checks` 表 + 本轮新增 `GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md` |
| 判断失败 | ✓ | stability check status = FAILED + 文档 |
| 手动 run-once | ✓ | schedule / heartbeat / monitor / daily-report-generate / stability-checks-generate / risk-results-run-once 均支持 |
| 验收记录模板 | ✓ | 本轮新增 `docs/current/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md` |
| GateJ-FREEZE 边界 | ✓ | 文档明确：只做 1h/24h/7d 验收 + 冻结，不夹带 AI 或新功能 |

GateJ-FREEZE 准备度结论：**就绪**。

## 25. Technical debt and risk classification

| 风险 | 描述 | 等级 | 不阻塞理由 | 后续处理 |
| --- | --- | --- | --- | --- |
| npm audit 4 个告警 | 既有依赖告警，未升级 | P2 | 既有风险已记录；与 GateJ-FREEZE 主链无关 | GateK 前或专项依赖升级 |
| Vite chunk > 500 kB | bundle 体积警告 | P2 | 不影响功能 | 专项前端优化 |
| Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation | UI 警告 | P2 | 不影响功能 | 专项前端升级 |
| 日报指标占位 0（total_equity / daily_pnl / max_drawdown） | 第一版未与 equity_curve 联动 | P2 | 已在文档明确 | 撮合回写完整后补 |
| cron 仅做字段数校验 | 未做完整语义 | P2 | 第一版只手动 run-once | 后台调度器接入时补 |
| fire 状态第一版固定 SUCCEEDED | 没有失败路径 | P2 | monitor 守护可检 SCHEDULE_FIRE_FAILED；第一版只 run-once | 后台调度器接入时补 |
| 后台常驻调度器未实现 | 全部走 run-once | P2 | GateJ-FREEZE 用 run-once 模拟连续运行 | 单独评估 Spring Scheduler 接入 |
| heartbeat lag 阈值固定 300s | 无运行时配置 | P2 | 第一版可接受 | 单独配置项 |
| alert 去重只按 alert_type + 5 分钟 | 维度较粗 | P2 | 第一版可接受 | 后续细化 |
| uptime_ratio 粗略口径 | 非精确加权 | P2 | 已在文档与 DB 注释明确"非最终验收" | GateK 前细化 |
| PaperTradingPage 膨胀 | 15 Tab + 30+ hooks | P1 | 不影响功能 | GateK 前重构 |
| E2E_TRADE_ORDER_ID 未配置 1 个 skip | 既有交易订单链路 | P3 | 与 GateJ 主链无关 | 长期 |
| Python editable install 不稳 | `pip install -e ".[dev]"` 卡顿 | P2 | 用 `pip install pytest mypy ruff` 替代可通过 | 长期 |
| 本轮 E2E 未实际重跑 | 未启动后端 local profile | P1 | 沿用 GateJ-3 24 passed/1 skipped 基线 | GateJ-FREEZE 入场前必须重跑 |
| 本轮 Python 未实际重跑 | 当前 shell 仅 WindowsApps stub | P1 | 沿用 BASELINE-FIX-2 / GateJ-3 通过基线 | GateJ-FREEZE 入场前必须重跑 |
| 缺少 GateJ-FREEZE 验收记录模板 | - | P1 → 已修复 | 本轮新增 `GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`（仅文档） | 已闭环 |

## 26. P0 issue list

**无 P0 阻塞性问题**。

## 27. P1 issue list

| ID | 描述 | 影响 | 处理建议 |
| --- | --- | --- | --- |
| P1-1 | 本轮 PRE-FREEZE-CODE-AUDIT `npm run test:e2e` 未实际重跑 | GateJ-FREEZE 1h 验收前的最后一次基线确认缺失 | GateJ-FREEZE 入场前必须在本地后端 local profile + 5432 + 种子 `account_id=3001` 环境下重跑一次完整 E2E，记录 passed/skipped 数 |
| P1-2 | 本轮 PRE-FREEZE-CODE-AUDIT Python `pytest / mypy / ruff` 未实际重跑（当前 shell 仅 WindowsApps stub） | GateJ-FREEZE 入场前的 Python 基线确认缺失 | GateJ-FREEZE 入场前必须在有真实 Python 解释器的窗口重跑 |
| P1-3 | `PaperTradingPage.tsx` 已较膨胀（15 Tab、Drawer width 1280、30+ hooks） | 维护负担可见 | GateK 前重构（不阻塞 GateJ-FREEZE） |
| P1-4 | 缺少 GateJ-FREEZE 验收记录模板 | 1h/24h/7d 验收记录无标准模板 | 已在本轮新增 `docs/current/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`，闭环 |

P1-1 / P1-2 必须在 GateJ-FREEZE 入场前完成；P1-3 不阻塞 GateJ-FREEZE；P1-4 已闭环。

## 28. P2 issue list

详见第 25 节技术债表中标记为 P2 的 11 项（npm audit / Vite chunk / Ant Design deprecation / 日报占位 / cron 字段数校验 / fire 状态第一版 SUCCEEDED / 后台调度器未实现 / heartbeat 阈值固定 / alert 去重粗 / uptime_ratio 粗略 / Python editable install）。

均不阻塞 GateJ-FREEZE，放入 FULL-AUDIT 或后续 Gate。

## 29. P3 issue list

- E2E_TRADE_ORDER_ID 未配置导致 1 个既有 skipped 用例。
- 前端组件后续拆分优化。
- 表格→图表升级。
- 命名统一微调。

## 30. Audit conclusion

- 无 P0 阻塞性问题。
- P1 共 4 条：其中 P1-1 / P1-2 为本轮环境限制下未实际重跑 E2E 与 Python 验证，必须在 GateJ-FREEZE 入场前补回；P1-3 不阻塞；P1-4 已在本轮闭环。
- P2 / P3 均不阻塞 GateJ-FREEZE。
- 文档、代码、DB、API、前端、E2E、Python、Paper/LIVE 隔离、AI 边界、模块边界均一致；Paper Trading 主链完整；GateJ-FREEZE 准备度就绪。

**第一轮结论：条件性允许进入 GateJ-FREEZE，但第一轮未实际重跑 `npm run test:e2e` 与 Python `pytest/mypy/ruff`，因此需要二次审查补齐验证。Codex second-pass 已在第 31 节后补齐并关闭该验证缺口。**

## Codex second-pass audit

> 二次审查日期：2026-05-22
> 执行者：Codex
> 范围限定：只做二次审查、实际验证和文档更新；不修业务代码、不新增业务功能/API/migration、不改前端页面实现、不接 AI、不执行 GateJ-FREEZE 1h/24h/7d 验收、不创建 `docs/gates/gate-j/`。

### 31. Second-pass scope

本轮覆盖：

1. 复核 Claude 第一轮 PRE-FREEZE-CODE-AUDIT 结论。
2. 实际执行后端 `mvn -f backend/pom.xml test`。
3. 实际执行前端 `npm run build`。
4. 启动后端 local profile 后实际执行完整 `npm run test:e2e`。
5. 实际执行 Python `python -m pytest -q` / `python -m mypy src` / `python -m ruff check .`。
6. 二次抽查指定 20 个 GateJ 主链 endpoint 的 Controller / DTO / Service 真实性。
7. 二次抽查 V21-V25 中 16 张 Paper 表的 COMMENT / CHECK / FK / UNIQUE / index。
8. 二次抽查 Paper/LIVE 隔离与 AI 边界。

### 32. Claude first-pass conclusion reviewed

Claude 第一轮结论中，以下实现真实性判断经 Codex 二次抽查成立：

- 文档 -> 代码、代码 -> 文档在 GateJ 主链范围内一致，未发现文档虚写。
- 指定 GateJ 主链 endpoint 均存在于 `PaperTradingController` / `PaperTradingScheduleController`，并委派到 `PaperTradingApiService` 与对应 application service。
- `API.md` 已记录 Paper Trading run、monitor、schedule、heartbeat、daily report、alert、recovery、stability、monitor run-once 等能力。
- V21-V25 Flyway migration 连续存在，覆盖 16 张 Paper 表；新增表和字段有 COMMENT，状态字段有 CHECK，关键 FK/UNIQUE/index 存在。
- `/paper-trading` 前端详情抽屉包含 15 个 Tab，并与后端能力对应。
- E2E 全量实际执行结果为 24 passed / 1 skipped；GateJ 主链 smoke 均执行通过，唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路。
- Python research 工具链实际执行通过，且仍为 offline-only。
- Paper/LIVE 隔离未发现越界调用；GateJ run-once / recover / retry / monitor / emergency stop 均未引用真实交易所下单或撤单适配器。
- AI 边界未发现业务代码接入；未发现 AI module、AI API、AI Signal API、模型调用、OpenAI / Claude / LLM provider 业务接入。

Claude 第一轮报告中过度保守或已过期的点：

- P1-1（E2E 未实际重跑）已由本轮 Codex 二次审查关闭。
- P1-2（Python pytest/mypy/ruff 未实际重跑）已由本轮 Codex 二次审查关闭。

### 33. Backend test actual result

命令：

```powershell
mvn -f backend/pom.xml test
```

实际结果：

- Exit code：0。
- Reactor：23 个 backend module 全部 `SUCCESS`。
- `nq-app` suite：`Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`。
- 关键 Paper 单元测试实际执行并通过：`PaperTradingRunServiceTest` 4、`PaperTradingMonitorServiceTest` 5、`PaperRunScheduleServiceTest` 11、`PaperRunMonitorServiceTest` 12、`PaperRunRecoveryServiceTest` 9、`PaperRunStabilityCheckServiceTest` 10、`PaperRunMonitorRunServiceTest` 8。
- 结论：通过，无 P0。

### 34. Frontend build actual result

命令：

```powershell
Set-Location frontend
npm run build
```

实际结果：

- Exit code：0。
- `tsc -b && vite build` 成功。
- 输出：`dist/assets/index-CLLFLWD4.js 1,478.51 kB / gzip 446.09 kB`。
- 仍有 Vite `chunk > 500 kB` 警告，沿用 P2，不阻塞 GateJ-FREEZE。
- 结论：通过，无 P0。

### 35. E2E actual result

命令：

```powershell
Set-Location frontend
npm run test:e2e
```

实际执行方式：

- 本轮先启动后端 local profile：`mvn -f backend/pom.xml -pl nq-app -am spring-boot:run '-Dspring-boot.run.profiles=local'`。
- `/actuator/health` 返回 `UP` 后执行完整 E2E，不是单 spec。
- Flyway 启动日志确认 schema 当前版本为 `25`。

实际结果：

- Exit code：0。
- Playwright：25 tests total，24 passed，1 skipped，0 failed。
- 唯一 skipped：`trading workspace / 配置订单 ID 时可打开订单详情`，原因是未配置 `E2E_TRADE_ORDER_ID`，为既有订单详情链路，不属于 GateJ 主链。
- GateJ 主链 spec 均执行通过：
  - `paper-trading-schedule-smoke`
  - `paper-trading-daily-report-smoke`
  - `paper-trading-alert-smoke`
  - `paper-trading-recovery-smoke`
  - `paper-trading-stability-check-smoke`
  - GateI-3/4 Paper Trading run / monitor smoke
- E2E 运行期间仍有既有 Ant Design/Vite console warning：React 19 compatibility、`Card.bordered`、`Modal.destroyOnClose`、`useForm` 未连接、`Descriptions` span 合计不匹配；本轮未修业务代码，统一列为 P2 前端技术债，不阻塞。
- 结论：通过，无 P0。

### 36. Python actual result

默认 shell 中 `python` 解析到 `C:\Users\lingy\AppData\Local\Microsoft\WindowsApps\python.exe`，首次执行 `python -m pytest -q` 失败，错误为 WindowsApps alias 无真实 Python 解释器。为完成本轮必须验证，Codex 使用 workspace bundled Python 临时置于当前命令 `PATH` 首位后执行相同 `python -m ...` 命令；这不修改仓库配置。

命令与实际结果：

```powershell
Set-Location research/py
python -m pytest -q
```

- Exit code：0。
- `2 passed in 0.03s`。

```powershell
Set-Location research/py
python -m mypy src
```

- Exit code：0。
- `Success: no issues found in 8 source files`。

```powershell
Set-Location research/py
python -m ruff check .
```

- Exit code：0。
- `All checks passed!`。

结论：Python 基线通过，无 P0。环境备注：默认 shell Python alias 仍不可用，后续人工复跑需显式使用真实 Python 解释器或修正 PATH。

### 37. API reality spot-check result

指定 20 个 endpoint 二次抽查结论：全部存在。

| Endpoint | Controller | DTO | Service 委派 | 结论 |
| --- | --- | --- | --- | --- |
| `POST /api/paper-trading/runs` | `PaperTradingController` | `PaperTradingRunCreateRequestBody` / `PaperTradingRunResponse` | `PaperTradingApiService.create` | OK |
| `POST /api/paper-trading/runs/{id}/start` | `PaperTradingController` | `PaperTradingRunResponse` | `PaperTradingApiService.start` | OK |
| `POST /api/paper-trading/runs/{id}/stop` | `PaperTradingController` | `PaperTradingRunResponse` | `PaperTradingApiService.stop` | OK |
| `GET /api/paper-trading/schedules` | `PaperTradingScheduleController` | `PaperRunScheduleResponse` | `PaperTradingApiService.listSchedules` | OK |
| `POST /api/paper-trading/schedules` | `PaperTradingScheduleController` | `PaperRunScheduleCreateRequestBody` / `PaperRunScheduleResponse` | `PaperTradingApiService.createSchedule` | OK |
| `POST /api/paper-trading/schedules/{scheduleId}/run-once` | `PaperTradingScheduleController` | `PaperRunScheduleFireResponse` | `PaperTradingApiService.runScheduleOnce` | OK |
| `GET /api/paper-trading/runs/{paperRunId}/heartbeats` | `PaperTradingController` | `PaperRunHeartbeatResponse` | `PaperTradingApiService.listHeartbeats` | OK |
| `POST /api/paper-trading/runs/{paperRunId}/heartbeats/run-once` | `PaperTradingController` | `PaperRunHeartbeatResponse` | `PaperTradingApiService.runHeartbeatOnce` | OK |
| `GET /api/paper-trading/runs/{paperRunId}/daily-reports` | `PaperTradingController` | `PaperRunDailyReportResponse` | `PaperTradingApiService.listDailyReports` | OK |
| `POST /api/paper-trading/runs/{paperRunId}/daily-reports/generate` | `PaperTradingController` | `PaperRunDailyReportGenerateRequestBody` / `PaperRunDailyReportResponse` | `PaperTradingApiService.generateDailyReport` | OK |
| `GET /api/paper-trading/runs/{paperRunId}/alerts` | `PaperTradingController` | `PaperRunAlertResponse` | `PaperTradingApiService.listAlerts` | OK |
| `POST /api/paper-trading/runs/{paperRunId}/alerts` | `PaperTradingController` | `PaperRunAlertCreateRequestBody` / `PaperRunAlertResponse` | `PaperTradingApiService.createAlert` | OK |
| `PATCH /api/paper-trading/runs/{paperRunId}/alerts/{alertId}/ack` | `PaperTradingController` | `PaperRunAlertAckRequestBody` / `PaperRunAlertResponse` | `PaperTradingApiService.ackAlert` | OK |
| `PATCH /api/paper-trading/runs/{paperRunId}/alerts/{alertId}/resolve` | `PaperTradingController` | `PaperRunAlertResponse` | `PaperTradingApiService.resolveAlert` | OK |
| `GET /api/paper-trading/runs/{paperRunId}/recovery-events` | `PaperTradingController` | `PaperRunRecoveryEventResponse` | `PaperTradingApiService.listRecoveryEvents` | OK |
| `POST /api/paper-trading/runs/{paperRunId}/recover` | `PaperTradingController` | `PaperRunRecoverRequestBody` / `PaperRunRecoveryEventResponse` | `PaperTradingApiService.recover` | OK |
| `POST /api/paper-trading/runs/{paperRunId}/retry-failed-step` | `PaperTradingController` | `PaperRunRetryFailedStepRequestBody` / `PaperRunRecoveryEventResponse` | `PaperTradingApiService.retryFailedStep` | OK |
| `GET /api/paper-trading/runs/{paperRunId}/stability-checks` | `PaperTradingController` | `PaperRunStabilityCheckResponse` | `PaperTradingApiService.listStabilityChecks` | OK |
| `POST /api/paper-trading/runs/{paperRunId}/stability-checks/generate` | `PaperTradingController` | `PaperRunStabilityCheckGenerateRequestBody` / `PaperRunStabilityCheckResponse` | `PaperTradingApiService.generateStabilityCheck` | OK |
| `POST /api/paper-trading/runs/{paperRunId}/monitor/run-once` | `PaperTradingController` | `PaperRunMonitorRunOnceResponse` | `PaperTradingApiService.runMonitorOnce` | OK |

### 38. DB reality spot-check result

二次抽查 V21-V25：

- V21：`paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`、`paper_trading_positions` 存在；COMMENT、CHECK、FK、index、`paper_trading_positions` UNIQUE 存在。
- V22：`paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events` 存在；COMMENT、CHECK、FK、index 存在。
- V23：`paper_run_schedules`、`paper_run_schedule_fires`、`paper_run_heartbeats` 存在；COMMENT、CHECK、FK、partial index / 时间倒序 index 存在。
- V24：`paper_run_daily_reports`、`paper_run_alerts` 存在；COMMENT、CHECK、FK、`uq_daily_reports_run_date`、status/severity index 存在。
- V25：`paper_run_recovery_events`、`paper_run_stability_checks` 存在；COMMENT、CHECK、FK、`uq_stability_checks_run_window`、status/type/window index 存在。
- Flyway 启动日志在本轮 E2E 后端启动中确认：`Successfully validated 25 migrations`，`Current version of schema "public": 25`，`Schema "public" is up to date`。

结论：DB 真实性成立，无新增 P0/P1。

### 39. Paper/LIVE isolation spot-check result

抽查 `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/**` 与 `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/**`：

- 未发现 `TradingAdapter`、`placeOrder`、`cancelOrder`、`RestTemplate`、`WebClient`、`HttpClient` 引用。
- schedule run-once：仅写 `paper_run_schedule_fires`，状态第一版固定 `SUCCEEDED`。
- heartbeat run-once：仅写 `paper_run_heartbeats`。
- daily report generate：仅聚合并 upsert `paper_run_daily_reports`。
- alert create/ack/resolve：仅写/更新 `paper_run_alerts`。
- recover / retry-failed-step：仅写 `paper_run_recovery_events`。
- stability check generate：仅聚合心跳、告警、fire、recovery、report 计数并 upsert `paper_run_stability_checks`。
- monitor run-once：仅检测 heartbeat lag / failed fire 并写入 `paper_run_alerts`。
- emergency stop：只调用 `PaperTradingRunService.stop` 停止 Paper run，并写入 `emergency_stop_events`；不调用真实交易所撤单。

结论：Paper/LIVE 隔离成立，未发现越界，无 P0。

### 40. AI boundary spot-check result

抽查范围：`backend`、`frontend/src`、`research/py`、`docs/current`、根目录 `README.md` / `AGENTS.md` / `CLAUDE.md`，并排除 `target` 生成目录。

结果：

- `backend` / `frontend/src` / `research/py` 未发现 OpenAI / Anthropic / LLM provider / AI Signal / AI Trading 业务接入。
- 命中项均为文档中的禁止范围或路线图描述，未发现业务代码实现。
- 无 AI module、无 AI API、无 AI Signal API、无模型调用、无 OpenAI / Claude / LLM provider 配置。

结论：AI 边界成立，无 P0。

### 41. New P0/P1/P2/P3 found by Codex

| 等级 | 新发现 | 说明 |
| --- | --- | --- |
| P0 | 0 | 未发现阻塞 GateJ-FREEZE 的实现缺口、文档虚写、E2E/Python 失败、LIVE 越界或 AI 越界。 |
| P1 | 0 | Claude 第一轮 P1-1/P1-2 已通过本轮实际验证关闭；P1-3 前端巨型页面仍为不阻塞项；P1-4 已闭环。 |
| P2 | 1 | E2E 期间仍有前端 runtime warning 集合：Ant Design React 19 compatibility、`Card.bordered`、`Modal.destroyOnClose`、`useForm` 未连接、`Descriptions` span 合计不匹配。构建和 E2E 均通过，作为前端专项技术债，不阻塞。 |
| P3 | 0 | 未新增 P3。 |

### 42. Final second-pass conclusion

- Codex 二次审查已完成。
- 后端测试通过。
- 前端 build 通过。
- 完整 E2E 已实际执行并通过：24 passed / 1 skipped / 0 failed，GateJ 主链不被 skip。
- Python `pytest` / `mypy` / `ruff` 已实际执行并通过。
- API / DB / Paper-LIVE 隔离 / AI 边界二次抽查未发现 P0/P1。
- 本轮未修改业务代码、未新增 API、未新增 migration、未改前端页面实现、未接 AI、未创建 `docs/gates/gate-j/`。

**结论：允许进入 GateJ-FREEZE，但必须在本轮审查报告提交后单独开工。GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。**
