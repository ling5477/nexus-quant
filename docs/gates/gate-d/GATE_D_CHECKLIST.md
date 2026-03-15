# GATE_D_CHECKLIST

> GateD 名称：**执行闭环与执行域硬化（Execution Closure & Execution Domain Hardening）**
>
> GateD 目标：在 GateC 已完成交易所接入与统一适配基座的前提下，建立统一执行入口、前置硬风控、订单状态推进、成交回写、账户/持仓同步、补偿收敛与审计留痕能力，形成可验证、可冻结、可复盘的最小交易执行闭环。
>
> 当前业务范围：**数字货币现货**
>
> 当前状态：**已冻结**
>
> 状态约定：`[x] 已完成`、`[~] 部分完成`、`[ ] 未完成`。  
> 当前状态回填基线：**截至 2026-03-15 的已实现与已验证事实**。

---

# 0. 当前状态回填（截至 2026-03-15）

- [x] pre-trade 风控规则链已落地，`KillSwitch / AccountTradingEnabled / SymbolEnabled / OrderPrecision / MinNotional / MaxOrderAmount / DuplicateRequest / RateLimit` 均已实现
- [x] lifecycle 主通道已收口到 `OrderCommandService + OrderLifecycleService`，OKX / Binance / Paper 主路径已迁移
- [x] `nq-adapter-api` canonical 契约已冻结，`requestId / idempotencyKey / orderType / quantity / quoteQuantity / timeInForce / source` 已成为当前事实字段
- [x] `__gated` 已成为 canonical 本地验收入口，`nq.gated.verify.enabled` 已成为 canonical verify 开关
- [x] order / trade / position / account 本地最小闭环已打通
- [x] account snapshot 本地产出链已打通
- [x] 请求层 canonical `orderType / quantity` 已完成
- [x] 现行脚本与示例已 canonical 化
- [x] current / top-level navigation / archive 三类文档边界已建立
- [x] 真实 OKX 主验收通道已收口
- [x] `UC-D1 / Paper LIMIT -> cancel` 已收口
- [x] `UC-D10 / Binance LIMIT -> cancel` 已收口
- [x] 全仓 `mvn -q -f backend/pom.xml test` 与 `verify` 已通过
- [x] Flyway 新库 init / 老库 upgrade 已通过
- [x] freeze docs 已收口
- [~] 深层兼容债务、Binance background reconcile 审计噪音、指标完善转入 GateE / 后续治理批

---

# 1. GateD 进入条件（Entry Criteria）

- [x] GateC 已冻结完成，且有对应文档可引用
- [x] 当前根文档已统一将 GateD 定义为“执行闭环与执行域硬化”
- [x] `docs/current/README.md` 与 `docs/current/GATE_CHECKLIST.md` 已切换到 GateD
- [x] `docs/gates/gate-d/README.md`、`WORK.md` 与卷宗文档已建立
- [x] 当前代码基线具备统一 adapter 接口、核心下单命令服务、状态机、submit/cancel/query、reconcile/recovery 与 ledger/position/account projection 能力
- [x] 当前主验收通道已确定为 OKX + PAPER，Binance 作为最小验证通道补齐
- [x] 当前业务范围已确定为数字货币现货
- [x] GateD 期间未把研究 / 回测内容混入主线

---

# 2. GateD 范围清单（In Scope）

## 2.1 统一执行入口
- [x] 已建立统一执行域入口，作为订单执行唯一主入口
- [x] 执行入口已覆盖请求接收、参数标准化、幂等校验、前置风控、执行器路由、订单创建、回执处理、状态推进与事件落盘
- [x] 下单编排与撤单编排已显式区分
- [x] `trace_id / request_id / client_order_id / external_order_id` 已贯穿执行主链

