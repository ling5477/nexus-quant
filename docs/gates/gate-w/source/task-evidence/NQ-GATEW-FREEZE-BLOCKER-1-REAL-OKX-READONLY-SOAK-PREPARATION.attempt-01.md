# NQ-GATEW-FREEZE-BLOCKER-1-REAL-OKX-READONLY-SOAK-PREPARATION — Attempt 01

## 1. Task classification

- 类型：`EXCHANGE_INTEGRATION / SECURITY_VALIDATION / SOAK_HARNESS / CREDENTIAL_BOUNDARY_REVIEW`。
- 范围：NQ-only；只准备并验证真实 OKX private read-only soak 工具，不执行交易写侧，不修改 GateW authority。
- 当前结论：`PASS / HARNESS_LOCALLY_VALIDATED / READY_TO_COMMIT`（通过 / harness 已完成本地验证 / 可提交）。
- 尚未发生：harness commit、push、exact-head CI、独立固定 commit worktree、credential/IP hard gate、真实首样本与 168 小时 soak 均为 `PENDING / NOT_STARTED`（待执行 / 未开始）。

## 2. Credential boundary

- credential material 只从既有 `exchange_account_credentials` DB 密文与 `JdbcOkxPrivateCredentialExecutor` 解密路径读取；没有第二套 OKX secret 输入机制。
- supervisor/launcher 显式拒绝 `NQ_OKX_API_KEY`、`NQ_OKX_API_SECRET`、`NQ_OKX_API_PASSPHRASE` 及其 REAL 变体。
- metadata hard gate 要求 `venue=OKX`、`trade_env=LIVE`、账户/credential 均 `ACTIVE`、`permission_scope=READ_ONLY`、`withdraw_enabled=false`、`ip_allowlist_required=true`，并拒绝既有 permission/IP probe 的 `FAILED / SKIPPED` 状态。
- 真实 `GET /api/v5/account/config` 返回的 permission 必须精确归一化为仅 `READ_ONLY`；`ip` 空字符串按未绑定 IP fail-closed。代码只向上透传 `ipAllowlistConfigured` 布尔值，不保存或输出 IP 内容。
- 不记录 API key、secret、passphrase、签名、header、cookie、账户 ID、余额、持仓、订单、IP allowlist 内容或 raw provider response。

## 3. Isolated environment and kill-switch fixture

- 唯一 profile：`gatew-okx-readonly-soak`；附加 `local/default/prod` profile 均拒绝。
- DB URL 必须是 `jdbc:postgresql://localhost` 或 `127.0.0.1`，不得含 user-info/query/fragment，数据库名必须含 `gatew` 或 `soak`；连接后再次以 `current_database()` 与 `inet_server_addr()` 复核。
- Flyway 必须完整到 V35 且无 pending migration；未修改任何 migration。
- 除 Flyway、必要 identity/credential metadata 与 kill-switch control tables 外，全部业务表必须为空；任何非空业务表阻止 bootstrap/sample。
- `bootstrap` 仅在上述隔离边界通过后，用 test-support transaction 将 `GLOBAL_TRADING` 从 `ENGAGED` 置为 `DISENGAGED` 并追加事件；没有新增生产 disengage API、Controller、Actuator 或 scheduler。
- `engage` 不依赖 credential 解密；graceful/failure stop、elapsed、hard failure 与启动异常都回到既有 production `KillSwitchService.engage(...)`。bootstrap 后到 supervisor ownership 移交前由 `finally` 保证 fail-closed。

## 4. Endpoint allowlist

- 只允许 typed `GET /api/v5/account/config` 与 `GET /api/v5/account/balance`；method/path 由 `OkxPrivateReadOperation` 固定，supervisor 不接受 URL/path 参数。
- `CountingTransport` 在 delegate 前拒绝其他 private read operation，分类为 `FORBIDDEN_ENDPOINT_ATTEMPTED` 并立即 failure-stop。
- 复用既有 `JdkOkxPrivateReadTransport`：concurrency=1、无自动 retry、response cap 256 KiB、typed query、无通用 raw request。
- OKX 官方合同：`https://www.okx.com/docs-v5/en/#rest-api-account-get-account-configuration`；执行日读取 section SHA-256 为 `61a77182919fbe0d6eb23fc88f5f71f4fab37fe743bed9ae3bc97721522f220b`。官方字段语义：`perm` 区分 `read_only / trade / withdraw`，`ip` 空字符串表示未绑定 IP。

## 5. Harness architecture

- `GateWOkxReadonlySoakCycleTest`：默认 CI 跳过的 test-only 单周期 launcher；只支持 `bootstrap / sample / engage`，不启动 Spring context、不注册 Bean/API/scheduler/runner。
- `scripts/gatew/gatew-okx-readonly-soak.ps1`：支持 `start / status / resume / stop / failure-stop / evidence-verify / cleanup`；内部 `run-loop` 仅供隐藏 supervisor 子进程。
- `start` 强制 detached fixed commit worktree、clean tracked files、传入 exact-head CI run、10/10 jobs success，随后 bootstrap、真实首样本、后台 supervisor ownership 移交。
- 默认合同：`168` 小时、`900` 秒 cadence、单并发、瞬时网络/timeout/429/5xx 最多 2 次有界 retry、连续认证失败默认 3 次停止；stop request 最多约 5 秒被 supervisor 观察。
- `resume` 先验证 manifest、fixed commit 与完整 hash chain，只在 planned end 之前 append；不覆盖既有样本。
- `cleanup` 只删除 transient control/cycle 文件，保留 `manifest.json / samples.jsonl / failures.jsonl / heartbeat.json`；不自动 drop DB，不删除审计证据。

