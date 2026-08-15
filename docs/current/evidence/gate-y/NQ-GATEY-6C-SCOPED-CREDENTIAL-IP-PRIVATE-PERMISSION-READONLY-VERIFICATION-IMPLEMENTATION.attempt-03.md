# GateY-6C scoped credential/IP/private permission read-only verification implementation — attempt-03

## Task classification

- 类型：`CODE_CHANGE / CREDENTIAL_SECURITY_BOUNDARY / OKX_PRIVATE_READONLY_VERIFICATION / PERMISSION_POLICY_IMPLEMENTATION`（代码变更 / 凭证安全边界 / OKX 私有只读验证 / 权限策略实现）。
- 归属：NQ-only / GateY-6C；风险等级 L。
- 日期：2026-08-15（Asia/Shanghai）。
- 结果：`BLOCKED / API_KEY_REQUIRED`（阻断 / 缺少可用 API key）；本地代码和回归已通过，真实远端验证未执行，machine authority 不变。

## Starting baseline

- branch=`dev`，起始 worktree/staged=`clean/empty`。
- `HEAD == origin/dev == 9d1f32f3d1a0789866879b98784ebe49fa54f29d`。
- exact-head CI=`31863332915 / completed / success`。
- authority checker=`PASS / CURRENT_AUTHORITY_CONSISTENT`。
- accepted batch=`GateY-6B / ACCEPTED|CI_GREEN`；work batch=`GateY-6C / NOT_STARTED / NONE / NOT_RUN`。
- LIVE=`DISABLED`，kill switch=`ENGAGED`，real provider/private trading=`NOT_IMPLEMENTED / NOT_IMPLEMENTED`。

## Existing implementation reuse

本轮复用现有 `CredentialPermissionProbeService`、`ExchangeCredentialPermissionProbePort`、`OkxRealReadonlyPermissionProbePort`、`JdbcOkxPrivateCredentialExecutor`、typed `OkxPrivateReadRequest`、signer、GET-only transport、`OkxSpotEndpointGuard`、controller/API DTO 和 Spring composition。没有新增第二套 credential store、secret resolver、signer、HTTP client、raw private transport 或 permission-probe framework。

本轮未修改 `JdbcOkxPrivateCredentialExecutor`、signer、HTTP transport、raw account/config parser、Spot provider contract 或 worker；credential material 仍只允许在既有 JDBC JIT callback 内短暂存在并在退出时清理。

## Permission-policy design

- 新增强类型 `CredentialPermissionExpectation`，只允许 `READ_ONLY_DIAGNOSTIC` 与 `GATEY_PILOT_READINESS`。
- 请求 mode 严格映射：`PAPER` / `READ_ONLY_DIAGNOSTIC` → GateW；`GATEY_PILOT_READINESS` → GateY。未知或空白之外的非 allowlist 值在 transaction、credential lookup 和 network 之前 fail closed。
- GateW policy 保持：`READ required / TRADE forbidden / WITHDRAW forbidden / IP MATCHED required`。
- GateY policy 独立实现：`READ required / TRADE required / WITHDRAW forbidden / IP MATCHED required`。
- transient result 与脱敏 audit metadata 增加 `readPermissionDetected`、`tradePermissionDetected`、`withdrawPermissionDetected`、`permissionExpectation`、`inherentOkxTradePermissionResidual`；DB 仍只持久化既有 `permissionScope`，migration=`0`。
- 未发生 remote probe 的 `SKIPPED` audit 将 `withdrawPermissionDetected` 固定为 `false`；本地 `withdrawEnabled` 风险仍通过 `WITHDRAW_ENABLED_RISK` policy decision 表达，避免把本地治理字段误写成远端检测事实。
- GateY 成功时 residual 固定反映 OKX `TRADE` permission 固有包含 funding-transfer capability；不得输出 `transferCapabilityAbsent=true`。

## Spring composition and safety gates

- 默认 context 仍装配 `NoRealExchangeCredentialPermissionProbePort`。
- GateW profiles 绑定 `READ_ONLY_DIAGNOSTIC`；`scoped-okx-private-readonly` 绑定 `GATEY_PILOT_READINESS`；GateW/GateY profile 冲突时回落 `NoReal`。
- 所有 probe 继续要求 `dryRun=true`、显式人工调用、exact profile/flags 和只读 typed endpoint。
- startup probe=`0`，scheduler probe=`0`。
- `SpotExecutionProviderPort` production transport=`0`，worker real-provider binding=`0`，real mutation reachability=`0`。
- NQ `FUNDS_MOVEMENT=DENY`：provider/worker 无 transfer/withdraw operation；raw/arbitrary private path不可表达；typed endpoint policy default deny。Remote `TRADE` capability不等于NQ application、`FIRST_REAL_ORDER`、micro-live或LIVE授权。

