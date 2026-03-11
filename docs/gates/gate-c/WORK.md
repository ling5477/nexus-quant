# docs/gates/gate-c/WORK.md

# Gate C WORK 记录

> 最后更新：2026-03-11
> 范围：Gate C（CEX 接入：OKX -> Binance）

---

## 1. 当前目标与边界

- 目标：冻结 GateC 全量能力与最终验收证据链，确保 `PR-C14 / PR-C15 / Phase D 重验` 的事实可直接用于审查、冻结与回放。
- 目标：把 GateC 的阶段进度对齐到“Phase A / B / C / D 已通过，GateC Final Acceptance 可冻结”的真实状态。
- 下一步只允许做冻结提交整理与归档，不进入 GateD，也不得借机扩展新范围。
- 不做：不做链上/OnchainOS；不做高频；不做复杂做市；不做 `nq-core/nq-ledger/nq-risk` 的交易所分支改造。

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
PR-W1（GateC-1.1）：WS 基建与连接治理（连接/login/订阅管理/心跳/重连，不落业务）
PR-W2（GateC-1.1）：WS 事件映射 + event_store 入链（不落业务表）
PR-W3（GateC-1.1）：WS-REST 协同与降级策略（WS 加速，REST 永远兜底）
PR-C10：Binance REST 基础设施（Signer + HTTP Client + mock 单测，无 key）
PR-C11：Binance exchangeInfo/filters 缓存 + 下单前 trim（无 key）
PR-C12：Binance REST 交易闭环（TradingAdapter + reconcile + 事件/落库串联）
PR-C13：Binance Testnet 运行态验收（UseCase-A/B）
PR-C14：Binance Ed25519 signer 支持（最小 PR，仅补签名能力）
PR-C15：Binance 实盘最小风险复验（Ed25519，LIMIT -> Cancel）
PR-BW1（GateC-2.1）：Binance 私有 WS 基建与连接治理（listenKey / 连接 / 心跳 / 重连，不落业务）
PR-BW2（GateC-2.1）：Binance WS 事件映射 + event_store 入链（不落业务表）
PR-BW3（GateC-2.1）：Binance WS-REST 协同与降级（WS 加速，REST 永远兜底）

---

## 3. 验证命令

- `mvn -q -f backend/pom.xml test`
- `docker compose up -d postgres`
- 启动 nq-app（profile=local）
- 表计数核验：strategy_runs/orders/trades/ledger_entries/ledger_events/audit_logs/risk_events/positions/event_store
- event_store topic 统计（order/trade/ledger/risk/position）

---

## 4. 今日完成（追加）

- 2026-03-11：完成 GateC Final Acceptance 收口校验。
    - 阶段结论：
      Phase A / B / C / D 已全部通过；文档冻结校验通过，当前无剩余阻断点。
    - 文档冻结口径：
      `docs/current/GATE_CHECKLIST.md` 与 `docs/gates/gate-c/WORK.md` 已对齐到“GateC 可冻结”的最终事实；
      `ARCHITECTURE / CONTRACTS / RECOVERY_RUNBOOK / EVOLUTION_RULES` 本轮核对后与实现和验收事实一致，无需改功能代码。
    - 下一步：
      进入冻结提交整理，不进入 GateD。

- 2026-03-11：在确认 OKX Real 账户可用余额约 `1U` 后，再次重跑 D1（OKX Real 最小复验），本轮通过；结合已通过的 D2，Phase D 当前可视为通过。
    - D0 / real 指纹：
      延续当前 `.env` 的 `NQ_OKX_ENV=real`、`NQ_OKX_REAL_BASE_URL=https://www.okx.com`、
      `NQ_BINANCE_ENV=real`、`NQ_BINANCE_KEY_TYPE=ed25519`、
      `NQ_BINANCE_REAL_BASE_URL=https://api.binance.com`、
      `NQ_BINANCE_REAL_WS_URL=wss://ws-api.binance.com:443/ws-api/v3`；
      `nq-app` 继续保持健康启动。
    - D1 / OKX Real 1U 余额重验：
      `clientOrderId=d1okx1u0311164501` 的 `DOGE-USDT` LIMIT 单完成 `ACCEPTED -> CANCELLED`，
      `orders.order_id=ord-3edbb2db-24b6-438a-812f-4bba7f66afae`，
      `orders.external_order_id=3379478338019745792`，`trades=0`，`ledger_entries=0`。
      `event_store` 记录 `PlaceOrderCommand / OrderAck / CancelOrderCommand / CancelAck`，
      `audit_logs` 记录 `ORDER_ACKED / ORDER_CANCELLED`，满足 Phase D 对 OKX Real UseCase-A 的证据要求。
    - 结论：
      OKX Real 的旧阻断点已从启动权限问题收口到余额口径，并已在 `1U` 余额约束下真实通过；
      结合前序已通过的 Binance Real 最小复验，Phase D 当前可以结束，下一步应恢复 GateC Final Acceptance，而不是进入 GateD。

- 2026-03-11：在修正实盘权限后，再次重跑 Phase D（只覆盖 OKX Real / Binance Real 最小复验），结论为部分通过，仍不能恢复 GateC Final Acceptance。
    - D0 / real 指纹：
      当前 `.env` 脱敏指纹为 `NQ_OKX_ENV=real`、`NQ_OKX_REAL_BASE_URL=https://www.okx.com`、
      `NQ_BINANCE_ENV=real`、`NQ_BINANCE_KEY_TYPE=ed25519`、
      `NQ_BINANCE_REAL_BASE_URL=https://api.binance.com`、
      `NQ_BINANCE_REAL_WS_URL=wss://ws-api.binance.com:443/ws-api/v3`。
    - real 启动恢复现状：
      通过本地 `.env` 重新生成启动批处理并重启 `nq-app` 后，应用健康检查恢复为 `UP`；
      本轮日志显示 `OKX adapter connection fingerprint: env=real, baseUrl=https://www.okx.com, apiKey=4e13...e4e5`，
      以及 `binance_ws_connected subscription_id=0`、`binance_ws_session_subscriptions_checked confirmed=true`，
      说明旧的 `OKX 401/50110` 与 `Binance -2015` 启动阻断已消失。
    - D1 / OKX Real 最小复验：
      两次 `LIMIT -> Cancel` 尝试都在下单阶段收敛为 `REJECTED/51008`。
      最新单 `clientOrderId=d1okx0311163301`、`symbol=DOGE-USDT`、
      `orders.order_id=ord-6b8916bf-dfee-496c-888a-ead4b73c4d6c`、`external_order_id=null`、`trades=0`、`ledger_entries=0`；
      `audit_logs` 记录 `ORDER_REJECTED(reject_code=51008)`，交易所原始原因是
      `Your available USDT balance is insufficient...`，因此本轮仍未拿到 `OrderAck / CancelAck`。
    - D2 / Binance Real 最小复验：
      第二次重试成功通过。`clientOrderId=d2bin0311163301` 的 `DOGE-USDT` LIMIT 单完成
      `ACCEPTED -> CANCELLED`，`orders.order_id=ord-1df6aa88-5393-4149-9cd9-a37d3768b207`，
      `orders.external_order_id=13994590627`，`trades=0`，`ledger_entries=0`；
      `event_store` 记录 `PlaceOrderCommand / OrderAck / CancelOrderCommand / CancelAck`，
      `audit_logs` 记录 `ORDER_ACKED / ORDER_CANCELLED`。
    - 结论：
      Binance Real 最小复验已恢复通过；Phase D 当前只剩 OKX Real 账户可用余额不足这一个真实阻断点，
      下一步只能继续做实盘环境收口，不能恢复 GateC Final Acceptance，也不能进入 GateD。

- 2026-03-11：执行 Phase D 重跑（只覆盖 OKX Real / Binance Real 最小复验），结论仍为未通过。
    - D0 / real 指纹：
      当前 `.env` 脱敏指纹为 `NQ_OKX_ENV=real`、`NQ_OKX_REAL_BASE_URL=https://www.okx.com`、
      `NQ_BINANCE_ENV=real`、`NQ_BINANCE_KEY_TYPE=ed25519`、
      `NQ_BINANCE_REAL_BASE_URL=https://api.binance.com`、
      `NQ_BINANCE_REAL_WS_URL=wss://ws-api.binance.com:443/ws-api/v3`；按规则已在切换到 real 后重启 `nq-app`。
    - D1 / OKX Real 最小复验：
      使用当前 `.env` 生成本地启动批处理并重启 `nq-app` 后，应用在启动恢复阶段再次命中
      `trace_id=trc-okx-recovery-startup` 的
      `/api/v5/trade/orders-pending?instType=SPOT&instId=BTC-USDT -> 401/50110`；
      应用上下文回滚，未能进入 `LIMIT 远离盘口 -> Cancel`，因此本轮没有新的 `OrderAck / CancelAck`、
      `orders.external_order_id`、`CANCELLED`、`trades=0`、`ledger_entries=0` 证据链。
    - D2 / Binance Real 最小复验：
      因 D1 的 OKX Real 启动恢复阻断，`nq-app` 无法保持健康态，本轮未能实际执行 Binance Real
      `LIMIT -> Cancel`；
      但同一轮 real 启动日志已出现
      `binance_ws_start fingerprint=env=real, baseUrl=https://api.binance.com, wsUrl=wss://ws-api.binance.com:443/ws-api/v3`
      与 `binance_ws_subscribe_failed status=401 error_code=-2015 reason=Invalid API-key, IP, or permissions for action.`，
      说明 Binance Real 私有 WS 路径仍未恢复。
    - 结论：
      本轮只能确认 Phase D 的两个阻断点仍然存在：OKX Real 仍被启动恢复 `401/50110` 卡死，Binance Real 仍可观测到
      `401/-2015`；因此下一步仍是继续 real 环境收口，不能恢复 GateC Final Acceptance，也不能进入 GateD。

