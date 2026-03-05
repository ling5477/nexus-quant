# docs/current/README.md
# Current Gate（当前阶段入口）

当前阶段：**Gate C（CEX 接入：OKX -> Binance）**

本目录是“当前 Gate 的唯一入口”。切换 Gate 时，只需要更新本目录内文件内容即可；历史 Gate 文档固定在 `docs/gates/` 下。

---

## 1. 当前 Gate 的目标

GateC 目标：在 GateB 的“幂等/状态机/事实链(event_store)/账本(ledger)/审计(audit)/风控(risk)/可恢复”底座上，接入真实 CEX（先 OKX 现货，后 Binance 现货），实现真实下单/撤单/成交同步/记账/持仓投影，并可重启恢复与对账。

关键原则：
- **REST-first**：先用 REST 跑通闭环（GateC-1），WS 仅作为后置加速（GateC-1.1），且必须保留 REST reconcile 兜底。
- **adapter 为中心**：PAPER/OKX/BINANCE 都是 adapter 实现，core/ledger/risk 不出现 venue 分支。
- **超时禁止盲重试**：必须 query-confirm（查单/挂单/成交）后再补偿动作。
- **幂等/去重/审计不可破坏**：orders 幂等、trades 去重、ledger 幂等、event_store 事实链全量留痕。

---

## 2. GateC 文档入口（冻结版）

- 总览架构：`docs/gates/gate-c/ARCHITECTURE.md`
- 契约：`docs/gates/gate-c/CONTRACTS.md`
- DB 增量：`docs/gates/gate-c/DB_SCHEMA.md`
- 决策记录：`docs/gates/gate-c/DECISIONS.md`
- 演进规则：`docs/gates/gate-c/EVOLUTION_RULES.md`
- 模块职责：`docs/gates/gate-c/MODULES.md`
- 数值精度：`docs/gates/gate-c/NUMERIC_POLICY.md`
- 恢复与对账：`docs/gates/gate-c/RECOVERY_RUNBOOK.md`
- 路线图：`docs/gates/gate-c/ROADMAP.md`
- 工作记录：`docs/gates/gate-c/WORK.md`
- 权威依据：`docs/gates/gate-c/SOURCES.md`
- 验收清单：`docs/gates/gate-c/GATE_C_CHECKLIST.md`
- PR 拆分说明：`docs/gates/gate-c/PR_SPLIT_PLAN.md`

---

## 3. 当前 Gate 唯一验收入口

- 统一验收清单：`docs/current/GATE_CHECKLIST.md`
  - 该文件是 GateC 的验收门禁（Source of Truth）。
- GateC 验收入口 `POST /__gatec/*` 仅在 `local + nq.gatec.verify.enabled=true` 时启用，生产环境不暴露；
  可重复验收脚本见 `scripts/gatec_okx_dome_verify.ps1`。

---

## 4. 当前执行顺序（只做 GateC 主线）

1) GateC-0（必须）：adapter-api 三分法 + AdapterRouter + orders.external_order_id + 回执事件化
2) GateC-1（必须）：OKX Spot REST-only 闭环（place/cancel/query/orders-pending/fills + reconcile + ledger + positions）
3) GateC-1.1（可选后置）：OKX 私有 WS（orders/account/positions 或 balance_and_position）+ REST reconcile 兜底
4) GateC-2：Binance 复用接入

---

## 5. 文档依据说明（必须）

- GateC 的所有“交易所接口/WS 通道/关键约束”的权威依据统一收敛在：
  - `docs/gates/gate-c/SOURCES.md`
- 当实现与文档不一致时：以 `docs/current/*` 为准，并在对应 Gate 文档与 SOURCES 中补齐依据链接。
