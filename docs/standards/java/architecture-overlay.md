# NexusQuant Java Architecture Overlay

来源：`docs/current/ARCHITECTURE.md`、`docs/current/MODULES.md`、root/module POM 与现有 ArchUnit tests。本文件描述实际架构，不决定 current Gate。

## 模块方向

- `NQ-ARCH-APP-001`：`nq-app` 是 Spring Boot composition root，只做启动、profile 与 wiring，不拥有业务主语义。
- `NQ-ARCH-API-001`：`nq-api` 承载 controller、DTO、web adapter 和 API contract，不写 SQL。
- `NQ-ARCH-CORE-001`：`nq-core` 承载 domain、policy、port 与 application service，不依赖 JDBC、`nq-infra` 或具体 exchange adapter。
- `NQ-ARCH-INFRA-001`：`nq-infra` 承载 JDBC、Flyway、repository/query adapter，不反向定义 core 语义。
- `NQ-ARCH-ADAPTER-001`：`nq-adapter-api` 定义 exchange contract；具体 adapter 不把交易所模型泄漏为平台主语义，也不因规范升级获得 real permission。
- `NQ-ARCH-OWNER-001`：account、auth、trading、strategy、research、marketdata、ledger、risk、scheduler、observability 继续由现有模块和 package owner 管理。

## Spring 与并发边界

- `NQ-ARCH-SPRING-001`：Spring stereotype、transaction 与 configuration 必须服从现有 module owner 和 composition root，不创建机械 `ServiceImpl`。
- `NQ-ARCH-EXECUTOR-001`：executor 变化必须审查交易所并发上限、连接池、MDC、安全上下文、停止语义和调度幂等；本任务不启用 virtual threads。
- `NQ-ARCH-HTTP-001`：现有 JDK/Spring/exchange client 模型保持不变；新代码只在对应 adapter boundary 选择当前已批准 client。

## 不变量保护

- `NQ-ARCH-TRADING-001`：Java record、sealed hierarchy、pattern switch、async、transaction 或 virtual threads 不得弱化模式隔离、订单状态机、风控、幂等、账务、审计和外部副作用顺序。
- `NQ-ARCH-DETERMINISM-001`：research/backtest/dataset/Shadow/Paper 的时间、随机种子和证据链保持可复现。
