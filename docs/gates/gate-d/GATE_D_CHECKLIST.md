# GATE_D_CHECKLIST

> GateD 名称：**执行闭环与执行域硬化（Execution Closure & Execution Domain Hardening）**
>
> GateD 目标：在 GateC 已完成交易所接入与统一适配基座的前提下，建立统一执行入口、前置硬风控、订单状态推进、成交回写、账户/持仓同步、补偿收敛与审计留痕能力，形成可验证、可冻结、可复盘的最小交易执行闭环。
>
> 当前主验收通道：**OKX + PAPER**
>
> 当前业务范围：**数字货币现货**
>
> 当前冻结原则：**先闭环、后扩边；先正确、后提速；先统一、后多样。**
>
> 状态约定：`[x] 已完成`、`[~] 部分完成`、`[ ] 未完成`。  
> 当前状态回填基线：**截至 2026-03-13 的已实现与已验证事实**。

---

# 0. 当前状态回填（截至 2026-03-13）

- [x] pre-trade 风控规则链已落地，`KillSwitch / AccountTradingEnabled / SymbolEnabled / OrderPrecision / MinNotional / MaxOrderAmount / DuplicateRequest / RateLimit` 均已实现
- [~] lifecycle 主通道已收口到 `OrderCommandService + OrderLifecycleService`，OKX / Binance / Paper 主路径已迁移，但 `query-confirm / trade-report` 的冻结与验收仍未全部完成
- [x] `nq-adapter-api` canonical 契约已冻结一轮，`requestId / idempotencyKey / orderType / quantity / quoteQuantity / timeInForce / source` 已成为当前事实字段
- [x] `__gated` 已成为 canonical 本地验收入口，`nq.gated.verify.enabled` 已成为 canonical verify 开关
- [x] order / trade / position / account 本地最小闭环已打通，`/__gated/orders`、`/__gated/orders/{orderId}`、`/__gated/orders/{orderId}/trade`、`/__gated/positions/{accountId}/{symbol}`、`/__gated/accounts/{accountId}` 均已有本地验证
- [x] account snapshot 本地产出链已打通，PAPER 成交后可生成 `account_snapshots` 并被查询接口读到
- [x] 请求层 canonical `orderType / quantity` 已完成，`GateDOrderHttpRequest` 的旧 `type / qty` 字段、访问器与 `JsonAlias` 均已删除
- [x] 现行脚本与示例已 canonical 化，当前脚本与 smoke 示例统一使用 `__gated + NQ_GATED_VERIFY_ENABLED + orderType / quantity`
- [x] current / top-level navigation / archive 三类文档边界已建立
- [ ] 真实 OKX 验收未完成，local fallback 不能视为真实通道通过
  当前已补 canonical non-fallback 启动路径、`.env -> dome|real -> NQ_OKX_API_*` 统一映射、query/reconcile/recovery 最小观察点，以及移除旧 GateC 工件路径依赖；本机在 `dome / real` 两种模式下都能推进到真实 OKX bootstrap，但仍因 `Permission denied: getsockopt` 阻断，不能视为真实通道通过。
- [~] 深层兼容债务仍有残留，主要集中在内部领域命名、部分旧构造器与 `__gatec -> 404` regression test 断言

---

# 1. GateD 阶段定位

## 1.1 GateD 解决的问题

GateD 解决的是“系统已经接上交易所，但还没有形成稳定、统一、可追踪执行闭环”的问题。

GateD 完成后，系统必须具备以下能力：

- 接收标准化订单请求
- 执行前进行硬风控校验
- 路由到统一执行器（PAPER / OKX）
- 写入订单与执行事件
- 接收订单回报与成交回报
- 推进订单状态机
- 更新成交、账户、持仓与账本投影
- 在 WS 丢失、回报延迟、状态不一致等场景下进行补偿收敛
- 保证全链路日志、事件、状态可追踪可复盘

---

## 1.2 GateD 与 GateC 的边界

GateC 已完成的内容重点是：

- 统一适配器接口
- 交易所 REST / WS 接入
- 字段映射
- 适配器骨架
- 基础调度、恢复、对账支撑能力

