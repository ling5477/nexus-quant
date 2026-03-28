# Current Modules（RC1）

## `frontend`

- RC1 的前端主改对象
- 负责账户上下文 store、header 上下文入口、账户与凭证管理页面骨架
- 负责拆分过大的页面组件，抽查询区 / 表格区 / 详情抽屉壳
- 负责逐步替代长期依赖手工输入 `accountId` 的模式

## `nq-api`

- 继续作为正式 HTTP API 层
- 负责 controller、request/response DTO 与 API contract
- RC1 期间不得再直接写 SQL
- RC1 期间不得再直接依赖 scheduler 具体实现

## `nq-core`

- 只保留业务核心、port、domain model 与 application service
- RC1 期间必须迁出所有 JDBC 实现

## `nq-infra`

- 作为 JDBC、Flyway、query adapter 与持久化适配层承接者
- RC1 期间负责接住从 `nq-core`、`nq-api` 下沉出来的 SQL 与 JDBC 实现

## `nq-app / nq-auth / nq-security / nq-gateway`

- `nq-app` 只负责装配与 profile 入口
- `nq-auth` 负责认证应用服务
- `nq-security` 负责 token 与过滤器
- `nq-gateway` 负责安全上下文桥接
- RC1 期间认证数据源要从配置驱动切到 DB-backed `users/roles/user_roles`

## `nq-scheduler`

- 负责调度、reconcile、recovery 等运行时编排实现
- RC1 期间只允许通过 application-facing service 向上暴露能力

## `nq-research / nq-backtest / nq-eval`

- 继续承接研究、回测、评估与发布事实能力
- RC1 期间要补 `marketdata` 正式域与 DB-backed 历史行情输入路径

## `research/py`

- RC1 期间从样例目录升级为正式研究子工程
- 负责 `data / strategy / backtest / tests` 包结构与 Python 工具链

## 本阶段边界

- 当前阶段只做结构收口、清理、基础模型与验证
- 当前阶段不恢复 GateH 新功能
- 当前阶段不做新交易所接入与复杂研究扩张
