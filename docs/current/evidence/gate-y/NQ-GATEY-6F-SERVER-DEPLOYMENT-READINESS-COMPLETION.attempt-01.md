# NQ-GATEY-6F Server Deployment Readiness Completion attempt-01

## 1. Final decision

`IMPLEMENTED / GATEY_6F_SERVER_DEPLOYMENT_READINESS_COMPLETE / RUNTIME_IDENTITY_SURFACE_COMPLETE / SYSTEMD_CONTRACT_COMPLETE / ENV_OWNERSHIP_CONTRACT_COMPLETE / CANONICAL_DATABASE_TARGET_COMPLETE / START_STOP_ORCHESTRATION_COMPLETE / ACTIVATION_ROLLBACK_COMPLETE / LOOPBACK_HEALTH_VERIFIER_COMPLETE / DRY_RUN_ZERO_MUTATION / P0_0 / P1_0 / PENDING_FINAL_DEPLOYMENT_READINESS_REVIEW`（已实现 / GateY-6F服务器部署准备闭环完成 / 等待最终独立review）。

本结论只接受本地代码、PowerShell与disposable Linux合同实现；没有SSH、服务器、生产DB、credential、交易所、deployment、migration、systemd真实操作、LIVE或pilot副作用。

## 2. Baseline 与 exact-head CI

- Branch=`dev`；staged=`0`。
- Starting `HEAD == origin/dev == 506b38549a139bafb25bf2ab5820aecac3792f1b`；`git fetch origin`成功。
- 起始worktree只包含GateY-6F implementation/review/remediation与两个deployment blocker evidence，无mixed worktree。
- Exact-head `NQ CI Baseline` run=`32389011832 / completed / success`，headSha精确匹配。
- Authority：GateY-6F=`NOT_STARTED`、LIVE=`DISABLED`、kill switch=`ENGAGED`、FIRST_REAL_ORDER/MICRO_LIVE=`NOT_AUTHORIZED`、soak=`NOT_STARTED`。

## 3. Scope 与 blocker closeout

同轮关闭runtime health identity、systemd、env ownership、canonical DB target、start/stop、activation/rollback、loopback health、Plan/preflight zero-mutation以及全部对应测试缺口。此前两个blocker evidence未修改；receipt、HardLink、stable-open、GateW frozen、migration与governance范围未重开。

## 4. Runtime identity surface

- Endpoint：`GET /actuator/readonlyproviderobservation`。
- Bean boundary：只在`nq.runtime.provider-observation.enabled=true`且既有mutation/LIVE/real-runtime条件全部fail-closed时注册。
- Loopback：复用`ReadOnlyProviderObservationRuntimeIdentity`的`bindAddress=127.0.0.1`与Java 21 hard gate。
- Stage-neutral：production Java不包含GateY stage-specific security branching；deployment profile alias只从YAML的中性配置键读取。
- Security：仅`@ReadOperation`，无`@WriteOperation`/`@DeleteOperation`；`diagnosticOnly=true`、`tradingAuthorization=false`、`noSideEffect=true`。

响应包含sourceCommit、releaseId、javaMajor、profile/capability/bind、provider/trading/LIVE、kill、mutationRuntimeBound、startedAt/generatedAt以及credential/decrypt/OKX/intent/receipt/order/ledger counters。不包含env、JDBC、DB password、credential、provider payload、account/tenant、token或secret。

未建立可靠production instrumentation的counter返回`NOT_INSTRUMENTED / value=null`，不得伪造0。若未来返回`OBSERVED`，health verifier只接受value=0；任何非零值均`BLOCKED / STARTUP_SIDE_EFFECT_OBSERVED`。

## 5. Qualification production context

`NexusQuantApplication + gatey-readonly-qualification`完整component scan通过：trusted observation authority=1、diagnostic endpoint=1；SpotExecutionProviderPort、TradingAdapter、worker、recovery/reconcile/business schedulers与private WebSocket均为0。

Startup DataSource connection attempts=`0`、OKX proxy selections=`0`。Endpoint连续GET两次后两项仍为0；test-only kill repository只发生两次read，engage/mutation=0。

## 6. Systemd contract

- Unit：`nq-gatey-readonly-qualification.service`。
- User/group：`nq-gatey-readonly:nq-gatey-readonly`；WorkingDirectory=`/opt/nexus-quant`。
- ExecStart：`/usr/bin/java -jar /opt/nexus-quant/current/app/nq-app.jar`；ExecStartPre执行canonical `UnitPreflight`。
- Restart=`no`、KillMode=`mixed`、TimeoutStartSec=`120s`、TimeoutStopSec=`30s`、UMask=`0077`。
- Hardening：NoNewPrivileges、PrivateTmp、ProtectSystem=strict、ProtectHome、kernel/control-group/clock/hostname/device限制、空capability set、release/config read-only，仅runtime directory可写。
- Output只进journal；未复制GateW unit或GateW runtime语义。

`systemd-analyze verify` static contract在network-none Linux无parse/unknown-key/invalid-argument错误。

## 7. Environment ownership 与 secret boundary

```text
runtime.env: root:nq-gatey-readonly / 0640 / service readable, not writable / non-secret only
secrets.env: root:root / 0600 / service not readable, not writable / systemd manager reads
db.pgpass: root:root / 0600 / service not readable, not writable / psql direct reference
```

PowerShell仅验证metadata/path并设置`PGPASSFILE`，不读取credential bytes。Git/release只包含placeholder templates，真实files不进入Git、manifest、logs或evidence。

## 8. Canonical database target

Committed单一事实源：`config/gatey-readonly-runtime-target.json`。