GateD 新增并冻结的内容重点是：

- 统一执行入口
- 订单执行主链路
- 前置硬风控
- 订单状态机硬化
- 交易回报到领域模型的闭环更新
- 账户 / 持仓 / 账本投影联动
- 执行补偿与收敛规则
- PAPER 与 LIVE 的统一执行抽象
- 审计与链路可观测性

---

## 1.3 GateD 不做的事情

GateD 明确不包含以下内容：

- Alpha 研究
- 回测引擎
- 因子系统
- 组合优化
- 合约 / 杠杆 / 期货执行
- 多交易所深度齐平扩展
- 高频低延迟极限优化
- 前端交易台完整建设
- 跨账户组合级复杂风控

---

# 2. GateD 进入条件（Entry Criteria）

以下条件全部满足，GateD 才允许正式进入实施态：

- [ ] GateC 已冻结完成，且已有明确冻结记录
- [ ] GateC 对应的 README / WORK / ROADMAP 已存在并可引用
- [ ] 当前根文档已统一将 GateD 定义为“执行闭环与执行域硬化”
- [ ] `docs/current/README.md` 已切换到 GateD
- [ ] `docs/current/GATE_CHECKLIST.md` 已切换到 GateD
- [ ] `docs/gates/gate-d/README.md` 已建立
- [ ] `docs/gates/gate-d/WORK.md` 已建立
- [ ] 当前代码基线至少具备以下基座能力：
    - [ ] 统一 adapter 接口
    - [ ] 核心下单命令服务或等价入口
    - [ ] 订单状态机初版
    - [ ] 交易所适配器可发单 / 撤单 / 查单
    - [ ] 基础 reconcile / recovery 能力
    - [ ] ledger / position 基础投影链路
- [ ] 当前主验收通道已确定为 OKX + PAPER
- [ ] 当前业务范围已确定为数字货币现货
- [ ] GateD 期间禁止把研究 / 回测内容混入主线

---

# 3. GateD 范围清单（In Scope）

## 3.1 统一执行入口

- [ ] 建立统一执行域入口，作为订单执行的唯一主入口
- [ ] 统一执行入口负责串联：
    - [ ] 请求接收
    - [ ] 参数标准化
    - [ ] 幂等校验
    - [ ] 前置风控
    - [ ] 执行器路由
    - [ ] 订单创建
    - [ ] 回执处理
    - [ ] 状态推进
    - [ ] 事件落盘
- [ ] 执行入口必须区分“下单编排”和“撤单编排”
- [ ] 执行入口必须具备 trace_id / request_id 贯穿能力

---

## 3.2 前置硬风控

- [ ] 建立 GateD 的前置硬风控服务
- [ ] 风控规则必须支持统一注册与有序执行
- [ ] 至少落地以下规则：
    - [ ] SymbolEnabledRule：交易对启用校验
    - [ ] AccountTradingEnabledRule：账户可交易校验
    - [ ] OrderPrecisionRule：价格 / 数量精度校验
    - [ ] MinNotionalRule：最小名义金额校验
    - [ ] MaxOrderAmountRule：单笔最大下单额校验
    - [ ] DuplicateRequestRule：重复请求校验
    - [ ] RateLimitRule：限频校验
- [ ] 风控失败必须返回明确错误码
- [ ] 风控失败必须返回明确失败消息
- [ ] 风控拒绝必须写入日志与执行事件

---

## 3.3 统一执行器抽象

- [ ] 建立统一执行器抽象，屏蔽 PAPER / LIVE 差异
- [ ] 执行器至少支持：
    - [ ] submit
    - [ ] cancel
    - [ ] query
- [ ] PAPER 执行器与 OKX 执行器必须遵循相同接口
- [ ] Binance 适配保持兼容，但不作为 GateD 主验收对象
- [ ] 执行器返回模型必须统一，不允许应用层感知交易所私有差异

---

## 3.4 订单状态机硬化

- [ ] 建立 GateD 订单状态机正式定义
- [ ] 明确区分：
    - [ ] 本地过程状态
    - [ ] 外部事实状态
    - [ ] 终态
    - [ ] 可补偿态
