# GateE PR_SPLIT_PLAN

原则：

- 一个 PR 只解决一类问题
- 按依赖顺序推进
- 文档、schema、契约、实现尽量分离，避免巨型 PR

---

## 1. 执行顺序总览

- [x] GateE-DOC-2：开工基线收口
- [x] GateE-0.1：Binance background reconcile 噪音治理
- [x] GateE-0.2：schema / metadata / contract 收口
- [x] GateE-0.3：adapter 返回模型一致性
- [x] GateE-1.1：策略定义与注册模型
- [x] GateE-1.2：策略运行主链与手动 trigger
- [x] GateE-2.1：调度任务与计划配置
- [ ] GateE-2.2：窗口 / 去重 / 串行化
- [ ] GateE-2.3：运行结果回传与查询面

---

## 2. GateE-DOC-2：开工基线收口

目标：

- 校对仓库现状
- 冻结对象语义
- 修正 current 入口
- 形成可执行 PR 路线

范围：

- `docs/gates/gate-e/*`
- `docs/current/*`

不做：

- 任何业务代码改动
- 任何 migration

---

## 3. GateE-0.1：Binance background reconcile 噪音治理

目标：

- 收敛 `credentials missing`
- 收敛 `-1021`
- 收敛 cooldown 内重复触发噪音

建议文件：

- `backend/nq-scheduler/**`
- `backend/nq-adapter-binance/**`
- 必要的 `docs/gates/gate-e/DECISIONS.md`
- 必要的 `docs/gates/gate-e/WORK.md`

验收：

- 同窗口内不再重复刷屏
- 真失败与降噪跳过可区分

---

## 4. GateE-0.2：schema / metadata / contract 收口

目标：

- 明确 `PlaceOrderCommand.strategyId` 兼容策略
- 统一 `strategyRunId` 命名
- 决定是否在本批引入 `V5__gate_e_contract_alignment.sql`

建议文件：

- `backend/nq-contracts/**`
- `backend/nq-core/**`
- `backend/nq-api/**`
- `backend/nq-infra/**`
- `docs/gates/gate-e/CONTRACTS.md`
- `docs/gates/gate-e/DB_SCHEMA.md`
- `docs/gates/gate-e/DECISIONS.md`

验收：

- 不再出现同义不同名
- 兼容方案明确且有回归测试

---

## 5. GateE-0.3：adapter 返回模型一致性

目标：

- 统一 place / cancel / query-confirm / reconcile 的响应口径

建议文件：

- `backend/nq-adapter-api/**`
- `backend/nq-adapter-binance/**`
- `backend/nq-adapter-okx/**`
- `backend/nq-api/**`
- `docs/gates/gate-e/DECISIONS.md`

验收：

- 上层不再按 venue 打补丁

---

## 6. GateE-1.1：策略定义与注册模型

目标：

- 新增 `StrategyDefinition` 契约
- 新增注册 / 启停入口
- 引入最小定义表

建议文件：

- `backend/nq-contracts/**`
- `backend/nq-app/**`
- `backend/nq-api/**`
- `backend/nq-infra/**`
- `docs/gates/gate-e/DB_SCHEMA.md`
- `docs/gates/gate-e/TEST_CASES.md`

验收：

- 能注册、查询、启停一个策略定义

---

## 7. GateE-1.2：策略运行主链与手动 trigger

目标：

- 新增 manual trigger
- 生成 `requestId` 与 `strategyRunId`
- 打通到现有执行域

建议文件：

- `backend/nq-core/**`
- `backend/nq-scheduler/**`
- `backend/nq-app/**`
- `backend/nq-api/**`
- 必要的 `backend/nq-infra/**`

验收：

- 手动 trigger 后能产生带 `strategy_run_id` 的订单

---

## 8. GateE-2.1：调度任务与计划配置

目标：

- 新增调度作业模型
- 建立 schedule 注册与启停
- 让 scheduler 能安全地产生 trigger 请求

建议文件：

- `backend/nq-scheduler/**`
- `backend/nq-infra/**`
- `backend/nq-app/**`
- `backend/nq-api/**`

验收：

- 一个启用的 schedule 能正常创建 trigger 请求

---

## 9. GateE-2.2：窗口 / 去重 / 串行化

目标：

- 实现 dedupKey
- 实现窗口控制
- 实现同策略串行运行保护

建议文件：

- `backend/nq-scheduler/**`
- `backend/nq-core/**`
- `backend/nq-infra/**`

验收：

- 重复触发不会双跑
- 窗口外不会误下单

---

## 10. GateE-2.3：运行结果回传与查询面

目标：

- 输出 `StrategyRunResult`
- 按 `strategyRunId` 查询订单、成交、账本结果

建议文件：

- `backend/nq-api/**`
- `backend/nq-core/**`
- `backend/nq-ledger/**`
- 必要的 `backend/nq-infra/**`

验收：

- 可从运行结果页反查执行结果

---

## 11. PR 粒度约束

- schema migration 单独成 PR，除非与同批实现不可拆
- 大规模 rename 单独成 PR
- 格式化不和业务逻辑混提
- 每个 PR 必须回填 `docs/gates/gate-e/WORK.md` 与必要 checklist
