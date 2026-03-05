# docs/gates/gate-c/WORK.md

# Gate C WORK 记录

> 最后更新：2026-03-04
> 范围：Gate C（CEX 接入：OKX -> Binance）

---

## 1. 今日目标与边界

- 目标：OKX Spot（REST-only）跑通真实闭环并保留可回放证据链。
- GateC-0 必须先做：adapter 三分法 + AdapterRouter + orders.external_order_id + 回执事件化。
- 不做：不做链上/OnchainOS；不做高频；不做复杂做市；不做 nq-engine 大重构。

---

## 2. 切片计划（建议 PR）

PR-C0（GateC-0）：adapter-api 三分法 + AdapterRouter + orders.external_order_id + place/cancel 回执事件化

- 修改点：
    - nq-adapter-api：新增 TradingAdapter/MarketDataAdapter/AccountAdapter
    - nq-core：OrderCommandService 改为调用 AdapterRouter；不再硬编码 PAPER
    - nq-infra：Flyway 增量 external_order_id + 索引
    - event_store：新增 OrderAck/Reject/CancelAck/CancelReject 的写入

PR-C1：OKX credentials + signer + http client + 单测
PR-C2：OKX instruments 元数据缓存 + 下单前 trim
PR-C3：OKX REST place/cancel + 状态机接入（Ack/Reject 事件）+ event_store
PR-C4：OKX 同步器（REST 轮询）：query order + pull fills -> trades 去重 -> 状态推进
PR-C5：复用 ledger/positions + 运行态验收与重启恢复脚本
PR-C6：GateC-1 收尾硬化（验收入口双门禁 + 验收脚本固化 + 重启窗口复现实验）
PR-C7（可选）：OKX 私有 WS + reconcile 兜底（orders/account/positions 或 balance_and_position）
PR-C8~：Binance（GateC-2）按同样路径复用接入

---

## 3. 验证命令

- `mvn -q -f backend/pom.xml test`
- `docker compose up -d postgres`
- 启动 nq-app（profile=local）
- 表计数核验：strategy_runs/orders/trades/ledger_entries/ledger_events/audit_logs/risk_events/positions/event_store
- event_store topic 统计（order/trade/ledger/risk/position）

---

## 4. 今日完成（追加）

- PR-C0（GateC-0）已完成：adapter-api 三分法、`AdapterRouter`、`orders.external_order_id` 与 `(venue, external_order_id)` 索引、
  `OrderAck/OrderReject/CancelAck/CancelReject` 事件化已落地。
    - 改动范围：`nq-adapter-api`、`nq-core`、`nq-scheduler`、`nq-infra`、`nq-contracts`
    - 验收证据：`mvn -q -f backend/pom.xml test` 通过；`docs/current/GATE_CHECKLIST.md` 的 GateC-0 条目已勾选；PAPER 已改造成
      `TradingAdapter` 实现
- PR-C1：已完成 OKX Signer / HTTP Client / instruments 缓存基础设施与单测。
    - 改动范围：`nq-adapter-okx`（`OkxRequestSigner`、`OkxHttpClient`、`OkxInstrumentsCache`、`OkxExchangeAdapter`）
    - 验收证据：新增 `OkxHttpClientTest`（覆盖 GET query / POST body 签名头）、`OkxInstrumentsCacheTest`（覆盖
      `tickSz/lotSz/minSz/state` 解析）；`mvn -q -f backend/pom.xml test` 通过
- PR-C2：已完成 OKX REST-only 交易闭环的最小代码链路。
    - 改动范围：`nq-adapter-okx`（真实 `place/cancel/get/listOpenOrders/fills` 映射 + trim + timeout query-confirm）、
      `nq-scheduler`（`OkxRestReconcileService`）、`nq-core`（`linkExternalOrderId`）
    - 验收证据：`mvn -q -f backend/pom.xml test` 通过；代码路径已覆盖 `OrderAck` 落 `external_order_id`、
      `fills -> TradeExecuted -> ledger posting`
- PR-C3：已完成 REST-only 恢复入口的最小实现。
    - 改动范围：`nq-scheduler`（`OkxRecoveryService`）、`nq-app`（`RecoveryService` 装配替换）
    - 验收证据：`mvn -q -f backend/pom.xml test` 通过；恢复流程代码已覆盖“扫描非终态订单 -> `orders-pending` 关联 ->
      reconcile 重放”
- PR-C4：已完成 GateC Demo 验收解阻入口（local only）。
    - 改动范围：`nq-app`（`GateCAcceptanceController` + 最小 HTTP DTO + `MockMvc` 测试）、`docs/current/GATE_CHECKLIST.md`
    - 验收证据：新增本地专用 endpoint `POST /__gatec/orders`、`POST /__gatec/orders/cancel`、
      `POST /__gatec/reconcile/runOnce`、`POST /__gatec/recovery/runOnce`；controller 仅做参数校验、trace 透传与服务触发，
      不承载业务逻辑；`MockMvc` 覆盖 local profile 可用与非 local 不暴露
