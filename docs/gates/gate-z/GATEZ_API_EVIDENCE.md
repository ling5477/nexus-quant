# GateZ API evidence

正式入口由 Java API Controller 持有：Marketdata job/bar/dataset，Strategy definition/version，Backtest run，Evaluation，Publish，Strategy SIM/Continuous SIM，Scheduler management 与 Paper Detail。请求经现有鉴权和业务 service，不通过 Python/AI 直写 Order/Trade/Ledger。

- Marketdata Controller 保有 public bar、dataset 与 quality 入口。
- Strategy Version 是冻结执行身份，Publish 绑定已评价结果。
- Backtest 与 Evaluation 形成研究侧运行/结果读写边界。
- StrategySimRunController 管理 SIM run，而不是 LIVE 下单入口。
- Scheduler 管理 API 只能操作固定 `ScheduledJobRegistry` key。
- PaperTradingController 的 Strategy SIM 读侧来自隔离 canonical account。

Marketdata/dataset 读写公开捕获与冻结数据身份；Backtest/Evaluation/Publish 读取同一冻结 StrategyVersion。Strategy SIM 创建/启动操作限 SIM，并由 canonical Risk/Execution 产生经济事实；控制与查询入口分离，停止/重启不生成新的逻辑订单。

Scheduler 管理 `PATCH`、`RUN_ONCE` 使用 JWT claims 的真实 actor，缺少身份 fail closed；允许管理固定 registry 的 job，不暴露任意任务执行。Continuous SIM poll 和 Paper Matching 仍由后台 registry/dispatcher 拥有。

PR #56 使 Strategy SIM 的 Paper Detail 四个 GET 从隔离 canonical account 读取 order、trade、position 与 summary；普通 Paper run 继续原有表。`/facts` 提供 PnL 汇总，不能从缺少独立 PnL 的 Position 行自行推导。LIVE、private exchange 与资金操作均不属于这些 GateZ API 的接受范围。
