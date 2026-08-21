# NQ-GATEY-6F Final Review, Commit, CI, Deployment and Acceptance attempt-01

## 1. Current decision

`BLOCKED / CANONICAL_DATABASE_TARGET_MISMATCH / SERVER_RUNTIME_ENVIRONMENT_NOT_PROVISIONED / FINAL_REVIEW_ACCEPTED / COMMITTED / EXACT_HEAD_CI_GREEN / IMMUTABLE_RELEASE_VERIFIED / DEPLOYMENT_NOT_STARTED / NO_DATABASE_MUTATION / NO_SERVER_MUTATION / P0_0 / P1_0`（数据库目标与服务器不一致 / GateY运行环境未预置 / 部署未开始）。

本文件是联合任务的单一append-forward evidence。尚未执行的commit、CI、release、server preflight、DB、activation、health和acceptance均明确标记为`PENDING_EXECUTION`，不得从本节推断为通过。

## 2. Starting baseline

- Branch=`dev`。
- Starting `HEAD == origin/dev == c48582a6d575d0ecb2a132781e076a5f78dc7dd2`；fetch后remote delta=`0`。
- Pre-evidence dirty paths=`27`、staged=`0`；均为GateY-6F implementation/tests/evidence/current ledgers。
- Authority：GateY-6F=`NOT_STARTED`、LIVE=`DISABLED`、kill switch=`ENGAGED`、errors=0；first real order/micro-live未授权，soak未开始。
- Accepted evidence解析的唯一服务器target=`admin@47.251.74.35`；历史hostname=`iZrj9gpab986sm4d0bb6agZ`。本阶段未执行SSH。

## 3. Phase A — Remediation final review

Counter truth：40-case self-test与动态deployment regression通过。`OBSERVED/null`、NI/0、UNKNOWN/0、nonzero、string、empty、missing、negative均fail-closed；`NOT_INSTRUMENTED/null`与`UNKNOWN/null`只分类为`NOT_VERIFIED`，不等于zero。

Release manifest：startup credential/OKX zero fields不存在；safety只允许`EXPECTED_CONFIGURATION` allowlist；caller-added runtime facts被`RELEASE_MANIFEST_RUNTIME_FACT_INVALID`拒绝。

Plan：PS5.1、PS7和Linux实际执行；`LOCAL_ONLY / ZERO_EXTERNAL_IO`，psql/pgpass/network/DB read/write/filesystem mutation/systemd mutation/runtime starts均为0，fixture digest不变。

UnitPreflight：显式`READ_ONLY_EXTERNAL_IO_ALLOWED`，credential-assisted external process consumption=true，psql/pgpass/network/DB read=`1/1/1/1`，DB write/systemd mutation/runtime starts=`0/0/0`，script credential bytes exposure=false。

## 4. Phase B — Deployment Readiness review

- Runtime endpoint仅`@ReadOperation`；loopback identity、diagnosticOnly=true、tradingAuthorization=false、noSideEffect=true。
- Full Spring context：trusted observation authority=1、diagnostic endpoint=1；SpotExecutionProviderPort/TradingAdapter/worker/recovery/scheduler/private WebSocket=0。
- Systemd：non-root、Restart=no、ProtectSystem=strict、NoNewPrivileges、UMask=0077、release/env write denial、secret templates only。
- DB target固定`gatey-production-control-plane / PRODUCTION_CONTROL_PLANE / 127.0.0.1:5432/nexus_quant / flyway_schema_history / gatey-readonly-qualification-db`；wrong target/reference/Flyway/kill fail-closed。
- Activation/rollback：verify→switch→start→health→identity；失败先stop/verify stopped，再恢复previous pointer，不自动启动previous runtime。
- Production Java GateY stage-semantic hits=0；Java platform=release-21、Spring Boot 3.5.10 / Framework 6.2.15；exceptions=0。

## 5. Phase C — Required validation

| Validation | Result |
| --- | --- |
| Full Maven | PASS；23 modules、321 reports、1552 tests、failures/errors/skipped=`0/0/48`、55.807s |
| PS5.1 deployment/release | PASS；48/48 + 29/29 |
| PS7 deployment/release | PASS；48/48 + 29/29 |
| Disposable Linux `--network none` | PASS；runtime22/22、installer13/13、release29/29 |
| GateW frozen | PASS；34/34 |
| Migration | PASS；V1～V41 continuous、41 files、target V41 |
| Java governance | PASS；V40 git blob contract PASS |
| Java Shadow | `VIOLATION_FOUND` Shadow-only；existing=144、ruleset expansion=14、new-code=0，非阻断 |
| Authority | PASS；errors=0 |
| Links | PASS WITH HISTORICAL WARNINGS；414 checked、14 existing warnings、0 errors |
| `git diff --check` | PASS；exit=0，仅既有LF→CRLF提示 |

