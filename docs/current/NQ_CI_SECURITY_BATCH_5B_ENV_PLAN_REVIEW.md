# NQ CI Security Batch 5B-ENV Plan Review

任务：NQ-CI-SECURITY-BATCH-5B-ENV-PLAN-REVIEW
日期：2026-06-20
分支：docs/ci-5b-env-plan-review
评审对象：commit `266cffd9`（`docs(ci): plan Batch 5B environment security boundary`）
状态：**PASS / ACCEPTED**。

> 本文是 review-only 结论。本轮不实现 env guard，不修改 workflow，不改 Java / TypeScript / Python 代码，不新增 API，不新增 migration，不启动 5B-SMOKE，不读取真实 credential，不做真实外联。

---

## 1. Review Decision

```text
NQ-CI-SECURITY-BATCH-5B-ENV-PLAN-REVIEW：PASS / ACCEPTED
```

- Batch 5B-ENV plan = **ACCEPTED / READY FOR IMPLEMENTATION**。
- Batch 5B-ENV implementation = **NOT STARTED**。
- Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**。
- 允许进入后续 **5B-ENV implementation**，但必须另起实现批次，且不得把本 review 解释为 workflow / runtime guard 已落地。

---

## 2. Scope Reviewed

只读核验：

- `docs/current/NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/README.md`
- `docs/current/ROADMAP.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `.github/workflows/ci.yml`
- `backend/nq-app/src/main/resources/application*.yml`
- 根目录 `.env.example` 与 `frontend/.env.example`
- `research/py/pyproject.toml`

未读取真实 `.env`、secret、credential、logs、dump、backup；未做 HTTP 探活；未调用真实交易所。

---

## 3. Findings

P0 = 0。P1 blockers = 0。

P1 风险成立，但已被正确作为 5B-ENV implementation 前置项处理，不阻塞 plan freeze：

- **P1 成立：无统一 `ci` / `paper` profile。** 当前 `application.yml` 默认 `NQ_PROFILE:local`，现有 profile 为 `local` / `test` / `prod` / `freeze` / `gated-verify`，未发现统一 `ci` / `paper` profile。计划要求 5B-ENV-A/B 收口 profile/provider 隔离，方向正确。
- **P1 成立：无运行态 env 冲突 fail-closed。** 当前 no-outbound 主要由 CI env-name 校验与 test-scope guard 覆盖；未发现已落地的 `LIVE=true` + `NO_OUTBOUND=true` runtime fail-closed。计划把该项列为 5B-ENV-B implementation prerequisite，方向正确。

P2 均成立，且不阻塞 plan acceptance：

- **P2 成立：real base-url 默认值存在误导风险。** `application.yml` 默认 `nq.okx.base-url=https://www.okx.com`；根目录 `.env.example` 含 OKX / Binance real REST 与 WS URL。它们不是 credential，但容易被误读为 real provider 已启用。计划要求 CI/test 不加载 real base-url，并由 denylist 覆盖 host，处理方向正确。
- **P2 成立：no-outbound 仍偏 test-scope + env-name 校验。** `.github/workflows/ci.yml` 已有 `no-outbound-guard` job 与 denylist；计划承认它尚不是完整网络层 egress block，并把 runtime context smoke no-outbound assertion 留给 5B-ENV-C，边界清楚。
- **P2 成立：占位符标记不统一。** 根目录 `.env.example` 使用 `REPLACE_WITH_LOCAL_*`，secret-scan allowlist 仍包含 `REPLACE_WITH_LOCAL` / `CHANGE_ME` / `FAKE-PLACEHOLDER`；计划要求 5B-ENV-A 决定统一迁移或追加 `DO_NOT_COMMIT_REAL_VALUE` / `PLACEHOLDER_ONLY`，可作为 implementation 输入。

P3 = accepted residual，不阻塞：

- Batch 5A 状态在 `NQ_CI_BASELINE_PLAN.md` 局部仍残留 `IMPLEMENTED / READY FOR FIRST-RUN` 旧措辞；本 review 已在允许的 status update 中收口为 `FROZEN / ACCEPTED`。
- 5B-ENV 计划中的控制变量多为新增规范，尚未落地；这正是后续 implementation 的范围，不能在本轮写成 implemented。

---

## 4. Required Review Points

| 检查项 | 结论 |
| --- | --- |
| 当前 CI/env 状态盘点 | 准确；workflow 8 jobs、CI secret 注入、backend profile、frontend no-backend E2E、research env、root `.env.example` 均与只读核验一致。 |
| env 分层 baseline | 可接受；CI required / local dev / test / forbidden env 分层清楚，且明确多数变量为后续新增规范。 |
| profile/provider 隔离 | 可接受；`ci` / `paper` 为新增目标，real provider 仍被限定到未来单独 safety proposal。 |
| no-outbound 规划 | 可接受；承认当前 guard 边界，后续补 context smoke/runtime assertion，不宣称已实现 egress block。 |
| secret/log/artifact redaction | 对齐 Batch 4；沿用 secret-scan、pre-upload redaction gate、日志不打印 secret 的 fail-closed 口径。 |
| 5B-SMOKE 前置条件 | 正确；必须等 5B-ENV-A..E 完成并 freeze，5B-SMOKE 继续 BLOCKED。 |
| 后续批次压缩 | 可接受；执行线可压缩为 5B-ENV implementation -> 5B-ENV first run/fix -> 5B-ENV freeze -> 5B-SMOKE implementation -> CI final freeze。 |
| 越界描述 | 未发现把 5B-ENV 写成 implemented、未发现启动 5B-SMOKE、未发现 LIVE / AI / DH runtime / RealClient / real provider 越界。 |

---

## 5. Validation

本轮实测命令：

```powershell
git status --short
git diff --check
git diff --stat
git diff origin/dev...HEAD --name-status
git diff -- .github/workflows
git diff -- backend frontend research scripts deploy
git diff -- "backend/**/db/migration"
```

结果：

- `git status --short`：clean baseline。
- `git diff --check`：exit 0。
- `git diff --stat`：clean baseline 时为空；本 review 更新后仅 docs/current 文档 diff。
- `git diff origin/dev...HEAD --name-status`：仅 6 个 docs/current 文件。
- `git diff -- .github/workflows`：空。
- `git diff -- backend frontend research scripts deploy`：空。
- `git diff -- "backend/**/db/migration"`：空。

---

## 6. Boundary Statement

```text
Batch 5B-ENV plan = ACCEPTED / READY FOR IMPLEMENTATION
Batch 5B-ENV implementation = NOT STARTED
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
No workflow changed
No code changed
No migration changed
No real credential read
No outbound call
No LIVE
No AI
No DH runtime
No RealClient
No real provider
```

---

## 7. Rollback

本轮是 docs-only review/status update。回滚方式：

```powershell
git checkout -- docs/current/NQ_CI_SECURITY_BATCH_5B_ENV_PLAN_REVIEW.md docs/current/NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md docs/current/NQ_CI_BASELINE_PLAN.md docs/current/README.md docs/current/ROADMAP.md docs/current/TESTING.md docs/current/WORKLOG.md
```

若已提交，则使用 `git revert <review-commit>`。回滚不影响 workflow、backend、frontend、research、scripts、deploy 或 migration。
