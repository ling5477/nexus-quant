# F008 Production Configuration Fail-Closed — Independent Review Attempt-02

日期：2026-09-05。任务：`NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW`，Attempt=02，NQ-only。

结论：`FAIL / PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW_REJECTED / P0_0 / P1_2 / P2_0 / P3_0 / NOT_READY_TO_COMMIT`。

显式 active prod 路径的主要整改有效，但 default-profile prod 仍可绕过整段生产校验。另有三项行为 mutation 被 canonical validator 接受。全部必要本地回归通过，不能覆盖这些独立反例。

## 1. 分类、独立性和基线

- Task classification：`AUDIT / INDEPENDENT_SECURITY_REVIEW / NO_IMPLEMENTATION / NO_COMMIT`。
- implementation participant：NO；remediation participant：NO。本 reviewer 当前会话未参与前序实施或整改。
- candidate authority inherited：NO。用户本轮任务为审查授权；从 machine policy 定位 [Audit Bootstrap Charter](../AUDIT_BOOTSTRAP_CHARTER.md)，读取 current authority 以约束状态操作。未使用 candidate AGENTS/CLAUDE/Skills 或 checker 自我声明作为正确性 authority。
- implementation evidence trusted：NO；[Implementation](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_IMPLEMENTATION.md)、[Attempt-01 FAIL Review](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW.md)、[Remediation Attempt-01](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REMEDIATION_ATTEMPT_01.md) 均仅为 `CLAIMED_RESULT / REVIEW_SUBJECT`。violations=0。
- Repository：`E:\Project\nexus-quant-gateaudit`；branch=`audit/post-gatey-agent-baseline`。
- HEAD=origin/audit=`aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc`；origin/dev=`4c19cb775ebb18b4288400a5a1a402145c2fe30a`。
- 已执行 `git fetch origin --prune`，exit=0；dev ancestor 检查 exit=0；staged=0。HEAD/origin audit 未变化。
- fingerprint before=after=`82b8a8be21ba3a7afbc0b86f5da89277a02b27fa85f0eb909aea2b690b3019af`，17 files，匹配 remediation 期望。
- 口径与 remediation 相同：按路径排序的 `status|path|length|lowercase-sha256`，LF 连接、UTF-8、无尾 LF；排除 remediation evidence 自身以兼容其原始口径，另排除本次新增 review evidence。ignored 测试产物不纳入。没有借排除项修改 candidate。
- 原 Implementation hash=`2229a3fe6f40f4e89364577f8d32ddf58903e4033b41819f33aede004ad740f3`；原 Review hash=`1c41c030c0722e2985a2c9a318479a00cc948e7584cb7a61f04fdca78b7e534a`，保持一致。

## 2. Findings

### P0

无。

### P1-01 / PRODUCTION_DEFAULT_PROFILE_VALIDATION_BYPASS

