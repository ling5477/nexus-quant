# RC1_CHECKLIST

当前状态：**RC1 completed and frozen；GateH-PRE completed；下一步进入 GateH-PLAN。**

## RC1 最终完成批次

- [x] `RC1-0`：文档主线切换
- [x] `RC1-1`：仓库清理
- [x] `RC1-2`：用户 / 账户 / 凭证 / 环境主模型
- [x] `RC1-3`：模块边界与 JDBC 收口
- [x] `RC1-4`：账户与凭证写侧闭环
- [x] `RC1-5`：marketdata / research 最小能力闭环
- [x] `RC1-6`：全量验证闭环
- [x] `RC1-7`：后端按业务域包结构收口

## RC1 已交付能力

- [x] 用户 / 交易账户 / 凭证 / 环境主模型已成立
- [x] `/api/auth/me` 与默认账户上下文联动已成立
- [x] 账户与凭证写侧闭环已成立
- [x] 凭证 active 版本切换与结构性校验已成立
- [x] JDBC 实现与模块边界收口已完成
- [x] 后端业务域包结构收口已完成
- [x] `marketdata` 最小 ingest / query 真闭环已成立
- [x] `research -> backtest -> eval` 最小 DB-backed happy path 已成立
- [x] `RC1-6` 验证闭环已通过

## GateH-PRE 已完成治理

- [x] `trading` anti-corruption 已完成，`nq-core` 不再把 adapter API model/service 当作 application 主语义
- [x] `marketdata` owner 已收口到正式主链，不再作为 `nq-backtest` 附属能力
- [x] `nq-api` research 编排层已移出，API 层回到 controller / DTO / web adapter
- [x] `nq-app` composition root 纯度已提升，业务实现与 runtime 策略已下移到对应 owner
- [x] `nq-infra` namespace 已统一到 domain-first infra 包规范
- [x] 前端正式入口已统一为 `/trading`，`/trade-validation` 只保留历史 alias
- [x] Python 子工程已闭环 `pytest` / `mypy` / `ruff` / CLI smoke

## RC1 / GateH-PRE 未纳入范围

- [x] GateH 功能开发未启动
- [x] publish 深化不在 RC1 必达范围
- [x] 新交易所接入未启动
- [x] Python 不进入 live trading / auth / recovery / ledger 主链
- [x] 更复杂的前端运营台、批量操作和视觉升级未展开

## 冻结约束

- [x] RC1 已完成并冻结
- [x] GateH-PRE 已完成并冻结为 GateH-PLAN 输入
- [x] 不允许回退 RC1 已确立的模块边界
- [x] 不允许回退 RC1 已确立的业务域包结构
- [x] 不允许回退账户 / 凭证 / 默认账户上下文主链
- [x] 不允许回退 `trading` anti-corruption 边界
- [x] 不允许回退 `marketdata / instrument / symbol` owner 收口
- [x] 不允许把历史迁移映射或历史路由 alias 当作当前事实入口

## GateH 状态与后续入口

- [x] `GateH = paused / not started`
- [x] GateH 尚未启动开发
- [x] 后续必须先进入 `GateH-PLAN`
- [x] 不得绕过 `GateH-PLAN` 直接恢复 GateH 开发
