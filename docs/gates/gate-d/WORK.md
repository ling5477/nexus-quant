# GateD WORK
# GateD 工作记录

## 1. 开工背景

当前仓库已经完成 GateC：多交易所接入、REST / WS 基座、reconcile / recovery、paper matching、ledger posting 基础存在。

GateD 开工的原因不是“继续接功能”，而是：
- 旧文档把 GateD 错定义成研究 / 回测，已与现状冲突
- `docs/current/*` 仍停留在 GateC
- `nq-core / nq-risk / nq-scheduler` 的执行域边界仍待收敛
- `docs/gates/gate-d/` 目录已存在但尚未正式立卷

## 2. 本轮文档目标
- 正式建立 GateD 文档目录
- 统一 README / AGENTS / current docs / roadmap 的阶段定义
- 给出模块改造说明与 checklist
- 补齐 GateD 的决策、演化规则、数值规范、PR 拆分、恢复手册

## 3. 后续代码目标
- 收敛 nq-core 执行入口
- 建立 nq-risk 规则链
- 瘦身 nq-scheduler
- 冻结 nq-adapter-api 契约
- 补齐 GateD Flyway 迁移与验收入口

## 4. 遗留项
- 需要结合实际代码进一步补 ADR
- 需要在代码提交后回填验证证据
- 需要在具体 PR 中补外部官方接口依据

## 5. 下一步输入
- `docs/current/GATE_CHECKLIST.md`
- `docs/gates/gate-d/MODULES.md`
- `docs/gates/gate-d/CONTRACTS.md`
- 目标代码模块现状

## 6. 本轮新增文档
- `DECISIONS.md`
- `EVOLUTION_RULES.md`
- `NUMERIC_POLICY.md`
- `PR_SPLIT_PLAN.md`
- `RECOVERY_RUNBOOK.md`

## 7. 入口联动修订
以下入口文档需同步感知以上新增文档：
- `AGENTS.md`
- `README.md`
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/gates/gate-d/README.md`
- `docs/gates/gate-d/GATE_D_CHECKLIST.md`

## 8. 2026-03-12 第一批代码收敛
- `nq-risk`：已从 `NoopRiskGate` 升级为 `PreTradeRiskService + RiskRuleRegistry`，首批规则包含：
  - `KillSwitchRiskRule`
  - `AccountTradingEnabledRule`
  - `SymbolEnabledRule`
  - `OrderPrecisionRule`
  - `MinNotionalRule`
  - `MaxOrderAmountRule`
  - `DuplicateRequestRule`
  - `RateLimitRule`
- `nq-core`：新增 `OrderLifecycleService`，把 scheduler 可见的状态推进能力收口为显式生命周期动作
- `nq-scheduler`：OKX 主验收通道的 `reconcile / recovery / ws acceleration` 已改为调用 `OrderLifecycleService`
- 测试：
  - `nq-risk` 新增规则链回归测试
  - `nq-scheduler` OKX 相关测试已同步更新

## 9. 本轮偏差记录
- `PlaceOrderCommand` 文档声明包含 `venue`，但在模块定向构建未联动依赖时暴露出 contracts 构件签名漂移风险
- 为保证第一批风控最小可合并，本轮暂采用以下兼容策略：
  - `DuplicateRequestRule` 先按 `accountId + clientOrderId` 做窗口拦截
  - `SymbolEnabledRule` 先按全局 symbol allow-list 校验
- 该偏差不改变 GateD 主目标，但需要在后续 `contracts/core` PR 中补齐契约统一

## 10. 本轮验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-risk,nq-core,nq-scheduler -am test`
- 结果：
  - 通过
- 备注：
  - 构建输出存在 SLF4J provider 缺失与 Mockito agent 预警，但不影响本轮测试通过

## 11. 2026-03-12 第二批代码收敛
- `nq-contracts`：
  - `PlaceOrderCommand / CancelOrderCommand` 已补齐 `requestId` 等强语义字段
  - `PlaceOrderCommand` 已补齐 `idempotencyKey / quantity / source`
  - 通过兼容构造器保持旧调用点可运行，避免第二批 PR 被机械签名改动淹没
- `nq-core`：
  - `PlaceOrderRequest / CancelOrderRequest` 已补齐 GateD 契约所需核心字段语义
  - 新增 `ExecutionCommandMapper`，把 contracts 组装从 `OrderCommandService` 中拆出
  - `OrderCommandService` 继续负责 place / cancel 编排；`OrderLifecycleService` 继续负责语义化生命周期动作
  - `transitionOrder(...)` 已收口为 core 包内可见，不再向 scheduler 暴露通用迁移入口
- `nq-risk`：
  - `DuplicateRequestRule` 已从 `accountId + clientOrderId` 升级为 `accountId + idempotencyKey`
- `nq-scheduler`：
  - Binance 的 `reconcile / ws acceleration` 已迁移到 `OrderLifecycleService`
  - Paper matching 已改为只通过 `markFilled(...)` 触发有限生命周期动作
  - 至此 OKX / Binance / Paper 三条已改路径不再并存“旧式通用迁移 + 新式生命周期迁移”
- `nq-app`：
  - Gate 本地验收入口已显式生成 `requestId / idempotencyKey / source / timeInForce`，避免继续走旧兼容构造器

## 12. 第二批与第一批偏差的收敛结果
- 第一批记录的 contracts 漂移问题已部分收敛：
  - `requestId / idempotencyKey / venue / accountId / symbol / quantity / price` 已在 contracts/core 层显式建模
  - 风控重复请求口径已切换到 `idempotencyKey`
- 当前仍保留的兼容层：
  - 旧构造器尚未删除
  - `GateC` 命名的本地验收入口尚未更名为 `GateD`
- 以上兼容层不改变 GateD 主目标，但需要在后续 app/api 冻结 PR 中继续收口

## 13. 第二批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-contracts,nq-core,nq-risk,nq-scheduler,nq-app -am test`
  - `mvn -q -f backend/pom.xml -pl nq-app -am -DskipTests package`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - 模块级测试通过
  - `nq-app` 在 `localhost:5432` 本地 Postgres 下已成功启动
  - `/actuator/health` 返回 `200`，健康状态为 `UP`
  - `POST /__gatec/orders` 空请求体返回 `400`，说明 Gate 本地验收入口已成功暴露并进入参数校验
