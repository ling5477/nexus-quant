# GateR Testing and CI Summary

## 测试执行与 CI 证据

- CI 目标：完成 Freeze closeout 前置条件校验。
- 最新 GitHub Actions：`gh run view 28852212136`。
- 最新 CI run 状态：`status=completed`，`conclusion=success`，`headSha=f2507cb2a061bfced5ea42554f75aba5ef879702`。
- 最新 CI run job 集：Diff check、Frontend build、Backend Maven test、No-outbound、CI security smoke、Secret scan、PostgreSQL/Flyway smoke、Frontend backend smoke。
- 结论：`NQ CI Baseline` 本轮通过。

## 归档轮次内已核验项（核心）

- `docs/current/ROADMAP.md` 和 `docs/current/GATER_PLAN.md` 的 GateR-0..8 完成状态与边界一致性。
- `git diff --check`、`git diff --stat`、`git status --short` 与 forbidden scope diff 已用于确认无 backend / frontend / migration / CI 文件变更。
- `rg` 关键词扫描覆盖了 `GateR|Shadow Run|read-only|LIVE|AI|DH|RealClient|real provider|private trading adapter|real permission probe|order|cancel|transfer|withdraw`。
- 证据命名与文档状态链路已在 `docs/current/TESTING.md`（历史实现轮次）和 `GATER_EVIDENCE_MATRIX.md` 中补齐。
- 本轮 freeze closeout 文档未新增测试、未新增代码、不改 CI、不改 migration。

## 现阶段可追溯测试清单

### Backend test evidence

- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunReadOnlyQueryServiceTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunReadOnlyControllerTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcShadowRunFactRepositoryTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunRunnerServiceTest.java`

### Frontend smoke evidence

- `frontend/tests/e2e/shadow-run-detail-smoke.spec.ts`

### 运行与验证命令（闭环）

- `git status --short --untracked-files=all`
- `git branch --show-current`
- `git rev-parse HEAD`
- `git rev-parse origin/dev`
- `git log --oneline -12`
- `gh run list --limit 5`
- `gh run view 28852212136 --json status,conclusion,headSha,name,createdAt,updatedAt,jobs`
- `git tag --list "nq-gater-freeze"`
- `git diff --check`
- `git diff --stat`
- `git diff --cached --name-only`
- `git diff -- backend`
- `git diff -- frontend`
- `git diff -- research`
- `git diff -- scripts`
- `git diff -- deploy`
- `git diff -- .github`
- `git diff -- backend/nq-infra/src/main/resources/db/migration`
- `rg "GateR|Shadow Run|shadow-runs|read-only|diagnostic only|trading authorization|LIVE READY|SHADOW LIVE TRADING ENABLED|REAL PROVIDER ENABLED|PRIVATE TRADING ENABLED|REAL PERMISSION PROBE ENABLED|AI STARTED|DH INTEGRATED|placeOrder|cancelOrder|withdraw|transfer|apiKey|secret|passphrase|token|credentialMaterial" README.md docs/current docs/gates backend frontend`