```text
targetId=gatey-production-control-plane
runtimeEnvironment=PRODUCTION_CONTROL_PLANE
host=127.0.0.1
port=5432
database=nexus_quant
Flyway source=flyway_schema_history
credentialReference=gatey-readonly-qualification-db
```

Configured JDBC必须精确匹配；`localhost`、任意URL、soak/test/other DB、wrong target ID/reference均拒绝。Psql只读验证database/port/kill/failed Flyway/current version；target mismatch、kill非ENGAGED、failed/pending schema均fail-closed。

## 9. Start / stop / activation / rollback

Start固定：Java21 → release/hash/POSIX → systemd source/installed hash → env/secret/pgpass metadata → DB/Flyway/kill → current → exact unit start → health。

Stop固定：stop exact unit → active=false → MainPID=0 → cgroup无残留 → listener消失；不删release/evidence、不写DB。

Activation只允许`NoMigration`或未来已接受rollback hard gate。失败时先证明runtime停止；stop未验证即阻断且不切pointer。Stop成功后才atomic恢复previous verified release。

Previous release按manifest schema分流：GateY使用GateY verifier；首部署GateW previous使用GateY release内manifest-bound frozen GateW verifier/contract。未知schema拒绝；previous runtime不自动重启。

## 10. Loopback health verifier

有界30秒轮询，同时要求systemd active、MainPID、`127.0.0.1:18890` listener属于MainPID、health=UP、identity commit/release/profile/capability/bind/Java匹配、provider enabled、trading/LIVE disabled、kill ENGAGED、mutationRuntimeBound=false、diagnostic/no-auth/no-side-effect flags正确以及counter为NOT_INSTRUMENTED或observed zero。

HTTP 200、PID或systemd active任一单独条件都不能通过。

## 11. Deployment orchestrator 与 dry-run

Canonical script：`scripts/gatey/invoke-gatey-readonly-runtime-deployment.ps1`。

Actions：`Plan / UnitPreflight / InstallUnit / Start / Stop / VerifyStopped / Health / Activate / Rollback / ContractSelfTest`。

- Plan与UnitPreflight只读，明确输出filesystem/systemd/DB/runtime mutation=false。
- Unit install使用root-owned temporary unit、systemd-analyze与atomic move。
- Deployment evidence只允许root在固定evidence root create-once写0600 sanitized JSON；Plan/Preflight禁止写evidence。
- 不含credential material read、OKX call、permission probe、observePrerequisites、POST、PLACE/CANCEL或pilot。

## 12. Immutable release closure

GateY release closed set从5扩展为13个manifest-bound artifacts：新增orchestrator、unit、runtime/secret/pgpass templates、DB target以及frozen GateW rollback verifier/contract。Builder/verifier/installer regression同步更新，service user继续不能写release。

## 13. Validation

| Command / check | Result |
| --- | --- |
| focused Java first run | PASS；endpoint unit + production context 4/4 |
| focused Java GET-only extension | initial FAIL；POST无handler但全局exception mapping把405转500；未改全局契约，改用annotation reflection |
| focused Java final | PASS；5 tests、0 failure/error |
| full `mvn -f backend/pom.xml test` | PASS；23 modules、BUILD SUCCESS、failures/errors=0、53.070s；nq-app 299/0/0/30 skipped |
| orchestrator self-test initial | initial FAIL；expected case count误写22、actual23；修正并最终扩展为27 |
| deployment contract PS5.1 / PS7 | PASS；33/33 per engine，self-test 27/27，zero mutation |
| GateY release PS5.1 / PS7 | PASS；27/27 per engine；manifest SHA-256=`e5d57be8a95c33b3221bc1682a8d5bbf35e3f69e78fcf2ee9b6691d669f6169f`一致 |
| disposable Linux runtime / installer / release | PASS；20/20 / 13/13 / 27/27；network none |
| GateW frozen | PASS；34/34，GateW diff=0 |
| migration | PASS；V1～V41、41 files、continuous、target V41、diff=0 |
| authority / stage / diff / secret scan | PASS；errors=0、production Java stage hits=0、diff-check通过、无secret material |

首次失败均保留且未写成通过；最终结果来自修正后的独立rerun。

## 14. Findings 与 residuals

### P0

- 无。

### P1

- 无。`DEPLOYMENT_RUNTIME_CONTRACT_INCOMPLETE`与`RUNTIME_HEALTH_IDENTITY_SURFACE_MISSING`由本实现关闭，等待独立final review确认。

### P2/P3 backlog

- Production counters目前为`NOT_INSTRUMENTED`，不伪造0；任务明确允许该语义。
- POST unsupported method被仓库全局exception mapping转换为500是既有非本轮API行为；endpoint通过Actuator annotation证明GET-only，不扩大修复。

## 15. Architecture hygiene 与 side-effect counters

- app/config只做composition与Actuator read surface；未新增Controller→transport、第二identity或第二DB source。
- deploy/systemd只定义process contract；scripts/gatey只做deployment orchestration；GateW frozen文件未修改。
- frontend/research/migration/governance/STATUS/ROADMAP diff=0。

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

## 16. Commit recommendation 与 next action

- Commit recommendation：`DO NOT COMMIT`。
- 唯一下一动作：`NQ-GATEY-6F-SERVER-DEPLOYMENT-READINESS-FINAL-REVIEW`。
- Review只复核runtime identity、systemd/env/DB、start-stop、activation/rollback、health与dry-run；不得重开receipt/HardLink/stable-open/default-context/Javadoc主题。
- 本轮未add/commit/push/tag或部署服务器。
