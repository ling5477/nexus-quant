# GateI Frontend Plan

本文件只规划 GateI 前端，不新增页面实现，不修改路由，不接入新 API。

## 前端结构约定

- `pages` 只做路由入口。
- `features/strategy` 承载策略版本业务前端逻辑。
- `features/publish` 承载发布版本业务前端逻辑。
- `features/backtest` 承载回测配置、结果和 dataset / strategy version 绑定逻辑。
- `features/evaluation` 承载评估报告逻辑。
- `features/paper-trading` 承载 Paper Trading run 逻辑。
- `features/risk` 承载风控结果逻辑。
- `features/portfolio` 承载资金曲线和持仓曲线逻辑。
- `features/replay` 承载交易复盘逻辑。
- `features/emergency-stop` 承载异常停机逻辑。
- 服务端数据继续使用 TanStack Query + Axios。
- Zustand 只放 auth、account-context 等全局状态，不存 GateI 服务端列表数据。

## `/strategies` 版本管理增强

- 页面目标：管理 strategy versions，支持创建、冻结、归档和查看参数快照。
- 查询条件：strategy、status、createdBy、createdAt range。
- 表格字段：versionId、versionName、status、strategyId、createdBy、createdAt、frozenAt。
- 操作按钮：新建版本、查看详情、冻结、归档。
- loading 状态：表格 skeleton 或 Ant Design loading。
- empty 状态：提示当前没有策略版本，可创建第一个版本。
- error 状态：展示错误摘要和重试按钮。
- E2E 覆盖点：`strategy-version-smoke` 打开页面、创建版本、冻结版本、查看快照。

## `/publishes` 发布版本管理增强

- 页面目标：管理策略发布版本，作为 Paper Trading 输入。
- 查询条件：strategyVersionId、status、targetEnvironment、createdAt range。
- 表格字段：publishVersionId、strategyVersionId、evaluationReportId、targetEnvironment、status、createdBy、createdAt、approvedAt。
- 操作按钮：创建发布、审批、查看发布快照。
- loading 状态：发布列表 loading。
- empty 状态：提示暂无发布版本。
- error 状态：展示 API 错误和重试入口。
- E2E 覆盖点：`publish-version-smoke` 创建发布版本并查看状态。

## `/backtests` 配置与结果增强

- 页面目标：增强回测配置，使其绑定 dataset、strategy version 和 parameter snapshot。
- 查询条件：strategyVersionId、datasetId、status、createdAt range。
- 表格字段：configId、strategyVersionId、datasetId、status、updatedAt、latestRunStatus。
- 操作按钮：绑定策略版本、绑定 dataset、编辑参数快照、启动回测、查看 run。
- loading 状态：配置和运行列表分别 loading。
- empty 状态：提示暂无回测配置。
- error 状态：展示绑定失败、启动失败或查询失败。
- E2E 覆盖点：`backtest-config-enhanced-smoke` 完成 dataset + strategy version 绑定。

## `/evaluations` 评估报告增强

- 页面目标：展示回测评估核心指标和输入追溯。
- 查询条件：backtestRunId、strategyVersionId、datasetId、metric range。
- 表格字段：reportId、backtestRunId、totalReturn、maxDrawdown、winRate、profitLossRatio、tradeCount、createdAt。
- 操作按钮：查看报告详情、查看输入快照、跳转回测 run。
- loading 状态：指标卡和表格 loading。
- empty 状态：提示暂无评估报告。
- error 状态：展示错误和重试。
- E2E 覆盖点：`evaluation-report-smoke` 查看报告指标和快照。

## `/paper-trading` 或 `/runs` Paper Trading 运行入口

- 页面目标：创建和管理 Paper Trading run。
- 查询条件：publishVersionId、accountId、status、environment、createdAt range。
- 表格字段：runId、publishVersionId、accountAlias、environment、status、initialCapital、currentEquity、startedAt、updatedAt。
- 操作按钮：创建 run、启动、暂停、停止、查看详情。
- loading 状态：run 列表 loading。
- empty 状态：提示暂无 Paper Trading run。
- error 状态：展示创建、启动、停止失败原因。
- E2E 覆盖点：`paper-trading-run-smoke` 创建并启动 Paper run。

## `/risk` 风控结果

- 页面目标：查询 Paper run/order 维度风控结果。
- 查询条件：runId、orderId、riskRuleCode、status、checkedAt range。
- 表格字段：riskResultId、runId、orderId、riskRuleCode、status、reason、checkedAt。
- 操作按钮：查看输入输出快照、跳转订单或 run。
- loading 状态：表格 loading。
- empty 状态：提示暂无风控结果。
- error 状态：展示查询失败和重试。
- E2E 覆盖点：`risk-result-smoke` 查看风控结果列表和详情。

## `/portfolio/equity-curve` 资金曲线

- 页面目标：展示 Paper run 资金曲线。
- 查询条件：runId、from、to、interval。
- 表格字段：snapshotTime、equity、cashBalance、positionValue、realizedPnl、unrealizedPnl、drawdown。
- 操作按钮：刷新、导出、跳转 run。
- loading 状态：图表和表格 loading。
- empty 状态：提示暂无资金曲线。
- error 状态：展示查询失败和重试。
- E2E 覆盖点：`equity-curve-smoke` 打开资金曲线并查看空态/数据态。

## `/portfolio/position-curve` 持仓曲线

- 页面目标：展示 Paper run 持仓曲线。
- 查询条件：runId、symbol、from、to、interval。
- 表格字段：snapshotTime、symbol、quantity、averagePrice、marketPrice、marketValue、unrealizedPnl。
- 操作按钮：刷新、导出、跳转 run。
- loading 状态：图表和表格 loading。
- empty 状态：提示暂无持仓曲线。
- error 状态：展示查询失败和重试。
- E2E 覆盖点：`position-curve-smoke` 打开持仓曲线并查看空态/数据态。

## `/replay` 交易复盘

- 页面目标：按 trade 展示策略信号、风控、订单、成交、持仓和资金变化。
- 查询条件：runId、tradeId、symbol、time range。
- 表格字段：tradeId、runId、symbol、side、price、quantity、tradeTime、riskStatus。
- 操作按钮：查看复盘详情、跳转 run、跳转订单。
- loading 状态：复盘链路 loading。
- empty 状态：提示暂无可复盘交易。
- error 状态：展示查询失败和重试。
- E2E 覆盖点：`trade-replay-smoke` 查看复盘详情。

## `/emergency-stop` 异常停机

- 页面目标：触发、查看和解除 Paper 主链异常停机。
- 查询条件：scopeType、scopeId、status、triggeredAt range。
- 表格字段：eventId、scopeType、scopeId、status、reasonCode、triggeredBy、triggeredAt、resolvedAt。
- 操作按钮：触发 stop、解除 stop、查看事件快照。
- loading 状态：事件列表 loading。
- empty 状态：提示暂无异常停机事件。
- error 状态：展示触发或解除失败。
- E2E 覆盖点：`emergency-stop-smoke` 触发并解除 Paper scope stop。

## 本轮限制

- 本轮不新增页面实现。
- 本轮不修改路由。
- 本轮不接入 API。
- 本轮不处理 npm audit。
- 本轮不处理 Vite chunk 警告。
