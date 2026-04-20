# GateG TEST_CASES

> 历史卷宗说明：本文件属于 GateG completed/frozen 历史档案，只读参考，不代表当前实现入口。当前事实以 docs/current/* 与最新源码为准.

# GateG 测试与验收清单

状态约定：

- `[x]` 已完成
- `[~]` 文档基线已完成，代码未开始
- `[ ]` 未开始

---

## 1. GateG-DOC-1 文档基线验收

- 当前状态：`[x]`
- 验证点：
  - GateG 主卷宗已建立
  - 输入 / 输出边界明确
  - 页面范围明确
  - 联调范围明确
  - PR 拆分可执行
  - 未完成事项已纳入 GateG 正式范围

---

## 2. GateG 后续实现验收

### UC-G1 前端工程骨架

- 当前状态：`[x]`
- 预期：
  - 能启动前端开发环境
  - 路由、请求层、状态管理、页面目录建立完成

### UC-G2 登录与鉴权守卫

- 当前状态：`[x]`
- 预期：
  - 能登录
  - 能获取当前用户
  - 未登录无法进入受保护页面
  - 已登录可正常刷新恢复状态

### UC-G3 策略 / 调度 / 运行页面

- 当前状态：`[~]`
- 预期：
  - 能查询策略列表与详情
  - 能创建策略
  - 能触发策略
  - 能查询调度列表与详情
  - 能执行 `scan-once`
  - 能查询运行列表与详情
- 当前说明：
  - 已完成 `/strategies / schedules / runs` 三页真实列表联调闭环
  - 已具备真实查询区、请求调用、加载态、空态、错误态与表格列表
  - 已补详情入口、详情抽屉与最小动作区
  - 更完整动作仍待后续子批补齐

### UC-G4 研究 / 回测 / 评估 / 发布页面

- 当前状态：`[~]`
- 预期：
  - 能查询与创建研究配置、回测配置
  - 能创建回测运行并执行 `start / evaluate / publish`
  - 能查看 `sim-orders / sim-trades / sim-positions / pnl-snapshots / evaluation / publish`
- 当前说明：
  - 已完成 `/research / backtests / evaluations / publishes` 四页真实列表联调闭环
  - 已具备真实查询区、请求调用、加载态、空态、错误态与表格列表
  - 已补详情入口、详情抽屉与最小动作区
  - 更完整动作仍待后续子批补齐

### UC-G5 交易验证操作页

- 当前状态：`[~]`
- 预期：
  - 能下单
  - 能撤单
  - 能查询订单、成交、持仓、账户
  - 能执行 `reconciliation / recovery run-once`
- 当前说明：
  - 已完成 `/trade-validation` 的真实联调闭环
  - 已具备真实查询区、聚合结果表、详情抽屉与最小动作区
  - 已接入下单、撤单、对账、恢复四类动作
  - 更完整操作流与更丰富详情仍待后续子批补齐

### UC-G6 Playwright 回归

- 当前状态：`[~]`
- 预期：
  - 覆盖登录
  - 覆盖至少一个策略链路
  - 覆盖至少一个回测链路
  - 覆盖至少一个交易验证链路
- 当前说明：
  - 已补登录成功、进入 dashboard、跳转 strategies 的 smoke baseline
  - 已新增策略列表真实查询用例：登录、进入策略页、触发查询、校验 `/api/strategies` 响应与列表渲染
  - 已新增研究配置真实查询用例：登录、进入研究页、触发查询、校验 `/api/research-configs` 响应与列表渲染
  - 已新增策略详情链路用例：登录、查询策略列表、进入详情抽屉、校验详情与动作区渲染
  - 已新增研究详情链路用例：登录、查询研究配置列表、进入详情抽屉、校验详情与动作区渲染
  - 已新增交易验证查询/详情链路用例：登录、进入 trade-validation、按订单查询、打开详情抽屉
  - 当前关键链路矩阵已覆盖：登录 -> dashboard、strategies 列表 -> 详情、research 列表 -> 详情、trade-validation 查询 -> 详情
  - GateG-FREEZE-FIX / E2E-FIX 实跑结果：
    - `npm install`：通过
    - `npx tsc -b`：通过
    - `npm run build`：在 IDE 终端通过
    - `npm run test:e2e`：完成有效实跑，结果 `4 passed / 2 skipped / 0 failed`
  - GateG-FREEZE-FIX 环境结论：
    - 本地 `nq-app` 已能稳定启动
    - 恢复链日志已明确：`configured_okx_env=dome mapped_trade_env=SIM`
    - `local` 下恢复链已安全跳过，不再阻塞登录页与页面联调
  - GateG-FREEZE-E2E-FIX 修复点：
    - 登录表单提交前统一 `trim()`，消除 Playwright 提交体尾随空格导致的 401
    - `strategies / research / trade-validation` 查询按钮断言改为匹配 Ant Design 渲染出来的 `查 询`
    - `trade-validation` 用例改为等待真实 UI 结果而不是脆弱的底层响应匹配
    - Playwright worker 固定为串行，支持 `E2E_EXTERNAL_DEV_SERVER=true` 复用外部已启动 dev server
  - 当前结论：GateG 已 Frozen