- 运行时前提：
  - 本地 Postgres 端口为 `5432`
  - docker-desktop 中的 Postgres 端口为 `15432`
- 运行时观察：
  - OKX adapter 在 local 启动时因当前环境外网受限，已按本轮新增策略退回 bootstrap stub
  - 应用日志包含 `okx_adapter_bootstrap_fallback_enabled`，但这不影响 `health`、本地验收入口和非 OKX 本地链路启动
- 备注：
  - 若要做真实 OKX 本地验收，需要在可联网环境下设置 `NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=false`

## 14. 2026-03-12 第三批代码收敛
- `nq-adapter-api`：
  - `AdapterOrderRequest` 已冻结 `requestId / idempotencyKey / orderType / quantity / quoteQuantity / timeInForce / source`
  - `AdapterCancelRequest` 已补齐 `requestId / reason`
  - `AdapterOrderAck / AdapterOrderSnapshot / AdapterTradeReport` 已补齐执行闭环所需的最小账户、状态、数量与 `rawPayload` 语义
- `nq-core`：
  - `OrderCommandService` 调用 adapter 时已显式透传 `requestId / idempotencyKey / timeInForce / source`
  - place/cancel 编排与 adapter 契约的语义边界继续收紧
- `nq-adapter-okx / nq-adapter-binance / nq-scheduler`：
  - OKX / Binance / Paper 已开始填充新的 `AdapterOrderAck / AdapterOrderSnapshot` 字段
  - `timeInForce` 已在 Binance canonical 请求路径落地
- `nq-api`：
  - `TradingQueryFacade` 已从 noop 升级为基于 `OrderRepository` 的最小订单查询视图
- `nq-app`：
  - `GateDAcceptanceController` 已成为 canonical 本地验收入口
  - canonical route 已迁移到 `/__gated`
  - `GET /__gated/orders/{orderId}` 已提供最小查询视图
  - `nq.gated.verify.enabled` 已成为 canonical 开关

## 15. 第三批兼容层与冻结结果
- 已冻结：
  - `nq-adapter-api` 的 canonical 字段名：`orderType / quantity / quoteQuantity / timeInForce / source`
  - `nq-app` 的 canonical 本地验收 route：`/__gated`
  - `nq-app` 的 canonical verify 开关：`nq.gated.verify.enabled`
- 仍保留的兼容层：
  - `AdapterOrderRequest.qty()`、`AdapterOrderRequest.type()`、`AdapterOrderSnapshot.status()` 等旧 accessor
  - `Adapter*` 的旧构造器
  - `GateCOrderHttpRequest / GateCCancelOrderHttpRequest / GateCTriggerResponse` 等 GateC DTO 名称
  - `__gatec` 路由别名
  - `NQ_GATEC_VERIFY_ENABLED` / `nq.gatec.verify.enabled` 旧开关别名
  - `application-gatec-verify.yml` 旧 profile 文件名
- 本轮明确未冻结：
  - 不宣称 local OKX bootstrap fallback 等于真实 OKX 验收通过
  - 不在本轮删除全部 GateC DTO / profile 历史残留

## 16. 第三批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-adapter-api,nq-api,nq-app,nq-core,nq-scheduler -am test`
  - `mvn -q -f backend/pom.xml -pl nq-app -am -DskipTests package`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - adapter/api/app 相关测试通过
  - `nq-app` 在 `localhost:5432` 本地 Postgres 下可启动，日志出现 `Tomcat started on port 18888` 与 `Started NexusQuantApplication`
  - `GET /actuator/health` 返回 `UP`
  - `POST /__gated/orders` 对 PAPER 下单成功，返回 `placeOrder`
  - `GET /__gated/orders/{orderId}` 可读取最小订单视图，验证结果为 `venue=PAPER`、`status=ACCEPTED`
- 运行时观察：
  - local 运行时若外网不可达，日志仍可能出现 `okx_adapter_bootstrap_fallback_enabled`
  - 该日志只代表 local smoke fallback 生效，不代表真实 OKX 验收通过

## 17. 2026-03-12 第四批代码收敛
- `nq-api`：
  - `TradingQueryFacade` 已补齐 `queryLatestTrade / queryPosition / queryAccount`
  - `CoreTradingQueryFacade` 已改为基于 `orders / trades / positions / account_snapshots` 的最小 JDBC 只读查询
  - 已新增 `TradeView / PositionView / AccountView / AccountBalanceView`
- `nq-app`：
  - `GateDAcceptanceController` 只保留 canonical route `__gated`
  - 已补 `GET /__gated/orders/{orderId}/trade`
  - 已补 `GET /__gated/positions/{accountId}/{symbol}`
  - 已补 `GET /__gated/accounts/{accountId}`
  - `GateDOrderHttpRequest / GateDCancelOrderHttpRequest / GateDReconcileRunOnceHttpRequest / GateDTriggerResponse` 已替换旧 GateC DTO 名称
- profile / config：
  - `NQ_GATEC_VERIFY_ENABLED` / `nq.gatec.verify.enabled` 旧开关别名已从 source 移除
  - `application-gatec-verify.yml` 已迁移为 `application-gated-verify.yml`

## 18. 第四批兼容层清理进度
- 已删除：
  - source 中的 `__gatec` 路由映射
  - source 中的 `NQ_GATEC_VERIFY_ENABLED` / `nq.gatec.verify.enabled` 别名读取
  - `application-gatec-verify.yml`
  - `GateCOrderHttpRequest / GateCCancelOrderHttpRequest / GateCReconcileRunOnceHttpRequest / GateCTriggerResponse` 源码命名
- 仍临时保留：
  - `AdapterOrderRequest.qty()`、`AdapterOrderRequest.type()`、`AdapterOrderSnapshot.status()` 等旧 accessor
  - `Adapter*` 旧构造器
  - regression test 中对 `__gatec` 的 `404` 断言，用于防止旧 route 被误恢复
- 本轮明确不做：
  - 不把 local `okx_adapter_bootstrap_fallback_enabled` 解释成真实 OKX 验收通过
  - 不在本轮清理 adapter 兼容层

