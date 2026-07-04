# GateP Testing Evidence Summary

本文归档 GateP release tag and archive 使用的测试、CI 和本轮文档归档验证证据。

## Latest CI evidence

| Field | Value |
| --- | --- |
| Workflow | `NQ CI Baseline` |
| Run id | `28714258374` |
| Branch | `dev` |
| Event | `push` |
| Status | `completed`（已完成） |
| Conclusion | `success`（成功） |
| Head SHA | `3650714ae9cd441e59eb5b09c605a14bbc9998dc` |
| Head commit | `chore(gatep): freeze baseline and stabilize research quality gate` |
| Created at | `2026-07-04T17:36:21Z` |
| Updated at | `2026-07-04T17:38:19Z` |

## GateP prior validation evidence

| Area | Evidence | Result |
| --- | --- | --- |
| Backend Batch 2 | `mvn -f backend/pom.xml -pl nq-api,nq-core,nq-app -am test` | BUILD SUCCESS；Marketdata quality service/controller tests 通过。 |
| Frontend Batch 3 | `npm --prefix frontend run build` and targeted Playwright smoke | Build PASS；3 smoke tests passed after test assertion fix。 |
| Backend Batch 4 | targeted preflight service/controller tests and scoped backend reactor | BUILD SUCCESS；fail-closed read-only baseline verified。 |
| Python Batch 5 | `python -m pytest research/py`; `python -m ruff check research/py`; `python -m mypy research/py` | pytest 10 passed；ruff all checks passed；mypy success。 |
| Freeze baseline stabilization | GitHub Actions run `28714258374` | success，headSha matches release tag target。 |

## This archive turn validation scope

本轮是 docs-only release tag and archive closeout。已执行的验证重点是 Git、tag、CI evidence、文档 diff 和 forbidden-scope diff。

本轮未复跑：

- `mvn -f backend/pom.xml test`。
- `npm run build`。
- `npm run test:e2e`。
- `python -m pytest -q` / `python -m mypy src` / `python -m ruff check .`。

原因：本轮不修改 backend / frontend / research / scripts / deploy / `.github` / migration，不新增 API、页面、测试、CI workflow 或 migration；最新 release tag target 已有 `NQ CI Baseline` success 证据。

## Boundary validation

本轮 archive validation 必须保留以下检查结果：

- `git diff -- backend`：应为空。
- `git diff -- frontend`：应为空。
- `git diff -- research`：应为空。
- `git diff -- scripts`：应为空。
- `git diff -- deploy`：应为空。
- `git diff -- .github`：应为空。
- `git diff -- "backend/**/db/migration"`：应为空。

若后续任何一项出现非空 diff，不能把本归档结论复用为通过，必须单独复核是否越界。
