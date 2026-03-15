# GateD DECISIONS

> 记录 GateD 实施过程中的中粒度工程决策。  
> ADR 记录高层结构性决策；本文件记录落地过程中的阶段性收敛结果。

---

## D-001：GateD 主验收通道固定为 OKX + PAPER

- 日期：2026-03-12
- 状态：已决定
- 背景：当前仓库已具备 OKX、Binance、Paper 三条执行通道，但 GateD 的核心目标是“执行闭环收敛”，不是“多交易所齐平扩边”。
- 决策：
  - GateD 的主验收通道固定为 **OKX + PAPER**
  - Binance 在 GateD 仅保持契约兼容与最小验证，不作为主扩边对象
- 影响模块：
  - `nq-core`
  - `nq-adapter-api`
  - `nq-adapter-okx`
  - `nq-adapter-binance`
  - `nq-app`
- 不选方案：
  - 不以 OKX / Binance 双主通道并行推进，因为会显著扩大 GateD 的测试矩阵与实现面
- 后续影响：
  - GateE 以后再考虑 Binance 深度齐平

---

## D-002：GateD 阶段定义修正为“统一执行闭环与执行域硬化”

- 日期：2026-03-12
- 状态：已决定
- 背景：旧版 roadmap 曾将 GateD 写成研究 / 回测阶段，这与当前代码基线和实施目标冲突。
- 决策：
  - GateD 统一定义为 **统一执行闭环与执行域硬化**
  - 研究 / 回测顺延到 GateF
- 影响文档：
  - `README.md`
  - `AGENTS.md`
  - `docs/current/*`
  - `docs/ROADMAP.md`
  - `docs/gates/gate-b/ROADMAP.md`
  - `docs/gates/gate-c/ROADMAP.md`
- 后续影响：
  - 任何新增文档、PR、模块说明不得再把 GateD 写成研究 / 回测阶段

---

## D-003：scheduler 只保留调度与协调职责，不再扩张为领域中心

- 日期：2026-03-12
- 状态：已决定
- 背景：当前 `nq-scheduler` 已包含 reconcile、recovery、WS 协调、paper 相关逻辑，存在继续膨胀为业务中心的风险。
- 决策：
  - `nq-scheduler` 只承担 job 触发、窗口扫描、恢复编排、受限兜底协调
  - 状态推进、执行编排、账本与投影更新统一回到 `nq-core` / `nq-ledger`
- 影响模块：
  - `nq-scheduler`
  - `nq-core`
  - `nq-ledger`
- 不选方案：
  - 不把 scheduler 继续做成“能调度也能写业务”的多功能模块
- 后续影响：
  - 任何新的恢复 / 补偿逻辑都必须先判断是否应归入 core 或 ledger

---

## D-004：current checklist 与 gate checklist 双轨保留

- 日期：2026-03-12
- 状态：已决定
- 背景：只保留 `docs/current/GATE_CHECKLIST.md` 会在阶段切换后丢失 GateD 原始冻结标准；只保留 gate 内 checklist 又不利于 Codex 快速定位当前入口。
- 决策：
  - 保留 `docs/current/GATE_CHECKLIST.md` 作为当前唯一工作入口
  - 保留 `docs/gates/gate-d/GATE_D_CHECKLIST.md` 作为 GateD 正式冻结卷宗
- 影响文档：
  - `docs/current/GATE_CHECKLIST.md`
  - `docs/gates/gate-d/GATE_D_CHECKLIST.md`
- 后续影响：
  - GateE 起始时沿用同样模式

---

## D-005：数值规范单独成文，不散落在代码注释与 schema 中

- 日期：2026-03-12
- 状态：已决定
- 背景：交易系统中价格、数量、金额、手续费、最小名义金额等规则极易因四舍五入、scale 不一致而产生隐性错误。
- 决策：
  - GateD 新增 `NUMERIC_POLICY.md`
  - 数值计算、比较、舍入、持久化精度统一以该文档为准
- 影响模块：
  - `nq-core`
  - `nq-risk`
  - `nq-ledger`
  - `nq-adapter-*`
  - `nq-infra`
- 后续影响：
  - 后续 Gate 若复用该规则，可提升为 `docs/NUMERIC_POLICY.md`

---

## D-006：恢复与补偿必须有操作手册，不接受“只在代码里懂”

- 日期：2026-03-12
- 状态：已决定
- 背景：GateD 强依赖 reconcile / recovery / query-confirm / degrade 收敛，若没有 runbook，排障时会高度依赖个人记忆。
- 决策：
  - GateD 新增 `RECOVERY_RUNBOOK.md`
  - 所有恢复、排障、人工核查动作必须可按 runbook 执行
- 影响模块：
  - `nq-scheduler`
  - `nq-core`
  - `nq-ledger`
  - `nq-observability`
  - `nq-app`
- 后续影响：
  - 每次新增恢复路径时同步更新 runbook

---

## D-007：PR 必须按能力边界拆分，不接受 GateD 巨型杂糅 PR

- 日期：2026-03-12
- 状态：已决定
- 背景：GateD 涉及文档、执行入口、风控、恢复、投影、迁移与验收，一次性堆到单个 PR 会显著提高 review 成本与返工成本。
- 决策：
  - GateD 新增 `PR_SPLIT_PLAN.md`
  - 后续提交按文档、contracts/core、risk、scheduler/recovery、ledger/db、app/api、tests/freeze 分拆
- 影响范围：整个 GateD
- 后续影响：
  - PR 描述必须标明对应的 split 阶段与 checklist 条目

