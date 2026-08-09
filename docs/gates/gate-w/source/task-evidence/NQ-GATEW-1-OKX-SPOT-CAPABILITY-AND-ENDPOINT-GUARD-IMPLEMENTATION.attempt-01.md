# NQ-GATEW-1-OKX-SPOT-CAPABILITY-AND-ENDPOINT-GUARD-IMPLEMENTATION Attempt 01

## Task Metadata

- Task ID：`NQ-GATEW-1-OKX-SPOT-CAPABILITY-AND-ENDPOINT-GUARD-IMPLEMENTATION`。
- Attempt：`01`。
- Task classification：`BACKEND_IMPLEMENTATION / ADAPTER_BOUNDARY / ENDPOINT_POLICY / FAIL_CLOSED_GUARD / TESTS / TASK_EVIDENCE`。
- Task ownership：`NQ-only`。
- Execution date：`2026-07-13`（Asia/Shanghai）。
- Branch：`dev`。
- Starting HEAD / origin-dev：`5661a13e236ce067edad9ae5789c97ae3ae2e7bb`，二者一致。
- Starting worktree / staged：均为空。

## Authority Before

```text
accepted_batch=GateV-FREEZE
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-PLAN
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-PLAN-COMMIT-AND-PUSH
```

安全状态保持：LIVE `DISABLED`、Shadow trading `NOT ENABLED`、AI `NOT STARTED`、DH runtime `NOT INTEGRATED`、Integration runtime `NOT STARTED`、RealClient/real provider/private trading adapter/real permission probe `NOT IMPLEMENTED`。

## Baseline and Preflight

- `git fetch origin` 后，branch=`dev`、worktree clean、staged empty、`HEAD == origin/dev`。
- 当前 HEAD 的 `NQ CI Baseline`：run `29199785253`，`completed / success`。
- `test-governance-workflow-lifecycle.ps1`、`test-current-authority-next-action.ps1`、`check-current-authority.ps1`：pre-edit 均 PASS。
- 实际工作目录为 `F:\project\nexus-quant`；任务文字中的 `E:\Project\nexus-quant` 未被用作执行目录。

## Files and Runtime Paths Inspected

- 规则/authority：`AGENTS.md`、`CLAUDE.md`、root/current README、`GATEW_PLAN.md`、`STATUS.md`、`API.md`、`ARCHITECTURE.md`、`MODULES.md`、`TESTING.md`、`WORKLOG.md`、`GOVERNANCE_WORKFLOW.md`。
- Adapter contract：`AdapterCapability`、`TradingAdapter`、`DefaultAdapterReadinessService`、public marketdata policy/categories 与 guarded adapter factory。
- OKX：`OkxExchangeAdapter`、`OkxHttpClient`、`OkxRuntimeConfig`、`OkxHistoricalKlineAdapter`、`OkxInstrumentsCache`、`OkxPermissionProbeBoundary`、现有 no-real/bootstrapping tests。
- Spring assembly：`ExchangeAdapterConfiguration`、`LocalTestFallbackConfiguration`、public marketdata outbound configuration、app readiness/smoke tests。
- 其他指定模块：`nq-core`、`nq-infra`、`nq-app`、`nq-risk`；未读取 `.env`、key/pem、secrets 或 credential files。

## Existing Inventory

| 分类 | 实际审计结果 | GateW-1 处理 |
| --- | --- | --- |
| Public marketdata | `OkxHistoricalKlineAdapter` / `OkxInstrumentsCache` 保持既有 public path；default public outbound 仍 no-egress，只有 manual profile + explicit flag 可创建 public client。 | 未改 public adapter；guard 只允许两个既有 public GET path 进入后续 public policy。 |
| Private read | 历史 `OkxExchangeAdapter` query/fill path 走 authenticated client；不是 GateW read-only candidate contract。 | matrix 标为 contract-only / runtime disabled；private allowlist 为空。 |
| Private mutating | 历史 `OkxExchangeAdapter` 包含 place/cancel；app 默认以 readiness guard fail-closed。 | `gatew` profile 不注册 mutating trading Bean；guard 永久拒绝。 |
| Funds movement | 本轮未发现需要复用的 GateW adapter path。 | `TRANSFER` / `WITHDRAW` 固定 funds-movement deny。 |
| NoReal/Fake/Test | `DefaultAdapterReadinessService`、Noop/Fake/Stub 与 bootstrap tests 均为无 IO fail-closed。 | 未标为 real-ready；原有行为未改。 |
| Historical spike | `OkxPermissionProbeBoundary` 是未接入 GateW runtime 的历史 classifier/test-only 组件。 | 不将其作为新 guard；记录为 P2 follow-up。 |