- 2026-03-11：在更换最新 Binance Spot Testnet HMAC 凭证后，再次重跑 Phase C（Binance Testnet，全量验收），本轮通过。
    - C0 / 环境指纹：
      当前 `.env` 指纹为 `NQ_BINANCE_ENV=dome`、`NQ_BINANCE_KEY_TYPE=hmac`、
      `NQ_BINANCE_DOME_BASE_URL=https://testnet.binance.vision`、
      `NQ_BINANCE_DOME_WS_URL=wss://ws-api.testnet.binance.vision/ws-api/v3`；
      通过重新生成本地启动批处理并重启 `nq-app`，`/actuator/health=UP`。
    - C1 / REST UseCase-A：
      `clientOrderId=pc1r0311153810`，`placeOrder=ACCEPTED`，`cancelOrder=CANCELLED`，`orders.external_order_id=14497482`
      ，终态 `CANCELLED`，`trades=0`，`ledger_entries=0`；
      `event_store` 包含 `order.command.v1 / order.event.v1 / risk.event.v1`，`audit_logs` 包含
      `ORDER_CREATED / ORDER_ACKED / ORDER_CANCELLED`。
    - C2 / REST UseCase-B：
      `clientOrderId=pc2r0311153942`，经三次 `reconcile/runOnce` 后终态 `FILLED`；`orders.external_order_id=14498581`，
      `trades=1`，`distinct exchange_trade_id=1`，`ledger_entries=2`，`idempotency_key` 为
      `trd-5e31e6c6-f298-4d36-84b9-88ac34aaaa14:LEDGER:1/2`，`positions(account_id=3001,symbol=BTC-USDT)=0.00010000`。
    - C3 / Binance 私有 WS：
      应用日志出现 `binance_ws_connected subscription_id=0` 与
      `binance_ws_session_subscriptions_checked confirmed=true`；
      最近窗口 `event_store where payload_json->>'source'='BINANCE_WS' = 10`，说明原始 WS 事件已恢复入链。
    - C4 / WS + REST 协同：
      `clientOrderId=pc4r0311154220` 完成 `ACCEPTED -> CANCELLED`，`orders.external_order_id=14500750`，`trades=0`，
      `ledger_entries=0`；
      `event_store` 中存在同一 `external_order_id=14500750` 的 `BINANCE_WS OrderAck + CancelAck`，未观察到状态回退。
    - C5 / 强制断连 / 降级：
      日志与审计链路出现 `binance_ws_reconnect_scheduled(reason=smoke_forced_reconnect)`、`BINANCE_WS_DISCONNECTED`、
      `BINANCE_WS_RECONCILE_DEGRADE_COMPLETED`、`BINANCE_WS_RECONNECTED`；
      复核 UseCase-B 的 `trades=1 / ledger_entries=2` 保持不变，未发生重复成交或重复记账。
    - 结论：
      最新 Testnet HMAC 凭证已消除 Binance Testnet 的 `-2015` 阻断，Phase C 可判定为通过；
      下一步只应进入 Phase D（OKX Real / Binance Real 最小复验），不进入 GateC Final Acceptance。

- 2026-03-11：在修正 `NQ_BINANCE_DOME_BASE_URL=https://testnet.binance.vision` 后，重新执行 Phase C（Binance
  Testnet，全量验收），结论仍为未通过。
    - C0 / 环境指纹：
      当前 `.env` 指纹确认为 `NQ_BINANCE_ENV=dome`、`NQ_BINANCE_KEY_TYPE=hmac`、
      `NQ_BINANCE_DOME_BASE_URL=https://testnet.binance.vision`、
      `NQ_BINANCE_DOME_WS_URL=wss://ws-api.testnet.binance.vision/ws-api/v3`；
      `NQ_GATEC_VERIFY_ENABLED=true`、`NQ_BINANCE_WS_ENABLED=true`，`nq-app` 可启动且 `/actuator/health=UP`。
    - C1 / REST UseCase-A：
      初次请求因人工传入了不存在的 `strategyRunId=phasec-c1` 命中 `fk_orders_strategy_run`，已确认为验收请求参数问题，不计入交易所阻断；
      改为 `strategyRunId=null` 后重新下单，`clientOrderId=pc10311144214` 收敛为 `REJECTED/-2015`，
      `external_order_id=null`，`trades=0`，`ledger_entries=0`，未拿到 `OrderAck / CancelAck`。
    - C2 / REST UseCase-B：
      `clientOrderId=pc20311144440` 的 `MARKET` 下单同样收敛为 `REJECTED/-2015`；两次 `reconcile/runOnce` 均
      `new_trades=0`，未进入 `trades / ledger / positions` 闭环。
    - C3 / Binance 私有 WS：
      应用日志在本轮窗口内持续出现
      `binance_ws_subscribe_failed status=401 error_code=-2015 reason=Invalid API-key, IP, or permissions for action.`；
      本轮窗口 `event_store where payload_json->>'source'='BINANCE_WS' = 0`，未拿到
      `status=200 / subscriptionId / session.subscriptions`。
    - C4 / WS + REST 协同：
      因 C1 未拿到 `OrderAck / CancelAck`、C3 未建立有效私有 WS，本轮不存在可验证的
      `BINANCE_WS_ORDER_ACK_ACCELERATE / BINANCE_WS_CANCEL_ACK_ACCELERATE` 证据链。
    - C5 / 强制断连 / 降级：
      `audit_logs` 可见
      `BINANCE_WS_DISCONNECTED / BINANCE_WS_RECONCILE_DEGRADE_COMPLETED / BINANCE_WS_RECONCILE_DEGRADE_SKIPPED_COOLDOWN`
      ，说明失败后降级动作仍在；
      但由于私有 WS 从未进入成功订阅态，本轮不能把该项判为通过。
    - 结论：
      `baseUrl` 修正后，Binance Testnet 的真实阻断点仍是同一组 `-2015` 鉴权失败，REST 与私有 WS 都未恢复；
      因此 Phase C 仍未通过，不能进入 Phase D，也不能恢复 GateC Final Acceptance。

- 2026-03-11：尝试重跑 Phase C（Binance Testnet，全量验收），但在 C0 环境指纹阶段即停止，未进入 C1-C5。
    - 本轮前提：用户已声明更换最新 Binance Spot Testnet HMAC key/secret，且不绑定 IP 白名单；本轮目标仅为重跑 Phase C，不进入
      Phase D 或 GateC Final Acceptance。
    - 当前 `.env` 脱敏指纹：`NQ_BINANCE_ENV=dome`、`NQ_BINANCE_KEY_TYPE=hmac`、
      `NQ_BINANCE_DOME_WS_URL=wss://ws-api.testnet.binance.vision/ws-api/v3`、`NQ_BINANCE_DOME_API_KEY=6VhX...P9SE`、
      `NQ_GATEC_VERIFY_ENABLED=true`、`NQ_BINANCE_WS_ENABLED=true`。
    - 真实阻塞点：`NQ_BINANCE_DOME_BASE_URL` 当前仍为 `https://testnet.binance.vision/api`，而 `docs/current/README.md
      ` 明确要求 Binance Testnet 必须固定为 `https://testnet.binance.vision`。
    - 处理结果：按本轮规则，C0 不满足即停止，不继续执行 REST UseCase-A / UseCase-B、私有 WS、WS+REST 协同和强制断连降级；本轮也不进入
      Phase D。
    - 下一步门禁：先只修本地 `.env` 的 `NQ_BINANCE_DOME_BASE_URL`，修正后重启 `nq-app`，然后重新开始 Phase C；在 Phase C
      真正跑完前，不得宣告 `-2015` 已消除。

