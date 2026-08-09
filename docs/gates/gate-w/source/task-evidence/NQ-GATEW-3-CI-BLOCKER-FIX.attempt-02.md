# NQ-GATEW-3-CI-BLOCKER-FIX Attempt 02

## Task classification

`CI_BLOCKER_FIX + CURRENT_AUTHORITY_RECONCILIATION + FRONTEND_TEST_TOOLING_FIX + REGRESSION_TESTS + TASK_EVIDENCE`。

结论：`IMPLEMENTED / PENDING_REVIEW`（已实现 / 待独立复核）。本 evidence 不表示 fix 已 commit/push，不表示 GitHub Actions 已恢复，也不表示 GateW-3 已 accepted。

## Preflight and failed CI fact

- Repository / branch：`F:\project\nexus-quant / dev`。
- Starting `HEAD == origin/dev == eff79d7c7ea1b034de4e77c7ec64974c247027f5`；worktree clean、staged empty。
- Preview implementation subject：`feat(trading): add limit-only dry-run order preview`。
- Failed exact-head CI：`NQ CI Baseline` run `29308652349 / completed / failure / headSha=eff79d7c7ea1b034de4e77c7ec64974c247027f5`。
- 实际 jobs 为 10：9 success、1 failure。唯一失败为 `Frontend backend E2E smoke / Run adapter readiness backend E2E`。

失败日志证明 Vite 请求绑定 `127.0.0.1:51888`，端口占用后自动切换至 `51889`，而 runner 继续轮询 `http://127.0.0.1:51888`，120 秒后 timeout。未发现第二个独立 CI failure；未 rerun 旧 job。

## Failed-authority catch-up

既有 high-risk governance contract 允许显式 reconciliation：

```text
REVIEW_ACCEPTED|READY_TO_COMMIT
→ COMMITTED|CI_FAILED|FIX_REQUIRED
```

本轮使用 `authorityCatchUp=true`，把 `work_batch_commit` 从 `UNCOMMITTED` 追赶到 `eff79d7c7ea1b034de4e77c7ec64974c247027f5`，把 `work_batch_ci_run` 从 `NOT_RUN` 追赶到 `29308652349`。`accepted_batch` 保持 GateW-2 / `ACCEPTED|CI_GREEN`，GateW 保持 `IN_PROGRESS|NOT_FROZEN`。preview implementation review 继续有效，但 GateW-3 尚未 accepted。

## Runner implementation

- 新增 `run-e2e-support.mjs`，使用 Node.js 内置 `net` 在固定 host `127.0.0.1` 上申请 ephemeral port；无第三方 port dependency。
- 同一 configuration 同时生成 Vite `--port`、wait URL 与 Playwright `E2E_BASE_URL`；不存在固定 `51888` 或 fallback `51889`。
- Vite args 固定包含 `--host 127.0.0.1 --port <selectedPort> --strictPort`。
- 可选 `E2E_BASE_URL` 只接受显式 `http://127.0.0.1:<1..65535>/`；非 loopback、HTTPS、credential、缺失/越界 port、path/query/hash 均 fail-closed。
- `waitForServerOrChildExit` 同时监听 server readiness、child `error` 与 child `exit`；Vite ready 前退出立即失败，不继续等待 120 秒。
- cleanup 固定为 `SIGTERM`、5 秒有界等待、必要时 `SIGKILL`、再 5 秒有界等待；cleanup failure 不会被 Playwright success 掩盖。
- `SIGINT` / `SIGTERM` 会终止当前 Vite/Playwright child；normal success、startup failure、Playwright failure 与 timeout 均进入 finally cleanup。
- runner 只输出 selected loopback endpoint 与 strict-port enabled；不输出完整 env、credential、header 或 backend response。

## Validation evidence

| Validation | Result | Scope / evidence |
| --- | --- | --- |
| `npm ci` | PASS | 现有 lockfile 安装成功；dependency/devDependency 与 `package-lock.json` 无 diff |
| Frontend build | PASS | `CI=true; npm run build`；Vite 8.0.3；3904 modules transformed |
| Runner unit tests | PASS | `node --test tests/e2e/run-e2e-support.test.mjs`；10/10 pass |
| Playwright version | PASS | `1.58.2` |
| Controlled 51888 no-backend regression | PASS | dummy 持续占用 51888；runner 选择 23595；Vite/Playwright 同端口；login smoke 1 passed；全部监听清理 |
| Controlled 51888 backend regression | PASS | dummy 持续占用 51888；local nq-app 18888 health UP；runner 选择 12255；backend smoke 1 passed；全部监听清理 |
| Real strict-port negative | PASS | 显式占用 51888 后 Vite 输出 `Port 51888 is already in use`；runner 0.35 秒内以 code 1 fail-fast；未执行 Playwright |
| Targeted Maven | PASS | `mvn -f backend/pom.xml -pl nq-core,nq-risk,nq-infra,nq-app -am test`；23/23 modules SUCCESS；02:42 |
| Full Maven | PASS | `mvn -f backend/pom.xml test`；23/23 modules SUCCESS；02:03 |
| Failed-authority governance | PASS | lifecycle、next-action、current authority、current links 均通过 |
| Static / forbidden scope | PASS | `git diff --check`；lock/config/spec/backend/frontend source/workflow/research/deploy/migration/archive/skills diff=0 |

Maven 固定 `CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`。local backend 使用 repo local profile 与本机 PostgreSQL；未读取 `.env`、未连接生产/共享远端数据库、未访问 OKX、未执行真实 network business call。

## Validation RCA retained

1. 首次 frontend build 读取既有 stale TypeScript incremental state 并报 `TS5101`；执行 `tsc -b --clean` 后原始 `npm run build` PASS，tracked config 无 diff。
2. 首次 backend start 显式注入 CI-only DB identity，被本机 PostgreSQL 以 SQLState `28P01` 拒绝；未猜测或读取本机 credential，进程全部清理。
3. disposable Docker PostgreSQL 内部 ready，但 Windows host 到随机 published port 返回 SQLState `08001`；容器、backend、dummy 全部清理。随后使用 repo local profile 原生本地配置完成真实 backend E2E。

## Immutable evidence and boundary

开始时对 `docs/current/evidence/gate-w/` 既有 20 份 evidence body 计算 SHA-256；本任务只新增 attempt-02 fix/review evidence，并按要求更新 index README。结束前必须再次复核既有 evidence body hash 与 diff lines。

`NO PREVIEW BUSINESS CHANGE / NO CONTROLLER / NO REST API / NO MIGRATION / NO NETWORK BUSINESS CALL / NO OKX HTTP / NO PRIVATE ENDPOINT / NO CREDENTIAL READ / NO BALANCE FETCH / NO ORDER SUBMISSION / NO ORDER CANCELLATION / NO ORDER STATE CHANGE / NO LEDGER WRITE / NO AUDIT WRITE / NO RISK MUTATION / NO LIVE / NO SHADOW ENABLE / NO DH / NO AI`。

## Findings and next action

- P0：0。
- P1：0。
- P2：既有 Spring local/test startup 会在原始 Maven log 输出随机 generated development password；本 evidence 不记录其值，本轮 frontend tooling allowlist 不授权 backend/logging 修复。
- P3：既有 Vite chunk-size、Ant Design React 19 compatibility/deprecated prop、Maven settings、Mockito/SLF4J warning；均非本轮 blocker。

结果：`IMPLEMENTED / PENDING_REVIEW`。下一动作：`NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW`。