---

## D-008：scheduler 第一批通过 OrderLifecycleService 调用显式生命周期动作

- 日期：2026-03-12
- 状态：已决定
- 背景：`nq-scheduler` 当前大量直接调用 `OrderCommandService.transitionOrder(...)`，虽然仍经过状态机，但暴露的是“任意迁移”能力，不符合 GateD 对 scheduler 边界收口的要求。
- 决策：
  - 在 `nq-core` 新增 `OrderLifecycleService`
  - 第一批先把 OKX 主验收通道的 reconcile / recovery / ws acceleration 改为调用显式生命周期动作
  - Binance 与其他次要路径放入后续同类 PR 继续收敛，不在本轮顺手扩散
- 影响模块：
  - `nq-core`
  - `nq-scheduler`
- 不选方案：
  - 不在本轮直接重写整个 scheduler 结构，避免把 PR-5 提前膨胀成大重构
- 后续影响：
  - 后续 scheduler 路径新增状态推进时，优先补 `OrderLifecycleService` 语义方法，而不是继续暴露通用 `transitionOrder`

---

## D-009：pre-trade 风控第一批先使用内存规则配置与全局 symbol allow-list

- 日期：2026-03-12
- 状态：已决定
- 背景：GateD 需要尽快把 `nq-risk` 从 `NoopRiskGate` 升级为规则链，但当前 contracts/core 还没有稳定的 `requestId / idempotencyKey` 与可验证的 venue 访问器供风险模块直接依赖。
- 决策：
  - 第一批新增 `PreTradeRiskService + RiskRuleRegistry + RiskRule`
  - 默认配置使用内存型 `PreTradeRiskSettings`
  - `DuplicateRequestRule` 先按 `accountId + clientOrderId` 进行窗口拦截
  - `SymbolEnabledRule` 第一批先使用全局 symbol allow-list 兼容 contracts 现状
- 影响模块：
  - `nq-risk`
  - `nq-app`
  - `nq-core`
- 不选方案：
  - 不在本轮提前修改 contracts / adapter / config center，把风控首批落地阻塞在更大的契约改造上
- 后续影响：
  - PR-2 / PR-3 后续需要补齐 `requestId / idempotencyKey / venue` 的强契约，并将风控从“最小稳定键”升级为完整 GateD 契约

---

## D-010：contracts/core 第二批采用“强语义字段 + 兼容构造器”收敛策略

- 日期：2026-03-12
- 状态：已决定
- 背景：第一批已经把风控和生命周期入口落地，但 `PlaceOrderCommand / CancelOrderCommand / PlaceOrderRequest / CancelOrderRequest`
  仍缺少 `requestId / idempotencyKey / venue / symbol / quantity` 等完整语义，且仓库内还存在旧构造器调用点。
- 决策：
  - 在 contracts/core 中补齐 `requestId / idempotencyKey / venue / accountId / symbol / quantity / price / source` 的强语义字段
  - 保留旧构造器作为兼容层，避免第二批 PR 为机械改签名而膨胀
  - 在 `nq-core` 新增 `ExecutionCommandMapper`，把 contracts 组装从 `OrderCommandService` 中拆出
- 影响模块：
  - `nq-contracts`
  - `nq-core`
  - `nq-risk`
  - `nq-app`
- 不选方案：
  - 不直接删除旧构造器，因为当前 demo runner、测试、验收入口仍有大量旧签名调用
- 后续影响：
  - 后续 adapter/api 与 API 层对齐时，应优先消费强语义字段，逐步淘汰兼容构造器

---

## D-011：scheduler 第二批把 Binance 与 Paper 的状态推进统一迁移到 OrderLifecycleService

- 日期：2026-03-12
- 状态：已决定
- 背景：第一批只收敛了 OKX 主验收通道，Binance reconcile / ws acceleration 与 Paper matching 仍保留旧式通用迁移路径，导致 scheduler 内仍然并存两套推进方式。
- 决策：
  - Binance reconcile / ws acceleration 全量改为调用 `OrderLifecycleService`
  - Paper matching 通过 `OrderExecutionGateway.markFilled(...)` 仅暴露有限终态动作
  - `OrderCommandService.transitionOrder(...)` 收口为 package-private，仅允许 core 内部与测试访问
- 影响模块：
  - `nq-core`
  - `nq-scheduler`
- 不选方案：
  - 不在本轮直接重写 `PaperMatchingService` 为新 executor 子系统，避免把最小 PR 升级成结构性大改
- 后续影响：
  - scheduler 后续新增路径若需要推进状态，必须先补语义方法，不允许重新暴露通用迁移接口

---

## D-012：local profile 允许 OKX adapter 在 bootstrap 外网失败时退回 stub 以保证应用可启动

- 日期：2026-03-12
- 状态：已决定
- 背景：`nq-app` local 启动时会构造 `OkxExchangeAdapter`，其默认行为会在 bean 初始化阶段访问 OKX `public/instruments`。在本地离线或受限网络环境下，这会导致 health 前置失败。
- 决策：
  - `application-local.yml` 新增 `nq.okx.adapter.stub-on-bootstrap-failure`
  - local 默认允许 OKX adapter 在 bootstrap 失败时退回 stub，实现“应用可启动，但 OKX 调用明确拒绝”
  - 真实本地 OKX 验收可通过显式设置 `NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=false` 恢复 fail-fast
- 影响模块：
  - `nq-app`
  - `nq-adapter-okx`
  - `nq-scheduler`
- 不选方案：
  - 不直接删除 OKX bean，也不在本轮引入新的 profile 分裂，以免把运行形态复杂化