- 2026-03-11：完成 Binance Testnet 环境收口分析，不进入 Phase C / Phase D / GateC Final Acceptance。
    - 环境指纹：
      当前本地 `.env` 的 Binance Testnet 运行组为 `NQ_BINANCE_ENV=dome`、`NQ_BINANCE_KEY_TYPE=hmac`、
      `NQ_BINANCE_DOME_BASE_URL=https://testnet.binance.vision`、
      `NQ_BINANCE_DOME_WS_URL=wss://ws-api.testnet.binance.vision/ws-api/v3`；
      `NQ_BINANCE_DOME_API_KEY` 与 `NQ_BINANCE_DOME_API_SECRET` 同时存在，当前未启用 `ed25519` 私钥路径；当前公网出口 IP 为
      `154.206.102.152`。
    - 结论：
      就 `.env` 形态而言，当前已是 Binance Spot Testnet 的正确 `dome + hmac` 组合，不存在 `real baseUrl`、`real wsUrl` 或
      `ed25519` 路径混入；
      但在该组合下，历史定向重验中 REST 下单与私有 WS 订阅仍统一返回
      `-2015 Invalid API-key, IP, or permissions for action.`，因此当前最可疑的阻断点已收口到 Binance 侧凭证对、IP
      白名单或权限配置，而不是本地环境组选择错误。
    - 下一步门禁：
      在重新生成/核对 Binance Spot Testnet HMAC key pair、确认权限并校准 IP 白名单之前，当前不具备重新执行 Phase C 的前提；
      后续恢复验收时，也只能先重跑 Phase C，不能跳到 Phase D。

- 2026-03-11：执行 GateC 环境修正后的定向重验，只覆盖 Phase C；因 Phase C 未通过，Phase D 按规则未进入。
    - Step 0 / 环境指纹：
      当前 `.env` 已切到 Binance Testnet 口径：`NQ_BINANCE_ENV=dome`、`NQ_BINANCE_KEY_TYPE=hmac`、
      `NQ_BINANCE_DOME_BASE_URL=https://testnet.binance.vision`、
      `NQ_BINANCE_DOME_WS_URL=wss://ws-api.testnet.binance.vision/ws-api/v3`；
      同时打开 `NQ_GATEC_VERIFY_ENABLED=true`、`NQ_BINANCE_WS_ENABLED=true`、
      `NQ_BINANCE_WS_SMOKE_FORCE_RECONNECT_MS=15000`。
    - 本地环境准备：
      当前库只有 `PAPER(1001)` 与 `OKX(2001)`，缺少 Binance / OKX Real 验收账户。
      为避免把环境阻塞误判成代码问题，本轮仅在本地数据库补齐：
      `accounts(2002,'OKX-REAL-2002','OKX','ACTIVE')`
      `accounts(3001,'BINANCE-DOME-3001','BINANCE','ACTIVE')`
      `accounts(3002,'BINANCE-REAL-3002','BINANCE','ACTIVE')`
      不涉及代码修改，不改变业务设计。
    - Phase C / C1（Binance Testnet REST UseCase-A）：
      `clientOrderId=c1bn0311114801`，`trace_id=trc-c1-binance-20260311`；
      `POST /__gatec/orders` 返回 `status=REJECTED`；
      `orders.reason=-2015`，`orders.external_order_id=null`，`trades=0`，`ledger_entries=0`；
      `event_store` 存在 `PlaceOrderCommand -> OrderCreated -> RiskPassed -> OrderReject`；
      `audit_logs` 存在
      `ORDER_REJECTED(reject_code=-2015, reject_reason=Invalid API-key, IP, or permissions for action.)`。
    - Phase C / C2（Binance Testnet REST UseCase-B）：
      `clientOrderId=c2bn0311114901`，`trace_id=trc-c2-binance-20260311`；
      `POST /__gatec/orders`（`MARKET`）同样返回 `status=REJECTED`；
      `orders.reason=-2015`，未进入成交 / reconcile / ledger / positions 验收。
    - Phase C / C3（Binance Testnet WS）：
      `nq-app` 已按本轮 `.env` 启动，但应用日志持续出现
      `binance_ws_subscribe_failed status=401 error_code=-2015 reason=Invalid API-key, IP, or permissions for action.`
      与 `binance_ws_closed status_code=1008 reason=disconnected`；
      当前重验窗口内数据库查询结果：
      `event_store where payload_json->>'source'='BINANCE_WS' and created_at > now()-20min = 0`
      `audit_logs where action like 'BINANCE_WS%' and created_at > now()-20min = 0`
      因此本轮未拿到 `status=200 / subscriptionId / session.subscriptions`，也没有当前会话的原始消息入链证据。
    - Phase C / C4（WS + REST 协同）：
      因 C1 下单阶段已被 `-2015` 拒绝、C3 私有 WS 未成功订阅，本轮不存在可验证的 `OrderAck / CancelAck` 加速链路；
      该项按同一阻断点记为未通过，不伪造通过。
    - Phase C / C5（强制断连 / 降级）：
      本轮日志可见 `binance_ws_reconnect_scheduled`、`binance_ws_closed status_code=1008` 与持续重连；
      但当前重验窗口内没有新的
      `BINANCE_WS_DISCONNECTED / BINANCE_WS_RECONCILE_DEGRADE_COMPLETED / BINANCE_WS_RECONCILE_DEGRADE_SKIPPED_COOLDOWN`
      审计或 event_store 证据链，
      因此该项记为未通过。
    - 结论：
      环境修正后，Binance Testnet 的真实阻断点仍是交易所侧 `-2015 Invalid API-key, IP, or permissions for action.`；
      GateC Final Acceptance 不能恢复，必须先继续收口 Binance 凭证 / 权限 / IP 白名单 / key 类型，再重新进入定向重验。

- 2026-03-11：执行 GateC Final Acceptance（仅回归、运行态验收、文档冻结校验），结论为 **GateC 当前不可冻结**。
    - Phase A / 全量测试：
      `mvn -q -f backend/pom.xml test` 通过（exit code 0）。
    - Phase B / OKX Demo（REST-only + WS + Recovery）：
      `pwsh -NoProfile -File scripts/gatec_okx_dome_verify.ps1 -BaseUrl http://localhost:28081 -AutoRestart -StartupTimeoutSec 300`
      通过（exit code 0）。
      UseCase-A：`clientOrderId=g6a0311014232`，`orders.external_order_id=3378627753464774656`，终态 `CANCELLED`，
      `trades=0`，`ledger_entries=0`。
      UseCase-B：`clientOrderId=g6b0311014234`，终态 `FILLED`，`trades=1`，`exchange_trade_id=1188024333`，
      `ledger_entries=4`，`idempotency_key` 连续为 `...:LEDGER:1/2/FEE_1/FEE_2`，
      `positions(account_id=2001,symbol=BTC-USDT)` 已更新，`event_store` 包含
      `TradeExecuted + LedgerPosted + PositionUpdated`。
      Recovery：`clientOrderId=g6c0311014235` 在重启前进入非终态并拿到 `OrderAck`；重启后
      `recovery/runOnce + reconcile/runOnce` 未产生重复 `trades/ledger`，随后撤单收敛为 `CANCELLED`。
      WS：应用日志出现 `okx_ws_connected` / `okx_ws_login_success`；强制断连后出现 `okx_ws_reconnect_scheduled`，
      `audit_logs` 写入 `WS_RECONCILE_DEGRADE_COMPLETED(new_trades=0)`，未观察到重复副作用或状态回退。
    - Phase C / Binance Testnet：
      当前 `.env` Testnet 凭证在 REST 与私有 WS 两条路径都失败。
      REST UseCase-A：`clientOrderId=bfa0311014529`，`placeOrder` 返回 `REJECTED`，`reject_code=-2015`，
      `reject_reason=Invalid API-key, IP, or permissions for action.`；`cancelOrder` 因订单已 `REJECTED` 返回 500。
      WS：应用日志中 `userDataStream.subscribe.signature` 返回 `401/-2015`，未进入 `executionReport` / 账户更新验收。
      因同一鉴权阻断，UseCase-B 与 WS 协同降级不再伪造通过，统一标记为环境受限。
    - Phase D / Real 最小复验：
      OKX Real：使用 `NQ_OKX_ENV=real` 启动时，`OkxRecoveryService` 在 `trace_id=trc-okx-recovery-startup` 调用
      `/api/v5/trade/orders-pending?instType=SPOT&instId=BTC-USDT` 返回 `401/50110`，应用上下文回滚，未能进入 LIMIT ->
      Cancel。
      Binance Real：在 `NQ_BINANCE_ENV=real`、`NQ_BINANCE_KEY_TYPE=ed25519` 下，`clientOrderId=brg0311020842` 的
      `DOGE-USDT` LIMIT 下单返回 `reject_code=-2015`，订单停在 `REJECTED`，未拿到 `OrderAck/CancelAck`，`trades=0`，
      `ledger=0`。
    - Phase E / 文档冻结校验：
      发现 `docs/current/GATE_CHECKLIST.md` 对 Binance/实盘仍保持全勾选，`docs/gates/gate-c/GATE_C_CHECKLIST.md` 整体滞后，
      `WORK.md` 顶部仍写 “Binance WS 尚未开始”。
      本轮只做文档对齐修复：回填 Final Acceptance 真实结果，不改功能实现。

