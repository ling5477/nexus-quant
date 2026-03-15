# GateE Candidates

> 来源：GateD 冻结后剩余的非阻塞治理项与 GateE 主体候选。  
> 原则：不把这些项重新定义成 GateD 主阻塞。

---

## 1. GateE-0 前置治理

### 1.1 Binance background reconcile 噪音治理
- 目标：统一 scheduler / reconcile 的 credential 与 timestamp 口径，清理高频伪噪音。

### 1.2 schema / metadata 收口
- 目标：收敛当前 schema / metadata 命名、文档与查询面口径。

### 1.3 返回模型一致性收尾
- 目标：继续收紧 `Paper / OKX / Binance` 在未成交、成交、恢复、对账场景下的响应口径。

说明：
- 以上三项都只是 GateE-0 前置治理，不是 GateE 主体。
- 它们的作用是为策略接入与调度编排清场，而不是替代 GateE 主定义。

---

## 2. GateE 主体候选

### 2.1 策略接入契约与注册
- 建立最小策略接入契约
- 建立策略注册机制

### 2.2 调度编排主链
- 建立调度编排主链
- 打通策略触发、运行窗口控制与状态衔接

### 2.3 策略运行状态与执行闭环
- 定义策略运行状态
- 连接策略运行与执行闭环

---

## 3. 当前建议排序

- Top 1：Binance background reconcile 噪音治理
- Top 2：schema / metadata 收口
- Top 3：返回模型一致性收尾

排序理由：
- Top 1 最贴近当前唯一高频执行域噪音点，影响面最窄，验证最直接。
- Top 2 能为后续策略接入与调度编排减少 schema / metadata 噪音。
- Top 3 能减少后续契约、查询和验收脚本分叉，但应放在 Top 1 / Top 2 之后。
