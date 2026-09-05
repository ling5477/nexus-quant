# F008 CI Production-Config Enforcement Remediation

日期：2026-09-05。任务：`NQ-GATEAUDIT-PHASE5-F008-CI-PROD-CONFIG-ENFORCEMENT-REMEDIATION`，NQ-only。

结果：`IMPLEMENTED / F008_CI_PROD_CONFIG_ENFORCEMENT_REMEDIATED / P0_0 / P1_1_REMEDIATED / PENDING_FINAL_CLOSURE_REVIEW`。

## 1. Scope 与基线

- Task classification：`CI_VALIDATOR_SECURITY_FIX / HIGH_RISK / SOFT_FAIL_EXPRESSION_ENFORCEMENT / REQUIRED_CAPABILITY_BINDING / TARGETED_MUTATION_REGRESSION / NO_PRODUCTION_CODE_CHANGE / NO_COMMIT`。
- 唯一目标：[Review Attempt-03](GATEAUDIT_PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW_ATTEMPT_03.md) 的 `CI_PROD_CONFIG_BYPASS_VARIANTS_NOT_ENFORCED`。不重新审查 default-prod、JWT/master-key、datasource fallback、JAR outbound-zero、PG16 或 canonical release。
- Repository=`E:\Project\nexus-quant-gateaudit`；branch=`audit/post-gatey-agent-baseline`；HEAD=origin/audit/post-gatey-agent-baseline=`aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc`。已执行 `git fetch origin --prune`，exit=0；staged=0。
- 读取 repository AGENTS、current STATUS authority、active Skill registry；primary Skill=`nq-dh-workflow-router`，负责 CI/HIGH_RISK 路由；supporting Skills=NONE。用户本轮显式授权整改，最终独立审查仍待执行。
- inherited candidate=23 files；仅更改其中两个 CI scripts 与 TESTING/WORKLOG；其余 19 个文件（包括 Java/config/workflow/deployment/systemd/STATUS/历史 evidence）逐文件 SHA256 不变。
- fingerprint before=`45c1b9d878cc66aeebbd399eb338960727a9677d6992e9248bbc5e311fbeaff5`。
- fingerprint after=`e772baf50ec5b586b5013f83c89a1adbfef33bba7af4a381adfe0720bd1271d3`。
- 口径：固定 preflight 23-file candidate，按 path 排序的 `status|path|byte-length|lowercase-sha256`，LF 连接、UTF-8、无尾 LF，再计算 SHA256。本新增 evidence 不自参与指纹；所有既有 evidence 均参与。文件清单与 hash 在 `artifacts/f008-ci-enforcement-remediation/before.json`、`after.json`。

## 2. Attempt-03 P1 RCA

1. **Expression soft-fail：** 原 required step 与 workflow regex 只拒绝 literal true，`${{ true }}`、`${{ 1 == 1 }}` 能穿透；required job 曾允许 literal false。只做 truthy 黑名单容易遗漏合法 expression 表达。
2. **Rejection-chain：** 原 standalone validator 只检查 workflow/字符串；mutation helper 自己调用 Maven 并计数非零，没有让真实 Java failure 经 canonical admission 同一入口传播。即使 Java tests 检出 R06/R09/R10，standalone validator 仍可能返回成功。

本轮将 Java tests 继续作为 production semantics authority；PowerShell 仅约束其实际、同源、无条件执行与失败传播，不实现 Spring profile 业务规则。

## 3. Validator remediation

修改：[Test-CanonicalDeliveryWorkflow.ps1](../../../scripts/ci/Test-CanonicalDeliveryWorkflow.ps1)。

