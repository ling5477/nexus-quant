# NQ-GATEY-6F server read-only runtime composition and deployment contract P0/P1 remediation attempt-02

## 1. 最终结论

`IMPLEMENTED / GATEY_6F_P0_P1_REMEDIATION_ATTEMPT_02_COMPLETE / VERIFIED_RECEIPT_MINTING_AUTHORITY_ENFORCED / CALLER_ASSERTION_TRUST_PATH_REMOVED / CAPABILITY_NEUTRAL_RUNTIME_COMPOSITION_ENFORCED / FAIL_CLOSED_COMPONENT_ASSEMBLY / ORIGINAL_CLOSED_FINDINGS_REGRESSION_GREEN / P0_0 / P1_0 / PENDING_INDEPENDENT_SECURITY_REVIEW`（已实现 / P0、P1 自查关闭 / 等待独立安全审查）。

本结论只覆盖本地 intentional dirty worktree 与 disposable synthetic/Linux 验证。GateY-6F 仍为 `NOT_STARTED`（未开始）；未执行 commit、push、deploy、tag、服务器访问、production migration、production backup/restore、credential、交易所访问或交易动作。

## 2. Baseline 与范围

- branch=`dev`；staged=`0`；本地 `HEAD == origin/dev == 2605a20e9de3a6ef2cacc3118a353942fa74b2b1`。
- `git fetch origin` 因 GitHub 443 无法连接而失败，因此未证明远端此刻仍等于本地 remote-tracking ref；baseline CI 沿用已给定并已核对的 `32041844923 / attempt 2 / completed / success`。
- dirty paths 全部属于 GateY-6F implementation、Attempt-01 review/remediation、Attempt-02 review 与本轮 remediation；未发现 `MIXED_WORKTREE`。
- 只处理 Attempt-02 的 `P0 / ROLLBACK_CONTRACT_UNPROVEN` 与 `P1 / STAGE_SEMANTIC_SECURITY_BOUNDARY_COUPLING`。
- 明确不处理：full default application-context proof、stable-open identity residual、Javadoc drift；均登记为 `BACKLOG / NON_BLOCKING`（后续清单 / 不阻断）。

## 3. P0 Receipt minting root cause

公开导出的 `New-GateYDeploymentReceipt` 接受 caller 自定义 Fields，再由 helper 附加固定 producer/version 与 digest。`invoke-gatey-readonly-deployment-contract.ps1` 只读取这些自洽 JSON，没有重新验证真实 release/schema/backup/restore/health 对象，因此 caller 可伪造 `COMPATIBLE`、backup/restore metadata 和 `HEALTHY` 并获得可信状态。

## 4. P0 trust-boundary implementation

- 删除公开通用 mint API；`New-GateYVerifiedReceipt` 保持 module-private。
- verifier 生成的 receipt 除 canonical digest 外，还登记在 module-private `ConditionalWeakTable` authority registry；evaluator 只接受同一模块实例内、由 verifier 生成且未修改的对象。
- 持久化 JSON 通过 `Read-GateYUntrustedDeploymentReceipt` 读取后仍是 untrusted input，不能恢复 trusted authority。
- producerIdentity、producerVersion、verificationResult、verifiedAt、proofType、观测 facts 与 receiptSha256 均由 verifier 内部生成或从实际检查对象派生；caller 不能提交检查结论。
- deployment evaluator 不再读取 compatibility/backup/restore/health receipt JSON；PRE_DEPLOYMENT 现场执行 verifier，POST_ACTIVATION 现场执行 health verifier。

## 5. Verifiers

### Compatibility

- `Test-GateYReleaseSchemaCompatibility` 不接受 compatibilityDecision 或 proofType。
- deployment evaluator 的 previous release source 固定为 `/opt/nexus-quant/current`；不再读取 caller-controlled previous-release path。
- 只有 canonical current、POSIX release verification 与 schema inventory identity 同时通过才可能返回 `COMPATIBLE`。
- disposable release 即使 schema 相同也只返回 `UNKNOWN`；缺少 current/受支持 proof 也返回 `UNKNOWN`，随后 rollback contract 强制 `VERIFIED_BACKUP_AND_RESTORE_REQUIRED`。
- `CALLER_ASSERTION` 永久拒绝。

### Backup

- `Test-GateYBackupArtifact` 只接受待检查 artifact path，检查 disposable path、regular-file/link identity、canonical schema、release binding、database identity、Flyway source、实际 SHA-256/size 与实际 owner/mode/ACL identity。
- 本轮只支持 disposable synthetic backup schema；production backup format 未声明为已验证，因而 production deployment fail-closed。

