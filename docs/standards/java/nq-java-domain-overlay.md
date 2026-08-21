# NexusQuant Java 领域 Overlay

本文件只补充 NQ 领域约束，不改变业务逻辑、交易合同、状态机、Schema、Golden Case、Authority 或 Gate。冲突时以当前 `docs/current/STATUS.md`、冻结合同和更严格安全边界为准。

## 数值

- `NQ-JAVA-NUMERIC-001`：价格、数量、金额、手续费、PnL 和收益率关键计算禁止使用 `float` / `double`。
- `NQ-JAVA-NUMERIC-002`：`BigDecimal` 必须明确 scale、rounding、单位和精度来源，禁止通过 `double` 构造。

## 时间与确定性

- `NQ-JAVA-TIME-001`：回测、Shadow、Paper、调度、恢复和状态机使用可注入时间源；基础设施时间读取必须在边界转换为统一 UTC 语义。
- `NQ-JAVA-DETERMINISM-001`：随机过程必须有显式种子；禁止依赖默认时区；同一输入、版本、种子和时钟的回放结果必须可复现。

## 交易模式隔离

- `NQ-JAVA-MODE-001`：LIVE、PAPER、SHADOW、BACKTEST 使用显式类型和配置隔离；禁止默认值、模糊 profile 或 fallback 跨越模式边界。
- `NQ-JAVA-MODE-002`：当前 Shadow 静态检查不启用 Shadow trading，不建立真实下单/撤单路径，也不改变 LIVE 或 Kill switch。

## 订单、风控与状态机

- `NQ-JAVA-ORDER-001`：订单状态迁移必须经过既有状态机，禁止直接覆盖状态字段。
- `NQ-JAVA-IDEMPOTENCY-001`：订单意图、提交、撤单、回调和恢复使用稳定幂等键；重试不得生成破坏去重的新身份。
- `NQ-JAVA-RETRY-001`：交易所失败区分可重试与不可重试错误；重试次数、退避、timeout、审计和终止条件显式。
- `NQ-JAVA-RISK-001`：所有交易意图先经过既有风控和账户边界；工程规范不得降低或绕过风控校验。

## 外部副作用与一致性

- `NQ-JAVA-SIDE-EFFECT-001`：禁止在数据库事务提交前产生不可撤销交易副作用；必须明确 timeout、retry、idempotency、audit 和失败补偿。
- `NQ-JAVA-CONSISTENCY-001`：订单、账务、持仓、资金与审计的多表写入必须沿用已批准事务/Outbox/状态机策略，规范治理不得自行改写一致性模型。

## 安全与审计

- `NQ-JAVA-SECRET-001`：禁止记录 API Key、Secret、passphrase、私钥、签名原文、完整账户数据或未脱敏交易所响应。
- `NQ-JAVA-AUDIT-001`：决策、风控快照、订单意图、执行请求、外部响应摘要和最终状态保持可追溯关联；不得重写历史证据来通过检查。

## Java / Spring 平台能力约束

- `NQ-JAVA-MODERN-001`：record、sealed hierarchy、pattern switch 与其他正式语言能力只在保持金额精度、identity、状态机和序列化 contract 时使用；禁止为现代化批量重构稳定模型。
- `NQ-JAVA-VTHREAD-001`：virtual threads 当前未启用；任何后续候选必须先验证 Spring lifecycle、MDC/security context、exchange concurrency limit、连接池、背压和 shutdown。
- `NQ-JAVA-SPRING-TX-001`：Spring transaction 必须保持 order、ledger、audit 与 exchange side-effect 的既有顺序；self invocation、private method 和 async boundary 不得伪造事务保护。
- `NQ-JAVA-SPRING-ASYNC-001`：异步任务不得使用 common pool、raw thread 或 unmanaged executor 绕过调度幂等、风险 gate、账户边界和观测。
- `NQ-JAVA-REPRODUCIBILITY-001`：dataset、backtest、research、Paper 与 Shadow 的版本、时间、seed、输入和结果证据必须可复现；平台语法升级不得改变相同输入的语义。
