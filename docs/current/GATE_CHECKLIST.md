# GateD Checklist（唯一验收入口）

> 当前阶段：**GateD（统一执行闭环与执行域硬化）**。  
> 本文件是 GateD 的唯一验收入口。历史 Gate 的失败记录与通过记录保留在各自 `docs/gates/gate-*/WORK.md`，不再混入本文件。  
> 状态约定：`[x] 已完成`、`[~] 部分完成`、`[ ] 未完成`。  
> 状态基线：**截至 2026-03-13 的已实现与已验证事实**。

---

## 0. 基础门禁（必须）

- [ ] `mvn -q -f backend/pom.xml test` 全绿
- [ ] `docker compose up -d postgres` 成功
- [x] `nq-app` 可在 `local` profile 启动并返回 health `UP`
- [x] GateD 验收入口已就绪且仅在 `local + gate verify enabled` 下暴露
- [~] 当前 `.env` 与 profile 能明确区分 `paper / okx-dome / okx-real / binance-dome / binance-real`

---

## 1. 文档与阶段边界（必须）

- [x] `docs/current/README.md` 已切换到 GateD 定义
- [x] `docs/current/GATE_CHECKLIST.md` 已清理 GateC 历史叠层内容，仅保留 GateD 门禁
- [x] `docs/gates/gate-d/` 文档已完整建档
- [x] `docs/gates/gate-d/DECISIONS.md` 已建立并开始维护
- [x] `docs/gates/gate-d/EVOLUTION_RULES.md` 已建立并生效
- [x] `docs/gates/gate-d/NUMERIC_POLICY.md` 已建立并落地到实现
- [x] `docs/gates/gate-d/PR_SPLIT_PLAN.md` 已建立并作为提交边界依据
- [x] `docs/gates/gate-d/RECOVERY_RUNBOOK.md` 已建立并可支撑恢复排障
- [x] `docs/ROADMAP.md`、`docs/gates/gate-b/ROADMAP.md`、`docs/gates/gate-c/ROADMAP.md` 中 GateD 定义已统一为“执行闭环与执行域硬化”
- [x] `AGENTS.md` 与 `README.md` 已对齐 GateD
- [x] current / top-level navigation / archive 三类文档边界已建立

---

## 2. 统一执行入口（必须）

- [~] `nq-core` 形成统一执行应用服务，覆盖 `place / cancel / query-confirm / acknowledge / reject / trade-report`
- [x] `OrderCommandService` 职责已收敛，不再无边界堆叠业务
- [x] `AdapterRouter` 继续作为 venue 路由入口，core 不依赖具体 adapter 实现类
- [~] controller / scheduler 不再自行推进订单状态，统一经 core 入口

---

## 3. 订单状态机硬化（必须）

- [~] 状态机文档已冻结：本地状态、外部事实状态、终态定义、非法回退规则
- [~] place / cancel / ws-ack / rest-reconcile / recovery 的状态推进都经过统一状态机入口
- [~] 禁止状态回退、禁止重复终态覆盖、禁止相同事件造成脏写
- [~] `external_order_id` 绑定与状态推进解耦，允许先 bind 后推进，也允许先推进后 bind，但都必须可审计

---

## 4. pre-trade 风控硬化（必须）

- [x] `nq-risk` 已从 `NoopRiskGate` 过渡到规则链实现
- [x] 至少具备以下规则：
    - [x] 交易开关
    - [x] 账户可交易校验
    - [x] symbol 允许校验
    - [x] 精度校验
    - [x] 最小名义金额校验
    - [x] 最大下单额校验
    - [x] 重复请求拦截
    - [x] 限频拦截
- [x] 风控拒绝返回标准化 `ruleCode / rejectReason / hardReject`
- [~] 风控结果写入 `audit_logs` 与 `event_store`

---

## 5. 统一 adapter 契约（必须）

- [x] `nq-adapter-api` 已冻结 GateD 执行契约
- [~] place / cancel / query / list-open-orders / list-fills / account-snapshot / position-snapshot 契约清晰
- [~] 交易所状态映射已统一归口到 adapter 层
- [~] `nq-core / nq-risk / nq-ledger / nq-scheduler` 无交易所方言分支

---

## 6. 补偿与同步（必须）

- [~] `nq-scheduler` 只负责 job 调度、窗口扫描、恢复编排
- [~] reconcile 对非终态订单执行 `query order + pull fills + projection sync`
- [~] recovery 可在启动或手工触发时重新收敛非终态订单与未完成投影
- [~] WS 断连 / 登录失效 / 订阅异常会触发一次受限 REST 兜底
- [x] 禁止在补偿链路中直接盲重试下单
- [~] query-confirm 规则有文档、有日志、有验收用例

