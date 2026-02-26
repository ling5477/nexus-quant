# 当前 Gate Checklist

> 说明：此文件用于“当前阶段”的验收清单入口。切换 Gate 时只需要更新本文件内容。
> 本文件是当前 Gate 的唯一验收入口（Source of Truth）。
> 关联工作记录：完成后将结果写入 `docs/gates/gate-b/WORK.md`。

- 当前阶段：Gate B（模拟盘最小交易闭环）

## Gate B 目标（必须达成）

在不接真实交易所网络的前提下，跑通一次端到端闭环，并具备：
- 严格状态机：订单状态只能通过显式迁移驱动
- 幂等：`client_order_id` 全链路贯穿，重复请求不产生重复副作用
- 可审计：关键动作写入 `audit_logs`，并记录 `trace_id` 与原因
- 可恢复：重启后不重复下单/不重复成交/不重复记账
- 可回放：关键命令/事件写入 `event_store`
- 可观测：traceId 串起 orders/trades/ledger/risk/audit/event_store

## 非目标（Gate B 不做）

- 不接真实 OKX/Binance 网络（不得发真实 HTTP/WebSocket）
- 不实现复杂策略（允许最小示例/定时触发用于闭环）
- 不实现复杂撮合/盘口/滑点建模（允许极简撮合）

---

## 验收清单（必须全部勾选）

### A. 构建与测试

- [x] 通过：`mvn -q -f backend/pom.xml test`
- [x] 单测覆盖（至少具备以下用例）：
    - [x] 订单状态机：≥5 个非法迁移用例（应失败）
    - [x] 幂等：重复 PlaceOrder（同 `account_id + client_order_id`）不新增 orders
    - [x] 撮合幂等：撮合 tick 重复执行不产生重复 trades
    - [x] 记账幂等：同一 trade 重复记账不新增 ledger_entries（依赖 `idempotency_key`）
    - [x] 记账平衡校验：PASS 与 FAIL 两类用例（FAIL 必须记录 risk/audit）

### B. 本地环境与启动

- [x] 启动数据库：`docker compose up -d postgres`
- [x] 数据库健康：`docker inspect -f "{{.State.Health.Status}}" nexusquant-postgres` => `healthy`
- [x] 启动应用：`mvn -q -f backend/pom.xml -pl nq-app -am spring-boot:run`
- [x] 探活成功：`GET /actuator/health` => `UP`

### C. 闭环跑通（最小业务闭环）

> 触发方式任选其一：HTTP 触发或 scheduler 定时触发。
> 触发后必须满足以下“落库证据”。

- [x] 已触发一次下单闭环（记录 trace_id）

#### C1. 数据库落库证据（必须出现）

- [x] `strategy_runs`：新增 1 条（`trace_id` 非空）
- [x] `orders`：新增 1 条，且：
    - [x] `client_order_id` 非空
    - [x] `trace_id` 非空
    - [x] `status` 进入终态（至少 FILLED 或 RISK_REJECTED/CANCELED）
- [x] `risk_events`：至少 1 条（包含 `trace_id`，scope/scope_id 指向订单或成交）
- [x] `trades`：>= 1 条（包含 `trace_id` 与 `ts`，且关联 `order_id`）
- [x] `ledger_entries`：>= 2 条（包含 `trace_id`、`ref_type/ref_id` 指向 TRADE、`idempotency_key` 非空）
- [x] `ledger_events`：数量 >= ledger_entries（每个 entry 至少一条投影事件，含 `trace_id`）
- [x] `positions`：发生更新（至少对应 `account_id + symbol` 一条记录更新，含 `trace_id`）
- [x] `audit_logs`：>= 3 条（覆盖下单/风控/成交或记账，含 `trace_id`）
- [x] `event_store`：至少包含以下 Topic 的记录（topic/type/version/key_value/trace_id 正确）：
    - [x] `TopicNames.ORDER_COMMAND_V1`（下单命令）
    - [x] `TopicNames.ORDER_EVENT_V1`（订单事件：created/risk/submitted/filled）
    - [x] `TopicNames.TRADE_EVENT_V1`（成交事件）
    - [x] `TopicNames.LEDGER_EVENT_V1`（记账事件：posted 或 failed）
    - [x] `TopicNames.RISK_EVENT_V1`（风控/异常事件）
    - [x] `TopicNames.AUDIT_EVENT_V1`（本轮未启用该 topic，按“若选择也写”记为 N/A）

### D. 重启恢复（必须通过）

- [x] 在存在“非终态订单”或“待处理任务”的情况下重启应用
- [x] 重启后满足：
    - [x] 不重复创建订单（幂等）
    - [x] 不重复生成 trades（撮合幂等）
    - [x] 不重复写 ledger_entries（记账幂等）
    - [x] trace_id 仍可串联本次恢复相关记录（audit/risk/event_store）

---

## 交付记录（Gate B 完成后必须做）

- [x] 将本次 Gate B 的验收结果、关键命令输出、以及遇到的问题与修复写入：
    - `docs/gates/gate-b/WORK.md`
- [x] 更新 `docs/current/` 指向下一 Gate 前，先冻结 `docs/gates/gate-b/` 内容（只读追加勘误）