- PR-C5：已完成 OKX `dome` 真实验收与运行态修复。
    - 改动范围：`nq-adapter-okx`（毫秒级 timestamp、OKX 细粒度错误透传）、`nq-scheduler`（OKX fee 规范化、paper matcher 跳过非
      PAPER）、`nq-ledger`（fee 成对分录 + base fee 扣减持仓）、`docs/current/GATE_CHECKLIST.md`
    - 验收证据：
      `UseCase-A`：`gtca003` LIMIT 远离盘口下单后得到 `OrderAck`，`orders.external_order_id=3359330641900167168`，随后撤单得到
      `CancelAck`，订单终态 `CANCELLED`，`trades=0`
      `UseCase-B`：`gtcb003` MARKET 成功成交，`trades=1`（`exchange_trade_id=1184203040`），`ledger_entries=4`，
      `positions(BTC-USDT)=0.00013958`，`event_store` 包含 `TradeExecuted + LedgerPosted + PositionUpdated`
      `FailurePath`：`gtcb002` 在旧 fee 占位逻辑下真实触发 `LEDGER_NOT_BALANCED`，已记录
      `LedgerPostFailed + risk.event.v1 + audit`
      `Recovery`：`gtcr002` 作为非终态 LIMIT 订单在重启前后保持 `ACCEPTED`，且 `trades/ledger` 计数保持 0；手动
      `recovery/runOnce` 后仍无重复副作用，随后显式撤单清理为 `CANCELLED`
- PR-C6：已完成 GateC-1 收尾硬化（不加新功能，只固化门禁与验收复现）。
    - 改动范围：`nq-app`（`GateCAcceptanceController` 双门禁、MockMvc 覆盖矩阵、`application*.yml` 默认关闭）、
      `scripts/gatec_okx_dome_verify.ps1`（A/B/C 一键验收）、`docs/current/*`、`.env.example`
    - 门禁策略：
      `local + nq.gatec.verify.enabled=true` 才暴露 `POST /__gatec/*`；
      `local + enabled=false` 或非 local（即便 enabled=true）都必须 404
    - 验收脚本：
      `pwsh -File scripts/gatec_okx_dome_verify.ps1`
      （从本地 `.env` 只读 `NQ_OKX_ENV/NQ_OKX_DOME_*`，不打印 secret/passphrase）
    - 重启窗口推进到终态复现实验（固化步骤）：
      1) 执行脚本 UseCase-C 下单得到非终态 LIMIT（记录 `trace_id/clientOrderId/orderId`）
      2) 按脚本提示重启 `nq-app`，继续执行 `recovery/runOnce` 与 `reconcile/runOnce`
      3) 观察点：
         - `orders`：状态从非终态推进到 `CANCELLED/FILLED/REJECTED` 之一
         - `trades/ledger_entries`：不出现重复增长（幂等去重有效）
         - `event_store`：追加恢复链路证据（recovery/reconcile 相关事件）
- PR-C7：已完成真实盘启动阻塞修复 + 复验通过（最小风险 UseCase-A）。
    - 改动范围：`nq-adapter-okx`（`OkxInstrumentsCache` 跳过 `preopen/缺精度` 条目，避免启动被单个脏 instrument 阻断）、
      `nq-adapter-okx` 单测（新增 preopen 缺字段回归用例）
    - 验收证据：`mvn -q -f backend/pom.xml test` 通过；真实盘 `BUY LIMIT`（`clientOrderId=rra131059b1`）下单 `ACCEPTED`，
      随后 `cancelOrder` 成功到 `CANCELLED`，`orders.external_order_id=3361652763553128448`，`trades=0`；
      `event_store` 含 `PlaceOrderCommand/OrderAck/CancelOrderCommand/CancelAck`，`audit_logs` 含完整状态迁移
- PR-C8：已完成 `dome` 自动复现实验收敛（UseCase-A/B/C with AutoRestart）并完成第 6 节文档对齐勾选。
    - 改动范围：`scripts/gatec_okx_dome_verify.ps1`（`-AutoRestart` 稳定化，UseCase-C 补 `cancel` 收敛终态）、
      `nq-scheduler`（`OkxRestReconcileService` 并发状态对齐容错）、`docs/current/GATE_CHECKLIST.md`
    - 验收证据：
      `pwsh -File scripts/gatec_okx_dome_verify.ps1 -AutoRestart -BaseUrl http://localhost:28081 -StartupTimeoutSec 300` 退出码 0；
      最新三单：`g6a0305054548 -> CANCELLED`、`g6b0305054550 -> FILLED`、`g6c0305054552 -> CANCELLED`；
      `trade/ledger` 计数：`A=0/0`、`B=1/4`、`C=0/0`；`docs/current/GATE_CHECKLIST.md` 第 6 节四项已全部勾选