- **continue-on-error policy：** required job/step 不得存在该字段；literal true、literal false、所有 expression 均拒绝，不评估 GitHub expression。同时覆盖 quoted key；required job/step 的 condition 仍禁止。
- **capability binding：** F008 step 仍唯一，owner=`backend / Backend regression`，两个真实 selector 必须保持；shell 必须为 bash。未新增 job/capability/workflow step。
- **failure-ignore policy：** 对 F008 single-purpose step 比较完整 run script；backend full regression 只允许现有 `set -euo pipefail` 与 `mvn -f backend/pom.xml test`。追加命令、`|| true`、`exit 0`、`|| :`、Maven failure-ignore flag 等均不能匹配执行合同。backend/workflow Maven/JVM options 注入字段也拒绝，防止通过环境旁路测试执行。
- **default canonical admission：** 结构合同通过后，默认入口对 `BackendRoot` 执行真实 F008 Maven regression；受检 initializer/tests/prod YAML/registration 必须与实际执行 reactor 属于同一 source tree，禁止“校验 mutant、运行原始 baseline”。
- **fresh result：** 执行前仅移除两份指定生成的 Surefire XML；执行后要求 native exit=0、两份 fresh reports 均存在、tests>0、failure/error/skip=0。非零 Maven、缺报告或测试未完整执行均拒绝 admission；不是复用旧 XML 或日志。
- **ContractOnly：** 仅用于大量结构 fixture 的显式模式，输出 `CANONICAL_ADMISSION=NOT_EVALUATED_CONTRACT_ONLY`，不输出 admission accepted。真实 CI contract step 必须保留原有完整四条命令，不允许将 default validator 替换为 `-ContractOnly` 或 failure-ignore wrapper。永久 mutation 验证该降级被拒绝。
- **accepted output：** 只有真实 Java capability 通过才输出 `PRODUCTION_CONFIG_REGRESSION=EXECUTED_PASS / CANONICAL_ADMISSION=ACCEPTED`。这里是 validator 的本地 admission 结果，不是 governance acceptance 或 production release admission。

实际绑定的 capability：

```text
mvn -f backend/pom.xml -pl nq-app -am test
-Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest
-Dsurefire.failIfNoSpecifiedTests=false
```

default validator 的 `-f` 使用绑定 backend 的 absolute pom path，其他 test/module selectors 与 workflow 一致。没有更改 `.github/workflows/ci.yml`；既有 CI 对 validator 的调用自动使用完整 admission。

## 4. Permanent mutation matrix

修改：[Test-CanonicalDeliveryWorkflow.Tests.ps1](../../../scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1)。PS5.1/PS7 均实际执行以下永久 cases：

| ID | Mutation | PS5.1 | PS7 |
| --- | --- | --- | --- |
| M01 | F008 continue-on-error literal true | REJECTED | REJECTED |
| M02 | F008 continue-on-error literal false | REJECTED | REJECTED |
| M03 | `${{ true }}` | REJECTED | REJECTED |
| M04 | `${{ 1 == 1 }}` | REJECTED | REJECTED |
| M05 | `${{ !false }}` | REJECTED | REJECTED |
| M06 | 删除 F008 step | REJECTED | REJECTED |
| M07 | F008 加 if/always condition | REJECTED | REJECTED |
| M08 | 删除 secret/profile test selector | REJECTED | REJECTED |
| M09 | 两个 selector 替换为无关 tests | REJECTED | REJECTED |
| M10 | `|| true`、`; exit 0`、`|| :`、Maven failure-ignore flag（4 cases） | 全部 REJECTED | 全部 REJECTED |
| M11 | full backend regression expression soft-fail | REJECTED | REJECTED |
| M12 | 同时 soft-fail full backend 与 F008 | REJECTED | REJECTED |
| M13 | capability 移到非 required job | REJECTED | REJECTED |
| M14 | duplicate capability，新增者为弱化版本 | REJECTED | REJECTED |

另永久覆盖 backend owner job 的上述 5 种 continue-on-error 值、quoted soft-fail key、canonical admission 被降级为 ContractOnly。原 92 项继续通过；新增 24 项，共 `116/116 REJECTED / accepted=0`，每种 PowerShell 版本各自独立运行一遍最终 suite。

## 5. R06 / R09 / R10 完整拒绝链

mutation helper 在独立 backend source copy 中进行原有 Java source mutation，然后调用 **默认 canonical validator** 的 `-BackendRoot` 入口，不使用 ContractOnly，不自行运行一个未接入 admission 的 Maven 命令。

