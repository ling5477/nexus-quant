# F008 Production Configuration Fail-Closed — Independent Review Attempt-03

日期：2026-09-05。任务：`NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW`，Attempt=03，NQ-only。

结论：`FAIL / PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW_REJECTED / P0_0 / P1_1 / NOT_READY_TO_COMMIT`。

Default-prod 目标运行路径的整改已独立验证；P1-B 的 mandatory CI enforcement 仍未满足本轮 closure 条件。现有 92 项 mutation 全部通过不能覆盖新增的真实反例。未给予 P5-F008 acceptance，未修改 current authority。

## 1. Classification、independence 与基线

- Task classification：`AUDIT / INDEPENDENT_TARGETED_SECURITY_REVIEW / DEFAULT_PROFILE_FAIL_CLOSED_REVIEW / CI_MANDATORY_CAPABILITY_REVIEW / NO_IMPLEMENTATION / NO_COMMIT`。
- implementation participant=NO；remediation participant=NO；review independence violations=0。本 reviewer 当前会话未参与此前 implementation 或 remediation。
- 授权来自本轮用户请求。通过 repository machine policy 的 `audit.bootstrapCharter` 定位并读取 [Audit Bootstrap Charter](../AUDIT_BOOTSTRAP_CHARTER.md)。读取 current authority 仅约束本轮状态边界；未将 candidate AGENTS/CLAUDE/Skills、checker 自我声明或 remediation evidence 当审查正确性的 authority。
- repository=`E:\Project\nexus-quant-gateaudit`；branch=`audit/post-gatey-agent-baseline`。
- HEAD=origin/audit/post-gatey-agent-baseline=`aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc`；origin/dev=`4c19cb775ebb18b4288400a5a1a402145c2fe30a`。本轮 `git fetch origin --prune` exit=0，之后核对相同。
- staged=0。继承 12 个 tracked modified 文件和 10 个 untracked candidate/evidence 文件，全部保护。
- candidate fingerprint before=after=`631ae7570b227b8a9102dd17e72f4cc9bbccd466720f5c3991d91a303327dfa1`，22 files。
- 指纹口径：`git status --porcelain=v1 -z --untracked-files=all` 中每个候选文件构造 `status|path|byte-length|lowercase-sha256`，按 path 排序、LF 连接、UTF-8、无尾 LF，再计算 SHA256。包含全部五份已有 F008 历史 evidence；仅排除本次新增 Attempt-03 evidence，ignored 运行产物不纳入。与 remediation 的 21-file/exclude-self 口径不同，不直接比较其 hash。逐文件清单保存在 `artifacts/f008-review03/fingerprint-before.json`。
- reviewer implementation changes=0；candidate changed=NO。唯一新增正式文件是本报告。

## 2. 历史链

以下内容均保留，没有覆盖；实施结论只按 `CLAIMED_RESULT / REVIEW_SUBJECT` 处理：

1. [Implementation](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_IMPLEMENTATION.md)
2. [Review Attempt-01 FAIL](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW.md)
3. [Remediation Attempt-01](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REMEDIATION_ATTEMPT_01.md)
4. [Review Attempt-02 FAIL](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW_ATTEMPT_02.md)
5. [Remediation Attempt-02](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REMEDIATION_ATTEMPT_02.md)
6. 本 Review Attempt-03 FAIL

## 3. Findings

### P0

无。

### P1-01 — CI_PROD_CONFIG_BYPASS_VARIANTS_NOT_ENFORCED（P1-B residual）

**证据位置：** [Test-CanonicalDeliveryWorkflow.ps1](../../../scripts/ci/Test-CanonicalDeliveryWorkflow.ps1) 第 130–151 行、307–322 行；[ci.yml](../../../.github/workflows/ci.yml) 第 274–277 行、297–308 行；[Test-CanonicalDeliveryWorkflow.Tests.ps1](../../../scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1) 第 93–113 行、239–263 行。

