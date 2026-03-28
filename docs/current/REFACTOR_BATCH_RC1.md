# RC1（项目收口重构批次）

当前状态：**进行中**

## 1. 目标

- 清理仓库中的无用产物、敏感文件、弃用配置、历史残留实现
- 正式建立“用户 - 交易账户 - 凭证 - 环境（SIM/LIVE）”主模型
- 将交易所凭证从全局 env/yml 配置模式切为数据库密文存储 + 服务端管理 + 前端可配置
- 重构 Java 模块边界：`nq-core` 不再包含 JDBC 实现，`nq-api` 不再直接写 SQL，controller 不再直接依赖 scheduler 具体实现
- 按业务域重整包结构，至少覆盖 `account / auth / strategy / trading / research / marketdata`
- 建立 `marketdata` 正式域与 Python 研究子工程骨架
- 建立前端账户上下文与账户/凭证管理入口
- 补 ArchUnit 约束与全量验证

## 2. 严格范围

- 不做 GateH 新功能
- 不做新交易所接入
- 不做复杂研究功能扩展
- 不做大规模 UI 美化
- compat drop 单独后续处理，RC1 只完成主读写切换与兼容层收口

## 3. 执行顺序

1. `RC1-0`：文档切换
2. `RC1-1`：仓库清理
3. `RC1-2`：表结构重构
4. `RC1-3`：Java 模块与包结构收口
5. `RC1-4`：前端基础重构
6. `RC1-5`：marketdata 域与 Python 研究骨架
7. `RC1-6`：残留清理与全量验证

## 4. 关键锁定规则

- 正式运行 profile 禁止从 `.env` / `application*.yml` 直接读取交易所凭证作为主数据源
- legacy env 只允许被显式导入工具读取
- `trade_env` canonical 固定为 `SIM / LIVE`
- `GateH` 保持暂停，待 RC1 完成后再重新规划