| Mutation | 同源 F008 tests | Assertion failures | Errors / skipped | canonical admission（PS5.1/PS7） |
| --- | --- | --- | --- | --- |
| R06 prod+local | 118 | 17 | 0 / 0 | REJECTED / REJECTED |
| R09 marker=false/default-prod bypass | 118 | 50 | 0 / 0 | REJECTED / REJECTED |
| R10 include/group effective profile bypass | 118 | 12 | 0 / 0 | REJECTED / REJECTED |

native Maven exit=1 经 validator 的 `CANONICAL_ADMISSION_REJECTED / PRODUCTION_CONFIG_REGRESSION_FAILED` 传播；helper 只接受这一明确 rejection，并解析本次 Surefire XML 确认实际执行、assertion failures>0、errors/skips=0。编译错误、缺依赖、静态字符串拒绝或任意 throw 不会被算作有效 Java rejection-chain proof。

最终两份日志都包含：

```text
BASELINE_CANONICAL_ADMISSION=ACCEPTED_JAVA_EXECUTED
MANDATORY_PRODUCTION_CONFIG_CAPABILITY_REJECTED=R06-... tests=118 assertion-failures=17 chain=SOURCE_MAVEN_REQUIRED_CAPABILITY_CANONICAL_ADMISSION
MANDATORY_PRODUCTION_CONFIG_CAPABILITY_REJECTED=R09-... tests=118 assertion-failures=50 chain=SOURCE_MAVEN_REQUIRED_CAPABILITY_CANONICAL_ADMISSION
MANDATORY_PRODUCTION_CONFIG_CAPABILITY_REJECTED=R10-... tests=118 assertion-failures=12 chain=SOURCE_MAVEN_REQUIRED_CAPABILITY_CANONICAL_ADMISSION
MUTATIONS_REJECTED=116
```

源码 mutation 仅发生在自动清理的 test copies；production/Java candidate unchanged=YES。

## 6. Validation 与环境 RCA

| Validation | 本轮实际结果 |
| --- | --- |
| PowerShell 5.1 | `5.1.26100.9168`；完整 suite exit=0；116 REJECTED，accepted=0 |
| PowerShell 7 | `7.6.5`；完整 suite exit=0；116 REJECTED，accepted=0 |
| canonical workflow validator | 两个 suite baseline 均实际调用 default admission，PASS；另执行两种 shell 的 ContractOnly 检查，均明确不授予 admission |
| mandatory targeted F008 Maven | 每种 shell 的 baseline 均 118 tests，failures/errors/skips=0，BUILD SUCCESS |
| required job/capability inventory | live workflow 解析；jobs=9，actual=25，registered=25，missing=0，unknown=0 |
| authority checker | `AUTHORITY_CHECK errors=0 / PASS / CURRENT_AUTHORITY_VALID` |
| git diff --check | exit=0 |
| Full Maven | NOT_REQUIRED / NOT_RUN |
| PG16 smoke | NOT_REQUIRED / NOT_RUN |
| packaged JAR probe | NOT_REQUIRED / NOT_RUN |
| canonical release regression | NOT_REQUIRED / NOT_RUN |
| frontend / remote exact-head CI | NOT_REQUIRED / NOT_RUN |

