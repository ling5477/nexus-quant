# GateY-6C scoped credential/IP/private permission read-only verification Security Review — attempt-01

## Task classification

- 类型：`INDEPENDENT_SECURITY_REVIEW / CREDENTIAL_BOUNDARY_REVIEW / REAL_PRIVATE_READONLY_CONFORMANCE_REVIEW / SECRET_EXPOSURE_INCIDENT_REVIEW / RUNTIME_REACHABILITY_REVIEW`（独立安全审查 / 凭证边界审查 / 真实私有只读符合性审查 / 秘密暴露事件审查 / 运行时可达性审查）。
- 归属：NQ-only / GateY-6C；风险等级 L。
- 日期：2026-08-15（Asia/Shanghai）。
- 结论：`REJECTED / P1_OPEN / AUTHORITY_UNCHANGED`（审查拒绝 / 存在未关闭 P1 / authority 不变）。代码、no-mutation 边界与全部本地回归通过，但辅助 NQ 管理密码终端回显事件没有证明 durable residual 已清除，不能判定 `CLOSED / ROTATED_AND_CONTAINED`。

## Starting baseline and authority before

- repository=`E:\\Project\\nexus-quant`，branch=`dev`。
- `HEAD == origin/dev == 9d1f32f3d1a0789866879b98784ebe49fa54f29d`；staged=`0`；`git diff --check` exit=`0`，仅有既有 LF→CRLF 工作区提示。
- accepted batch=`GateY-6B / ACCEPTED|CI_GREEN`。
- work batch=`GateY-6C / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。
- LIVE=`DISABLED`，kill switch=`ENGAGED`，real provider/private trading=`NOT_IMPLEMENTED / NOT_IMPLEMENTED`。
- dirty paths 全部属于 attempt-03/04 的 backend implementation/test 与 current evidence；migration/frontend/research/CI/governance contract/hard-gate manifest diff 均为 0。

## Implementation diff reviewed

- `CredentialPermissionExpectation` 只定义 `READ_ONLY_DIAGNOSTIC` 与 `GATEY_PILOT_READINESS`；`PAPER` 仅映射到前者，未知值在 transaction、credential lookup 与 network 前 fail closed。
- Spring composition 将 GateW profiles 绑定到 `READ_ONLY_DIAGNOSTIC`、将 `scoped-okx-private-readonly` 绑定到 `GATEY_PILOT_READINESS`；两类 profile 同时存在或都不存在时回落 `NoReal`。
- `OkxRealReadonlyPermissionProbePort` 同时校验 request expectation 与 profile-bound expectation；GateW 为 READ required / TRADE forbidden / WITHDRAW forbidden，GateY 为 READ required / TRADE required / WITHDRAW forbidden；未知 permission、非 MATCHED IP 与 scope mismatch 均失败。
- GateY 成功结果与 audit 显式记录 `inherentOkxTradePermissionResidual=true`，没有把 remote TRADE 固有 funding-transfer capability 隐藏或表述为 absent。
- `OkxRecoveryService` 的唯一变化是 `@Profile("!scoped-okx-private-readonly")`；scoped diagnostic context 不注册 recovery bean，其他 profile 与默认 context 的原行为未扩大。
- IDE error inspection 对 8 个变更 production Java 文件均返回 errors=`0`。

## Remote verification evidence reviewed

- 未复制或读取 raw OKX response、authenticated header、signature 或 credential material，也未重新发送 remote probe。
- attempt-04 的脱敏 persisted summary 记录 exact metadata：owner/account/credential=`2/1/1`、exchange/tradeEnv/type/status=`OKX/LIVE/OKX_API_V5/ACTIVE`，writeback=`SUCCEEDED / TRADE / withdraw=false / IP PASSED / failedAuth=0 / error=NULL`。
- audit 聚合记录 STARTED/SUCCEEDED=`1/1`、FAILED/SKIPPED=`0/0`，成功 metadata 为 GateY expectation、READ/TRADE=true、WITHDRAW=false、residual=true；实现与测试字段逐项一致。
- Remote READ=`VERIFIED`；TRADE=`VERIFIED`；WITHDRAW=`ABSENT`；IP=`MATCHED`；real OKX call=`1`；retry=`0`。
- Exchange mutation total=`0`；PLACE/CANCEL/TRANSFER/WITHDRAW/other mutation=`0/0/0/0/0`。
- 当前已配置的只读 PostgreSQL 连接对 exact metadata 查询返回 0 行，不能证明它是 attempt-04 目标数据库；本 review 不把该结果解释为远端事实反证。目标 persisted facts 的独立重查询不可用，记录为证据来源限制。

## Permission expectation review

| Policy | READ | TRADE | WITHDRAW | IP |
| --- | --- | --- | --- | --- |
| GateW `READ_ONLY_DIAGNOSTIC` | required | forbidden | forbidden | MATCHED required |
| GateY `GATEY_PILOT_READINESS` | required | required | forbidden | MATCHED required |

- policy 由 enum、profile-bound constructor 与 adapter classification 收敛，不存在散落 if/string 构成的第二套隐式状态机。
- client 只能提交 allowlisted mode；不能构造 enum 之外的 expectation，且 mode 与 active profile policy 不一致时 adapter 在 credential access 前拒绝。
- GateW READ-only success 与 TRADE/WITHDRAW rejection 回归通过；GateY success 不构成 trading authorization。

## Funds-movement containment

- `NQ_FUNDS_MOVEMENT=DENIED`。
- `OkxPrivateReadOperation` 只有编译期封闭的 GET operation；没有 transfer/withdraw operation，`/api/v5/asset/transfer` 未加入 allowlist。
- `OkxPrivateReadRequest` 不允许调用方提供 host、path、method、body 或任意 query map；transport 固定 GET、无自动 retry，endpoint guard 对 FUNDS_MOVEMENT 与 PRIVATE_MUTATING 永久 deny。
- `SpotExecutionProviderPort` / reviewed worker 不提供 funds-movement operation；未发现 reflection、generic execute 或 raw/arbitrary private escape hatch。
- 正确结论为 `REMOTE_CAPABILITY_EXISTS + NQ_RUNTIME_UNREACHABLE`，不是 `REMOTE_TRANSFER_CAPABILITY_ABSENT`。

## Credential/JIT review

- credential material 仍只在既有 `JdbcOkxPrivateCredentialExecutor` 同步 JIT callback 内出现；session 绑定 owner thread，callback 后失效，credential char arrays 在 finally 清零。
- core/domain/API response 不携带 secret；raw response/authenticated headers/signature 不离开 transport；decrypted payload 不持久化；异常只返回固定分类；retry=`0`。
- 未新增第二套 resolver/store/signer/client。changed diff/evidence 中 `apiKey`、`secretKey`、`passphrase`、`privateKey`、`Authorization`、`OK-ACCESS-SIGN`、`decrypted_payload`、`encrypted_payload` 命中均为字段名、否定性边界或测试断言；未发现真实值。
- OKX credential material exposure=`0`。

## Management password incident

- Exposure channel：普通 PowerShell 终端误输入并回显一次；不是 OKX credential material。
- Rotation：attempt-04 仅以脱敏事实证明再次轮换后 `bcryptShape=true`、`enabled=true`、roles=`3` 与更新时间；本 review 未读取旧密码、新密码或 hash 内容。
- Repository/evidence：changed paths、untracked evidence 与目标 artifact filename/reference 的脱敏扫描未发现密码值或 incident-specific artifact；但这不足以证明所有 durable location 已清理。
- Durable residual：`NOT PROVEN ABSENT`。现有证据未逐项证明 terminal transcript、PowerShell history、application log、process arguments、request capture、screenshot、CI/shared artifact、browser local/session storage 中无残留。本轮又明确禁止重新读取 credential material，因此不能用可能再次暴露密码的方式补做内容检查。
- Incident disposition：`OPEN / CONTAINMENT_NOT_PROVEN`；不得写为 `CLOSED / ROTATED_AND_CONTAINED`。

## OkxRecoveryService and Spring composition review

- scoped diagnostic profile 下 recovery bean、startup recovery 与 scheduler recovery 均为 0；focused Spring test 通过。
- `@Profile("!scoped-okx-private-readonly")` 只减少 scoped diagnostic runtime 的既有 side effect，不新增 private API、scheduler、order/cancel/reconciliation mutation 或 execution provider binding。
- default context 仍选择 `NoRealExchangeCredentialPermissionProbePort`；显式且完整的 scoped context 才选择 real GET-only probe。GateW/GateY profile 冲突、flags 不完整、expected IP 非 literal 或 executor 非既有 JDBC implementation 均回落 NoReal。
- real execution provider binding=`0`，worker real-provider binding=`0`，LIVE enable=`0`，kill disengage=`0`。

## Control-plane write review

- credential bootstrap、probe IN_PROGRESS/result 与 credential audit 是合法 NQ control-plane 写入，不计入 exchange mutation。
- prepare/claim 与 finalize 分别由短事务承载；probe port 调用位于两个 transaction callback 之间。
- `permission_probe_status <> IN_PROGRESS` 与 `permission_probe_status = IN_PROGRESS` CAS 阻止并发重复 probe；当前同步、无 takeover/lease 的状态模型中只有持有唯一 IN_PROGRESS 的调用能够 finalize。writeback conflict 不返回 success。
- remote failure 没有 permission observation 时保留最后已知 permission scope、withdraw 与 IP 风险事实；audit metadata 只包含脱敏 enum/boolean/ID，不包含 material。

## Validation

| Command / check | Result | Scope / warnings |
| --- | --- | --- |
| focused GateY-6C/GateW/API/Spring/recovery Maven | PASS（通过） | core/infra/scheduler/API/app=`16/8/2/7/10`，合计 43 tests，failures/errors/skipped=`0/0/0` |
| `scripts/gatey/tests/run-gatey4-deployment-boundary-regression.ps1` | PASS（通过） | delegate-release/linux-root/identity/no-start/no-secret/no-network |
| GateY-6B provider/readiness + ArchUnit Maven | PASS（通过） | adapter provider=`14`；app/readiness/ArchUnit=`24`；failures/errors=`0/0` |
| `mvn -f backend/pom.xml test` | PASS（通过） | 23/23 modules `BUILD SUCCESS`；1484 tests，failures/errors/skipped=`0/0/45`；52.929s |
| changed production Java IDE inspection | PASS（通过） | 8 files，errors=`0` |
| current authority checker | PASS（通过） | review 前 errors=`0`；authority 保持 pending review |
| remote OKX probe | NOT RUN（未运行） | 按任务禁止重跑；本 review 的 real OKX call increment=`0` |

IDE terminal 因 PowerShell executable path 引号解析返回 `Illegal char <">`，命令未进入 Maven；随后降级到同一工作区 PowerShell，所有 focused 与 full-backend 命令均真实执行并通过。artifact 目录存在若干既有、与本任务无关且拒绝访问的旧 pip 临时目录，因此 artifact 内容扫描并非全仓完备；目标 task 名称/端口/incident reference 未在可访问 artifact 中命中。

