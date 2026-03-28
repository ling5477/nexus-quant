# GateH（暂停中）

当前状态：**Paused until RC1 complete。**

GateH 规划卷宗保留，但当前不再作为主线执行入口。RC1 完成前，禁止继续推进 GateH 新功能、页面扩展和后续工作流增强。

---

## 1. GateG 交接基线

- GateG 已正式 Frozen
- `frontend` 的 `npm install`、`npx tsc -b`、`npm run build` 已通过
- `npm run test:e2e` 已完成有效实跑，结果为 `4 passed / 2 skipped / 0 failed`
- `strategies-detail / research-detail` 的 skip 属于“无预置数据即 skip”的既有设计
- GateG 页面、详情、最小动作、trade-validation、文档与回归矩阵已冻结

GateH 的任何实现，都必须以这个冻结基线为前提，不能倒回去重写 GateG 已完成主链。

---

## 2. 候选增强项盘点

| 方向 | 当前仓库事实 | 用户价值 | 技术复杂度 | 是否依赖后端扩展 | 是否依赖 DB 变更 | 是否适合作为 GateH 首批 |
| --- | --- | --- | --- | --- | --- | --- |
| `strategies / schedules / runs` 更完整动作 | 后端已存在 `POST /api/strategies`、`POST /api/strategies/{strategyCode}/trigger`、`POST /api/strategy-schedules`、`POST /api/strategy-schedules/scan-once`；前端尚未接这些动作 | 高 | 中 | `strategies / schedules` 首批否；`runs` 若要求独立写动作则是 | 否 | 是 |
| `research / backtests / evaluations / publishes` 更深详情与更完整动作 | 后端已存在 `POST /api/backtest-runs`、`POST /api/backtest-runs/{runId}/start`、`GET /api/backtest-runs/{runId}` 与 `sim_* / evaluation / publish`；前端尚未形成完整回测运行工作流 | 高 | 中高 | 首批否 | 否 | 建议第二批 |
| `trade-validation` 更系统的多结果组织方式 | 当前接口以单订单读模型和统一 `OperationTriggerResponse` 为主；前端只保留最近一次动作反馈 | 中高 | 中高 | 基础版否；若要全局列表/结构化动作结果则是 | 基础版否 | 否 |
| 更广覆盖的 E2E / 测试数据治理 | 当前 E2E 有 `2 skipped`，依赖预置策略、研究配置与 `E2E_TRADE_ORDER_ID` | 高 | 中 | 通常否，但可能需要补 seed 方案或测试脚本 | 否 | 作为贯穿项适合尽早规划，正式收口建议最后一批 |

---

## 3. 推荐范围

本阶段做什么：

- 优先补齐基于现有正式 `/api/**` 的前端工作流闭环
- 优先把 `strategies / schedules / runs` 与 `backtest-runs` 这类“已有后端能力但前端未收口”的路径做实
- 在不新增后端协议的前提下，整理 `trade-validation` 的多结果工作区
- 为 GateH 新增链路补相应 E2E，并建立更稳定的测试数据治理方案

本阶段不做什么：

- 不回头改 GateG 已冻结主链定义
- 不直接做数据库大改
- 不直接做后端大重构
- 不把 `trade-validation` 扩成完整运维控制台
- 不在首批强行要求新增全局订单列表、结构化动作审计或新的查询协议
- 不引入新的认证、trace 或错误模型

---

## 4. 推荐批次拆分

### GateH-1：策略 / 调度 / 运行工作流增强

做什么：

- `strategies`：补 `create / trigger`
- `schedules`：补 `create / scan-once`
- `runs`：补与 trigger / scan-once 联动的结果可见性与跳转

完成标准：

- 用户可在控制台完成策略创建、手工触发、调度创建、scan-once
- 动作结果能回流到列表、详情和运行记录视图
- 至少补一条覆盖新增工作流的 E2E

### GateH-2：回测运行工作流与深详情

做什么：

- 建立 `backtest-runs` 的 create / start / detail 工作流
- 在前端接入 `sim-orders / sim-trades / sim-positions / pnl-snapshots / evaluation / publish`
- 收口 `research / backtests / evaluations / publishes` 间的跳转与动作衔接

完成标准：

- 用户可完成回测运行创建、启动、查看结果、评估、发布
- 基础工作流不依赖新增后端接口或数据库改动
- 至少补一条覆盖 run 主链的 E2E

### GateH-3：trade-validation 多结果工作区

做什么：

- 在当前 `/api/trading/**` 口径下重组页面
- 保留查询上下文、动作反馈、Trace 关联与多块结果同时对照
- 明确区分“当前正式接口可实现的工作区增强”和“需要新增查询面的扩展需求”

完成标准：

- 页面不再只保留最近一次瞬时反馈
- 订单、成交、账户、持仓与最近动作能在同一工作区内稳定对照
- 若发现必须新增后端能力，明确拆成后续批，不在本批偷偷扩口

### GateH-4：E2E 与测试数据治理

做什么：

- 为 GateH 新增链路补 Playwright 用例
- 建立可复用的 seed / 预置数据方案
- 降低或消除核心链路对“无预置数据即 skip”的依赖

完成标准：

- GateH 新链路均有对应 E2E
- 有明确的环境变量、seed 步骤与运行说明
- skip 数量显著下降，或已被限制在明确的环境缺口场景

---

## 5. 前置条件与开工判断

当前结论：**GateH 已具备开工条件，但仅对“基于现有接口完成前端闭环”的部分成立。**

已满足：

- GateG 冻结验收完成
- 首批所需主要接口已存在
- 首批不依赖数据库变更
- 现有前端工程、鉴权链与 E2E 基础设施已稳定

仍需单独评估：

- 若 GateH-3 追求全局列表、历史检索或结构化动作结果，需要额外后端设计
- 若 GateH-4 要把 skip 完全消除，需要先明确测试数据 seed 策略

---

## 6. 入口索引

- GateH checklist：`docs/gates/gate-h/GATE_H_CHECKLIST.md`
- GateH PR 拆分：`docs/gates/gate-h/PR_SPLIT_PLAN.md`
- GateH 工作台账：`docs/gates/gate-h/WORK.md`
- GateH 依据索引：`docs/gates/gate-h/SOURCES.md`
- GateG 冻结交接：`docs/gates/gate-g/WORK.md`