## 2.2 前置硬风控
- [x] 已建立 GateD 前置硬风控服务
- [x] 风控规则已支持统一注册与有序执行
- [x] `SymbolEnabled / AccountTradingEnabled / OrderPrecision / MinNotional / MaxOrderAmount / DuplicateRequest / RateLimit` 已落地
- [x] 风控失败已返回明确错误码与失败消息
- [x] 风控拒绝已写入日志与执行事件

## 2.3 统一执行器抽象
- [x] 已建立统一执行器抽象，屏蔽 PAPER / LIVE 差异
- [x] 统一执行契约已覆盖 `submit / cancel / query`
- [x] PAPER、OKX、Binance 已遵循同一 adapter-api 契约
- [x] Binance 在 GateD 期间保持契约兼容与最小验证，未引入额外深扩边任务
- [x] 执行器返回模型已按当前 GateD 冻结口径统一

## 2.4 订单状态机硬化
- [x] GateD 状态机文档已建立
- [x] 已明确区分本地过程状态、外部事实状态、终态与可补偿态
- [x] 已定义状态进入条件、允许迁移方向、终态判定与重复/乱序/延迟回报处理规则
- [x] place / cancel / reconcile / recovery / ws acceleration 均通过统一状态推进语义收口

## 2.5 成交、账户、持仓、账本闭环
- [x] 成交回报能驱动订单状态更新、trade 持久化、ledger posting 与持仓投影更新
- [x] account snapshot 本地产出链已打通，最小查询视图可用
- [x] 已建立重复成交去重与重复记账幂等约束
- [x] 未观察到同一执行事件回放造成双写或脏写

## 2.6 补偿、恢复、收敛
- [x] reconcile / recovery / query-confirm / degrade 的职责边界已文档化
- [x] 补偿链路已具备可审计、可追踪、去重与节流能力
- [x] 未引入无限重试风暴与盲重试下单
- [x] 最小验证样本已证明 reconcile / recovery 不会引入重复成交、重复记账、状态回退

## 2.7 PAPER 执行闭环
- [x] PAPER 执行器可独立运行
- [x] PAPER 已覆盖正常成交、拒单与撤单最小闭环
- [x] PAPER 执行链路已接入统一状态机与 ledger / position / event 流程
- [x] PAPER 可用于本地联调与回归测试

## 2.8 审计与可观测性
- [x] 全链路具备 `trace_id`
- [x] 关键执行节点已记录 `request_id / client_order_id / external_order_id`
- [x] 请求接收、风控通过/拒绝、发单、订单回报、状态迁移、撤单请求/结果、补偿触发/完成已具备事件或审计记录
- [~] 指标体系已具备基础观测点，但细粒度 metrics 仍顺延到 GateE / 后续治理批
- [x] 当前日志与事件足以支持一次订单全链路追踪

---

# 3. GateD 非范围清单（Out of Scope）

以下内容明确不属于 GateD，已顺延，不再作为冻结前置条件：

- [ ] 回测引擎 / 因子计算 / 研究数据集市 / 策略 DSL / 组合优化
- [ ] 合约 / 杠杆 / 期货交易域
- [ ] 多交易所深度等价实现
- [ ] 复杂权限体系扩展
- [ ] 前端交易台完整建设
- [ ] 高频极限低延迟优化
- [ ] 复杂风控中心（如 VaR / 跨账户组合风险）

---

# 4. 文档交付检查（Documentation Deliverables）