## 19. 第四批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-api,nq-app -am test`
  - `mvn -q -f backend/pom.xml -pl nq-app -am -DskipTests package`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
  - 本地 smoke：`POST /__gated/orders` -> `GET /__gated/orders/{orderId}` -> `GET /__gated/orders/{orderId}/trade` -> `GET /__gated/positions/{accountId}/{symbol}` -> `GET /__gated/accounts/{accountId}`
- 结果：
  - `nq-api / nq-app` 测试通过
  - `nq-app` 在 `localhost:5432` 本地 Postgres 下可启动，`GET /actuator/health` 返回 `UP`
  - `POST /__gated/orders` 对 PAPER `MARKET` 下单成功
  - `GET /__gated/orders/{orderId}` 返回 `orderId,status=ACCEPTED,venue=PAPER`
  - `GET /__gated/orders/{orderId}/trade` 返回成交视图，验证结果为 `quantity=0.001, venue=PAPER`
  - `GET /__gated/positions/1001/BTC-USDT` 返回持仓视图，验证结果为 `symbol=BTC-USDT, venue=PAPER`
  - `POST /__gatec/orders` 返回 `404`
  - `GET /__gated/accounts/1001` 当前返回 `404`，原因是本地库尚无 `account_snapshots` 数据，不是 route/bean 缺失
- 运行时观察：
  - local 运行时日志仍可能出现 `okx_adapter_bootstrap_fallback_enabled`
  - 该日志只代表 local smoke fallback 生效，不代表真实 OKX 验收通过

## 20. 2026-03-12 第五批根因分析
- `GET /__gated/accounts/{accountId}` 返回 `404` 的根因不是 route 缺失，也不是 `nq-api` 查询条件错误。
- PAPER 成交链已经实际走到：
  - `PaperMatchingService`
  - `LedgerModuleTradeLedgerGateway`
  - `TradeLedgerPostingService`
- 现状缺口在于：
  - `TradeLedgerPostingService` 只写 `ledger_entries / ledger_events / positions / event_store / audit`
  - `LedgerPostingRepository` 与 `JdbcLedgerPostingRepository` 中不存在 `account_snapshots` 写入能力
  - 因此本地链路虽然已有 `trade / position` 数据，但 `account_snapshots` 一直空表，account query 只能返回 `404`
- 补充结论：
  - `ModuleWiringConfiguration` 里的 `NoopLedgerService` 是历史占位 bean，但不是本地 PAPER 成交后的主链路瓶颈；真正的执行链已绕过它走 `TradeLedgerPostingService`

## 21. 2026-03-12 第五批代码收敛
- `nq-ledger`：
  - 新增 `AccountSnapshotProjection`
  - `LedgerPostingRepository` 已补 `insertAccountSnapshot(...)`
  - `JdbcLedgerPostingRepository` 已补 `account_snapshots` 插入实现
  - `TradeLedgerPostingService` 已在成交记账成功后同步写最小账户快照
- 本轮最小快照来源定义：
  - base 资产：来自最新 `positions` 投影
  - quote / fee 资产：来自 `ledger_entries` 当前聚合余额
  - `available`：本地 PAPER 最小链路先等于 `balance`
  - `frozen`：本地 PAPER 最小链路先写 `0`
- `nq-ledger` 测试：
  - `TradeLedgerPostingServiceTest` 已补账户快照断言，确认重复记账不会重复放大 snapshot 币种集合

## 22. 第五批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-ledger,nq-api,nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
  - 本地 smoke：`POST /__gated/orders` -> `GET /__gated/orders/{orderId}` -> `GET /__gated/orders/{orderId}/trade` -> `GET /__gated/positions/{accountId}/{symbol}` -> `GET /__gated/accounts/{accountId}`
- 结果：
  - `nq-ledger / nq-api / nq-app` 测试通过
  - `nq-app` local 启动成功，`GET /actuator/health` 返回 `UP`
  - `POST /__gated/orders` 对 PAPER `MARKET` 下单成功
  - `GET /__gated/orders/{orderId}`、`GET /__gated/orders/{orderId}/trade`、`GET /__gated/positions/{accountId}/{symbol}` 继续可用，无回归
  - `GET /__gated/accounts/1001` 已返回真实快照，不再是 `404`
  - 本地 smoke 观测值：
    - `balances=2`
    - `firstCurrency=BTC`
    - `firstBalance=0.014`
  - `POST /__gatec/orders` 继续返回 `404`
- 运行时观察：
  - local 运行时日志仍可能出现 `okx_adapter_bootstrap_fallback_enabled`
  - 该日志只代表 local smoke fallback 生效，不代表真实 OKX 验收通过

## 23. 2026-03-12 第六批代码收敛
- `nq-app / nq-api / nq-ledger`：
  - `GateDAcceptanceControllerLocalTest` 已把 account query 断言固定为 `BTC / USDT` 两类快照
  - `CoreTradingQueryFacadeTest` 已补齐双资产余额、available、frozen 的只读映射断言
  - `TradeLedgerPostingServiceTest` 已补齐 `BTC / USDT` 两类 snapshot 的最小余额断言
- `nq-adapter-api`：
  - 已删除 `AdapterOrderRequest.qty()/type()` 与旧构造器
  - 已删除 `AdapterOrderSnapshot.status()` 与旧构造器
  - 已删除 `AdapterOrderAck` 的旧构造器与 `ts()` 兼容访问器
  - 已删除 `AdapterCancelRequest` 旧构造器
  - 已删除 `AdapterTradeReport` 旧构造器与 `qty()` 兼容访问器
- 直接调用点迁移：
  - `nq-adapter-binance / nq-adapter-okx` 已改为只消费 `quantity / orderType / externalStatus / ackTs`
  - `nq-core / nq-scheduler` 相关测试桩与 reconcile 路径已同步改到 canonical 字段

## 24. 第六批兼容层清理进度
- 已删除：
  - adapter 层 canonical 模型中的 `qty()/type()/status()` 旧 accessor
  - `AdapterOrderRequest / AdapterOrderSnapshot / AdapterOrderAck / AdapterCancelRequest / AdapterTradeReport` 的旧构造器
- 仍临时保留：
  - `contracts/core` 与 `nq-app` 请求模型中的历史 `qty()/type()` 访问器
  - `nq-api` 视图模型中的历史 `qty()` 访问器
  - regression test 中对 `__gatec` 的 `404` 断言
