# GateJ-FREEZE UI + UX Smoke Report

## 1. Summary

本报告记录 2026-05-30 对 NexusQuant GateJ-FREEZE 控制台执行的浏览器级只读 UI/UX 巡检结果。本次审查不是 GateJ-FREEZE 运行稳定性验收，不替代 30m / 1h / 24h / 7d 连续运行验收记录。

检查对象为 freeze 测试站点：

- URL: `http://47.251.74.35:5179/`
- Browser: Chrome；主检查使用 `chrome-devtools`
- Login state: 已登录；未输出、保存或重新输入凭证
- Check mode: 只读；仅浏览、菜单切换、DOM / Console / Network 读取

结论：

- Functional stability: PASS
- UI/UX professionalism: FAIL
- GateJ-FREEZE 7d stability acceptance: continue, not interrupted
- GateJ completed: still false until 7d acceptance passes and freeze docs are completed

本次发现不应被写作后端稳定性失败，也不应打断当前 7d 连续运行验收。当前判断是：运行稳定性验收继续，UI/UX 专业性验收未通过。

## 2. Scope

检查范围：

- Login
- Dashboard
- Trading Workbench
- Marketdata
- Instrument Catalog
- Strategies
- Schedules
- Runs
- Research
- Backtests
- Evaluations
- Publishes
- Paper Trading

只读边界：

- no create
- no update
- no delete
- no start/stop
- no order placement
- no publish
- no evaluation trigger
- no external sync
- no credential/token output

## 3. Stability Findings

稳定性方面通过项：

- 页面均可打开。
- 无白屏。
- 无崩溃。
- 无 `internal server error`。
- 无明显 Console error/warn。
- 观察到的 Network 请求为 200。
- 未发现旧 Gate 文案残留：`GateH-1`、`GateH-2`、`GateI-3`、`GateH-PRE`、`GateG`、`LOCAL`、`Gate-3`。

说明：上述结论只覆盖本次浏览器只读 smoke review 的页面可用性与前端运行时观察，不等同于 7d 连续运行验收已完成。

## 4. UI/UX Findings

UI/UX 专业性未通过的主要原因：

- Dashboard 暴露工程实现文案：`Axios instance`、`TanStack Query`、`Zustand`、`X-Trace-Id`、`当前批次`。
- freeze 期间多个写动作按钮仍可见且可点击：`同步 Catalog`、`创建任务`、`Run once`、`创建 Dataset`、`新建研究配置`、`新建回测配置`、`创建 Paper Run`。
- Instrument Catalog 同步入口前端未禁用，freeze 环境下仍存在误触发外部同步的 UI 风险。
- Dashboard 缺运营总览：系统健康、Paper Run 状态、告警数量、风险摘要、最近运行。
- Paper Trading 缺交易监控摘要：PnL、回撤、权益、持仓、订单、成交、告警。
- Schedules / Runs 缺运维摘要：下一次执行、失败次数、耗时、失败摘要、trace/关联跳转。
- 空态说明偏弱，多数只说明“点击查询后加载”，缺少业务原因和下一步建议。
- 表格密度和列可读性待优化。
- 中英文术语不统一，例如 `Marketdata`、`Dataset`、`Run once`。

## 5. Page Review Table

