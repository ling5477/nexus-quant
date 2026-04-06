# Current Checklist（RC1 冻结）

当前状态：**RC1 已整体完成并冻结；`GateH = paused`；下一步 = `GateH-PLAN`。**

## 1. RC1 最终完成批次

- [x] `RC1-0`：文档切换
- [x] `RC1-1`：仓库清理
- [x] `RC1-2`：表结构与账户/凭证主模型
- [x] `RC1-3`：模块边界与 JDBC 收口
- [x] `RC1-4`：账户与凭证写侧闭环
- [x] `RC1-5`：marketdata / research 最小能力闭环
- [x] `RC1-6`：全量验证闭环
- [x] `RC1-7`：后端业务域包结构收口

## 2. RC1 冻结基线能力

- [x] 用户 / 账户 / 凭证 / 环境主模型成立
- [x] 默认账户上下文与 `/api/auth/me` 联动成立
- [x] 账户与凭证写侧闭环成立
- [x] JDBC 实现与模块边界收口完成
- [x] `marketdata` 最小 ingest/query 真闭环成立
- [x] `research -> backtest -> eval` 最小 happy path 成立
- [x] 全量构建、测试、启动、E2E smoke 通过

## 3. RC1 未纳入范围

- [x] GateH 功能开发未启动
- [x] publish 深化不在 RC1 必达范围
- [x] 多交易所历史行情平台化不在 RC1 必达范围
- [x] 更复杂的前端运营台与批量操作未纳入 RC1

## 4. 后续入口

- [x] `GateH = paused`
- [x] GateH 尚未启动开发
- [x] 后续必须先进入 `GateH-PLAN`
