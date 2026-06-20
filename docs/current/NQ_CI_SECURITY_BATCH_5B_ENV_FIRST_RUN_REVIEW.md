# NQ CI Security Batch 5B-ENV First Run Review

任务：NQ-CI-SECURITY-BATCH-5B-ENV-FIRST-RUN-REVIEW
日期：2026-06-20
状态：**SUPERSEDED**。本文件第 1–10 节是 first-run review 当时（commit `0ef4dbbe` 尚无 target run）的历史结论，第 11 节记录 first run RED + fix-forward，均保留作记录。**当前权威状态见第 12 节：fix rerun GREEN（run `27876451289`），Batch 5B-ENV = FIX RERUN GREEN / READY FOR FREEZE（尚未 freeze）。**

## 1. Review decision

```text
BLOCKED
```

精确原因：当前 5B-ENV implementation commit `0ef4dbbeb769bf31a9efa768911ccc79b600383d` 在 GitHub Actions 中没有可评审 run。`gh run list --commit 0ef4dbbeb769bf31a9efa768911ccc79b600383d` 返回空数组；当前分支 `docs/ci-5b-env-plan-review` 的最近 GitHub Actions run 仍是旧 plan-review commit `266cffd9c82fcc515fb8b4a8f1265c06f6207a8f`，不是 5B-ENV implementation commit。

因此本轮不能确认 first green，不能把 5B-ENV 写成 accepted/frozen，不能允许进入 5B-ENV freeze。

## 2. Scope

- Repository：`E:\Project\nexus-quant`。
- Review target commit：`0ef4dbbeb769bf31a9efa768911ccc79b600383d` / `ci(security): add Batch 5B environment safety guard`。
- Allowed read scope：GitHub Actions run metadata、`.github/workflows/ci.yml`、5B-ENV guard/config/test/docs。
- Changed scope：docs-current only。
- Excluded：workflow、Java / TypeScript / Python code、migration、frontend、research、scripts、deploy、真实 `.env`、secret、credential、logs、dump、backup。

## 3. GitHub Actions evidence

| Query | Result | Decision impact |
| --- | --- | --- |
| `gh run list --commit 0ef4dbbeb769bf31a9efa768911ccc79b600383d --limit 20` | `[]` | No target first-run exists. |
| `gh run list --branch docs/ci-5b-env-plan-review --limit 20` | run `27838086804`, completed / success, headSha `266cffd9...` | Non-target historical plan-review run; cannot prove implementation. |
| `gh run list --branch dev --limit 10` | latest dev run `27838189279`, completed / success, headSha `a59d0bd...` | Non-target dev docs-plan run; cannot prove implementation. |

Target implementation run ID：**NONE / NOT FOUND**。
Target implementation run status：**NO RUN**。

## 4. Job review result

Because the target implementation run does not exist, the following job conclusions are **not reviewable** for commit `0ef4dbbe`:

| Job | Review result |
| --- | --- |
| diff-check | BLOCKED / NO TARGET RUN |
| no-outbound-guard | BLOCKED / NO TARGET RUN |
| backend | BLOCKED / NO TARGET RUN |
| postgres-flyway / repository / app context jobs | BLOCKED / NO TARGET RUN |
| frontend | BLOCKED / NO TARGET RUN |
| frontend-no-backend-e2e / Batch 5A | BLOCKED / NO TARGET RUN |
| research | BLOCKED / NO TARGET RUN |
| secret-scan | BLOCKED / NO TARGET RUN |

No target CI logs were available, so this review did not inspect job logs and did not read or print any secret material.

## 5. Static implementation review

Static read-only checks show the implementation intent is correctly bounded, but static checks cannot replace the missing CI first run.

- `.github/workflows/ci.yml` passes safe control env into `no-outbound-guard` and `backend`: `NQ_NO_OUTBOUND=true`, LIVE/AI/DH/real provider/real client/real exchange flags false, exchange endpoints placeholder-only.
- `no-outbound-guard` runs `NoOutboundExchangeGuardTest,EnvSafetyValidatorTest` and still checks forbidden credential env names plus denylist coverage.
- `EnvSafetyGuardConfiguration` wires a Spring `ApplicationRunner` startup guard and reports only variable names / conflict types, not secret values.
- `EnvSafetyValidatorTest` statically covers LIVE/no-outbound, CI/LIVE, CI/real provider-client, test/ci/paper real exchange, no-outbound real endpoint, CI/test credential material, AI/DH runtime, and placeholder safety.
- `application-ci.yml`, `application-test.yml`, and `application-paper.yml` keep no-outbound true and real provider/exchange false by default.
- `.env.example` uses placeholder-only values for exchange endpoints and credential material.
- Existing `NoRealExchangeCredentialPermissionProbePortTest` remains the no-real probe assertion for `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`; no real permission probe adapter is implemented.

