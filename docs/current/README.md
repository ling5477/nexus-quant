# Current Stage（当前阶段入口）

当前阶段：**GateG（前端控制台与联调）**

当前状态：**GateF 已完成并冻结；GateG-DOC-1 已完成；下一步进入 GateG-1。**

---

## 1. 当前阶段结论

- GateD 已冻结
- GateE 已冻结
- GateF 已完成并冻结
- current 目录不再表示 GateF 进行中
- current 目录现在承载 GateG 当前入口
- GateG 主卷宗、输入清单、PR 拆分与测试清单已建立

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
- 研究配置、回测配置、回测运行及其子资源查询面已达到 GateG 前端联调前最低可用标准

---

## 3. GateG 正式范围

GateG 只做以下工作：

- 前端工程骨架
- 登录页与鉴权守卫
- 基础布局、菜单、路由
- 策略定义 / 调度 / 运行页面
- 研究配置 / 回测配置 / 回测运行页面
- 回测运行详情中的 `sim_* / evaluation / publish` 视图
- 交易验证操作页
- Playwright 关键链路回归

当前明确不做：

- 先做一轮数据库大改再开工
- 回头重写 GateF 主链
- 回头清理 GateE / GateD 历史执行债务
- 新交易所扩张
- 研究平台二次扩张

---

## 4. 当前后端基线

- 正式 HTTP 路由统一使用 `/api/**`
- 旧 `/__gated/**` 已退出正式运行链路
- `nq-api` 已具备统一参数校验、全局异常处理与统一错误响应模型 `ApiErrorResponse`
- 正式 HTTP trace header 统一为 `X-Trace-Id`
- 最小真实认证鉴权链已完成：`POST /api/auth/login`、Bearer access token、`GET /api/auth/me`
- 正式 `/api/**` 默认受保护：`GET /api/**` 需已认证，非 `GET /api/**` 需 `ADMIN` 或 `OPERATOR`
- 关键写链事务边界已完成当前阶段收口
- 现有表结构不是 GateG 开工阻塞项

---

## 5. 当前执行顺序

1. GateG-DOC-1：主卷宗与边界冻结
2. GateG-1：前端工程骨架
3. GateG-2：登录、鉴权守卫、布局、菜单
4. GateG-3：策略 / 调度 / 运行页面
5. GateG-4：研究 / 回测 / 评估 / 发布页面
6. GateG-5：交易验证操作页
7. GateG-6：Playwright 回归