`Assert-RequiredStep` 和 workflow 全局检查只拒绝 literal `continue-on-error: true`。`Assert-SinglePurposeStep` 校验 exact Maven invocation，但未补上 step-level expression 的 fail-closed 校验。以下有效 YAML 保持 production-config Maven selector 原样，却被实际 validator 接受：

```yaml
      - name: Run production configuration fail-closed regression
        continue-on-error: ${{ true }}
        shell: bash
        run: mvn -f backend/pom.xml -pl nq-app -am test -Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest -Dsurefire.failIfNoSpecifiedTests=false
```

`${{ 1 == 1 }}` 也被接受。独立 mutation 将 `${{ true }}` 同时放到该 step 与 `Run backend tests` step 后，validator 仍 exit=0。GitHub Actions 的 step `continue-on-error` 为真会允许该 step 的非零结果不使 job 失败，因此本 validator 不能保障两处安全测试失败都必须阻断 backend job。

**准确限度：** 未变异 candidate 中这两个 step 均无 soft-fail，两个实际 Maven command 的正向执行均通过。仅软化 F008 step 时 full Maven 仍是现有兜底。双 step 变异证明该兜底的相同缺口；没有声称变异已提交、已运行 remote CI 或线上已被绕过。本 finding 的范围是 mandatory CI capability 的不可绕过性，未新增其他领域 finding。

R06/R09/R10 还存在用户指定的 rejection-chain 缺口。独立 backend source copy 中执行真实源码替换，Maven 实际编译成功，然后 F008 assertion 失败；不是故意编译错误或依赖缺失：

| Mutation | source replacement | F008 Maven | Tests / failures / errors | standalone canonical validator |
| --- | --- | --- | --- | --- |
| R06 | 两处 `!APPROVED_PRODUCTION_PROFILES.equals(effectiveProfiles)` 改为 `!effectiveProfiles.contains(PROD_PROFILE)` | exit=1 / REJECTED | 118 / 17 / 0 | exit=0 / ACCEPTED |
| R09 | `productionMarker || productionProfile` 改为 `productionMarker` | exit=1 / REJECTED | 118 / 50 / 0 | exit=0 / ACCEPTED |
| R10 | helper return 改为 `Set.of(environment.getProperty("spring.profiles.active", PROD_PROFILE))` | exit=1 / REJECTED | 118 / 12 / 0 | exit=0 / ACCEPTED |

每个变异还通过 validator 的真实 `-ProductionConfigInitializerPath` 参数独立校验。其 stdout 仍打印 9 required jobs、25 capabilities、missing/unknown=0。canonical suite 新增 helper 只判断 mutation 的 Maven exit 非零，并未把 baseline source 的行为结果接入 standalone validator；因此不能把 suite 的 `MANDATORY_PRODUCTION_CONFIG_CAPABILITY_REJECTED` 等同于 validator 已拒绝该 source candidate。这里明确区分“Java regression 检出变异”与“用户要求的完整入口拒绝链”。原始 workflow 在未 soft-fail 时仍会被 Maven regression 阻断，不能据此声称 R06/R09/R10 可通过原始完整远端 CI。

**独立 artifact：** `ci-independent-mutations.json`、`ci-mutation-continue-expression.log`、`ci-mutation-continue-expression-comparison.log`、`both-backend-regressions-soft-fail.log`、`source-mutations.json`、`source-validator-R06/R09/R10.log`、`source-maven-R06/R09/R10.log`，均位于 `artifacts/f008-review03/`。完整可重建变异脚本为 `mutations.py`、`inventory.py`。LF fixture 正向控制沿用 byte-equivalent workflow 文本。

**最小修复建议：** 对 required capability step 的 `continue-on-error` 采用 fail-closed 值合同，例如仅允许缺省或 literal false，拒绝未知/表达式值；保留 exact F008 selector 与 required job ownership。明确 canonical validation 入口如何消费 baseline Java regression 的真实退出码，让 R06/R09/R10 经该入口非零退出；无需在 PowerShell 中重写 Spring profile 逻辑，也不增加 Attempt-specific matcher。补 expression soft-fail 与双 backend step 反例，并验证 baseline 成功、行为 mutation 因 assertion 失败而被入口拒绝。