- 后续影响：
  - GateD 正式 OKX 验收前，需要在可联网环境下关闭 stub fallback，验证真实通道完整可用

---

## D-013：adapter-api 第三批冻结执行字段名，旧 accessor 仅保留兼容别名

- 日期：2026-03-12
- 状态：已决定
- 背景：第二批已经把 contracts/core 的 `requestId / idempotencyKey / quantity / timeInForce / source` 收敛到强语义字段，但 `nq-adapter-api` 仍停留在 `qty / type` 等历史命名，导致 core 到 adapter 之间还存在第二套语义。
- 决策：
  - `AdapterOrderRequest` canonical 字段冻结为 `requestId / orderId / accountId / venue / symbol / clientOrderId / idempotencyKey / side / orderType / price / quantity / quoteQuantity / timeInForce / source / strategyRunId / traceId`
  - `AdapterCancelRequest` 增补 `requestId / reason`
  - `AdapterOrderAck / AdapterOrderSnapshot / AdapterTradeReport` 增补 `accountId / symbol / clientOrderId / externalStatus / quantity / rawPayload` 等最小执行闭环字段
  - `qty()`、`type()`、`status()`、旧构造器仅作为兼容别名保留，后续新代码不得再以其为事实来源
- 影响模块：
  - `nq-adapter-api`
  - `nq-core`
  - `nq-adapter-okx`
  - `nq-adapter-binance`
  - `nq-scheduler`
- 不选方案：
  - 不在第三批直接删除旧 accessor / 旧构造器，因为当前 adapter 测试和少量历史调用点仍依赖这些签名
- 后续影响：
  - 第四批以后新增 adapter 代码应优先使用 `orderType / quantity / externalStatus` 等 canonical 字段

---

## D-014：GateD 本地验收入口迁移到 `/__gated`，`/__gatec` 仅保留兼容别名

- 日期：2026-03-12
- 状态：已决定
- 背景：当前 `nq-app` 仍以 `GateCAcceptanceController` 和 `nq.gatec.verify.enabled` 作为本地验收入口，已与 GateD 阶段命名不一致，同时缺少最小订单查询视图来确认订单事实可读。
- 决策：
  - 本地验收 canonical route 迁移到 `/__gated`
  - `GateDAcceptanceController` 同时提供最小 `GET /orders/{orderId}` 查询视图
  - `nq.gated.verify.enabled` 作为 canonical 开关；`/__gatec` 路由与 `nq.gatec.verify.enabled` 仅作为兼容别名保留
  - local fallback 继续只用于“启动成功 + smoke 可跑”，不得在文档或日志中表述为 OKX 真实验收通过
- 影响模块：
  - `nq-app`
  - `nq-api`
  - `docs/gates/gate-d/*`
- 不选方案：
  - 不在第三批同步重命名所有 GateC DTO / profile 文件，避免 PR 被大面积机械改名淹没
- 后续影响：
  - 后续 app/api 冻结 PR 可以继续移除 GateC 兼容 DTO、旧 profile 命名和旧环境变量别名

---

## D-015：第四批把 nq-api 最小查询闭环扩展到 order / trade / position / account

- 日期：2026-03-12
- 状态：已决定
- 背景：第三批只提供了订单最小读视图，仍无法在本地验收中确认成交、持仓与账户快照是否已经联动落库，GateD 查询闭环仍然不完整。
- 决策：
  - `TradingQueryFacade` 冻结四类最小查询入口：`queryOrder / queryLatestTrade / queryPosition / queryAccount`
  - `nq-api` 本轮直接基于 `orders / trades / positions / account_snapshots` 提供只读 JDBC 查询
  - 不在本轮新增新的 query projection 模块，也不把 `nq-app` 直接绑到 scheduler / ledger service
- 影响模块：
  - `nq-api`
  - `nq-app`
  - `docs/gates/gate-d/CONTRACTS.md`
- 不选方案：
  - 不为了查询闭环引入新的 API schema 或复杂聚合服务，避免第四批从“最小可合并”滑向结构性扩面
- 后续影响：
  - 后续若要补正式查询 API，应继续以这四类最小视图为基线扩展，而不是重新发明字段语义

---

## D-016：第四批开始删除 GateC HTTP 兼容层，`__gated + nq.gated.verify.enabled + gated-verify` 成为唯一 canonical 入口

- 日期：2026-03-12
- 状态：已决定
- 背景：第三批虽然完成了 canonical route 与开关迁移，但 `GateC* DTO`、`__gatec` 路由别名、`NQ_GATEC_VERIFY_ENABLED` 与 `application-gatec-verify.yml` 仍然保留，继续制造阶段命名漂移。
- 决策：
  - 删除 source 中的 `__gatec` 路由映射、`NQ_GATEC_VERIFY_ENABLED` / `nq.gatec.verify.enabled` 旧开关别名、`application-gatec-verify.yml`
  - `GateC*` HTTP DTO/response 全量迁移到 `GateD*` 命名
  - regression test 允许继续直接请求 `__gatec`，但只用于断言 `404`，不再作为兼容运行入口
  - local fallback 说明保持不变：只用于启动与 smoke，不代表真实 OKX 验收通过
- 影响模块：
  - `nq-app`
  - `docs/gates/gate-d/CONTRACTS.md`
  - `docs/gates/gate-d/WORK.md`
- 不选方案：
  - 不在本轮继续删除 adapter 层旧 accessor / 旧构造器，避免把第四批扩大为 adapter 大清理
