# Java Platform Profile

本文件描述 `platform-profile.json` 当前探测到的 Java 编译平台。版本事实必须从 profile 读取；未来平台升级只更新 profile 和兼容性审查，不在 Skill 中硬编码版本。

## 正式语言能力

- `JAVA-PLATFORM-RECORD-001`：允许 record 用于 immutable DTO、value object、command/query payload、projection、configuration carrier 与 test fixture；不得机械替代具有 identity、可变状态机或代理继承要求的对象。
- `JAVA-PLATFORM-SEALED-001`：closed-world 的 result、command、event、state 或 error taxonomy 可以使用 sealed hierarchy；禁止为追求现代语法重构稳定开放层次。
- `JAVA-PLATFORM-PATTERN-001`：允许平台正式支持的 `instanceof` pattern、pattern switch 与 record pattern；保持穷尽性和领域状态安全。
- `JAVA-PLATFORM-SWITCH-001`：允许 switch expression；未知状态的 default 策略必须显式且 fail-closed。
- `JAVA-PLATFORM-VAR-001`：局部 `var` 仅在右侧类型明显且不损害金额、状态、泛型或跨层 DTO 语义时使用；既不全面禁止也不强制。
- `JAVA-PLATFORM-TEXT-BLOCK-001`：允许 text block 表达长文本、JSON 或 SQL；参数绑定、安全过滤和缩进语义仍必须成立。
- `JAVA-PLATFORM-COLLECTION-001`：允许 `Stream.toList()`、copy factories 与 SequencedCollection family；不得假定 `Stream.toList()` 可变，也不得机械替换后改变可变性。

## Preview 与并发

- `JAVA-PLATFORM-PREVIEW-001`：compiler、CI、runtime、test 与 deployment 未共同启用并获 Authority 授权时，禁止 preview features。
- `JAVA-PLATFORM-VTHREAD-001`：virtual threads 在平台可用但不是默认架构选择；未启用时禁止 Agent 自行引入或替换 executor 模型。
- `JAVA-PLATFORM-ASYNC-001`：`CompletableFuture` 必须使用项目明确 executor、timeout、failure handling、context propagation 与 shutdown；禁止无依据使用 common pool。
- `JAVA-PLATFORM-EXECUTOR-001`：禁止 raw thread 与 unmanaged executor 绕过 Spring/项目生命周期、MDC、安全上下文、rate limit、数据库连接池或 adapter concurrency boundary。

## 时间与兼容

- `JAVA-TIME-DIRECT-READ`：domain/application 层禁止直接调用系统时钟；使用 `Clock` 或项目 TimeProvider。
- `JAVA-LEGACY-DATE-IN-DOMAIN`：`Date`、`Calendar` 等 legacy 类型仅在协议要求的 adapter boundary 转换。
- `JAVA-PLATFORM-TIME-001`：内部优先使用 `Instant`、`Duration`、`LocalDate`；只有真实 timezone semantics 才使用 offset/zoned 类型。
- `JAVA-PLATFORM-OPTIONAL-001`：Optional 主要用于返回值；字段、参数和序列化模型必须服从 contract，不作为 nullability 逃生口。