**风险与回滚：** 触发条件为未来 candidate 软化 required step 或再次引入 profile branch bypass；最坏可使 workflow validator 给出错误可交付信号。后续整改应仅对 CI validator/相应 regression 作最小补丁；回滚只使用该整改自身的反向补丁，不发布已知缺口。本 review 未修复 candidate。

### P2 / P3

无独立新增 finding。

## 4. P1-A default-prod 独立验证

[ProductionConfigurationApplicationContextInitializer.java](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/env/ProductionConfigurationApplicationContextInitializer.java) 第 124–161 行：active array 非空取 active，否则取 default；production mode 为 marker=true 或 effective set 包含 prod；production effective set 必须精确为 `{prod}`。datasource、JWT/master、alternate identity 与 Flyway 检查共处该 production 分支。

| 输入 | 实际结果 |
| --- | --- |
| active={prod} | production validation |
| active={} / default={prod} / marker missing、false、true | production validation；三种 marker 均拒绝 known JWT、known master、blank URL/user/password |
| active={prod,local} | REJECTED |
| active={} / default={prod,local} | REJECTED |
| prod + include(local) / group(local) | REJECTED；agent 观察 expanded effective profile |
| prod + unknown | REJECTED |
| marker=true + effective local | REJECTED，即使 default={prod}；active 非空优先 |
| explicit local / test / ci / default local | PASS，未提升为 production validation |
| synthetic valid active prod / default prod | PASS；真实 Hikari DataSource URL/user/password 与真实 SecurityRuntimeProperties、AccountCredentialRuntimeProperties 消费值一致，pool 未启动 |

独立 `ReviewContext.java`/`context_probe.py` 使用新构建 JAR 解出的实际 classes、libraries 和 `spring.factories`，运行 SpringApplication、真实 DataSourceAutoConfiguration 与生产配置属性 binding；不手动 new/register production initializer。43/43 符合预期：30 个 active/default × marker × invalid-property 用例，6 个正向控制，3 个既有 identity guard spot-check，4 个 include/group 用例。所有负例 refresh=0；所有用例 loopback TCP=0、raw synthetic leakage=0。该正向控制证明配置合同与实际 consumer binding，不等于完整生产服务启动或生产部署授权。

P1-A 目标运行行为=`REVIEW_VERIFIED / NO_TARGETED_BYPASS_OBSERVED`。因 P1-B 未通过，本轮不执行用户限定的两 finding 全部 closure / P5-F008 acceptance 转移。

## 5. 真实 packaged application JAR exploit replay

本轮重新执行 23-module package+repackage。JAR SHA256=`04598d14e578d181b3fc9ed7b28c25400dc165294790adbd46ce10505a12ea8e`。比较 JAR initializer class、prod YAML、`META-INF/spring.factories` 与 freshly built `target/classes` 字节完全一致。

执行方式为真实 `java -jar backend/nq-app/target/nq-app-0.1.0-SNAPSHOT.jar ...`。没有改写 JAR，没有用手动 initializer unit test 替代。应用保持其原有 web application type，server port=0；所有 datasource alias 与 CLI effective URL 锁定 reviewer 独占 loopback listener，并将 Flyway enabled=true 作为提前拒绝挑战。

`ReviewAgent.java` 仅插入观察事件：guard 输入 profile、AbstractApplicationContext.refresh、HikariDataSource/HikariPool constructor、Flyway.migrate。它不改变条件、返回值或异常；两个 exploit 另有无 agent 的 plain `java -jar` 复跑，以排除观察器改变拒绝结论。