- 2026-03-10：完成 Binance ws-api `1008/disconnected` 最小排查回合，仅补诊断，不做业务修复。
    - 排查目标：
        1) 打印应用内 `userDataStream.subscribe.signature` 脱敏原始报文；
        2) 记录服务端关闭前最近 3 帧、close payload、ping/pong、本地 close/reconnect 时序；
        3) 与独立 probe 做逐字段对比，收敛到可直接修复的点。
    - 代码改动：
      `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsClient.java`
      `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceRuntimeConfig.java`
      `backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsClientTest.java`
      `backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceRuntimeConfigTest.java`
      `backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsClientLiveDiagnosticTest.java`
    - 真实证据（手工 live diagnostic，使用同一份项目 `.env` 的 HMAC Testnet 凭证）：
      `probe.url=wss://ws-api.testnet.binance.vision/ws-api/v3`
      `probe.request.id=1`
      `probe.response={"id":1,"status":200,"result":{"subscriptionId":0}}`
      `client.request.id="req-binance-ws-a35ed343-5126-4dda-8914-4fbee7a117f4"`
      `client.request.method="userDataStream.subscribe.signature"`
      `client.signingPayload="apiKey=<masked>&timestamp=1773134512052"`
      `client.recentFrames` 连续收到 3 次：
      `{"id":null,"status":400,"error":{"code":-1135,"msg":"Invalid 'id' in JSON request; expected an integer, a string matching '^[a-zA-Z0-9-_]{1,36}$', or null."}}`
      `client.close=code=1008, reason=disconnected, payload_hex=646973636f6e6e6563746564`
      `client.serverPingObserved=false`
      `client.serverPongObserved=false`
      `client.clientPingSent=false`
      `client.clientPongSent=false`
      `client.recentLifecycle` 显示顺序：
      `transport_open -> remote_close(code=1008) -> reconnect_scheduled(listener_close) -> local_close_requested(reconnect)`
    - 对比结论：
        1) URL、method、apiKey/timestamp/signature 生成方式与 probe 一致；
        2) 决定性差异是首帧 `id`：
           probe 使用 `1`，服务端回 `200/subscriptionId=0`；
           应用内使用 `req-binance-ws-<uuid>`，长度超过官方允许的 36 字符，服务端先回 `400/-1135`，随后关闭为
           `1008/disconnected`；
        3) 在当前失败路径里，没有证据表明 `ping/pong` 是根因，因为会话在订阅校验阶段已经被拒绝，尚未进入存活期。
    - 下一步最小修复建议：
        1) 仅在 `BinanceWsClient` 把 ws-api request `id` 收敛为官方允许格式（推荐短整数或 <=36 的短字符串）；
        2) 修复后复跑本轮 live diagnostic，确认应用内首帧拿到 `status=200/subscriptionId`；
        3) 若修复后仍被 close，再继续观察 ping/pong 和 `BINANCE_WS` 入链，不提前恢复 GateC Final Acceptance。
- 2026-03-10：完成 Binance ws-api request `id` 非法问题最小修复回合，只修 `BinanceWsClient` 的 request id 生成策略。
    - 修复范围：
      `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsClient.java`
      `backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsClientTest.java`
    - 修复内容：
        1) `userDataStream.subscribe.signature` 的 ws-api request `id` 改为递增整数；
        2) `session.subscriptions` 同样改为递增整数；
        3) 保留原有签名、URL、时序与业务边界不变，不扩散到 core/ledger/risk。
    - 验证结果：
        1) `mvn -q -f backend/pom.xml test` 通过；
        2) live diagnostic：
           `probe.response={"id":1,"status":200,"result":{"subscriptionId":0}}`
           `client.request.id=1`
           `client.recentFrames` 首帧为 `{"id":1,"status":200,"result":{"subscriptionId":0}}`
           第二帧为 `{"id":2,"status":200,"result":[{"subscriptionId":0}]}`
           说明应用内客户端已拿到 `subscriptionId=0` 且 `session.subscriptions` 确认成功；
        3) GateC-2.1 最小回归：
           启动 `nq-app`（local, `NQ_GATEC_VERIFY_ENABLED=true`, `NQ_BINANCE_WS_ENABLED=true`）后，
           Binance WS 成功打印 `binance_ws_connected subscription_id=0` 与
           `binance_ws_session_subscriptions_checked confirmed=true`；
           UseCase-A：`clientOrderId=bwa0310180528`，下单返回 `ACCEPTED`，撤单返回 `CANCELLED`；
           `orders.external_order_id=13692003`；
           `event_store` 中 `source=BINANCE_WS` 存在 `OrderAck` 与 `CancelAck`（同一 `external_order_id=13692003`）；
           `trades=0`；
           `ledger_entries(trace_id in place/cancel)=0`；
           forced reconnect 触发了
           `BINANCE_WS_DISCONNECTED`
           `BINANCE_WS_RECONCILE_DEGRADE_COMPLETED(new_trades=0)`
           `BINANCE_WS_RECONNECTED`
           审计链。
    - 结论：
      当前 `ws-api request id` 非法问题已修复，GateC-2.1 的连接、订阅确认、最小 UseCase-A 与断连降级回归通过；
      但 GateC Final Acceptance 不在本轮自动恢复范围内，仍按门禁保持阻塞状态，待后续统一验收。

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
      `pwsh -File scripts/gatec_okx_dome_verify.ps1 -AutoRestart -BaseUrl http://localhost:28081 -StartupTimeoutSec 300`
      退出码 0；
      最新三单：`g6a0305054548 -> CANCELLED`、`g6b0305054550 -> FILLED`、`g6c0305054552 -> CANCELLED`；
      `trade/ledger` 计数：`A=0/0`、`B=1/4`、`C=0/0`；`docs/current/GATE_CHECKLIST.md` 第 6 节四项已全部勾选
- PR-W1：已完成 OKX 私有 WS 基建与连接治理（不落业务）。
    - 改动范围：`nq-adapter-okx`（`OkxWsClient/OkxWsProtocol/OkxWsSubscription/OkxWsMetricsSnapshot`、`OkxRuntimeConfig`）、
      `nq-app`（`OkxWsSmokeRunner`、`ModuleWiringConfiguration`、`application*.yml`）、`.env.example`、
      `nq-adapter-okx` 单测（`OkxWsProtocolTest`、`OkxRuntimeConfigTest`）
    - 验收证据：
      `mvn -q -f backend/pom.xml test` 通过；
      `pwsh -File scripts/gatec_okx_dome_verify.ps1 -AutoRestart -BaseUrl http://localhost:28081 -StartupTimeoutSec 300`
      通过（退出码 0）；
      `dome` 本地 smoke（`NQ_OKX_WS_ENABLED=true`）连续运行 5+ 分钟，日志包含
      `okx_ws_connected`、`okx_ws_login_success`、`okx_ws_metrics(ws_connected=1,last_msg_age_ms<2000)`、
      `okx_ws_reconnect_scheduled` + 重连后再次 `okx_ws_connected/okx_ws_login_success`
- PR-W2：已完成 WS 事件映射 + event_store 入链（不落业务表）。
    - 改动范围：`nq-adapter-okx`（`OkxWsEventMapper`）、`nq-app`（`OkxWsEventStoreBridge`）
    - 映射范围：`orders -> order.event.v1`、`account -> audit.event.v1`、`balance_and_position -> position.event.v1`
    - 验收证据：
      `docker exec -i nexusquant-postgres psql -U postgres -d nexus_quant -c "select topic,count(*) from event_store where payload_json->>'source'='OKX_WS' group by topic"`
      返回 `order.event.v1` 与 `audit.event.v1` 均有入链记录
