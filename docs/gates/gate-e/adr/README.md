# GateE ADR README

## 1. 用途

GateE ADR 用于记录 GateE 实施过程中出现的高层结构性决策。

## 2. 何时新增 ADR

当出现以下情况时新增 ADR：
- 策略定义模型需要做不可逆边界选择
- `strategyId / strategyRunId` 的兼容收口涉及 breaking change
- 调度编排主链需要确定核心流程或持久化模型
- schema / metadata 收口需要引入结构性取舍
- 某项决策会显著影响后续多个 PR

## 3. 当前状态

当前尚无具体 ADR 文件，但已经在 `DECISIONS.md` 中冻结了 GateE 的若干中粒度决策。

后续一旦出现跨多个 PR 的高层取舍，就在本目录新增独立 ADR，而不是把所有东西都堆在一个大杂烩文档里。
