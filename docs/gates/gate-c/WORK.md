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
PR-C6（可选）：OKX 私有 WS + reconcile 兜底（orders/account/positions 或 balance_and_position）
PR-C7~：Binance（GateC-2）按同样路径复用接入

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
      `FailurePath`：`gtcb002` 在旧 fee 占位逻辑下真实触发 `LEDGER_NOT_BALANCED`，已记录 `LedgerPostFailed + risk.event.v1 + audit`
      `Recovery`：`gtcr002` 作为非终态 LIMIT 订单在重启前后保持 `ACCEPTED`，且 `trades/ledger` 计数保持 0；手动
      `recovery/runOnce` 后仍无重复副作用，随后显式撤单清理为 `CANCELLED`
- 今日收尾：已完成 GateC-0 ~ GateC-1（含 Demo 验收入口与 `dome` 真实验收）的代码与文档沉淀，当前工作区可提交。
- 明日续做：整理 PR 拆分说明，补充“重启窗口内推进到终态”的自动化复现实验，并评估是否进入 GateC-1.1（WS）前置准备。

---

## 5. 坑与修复（追加）

- `nq-adapter-okx` 在 `-pl nq-adapter-okx test` 下会因为未联动构建 `nq-adapter-api` 的新 DTO 而出现编译噪声；当前以全量
  reactor 命令 `mvn -q -f backend/pom.xml test` 作为统一验收命令。
- GateC-1 的“真实成交/账本计数”验收仍依赖有效 OKX 凭证与可访问的真实/模拟 API；本次先完成代码链路与单测，不伪造运行态结果。
- 由于仓库原先缺少合规入口，无法在不旁路 `OrderCommandService/reconcile/recovery` 的前提下做 Demo 验收；PR-C4 新增 local-only
  触发器后，后续可按文档直接执行用例 A/B/恢复。
- 真实 OKX 联调发现 `clOrdId` 规则比本地幂等键更严格：过长或过于花哨的值会触发 `51000 Parameter clOrdId error`，因此验收时
  统一使用更短的字母数字 ID。
- 真实 OKX 请求对 `OK-ACCESS-TIMESTAMP` 的毫秒精度更稳定；纳秒精度会放大鉴权不确定性。
- 真实 OKX fills 的 `fee` 为负值语义（表示扣减）；GateC 验收已统一转为非负费用额进入事件与账本，避免同步链路因参数校验中断。