- [ ] 至少支持以下状态集合（命名可在实现中微调，但语义必须等价）：
    - [ ] CREATED
    - [ ] RISK_REJECTED
    - [ ] PENDING_SUBMIT
    - [ ] SUBMIT_FAILED
    - [ ] SUBMITTED
    - [ ] PARTIALLY_FILLED
    - [ ] FILLED
    - [ ] CANCEL_PENDING
    - [ ] CANCELED
    - [ ] REJECTED
    - [ ] EXPIRED（如交易所存在）
- [ ] 明确每个状态的进入条件
- [ ] 明确每个状态的允许迁移方向
- [ ] 明确终态判定规则
- [ ] 明确重复回报、乱序回报、延迟回报的处理规则

---

## 3.5 成交、账户、持仓、账本闭环

- [ ] 成交回报必须能驱动订单状态更新
- [ ] 成交回报必须能驱动 trade 记录持久化
- [ ] 成交回报必须能驱动 ledger posting
- [ ] 成交回报必须能驱动持仓投影更新
- [ ] 账户同步必须能形成 account snapshot
- [ ] 持仓同步必须能形成 position snapshot
- [ ] 幂等处理必须确保重复成交回报不产生重复记账
- [ ] 回放同一执行事件不得产生双写或脏写

---

## 3.6 补偿、恢复、收敛

- [ ] 明确 reconcile 负责的状态集合
- [ ] 明确 recovery 在进程重启 / 启动后的处理范围
- [ ] 明确 WS acceleration 与 REST query-confirm 的衔接关系
- [ ] 明确 WS 丢失场景下的降级策略
- [ ] 明确本地状态落后于交易所状态时的收敛规则
- [ ] 明确本地状态超时未闭合时的补偿触发规则
- [ ] 补偿链路必须可审计、可追踪
- [ ] 补偿不能引入无限重试风暴
- [ ] 补偿必须具备去重与节流机制

---

## 3.7 PAPER 执行闭环

- [ ] PAPER 执行器必须可独立运行
- [ ] PAPER 执行器必须支持：
    - [ ] 正常成交
    - [ ] 部分成交
    - [ ] 拒单
    - [ ] 撤单
- [ ] PAPER 执行器必须产出统一回执模型
- [ ] PAPER 执行链路必须接入同一套状态机
- [ ] PAPER 执行链路必须接入同一套 ledger / position / event 流程
- [ ] PAPER 必须可用于本地联调和回归测试

---

## 3.8 审计与可观测性

- [ ] 全链路必须具备 trace_id
- [ ] 关键执行节点必须记录 request_id / client_order_id / external_order_id
- [ ] 以下动作必须有事件记录：
    - [ ] 接收请求
    - [ ] 风控通过
    - [ ] 风控拒绝
    - [ ] 发单
    - [ ] 发单失败
    - [ ] 收到订单回报
    - [ ] 收到成交回报
    - [ ] 状态迁移
    - [ ] 补偿触发
    - [ ] 补偿完成
    - [ ] 撤单请求
    - [ ] 撤单结果
- [ ] 至少建立以下指标：
    - [ ] 下单成功率
    - [ ] 风控拒绝次数
    - [ ] reconcile 触发次数
    - [ ] recovery 修正次数
    - [ ] WS accelerate 命中次数
    - [ ] 终态收敛耗时
- [ ] 日志格式应能支持一次订单全链路追踪

---

# 4. GateD 非范围清单（Out of Scope）

以下内容不属于 GateD，实施过程中不得偷渡进入当前 Gate：

- [ ] 回测引擎
- [ ] 因子计算框架
- [ ] 研究数据集市
- [ ] 策略 DSL
- [ ] 组合优化引擎
- [ ] 合约 / 杠杆 / 期货交易域
- [ ] 多交易所深度等价实现
- [ ] 复杂权限体系扩展
- [ ] 前端交易台完整建设
- [ ] 高频极限低延迟优化
- [ ] 复杂风控中心（如 VaR / 跨账户组合风险）

---

# 5. 文档交付检查（Documentation Deliverables）

以下 GateD 文档必须存在，且内容与当前代码一致：