- 本轮明确不做：
  - 不把 adapter alias 清理扩展到真实 venue account sync
  - 不把 `okx_adapter_bootstrap_fallback_enabled` 解释成真实 OKX 验收通过

## 25. 第六批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-adapter-api,nq-adapter-binance,nq-adapter-okx,nq-core,nq-scheduler,nq-ledger,nq-api,nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - adapter/api/core/scheduler/ledger/app 相关测试通过
  - `nq-app` 在 `localhost:5432` 本地 Postgres 下可启动，日志出现 `Started NexusQuantApplication`
  - `TradeLedgerPostingServiceTest` 已固定：
    - `BTC.balance=0.01000000`
    - `BTC.available=0.01000000`
    - `USDT.balance=0`
  - `GateDAcceptanceControllerLocalTest` 已固定 account query 返回两类余额：`BTC / USDT`
- 运行时观察：
  - local 启动日志仍可能出现 `okx_adapter_bootstrap_fallback_enabled`
  - 该日志只代表 local smoke fallback 生效，不代表真实 OKX 验收通过

## 26. 2026-03-12 第七批代码收敛
- `nq-contracts / nq-core`：
  - 已删除 `PlaceOrderCommand.qty()`
  - 已删除 `PlaceOrderRequest.qty()`
- `nq-risk`：
  - `OrderPrecisionRule / MinNotionalRule / MaxOrderAmountRule` 已改为只读取 `context.command().quantity()`
- `nq-api`：
  - 已删除 `OrderView / TradeView / PositionView` 中的 `qty()` 兼容访问器
- 本轮范围控制：
  - 未修改 `__gated` 主入口
  - 未扩到真实 venue account sync
  - 未触碰 `nq-ledger` 与 account snapshot 产出链路

## 27. 第七批兼容层清理进度
- 已删除：
  - `PlaceOrderCommand.qty()`
  - `PlaceOrderRequest.qty()`
  - `OrderView.qty()`
  - `TradeView.qty()`
  - `PositionView.qty()`
- 仍临时保留：
  - `GateDOrderHttpRequest` 中的 `qty / type` 请求字段
  - regression test 中对 `__gatec` 的 `404` 断言
  - 领域模型中的既有 `qty()` 命名，如 `OrderRecord / PositionProjection / TradeLedgerRequest / PaperTradeRecord`，本轮不按“上层兼容 alias”处理
- 本轮明确不做：
  - 不继续修改 `nq-app` 主入口协议
  - 不把 local `okx_adapter_bootstrap_fallback_enabled` 解释成真实 OKX 验收通过

## 28. 第七批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-contracts,nq-core,nq-risk,nq-ledger,nq-api,nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-contracts / nq-core / nq-risk / nq-ledger / nq-api / nq-app` 相关测试通过
  - local 启动在 `localhost:5432` 本地 Postgres 下成功推进到 `Started NexusQuantApplication`
  - 本轮未改 `app/api` 主入口，但既有 `order / trade / position / account` 本地闭环测试未回归
- 运行时观察：
  - local 启动日志仍可能出现 `okx_adapter_bootstrap_fallback_enabled`
  - 调度线程仍会输出 `ledger_reconcile_diff ... snapshot_balance=0.01400000 diff=-0.01400000`，该现象属于既有 reconcile 观测，不是本轮 `qty()` alias 清理引入的新故障

## 29. 2026-03-12 第八批代码收敛
- `nq-app`：
  - `GateDOrderHttpRequest` 已从 `type / qty` 字段切换为 `orderType / quantity`
  - `GateDAcceptanceController` 已只消费 `request.orderType()` 与 `request.quantity()`
  - `validateOrderRequest(...)` 的错误信息已同步切换为 `orderType / quantity`
- 兼容策略：
  - 旧 `type / qty` 不再作为 DTO 字段或访问器保留
  - 旧 JSON 输入暂通过 `JsonAlias` 兼容，避免本地 smoke 与手工脚本同批次全部中断

## 30. 第八批请求层兼容清理进度
- 已删除：
  - `GateDOrderHttpRequest` 中的 `type` 字段与访问器
  - `GateDOrderHttpRequest` 中的 `qty` 字段与访问器
- 仍临时保留：
  - `GateDOrderHttpRequest` 上的 `JsonAlias(\"type\")`
  - `GateDOrderHttpRequest` 上的 `JsonAlias(\"qty\")`
  - regression test 中对 `__gatec` 的 `404` 断言
- 本轮明确不做：
  - 不继续改 `__gated` 主入口 route / response 语义
  - 不把 alias 清理扩展到真实 venue account sync

## 31. 第八批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试通过
  - `GateDAcceptanceControllerLocalTest` 已覆盖 canonical `orderType / quantity` 请求体序列化
  - 同时新增旧 `type / qty` JSON alias 兼容断言，确认请求层清理不会打断现有本地手工请求
  - local 启动在 `localhost:5432` 本地 Postgres 下成功推进到 `Started NexusQuantApplication`

## 32. 2026-03-12 第九批代码收敛
- `nq-app`：
  - `GateDOrderHttpRequest` 已删除 `JsonAlias(\"type\") / JsonAlias(\"qty\")`
  - `GateDAcceptanceControllerLocalTest` 已删除旧 alias 兼容断言
  - 现存测试输入与本地示例请求已统一使用 `orderType / quantity`
- 文档：
  - `CONTRACTS.md / DECISIONS.md / WORK.md` 已同步移除“请求层 alias 暂留”的表述

## 33. 第九批请求层兼容清理进度
- 已彻底删除：
  - `GateDOrderHttpRequest.type` 历史字段与访问器
  - `GateDOrderHttpRequest.qty` 历史字段与访问器
  - `GateDOrderHttpRequest` 上的 `JsonAlias(\"type\")`
  - `GateDOrderHttpRequest` 上的 `JsonAlias(\"qty\")`
  - `GateDAcceptanceControllerLocalTest` 中的旧 `type / qty` 输入样例
- 仍临时保留：
  - regression test 中对 `__gatec` 的 `404` 断言
- 本轮明确不做：
  - 不继续改 `__gated` 主入口 route / response 语义
  - 不把 alias 清理扩展到真实 venue account sync