执行命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci/Test-CanonicalDeliveryWorkflow.ps1 -ContractOnly
pwsh -NoProfile -File scripts/ci/Test-CanonicalDeliveryWorkflow.ps1 -ContractOnly
pwsh -NoProfile -File scripts/docs/check-current-authority.ps1
git diff --check
```

本地 runner 仅清除子进程继承的 NQ/SPRING/PG/JVM/Maven 配置，设置 CI/no-outbound、关闭 AI/DH/real exchange，DB aliases 指向未使用的 `127.0.0.1:1/unused`；F008 selected tests 自身使用隔离环境，不运行 Full Maven 或访问既有本机 DB。未读取 credential 或修改系统配置。

运行期间的真实失败与修正均保留：

- PS5.1 默认 ExecutionPolicy 不允许脚本；使用进程级 Bypass 执行本轮已授权测试，未修改系统/用户策略。
- 原 suite 临时路径过长，PS5.1 Copy-Item 失败；缩短 source-copy root 后，编译生成的深层 nested class 又使 Remove-Item 失败。最终 suite 使用短唯一目录，并在普通 absolute containment 检查之后，仅 Windows cleanup 使用 extended literal path；Linux 路径不变。
- Python 子进程继承 PS7 的 PSModulePath，使 PS5.1 误加载 PS7 Utility、Get-FileHash 不可用；runner 移除该子进程继承值，让每种 shell 自行建立模块路径后恢复。最初的 suite Import-Module 尝试无效并已移除，没有为环境问题保留不必要实现。
- 这些失败运行不计入 final PASS。修正后 PS5.1/PS7 两个完整 suite 均 exit=0，包含原 archive checksum/tamper cases 与完整 cleanup。

Logs：`artifacts/f008-ci-enforcement-remediation/ps51.log`、`ps7.log`、`validation-results.json`；失败 logs 同目录单独保留。`inventory.json` 从当前 YAML 的 SafeConstructor 解析结果逐项核对 owner/step/command，再比较 current validator registry；不是抄录 fixed missing/unknown stdout。执行 F008 的 root native log 位于 `backend/nq-app/target/production-config-admission.log`；它是本轮 targeted output，不是 Full Maven。

## 7. Authority、变更边界、回滚

本轮正式文件 delta：

1. `scripts/ci/Test-CanonicalDeliveryWorkflow.ps1`
2. `scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1`
3. `docs/current/TESTING.md`，仅追加本轮验证
4. `docs/current/WORKLOG.md`，仅追加本轮记录
5. 本 evidence

未修改 Java production/tests、application YAML、workflow、deployment contract、systemd、frontend、migration、research、STATUS、任何历史 Attempt evidence 或 task-ID matcher。P5-F007/P5-F009 不涉及。default-prod/JWT/master-key/datasource/JAR/PG16/release 的 prior review 结果保持，不在本轮重新声明独立通过。

Authority 原样保持：

```text
accepted_batch=GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW
```

Git：staged=0，commit=NONE，push=NONE；未 add/reset/rebase/amend。临时 mutation copies 与 inventory 编译目录已清理；保留 ignored logs、before snapshots 与 `task-delta.patch` 供复核。

风险与影响面：默认 validator 现在需要 Java/Maven，并执行 F008 targeted reactor；不会执行全部 backend tests。canonical CI 的 diff-check 已具备 Java setup，无需 workflow 修改。若能力执行、fresh reports 或绑定失败则拒绝，不将结构检查成功写成 admission 成功。高风险实现仍需独立 final closure review；本实施会话不能审查自身整改来满足 independence。

回滚方式：仅对本轮四个修改文件使用 `artifacts/f008-ci-enforcement-remediation/task-delta.patch` 的反向补丁，并删除本次新增 evidence；before snapshots 可辅助核对。不得 reset/restore 整个 inherited candidate。未执行回滚。

建议 commit message（未执行）：`fix(ci): 阻断生产配置回归软失败并绑定真实 admission`。

## 8. Final decision

- P0=0；P1=1 REMEDIATED，已知 residual=0，pending independent final review；P2=0；P3=0。
- `IMPLEMENTED / F008_CI_PROD_CONFIG_ENFORCEMENT_REMEDIATED / P0_0 / P1_1_REMEDIATED / PENDING_FINAL_CLOSURE_REVIEW`。
- 下一任务：`NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-FINAL-CLOSURE-REVIEW`，这是用户指定的下一工作，不是本轮新增 governance matcher 或 current authority mutation。
- 未输出 REVIEW_ACCEPTED、READY_TO_COMMIT 或 ACCEPTED/CLOSED。

工具声明：使用 Git、PowerShell 5.1/7、Python、Java/javac/Maven；MCP 未使用；Skill 使用 nq-dh-workflow-router（路由与边界）。网络包括授权的 Git origin fetch 与 mandatory Maven dependency resolution；未使用 PG、Docker、生产服务、交易、部署或消息发送接口。正式写操作仅上述五个文件，另生成本地 ignored 验证产物；无 stage/commit/push/系统配置写入。
