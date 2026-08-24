# NQ-GATEY-6F Server Deployment Readiness Remediation attempt-01

## 1. Final decision

`IMPLEMENTED / GATEY_6F_SERVER_DEPLOYMENT_READINESS_REMEDIATION_COMPLETE / COUNTER_NULL_FAIL_CLOSED / RUNTIME_FACTS_REMOVED_FROM_RELEASE_MANIFEST / PLAN_ZERO_EXTERNAL_IO_VERIFIED / PREFLIGHT_IO_EXPLICITLY_CLASSIFIED / P0_0 / P1_0 / READY_FOR_FINAL_DEPLOYMENT_REVIEW`（已实现 / 三个P1已关闭 / 可进入最终联合review）。

Commit recommendation：`DO NOT COMMIT`。本轮只实施本地PowerShell合同与回归，不访问服务器、生产DB、credential material、exchange、LIVE或交易路径。

## 2. Baseline 与 scope

- Branch=`dev`；`HEAD == origin/dev == c48582a6d575d0ecb2a132781e076a5f78dc7dd2`；`git fetch origin`成功。
- staged=`0`；pre-write dirty paths=`26`，与24个Completion implementation paths加Final Review Attempt-01/02历史证据精确一致。
- Authority：GateY-6F=`NOT_STARTED`、LIVE=`DISABLED`、kill switch=`ENGAGED`、errors=0。
- Final Review Attempt-01/02保持不可变；SHA-256分别为`4b349b42d69f5dd1b08596e2fbd7b9f462139f78aea0f934342162afdbf520f0`和`67f04a7a48d3210e634e8334791e00ef339e14ed94426331554267cbfa8b4fdb`。
- Implementation scope仅5个既有GateY PowerShell路径；Java endpoint、Spring composition、systemd、DB target、receipt/HardLink、GateW和migration未由本任务修改。

## 3. P1-01 — Counter null fail-closed

### Root cause

旧`Assert-CounterSafe`在检查null前执行`[long]$Counter.value`，PowerShell把显式`$null`转换为0，导致`OBSERVED/null`被误判为zero。

### Change

新parser依次验证：Counter对象存在、`status/value`属性存在、status/value组合、value非null、CLR integral type，最后才用`[decimal]`比较数值；不存在`[long]$null`路径。

返回分类仅为：

| Input | Result |
| --- | --- |
| `VERIFIED_ZERO / integer 0` | `VERIFIED_ZERO` |
| `OBSERVED / integer 0` | 显式映射为`VERIFIED_ZERO` |
| `NOT_INSTRUMENTED / null` | `NOT_VERIFIED` |
| `UNKNOWN / null` | `NOT_VERIFIED` |
| `OBSERVED / null` | `BLOCKED / STARTUP_COUNTER_INVALID` |
| `NOT_INSTRUMENTED / 0` | blocked |
| `UNKNOWN / 0` | blocked |
| `OBSERVED / 1`或negative | `BLOCKED / STARTUP_SIDE_EFFECT_OBSERVED` |
| string `"0"`、empty string、missing property、non-integral | `BLOCKED / STARTUP_COUNTER_INVALID` |

Health counter proof新增`total / verifiedZero / notVerified / unknownNeverPromotedToZero`，当前9个production counters精确分类为`verifiedZero=0 / notVerified=9`，不会把unknown升级为zero。

永久gate：`counter-null-rejected`、`unknown-never-equals-zero`、`not-instrumented-never-equals-verified-zero`及全部malformed/nonzero负例均通过。P1-01关闭。

## 4. P1-02 — Release/build facts与runtime facts分离

### Root cause

旧release manifest即使没有production instrumentation，也生成`startupCredentialReads=0 / startupOkxGetCalls=0 / startupOkxPostCalls=0`，把运行时未知事实伪装成immutable build fact。

### Change

- 删除全部startup counter字段。
- `safety`只保留配置期望，并强制`factClassification=EXPECTED_CONFIGURATION`。
- verifier采用精确allowlist；caller新增startup counters、`runtimeHealthy`、`killSwitchObserved`或`databaseConnected`均返回`BLOCKED / RELEASE_MANIFEST_RUNTIME_FACT_INVALID`。
- 运行时counter唯一来源保持`GET /actuator/readonlyproviderobservation`及未来真实instrumentation；manifest不能满足health counter proof。

PS5.1、PS7与Linux release regression均通过29/29；`release-manifest-cannot-assert-runtime-counter-zero`和`caller-runtime-zero-field-rejected`永久固化。跨引擎canonical manifest SHA-256一致为`0039f316e7975478f210f7420f41077f0cfb519c3f5d4b50fc3c2920650b5bda`。P1-02关闭。

## 5. P1-03 — Plan与Preflight I/O语义分离

### Before

`Plan → Invoke-ReleasePreflight → Invoke-DatabaseFacts → PGPASSFILE + psql`，但回归未执行Plan却声明zero network/credential interaction。

### After

`Plan`改为：

```text
Get committed/local target contract
→ verify local release/manifest/artifact hashes
→ verify local systemd source contract
→ render expected actions
```