- [ ] `docs/gates/gate-d/README.md`
- [ ] `docs/gates/gate-d/GATE_D_CHECKLIST.md`
- [ ] `docs/gates/gate-d/WORK.md`
- [ ] `docs/gates/gate-d/ARCHITECTURE.md`
- [ ] `docs/gates/gate-d/CONTRACTS.md`
- [ ] `docs/gates/gate-d/MODULES.md`
- [ ] `docs/gates/gate-d/DB_SCHEMA.md`
- [ ] `docs/gates/gate-d/STATE_MACHINE.md`
- [ ] `docs/gates/gate-d/RISK_RULES.md`
- [ ] `docs/gates/gate-d/COMPENSATION_SYNC.md`
- [ ] `docs/gates/gate-d/TEST_CASES.md`
- [ ] `docs/gates/gate-d/DECISIONS.md`
- [ ] `docs/gates/gate-d/EVOLUTION_RULES.md`
- [ ] `docs/gates/gate-d/NUMERIC_POLICY.md`
- [ ] `docs/gates/gate-d/PR_SPLIT_PLAN.md`
- [ ] `docs/gates/gate-d/RECOVERY_RUNBOOK.md`
- [ ] `docs/gates/gate-d/adr/ADR-001-unified-execution-entry.md`
- [ ] `docs/gates/gate-d/adr/ADR-002-risk-before-execution.md`
- [ ] `docs/gates/gate-d/adr/ADR-003-rest-first-ws-accelerated.md`

同时以下全局文档必须已同步修订：

- [ ] 根目录 `README.md`
- [ ] 根目录 `AGENTS.md`
- [ ] `docs/current/README.md`
- [ ] `docs/current/GATE_CHECKLIST.md`
- [ ] `docs/ROADMAP.md`
- [ ] `docs/MODULES.md`
- [ ] `docs/ARCHITECTURE.md`
- [ ] `docs/gates/gate-b/ROADMAP.md`
- [ ] `docs/gates/gate-c/ROADMAP.md`

---

# 6. 模块改造检查（Module Change Checklist）

## 6.1 nq-core

- [ ] 已明确 nq-core 为 GateD 执行域中心模块
- [ ] 已建立统一执行应用服务
- [ ] 已建立订单生命周期编排服务
- [ ] 已建立 trace / correlation 贯穿能力
- [ ] 已建立撤单编排能力
- [ ] 已将状态推进规则集中管理
- [ ] 已与 risk / adapter / ledger / scheduler 边界清晰分离

---

## 6.2 nq-risk

- [ ] 已从 noop / kill switch 过渡到规则化前置硬风控
- [ ] 已建立 RiskRule 抽象
- [ ] 已建立 RiskRuleRegistry
- [ ] 已支持规则链执行
- [ ] 已定义标准拒绝码与拒绝消息
- [ ] 已接入执行主链路

---

## 6.3 nq-adapter-api

- [ ] 已冻结 GateD 所需统一执行契约
- [ ] 已明确 submit / cancel / query 的统一模型
- [ ] 已明确 order snapshot / trade report / account snapshot / position snapshot 统一字段
- [ ] 已明确 venue status -> unified status 的映射要求

---

## 6.4 nq-adapter-okx

- [ ] 已作为 GateD 主执行通道完成闭环
- [ ] 已支持 submit / cancel / query
- [ ] 已支持 order snapshot 统一映射
- [ ] 已支持 trade report 统一映射
- [ ] 已支持 account snapshot 拉取与映射
- [ ] 已支持 position snapshot 拉取与映射
- [ ] 已支持 query-confirm 收敛链路

---

## 6.5 nq-adapter-binance

- [ ] 已保持统一接口兼容
- [ ] 已跟随 adapter-api 契约升级
- [ ] 已补齐必要映射
- [ ] 未在 GateD 期间引入额外深扩边任务

---

## 6.6 nq-scheduler

- [ ] 已限定 scheduler 只承担调度 / job / 协调入口职责
- [ ] reconcile job 已与领域规则边界清晰
- [ ] recovery job 已与领域规则边界清晰
- [ ] WS degrade / acceleration 协调逻辑已文档化
- [ ] PAPER 相关逻辑未继续无边界生长在 scheduler 内