### Restore

- `Test-GateYRestoreEvidence` 只接受 verifier-generated backup receipt 与实际 artifact path；重新验证 hash/binding，在独立 `/tmp/nq-gatey-restore-*` 中执行 synthetic restore copy，并从 restored artifact 派生 result、Flyway version 和 integrity checks。
- caller restore metadata JSON 不能 mint receipt；来自另一 backup 的 restore receipt 被拒绝。

### Health

- `Test-GateYPostActivationHealth` 检查 release、installation verification、current pointer 与 expected runtime identity。
- 本轮未启动 JVM、未执行 loopback actuator health probe，因此 verifier 固定生成 `NOT_VERIFIED / NO_LIVE_JVM_HEALTH_PROBE`；`Assert-GateYHealthReceipt` 返回 `BLOCKED / QUALIFICATION_HEALTH_NOT_VERIFIED`，不能进入 `POST_ACTIVATION_ACCEPTED`。

## 6. Receipt forgery regression

Attempt-02 reviewer PoC 已固化为 10 个永久 case：forged COMPATIBLE、CALLER_ASSERTION、forged backup、forged restore、forged health、other release、other schema、restore from other backup、modified digest 均拒绝；valid verifier-generated disposable chain 通过。PowerShell 5.1、PowerShell 7 与 disposable Linux 均为 32/32 PASS（通过）。

P0 result：`P0_0 / VERIFIED_RECEIPT_MINTING_AUTHORITY_ENFORCED / CALLER_ASSERTION_TRUST_PATH_REMOVED`（自查关闭，等待独立审查）。

## 7. P1 root cause 与 capability-neutral model

原实现把 `GateYReadonlyQualification*`、`GATEY_READONLY_QUALIFICATION_*` 与 `!gatey-readonly-qualification` 写入 production app/api/infra/scheduler。新增敏感 component 默认装配，再依赖跨模块 negative profile denylist 手工排除，属于 fail-open。

修复后 production capability 为：

- `nq.runtime.provider-observation.enabled`：只读 trusted provider observation，缺失默认不装配。
- `nq.runtime.trading-components.enabled`：adapter/controller/catalog/recovery/reconcile/maintenance/business scheduler/private WS 闭包，所有条件均 `matchIfMissing=false`。
- base `application.yml` 将 trading-components 默认值设为 false；只有 local/test/paper/prod/ci/freeze/gated-verify 等现有明确 runtime profile 显式开启，防止无 profile/capability 的新增上下文默认装配。
- `ReadOnlyProviderObservationConfiguration`、`ReadOnlyProviderObservationRuntimeIdentity`、`KillSwitchGuardedProviderObservationAuthority` 与 `READ_ONLY_PROVIDER_OBSERVATION_KILL_SWITCH_REQUIRED` 均为长期 capability-neutral 语义。
- `gatey-readonly-qualification` 仅保留在 `application-gatey-readonly-qualification.yml` 的 profile alias 与 info 字段；alias 显式开启 provider-observation、关闭 trading-components。

## 8. Spring composition proof

`NexusQuantApplication + gatey-readonly-qualification` 完整 component scan 启动通过：

| Bean/counter | Count |
| --- | ---: |
| guarded trusted observation authority | 1 |
| `SpotExecutionProviderPort` | 0 |
| `TradingAdapter` | 0 |
| execution worker/admission | 0 |
| catalog sync | 0 |
| OKX/Binance recovery | 0/0 |
| OKX/Binance REST reconcile | 0/0 |
| ledger/paper/maintenance/validation business scheduler | 0/0/0/0 |
| OKX/Binance private WS client | 0/0 |
| startup DataSource/credential path attempts | 0 |
| startup OKX outbound selections | 0 |

新增 future-sensitive-consumer test 证明：没有显式 `nq.runtime.trading-components.enabled=true` 时 component 不装配。production Java scan 对 `GateYReadonlyQualification`、`GATEY_READONLY_QUALIFICATION`、`!gatey-readonly-qualification` 与 `nq.gatey.readonly-qualification` 均为 0 hit；剩余 GateY production references 仅为 deployment alias YAML 2 处。

P1 result：`P1_0 / CAPABILITY_NEUTRAL_RUNTIME_COMPOSITION_ENFORCED / FAIL_CLOSED_COMPONENT_ASSEMBLY`（自查关闭，等待独立审查）。

