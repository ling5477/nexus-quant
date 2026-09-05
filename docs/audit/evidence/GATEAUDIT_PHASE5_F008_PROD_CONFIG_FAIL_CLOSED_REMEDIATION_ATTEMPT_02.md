# F008 Production Configuration Fail-Closed — Remediation Attempt-02

日期：2026-09-05。任务：`NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REMEDIATION-ATTEMPT-02`。

结果：`IMPLEMENTED / PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REMEDIATION_ATTEMPT_02_COMPLETE / P0_0 / P1_2_REMEDIATED / PENDING_INDEPENDENT_REVIEW`。

本次仅处理 [Review Attempt-02](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW_ATTEMPT_02.md) 的两个 P1：

| Finding | 状态 |
| --- | --- |
| `DEFAULT_PROD_PROFILE_VALIDATION_BYPASS` | `REMEDIATED_PENDING_INDEPENDENT_REVIEW` |
| `CI_PROD_CONFIG_BYPASS_VARIANTS_NOT_ENFORCED` | `REMEDIATED_PENDING_INDEPENDENT_REVIEW` |
| P5-F008 | `REMEDIATED_PENDING_INDEPENDENT_REVIEW` |

P5-F007/P5-F009 保持 `OPEN / NOT_IMPLEMENTED`。本证据不是 formal review acceptance；未 commit、push、stage 或运行 remote exact-head CI。

## 1. 范围与边界

- 修改生产 profile fail-closed 判定、对应 Java 回归、mandatory CI capability 与其 canonical validator/mutation proof。
- 不修改 datasource fallback、JWT/master-key known-default、alternate DataSource、JDBC URL identity、Flyway split-brain、initializer registration、secret-redaction 既有整改逻辑。
- 不涉及 JWT 角色架构、历史密文轮换、legacy profile 清理、frontend、migration、Phase6、LIVE、真实交易、真实 provider 或生产部署。
- `STATUS.md` 的 authority block 保持：`IMPLEMENTED|PENDING_REVIEW / NONE / NOT_RUN`，next action 仍为 `NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW`。

## 2. P1-01 — default-prod validation bypass

根因是 guard 只从 `Environment.getActiveProfiles()` 识别 prod。Spring 环境的 active set 为空而 default set 为 `{prod}` 时，生产配置可能已生效，原条件却跳过 profile、datasource、secret、alternate DataSource 与 Flyway validation。

[ProductionConfigurationApplicationContextInitializer.java](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/env/ProductionConfigurationApplicationContextInitializer.java) 现在只在一个 `effectiveProfiles` 分支中选择 profile：active 非空时取 active，否则取 default。production mode 为 `effectiveProfiles.contains("prod") || productionMarker`；只要包含 prod，就强制完整 production validation，并要求最终 set 精确为 `{prod}`。marker 为缺失、`false` 或 `true` 都不会关闭 prod validation；marker 为 `true` 且 effective set 不是 `{prod}` 仍立即 fail-closed。

永久 Java regression 覆盖：

- `active=[] / default=[prod]` 与 marker=`absent|false|true` 的 repository-known JWT rejection；
- `active=[] / default=[prod,local]` 的 exact-set rejection；
- `active=[] / default=[prod]`、无 marker、全 synthetic valid 配置的正向控制；
- default-prod known secret 在真实 DataSource/Flyway auto-configuration 前失败，Hikari/Flyway output 均不存在。

## 3. Packaged artifact proof

新建 executable JAR 后，验证从该 JAR 解出的 `BOOT-INF/classes`、`BOOT-INF/lib` 与根 `META-INF/spring.factories` 运行 `SpringApplication`。probe 不手动注册 production initializer；由 packaged `spring.factories` 自动发现。probe 在 ConfigData 前设置 `active=[]`、`default=[prod]`，并以新生成的 process-only synthetic values运行。

| Case | marker | secret | final environment | result |
| --- | --- | --- | --- | --- |
| `marker-missing-known-jwt` | absent | repository-known | active empty, default prod | `PROD_CONFIGURATION_INVALID` / TCP=0 |
| `marker-false-known-jwt` | false | repository-known | active empty, default prod | `PROD_CONFIGURATION_INVALID` / TCP=0 |
| `marker-missing-blank-jwt` | absent | blank | active empty, default prod | `PROD_CONFIGURATION_INVALID` / TCP=0 |
| `marker-false-blank-jwt` | false | blank | active empty, default prod | `PROD_CONFIGURATION_INVALID` / TCP=0 |

每项均先对 loopback listener 进行正向连接控制，再将 datasource 指向该 listener。所有 4 项均在 Hikari/Flyway/application-started 前失败，listener pending connection=0，synthetic JWT/master value output occurrences=0。记录：`artifacts/f008-remediation-attempt02/packaged-default-prod-probe.log`。

初次直接 `java -jar` 参数构造以空 active profile 配合 default-prod 时，当前 `application.yml` 的 local activation 默认值使其没有形成目标 default-prod 环境，且触发了本机共享数据库的启动路径。Flyway V45 因既有外键数据不完整失败并回滚；该无效 harness 未计入通过结果，已停止使用。此后只采用上述从 freshly packaged artifact 加载、明确记录 final environment、仅指向 loopback listener 的 probe。没有修改 application profile policy。

## 4. P1-02 — mandatory CI capability

