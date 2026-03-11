# docs/current/GATE_CHECKLIST.md

# Current Gate Checklist（Gate C：OKX -> Binance）

> 本文件是“当前 Gate 的唯一验收入口（Source of Truth）”。
> 切换 Gate 时只需要更新本文件内容；历史 Gate 文档固定在 `docs/gates/` 下。
>
> 当前阶段：**Gate C（CEX 接入：OKX -> Binance）**
> 验收优先级：GateC-0 -> GateC-1（OKX REST-only）-> GateC-1.1（OKX WS 可选）-> GateC-2（Binance）
>
> 2026-03-11 GateC Final Acceptance 状态：**通过，建议冻结 GateC**。
> - Phase A：`mvn -q -f backend/pom.xml test` 已通过。
> - Phase B：OKX Demo（REST-only + WS + Recovery）已通过。
> - Phase C：Binance Testnet（REST-only + WS + 协同降级）已通过。
> - Phase D：OKX Real / Binance Real 最小复验已通过。
> - 当前无阻断点；下一步应进入冻结提交整理，而不是进入 GateD。
>
> 2026-03-11 GateC 定向重验（环境修正后）状态：**仍未恢复 GateC Final Acceptance**。
> - Step 0 指纹确认通过：Binance Testnet 使用 `NQ_BINANCE_ENV=dome`、`NQ_BINANCE_KEY_TYPE=hmac`、
    `baseUrl=https://testnet.binance.vision`、`wsUrl=wss://ws-api.testnet.binance.vision/ws-api/v3`；
    `NQ_GATEC_VERIFY_ENABLED=true`、`NQ_BINANCE_WS_ENABLED=true`。
> - 本地库缺少 Binance / OKX Real 验收账户，重验前仅补齐本地
    `accounts(2002=OKX-REAL-2002, 3001=BINANCE-DOME-3001, 3002=BINANCE-REAL-3002)`，未改代码。
> - Binance Testnet REST 定向重验仍失败：`c1bn0311114801`（LIMIT）与 `c2bn0311114901`（MARKET）均在 `placeOrder` 收敛为
    `REJECTED/-2015`；`external_order_id=null`，`trades=0`，`ledger_entries=0`。
> - Binance Testnet WS 定向重验仍失败：当前重验窗口内持续 `status=401/-2015`，且没有新的
    `event_store(payload_json->>'source'='BINANCE_WS')` 或 `audit_logs(action like 'BINANCE_WS%')` 证据链。
> - 按规则，Phase C 未通过即停止；Phase D（OKX Real / Binance Real 最小复验）本轮未执行。
>
> 2026-03-11 Phase C 重跑（最新 Testnet HMAC 凭证）状态：**C0 即阻断，未进入 C1-C5**。
> - 当前 `.env` 指纹显示：`NQ_BINANCE_ENV=dome`、`NQ_BINANCE_KEY_TYPE=hmac`、
    `NQ_BINANCE_DOME_WS_URL=wss://ws-api.testnet.binance.vision/ws-api/v3`、`NQ_GATEC_VERIFY_ENABLED=true`、
    `NQ_BINANCE_WS_ENABLED=true`。
> - 真实阻塞点：`NQ_BINANCE_DOME_BASE_URL` 仍为 `https://testnet.binance.vision/api`，与 `docs/current/README.md
    ` 规定的 `https://testnet.binance.vision` 不一致。
> - 按本轮规则，C0 指纹不通过即停止，不继续执行 Binance Testnet 的 REST / WS / 协同 / 降级验收，也不进入 Phase D。
>
> 2026-03-11 Phase C 重跑（baseUrl 已修正为 `https://testnet.binance.vision`，继续使用最新 Testnet HMAC）状态：**仍未通过**。
> - C0 指纹已通过：`NQ_BINANCE_ENV=dome`、`NQ_BINANCE_KEY_TYPE=hmac`、
    `NQ_BINANCE_DOME_BASE_URL=https://testnet.binance.vision`、
    `NQ_BINANCE_DOME_WS_URL=wss://ws-api.testnet.binance.vision/ws-api/v3`。
