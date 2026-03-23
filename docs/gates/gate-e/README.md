# GateE README
# GateE（v1.4：策略接入与调度编排）

当前状态：**GateE-0.1 与 GateE-0.2 已完成，下一步进入 GateE-0.3**。

GateE 不是 GateD 的返工阶段，也不是 GateF 的预演阶段。GateD 已冻结，GateE 的正式定义固定为：**策略接入与调度编排**。

---

## 1. GateE 正式定义

GateE 负责把“谁来发出策略动作、何时触发、如何去重、如何串行化、如何把结果回传给策略运行”这条主链从文档层写实到工程可实施。

本阶段的正式主链为：

`StrategySignal -> ExecutionRequest -> Order / Trade -> Ledger / Position / Account -> StrategyRunResult`

其中：

- `StrategySignal` 是 GateE 新增的策略层输入语义，表示某次策略运行产出的可执行动作。
- `ExecutionRequest` 在当前仓库中不另起炉灶，直接映射到现有 `PlaceOrderRequest -> PlaceOrderCommand -> AdapterOrderRequest` 链路。
- `Order / Trade / Ledger / Position / Account` 继续复用 GateD 已冻结执行闭环。
- `StrategyRunResult` 是 GateE 对一次策略运行的结果归档与读侧输出，不改写 GateD 订单状态机。

---

## 2. GateD / GateE / GateF 边界

### 2.1 GateD 已冻结内容

- 统一执行入口与订单状态推进
- pre-trade 风控规则链
- Paper / OKX / Binance 最小执行闭环
- trade / ledger / position / account 投影联动
- reconcile / recovery / query-confirm / degrade

### 2.2 GateE 当前负责

- 策略定义、注册、启停、配置快照边界
- 调度触发模型与运行窗口
- 去重、串行化、并发保护
- `strategyRunId` 贯穿执行域
- 执行结果回传到策略运行层
- 为主体实现开路的 GateE-0 前置治理

### 2.3 GateE 不负责

- 回写 GateD 新能力
- 把前置治理写成 GateE 主目标
- 研究、回测、因子、评估平台
- 新一轮交易所大扩张
- 分布式调度平台、DSL、插件生态

### 2.4 GateF 不提前进入

GateF 才负责研究 / 回测 / 评估能力。GateE 允许定义“运行窗口”和“受控 replay / retry”，但不做历史回放引擎，不做研究数据面。

---

## 3. GateE 分段推进

### 3.1 GateE-0：前置治理收口

GateE-0 只处理当前仓库里已经妨碍 GateE 主链开工的遗留项：

- 文档口径统一
- schema / metadata / contract 收口
- 命名统一
- 返回模型一致性
- 调度与执行链边界澄清
- Binance background reconcile 噪音清理

GateE-0 的性质是“清场”，不是主体实现。

### 3.2 GateE 主链

GateE 主链固定为两段：

- GateE-1：策略接入与注册
- GateE-2：调度编排主链

这两段至少要覆盖：

- 策略定义
- 策略启停
- 配置快照
- 手动触发与调度触发
- `strategyRunId` 运行跟踪
- 去重与串行化
- 运行结果回传
- 最小审计与可观测闭环

---

## 4. 基于仓库现状的真实起点

### 4.1 已存在事实

- `backend/nq-infra/src/main/resources/db/migration/V1__init.sql` 已创建 `strategy_runs`
- `backend/nq-infra/src/main/resources/db/migration/V5__gate_e_schema_contract_alignment.sql` 已创建 `strategy_definitions`
- `backend/nq-infra/src/main/resources/db/migration/V5__gate_e_schema_contract_alignment.sql` 已创建 `strategy_schedules`
- `orders.strategy_run_id` 与 `idx_orders_strategy_run_id` 已存在
- `PlaceOrderRequest` 已使用 `strategyRunId`
- `AdapterOrderRequest` 已使用 `strategyRunId`
- `PlaceOrderCommand` 仍保留 `strategyId` 字段，但当前语义与运行血缘存在兼容期混用
- `StrategyScheduler` / `NoopStrategyScheduler` 已存在，但只提供 `start / stop / restart` 占位接口
- `GateBDemoStrategyRunner` 已证明“定时触发 -> 下单 -> 订单血缘”最小演示链路

