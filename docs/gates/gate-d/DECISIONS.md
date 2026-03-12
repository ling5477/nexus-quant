# GateD DECISIONS

> 记录 GateD 实施过程中的中粒度工程决策。  
> ADR 记录高层结构性决策；本文件记录落地过程中的阶段性收敛结果。

---

## D-001：GateD 主验收通道固定为 OKX + PAPER

- 日期：2026-03-12
- 状态：已决定
- 背景：当前仓库已具备 OKX、Binance、Paper 三条执行通道，但 GateD 的核心目标是“执行闭环收敛”，不是“多交易所齐平扩边”。
- 决策：
  - GateD 的主验收通道固定为 **OKX + PAPER**
  - Binance 在 GateD 仅保持契约兼容与最小验证，不作为主扩边对象
- 影响模块：
  - `nq-core`
  - `nq-adapter-api`
  - `nq-adapter-okx`
  - `nq-adapter-binance`
  - `nq-app`
- 不选方案：
  - 不以 OKX / Binance 双主通道并行推进，因为会显著扩大 GateD 的测试矩阵与实现面
- 后续影响：
  - GateE 以后再考虑 Binance 深度齐平

---

## D-002：GateD 阶段定义修正为“统一执行闭环与执行域硬化”

- 日期：2026-03-12
- 状态：已决定
- 背景：旧版 roadmap 曾将 GateD 写成研究 / 回测阶段，这与当前代码基线和实施目标冲突。
- 决策：
  - GateD 统一定义为 **统一执行闭环与执行域硬化**
  - 研究 / 回测顺延到 GateF
- 影响文档：
  - `README.md`
  - `AGENTS.md`
  - `docs/current/*`
  - `docs/ROADMAP.md`
  - `docs/gates/gate-b/ROADMAP.md`
  - `docs/gates/gate-c/ROADMAP.md`
- 后续影响：
  - 任何新增文档、PR、模块说明不得再把 GateD 写成研究 / 回测阶段

---

## D-003：scheduler 只保留调度与协调职责，不再扩张为领域中心

- 日期：2026-03-12
- 状态：已决定
- 背景：当前 `nq-scheduler` 已包含 reconcile、recovery、WS 协调、paper 相关逻辑，存在继续膨胀为业务中心的风险。
- 决策：
  - `nq-scheduler` 只承担 job 触发、窗口扫描、恢复编排、受限兜底协调
  - 状态推进、执行编排、账本与投影更新统一回到 `nq-core` / `nq-ledger`
- 影响模块：
  - `nq-scheduler`
  - `nq-core`
  - `nq-ledger`
- 不选方案：
  - 不把 scheduler 继续做成“能调度也能写业务”的多功能模块
- 后续影响：
  - 任何新的恢复 / 补偿逻辑都必须先判断是否应归入 core 或 ledger

---

## D-004：current checklist 与 gate checklist 双轨保留

- 日期：2026-03-12
- 状态：已决定
- 背景：只保留 `docs/current/GATE_CHECKLIST.md` 会在阶段切换后丢失 GateD 原始冻结标准；只保留 gate 内 checklist 又不利于 Codex 快速定位当前入口。
- 决策：
  - 保留 `docs/current/GATE_CHECKLIST.md` 作为当前唯一工作入口
  - 保留 `docs/gates/gate-d/GATE_D_CHECKLIST.md` 作为 GateD 正式冻结卷宗
- 影响文档：
  - `docs/current/GATE_CHECKLIST.md`
  - `docs/gates/gate-d/GATE_D_CHECKLIST.md`
- 后续影响：
  - GateE 起始时沿用同样模式

---

## D-005：数值规范单独成文，不散落在代码注释与 schema 中

- 日期：2026-03-12
- 状态：已决定
- 背景：交易系统中价格、数量、金额、手续费、最小名义金额等规则极易因四舍五入、scale 不一致而产生隐性错误。
- 决策：
  - GateD 新增 `NUMERIC_POLICY.md`
  - 数值计算、比较、舍入、持久化精度统一以该文档为准
- 影响模块：
  - `nq-core`
  - `nq-risk`
  - `nq-ledger`
  - `nq-adapter-*`
  - `nq-infra`
- 后续影响：
  - 后续 Gate 若复用该规则，可提升为 `docs/NUMERIC_POLICY.md`

---

## D-006：恢复与补偿必须有操作手册，不接受“只在代码里懂”

- 日期：2026-03-12
- 状态：已决定
- 背景：GateD 强依赖 reconcile / recovery / query-confirm / degrade 收敛，若没有 runbook，排障时会高度依赖个人记忆。
- 决策：
  - GateD 新增 `RECOVERY_RUNBOOK.md`
  - 所有恢复、排障、人工核查动作必须可按 runbook 执行
- 影响模块：
  - `nq-scheduler`
  - `nq-core`
  - `nq-ledger`
  - `nq-observability`
  - `nq-app`
- 后续影响：
  - 每次新增恢复路径时同步更新 runbook

---

## D-007：PR 必须按能力边界拆分，不接受 GateD 巨型杂糅 PR

- 日期：2026-03-12
- 状态：已决定
- 背景：GateD 涉及文档、执行入口、风控、恢复、投影、迁移与验收，一次性堆到单个 PR 会显著提高 review 成本与返工成本。
- 决策：
  - GateD 新增 `PR_SPLIT_PLAN.md`
  - 后续提交按文档、contracts/core、risk、scheduler/recovery、ledger/db、app/api、tests/freeze 分拆
- 影响范围：整个 GateD
- 后续影响：
  - PR 描述必须标明对应的 split 阶段与 checklist 条目
