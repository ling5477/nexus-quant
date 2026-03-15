# GateD README
# GateD（统一执行闭环与执行域硬化）

GateD 的目标不是再把系统“接得更多”，而是把 GateC 已经接上的能力收敛成**统一执行闭环**。

一句话定义：

> 给定一个标准化订单请求，系统能够在 Paper、OKX 或 Binance 通道中完成前置风控、下单、状态更新、成交回写、账本投影、持仓与账户同步、异常补偿和审计记录，形成可验证、可恢复、可冻结的执行闭环。

---

## 0. 当前状态摘要（截至 2026-03-15）

> 本摘要用于 GateD 卷宗级概览，细项以 `GATE_D_CHECKLIST.md` 为准。
> 状态约定：`[x] 已完成`、`[~] 部分完成`、`[ ] 未完成`。

- [x] pre-trade 风控规则链已完成
- [~] lifecycle 主通道收口部分完成
- [x] adapter canonical 契约冻结已完成
- [x] `__gated` canonical 入口已完成
- [x] order / trade / position / account 本地最小闭环已完成
- [x] account snapshot 本地产出链已完成
- [x] 请求层 canonical `orderType / quantity` 已完成
- [x] 现行脚本与示例 canonical 化已完成
- [x] current / top-level / archive 文档边界已建立
- [x] 真实 OKX 主验收通道已收口，当前不再是主阻塞
- [x] 全仓 `mvn test / mvn verify`、Flyway init / upgrade 与 freeze docs 已完成，GateD 已冻结，GateE 待启动
- [~] 深层兼容债务仍部分完成

---

## 1. GateD 与 GateC 的区别

### GateC 解决的问题
- Adapter API 成型
- OKX / Binance 接入
- REST / WS 基座铺开
- reconcile / recovery / paper matching 能跑

### GateD 解决的问题
- 统一执行入口
- 统一状态推进
- pre-trade 风控硬规则
- trade / ledger / position / account 的闭环联动
- recovery / reconcile / degrade 的规则收敛
- 文档、测试、验收冻结

GateC 像是把各路管线接上；GateD 则是把整套管线拧紧，不再漏水。

---

## 2. 范围

### 2.1 包含
- 统一执行入口与执行域模型
- pre-trade 风控规则链
- 订单状态机硬化
- Paper / OKX / Binance 统一执行契约
- trade / ledger / position / account 投影联动
- reconcile / recovery / query-confirm / degrade
- trace / audit / event_store / metrics

### 2.2 不包含
- 研究平台
- 回测与因子系统
- 前端控制台
- 大规模生产基建
- 合约 / 杠杆 / 期货 / 期权

---

## 3. 涉及模块

- `nq-core`
- `nq-risk`
- `nq-adapter-api`
- `nq-adapter-okx`
- `nq-adapter-binance`
- `nq-scheduler`
- `nq-ledger`
- `nq-app`
- `nq-infra`
- `nq-observability`
- `nq-api`

主改优先级：`nq-core -> nq-risk -> nq-scheduler -> nq-adapter-api -> nq-adapter-okx -> nq-ledger -> nq-app`

---

## 4. GateD 交付物

### 文档
- `README.md`
- `ARCHITECTURE.md`
- `CONTRACTS.md`
- `MODULES.md`
- `DB_SCHEMA.md`
- `STATE_MACHINE.md`
- `RISK_RULES.md`
- `COMPENSATION_SYNC.md`
- `TEST_CASES.md`
- `DECISIONS.md`
- `EVOLUTION_RULES.md`
- `NUMERIC_POLICY.md`
- `PR_SPLIT_PLAN.md`
- `RECOVERY_RUNBOOK.md`
- `SOURCES.md`
- `WORK.md`
- `FREEZE_SUMMARY.md`
- `adr/*`

### 代码
- 执行域统一入口
- pre-trade 风控规则链
- 统一 adapter 契约冻结
- 补偿链路规则收敛
- ledger / projection 联动补齐
- 验收入口与验证脚本

### 数据库
- 当前数据库冻结基线为 `V1 -> V4`；本批已验证新库 init 与 `V3 -> V4` upgrade，无额外 GateD migration 必要
- 关键唯一键、索引、版本字段、审计字段补齐

---

## 5. 冻结标准

GateD 满足以下条件才允许冻结：

1. 文档齐全并与代码对齐
2. 统一执行入口已落地
3. pre-trade 风控硬规则已生效
4. 订单状态机收敛且无非法回退
5. reconcile / recovery / degrade 可收敛
6. trade / ledger / position / account 联动可验证
7. Paper / OKX / Binance 统一执行契约成立
8. GateD 验收用例全部通过

---

## 6. Codex 工作顺序

1. 读 `AGENTS.md`
2. 读 `docs/current/README.md`
3. 读 `docs/current/GATE_CHECKLIST.md`
4. 读本目录目标文档
5. 再读代码
6. 先改文档，再改代码，再补 `WORK.md`



## 7. GateD 文档阅读顺序

建议按以下顺序阅读：

1. `README.md`
2. `GATE_D_CHECKLIST.md`
3. `MODULES.md`
4. `CONTRACTS.md`
5. `STATE_MACHINE.md`
6. `RISK_RULES.md`
7. `COMPENSATION_SYNC.md`
8. `DECISIONS.md`
9. `EVOLUTION_RULES.md`
10. `NUMERIC_POLICY.md`
11. `PR_SPLIT_PLAN.md`
12. `RECOVERY_RUNBOOK.md`
13. `WORK.md`

其中：
- `DECISIONS.md` 负责记录阶段性工程收口
- `EVOLUTION_RULES.md` 负责约束演化边界
- `NUMERIC_POLICY.md` 负责数值与精度正确性
- `PR_SPLIT_PLAN.md` 负责提交拆分
- `RECOVERY_RUNBOOK.md` 负责恢复与排障操作


