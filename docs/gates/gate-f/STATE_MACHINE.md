# GateF STATE_MACHINE
# GateF 状态机与推进事件

GateF 当前只冻结研究 / 回测运行侧最小状态机。

---

## 1. BacktestRun 状态机

对象：`backtestRunId`

状态：

- `CREATED`
- `PREPARING_DATA`
- `RUNNING`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

推进：

- 创建运行：`null -> CREATED`
- 开始准备数据：`CREATED -> PREPARING_DATA`
- 开始执行回测：`PREPARING_DATA -> RUNNING`
- 正常完成：`RUNNING -> COMPLETED`
- 执行失败：`PREPARING_DATA|RUNNING -> FAILED`
- 人工取消：`CREATED|PREPARING_DATA -> CANCELLED`

---

## 2. Evaluation 状态

对象：`evaluationSummary`

最小状态：

- `PENDING`
- `GENERATED`
- `FAILED`

说明：

- GateF-DOC-1 只冻结评估生成的阶段语义
- 不展开具体指标实现
