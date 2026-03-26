# GateG（前端控制台与联调）

当前状态：**GateG-DOC-1 已完成；GateG-1 尚未开始。**

GateG 是 GateF 之后的独立阶段。GateG 不回头重写 GateF，也不以前置数据库大改为条件。

---

## 1. GateG 正式定义

GateG 负责把当前已经具备后端基础能力的系统，补成一套可登录、可浏览、可操作、可回归的前端控制台。

本阶段正式主线为：

`Login -> Guard -> Layout -> Menu -> Strategy / Schedule / Run -> Research / Backtest / Evaluation / Publish -> Trading Verification -> Playwright Regression`

GateG 解决的是：**后端已经具备最小业务能力后，如何形成面向实际使用与联调的控制台。**

---

## 2. GateF / GateG 边界

### 2.1 GateF 已冻结内容

- 研究配置、回测配置、回测运行主链
- sim_* 事实链
- evaluation / publish 查询与最小写链
- `/api/**` 正式路由收口
- 最小真实认证鉴权链

### 2.2 GateG 当前负责

- 前端工程骨架
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

## 3. 基于仓库现状的真实起点

### 3.1 已存在事实

- `frontend/` 当前已有 `package.json` 与 Playwright 依赖
- 后端正式接口已统一到 `/api/**`
- `/api/auth/login`、`/api/auth/me` 已可用
- 策略、调度、运行、研究、回测、评估、发布、交易验证接口已可供前端联调
- 认证链已经从 stub / noop 切到最小真实链路

### 3.2 当前缺口

- 还没有真正的前端工程骨架
- 还没有登录页、路由守卫、基础布局与菜单
- 还没有把后端现有接口收成统一 API 模块
- 还没有可操作的交易验证页面
- 还没有 Playwright 关键链路回归用例

### 3.3 当前结论

当前仓库不是前端从零需求定义，而是**后端已基本就位、前端尚未正式开工**的状态。GateG 的工作重点不是发散设计，而是把现有接口与流程落成可用控制台。

---

## 4. GateG 完成标准

1. `frontend/` 建立正式工程骨架
2. 登录态、鉴权守卫、基础布局、菜单与路由形成最小闭环
3. 策略 / 调度 / 运行页面可查可触发
4. 研究 / 回测 / 评估 / 发布页面可查可操作
5. 交易验证页可完成下单、撤单、对账、恢复与结果核对
6. Playwright 覆盖登录、关键页面进入、关键操作链路

---

## 5. 当前实施顺序

1. GateG-DOC-1：主卷宗、输入边界、页面与回归范围
2. GateG-1：前端工程骨架
3. GateG-2：登录、鉴权守卫、布局、菜单
4. GateG-3：策略 / 调度 / 运行页面
5. GateG-4：研究 / 回测 / 评估 / 发布页面
6. GateG-5：交易验证操作页
7. GateG-6：Playwright 回归

---

## 6. 入口索引

- GateG checklist：`docs/gates/gate-g/GATE_G_CHECKLIST.md`
- GateG 架构：`docs/gates/gate-g/ARCHITECTURE.md`
- GateG 模块边界：`docs/gates/gate-g/MODULES.md`
- GateG 契约：`docs/gates/gate-g/CONTRACTS.md`
- GateG 验收清单：`docs/gates/gate-g/TEST_CASES.md`
- GateG PR 拆分：`docs/gates/gate-g/PR_SPLIT_PLAN.md`
- GateG 工作台账：`docs/gates/gate-g/WORK.md`
- GateG 依据索引：`docs/gates/gate-g/SOURCES.md`