---

## 7. trade / ledger / position / account 联动（必须）

- [~] fills 去重生效（`exchange_trade_id` 或等价键唯一）
- [x] 每笔 fill 只触发一次 ledger posting
- [x] ledger posting 幂等键有效
- [x] position projection 可见且无重复叠加
- [x] account snapshot 同步路径清晰，至少支持本地 PAPER 产出与查询
- [~] ledger 或 projection 失败路径会写事件与审计

---

## 8. Paper 与真实 venue 双通道（必须）

- [x] Paper executor / adapter 与真实 venue 走统一执行接口
- [x] Paper 支持 LIMIT -> cancel
  2026-03-15 已完成最小 UC-D1 真样本：`POST /__gated/orders(place LIMIT BUY BTC-USDT @ 10 x 0.001, venue=PAPER)` 返回 `200 ACCEPTED`，随后 `GET /__gated/orders/{orderId}` 为 `ACCEPTED`、`GET /__gated/orders/{orderId}/trade` 为 `404`；执行 `POST /__gated/orders/cancel` 后，订单收敛为 `CANCELLED`，再次查询订单仍为 `CANCELLED`、trade 仍为 `404`。库内对应 `orders(order_id=ord-32cd0786-cf03-4f8e-9a83-559ef116f345,status=CANCELLED,external_order_id=paper-ord-32cd0786-cf03-4f8e-9a83-559ef116f345)`，`trades / ledger_entries / positions / account_snapshots` 均为 0 行；`event_store` 仅有 `PlaceOrderCommand / OrderCreated / RiskPassed / OrderAck / CancelOrderCommand / OrderStatusChangedPayload / CancelAck`，未出现 `TradeExecuted / LedgerPosted`。由于 `GateDAcceptanceController.runReconcile(...)` 目前只支持 `OKX / BINANCE`，Paper 本批只验证 `place -> query -> cancel -> query -> trade -> recovery`，其中 `recoveryRunOnce` 返回 `processed_events=0 / processed_ledger=0 / invalid_transitions=0`，未观察到重复成交、重复记账、状态回退。