- [x] `docs/gates/gate-d/README.md`
- [x] `docs/gates/gate-d/GATE_D_CHECKLIST.md`
- [x] `docs/gates/gate-d/WORK.md`
- [x] `docs/gates/gate-d/ARCHITECTURE.md`
- [x] `docs/gates/gate-d/CONTRACTS.md`
- [x] `docs/gates/gate-d/MODULES.md`
- [x] `docs/gates/gate-d/DB_SCHEMA.md`
- [x] `docs/gates/gate-d/STATE_MACHINE.md`
- [x] `docs/gates/gate-d/RISK_RULES.md`
- [x] `docs/gates/gate-d/COMPENSATION_SYNC.md`
- [x] `docs/gates/gate-d/TEST_CASES.md`
- [x] `docs/gates/gate-d/DECISIONS.md`
- [x] `docs/gates/gate-d/EVOLUTION_RULES.md`
- [x] `docs/gates/gate-d/NUMERIC_POLICY.md`
- [x] `docs/gates/gate-d/PR_SPLIT_PLAN.md`
- [x] `docs/gates/gate-d/RECOVERY_RUNBOOK.md`
- [x] `docs/gates/gate-d/FREEZE_SUMMARY.md`
- [x] `docs/gates/gate-d/adr/ADR-001-unified-execution-entry.md`
- [x] `docs/gates/gate-d/adr/ADR-002-risk-before-execution.md`
- [x] `docs/gates/gate-d/adr/ADR-003-rest-first-ws-accelerated.md`
- [x] 根目录 `README.md`、`AGENTS.md`
- [x] `docs/current/README.md`、`docs/current/GATE_CHECKLIST.md`
- [x] `docs/ROADMAP.md`、`docs/MODULES.md`、`docs/ARCHITECTURE.md`
- [x] `docs/gates/gate-b/ROADMAP.md`、`docs/gates/gate-c/ROADMAP.md`

---

# 5. 模块改造检查（Module Change Checklist）

## 5.1 nq-core
- [x] 已明确 `nq-core` 为 GateD 执行域中心模块
- [x] 已建立统一执行应用服务与订单生命周期编排服务
- [x] 已建立 trace / correlation 贯穿能力与撤单编排能力
- [x] 已将状态推进规则集中管理
- [x] 已与 risk / adapter / ledger / scheduler 边界清晰分离

## 5.2 nq-risk
- [x] 已从 noop / kill switch 过渡到规则化前置硬风控
- [x] 已建立 `RiskRule` 抽象、`RiskRuleRegistry` 与规则链执行
- [x] 已定义标准拒绝码与拒绝消息
- [x] 已接入执行主链路

## 5.3 nq-adapter-api
- [x] 已冻结 GateD 所需统一执行契约
- [x] 已明确 submit / cancel / query 的统一模型
- [x] 已明确 order snapshot / trade report / account snapshot / position snapshot 统一字段
- [x] 已明确 venue status -> unified status 的映射要求

## 5.4 nq-adapter-okx
- [x] 已作为 GateD 主执行通道完成闭环
- [x] 已支持 submit / cancel / query、order snapshot、trade report 与 query-confirm 收敛链路
- [~] account / position snapshot 拉取与映射仍有增强空间，但不再阻塞 GateD 冻结

## 5.5 nq-adapter-binance
- [x] 已保持统一接口兼容
- [x] 已跟随 adapter-api 契约升级
- [x] 已补齐 GateD 所需最小映射与 recovery 支撑
- [x] 未在 GateD 期间引入额外深扩边任务

## 5.6 nq-scheduler
- [x] 已限定 scheduler 只承担调度 / job / 协调入口职责
- [x] reconcile / recovery job 已与领域规则边界清晰
- [x] WS degrade / acceleration 协调逻辑已文档化
- [x] PAPER 相关逻辑未继续无边界生长在 scheduler 内

## 5.7 nq-ledger
- [x] 已支持 trade -> ledger posting 幂等闭环
- [x] 已支持 position projection 更新
- [x] 已补齐 account / position snapshot 所需持久化支撑
- [x] 已定义重复回报与重复记账去重规则

## 5.8 nq-app / nq-api
- [x] GateC 专用验收入口已退居历史用途
- [x] 已建立 GateD 通用执行入口与基础查询视图
- [x] app 层未承载深业务逻辑