Phase C findings：P0=0、P1=0。既有P2/P3不阻断且不拆新readiness任务。

## 6. Pending phases

```text
Phase D exact commit/push = PENDING_EXECUTION
Phase E exact-head CI = PENDING_EXECUTION
Phase F immutable release = PENDING_EXECUTION
Phase G server read-only preflight = PENDING_EXECUTION
Phase H DB/rollback hard gate = PENDING_EXECUTION
Phase I install/activate = PENDING_EXECUTION
Phase J health/runtime acceptance = PENDING_EXECUTION
Phase K side-effect acceptance = PENDING_EXECUTION
Final deployment decision = PENDING_EXECUTION
```

## 7. Safety boundary before commit

```text
Server SSH read/write = 0/0
Production DB read/write = 0/0
Release upload/install = 0/0
Systemd/current mutation = 0/0
Credential material read = 0
OKX GET/POST = 0/0
PLACE/CANCEL/transfer/withdraw = 0/0/0/0
ExecutionIntent/Receipt/Order/Ledger delta = 0/0/0/0
LIVE enable = 0
Kill disengage = 0
```

## 8. Next phase

仅在精确cached allowlist复核通过后执行commit message：`fix(gatey): complete readonly server deployment readiness`，随后push并等待exact-head CI。任何cached scope异常、CI失败或后续production hard gate失败均停止部署并追加真实结果。

## 9. Phase D/E implementation commit and exact-head CI

- Implementation commit=`7c5de6e56f6df2623c0d54f591fd69f9d7745cc6`；28 files、4056 insertions、7 deletions。
- Push=`origin/dev` fast-forward success。
- `NQ CI Baseline` run=`32492178305 / completed / success`，headSha精确匹配；11/11 jobs全部success，包括Secret scan、no-outbound、Java Shadow、Backend Maven、PostgreSQL/Flyway与两类E2E。

## 10. Phase F first build blocker and local fix

clean、committed、CI-green implementation HEAD首次执行canonical builder返回：

```text
BLOCKED / RELEASE_APPLICATION_MIGRATION_MISMATCH
```

Server contact/upload/install=`0/0/0`。RCA确认Spring Boot fat JAR把41个Flyway migration放在唯一`BOOT-INF/lib/nq-infra-*.jar`的`db/migration/**`内；builder错误地只检查不存在的`BOOT-INF/classes/db/migration/**`。该问题属于局部build verifier路径缺陷，不改变安全、credential、LIVE、trading、DB或rollback模型。

最小fix：builder要求唯一nested `nq-infra` JAR，复制到bounded memory stream后逐条验证41个migration path/hash与closed count；synthetic builder fixture同步改为真实nested-JAR布局，tamper仍精确拒绝。

Fix validation：PowerShell parser PASS；builder self-test PASS（41 migrations、valid accepted、tamper rejected）；PS5.1/PS7/Linux release regression均29/29；刚才真实`nq-app` fat JAR独立返回`PASS / REAL_FAT_JAR_MIGRATION_BINDING / migrationCount=41`。

```text
Forward fix commit = PENDING_EXECUTION
Forward fix exact-head CI = PENDING_EXECUTION
Immutable release retry = PENDING_EXECUTION
```

## 11. Phase F second build blocker and local fix

Forward fix commit=`31c87e5b7fe18bf4418781e83df0180165de39e7`，push成功；exact-head `NQ CI Baseline` run=`32493024365 / completed / success / 11 of 11`。

第二次clean CI-green builder执行越过nested migration hard gate后返回：

```text
BLOCKED / RELEASE_ARTIFACT_MISSING
```

RCA：`Invoke-ExactSourceApplicationBuild`把Maven stdout写入PowerShell success stream，导致调用方`$applicationJar`收到`Object[]`而不是单一JAR path；所有13个canonical source artifacts实际存在。该问题同样是局部build orchestration返回值缺陷，server contact/upload/install仍为0。

