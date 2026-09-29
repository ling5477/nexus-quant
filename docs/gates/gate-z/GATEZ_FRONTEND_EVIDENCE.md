# GateZ frontend evidence

最终用户链从 Account 建立 SIM 身份，到 Marketdata/Dataset、Strategy/frozen Version、Backtest、Evaluation、Publish、Paper/Continuous SIM、Scheduler management 与 Paper Detail。页面应显示真实状态、拒绝原因与 canonical 结果，不把按钮操作等同于后台成交。

- Account 页提供正式 SIM account 身份入口。
- Marketdata 页展示数据与质量来源。
- Strategy/Backtest/Evaluation/Publish 保留冻结版本连续性。
- Paper 页提供 Continuous SIM 的启动、停止、恢复及状态入口。
- Scheduler 页按角色展示固定任务的状态和有权限的操作。
- Paper Detail 展示 canonical order、trade、position 与 summary。

PR #53 增加 Scheduler 管理页；PR #54 在真实 JWT 下证明 actor 归属。PR #55 的 SIM account bridge 让账户到 Strategy 创建链可走正式身份。PR #56 将 Strategy SIM Panel 与 Paper Detail 的已实现 order/trade/position/summary 字段对齐 canonical economic facts，并保留普通 Paper run 的既有读侧。

PR #56 的 frontend unit `40/40`、TypeScript/Vite build、目标 Paper Detail Playwright E2E 是历史接受证据。Position 行没有独立 realized/unrealized PnL，前端不合成该数字；PnL summary 来自 Strategy SIM `/facts`。本 closeout 不修改前端组件或重新运行浏览器 E2E。