## Implemented Contract

- adapter API：`ExchangeCapability`、`EndpointAccessClass`、`EndpointGuardReason`、`EndpointPolicyDecision`。
- OKX Spot：`OkxSpotCapabilityDefinition`、`OkxSpotCapabilityMatrix`、`OkxSpotEndpointGuard`。
- matrix 字段：`capability`、`endpointClass`、`implemented`、`runtimeEnabled`、`credentialRequired`、`networkRequired`、`tradingAuthorization`、`reasonCode`。
- `EndpointPolicyDecision` 与 matrix definition 强制 `tradingAuthorization=false`；能力存在、public policy allow、测试通过均不表示交易授权。

## OKX Spot Capability Matrix

| Capability | Endpoint class | Implemented | Runtime enabled | Credential / network | Decision |
| --- | --- | --- | --- | --- | --- |
| `PUBLIC_MARKET_DATA` | `PUBLIC_READ` | true | false（default no-egress） | false / true | `ALLOW_PUBLIC_READ` 仅通过 endpoint policy，仍由 public runtime profile/flag 决定。 |
| `PRIVATE_ACCOUNT_CONFIGURATION_READ` | `PRIVATE_READ_ONLY` | false | false | true / true | `DENY_PRIVATE_RUNTIME_DISABLED` |
| `PRIVATE_ACCOUNT_BALANCE_READ` | `PRIVATE_READ_ONLY` | false | false | true / true | `DENY_PRIVATE_RUNTIME_DISABLED` |
| `PRIVATE_PERMISSION_READ` | `PRIVATE_READ_ONLY` | false | false | true / true | `DENY_PRIVATE_RUNTIME_DISABLED` |
| `ORDER_PREVIEW_LOCAL` | `LOCAL_ONLY` | false | false | false / false | `DENY_UNKNOWN_ENDPOINT`（尚未实现） |
| `ORDER_SUBMISSION` / `ORDER_CANCEL` | `PRIVATE_MUTATING` | false | false | true / true | `DENY_MUTATING_ENDPOINT` |
| `TRANSFER` / `WITHDRAW` | `FUNDS_MOVEMENT` | false | false | true / true | `DENY_FUNDS_MOVEMENT` |
| `UNKNOWN` | `UNKNOWN` | false | false | false / false | `DENY_UNKNOWN_ENDPOINT` |

所有行的 `tradingAuthorization=false`。

## Endpoint Policy and Guard Decision

- default deny；未登记 capability、空值、blank、非法 URI、scheme/authority/fragment、编码 path、反斜线和 dot segment 均拒绝。
- public 仅精确匹配既有 `/api/v5/public/instruments` 与 `/api/v5/market/history-candles`，method 必须为 `GET`；不使用 contains 匹配。
- query string 不参与分类；大小写和 duplicate slash 先规范化，不能将 private path 伪装为 public；percent-encoded path 直接拒绝。
- private read：`DENY_PRIVATE_RUNTIME_DISABLED`；private mutating：`DENY_MUTATING_ENDPOINT`；transfer/withdraw：`DENY_FUNDS_MOVEMENT`；unknown/local symbolic operation：`DENY_UNKNOWN_ENDPOINT`。
- guard 无 HTTP client、credential、signer、Spring 或环境读取依赖；不执行 network request。

## Spring and Profile Boundary

- 默认/local/test 不改变既有 app 装配；现有 `DefaultAdapterReadinessService` 继续无 IO fail-closed。
- 显式 `gatew` profile 排除 `okxTradingAdapter`、`binanceTradingAdapter`、`okxWsClient`、`binanceWsClient` 与会构造 public HTTP client 的 `OkxHistoricalKlineAdapter`，因此不注册 mutating trading Bean、private WebSocket Bean 或自动 HTTP client。
- GateW-1 不新增 `@Scheduled`、`ApplicationRunner`、`CommandLineRunner`、自动探活、自动 credential 解密或 permission probe。

