# 工程标准索引

普通 Java/Spring 任务直接读取命中主题，不调用交易 Skill，不预读整个标准库。

| 主题 | 唯一详细 owner |
| --- | --- |
| 模块、controller/service/repository、composition root | [architecture-overlay](../../../../docs/standards/java/architecture-overlay.md) |
| Java 命名、数值、资源、异常、日志、并发、SQL、安全 | [通用标准](../../../../docs/standards/java/common-java-engineering-standard.md) |
| Java 版本与语言能力 | 当前 POM、[platform-profile](../../../../docs/standards/java/platform-profile.json) 与 [Java profile](../../../../docs/standards/java/java-platform-profile.md) |
| Spring 装配、配置、事务、代理与 async | [Spring profile](../../../../docs/standards/java/spring-platform-profile.md) |
| 交易金额、订单、风险、账务及审计 | [领域 overlay](../../../../docs/standards/java/nq-java-domain-overlay.md) |
| 交易证明 | [证明选择](proof-selection.md) |
| 验证、审查与交付 | [统一合同](regression-delivery.md) |

Java 源码与测试通过显式 import 引用类型，正文使用简单类名或 Outer.Inner，不用通配符 import 或包名开头的全限定类型。同名冲突先调整导入或使用外层限定；无法消除时说明原因，不改变公开契约或业务语义。字符串中的反射和配置类名除外。

真实模块事实按问题查 ARCHITECTURE/MODULES；nq-api 不写 SQL，nq-core 不依赖 JDBC/infra，持久化在 nq-infra，exchange adapter 不直接写库。静态 verifier 只证明其覆盖的规则，不能替代业务证明；仅规则/平台变更或明确门禁时运行相关扫描。