| Case | final profiles（instrumented） | Result | refresh / DataSource / Hikari / Flyway | DB TCP |
| --- | --- | --- | --- | --- |
| default prod / marker missing / known JWT | active=[] default=[prod] | PROD_CONFIGURATION_INVALID | 0 / 0 / 0 / 0 | 0 |
| default prod / marker=false / known JWT | active=[] default=[prod] | PROD_CONFIGURATION_INVALID | 0 / 0 / 0 / 0 | 0 |
| default prod / marker=true / known JWT | active=[] default=[prod] | PROD_CONFIGURATION_INVALID | 0 / 0 / 0 / 0 | 0 |
| default prod / marker missing / blank JWT | active=[] default=[prod] | PROD_CONFIGURATION_INVALID | 0 / 0 / 0 / 0 | 0 |
| default prod / marker=false / blank JWT | active=[] default=[prod] | PROD_CONFIGURATION_INVALID | 0 / 0 / 0 / 0 | 0 |
| 两个 plain 无 agent exploit replay | profile 设置同上；未插桩 | PROD_CONFIGURATION_INVALID | 未直接计数；相同 pre-refresh error stack | 0 |

完整矩阵 14/14 拒绝，包括上述 7 项与 active prod、default mixed、active mixed、include、group、unknown、marker+local。12 项 instrumented negative 的 refresh/DataSource/Hikari/Flyway events 全为 0。没有 DataSource construction，也就未启动 Hikari connection 或 Flyway connection；TCP 正向 listener 自检在每个 case 前成功，计数重置后 application TCP=0。initializer automatic discovery=PASS，secret raw leakage=0。

可重现入口：`python -X utf8 artifacts/f008-review03/jar_probe.py`；结果 `jar-results.json` 与 `jar-*.log`。

**无效 harness 与命令修正记录：** 首轮 reviewer harness 强制 `--spring.main.web-application-type=none`，agent 实际观察 default=[default]，没有形成目标 default-prod effective environment。该轮 7 个用例连接了独占 loopback listener，各 TCP=1；从未连接共享 DB。这些结果保留在 `initial-nonweb-harness/`，不计入目标 prod PASS 或新增 finding。移除非任务要求的 web type override 后，同一真实 JAR 形成 default=[prod] 并提前拒绝。另有单独 `spring-boot:repackage` invocation exit=1，错误为 `Source file is not available, make sure 'package' runs as part of the same lifecycle`；改为同一 Maven invocation `package spring-boot:repackage` 后 23/23 SUCCESS，失败日志 `repackage.log` 保留。没有通过修改 candidate 消除 harness 错误。

## 6. Required jobs 与 capability inventory

从实际 workflow 以 packaged SnakeYAML SafeConstructor 独立解析 jobs/steps，并按真实 producer/consumer command 建立 inventory，再比较 validator `$criticalCapabilities` registry。不是照抄固定 missing/unknown stdout。

- required jobs=9，actual capabilities=25，registered=25，missing=0，unknown=0，unobserved command token=0。
- production-config owner=`backend / Backend regression`；step 唯一、当前无 if/continue-on-error/failure-ignore。
- actual command：`mvn -f backend/pom.xml -pl nq-app -am test -Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest -Dsurefire.failIfNoSpecifiedTests=false`。
- 当前 command 真实执行 118 tests，exit=0；R06/R09/R10 真实失败则 native exit=1。原始 bash step 将其传递给 job；表达式软化变体的 validator 缺口见 P1-01。remote job 未执行。

| Job ID | Required check name |
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

25 个 capability 的逐项 owner、step、完整 invocation 保存在 `capability-inventory.json`，原始解析结果为 `workflow-parsed.log`。其中两项 critical E2E 的 `npm run test:e2e` 继续追至 frontend/package.json 与 tests/e2e/run-e2e.mjs 的 Playwright CLI child process，未执行 frontend regression。

## 7. Mutation 实际结果

| 范围 | 本轮结果 |
| --- | --- |
| canonical existing mutation suite | exit=0；92/92 REJECTED，accepted=0；包含 R06/R09/R10 的 Maven rejection |
| 独立 capability removed | REJECTED |
| conditional execution | REJECTED |
| literal continue-on-error=true | REJECTED |
| failure ignored / `\|\| true` | REJECTED |
| unrelated-test replacement | REJECTED |
| 移除 F008 selector | REJECTED |
| 只保留 initializer selector、移除 secret/profile regression selector | REJECTED |
| Maven failure-ignore flag | REJECTED |
| expression continue-on-error=`${{ true }}` | ACCEPTED，P1-B |
| expression continue-on-error=`${{ 1 == 1 }}` | ACCEPTED，P1-B 同根因 |
| 双 backend regression step expression soft-fail | ACCEPTED，P1-B 同根因 |
| R06/R09/R10 independent Java mutation | Maven 3/3 REJECTED；standalone validator 3/3 ACCEPTED |