- PR-W3：已完成 WS-REST 协同与降级策略（WS 加速，REST 永远兜底）。
    - 改动范围：
      `nq-adapter-okx`（`OkxWsClient` 连接事件回调，`OkxWsConnectionListener`）、
      `nq-scheduler`（`OkxWsOrderAccelerationService`、`OkxWsDegradeReconcileCoordinator` + 单测）、
      `nq-app`（`OkxWsEventStoreBridge` 接入加速器）、`application*.yml`、`.env.example`
    - 协同口径：
        1) 仅加速 `OrderAck/CancelAck/OrderReject/CancelReject`，并通过 `OrderCommandService` 状态机入口推进
        2) `fills/trades/ledger` 仍 REST-first，不新增 WS 直写路径
        3) 断线/重连失败/订阅失败阈值触发一次受限 `reconcileOnce(limit)`，带 cooldown 去抖
    - 验收证据：
      `mvn -q -f backend/pom.xml test` 通过（新增 `OkxWsOrderAccelerationServiceTest`、
      `OkxWsDegradeReconcileCoordinatorTest`）；
      `pwsh -File scripts/gatec_okx_dome_verify.ps1 -BaseUrl http://localhost:28081 -SkipRestartPause -StartupTimeoutSec 120`
      通过（WS 开启）；
      `pwsh -NoProfile -File scripts/gatec_okx_dome_verify.ps1 -BaseUrl http://localhost:28081 -SkipRestartPause -StartupTimeoutSec 120`
      复验通过（exit code=0，2026-03-05 18:42）；
      `UseCase-A` 最新单 `g6a0305101443 -> CANCELLED`，`trades=0`，`ledger_entries=0`；
      `UseCase-B` 最新单 `g6b0305101445 -> FILLED`，`trades=1`，`ledger_entries=4`；
      `audit_logs` 出现 `WS_RECONNECT_SCHEDULED/WS_RECONCILE_DEGRADE_COMPLETED/WS_RECONCILE_DEGRADE_SKIPPED_COOLDOWN`，
      `event_store(topic=audit.event.v1)` 出现对应 `payload.action=WS_*` 证据链
    - 评审修复（方案 A，`CANCEL_REJECTED`）：
        1) 新增 `OrderStatus.CANCEL_REJECTED`，并补状态机迁移：`CANCEL_REQUESTED -> CANCEL_REJECTED`、
           `CANCEL_REJECTED -> ACCEPTED/PARTIALLY_FILLED/FILLED/CANCEL_REQUESTED`
        2) `OrderCommandService.cancelOrder` 在 `CancelReject` 时推进到 `CANCEL_REJECTED`，不再停留 `CANCEL_REQUESTED`
        3) `OkxRestReconcileService` 对历史 `CANCEL_REQUESTED` 脏状态先过渡到 `CANCEL_REJECTED` 再对齐，消除非法迁移
        4) 新增回归测试：`OrderCommandServiceTest`、`OkxWsOrderAccelerationServiceTest`、`OkxRestReconcileServiceTest`
- 今日收尾：
    - 已完成 GateC-0 ~ GateC-1.1（含 WS 协同降级与 `CANCEL_REJECTED` 修复）的代码与文档沉淀，当前工作区可提交。
    - 已完成 GateC-1.1 最终验收回填：`docs/current/GATE_CHECKLIST.md` 与 GateC 文档（
      `ARCHITECTURE/CONTRACTS/RECOVERY_RUNBOOK/DECISIONS/WORK`）已对齐并冻结。
- PR-C9：已补齐 `DB_SCHEMA` 第 5 条建议 DDL（trades 订单维度回溯索引）。
    - 改动范围：`nq-infra`（`V4__gate_c_trade_external_order_id_index.sql`）、`nq-scheduler`（`PaperTradeRecord` /
      `JdbcTradeRepository` / `OkxRestReconcileService` / `PaperMatchingService`）、`docs/gates/gate-c/DB_SCHEMA.md`
    - 验收证据：`mvn -q -f backend/pom.xml test` 通过；`trades` 新增 `external_order_id` 并创建
      `idx_trades_exchange_external_order_id (exchange, external_order_id)` 条件索引
- PR-C10：已完成 Binance REST 基础设施（无 key 阶段）。
    - 改动范围：`nq-adapter-binance`（`BinanceRuntimeConfig`、`BinanceRequestSigner`、`BinanceHttpClient`、
      `BinanceApiException`、mock 单测）、`.env.example`、`docs/current/GATE_CHECKLIST.md`、`docs/gates/gate-c/SOURCES.md`
    - 配置键：`NQ_BINANCE_ENV`、`NQ_BINANCE_DOME_BASE_URL`、`NQ_BINANCE_DOME_API_KEY`、`NQ_BINANCE_DOME_API_SECRET`、
      `NQ_BINANCE_REAL_BASE_URL`、`NQ_BINANCE_REAL_API_KEY`、`NQ_BINANCE_REAL_API_SECRET`、`NQ_BINANCE_TIMEOUT_MS`
    - 验收证据：`mvn -q -f backend/pom.xml test` 通过；mock 单测覆盖 `GET + query` 签名、`POST/DELETE` 签名路径、错误响应
      `code/msg` 结构化解析；未访问真实 Binance 网络
- PR-C11：已完成 Binance exchangeInfo/filters 缓存 + 下单前 trim（无 key 阶段）。
    - 改动范围：`nq-adapter-binance`（`BinanceExchangeInfoClient`、`BinanceFiltersCache`、`BinanceOrderTrimmer`、
      `BinanceSymbolFilters`、`BinanceTrimResult`、mock/trim 单测）、`.env.example`、`docs/current/GATE_CHECKLIST.md`、
      `docs/gates/gate-c/SOURCES.md`
    - filters 覆盖：`PRICE_FILTER`、`LOT_SIZE`、`MIN_NOTIONAL/NOTIONAL`、`MARKET_LOT_SIZE`
    - trim 规则：`price -> tickSize` 向下截断、`qty -> stepSize` 向下截断；校验 `TRADING` 状态、`minQty/maxQty`、
      `minNotional/maxNotional`；symbol 命名差异（`BTC-USDT` / `BTCUSDT`）封装在 adapter-binance cache 内
    - 配置键：`NQ_BINANCE_EXCHANGE_INFO_REFRESH_MS`
    - 验收证据：`mvn -q -f backend/pom.xml test` 通过；mock 单测覆盖 `exchangeInfo` 解析与 cache TTL 刷新；trim
      单测覆盖价格截断、数量截断、`qty < minQty` 拒单、`notional < minNotional` 拒单、symbol 非 `TRADING`/不存在拒单；未访问真实
      Binance 网络
- PR-C12：已完成 Binance REST-only 闭环的无 key 阶段代码路径与回归测试。
    - 改动范围：`nq-adapter-binance`（`BinanceExchangeAdapter`、`BinanceTradeFill`、adapter mock 单测）、`nq-scheduler`（
      `BinanceRestReconcileService`、reconcile 回归测试、`nq-scheduler/pom.xml`）、`nq-app`（`/__gatec/reconcile/runOnce` 新增
      `venue=BINANCE` 路由）、`docs/current/GATE_CHECKLIST.md`、`docs/gates/gate-c/SOURCES.md`
    - REST 接口口径：`POST /api/v3/order`、`DELETE /api/v3/order`、`GET /api/v3/order`、`GET /api/v3/openOrders`、
      `GET /api/v3/myTrades`
    - reconcile 口径：扫描 `SENT/ACCEPTED/PARTIALLY_FILLED/CANCEL_REQUESTED/CANCEL_REJECTED` 的 Binance 非终态订单；
      `getOrder` 对齐状态；`myTrades` 去重写 `trades`；每笔成交写 `TradeExecuted` 到 `event_store` 并复用
      `TradeLedgerGateway` 触发幂等记账
    - 运行态前置说明：当前仍是无 key 阶段，未访问真实 Binance 网络；运行态 UseCase-A（`LIMIT -> Cancel`）留到 PR-C13 / 用户提供
      key 后执行
    - 验收证据：`$env:MAVEN_OPTS='-Xmx2g'; mvn -q -f backend/pom.xml test` 通过；新增 `BinanceExchangeAdapterTest`
      覆盖下单/撤单/查单/开单列表 request 组装与结构化拒单；新增 `BinanceRestReconcileServiceTest` 覆盖
      `myTrades -> trades/event_store/ledger` 与重复 `tradeId` 去重；既有 OKX 脚本
      `pwsh -NoProfile -File scripts/gatec_okx_dome_verify.ps1 -BaseUrl http://localhost:28081 -SkipRestartPause -StartupTimeoutSec 120`
      退出码 `0`