## 9. Previous findings regression

- `RELEASE_HARDLINK_BYPASS_CLOSED`：GateY PS5.1/7/Linux 32/32 与 Linux installer 13/13 继续覆盖 external HardLink、parent traversal、post-verification swap、installed hardlink。
- `QUALIFICATION_PRODUCTION_CONTEXT_BOOTABLE`：focused Spring 10/10 与 full Maven 通过。
- `LINUX_INSTALLATION_CONTRACT_ENFORCED`：disposable Linux installer 13/13，通过 root/POSIX/service-user denial/no-overwrite/atomic current/previous release preservation。

## 10. Validation

| Command/check | Result | Scope/environment/known warnings |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | PASS（通过） | 23 modules、320 reports、1548 tests、failures/errors/skipped=`0/0/48`；首次 full run 因一个轻量 test context 未显式 enable capability 导致 1 failure/2 errors，修复 test property 后 targeted 3/3 与两次 final full rerun 均通过 |
| focused Spring regression | PASS（通过） | qualification/config/adapter/authority 10/10；full production context 启动通过 |
| Windows PowerShell 5.1 | PASS（通过） | 32/32；manifest SHA-256=`f41a69d38fdc61ca324fef4e14aa7886727134810fcc9140fad4890763ba00d5` |
| PowerShell 7 | PASS（通过） | 32/32；manifest hash 与 PS5.1 一致 |
| disposable Linux verifier | PASS（通过） | 32/32；cached image、`--network none --rm`，manifest hash一致 |
| disposable Linux installer | PASS（通过） | 13/13；productionMutation=false |
| GateW frozen regression | PASS（通过） | 34/34；GateW diff=0 |
| migration inventory | PASS（通过） | V1–V41、41 files、target V41、inventory SHA-256=`2b6847457a91423f0cbbaed49c3e018f28846a5b94615a169fc5bee67802488b`、migration diff=0 |
| current authority | PASS（通过） | errors=0；GateY-6F=`NOT_STARTED` |
| `git diff --check` | PASS（通过） | exit=0；仅 LF→CRLF 工作区提示 |

验证历史：首次 focused Maven 命令因 PowerShell `-D` 参数未加引号而未进入测试；首次 P0 PS7 run 因 Windows ACL owner 反斜杠 canonical JSON 表示失败，改为不可逆 owner digest 后通过；Docker engine 初始未运行，启动本机 Docker Desktop 后使用 cached image 完成 network-none 回归。以上失败均已 RCA、最小修复并复跑关闭。

## 11. Architecture hygiene 与 deferred items

- app/config 只做 composition；security semantics 位于 guarded authority；provider protocol 仍在 adapter；stage-specific orchestration 仍在 `scripts/gatey`。
- 未新增第二 credential/provider abstraction、execution port、Controller→provider transport、module extraction、migration 或通用 deployment framework。
- Deferred P2：full default production-context proof；stable-open identity residual。
- Deferred P3：Javadoc drift。
- production backup/restore 与真实 JVM health probe 未执行且未宣称通过；当前 contract 对这些来源 fail-closed。

## 12. Side-effect counters

```text
Server SSH read/write = 0/0
Deployment = 0
Production Migration = 0
Production Backup/Restore = 0/0
Systemd/server symlink change = 0/0

Credential metadata/material read = 0/0
Decrypt = 0

OKX GET/POST = 0/0
PLACE/CANCEL = 0/0
Transfer/Withdraw = 0/0

ExecutionIntent/ExecutionReceipt delta = 0/0
Order/Ledger delta = 0/0

LIVE enable = 0
Kill disengage = 0
```

Disposable Linux side effects仅发生在本机 cached、network-none、`--rm` 容器的 `/tmp` 与临时 service user；不构成服务器或 production mutation。为运行回归启动了本机 Docker Desktop；未拉取镜像、未创建持久卷。

## 13. Authority 与交接

- `STATUS.md` / `ROADMAP.md` 未修改；GateY-6F=`NOT_STARTED`、LIVE=`DISABLED`、kill switch=`ENGAGED`。
- P0 findings remaining：0（self-review；待独立 Security Review）。
- P1 findings remaining：0（self-review；待独立 Security Review）。
- Commit recommendation：`DO NOT COMMIT`。
- Next concrete action：`NQ-GATEY-6F-SERVER-READONLY-RUNTIME-COMPOSITION-AND-DEPLOYMENT-CONTRACT-SECURITY-REVIEW-ATTEMPT-03`。