- 后续影响：
  - 下一轮兼容层清理的主战场收敛到 adapter 旧 accessor / 旧构造器与少量历史测试辅助代码

---

## D-017：第五批以 TradeLedgerPostingService 为本地 account snapshot 唯一产出入口

- 日期：2026-03-12
- 状态：已决定
- 背景：第四批已经补了 `GET /__gated/accounts/{accountId}` 读链，但本地 runtime 一直返回 `404`。排查后确认：
  - PAPER 成交已经通过 `PaperMatchingService -> TradeLedgerGateway -> TradeLedgerPostingService`
  - `nq-api` 的 account 查询 SQL 本身可用
  - 根因是 `account_snapshots` 只有表和读逻辑，没有任何写入链路
- 决策：
  - 第五批在 `TradeLedgerPostingService` 内补最小账户快照写入
  - base 资产余额来自最新 `positions` 投影
  - quote / fee 资产余额来自 `ledger_entries` 聚合余额
  - 本轮只保证本地 PAPER 可验证闭环，不把真实 OKX/Binance account sync 一起做满
- 影响模块：
  - `nq-ledger`
  - `nq-api`
  - `nq-app`
- 不选方案：
  - 不在本轮新建独立 account projection service，避免最小 PR 演变成新的读写子系统
  - 不依赖 `NoopAccountAdapter` 补本地数据，因为它不是 PAPER 成交后的事实来源
- 后续影响：
  - 真实交易所账户同步路径后续仍可接入 adapter/account sync，但本地 GateD 验收先以 ledger + position 导出的最小快照为准

---

## D-018：第六批在固定本地 account snapshot 双资产断言后，开始删除 adapter-api 旧 alias

- 日期：2026-03-12
- 状态：已决定
- 背景：第五批已经把 `GET /__gated/accounts/{accountId}` 从 `404` 推进到可返回真实快照，但相关断言仍主要依赖手工 smoke；同时 `nq-adapter-api` 仍保留 `qty()/type()/status()` 和多组旧构造器，继续制造 canonical 字段与历史别名并存。
- 决策：
  - 在 `nq-ledger / nq-api / nq-app` 测试中固定 `BTC / USDT` 两类账户快照结果，避免本地 account snapshot 再次回退为“有接口、无稳定断言”
  - 删除 `AdapterOrderRequest.qty()/type()` 与其旧构造器
  - 删除 `AdapterOrderSnapshot.status()` 与其旧构造器
  - 删除 `AdapterOrderAck` 旧构造器与 `ts()` 兼容访问器
  - 删除 `AdapterCancelRequest` 旧构造器
  - 删除 `AdapterTradeReport` 旧构造器与 `qty()` 兼容访问器
- 影响模块：
  - `nq-adapter-api`
  - `nq-adapter-binance`
  - `nq-adapter-okx`
  - `nq-core`
  - `nq-scheduler`
  - `nq-ledger`
  - `nq-api`
  - `nq-app`
- 不选方案：
  - 不在本轮继续清理 `contracts/core`、`api view` 等非 adapter 层的历史 `qty()/type()/status()` 访问器，避免第六批从 adapter 收口滑向跨模块机械改名
  - 不把本地双资产断言升级成真实 venue account sync 验收，避免越过 GateD 当前边界
- 后续影响：
  - 下一轮若继续清理兼容层，应优先处理 `contracts/core` 与 `api view` 中仍保留的历史访问器
  - `okx_adapter_bootstrap_fallback_enabled` 仍只代表 local smoke fallback，不代表真实 OKX 验收通过

---

## D-019：第七批删除 contracts / core / api view 的 `qty()` 历史访问器，`nq-app` 请求 DTO 暂保留

- 日期：2026-03-12
- 状态：已决定
- 背景：
  - 第六批已经删除 adapter-api 的旧 alias，但 `PlaceOrderCommand`、`PlaceOrderRequest` 以及 `OrderView / TradeView / PositionView` 仍保留 `qty()` 兼容访问器。
  - 这些 accessor 会继续把上层调用方留在历史命名上，削弱 GateD 已冻结的 canonical `quantity` 语义。
  - 与此同时，`GateDOrderHttpRequest` 仍以 `qty / type` 暴露本地验收请求体；若本轮继续改它，会把“小范围 alias 清理”扩成新的入口协议变更。
- 决策：
  - 删除 `PlaceOrderCommand.qty()`。
  - 删除 `PlaceOrderRequest.qty()`。
  - 删除 `OrderView / TradeView / PositionView` 中的 `qty()` 兼容访问器。
  - 风控规则与其余直接消费者统一切到 `quantity()`。
  - `GateDOrderHttpRequest.qty / type` 本轮暂不动，继续作为本地验收 HTTP 兼容字段保留。
- 影响模块：
  - `nq-contracts`
  - `nq-core`
  - `nq-risk`
  - `nq-api`
  - `nq-app`
- 不选方案：
  - 不在本轮继续修改 `__gated` 主入口或 HTTP DTO，避免把本轮变成新的 app/api 入口改造。
  - 不把 alias 清理扩展到真实 venue account sync，保持 GateD 边界不偏移。
- 后续影响：
  - 下一轮若继续清理历史命名，应优先处理 `nq-app` 请求 DTO 中残留的 `qty / type` 字段与示例请求。
  - `OrderRecord / PositionProjection / TradeLedgerRequest` 等领域模型中的 `qty()` 仍按既有领域命名保留，不在本轮以“上层兼容 alias”对待。

---

