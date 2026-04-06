# RC1_CHECKLIST

当前状态：**RC1 已整体完成并冻结。**

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

## RC1 未纳入范围 / 刻意未做项

- [x] GateH 功能开发未启动
- [x] publish 深化不在 RC1 必达范围
- [x] 多交易所历史行情平台化不在 RC1 必达范围
- [x] research front-end 深化不在 RC1 必达范围
- [x] 管理员跨用户账户 / 凭证运营能力未在 RC1 内展开
- [x] 凭证真实外网探活未纳入 RC1，当前为结构性校验
- [x] 更复杂的前端运营台与批量操作未纳入 RC1

## RC1 冻结约束

- [x] RC1 已完成并冻结
- [x] 不允许回退 RC1 已确立的模块边界
- [x] 不允许回退 RC1 已确立的业务域包结构
- [x] 不允许回退账户 / 凭证 / 默认账户上下文主链
- [x] 不允许回退 `marketdata / research` 最小能力闭环

## GateH 状态与后续入口

- [x] `GateH = paused`
- [x] GateH 尚未启动开发
- [x] 后续必须先进入 `GateH-PLAN`
- [x] 不得绕过 `GateH-PLAN` 直接恢复 GateH 开发
