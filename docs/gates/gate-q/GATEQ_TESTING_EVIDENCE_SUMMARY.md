# GateQ Testing Evidence Summary

本文归档 GateQ freeze / release tag / archive 的测试与 CI 证据摘要。代码级验证以 GateQ readiness review 已接受证据、GateQ freeze closeout preflight CI 和本轮 release preflight 为准；本归档任务未新增后端、前端、Python、测试、migration 或 CI workflow。

## Release preflight evidence

| Evidence | Result | Notes |
| --- | --- | --- |
| Branch | `dev` | 本轮预检确认当前分支为 `dev`。 |
| Freeze closeout commit | `9c8cbfe740751a1896cd6afdd04d1b9141531b10` | Commit message: `docs(gateq): freeze GateQ validation baseline`。 |
| `origin/dev` alignment | PASS | 本轮预检确认 `HEAD` 与 `origin/dev` 对齐。 |
| Working tree before archive write | PASS / CLEAN | 本轮写入前 `git status --short` 无输出。 |
| Local tag precheck | PASS | 本轮写入前 `git tag --list "nq-gateq-freeze"` 无输出。 |
| Remote tag precheck | PASS | 本轮写入前 `git ls-remote --tags origin "refs/tags/nq-gateq-freeze*"` 无输出。 |
| Latest pre-archive CI | PASS / SUCCESS | GitHub Actions `NQ CI Baseline` run `28763029176`，`status=completed`、`conclusion=success`、`headSha=9c8cbfe740751a1896cd6afdd04d1b9141531b10`。 |

## GateQ readiness review accepted validation

| Command / Evidence | Result | Notes |
| --- | --- | --- |
| GitHub Actions run `28747045673` | PASS / SUCCESS | readiness review 前置 CI evidence；all jobs success。 |
| `mvn -f backend/pom.xml -pl nq-api,nq-core,nq-app -am test` | PASS / BUILD SUCCESS | readiness review 已接受；23 reactor modules success。 |
| `npm --prefix frontend run build` | PASS | readiness review 已接受；保留既有 Vite chunk size warning。 |
| `npm --prefix frontend run test:e2e -- tests/e2e/strategy-validation-paper-shadow-smoke.spec.ts --project=chromium` | PASS | 2 passed；覆盖 Strategy Validation / Paper Shadow Comparison / Evidence Matrix / forbidden wording。 |
| GateQ risk-word scan | REVIEWED / PASS | 命中按实现事实、历史证据、否定边界、测试断言和禁止项分类；未发现当前正向越界表达。 |

## Archive task validation intent

本归档任务必须执行并在最终报告中列出：

- `git diff --check`
- `git diff --stat`
- `git diff -- backend`
- `git diff -- frontend`
- `git diff -- research`
- `git diff -- scripts`
- `git diff -- deploy`
- `git diff -- .github`
- `git diff -- backend/**/db/migration`
- GateQ / GateR / LIVE / AI / DH / real provider / credential / trading 关键词语义分类扫描
- staged diff checks
- commit / push / annotated tag / remote tag verification

## Not rerun in archive task

- 未重跑 Maven。
- 未重跑 frontend build。
- 未重跑 Playwright。
- 未重跑 Python `pytest` / `mypy` / `ruff`。

原因：本轮是 docs-only release tag and archive，不修改 `backend/**`、`frontend/**`、`research/**`、测试、migration、workflow 或 runtime 配置。代码级验证以前置 readiness review 与 CI success 为依据；tag push 前后的 Git / CI / remote tag 状态以最终命令证据为准。

## Boundary

本轮未调用真实交易所，未读取 credential material，未启动 Shadow Live runner，未创建 shadow run，未写真实账户、资金、订单或 ledger 状态，未开启 LIVE、AI runtime 或 DH runtime，未实现 RealClient、real provider、private trading adapter 或 real permission probe。
