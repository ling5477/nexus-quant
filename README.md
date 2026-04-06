# README.md
# NexusQuant（nexus-quant）

NexusQuant 是面向数字资产交易场景的量化系统工程骨架，核心原则是：  
**幂等（client_order_id） + 严格状态机 + 事实链（event_store） + 账本（ledger_entries） + 可审计 + 可恢复 + 可观测**。

> 当前处于哪个阶段、当前入口代表什么，以 `docs/current/` 为准。  
> 历史 Gate 冻结卷宗位于 `docs/gates/gate-*/`，只读参考。

---

## 1. 当前阶段

当前阶段：**RC1 冻结完成基线**

当前状态：

- GateD 已冻结
- GateE 已冻结
- GateF 已完成并冻结
- GateG 已完成并冻结
- GateG-FREEZE-FIX 已完成
- GateG-FREEZE-E2E-FIX 已完成
- `RC1-0 / 1 / 2 / 3 / 4 / 5 / 6 / 7` 已全部完成
- RC1 已整体完成并冻结
- GateH 已暂停，尚未启动开发
- 下一步不是 GateH 开发，而是 `GateH-PLAN`

当前仓库入口代表：

- `docs/current/REFACTOR_BATCH_RC1.md` 为 RC1 冻结说明
- `docs/current/RC1_CHECKLIST.md` 为 RC1 最终 checklist
- `GateH` 只保留为暂停卷宗，不是当前开发入口

---

## 2. 当前系统已具备的正式能力

- 用户 / 交易账户 / 凭证 / 环境主模型已成立
- 默认账户上下文与 `/api/auth/me` 联动已成立
- 账户与凭证写侧闭环已成立
- 凭证 active 版本切换与结构性校验已成立
- JDBC 实现与模块边界收口已完成
- 后端业务域包结构收口已完成
- `marketdata` 最小 ingest/query 真闭环已成立
- `research -> backtest -> eval` 最小 DB-backed happy path 已成立
- RC1-6 验证闭环已通过

---

## 3. RC1 未纳入范围

- GateH 功能开发未启动
- publish 深化不在 RC1 必达范围
- 多交易所历史行情平台化不在 RC1 必达范围
- research/front-end 深化不在 RC1 必达范围
- 管理员跨用户账户/凭证运营能力未在 RC1 内展开
- 凭证真实外网探活未纳入 RC1，当前为结构性校验
- 更复杂的前端运营台与批量操作未纳入 RC1

---

## 4. 当前入口

- 当前阶段入口：`docs/current/README.md`
- RC1 冻结说明：`docs/current/REFACTOR_BATCH_RC1.md`
- RC1 最终 checklist：`docs/current/RC1_CHECKLIST.md`
- 当前模块基线：`docs/current/MODULES.md`
- GateH 暂停卷宗：`docs/gates/gate-h/README.md`

---

## 5. 下一步

下一步不是 GateH 开发，而是：**GateH-PLAN**。