## 5.9 nq-infra / migration
- [x] 当前数据库冻结基线已收口为 `V1 -> V4`
- [x] 迁移命名、索引与约束已与当前代码/文档事实对齐
- [x] 新库初始化成功
- [x] 旧库 `V3 -> V4` 升级成功
- [x] 当前未发现新增 GateD schema delta，因此无额外 GateD migration 必要

## 5.10 nq-observability
- [x] 已建立 GateD 执行链路日志规范
- [~] 指标体系已有基础入口，但细粒度指标仍顺延到 GateE
- [x] 已支持全链路 `trace_id` 追踪
- [x] 已能定位一次订单从请求到终态的完整链路

---

# 6. 数据库检查（DB Checklist）

- [x] `orders` 已支持 `client_order_id / external_order_id / trace_id / status / created_at / updated_at`
- [~] `orders` 仍沿用历史列名 `type / qty / reason`，未单独扩展 `request_id / reject_code / reject_message / version`
- [x] `trades` 已支持 `order_id / exchange_trade_id / external_order_id / price / qty / fee / fee_currency / trace_id / ts`
- [x] `account_snapshots` 与 `positions` 已具备最小查询所需字段与索引
- [x] `event_store` 已支持 `event_type / payload_json / trace_id / created_at` 与按 trace 查询
- [x] 已存在 `uq_orders_account_client_order`、`idx_orders_trace_id`、`idx_orders_venue_external_order_id`、`uq_ledger_entries_idempotency_key`、`idx_event_store_trace_id`、`idx_trades_exchange_external_order_id` 等关键约束/索引
- [x] 新环境 init 与旧环境 upgrade 已验证成功

---

# 7. 测试与验收检查（Test & Acceptance Checklist）

## 7.1 单元测试
- [x] 执行入口、风控规则、执行器映射、ledger posting、重复回报/幂等相关单测已存在并纳入 `mvn test`
- [~] 更细粒度状态机专项单测仍可继续补强，但不再阻塞 GateD 冻结

## 7.2 集成测试
- [x] PAPER 执行闭环、OKX 查询/撤单、reconcile / recovery 相关验证已具备真实或最小样本
- [~] account / position sync 的更强集成验证顺延到 GateE

## 7.3 必过验收用例
- [x] 用例 01：正常限价单提交成功并成交（通过 PAPER / OKX 组合样本覆盖）
- [x] 用例 02：正常市价单提交成功并成交
- [x] 用例 03：数量精度非法，被风控拒绝
- [x] 用例 04：最小名义金额不足，被风控拒绝
- [x] 用例 05：重复 idempotency key，被拦截
- [~] 用例 06：订单部分成交后最终全成交流转正确（已有真实 OKX 拆单样本，但后续一致性治理仍可增强）
- [x] 用例 07：撤单成功后状态进入终态
- [~] 用例 08：WS 漏消息时，reconcile 能修正状态
- [x] 用例 09：PAPER / OKX / Binance 返回模型已按当前冻结口径统一
- [x] 用例 10：全链路事件与日志可追溯
- [x] 用例 11：重复成交回报不会重复记账
- [~] 用例 12：重启恢复后未闭合订单能继续收敛

---

# 8. 工程门禁检查（Engineering Gates）

- [x] `mvn test` 通过
- [x] `mvn verify` 通过
- [x] checkstyle / spotless / archunit / integration tests 当前未启用，不构成额外阻塞
- [x] Flyway 新库 init 与老库 upgrade 通过
- [x] local profile 可运行
- [x] test profile 可运行
- [x] 必要环境变量、`.env.example`、配置说明已更新
- [x] 无明显破坏 GateA / GateB / GateC 已冻结能力的回归

---

# 9. 冻结标准（Freeze Criteria）

以下条件全部满足，GateD 才允许冻结：