| Page | Open | Old Gate Residue | Dev Copy Residue | Controls | Layout / UX | Error / 500 | Console | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Login | Yes | No | No | Username/password inputs are appropriate | Simple and usable | No | No obvious error/warn | 未发现默认账号、密码、API 路径或后端端口泄露。 |
| Dashboard | Yes | No | Yes | No filter controls | 可用但偏工程说明，不像真实运营控制台首页 | No | No obvious error/warn | 暴露 `Axios instance`、`TanStack Query`、`Zustand`、`X-Trace-Id`、`当前批次`；缺系统健康、Paper Run、告警、风险摘要。 |
| Trading Workbench | Yes | No | Minor | Order status uses Select; order id / symbol remain text inputs | 账户上下文缺失时禁用态清楚 | No | No obvious error/warn | 下单前检查、撤单、对账、恢复等按钮在未选账户时 disabled。 |
| Marketdata | Yes | No | No | exchange / market / symbol / interval use Select; start/end use DatePicker | 功能可读，但表格列多、横向密度偏高 | No | No obvious error/warn | `创建任务`、`Run once`、`创建 Dataset` 在 freeze 期间仍可点击。 |
| Instrument Catalog | Yes | No | No | Basic Select filter | 表格可读，主数据字段清晰 | No | No obvious error/warn | `同步 Catalog` 仍可点击；freeze 下应前端 disabled 或明确只读模式。 |
| Strategies | Yes | No | No | strategyType / exchange / tradeEnv / enabled use Select | 空态可读，但仍偏列表查询页 | No | No obvious error/warn | 缺策略版本、风险等级、适用市场、最近运行结果摘要。 |
| Schedules | Yes | No | No | scheduleType / status / enabled use Select | 空态基本可读 | No | No obvious error/warn | 缺下一次触发时间、失败次数、心跳状态等运维信息。 |
| Runs | Yes | No | No | runStatus / triggerType use Select | 空态基本可读 | No | No obvious error/warn | 缺失败摘要、耗时、trace 入口、关联策略/调度跳转。 |
| Research | Yes | No | Yes | Text filters | 动作区存在可点击新建入口 | No | No obvious error/warn | 文案仍出现“最小 create 动作”等工程化表达；`新建研究配置` 可点击。 |
| Backtests | Yes | No | Yes | Text filters; date controls in create flow previously improved | 动作区存在可点击新建入口 | No | No obvious error/warn | 文案仍出现“最小 create 动作”等工程化表达；`新建回测配置` 可点击。 |
| Evaluations | Yes | No | No | evaluationStatus uses Select | 空态基本可读 | No | No obvious error/warn | 缺评分、指标摘要、风险结论、发布建议。 |
| Publishes | Yes | No | No | publishStatus uses Select | 空态基本可读 | No | No obvious error/warn | 缺审批状态、发布环境、版本号、冻结/回滚关系说明。 |
| Paper Trading | Yes | No | No | status uses Select; publish id remains text input | 信息不足，尚不像交易运行监控页 | No | No obvious error/warn | `创建 Paper Run` 可点击；缺 PnL、回撤、权益、持仓、订单、成交、告警摘要。 |

## 6. Severity Classification

### P0

- 未发现。

### P1

- freeze 期间多个写动作按钮仍启用。
- Dashboard 暴露工程实现文案。
- Instrument Catalog 外部同步入口未前端禁用。

### P2

- Dashboard 缺系统健康、Paper Run 状态、告警数量、风险摘要、最近运行。
- Paper Trading 缺 PnL、回撤、权益、持仓、订单、成交、告警。
- Schedules / Runs 缺下一次执行、失败次数、耗时、失败摘要、trace/关联跳转。
- 多个页面空态偏弱。

### P3

- 表格列可读性和横向密度优化。
- 中英文术语统一。
- 后续补充运行日历、告警中心、风险总览、数据源状态、审计日志入口。

## 7. Acceptance Impact

- 这些发现不打断当前 7d 连续运行验收。
- 不建议现在立即修复，因为修复需要重新 build / release / deploy，会破坏当前连续运行证据。
- 如果 7d 最终 PASS，可以判定 GateJ-FREEZE 稳定性验收通过。
- 但不能声明 UI/UX 专业化已完成。
- 这些问题应作为 post-freeze remediation 跟踪。

## 8. Recommended Follow-up

建议新增后续任务：`GateJ-POST-FREEZE-UI-AUDIT-FIX`。

建议范围：

1. Dashboard 清理工程实现文案。
2. freeze 环境禁用高风险写按钮或加只读模式。
3. Instrument Catalog 同步按钮在 freeze 下 disabled。
4. 所有写操作统一二次确认。
5. Dashboard 补系统健康、Paper Run、告警、风险摘要。
6. Paper Trading 补 PnL、回撤、权益、持仓、订单、成交、告警摘要。
7. Schedules / Runs 补下一次执行、失败次数、耗时、失败摘要。
8. 统一中英文术语。

该任务应在 GateJ-FREEZE 7d 验收结束后单独开工，避免影响当前连续运行证据。

## 9. Current GateJ-FREEZE Status

- 30m observation: PASS
- 1h acceptance: PASS
- 24h acceptance: PASS
- 7d acceptance: running
- GateJ completed: no

补充状态：

- FIX-5 / FIX-6 / FIX-7 已完成并通过 ECS 复验。
- 安全组已确认 `5179` 只允许本人 IP 访问。
- 本次 UI/UX smoke review 只记录专业性缺口，不改变 GateJ-FREEZE 稳定性验收状态。