> - 运行态启动：`nq-app` 在 `local + NQ_GATEC_VERIFY_ENABLED=true + NQ_BINANCE_WS_ENABLED=true` 下可启动，
    `/actuator/health` 返回 `UP`。
> - C1 / REST UseCase-A：`clientOrderId=pc10311144214`，`placeOrder` 收敛为 `REJECTED/-2015`，`external_order_id=null`，
    `trades=0`，`ledger_entries=0`；未拿到 `OrderAck/CancelAck`。
> - C2 / REST UseCase-B：`clientOrderId=pc20311144440`，`MARKET` 下单同样收敛为 `REJECTED/-2015`；两次 `reconcile/runOnce`
    均 `new_trades=0`，未进入 `trades/ledger/positions` 验收。
> - C3 / Binance 私有 WS：应用日志持续 `binance_ws_subscribe_failed status=401 error_code=-2015`，当前窗口
    `event_store(payload_json->>'source'='BINANCE_WS')=0`，未拿到 `status=200 / subscriptionId / session.subscriptions`。
> - C4 / WS + REST 协同：因 C1 未拿到 `OrderAck/CancelAck`、C3 未建立有效私有 WS，会话内不存在可验证的
    `BINANCE_WS_ORDER_ACK_ACCELERATE / BINANCE_WS_CANCEL_ACK_ACCELERATE` 证据链。
> - C5 / 强制断连 / 降级：`audit_logs` 可见
    `BINANCE_WS_DISCONNECTED / BINANCE_WS_RECONCILE_DEGRADE_COMPLETED / BINANCE_WS_RECONCILE_DEGRADE_SKIPPED_COOLDOWN`
    ，但因私有 WS 从未进入成功订阅态，本轮只能确认失败后降级动作存在，不能判定 Phase C 的 WS 闭环通过。
>
> 2026-03-11 Phase C 再重跑（更换最新 Testnet HMAC 凭证后）状态：**通过**。
> - C0 指纹通过：`NQ_BINANCE_ENV=dome`、`NQ_BINANCE_KEY_TYPE=hmac`、
    `NQ_BINANCE_DOME_BASE_URL=https://testnet.binance.vision`、
    `NQ_BINANCE_DOME_WS_URL=wss://ws-api.testnet.binance.vision/ws-api/v3`。
> - C1 / REST UseCase-A：`clientOrderId=pc1r0311153810`，`placeOrder=ACCEPTED`，`cancelOrder=CANCELLED`，
    `orders.external_order_id=14497482`，终态 `CANCELLED`，`trades=0`，`ledger_entries=0`。
> - C2 / REST UseCase-B：`clientOrderId=pc2r0311153942`，终态 `FILLED`，`orders.external_order_id=14498581`，`trades=1`，
    `distinct exchange_trade_id=1`，`ledger_entries=2`，`positions(account_id=3001,symbol=BTC-USDT)=0.00010000`。
> - C3 / Binance 私有 WS：应用日志出现 `binance_ws_connected subscription_id=0` 与
    `binance_ws_session_subscriptions_checked confirmed=true`；最近窗口
    `event_store(payload_json->>'source'='BINANCE_WS')=10`。
> - C4 / WS + REST 协同：`clientOrderId=pc4r0311154220` 完成 `ACCEPTED -> CANCELLED`，`external_order_id=14500750`，
    `trades=0`，`ledger_entries=0`；`event_store` 中存在同一 `external_order_id=14500750` 的
    `BINANCE_WS OrderAck + CancelAck`。
> - C5 / 强制断连 / 降级：`audit_logs` 出现
    `BINANCE_WS_DISCONNECTED / BINANCE_WS_RECONCILE_DEGRADE_COMPLETED / BINANCE_WS_RECONNECTED`；复核 UseCase-B 的
    `trades=1 / ledger_entries=2` 保持不变，未出现重复成交或重复记账。