## 6. Evidence schema and integrity

- 未跟踪目录：`target/gatew-okx-readonly-soak/<runId>/`。
- `manifest.json` 只记录 run/commit/CI/time/cadence/profile、不可逆 DB/credential/host fingerprint、allowlist/Flyway/script version；不记录连接串、用户名或 credential material。
- `samples.jsonl` 每条仅包含任务允许字段，使用 `previousRecordHash + SHA-256 recordHash` 前向链；sequence 必须从 1 连续递增，duplicate/missing/tamper 均拒绝。
- `failures.jsonl` 只复制对应脱敏 sample；`heartbeat.json` 只记录状态、reason、sequence 和连续认证失败数。
- `final-summary.json` 由后续 acceptance 任务独占；本 harness 的 `evidence-verify` 若发现该文件会拒绝继续。

## 7. Files changed

- Production 最小安全例外：
  - `backend/nq-adapter-okx/src/main/java/**/JdkOkxPrivateReadTransport.java`
  - `backend/nq-adapter-okx/src/main/java/**/OkxPrivateReadError.java`
  - `backend/nq-adapter-okx/src/main/java/**/OkxPrivateReadResult.java`
  - `backend/nq-infra/src/main/java/**/OkxPrivateReadObservation.java`
  - `backend/nq-infra/src/main/java/**/OkxPrivateReadonlyProbeService.java`
- Test/harness：adapter/infra regression tests、`GateWOkxReadonlySoakCycleTest`、`GateWOkxReadonlySoakSupportTest`、PowerShell supervisor。
- Docs：本 preparation evidence、GateW evidence index、`TESTING.md`、`WORKLOG.md`。
- Production 例外理由：现有 transport 原先丢弃 `account/config.ip`，若 test-support 重做签名、HTTP 与 secret handling 会复制高风险实现；因此仅增加不可逆布尔事实与 fail-closed 分类，无自动装配、API、scheduler、依赖或写 endpoint。

## 8. Validation

| Command | Result | Scope / Environment |
| --- | --- | --- |
| focused Maven（3 suites） | `PASS` | `GateWOkxReadonlySoakSupportTest` 29/29；adapter/infra targeted suites 通过；无 real credential/network |
| required targeted Maven | `PASS` | 23/23 reactor modules `SUCCESS`；`BUILD SUCCESS`；`nq-app` 182 tests、0 failure/error、9 environment-gated skipped |
| full Maven | `PASS` | 23/23 reactor modules `SUCCESS`；`BUILD SUCCESS`；manual real soak 默认 skipped |
| PowerShell parser + supervisor self-test | `PASS` | 10 cases；hash chain、append-only resume、duplicate rejection、cleanup、no final summary、zero private network |

已知 warning：既有 SLF4J NOP、Mockito dynamic-agent/JDK future warning；P3，不阻断。前端/Python 无 diff，未运行。真实 PostgreSQL/OKX/credential/soak 尚未运行，不能写成 PASS。

## 9. Operations commands

以下命令只能在 harness commit exact-head CI GREEN 后创建的 detached worktree 中使用；credential 与 DB 配置必须通过本地安全环境提供，不得写入命令历史、对话或 evidence：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gatew/gatew-okx-readonly-soak.ps1 `
  -Action start -StartingCiRun <EXACT_HEAD_CI_RUN>

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gatew/gatew-okx-readonly-soak.ps1 `
  -Action status -RunId <RUN_ID>

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gatew/gatew-okx-readonly-soak.ps1 `
  -Action stop -RunId <RUN_ID>

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gatew/gatew-okx-readonly-soak.ps1 `
  -Action evidence-verify -RunId <RUN_ID>
```

## 10. Findings and limitations

- P0：0。
- P1：0（本地实现审查）；exact-head CI 与真实 hard gates 尚待执行，未伪写为通过。
- P2：0。
- P3：既有测试工具 warning；每个 cadence 通过 Maven test-only launcher 启动一个短生命周期 JVM，资源成本高但有界，未引入常驻 production runtime。
- 限制：无 credential 时返回 `BLOCKED / API_KEY_REQUIRED`；真实 permission 非只读返回 `BLOCKED / CREDENTIAL_PERMISSION_NOT_READONLY`；trade/withdraw metadata 不安全返回 `BLOCKED / UNSAFE_CREDENTIAL_PERMISSIONS`；IP allowlist 未证明返回 `BLOCKED / IP_ALLOWLIST_REQUIRED`。
- 本文不证明真实 OKX 可达、不证明 7-day soak PASS、不证明 GateW freeze ready/frozen/tagged。

## 11. Rollback

1. 若 supervisor 已运行，先执行 `stop` 并确认 heartbeat 为 `STOPPED / FAILURE_STOPPED / ELAPSED_PENDING_ACCEPTANCE` 且 kill switch 为 `ENGAGED`。
2. 执行 `evidence-verify`，保留审计 evidence；`cleanup` 只清理 transient control files。
3. 代码回滚使用后续明确 commit 的 `git revert <HARNESS_COMMIT>`；不得 `reset --hard`，不得删除历史 evidence。

## 12. Next action

精确提交并 push harness，等待其 exact-head `NQ CI Baseline` 10/10 jobs GREEN；然后创建 detached fixed-commit worktree。只有 credential metadata、真实 permission、IP allowlist、隔离 PostgreSQL V35、空业务表与首条 real read-only sample 全部通过时才允许报告 `SOAK_STARTED / SEVEN_DAY_ACCEPTANCE_PENDING`。期满后的唯一 acceptance 任务为 `NQ-GATEW-FREEZE-BLOCKER-1-REAL-OKX-READONLY-SOAK-ACCEPTANCE`。
