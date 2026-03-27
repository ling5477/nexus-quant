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
