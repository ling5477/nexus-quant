# docs/current/GATE_CHECKLIST.md
# GateD Checklist（唯一验收入口）

> 当前阶段：**GateD（统一执行闭环与执行域硬化）**。  
> 本文件是 GateD 的唯一验收入口。历史 Gate 的失败记录与通过记录保留在各自 `docs/gates/gate-*/WORK.md`，不再混入本文件。

---

## 0. 基础门禁（必须）

- [ ] `mvn -q -f backend/pom.xml test` 全绿
- [ ] `docker compose up -d postgres` 成功
- [ ] `nq-app` 可在 `local` profile 启动并返回 health `UP`
- [ ] GateD 验收入口已就绪且仅在 `local + gate verify enabled` 下暴露
- [ ] 当前 `.env` 与 profile 能明确区分 `paper / okx-dome / okx-real / binance-dome / binance-real`

---

## 1. 文档与阶段边界（必须）

- [ ] `docs/current/README.md` 已切换到 GateD 定义
- [ ] `docs/current/GATE_CHECKLIST.md` 已清理 GateC 历史叠层内容，仅保留 GateD 门禁
- [ ] `docs/gates/gate-d/` 文档已完整建档
- [ ] `docs/gates/gate-d/DECISIONS.md` 已建立并开始维护
- [ ] `docs/gates/gate-d/EVOLUTION_RULES.md` 已建立并生效
- [ ] `docs/gates/gate-d/NUMERIC_POLICY.md` 已建立并落地到实现
- [ ] `docs/gates/gate-d/PR_SPLIT_PLAN.md` 已建立并作为提交边界依据
- [ ] `docs/gates/gate-d/RECOVERY_RUNBOOK.md` 已建立并可支撑恢复排障
- [ ] `docs/ROADMAP.md`、`docs/gates/gate-b/ROADMAP.md`、`docs/gates/gate-c/ROADMAP.md` 中 GateD 定义已统一为“执行闭环与执行域硬化”
- [ ] `AGENTS.md` 与 `README.md` 已对齐 GateD

---

## 2. 统一执行入口（必须）

- [ ] `nq-core` 形成统一执行应用服务，覆盖 `place / cancel / query-confirm / acknowledge / reject / trade-report`
- [ ] `OrderCommandService` 职责已收敛，不再无边界堆叠业务
- [ ] `AdapterRouter` 继续作为 venue 路由入口，core 不依赖具体 adapter 实现类
- [ ] controller / scheduler 不再自行推进订单状态，统一经 core 入口

---

## 3. 订单状态机硬化（必须）

- [ ] 状态机文档已冻结：本地状态、外部事实状态、终态定义、非法回退规则
- [ ] place / cancel / ws-ack / rest-reconcile / recovery 的状态推进都经过统一状态机入口
- [ ] 禁止状态回退、禁止重复终态覆盖、禁止相同事件造成脏写
- [ ] `external_order_id` 绑定与状态推进解耦，允许先 bind 后推进，也允许先推进后 bind，但都必须可审计

---

## 4. pre-trade 风控硬化（必须）

- [ ] `nq-risk` 已从 `NoopRiskGate` 过渡到规则链实现
- [ ] 至少具备以下规则：
    - [ ] 交易开关
    - [ ] 账户可交易校验
    - [ ] symbol 允许校验
    - [ ] 精度校验
    - [ ] 最小名义金额校验
    - [ ] 最大下单额校验
    - [ ] 重复请求拦截
    - [ ] 限频拦截
- [ ] 风控拒绝返回标准化 `ruleCode / rejectReason / hardReject`
- [ ] 风控结果写入 `audit_logs` 与 `event_store`

---

## 5. 统一 adapter 契约（必须）

- [ ] `nq-adapter-api` 已冻结 GateD 执行契约
- [ ] place / cancel / query / list-open-orders / list-fills / account-snapshot / position-snapshot 契约清晰
- [ ] 交易所状态映射已统一归口到 adapter 层
- [ ] `nq-core / nq-risk / nq-ledger / nq-scheduler` 无交易所方言分支

---

## 6. 补偿与同步（必须）

- [ ] `nq-scheduler` 只负责 job 调度、窗口扫描、恢复编排
- [ ] reconcile 对非终态订单执行 `query order + pull fills + projection sync`
- [ ] recovery 可在启动或手工触发时重新收敛非终态订单与未完成投影
- [ ] WS 断连 / 登录失效 / 订阅异常会触发一次受限 REST 兜底
- [ ] 禁止在补偿链路中直接盲重试下单
- [ ] query-confirm 规则有文档、有日志、有验收用例

---

## 7. trade / ledger / position / account 联动（必须）

- [ ] fills 去重生效（`exchange_trade_id` 或等价键唯一）
- [ ] 每笔 fill 只触发一次 ledger posting
- [ ] ledger posting 幂等键有效
- [ ] position projection 可见且无重复叠加
- [ ] account snapshot 同步路径清晰，至少支持手工 / 定时同步
- [ ] ledger 或 projection 失败路径会写事件与审计

---

## 8. Paper 与真实 venue 双通道（必须）

- [ ] Paper executor / adapter 与真实 venue 走统一执行接口
- [ ] Paper 支持 LIMIT -> cancel
- [ ] Paper 支持 MARKET -> fill
- [ ] OKX 至少完成最小 LIMIT -> cancel 验证
- [ ] Binance 至少完成最小 LIMIT -> cancel 验证
- [ ] Paper / OKX / Binance 的返回模型在 core 层一致

---

## 9. 可观测性（必须）

- [ ] 日志字段统一：`trace_id、request_id、client_order_id、external_order_id、account_id、symbol、venue`
- [ ] 至少具备以下指标：
    - [ ] 下单成功率
    - [ ] 风控拒绝次数
    - [ ] reconcile 触发次数
    - [ ] recovery 修正次数
    - [ ] WS degrade 次数
    - [ ] 重复回执 / 重复成交拦截次数
- [ ] 能以 `trace_id` 追完整个执行闭环

---

## 10. 数据库迁移（必须）

- [ ] GateD 新迁移脚本已创建，例如 `V5__gate_d_execution_closure.sql`
- [ ] 新环境可完整初始化
- [ ] 老环境可平滑升级
- [ ] 关键索引已补齐：`idempotency_key / client_order_id / external_order_id / exchange_trade_id / trace_id`
- [ ] 不破坏 GateA / GateB / GateC 已有数据

---

## 11. 最小验收用例（必须全部通过）

- [ ] UC-D1：paper LIMIT -> cancel
- [ ] UC-D2：paper MARKET -> fill
- [ ] UC-D3：精度非法被风控拒绝
- [ ] UC-D4：最小名义金额不足被风控拒绝
- [ ] UC-D5：重复 idempotency key 被拦截
- [ ] UC-D6：reconcile 能修正非终态订单
- [ ] UC-D7：recovery 能在重启后恢复执行状态
- [ ] UC-D8：WS 断连后触发受限 REST 兜底且不重复成交
- [ ] UC-D9：OKX 最小 LIMIT -> cancel 通过
- [ ] UC-D10：Binance 最小 LIMIT -> cancel 通过

---

## 12. GateD 冻结条件

以下条件全部满足，GateD 才允许冻结：

- [ ] 文档齐全并对齐代码
- [ ] 最小执行闭环稳定
- [ ] 风控硬规则生效
- [ ] 补偿链路可收敛
- [ ] Paper 与真实 venue 契约统一
- [ ] 测试与验收全通过
- [ ] `docs/gates/gate-d/WORK.md` 已写明完成项、遗留项、下一 Gate 输入项

