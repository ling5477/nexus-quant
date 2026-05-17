# NexusQuant（nexus-quant）

NexusQuant 是面向数字资产交易场景的量化系统工程骨架，核心原则是：
**幂等（client_order_id） + 严格状态机 + 事实链（event_store） + 账本（ledger_entries） + 可审计 + 可恢复 + 可观测**。

> 当前事实入口以 `docs/current/` 为准。
> 历史 Gate 冻结卷宗位于 `docs/gates/gate-*/`，只读参考，不代表当前实现入口。

---

## 1. 当前阶段

当前阶段：**RC1 completed and frozen；GateH-PRE completed；下一步进入 `GateH-PLAN`。**

当前状态：

- GateD / GateE / GateF / GateG 均已冻结，相关文档只作历史参考。
- `RC1-0 / 1 / 2 / 3 / 4 / 5 / 6 / 7` 已全部完成，RC1 已整体冻结。
- `GateH = paused / not started`，尚未启动开发。
- `GateH-PRE` 前置治理已完成，下一步只能进入 `GateH-PLAN`，不得直接恢复 GateH 功能开发。

---

## 2. 当前系统正式基线

- 用户 / 交易账户 / 凭证 / 环境主模型已成立。
- 默认账户上下文与 `/api/auth/me` 联动已成立。
- 账户与凭证写侧闭环、active 版本切换与结构性校验已成立。
- `trading` anti-corruption 已成立，`nq-core` 不再把 adapter API model/service 当作 application 主语义。
- `marketdata` owner 已收口到正式主链，不再作为 `nq-backtest` 附属能力。
- `nq-api` research 编排层已移出，API 层回到 controller / DTO / web adapter 角色。
- `nq-app` 更接近纯 composition root，业务实现与 runtime 策略已下移到对应 owner。
- `nq-infra` namespace 已收敛到 domain-first 持久化与基础设施实现。
- 前端正式交易入口是 `/trading`，旧 `/trade-validation` 仅为历史路由 alias。
- Python 子工程定位为离线研究工具链，`pytest` / `mypy` / `ruff` / CLI smoke 已闭环。

---

## 3. 当前入口

- 文档总入口：`docs/README.md`
- 当前阶段入口：`docs/current/README.md`
- 当前状态：`docs/current/STATUS.md`
- 当前架构：`docs/current/ARCHITECTURE.md`
- 当前模块基线：`docs/current/MODULES.md`
- 当前 API：`docs/current/API.md`
- 当前数据库：`docs/current/DB_SCHEMA.md`
- 当前验证：`docs/current/TESTING.md`
- 当前运行手册：`docs/current/RUNBOOK.md`
- 下一阶段计划：`docs/current/PLAN_GATEH.md`
- RC1 归档：`docs/archive/rc1/`
- Gate 输入与 GateH-PRE 归档：`docs/archive/gate-inputs/`
- GateH 暂停卷宗：`docs/gates/gate-h/README.md`

---

## 4. 当前不做的事

- 不直接进入 GateH 功能开发。
- 不恢复历史前端壳或旧验证页主线。
- 不把 `/trade-validation` 当作正式页面入口。
- 不把历史 RC1 包迁移映射当作当前包事实。
- 不让 Python 进入 live trading / auth / recovery / ledger 主链。

---

## 5. 下一步

下一步是：**GateH-PLAN**。

GateH-PLAN 必须以当前冻结基线为输入，先规划 scope、接口、数据、测试与回滚边界，再决定是否进入正式 GateH 开发。