## 6. Findings

| Severity | Finding | Impact | Required action |
| --- | --- | --- | --- |
| P0 | Target implementation commit has no GitHub Actions run. | Cannot accept first run; cannot enter freeze. | Trigger/push a run for commit `0ef4dbbe` or a successor commit, then rerun first-run review. |
| P1 | None beyond P0 blocker. | - | - |
| P2 | Static review cannot prove runtime CI behavior without target run logs. | Guard appears correctly wired, but first-run evidence is absent. | Re-review after target CI completes. |
| P3 | Last successful branch run is old plan-review SHA `266cffd9`, not implementation SHA. | Useful context only. | Do not cite it as 5B-ENV implementation evidence. |

## 7. Boundary confirmation

```text
Batch 5B-ENV = IMPLEMENTED / PENDING FIRST CI RUN
Batch 5B-ENV first-run review = BLOCKED / NO TARGET RUN
Batch 5B-SMOKE = STILL BLOCKED
No real credential read
No outbound call
No LIVE
No AI
No DH runtime
No RealClient
No real provider
No real exchange adapter
No real permission probe
```

## 8. Validation

| Command | Result | Notes |
| --- | --- | --- |
| git status --short | PASS | Docs-only changes: baseline plan, 5B-ENV plan, README, TESTING, WORKLOG, and this first-run review document. |
| gh run list --commit 0ef4dbbeb769bf31a9efa768911ccc79b600383d --limit 20 | [] | Blocking evidence. |
| gh run list --branch docs/ci-5b-env-plan-review --limit 20 | Latest branch run is 27838086804, success, old SHA 266cffd9... | Non-target. |
| git diff --check | PASS | exit 0; only LF/CRLF working-tree warnings. |
| git diff --stat | PASS | tracked docs-current diff only; new first-run review doc is untracked until staged. |
| git diff -- backend db migration pathspec | PASS | Empty. |
| git diff -- frontend research scripts deploy | PASS | Empty. |
| git diff -- .github/workflows/ci.yml | PASS | Empty; workflow unchanged in this review. |

## 9. Rollback

Docs-only rollback: revert this review document and the status entries added to current docs. No runtime rollback is required because no workflow/code/config/migration was changed in this review.

## 10. Next concrete action

Run GitHub Actions for implementation commit `0ef4dbbe` or a successor commit that contains the same 5B-ENV implementation, then rerun `NQ-CI-SECURITY-BATCH-5B-ENV-FIRST-RUN-REVIEW`. Do not start 5B-SMOKE before first-run review and freeze review pass.

## 11. First-run RED + fix-forward（2026-06-20 authoritative update）

任务：NQ-CI-SECURITY-BATCH-5B-ENV-FIRST-RUN-FIX

合入 `dev` 后 first run 实际已发生（不再是第 1–10 节所述的 "no target run"），结论为 RED：

| 项 | 事实 |
| --- | --- |
| dev HEAD | `2bb1248a`（5B-ENV merge + 文档收尾） |
| 失败 run | `27875157176`（HEAD `2bb1248a`，push: dev）；merge commit run `27875083681` 同样 RED |
| 失败 job | `Backend Maven test`、`No-outbound guard` |
| 通过 job | diff-check、postgres-flyway、frontend、frontend-no-backend-e2e、research、secret-scan |
| 失败测试 | `NoOutboundExchangeGuardTest.shouldRejectExchangeCredentialEnvWhenCiGuardIsRequired`（assert 行 91） |
| 断言信息 | `CI no-outbound guard forbids exchange credential/live env: NQ_LIVE_ENABLED` |

Root cause：`.github/workflows/ci.yml` 在 `no-outbound-guard` job 与 `backend` job 的 job-level `env:` 中注入了 `NQ_LIVE_ENABLED="false"` / `NQ_REAL_PROVIDER_ENABLED="false"` / `NQ_REAL_CLIENT_ENABLED="false"`。既有 `NoOutboundExchangeGuardTest` 在 CI-guard-required 模式下，将这三个变量名列为禁止以任何非空值存在的 exchange credential/live env（值为 `"false"` 同样违规）。`backend` job 因 `CI=true` 也进入该 guard 模式，故两个 job 同时 RED。`EnvSafetyValidatorTest` 本身 8/8 通过，不是回归来源。