**证据位置：** [ProductionConfigurationApplicationContextInitializer.java:124](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/env/ProductionConfigurationApplicationContextInitializer.java#L124)，尤其 124–130 行生产识别条件与 131–148 行受该条件控制的所有校验。

触发输入：

```text
--spring.config.location=classpath:/application.yml
--spring.profiles.active=
--spring.profiles.default=prod
marker absent（marker=false 亦可）
JWT/master property 使用仓库已公开的历史默认值
datasource 指向 reviewer 独占的 loopback 测试目标
```

`getActiveProfiles()` 为空，但 Spring ConfigData 接受 default prod 并加载 `application-prod.yml`。guard 只计算 `productionMarker || activeProfiles.contains("prod")`，因此跳过 profile、datasource、secret、alternate DataSource 和 Flyway identity 校验。

独立 SpringApplication 探针使用自动 initializer discovery、真实 DataSourceAutoConfiguration、真实 SecurityRuntimeProperties/AccountCredentialRuntimeProperties 和 `SecurityConfiguration.tokenService` 消费链，实际输出：

```text
REVIEW_CONTEXT=ACCEPTED ACTIVE= DEFAULT=prod
REVIEW_OLD_ADMIN=ACCEPTED
REVIEW_CONSUMER_IDENTITY=PASS POOL_STARTED=false
```

此探针成功创建 Hikari DataSource，但正向配置消费检查不启动连接池；公开 master key 与最终消费值一致。旧 key 签发的 synthetic ADMIN token 被 runtime 接受。marker=false 补充用例得到相同结果。marker=true 正向拒绝控制在 refresh 前失败。

真实 packaged JAR 使用同样 default-prod 输入：退出码=1，但**不是 guard 拒绝**；`PROD_CONFIGURATION_INVALID` 未出现，Hikari 已启动，loopback TCP 连接数=1，随后因该监听器不提供 PostgreSQL 协议而启动失败。没有连接共享 5432 或生产 DB。

追加 ConfigData TRACE 独立重跑确认：`active=[] / default=[prod] / accepted=[prod]`，加载并添加 `application-prod.yml` property source，`Setting default profiles: [prod]`、`Setting active profiles: []`；随后 Hikari 启动、TCP=1。Spring 较早的 startup INFO 曾打印 default="default"，不把该早期日志当最终 ConfigData 状态。

运行记录：`artifacts/f008-review02/runtime/default-prod-known.log`、`runtime/default-prod-marker-false-known.log`、`jar-output/default-prod-known.log`（ConfigData 证据约 28、38、40、41 行）、`jar-baseline-results.json`、`jar-trace.log`。运行脚本：`runtime.py`、`additional.py`、`jar.py`。

**影响面与限度：** 非 canonical launcher 以 default prod 激活生产配置时，可使用公开 JWT/master key，并在校验前触发 DB outbound。当前 canonical systemd 固定显式 prod 与 marker=true，未被该反例穿透；不能据此声称线上已受攻击。公开 master key 的 production bypass 按用户规则为 P1，与 profile/JWT 共享同一根因，未重复计数。

**最小 remediation：** 将 default-profile 的 production 激活纳入生产识别；按照本任务 strict active-set contract，对 default-only prod 明确拒绝。保持 ordinary local/test/CI 行为。补 marker absent/false/true、default prod、default/group 展开、公开双 key、DataSource/Flyway construction/connection=0 及真实 packaged JAR 反例的永久回归。无须修改 JWT role architecture、轮换历史密文或清理 legacy profiles。

**验证与回滚：** 上述输入应在 refresh 前报固定 guard 错误，旧 key 不得获得生产 runtime；复验 explicit prod 正向控制和 PG16。后续实施任务只用文件级反向补丁回滚其自身变更，禁止发布已知绕过状态。本 review 未实施修复或回滚。

### P1-02 / CI_PRODUCTION_CONFIG_ENFORCEMENT_GAP

**证据位置：** [Test-CanonicalDeliveryWorkflow.ps1:399](../../../scripts/ci/Test-CanonicalDeliveryWorkflow.ps1#L399)，399–414 行对 initializer/测试方法采用 `.Contains($marker)`；这些检查不验证 profile 与 marker 分支行为。

独立 mutation 使用 validator 的路径参数读取 reviewer 隔离文件，未替换 candidate 或其测试：

| ID | 实际变异 | Validator |
| --- | --- | --- |
| R01 | 恢复 datasource URL fallback | REJECTED |
| R02 | 恢复 username fallback | REJECTED |
| R03 | 恢复 password fallback | REJECTED |
| R04 | 恢复 public JWT fallback | REJECTED |
| R05 | 恢复 public master-key fallback | REJECTED |
| R06 | exact effective set 比较改为仅包含 prod | **ACCEPTED** |
| R07 | 清空 initializer registration | REJECTED（null-valued expression；不是友好的专用诊断） |
| R08 | registration 改为不存在的 initializer class | REJECTED |
| R09 | `productionMarker || prodProfile` 改为 `productionMarker` | **ACCEPTED** |
| R10 | 改为检查原始 `spring.profiles.active`，忽略 include/group expansion | **ACCEPTED** |

对 R06/R09/R10 的 Java 变体另行 `javac` 编译，并以独立进程将该 class 置于测试 classpath 首位：R06 接受 `prod,local`；R09 接受 marker=false + public JWT 并接受旧 key ADMIN token；R10 接受 raw prod + include local，最终 active=`local,prod`。不是 mutation 名称判断。

**准确限定：** 直接调用 candidate 现有 JUnit 测试方法中的断言时，三个变体均报 `AssertionFailedError`（探针 exit=42）；未变异的对照断言通过。因此本项是用户明确要求的 **canonical validator mutation closure gap**，不声称这三个变体能通过当前完整 Maven/远端 CI。Remote exact-head CI 未运行。

复现入口：`pwsh -NoProfile -File artifacts/f008-review02/mutations.ps1`；`runtime.py` 中的编译/执行；`AssertionProbe.java`。证据：`mutations.log`、`runtime-results.json`、`assertion-R06.log`、`assertion-R09.log`、`assertion-R10.log`。

**最小 remediation：** 将 canonical validation 入口与实际 production invariant 的行为验证绑定，确保指定 R06/R09/R10 从同一入口返回非零；保留现有 Maven 回归兜底。加入 default-profile bypass 的永久 mutation/运行证明；不要增加 Attempt/Finding 名称特判或更多字符串存在性检查。

**验证与回滚：** 正常 candidate validator PASS，R01–R10 全部 REJECTED，同时 capability set 与 9 required jobs 不变。后续实施用自身反向补丁回滚，本 review 不改 validator。

### P2 / P3

无独立新增 finding。前述同一根因的公开 master key impact 已计为 P1。

## 3. Profile、JWT 与 master-key 复核

| 范围 | 本轮实际结果 |
| --- | --- |
| effective policy | 应严格为 active `{prod}`；显式 prod 路径成立，default-only prod 反例不成立 |
| prod only + 新 synthetic keys | PASS；真实配置绑定和 DataSource consumer identity 一致 |
| prod + local/test/ci/paper/public-marketdata-manual/freeze/gated-verify/GateW legacy/GateY legacy/unknown | 10/10 REJECTED |
| env/CLI/JVM/JSON include、group、alias expansion | 全部 REJECTED |
| marker=true + prod absent / prod+local | REJECTED；default-only prod + marker=true 亦拒绝 |
| explicit prod + marker absent/false，缺 JWT | REJECTED；marker 不关闭显式 prod 的校验 |
| ordinary local/test/CI | PASS；没有被升级为 production validation |
| JWT effective property | `nq.security.secret` → SecurityRuntimeProperties → SecurityConfiguration → JwtTokenService |
| master effective property | `nq.account.credentials.master-key` → AccountCredentialRuntimeProperties → AccountModuleConfiguration → JdbcExchangeAccountCredentialRepository |
| explicit prod 下两项 secret missing/blank/whitespace/known default | 全部 REJECTED |
| higher-priority CLI/JVM/JSON known-default override | 两项均 REJECTED |
| config import known default | 去除高优先级 env 后，两项均 REJECTED；有效 import PASS |
| base application.yml inherited public defaults | 显式 prod + 隔离 base-only config fixture：REJECTED |
| synthetic external/env/CLI/JVM/JSON/import | PASS；guard Binder 与真实 properties bean/consumer 相同 |
| forged old-key ADMIN，显式 prod + 新 synthetic signing key | REJECTED；当前 key 签发 token 的正向控制 PASS |
| forged old-key ADMIN，default-only prod + public key | **ACCEPTED**；P1-01 |
| ciphertext rotation/key versioning/online re-encryption | NOT_REQUIRED / 未执行 |

Independent runtime 主矩阵=68 cases，修正后 67 个符合预期，1 个真实 default-prod bypass；含 3 个预期会绕过的隔离 mutant 正向控制和 1 个故意测试失败的脱敏用例。另有 3 个补充用例，marker=true/default-only 与 base-default 拒绝通过，marker=false/default-only 复现同一 P1。不要将这些数字写成全部安全用例通过。

## 4. Accepted F008 areas targeted regression

- Datasource URL/username/password：prod YAML 均无 fallback，R01–R03 拒绝。env/CLI/JVM system property/SPRING_APPLICATION_JSON/approved test import 五种来源，guard Binder 值与真实 Hikari DataSource 的 URL/user/password 相等；未出现 validator safe / consumer malicious 的显式 prod 反例。
- Alternate DataSource：JNDI、custom class、XA、alternate type、Hikari JDBC URL/user/password/data-source-properties 路径均拒绝；候选原测试还覆盖 camelCase 与 map alias。没有添加实际 custom DataSource bean 或真实 JNDI 服务。
- JDBC URL：URL credentials、host/port/dbname/service overrides、malformed/non-PG 均拒绝；原 targeted suite 的 user-info/query 参数测试也真实执行。
- Flyway：prod YAML `enabled=false`；独立 URL/user/password 均拒绝。未启用 production Flyway 或修改 migration ownership。
- Startup ordering：被 guard 识别的非法配置在 refresh 前失败；原测试 construction/connection counters=0，独立探针 bean=0，真实 JAR 16 个 negative cases TCP=0。**全称条件不通过**：default-prod bypass JAR Hikari construction>0、DB TCP=1；该反例没有独立启用 Flyway，不能宣称已观察到 Flyway outbound。
- Initializer：源码 registration 正确。新构建 executable JAR 中 initializer class、prod YAML、spring.factories 与 target/classes 字节相同；spring.factories 实际位于 JAR 根 `META-INF/spring.factories`，class/prod YAML 位于 `BOOT-INF/classes`。16 个负例由真实 `java -jar` 自动发现 guard；不是手动 new initializer。
- JAR SHA256=`082d80a99f2e7150dd61ea549097ffd07518d393855fd3f4634260e4848d9801`。此构建仅供本地 proof，不是 release admission 或生产发布许可。

## 5. Secret redaction

本 reviewer 独立生成新随机值，格式为 `F008_REVIEW02_JWT_<random>`、`F008_REVIEW02_MASTER_<random>`、`F008_REVIEW02_DB_<random>`，未复用实施方 synthetic secret。原值仅在测试进程内存、受控 subprocess environment/arguments 中使用，未写入代码或 evidence。

- 触发 profile conflict、missing secret、known-default failure、bad datasource、startup failure、故意 `AssertionError` 测试失败。
- 每个 subprocess 分别捕获 stdout/stderr，合并后先按完整随机值检查，再写日志；全部 raw occurrences=0。异常及 Spring failure output 包含在捕获范围内。
- 额外扫描本次日志、临时输出、backend Surefire XML/TXT 和本 evidence 的完整 reviewer synthetic 格式：首次最终核验 817 files，raw occurrences=0。搜索错误不算零匹配；UTF-8/UTF-16 BOM 显式处理。
- 不打印 JWT token、输入 secret、失败断言的 expected/actual secret。测试失败用固定分类；probe 不传播原始异常值。
- 证明范围为本轮实际生成值和本地输出；不声称覆盖未执行的线上日志、任意未来异常或真实 credential。

## 6. Required jobs 与 capabilities

不是只引用 validator 打印的 missing/unknown=0。reviewer 从实际 workflow 的 job/command/producer/consumer 独立形成 25 项 inventory，再与 validator `$criticalCapabilities` 比较；`capabilities.py`、`capabilities.json` 保存映射与结果：actual=25、registered=25、missing=0、unknown=0、unobserved=0。

25 项为：supply-chain-lock-validator、gitleaks-canonical-scan、backend-artifact-build、frontend-production-build、npm-lock-enforced-install、playwright-locked-consumer、frontend-sbom-producer、frontend-manifest-producer、cyclonedx-backend-sbom-producer、backend-manifest-producer、provenance-producer、pre-upload-provenance-admission、backend-delivery-upload、frontend-delivery-upload、provenance-delivery-upload、post-upload-provenance-readback、critical-e2e-loopback-execution、critical-e2e-real-backend-execution、canonical-release-build、canonical-release-verifier、canonical-release-install-activation、production-configuration-fail-closed、current-schema-restore-drill、backup-integrity-check、post-restore-validation。

9 个 job ID/name 未变化：

| Job | Check name |
| --- | --- |
| diff-check | Repository hygiene and governance |
| no-outbound-guard | Runtime safety and no-outbound |
| backend | Backend regression |
| postgres-flyway | PostgreSQL and Flyway |
| frontend-critical | Frontend build and critical E2E |
| research | Research quality |
| secret-scan | Secret scanning |
| java-engineering-shadow | Java architecture guard |
| delivery-provenance | Delivery SBOM and provenance |

本段证明 inventory/ownership 一致，不以 capability 名称存在代替 F008 enforcement；其行为缺口另列 P1-02。

## 7. Reviewer 实际验证记录

所有命令在本地执行；Maven `-o`，Docker `--pull never`。完整输出保存在 ignored `artifacts/f008-review02/`，没有借用旧 log 作本轮 PASS。

| 验证 | 命令/输出 | 本轮结果 |
| --- | --- | --- |
| targeted F008 | `mvn -o -f backend/pom.xml -pl nq-app -am test -Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest -Dsurefire.failIfNoSpecifiedTests=false` / targeted.log | exit=0；111 tests，0 failure/error/skip |
| Full Maven | `mvn -o -f backend/pom.xml test` / full.log | exit=0；23/23 modules BUILD SUCCESS；各模块汇总共 1766 tests，0 failure/error，53 skips |
| nq-app | 同 Full Maven | 437 tests，0 failure/error，35 skips；独立实际结果 |
| PG16 fixture | reviewed `verify.ps1` + BackendCiLegacyAccountFixture.java / fixture.log | PostgreSQL 16.15，server_version_num=160015，V46，pending=0 |
| PG16 app-context | NqAppContextPostgresSmokeTest，required=true / pg-smoke.log | 1/1 PASS，skip=0 |
| PG16 repository | JdbcRepositoryPostgresSmokeTest，required=true / pg-smoke.log | 1/1 PASS，skip=0 |
| canonical release | `pwsh -NoProfile -File scripts/deployment/tests/Test-NqCanonicalRelease.Tests.ps1` / release.log | exit=0；66/66 PASS |
| canonical validator | `pwsh -NoProfile -File scripts/ci/Test-CanonicalDeliveryWorkflow.ps1` / validator.log | exit=0；9 jobs，25 capabilities |
| existing mutations | `pwsh -NoProfile -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1` / existing-mutations.log | exit=0；84/84 REJECTED |
| reviewer mutations | mutations.ps1 / mutations.log | R01–R10：7 REJECTED、3 ACCEPTED；FAIL |
| package | reactor `-DskipTests install`，随后 nq-app `package spring-boot:repackage` | 两步 exit=0；只用于打包，未冒充测试 |
| packaged JAR | `python artifacts/f008-review02/jar.py` | 17 cases：16 guard-rejected；1 default-prod bypass；追加 TRACE 同样复现 |
| frontend | 未执行 | NOT_REQUIRED；diff 未触及 frontend |
| remote exact-head CI | 未执行 | NOT_RUN；未提交 candidate 不要求远端 CI |

PG16 使用缓存 digest `postgres@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`，新建独占容器 `nq-f008-review02-*`，仅 `127.0.0.1:43205`；finally 清理后 task container residue=0。sanitized 子进程清除继承 NQ/SPRING/PG/JVM 配置值，NQ_NO_OUTBOUND=true、真实 exchange/AI/DH 关闭、venue endpoint placeholder。未访问本机共享 DB、真实账户、生产服务或私有交易 API。

Reviewer harness 失败与处理（不隐瞒，均不计产品 finding）：初轮两个 import negative 被高优先级有效 environment 覆盖，随后移除该 env 并验证最终值；一次 consumer-import 与打包编译并行导致 ClassNotFoundException，在构建完成后重跑主矩阵通过该项；JAR 首检误假设 spring.factories 在 BOOT-INF/classes，检查真实 ZIP 后更正为根 META-INF；断言探针首轮 PowerShell 路径串含字面 `+`，实际跑了 baseline（断言 PASS），修正路径后三个 mutant 全部 AssertionFailedError；capability helper 初轮 Windows GBK 解码失败，显式 UTF-8 后通过。最终结果仅使用纠正后的有效运行。

## 8. Scope、closure 与 authority

未实施 Spring profile consolidation、legacy Gate profile deletion、freeze/gated-verify cleanup、GateW/GateY removal、credential ciphertext migration、JWT role redesign、observability、i18n 或 Phase6。未发现需要扩大审计的其他新问题。P5-F007/P5-F009 保持 `OPEN / NOT_IMPLEMENTED`；本轮只把既有 legacy profiles 作为 F008 混合输入测试。

| 原 finding/workstream | Attempt-02 closure |
| --- | --- |
| P1-01 CANONICAL_PRODUCTION_PROFILE_MIXING | NOT_CLOSED；显式 active 组合已拒绝，但 production default-profile detection 留有 P1-01 反例 |
| P1-02 PRODUCTION_JWT_PUBLIC_DEFAULT_KEY | NOT_CLOSED；explicit prod 修复有效，default-prod 可接受公开 key/伪造 ADMIN token |
| P1-03 CI_PRODUCTION_FALLBACK_RESTORATION_NOT_BLOCKED | NOT_CLOSED；五项 fallback restoration 拒绝，但 R06/R09/R10 validator closure 不满足 |
| P2-01 PRODUCTION_CREDENTIAL_ENCRYPTION_PUBLIC_DEFAULT_KEY | NOT_CLOSED；default-prod public master key impact 为 P1，同根因合并计数 |
| P5-F008 | REVIEW_REJECTED / NOT_READY_TO_COMMIT |
| P5-F007 / P5-F009 | OPEN / NOT_IMPLEMENTED；不改动 |

FAIL 保持 machine authority 不变，无 task-specific governance matcher：

```text
accepted_batch=GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW
```

真实 review decision 保存在本文件；不将未完成的审查写为 acceptance。LIVE=DISABLED，kill switch=ENGAGED。

## 9. 收尾与交付

- 唯一正式新增文件：本 Attempt-02 evidence；Implementation / Review Attempt-01 FAIL / Remediation Attempt-01 全部保留。reviewer candidate modifications=0，candidate changed=NO。
- `git diff --check` exit=0；staged=0；commit=NONE；push=NONE；未 add/reset/rebase/amend。
- `pwsh -NoProfile -File scripts/docs/check-doc-links.ps1`：exit=0，checked=227、warnings=123（既有 historical ledger 链接警告）、errors=0；`pwsh -NoProfile -File scripts/docs/check-current-authority.ps1`：exit=0、errors=0。current authority 保持原值。
- 任务创建的容器、TCP listeners、JVM 进程和编译/变异临时目录已清理；temporary disposable residue=0。ignored `artifacts/f008-review02/` 的 reviewer 源码、日志和结果 JSON 明确保留为本地复验材料，不纳入 candidate，也不作为远端可得的 evidence 承诺。
- 未验证：线上部署、真实 provider、真实密钥 provisioning/rotation、remote exact-head CI、OS 全网络抓包；均未被报告为 PASS。
- 风险：两个 P1 未关闭，不具备 READY_TO_COMMIT 条件。最小整改要求见 Findings；本 review 不修代码。
- 回滚：如需撤销此次审查交付，只撤销本新增 evidence，并按需要清理本次明确列出的 ignored review artifacts；不得回滚或覆盖 candidate。未实际回滚。
- 后续：独立 implementation/remediation 修复两个根因后重新 Review；本轮不进入 commit/push/exact-head CI。
- 建议 commit message（仅 evidence 建议，未提交）：`docs(audit): 记录 F008 第二次独立审查阻断项`。

Fingerprint 复算曾因 Python ordinal 排序与原 PowerShell `Sort-Object Path` 口径不同而报不一致；按原 PowerShell 算法复算即与 before 相同。最终 `finalize.py` 校验原排序 manifest 中每个当前文件的 status/length/hash，再计算 SHA256；不是修改 candidate 或更换期望值来消除差异。结果见 `artifacts/f008-review02/final-check.json`。

Final decision：`FAIL / PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW_REJECTED / P0_0 / P1_2 / NOT_READY_TO_COMMIT`。

工具声明：外部工具使用本地 Git、PowerShell 7、Python、Java/javac、Maven、Docker；MCP 未使用；Skills 未使用（未将 candidate Skills 用作审查 authority）；网络仅用户指定 Git origin fetch，以及 disposable PG16/loopback TCP 证明，依赖与镜像均使用缓存。正式写操作仅新增本 review evidence；另创建 ignored reviewer harness、日志、测试 fixture/编译输出并清理 disposable 产物。未修改 candidate、系统配置或远端仓库。