- PR-C13：已完成 Binance Spot Testnet 最小风险运行态验收（UseCase-A）。
    - 运行环境：`NQ_BINANCE_ENV=dome`，本地 `.env` 指向 `https://testnet.binance.vision`；未使用真实盘，未提交任何
      key/secret。
    - 验收命令：
      `mvn -q -f backend/pom.xml test`
      `pwsh -NoProfile -File scripts/gatec_okx_dome_verify.ps1 -BaseUrl http://localhost:8080 -SkipRestartPause -StartupTimeoutSec 120`
      `Invoke-WebRequest -Method Post http://localhost:8080/__gatec/orders ... venue=BINANCE`
      `Invoke-WebRequest -Method Post http://localhost:8080/__gatec/orders/cancel ...`
    - UseCase-A 结果：
      `clientOrderId=bta0309110511`
      `order_id=ord-db4c67cf-0e41-4297-8f74-4f290f00a3f5`
      `orders.external_order_id=12564242`
      订单状态按事件驱动完成 `SENT -> ACCEPTED -> CANCELLED`
      `event_store` 已包含 `PlaceOrderCommand`、`OrderAck`、`CancelOrderCommand`、`CancelAck`
      `trades=0`
      `ledger_entries(相关 trace)=0`
      `audit_logs` 已记录 `ORDER_CREATED / ORDER_ACKED / ORDER_CANCELLED` 与状态迁移证据链
    - UseCase-B：本次未执行。
      Why：当前任务明确要求先完成最小风险 UseCase-A；A 通过后未继续扩大 Testnet 风险暴露。
    - 恢复门禁：本次未执行。
      Why：本次聚焦 Binance Testnet 最小风险验收；恢复门禁留待后续在更稳定的长驻进程方式下单独验证，不伪造结果。
    - 发现的问题与处理：
        1) 本地 `.env` 初始把 `NQ_BINANCE_DOME_BASE_URL` 配成了 `https://testnet.binance.vision/api`，导致 adapter 访问 `
           exchangeInfo` 时命中 `/api/api/v3/exchangeInfo
           ` 并被 404 拒绝；已仅在本地修正为 `https://testnet.binance.vision`，仓库占位符未改。
        2) 初次 LIMIT 价格过远触发 Binance `-1013 Filter failure: PERCENT_PRICE_BY_SIDE`；随后按 Testnet `exchangeInfo`
           与公开价格重算为 `BUY 35000 / 0.001 BTC`，成功获得 `OrderAck` 且未成交。
        3) 本地 `spring-boot:run` 后台进程在验收后续步骤中不够稳定；但撤单已在进程存活窗口内完成，UseCase-A
           的订单/事件/审计/零成交证据均已落库。
- PR-C13：已完成 Binance Spot Testnet 成交闭环验收（UseCase-B）。
    - 运行环境：继续使用 Binance Spot Testnet；未访问真实盘，未提交任何 key/secret。
    - 代码修复：
      `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/model/BinanceSymbolFilters.java`
      `backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceOrderTrimmerTest.java`
      Why：Binance Testnet 的 `MARKET_LOT_SIZE.stepSize=0`，旧逻辑把它当成有效步长，导致 MARKET 单在 trim 阶段直接抛出
      `increment must be positive`。现已改为在 `marketStepSize <= 0` 时回退到 `LOT_SIZE.stepSize`，并补回归测试。
    - 验收命令：
      `mvn -q -f backend/pom.xml test`
      `pwsh -NoProfile -File scripts/gatec_okx_dome_verify.ps1 -BaseUrl http://localhost:8080 -SkipRestartPause -StartupTimeoutSec 120`
      `POST /__gatec/orders`（`venue=BINANCE,type=MARKET,qty=0.0001`）
      `POST /__gatec/reconcile/runOnce`（重复 3 次，验证去重）
    - UseCase-B 结果：
      `clientOrderId=btb0309114652`
      `placeTrace=trc-binance-b-place-20260309114652`
      `order_id=ord-dd13bff3-4ef8-490f-868f-041b56a51b96`
      `orders.external_order_id=12584602`
      下单结果：`ACCEPTED`
      第 1 次 reconcile：`new_trades=1`
      第 2/3 次 reconcile：`new_trades=0`
      终态：`FILLED`
    - 关键表计数：
      `orders`：1（目标订单终态 `FILLED`）
      `trades`：1（`exchange=BINANCE`,`exchange_trade_id=2194445`）
      `ledger_entries`：2（`trace_id=trc-binance-b-place-20260309114652`，`idempotency_key` 已生成）
      `positions(account_id=2001,symbol=BTC-USDT)`：1 行，`qty=0.00353689`
      `event_store`：已包含 `PlaceOrderCommand`、`OrderAck`、`TradeExecuted`、`LedgerPosted`、`PositionUpdated`、
      `AuditRecorded`
    - 幂等/去重证据：
      重复 reconcile 两次后 `new_trades=0`
      `tradeCount` 保持 1
      `ledger_entries` 保持 2
      `audit_logs` 出现 `BINANCE_FILL_DEDUP_HIT`
    - 发现的问题与修复：
        1) 首次 MARKET 尝试被本地 trim 拒绝，根因是 `MARKET_LOT_SIZE.stepSize=0` 未回退；已修复并补回归测试。
        2) 本地 `spring-boot:run` 后台驻留不稳定，因此本次 UseCase-B 采用“单次长脚本启动应用 -> 验收 ->
           停止进程”的方式执行；这是本地运行方式问题，不影响 Binance 成交、去重、账本与持仓闭环结论。
- PR-C14：已完成 Binance Ed25519 signer 支持（最小 PR，仅补签名能力）。
    - 改动范围：`nq-adapter-binance`（`BinanceKeyType`、`BinanceApiCredentials`、`BinanceRequestSigner`、
      `BinanceHmacRequestSigner`、`BinanceEd25519RequestSigner`、`BinanceHttpClient`、runtime config/test）、`.env.example`、
      `docs/current/GATE_CHECKLIST.md`、`docs/gates/gate-c/SOURCES.md`
    - 配置键：
      `NQ_BINANCE_KEY_TYPE=hmac|ed25519`
      `NQ_BINANCE_DOME_PRIVATE_KEY`
      `NQ_BINANCE_DOME_PRIVATE_KEY_PATH`
      `NQ_BINANCE_REAL_PRIVATE_KEY`
      `NQ_BINANCE_REAL_PRIVATE_KEY_PATH`
    - 设计口径：
        1) 仅在 `nq-adapter-binance` 内补算法分发，不修改 `nq-core/nq-ledger/nq-risk`
        2) 保留现有 HMAC `API_KEY + API_SECRET` 路径不变
        3) Ed25519 私钥支持两种输入：env inline PEM 或本地文件路径；两者都不会进入日志指纹
        4) signer 配置错误统一包装为 `BINANCE_SIGNER_CONFIG_INVALID`，避免上层拿到非结构化异常
    - 验收证据：
      `mvn -q -f backend/pom.xml test` 通过
      新增 `BinanceRequestSignerTest` 覆盖 HMAC 与 Ed25519 两条签名路径
      `BinanceRuntimeConfigTest` 覆盖 `hmac|ed25519` 配置选择与缺失私钥场景
      `BinanceHttpClientTest` 覆盖 Ed25519 URL 编码签名与配置错误结构化异常
- PR-C15：已执行 Binance 实盘 Ed25519 最小风险复验（UseCase-A），结果真实失败并已留痕。
    - 运行环境：本地 `.env` 使用 `NQ_BINANCE_ENV=real`、`NQ_BINANCE_KEY_TYPE=ed25519`、
      `NQ_BINANCE_REAL_BASE_URL=https://api.binance.com`、`NQ_BINANCE_REAL_PRIVATE_KEY_PATH=<local-path>`；未提交任何真实
      key/private key。
    - 启动基线：
        1) `mvn -q -f backend/pom.xml test` 通过。
        2) 带 `.env` 注入后 `nq-app` 可正常启动在 `local`，并打印 Binance/OKX 脱敏指纹。
        3) OKX 既有链路未被本次 Binance 实盘配置破坏：应用启动后仍能完成 OKX smoke 登录与调度，不涉及任何 Binance WS。
    - 实盘 UseCase-A 输入：
      `symbol=BTC-USDT`
      `orderType=LIMIT`
      `clientOrderId=bra0309172544`
      `traceId=trc-binance-real-a-place-0309172544`
      `price=64575.53`
      `qty=0.00025`
    - 实际结果：
      订单已进入本地证据链，但被 Binance 实盘拒单，未进入 `OrderAck/CancelAck`。
      `orders.order_id=ord-cb254258-a760-4dd7-9bd1-02c41b8bc031`
      `orders.status=REJECTED`
      `orders.external_order_id` 为空
      `trades=0`
      `ledger_entries(相关 trace)=0`
    - Binance 返回：
      `reject_code=-2015`
      `reject_reason=Invalid API-key, IP, or permissions for action.`
    - event_store / audit 留痕：
        1) `event_store` 已记录 `PlaceOrderCommand`、`OrderCreated`、`RiskPassed`、`OrderReject`
        2) `audit_logs` 已记录 `ORDER_CREATED`、`ORDER_STATUS_TRANSITION(NEW->RISK_PASSED->SENT->REJECTED)`、
           `ORDER_REJECTED`
        3) 说明链路已走到 Binance 外部拒单返回，而不是本地 signer/trim 崩溃
    - 结论与修复建议：
      当前阻塞不是代码路径缺失，而是 Binance 实盘侧凭证条件未满足；首轮实盘拒单为
      `-2015 Invalid API-key, IP, or permissions for action.`。未在本次任务中修改任何业务代码，也未伪造通过结果。
