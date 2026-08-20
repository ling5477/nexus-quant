# 通用 Java 工程规范

版本：`2.0.0`
适用：NexusQuant 与 Decision Hub 的 Java 工程。
优先级：用户与安全边界 > 当前 Authority / 冻结合同 / Schema / Golden Case / 状态机 / runtime invariants > 领域架构 > 实际 Java 平台 > 实际 Spring 平台 > 本规范 > Alibaba Huangshan 适配 > IDE 偏好。

本文件只保留跨项目、跨受支持平台 minor version 成立的工程原则，并在 NQ、DH 中保持 UTF-8、LF、字节完全一致。具体语言能力、Spring API 和模块边界分别读取 `java-platform-profile.md`、`spring-platform-profile.md` 与 `architecture-overlay.md`。

## 1. 命名、对象与接口

- `JAVA-COMMON-NAMING-001`：包、类型、方法、字段与常量遵守 Java 约定并表达业务语义；禁止误导缩写、中英拼音混杂和歧视性词语。
- `JAVA-COMMON-CONSTANT-001`：超时、重试、限流、批量大小、精度和业务阈值使用具名配置或常量；禁止硬编码生产地址和凭证。
- `JAVA-COMMON-OBJECT-001`：对象相等、hash、identity 与生命周期语义必须一致；语言简写不得改变领域对象语义。
- `JAVA-COMMON-ARCH-001`：公共类型只暴露稳定契约；内部实现保持最小可见性，数据库 Entity 不直接作为对外 API。
- `JAVA-COMMON-ARCH-002`：接口只用于 port、SPI、跨模块 contract、可替换实现、多策略或明确架构边界；单一实现且无替换语义的应用服务允许使用具体类，禁止 `ServiceImpl` / `RepositoryImpl` ceremony。

## 2. 数值、集合与空值

- `JAVA-COMMON-NUMERIC-001`：金额与精确小数使用明确的 scale、rounding、单位和精度来源；禁止二进制浮点隐式进入精确计算。
- `JAVA-COMMON-COLLECTION-001`：集合的可变性、顺序、去重和容量边界必须显式；返回空集合而不是 `null`。
- `JAVA-COMMON-GENERICS-001`：禁止无理由 raw type 和不安全强转；泛型边界表达真实生产者/消费者语义。
- `JAVA-COMMON-OPTIONAL-001`：可选返回语义不得掩盖必填输入校验；字段、参数和序列化模型是否允许 optional 由平台 profile 与 contract 决定。
- `JAVA-COMMON-NULL-001`：公共入口明确 nullability；失败使用有语义的异常或结果类型，禁止含义不明的 `null`、`false`。

## 3. 控制流、异常与日志

- `JAVA-COMMON-CONTROL-001`：控制流保持穷尽、可读与 fail-closed；不得用默认成功路径吞掉未知状态。
- `JAVA-COMMON-EXCEPTION-001`：不得空 `catch`、仅打印、吞异常或无差别包装；依赖异常转换为项目错误并保留 cause。
- `JAVA-COMMON-EXCEPTION-002`：区分输入、业务拒绝、依赖失败、超时、并发冲突与基础设施故障；一致性受损时记录失败状态或补偿证据。
- `JAVA-COMMON-LOG-001`：关键路径提供结构化日志、错误码、耗时与适用的 trace/request/tenant/account/biz 标识。
- `JAVA-COMMON-LOG-002`：正式代码不得使用标准输出代替日志；工具输出应提供稳定、可解析摘要。
- `JAVA-COMMON-SECRET-001`：日志、异常、注释、报告与测试夹具不得泄露凭证、签名原文、隐私或未脱敏敏感数据。

## 4. 并发、时间、资源与副作用

- `JAVA-COMMON-CONCURRENCY-001`：共享状态声明线程安全策略；executor、queue、cache 和并发度必须有界并具备背压与生命周期管理。
- `JAVA-COMMON-CONCURRENCY-002`：并发更新使用锁、CAS、版本或状态机保护，不得以“低概率”替代竞态处理。
- `JAVA-COMMON-TIME-001`：领域与应用逻辑使用可替换时间抽象；系统时钟读取和 legacy 时间转换只发生在明确边界。
- `JAVA-COMMON-TIME-002`：时区、时间精度和持久化语义显式；禁止依赖服务器默认时区。
- `JAVA-COMMON-RESOURCE-001`：文件、连接、锁、executor、订阅与临时资源在成功和异常路径均正确释放。
- `JAVA-COMMON-EXTERNAL-001`：外部调用具有 timeout、错误映射、有限重试、限流、追踪、环境隔离和敏感信息过滤。
- `JAVA-COMMON-IDEMPOTENCY-001`：回调、消息、任务、导入、同步、资金和状态流转定义稳定幂等键、窗口、并发重复处理与返回语义。

