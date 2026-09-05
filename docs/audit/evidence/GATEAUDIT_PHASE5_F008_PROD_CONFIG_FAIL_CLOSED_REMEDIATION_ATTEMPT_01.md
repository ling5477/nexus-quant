# F008 Production Configuration Fail-Closed — Remediation Attempt-01

日期：2026-09-05。任务：`NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REMEDIATION-ATTEMPT-01`。

结论：`IMPLEMENTED / PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REMEDIATION_ATTEMPT_01_COMPLETE / P0_0 / P1_3_REMEDIATED / P2_1_REMEDIATED / PENDING_INDEPENDENT_REVIEW`。

本文件是追加的实施/验证证据，不是独立正式 Review 或 acceptance。四项 finding 均为 `REMEDIATED_PENDING_INDEPENDENT_REVIEW`（已整改，待独立复审）；历史 Review 的 FAIL 结论保持不变。

## 1. 分类、基线与边界

- Repository：`E:\Project\nexus-quant-gateaudit`；NQ-only，`SECURITY_REMEDIATION / HIGH_RISK / FORWARD_ONLY / NO_COMMIT`。
- branch：`audit/post-gatey-agent-baseline`。
- HEAD 与 `origin/audit/post-gatey-agent-baseline`：`aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc`。
- `origin/dev`：`4c19cb775ebb18b4288400a5a1a402145c2fe30a`；`git merge-base --is-ancestor origin/dev HEAD` exit=0。
- 本任务 preflight 已执行 `git fetch origin --prune`、status、branch、refs、ancestor、diff/check/stat/cached 检查。开始与结束均 staged=0；commit=NONE，push=NONE。
- 只整改 Formal Review 的 P1-01/P1-02/P1-03/P2-01。不重构 JWT claims/authorization，不修改真实 credential，不做 ciphertext rotation、key-version migration、线上重加密、部署或真实交易。
- P5-F007、P5-F009 仍为 `OPEN / NOT_IMPLEMENTED`；不清理 legacy profiles/helpers，不进入 Phase6，不改 frontend、migration、governance matcher、CI capability registry 或 frozen archive。
- Skill routing：`nq-dh-workflow-router` 解析 authority；primary=`java-backend-maintenance`；supporting=`java-backend-regression-tests`（永久回归与失败副作用证明）、`nq-docs-writer`（用户要求的证据与 current owner 同步）。

## 2. 继承 Finding、RCA 与最小整改

原始证据：[Implementation](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_IMPLEMENTATION.md)、[Formal Review](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW.md)。两份文件未改写。

| Finding | RCA | 本次整改与证明 |
| --- | --- | --- |
| P1-01 `CANONICAL_PRODUCTION_PROFILE_MIXING` | 原 guard 只识别是否包含 prod，未拒绝 ConfigData include/group 引入的 local 等 profile | 在 initializer 中检查最终 effective active profile set 必须严格等于 `{prod}`；包含 profile expansion、marker 与 developer 回归 |
| P1-02 `PRODUCTION_JWT_PUBLIC_DEFAULT_KEY` | prod 可继承基础配置公开 JWT signing secret；旧 key 可签发 synthetic ADMIN token | prod YAML 无 fallback；Binder 读取最终 `nq.security.secret` 并拒绝缺失/空白/公开默认值；永久旧 key 签名拒绝测试 |
| P1-03 `CI_PRODUCTION_FALLBACK_RESTORATION_NOT_BLOCKED` | CI 只检查 guard/部署结构，未直接绑定实际 production YAML 的五项值 | canonical validator 直接检查完全限定 YAML property 的无默认 placeholder、exact profile set、initializer registration；新增 12 项 mutation 全拒绝 |
| P2-01 `PRODUCTION_CREDENTIAL_ENCRYPTION_PUBLIC_DEFAULT_KEY` | prod 可继承公开 credential encryption master key | 校验最终 `nq.account.credentials.master-key`，prod YAML 无 fallback；与 JWT 相同失败矩阵；不涉及历史密文轮换 |