最小fix：捕获Maven output与exit code，函数success stream只返回单一application JAR path；release regression永久拒绝raw `& $maven @arguments` success-stream leakage。Builder self-test、PS5.1/PS7/Linux release 29/29均通过。

```text
Second forward fix commit = PENDING_EXECUTION
Second forward fix exact-head CI = PENDING_EXECUTION
Immutable release retry = PENDING_EXECUTION
```

## 12. Final forward fix, CI and immutable release

- Second forward fix commit=`96ae90ffb73fdb27b1549e9a88e78fab15397d40`；push=`origin/dev` success。
- Exact-head `NQ CI Baseline` run=`32493560487 / completed / success / 11 of 11`；headSha精确匹配。
- Clean exact-head builder第三次执行：`PASS / GATEY_READONLY_RELEASE_BUILT_VERIFIED`。
- Release ID/source commit=`96ae90ffb73fdb27b1549e9a88e78fab15397d40`。
- Manifest SHA-256=`0b1afbf655e63de428cbcce22708b8a8733cbf2b3ffe8fcc93f3cc7dfdcdf083`。
- Application SHA-256=`6c1513e594903afcf9c9c43ed64d2c8e3de7c3bea511b642bc2150480038db76`。
- Artifact count=`13`；Java major=`21`；profile=`gatey-readonly-qualification`；schema target=`V41`；runtime fact classification=`EXPECTED_CONFIGURATION`。
- Independent local verifier：`PASS / GATEY_READONLY_RELEASE_VERIFIED`。
- Builder前source tree clean；builder后只有预期untracked local `target/` release output，无source diff。

## 13. Phase G server authentication hard blocker

首次server contact严格使用：

```text
BatchMode=yes
StrictHostKeyChecking=yes
ConnectTimeout=10
target=admin@47.251.74.35
command=hostname
```

结果：exit=`1 / Permission denied (publickey,password)`。Remote command executed=`0`。Accepted evidence与本会话用户提供的Deployment附件只包含target，没有可调用IdentityFile path；未读取`.ssh`、private key、SSH config、agent material，未尝试密码或修改known_hosts。

Production hard gate decision：

`BLOCKED / SERVER_AUTHENTICATION_UNAVAILABLE / DEPLOYMENT_NOT_STARTED / NO_SERVER_MUTATION`。

## 14. Unexecuted production phases

```text
Server hostname/current release/Java/disk/systemd/process preflight = NOT RUN
Server LIVE/kill/runtime conflict verification = NOT RUN
Flyway before/pending migration calculation = NOT RUN
Rollback hard gate = NOT RUN
Release upload/install = 0/0
Atomic activation/systemd start = 0/0
Loopback health/runtime identity = NOT RUN
Production counter semantics = NOT RUN
Production side-effect delta query = NOT RUN
Rollback = NOT REQUIRED / activation never started
```

## 15. Current final result and resume condition

- Final P0=`0`；Final P1=`0`。Authentication blocker不是implementation/security finding，也不构成deployment acceptance。
- GateY-6F仍为`NOT_STARTED`；LIVE=`DISABLED`、kill=`ENGAGED`仅来自current Authority，server state未验证。
- Local release保留在ignored `target/gatey-readonly-releases/96ae90ffb73fdb27b1549e9a88e78fab15397d40`；未上传。
- Resume condition：用户提供该target的明确IdentityFile path reference，或显式确认已配置可用non-interactive SSH agent；随后在同一联合任务从Phase G重新执行hostname只读probe。不得新开Deployment Readiness子任务，也不得跳过server/DB/rollback hard gates。

```text
Server SSH attempts/successful reads/writes = 1/0/0
Release upload/install = 0/0
Production DB read/write = 0/0
Systemd/current pointer mutation = 0/0
Credential material read = 0
OKX GET/POST = 0/0
PLACE/CANCEL/transfer/withdraw = 0/0/0/0
ExecutionIntent/Receipt/Order/Ledger delta = 0/0/0/0
LIVE enable = 0
Kill disengage = 0
```

## 16. 2026-08-22 Phase G resumed with explicit SSH identity

用户提供明确IdentityFile path reference后，仅验证path存在/non-empty，不读取key内容；OpenSSH以`IdentitiesOnly=yes / BatchMode=yes / StrictHostKeyChecking=yes`消费该identity，hostname只读probe成功，hostname=`iZrj9gpab986sm4d0bb6agZ`。