## 34. 第九批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试继续通过
  - `GateDAcceptanceControllerLocalTest` 仍覆盖 canonical `orderType / quantity` 请求体
  - `GateDAcceptanceControllerLocalDisabledTest / NonLocalTest` 未回归
  - local 启动在 `localhost:5432` 本地 Postgres 下继续成功推进到 `Started NexusQuantApplication`

## 35. 2026-03-12 第十批文档 / 脚本残留清理
- 扫描范围：
  - `README.md`
  - `docs/**`
  - `scripts/**`
  - `backend/nq-app` 下的文档型文件
- 扫描结论：
  - 当前真正需要修正的现行残留，集中在 `scripts/gatec_okx_dome_verify.ps1`
  - 历史 Gate 文档中的 `type / qty / __gatec` 记录仍存在，但属于冻结快照，不在本轮改写范围
  - `docs/CONTRACTS.md` 仍包含 GateA 示例中的 `type / qty`，但该文件本身声明为 `Gate A` 文档，不作为 GateD Source of Truth，本轮不改
- 本轮修订：
  - `scripts/gatec_okx_dome_verify.ps1` 的示例请求体已统一为 `orderType / quantity`
  - 同脚本中的示例 route 已统一为 `__gated`
  - 同脚本中的示例 verify 开关已统一为 `NQ_GATED_VERIFY_ENABLED`
  - `docs/gates/gate-d/CONTRACTS.md` 已同步声明当前脚本与 smoke 示例只能使用 canonical 命名

## 36. 第十批残留状态
- 已清除：
  - 现行 smoke 脚本中的 `type / qty` 请求体示例
  - 现行 smoke 脚本中的 `__gatec` route 示例
  - 现行 smoke 脚本中的 `NQ_GATEC_VERIFY_ENABLED` 示例
- 仍暂留：
  - `scripts/gatec_okx_dome_verify.ps1` 的历史文件名 `gatec_*`
  - `docs/gates/gate-a/**`、`docs/gates/gate-b/**`、`docs/gates/gate-c/**` 中的历史记录
  - `docs/CONTRACTS.md` 中声明为 GateA 的历史示例
  - regression test 中对 `__gatec` 返回 `404` 的断言
- 本轮明确不做：
  - 不改 `__gated` 主入口业务语义
  - 不扩到真实 venue account sync
  - 不改 `nq-core / nq-api / nq-ledger` 业务主链

## 37. 第十批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试继续通过
  - local 启动在 `localhost:5432` 本地 Postgres 下继续成功推进到 `Started NexusQuantApplication`
  - 文档与脚本示例已统一到 `__gated + NQ_GATED_VERIFY_ENABLED + orderType / quantity`

## 38. 2026-03-13 第十一批历史标识与归档边界清理
- 本轮目标：
  - 清理现行脚本文件名里残留的 `gatec`
  - 为根级 GateA 文档补充 archive 标识，避免误读为当前 Source of Truth
- 本轮修订：
  - `scripts/gatec_okx_dome_verify.ps1` 已重命名为 `scripts/gated_okx_dome_verify.ps1`
  - 根级 `docs/CONTRACTS.md` 已新增 archive banner，明确其为 GateA 历史留档
  - `README.md` 已新增根级 GateA 文档的 archive 边界说明
  - `docs/gates/gate-d/DECISIONS.md / WORK.md` 已同步回填本轮决策与范围

## 39. 第十一批归档状态
- 已重命名：
  - `scripts/gatec_okx_dome_verify.ps1` -> `scripts/gated_okx_dome_verify.ps1`
- 已标注为 archive：
  - `docs/CONTRACTS.md`
- 仍暂留：
  - `docs/gates/gate-c/**` 中对旧脚本文件名的历史引用
  - regression test 中对 `__gatec` 返回 `404` 的断言
  - 其他根级 GateA 文档仍未统一补 archive banner
- 本轮明确不做：
  - 不改 `__gated` 主入口业务语义
  - 不扩到真实 venue account sync
  - 不改任何业务主链模块

## 40. 第十一批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试继续通过
  - local 启动在 `localhost:5432` 本地 Postgres 下继续成功推进到 `Started NexusQuantApplication`
  - 当前现行脚本与根级历史文档的边界说明已清晰，不再误导为 GateD 当前事实来源

## 41. 2026-03-13 第十二批根级历史文档 archive 标识清理
- 本轮目标：
  - 为根级 `docs/*.md` 中明确属于 GateA 历史留档的文档统一补 archive banner
  - 保留根级导航文档与 GateD 对齐概览，不做整批 archive 化
- 本轮修订：
  - 已补 archive banner：
    - `docs/DB_SCHEMA.md`
    - `docs/DECISIONS.md`
    - `docs/EVOLUTION_RULES.md`
    - `docs/GATE_A_CHECKLIST.md`
    - `docs/NUMERIC_POLICY.md`
    - `docs/RECOVERY_RUNBOOK.md`
    - `docs/ROADMAP.md`
    - `docs/WORK.md`
  - `docs/README.md` 已补总边界说明，明确根级旧文档只作 archive 参考
- 本轮范围控制：
  - 未改 `docs/ARCHITECTURE.md`
  - 未改 `docs/MODULES.md`
  - 未改任何业务主链模块

## 42. 第十二批 archive 状态
- 已补 archive banner：
  - `docs/CONTRACTS.md`
  - `docs/DB_SCHEMA.md`
  - `docs/DECISIONS.md`
  - `docs/EVOLUTION_RULES.md`
  - `docs/GATE_A_CHECKLIST.md`
  - `docs/NUMERIC_POLICY.md`
  - `docs/RECOVERY_RUNBOOK.md`
  - `docs/ROADMAP.md`
  - `docs/WORK.md`
- 仍暂留：
  - `docs/ARCHITECTURE.md`
  - `docs/MODULES.md`
  - `docs/README.md` 仅加边界说明，不标 archive
  - 历史 Gate 冻结快照目录 `docs/gates/gate-a/**`、`docs/gates/gate-b/**`、`docs/gates/gate-c/**`
- 暂留原因：
  - 这几份文档仍承担顶层导航或 GateD 对齐说明职责，不适合在本轮直接整体标记为 archive

