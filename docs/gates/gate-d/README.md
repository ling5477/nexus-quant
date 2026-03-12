# GateD README
# GateD（统一执行闭环与执行域硬化）

GateD 的目标不是再把系统“接得更多”，而是把 GateC 已经接上的能力收敛成**统一执行闭环**。

一句话定义：

> 给定一个标准化订单请求，系统能够在 Paper、OKX 或 Binance 通道中完成前置风控、下单、状态更新、成交回写、账本投影、持仓与账户同步、异常补偿和审计记录，形成可验证、可恢复、可冻结的执行闭环。

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
- `SOURCES.md`
- `WORK.md`
- `adr/*`

### 代码
- 执行域统一入口
- pre-trade 风控规则链
- 统一 adapter 契约冻结
- 补偿链路规则收敛
- ledger / projection 联动补齐
- 验收入口与验证脚本

### 数据库
- GateD 迁移脚本，例如 `V5__gate_d_execution_closure.sql`
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

