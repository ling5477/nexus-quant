# 工程标准与安全边界

这是可直接读取的工程参考，不改变 Skill 触发范围。普通 Java/Spring 任务只读命中主题，不必启动交易证明 Skill；完成判断取决于受影响契约和实际验证。

## Architecture / module ownership

canonical owner 是 [architecture-overlay](../../../../docs/standards/java/architecture-overlay.md)，模块事实查 [ARCHITECTURE](../../../../docs/current/ARCHITECTURE.md) 与 [MODULES](../../../../docs/current/MODULES.md)。

- Controller 做请求/响应转换并传递鉴权上下文；Service 编排业务，SQL、HTTP 细节留在相应 adapter。Repository port 在 core/contracts，持久化实现由 infra 拥有；core 不反向依赖 infra/JDBC，exchange adapter 不直接写库。
- composition root 管装配，不定义业务语义。沿用既有 account、trading、risk、ledger 等 owner，不以跨模块复用为由把领域逻辑塞入公共工具层。
- 大 Service 只在本次目标需要时收口；抽取协作类、重复分支或事务边界必须保持公开契约与行为。检查 Spring 循环依赖、profile、配置默认值/校验以及启动是否被无关配置阻塞。
- 跨模块变更按 affected scope 使用 `ModuleBoundaryArchTest`、`PackageBoundaryArchTest`；它们只证明所编码的依赖规则，不证明全部业务 ownership。

## Java / Spring

语言、依赖或平台问题先核对 [platform-profile.json](../../../../docs/standards/java/platform-profile.json) 与当前 POM/运行配置，再按主题查 [Java profile](../../../../docs/standards/java/java-platform-profile.md)、[Spring profile](../../../../docs/standards/java/spring-platform-profile.md)；普通局部修复无需预读全目录。

[通用规范](../../../../docs/standards/java/common-java-engineering-standard.md) 是命名、精确数值、null/集合、异常/日志、时间、资源、有界并发、SQL、依赖和测试的 canonical owner；[领域 overlay](../../../../docs/standards/java/nq-java-domain-overlay.md) 拥有 NQ 金额、订单、风控、账务与审计约束。

- 装配/配置：constructor injection、明确 stereotype、typed configuration、安全默认值和输入校验；不制造无用途接口或 `ServiceImpl`，不为新语法批量改写稳定 API/identity/序列化模型。
- 事务/并发：检查 Spring proxy、self invocation、private method、propagation、isolation、rollback、readOnly 和 async 边界。状态更新必须校验当前状态并具备相应锁/CAS/version 保护；JVM 锁不能当作跨实例数据库互斥证明。
- 幂等：稳定业务 key、并发重复、窗口、返回语义和失败补偿显式；重试不可换新身份逃过去重。已 accepted 后 timeout 属于结果不确定，须按既有 query/reconciliation 契约收敛，不能盲目重发不可撤销动作。
- 副作用：不可在数据库提交前产生不可撤销交易动作，不在长事务内等待不可控外调；outbox/状态机/补偿沿用已有模型。executor、queue、cache、连接池、批量与重试有界，保留背压、MDC/安全上下文和停止/失败时资源释放。
- 平台/依赖变更核对兼容、依赖图、测试和恢复影响；静态规则变更才按范围执行 verifier/Shadow scan。Shadow finding 与配置损坏的处理不同，不能把 Shadow 规则索引存在写成强制通过证明。

## Risk / credential / LIVE security

根 [AGENTS](../../../../AGENTS.md) 拥有真实操作授权边界；通用规范 `JAVA-COMMON-SECURITY-*` / `SECRET-*` 与领域 `NQ-JAVA-RISK-*` / `SECRET-*` / `AUDIT-*` 拥有详细安全约束。

- 权限、tenant/account、环境和 risk-before-execution 在服务端执行，不能依靠页面隐藏按钮或测试 mock。canonical 环境只有 `SIM / LIVE`；Paper/Shadow/backtest 是执行或验证模式，venue DEMO 仅映射 SIM，不能形成第三个 canonical 环境或危险 fallback。
- credential、真实 provider、PLACE/CANCEL、transfer/withdraw、解除 kill switch 和生产操作同时需要有效 current authority 与用户明确授权。实现/fixture 测试不构成执行授权；高风险输入、未知状态和缺失授权 fail-closed。
- 外调校验输入及目标边界，具备 timeout、有界重试/退避、限流、错误转换和脱敏关联信息。禁止日志、异常、报告、fixture、制品保存 API Key/Secret/passphrase/签名原文或未脱敏响应。
- `Test-DeliveryArtifactSafety.ps1` 只检查其支持的文本、归档和规则；其通过不证明不存在任意编码秘密，也不授予 LIVE 权限。行为安全仍以对应权限/risk/adapter 测试及风险相关审查证明。

## 完成判断

指出本次适用的标准条目与根因/契约，提供目标验证；不适用或未运行明确说明。涉及交易因果、数据库、重启时，读取 [证明选择](proof-selection.md)；测试或交付问题读取 [回归与交付](regression-delivery.md)。不固定全量 Maven、Shadow scan 或普通任务独立 review。
