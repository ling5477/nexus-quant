# Current Checklist（GateG）

当前阶段：**GateG（前端控制台与联调）**

当前状态：**GateG 已开工；GateG-DOC-1 / GateG-DOC-2 / GateG-1 / GateG-2 / GateG-3A / GateG-3B / GateG-4A / GateG-4B / GateG-4C / GateG-5 / GateG-FREEZE-FIX / GateG-FREEZE-E2E-FIX 已完成；GateG 当前结论为 Frozen。**

---

## 1. GateF 已完成交接

- [x] GateF-DOC-1
- [x] GateF-1 研究 / 回测配置与运行骨架
- [x] GateF-2 市场数据输入与回测运行主链
- [x] GateF-3 模拟成交 / 持仓 / PnL
- [x] GateF-4 评估指标与结果查询
- [x] GateF-5 研究产物与执行域接口收口
- [x] GateF-Freeze-Fix Step 1 ~ Step 6

## 2. GateG 当前完成项

- [x] GateG-DOC-1 主卷宗
- [x] GateG-DOC-1 输入 / 输出边界
- [x] GateG-DOC-1 PR 拆分计划
- [x] GateG-DOC-1 页面 / 联调 / 回归范围定义
- [x] GateG-DOC-2 已完成事实文档收口
- [x] GateG-1 前端工程骨架
- [x] GateG-2 登录、鉴权守卫、布局、菜单最小闭环
- [~] GateG-3 `strategies / schedules / runs` 真实列表联调闭环
- [~] GateG-3 `research / backtests / evaluations / publishes` 真实列表联调闭环
- [x] GateG-4A `strategies / schedules / runs` 详情与最小动作闭环
- [x] GateG-4B `research / backtests / evaluations / publishes` 详情与最小动作闭环
- [x] GateG-4C `trade-validation` 真实联调闭环
- [x] GateG-5 回归、构建与文档收口

## 3. GateG 已落地能力

- [x] 正式前端工程：React 19 + TypeScript + Vite 8
- [x] 登录页与 token 持久化
- [x] `GET /api/auth/me` 恢复当前用户
- [x] 受保护路由守卫
- [x] 控制台布局、顶部区、左侧菜单、面包屑
- [x] 统一 API client 与 `401 / 403 / 500` 基础处理
- [x] 九个业务页首屏壳子
- [x] Playwright smoke baseline
- [x] `strategies / schedules / runs` 三页真实查询区、真实请求、加载态、空态、错误态与表格列表
- [x] `research / backtests / evaluations / publishes` 四页真实查询区、真实请求、加载态、空态、错误态与表格列表
- [x] `strategies / schedules / runs` 三页详情入口、详情展示区与最小动作区
- [x] `research / backtests / evaluations / publishes` 四页详情入口、详情展示区与最小动作区
- [x] `trade-validation` 查询区、聚合结果表、详情抽屉与最小动作区
- [x] Playwright 覆盖矩阵已整理，关键链路已具备对应 spec

## 4. GateG 待实现

- [ ] GateG-4A 后续：`strategies / schedules / runs` 的更完整动作
- [ ] GateG-4B 后续：`research / backtests / evaluations / publishes` 的更完整动作
- [ ] GateG-4C 后续：`trade-validation` 更完整操作流与更丰富详情
- [ ] GateG 后续增强：更完整 Playwright E2E 与本地实跑闭环

## 5. 环境说明

- [x] 当前已确认数据库结构不是 GateG 阻塞项
- [x] 当前已确认后端架构不是 GateG 阻塞项
- [x] 当前已确认前端骨架不是 GateG 阻塞项
- [x] `frontend` 的 `npm install` 已通过
- [x] `frontend` 的 `npx tsc -b` 已通过
- [x] `frontend` 的 `npm run build` 已在 IDE 终端通过
- [x] 当前 shell 执行器中的 `vite / playwright` 仍可能遇到 `spawn EPERM`
- [x] `nq-app` 已在 `local` profile 下稳定启动并驻留 `18888`
- [x] `OkxRecoveryService` 已记录 `configured_okx_env=dome mapped_trade_env=SIM`，并在 `local` 下安全跳过恢复链
- [x] `npm run test:e2e` 已形成有效实跑结果，最终结果为 `4 passed / 2 skipped / 0 failed`
- [x] GateG 当前已满足 Frozen 条件

## 6. 当前批次说明

- [x] 本批是 GateG-3 首批真实列表联调
- [x] 本批只完成 `strategies / schedules / runs` 三页列表闭环，不扩散到其他业务页
- [x] 本批已同步补齐对应 API、TS 类型、query keys、hooks、Playwright 与指定文档
- [x] 本批继续完成 GateG-3B，只覆盖 `research / backtests / evaluations / publishes` 四页真实列表联调
- [x] 本批继续完成 GateG-4A，只覆盖 `strategies / schedules / runs` 三页详情与最小动作闭环
- [x] 本批继续完成 GateG-4B，只覆盖 `research / backtests / evaluations / publishes` 四页详情与最小动作闭环
- [x] 本批继续完成 GateG-4C，只覆盖 `trade-validation` 的真实联调闭环
- [x] 本批完成 GateG-5，只做回归矩阵、build/test 说明、文档收口与冻结结论
- [x] 本批执行 GateG-FREEZE-FIX，只做 DEMO/REAL 凭证命中修复、local 启动修复与冻结状态判定
- [x] 本批执行 GateG-FREEZE-E2E-FIX，只做 5 条 Playwright 失败 spec 的最小修复与全量重跑
