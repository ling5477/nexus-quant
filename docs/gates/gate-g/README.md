# GateG（前端控制台与联调）

> 历史卷宗说明：本文件属于 GateG completed/frozen 历史档案，只读参考，不代表当前实现入口。当前事实以 docs/current/* 与最新源码为准.


当前状态：**GateG-DOC-1 / GateG-DOC-2 / GateG-1 / GateG-2 / GateG-3A / GateG-3B / GateG-4A / GateG-4B / GateG-4C / GateG-5 / GateG-FREEZE-FIX / GateG-FREEZE-E2E-FIX 已完成；GateG 目前已 Frozen，后续增强归属 GateH。**

GateG 是 GateF 之后的独立阶段。GateG 不回头重写 GateF，也不以前置数据库大改为条件。

---

## 1. GateG 正式定义

GateG 负责把当前已经具备后端基础能力的系统，补成一套可登录、可浏览、可操作、可回归的前端控制台。

本阶段正式主线为：

`Login -> Guard -> Layout -> Menu -> Strategy / Schedule / Run -> Research / Backtest / Evaluation / Publish -> Trading Verification -> Playwright Regression`

GateG 当前不是“前端是否开工”的讨论阶段，而是**在已完成前端骨架和认证闭环的基础上，继续把页面联调做实**。

---

## 2. GateF / GateG 边界

### 2.1 GateF 已冻结内容

- 研究配置、回测配置、回测运行主链
- sim_* 事实链
- evaluation / publish 查询与最小写链
- `/api/**` 正式路由收口
- 最小真实认证鉴权链

### 2.2 GateG 当前负责

- 正式前端工程
- 登录页与鉴权守卫
- 基础布局、菜单与路由
- 策略定义 / 调度 / 运行页面
- 研究配置 / 回测配置 / 回测运行页面
- 回测运行详情中的 `sim_* / evaluation / publish` 视图
- 交易验证操作页
- Playwright 关键链路回归

### 2.3 GateG 不负责

- 回头重写 GateF 主链
- 先做一轮数据库大改再开前端
- 新交易所扩张
- 合约 / 杠杆 / 期货扩展
- 研究平台第二轮扩张

---

## 3. 当前已完成事实

### 3.1 已落地的前端能力

- `frontend/` 已建立正式 React 19 + TypeScript + Vite 8 工程
- 已落地登录页、token 持久化、`POST /api/auth/login`、`GET /api/auth/me`
- 已落地受保护路由守卫、控制台布局、左侧菜单、页头与面包屑
- 已落地统一 Axios API client、Bearer token 注入与 `401 / 403 / 500` 基础处理
- 已落地以下正式路由与页面首屏壳子：
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
  - 登录成功
  - 进入 dashboard
  - 跳转至少一个菜单页

### 3.2 已具备的后端联调基线

- 后端正式接口已统一到 `/api/**`
- `/api/auth/login`、`/api/auth/me` 已可用
- 策略、调度、运行、研究、回测、评估、发布、交易验证接口已可供后续页面联调
- 认证链已经从 stub / noop 切到最小真实链路

### 3.3 当前结论

当前仓库已经不是“后端已就位、前端尚未开工”的状态，而是**后端主链已就位，前端骨架与认证闭环已完成，后续进入具体业务页面联调阶段**。

---

## 4. 当前未完成范围

当前未完成，但已经不再属于“前端主功能未落地”的范围包括：

- `npm run build` 已在 IDE 终端通过，但当前 shell 执行器仍受 `spawn EPERM` 限制
- `nq-app` 已能在 `local` 下稳定启动，`.env` 中按 DOME/REAL 分组的凭证已可被运行时命中
- `npm run test:e2e` 已完成实跑，最终结果为 `4 passed / 2 skipped / 0 failed`
- 更完整动作和更大范围的 E2E 仍属后续增强项

这些工作属于 GateG-3 ~ GateG-6 的正常收口任务，不构成 GateG 启动阻塞。

---

## 5. 环境与阻塞说明

- `frontend` 的 `npm install`、`npx tsc -b` 已通过
- `frontend` 的 `npm run build` 已在 IDE 终端通过
- `OkxRecoveryService` 当前记录 `configured_okx_env=dome mapped_trade_env=SIM`，说明恢复链命中的仍是 DEMO/SIM 组
- `npm run test:e2e` 已形成有效实跑结果，最终结果为 `4 passed / 2 skipped / 0 failed`
- `research-detail` 与 `strategies-detail` 在无预置策略/研究配置数据时按设计 skip，不计为失败
- 当前 shell 执行器对 `vite / playwright` 仍可能报 `spawn EPERM`

因此 GateG 当前已无冻结阻塞，当前卷宗只作为冻结基线；后续增强与新批次统一归属 GateH。

---

## 6. GateG 完成标准

1. `frontend/` 正式工程骨架已建立
2. 登录态、鉴权守卫、基础布局、菜单与路由已形成闭环
3. 策略 / 调度 / 运行页面已具备列表、详情与最小动作
4. 研究 / 回测 / 评估 / 发布页面已具备列表、详情与最小动作
5. 交易验证页已具备聚合查询、详情与最小动作
6. Playwright 已覆盖登录、dashboard、策略详情、研究详情、交易验证查询/详情链路

---

## 7. 当前实施顺序

1. GateG-DOC-1：主卷宗、输入边界、页面与回归范围（已完成）
2. GateG-DOC-2：已完成事实文档收口（已完成）
3. GateG-1：前端工程骨架（已完成）
4. GateG-2：登录、鉴权守卫、布局、菜单（已完成）
5. GateG-3A / GateG-3B：列表联调（已完成）
6. GateG-4A / GateG-4B / GateG-4C：详情与最小动作（已完成）
7. GateG-5：回归、构建与文档收口（已完成）
8. GateG-FREEZE-FIX：修复 local DEMO/REAL 凭证命中与恢复链阻塞（已完成）
9. GateG-FREEZE-E2E-FIX：修复 5 条 Playwright 失败 spec 并完成全量回归（已完成）

---

## 8. 入口索引

- GateG checklist：`docs/gates/gate-g/GATE_G_CHECKLIST.md`
- GateG 架构：`docs/gates/gate-g/ARCHITECTURE.md`
- GateG 模块边界：`docs/gates/gate-g/MODULES.md`
- GateG 契约：`docs/gates/gate-g/CONTRACTS.md`
- GateG 验收清单：`docs/gates/gate-g/TEST_CASES.md`
- GateG PR 拆分：`docs/gates/gate-g/PR_SPLIT_PLAN.md`
- GateG 工作台账：`docs/gates/gate-g/WORK.md`
- GateG 依据索引：`docs/gates/gate-g/SOURCES.md`
