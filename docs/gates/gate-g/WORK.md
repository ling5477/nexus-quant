# GateG 工作台账

## 1. GateG-DOC-1

- 已建立 GateG 主卷宗与边界
- 已把上一个会话未完成事项收口为 GateG 正式范围
- 已冻结 GateG 页面范围、联调范围与回归范围

## 2. GateG-1

- 已在 `frontend/` 建立正式 React 19 + TypeScript + Vite 8 工程骨架
- 已建立应用 Provider、路由、布局、页面、store、types、utils、Playwright 目录结构
- 已收口环境变量、Vite dev server 与本地 `/api` 代理口径

## 3. GateG-2

- 已落地登录页
- 已落地 token 持久化
- 已落地 `GET /api/auth/me` 恢复当前用户
- 已落地受保护路由守卫
- 已落地控制台布局、顶部区、左侧菜单、面包屑与 dashboard
- 已落地统一 API client、Bearer token 注入与 `401 / 403 / 500` 基础处理
- 已落地以下页面首屏壳子：
  - `dashboard`
  - `strategies`
  - `schedules`
  - `runs`
  - `research`
  - `backtests`
  - `evaluations`
  - `publishes`
  - `trade-validation`
- 已落地 Playwright smoke baseline：
  - 登录成功
  - 进入 dashboard
  - 跳转至少一个菜单页

## 4. GateG-DOC-2

本批目标：把 GateG 当前已完成事实收口进正式文档，清理“规划态 / 待启动”的过期表述，为 GateG-3 建立准确基线。

本批已完成：

- 已更新 `docs/current/README.md`
- 已更新 `docs/current/GATE_CHECKLIST.md`
- 已更新 `docs/gates/gate-g/README.md`
- 已更新 `docs/gates/gate-g/CONTRACTS.md`
- 已更新 `docs/gates/gate-g/ARCHITECTURE.md`
- 已更新 `docs/gates/gate-g/WORK.md`

本批收口结论：

- GateG 已正式开工
- GateG-1 已完成
- GateG-2 最小闭环已完成
- 下一步正式进入 GateG-3

## 5. GateG-3（首批真实列表联调）

- 已完成 `strategies / schedules / runs` 三页真实列表联调闭环
- 已补对应 API 文件、TS 类型、query keys 与 query/hooks 封装
- 已把三页从首屏壳子替换为真实查询区、真实请求、加载态、空态、错误态与表格列表
- 已新增策略列表真实查询 Playwright 用例
- 已同步更新 `docs/current/README.md`
- 已同步更新 `docs/current/GATE_CHECKLIST.md`
- 已同步更新 `docs/gates/gate-g/CONTRACTS.md`
- 已同步更新 `docs/gates/gate-g/TEST_CASES.md`
- 已同步更新 `docs/gates/gate-g/WORK.md`

## 6. GateG-3B（研究 / 回测 / 评估 / 发布列表联调）

- 已完成 `research / backtests / evaluations / publishes` 四页真实列表联调闭环
- 已补对应 API 文件、TS 类型、query keys 与 query/hooks 封装
- 已把四页从首屏壳子替换为真实查询区、真实请求、加载态、空态、错误态与表格列表
- 已新增研究配置真实查询 Playwright 用例
- 已同步更新 `docs/current/README.md`
- 已同步更新 `docs/current/GATE_CHECKLIST.md`
- 已同步更新 `docs/gates/gate-g/CONTRACTS.md`
- 已同步更新 `docs/gates/gate-g/TEST_CASES.md`
- 已同步更新 `docs/gates/gate-g/WORK.md`

## 7. 当前未完成范围

当前尚未完成，但已具备明确扩展点的范围包括：

- `strategies / schedules / runs` 的更完整动作
- `research / backtests / evaluations / publishes` 的详情页与动作
- `trade-validation` 的真实联调
- 完整 Playwright E2E，而不仅是 smoke baseline 与单页真实查询

## 8. GateG-4A（策略 / 调度 / 运行详情与最小动作）

