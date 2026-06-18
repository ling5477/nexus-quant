# NQ CI Security Guard Batch 4F Plan Review

任务：NQ-CI-SECURITY-GUARD-BATCH-4F-PLAN-REVIEW
日期：2026-06-18
状态：**PASS / ACCEPTED**

## 审查结论

结论：**PASS / ACCEPTED**

- NQ-CI-SECURITY-GUARD-BATCH-4F-PLAN-REVIEW = **PASS / ACCEPTED**。
- Batch 4F plan = **ACCEPTED AS IMPLEMENTATION BASELINE**。
- Batch 4F implementation = **NOT STARTED**。
- Batch 4F execution sequence = **SYNCED / ACCEPTED**。
- Batch 4F-A = **IMPLEMENTED / READY FOR REVIEW**（后续 preflight 见 `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`）。
- Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = **NOT STARTED**。
- Batch 4C = **FROZEN / ACCEPTED**。
- Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**。
- Batch 5 = **PENDING**。
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

## 范围

已审查：

- `docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md`
- `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `.github/workflows/ci.yml`
- Maven / npm / Python dependency entry files：`backend/**/pom.xml`、`frontend/package.json`、`frontend/package-lock.json`、`research/py/pyproject.toml`
- `docs/current/README.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`

未审查：

- 未运行 Maven / npm / Python dependency vulnerability audit。
- 未生成 SBOM。
- 未运行 GitHub Dependency Review、Dependabot 或 Renovate。

明确不涉及：

- 不实现 dependency audit。
- 不修改 `.github/workflows/ci.yml`。
- 不新增 GitHub Actions job。
- 不修改 backend / frontend / research / scripts / deploy。
- 不修改 `pom.xml` / `package.json` / `package-lock.json` / `pyproject.toml` / requirements 文件。
- 不新增 migration，不改测试，不进入 Batch 5。

## Checklist

| # | 检查项 | 结论 | 证据 |
| --- | --- | --- | --- |
| 1 | Java / Maven 依赖审计 | PASS | 计划覆盖 inventory、OWASP / OSV 候选、report-only 起步、blocking escalation。 |
| 2 | frontend / npm 依赖审计 | PASS | 计划以 `package-lock.json` 为 authoritative graph，明确 `npm audit --json` 仅先做 summary / triage。 |
| 3 | Python / research 依赖审计 | PASS | 计划覆盖 `pyproject.toml`、dev environment audit、无 lockfile 边界。 |
| 4 | GitHub Actions supply-chain 风险 | PASS | 计划盘点 official actions major tags 与 gitleaks binary download。 |
| 5 | action version pin / SHA pin / checksum pin | PASS | 计划明确 action SHA pin 与 CLI checksum verification；当前 gap 只列后续 hardening。 |
| 6 | SBOM 策略 | PASS | 计划覆盖 Maven / npm / Python SBOM，report-only 起步，raw SBOM 上传需复用 4C。 |
| 7 | Dependency Review 策略 | PASS | 计划将 PR delta 与 existing baseline debt 分离，默认 report-only。 |
| 8 | Dependabot / Renovate 策略 | PASS | 计划列为后续 governance batch，禁止 auto-merge。 |
| 9 | CI blocking 与 advisory/report-only 边界 | PASS | 计划区分 first implementation blocking、post-triage blocking、report-only。 |
| 10 | 不把 dependency tree / lockfile / SBOM / vuln report 误判为 credential | PASS | 计划明确这些默认不是 credential，但作为 sensitive engineering artifact 管理。 |
| 11 | 禁止 raw dependency report / raw SBOM / raw lockfile 直接上传 | PASS | 计划明确不得直接输出/上传，必须走 4C pre-upload redaction gate。 |
| 12 | 复用 Batch 4C artifact/log redaction baseline | PASS | 计划单独章节写明 Batch 4C 是 redaction baseline。 |
| 13 | 没有弱化 Batch 4C | PASS | 计划要求 4F 不得 weaken 4C，raw artifact 可选且需单独 review。 |
| 14 | 没有进入 Batch 5 frontend E2E hardening | PASS | 计划明确 4F 不启动 Playwright browser-cache / E2E startup strategy。 |
| 15 | implementation 拆批合理 | PASS | 计划拆为 4F-A 到 4F-F 六个小批次。 |
| 16 | 未要求直接 `npm audit fix` | PASS | 计划明确 `npm audit fix` must not run in CI automatically。 |
| 17 | 未要求直接升级依赖 | PASS | 计划要求 triage、major upgrade 手动 review，不自动升级。 |
| 18 | 未要求修改 POM / lockfile / pyproject | PASS | 本轮 scope 禁止修改；后续治理单独批次。 |
| 19 | 未把既有 npm advisories 直接设为 blocking | PASS | 计划明确既有 advisories report-only / non-blocking，先 triage。 |
| 20 | Python research 无 lockfile 边界 | PASS | 计划明确无 lockfile 时不宜 fail-closed，除非 lock/constraints 单独引入。 |
| 21 | GitHub Actions major tag / gitleaks checksum gap 是后续 hardening | PASS | 计划列为 P2/P3 gap 与 4F-E，不是本轮实现。 |
| 22 | `docs/current` 无真实 credential material | PASS | 高置信 credential 正则在 `docs/current` / `.github` 中无命中；宽松前缀命中仅为规则/证明文本候选，未输出 secret value。 |
| 23 | 工作区无 workflow / code / test / migration / frontend / research / scripts / deploy diff | PASS | `git diff -- .github/workflows/ci.yml`、`git diff -- backend frontend research scripts deploy`、`git diff -- "backend/**/db/migration"` 均为空。 |

## Findings

### P0

- 无。

### P1

- 无。

### P2

- Dependency audit implementation 尚未开始；Maven / npm / Python vulnerability audit 均未进入 CI。该项不是 plan blocker，是 4F 后续 implementation scope。
- GitHub Actions 当前使用 major tags，gitleaks CLI 仅版本 pin、未 checksum pin。计划已正确列为后续 supply-chain hardening gap。
- Python research 无 lockfile；计划已正确保持 advisory/report-only 起步，避免非确定性 fail-closed。

### P3

- SBOM、Dependency Review、Dependabot / Renovate 均未实现；计划已拆为后续小批次。
- Raw dependency report / SBOM / lockfile 如未来上传，仍需先扩展或复用 Batch 4C redaction gate，并保持 bounded retention。

## 证据

- `docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md`：覆盖 Maven / npm / Python / GitHub Actions / SBOM / Dependency Review / Dependabot-Renovate / blocking-advisory / raw artifact hygiene / Batch 4C / Batch 5。
- `.github/workflows/ci.yml`：official actions 仍为 major tags；gitleaks CLI 固定 `8.18.4`，无 checksum verification；本轮未改。
- `git ls-files` / `rg --files`：依赖入口为 Maven POM、`frontend/package.json`、`frontend/package-lock.json`、`research/py/pyproject.toml`；未发现 tracked `requirements*.txt`。
- 高置信 credential check：`NO_HIGH_CONFIDENCE_CREDENTIAL_PATTERN_HITS`。

## 风险

- 影响面：仅文档与后续实现基线；当前 CI 行为未改变。
- 触发条件：后续若直接把 vulnerability findings fail-closed、上传 raw audit/SBOM、或跳过 checksum/action pinning review，可能产生 CI 噪声或泄露 dependency metadata。
- 最坏结果：dependency audit implementation 阻塞开发、误报 credential、或上传过宽 artifact。

## 修复建议

- 最小修复：无阻断项，无需修复 plan。
- 验证方式：先进入 4F-A dependency audit input / toolchain preflight；4F-A 完成前不得启动 4F-B 至 4F-F，后续每批分别单独 plan/review/first-run/freeze。
- 回滚方式：删除本 review 文档并还原 `README.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md` 中的 4F review 状态即可；未触碰 workflow / code / dependency files。

## 未验证项

- 原因：本轮是 plan review，禁止实现 dependency audit 或调用外部 dependency audit 上传服务。
- 后续验证命令：由 4F-B 起分别定义，不在本轮执行。

## Execution sequence

原 plan 中已存在 `4F-A plan review`，但该项已由本 review 完成，不再作为后续 execution batch。后续 Batch 4F implementation 必须按下表执行，禁止在 4F-A 完成前启动 4F-B 或修改 workflow。

| Batch | Name | Status | Prerequisite | Allowed scope | Success |
| --- | --- | --- | --- | --- | --- |
| 4F-A | dependency audit input / toolchain preflight | **IMPLEMENTED / READY FOR REVIEW** | Batch 4F plan review PASS / ACCEPTED；Batch 4C FROZEN / ACCEPTED | docs-only preflight；确认 dependency sources、lockfile/无 lockfile 状态、可用审计工具、tool pin/checksum policy、输出卫生规则和 4F-B 输入条件；不改 workflow、不运行 scanner、不上传 artifact | Java/Maven、npm、Python/research、GitHub Actions inputs 与 tool candidates 已记录于 `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`；raw output / SBOM / lockfile hygiene 规则复用 Batch 4C；4F-B 输入条件明确 |
| 4F-B | sanitized advisory audit summary | **NOT STARTED** | 4F-A completed / accepted | 最小 report-only audit summary；默认不上传 raw JSON / SBOM | Maven / npm / Python 只输出 sanitized summary；不 blocking vulnerability findings |
| 4F-C | SBOM report-only | **NOT STARTED** | 4F-A completed / accepted；4F-B baseline decision recorded | Maven / npm SBOM；Python SBOM 取决于 lock/constraints 决策 | SBOM artifact 先过 Batch 4C pre-upload redaction gate；retention bounded |
| 4F-D | PR dependency delta review | **NOT STARTED** | 4F-A completed / accepted；PR permission review prepared | GitHub Dependency Review 或等价 PR delta check | 默认 report-only；PR comment/annotation 权限单独评审 |
| 4F-E | GitHub Actions / CLI supply-chain pinning | **NOT STARTED** | 4F-A completed / accepted；current action / CLI inventory recorded | action SHA pinning 与 downloaded CLI checksum verification | 不同时升级 action major versions；不扩大 write/id-token 权限 |
| 4F-F | Dependabot / Renovate governance | **NOT STARTED** | 4F-A completed / accepted；baseline advisory policy recorded | dependency update automation governance | 分组、节流、禁 auto-merge、major upgrade 手动 review |

Batch 4F 任一后续产物上传仍必须经过 Batch 4C redaction gate。Batch 5 仍 **PENDING**，不进入 Playwright 或 frontend E2E hardening。

## Review decision

**NQ-CI-SECURITY-GUARD-BATCH-4F-PLAN-REVIEW：PASS / ACCEPTED**

**Batch 4F plan：ACCEPTED AS IMPLEMENTATION BASELINE**

**Batch 4F execution sequence：SYNCED / ACCEPTED**

**Batch 4F-A：IMPLEMENTED / READY FOR REVIEW**

**Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F：NOT STARTED**

**Batch 4F implementation：NOT STARTED**

**Batch 4C：FROZEN / ACCEPTED**

**Batch 5：PENDING**
