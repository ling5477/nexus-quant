# Current Stage（当前阶段入口）

当前阶段：**GateG（前端控制台与联调）**

当前状态：**GateF 已完成并冻结；GateG-DOC-1 / GateG-DOC-2 / GateG-1 / GateG-2 / GateG-3A / GateG-3B / GateG-4A / GateG-4B / GateG-4C / GateG-5 已完成；当前代码、文档与回归矩阵已收口，剩余是受环境限制的实跑验证。**

---

## 1. 当前阶段结论

- GateD 已冻结
- GateE 已冻结
- GateF 已完成并冻结
- current 目录当前承载 GateG 正式入口
- GateG 已不再处于规划态，而是已经形成前端可运行骨架
- GateG-3 的起点不是“启动前端工程”，而是在现有前端骨架上继续接策略 / 调度 / 运行的真实字段、列表与动作
- GateG-3 本批已完成 `strategies / schedules / runs` 三页真实列表联调闭环
- GateG-3B 本批已完成 `research / backtests / evaluations / publishes` 四页真实列表联调闭环
- GateG-4A 本批已完成 `strategies / schedules / runs` 三页详情与最小动作闭环
- GateG-4B 本批已完成 `research / backtests / evaluations / publishes` 四页详情与最小动作闭环
- GateG-4C 本批已完成 `trade-validation` 的真实联调闭环
- GateG-5 本批已完成回归矩阵、构建说明、测试说明与冻结结论收口

---

## 2. GateF 最终完成事实

- `nq-research`
- `nq-backtest`
- `nq-eval`
- `research_configs / backtest_configs / backtest_runs`
- `sim_orders / sim_trades / sim_positions / sim_pnl_snapshots`
- `backtest_eval_reports`
- `publish` 写链与查询呈现已补齐
- GateF-Freeze-Fix Step 6 已完成：
  - `GET /api/research-configs`
  - `GET /api/research-configs/{configId}`
  - `GET /api/backtest-configs`
  - `GET /api/backtest-configs/{configId}`
  - `GET /api/backtest-runs`
  - `GET /api/backtest-runs/{runId}`
  - `GET /api/backtest-runs/{runId}/sim-orders`
  - `GET /api/backtest-runs/{runId}/sim-trades`
  - `GET /api/backtest-runs/{runId}/sim-positions`
  - `GET /api/backtest-runs/{runId}/pnl-snapshots`
  - `GET /api/backtest-runs/{runId}/evaluation`
  - `GET /api/backtest-runs/{runId}/publish`
- 研究配置、回测配置、回测运行及其子资源查询面已达到 GateG 前端联调最低可用标准

---

## 3. GateG 当前已完成事实

当前已经落地，而不是待规划的能力包括：

- `frontend/` 已建立正式 React 19 + TypeScript + Vite 8 工程
- 已落地登录页、token 持久化、`POST /api/auth/login` 与 `GET /api/auth/me` 恢复链路
- 已落地受保护路由守卫、控制台顶部区、左侧菜单、面包屑与主内容区
- 已落地统一 API client、Bearer token 注入、`401 / 403 / 500` 基础处理
- 已落地 `strategies / schedules / runs` 三页真实查询区、真实请求、加载态、空态、错误态与表格列表
- 已落地 `research / backtests / evaluations / publishes` 四页真实查询区、真实请求、加载态、空态、错误态与表格列表
- 已落地 `strategies / schedules / runs` 三页详情抽屉与最小动作区
- 已落地 `research / backtests / evaluations / publishes` 四页详情抽屉与最小动作区
- 已落地 `trade-validation` 的聚合查询、详情抽屉与下单 / 撤单 / 对账 / 恢复最小动作区
- 已建立 Playwright 回归矩阵，覆盖登录、dashboard、strategies 详情、research 详情、trade-validation 查询 / 详情
- 已落地以下页面的首屏壳子与正式路由：
  - `/login`
  - `/dashboard`
  - `/strategies`
  - `/schedules`
  - `/runs`
  - `/research`
  - `/backtests`
  - `/evaluations`
  - `/publishes`
  - `/trade-validation`
- 已落地 Playwright smoke baseline：
  - 打开登录页
  - 登录成功
  - 进入 dashboard
  - 跳转至少一个菜单页

---

## 4. 当前未完成范围

当前尚未完成，但已经有明确扩展点的范围包括：

- `strategies / schedules / runs` 的更完整动作（如 trigger / scan-once 等）
- `research / backtests / evaluations / publishes` 的更完整动作
- `trade-validation` 的更完整操作流和更丰富详情
- 代理执行环境里的 `vite build / playwright test` 可执行性

这些未完成项属于 GateG-3 ~ GateG-6 的正常工作范围，不代表前端仍停留在“待启动”状态。

---

## 5. 当前阻塞性质

- 当前记录里的 build / Playwright 未在当时执行环境中实跑完成，根因是 **npm registry 网络受限**
- 这不是数据库结构阻塞
- 这不是后端架构阻塞
- 这不是认证协议未定
- 这也不是前端骨架未完成

因此 GateG 当前的主阻塞是**执行环境网络条件**，不是系统设计边界。

---

## 6. 当前后端基线

- 正式 HTTP 路由统一使用 `/api/**`
- 旧 `/__gated/**` 已退出正式运行链路
- `nq-api` 已具备统一参数校验、全局异常处理与统一错误响应模型 `ApiErrorResponse`
- 正式 HTTP trace header 统一为 `X-Trace-Id`
- 最小真实认证鉴权链已完成：`POST /api/auth/login`、Bearer access token、`GET /api/auth/me`
- 正式 `/api/**` 默认受保护：`GET /api/**` 需已认证，非 `GET /api/**` 需 `ADMIN` 或 `OPERATOR`
- 关键写链事务边界已完成当前阶段收口
- 现有表结构不是 GateG 开工阻塞项

---

## 7. 当前执行顺序

1. GateG-DOC-1：主卷宗与边界冻结（已完成）
2. GateG-DOC-2：已完成事实文档收口（已完成）
3. GateG-1：前端工程骨架（已完成）
4. GateG-2：登录、鉴权守卫、布局、菜单（最小闭环已完成）
5. GateG-3A：策略 / 调度 / 运行列表联调（已完成）
6. GateG-3B：研究 / 回测 / 评估 / 发布列表联调（已完成）
7. GateG-4A：策略 / 调度 / 运行详情与最小动作（已完成）
8. GateG-4B：research / backtests / evaluations / publishes 详情与最小动作（已完成）
9. GateG-4C：trade-validation 真实联调闭环（已完成）
10. GateG-5：回归、构建与文档收口（已完成）
11. GateG 后续增强：更完整动作与更完整 E2E