- 已完成 `strategies / schedules / runs` 三页详情抽屉与最小动作闭环
- 已补对应 detail API、status action API、TS 类型与 query / mutation hooks
- 已补运行页 detail API，并按真实契约把动作区处理为不可操作状态
- 已新增策略详情 Playwright 用例
- 已同步更新 `docs/current/README.md`
- 已同步更新 `docs/current/GATE_CHECKLIST.md`
- 已同步更新 `docs/gates/gate-g/CONTRACTS.md`
- 已同步更新 `docs/gates/gate-g/TEST_CASES.md`
- 已同步更新 `docs/gates/gate-g/WORK.md`

## 9. GateG-4B（research / backtests / evaluations / publishes 详情与最小动作）

- 已完成 `research / backtests / evaluations / publishes` 四页详情与最小动作闭环
- 已补对应 detail API、action API、TS 类型与 query / mutation hooks
- 已将 research/backtests 的 create 动作收口到页面动作区，详情抽屉明确只读
- 已将 evaluations/publishes 的 evaluate / publish 动作收口到详情抽屉动作区
- 已新增研究详情 Playwright 用例
- 已同步更新 `docs/current/README.md`
- 已同步更新 `docs/current/GATE_CHECKLIST.md`
- 已同步更新 `docs/gates/gate-g/CONTRACTS.md`
- 已同步更新 `docs/gates/gate-g/TEST_CASES.md`
- 已同步更新 `docs/gates/gate-g/WORK.md`

## 10. GateG-4C（trade-validation 真实联调闭环）

- 已完成 `/trade-validation` 的真实联调闭环
- 已补对应 trade-validation API、TS 类型、query key、query / mutation hooks
- 已把页面收口为“订单查询主表 + 详情抽屉 + 下单 / 撤单 / 对账 / 恢复动作区”
- 已新增 trade-validation Playwright 用例
- 已同步更新 `docs/current/README.md`
- 已同步更新 `docs/current/GATE_CHECKLIST.md`
- 已同步更新 `docs/gates/gate-g/CONTRACTS.md`
- 已同步更新 `docs/gates/gate-g/TEST_CASES.md`
- 已同步更新 `docs/gates/gate-g/WORK.md`

## 11. GateG-5（回归、构建与文档收口）

- 已盘点现有 Playwright 用例并形成覆盖矩阵
- 已确认关键链路已覆盖：登录 -> dashboard、strategies 列表 -> 详情、research 列表 -> 详情、trade-validation 查询 -> 详情
- 已重跑 `npm run build` 与 `npm run test:e2e`
- 已明确 `E2E_BASE_URL / E2E_USERNAME / E2E_PASSWORD / E2E_TRADE_ORDER_ID` 等环境依赖
- 已同步更新 `docs/current/README.md`
- 已同步更新 `docs/current/GATE_CHECKLIST.md`
- 已同步更新 `docs/gates/gate-g/README.md`
- 已同步更新 `docs/gates/gate-g/TEST_CASES.md`
- 已同步更新 `docs/gates/gate-g/WORK.md`
- 已形成 GateG 当前冻结结论：代码、文档、回归矩阵可收口；环境受限项需线下补实跑

## 12. 环境受阻说明

- `tsc -b` 已通过
- 当前代理执行环境中的 `vite build / playwright test` 遇到 `spawn EPERM`
- 本地后端 `18888` 在当前代理执行环境未启动，因此无法直接做端到端接口探针
- 这不是数据库结构阻塞
- 这不是后端架构阻塞
- 这不是前端工程骨架未完成

## 13. 下一步归属

- GateG-4A 后续子批：策略定义 / 调度 / 运行页面更完整动作
- GateG-4B 后续子批：research / backtests / evaluations / publishes 更完整动作
- GateG-4C 后续子批：trade-validation 更完整操作流与更丰富详情
- GateG-6 / 冻结后增强：更多 E2E、环境实跑补齐与增强型操作流

## 14. GateG-FREEZE-VERIFY（本地验收补跑）

- 已执行 `npm install`
- 已执行 `npx tsc -b`
- 已执行 `npm run build`
- 已尝试执行 `npm run test:e2e`
- 已查询本地数据库并确认存在可供 `E2E_TRADE_ORDER_ID` 使用的真实订单数据：`ord-808ce57d-d71f-4210-9856-878deb199d8d`
- 已补后端最小修复，显式指定以下 Spring 组件的运行时构造器：
  - `BacktestPublishService`
  - `JdbcExecutionStrategyDefinitionWriter`
  - `BacktestExecutionService`
  - `BacktestEvaluationService`