开发期回归曾出现 6 个失败：缺失 external placeholder 经 Binder 可能保留为非空 `${...}` 字面量。RCA 后在共用 `requiredProperty` 中拒绝未解析 placeholder，未依赖 bean 后续绑定报错。最终 targeted 111/111、full Maven 均通过。失败未被算作 PASS；初始 targeted log 已被修正后的运行覆盖，本段保留 RCA。

guard 继续在 ConfigData 解析后、context refresh 前执行；datasource/Hikari/JNDI/XA/独立 Flyway identity 拦截保持不变。异常只输出 property name、固定原因与 `PROD_CONFIGURATION_INVALID`，不传播可能携带输入值的绑定异常 cause。

## 3. Effective profile 矩阵

永久测试：[ProductionSecretProfileRegressionTest.java](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionSecretProfileRegressionTest.java)。使用真实 SpringApplication/ConfigData，移除宿主 system/environment property sources；测试通过显式 synthetic source 注入，不读取开发者实际配置。

| 输入/解析路径 | 结果 |
| --- | --- |
| prod only + 合法 synthetic 配置 | PASS；最终 set=`{prod}` |
| prod + local/test/ci/paper/freeze/gated-verify | 全部 REJECTED |
| prod + gatew-okx-readonly-soak/gatey-readonly-qualification | 全部 REJECTED |
| prod + public-marketdata-manual/unknown | 全部 REJECTED |
| `SPRING_PROFILES_INCLUDE=local` | REJECTED |
| `spring.profiles.group.prod=local` | REJECTED |
| alias group 展开为 prod/local | REJECTED |
| `SPRING_APPLICATION_JSON` include 注入 | REJECTED |
| CLI include 注入 / system property include 注入 | 全部 REJECTED |
| marker=true，最终无 prod（local/test/ci/default） | 全部 REJECTED |
| prod active，marker absent/false/true，缺少 JWT key | 全部 REJECTED；marker 不能关闭 guard |
| 无 marker 的 local/test/ci/default-local，无 production keys | PASS；开发/测试行为保留 |

没有批准额外 adjunct profile；canonical contract 的 `approvedActiveProfiles` 固定为 `["prod"]`。实际 systemd `ExecStart` 固定 prod 与 marker=true；该 unit 是继承 candidate，本 Attempt 未再次修改。

## 4. Effective secrets 与 JWT 回归

canonical identity 复用 `nq.security.secret` / `NQ_SECURITY_SECRET`、`nq.account.credentials.master-key` / `NQ_ACCOUNT_CREDENTIALS_MASTER_KEY`，没有第二套 PROD key property。

| 最终 effective value / 来源 | JWT | Credential master key |
| --- | --- | --- |
| missing / empty / whitespace | REJECTED | REJECTED |
| 首尾空白 / 未解析 placeholder | guard 拒绝 | guard 拒绝 |
| repository base/local/test/gated-verify 的 7 个公开默认字符串（含跨 key 复用） | 全部 REJECTED | 全部 REJECTED |
| 唯一 synthetic strong key | PASS | PASS |
| canonical environment / Spring relaxed environment | PASS | PASS |
| CLI / system properties / SPRING_APPLICATION_JSON | PASS | PASS |
| 明确的 classpath config import fixture | PASS | PASS |
| 高优先级 JSON 空白覆盖低优先级有效值 | REJECTED | REJECTED |

校验的是 Binder 最终 property，不是只看某个环境变量是否存在。保留 Spring 正常 source precedence。import fixture 只用于测试，不意味着授权任意生产 import。

`rejectsOldDefaultSignedRolesUnderValidProductionKey` 通过现有 `JwtTokenService` 签发带 ADMIN role 的 synthetic token：有效 runtime key 签发/parse 正向控制成功；公开旧 default key 签发的 token 被当前有效 key runtime 拒绝。未更改 JwtTokenService、claims 或角色查库机制。