>
> 2026-03-11 Phase D 重跑（OKX Real / Binance Real 最小复验）状态：**未通过**。
> - D0 指纹确认通过：`NQ_OKX_ENV=real`、`NQ_OKX_REAL_BASE_URL=https://www.okx.com`；
>   `NQ_BINANCE_ENV=real`、`NQ_BINANCE_KEY_TYPE=ed25519`、
>   `NQ_BINANCE_REAL_BASE_URL=https://api.binance.com`、
>   `NQ_BINANCE_REAL_WS_URL=wss://ws-api.binance.com:443/ws-api/v3`。
> - D1 / OKX Real 最小复验：按当前 `.env` 重启 `nq-app` 后，应用在启动恢复阶段再次被
>   `trace_id=trc-okx-recovery-startup` 的 `/api/v5/trade/orders-pending?instType=SPOT&instId=BTC-USDT`
>   `401/50110` 阻断；应用上下文回滚，未能进入 `LIMIT -> Cancel`，因此没有新的 `OrderAck / CancelAck /
>   orders.external_order_id / CANCELLED / trades=0 / ledger_entries=0` 证据。
> - D2 / Binance Real 最小复验：由于 D1 的 OKX Real 启动恢复阻断，`nq-app` 无法保持健康态，未能执行
>   Binance Real `LIMIT -> Cancel`；但同一轮启动日志已出现
>   `binance_ws_subscribe_failed status=401 error_code=-2015`，说明 Binance Real 私有 WS 路径仍未恢复。
> - 结论：Phase D 仍未通过，GateC Final Acceptance 不能恢复。
>
> 2026-03-11 Phase D 再重跑（权限问题修复后）状态：**部分通过，仍未恢复 GateC Final Acceptance**。
> - D0 指纹确认通过：`NQ_OKX_ENV=real`、`NQ_OKX_REAL_BASE_URL=https://www.okx.com`；
>   `NQ_BINANCE_ENV=real`、`NQ_BINANCE_KEY_TYPE=ed25519`、
>   `NQ_BINANCE_REAL_BASE_URL=https://api.binance.com`、
>   `NQ_BINANCE_REAL_WS_URL=wss://ws-api.binance.com:443/ws-api/v3`。
> - 启动恢复现状：本轮通过本地 `.env` 重启 `nq-app` 后，应用健康检查恢复为 `UP`，不再被
>   OKX `401/50110` 或 Binance `-2015` 启动阻断；日志显示
>   `OKX adapter connection fingerprint: env=real, baseUrl=https://www.okx.com, apiKey=4e13...e4e5`，
>   以及 `binance_ws_connected subscription_id=0`、`binance_ws_session_subscriptions_checked confirmed=true`。
> - D1 / OKX Real 最小复验：两次 `LIMIT -> Cancel` 尝试都在下单阶段收敛为 `REJECTED/51008`，
>   最新单 `clientOrderId=d1okx0311163301`、`symbol=DOGE-USDT`、
>   `orders.order_id=ord-6b8916bf-dfee-496c-888a-ead4b73c4d6c`、`external_order_id=null`、
>   `trades=0`、`ledger_entries=0`；交易所返回
>   `Order failed. Your available USDT balance is insufficient...`，因此未拿到 `OrderAck / CancelAck`。
> - D2 / Binance Real 最小复验：第二次重试成功通过。
>   `clientOrderId=d2bin0311163301` 完成 `ACCEPTED -> CANCELLED`，
>   `orders.order_id=ord-1df6aa88-5393-4149-9cd9-a37d3768b207`，
>   `orders.external_order_id=13994590627`，`trades=0`，`ledger_entries=0`；
>   `event_store` 记录了 `PlaceOrderCommand / OrderAck / CancelOrderCommand / CancelAck`，
>   `audit_logs` 记录了 `ORDER_ACKED / ORDER_CANCELLED`。
> - 结论：Binance Real 已恢复最小复验通过；当前 Phase D 仅剩 OKX Real 账户可用余额不足这一条真实阻断点。
>
> 2026-03-11 OKX Real 1U 余额重验状态：**通过**。
> - 延续当前 `real` 指纹与已恢复的健康启动，在确认账户可用余额约 `1U` 后，仅重跑 OKX Real `UseCase-A`。
> - `clientOrderId=d1okx1u0311164501` 的 `DOGE-USDT` LIMIT 单完成 `ACCEPTED -> CANCELLED`，
>   `orders.order_id=ord-3edbb2db-24b6-438a-812f-4bba7f66afae`，
>   `orders.external_order_id=3379478338019745792`，`trades=0`，`ledger_entries=0`。
> - `event_store` 记录了 `PlaceOrderCommand / OrderAck / CancelOrderCommand / CancelAck`，
>   `audit_logs` 记录了 `ORDER_ACKED / ORDER_CANCELLED`，证据链齐全。
> - 结论：D1 已补通过；结合本轮已通过的 D2，Phase D 当前可视为通过，可以恢复 GateC Final Acceptance。