## 43. 第十二批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试继续通过
  - local 启动在 `localhost:5432` 本地 Postgres 下继续成功推进到 `Started NexusQuantApplication`
  - 根级历史 GateA 文档已形成统一 archive 边界说明，不再与当前阶段入口混淆

## 44. 2026-03-13 第十三批顶层导航文档定位清理
- 本轮目标：
  - 明确 `docs/ARCHITECTURE.md` 与 `docs/MODULES.md` 的角色
  - 避免它们继续在 current / archive 之间产生语义混淆
- 定位结论：
  - `docs/ARCHITECTURE.md`：顶层导航摘要
  - `docs/MODULES.md`：顶层导航摘要
  - 二者都不是 current Source of Truth，也不是 archive 参考
- 本轮修订：
  - 两份文档顶部已新增 `Top-Level Navigation Notice`
  - `docs/README.md` 已补充例外说明，明确它们属于导航摘要
  - `docs/gates/gate-d/DECISIONS.md / WORK.md` 已同步回填该定位结论

## 45. 第十三批文档角色边界
- current source of truth：
  - `docs/current/*`
  - `docs/gates/gate-d/*`
- top-level navigation：
  - `docs/ARCHITECTURE.md`
  - `docs/MODULES.md`
  - `docs/README.md`
- archive 参考：
  - `docs/CONTRACTS.md`
  - `docs/DB_SCHEMA.md`
  - `docs/DECISIONS.md`
  - `docs/EVOLUTION_RULES.md`
  - `docs/GATE_A_CHECKLIST.md`
  - `docs/NUMERIC_POLICY.md`
  - `docs/RECOVERY_RUNBOOK.md`
  - `docs/ROADMAP.md`
  - `docs/WORK.md`

## 46. 第十三批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试继续通过
  - local 启动在 `localhost:5432` 本地 Postgres 下继续成功推进到 `Started NexusQuantApplication`
  - 根级文档角色已拆分为 current source / top-level navigation / archive 三类，不再混淆

## 47. 2026-03-13 第十四批 checklist 状态回填
- 本轮目标：
  - 更新 `docs/current/GATE_CHECKLIST.md`
  - 更新 `docs/gates/gate-d/GATE_D_CHECKLIST.md`
  - 把 GateD 迄今已完成、部分完成、未完成的条目状态显式同步回 checklist
- 本轮修订：
  - `docs/current/GATE_CHECKLIST.md` 已从“全未勾选草稿”改为显式状态版本
  - `docs/gates/gate-d/GATE_D_CHECKLIST.md` 已新增“当前状态回填（截至 2026-03-13）”区块
  - `docs/gates/gate-d/GATE_D_CHECKLIST.md` 的冻结结论状态已标记为“进行中”
  - 已显式同步以下关键事实：
    - pre-trade 风控规则链：已完成
    - lifecycle 主通道收口：部分完成
    - adapter canonical 契约冻结：已完成
    - `__gated` canonical 入口：已完成
    - order / trade / position / account 本地最小闭环：已完成
    - account snapshot 本地产出链：已完成
    - 请求层 canonical `orderType / quantity`：已完成
    - 现行脚本与示例 canonical 化：已完成
    - current / top-level / archive 文档边界建立：已完成
    - 真实 OKX 验收：未完成
    - 深层兼容债务残留：部分完成
- 本轮范围控制：
  - 未改任何业务主链模块
  - 未扩到真实 venue account sync
  - 未继续改 `__gated` 主入口业务语义

## 48. 第十四批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试继续通过
  - local 启动在 `localhost:5432` 本地 Postgres 下继续成功推进到 `Started NexusQuantApplication`
  - 两份 checklist 已可直接反映当前 GateD 的完成态、部分完成态与未完成态，不再需要只依赖 `WORK.md` 推断

## 49. 2026-03-13 第十五批入口文档状态摘要同步
- 本轮目标：
  - 更新 `docs/current/README.md`
  - 更新 `docs/gates/gate-d/README.md`
  - 让入口文档中的阶段状态摘要与 checklist 保持一致
- 本轮修订：
  - `docs/current/README.md` 已新增“当前阶段状态摘要（截至 2026-03-13）”
  - `docs/gates/gate-d/README.md` 已新增“当前状态摘要（截至 2026-03-13）”
  - 两份入口文档均已显式同步以下核心状态：
    - pre-trade 风控规则链：已完成
    - lifecycle 主通道收口：部分完成
    - adapter canonical 契约冻结：已完成
    - `__gated` canonical 入口：已完成
    - order / trade / position / account 本地最小闭环：已完成
    - account snapshot 本地产出链：已完成
    - 请求层 canonical `orderType / quantity`：已完成
    - 现行脚本与示例 canonical 化：已完成
    - current / top-level / archive 文档边界建立：已完成
    - 真实 OKX 验收：未完成
    - 深层兼容债务：部分完成
- 本轮范围控制：
  - 未改任何业务主链模块
  - 未扩到真实 venue account sync
  - 未继续改 `__gated` 主入口业务语义

## 50. 第十五批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试继续通过
  - local 启动在 `localhost:5432` 本地 Postgres 下继续成功推进到 `Started NexusQuantApplication`
  - current README、gate README 与两份 checklist 的阶段状态摘要已保持同源一致

## 51. 2026-03-13 第十六批 GateD 治理文档同步
- 本轮目标：
  - 更新 `docs/gates/gate-d/PR_SPLIT_PLAN.md`
  - 更新 `docs/gates/gate-d/TEST_CASES.md`
  - 更新 `docs/gates/gate-d/SOURCES.md`
  - 让三份治理文档与当前已完成事实保持一致
- 本轮修订：
  - `PR_SPLIT_PLAN.md` 已切换为状态版执行计划，并显式标记 PR-1 至 PR-8 的 `已完成 / 进行中 / 未开始`
  - `TEST_CASES.md` 已显式同步当前用例状态，并补入本地闭环已落地的查询验证与 `BTC / USDT` 双资产断言
  - `SOURCES.md` 已按 `current source / top-level navigation / archive / code / external` 五层边界重写
- 本轮范围控制：
  - 未改任何业务主链模块
  - 未扩到真实 venue account sync
  - 未继续改 `__gated` 主入口业务语义

