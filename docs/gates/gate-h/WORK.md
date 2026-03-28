# GateH 工作台账

## 1. GateH-PLAN（2026-03-27）

本批目标：

- 盘点 GateG 冻结后仍有价值的增强项
- 依据仓库事实判断优先级、依赖关系、风险与收益
- 明确 GateH 的目标、边界、前置条件与非目标
- 输出正式规划文档与分批执行建议

本批已完成：

- 已核对 GateG 冻结结果与最终验收事实
- 已核对前端当前页面、API 封装、Playwright 用例与 skip 条件
- 已核对后端已存在但尚未前端闭环的正式接口：
  - `POST /api/strategies`
  - `POST /api/strategies/{strategyCode}/trigger`
  - `POST /api/strategy-schedules`
  - `POST /api/strategy-schedules/scan-once`
  - `POST /api/backtest-runs`
  - `POST /api/backtest-runs/{runId}/start`
  - `GET /api/backtest-runs/{runId}` 及 `sim_* / evaluation / publish`
- 已确认 `trade-validation` 当前正式协议仍以单订单读模型与统一 `OperationTriggerResponse` 为主
- 已确认当前 E2E 的主要不确定性来自预置数据与环境变量治理，而不是 GateG 冻结失败

本批收口结论：

- GateH 首批应优先做“已有正式接口但尚未前端闭环”的工作流增强
- GateH 不应在首批混入数据库大改、后端重构或新的协议设计
- `trade-validation` 适合先做基于现有接口的工作区增强，不适合直接扩成新平台
- 更广覆盖的 E2E 与测试数据治理应作为 GateH 的正式收口批次，而不是继续挂在 GateG

## 2. 推荐批次

- GateH-1：策略 / 调度 / 运行工作流增强
- GateH-2：回测运行工作流与深详情
- GateH-3：trade-validation 多结果工作区
- GateH-4：E2E 与测试数据治理

## 3. 当前开工判断

- GateH-1：可直接开工
- GateH-2：可直接开工
- GateH-3：基础版可开工；若要新增全局查询面或结构化动作结果，需要单独评估后端扩展
- GateH-4：可规划并开工，但需先定 seed / 预置数据策略
