# GateE README
# GateE（v1.4：策略接入与调度编排）

当前状态：**待启动**。

GateE 不是 GateD 的返工阶段。GateD 已冻结，GateE 的主目标固定为：**策略接入与调度编排**。

---

## 1. GateE 与 GateD 的边界

### GateD 已完成
- 执行闭环与执行域硬化
- 最小验收闭环（Paper / OKX / Binance）
- 工程门禁、Flyway、freeze docs 收口

### GateE 负责
- 策略接入契约与注册
- 策略运行状态管理
- 调度编排主链
- 为上述主体开路的前置治理

### GateE 不负责
- 回写 GateD 新内容
- 把前置治理扩写成 GateE 主目标
- 提前展开强依赖实现细节的细分文档

---

## 2. 当前阶段结构

### GateE-0：前置治理批
只做：
- Binance background reconcile 噪音治理
- schema / metadata 收口
- 返回模型一致性收尾

### GateE-1：策略接入与注册
- 策略接入契约
- 策略注册
- 策略运行状态定义

### GateE-2：调度编排主链
- 调度编排主链
- 策略触发与运行窗口控制
- 策略运行状态与执行闭环衔接

说明：
- GateE-0 只是前置治理，不等于 GateE 主体。
- GateE 主定义始终是“策略接入与调度编排”。

---

## 3. 当前建议排序

- Top 1：Binance background reconcile 噪音治理
- Top 2：schema / metadata 收口
- Top 3：返回模型一致性收尾

排序原因：
- Top 1 最贴近当前唯一高频执行域噪音点，影响面最窄，验证最直接。
- Top 2 能为 GateE 后续策略接入与查询一致性打基础。
- Top 3 能减少后续契约与验收脚本分叉，但应排在 schema / metadata 之后。

---

## 4. 文档入口

- GateE checklist：`docs/gates/gate-e/GATE_E_CHECKLIST.md`
- GateE 拆批计划：`docs/gates/gate-e/PR_SPLIT_PLAN.md`
- GateE 工作记录：`docs/gates/gate-e/WORK.md`
- GateE 决策：`docs/gates/gate-e/DECISIONS.md`
- GateE 候选清单：`docs/gates/gate-e/GATE_E_CANDIDATES.md`
- GateE 架构摘要：`docs/gates/gate-e/ARCHITECTURE.md`
- GateE 模块摘要：`docs/gates/gate-e/MODULES.md`
- GateE ADR 说明：`docs/gates/gate-e/adr/README.md`
- GateD 冻结证据：`docs/gates/gate-d/FREEZE_SUMMARY.md`

---

## 5. 当前暂不建立的 GateE 文档

本批刻意暂不创建以下文档：
- `CONTRACTS.md`
- `DB_SCHEMA.md`
- `STATE_MACHINE.md`
- `TEST_CASES.md`
- `SOURCES.md`
- `RECOVERY_RUNBOOK.md`
- `COMPENSATION_SYNC.md`
- `RISK_RULES.md`
- `NUMERIC_POLICY.md`
- `EVOLUTION_RULES.md`
- `FREEZE_SUMMARY.md`

原因：
- 这些文档强依赖 GateE 第一批或后续实现
- 现在提前展开会造成文档漂移
- 等对应实现批次时再建