## 52. 第十六批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试继续通过
  - local 启动在 `localhost:5432` 本地 Postgres 下继续成功推进到 `Started NexusQuantApplication`
  - `PR_SPLIT_PLAN / TEST_CASES / SOURCES` 已与 checklist、README、WORK 的当前事实对齐

## 53. 2026-03-13 第十七批顶层导航摘要维护约束补丁
- 本轮目标：
  - 为 `docs/ARCHITECTURE.md` 与 `docs/MODULES.md` 补清晰的维护约束与边界说明
  - 防止顶层导航摘要继续漂移并重新膨胀成事实来源
- 本轮修订：
  - `docs/ARCHITECTURE.md` 已补充：
    - 当执行链路、模块边界、验收状态明显变化时，需要同步检查摘要
    - 本文件只保留高层概览，不承载详细冻结条件、契约、测试状态
  - `docs/MODULES.md` 已补充：
    - 当前模块职责事实以 `docs/current/*`、`docs/gates/gate-d/MODULES.md`、README/checklist 为准
    - 只有当模块职责、边界、主战场模块优先级明显变化时，才需要同步该摘要
    - 本文件不承担详细阶段状态或详细实施计划职责
  - `docs/gates/gate-d/DECISIONS.md` 已同步记录该维护约束决策
- 为什么现在补：
  - 当前文档体系已经拆清 `current source / top-level navigation / archive`
  - 若不补维护约束，顶层导航摘要仍可能在后续多批改动中再次漂移并与 current source 脱节
- 本轮范围控制：
  - 未改业务主链模块
  - 未扩到真实 venue account sync
  - 未继续改 `__gated` 主入口业务语义

## 54. 第十七批验证证据
- 文档检查：
  - `docs/ARCHITECTURE.md` 已明确：
    - top-level navigation
    - current source 指向
    - 冲突时以后者为准
    - maintenance rule
    - 仅保留高层概览
  - `docs/MODULES.md` 已明确：
    - top-level navigation
    - 模块职责事实来源
    - 冲突时以后者为准
    - maintenance rule
    - 不承担详细阶段状态或详细实施计划
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `NQ_DB_PORT=5432 NQ_GATED_VERIFY_ENABLED=true NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=true mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run`
- 结果：
  - `nq-app` 相关测试继续通过
  - local 启动在 `localhost:5432` 本地 Postgres 下继续成功推进到 `Tomcat started on port 18888` 与 `Started NexusQuantApplication`
  - 顶层导航摘要的角色、冲突处理与维护约束已落文，不再只靠 `README / SOURCES / WORK` 间接推断
- 当前仍未完成事项：
  - 真实 OKX 验收仍未完成
  - 深层兼容债务仍为部分完成
  - `okx_adapter_bootstrap_fallback_enabled` 仍只代表 local smoke fallback，不代表真实 OKX 验收通过

## 55. 2026-03-13 第十八批 OKX 最小验收收口
- 本轮目标：
  - 修复 `scripts/gated_okx_dome_verify.ps1` 对旧 GateC 工件路径的依赖
  - 提供明确的 canonical non-fallback 启动路径
  - 为 `query / reconcile / recovery` 补最小可核对观察点
  - 尝试推进 `UC-D9: OKX LIMIT -> cancel`
- 本轮修订：
  - `scripts/gated_okx_dome_verify.ps1` 已不再依赖 `artifacts/start-gatec-app-local.cmd`
  - 脚本在 `-AutoRestart` 模式下改为直接使用 PowerShell 启动 `nq-app`，并显式设置：
    - `NQ_DB_PORT=5432`
    - `NQ_GATED_VERIFY_ENABLED=true`
    - `NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=false`
    - 若进程环境未提供 `NQ_OKX_ENV`，则默认补 `dome`
  - 脚本已补 `GET /__gated/orders/{orderId}` 与 `GET /__gated/orders/{orderId}/trade` 观察点，用于核对：
    - LIMIT -> cancel 后是否仍有成交
    - recovery / reconcile 后订单状态是否可查询
  - `nq-adapter-okx` 已补最小结构化日志：
    - `okx_query_confirm_place_started`
    - `okx_query_confirm_place_resolved`
    - `okx_query_confirm_place_unconfirmed`
    - `okx_query_confirm_cancel_started`
    - `okx_query_confirm_cancel_resolved`
    - `okx_query_confirm_cancel_lookup_failed`
    - `okx_query_confirm_cancel_unconfirmed`
- 为什么现在做：
  - GateD 当前真正卡住的是真实 OKX 最小验收，不是文档治理或本地 PAPER 闭环
  - 在不混迁移闭环、不混兼容债务的前提下，这一批需要先把真实验收脚本与 non-fallback 启动路径修正到可执行

## 56. 第十八批验证证据
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-adapter-okx,nq-scheduler,nq-app -am test`
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gated_okx_dome_verify.ps1 -AutoRestart -StartupTimeoutSec 90`
  - `NQ_OKX_ENV=dome` 后重跑
  - `NQ_OKX_ENV=dome + NQ_GATED_VERIFY_ENABLED=true` 后重跑
  - `NQ_OKX_ENV=dome + NQ_GATED_VERIFY_ENABLED=true + dummy OKX dome credentials` 后重跑
- 结果：
  - `nq-adapter-okx / nq-scheduler / nq-app` 相关测试通过
  - 脚本已逐层暴露真实阻断点：
    - 初次执行：缺少 `NQ_OKX_ENV=dome`
    - 第二次执行：缺少 `NQ_GATED_VERIFY_ENABLED=true`
    - 第三次执行：缺少 `NQ_OKX_DOME_API_KEY`
    - 第四次执行：在 canonical non-fallback 启动路径下，`nq-app` 启动失败，日志显示 OKX public instruments 请求在当前环境报 `Permission denied: getsockopt`
  - 因此本轮已证明：
    - 脚本路径与启动路径问题已修正
    - 当前仍未完成的阻塞已从“脚本依赖旧工件”收敛为“真实 OKX 外网/环境不可达”
    - `UC-D9` 仍未转绿，GateD 冻结仍被真实 OKX 验收阻断