## D-020：第八批把 `GateDOrderHttpRequest` 收口到 `orderType / quantity`，旧 `type / qty` 仅保留 JSON alias

- 日期：2026-03-12
- 状态：已决定
- 背景：
  - 第七批已经把 `contracts / core / api view` 的 `qty()` 历史访问器清理完毕，但 `nq-app` 的本地验收请求 DTO 仍保留 `qty / type`。
  - 若继续让请求层停留在历史命名，`__gated` 虽然路由已 canonical，但本地入口语义仍会和上层 contracts/core 的 `quantity / orderType` 产生二义性。
- 决策：
  - `GateDOrderHttpRequest` 记录字段改为 `orderType / quantity`。
  - `GateDAcceptanceController` 只读取 `orderType()` 与 `quantity()`。
  - 为避免同一批次打断所有本地脚本，旧 `type / qty` 仅通过 Jackson `JsonAlias` 暂时兼容。
- 影响模块：
  - `nq-app`
  - `docs/gates/gate-d/CONTRACTS.md`
  - `docs/gates/gate-d/WORK.md`
- 不选方案：
  - 不直接移除旧 JSON 输入兼容，避免本地 smoke、示例脚本和手工请求在同一批次全部失效。
  - 不继续改 `__gated` 路由、response 语义或真实 venue account sync。
- 后续影响：
  - 下一轮若继续清理请求层兼容债务，应评估是否移除 `JsonAlias(type/qty)`，并同步更新本地请求示例与 smoke 文档。

---

## D-021：第九批删除 `GateDOrderHttpRequest` 的 `JsonAlias(type/qty)`，本地示例请求只保留 canonical 字段

- 日期：2026-03-12
- 状态：已决定
- 背景：
  - 第八批虽然已经把 `GateDOrderHttpRequest` 的代码字段收口到 `orderType / quantity`，但仍保留 `JsonAlias(type/qty)`。
  - 这会让请求层继续存在“代码字段已 canonical、输入样例仍可漂移”的双轨状态，不利于冻结本地验收入口契约。
- 决策：
  - 删除 `GateDOrderHttpRequest` 上的 `JsonAlias("type") / JsonAlias("qty")`。
  - 删除对应的兼容测试输入。
  - 本地示例请求、测试输入与 smoke 文档统一改成 `orderType / quantity`。
- 影响模块：
  - `nq-app`
  - `docs/gates/gate-d/CONTRACTS.md`
  - `docs/gates/gate-d/WORK.md`
- 不选方案：
  - 不继续保留 alias 作为“长期兼容层”，避免 GateD 本地验收入口永远处于半冻结状态。
  - 不继续改 `__gated` 主入口 route / response，也不扩到真实 venue account sync。
- 后续影响：
  - 请求层兼容壳在 `GateDOrderHttpRequest` 上已清空；下一轮若继续清理，重点应转向无效示例或历史脚本，而不是再回到 DTO 字段层。

---

## D-022：第十批清理仓库内现行 smoke 脚本的旧 `type / qty / __gatec / NQ_GATEC_VERIFY_ENABLED` 示例

- 日期：2026-03-12
- 状态：已决定
- 背景：
  - 第九批已经删除 `GateDOrderHttpRequest` 的 `JsonAlias(type/qty)`，请求层代码与测试已只接受 canonical `orderType / quantity`。
  - 但仓库内仍有现行 smoke 脚本 `scripts/gatec_okx_dome_verify.ps1` 使用旧 `__gatec` route、`NQ_GATEC_VERIFY_ENABLED` 开关以及 `type / qty` 请求体示例。
  - 如果不清理这些示例，开发者会继续被过时样例误导，造成“代码已冻结、脚本仍漂移”的文档债务。
- 决策：
  - 更新现行 smoke 脚本中的请求体示例，统一改为 `orderType / quantity`。
  - 同步把脚本中的 canonical route / verify switch 示例改为 `__gated` 与 `NQ_GATED_VERIFY_ENABLED`。
  - `docs/gates/gate-d/CONTRACTS.md` 同步声明：本地脚本、手工 smoke 文档与 curl 示例不得再传播旧命名。
- 影响模块：
  - `scripts`
  - `docs/gates/gate-d`
- 不选方案：
  - 不修改历史 Gate 冻结快照中的 `type / qty / __gatec` 记录，避免篡改历史阶段事实。
  - 不在本轮扩到真实 venue account sync，也不继续改 `__gated` 主入口业务语义。
- 后续影响：
  - 当前仍允许暂留的旧命名主要只剩历史 Gate 文档记录与 `__gatec -> 404` regression test 断言。
  - 若后续要继续清理，应优先处理脚本文件名或历史非 Source-of-Truth 文档归档，而不是再动业务主链。

---

## D-023：第十一批把现行 OKX Dome 验证脚本重命名为 GateD 命名，并为根级 GateA 文档补 archive 标识

- 日期：2026-03-13
- 状态：已决定
- 背景：
  - 第十批已经清理了现行脚本里的 `type / qty / __gatec / NQ_GATEC_VERIFY_ENABLED` 示例，但文件名 `scripts/gatec_okx_dome_verify.ps1` 仍会误导当前阶段。
  - 根级 `docs/CONTRACTS.md` 也仍以普通文档形态存在，虽然内容明确属于 Gate A，却容易被误读为当前 Source of Truth。
- 决策：
  - 将现行脚本重命名为 `scripts/gated_okx_dome_verify.ps1`，使文件名与当前阶段入口语义保持一致。
  - 在根级 `docs/CONTRACTS.md` 增加 archive 标识，明确其为 Gate A 历史留档，当前阶段应转向 `docs/current/*` 与 `docs/gates/gate-d/CONTRACTS.md`。
  - `README.md` 同步补充根级 GateA 文档的 archive 边界说明。
