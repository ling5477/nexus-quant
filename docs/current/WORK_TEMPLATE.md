# docs/current/WORK_TEMPLATE.md
# Current Gate Work Template（工作记录模板）

> 用途：在当前 Gate 推进过程中记录“做了什么/为什么/怎么验收/遇到什么坑”，以便复盘与交接。
> 当前阶段：**Gate C（CEX 接入：OKX -> Binance）**
>
> 规则：
> - 每个 PR 一条记录（PR 标题 + 范围 + 验收点）。
> - 任何契约/表结构/状态机变化必须同步记录在本文件与 Gate 文档（DECISIONS/CONTRACTS/DB_SCHEMA）。
> - 任何线上级风险（重复下单、重复记账、数据不一致）必须记录 Failure Mode 与防护。

---

## 1. 今日目标
- 目标：
- 非目标（不做的事）：

---

## 2. 当前主线切片（建议按 PR）
- PR-C0（GateC-0）：adapter-api 三分法 + AdapterRouter + orders.external_order_id + 回执事件化
- PR-C1：OKX signer/http client + instruments 缓存 + 下单前 trim
- PR-C2：OKX REST place/cancel/get/orders-pending/fills + reconcile 同步器
- PR-C3：重启恢复流程（REST-only）+ 对账差异审计（必要时）
- PR-C4（可选）：OKX 私有 WS + REST reconcile 兜底
- PR-C5：Binance 复用接入（GateC-2）

---

## 3. 变更记录（按 PR 追加）

### PR-编号：
- 目的：
- 改动范围（模块/类/表）：
- 关键决策（为什么这么做）：
- 验收（对应 docs/current/GATE_CHECKLIST.md 的条目）：
- 风险与回滚：
- 备注：

---

## 4. 验收命令与观测
- `mvn -q -f backend/pom.xml test`
- `docker compose up -d postgres`
- 启动 `nq-app`（profile=local）
- 表计数核验：strategy_runs/orders/trades/ledger_entries/ledger_events/positions/audit_logs/risk_events/event_store
- 关键日志/trace_id 检查点：

---

## 5. 坑与修复（持续追加）
- 现象：
- 根因：
- 修复：
- 防回归（测试/门禁/告警）：

---

## 6. 待办
- TODO：