Root只读preflight最终确认：

```text
NTP synchronized = yes
Java = OpenJDK 21.0.11
PowerShell major = 7
disk available = 22,793,764 KiB
current = /opt/nexus-quant/releases/b103069d8bfcecccba0b4d590317ddccc66898b9
release root = root:root / 0755
active NQ process = 0
listener 127.0.0.1:18890 = absent
running NQ container = nq-gatew-postgres only
GateY service user = missing
GateY runtime.env = missing
GateY secrets.env = missing
GateY db.pgpass = missing
GateY systemd unit = missing
```

历史GateW soak/failclose units只处于failed或inactive/dead，没有active worker/process；未清理或修改这些历史units。

无凭证PostgreSQL readiness：

```text
127.0.0.1:5432/nexus_quant = not accepting
127.0.0.1:55432/nexus_quant = accepting
nq-gatew-postgres network mode = host
```

Committed GateY canonical target固定`127.0.0.1:5432/nexus_quant`，与服务器listener不一致；DB identity、Flyway history、kill persisted fact均未查询。真实GateY runtime/secret/pgpass也未预置，且任务没有DB username/password、security secret、master key的受控引用。不得从placeholder生成、复制GateW secret、读取container env或绕过owner/mode合同。

Production hard gate：

`BLOCKED / CANONICAL_DATABASE_TARGET_MISMATCH / SERVER_RUNTIME_ENVIRONMENT_NOT_PROVISIONED / DB_AND_ROLLBACK_PREFLIGHT_NOT_EXECUTED / DEPLOYMENT_NOT_STARTED / NO_DATABASE_MUTATION / NO_SERVER_MUTATION`。

## 17. Updated side effects and resume conditions

```text
SSH invocations/authenticated sessions/server mutations = 5/4/0
SSH identity consumed by OpenSSH process = true
SSH key bytes exposed to agent/evidence = false/false
Release upload/install = 0/0
Production DB readiness probes/table reads/writes = 2/0/0
Flyway query/migration = 0/0
Systemd/current pointer mutation = 0/0
OKX GET/POST = 0/0
PLACE/CANCEL/transfer/withdraw = 0/0/0/0
ExecutionIntent/Receipt/Order/Ledger delta = 0/0/0/0
LIVE enable = 0
Kill disengage = 0
Rollback = NOT REQUIRED
```

Resume requires both：

1. 明确决定canonical DB target：将repo contract forward-fix到服务器真实`55432`，或在服务器受控提供canonical `5432` listener；不得由agent自行选择。
2. 通过服务器外部安全渠道预置真实`runtime.env / secrets.env / db.pgpass`，满足`root:nq-gatey-readonly/0640`与`root:root/0600`合同；只需通知已预置，不要在聊天或仓库粘贴secret。

满足后在同一联合任务从Phase G重新执行metadata、DB identity/Flyway/kill和rollback hard gates；不得直接upload或activation。

## 18. Canonical DB port authorization and forward fix

用户明确授权将GateY canonical DB port forward-fix为服务器实际`55432`。Server metadata recheck仍显示service user与三个GateY env files missing；`55432` accepting。

Forward fix精确修改：runtime target JSON、runtime env template、pgpass template、runtime orchestrator及两份deployment regressions；全局PostgreSQL默认、migration、GateW contract、Java/runtime identity均未修改。新增永久负例`wrong-canonical-db-port-blocked`，旧5432 target精确返回`DATABASE_TARGET_MISMATCH`。

Validation：PowerShell parser=0 errors；self-test=41 cases；PS5.1/PS7 deployment48 + release29；Linux runtime22/installer13/release29；GateW34；V1～V41 continuous。PS5.1/PS7/Linux canonical synthetic manifest hash一致为`b01cdb4674c155fe02a83484bb262b72fbfc78e6bd2bea00bbd7d0dcd7271421`。

用户说明GateW runtime可停止、OKX keys已在服务器配置且DB已有相关账户。Read-only preflight实际确认active GateW worker/process=0，只有`nq-gatew-postgres`数据库容器运行；该DB前置不会停止。上述说明不授权读取/复制credential bytes，GateY env files仍须建立明确provisioning合同。

```text
Canonical 55432 forward-fix commit = PENDING_EXECUTION
Forward-fix exact-head CI = PENDING_EXECUTION
New immutable release = PENDING_EXECUTION
Server mutation = 0
```