- [x] Paper 支持 MARKET -> fill
- [x] OKX 至少完成最小 LIMIT -> cancel 验证
  当前官方脚本已把 `accountId` 解析规则收口为 `-AccountId -> NQ_GATED_ACCOUNT_ID / NQ_OKX_VERIFY_ACCOUNT_ID / NQ_ACCOUNT_ID -> 1001`，默认不再写死旧 `2001`。在 `serviceBaseUrl=http://localhost:18888`、`verifyAccountId=1001`、`okxEnv=real` 下，官方脚本已完成不带 `-SkipRestartPause` 的真重启样本：
  - UseCase-A：`place=200 / cancel=200 / reconcile=200(new_trades=0) / order=200(CANCELLED) / trade=404`
  - UseCase-B：`place=200 / reconcile=200(new_trades=2) / order=200(FILLED) / trade=200`
  - UseCase-C：`place=200 / recovery=200(processed_events=2, processed_ledger=0, invalid_transitions=0) / reconcile=200(new_trades=0) / cancel=200 / order=200(CANCELLED) / trade=404`
  结论：最小 `LIMIT -> cancel` 与真重启后 `recovery / reconcile / cancel / query` 样本均已取得；当前 `trade=404` 只在未成交的取消路径（A/C）稳定出现，已成交的 MARKET 样本（B）能返回 `trade=200`。UseCase-B 的 `new_trades=2` 已通过库内 `orders / trades / ledger_entries` 明细核对：同一 `external_order_id=3385560659240116224` 下存在两条不同 `exchange_trade_id`（`1189586011`、`1189586012`）的真实成交，以及各自独立的 ledger idempotency key，当前更符合“交易所真实拆单”而非重复写入。2026-03-14 在 real 账户 `USDT availBal=0.9988651685332477`、`BTC-USDT state=live / tickSz=0.1 / lotSz=0.00000001 / minSz=0.00001` 下，官方脚本已把 A/C 的 LIMIT 样本从 `price=10000 / quantity=0.0002` 收口为 `price=10000 / quantity=0.00005`；同日又新增独立 place-timeout probe（`BTC-USDT / price=10000 / quantity=0.00005`），并在 `-ForcePlaceTimeoutOnce` 下真实命中 `okx_force_timeout_place_once_enabled / consumed / throwing_http_timeout / okx_query_confirm_place_started / okx_query_confirm_place_resolved(strategy=getOrder)`。对应 probe 订单 `g6p0314124337 / external_order_id=3388655881851461632` 先收敛为 `ACCEPTED`，随后 cleanup cancel 收敛到 `CANCELLED`，`trades=0`。同日晚些时候继续收口 UseCase-B：先用 `BTC-USDT MARKET BUY 0.00001` 真实命中 `51020`（最小下单额不足），确认 `51008` 余额噪音已被替换为可解释约束；随后将 B 收口为 `BTC-USDT MARKET SELL 0.00002`，订单 `g6b0314135817 / external_order_id=3388806192184385536` 经 reconcile 对齐为 `FILLED / reason=RECONCILE_STATUS_ALIGN`。库内 `trades / ledger_entries` 对该单仍为 0 行，但外部余额已由 `BTC 0.000380993976 / USDT 0.9988651685332477` 变为 `BTC 0.000360993976 / USDT 2.4147938225332477`，说明 B 已从“余额噪音样本”收口为真实成交样本，剩余现象转为 trade/ledger 同步缺口。2026-03-14 最新定位批进一步确认：当前断点不在 B 参数，而在 `OkxRestReconcileService` 的 fills 同步链。该服务在同一轮 `reconcileSingleOrder(...)` 中先 `alignOrderStatus(... FILLED ...)` 再只调用一次 `reconcileFills(...)`；若这一次 `listFills(...)` 返回空，订单已成 `FILLED` 终态，而 `reconcileOnce / OkxRecoveryService` 后续只扫非终态订单，`OkxWsEventMapper` 也只把 filled 证据写入 `event_store`、不会补 `trades / ledger_entries`。因此 `g6b0314135817` 当前呈现为 `orders=FILLED / RECONCILE_STATUS_ALIGN` 且 `trades=0 / ledger_entries=0 / event_store` 无 `TradeExecuted / LedgerPosted`，更符合“终态后无后续补扫者的同步缺口”，不是简单窗口延迟。因此 place / cancel 两侧的 query-confirm 真实样本都已补齐；当前剩余 gap 不再是 real OKX query-confirm 样本缺失，而是 checklist 冻结口径、Paper / Binance 未完项与其他 GateD 收尾项。 2026-03-14 最新最小修复批已在 `OkxRestReconcileService` 增加 `venue=OKX + status=FILLED + external_order_id 非空 + trades 不存在` 的补扫条件；官方脚本最新 B 样本 `g6b0314144706 / ord-35fbbfcc-25c8-4974-8de4-2d1146606ac9 / external_order_id=3388904470867566593` 先记录 `OKX_RECONCILE_COMPLETED(new_trades=0)`，随后在同一脚本窗口内由补扫记录 `OKX_FILLED_ORDER_FILL_BACKFILL_COMPLETED(new_trades=1)`，并落出 `trades(exchange_trade_id=976910311)`、4 条 `ledger_entries`、`TradeExecuted` 与 `LedgerPosted`；A/C 继续保持 `CANCELLED` 且 `trade_count=0`，当前未观察到重复成交、重复记账、状态回退。 2026-03-14 继续收口 `ledger_reconcile_diff / LEDGER_MISSING`：真实样本显示 `account_snapshots(account_id=1001,currency=BTC,balance=0.00994000)` 与 `positions(BTC-USDT).qty=0.00994000` 完全一致，而 `ledger_entries` 对同账户只存在 `USDT` 分录，说明这条告警并非当前账本漏写，而是“position-backed base snapshot 被 ledger 对账 SQL 误判”为 `LEDGER_MISSING`。随后在 `JdbcLedgerReconcileRepository` 的 `LEDGER_MISSING` 分支排除了可被 `positions` 聚合解释的 base 资产快照后，最新 `LEDGER_RECONCILE` 已记录 `RECONCILE_MATCH(diff_count=0)`，因此它不再视为 GateD 主阻塞。
- [ ] Binance 至少完成最小 LIMIT -> cancel 验证
- [~] Paper / OKX / Binance 的返回模型在 core 层一致

---

## 9. 可观测性（必须）

- [~] 日志字段统一：`trace_id、request_id、client_order_id、external_order_id、account_id、symbol、venue`
- [ ] 至少具备以下指标：
    - [ ] 下单成功率
    - [ ] 风控拒绝次数
    - [ ] reconcile 触发次数
    - [ ] recovery 修正次数
    - [ ] WS degrade 次数
    - [ ] 重复回执 / 重复成交拦截次数
- [~] 能以 `trace_id` 追完整个执行闭环

---

## 10. 数据库迁移（必须）

- [ ] GateD 新迁移脚本已创建，例如 `V5__gate_d_execution_closure.sql`
- [~] 新环境可完整初始化
- [ ] 老环境可平滑升级
- [~] 关键索引已补齐：`idempotency_key / client_order_id / external_order_id / exchange_trade_id / trace_id`
- [~] 不破坏 GateA / GateB / GateC 已有数据