---

## 0. 基础构建门禁（必须）

- [x] `mvn -q -f backend/pom.xml test` 全绿
- [x] 本地 Postgres 可启动（docker compose）
- [x] `nq-app` 可启动（profile=local），health 可用（若已有 actuator）
- [x] GateC 验收入口已就绪（local only）：`POST /__gatec/orders`、`POST /__gatec/orders/cancel`、
  `POST /__gatec/reconcile/runOnce`、`POST /__gatec/recovery/runOnce`
- [x] GateC 验收入口已硬化（`local + nq.gatec.verify.enabled=true` 双门禁；生产零暴露）

---

## 1. GateC-0（必须先做：前置改造门禁）

目标：把“执行链路”从 PAPER 专用路径升级为“adapter 为中心”的统一链路。

### 1.1 adapter-api 三分法（必须）

- [x] `nq-adapter-api` 定义三接口（Port）：
    - [x] TradingAdapter：placeOrder/cancelOrder/getOrder/listOpenOrders
    - [x] MarketDataAdapter：subscribeBars/subscribeTrades/subscribeOrderBook（可先 stub）
    - [x] AccountAdapter：getBalances/getPositions/getAccountSnapshot（可先 REST 拉取）

### 1.2 AdapterRouter（必须）

- [x] 存在 `AdapterRouter`（建议在 `nq-core.execution` 或 `nq-execution` 模块）
- [x] 可按 `(accountId, venue)` 路由到 PAPER/OKX/BINANCE 的 adapter
- [x] core 仅依赖 adapter-api，不依赖 okx/binance 实现类

### 1.3 orders.external_order_id（必须）

- [x] orders 表新增 `external_order_id`
- [x] 索引：`(venue, external_order_id)`（或字段名为 exchange 时对应 `(exchange, external_order_id)`）
- [x] placeOrder 成功回执后必须落库 `external_order_id`

### 1.4 回执事件化（必须）

- [x] placeOrder 的外部结果必须转为事件并写 event_store：
    - [x] OrderAck / OrderReject
- [x] cancelOrder 的外部结果必须转为事件并写 event_store：
    - [x] CancelAck / CancelReject
- [x] fills 必须转为 TradeExecuted 并写 event_store

### 1.5 禁止项（必须满足）

- [x] `OrderCommandService` 不允许出现 “SUBMITTED_TO_PAPER” 这类硬编码分支（或任何 paper-only 分支）
- [x] `nq-scheduler` 不允许存在绕过 adapter 的 PAPER 专用链路（PAPER 必须是 TradingAdapter 的一种实现）

---

## 2. GateC-1（必须：OKX Spot REST-only 闭环）

目标：仅靠 REST 跑通真实闭环（可复现、可验收、可恢复）。

### 2.1 OKX 基础能力（必须）

- [x] OKX signer/鉴权可用（失败可定位：timestamp/signature/apiKey 权限）
- [x] public instruments 可拉取并缓存（用于精度/最小下单量校验与 trim）
- [x] 下单前按 instruments 做 trim（tickSz/lotSz/minSz）

### 2.2 OKX REST 交易链路（必须）

- [x] REST 下单成功：本地订单状态推进到 SENT -> ACCEPTED（由 OrderAck 事件驱动）
- [x] REST 撤单成功：状态推进到 CANCELED（由 CancelAck 事件驱动）
- [x] placeOrder 超时/网络异常：禁止盲重试；必须 query-confirm（查单或挂单列表）

### 2.3 REST reconcile 同步器（必须）

> 同步器可以在 `nq-scheduler`，但必须只调用 adapter-api/adapter-okx，不能绕过 core/ledger 规则。