[ci.yml](../../../.github/workflows/ci.yml) 的既有 `backend` required job 新增唯一、无条件、fail-closed step：

```text
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest -Dsurefire.failIfNoSpecifiedTests=false
```

[Test-CanonicalDeliveryWorkflow.ps1](../../../scripts/ci/Test-CanonicalDeliveryWorkflow.ps1) 将该 step 作为 `production-configuration-fail-closed-regression` capability 的唯一 owner，要求 exact invocation、required job membership、无 conditional、无 soft-fail、无 failure-ignore。`diff-check` 使用锁中 pin 的 Java setup action，lock 的 setup-java expected occurrences 随真实 workflow inventory 从 5 更新为 6；required jobs 仍为 9，capabilities 仍是实际 inventory 的 25。

[Test-CanonicalDeliveryWorkflow.Tests.ps1](../../../scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1) 在隔离 backend source copy 中修改 Java initializer，并运行与 CI 完全相同的 Maven capability command。以下行为变异均使该 command non-zero：

| Mutation | bypass target | result |
| --- | --- | --- |
| R06 | `{prod,local}` 被放宽为 only-contains-prod | `REJECTED` |
| R09 | 仅 marker 识别 production mode | `REJECTED` |
| R10 | 回退到 raw `spring.profiles.active`、忽略 effective set | `REJECTED` |

同一 mutation suite 还拒绝该 CI step 的删除、conditional、`continue-on-error`、`|| true` 与替换为无关测试。没有在 PowerShell 中复制 Java production-profile 业务逻辑，也没有按 finding 名称特判。

## 5. 实际验证

| Check | Result |
| --- | --- |
| targeted F008 regression | 118 tests，failures=0，errors=0；`BUILD SUCCESS` |
| local mandatory CI command | 118 tests，failures=0，errors=0；`BUILD SUCCESS` |
| canonical workflow validator | required jobs=9；capabilities actual/registered/missing/unknown=`25/25/0/0` |
| canonical mutation suite | R06/R09/R10 capability command 均 non-zero；总计 `MUTATIONS_REJECTED=92` |
| package + repackage | 23-module reactor `BUILD SUCCESS`；executable JAR repackage `BUILD SUCCESS` |
| packaged default-prod proof | 4/4 `PROD_CONFIGURATION_INVALID`；Hikari/Flyway=0；loopback DB TCP=0；auto discovery=PASS |
| isolated full Maven | disposable PostgreSQL 16.15，23/23 modules `BUILD SUCCESS` |
| PG16 fixture | V1→V46，pending=0 |
| PG16 app-context smoke | 1/1 PASS |
| PG16 repository smoke | 1/1 PASS |
| canonical release regression | 66/66 PASS |
| frontend | `NOT_REQUIRED` |
| remote exact-head CI | `NOT_RUN`；candidate 未提交 |

首次无隔离的 `mvn -o -f backend/pom.xml test` 连接到已有本机数据库，在 V45 既有外键数据不一致处失败；22 个模块已成功，`nq-app` 因该外部数据库状态失败。该结果没有被写成通过。随后在 digest-pinned、随机 loopback port 的 disposable PG16 中完整重跑，通过并在 finally 清理 container（residue=0）。

验证日志位于 `artifacts/f008-remediation-attempt02/`，仅为 ignored local artifact；它们不是 release admission 或 production startup authorization。

## 6. Git、回滚与残余风险

- baseline：`audit/post-gatey-agent-baseline`，HEAD/remote baseline=`aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc`；staged=0，commit/push=`NONE/NONE`。
- Review Attempt-02 remediation 前的 inherited candidate fingerprint=`82b8a8be21ba3a7afbc0b86f5da89277a02b27fa85f0eb909aea2b690b3019af`。
- 本次代码与 CI docs 写入前、排除本 evidence 的 pre-doc fingerprint=`99303dcad941a1f4729c829bea598883bc069cc0f6901fae5bb148cc05a097e7`；收尾复算、同样排除本 evidence 的 final fingerprint=`f7acf53ed9495322c5df740e16b0eb807ced3676ee2b73a9b9dafb3636ed9c3e`（21 files）。
- 如需回滚，仅对本 Attempt 修改的 initializer、两项 Java test、workflow/lock/validator/mutation suite 与本 evidence/current-doc append 做逐文件反向补丁；不得 reset、checkout 或删除 inherited F008 candidate。
- 未验证生产 credential provisioning/rotation、生产部署、真实 provider/exchange、remote exact-head CI 或 OS 全网络抓包。LIVE 保持 disabled，kill switch 保持 engaged。

建议 commit message（未执行）：`fix(config): 修复默认 prod 配置校验旁路`。

> **工具声明**
>
> - 外部工具：使用了本地 Git、PowerShell、Java/javac、Maven、Docker（用途：代码验证、packaged artifact proof 与 disposable PG16 smoke）。
> - MCP：未使用。
> - Skills：使用 `nq-dh-workflow-router`、`java-backend-maintenance`、`java-backend-regression-tests`、`nq-java-engineering-standard`、`nq-docs-writer`。
> - 网络访问：仅 `git fetch origin --prune` 与 cached disposable PostgreSQL image；未调用生产/交易/部署服务。
> - 写操作：修改 F008 initializer/tests、CI workflow/lock/validator/mutation suite、current docs，并新增本 evidence；未执行 git add/commit/push。
