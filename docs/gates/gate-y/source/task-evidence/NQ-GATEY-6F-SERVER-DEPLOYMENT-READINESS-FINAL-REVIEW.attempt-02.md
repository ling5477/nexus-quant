# NQ-GATEY-6F Server Deployment Readiness Final Review attempt-02

## 1. Final review decision

`FAIL / GATEY_6F_SERVER_DEPLOYMENT_READINESS_FINAL_REVIEW_ATTEMPT_02_REJECTED / UPSTREAM_DELTA_COMPATIBLE / P0_0 / P1_3 / P0_P1_BLOCKERS_REMAIN / NOT_READY_TO_COMMIT`（失败 / upstream delta兼容 / 发现3个P1 / 不可提交）。

本轮已在更新后的canonical baseline完成技术审查，不再因旧`506b3854...`基线停止。7个upstream提交本身不使implementation失效；拒绝原因来自当前Deployment Readiness implementation的counter与dry-run evidence integrity问题。

## 2. Task、baseline 与 authority

- Task：`NQ-GATEY-6F-SERVER-DEPLOYMENT-READINESS-FINAL-REVIEW`，Attempt=`02`。
- Classification：`NQ-only / REVIEW_ONLY / INDEPENDENT_SECURITY_REVIEW / DEPLOYMENT_CONTRACT_REVIEW / RUNTIME_IDENTITY_REVIEW / UPSTREAM_DELTA_COMPATIBILITY_REVIEW / TARGETED_REGRESSION`。
- Branch：`dev`。
- `HEAD == origin/dev == c48582a6d575d0ecb2a132781e076a5f78dc7dd2`；`git fetch origin`成功。
- Previous baseline：`506b38549a139bafb25bf2ab5820aecac3792f1b`，是当前HEAD祖先。
- Current exact-head CI：`32455734846 / completed / success`，headSha精确匹配。
- Authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0`。
- Current authority：GateY-6F=`NOT_STARTED`、LIVE=`DISABLED`、kill switch=`ENGAGED`；first real order与micro-live未授权。
- Attempt-01保持不可变；pre-write SHA-256=`4b349b42d69f5dd1b08596e2fbd7b9f462139f78aea0f934342162afdbf520f0`。

## 3. Upstream Delta Compatibility Gate

区间共有7个提交、80个路径：Java工程标准及Shadow治理、router/AGENTS接线、CI新增Java Shadow job、V40 checksum test跨平台LF归一化和对应治理证据。

分类结果：

| Area | Upstream impact | Compatibility |
| --- | --- | --- |
| runtime composition / Spring profile / Actuator / kill switch | 无路径或语义变更 | COMPATIBLE（兼容） |
| credential / OKX transport / execution/mutation runtime | 无变更 | COMPATIBLE（兼容） |
| systemd / deploy / GateY scripts / release / installer | 无变更 | COMPATIBLE（兼容） |
| Flyway migration SQL / DB configuration | SQL无变更；仅V40 checksum test canonical LF修正 | COMPATIBLE（兼容） |
| Maven dependency | 无POM变更 | COMPATIBLE（兼容） |
| test infrastructure | CI新增Java governance/Shadow；current dirty Java经临时clean fixture验证new-code Shadow violation=0 | COMPATIBLE（兼容） |
| authority / governance semantics | 新增NQ Java skill路由，不改变STATUS或GateY runtime contract | COMPATIBLE（兼容） |

Upstream paths=`80`，Completion implementation paths=`24`，direct overlap=`0`。Full Maven、Java governance verifier、V40 blob contract和current-head CI均通过，未发现接口失效、Spring graph变化或DB/migration语义变化。因此结论为：`UPSTREAM_DELTA_COMPATIBLE`。

## 4. Changed-set integrity

- Attempt-02 evidence写入前：总dirty paths=`25`，其中Completion implementation expected/actual=`24/24`、missing/extra=`0/0`，另1个为允许的Attempt-01历史证据；staged=`0`。
- 两个deployment历史blocker、Completion evidence和Attempt-01均未被本review改写。
- Attempt-02写入后预期总路径=`26`；仅新增Attempt-02 evidence，并在既有`README.md`、`TESTING.md`、`WORKLOG.md`追加最小索引。
- Implementation本轮byte changes=`0`。

## 5. Runtime identity review

- Endpoint：`GET /actuator/readonlyproviderobservation`。
- `ReadOnlyProviderObservationRuntimeIdentity`构造器验证exact commit、source/release相等、固定capability、`127.0.0.1`和Java 21；非loopback启动fail-closed。
- Endpoint仅有`@ReadOperation`，无`@WriteOperation`/`@DeleteOperation`；full Maven中的反射测试通过。
- `diagnosticOnly=true`、`tradingAuthorization=false`、`noSideEffect=true`。
- 响应字段只含release/runtime/capability/LIVE/kill/mutation/counter/time/diagnostic facts；未发现credential、password、API key、raw provider/JDBC/environment/header/private payload字段。
- 连续GET context test保持DataSource connection attempts=`0`、OKX proxy selections=`0`、kill engage=`0`；POST无Actuator operation，不执行endpoint业务方法。既有全局405→500 mapping保留P3。

Runtime identity本身通过；counter消费端存在下述P1，故不能输出`LOOPBACK_HEALTH_VERIFIED`或`COUNTER_SEMANTICS_FAIL_CLOSED`。

## 6. Counter semantics

### P1-01 — `OBSERVED/null`被PowerShell强制转换为verified zero

`scripts/gatey/invoke-gatey-readonly-runtime-deployment.ps1:390`的`Assert-CounterSafe`使用：

```powershell
if ([string]$Counter.status -ceq 'OBSERVED' -and [long]$Counter.value -eq 0)
```

PowerShell把`[long]$null`转换为`0`。独立内存攻击实际得到：

```text
FAIL / COUNTER_NULL_ACCEPTED_AS_ZERO
observed-null: ACCEPTED
```

同一矩阵确认`NOT_INSTRUMENTED/null`允许、`NOT_INSTRUMENTED/0`拒绝、`UNKNOWN/null`拒绝、`OBSERVED/1`拒绝；唯独`OBSERVED/null`错误通过。缺失`value`属性由StrictMode阻断，但显式JSON `null`不会。

影响：health response mapper或PowerShell JSON parser收到`OBSERVED/null`时可把未知值提升为zero并通过deployment health，直接违反`UNKNOWN != VERIFIED_ZERO`。优先级：P1。

### P1-02 — release manifest继续伪造production startup counter为0

Java endpoint明确返回`NOT_INSTRUMENTED / value=null`，但`gatey-readonly-release-contract.psm1:208-210`仍在manifest中生成：

```text
startupCredentialReads = 0
startupOkxGetCalls = 0
startupOkxPostCalls = 0
```

当前不存在可靠production instrumentation，manifest却生成`0`正向事实，和runtime health合同、Completion evidence“不得伪造0”的结论冲突。该manifest参与immutable release verification，因此不是普通文案问题，而是deployment evidence integrity错误。优先级：P1。

## 7. Spring composition

- Full `NexusQuantApplication + gatey-readonly-qualification` context通过。
- trusted observation authority=`1`，runtime diagnostic endpoint=`1`。
- SpotExecutionProviderPort、TradingAdapter、execution worker、recovery/reconcile、business scheduler、private WebSocket均为`0`。
- startup DataSource connection/OKX selection=`0/0`；连续GET后仍为0。
- production Java stage-semantic scan hits=`0`；只在test类名中存在GateY文本。
- Upstream未改nq-app、profile、POM或相关Spring graph。

## 8. Systemd、environment 与 secret boundary

- non-root user/group=`nq-gatey-readonly`；Restart=`no`、UMask=`0077`、NoNewPrivileges、PrivateTmp、ProtectSystem=strict、ProtectHome及精确ReadOnly/ReadWrite路径存在。
- ExecStart固定immutable current JAR；无Git checkout、server-side Maven build、secret shell拼接或GateW unit复用。
- `runtime.env=root:nq-gatey-readonly/0640`且service只读；`secrets.env`和`db.pgpass=root:root/0600`且service不可读写。
- Linux `systemd-analyze` static regression无parse/unknown-key/invalid-argument错误；installer实际验证root owner/mode、service write denial、no-overwrite、HardLink rejection与atomic current。
- PowerShell metadata verifier不直接读取secret bytes；但Plan/UnitPreflight会启动psql读取pgpass material，见P1-03。

## 9. Canonical DB target 与 mismatch attacks

Canonical contract固定：`gatey-production-control-plane / PRODUCTION_CONTROL_PLANE / 127.0.0.1:5432/nexus_quant / flyway_schema_history / gatey-readonly-qualification-db`。

独立内存攻击17个正/负场景：wrong targetId/environment/host/port/name/Flyway table/credential reference、arbitrary JDBC、future/pending/failed Flyway和kill disengaged均精确fail-closed；`localhost`、missing target及wrong unit由现有self-test/contract test拒绝。V1～V41 continuous、41 files、target=`V41`。

Canonical DB target本身通过。

## 10. Start、stop、activation 与 rollback

- Start静态顺序：release/POSIX → unit/env/secret metadata → canonical DB/Flyway/kill → current → start → loopback listener/PID/health/identity。
- Stop要求inactive、MainPID=0、cgroup无process、listener关闭，并报告release未删、DB mutation=false。
- Activation health failure代码路径先`Stop-Runtime`，stop失败返回`FAILED_RUNTIME_STOP_NOT_VERIFIED`且不恢复pointer；stop成功才恢复previous pointer；previous runtime不自动重启。
- `NoMigration`是当前唯一可activation/rollback分支；其他情况`ROLLBACK_CURRENT_VERIFICATION_NOT_IMPLEMENTED`，保持CODE_ROLLBACK与DATABASE_RECOVERY分离。
- GateY receipt/audit-only与GateW frozen rollback verifier回归均通过。

现有Linux regression只把`activation-rollback-path-present`加入case list，没有实际调用`Activate-Runtime` fault injection。独立fault-injection harness首次在容器启动层exit=1且无输出，未记为通过。由于本轮已有P1 blocker，activation/rollback只记录为静态合同通过，不升级为完整独立动态acceptance。

## 11. Dry-run zero mutation

### P1-03 — Plan/UnitPreflight实际访问credential与DB network，但回归伪报zero

PowerShell AST实际调用链：

```text
Invoke-Plan -> Invoke-ReleasePreflight
Invoke-UnitPreflight -> Invoke-ReleasePreflight
Invoke-ReleasePreflight -> Invoke-DatabaseFacts
Invoke-DatabaseFacts -> $env:PGPASSFILE + /usr/bin/psql
```

对应实现位于`invoke-gatey-readonly-runtime-deployment.ps1:461-480,524-555,581-619`。因此Plan和UnitPreflight会让psql读取root-only pgpass并建立loopback DB连接，不满足任务要求的`credential material read=0 / network=0`。

现有Windows regression只执行`ContractSelfTest`，随后在`run-gatey-readonly-runtime-deployment-contract-regression.ps1:136-147`直接输出`dryRunZeroMutation=true / credentialMaterialRead=false / networkCalled=false`。Linux regression同样只运行ContractSelfTest后手工增加`dry-run-zero-mutation-pass`，没有任何`-Action Plan`调用；source scan中tests目录Plan invocation=`0`。

这不是“只读DB访问是否允许”的一般设计争议，而是与本任务明确zero-network/zero-credential合同冲突，且测试结果声称未发生未实际测量的事实。优先级：P1。

## 12. Validation

| Check | Result |
| --- | --- |
| baseline / remote / CI | PASS（通过）；`c48582a6...`，run `32455734846 / success` |
| upstream delta compatibility | PASS（通过）；80 vs 24 direct overlap=0，相关runtime/deploy contract无变化 |
| Full Maven | PASS（通过）；23/23 modules、321 reports、1552 tests、failures/errors/skipped=`0/0/48`、56.764s |
| PowerShell 5.1 deployment/release | PASS（既有suite通过）；33/33 + 27/27，但未覆盖3个P1 |
| PowerShell 7 deployment/release | PASS（既有suite通过）；33/33 + 27/27，但未覆盖3个P1 |
| Independent counter/DB attacks | FAIL（失败）；17个场景按预期，`OBSERVED/null`错误通过 |
| Disposable Linux `--network none` runtime | PASS（既有suite通过）；20/20，但Plan未实际执行 |
| Linux installer | PASS（通过）；13/13 |
| Linux release | PASS（通过）；27/27 |
| GateW frozen | PASS（通过）；34/34 |
| Migration inventory | PASS（通过）；V1～V41 continuous、41 files、target V41 |
| Java governance verifier | PASS（通过）；release-21 / Spring Boot 3.5.10 / Framework 6.2.15 / V40 blob PASS |
| Java Shadow | `VIOLATION_FOUND`（Shadow发现）；existing baseline=144、ruleset expansion=14、new-code violation=0，按合同非阻断 |
| Authority | PASS（通过）；errors=0 |
| Links | PASS WITH HISTORICAL WARNINGS（通过并有历史警告）；411 checked、14 existing warnings、0 errors |
| `git diff --check` | PASS（通过），exit=0；仅既有LF→CRLF工作区提示 |

执行历史：Docker首次因Desktop engine未启动而连接失败；用户启动后，三组network-none Linux回归均exit=0。主worktree Shadow首次因既有不可读`artifacts/pre-clean-3-pip-tmp`目录exit=3；随后在系统临时clean clone叠加5个dirty Java文件，原始verifier/Shadow成功执行并清理fixture。临时clone首次因Windows长路径checkout失败，启用`core.longpaths=true`后重跑通过。上述失败均未写成通过。

## 13. Findings

### P0

- 无。

### P1

1. `COUNTER_OBSERVED_NULL_COERCED_TO_ZERO`：`OBSERVED/null`被health verifier接受。
2. `RELEASE_MANIFEST_INVENTS_STARTUP_ZERO_COUNTERS`：production instrumentation缺失时manifest仍生成credential/OKX zero事实。
3. `DRY_RUN_CREDENTIAL_AND_NETWORK_SIDE_EFFECT_MISREPORTED`：Plan/UnitPreflight实际调用pgpass+psql，regression未执行Plan却声明zero。

### P2/P3 backlog

- P2：无新增；stable-open identity与full default context保持既有deferred residual，不在本轮重开。
- P3：unsupported POST被全局exception mapper转换为500；endpoint operation未执行，本轮不修。
- Javadoc drift保持既有P3，不重开。

## 14. Side-effect counters

```text
Implementation edits by review = 0
Server SSH read/write = 0/0
Release upload/install on server = 0/0
Production current switch/systemd mutation = 0/0
Production DB read/write = 0/0
Production migration/backup/restore = 0/0/0
Credential metadata/material read = 0/0
Decrypt = 0
OKX GET/POST = 0/0
PLACE/CANCEL = 0/0
Transfer/Withdraw = 0/0
ExecutionIntent/Receipt = 0/0
Order/Ledger mutation = 0/0
LIVE enable = 0
Kill disengage = 0
Disposable container network = none
```

## 15. Commit recommendation 与 next action

- Commit recommendation：`DO NOT COMMIT`。
- 唯一下一动作：`NQ-GATEY-6F-SERVER-DEPLOYMENT-READINESS-REMEDIATION`。
- 该单一remediation必须同时修复3个P1并补永久负向测试；不得再按counter/manifest/dry-run拆成多个readiness子任务。
- 本轮未add/commit/push/tag/rebase/reset或部署。