额外 workflow mutation 合计 11 项：8 REJECTED / 3 ACCEPTED。不得将这 3 项、standalone source validator 的 3 项接受，与 existing suite 的 92/92 混为全部通过。

独立 harness 初次 Python 默认 GBK 读 UTF-8 workflow exit=1；启用 `python -X utf8` 后修正。初版 Windows fixture 写入 CRLF，导致 validator 在 required job name regex 提前拒绝；显式写 LF 后重跑全部 workflow mutations，表中仅记录修正后的有效结果，不把环境/格式失败算作安全拒绝。

## 8. Full validation 与此前 finding spot-check

所有 PASS 都来自本轮新执行日志 `artifacts/f008-review03/`，未复用 remediation 日志作为通过证据。

| Check | 命令 / 输出 | 实际结果 |
| --- | --- | --- |
| targeted F008 | `mvn -o -f backend/pom.xml -pl nq-app -am test -Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest -Dsurefire.failIfNoSpecifiedTests=false` / targeted.log | exit=0；118 tests，0 failure/error/skip |
| mandatory CI capability | 上述 exact workflow command，不加 `-o` / mandatory-capability.log | exit=0；118 tests，0 failure/error/skip |
| Full Maven REQUIRED | `mvn -o -f backend/pom.xml test` / full.log | exit=0；23/23 modules SUCCESS；1773 tests，0 failure/error，53 skips |
| nq-app | 同 full.log 的真实模块汇总 | 444 tests，0 failure/error，35 skips |
| PG16 fixture | reviewed BackendCiLegacyAccountFixture.java / fixture.log | fresh V1→V46，Flyway validate PASS，pending=0 |
| PG16 app-context | `NqAppContextPostgresSmokeTest`，required=true / pg-smokes.log | 1/1 PASS，skip=0 |
| PG16 repository | `JdbcRepositoryPostgresSmokeTest`，required=true / pg-smokes.log | 1/1 PASS，skip=0 |
| package+repackage | `mvn -o -f backend/pom.xml -pl nq-app -am package spring-boot:repackage -DskipTests` / package-repackage.log | exit=0，23/23 SUCCESS |
| packaged JAR | jar_probe.py / jar-results.json | 14/14 targeted negatives rejected；DB TCP=0 |
| independent runtime/config consumer | context_probe.py / context-results.json | 43/43 expected outcomes |
| canonical validator | `pwsh -NoProfile -File scripts/ci/Test-CanonicalDeliveryWorkflow.ps1` / validator.log | exit=0；9 jobs，25/25/0/0 |
| canonical mutation suite | `pwsh -NoProfile -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1` / canonical-mutations.log | exit=0；92 REJECTED |
| canonical release | `pwsh -NoProfile -File scripts/deployment/tests/Test-NqCanonicalRelease.Tests.ps1` / canonical-release.log | exit=0；66/66 PASS |
| frontend | 未变更且本轮未要求执行 | NOT_REQUIRED / NOT_RUN |
| remote exact-head CI | candidate 未提交；禁止发布 | NOT_RUN |

Full Maven 的 53 个 skipped 用例不是已运行通过；required PG16 双 smoke 已另行显式打开，均 skip=0。原有 JWT public default、credential master-key public default、datasource URL/user/password fallback closure condition 的 targeted spot-check=`REVIEW_VERIFIED`；production YAML 三项无 fallback，Java suite 及 independent default-prod invalid input 均拒绝。alternate datasource、JDBC URL identity override、Flyway split-brain 各做本轮实际 spot-check，均拒绝。未扩大为完整旧区域重审。

