# GateW Attempt-10 pre-create internal readback failure RCA/fix — attempt 01

## 1. Task classification

- Task：`NQ-GATEW-ATTEMPT-10-PRECREATE-INTERNAL-READBACK-FAILURE-RCA-AND-FIX`
- Type：
  `INTERNAL_READBACK_RCA / JAVA_LAUNCHER_DIAGNOSTICS / JDBC_READONLY_PATH_FIX / CROSS_PLATFORM_RUNNER_FIX / FAIL_CLOSED_SECURITY_REVIEW / DISPOSABLE_LINUX_VERIFICATION / RELEASE_CANDIDATE_STABILIZATION`
- Scope：NQ-only、GateW release-candidate 代码整改。
- Starting HEAD：`3bfb4e5bcc2fa30db1d75e9162b1121ff6bf4b60`
- Starting exact-head CI：`30566232790 / completed / success / 10 of 10`
- Authority before：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATION
work_batch_status=DEPLOYMENT_VERIFICATION_FAILED|CODE_REMEDIATION_REQUIRED
next_action=NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-INTERNAL-READBACK-FAILURE-RCA-AND-FIX
Attempt-10=NOT_CREATED|START_BLOCKED
```

本任务未连接生产服务器，未上传、安装、激活或部署 release，未修改 systemd，未访问 OKX 或真实 credential，未创建
Attempt-10、RunId、acceptance clock 或 worker。

## 2. Historical failure fact

前一生产 canonical `precreate-prerequisite` 返回：

```text
releaseBindingVerified=true
readyForAttemptCreation=false
blockerCodes=[INTERNAL_SANITIZED_READBACK_FAILURE]
managementHealthy=false
postgresReachable=false
```

其中 false/UNKNOWN 是旧 fallback projection，不是 credential、permission、IP allowlist、account 或 PostgreSQL 实际不满足的
证据。旧实现主动抑制 Java/JDBC 原始异常，本任务不通过生产 SSH、raw exception、stack trace、JDBC URL/password 或手工查询
恢复被丢弃的历史底层值。

## 3. RCA

### 3.1 Code-contract root cause

旧 readback 链把多层失败收敛到同一 fallback：

1. `PrerequisiteMain` 将 configuration、datasource、driver、connection、query、mapping 与 serialization 放在宽泛异常区间；
2. PowerShell launcher 丢弃 stderr，不保留 JVM exit code，不区分合法 fail-closed exit、main/classpath failure、输出污染与
   JSON parse failure；
3. PowerShell catch 再将缺失或无效 readback 投影为 `INTERNAL_SANITIZED_READBACK_FAILURE`；
4. pre-create 读取分为 kill-switch query 与 credential aggregate query，失败边界比必要范围更宽；
5. Linux security regression runner 无条件使用 Windows-only `-WindowStyle Hidden`，导致此前 Linux 回归只能修改临时
   harness 后运行。

因此可以高置信确认的根因是 readback/launcher contract 的失败分层缺失与信息丢失；无法、也不应把历史 fallback 反推成某个 生产
credential/permission/IP 事实。

### 3.2 Layer matrix

| Layer                 | Expected behavior                                  | Old observed/code behavior    | Safe evidence after fix                              | Failure category                                          | Fix                        |
|-----------------------|----------------------------------------------------|-------------------------------|------------------------------------------------------|-----------------------------------------------------------|----------------------------|
| PowerShell control    | 只输出一个 closed-schema JSON                      | catch 统一 fallback           | 正反例均为单一 JSON                                  | `POWERSHELL_CONTROL`                                      | 保留稳定 stage/code        |
| Java executable       | 固定 `/usr/bin/java`、Java 21                      | 无启动布尔/exit taxonomy      | Java `21.0.12`、`javaStarted=true`                   | `JAVA_EXECUTABLE_NOT_FOUND` / `JAVA_PROCESS_START_FAILED` | 显式检查与诊断             |
| classpath/main        | release closed set 内可启动                        | main/classpath 失败不可区分   | 122 JAR `unzip -t`；main 正常启动                    | `MAIN_CLASS_NOT_FOUND` / `CLASSPATH_INCOMPLETE`           | 固定 classpath 与分类      |
| configuration         | profile/action/result path 受控加载                | 宽泛异常                      | `configurationLoaded=true`                           | `CONFIGURATION_NOT_LOADED`                                | Java 分段初始化            |
| datasource/driver     | 只使用正式 descriptor + encrypted secret reference | 宽泛异常                      | `datasourceConfigured=true`、`jdbcDriverLoaded=true` | `DATASOURCE_NOT_CONFIGURED` / `JDBC_DRIVER_NOT_FOUND`     | 分段 taxonomy              |
| PostgreSQL            | 只连接 loopback disposable DB                      | fallback 不能区分             | 正例可达；unused port 精确分类                       | `POSTGRES_CONNECTION_FAILED`                              | timeout + 稳定错误码       |
| query                 | 单条 bounded SELECT、无 DB write                   | kill switch 与 aggregate 分开 | `queryExecuted=true`；relation fault 精确分类        | `QUERY_EXECUTION_FAILED`                                  | 合并为单条 aggregate query |
| result mapping        | alias/type/null 映射稳定                           | 宽泛异常                      | 正常 mapping；overflow unit fixture                  | `RESULT_MAPPING_FAILED`                                   | 显式 mapping stage         |
| JSON serialization    | result file closed schema，stdout 空               | serialization/parse 不可区分  | `jsonSerialized=true`；敏感字段拒绝                  | `JSON_SERIALIZATION_FAILED`                               | Java closed-schema 自检    |
| stdout/stderr         | stdout 无污染；stderr 只允许稳定 marker            | stderr 被直接丢弃             | warning/main/classpath/marker 单测                   | `OUTPUT_CONTRACT_CONTAMINATED`                            | 分离并限长                 |
| PowerShell JSON parse | 只接受闭合字段集                                   | 统一 fallback                 | parse/contract 单测                                  | `POWERSHELL_JSON_PARSE_FAILED`                            | 独立分类                   |
| cross-platform runner | Linux 不出现 WindowStyle                           | Linux runner 不兼容           | Windows 条件分支；Linux 12/12                        | `SECURITY_CROSS_PLATFORM_WINDOW_STYLE_INVALID`            | 仅 Win32 设置 WindowStyle  |

### 3.3 Release stabilization defects

Readback fix 后构建 final release 时又发现两个独立 supply-chain 缺陷：

1. `Get-GateWCrc32` 使用 `[uint32]` 中间位运算，受 PowerShell signed conversion 影响；
2. canonical ZIP writer 的条件表达式把 `[byte[]]` 展开为 `object[]`，导致 `BinaryWriter.Write` 选择错误 overload，只写
   `0x01`，central directory 与声明的数据区重叠。

修复使用 `[uint64]` 中间值和显式 `0xffffffff` mask，并将 entry data 显式声明为 `[byte[]]`。Builder self-test 新增标准
CRC32 向量 `123456789 -> cbf43926` 与真实 entry 内容回读。中间 commit `f54cdc81...` 虽然 CI green，但其 JAR 结构/CRC
错误，明确作废，不是 release baseline。

## 4. Implementation commits and CI

| Purpose                                   | Commit                                     | Exact-head CI                      | Decision                        |
|-------------------------------------------|--------------------------------------------|------------------------------------|---------------------------------|
| readback/JDBC/launcher/cross-platform fix | `22c6c17a0d6a290679f0bd4808fb38022330c4a0` | `30572653874 / success / 10 of 10` | accepted implementation layer   |
| initial release determinism extraction    | `f54cdc810cddad52e14048f72fe1ad32e6a22472` | `30573625689 / success / 10 of 10` | invalid RC artifact；superseded |
| canonical ZIP CRC/byte-array correction   | `5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6` | `30576297678 / success / 10 of 10` | final RC source head            |

`30576297678` 的 `headSha` 精确为 `5e7a9c4e...`，10 个实际 jobs 全部 `success`。

## 5. Code and regression validation

| Validation                                         | Result                                                |
|----------------------------------------------------|-------------------------------------------------------|
| PowerShell 5.1 / 7.6.3 native AST                  | PASS                                                  |
| IDEA inspections for changed PowerShell files      | 0 warning / 0 error                                   |
| builder self-test，PowerShell 5.1 / 7              | PASS                                                  |
| control self-test                                  | PASS / 66 cases                                       |
| worker self-test                                   | PASS / 59 cases                                       |
| fail-close self-test                               | PASS / 8 cases                                        |
| installer self-test                                | PASS                                                  |
| release reproducibility regression                 | PASS / 16 cases per engine；cross-engine hashes equal |
| remediation regression                             | PASS / 32 cases                                       |
| security regression                                | PASS / 12 cases                                       |
| governance lifecycle / authority / evidence policy | PASS                                                  |
| focused Maven                                      | PASS / 49 tests / 0 failures / 0 errors / 2 skipped   |
| complete candidate JAR integrity                   | PASS / 122 JAR `unzip -t`                             |

首次 focused Maven 命令因 PowerShell `-D` quoting 错误 exit `1`，加引号后按同一测试范围重跑通过；该失败未写成通过。

## 6. Final reproducible release candidate

最终 source：`5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6`

```text
sourceTreeMode=EXACT_COMMIT
manifest SHA-256=cbc2c0c49ec1bce7b0bf7211535b2face2e4c37471d5d4d645cfe52ec4dbce7b
bundle SHA-256=84446b2d9631780df1e921b57ec62a56658e9a2998914e2a05b66cddf5e952d3
artifactCount=131
USTAR entries=132
manifestBytesIdentical=true
bundleBytesIdentical=true
artifactDescriptorsIdentical=true
missing/extra/undeclared=0/0/0
serverGitReferences=0
sensitiveArtifacts=0
```

两次 `EXACT_COMMIT` 构建分别使用 PowerShell 5.1 与 7；manifest、artifact descriptor 与 bundle bytes 完全一致。

## 7. Disposable Linux/PostgreSQL verification

### 7.1 Environment

- WSL Ubuntu disposable Linux。
- Java：`21.0.12`。
- PowerShell：`7.5.2`。
- PostgreSQL：Windows host 上任务专用 PostgreSQL 17 instance，通过 WSL loopback `127.0.0.1:55439` 访问。
- Management fixture：只监听 `127.0.0.1:18889`，返回 `{"status":"UP"}`。
- DB：current schema 的最小受控 fixture；真实 credential material=`0`，仅使用 encrypted fake test secret。
- 外部网络：未使用；Java 只访问两个 loopback target。

Canonical root verifier：

```text
PASS / IMMUTABLE_RELEASE_VERIFIED
sourceCommit=5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6
manifestSha256=cbc2c0c49ec1bce7b0bf7211535b2face2e4c37471d5d4d645cfe52ec4dbce7b
artifactCount=131
posixVerified=true
```

独立 ownership/mode 检查覆盖 131 artifact files、6 directories，symlink=`0`。122 个 JAR 全部通过 `unzip -t`。

### 7.2 Canonical helper matrix

每个场景均通过：

```text
bin/gatew-okx-readonly-soak-control.ps1 -Action precreate-prerequisite
```

| Scenario                       | Helper exit | ready | Exact blocker/failure                                                                         |
|--------------------------------|------------:|-------|-----------------------------------------------------------------------------------------------|
| all prerequisites satisfied    |           0 | true  | blocker=`[]`；stage=`COMPLETED`；code=`NONE`                                                  |
| OKX LIVE account missing       |           2 | false | `ACCOUNT_SCOPE_MISMATCH`                                                                      |
| credential missing             |           2 | false | `CREDENTIAL_NOT_CONFIGURED`                                                                   |
| active credential count=0      |           2 | false | `ACTIVE_CREDENTIAL_COUNT_INVALID`                                                             |
| active credential count=2      |           2 | false | `ACTIVE_CREDENTIAL_COUNT_INVALID`                                                             |
| credential type mismatch       |           2 | false | `CREDENTIAL_TYPE_MISMATCH`                                                                    |
| credential status inactive     |           2 | false | `CREDENTIAL_LOCAL_STATUS_NOT_ACTIVE`                                                          |
| permission fact missing        |           2 | false | `PERMISSION_FACT_MISSING`                                                                     |
| permission fact stale          |           2 | false | `PERMISSION_FACT_STALE`                                                                       |
| read permission not verified   |           2 | false | `READ_PERMISSION_NOT_VERIFIED`                                                                |
| trade permission unsafe        |           2 | false | `READ_PERMISSION_NOT_VERIFIED,TRADE_PERMISSION_NOT_DISABLED`                                  |
| withdraw permission unsafe     |           2 | false | `WITHDRAW_PERMISSION_NOT_DISABLED`                                                            |
| IP allowlist unknown           |           2 | false | `IP_ALLOWLIST_NOT_VERIFIED`                                                                   |
| management fixture unavailable |           2 | false | `MANAGEMENT_UNREACHABLE`；stage=`COMPLETED`                                                   |
| PostgreSQL unused port         |           2 | false | `POSTGRES_UNREACHABLE`；stage=`POSTGRES_CONNECTION`；code=`POSTGRES_CONNECTION_FAILED`        |
| query relation fault           |           2 | false | `INTERNAL_SANITIZED_READBACK_FAILURE`；stage=`QUERY_EXECUTION`；code=`QUERY_EXECUTION_FAILED` |

业务 blocker 场景均为：

```text
javaStarted=true
configurationLoaded=true
datasourceConfigured=true
jdbcDriverLoaded=true
postgresConnectionAttempted=true
queryExecuted=true
resultMapped=true
jsonSerialized=true
credentialMaterialExposed=false
```

Positive 场景对 `users`、`exchange_accounts`、`exchange_account_credentials`、`kill_switch_states` 做 helper 前后状态摘要，
`databaseStateBeforeAfterIdentical=true`，证明应用 readback 无 DB write。Fixture 的创建、负例 fault injection 与清理只发生在
任务专用 disposable DB；production SQL/write=`0`。

`RESULT_MAPPING_FAILED` 需要让聚合 `COUNT(*)` 超过 `Integer.MAX_VALUE`，未在真实 PostgreSQL 构造不受控海量数据。对应
Mockito test 使用 `Long.MAX_VALUE`，精确验证：

```text
failureStage=RESULT_MAPPING
failureCode=RESULT_MAPPING_FAILED
queryExecuted=true
resultMapped=false
```

### 7.3 Cleanup

- WSL 临时 `/usr/bin/java`、`/usr/bin/pwsh`、root descriptor、encrypted fake secret、management fixture、release runtime 与
  `/run/nq-gatew-precreate-*` 全部删除。
- Disposable PostgreSQL 在最终 `users/accounts/credentials/kill_switch_states=1/0/0/1` 后 fast stop；data root 与 marker
  已删除。
- 两个本地 RC build roots 已删除。
- Cleanup 后所有任务专用路径均 `exists=false`。

首次 WSL setup 的最后一条 `stat` 因 PowerShell 到 WSL 的 CRLF 尾字符 exit `1`；Java、PowerShell、health、PostgreSQL
connectivity 和两个 root:root `0600` 文件已由独立命令全部复核通过，因此该包装错误不影响正式 helper 或 release 结果。

## 8. Security and side-effect boundary

```text
production SSH=0
production changes=0
production deployment=0
production database reads/writes=0/0
production manual SQL=0
OKX/private endpoint calls=0
real credential material read=0
raw provider response=0
Attempt-10 created=false
RunId/clock/worker created=false/false/false
LIVE/trading write=0
freeze/archive/tag=0/0/0
```

本任务的 disposable fixture 使用假数据与受控 SQL，仅用于正反例构造和清理；helper 前后数据库状态一致。未读取或输出 JDBC
URL、数据库 password、API Key、Secret、Passphrase、credential payload、owner/account 私密值、raw exception、stack trace 或 SQL
全文。

## 9. Findings and limitations

- P0：无。
- P1：无开放项。旧 readback failure conflation 与 Linux runner incompatibility 已修复；A2 corrupt JAR baseline 已作废并由
  A3 替代。
- P2：真实 PostgreSQL mapping overflow 未构造；由 deterministic unit fixture 覆盖，避免超过 21 亿行的不受控数据。
- P3：WSL runner 使用 Windows host disposable PostgreSQL，不等同于独立 Linux PostgreSQL service；JVM、PowerShell、 release
  root/POSIX 与 canonical helper 均在 Linux 执行，连接仅走 loopback。
- 未验证：生产部署后的 canonical pre-create、生产 management/PostgreSQL 只读结果、真实 credential/permission/IP allowlist、
  Attempt-10 start 与 168h acceptance。以上必须由后续独立授权任务处理。

## 10. Final decision and authority projection

结论：

```text
PASS /
INTERNAL_READBACK_ROOT_CAUSE_FIXED /
DISPOSABLE_LINUX_VALIDATION_PASSED /
RELEASE_CANDIDATE_STABILIZED /
COMMITTED /
CI_GREEN /
PRODUCTION_DEPLOYMENT_NOT_STARTED /
ATTEMPT_10_NOT_AUTHORIZED
```

Authority after：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION
work_batch_status=IMPLEMENTED|CI_GREEN|DISPOSABLE_LINUX_VALIDATION_PASSED
work_batch_commit=5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6
work_batch_ci_run=30576297678
next_action=NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW
Attempt-10=NOT_CREATED|NOT_AUTHORIZED
```

唯一下一动作：

```text
NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW
```

该 review 不授权生产部署、Attempt-10、OKX、credential、LIVE、交易写侧或 freeze/archive/tag。