- PR-C15（复验 2）：在确认 Spot Trade / IP 白名单 / Ed25519 key 绑定均正确后，再次执行 Binance 实盘 Ed25519 最小风险复验，初始
  BTC 路径仍因余额不足未通过，但阻塞已收敛为余额问题。
    - 运行方式：继续只做 `UseCase-A`，不做 MARKET、不做 WS、不改代码；本地 `.env` 保持 `NQ_BINANCE_ENV=real`、
      `NQ_BINANCE_KEY_TYPE=ed25519`、`NQ_BINANCE_REAL_PRIVATE_KEY_PATH=<local-path>`。
    - 实盘输入：
      `symbol=BTC-USDT`
      `orderType=LIMIT`
      `clientOrderId=brb0309173738`
      `traceId=trc-binance-real-a-place-retry-0309173738`
      `price=64834.12`
      `qty=0.00025`
    - 实际结果：
      `orders.order_id=ord-833c7d3c-2a66-4152-b395-f64ca5034920`
      `orders.status=REJECTED`
      `orders.external_order_id` 为空
      `trades=0`
      `ledger_entries(相关 trace)=0`
    - Binance 返回：
      `reject_code=-2010`
      `reject_reason=Account has insufficient balance for requested action.`
    - 证据链：
        1) `event_store` 已记录 `PlaceOrderCommand`、`OrderCreated`、`RiskPassed`、`OrderReject`
        2) `audit_logs` 已记录 `ORDER_CREATED`、`ORDER_STATUS_TRANSITION(NEW->RISK_PASSED->SENT->REJECTED)`、
           `ORDER_REJECTED`
        3) 说明 Ed25519 实盘请求已通过权限/签名阶段，当前阻塞收敛为账户可用余额不足，尚未进入 `OrderAck` / `CancelAck`
    - 结论与建议：
      当时无需改代码。后续只需要选择更低 `minNotional` 的 symbol 并保持最小风险 LIMIT->Cancel 路径即可。
- PR-C15（复验 3）：在现货资金补到约 `2.9U` 后，改用 `DOGE-USDT` 完成 Binance 实盘 Ed25519 最小风险复验，并真实通过。
    - Why：`BTC-USDT` 的最小可行买单名义金额明显高于当时可用余额；公开 `exchangeInfo` 显示 `DOGEUSDT` 的 `minNotional=1.0`
      ，适合继续做最小风险验证。
    - 运行方式：仍然只做 `UseCase-A`，不做 MARKET、不做 WS、不改代码；通过本地 `local + nq.gatec.verify.enabled=true`
      验收入口下单/撤单。
    - 实盘输入：
      `symbol=DOGE-USDT`
      `orderType=LIMIT`
      `clientOrderId=bre0309174403`
      `placeTraceId=trc-binance-real-doge-a-place2-0309174403`
      `cancelTraceId=trc-binance-real-doge-a-cancel2-0309174403`
      `price=0.08191`
      `qty=15`
    - 实际结果：
      `orders.order_id=ord-8fb271d4-db12-40a9-b933-ff110aa88735`
      `orders.status=CANCELLED`
      `orders.external_order_id=13975572161`
      `trades=0`
      `ledger_entries(相关 trace)=0`
    - 状态机与证据链：
        1) `audit_logs` 已记录 `NEW -> RISK_PASSED -> SENT -> ACCEPTED -> CANCEL_REQUESTED -> CANCELLED`
        2) `event_store` 已记录 `PlaceOrderCommand`、`OrderCreated`、`RiskPassed`、`OrderAck`、`CancelOrderCommand`、
           `CancelAck`
        3) `OrderAck` / `CancelAck` 均带 `venue=BINANCE`、`client_order_id=bre0309174403`、`external_order_id=13975572161`
    - 运行态观察：
        1) 撤单请求的 HTTP summary 为空，但数据库与证据链已明确表明撤单成功；这是本地验收脚本/响应摘要问题，不影响 Binance
           实盘主链路结论。
        2) 同一订单在 reconcile 中还出现过一次 `BINANCE_RECONCILE_ORDER_FAILED`（`/api/v3/order`
           超时）审计事件，但没有影响终态收敛，也没有产生重复成交或重复记账。
    - 结论：
      Binance 实盘 Ed25519 `UseCase-A` 已通过，且未破坏幂等、状态机、`event_store`、`audit_logs`、`ledger` 不变量。
- PR-BW1：已完成 Binance 私有 WS 基建与连接治理（不落业务）。
    - 改动范围：`nq-adapter-binance`（`BinanceRuntimeConfig` 扩展 WS 配置、`ws/BinanceListenKeyClient`、
      `ws/BinanceWsClient`、`ws/BinanceWsProtocol`、`ws/BinanceWsMetricsSnapshot`、对应单测）、`nq-app`（
      `BinanceWsSmokeRunner`、`ModuleWiringConfiguration`、`application*.yml`）、`.env.example`、
      `docs/current/GATE_CHECKLIST.md`、`docs/gates/gate-c/SOURCES.md`
    - listenKey 口径：
        1) 启动前通过 `POST /api/v3/userDataStream` 创建 listenKey
        2) 按 `NQ_BINANCE_LISTENKEY_REFRESH_MS` 定时执行 `PUT /api/v3/userDataStream`
        3) stop/rebuild 会 best-effort `DELETE /api/v3/userDataStream`
        4) 断线或 refresh 失败后，重连路径会重新申请 listenKey 并重建 WS 会话
    - 连接治理口径：
        1) 使用 `wss://.../ws/<listenKey>` 建立用户数据流连接
        2) 记录 `ws_connected`、`reconnect_count`、`listenkey_refresh_success_count`、
           `listenkey_refresh_fail_count`、`last_msg_age_ms`、`lastReconnectTs`
        3) 心跳巡检基于 `lastMessageTs` + ping，发现陈旧连接后按指数退避重连，避免重连风暴
        4) local smoke runner 仅做连接 + listenKey + 指标日志，默认由 `NQ_BINANCE_WS_ENABLED=false` 关闭
    - 验收命令：
      `mvn -q -f backend/pom.xml test`
      `mvn -q -f backend/pom.xml -pl nq-app spring-boot:run`
      `$env:NQ_PROFILE='local'`
      `$env:NQ_BINANCE_WS_ENABLED='true'`
      `$env:NQ_BINANCE_ENV='dome'`
      `$env:NQ_BINANCE_DOME_BASE_URL='https://testnet.binance.vision'`
      `$env:NQ_BINANCE_DOME_WS_URL='wss://stream.testnet.binance.vision/ws'`
      `$env:NQ_BINANCE_DOME_API_KEY='<local-only>'`
      `$env:NQ_BINANCE_DOME_API_SECRET='<local-only>'`
      观察 `binance_ws_connected` / `binance_ws_metrics` 日志持续 5+ 分钟；断网/断连后观察
      `binance_ws_reconnect_scheduled` 与后续 `binance_ws_session_rebuilt`
- PR-BW2：已完成 Binance 私有 WS 原始消息映射 + `event_store` 入链（不落业务表）。
    - 改动范围：`nq-adapter-binance`（`ws/BinanceWsRawMessage`、`ws/BinanceWsRawMessageListener`、
      `ws/BinanceWsEventMapper`、`ws/BinanceWsClient` 原始消息分发扩展、对应单测）、`nq-app`（
      `BinanceWsEventStoreBridge`）、`docs/current/GATE_CHECKLIST.md`、`docs/gates/gate-c/SOURCES.md`
    - 映射范围：
      `executionReport -> order.event.v1`（覆盖 `OrderAck / CancelAck / OrderReject / CancelReject`，若字段齐全则补
      `OrderPartiallyFilled / OrderFilled` 证据事件）
      `outboundAccountPosition -> position.event.v1`
      `balanceUpdate -> audit.event.v1`
    - 证据链口径：
        1) 所有 WS 映射事件统一写 `event_store`，`EventEnvelope.source=BINANCE_WS`
        2) `trace_id` 一律在 WS bridge 侧新生成，禁止伪造 REST trace
        3) `executionReport.key` 优先 `clientOrderId`，缺失时退化为 `orderId`
        4) 账户/余额消息只形成外部参考快照，不写 `positions` 业务表
    - 失败路径：
        1) 原始 payload 解析失败：写 `audit_logs(action=BINANCE_WS_PARSE_FAILED)` + `audit.event.v1`
        2) 映射异常：写 `audit_logs(action=BINANCE_WS_EVENT_MAPPING_FAILED)` + `audit.event.v1`
    - 验收证据：
      `BinanceWsEventMapperTest` 覆盖 `executionReport/outboundAccountPosition/balanceUpdate` 的 topic、`source`、
      `trace_id`、`client_order_id`、`external_order_id` 映射；
      `BinanceWsEventStoreBridgeTest` 验证原始消息进入后会调用 `EventStoreAppender.append(...)`，且解析失败进入审计路径；
      `mvn -q -f backend/pom.xml test` 通过
    - 已知限制：
        1) 本 PR 不推进状态机，不触发 `ledger/reconcile/recovery`
        2) `executionReport` 的 `TRADE` 类消息当前仅作为订单事件证据，不落 `trades`
        3) Binance 用户数据流未直接提供稳定 `accountId` 时，账户类事件 key 退化为 `BINANCE|UNKNOWN_ACCOUNT`
