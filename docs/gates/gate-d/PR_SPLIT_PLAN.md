# GateD PR_SPLIT_PLAN

> GateD 提交拆分计划。  
> 原则：单个 PR 只解决一类边界问题，做到能 review、能回滚、能定位。
> 状态约定：`[x] 已完成`、`[~] 进行中`、`[ ] 未开始`。  
> 当前状态基线：**截至 2026-03-15 的已实现与已验证事实**。

---

## 当前推进状态

- [x] PR-1：文档与阶段入口对齐
- [x] PR-2：contracts / core 执行入口收敛
- [x] PR-3：pre-trade 风控规则链
- [x] PR-4：状态机、事件与执行回执收敛
- [x] PR-5：scheduler / recovery / reconcile / degrade 收敛
- [x] PR-6：ledger / projection / db schema
- [x] PR-7：app / api 验收入口与查询视图
- [x] PR-8：integration tests / freeze docs

---

## PR-1：文档与阶段入口对齐（已完成）

### 目标
- 修正 GateD 阶段定义
- 建立 GateD 完整文档目录
- 同步更新 `AGENTS.md`、`README.md`、`docs/current/*`

### 涉及文件
- `AGENTS.md`
- `README.md`
- `docs/current/*`
- `docs/gates/gate-d/*`
- `docs/ROADMAP.md`
- `docs/gates/gate-b/ROADMAP.md`
- `docs/gates/gate-c/ROADMAP.md`

### 不包含
- 任何核心代码逻辑改造

---

## PR-2：contracts / core 执行入口收敛（已完成）

### 目标
- 统一执行应用服务
- 收敛 place / cancel / ack / reject / trade-report / query-confirm 入口
- 明确 core 与 adapter / scheduler / ledger 边界

### 涉及模块
- `nq-core`
- `nq-contracts`
- `nq-adapter-api`

### 不包含
- 风控规则实现
- Flyway 迁移
- 大规模恢复逻辑重写

---

## PR-3：pre-trade 风控规则链（已完成）

### 目标
- 从 `NoopRiskGate` 过渡到规则链
- 引入 rule registry、拒绝码、统一返回模型

### 涉及模块
- `nq-risk`
- `nq-core`
- 文档：`RISK_RULES.md`

### 不包含
- 多交易所深扩边
- 复杂组合风控

---

## PR-4：状态机、事件与执行回执收敛（已完成）

### 目标
- 冻结订单状态机
- 明确本地状态与外部事实状态
- 收敛重复回报、乱序回报、终态保护

### 涉及模块
- `nq-core`
- `nq-adapter-api`
- `nq-adapter-okx`
- `nq-adapter-binance`
- 文档：`STATE_MACHINE.md`

---

## PR-5：scheduler / recovery / reconcile / degrade 收敛（已完成）

### 目标
- scheduler 瘦身
- 明确 reconcile、recovery、query-confirm、degrade 的职责与调用关系
- 补充运行与排障手册

### 涉及模块
- `nq-scheduler`
- `nq-core`
- `nq-observability`
- 文档：`COMPENSATION_SYNC.md`、`RECOVERY_RUNBOOK.md`

---

## PR-6：ledger / projection / db schema（已完成）

### 目标
- fills 去重
- ledger posting 幂等
- position / account snapshot 持久化与投影增强
- 新增 GateD 迁移脚本

### 涉及模块
- `nq-ledger`
- `nq-infra`
- 文档：`DB_SCHEMA.md`、`NUMERIC_POLICY.md`

---

## PR-7：app / api 验收入口与查询视图（已完成）

### 目标
- 建立 GateD 最小验收入口
- 收敛阶段性接口与正式查询入口
- 完成最小本地验证闭环

### 涉及模块
- `nq-app`
- `nq-api`

### 当前进展
- `__gated` canonical 验收入口、order/trade/position/account 本地闭环已完成
- OKX 验收脚本已改为 canonical non-fallback 启动路径，并显式支持 `.env -> NQ_OKX_ENV=dome|real -> NQ_OKX_API_*` 统一运行时变量映射
- `serviceBaseUrl` 已从旧 `http://localhost:28081` 收口为 `-BaseUrl -> NQ_GATED_SERVICE_BASE_URL / NQ_APP_BASE_URL -> http://localhost:${NQ_APP_PORT|18888}`，health timeout 已被排除
- 脚本 `accountId` 已收口为 `-AccountId -> NQ_GATED_ACCOUNT_ID / NQ_OKX_VERIFY_ACCOUNT_ID / NQ_ACCOUNT_ID -> 1001`，官方脚本默认不再写死旧 `2001`
- 本次官方脚本在 `verifyAccountId=1001` 下已拿到真重启真实样本：
  - UseCase-A：`place=200 / cancel=200 / reconcile=200(new_trades=0) / order=200(CANCELLED) / trade=404`
  - UseCase-B：`place=200 / reconcile=200(new_trades=2) / order=200(FILLED) / trade=200`
  - UseCase-C：`place=200 / recovery=200(processed_events=2, processed_ledger=0, invalid_transitions=0) / reconcile=200(new_trades=0) / cancel=200 / order=200(CANCELLED) / trade=404`