- 今日收尾：已完成 GateC-0 ~ GateC-1（含 Demo 验收入口与 `dome` 真实验收）的代码与文档沉淀，当前工作区可提交。
- 明日续做：已完成 整理 PR 拆分说明，补充“重启窗口内推进到终态”的自动化复现实验，并评估是否进入 GateC-1.1（WS）前置准备。

---

## 5. 坑与修复（追加）

- `nq-adapter-okx` 在 `-pl nq-adapter-okx test` 下会因为未联动构建 `nq-adapter-api` 的新 DTO 而出现编译噪声；当前以全量
  reactor 命令 `mvn -q -f backend/pom.xml test` 作为统一验收命令。
- GateC-1 的“真实成交/账本计数”验收仍依赖有效 OKX 凭证与可访问的真实/模拟 API；本次先完成代码链路与单测，不伪造运行态结果。
- 由于仓库原先缺少合规入口，无法在不旁路 `OrderCommandService/reconcile/recovery` 的前提下做 Demo 验收；PR-C4 新增
  local-only
  触发器后，后续可按文档直接执行用例 A/B/恢复。
- 真实 OKX 联调发现 `clOrdId` 规则比本地幂等键更严格：过长或过于花哨的值会触发 `51000 Parameter clOrdId error`，因此验收时
  统一使用更短的字母数字 ID。
- 真实 OKX 请求对 `OK-ACCESS-TIMESTAMP` 的毫秒精度更稳定；纳秒精度会放大鉴权不确定性。
- 真实 OKX fills 的 `fee` 为负值语义（表示扣减）；GateC 验收已统一转为非负费用额进入事件与账本，避免同步链路因参数校验中断。
- 真实盘 `public/instruments` 会返回 `state=preopen` 且 `tickSz/lotSz/minSz` 为空的条目（如 `ROBO-USDT`）；旧逻辑会在启动预热时
  fail-fast。现已改为仅缓存 `state=live` 且精度字段完整的条目，缺失条目告警并跳过，避免阻断应用启动与恢复流程。

---

## 6. PR 拆分说明（收敛版）

- 见：`docs/gates/gate-c/PR_SPLIT_PLAN.md`
- 结论：已按 C0~C7 把核心风险拆到独立 PR，后续进入 WS 时建议继续按 `连接治理 -> 事件映射 -> 协同降级` 三段拆分，避免把
  实时链路、状态机、幂等问题混在一个提交中。

---

## 7. 重启窗口自动化复现实验（新增）

- 脚本：`scripts/gatec_okx_dome_verify.ps1`
- 自动模式命令（会自动 stop/start 本地 `28081` 服务并继续 recovery/reconcile）：
  `pwsh -File scripts/gatec_okx_dome_verify.ps1 -AutoRestart -BaseUrl http://localhost:28081`
- 前置条件：
  - `.env` 中 `NQ_OKX_ENV=dome`
  - `NQ_GATEC_VERIFY_ENABLED=true`
  - 本地验收入口可访问（`local + enabled=true`）
- 观察点：
  - UseCase-C 在重启后继续执行 `recovery/runOnce` 与 `reconcile/runOnce`
  - 订单状态可由非终态推进到终态（`CANCELLED/FILLED/REJECTED`）
  - `trades/ledger` 不出现重复副作用（依赖去重与幂等）

---

## 8. 是否进入 GateC-1.1（WS）前置准备评估

- 当前结论：`有条件进入，但建议先补齐文档对齐门禁后再开工`。
- 已满足：
  - GateC-1（REST-only）主链路已跑通，真实盘 UseCase-A 可复验
  - 重启恢复门禁已通过，`OKX 51603` 容错不阻断启动
  - 验收入口已做双门禁，生产零暴露
- 仍待补齐（来自 `docs/current/GATE_CHECKLIST.md` 第 6 节）：
  - `docs/gates/gate-c/ARCHITECTURE.md` 与实现一致性复核
  - `docs/gates/gate-c/CONTRACTS.md` 与 DTO 字段一致性复核
  - `docs/gates/gate-c/DB_SCHEMA.md` 与 Flyway 一致性复核
  - `docs/gates/gate-c/RECOVERY_RUNBOOK.md` 与当前恢复行为一致性复核
- 建议准入门槛（满足后进入 WS）：
  - 上述 4 项文档对齐全部勾选
  - 真实盘最小风险用例连续 2 次通过（`LIMIT -> Cancel`）
  - 自动化 UseCase-C（`-AutoRestart`）至少 1 次通过并留痕
