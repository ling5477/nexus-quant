# GateH Frontend Plan

本文件只规划 GateH 前端，不新增页面实现，不新增业务路由代码。

## 前端结构要求

- `pages` 只做路由入口和页面装配。
- `features/trading` 承载交易工作台前端逻辑。
- `features/marketdata` 承载行情查询、接入任务和数据集逻辑。
- `features/instruments` 承载交易对目录逻辑。
- `features/backtest` 承载数据集绑定逻辑。
- service data 使用 TanStack Query + Axios。
- Zustand 只放 `auth`、`account-context` 等全局状态。
- 页面不直接散写 Axios 请求。

## `/trading` 正式交易工作台

- 页面目标：以当前账户上下文展示正式订单工作台，清晰区分 SIM / LIVE。
- 查询条件：账户、exchange、market type、symbol、environment、order status、time range。
- 表格字段：order id、client order id、symbol、side、type、price、quantity、filled quantity、status、environment、created at、updated at。
- 操作按钮：刷新、查看详情、切换账户上下文、查看风控摘要；不在 GateH-PLAN 实现新下单能力。
- loading 状态：订单列表、详情、账户上下文独立 loading。
- empty 状态：当前账户无订单时展示空列表和当前筛选条件。
- error 状态：账户无权限、API 失败、SIM / LIVE 边界错误需展示明确错误。
- E2E 覆盖点：`trading-workspace-smoke` 验证登录、账户上下文、订单列表、详情入口、SIM / LIVE 展示。

## `/instruments` 交易对目录

- 页面目标：提供 GateH 交易对选择的统一来源。
- 查询条件：exchange、market type、symbol、base asset、quote asset、status。
- 表格字段：exchange、market type、symbol、base asset、quote asset、price precision、quantity precision、min quantity、status、source、updated at。
- 操作按钮：刷新、同步 instrument、重置筛选。
- loading 状态：目录查询和同步任务触发分别展示。
- empty 状态：无匹配交易对时展示当前筛选条件。
- error 状态：同步失败、查询失败、权限不足。
- E2E 覆盖点：`instruments-query-smoke` 验证筛选、表格渲染、空态、错误态入口。

## `/marketdata` 行情数据查询

- 页面目标：查询已入库历史 K 线和质量状态。
- 查询条件：exchange、market type、symbol、interval、time range、quality status。
- 表格字段：open time、close time、open、high、low、close、volume、quote volume、trade count、source、quality status、ingested at。
- 操作按钮：查询、重置、跳转接入任务、创建数据集入口。
- loading 状态：K 线查询时表格保持结构稳定。
- empty 状态：无数据时提示当前范围尚未接入或筛选过窄。
- error 状态：参数非法、数据缺口、接口失败。
- E2E 覆盖点：`marketdata-bars-query-smoke` 验证筛选查询、表格字段、空态和错误态。

## `/marketdata/ingestion` 行情接入任务

- 页面目标：管理历史行情接入任务和执行记录。
- 查询条件：exchange、market type、symbol、interval、job status、enabled。
- 表格字段：job id、exchange、market type、symbols、intervals、status、enabled、last run、next run、updated at。
- 操作按钮：新建任务、run once、启用/停用、查看详情、刷新。
- loading 状态：列表加载、run once、启停任务分别展示。
- empty 状态：暂无接入任务时展示创建入口。
- error 状态：任务创建失败、交易所不可用、限流、权限不足。
- E2E 覆盖点：`marketdata-ingestion-smoke` 验证任务列表、创建入口、run once 触发、状态展示。

## `/backtests` 数据集绑定入口

- 页面目标：在回测配置中选择真实历史行情数据集。
- 查询条件：策略、config status、dataset、symbol、interval、time range。
- 表格字段：config id、strategy、dataset name、symbols、intervals、time range、quality status、updated at。
- 操作按钮：绑定数据集、查看数据集详情、清除绑定、刷新。
- loading 状态：配置列表、dataset 下拉、绑定提交分别展示。
- empty 状态：暂无可绑定配置或暂无 dataset 时给出空态。
- error 状态：dataset 不存在、质量状态不满足、权限不足、绑定冲突。
- E2E 覆盖点：`backtest-dataset-binding-smoke` 验证 dataset 查询、绑定提交、绑定结果展示。

## 本轮限制

- 本轮不新增前端页面实现。
- 本轮不新增 API client 实现。
- 本轮不新增路由配置。
- 本轮不修改交易、行情、策略业务逻辑。