- 影响模块：
  - `scripts`
  - `docs`
  - `README.md`
- 不选方案：
  - 不在本轮移动整批根级 GateA 文档到新目录，避免把“历史标识清理”扩成大规模文档迁移。
  - 不触碰 `nq-core / nq-api / nq-ledger / nq-scheduler` 业务逻辑。
- 后续影响：
  - 现行示例脚本的命名歧义已收口；后续若继续清历史债务，可评估是否为其他根级 GateA 文档补统一 archive banner。
  - 历史 GateC 文档中对旧脚本文件名的引用仍作为冻结记录保留，不再代表当前可执行说明。

---

## D-024：第十二批统一为根级历史 GateA 文档补 archive banner，仅保留根级导航文档不动

- 日期：2026-03-13
- 状态：已决定
- 背景：
  - 第十一批已经处理了最容易误导当前阶段的两个点：现行脚本文件名与根级 `docs/CONTRACTS.md`。
  - 但根级 `docs/*.md` 里仍有多份 GateA 历史文档，例如 `DB_SCHEMA.md`、`DECISIONS.md`、`WORK.md`，如果没有明确 archive 标识，仍可能被误读为当前事实来源。
  - 同时，`docs/ARCHITECTURE.md`、`docs/MODULES.md`、`docs/README.md` 仍承担顶层说明或 GateD 对齐导航职责，不适合直接整体标成 archive。
- 决策：
  - 为明确属于根级历史留档的 GateA 文档统一增加 archive banner。
  - 保留 `docs/ARCHITECTURE.md`、`docs/MODULES.md`、`docs/README.md` 作为顶层说明入口，不将它们整体标记为 archive。
  - 在 `docs/README.md` 中补充总边界说明，明确根级旧文档只作 archive 参考。
- 影响模块：
  - `docs`
  - `README.md`（若需导航说明）
- 不选方案：
  - 不把全部根级 `docs/*.md` 一次性搬迁到新目录，避免扩大为大规模文档迁移。
  - 不把根级 GateD 对齐说明文档与 GateA 历史留档混为一谈。
- 后续影响：
  - 当前根级历史 GateA 文档的误导风险已明显下降。
  - 后续若继续清文档债务，可评估是否为 `docs/ARCHITECTURE.md / MODULES.md` 单独建立“顶层导航文档”定位说明。

---

## D-025：第十三批将根级 `ARCHITECTURE.md / MODULES.md` 定位为“顶层导航摘要”，不作为 current 或 archive 事实源

- 日期：2026-03-13
- 状态：已决定
- 背景：
  - 第十二批已经为根级历史 GateA 文档补齐 archive banner，但有两份根级文档不能简单归入 archive：`docs/ARCHITECTURE.md` 与 `docs/MODULES.md`。
  - 它们内容已经对齐 GateD，用于快速概览当前架构与模块边界；若直接标成 archive，会丢掉顶层导航价值。
  - 但如果继续以普通正文存在，又容易和 `docs/current/*`、`docs/gates/gate-d/*` 的权威文档角色重叠。
- 决策：
  - 将 `docs/ARCHITECTURE.md` 与 `docs/MODULES.md` 明确定位为“顶层导航摘要 / 辅助索引”。
  - 在两份文档顶部增加 `Top-Level Navigation Notice`，声明其不是当前阶段的 Source of Truth。
  - `docs/README.md` 同步声明：这两份文档属于导航摘要，不属于 archive，但若与当前 Gate 文档冲突，以后者为准。
- 影响模块：
  - `docs`
- 不选方案：
  - 不把这两份文档整体打成 archive，因为它们仍有快速导航价值。
  - 不把它们上升为 current 事实源，避免削弱 `docs/current/*` 与 `docs/gates/gate-d/*` 的唯一入口地位。
- 后续影响：
  - 根级文档分层将更清晰：history archive、top-level navigation、current source 三类角色不再混淆。
  - 后续若继续清文档债务，应优先保持三类角色边界稳定，而不是反复改入口定义。

---

## D-026：第十四批将 GateD checklist 从“全未勾选草稿”切换为“按事实显式状态回填”

- 日期：2026-03-13
- 状态：已决定
- 背景：
  - `docs/current/GATE_CHECKLIST.md` 与 `docs/gates/gate-d/GATE_D_CHECKLIST.md` 长期停留在“全部未勾选”的草稿态，已经无法反映 GateD 迄今真实推进结果。
  - 继续维持草稿态会让使用者误以为 pre-trade 风控、`__gated` 入口、account snapshot 本地闭环等能力尚未实施，削弱 checklist 作为阶段门禁与冻结依据的价值。
- 决策：
  - 两份 checklist 统一采用显式状态约定：`[x] 已完成`、`[~] 部分完成`、`[ ] 未完成`。
  - `docs/current/GATE_CHECKLIST.md` 负责反映当前 GateD 工作入口的实际完成度。
  - `docs/gates/gate-d/GATE_D_CHECKLIST.md` 负责在不改写阶段定义的前提下，补充 GateD 卷宗级状态回填摘要，并把冻结状态明确标为“进行中”。
- 影响文档：
  - `docs/current/GATE_CHECKLIST.md`
  - `docs/gates/gate-d/GATE_D_CHECKLIST.md`
  - `docs/gates/gate-d/WORK.md`
