# Testing / CI / Git / evidence

按问题直接读取对应小节；参考归属不等于需要调用交易 Skill。通用测试原则归 [Java standard §6](../../../../docs/standards/java/common-java-engineering-standard.md)，文档事实归 [DOC_RULES](../../../../docs/DOC_RULES.md) 与 [FACT_SOURCE_INDEX](../../../../docs/current/FACT_SOURCE_INDEX.md)。

## Synthetic Test Identity Policy

1. Runtime 可使用随机高熵 identity，保留唯一性、幂等及跨进程恢复语义。
2. Git-tracked evidence 默认使用 `SYNTH-L4:<BATCH>:R<NN>:<TYPE>:<NNN>` 等低熵 canonical references；分隔短字段以避免连字符拼接再次触发 generic-api-key。批次内 run 唯一，相同原始身份保持相同引用，不同身份和类型不得合并。
3. Raw runtime identity 默认只保存在 `target/`、Temp 或 CI ephemeral artifacts，不提交原值及其 hash/base64/hex 替身。
4. Canonicalization 必须 field-aware，不能隐藏 credential/secret/token；原始字段与凭证子树保持可被 scanner 拒绝。
5. Secret scanning 不得按 Gate/evidence 目录整体豁免；新批次不扩充历史兼容 allowlist。
6. 新 L4 qualification 导出前必须验证身份关系等价、raw identity 零泄漏和 secret negatives，不能因随机 synthetic identities 批量触发 generic-api-key。

## 回归证明

- 从业务不变量和 bug 失败证据选择成功、失败、边界、非法状态、重复/幂等、权限/租户及适用的并发场景。修复后保留能检测原缺陷的关键断言，不能用删断言、跳过或过度 mock 伪造通过。
- Domain/Service 使用目标 unit tests；HTTP contract 用项目现有 MockMvc/WebMvcTest 或 slice；SQL/映射/事务副作用用隔离 PostgreSQL；跨组件集成只扩到受影响调用链，不一律 `@SpringBootTest`。
- 断言关键业务字段、数据库行与状态、事件、审计、outbox、副作用数量及关联，而不止 HTTP 200 或方法调用。Golden case 固定输入/输出、clock、seed、ID 与外部 fixture；不依赖执行顺序、共享临时状态或 JSON 对象字段顺序。
- 拒绝/失败证明应包含相应外部调用与持久化副作用未发生；部分失败证明已完成和待补偿事实不丢失。重启证明使用真实跨进程状态，不能只 new 对象。
- 命令由当前 POM、测试入口和 CI 合同确定。目标测试优先，跨模块才扩大；完整 Maven 仅在覆盖或实际验收合同要求时运行，不作为普通任务默认值。

## CI / delivery

- verifier 检查 standards/platform/configuration 合同；相关 ArchUnit 和数据库测试证明运行语义。Java Shadow findings 当前为非阻断，但配置、执行、baseline、report 故障必须拒绝，禁止以 soft-fail 掩盖工具失败。
- 发布验收使用当前 canonical delivery 合同与 exact-head CI，核对 commit、候选/fixture、环境、命令、exit、jobs 的实际结论。local PASS、conditional skip、CI success、独立接受、生产已部署是不同事实。
- 制品 safety/provenance 检查复用 `scripts/ci/Test-DeliveryArtifactSafety.ps1`、`scripts/ci/tests/Test-DeliveryEvidence.ps1` 及当前 delivery contract；规则有覆盖范围，不能视为任意秘密扫描或运行时安全的完整证明。
- 风险影响交易/资金、迁移、安全、关键并发或跨模块架构时遵守根约束的真正独立审查；普通任务不强制审查。review-only 不修改候选；实现者换 Skill/角色不产生独立性。

## Git / evidence / docs

- 写前核对实际 repo/branch、目标文件已有改动；最终 diff 只包含授权的最小完整变更，保护用户工作，不夹带生成物、敏感信息、无关 lockfile 或格式化。
- Git 发布操作须用户明确授权；提交时精确选择已检查文件并核对 staged diff。记录候选内容与证据身份，不能把旧候选通过结论直接移给新内容。
- 证据注明候选/fixture hash、环境、命令、退出码、关键结果和未覆盖情况；相同候选/环境/覆盖假设的有效证据可复用，代码、配置、依赖或 fixture 变化时评估失效范围。失败 attempt 与后续成功分别保留，frozen history 不就地改写。
- 文档根据事实写作，以简体中文说明并保留路径/协议/状态 token；区分计划、已实现、已验证、已审查、已接受。API 变化更新 API owner，schema 更新 DB_SCHEMA，阶段/安全事实才查 STATUS，下一动作查 ROADMAP；TESTING/WORKLOG 是按合同需要追加的证据账本，不是普通修改的固定输出。
- 只停止依赖冲突事实的写入，继续无依赖工作。对修改的链接/事实运行相应 docs checker 和 diff check；没有事实变化无需制造文档或计划链。