真实 production provisioning、密钥质量/熵治理、ciphertext rotation 和旧数据解密迁移未验证且不在本任务范围；不得据本地 synthetic PASS 推断线上可直接换 key。

## 5. Datasource、启动时序与脱敏

[application-prod.yml](../../../backend/nq-app/src/main/resources/application-prod.yml) 中五项配置均为无 fallback placeholder：datasource URL/user/password、JWT secret、credential master key。prod Flyway 保持 `enabled=false`，未改变 migration ownership。

- 原 50 项测试保留 DataSource construction/connection counters；失败均在 refresh 前，计数为 0。
- 新 61 项测试的拒绝断言检查 application bean creation probe=0；补充真实 DataSource/Flyway auto-configuration 失败路径，未出现 Hikari/Flyway 初始化。
- executable JAR 的 9 个 negative cases：missing JWT、missing master、default JWT、default master、profile include、profile group、profile JSON、marker=false 且缺 key、invalid datasource。全部以 `PROD_CONFIGURATION_INVALID` 失败，未进入 refresh/Hikari/Flyway/应用 started 阶段。
- JAR harness 增加独立 loopback TCP listener，每项先用 synthetic client 正向验证探针可见性，再把 datasource 指向该随机端口。9/9 项在子进程退出后无 pending DB TCP connection（0）；invalid URL 项由格式 guard 先拒绝。无现有 DB/5432 访问。
- outbound=0 的证明范围：应用级构造/连接计数、pre-refresh 时序以及上述 JAR loopback DB 连接探针；不是 OS 全网抓包，不声称观测了所有网络接口的每个 SYN。
- 两个测试 key 使用独立 UUID 生成，assertions 不输出 expected/actual secret；输出捕获和异常链检查全部通过。JAR 另生成唯一 synthetic pair，stdout/stderr 原值 occurrences=0。
- targeted/full/Surefire 与本次生成证据搜索完整 synthetic key 格式，匹配文件数=0；本文件仅记录 property name、方法、结果，无 key 值。

构建 JAR：`backend/nq-app/target/nq-app-0.1.0-SNAPSHOT.jar`；SHA256=`017eea9c0e3b1dcba38ed28f71648064c0ab3887960f71158b4f3a3dee4a098b`。JAR/helper/log 为 ignored 本地验证产物，不是 deployable admission 或生产启动授权。

## 6. CI anti-regression

[Validator](../../../scripts/ci/Test-CanonicalDeliveryWorkflow.ps1) 读取真实 production YAML 的 fully-qualified keys；校验五个 placeholder 精确无默认值、prod activation、Flyway=false、exact approved profiles、registration、回归文件/关键测试入口。检查真实 invariant，无 Finding/Attempt/mutation 名称特判。

解析器有意仅接受单文档 block mapping/scalar 子集；重复 key、非法缩进、多文档和不支持的 YAML 语法 fail-closed。不是通用 YAML parser；未来扩展格式须同步语义验证。

[Mutation suite](../../../scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1) 使用隔离副本，不修改真实候选文件。

| 新增 mutation | 数量 | 结果 |
| --- | --- | --- |
| M-PROD-01～05：分别恢复 URL/user/password/JWT/master-key fallback | 5 | 全部 REJECTED |
| M-PROD-06：systemd prod+local / approved set prod+local | 2 | 全部 REJECTED |
| M-PROD-07：删除 / 破坏 initializer registration | 2 | 全部 REJECTED |
| duplicate production YAML key / extra YAML document | 2 | 全部 REJECTED |
| 删除 secret/profile regression suite | 1 | REJECTED |

既有 72 项 + 新增 12 项 = 84/84 REJECTED；accepted mutations=0。Windows PowerShell 5.1 与 PowerShell 7 都通过。required jobs=9，actual/registered capabilities=25/25，missing=0，unknown=0。未新增 capability 或修改 registry。

## 7. 实际验证结果与复验入口