## Tests and Validation

| Command / evidence | Result | Notes |
| --- | --- | --- |
| Initial reactor command | FAIL then fixed | `OkxSpotCapabilityMatrix` static/instance helper name collision；仅编译错误，无 IO/credential/trading side effect。重命名为 `buildDefinitions()` 后重跑。 |
| `mvn -f backend/pom.xml -pl nq-adapter-api,nq-adapter-okx,nq-app -am test` | PASS | 23-module reactor success；`nq-adapter-api` 81 tests、`nq-adapter-okx` 46 tests、`nq-app` 134 tests（4 configured skips）。新增 matrix/guard tests 全部通过。 |
| `mvn -f backend/pom.xml test` | PASS | 23/23 modules SUCCESS；总耗时 8m10s。既有 configured skips 保持；无 failures/errors。 |
| 最终源码后的全量重试：`mvn -q -f backend/pom.xml test` | 环境阻断，未作为源码结果 | 共享 Maven 本地仓库正被另一工作区 Maven 写入，解析阶段发生 `FileAlreadyExistsException`；reactor tests 尚未开始。未改动 Maven、依赖或仓库缓存。 |
| 离线回退：`mvn -o -ntp -f backend/pom.xml test` | 环境不适用，未作为源码结果 | 当前 mirror 缓存缺少 `spring-boot-dependencies:3.5.10` 的 offline metadata；不下载依赖、不改 settings。 |
| `mvn -f backend/pom.xml -pl nq-adapter-api,nq-adapter-okx -am test` | PASS | 补强 GateW profile 后的 5-module reactor success；`nq-adapter-api` 81 tests、`nq-adapter-okx` 47 tests。直接模块级复查未带 `-am` 时因本地未安装上游 `nq-adapter-api` 产物不可解析，未作为验证结论；已改用本行的正确 reactor 命令复验。 |
| `test-governance-workflow-lifecycle.ps1` | PASS | lifecycle 与 task-evidence policy fixture 通过。 |
| `test-current-authority-next-action.ps1` | PASS | canonical action regression 通过。 |
| `check-current-authority.ps1` | PASS | post-edit authority 一致。 |
| `git diff --check` / forbidden scope diff | PASS | 收尾复查无 whitespace error，禁止范围无 diff。 |

Full Maven 的既有 local Spring integration test 对 `localhost:5432/nexus_quant` 应用了仓库已存在的 V33；本任务没有新增、修改或执行自定义 migration，未访问生产数据库。

## Findings

- P0：0。
- P1：0。
- P2：`OkxPermissionProbeBoundary` 是历史 classifier，未接入 GateW typed guard；后续 GateW-2 不得复用其字符串 containment 判断，必须沿 typed matrix/guard 与经官方复核的 private allowlist 实现。
- P3：Maven 使用的全局 settings 输出既有 `profiles` 标签 warning；不影响 BUILD SUCCESS，本任务不修改全局 Maven 配置。

## Not Implemented and Boundary Confirmation

- 未实现 real permission probe、private read client、credential access/decryption、private endpoint allowlist、account/balance snapshot、order preview、reconciliation、kill switch/soak。
- 未新增 HTTP API、migration、依赖、scheduler、runner、Live/Shadow/AI/DH/Integration runtime。
- 未调用真实 OKX，未读取/解密 credential，未提交订单、撤单、转账、提现，未修改真实账户、订单、资金或 ledger。
- public allow 与 `PASS` 不构成 `ready to trade`、`authorized`、`LIVE` 或真实 provider permission。

## Rollback

未 stage、commit 或 push。回滚代码/文档时仅删除本 attempt 新增文件并以反向 patch 恢复本轮修改；不得使用 `git reset --hard`，不得改 tag、archive、migration 或凭证。

## Authority After

```text
accepted_batch=GateW-PLAN
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-1
work_batch_status=IMPLEMENTED|SELF_REVIEWED
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-1-COMMIT-AND-PUSH
```

## Final Decision and Next Action

`IMPLEMENTED / SELF_REVIEWED / READY_TO_COMMIT`（已实施 / 已自审 / 可进入提交前复核）。P0=0、P1=0；唯一下一动作为 `NQ-GATEW-1-COMMIT-AND-PUSH`。GateW-2 未初始化。