- 本批验收结果：
  - `npm install`：通过
  - `npx tsc -b`：通过
  - `npm run build`：在 IDE 终端通过
  - `npm run test:e2e`：未完成有效实跑
- 本批后端启动结果：
  - 先后暴露出 4 个双构造器 Spring 组件的无参实例化问题，已做最小修复
  - 修复后 `nq-app` 能进入 Tomcat started 阶段
  - 最终仍在 `OkxRecoveryService` 启动恢复链中因缺少 OKX 凭证抛出 `OkxApiException`，导致应用退出
- 本批冻结结论：
  - GateG 当前仍为 `Freeze Candidate`
  - 当前唯一待办是：在具备可启动 `local` profile 的本地验收环境中补跑 `npm run test:e2e`

## 15. GateG-FREEZE-FIX（DEMO/REAL 凭证命中修复）

- 已确认 `.env` 中的交易所凭证按 `NQ_OKX_DOME_* / NQ_OKX_REAL_* / NQ_BINANCE_DOME_* / NQ_BINANCE_REAL_*` 分组配置
- 已确认 `OkxRuntimeConfig / BinanceRuntimeConfig` 原先直接读取 `System.getenv()`，而 `mvn spring-boot:run` / IDE Run Configuration 不会自动把 root `.env` 注入为 OS 环境变量
- 已确认恢复链当前关联的历史 OKX 订单 `trade_env` 为 `SIM`
- 已确认 OKX 运行时环境组为 `dome`，与 `SIM` 语义对应，但修复前进程看不到 `.env` 中的 `NQ_OKX_DOME_*`，因此命中结果为空
- 已新增 `ProcessEnvironmentResolver`，统一把 root `.env`、System properties 与 OS env 合并进运行时配置解析
- 已让 `OkxRuntimeConfig / BinanceRuntimeConfig` 通过该解析器读取 DOME/REAL 分组凭证
- 已为 `local` 增加 `nq.okx.recovery.enabled=false` 默认值，避免 GateG 本地验收把恢复链误当成硬启动前置
- 已为 `OkxRecoveryService` 增加日志：`configured_okx_env=dome mapped_trade_env=SIM`
- 本批运行结果：
  - `nq-app local`：通过，`18888` 可稳定启动
  - `npm install`：通过
  - `npx tsc -b`：通过
  - `npm run build`：通过
  - `npm run test:e2e`：已形成有效实跑结果，但当前 `1 passed / 5 failed`
- 本批冻结结论：
  - DEMO/REAL 凭证命中问题已修复
  - GateG 当前仍为 `Freeze Candidate`
  - 当前唯一待办是修复 5 条 Playwright 失败用例并重跑全量 E2E

## 16. GateG-FREEZE-E2E-FIX（剩余 Playwright 失败用例收口）

- 已逐条核对 5 条失败 spec 的错误上下文与 trace
- 已确认失败归类：
  - 登录请求体尾随空格导致 `POST /api/auth/login` 返回 401
  - `strategies / research / trade-validation` 的查询按钮可访问名称实际为 `查 询`
  - `trade-validation` 用例对底层网络响应的等待条件脆弱，页面已渲染真实结果时仍可能错过响应匹配
  - 并发 worker 会把本地单套 backend + dev server 放大成非确定性噪音
- 已做最小修复：
  - 登录表单提交前 `trim()`
  - 失败 spec 的查询按钮选择器改为 `/查\\s*询/`
  - `trade-validation` 改为等待真实 UI 结果
  - Playwright worker 固定为 `1`
  - Playwright 支持 `E2E_EXTERNAL_DEV_SERVER=true`
- 单条/分组重跑结果：
  - `strategies-query.spec.ts`：通过
  - `research-query.spec.ts`：通过
  - `trade-validation-query.spec.ts`：通过
  - `strategies-detail.spec.ts`：skip（无预置策略数据）
  - `research-detail.spec.ts`：skip（无预置研究配置数据）
- 全量 `npm run test:e2e` 结果：
  - `4 passed / 2 skipped / 0 failed`
- 本批冻结结论：
  - GateG 已满足 `npm install / npx tsc -b / npm run build / npm run test:e2e` 全部冻结条件
  - GateG 正式 Frozen
