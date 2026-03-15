# GateD Checklist（当前入口）

> 当前阶段：**GateD（统一执行闭环与执行域硬化）**。  
> 当前状态：**已冻结，GateE 待启动**。  
> 本文件只保留当前入口所需的冻结事实与顺延项；GateD 完整卷宗以 `docs/gates/gate-d/*` 为准。  
> 状态约定：`[x] 已完成`、`[~] 顺延治理`、`[ ] 未开始/不在当前阶段执行`。  
> 状态基线：**截至 2026-03-15 的已实现与已验证事实**。

---

## 0. 基础门禁（已收口）

- [x] `mvn -q -f backend/pom.xml test` 全绿
- [x] `mvn -q -f backend/pom.xml verify` 全绿
- [x] 本地 PostgreSQL 可用（宿主机 `localhost:5432` 已完成门禁、验收与 Flyway 验证；`docker compose` 不再作为冻结前置）
- [x] `nq-app` 可在 `local` profile 启动并返回 health `UP`
- [x] GateD 验收入口已就绪且仅在 `local + gate verify enabled` 下暴露
- [x] 当前 `.env.example` 与 profile 已明确区分 `paper / okx-dome / okx-real / binance-dome / binance-real`，并收口到 canonical `NQ_GATED_VERIFY_ENABLED`

---

## 1. 文档与阶段边界（已收口）

- [x] `docs/current/README.md` 已切换到 GateD 定义
- [x] `docs/current/GATE_CHECKLIST.md` 已切换为当前冻结入口
- [x] `docs/gates/gate-d/` 文档已完整建档并完成冻结收尾
- [x] `docs/gates/gate-d/DECISIONS.md` / `EVOLUTION_RULES.md` / `NUMERIC_POLICY.md` / `PR_SPLIT_PLAN.md` / `RECOVERY_RUNBOOK.md` 已建立并与当前事实一致
- [x] `docs/ROADMAP.md`、`docs/gates/gate-b/ROADMAP.md`、`docs/gates/gate-c/ROADMAP.md` 中 GateD 定义已统一
- [x] `AGENTS.md` 与 `README.md` 已对齐 GateD
- [x] current / top-level navigation / archive 三类文档边界已建立

---

## 2. GateD 主线能力（已冻结）

- [x] `nq-core` 已形成统一执行应用服务与生命周期编排主链
- [x] controller / scheduler 不再各自实现独立状态推进主逻辑
- [x] 状态机文档与状态推进规则已冻结
- [x] 风控规则链已落地，拒绝码与拒绝消息口径稳定
- [x] adapter 契约、交易所状态映射与统一执行模型已按当前冻结口径收口
- [x] reconcile / recovery / query-confirm / degrade 的边界已形成冻结事实
- [x] fills 去重、ledger posting 幂等、position projection 与 account snapshot 最小闭环已成立
- [x] `Paper / OKX / Binance` 返回模型已按当前 GateD 冻结口径收口
- [x] `trace_id` 已可追完整个执行闭环

---

## 3. 数据库与迁移（已收口）

- [x] 当前数据库冻结基线已明确为 `V1 -> V4`
- [x] 新环境可完整初始化
- [x] 老环境可平滑升级（已用受控临时库验证 `V3 -> V4`）
- [x] 关键索引已补齐：`idempotency_key / client_order_id / external_order_id / exchange_trade_id / trace_id`
- [x] 未观察到对 GateA / GateB / GateC 已冻结能力的明显破坏回归
- [x] 本批确认无额外 GateD migration 必要

---

## 4. 最小验收用例（已收口）

- [x] UC-D1：paper LIMIT -> cancel
- [x] UC-D2：paper MARKET -> fill
- [x] UC-D3：精度非法被风控拒绝
- [x] UC-D4：最小名义金额不足被风控拒绝
- [x] UC-D5：重复 idempotency key 被拦截
- [~] UC-D6：reconcile 能修正非终态订单（已具最小收口样本，增强样本顺延治理）
- [~] UC-D7：recovery 能在重启后恢复执行状态（已具最小收口样本，增强样本顺延治理）
- [~] UC-D8：WS 断连后触发受限 REST 兜底且不重复成交（基础能力成立，降噪与强化样本顺延治理）
- [x] UC-D9：OKX 最小 LIMIT -> cancel 通过
- [x] UC-D10：Binance 最小 LIMIT -> cancel 通过

说明：
- 以上样本的 trace、库表证据与验收细节，以 `docs/gates/gate-d/WORK.md`、`docs/gates/gate-d/TEST_CASES.md` 与 `docs/gates/gate-d/FREEZE_SUMMARY.md` 为准。

---

## 5. 冻结结论

- [x] 文档齐全并对齐代码
- [x] 最小执行闭环稳定
- [x] 风控硬规则生效
- [x] 补偿链路可收敛
- [x] Paper 与真实 venue 契约统一
- [x] 测试与验收全通过
- [x] `docs/gates/gate-d/WORK.md` 已写明完成项、遗留项、下一 Gate 输入项
- [x] GateD 冻结收尾已完成，当前状态记为“已冻结，GateE 待启动”

---

## 6. 顺延治理项（不再阻塞 GateD）

- [~] Binance background reconcile 审计噪音治理
- [~] 深层兼容债务收口
- [~] 返回模型一致性细节打磨
- [~] account / position snapshot 拉取增强
- [~] observability / 指标完善
- [~] schema / metadata 后续演化

说明：
- 上述条目全部转入 GateE / 后续治理批，不再视为 GateD 主阻塞。