Secret redaction：reviewer 新生成 process-only synthetic JWT/master/DB 值，子进程输出落盘前按本次原值检查并脱敏，packaged/context probes raw occurrences=0。独立扫描本轮 112 份日志中的 reviewer 随机值格式，matches=0。该结论限定本次生成值、观察到的 stdout/stderr/异常；不声称覆盖所有未来日志或真实 credential。

## 9. Test environment isolation incident

- local existing DB used=NO；没有查看或连接历史本机数据库。
- disposable PG16=`nq-f008-review03-e78374b71f`；version=`160015 / PostgreSQL 16.15`。
- image=`postgres@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`，`--pull never`；仅 `127.0.0.1:42409`。
- child process 清除继承的 NQ/SPRING/PG/JVM/Maven 配置，再显式绑定 NQ_DB 与 SPRING_DATASOURCE aliases 至该 disposable DB。CI/no-outbound=true，real exchange/AI/DH disabled，venue endpoints=placeholder。
- fixture 只写 disposable DB；V45 在 fresh PG16 通过。Full Maven/双 smoke 均在该隔离环境执行。
- 先前 incident 保持 `TEST_ENVIRONMENT_ISOLATION_INCIDENT`；production finding created=NO。未调查、修复或推断历史本机 DB 数据。
- finally stop container，`pg-residue.log` 为空，task container residue=0。

## 10. Git、authority 与最终决定

- 最终 staged=0；commit=NONE；push=NONE；git add/reset/rebase/amend=NONE。
- candidate fingerprint unchanged；reviewer modifications=本 evidence 1 file；production/test/CI/current-doc candidate 修改=0。
- generated reviewer harness 与本轮日志作为 ignored local evidence 保留；temporary source copies、JAR extraction、observer/fixture compiled directories 清理，temporary residue=0。常规 Maven target build outputs 保留，不是新增正式 candidate。
- `git diff --check` exit=0；`git diff --cached --name-only` 为空；收尾 `git status --short` 仅较基线多本报告。Git 的 LF→CRLF 提示是 warning，没有 whitespace error。
- 回滚本 review：只删除本次新增 evidence 及指定 ignored reviewer artifacts；不还原或清理 inherited candidate。未执行回滚。
- 建议 commit message（仅报告，未执行）：`docs(audit): 记录 F008 Attempt-03 独立审查结果`。这不是实施 candidate 的 commit 许可。

Authority after 保持原值：

```text
accepted_batch=GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW
```

Findings：P0=0 / P1=1 / P2=0 / P3=0。

- P5-F008=`REVIEW_REJECTED / NOT_READY_TO_COMMIT`（本轮 review disposition，未自动 mutation authority）。
- P5-F007 / P5-F009=`OPEN / NOT_IMPLEMENTED`，未涉及。
- P1-A targeted behavior=`REVIEW_VERIFIED`；P1-B=`OPEN / REVIEW_NOT_VERIFIED`。没有将旧 Attempt-01/02 FAIL 改成通过，没有写 `ACCEPTED / CLOSED`。
- Final decision：`FAIL / PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW_REJECTED / P0_0 / P1_1 / NOT_READY_TO_COMMIT`。
- Next concrete action：独立、明确限定范围的 P1-B remediation，修复 expression soft-fail 与 canonical rejection-chain 残留后，再次独立 review。当前不进入 commit/push/exact-head CI；用户指定的 PASS-only next action 尚未满足。

工具声明：外部工具使用 Git、PowerShell 7、Python、Maven、Java/javac、Docker；MCP 使用 Codex open_in_codex 显示本报告（queued）；Skills 未使用，未将 candidate Skills 当 authority；网络使用授权的 Git origin fetch、disposable PG16/loopback 探针，Maven 大部分 `-o`，exact capability 与 canonical suite 保持原始 Maven invocation；未调用生产、真实交易、部署或外部发送接口。正式写操作仅新增本 review evidence，另生成并清理隔离 reviewer 测试产物。无 production implementation、current authority 或远端写入。