- [x] 同步器每 N 秒扫描非终态订单（SENT/ACCEPTED/PARTIALLY_FILLED 等）
- [x] 对每个订单执行：
    - [x] query order（推进订单终态）
    - [x] pull fills（写 trades，去重生效：UNIQUE(exchange, exchange_trade_id)）
    - [x] 每笔 fill 触发 ledger posting（ledger_entries.idempotency_key 幂等）
- [x] WS 未启用时，靠 reconcile 也能推进到终态（FILLED/CANCELED/REJECTED）

### 2.4 账本与持仓（必须）

- [x] trades >= 1（exchange=OKX；exchange_trade_id 去重生效）
- [x] ledger_entries >= 2（idempotency_key 非空）
- [x] positions 更新（positions>=1 或对应投影可见）
- [x] 记账不平衡会走失败路径（写 risk/audit + event_store）

### 2.5 事实链/审计（必须）

- [x] event_store 至少包含：
    - [x] order.command（Place/Cancel）
    - [x] order.event（Ack/Reject/CancelAck/CancelReject）
    - [x] trade.event（TradeExecuted）
    - [x] ledger.event（LedgerPosted/失败事件若有）
    - [x] risk/audit 关键节点
- [x] trace_id 贯穿：orders/trades/ledger/risk/audit/event_store 都可追踪

---

## 3. GateC-1.1（可选后置：OKX 私有 WS + REST 兜底）

目标：WS 做实时加速，但不改变事实来源。

- [x] PR-W1 已完成连接治理层（连接/login/订阅管理/心跳/重连/指标），且不落业务表/不推进状态机
- [x] orders/account/positions（或 balance_and_position）订阅成功
- [x] WS 回报映射为标准事件写入 event_store（order.event/audit.event/position.event）
- [x] WS 断线可自动重连并重订阅
- [x] WS 异常必须降级触发一次 REST reconcile（限定窗口/非终态订单集合）
- [x] WS + REST 同时开启不产生重复 trades/ledger（幂等兜底有效）
- [x] CancelReject 不再停留 `CANCEL_REQUESTED`：状态推进到 `CANCEL_REJECTED`，并可由 REST reconcile 对齐回实时事实
- 历史失败记录：2026-03-11 Final Acceptance / OKX Real 最小复验曾因
  `/api/v5/trade/orders-pending -> 401/50110` 启动阻断失败；该问题已由后续 real 权限修正覆盖
- 历史失败记录：2026-03-11 Phase D 再重跑 / OKX Real 最小复验曾因 `clientOrderId=d1okx0311163301`
  命中 `REJECTED/51008`（`available USDT balance is insufficient`）失败；该问题已由后续 `1U` 余额重验覆盖
- [x] 2026-03-11 OKX Real 1U 余额重验：`clientOrderId=d1okx1u0311164501` 的 `DOGE-USDT` LIMIT 已完成
  `ACCEPTED -> CANCELLED`，`orders.external_order_id=3379478338019745792`，`trades=0`，`ledger_entries=0`，
  `event_store + audit_logs` 证据链齐全

---

## 4. GateC-2（Binance Spot）

目标：复用 GateC 框架，仅替换 adapter 实现。

- [x] PR-C10（无 key 阶段）：REST signer + HTTP client + mock 单测已完成，不依赖真实网络/真实 key
- [x] PR-C11（无 key 阶段）：exchangeInfo/filters 缓存 + trim 规则已完成，不依赖真实网络/真实 key
- [x] PR-C12（无 key 阶段）：adapter-binance 已实现 TradingAdapter（place/cancel/get/listOpenOrders）且 mock 单测覆盖 request
  组装/错误解析
- [x] PR-C12（无 key 阶段）：Binance REST reconcile 已实现 query order + myTrades -> trades 去重 -> ledger posting 幂等回归测试
- [x] PR-C14（无 key 阶段）：adapter-binance 已支持 `NQ_BINANCE_KEY_TYPE=hmac|ed25519`，且 Ed25519 signer / 配置错误路径有单测覆盖
- [x] 历史运行态证据：PR-C12（运行态阶段）REST-only UseCase-A（LIMIT 远离盘口 -> Cancel）曾通过并在 WORK.md 留存证据链
- [x] 历史运行态证据：PR-C13（运行态阶段）REST-only UseCase-B（MARKET 小额成交 -> reconcile -> trades/ledger/positions）曾通过并在
  WORK.md 留存证据链