## 5. 事务、数据库与 SQL

- `JAVA-COMMON-TX-001`：事务范围最小；多表写、状态迁移和审计写入明确隔离、锁、失败补偿与一致性策略。
- `JAVA-COMMON-TX-002`：禁止在事务提交前产生不可撤销外部副作用，禁止在事务内等待不受控长耗时调用。
- `JAVA-COMMON-DB-001`：数据库规则服从仓库实际数据库、Migration、Repository/JDBC 与冻结 Schema；外部手册的数据库专有规则不得直接成为项目 Authority。
- `JAVA-COMMON-DB-002`：禁止 N+1、无边界全表扫描和无上限批量；分页、索引、排序与内存上限必须可解释。
- `JAVA-COMMON-SQL-001`：SQL 参数绑定、字段、过滤、排序和锁语义显式；禁止拼接不可信输入与依赖未声明顺序。
- `JAVA-COMMON-SQL-002`：金额精度、事务隔离、并发写、时区和结构化字段边界显式；半结构化字段不得替代核心关系模型或保存凭证。
- `JAVA-COMMON-MIGRATION-001`：Schema 变更通过既有 Migration，历史 migration 不修改，兼容修复使用 forward-only 语义。
- `JAVA-COMMON-MIGRATION-002`：大表 migration/backfill 分页、限流、可恢复并说明锁与回滚风险。

## 6. 注释、测试与依赖

- `JAVA-COMMON-DOC-001`：公共 API、SPI、复杂领域对象和特殊实现记录用途、不变量、失败模式、并发、事务与副作用；不堆砌样板注释。
- `JAVA-COMMON-DOC-002`：注释解释 Why 和约束并随实现同步；TODO/FIXME 包含日期、原因、完成条件和风险。
- `JAVA-COMMON-DOC-003`：Git 是作者与历史权威；禁止维护易失真的作者和创建日期注释。
- `JAVA-COMMON-TEST-001`：核心修改覆盖正常、失败、边界以及适用的权限、租户、幂等、并发和非法状态。
- `JAVA-COMMON-TEST-002`：测试确定性、隔离外部服务并控制时间与随机源；skip、flaky 或环境阻塞不得写成 PASS。
- `JAVA-COMMON-DEPENDENCY-001`：新增或升级依赖说明用途、替代、许可证、安全、兼容与回滚；标准升级不得顺带升级技术栈。
- `JAVA-COMMON-DEPRECATED-001`：新增代码不使用平台已弃用 API；历史用法进入 baseline，迁移服从合同与独立任务边界。
- `JAVA-COMMON-GENERATED-001`：generated source、vendor 和外部依赖源码不手工修改，并从治理扫描中排除。

## 7. 安全、可观测性与确定性

- `JAVA-COMMON-SECURITY-001`：外部输入执行长度、格式、枚举、权限、租户与账户边界校验。
- `JAVA-COMMON-SECURITY-002`：高风险能力默认拒绝；生产地址、凭证、provider/LIVE 开关和权限提升必须由独立 Authority 授权。
- `JAVA-COMMON-OBS-001`：关键业务路径提供日志、指标、审计关联和可诊断错误；不得以观测为由泄露敏感数据。
- `JAVA-COMMON-DETERMINISM-001`：回放、证据、checksum、排序和生成物可复现；内容 hash 不得依赖未规范化当前时间或集合顺序。

## 8. 静态治理与豁免

- `JAVA-COMMON-STATIC-001`：复用仓库实际静态工具并验证平台兼容；旧版 p3c 工具或 IDE 插件不直接作为 CI Authority。
- `JAVA-COMMON-STATIC-002`：高置信度结构规则可静态检查；事务、状态机、精度、幂等和架构依赖继续由 ArchUnit、合同/集成/数据库测试与 review 验证。
- `JAVA-COMMON-STATIC-003`：扫描范围由 platform profile 和 architecture scope 驱动；禁止全仓字符串 grep 冒充语义分析。
- `JAVA-COMMON-SHADOW-001`：Shadow finding 不阻断；配置、mapping、platform profile、baseline、执行和报告故障必须阻断。
- `JAVA-COMMON-EXCEPTION-MECH-001`：豁免引用现有 rule ID、精确 scope、审批、创建时间与客观到期条件；禁止 wildcard、suppress-all 和允许新增调用点。