---

## 6.7 nq-ledger

- [ ] 已支持 trade -> ledger posting 幂等闭环
- [ ] 已支持 position projection 更新
- [ ] 已补齐 account / position snapshot 所需持久化支撑
- [ ] 已定义重复回报与重复记账去重规则

---

## 6.8 nq-app / nq-api

- [ ] GateC 专用验收入口已退居历史用途
- [ ] 已建立 GateD 通用执行入口
- [ ] 已建立 GateD 基础查询视图或查询入口
- [ ] app 层未承载深业务逻辑

---

## 6.9 nq-infra / migration

- [ ] 已新增 GateD 对应 Flyway 迁移
- [ ] 迁移脚本命名规范且可追溯
- [ ] 新字段、新索引、新约束已与 DB_SCHEMA 文档一致
- [ ] 迁移可在新库初始化成功
- [ ] 迁移可在旧库升级成功

---

## 6.10 nq-observability

- [ ] 已建立 GateD 执行链路日志规范
- [ ] 已建立 GateD 关键指标
- [ ] 已支持全链路 trace_id 追踪
- [ ] 已能定位一次订单从请求到终态的完整链路

---

# 7. 数据库检查（DB Checklist）

以下数据库能力必须完成并通过核对：

## 7.1 orders

- [ ] 支持 client_order_id
- [ ] 支持 external_order_id / exchange_order_id
- [ ] 支持 request_id / idempotency_key
- [ ] 支持 unified status
- [ ] 支持 filled quantity / avg price
- [ ] 支持 reject code / reject message
- [ ] 支持 created_at / updated_at
- [ ] 支持 version（如采用乐观锁）

---

## 7.2 trades

- [ ] 支持 external trade id
- [ ] 支持 order 关联
- [ ] 支持 symbol / side / price / quantity
- [ ] 支持 fee / fee currency
- [ ] 支持 ts / trace_id
- [ ] 支持去重约束或去重策略

---

## 7.3 account snapshots

- [ ] 支持 account 维度快照
- [ ] 支持 currency / balance / available / frozen
- [ ] 支持 snapshot_time
- [ ] 支持查询索引

---

## 7.4 position snapshots

- [ ] 支持 account + symbol 维度快照
- [ ] 支持 quantity / available quantity / avg cost
- [ ] 支持 snapshot_time
- [ ] 支持查询索引

---

## 7.5 execution events / event store

- [ ] 已明确事件持久化表模型
- [ ] 支持 event_type
- [ ] 支持 aggregate_type / aggregate_id
- [ ] 支持 trace_id
- [ ] 支持 payload_json
- [ ] 支持 source
- [ ] 支持 created_at
- [ ] 支持按 trace / aggregate 查询

---

## 7.6 索引与约束

- [ ] `uk_orders_idempotency_key`
- [ ] `uk_orders_client_order_id`
- [ ] `idx_orders_external_order_id`
- [ ] `idx_orders_account_symbol_status`
- [ ] `idx_trades_order_id`
- [ ] `idx_trades_external_trade_id`
- [ ] `idx_execution_events_trace_id`
- [ ] `idx_execution_events_aggregate`

> 注：最终命名可按现有项目规范微调，但语义与能力必须等价。

---

# 8. 测试与验收检查（Test & Acceptance Checklist）

## 8.1 单元测试

- [ ] 执行入口服务单元测试
- [ ] 风控规则单元测试
- [ ] 状态机迁移单元测试
- [ ] 执行器映射单元测试
- [ ] ledger posting 单元测试
- [ ] 重复回报去重单元测试

---

## 8.2 集成测试

- [ ] PAPER 执行闭环集成测试
- [ ] OKX 提交 / 查询 / 撤单集成测试
- [ ] reconcile 收敛集成测试
- [ ] recovery 启动修正集成测试
- [ ] account / position sync 集成测试

---

## 8.3 必过验收用例

以下用例全部通过，GateD 才允许冻结：

