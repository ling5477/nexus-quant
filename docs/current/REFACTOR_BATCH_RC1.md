# RC1（项目收口重构批次）

当前状态：**completed and frozen**

## 1. RC1 最终完成概述

RC1 已整体完成，`RC1-0 / 1 / 2 / 3 / 4 / 5 / 6 / 7` 全部完成。RC1 不再是进行中批次，当前仓库不再以“结构收口执行中”为主线，而是以 **RC1 冻结基线** 作为后续规划输入。

各子批次最终状态：

- `RC1-0`：完成 RC1 文档主线切换，`docs/current/*` 成为 source of truth。
- `RC1-1`：完成仓库清理、无用产物与敏感残留移除。
- `RC1-2`：完成用户 / 账户 / 凭证 / 环境主模型与数据库落库。
- `RC1-3`：完成模块边界与 JDBC 实现收口，`nq-infra` 成为正式持久化承接层。
- `RC1-4`：完成账户与凭证写侧闭环、默认账户上下文与 `/api/auth/me` 联动。
- `RC1-5`：完成 `marketdata` 最小 ingest/query 真闭环，以及 `research -> backtest -> eval` 最小 DB-backed happy path。
- `RC1-6`：完成 compile / test / E2E / Python 验证链闭环。
- `RC1-7`：完成后端按业务域的包结构收口。

## 2. RC1 冻结基线已交付能力

当前系统已正式具备：

- 表结构主模型收口完成。
- DB-backed auth 已成立。
- 用户 / 账户 / 凭证 / 默认账户上下文主链已成立。
- 账户与凭证写侧闭环已成立。
- 凭证 active 版本切换与结构性校验已成立。
- JDBC 实现已收口到 `nq-infra`。
- contracts 模块拆分已完成。
- 后端按业务域包结构收口已完成。
- `marketdata` 已具备最小 ingest/query 真闭环。
- `research -> backtest -> eval` 已具备最小 DB-backed happy path。
- `RC1-6` 验证链已闭环。

## 3. RC1 边界与未纳入范围

以下事项不是遗漏，而是 RC1 的明确边界控制：

- GateH 功能开发未启动。
- publish 深化不在 RC1 必达范围。
- 多交易所历史行情平台化不在 RC1 必达范围。
- research / front-end 深化不在 RC1 必达范围。
- 管理员跨用户账户 / 凭证运营能力未在 RC1 内展开。
- 凭证真实外网探活未纳入 RC1，当前为结构性校验。
- 更复杂的前端运营台与批量操作未纳入 RC1。

## 4. GateH 状态

- `GateH = paused`
- GateH 尚未启动开发。
- 后续必须先进入 `GateH-PLAN`，再决定正式开工范围。
- 任何后续功能开发不得绕过 `GateH-PLAN` 直接恢复 GateH。

## 5. RC1 冻结约束

- RC1 已冻结。
- 不允许回退 RC1 已确立的模块边界、包结构、账户 / 凭证主链、marketdata / research 最小闭环。
- 后续 GateH 只能在 RC1 冻结基线之上做增量规划。

## 6. 后续入口

当前下一步不是 GateH 开发，而是：**GateH-PLAN**。
