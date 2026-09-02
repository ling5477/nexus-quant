# GateAUDIT Phase5B Exact-Head CI Remediation Attempt-02

## 1. 基线与失败证据

- starting SHA：`377c0484a483ba08fadca12b29a56ade65e060ef`
- failed run：`33610423976 / workflow_dispatch / completed / failure`
- final jobs：`7 success / 1 failed / 1 skipped`
- failed job：`Frontend build and critical E2E`
- failed step：`Run loopback critical E2E allowlist`
- failed spec：`frontend/tests/e2e/validation-review-workbench-smoke.spec.ts`
- failed test：原 line 327 的 monolithic loading/empty/error/permission/404/owner-boundary test
- failed assertion：原 line 366 的 Idempotency-Key fail-closed UI assertion
- CI result：`17 passed / 1 failed`；单 test 在默认 30 秒 budget 到期后关闭 page。

## 2. 修改前隔离与 RCA

使用 loopback Vite、Chromium、`CI=true` 和 fresh browser context，单独建立 validation review fixture，仅执行 Idempotency-Key fail-closed 业务路径。

首次临时 fixture 直接进入 queue URL，因未显式打开 detail Drawer 而在业务动作前找不到“确认已阅”；这证明原 monolithic test 的 Idempotency-Key 段隐式继承了前一段 unknown-state 场景的 detail URL。修正临时 fixture 为显式 `reviewCaseId` navigation 后：

- run 1：`PASS / 1380 ms / POST=0`
- run 2：`PASS / 1328 ms / POST=0`
- run 3：`PASS / 1348 ms / POST=0`

RCA classification：`MONOLITHIC_E2E_TEST_BUDGET_EXHAUSTION`。

产品 fail-closed 行为独立稳定，未发现 production correctness defect；修复范围保持 test-only。

## 3. Semantic scenario inventory

拆分前 monolithic test 包含 8 个语义组：

1. OPERATOR loading 状态与 owner filter 隐藏
2. empty queue
3. queue API error
4. queue permission denied
5. detail 404
6. invalid reviewCaseId 在请求前拒绝
7. unknown state 不展示动作
8. Idempotency-Key 生成失败 fail-closed，且 POST=0

拆分后仍为上述 8 个语义组，每组拥有独立 Playwright test、独立 `seedReviewWorkbench`、显式 navigation 和独立 audit assertions。

- before semantic scenarios：`8`
- after semantic scenarios：`8`
- missing：`0`
- weakened：`0`
- deleted：`0`
- target spec test count：`4 → 11`
- loopback allowlist test count：`18 → 25`
- critical spec files：仍为 `5`（loopback `3`、real-backend `2`）

## 4. Remediation constraints

- strategy：`STRUCTURAL_SPLIT`
- production frontend changes：`0`
- backend/deployment/workflow/config changes：`0`
- timeout changes：`0`
- retry changes：`0`
- skip/fixme changes：`0`
- Idempotency-Key exact UI assertion：保留
- Idempotency-Key no-POST assertion：保留

## 5. Validation

### Target scenario

Command：canonical E2E runner + Chromium + exact title grep + `--repeat-each=3`。

- result：`3/3 PASS`
- failed：`0`
- skipped：`0`
- reporter：`CRITICAL_E2E_NO_SKIP executed=3 status=passed`
- total elapsed：`9.424 seconds`

### Entire target spec

Command：canonical E2E runner + Chromium + `validation-review-workbench-smoke.spec.ts --repeat-each=3`。

- result：`33/33 PASS`
- failed：`0`
- skipped：`0`
- reporter：`CRITICAL_E2E_NO_SKIP executed=33 status=passed`
- total elapsed：`70.239 seconds`

### Canonical loopback allowlist

- expected specs：`3`
- executed specs：`3`
- executed cases：`25`
- failed：`0`
- skipped：`0`
- fixme：`0`
- reporter：`CRITICAL_E2E_NO_SKIP executed=25 status=passed`
- total elapsed：`56.732 seconds`

### NoSkipReporter

- clean synthetic fixture：`PASS`
- skipped synthetic fixture：`REJECTED / CRITICAL_E2E_SKIP_FORBIDDEN`

### Build and governance boundary

- `npm run build`：`PASS`
- canonical delivery workflow positive validator：`PASS`
- critical spec binding：`5`
- capabilities：`ownership=24 / missing=0 / unknown=0`
- `.github/workflows/ci.yml`、`scripts/ci/**`、`scripts/deployment/**`：`NO DIFF`

## 6. Focused review and candidate

- focused independent review：`PASS / PHASE5B_CRITICAL_E2E_TIMING_REMEDIATION_FOCUSED_REVIEW_ACCEPTED`
- P0：`0`
- P1：`0`
- P2：`0`
- P3：`0`
- scope drift：`0`
- decision：`READY_TO_COMMIT`
- functional candidate files：`1`
- candidate fingerprint：`0bd34ca66d3619d3c230398c9a99e4f2df4d8009a3d4045fa8ba65511c6363d8`
- exact-head CI：`PENDING_FORWARD_COMMIT_AND_WORKFLOW_DISPATCH`

P5-F002 / P5-F003 在 9/9 exact-head CI green 前继续保持 `PENDING / EXACT_HEAD_CI_NOT_GREEN`。