不再要求root Linux，不读取runtime.env/secret/pgpass，不运行Java、psql、systemctl或HTTP，不验证runtime environment/DB facts。输出：

```text
scope=LOCAL_ONLY
externalIoClassification=ZERO_EXTERNAL_IO
psqlInvocations=0
pgpassUses=0
networkCalls=0
databaseReads/Writes=0/0
filesystem/systemd mutations=0/0
runtimeStarts=0
credentialAssistedExternalIo=false
credentialMaterialConsumedByExternalProcess=false
```

`UnitPreflight`继续执行明确需要的root/server read-only verification，并准确输出：

```text
scope=READ_ONLY_RUNTIME_IO
externalIoClassification=READ_ONLY_EXTERNAL_IO_ALLOWED
credentialAssistedExternalIo=true
credentialMaterialConsumedByExternalProcess=true
credentialBytesExposedToScript=false
psqlInvocations=1
pgpassUses=1
networkCalls=1
databaseReads/Writes=1/0
filesystem/systemd mutations=0/0
runtimeStarts=0
```

### Dynamic proof

PS5.1、PS7和network-none Linux均实际执行`-Action Plan`：Plan成功、fixture tree digest前后相同、runtime/DB facts保持unverified，AST call graph不存在`Invoke-ReleasePreflight / Invoke-DatabaseFacts / Invoke-Native / Invoke-RestMethod / psql / systemctl / PGPASSFILE`。永久gate：`plan-zero-external-io-classified`、`dynamic-plan-zero-external-io-pass`、`preflight-external-io-explicitly-classified`。P1-03关闭。

## 6. Files changed

Implementation：

- `scripts/gatey/invoke-gatey-readonly-runtime-deployment.ps1`
- `scripts/gatey/gatey-readonly-release-contract.psm1`
- `scripts/gatey/tests/run-gatey-readonly-runtime-deployment-contract-regression.ps1`
- `scripts/gatey/tests/run-gatey-readonly-runtime-deployment-linux-regression.ps1`
- `scripts/gatey/tests/run-gatey-readonly-release-contract-regression.ps1`

Evidence/current docs：本文件、GateY evidence README、`TESTING.md`、`WORKLOG.md`。

## 7. Validation

| Validation | Result |
| --- | --- |
| PowerShell parser | PASS；5个目标脚本parse error files=0 |
| Contract self-test | PASS；40 cases，完整counter truth table与I/O classifications |
| PS5.1 deployment/release | PASS；48/48 + 29/29 |
| PS7 deployment/release | PASS；48/48 + 29/29 |
| Disposable Linux `--network none` runtime | PASS；22/22，实际Plan执行 |
| Linux installer | PASS；13/13 |
| Linux release | PASS；29/29 |
| Full Maven | PASS；23 modules、321 reports、1552 tests、failures/errors/skipped=`0/0/48`、52.699s |
| GateW frozen | PASS；34/34 |
| Migration | PASS；V1～V41 continuous、41 files、target V41、inventory SHA-256=`2b6847457a91423f0cbbaed49c3e018f28846a5b94615a169fc5bee67802488b` |
| Authority | PASS；errors=0 |
| Links | PASS WITH HISTORICAL WARNINGS（通过并有历史警告）；414 checked、14 existing warnings、0 errors |
| `git diff --check` | PASS（通过），exit=0；仅既有LF→CRLF工作区提示 |

执行历史：首轮parser汇报wrapper因`$f:`字符串插值错误exit=1，目标PS scripts未执行；修正wrapper后parse error files=0。首次targeted manifest hash为`cefb...`，随后仅增强Preflight审计字段导致orchestrator artifact hash变化，全部PS5.1/PS7/Linux suites最终重跑，正式最终hash为`0039...b5bda`。失败和中间结果均未写成最终PASS。

## 8. Architecture hygiene 与 boundary

- release manifest=immutable build/release facts + explicit expected configuration。
- runtime endpoint=runtime observed facts。
- Plan=pure local planning。
- UnitPreflight=explicit credential-assisted read-only runtime I/O。
- activation=mutation phase；既有start/stop/rollback顺序不变。
- Java、frontend、research、systemd、canonical DB target、migration、GateW、STATUS、ROADMAP本任务新增diff=0。
- 既有receipt audit-only、HardLink/no-link、POSIX installer与GateW frozen regressions全部保持。

```text
Server SSH read/write = 0/0
Production release upload/install = 0/0
Production current/systemd mutation = 0/0
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

## 9. Findings、rollback 与 next action

- P0 remaining：0。
- P1 remaining：0；Attempt-02三个P1全部关闭，等待最终独立review。
- P2/P3：不重开stable-open、default-context、POST 405→500或Javadoc既有backlog。
- Rollback：恢复上述5个PowerShell implementation/test文件到本remediation前内容，并删除本evidence及三处索引追加；不得回退其他GateY dirty implementation。
- Commit recommendation：`DO NOT COMMIT`。
- 唯一下一动作：`NQ-GATEY-6F-FINAL-REVIEW-COMMIT-CI-DEPLOYMENT-AND-ACCEPTANCE`；该联合任务按`final review → commit → push → exact-head CI → immutable release → server preflight → deployment → health → acceptance`执行。