## Credential availability assessment

- 未读取 `.env`，未读取 `encrypted_payload`，未解密 credential，未输出 API key、secret、passphrase、signature、header、raw response、UID payload或数据库连接凭证。
- 当前进程对 datasource、credential master key、expected IP 与受控 profile 没有显式安全注入；只检查环境变量是否存在，没有读取或输出其值。
- 对本地 PostgreSQL 只执行一次 metadata-only `SELECT`，列严格限于 `owner_user_id`、`exchange_account_id`、`credential_id`、`credential_type`、`credential_status`、`is_active`、`exchange_code`、`trade_env`、account status；查询条件限定 active `OKX / LIVE / OKX_API_V5` exact candidate。
- metadata query 结果=`0 rows`；exact credential reference=`NOT_AVAILABLE`，credential material access/exposure=`0/0`。
- 因 exact credential 不存在，expected IP 与可用 secure runtime 也未建立，受控 remote probe 的全部前置条件不成立。

## Remote verification decision

- Remote READ=`NOT_VERIFIED / API_KEY_REQUIRED`。
- Remote TRADE=`NOT_VERIFIED / API_KEY_REQUIRED`。
- Remote WITHDRAW=`NOT_VERIFIED / API_KEY_REQUIRED`；不得把本地测试写成 remote absent。
- IP binding=`NOT_VERIFIED / API_KEY_REQUIRED`。
- `INHERENT_OKX_TRADE_PERMISSION_RESIDUAL=ACKNOWLEDGED`（合同与实现已显式承认；不是 remote permission 通过证据）。
- `NQ_FUNDS_MOVEMENT=DENIED`（本地 code/test boundary）。
- `REMOTE_ACCOUNT_IDENTITY=NOT_VERIFIABLE`；本轮不新增 raw UID 持久化、migration或第二套 identity SoR。
- authenticated endpoint=`NONE`；`GET /api/v5/account/config` count=`0`；retry=`0`；OKX call=`0`。
- mutation total=`0`；PLACE/CANCEL/transfer/withdraw/borrow/leverage/derivatives=`0/0/0/0/0/0/0`。

## Files and modules

新增：

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/CredentialPermissionExpectation.java`
- `docs/current/evidence/gate-y/NQ-GATEY-6C-SCOPED-CREDENTIAL-IP-PRIVATE-PERMISSION-READONLY-VERIFICATION-IMPLEMENTATION.attempt-03.md`

修改：

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/CredentialPermissionProbeRequestBody.java`
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountModuleConfiguration.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/ExchangeCredentialPermissionProbeRequest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/ExchangeCredentialPermissionProbeResult.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeServiceTest.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/probe/OkxRealReadonlyPermissionProbePort.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/NoRealExchangeCredentialPermissionProbePortTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/probe/OkxRealReadonlyPermissionProbePortTest.java`
- `docs/current/evidence/gate-y/README.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

模块：`nq-api`、`nq-app`、`nq-core`、`nq-infra` 与最小 current evidence；frontend、research、migration、CI、deploy、governance、hard-gate manifest、`STATUS.md` 均不修改。

## Validation

| Command / check | Result | Scope / environment / warnings |
| --- | --- | --- |
| focused account/probe/API/Spring tests | PASS（通过） | core=`16`、infra=`8`、API=`7`、Spring=`9`；failures/errors=`0/0` |
| GateW permission regression | PASS（通过） | READ-only pass；TRADE/WITHDRAW fail；GateW policy 未被 GateY 放宽 |
| GateY permission/IP policy tests | PASS（通过） | READ+TRADE pass；missing READ/TRADE、WITHDRAW、unknown/malformed permission 与非 MATCHED IP 均 fail closed |
| `scripts/gatey/tests/run-gatey4-deployment-boundary-regression.ps1` | PASS（通过） | no-start/no-secret/no-network boundary 保持 |
| GateY-6B + ArchUnit regression | PASS（通过） | core=`9`、adapter=`28`、app/architecture=`24`；failures/errors=`0/0` |
| `mvn -f backend/pom.xml test` | PASS（通过） | 最终 audit 语义修正后完整重跑；23/23 modules `BUILD SUCCESS`；failures/errors=`0/0`；`nq-app=281 tests / 27 skipped`；1:02 min（修正前同命令也曾于 59.547s 通过） |
| `mvn -f backend/pom.xml -pl nq-core "-Dtest=CredentialPermissionProbeServiceTest" test` | PASS（通过） | 最终审计语义修正后重跑；16/16，failures/errors/skipped=`0/0/0`，`BUILD SUCCESS` |
| metadata-only PostgreSQL query | PASS / ZERO CANDIDATE（通过 / 无候选） | exit=`0`，active OKX LIVE exact credential rows=`0`；未选择敏感列 |
| remote authenticated probe | NOT RUN / BLOCKING（未运行 / 阻断） | exact credential、expected IP 与 secure runtime 不可用；不得以本地测试替代 |