## 57. 2026-03-13 第十九批 OKX 配置收口与 dome/real 环境切换
- 本轮目标：
  - 让 `nq-app` 与 OKX 启动脚本同时支持 `dome / real` 两套环境切换
  - 把 `.env` 中的两套凭证来源统一映射成单套运行时变量
  - 明确 `real` 环境禁止 fallback 冒充成功
- 本轮修订：
  - `scripts/gated_okx_dome_verify.ps1` 已显式支持：
    - 加载 `.env`
    - 读取 `NQ_OKX_ENV=dome|real`
    - 从 `NQ_OKX_DOME_* / NQ_OKX_REAL_*` 中选当前环境对应的一套值
    - 归一映射为：
      - `NQ_OKX_API_KEY`
      - `NQ_OKX_API_SECRET`
      - `NQ_OKX_API_PASSPHRASE`
      - `NQ_OKX_BASE_URL`
      - `NQ_OKX_WS_URL`
    - 在 acceptance 启动链中强制：
      - `NQ_GATED_VERIFY_ENABLED=true`
      - `NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=false`
  - `backend/nq-app/src/main/resources/application.yml` 已显式声明统一变量读取：
    - `nq.okx.env`
    - `nq.okx.api-key / api-secret / api-passphrase`
    - `nq.okx.base-url / ws-url`
  - `OkxRuntimeConfig` 已改为优先读取统一运行时变量，再回退到历史 `DOME/REAL` 专属命名，避免配置切换逻辑散落在代码各处
- 为什么现在做：
  - 第十八批已经把真实 OKX 验收脚本和 non-fallback 启动路径修正到可执行，但 `.env` 与 `spring-boot:run` 之间仍没有统一配置桥接
  - 若不先收口配置与环境切换，后续真实 OKX dome/real 验收仍会继续被“脚本看到一套、应用看到另一套”拖住
- 本轮范围控制：
  - 未改业务主链模块
  - 未扩到真实 venue account sync
  - 未改 `__gated` 主入口业务语义

## 58. 第十九批验证证据
- 代码/脚本检查：
  - `gated_okx_dome_verify.ps1` 已显式包含 `.env` 加载、`NQ_OKX_ENV` 解析、统一变量映射、non-fallback 强制
  - `application.yml` 已显式声明统一 OKX 运行时变量
  - `OkxRuntimeConfig` 已优先读取统一变量，再回退到 `DOME/REAL` 专属命名
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - 使用自定义临时 env 文件验证 `real` 模式缺凭证时报缺失项
  - 使用当前 `.env` 验证 `dome` 模式可推进到 canonical 启动链
- 结果：
  - `nq-app` 相关测试通过
  - `real` 模式在未提供真实凭证时已能明确报缺失项，而不是静默 fallback
  - `dome` 模式仍可推进到 canonical 启动链；若当前环境外网不可达，阻断点仍表现为 OKX 连接失败，而不是配置映射失败

## 59. 2026-03-13 第二十批真实 OKX 最小验收预检查与 UC-D9 推进
- 本轮目标：
  - 使用当前 `.env` 和 `scripts/gated_okx_dome_verify.ps1` 做真实环境预检查
  - 直接推进 `UC-D9: OKX LIMIT -> cancel`
  - 记录每一个真实阻塞点，并仅做最小回填
- 本轮修订：
  - `gated_okx_dome_verify.ps1` 已补：
    - 当从 `powershell.exe` 5.1 启动时，自动切换到 `pwsh` 继续执行
    - `Select-FirstNonBlankValue(...)` 显式做统一变量优先、环境专属变量回退，避免空值或求值顺序继续遮蔽真实凭证
  - `docs/current/GATE_CHECKLIST.md`
  - `docs/gates/gate-d/GATE_D_CHECKLIST.md`
  - `docs/gates/gate-d/PR_SPLIT_PLAN.md`
  - 已按真实执行结果回填最新阻塞描述
- 为什么现在做：
  - 第十九批已经把 `dome / real` 配置切换收口到统一运行时变量，但真实 OKX 最小验收仍未获得一条从脚本到启动链的完整证据
  - 若不先把脚本解释器兼容性与真实阻塞点收口，后续 `UC-D9` 仍会混杂“配置问题”和“真实环境问题”
- 本轮范围控制：
  - 未改 migration 闭环
  - 未改兼容债务清理
  - 未改真实 venue account sync 深扩边
  - 未改 `__gated` 主入口业务语义

## 60. 第二十批验证证据
- 真实环境预检查：
  - 当前 `.env` 已被脚本实际加载
  - 当前默认 `NQ_OKX_ENV=dome`
  - 脚本已确认：
    - `NQ_OKX_API_KEY`
    - `NQ_OKX_API_SECRET`
    - `NQ_OKX_API_PASSPHRASE`
    - `NQ_OKX_BASE_URL`
    - `NQ_OKX_WS_URL`
    的统一映射路径可被构造
  - 启动模式明确打印为：`canonical_non_fallback`
  - `NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=false` 已强制生效
- 命令：
  - `mvn -q -f backend/pom.xml -pl nq-app -am test`
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gated_okx_dome_verify.ps1 -AutoRestart -StartupTimeoutSec 60`
  - `NQ_OKX_ENV=real powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gated_okx_dome_verify.ps1 -AutoRestart -StartupTimeoutSec 60`
- 结果：
  - `nq-app` 相关测试继续通过
  - `dome` 模式：
    - 已推进到 canonical non-fallback 启动链
    - 已进入真实 OKX bootstrap
    - 阻断点为 `Permission denied: getsockopt`
  - `real` 模式：
    - 已推进到 canonical non-fallback 启动链
    - 已进入真实 OKX bootstrap
    - 阻断点同样为 `Permission denied: getsockopt`
  - 当前未进入真实下单/撤单执行阶段，因此：
    - 暂无新的 `query-confirm started/resolved/unconfirmed` 实际样本
    - 暂无新的 `reconcileOnce / recoveryOnce` 实际副作用样本
    - 暂未观察到重复成交、重复记账、状态回退，但原因是链路尚未进入真实执行，不是因为已经验收通过
- 当前仍未完成事项：
  - `UC-D9` 仍未转绿
  - 真实 OKX 验收仍被外网连接权限问题阻断
  - GateD migration 闭环仍未完成
  - 深层兼容债务仍为部分完成
