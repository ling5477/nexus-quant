# GateAUDIT Phase5B Exact-Head CI Remediation Attempt-01

## 1. 任务边界

- 基线提交：`0cbcff1c573fa732ae0b4036f32767052aeaf10c`
- 失败 CI：`33600183703 / completed / failure`
- 修复范围：PowerShell mutation closure 的 Linux scope，以及 canonical CI wrapper 的 disposable installation root。
- 明确不修改：production validator、installer security boundary、release admission model、activation/rollback/recovery model、PG16 contract、JAR verifier、业务代码与 current authority。
- Git 策略：仅允许 forward-only commit；禁止 amend、reset、rebase 与 force-push。

## 2. 根因与最小修复

### 2.1 Governance mutation closure

`scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1` 的 `failure-ignored` closure 原先依赖外部 `Assert-Condition`。Linux PowerShell 的独立 closure scope 无法解析该 helper，导致 test harness 在 validator 判断前失败。

修复后 closure 使用本地 `if (-not $match.Success) { throw ... }`，mutation 本身、validator invocation 与 fail-closed 规则均未改变。

### 2.2 Disposable installation root

`scripts/deployment/Install-NqCanonicalReleaseCi.ps1` 原先把 `$env:RUNNER_TEMP` 作为 installation root。GitHub Ubuntu runner 的 `RUNNER_TEMP` 位于 `/home/runner/work/_temp`，不属于 installer 认可的 `[IO.Path]::GetTempPath()` boundary。

修复后 installation root 为系统 temp 下的 `nq-canonical-installation-<GUID>` 唯一目录。release root、admission、install、observe-database、activate 与 verify 调用均未改变；`Install-NqCanonicalRelease.ps1` 未修改。

## 3. 本地与 Linux 回归

### Governance

- Windows PowerShell 7.6.5 validator：`PASS`
- Windows mutation suite：`64/64 REJECTED`
- Linux PowerShell 7.6.5 validator：`PASS`
- Linux mutation suite：`64/64 REJECTED`
- 原失败 case：`verify-canonical-release-and-external-admission-failure-ignored = REJECTED`
- capabilities：`ownership=24 / missing=0 / unknown=0`

### Canonical release / activation / JAR

- Windows canonical suite：`66/66 PASS`
- Linux canonical suite：`73/73 PASS`（以当前实际 suite 数量为准）
- external admission replacement 与 caller override：`REJECTED`
- arbitrary non-temp installation root：`BLOCKED / DISPOSABLE_INSTALLATION_ROOT_INVALID`
- canonical temp-root preflight：`PASS / NQ_CANONICAL_INSTALL_PREFLIGHT`
- cross-process activation、activation-vs-rollback、recovery-vs-activation：`PASS`
- JAR local-name mismatch、local-method mismatch、CRC/truncation：`REJECTED`
- valid data descriptor：`PASS`

### PostgreSQL 16 restore

- PostgreSQL server：`16.15 / 160015`
- `pg_dump`：`16.15`
- `pg_restore`：`16.15`
- migrations：`46 / latest=V46 / pending=0`
- backup SHA-256：`6bb74a3a0d2df6b0f9002c2cea8ba1d4fafa8a7a57364eb54c7694cb8e49b824`
- source/restored canary：`75|464|276|46|V46|1`
- Flyway validate、repository smoke、application-context smoke：`PASS`
- wrong PostgreSQL major（含 PG17 contract negative）：migration 前 `REJECTED`
- backup integrity consumer：`PASS`
- post-restore validation consumer：`PASS`
- production access：`NONE`

所有本轮生成的 restore evidence、portable Linux PowerShell 与隔离 Linux regression repository 均位于明确的临时/ignored 路径，并在验证后清理。

## 4. Focused independent review

- 结果：`PASS / PHASE5B_EXACT_HEAD_CI_REMEDIATION_FOCUSED_REVIEW_ACCEPTED`
- P0：`0`
- P1：`0`
- P2：`0`
- P3：`0`
- scope drift：`0`
- decision：`READY_TO_COMMIT`

## 5. 提交前状态

- 允许提交文件：两个修复文件与本 evidence。
- current authority：不在本任务修改。
- exact-head CI：`PENDING_FORWARD_COMMIT_AND_WORKFLOW_DISPATCH`。
- P5-F002 / P5-F003：继续 `PENDING_EXACT_HEAD_CI`；不得由本地验证提前接受。