### 4.2 当前缺口

- 已有正式策略定义表，但还没有策略注册 / 启停 / 查询入口
- 已有正式调度配置表，但还没有 schedule job 运行逻辑
- 没有明确的触发去重与串行化规则
- 没有把策略运行结果沉淀成读侧对象
- 没有把 `strategyId / strategyRunId / requestId / dedupKey` 统一冻结到一套契约里

### 4.3 当前结论

当前仓库不是从零开始，但也绝对不能直接开写 GateE 主链代码。必须先把对象语义、主链边界、状态机、迁移触发条件和 PR 顺序写死。

---

## 5. GateE 核心对象

详见 [CONTRACTS.md](./CONTRACTS.md)，本 README 只保留阶段级结论：

- `strategyId`：策略定义标识。当前阶段不再额外引入独立 `strategyInstanceId`，避免在仓库尚无实例模型时制造空抽象。
- `strategyRunId`：某次运行标识，必须贯穿订单、事件、审计与结果汇总。
- `requestId`：某次触发请求标识。当前阶段用它统一 manual trigger、scheduled trigger、recovery retry 的入口身份。
- `dedupKey`：编排去重键，按 `strategyId + triggerSource + window + signalId/requestId` 归一，不等同于订单幂等键。
- `scheduleJobId`：调度配置身份，仅在引入正式调度表后持久化；当前仓库尚无对应表或类。
- `triggerId`：本阶段不单独持久化；触发请求先以 `requestId` 统一，接受后映射为 `strategyRunId`。
- `signalId`：仅在策略输出存在稳定信号身份时使用；当前仓库尚无对应模型，因此不是 GateE-1 的硬依赖字段。

---

## 6. 完成标准

GateE 文档基线完成，不代表 GateE 已完成。真正的阶段完成至少要求：

1. GateE-0 前置治理收口，不再阻塞主体编码
2. GateE-1 完成策略注册、启停、手动触发、运行基础状态
3. GateE-2 完成调度主链、窗口、去重、串行化、结果回传
4. `strategyRunId` 能从策略运行反查到订单、成交、账本结果
5. current 入口、GateE 卷宗、测试清单、PR 计划保持一致

---

## 7. 当前实施顺序

1. GateE-DOC-2：开工基线收口
2. GateE-0.1：Binance background reconcile 噪音治理
3. GateE-0.2：schema / metadata / contract 收口
4. GateE-0.3：返回模型一致性收尾
5. GateE-1.1：策略定义与注册模型
6. GateE-1.2：运行主链与手动触发
7. GateE-2.1：调度任务与触发编排
8. GateE-2.2：窗口、去重、串行化、retry / recovery 收口

---

## 8. 入口索引

- GateE checklist：`docs/gates/gate-e/GATE_E_CHECKLIST.md`
- GateE 架构：`docs/gates/gate-e/ARCHITECTURE.md`
- GateE 模块边界：`docs/gates/gate-e/MODULES.md`
- GateE 契约：`docs/gates/gate-e/CONTRACTS.md`
- GateE 数据模型：`docs/gates/gate-e/DB_SCHEMA.md`
- GateE 状态机：`docs/gates/gate-e/STATE_MACHINE.md`
- GateE 验收清单：`docs/gates/gate-e/TEST_CASES.md`
- GateE PR 拆分：`docs/gates/gate-e/PR_SPLIT_PLAN.md`
- GateE 决策：`docs/gates/gate-e/DECISIONS.md`
- GateE 工作台账：`docs/gates/gate-e/WORK.md`