---

## 11. 最小验收用例（必须全部通过）

- [x] UC-D1：paper LIMIT -> cancel
  2026-03-15 已用 `trace_id=trc-paper-ucd1-20260315-1024` 跑通最小验收链路：`place=200(ACCEPTED) -> order=200(ACCEPTED) -> trade=404 -> cancel=200(CANCELLED) -> order=200(CANCELLED) -> trade=404 -> recovery=200(processed_events=0, processed_ledger=0, invalid_transitions=0)`。DB 证据为 `orders=CANCELLED`、`trades=0`、`ledger_entries=0`、`positions=0`、`account_snapshots=0`，`event_store` 仅出现 `OrderAck / CancelAck` 等未成交事件；说明 Paper LIMIT 未撮合样本的取消主链已闭环，且未引入重复成交、重复记账、状态回退。`Paper / OKX / Binance` 返回模型一致性仍保持 `[~]`，剩余缺口收敛到 Binance 最小验收与 PR-8 关门批。
- [x] UC-D2：paper MARKET -> fill
- [x] UC-D3：精度非法被风控拒绝
- [x] UC-D4：最小名义金额不足被风控拒绝
- [x] UC-D5：重复 idempotency key 被拦截
- [~] UC-D6：reconcile 能修正非终态订单
- [~] UC-D7：recovery 能在重启后恢复执行状态
- [~] UC-D8：WS 断连后触发受限 REST 兜底且不重复成交
- [x] UC-D9：OKX 最小 LIMIT -> cancel 通过
  当前官方脚本已完成 `accountId` 收口，解析规则为 `-AccountId -> NQ_GATED_ACCOUNT_ID / NQ_OKX_VERIFY_ACCOUNT_ID / NQ_ACCOUNT_ID -> 1001`；在不显式传参的情况下，脚本本次实际输出 `verifyAccountId=1001`，并得到：
  - UseCase-A：`place=200 / cancel=200 / reconcile=200(new_trades=0) / order=200(CANCELLED) / trade=404`
  - UseCase-B：`place=200 / reconcile=200(new_trades=2) / order=200(FILLED) / trade=200`
  - UseCase-C：`place=200 / recovery=200(processed_events=2, processed_ledger=0, invalid_transitions=0) / reconcile=200(new_trades=0) / cancel=200 / order=200(CANCELLED) / trade=404`
  结论：官方脚本已不再被 `accountId=2001` 的假失败阻断，`UC-D9` 的最小 `LIMIT -> cancel` 路径与真重启恢复路径都已拿到正向样本；2026-03-14 又补齐了 real OKX 的可撤样本收口证据：A/C 的 LIMIT 参数已收口为 `price=10000 / quantity=0.00005`，place 能稳定进入 `ACCEPTED`，cancel 能稳定进入 adapter，并在 A 样本命中 `okx_force_timeout_cancel_once_consumed -> okx_query_confirm_cancel_started -> okx_query_confirm_cancel_resolved`，最终 A/C 都为 `CANCELLED` 且 `trades=0`。同日新增的独立 place-timeout probe 也已真实命中 `okx_force_timeout_place_once_consumed -> okx_query_confirm_place_started -> okx_query_confirm_place_resolved(strategy=getOrder)`，说明 real OKX 的 place / cancel 两侧 query-confirm 样本均已闭环。当前 `trade=404` 也已被更精确地验证为“取消且未成交路径的稳定结果”，不是所有真实样本都返回 `404`；UseCase-B 的 `reconcile new_trades=2` 也已通过 DB 明细确认为两条不同 `exchange_trade_id` 的真实成交，而不是直接可见的重复成交/重复记账。
- [ ] UC-D10：Binance 最小 LIMIT -> cancel 通过

---

## 12. GateD 冻结条件

以下条件全部满足，GateD 才允许冻结：

- [x] 文档齐全并对齐代码
- [~] 最小执行闭环稳定
- [x] 风控硬规则生效
- [~] 补偿链路可收敛
- [~] Paper 与真实 venue 契约统一
- [ ] 测试与验收全通过
- [~] `docs/gates/gate-d/WORK.md` 已写明完成项、遗留项、下一 Gate 输入项

截至 2026-03-15，真实 OKX 主验收通道、`ledger_reconcile_diff / LEDGER_MISSING` 误报与 `UC-D1 / Paper LIMIT -> cancel` 都已收口；当前冻结阻塞项已明确收敛为两类：`Binance 最小 LIMIT -> cancel / UC-D10`，以及 `mvn test + Flyway init/upgrade + freeze docs` 这组工程门禁与迁移冻结口径。深层兼容债务、指标完善、Binance 深度齐平不再建议继续阻塞 GateD 主线冻结判断。