Fix（fix-forward，最小且不削弱安全语义）：从上述两个 job 的 `env:` 中删除这三个变量名的注入；不修改、不放行 `NoOutboundExchangeGuardTest`。依据：`EnvSafetyGuardConfiguration` 对这些开关缺省即按 `false` 处理（absence => false），不注入不改变 5B-ENV 启动期 fail-closed 语义。`NQ_AI_ENABLED` / `NQ_DH_RUNTIME_ENABLED` / `NQ_REAL_EXCHANGE_ENABLED` 不在该测试禁止名单，保留注入。workflow 的 `forbidden_true_names` 校验步骤（"Verify no exchange credential env is injected"）保留，未削弱：变量未注入时其值非 `"true"`，校验仍通过。

本地验证：`mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NoOutboundExchangeGuardTest,EnvSafetyValidatorTest -Dnq.no-outbound.guard.required=true` 结果见 `docs/current/TESTING.md`。

当前权威边界：

```text
Batch 5B-ENV = IMPLEMENTED / FIRST RUN RED / FIXED LOCALLY / PENDING CI RERUN
Batch 5B-SMOKE = STILL BLOCKED
root cause = workflow injected env names forbidden by existing no-outbound guard
fix = remove forbidden env-name injections from workflow jobs, not relax test
No real credential read
No outbound call
No LIVE
No AI
No DH runtime
No RealClient
No real provider
No real exchange adapter
No real permission probe
```

下一步：push 本 fix commit，等待 GitHub Actions 重新对 `dev` 跑全绿后，才可把 5B-ENV first run 记为 GREEN，并据此另起 first-run review / freeze。CI 真实全绿前不得把 5B-ENV 写成 green 或 frozen。

## 12. Fix rerun GREEN（2026-06-21 authoritative update）

任务：NQ-CI-SECURITY-BATCH-5B-ENV-FIX-CI-RERUN-REVIEW

fix commit `8ba140d9 ci(security): fix 5B env guard workflow env conflict` 已 push 到 `dev`，触发的目标 rerun 全绿：

| 项 | 值 |
| --- | --- |
| run ID | `27876451289` |
| workflow | NQ CI Baseline |
| event | push |
| headSha | `8ba140d96d84b7e2ae5f379043779bfeb925e2fc`（== `dev` HEAD == `origin/dev`） |
| status / conclusion | completed / **success** |

8 个 job 结果（全部 success）：

| Job | 结果 |
| --- | --- |
| diff-check | success |
| no-outbound-guard | success（恢复） |
| backend | success（恢复） |
| postgres-flyway | success |
| frontend | success |
| frontend-no-backend-e2e (Batch 5A) | success |
| research | success |
| secret-scan | success |

测试证据（no-outbound-guard job，`-Dnq.no-outbound.guard.required=true`）：

- `EnvSafetyValidatorTest`：Tests run 8 / Failures 0 / Errors 0 / Skipped 0。
- `NoOutboundExchangeGuardTest`：Tests run 3 / Failures 0 / Errors 0 / Skipped 0 —— `shouldRejectExchangeCredentialEnvWhenCiGuardIsRequired` 实跑通过（非 skip），不再因 `NQ_LIVE_ENABLED` / `NQ_REAL_PROVIDER_ENABLED` / `NQ_REAL_CLIENT_ENABLED` 非空而失败。

确认：pushed `ci.yml` 中 `no-outbound-guard` 与 `backend` job 已不再注入这三个变量（仅保留为说明注释与 `forbidden_true_names` 断言名单）；workflow trigger `pull_request:[dev]` + `push:[dev]` + `workflow_dispatch` 保留；8 个 job 未删；未新增 GitHub secret。

当前权威边界：

```text
Batch 5B-ENV = FIX RERUN GREEN / READY FOR FREEZE
Batch 5B-SMOKE = STILL BLOCKED
target run = 27876451289 / success / headSha 8ba140d9
No real credential read
No outbound call
No LIVE
No AI
No DH runtime
No RealClient
No real provider
No real exchange adapter
No real permission probe
```

注：本轮只确认 rerun GREEN 并标 READY FOR FREEZE；**未 freeze**。进入 freeze 须另起 freeze 工作单（按既有 freeze review 流程固化 immutable green run + 冻结卷宗）。
