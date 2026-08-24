# NQ-GATEY-6F Server Runtime Deployment Contract Remediation attempt-01

## 1. Final decision

`BLOCKED / RUNTIME_HEALTH_IDENTITY_SURFACE_MISSING / SCOPE_ESCALATION_REQUIRED / DEPLOYMENT_RUNTIME_CONTRACT_INCOMPLETE / NO_PARTIAL_DEPLOYMENT_CONTRACT / NO_SERVER_ACCESS`（阻断 / runtime health identity surface缺失 / 需要扩大Java只读surface范围 / 未生成半成品部署合同 / 未访问服务器）。

本任务要求health verifier实际证明运行JVM的source commit、release、profile、capability、LIVE、kill和startup side-effect counters。当前应用只暴露通用`/actuator/health`以及由YAML静态填充的部分`/actuator/info`；无法在不修改Java的前提下证明完整runtime事实。任务明确规定若因此需要修改Java必须停止，因此本轮不新增systemd、env、DB target或orchestrator半成品。

## 2. Task classification 与 baseline

- Task：`NQ-GATEY-6F-SERVER-RUNTIME-DEPLOYMENT-CONTRACT-REMEDIATION`。
- Classification：`DEPLOYMENT_CONTRACT_IMPLEMENTATION + SYSTEMD_RUNTIME_CONTRACT + ENV_OWNERSHIP + DATABASE_TARGET_CONTRACT + ACTIVATION_ROLLBACK_ORCHESTRATION + TESTS`；NQ-only、L级。
- Branch=`dev`；staged=`0`。
- `HEAD == origin/dev == 506b38549a139bafb25bf2ab5820aecac3792f1b`；`git fetch origin`成功。
- Worktree起始仅包含Deployment Attempt-01 blocker evidence允许的4个文档路径。
- Exact-head CI：`NQ CI Baseline` run=`32389011832 / completed / success`，headSha精确匹配。
- Authority：GateY-6F=`NOT_STARTED`、LIVE=`DISABLED`、kill switch=`ENGAGED`、first real order/micro-live未授权、soak未开始。

## 3. Scope 与 files inspected

已只读审计：

- `scripts/gatey/**` GateY builder、release contract、installer、deployment evaluator与regressions；
- `deploy/systemd/nq-gatew-soak@.service`、`nq-gatew-soak-failclose@.service`安全原则，仅参考不修改；
- `deploy/.env.freeze.example`、`deploy/docker-compose.freeze.yml`的non-secret DB identity；
- `application.yml`与`application-gatey-readonly-qualification.yml`；
- `ReadOnlyProviderObservationRuntimeIdentity`、`ReadOnlyProviderObservationConfiguration`、`KillSwitchGuardedProviderObservationAuthority`；
- durable kill switch schema/repository与GateW accepted localhost DB identity；
- Deployment Attempt-01 blocker与GateY-6F implementation/remediation/security evidence。

未读取服务器、生产DB、credential、`.env`、SSH key、日志、backup或provider response。

## 4. Deployment Attempt-01 blocker analysis

Attempt-01发现的systemd、env ownership与canonical DB target缺口均真实存在。审计可确定以下候选安全语义：

```text
release/current=/opt/nexus-quant/releases/<releaseId> + /opt/nexus-quant/current
service user=nq-gatey-readonly
profile=gatey-readonly-qualification
management=127.0.0.1:18890
database host/port/name=127.0.0.1:5432/nexus_quant
kill source=kill_switch_states / GLOBAL_TRADING / ENGAGED
```

但这些静态事实不足以完成任务要求的runtime health acceptance。

## 5. Existing runtime identity surface

### Available

- `application.yml`只暴露actuator `health,info`。
- qualification profile开启`management.info.env.enabled=true`。
- `/actuator/info`可静态提供：

```text
info.nq.release-id
info.nq.source-commit
info.nq.qualification-profile
```

- `ReadOnlyProviderObservationRuntimeIdentity` bean在context创建时验证releaseId/sourceCommit、capability、loopback bind与Java 21。
- durable kill可由`kill_switch_states`只读SQL核对。

### Missing

没有可执行只读surface证明：

