# GateG 架构与页面结构

## 1. 前端技术栈冻结

GateG 前端技术栈固定为：

- React
- TypeScript
- Vite
- React Router
- TanStack Query
- Axios
- Zustand
- Ant Design
- Playwright

这套栈的目的不是追求花哨，而是确保页面、请求、状态、表单、回归都能快速收口。

---

## 2. 前端目录建议

```text
frontend/
  src/
    api/
      auth/
      strategies/
      strategySchedules/
      strategyRuns/
      research/
      backtests/
      trading/
    app/
      router/
      store/
      providers/
    components/
      layout/
      tables/
      forms/
      feedback/
    pages/
      auth/
      strategies/
      schedules/
      strategyRuns/
      researchConfigs/
      backtestConfigs/
      backtestRuns/
      trading/
    hooks/
    utils/
    types/
```

---

## 3. 页面路由冻结

- `/login`
- `/strategies`
- `/strategies/:strategyCode`
- `/strategy-schedules`
- `/strategy-schedules/:scheduleId`
- `/strategy-runs`
- `/strategy-runs/:runId`
- `/research-configs`
- `/research-configs/:configId`
- `/backtest-configs`
- `/backtest-configs/:configId`
- `/backtest-runs`
- `/backtest-runs/:runId`
- `/trading`

其中 `/backtest-runs/:runId` 下固定使用 tab：

- 概览
- sim-orders
- sim-trades
- sim-positions
- pnl-snapshots
- evaluation
- publish

---

## 4. 布局结构冻结

- 左侧菜单：策略、调度、运行、研究、回测、交易验证
- 顶部区域：当前用户、环境标识、退出登录
- 主内容区：列表 / 详情 / 操作区
- 所有详情页统一保留 trace / 主键 / 状态字段显示区

---

## 5. 请求层约束

- 所有请求统一由 Axios instance 发起
- `Authorization: Bearer <token>` 统一由请求拦截器追加
- 401 统一回到登录页
- 403 统一显示权限不足
- 错误响应统一读取 `ApiErrorResponse`
- 列表与详情查询统一由 TanStack Query 管理缓存与重试

---

## 6. 状态管理约束

- 鉴权态使用 Zustand 保存 token、currentUser、登录状态
- 服务端数据不落到全局 store，统一交给 TanStack Query
- 表单只保留页面局部状态，不做全局表单仓库

---

## 7. 联调边界

- 前端只对接正式 `/api/**`
- 不直接调用 `__gated` 历史路由
- 需要后端补口时，只允许补 GateG 页面必需字段 / DTO / 查询条件
- 不把前端联调扩成新的表结构重构任务