- PR-BW3：已完成 Binance WS-REST 协同与降级策略（WS 加速，REST 永远兜底）。
    - 改动范围：`nq-adapter-binance`（`ws/BinanceWsConnectionListener`、`ws/BinanceWsClient` 连接状态回调扩展）、
      `nq-scheduler`（`BinanceWsOrderAccelerationService`、`BinanceWsDegradeReconcileCoordinator` + 单测）、`nq-app`（
      `BinanceWsEventStoreBridge` 接入加速器、`application*.yml` 降级配置）、`.env.example`、
      `docs/current/GATE_CHECKLIST.md`
    - 协同口径：
        1) 仅加速 `OrderAck / CancelAck / OrderReject / CancelReject`，并通过 `OrderCommandService` 状态机入口推进
        2) `OrderAck / CancelAck` 允许在幂等前提下调用 `linkExternalOrderId`
        3) `executionReport` 中的 `TRADE / FILLED` 仍只保留证据，不写 `trades/ledger`
        4) WS 晚到事件若命中终态或非法顺序，只留 `audit_logs + audit.event.v1`，不回退状态
    - 断线降级口径：
        1) `listener_close / listener_error / heartbeat_stale / ping_failed` 等断线窗口触发一次受限
           `reconcileOnce(limit)`
        2) `listenKey refresh failed` 会同时写 `BINANCE_WS_LISTENKEY_EXPIRED` 审计并触发受限 reconcile
        3) `connect_failed / listenkey_create_failed` 采用阈值 + cooldown 去抖，避免重连风暴时反复扫单
    - 去重与一致性：
        1) WS 重复 `Ack/Reject` 在订单已处于目标终态或等价状态时直接幂等返回
        2) 迟到 `Ack` 不允许把 `FILLED/CANCELLED/REJECTED` 回退到 `ACCEPTED`
        3) `CancelReject` 只在 `CANCEL_REQUESTED` 语境下推进到 `CANCEL_REJECTED`；乱序消息只记证据
        4) `trades` 继续依赖 `UNIQUE(exchange, exchange_trade_id)`，`ledger` 继续依赖 `ledger_entries.idempotency_key`
    - 验收证据：
      `mvn -q -f backend/pom.xml test` 通过；
      新增 `BinanceWsOrderAccelerationServiceTest` 覆盖重复 `OrderAck` 幂等、终态保护、`CancelAck` 加速、`CancelReject` 推进；
      新增 `BinanceWsDegradeReconcileCoordinatorTest` 覆盖断线触发 reconcile、cooldown 去抖、重连失败阈值、listenKey 失效降级；
      `BinanceWsEventStoreBridgeTest` 已验证“先写 event_store，再交给加速器”。
    - 本地验收命令（dome）：
      `mvn -q -f backend/pom.xml test`
      `$env:NQ_PROFILE='local'`
      `$env:NQ_GATEC_VERIFY_ENABLED='true'`
      `$env:NQ_BINANCE_WS_ENABLED='true'`
      `$env:NQ_BINANCE_WS_DEGRADE_RECONCILE_LIMIT='20'`
      `$env:NQ_BINANCE_WS_DEGRADE_COOLDOWN_MS='5000'`
      `$env:NQ_BINANCE_WS_DEGRADE_RECONNECT_FAIL_THRESHOLD='2'`
      `mvn -q -f backend/pom.xml -pl nq-app spring-boot:run`
      运行 `UseCase-A`（`LIMIT 远离盘口 -> Cancel`）后观察订单及时推进、`trades=0`、`ledger_entries=0`；再强制断连或触发
      `NQ_BINANCE_WS_SMOKE_FORCE_RECONNECT_MS`，观察 `BINANCE_WS_DISCONNECTED / BINANCE_WS_RECONCILE_DEGRADE_COMPLETED`
      审计证据且无重复副作用
    - 已知限制：
        1) 本回合未直接读取本地敏感凭证，因此没有追加真实 dome 运行态截图或数据库计数
        2) Binance 用户数据流未提供稳定 `accountId` 时，WS 加速会回退到 `clientOrderId / externalOrderId` 匹配非终态与终态订单快照
        3) 这次不引入新的 ADR；原因是 `docs/gates/gate-c/DECISIONS.md` 的 `ADR-C10` 已覆盖“WS 只加速、REST-first、断线降级”规则

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

## 8. 是否进入 GateC-2.1（Binance WS）前置准备评估

- 当前结论：`GateC-2（Binance REST-only）已完成并冻结；若继续推进 Binance 私有 WS，只能作为下一阶段工作，且必须严格按 PR-BW1 / PR-BW2 / PR-BW3 三段拆分。`
- 已满足：
    - GateC-2（REST-only）无 key 阶段能力已完成：`PR-C10/PR-C11/PR-C12`
    - Binance Testnet `UseCase-A/B` 已通过，且 `trades/ledger/positions/event_store` 证据链完整
    - Binance 实盘 Ed25519 最小风险 `UseCase-A` 已通过：`DOGE-USDT` 单 `bre0309174403` 完成
      `SENT -> ACCEPTED -> CANCELLED`
    - `docs/current/GATE_CHECKLIST.md` 第 4 节（GateC-2）已对齐当前实现与验收结果
- GateC-2.1 进入条件：
    - `PR-C14/PR-C15` 的收尾说明、WORK 文档与 checklist 已冻结，避免 REST-only 证据链继续漂移
    - 明确遵守 `WS 只加速、REST reconcile 永远兜底`，且下一阶段不改变 GateC-2 的事实口径
    - 若确有实时性需求，再按 `PR-BW1（连接治理） -> PR-BW2（事件入链） -> PR-BW3（协同降级）` 顺序推进；不得跨 PR 混做

---

## 9. 收尾 PR 描述（PR-C14 / PR-C15）

- 标题建议：`Gate C 收尾：PR-C14 Ed25519 signer + PR-C15 Binance 实盘最小复验回填`
- 对应条目：
    - `docs/current/GATE_CHECKLIST.md` 中的 `PR-C14（无 key 阶段）`
    - `docs/current/GATE_CHECKLIST.md` 中的 `Binance 实盘最小复验（Ed25519，LIMIT -> Cancel）通过`
- PR-C14 范围：
    - `nq-adapter-binance` 的 `BinanceKeyType`、`BinanceApiCredentials`、`BinanceRequestSigner`、
      `BinanceHmacRequestSigner`、`BinanceEd25519RequestSigner`、`BinanceHttpClient`、`BinanceRuntimeConfig`
    - 对应单测：`BinanceRequestSignerTest`、`BinanceRuntimeConfigTest`、`BinanceHttpClientTest`
    - 配套文档：`.env.example`、`docs/gates/gate-c/SOURCES.md`
- PR-C14 结论：
    - 已补齐 `NQ_BINANCE_KEY_TYPE=hmac|ed25519`
    - 已补齐 `PRIVATE_KEY / PRIVATE_KEY_PATH` 配置读取
    - 已保持 HMAC 路径不回归，配置错误统一落成 `BINANCE_SIGNER_CONFIG_INVALID`
- PR-C15 范围：
    - 只做 Binance 实盘 Ed25519 最小风险 `UseCase-A`，不做 MARKET、不做 WS、不改业务代码
    - 文档留痕位于 `docs/current/GATE_CHECKLIST.md` 与本文件 `PR-C15 / 复验 2 / 复验 3`
- PR-C15 关键证据：
    - 实盘成功单：`DOGE-USDT`
    - `clientOrderId=bre0309174403`
    - `orders.order_id=ord-8fb271d4-db12-40a9-b933-ff110aa88735`
    - `orders.external_order_id=13975572161`
    - 终态：`CANCELLED`
    - `trades=0`
    - `ledger_entries(相关 trace)=0`
- PR-C15 证据链：
    - `event_store` 已记录 `PlaceOrderCommand`、`OrderCreated`、`RiskPassed`、`OrderAck`、`CancelOrderCommand`、`CancelAck`
    - `audit_logs` 已记录 `NEW -> RISK_PASSED -> SENT -> ACCEPTED -> CANCEL_REQUESTED -> CANCELLED`
- 不变量证明：
    - 未改动 `nq-core/nq-ledger/nq-risk`
    - 订单状态仍经由现有事件/状态机入口推进，没有直接 update DB
    - REST-only 主链路未被 Binance Ed25519 接入破坏
    - 实盘最小复验下 `trades=0`、`ledger=0`，未产生额外副作用
- 边界说明：
    - 本收尾 PR 不包含 Binance WS（GateC-2.1）
    - 本收尾 PR 不包含 Binance 实盘 MARKET 成交复验
    - 本收尾 PR 不包含任何真实 key/private key；本地 `.env` 仅用于运行态验证，不入库