- 不选方案：
  - 不继续保留“全部未勾选，进度靠 WORK 猜”的做法，因为这会让 checklist 失去阶段门禁意义。
  - 不借本轮 checklist 更新重新定义 GateD 范围，只按当前已实现事实同步状态。
- 后续影响：
  - 后续每批最小可合并改动若改变 GateD 关键能力状态，必须同步更新对应 checklist 条目，而不是只回填 `WORK.md`。
  - 真实 OKX 验收、深层兼容债务清理、全量工程门禁通过前，相关 checklist 条目应继续保持 `部分完成` 或 `未完成`。

---

## D-027：第十五批要求 current README 与 gate README 仅维护“状态摘要”，并与 checklist 保持同源一致

- 日期：2026-03-13
- 状态：已决定
- 背景：
  - 第十四批已经把 `docs/current/GATE_CHECKLIST.md` 与 `docs/gates/gate-d/GATE_D_CHECKLIST.md` 切到显式状态回填。
  - 如果入口文档 `docs/current/README.md` 与 `docs/gates/gate-d/README.md` 仍停留在“只讲目标、不讲当前状态”的写法，读者仍然需要在 README 与 checklist 之间自行推断真实进度。
  - 但如果 README 再独立维护一套详细门禁，又会重新制造第二套事实源。
- 决策：
  - `docs/current/README.md` 与 `docs/gates/gate-d/README.md` 只维护入口级“状态摘要”。
  - README 中的状态摘要必须与对应 checklist 的关键状态一致，不得单独发明新的完成度口径。
  - 详细门禁、冻结条件与细项状态仍以 checklist 为准。
- 影响文档：
  - `docs/current/README.md`
  - `docs/gates/gate-d/README.md`
  - `docs/current/GATE_CHECKLIST.md`
  - `docs/gates/gate-d/GATE_D_CHECKLIST.md`
- 不选方案：
  - 不把 README 扩写成第二份 checklist，避免入口文档与门禁文档长期漂移。
  - 不借 README 状态同步顺手改业务主链或真实 venue 验收结论。
- 后续影响：
  - 后续若 GateD 关键能力状态发生变化，应优先更新 checklist，再同步 README 摘要。
  - README 的职责继续限定为入口导航与阶段摘要，不承担细粒度冻结判定。

---

## D-028：第十六批要求 `PR_SPLIT_PLAN / TEST_CASES / SOURCES` 同步到当前事实，但继续只承担文档治理职责

- 日期：2026-03-13
- 状态：已决定
- 背景：
  - 前十五批已经把 checklist 与 README 的阶段状态同步起来，但 `PR_SPLIT_PLAN.md` 仍是纯草稿式计划、`TEST_CASES.md` 仍未标当前用例落地状态、`SOURCES.md` 仍未反映 current / top-level navigation / archive 的最新边界。
  - 如果这三份文档不跟进，会继续出现“入口文档已同步、辅助治理文档仍停留在旧语义”的断层。
- 决策：
  - `PR_SPLIT_PLAN.md` 切换为状态版执行计划，显式标明 `已完成 / 进行中 / 未开始`。
  - `TEST_CASES.md` 显式标明当前用例状态，并把已经落地的本地闭环与 `BTC / USDT` 双资产断言写入用例预期。
  - `SOURCES.md` 按 `current source / top-level navigation / archive / code / external` 重新分层。
- 影响文档：
  - `docs/gates/gate-d/PR_SPLIT_PLAN.md`
  - `docs/gates/gate-d/TEST_CASES.md`
  - `docs/gates/gate-d/SOURCES.md`
  - `docs/gates/gate-d/WORK.md`
- 不选方案：
  - 不借本轮文档对齐顺手修改业务主链、真实 venue 验收结论或 `__gated` 入口语义。
  - 不把 `PR_SPLIT_PLAN` 直接改写成冻结结论，因为 PR-8 仍未完成。
- 后续影响：
  - 后续若阶段状态变化，除 checklist 与 README 外，`PR_SPLIT_PLAN / TEST_CASES / SOURCES` 也需要同步维护。
  - 这三份文档继续只承担治理与追溯职责，不作为新的业务事实源。

---

## D-029：顶层导航摘要必须显式声明维护约束，防止重新漂移成事实来源

- 日期：2026-03-13
- 状态：已决定
- 背景：
  - 第十三批已经把 `docs/ARCHITECTURE.md` 与 `docs/MODULES.md` 定位为 top-level navigation，但当时只拆清了角色，没有把“何时更新、承载什么、不承载什么”写成维护约束。
  - 如果缺少这层约束，两份顶层摘要后续仍可能在多批改动中逐步漂移，并重新膨胀成与 `docs/current/*`、`docs/gates/gate-d/*` 并行的第二套事实来源。
- 决策：
  - `docs/ARCHITECTURE.md` 必须显式声明：只保留高层概览，不承载细粒度冻结条件、详细契约、详细测试状态。
  - `docs/MODULES.md` 必须显式声明：只保留模块级导航摘要，不承担详细阶段状态或详细实施计划。
  - 两份文档都必须显式声明：若与 `docs/current/*` 或 `docs/gates/gate-d/*` 冲突，以后者为准。
  - 当执行链路、模块边界、主战场模块优先级、验收状态发生明显变化时，应同步检查这两份摘要是否需要更新。
- 影响文档：
  - `docs/ARCHITECTURE.md`
  - `docs/MODULES.md`
  - `docs/gates/gate-d/WORK.md`
- 不选方案：
  - 不把这两份文档重新提升为 current source of truth。
  - 不把它们直接归入 archive，因为它们仍承担顶层导航摘要职责。