- [ ] 用例 01：正常限价单提交成功并成交
- [ ] 用例 02：正常市价单提交成功并成交
- [ ] 用例 03：数量精度非法，被风控拒绝
- [ ] 用例 04：最小名义金额不足，被风控拒绝
- [ ] 用例 05：重复 idempotency key，被拦截
- [ ] 用例 06：订单部分成交后最终全成交流转正确
- [ ] 用例 07：撤单成功后状态进入终态
- [ ] 用例 08：WS 漏消息时，reconcile 能修正状态
- [ ] 用例 09：PAPER 与 OKX 返回统一模型一致
- [ ] 用例 10：全链路事件与日志可追溯
- [ ] 用例 11：重复成交回报不会重复记账
- [ ] 用例 12：重启恢复后未闭合订单能继续收敛

---

# 9. 工程门禁检查（Engineering Gates）

- [ ] `mvn test` 通过
- [ ] `mvn verify` 通过
- [ ] checkstyle 通过
- [ ] spotless 通过
- [ ] archunit 通过（如项目已启用）
- [ ] integration tests 通过（如项目已启用）
- [ ] Flyway 迁移通过
- [ ] 本地 profile 可运行
- [ ] test profile 可运行
- [ ] PAPER profile 可运行
- [ ] OKX-SIM 或等效 profile 可运行
- [ ] 必要环境变量、`.env.example`、配置说明已更新
- [ ] 无明显破坏 GateA / GateB / GateC 已冻结能力的回归

---

# 10. 冻结标准（Freeze Criteria）

以下条件全部满足，GateD 才允许冻结：

- [ ] GateD 范围内文档已齐全
- [ ] GateD 范围内代码已合并
- [ ] 统一执行入口已落地
- [ ] 前置硬风控已生效
- [ ] PAPER 执行闭环已打通
- [ ] OKX 主验收通道已打通
- [ ] 订单状态机已正式冻结
- [ ] 账户 / 持仓 / ledger / event 闭环已打通
- [ ] reconcile / recovery / query-confirm 可收敛
- [ ] 验收用例全部通过
- [ ] 工程门禁全部通过
- [ ] `docs/gates/gate-d/WORK.md` 已更新为冻结状态
- [ ] `docs/current/*` 可准备切换到下一个 Gate

---

# 11. 阻塞项记录（Blocking Issues）

> 用于记录 GateD 推进期间仍未解决、且影响冻结的阻塞项。

## 11.1 当前阻塞项

- [ ] 无
- [ ] 阻塞项 1：________________________________
- [ ] 阻塞项 2：________________________________
- [ ] 阻塞项 3：________________________________

---

# 12. 遗留项记录（Deferred / Follow-up）

> 用于记录明确不在 GateD 范围内，或 GateD 完成后顺延到后续 Gate 的事项。

- [ ] GateE / 后续阶段处理：研究 / 回测
- [ ] GateE / 后续阶段处理：策略接入增强
- [ ] GateE / 后续阶段处理：绩效分析 / 回放增强
- [ ] GateE / 后续阶段处理：Binance 深度齐平
- [ ] GateE / 后续阶段处理：合约 / 杠杆扩展
- [ ] 其他遗留项：________________________________
- [ ] 其他遗留项：________________________________

---

# 13. 冻结结论（Freeze Decision）

## 13.1 结论状态

- [ ] 未开始
- [x] 进行中
- [ ] 可冻结
- [ ] 已冻结
- [ ] 冻结失败，需返工

---

## 13.2 冻结结论说明

填写说明：

- GateD 是否达到冻结标准：
- 未达标项：
- 返工范围：
- 冻结日期：
- 对下一 Gate 的输入：

---

## 13.3 审核签署

- 架构 / 主线负责人：____________________
- 执行域负责人：________________________
- 风控负责人：__________________________
- 数据 / 账本负责人：____________________
- 验收日期：____________________________

---

# 14. GateD 完成定义（Definition of Done）

当且仅当以下描述为真时，GateD 视为完成：

> 给定一个标准化订单请求，系统能够在 PAPER 或 OKX 通道中完成前置风控、下单执行、状态推进、成交回写、账户与持仓同步、账本更新、异常补偿与事件留痕，并通过统一日志、指标和查询链路完成全流程验证与追踪。

---