```text
running JVM actual capability identity
running JVM actual bind address / Java major identity bean
trading-components actual disabled state
LIVE actual disabled state as consumed by this JVM
kill state as consumed by this JVM
startup credential metadata/material/decrypt counters
startup OKX GET/POST counters
startup ExecutionIntent/Receipt/Order/Ledger deltas
```

仓库没有`InfoContributor`、deployment-safe `HealthIndicator`或现有readiness DTO暴露这些组合事实。

## 6. Why scripts/systemd cannot close the gap

- systemd unit与root-owned env只能证明期望配置和文件ownership，不能证明Spring实际解析/装配结果。
- DB query可以证明durable kill与mutation表事实，不能证明JVM已消费同一kill snapshot，也不能观测decrypt/OKX调用计数。
- `/actuator/health`的HTTP 200、systemd active、PID与listener都不能替代capability/runtime identity。
- `/actuator/info`的YAML静态值没有暴露`ReadOnlyProviderObservationRuntimeIdentity` bean的完整内容。
- 仅靠journal string scan或静态source scan不能满足任务要求的production executable verifier。

继续新增unit/orchestrator会形成表面完整、实际不可验收的fail-open deployment contract，因此禁止。

## 7. Required scope escalation

关闭blocker至少需要一个最小、只读、deployment-safe Java identity surface，例如复用现有actuator info/health扩展，返回不可变且脱敏的：

```text
releaseId
sourceCommit
qualificationProfile
capabilityIdentity
bindAddress
javaMajor
tradingComponentsEnabled
liveEnabled
killStatus + observed version/time
startup DB/credential/decrypt/OKX/mutation counters
```

该surface必须无credential/provider payload、无写操作、仅loopback management访问，并由独立安全审查验证。具体Java设计不在本任务内实现。

## 8. Implementation and tests

```text
systemd contract=NOT_CREATED
env template=NOT_CREATED
canonical DB target=NOT_CREATED
deployment orchestrator=NOT_CREATED
PowerShell regression=NOT_CREATED
disposable Linux regression=NOT_CREATED
Maven=NOT_RUN
GateY/GateW regression=NOT_RUN
```

原因：Java identity hard gate先失败，继续实现会产生不可接受的部分合同。Exact-head CI已为当前committed baseline提供绿色证据，但不代表本次未实现remediation通过。

## 9. Findings

### P0

- 无。

### P1

- `DEPLOYMENT_RUNTIME_CONTRACT_INCOMPLETE`仍未关闭。
- blocker根因进一步定位为`RUNTIME_HEALTH_IDENTITY_SURFACE_MISSING`；不是systemd命名或脚本格式问题。

### P2

- 无；未扩展receipt、HardLink、stable-open、default context或Javadoc主题。

## 10. Architecture hygiene

- backend Java、frontend、research、migration、GateW frozen、governance、STATUS、ROADMAP均未修改。
- 未新增第二runtime identity、第二DB source或通用deployment framework。
- Deployment Attempt-01 evidence未修改。

## 11. Side-effect counters

```text
Server SSH read/write = 0/0

Release upload/install = 0/0
Atomic current switch = 0
Systemd mutation = 0

Production DB read/write = 0/0
Production Migration = 0
Production Backup/Restore = 0/0

Credential metadata/material read = 0/0
Decrypt = 0

OKX GET/POST = 0/0
PLACE/CANCEL = 0/0
Transfer/Withdraw = 0/0

LIVE enable = 0
Kill disengage = 0
```

## 12. Commit recommendation 与 next action

- Final decision：`BLOCKED / RUNTIME_HEALTH_IDENTITY_SURFACE_MISSING / SCOPE_ESCALATION_REQUIRED / DEPLOYMENT_RUNTIME_CONTRACT_INCOMPLETE / NO_PARTIAL_DEPLOYMENT_CONTRACT / NO_SERVER_ACCESS`。
- Commit recommendation：`DO NOT COMMIT`。
- Next concrete action：`NQ-GATEY-6F-RUNTIME-HEALTH-IDENTITY-SURFACE-IMPLEMENTATION`。
- 该任务必须显式允许最小Java只读surface、tests和独立security review；通过后再重新执行本deployment contract remediation，不得直接部署。
- 本轮未add/commit/push/tag。
