# GATE_E_CHECKLIST

GateE 名称：**策略接入与调度编排**

当前状态：**文档开工基线已完成，业务代码尚未进入 GateE 批次实施**

状态约定：

- `[x]` 已完成
- `[~]` 已冻结文档语义、代码待收口
- `[ ]` 未开始

---

## 0. 开工基线

- [x] 已核对 `docs/current/*`、`docs/gates/gate-d/*`、`docs/gates/gate-e/*`
- [x] 已核对 `strategy_runs`
- [x] 已核对 `orders.strategy_run_id`
- [x] 已核对 `StrategyScheduler / NoopStrategyScheduler / GateBDemoStrategyRunner`
- [x] 已核对 `PlaceOrderRequest / PlaceOrderCommand / AdapterOrderRequest`
- [x] 已冻结 GateE 主链、对象语义、状态机、测试清单、PR 顺序

---

## 1. GateE-0 前置治理

- [x] GateE-0.1 Binance background reconcile 噪音治理
- [x] GateE-0.2 schema / metadata / contract 收口
- [x] GateE-0.3 adapter 返回模型一致性

---

## 2. GateE-1 策略接入

- [ ] 新增策略定义模型
- [ ] 新增策略注册入口
- [ ] 新增策略启停入口
- [ ] 新增手动 trigger
- [ ] `strategyId / strategyRunId` 完成代码收口
- [ ] 建立运行基础状态与结果汇总

---

## 3. GateE-2 调度编排

- [ ] 新增调度作业模型
- [ ] 新增 scheduled trigger
- [ ] 新增窗口控制
- [ ] 新增 dedupKey 去重
- [ ] 新增同策略串行化保护
- [ ] 新增运行结果查询与反查执行结果

---

## 4. 当前已确认的兼容债务

 - [~] `PlaceOrderCommand.strategyId` 语义与运行血缘不一致，本批已写死迁移方向，代码收口顺延 GateE-1
- [x] `strategy_definitions` / `strategy_schedules` 已落表
- [~] 当前不存在正式策略读写 API

---

## 5. 门禁

- [x] current 入口与 GateE 卷宗口径一致
- [x] GateD / GateE / GateF 边界已写死
- [x] `strategyId` 与 `strategyRunId` 已分义
- [ ] GateE 相关实现完成后复跑 `mvn -q -f backend/pom.xml test`
- [ ] GateE 相关实现完成后复跑 `mvn -q -f backend/pom.xml verify`
- [ ] 所有实现 PR 均回填 `WORK.md` 与 `DECISIONS.md`