| 检查 | 命令/证据 | 结果 |
| --- | --- | --- |
| targeted F008 | 下方 Maven 命令；`artifacts/f008-remediation-targeted-final.log` | exit=0；50+61=111 tests，failures/errors/skips=0 |
| Full Maven | `mvn -o -f backend/pom.xml test`；`artifacts/f008-remediation-full.log` | exit=0；23/23 modules BUILD SUCCESS |
| nq-app full | 同上 | 437 tests，failures=0，errors=0，既有 skips=35 |
| PG16 app-context smoke | `NqAppContextPostgresSmokeTest`，required=true | 1/1 PASS，skip=0 |
| PG16 repository smoke | `JdbcRepositoryPostgresSmokeTest`，required=true | 1/1 PASS，skip=0 |
| canonical release regression | `scripts/deployment/tests/Test-NqCanonicalRelease.Tests.ps1` | 66/66 PASS |
| canonical validator | `scripts/ci/Test-CanonicalDeliveryWorkflow.ps1` | PASS；9 jobs / 25 capabilities / missing=0 / unknown=0 |
| CI mutations | `scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1` | PS5.1 和 PS7 exit=0；各 84/84 REJECTED |
| packaged initializer discovery | `artifacts/f008-remediation-jar-proof.ps1` | exit=0；9/9 REJECTED；raw secrets=0；DB TCP connections=0 |
| frontend | 未运行 | NOT_REQUIRED |
| 远端 exact-head CI | 未运行 | NOT_RUN；本地结果不冒充远端 CI acceptance |

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am test '-Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest' '-Dsurefire.failIfNoSpecifiedTests=false'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci/Test-CanonicalDeliveryWorkflow.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File artifacts/f008-remediation-jar-proof.ps1
```

Full Maven 和双 smoke 通过本地 `artifacts/f008-remediation-verify.ps1` 在 sanitized CI environment 中执行：PG16.15（server_version_num=160015），cached digest=`postgres@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`，仅绑定 `127.0.0.1:42177`。复验脚本每次选择新的随机 loopback 端口，不复用 42177 或共享 5432。既有 `BackendCiLegacyAccountFixture.java` 完成 V1→V46，pending=0。任务容器 finally 清理，residue=0；Docker Desktop 本轮启动后保持运行。依赖/Maven/镜像均使用缓存，未下载。

`NQ_NO_OUTBOUND=true`、真实交易/AI/DH 开关关闭、venue endpoint 使用占位值；只连接新建 disposable PG16。未访问生产 DB、生产服务器、真实 exchange 私有 API 或 credential。

## 8. Review helper 事件与独立检查

历史 Formal Review 记录：discarded PowerShell packaged-JAR helper 使用保留变量导致 prod/marker 参数丢失，曾短暂默认 local 连接本机 PG17。该失败不计历史 PASS，本轮未复用该 helper。

该旧 helper 是当时外部 disposable fixture，当前已不存在；在本轮相关 active repository caller 检查中未发现调用。只保留为历史事件 / P5-F009 legacy candidate 参考，不报告新的 active-runtime finding，不清理其他 legacy asset。新 harness 使用 `ProcessStartInfo.ArgumentList`、显式 prod 参数断言、sanitized process environment、20 秒 timeout 和进程树清理。

本轮另有 bounded read-only 独立静态检查，未报告新 P0/P1；其“新增 regression suite 删除保护”建议已落地且 mutation 通过。这不等于 Formal Review Attempt-02；下一任务仍必须独立正式复审。

## 9. 文件范围与 candidate fingerprint

本轮新建：

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionSecretProfileRegressionTest.java`
- `backend/nq-app/src/test/resources/production-secret-import.properties`
- 本 evidence 文件。

在继承 candidate 上修改：

- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/env/ProductionConfigurationApplicationContextInitializer.java`
- `backend/nq-app/src/main/resources/application-prod.yml`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionConfigurationApplicationContextInitializerTest.java`
- `deploy/canonical/deployment-contract.json`
- `scripts/ci/Test-CanonicalDeliveryWorkflow.ps1`
- `scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1`
- `docs/current/ROADMAP.md`、`RUNBOOK.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。

继承但本轮未再次改写：`deploy/canonical/nq-canonical.service`、`backend/nq-app/src/main/resources/META-INF/spring.factories`、原 Implementation/Review evidence。ignored `artifacts/f008-remediation-*` 仅供验证；范围外正式文件=0。

Fingerprint 口径：`git status --short --untracked-files=all` 的每条按 path 排序，取 `status|path|length|sha256`，以 LF 连接、UTF-8 编码、无尾随 LF 后计算 SHA256。排除本 evidence 自身，避免自引用 hash；ignored build/log/helper 不纳入。

- before：15 files，`9c15450190b1ffb24f8d7d85ec143816de48ad6fcbe1ae9195ae328e06a67e88`。
- after：17 files，`82b8a8be21ba3a7afbc0b86f5da89277a02b27fa85f0eb909aea2b690b3019af`。
- 15→17 来自上述两个新增永久测试文件；加本 evidence 后最终 candidate 为 18 files。
- 原 Implementation SHA256：`2229a3fe6f40f4e89364577f8d32ddf58903e4033b41819f33aede004ad740f3`。
- 原 Formal Review SHA256：`1c41c030c0722e2985a2c9a318479a00cc948e7584cb7a61f04fdca78b7e534a`。

## 10. Authority、状态与回滚

本 Attempt 不修改继承 candidate 的 machine authority block；相对 HEAD 的 IMPLEMENTATION→REVIEW diff 来自前序 implementation，并非本次 remediation 新增 authority exception。

```text
accepted_batch=GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW
```

P1-01/P1-02/P1-03/P2-01/P5-F008=`REMEDIATED_PENDING_INDEPENDENT_REVIEW`；本次范围内未发现残余 P0/P1，不替代正式复审 severity 结论。P5-F007/P5-F009=`OPEN / NOT_IMPLEMENTED`。LIVE=DISABLED，kill switch=ENGAGED；无真实生产操作或历史密文更改。

回滚只允许针对本 Attempt 的逐文件反向补丁，保留 inherited candidate 和两份原证据；不得使用 `git reset/checkout` 将全部未提交实现抹掉。若撤销安全整改，应恢复 finding 的待整改状态并保持禁止生产发布；本任务未实际回滚。

建议 commit message（仅建议，未提交）：`fix(config): 收紧生产 profile 与密钥并阻断配置回退`。

工具声明：使用本地 PowerShell、Git、Maven/Java、Docker 和 apply_patch；使用 bounded read-only subagent 静态检查。MCP/外部服务扫描插件未使用；网络访问仅授权的 Git origin fetch 与 disposable loopback PG/TCP 验证，无真实交易/部署/凭证服务调用。正式写操作仅限第 9 节文件，另有 ignored 验证脚本与产物；无 add/commit/push/reset/rebase。

## 11. 收尾核验

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-doc-links.ps1`：exit=0，checked=221，warnings=123（既有 historical ledger 断链），errors=0。
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1`：exit=0，errors=0，`CURRENT_AUTHORITY_VALID`。
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-current-authority-next-action.ps1`：exit=0，positive-actions=7，ambiguous-actions=4，safety-negative=9，schema-negative=4，whitespace-negative=5，failed=0。
- canonical validator 再验：exit=0，required jobs=9，capabilities=25，missing=0，unknown=0；remote enforcement 未修改。
- `git diff --check`：exit=0；已核对范围 diff、untracked Java/test/resource 内容与 staged 列表。原 evidence hashes 和 JAR hash 与本文记录一致；17-file after fingerprint 复算一致。
- artifacts 日志、nq-app Surefire 与本文完整 synthetic key 格式扫描：匹配文件数=0；每个搜索检查错误退出码，未以搜索失败冒充零匹配。
- 本任务 disposable PG container 查询无残留；未清理或覆盖用户其他容器/数据。
