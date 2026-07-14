# NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW Attempt 02

## Task classification

`INDEPENDENT_CONFORMANCE_REVIEW + FRONTEND_TEST_TOOLING_SECURITY_REVIEW + PROCESS_LIFECYCLE_REVIEW + REGRESSION_VALIDATION + TASK_EVIDENCE`。

结论：`PASS / CI_BLOCKER_FIX_ACCEPTED / READY_TO_COMMIT`（通过 / CI blocker 修复已接受 / 可进入提交前复核）。本 review 与 implementation 检查清单分离，重新读取真实 diff、运行负向 strict-port 场景并复核全部 allowlist/forbidden scope；不表示 GitHub CI 已 green。

## Review target

代码/tooling 实际 4 路径：

```text
frontend/tests/e2e/run-e2e.mjs
frontend/tests/e2e/run-e2e-support.mjs
frontend/tests/e2e/run-e2e-support.test.mjs
frontend/package.json
```

Current-control/evidence 实际 10 路径：

```text
docs/current/STATUS.md
docs/current/README.md
docs/current/ROADMAP.md
docs/current/GATEW_PLAN.md
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/FACT_SOURCE_INDEX.md
docs/current/evidence/gate-w/README.md
docs/current/evidence/gate-w/NQ-GATEW-3-CI-BLOCKER-FIX.attempt-02.md
docs/current/evidence/gate-w/NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW.attempt-02.md
```

最终候选范围为 14 路径；仅精确暂存实际修改文件。`package-lock.json`、Playwright/Vite config、backend E2E spec、preview implementation 与全部 forbidden areas 无 diff。

## Conformance review

1. 默认 endpoint 由 Node.js `net` 在 `127.0.0.1` 申请合法 ephemeral port；无固定 51888/51889、外部 host、random remote URL 或第三方 dependency。
2. `createRunnerConfiguration` 是 endpoint 单一事实源；Vite args、wait URL、Playwright `E2E_BASE_URL` 完全一致。
3. Vite args 包含 `--strictPort`；真实占用端口时 Vite 不自动切换，runner 在 0.35 秒内由 child exit fail-fast。
4. `E2E_BASE_URL` parser 只接受 loopback HTTP + explicit valid port + `/`；credential、query/hash/path、HTTPS、localhost、0.0.0.0 与公网/test-net IP 均拒绝。
5. readiness 与 Vite `error/exit` 竞争；ready 前 exit 不会等待完整 timeout。server ready 后才启动 Playwright。
6. finally cleanup 对 normal success、startup failure、Playwright non-zero、timeout 与 signal 均生效；SIGTERM / bounded wait / SIGKILL fallback 无无界等待，cleanup failure 不改写为 success。
7. package script 使用 `node --test ... && node ...`；runner unit test failure 会阻止 E2E。
8. 无 `continue-on-error`、retry-as-pass、skip E2E、fallback 51889、E2E spec 放宽或 backend/frontend business change。

## Validation and evidence

- Runner unit tests：10/10 PASS，覆盖 dynamic allocation、valid override、non-loopback、credential、missing/illegal port、single Vite port、`--strictPort`、local server ready、Vite early exit、Playwright/Vite baseURL equality。
- Controlled 51888 backend E2E：dummy 51888 + local nq-app 18888 同时在线；selected 12255；backend smoke `1 passed (9.1s)`；51888/18888/12255 最终均无 listener。
- Targeted/full Maven：两条要求命令均 23/23 reactor modules SUCCESS、`BUILD SUCCESS`。
- Frontend：`npm ci`、clean-state `npm run build`、Playwright 1.58.2 均通过；lockfile/config diff=0。
- Governance：failed catch-up 与 review-state current authority 的 lifecycle、next-action、authority、link checks 均通过。
- Immutable preview evidence：attempt-02 security/risk、implementation attempt-01、implementation-review attempt-01 未修改。

## Findings

- P0：0。
- P1：0。
- P2：既有 Spring local/test generated development password 会出现在原始 Maven output；属于 out-of-scope log hygiene residual，不影响本 runner contract，但后续日志任务应脱敏。
- P3：既有 Vite chunk-size、Ant Design compatibility/deprecation、Maven settings、Mockito/SLF4J warning；不扩 scope。

## Boundary confirmation

`NO PREVIEW BUSINESS CHANGE / NO CONTROLLER / NO REST API / NO MIGRATION / NO NETWORK BUSINESS CALL / NO OKX HTTP / NO PRIVATE ENDPOINT / NO CREDENTIAL READ / NO BALANCE FETCH / NO ORDER SUBMISSION / NO ORDER CANCELLATION / NO ORDER STATE CHANGE / NO LEDGER WRITE / NO AUDIT WRITE / NO RISK MUTATION / NO LIVE / NO SHADOW ENABLE / NO DH / NO AI / NO COMMIT YET / NO PUSH YET`。

## Authority after review

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-3
work_batch_status=COMMITTED|CI_FAILED|FIX_REQUIRED
work_batch_commit=eff79d7c7ea1b034de4e77c7ec64974c247027f5
work_batch_ci_run=29308652349
next_action=NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH
```

## Final decision

`PASS / CI_BLOCKER_FIX_ACCEPTED / READY_TO_COMMIT`。推荐 commit message：`fix(ci): coordinate Vite E2E port dynamically`。

下一动作：`NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH`。
