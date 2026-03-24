# GateF README
# GateF（研究 / 回测 / 评估能力）

当前状态：**GateF-DOC-1 已完成；后续进入 GateF-1 文档后的首个实现批次。**

GateF 是 GateE 之后的独立阶段。GateF 不回头重写 GateE，也不继续承接 GateE 遗留执行债务。

---

## 1. GateF 正式定义

GateF 负责建立以下能力：

- 研究配置与回测配置
- 回测运行模型
- 市场数据输入边界
- 模拟订单 / 模拟成交 / 模拟持仓 / PnL
- 评估指标与结果摘要
- 研究产物与执行域的接口约定

---

## 2. GateF 明确不做

- 回头重写 GateE 执行主链
- 生产实盘调度编排
- 多交易所实时执行优化
- GateE 遗留执行债务清理
- 大而全数据中台

---

## 3. GateF 输入资产

当前仓库已经可复用的输入资产：

- `strategy_definitions`
- `strategy_schedules`
- `strategy_runs`
- `orders`
- `trades`
- canonical adapter 返回模型
- GateE run 查询面
- GateE 冻结文档卷宗

说明：

- 这些资产是 GateF 的输入起点
- 但不代表 GateF 可以直接复用 GateE 的实体模型

---

## 4. GateF 最小主链

GateF 当前冻结的最小主链为：

1. 创建研究 / 回测配置
2. 发起 `backtest run`
3. 加载市场数据
4. 模拟撮合 / 模拟成交 / 更新持仓与资金
5. 生成运行结果
6. 生成评估指标与摘要
7. 提供查询与结果输出入口

---

## 5. GateF 与 GateE 的关系

- GateE 解决：策略接入与调度编排
- GateF 解决：研究 / 回测 / 评估
- GateF 消费 GateE 已冻结产物与接口
- GateF 不回头重写 GateE

---

## 6. 当前完成标准

GateF-DOC-1 完成后至少要求：

- GateF 主卷宗建立
- 输入 / 输出边界明确
- 最小主链明确
- GateF 与 GateE 的接口边界明确
- 待决策问题集中列出
- PR 拆分可执行

---

## 7. 入口索引

- GateF checklist：`docs/gates/gate-f/GATE_F_CHECKLIST.md`
- GateF 架构：`docs/gates/gate-f/ARCHITECTURE.md`
- GateF 模块边界：`docs/gates/gate-f/MODULES.md`
- GateF 契约：`docs/gates/gate-f/CONTRACTS.md`
- GateF 数据模型：`docs/gates/gate-f/DB_SCHEMA.md`
- GateF 状态机：`docs/gates/gate-f/STATE_MACHINE.md`
- GateF 测试清单：`docs/gates/gate-f/TEST_CASES.md`
- GateF PR 拆分：`docs/gates/gate-f/PR_SPLIT_PLAN.md`
- GateF 决策：`docs/gates/gate-f/DECISIONS.md`
- GateF 工作台账：`docs/gates/gate-f/WORK.md`
- GateF 依据索引：`docs/gates/gate-f/SOURCES.md`