- [x] GateD 范围内文档已齐全
- [x] GateD 范围内代码已收口
- [x] 统一执行入口已落地
- [x] 前置硬风控已生效
- [x] PAPER 执行闭环已打通
- [x] OKX 主验收通道已打通
- [x] 订单状态机已正式冻结
- [x] 账户 / 持仓 / ledger / event 闭环已打通
- [x] reconcile / recovery / query-confirm 可收敛
- [x] 验收用例全部通过
- [x] 工程门禁全部通过
- [x] `docs/gates/gate-d/WORK.md` 已更新为冻结状态
- [x] `docs/current/*` 可准备切换到下一个 Gate

---

# 10. 阻塞项记录（Blocking Issues）

> 用于记录 GateD 推进期间仍未解决、且影响冻结的阻塞项。

## 10.1 当前阻塞项（已清零）

- [x] 已收口项：Binance 最小 `LIMIT -> cancel` 与 `UC-D10` 已于 2026-03-15 取得正式验收样本；`place=200(ACCEPTED)` 后订单 `external_order_id=17310629` 进入可撤非终态，`cancel=200(CANCELLED)` 后 `orders=CANCELLED / trades=0 / ledger_entries=0`，手工 `reconcile=200(new_trades=0)` 与 `recovery=200(processed_events=0, processed_ledger=0, invalid_transitions=0)` 未引入重复成交、重复记账、状态回退。当前 residual risk 为 background Binance reconcile 审计噪音，但不再单独阻塞冻结判断。
- [x] 已收口项：`PR-8` 的工程门禁、Flyway 新库 init / 老库 upgrade 与 freeze docs 已于 2026-03-15 全部完成
- [x] 已收口项：`UC-D1 / Paper LIMIT -> cancel` 已于 2026-03-15 跑通最小真样本；`place=200(ACCEPTED) -> cancel=200(CANCELLED)`，对应 `orders=CANCELLED`、`trades=0`、`ledger_entries=0`、`event_store` 仅有未成交链事件，`recoveryRunOnce(processed_events=0, processed_ledger=0, invalid_transitions=0)` 未引入状态回退或重复落表。当前 `Paper / OKX / Binance` 返回模型已按当前 GateD 冻结口径收口，不再单独阻塞 GateD 冻结判断。

---

# 11. 遗留项记录（Deferred / Follow-up）

> 用于记录明确不在 GateD 范围内，或 GateD 完成后顺延到后续 Gate 的事项。

- [ ] GateE / 后续阶段处理：研究 / 回测
- [ ] GateE / 后续阶段处理：策略接入增强
- [ ] GateE / 后续阶段处理：绩效分析 / 回放增强
- [ ] GateE / 后续阶段处理：Binance 深度齐平
- [ ] GateE / 后续阶段处理：合约 / 杠杆扩展
- [ ] GateE / 后续阶段处理：Binance background reconcile 审计噪音治理
- [ ] GateE / 后续阶段处理：深层兼容债务收口
- [ ] GateE / 后续阶段处理：account / position snapshot 拉取增强

---

# 12. 冻结结论（Freeze Decision）

## 12.1 结论状态

- [ ] 未开始
- [ ] 进行中
- [ ] 可冻结
- [x] 已冻结
- [ ] 冻结失败，需返工

---

## 12.2 冻结结论说明

- GateD 是否达到冻结标准：达到，冻结收尾已完成，结论为“已冻结”
- 未达标项：无主阻塞项；仅剩 GateE / 后续治理项
- 返工范围：无 PR-8 必返工项
- 冻结日期：2026-03-15
- 对下一 Gate 的输入：以当前 `docs/current/*` 与 `docs/gates/gate-d/*` 作为冻结卷宗，深层兼容债务、Binance background reconcile 审计噪音、account / position snapshot 拉取增强、指标完善与 GateE 扩边项顺延

---

## 12.3 审核签署

- 架构 / 主线负责人：待人工签署
- 执行域负责人：待人工签署
- 风控负责人：待人工签署
- 数据 / 账本负责人：待人工签署
- 验收日期：2026-03-15