- 后续影响：
  - 以后每批只要改动执行链路、模块边界或阶段状态，都应把“同步检查顶层导航摘要”作为 README/checklist 回填后的常规动作。

---

## D-030：OKX dome/real 双环境切换在启动脚本层归一到单套运行时变量

- 日期：2026-03-13
- 状态：已决定
- 背景：
  - 第十八批已经把 OKX 最小验收脚本从旧 GateC 工件路径切到 canonical non-fallback 启动路径，但当前 `.env` 中仍同时存在 `NQ_OKX_DOME_* / NQ_OKX_REAL_*` 两套命名。
  - 直接 `mvn spring-boot:run` 时，进程不会自动读取 `.env`；如果继续让代码层直接感知两套命名，dome/real 切换会分散在脚本、配置、adapter 多处，后续极易漂移。
- 决策：
  - `.env` 允许同时保留 `NQ_OKX_DOME_*` 与 `NQ_OKX_REAL_*` 两套来源变量。
  - 启动脚本负责显式加载 `.env`、读取 `NQ_OKX_ENV=dome|real`，并把当前所选环境归一映射到：
    - `NQ_OKX_API_KEY`
    - `NQ_OKX_API_SECRET`
    - `NQ_OKX_API_PASSPHRASE`
    - `NQ_OKX_BASE_URL`
    - `NQ_OKX_WS_URL`
  - `application*.yml` 只声明并读取这套统一变量；真实敏感值仍只从环境变量注入，不写入仓库。
  - `nq-adapter-okx` 运行时配置优先读取统一变量，再回退到历史 `DOME/REAL` 专属命名，确保新旧启动路径可兼容过渡。
  - `NQ_OKX_ENV=real` 时，必须强制 `NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=false`；缺失真实凭证时应显式失败，不允许静默 fallback。
- 影响文件：
  - `scripts/gated_okx_dome_verify.ps1`
  - `backend/nq-app/src/main/resources/application.yml`
  - `backend/nq-app/src/main/resources/application-local.yml`
  - `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxRuntimeConfig.java`
- 不选方案：
  - 不让业务代码直接处理 `DOME/REAL` 两套命名并在各处自己选环境。
  - 不把真实敏感值写入 `application*.yml`。
  - 不在本轮顺手扩到真实 venue account sync、迁移闭环或兼容债务清理。
- 后续影响：
  - 后续真实 OKX 验收、local smoke、real 切换都应复用同一套统一运行时变量，避免再次出现“脚本与应用看到的是两套不同配置”的问题。

---

## D-031：真实 OKX 验收脚本在 Windows 上统一切换到 PowerShell 7 执行

- 日期：2026-03-13
- 状态：已决定
- 背景：
  - 第二十批真实 OKX 最小验收预检查中，`gated_okx_dome_verify.ps1` 在当前 `pwsh 7` 下能正确解析仓库 `.env`，但通过 `powershell.exe 5.1` 直接执行时，会把 UTF-8 `.env` 读错，导致脚本误判 `NQ_OKX_DOME_* / NQ_OKX_REAL_*` 缺失。
  - 该问题会制造“脚本缺凭证”的伪阻塞，掩盖真实 OKX 启动链上的真正问题。
- 决策：
  - `gated_okx_dome_verify.ps1` 在 Windows 上若检测到当前解释器为 `powershell.exe 5.1`，应自动切换到 `pwsh 7` 继续执行。
  - 验收脚本仍保持同一 canonical 路径；不引入第二份平行脚本，不把 `.env` 解析分散到多套实现里。
- 影响文件：
  - `scripts/gated_okx_dome_verify.ps1`
- 不选方案：
  - 不继续为 `powershell.exe 5.1` 单独维护一套 `.env` 解析逻辑。
  - 不把“5.1 解析失败”误记成真实 OKX 验收失败。
- 后续影响：
  - 后续 Windows 上的真实 OKX 验收，应默认以 `pwsh 7` 作为 canonical 脚本解释器。
  - 脚本若再次失败，应优先视为真实环境/连通性问题，而不是解释器兼容问题。

---

## D-032：PR-8 冻结批将数据库基线正式收口为 `V1 -> V4`，不再预设额外 GateD migration

- 日期：2026-03-15
- 状态：已决定
- 背景：此前 checklist / README 用“例如 `V5__gate_d_execution_closure.sql`”表述 GateD migration，容易让后续实施者误以为 GateD 冻结必须再补一条 schema 变更；但当前代码基线已经通过 `mvn test / verify` 与 Flyway 新库 init / 老库 upgrade 验证，且未暴露新的 schema 差异需求。
- 决策：
  - 将 GateD 当前数据库冻结基线正式收口为 `V1 -> V4`
  - PR-8 真实验证口径固定为：
    - 空库可直接迁到 `V4`
    - 旧库可从 `V3` 平滑升级到 `V4`
  - 不再把“必须新增 `V5` migration”作为 GateD 冻结前置条件
- 影响文档：
  - `docs/current/README.md`
  - `docs/current/GATE_CHECKLIST.md`
  - `docs/gates/gate-d/README.md`
  - `docs/gates/gate-d/GATE_D_CHECKLIST.md`
  - `docs/gates/gate-d/PR_SPLIT_PLAN.md`
  - `docs/gates/gate-d/WORK.md`
- 后续影响：
  - 后续若出现真实 schema 变更，再以 GateE 或独立治理批新增 migration；不得为了迎合历史占位描述而制造空迁移