- 说明：`UC-D9` 的最小 `LIMIT -> cancel` 与真重启后的 `recovery / reconcile / cancel / query` 已取得官方脚本正向样本；UseCase-B 的 `reconcile new_trades=2 / trade=200` 已通过 DB 明细解释为同一订单下两条不同 `exchange_trade_id` 的真实成交，不再是待解释主阻塞。2026-03-14 新增独立 place-timeout probe（`BTC-USDT / price=10000 / quantity=0.00005`）并在 `-ForcePlaceTimeoutOnce` 下真实命中 `okx_force_timeout_place_once_enabled / consumed / throwing_http_timeout / okx_query_confirm_place_started / okx_query_confirm_place_resolved(strategy=getOrder)`，对应 probe 订单先 `ACCEPTED` 后 cleanup cancel 至 `CANCELLED`、`trades=0`。同日晚些时候继续收口 UseCase-B：先用 `BTC-USDT MARKET BUY 0.00001` 真实命中 `51020`（最小下单额不足），确认 `51008` 余额噪音已被替换为可解释约束；随后将 B 收口为 `BTC-USDT MARKET SELL 0.00002`，订单 `g6b0314135817 / external_order_id=3388806192184385536` 经 reconcile 对齐为 `FILLED / reason=RECONCILE_STATUS_ALIGN`。库内 `trades / ledger_entries` 对该单仍为 0 行，但外部余额已由 `BTC 0.000380993976 / USDT 0.9988651685332477` 变为 `BTC 0.000360993976 / USDT 2.4147938225332477`，说明 B 已从“余额噪音样本”收口为真实成交样本，剩余现象转为 trade/ledger 同步缺口。2026-03-14 最新定位批进一步确认：当前断点不在 B 参数，而在 `OkxRestReconcileService` 的 fills 同步链。该服务在同一轮 `reconcileSingleOrder(...)` 中先 `alignOrderStatus(... FILLED ...)` 再只调用一次 `reconcileFills(...)`；若这一次 `listFills(...)` 返回空，订单已成 `FILLED` 终态，而 `reconcileOnce / OkxRecoveryService` 后续只扫非终态订单，`OkxWsEventMapper` 也只把 filled 证据写入 `event_store`、不会补 `trades / ledger_entries`。因此 `g6b0314135817` 当前呈现为 `orders=FILLED / RECONCILE_STATUS_ALIGN` 且 `trades=0 / ledger_entries=0 / event_store` 无 `TradeExecuted / LedgerPosted`，更符合“终态后无后续补扫者的同步缺口”，不是简单窗口延迟。因此 PR-7 不再以“query-confirm timeout 分支缺真实样本”为主阻塞，后续 remaining work 收敛为 checklist 冻结口径、Paper / Binance 验收与其余文档同步。 2026-03-14 最新最小修复批已在 `OkxRestReconcileService` 增加 `venue=OKX + status=FILLED + external_order_id 非空 + trades 不存在` 的补扫条件；官方脚本最新 B 样本 `g6b0314144706 / ord-35fbbfcc-25c8-4974-8de4-2d1146606ac9 / external_order_id=3388904470867566593` 先记录 `OKX_RECONCILE_COMPLETED(new_trades=0)`，随后在同一脚本窗口内由补扫记录 `OKX_FILLED_ORDER_FILL_BACKFILL_COMPLETED(new_trades=1)`，并落出 `trades(exchange_trade_id=976910311)`、4 条 `ledger_entries`、`TradeExecuted` 与 `LedgerPosted`；A/C 继续保持 `CANCELLED` 且 `trade_count=0`，当前未观察到重复成交、重复记账、状态回退。 2026-03-14 继续收口 `ledger_reconcile_diff / LEDGER_MISSING`：真实样本显示 `account_snapshots(account_id=1001,currency=BTC,balance=0.00994000)` 与 `positions(BTC-USDT).qty=0.00994000` 完全一致，而 `ledger_entries` 对同账户只存在 `USDT` 分录，说明这条告警并非当前账本漏写，而是“position-backed base snapshot 被 ledger 对账 SQL 误判”为 `LEDGER_MISSING`。随后在 `JdbcLedgerReconcileRepository` 的 `LEDGER_MISSING` 分支排除了可被 `positions` 聚合解释的 base 资产快照后，最新 `LEDGER_RECONCILE` 已记录 `RECONCILE_MATCH(diff_count=0)`，因此它不再视为 GateD 主阻塞。

---

## PR-8：integration tests / freeze docs（已完成）

### 目标
- 跑通 GateD 用例
- 更新 `WORK.md`
- 形成冻结结论

### 最终收口结论（截至 2026-03-15）
- `PR-7` 已不再被 real OKX 主链阻断；place / cancel query-confirm、UseCase-B `trades / ledger`、以及 `LEDGER_MISSING` 误报都已收口
- `UC-D1 / Paper LIMIT -> cancel` 已于 2026-03-15 取得最小真样本
- `UC-D10 / Binance LIMIT -> cancel` 已于 2026-03-15 取得最小真样本
- `mvn -q -f backend/pom.xml test` 与 `mvn -q -f backend/pom.xml verify` 已通过
- Flyway 新库 init 与 `V3 -> V4` 老库 upgrade 已通过
- freeze docs 已完成，GateD 当前状态为“已冻结，GateE 待启动”
- 深层兼容债务、指标完善，以及 Binance background reconcile 审计噪音不再阻塞 GateD 主线冻结判断，可顺延到 GateE 或后续治理批

### 涉及模块
- `nq-app`
- `nq-core`
- `nq-risk`
- `nq-scheduler`
- `nq-ledger`
- 文档：`TEST_CASES.md`、`GATE_D_CHECKLIST.md`、`WORK.md`

---

## PR 通用要求

每个 PR 都必须：

- 标注对应 checklist 条目
- 说明不包含的范围
- 写出验证方式
- 若改动契约 / 状态机 / DB / 恢复逻辑，必须同步更新文档
- 不接受“文档之后补”的口头承诺