已知非产品失败：第一次 focused Maven 调用因 PowerShell 未引用 `-Dsurefire...` 参数，exit=`1` 且未进入编译；参数引用修正后全部通过。最终审计语义修正新增 JSON assertion 后，第一次 core 重跑因测试方法未声明 `JsonProcessingException` 在 `testCompile` exit=`1`；最小增加 `throws Exception` 后再次重跑 16/16 通过。IDE terminal 因 PowerShell executable path 引号解析返回 `Illegal char <\">`，测试降级到工作区 PowerShell，结果可信度高，因为同一仓库、同一 JDK/Maven 且最终全量与 focused rerun 均通过。

## Findings

- P0：无。
- P1：无代码级 open finding；remote READ/TRADE/WITHDRAW/IP 事实因 API key 缺失不可验证，属于任务完成 blocker，不能降级为本地 PASS。
- P2：无。
- P3：无。

## Authority, boundary and final decision

- authority after 保持 GateY-6C=`NOT_STARTED / NONE / NOT_RUN`；`STATUS.md` 不修改。
- next action 保持 `NQ-GATEY-6C-SCOPED-CREDENTIAL-IP-PRIVATE-PERMISSION-READONLY-VERIFICATION-IMPLEMENTATION`。
- `FIRST_REAL_ORDER=NOT_AUTHORIZED`，micro-live=`NOT_AUTHORIZED`，LIVE=`DISABLED`，kill switch=`ENGAGED`。
- real provider/private trading=`NOT_IMPLEMENTED / NOT_IMPLEMENTED`。
- 最终结论：`BLOCKED / API_KEY_REQUIRED / LOCAL_POLICY_IMPLEMENTED / FULL_BACKEND_GREEN / REMOTE_READ_NOT_VERIFIED / REMOTE_TRADE_NOT_VERIFIED / REMOTE_WITHDRAW_NOT_VERIFIED / IP_BINDING_NOT_VERIFIED / OKX_TRADE_TRANSFER_RESIDUAL_ACKNOWLEDGED / NQ_FUNDS_MOVEMENT_DENIED / GATEW_POLICY_PRESERVED / NO_SECRET_EXPOSURE / NO_EXCHANGE_MUTATION / REAL_PROVIDER_NOT_IMPLEMENTED / PRIVATE_TRADING_NOT_IMPLEMENTED / FIRST_REAL_ORDER_NOT_AUTHORIZED / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED`。

## Rollback and next action

- 未 commit/push/stage。回滚只需恢复本文件所列 product/test/docs diff，并删除两个新增文件；不得使用破坏性 Git 命令覆盖其他改动。
- credential 应由既有安全写侧/JIT path 注入，不能从聊天提供、不能读取 `.env` 明文。exact LIVE OKX credential 与 expected egress IP 安全配置完成后，重跑同一 GateY-6C task；先复核 full backend、default NoReal、mutation unreachable 与 transport diff，再最多调用一次 `GET /api/v5/account/config`，retry=`0`。
- 只有 remote READ+TRADE、WITHDRAW absent、IP matched 全部通过后，才可把 work batch 改为 `IMPLEMENTED|PENDING_REVIEW` 并进入独立 Security Review。
- 建议未来 commit：`feat(gatey): verify scoped OKX pilot credential readiness`；当前 blocker 未关闭，不建议提交为 GateY-6C 完成。

## Attempt-04 runtime addendum

后续 attempt-04 的真实隔离启动暴露一个明确的 composition 缺陷：`scoped-okx-private-readonly` profile 下仍会注册 `OkxRecoveryService`。依据“真实 probe 暴露明确实现缺陷时才允许继续修改 Java”的边界，已对该 service 增加 `@Profile("!scoped-okx-private-readonly")`，并在 Spring context test 中断言 scoped profile 不注册 recovery/scheduler bean。该变更不新增 provider、worker、scheduler、mutation endpoint 或 startup probe。

- `OkxRecoveryServiceTest`：`2/2` PASS（通过）。
- `OkxPrivateReadOnlyPermissionProbeSpringContextTest`：`10/10` PASS（通过）。
- 合计：`12 tests / 0 failures / 0 errors`。
- 用于隔离 runtime 的构建 artifact SHA-256：`cd54c3e7ba1953b333ae7d8c57528cb3ceebda57495e68ed229c4eb37af36ca3`。
- attempt-03 的 full backend green 证据保持有效；attempt-04 未重复执行 full backend。