## Findings

### P0

- 无。

### P1

- `AUXILIARY_MANAGEMENT_PASSWORD_CONTAINMENT_UNPROVEN`：证据只证明一次普通终端回显和后续轮换，没有证明 durable residual 已清除。触发位置见 attempt-04 lines 26-30；该缺口直接违反本 review 的 incident close 条件，阻断 acceptance 与 authority 更新。

### P2

- `TARGET_PERSISTED_FACTS_REQUERY_UNAVAILABLE`：当前只读 PostgreSQL 连接不是可确认的 attempt-04 目标数据库，exact query 返回 0 行；remote permission 事实只能由脱敏 attempt evidence、实现与测试交叉支持，缺少本轮可重复的目标 DB 聚合查询。不得因此重跑 OKX probe。

### P3

- 无。

## Authority after, decision and next action

- authority after 保持：accepted batch=`GateY-6B / ACCEPTED|CI_GREEN`；work batch=`GateY-6C / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。
- next action 保持 `NQ-GATEY-6C-SCOPED-CREDENTIAL-IP-PRIVATE-PERMISSION-READONLY-VERIFICATION-SECURITY-REVIEW`；不得进入 commit/push。
- review decision：`FAIL / GATEY_6C_SECURITY_REVIEW_REJECTED / P0_0 / P1_1 / MANAGEMENT_PASSWORD_INCIDENT_CONTAINMENT_NOT_PROVEN / AUTHORITY_UNCHANGED / NOT_READY_TO_COMMIT`。
- remediation：不得读取或复制密码；由有权限的 operator 在安全边界外完成 terminal transcript/history/log/artifact residual 清理与失效确认，只提交不含密码内容的脱敏 containment attestation；同时提供目标 DB 的 allowlisted、read-only、脱敏 summary/audit 聚合证据。完成后重跑本 Security Review，仍不得重跑 OKX probe。
- commit recommendation 保留为未来通过后使用：`feat(gatey): verify scoped OKX pilot credential readiness`；当前不建议 stage/commit。