- [x] 历史运行态证据：Binance 实盘最小复验（Ed25519，LIMIT -> Cancel）曾通过：`DOGE-USDT` 单 `bre0309174403` 已完成
  `SENT -> ACCEPTED -> CANCELLED`，`orders.external_order_id=13975572161`，`trades=0`，`ledger=0`，`event_store/audit` 证据链齐全
- [x] 历史实现/回归证据：GateC-2.1 / PR-BW1：Binance 私有 WS 基建与连接治理已完成，覆盖 listenKey
  生命周期、连接/重连、心跳/最近消息时间、local smoke runner（默认关闭），且不写业务表/不推进状态机
- [x] 历史实现/回归证据：GateC-2.1 / PR-BW2：Binance 用户数据流原始消息已映射为标准事件并写入 `event_store`，覆盖
  `executionReport -> order.event.v1`、`outboundAccountPosition -> position.event.v1`、`balanceUpdate -> audit.event.v1`
  ，解析/映射失败会写 `audit_logs + audit.event.v1`，且不写业务表/不推进状态机/不触发 reconcile
- [x] 历史实现/回归证据：GateC-2.1 / PR-BW3：Binance WS 仅加速 `OrderAck/CancelAck/OrderReject/CancelReject`
  ，通过既有状态机入口推进并允许 `linkExternalOrderId`；`fills/trades/ledger` 仍 REST-first；WS 断线或 listenKey 失效会触发一次受限
  `reconcileOnce(limit)` 并写 `audit_logs + audit.event.v1`，且 WS+REST 并行不重复 trades/ledger、不回退状态
- [x] testnet 通过后再上真实 key（Trade+Read；不启用提现权限）
- 历史失败记录：2026-03-11 Final Acceptance / Binance Testnet REST-only UseCase-A 曾因 `-2015` 鉴权阻塞失败；
  已由后续 Testnet 凭证重置与重验通过覆盖
- 历史失败记录：2026-03-11 Final Acceptance / Binance Testnet REST-only UseCase-B 曾因同一 `-2015` 鉴权阻塞未进入
  MARKET 下单与 reconcile；已由后续重验通过覆盖
- 历史失败记录：2026-03-11 Final Acceptance / Binance Testnet WS 曾因 `401/-2015` 未能进入
  `executionReport` / 账户更新验收；已由后续 WS 重验通过覆盖
- 历史失败记录：2026-03-11 Final Acceptance / Binance Real 最小复验曾因 `clientOrderId=brg0311020842`
  命中 `-2015` 失败；已由后续 `clientOrderId=d2bin0311163301` 的实盘重验通过覆盖
- [x] 2026-03-11 Phase D 再重跑 / Binance Real 最小复验：`clientOrderId=d2bin0311163301` 的 `DOGE-USDT` LIMIT
  已完成 `ACCEPTED -> CANCELLED`，`orders.external_order_id=13994590627`，`trades=0`，`ledger_entries=0`，
  `event_store + audit_logs` 证据链齐全

---

## 5. 重启恢复（GateC 必测门禁）

- [x] 制造非终态订单（SENT/ACCEPTED，trades=0）后重启服务
- [x] 重启后不重复下单、不重复成交、不重复记账
- [x] 重启后通过 REST reconcile 推进到终态
- [x] 全程 trace_id 可追踪、audit/risk 有证据链
- [x] query-confirm 遇到 `OKX 51603 (Order does not exist)` 不阻断启动，且降级证据链完整（audit + event_store + 终态推进）

---

## 6. 文档对齐检查（必须）

- [x] `docs/gates/gate-c/ARCHITECTURE.md` 已冻结并与实现一致
- [x] `docs/gates/gate-c/CONTRACTS.md` 的事件字段与代码 DTO 一致
- [x] `docs/gates/gate-c/DB_SCHEMA.md` 的 DDL 与 Flyway 增量一致
- [x] `docs/gates/gate-c/RECOVERY_RUNBOOK.md` 与启动恢复行为一致
