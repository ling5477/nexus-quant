# NQ CI Dependency Audit Plan

任务：NQ-CI-SECURITY-GUARD-BATCH-4F-DEPENDENCY-AUDIT-PLAN
日期：2026-06-18
状态：**PLAN REVIEW ACCEPTED / 4F-A IMPLEMENTED / READY FOR REVIEW**

本文件只规划 GateK CI Batch 4F dependency audit / supply-chain audit。未修改 `.github/workflows/ci.yml`，未新增 CI job，未运行外部 dependency audit 上传服务，未上传 artifact，未修改 backend / frontend / research / scripts / deploy / migration / tests / lockfile / POM。

固定事实：

- GateJ completed；Next: GateK-PLAN。
- GateK planning baseline FROZEN / ACCEPTED。
- NQ CI Batch 1 FROZEN / ACCEPTED；Batch 2A/2B/2C/2D/2E FROZEN / ACCEPTED。
- Batch 3 no-outbound guard FROZEN / ACCEPTED。
- Batch 4C security artifact/log redaction baseline FROZEN / ACCEPTED。
- Static workflow assertion OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- Batch 4F dependency audit plan review = **PASS / ACCEPTED**；plan = **ACCEPTED AS IMPLEMENTATION BASELINE**。
- Batch 4F implementation = **4F-A IMPLEMENTED / READY FOR REVIEW**；4F-B 至 4F-F 仍 **NOT STARTED**。
- Batch 4F-A dependency audit input / toolchain preflight = **IMPLEMENTED / READY FOR REVIEW**（见 `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`）。
- Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = **NOT STARTED**。
- Batch 5 frontend E2E hardening = **PENDING**。
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

## Task classification

- Primary type: `CI_CD` planning。
- Auxiliary: `SECURITY_AUDIT`、`DEPENDENCY_AUDIT_PLANNING`、`SUPPLY_CHAIN_REVIEW`、`DOCUMENTATION`。
- Primary skill: `nq-dh-workflow-router`，用于 NQ / Gate / CI 边界分类与禁止范围确认。
- 辅助 MCP / skill: `idea-mcp` 只读检索和文件读取；未使用数据库、浏览器、GitHub 写接口或外部搜索。

## Scope

允许范围：

- 只读检查 `.github/workflows/ci.yml`。
- 只读检查 Maven POM、frontend `package.json` / `package-lock.json`、research `pyproject.toml`。
- 只读检查 `docs/current` CI 文档。
- 新增本文件。
- 同步 `docs/current/README.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`、`NQ_CI_SECURITY_GUARD_PLAN.md`、`NQ_CI_BASELINE_PLAN.md`。

禁止范围：

- 不修改 `.github/workflows/ci.yml`。
- 不新增 GitHub Actions job。
- 不运行真实外部 dependency audit 上传服务。
- 不上传 artifact。
- 不改 backend / frontend / research / scripts / deploy。
- 不改 `package-lock.json`、`pom.xml`、`pyproject.toml`。
- 不改测试，不新增 migration。
- 不开启 LIVE，不接 AI，不接 DH runtime，不实现 RealClient / real provider / real exchange adapter。
- 不进入 Batch 5。

## Files inspected

只读检查：

- `AGENTS.md`
- `README.md`
- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`
- `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`
- `docs/current/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`
- `docs/current/NQ_CI_LOG_REDACTION_PROOF_PLAN.md`
- `docs/current/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`
- `.github/workflows/ci.yml`
- `backend/pom.xml`
- `backend/**/pom.xml`
- `frontend/package.json`
- `frontend/package-lock.json`
- `research/py/pyproject.toml`

只读命令摘要：

```powershell
Get-Location
git status --short
git branch --show-current
git log --oneline -8
dir .github\workflows
dir backend
dir frontend
dir research
git ls-files "*pom.xml" "package.json" "package-lock.json" "pyproject.toml" "requirements*.txt" ".github/workflows/*.yml"
rg --files -g 'pom.xml' -g 'package.json' -g 'package-lock.json' -g 'pyproject.toml' -g 'requirements*.txt' -g '.github/workflows/*.yml' -g '!node_modules' -g '!target' -g '!build' -g '!dist' -g '!test-results'
git grep -nE "dependency-review|dependabot|renovate|cyclonedx|sbom|audit-ci|npm audit|pip-audit|osv|trivy|grype|snyk|owasp|versions-maven-plugin|maven-dependency-plugin" -- .github docs backend frontend research
rg -n "uses:|GITLEAKS_VERSION|curl --fail|upload-artifact|setup-node|setup-python|setup-java|checkout" .github\workflows\ci.yml
rg -n "<dependency>|<artifactId>|<groupId>|<version>|<scope>" backend -g pom.xml
rg -n '"(dependencies|devDependencies|lockfileVersion|packages|node_modules/)' frontend\package-lock.json frontend\package.json
rg -n "requires-python|dependencies|dev =|pytest|mypy|ruff|setuptools" research\py\pyproject.toml
```

## Files changed

新增：

- `docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md`

同步：

- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`
- `docs/current/NQ_CI_BASELINE_PLAN.md`

未修改：

- `.github/workflows/ci.yml`
- `backend/**`
- `frontend/**`
- `research/**`
- `scripts/**`
- `deploy/**`
- `backend/**/db/migration/**`
- `frontend/package-lock.json`
- `backend/**/pom.xml`
- `research/py/pyproject.toml`

## Current dependency surface

| Layer | Current files | Current fact | 4F planning consequence |
| --- | --- | --- | --- |
| Java / Maven | `backend/pom.xml` + 22 child `pom.xml` files | Java 21 / Spring Boot `3.5.10` via BOM；explicit managed `jjwt.version=0.12.6`；workflow already uses `maven-dependency-plugin:3.8.1:build-classpath` only for classpath preparation. | Need Maven vulnerability audit and optional SBOM; current `maven-dependency-plugin` usage is not vulnerability audit. |
| Frontend / npm | `frontend/package.json` + `frontend/package-lock.json` lockfile v3 | React/Vite/AntD/TanStack/Axios/Zustand/Playwright/TypeScript；existing docs record `npm audit` advisories as non-blocking. | Need npm audit triage first; do not run `npm audit fix` in CI. |
| Python / research | `research/py/pyproject.toml` | `dependencies = []`; dev extra has `pytest>=8.0`, `mypy>=1.8`, `ruff>=0.8`; no requirements file found. | Audit installed dev environment or pyproject project; no lockfile means fail-closed policy must be conservative. |
| GitHub Actions | `.github/workflows/ci.yml` | `actions/checkout@v4`, `setup-java@v4`, `upload-artifact@v4`, `setup-node@v4`, `setup-python@v5`; gitleaks CLI pinned to `8.18.4` but downloaded from release URL without checksum pin. | Need action pin/SHA strategy and tool checksum strategy, but not in this planning turn. |

## Java / Maven dependency audit plan

Recommended layers:

1. **Inventory first**
   - Generate effective dependency view for `backend/pom.xml` and reactor modules.
   - Preferred future commands:

```powershell
mvn -f backend/pom.xml -DskipTests dependency:tree
mvn -f backend/pom.xml -DskipTests org.cyclonedx:cyclonedx-maven-plugin:<pinned-version>:makeAggregateBom
```

2. **Vulnerability audit**
   - Candidate tools:
     - `org.owasp:dependency-check-maven` for local CVE/NVD style scanning.
     - `osv-scanner` for package ecosystem advisory matching.
     - GitHub Dependency Review for PR delta when repo is hosted on GitHub.
   - First implementation should be **report-only / advisory** because Maven audit tools produce false positives on transitive dependencies and unused code paths.

3. **Blocking escalation**
   - Fail-closed only after baseline triage.
   - Blocking candidates:
     - Critical CVE with direct runtime dependency and available fixed version.
     - Known exploited vulnerability in a runtime path.
     - Spring Security / auth / JWT / web stack vulnerability affecting `nq-api`, `nq-security`, `nq-auth`, or app startup.
     - Dependency introduced in PR with critical/high advisory and no documented mitigation.
   - Non-blocking candidates:
     - Test-scope only dependencies unless they execute in CI with credential/log side effects.
     - CVE on unused optional classpath segment with no reachable path.
     - Unfixed advisory requiring broad framework upgrade outside a scoped patch.

Controls:

- Do not print full dependency tree in CI logs by default.
- If an SBOM or dependency tree is generated, scan it through Batch 4C pre-upload redaction gate before upload.
- Keep Maven audit separate from backend `mvn test`; audit failure must not mask compile/test failure.
- Pin any audit plugin version; do not use floating `latest`.
- Do not introduce new Maven plugin config in this planning batch.

## Frontend / npm dependency audit plan

Recommended layers:

1. **Lockfile based audit**
   - Use `frontend/package-lock.json` as the authoritative npm dependency graph.
   - Future first-pass command:

```powershell
Set-Location frontend
npm audit --json
```

2. **Report-only baseline**
   - Existing docs already record known `npm audit` advisories as non-blocking.
   - First implementation should parse and summarize severity counts, affected package, dependency path count, fix availability, and whether fix requires semver-major.
   - Do not upload raw `npm audit --json` without redaction review.

3. **Blocking escalation**
   - Blocking candidates after triage:
     - Critical or high advisory in direct production dependency with non-breaking fix available.
     - Vulnerability in `axios`, router, build server, dev server, or browser-executed package that impacts production bundle or CI E2E safety.
     - New PR introduces a vulnerable package or lockfile drift that increases critical/high count.
   - Advisory-only candidates:
     - Vulnerability only in dev tooling and not used in production bundle.
     - Fix requires major React/Vite/AntD/toolchain upgrade.
     - Known false positive already accepted with expiry and owner.

Controls:

- `npm audit fix` must not run in CI automatically.
- `package-lock.json` is not a credential by itself, but it can contain resolved URLs, package names, integrity hashes, and local path hints. Treat it as dependency evidence, not secret material.
- Do not paste full lockfile or raw audit JSON into logs or docs.
- Any generated `audit.json` must go through Batch 4C pre-upload redaction before artifact upload.
- Keep Batch 5 E2E hardening separate; dependency audit must not pull Playwright browser-cache or E2E startup changes into 4F.

## Python / research dependency audit plan

Current facts:

- `research/py/pyproject.toml` has no runtime dependency.
- Dev dependencies are `pytest>=8.0`, `mypy>=1.8`, `ruff>=0.8`.
- No `requirements*.txt` found under tracked files.
- No Python lockfile is present.

Recommended layers:

1. **Environment audit**
   - First pass should audit the installed research dev environment after `python -m pip install -e ".[dev]"`.
   - Candidate tool:

```powershell
Set-Location research/py
python -m pip install -e ".[dev]"
python -m pip_audit
```

2. **Project-level advisory**
   - Because there is no lockfile, exact transitive versions can vary by resolver time.
   - First implementation should be advisory/report-only unless a lock/constraints file is introduced in a separately reviewed batch.

3. **Blocking escalation**
   - Blocking candidates after triage:
     - Critical/high vulnerability in directly declared dev tool that executes in CI.
     - Malicious/yanked package in installed environment.
     - New runtime dependency added to `dependencies` with known exploitable vulnerability.
   - Advisory-only candidates:
     - Transitive dev dependency advisory without deterministic lock.
     - Advisory requiring broad Python tooling upgrade.

Controls:

- Do not add `requirements.txt`, constraints, or lockfile in this planning batch.
- Do not upload raw `pip-audit` JSON before redaction.
- Do not make Python audit blocking until dependency resolution is deterministic or baseline is accepted.

## GitHub Actions supply-chain plan

Current facts:

- GitHub official actions use major tags:
  - `actions/checkout@v4`
  - `actions/setup-java@v4`
  - `actions/upload-artifact@v4`
  - `actions/setup-node@v4`
  - `actions/setup-python@v5`
- gitleaks CLI is pinned to version `8.18.4` but the release tarball is not pinned by SHA256 checksum.
- Workflow permissions are already minimized at `contents: read`.

Recommended policy:

1. **Action version pinning**
   - Short term: keep major tags as accepted baseline, record as P2/P3 supply-chain hardening gap.
   - Hardening target: pin third-party actions to immutable commit SHA.
   - For GitHub-owned actions, either pin to commit SHA or retain major tags with scheduled review. Because major tags can move, SHA pinning gives stronger reproducibility.

2. **Tool binary pinning**
   - Any downloaded CLI must have pinned version and checksum verification.
   - Current gitleaks `8.18.4` version pin is useful but incomplete without SHA256.

3. **Permissions**
   - Keep `contents: read`.
   - Dependency review may require `pull-requests: read`; do not grant write permissions unless separately reviewed.
   - Do not use `id-token: write` for dependency audit.

4. **No external write/upload**
   - Do not send dependency trees, SBOMs, or audit results to SaaS scanners unless explicitly approved.
   - Default tools should run locally in GitHub runner and produce sanitized summaries.

## SBOM plan

Recommendation: **Yes, generate SBOM later, but not as a blocking first step.**

Layered approach:

- Java: CycloneDX aggregate BOM for Maven reactor.
- Frontend: CycloneDX npm SBOM or npm package-lock derived SBOM.
- Python: CycloneDX Python SBOM only after deterministic dependency resolution is agreed; otherwise mark research SBOM as advisory.

Rules:

- SBOM is dependency metadata, not credential material by default.
- SBOM may expose internal module names, package paths, repository URLs, exact versions, checksums, and local path hints. Treat as sensitive engineering artifact.
- Do not upload raw SBOM until Batch 4C redaction gate supports it.
- Prefer retention-limited artifact, sanitized summary in logs, and no public release attachment.
- SBOM generation failure should be advisory in first implementation unless it detects malformed dependency files or unsafe path/content.

## Dependency review plan

Recommendation: **Yes, add GitHub Dependency Review later for PR delta.**

Use cases:

- Detect newly introduced vulnerable dependencies in PRs.
- Separate "new risk introduced by this PR" from existing baseline debt.
- Avoid blocking every PR on old accepted advisories.

Policy:

- First pass: advisory/report-only with clear summary.
- Later fail-closed for new critical/high vulnerabilities in changed dependency manifests when fix or mitigation exists.
- Must not comment raw dependency tree or raw lockfile into PR.
- If PR annotations/comments are used, review `pull-requests: write` permission separately; default plan should avoid write permission.

## Dependabot / Renovate plan

Recommendation: **Yes, but as a separate governance batch after audit baseline.**

Options:

- Dependabot:
  - Native GitHub integration.
  - Good for grouped ecosystem updates and security updates.
  - Needs config review for `backend` Maven, `frontend` npm, GitHub Actions, and optionally Python.
- Renovate:
  - More configurable grouping and schedule control.
  - Higher configuration surface and noise risk.

Recommended first choice:

- Start with Dependabot config in a later batch because NQ already uses GitHub Actions.
- Group updates by ecosystem and risk:
  - Maven security patches.
  - npm production dependencies.
  - npm dev tooling.
  - GitHub Actions.
  - Python dev tooling.
- Require manual review for major upgrades.
- Do not auto-merge security or dependency updates without CI green and review.

## CI blocking policy

Blocking from first implementation:

- Audit job cannot run safely, cannot parse results, or scanner exits with infrastructure/config error.
- Dependency manifest is malformed:
  - Maven POM cannot be parsed.
  - `package-lock.json` does not match `package.json` / `npm ci` would fail.
  - `pyproject.toml` cannot be parsed.
- Audit step prints raw secret value, raw token, raw environment dump, or raw artifact content.
- Audit artifact upload is attempted without pre-upload redaction.
- New direct dependency in PR has critical vulnerability with available non-breaking fix and no mitigation.
- Supply-chain tool download is unpinned or checksum verification fails after checksum policy is implemented.

Blocking after baseline triage:

- Critical/high exploitable runtime dependency in Maven backend with reachable path and fixed version.
- Critical/high npm production dependency with safe fix.
- Malicious/yanked Python package or vulnerable direct research dependency executing in CI.
- GitHub Action update introduces unpinned third-party action or write/id-token permission without review.

## Advisory / report-only policy

Report-only in first implementation:

- Existing npm advisories already recorded in docs.
- Maven transitive vulnerabilities without reachability confirmation.
- Test-scope or dev-scope vulnerabilities without production/runtime exposure.
- Python transitive dev dependency findings without lockfile determinism.
- SBOM generation coverage gaps.
- GitHub-owned action major-tag pinning gap.
- gitleaks CLI missing SHA256 checksum, until the supply-chain pinning batch is implemented.

Report-only must still be visible:

- Severity.
- Ecosystem.
- Direct/transitive.
- Runtime/dev/test scope.
- Fix availability.
- Owner decision.
- Expiry/revisit date.

## False-positive and triage policy

Required triage dimensions:

- Ecosystem: Maven / npm / Python / GitHub Actions.
- Direct vs transitive.
- Runtime vs test/dev/build-time.
- Reachable vs not yet proven.
- Fix available vs no fixed version.
- Semver-compatible vs major upgrade.
- Existing baseline vs newly introduced by PR.
- Exploit known / actively exploited vs generic CVSS.

Allowed outcomes:

- `BLOCKING_FIX_REQUIRED`
- `ADVISORY_ACCEPTED_WITH_EXPIRY`
- `FALSE_POSITIVE_WITH_EVIDENCE`
- `MITIGATED_BY_CONFIGURATION`
- `DEFERRED_MAJOR_UPGRADE`

Any accepted advisory must have:

- Reason.
- Owner.
- Expiry/revisit condition.
- Evidence path.
- Whether it can become blocking later.

## Credential and raw-artifact hygiene

Dependency reports are not credentials by default, but they can be mishandled. Batch 4F must keep these rules:

- Do not classify dependency tree, lockfile, SBOM, package hash, or advisory ID as credential merely because it contains `token`, `secret`, `cookie`, or package names with those words.
- Do not print full `package-lock.json`, raw dependency tree, raw SBOM, raw audit JSON, full environment variables, full Maven settings, or full npm config.
- Do not upload raw artifact unless:
  - It is generated in a controlled directory.
  - It is text or explicitly supported binary format.
  - It passes Batch 4C pre-upload redaction gate.
  - Retention is bounded.
  - The artifact name does not contain branch/user/path secrets.
- Do not include:
  - Full runner path if avoidable.
  - Environment variable dumps.
  - Token masks as proof of safety.
  - Raw HTTP request/response from external scanners.
  - Full dependency tree in PR comments.
- Summaries may include:
  - Ecosystem.
  - Package name.
  - Direct/transitive.
  - Severity.
  - Advisory ID.
  - Fix availability.
  - Redacted path such as `frontend/package-lock.json`.

## Relationship to Batch 4C

Batch 4C is the redaction baseline that Batch 4F must reuse.

- Batch 4C = FROZEN / ACCEPTED.
- Batch 4F must not weaken Batch 4C.
- Any future dependency audit output or SBOM upload must run through pre-upload redaction.
- Any future log output must follow 4C-C proof discipline: no secret value, no complete matching line, no raw artifact dump.
- Batch 4F should produce sanitized summaries first; raw artifacts are optional and must be separately reviewed.

## Relationship to Batch 5

Batch 5 is frontend E2E hardening and remains PENDING.

- Batch 4F must not start Playwright browser-cache hardening.
- Batch 4F must not change frontend E2E backend startup strategy.
- npm dependency audit can report Playwright/Vite/dev-tool vulnerabilities, but fixing those by changing E2E infra belongs to a separate Batch 5 or dependency-update batch.
- Batch 4F must not use E2E pass/fail as dependency audit evidence.

## Findings

### P0

- 无。本轮只规划，未发现真实 credential 泄露、真实交易、LIVE、AI、DH runtime、RealClient、real provider 或 real exchange adapter 启动。

### P1

- 无 planning blocker。

### P2

- Dependency audit 尚未实现。当前只有 Batch 4F planning；Maven / npm / Python vulnerability audit 均未进入 CI。
- GitHub Actions 当前使用 major tag，gitleaks CLI 仅版本 pin 未 checksum pin；属于 supply-chain hardening gap。
- npm 既有 advisories 已多次记录，不能直接改为 blocking，必须先 triage。
- Python research 无 lockfile，`pip-audit` 结果在未固定解析前不宜 fail-closed。

### P3

- SBOM 尚未生成；建议后续 report-only 起步。
- Dependabot / Renovate 尚未配置；建议后续单独治理。
- Raw dependency reports / SBOM / lockfile 如上传，可能暴露路径、包图和内部模块信息，需复用 Batch 4C redaction 与 retention。

## Execution sequence

Plan review 已由 `NQ-CI-SECURITY-GUARD-BATCH-4F-PLAN-REVIEW` 接受为 implementation baseline；它不是 execution batch。Batch 4F implementation 必须按下表顺序执行，禁止跳过 4F-A 直接进入 4F-B 或修改 workflow。

| Batch | Name | Status | Prerequisite | Allowed scope | Success |
| --- | --- | --- | --- | --- | --- |
| 4F-A | dependency audit input / toolchain preflight | **IMPLEMENTED / READY FOR REVIEW** | Batch 4F plan review PASS / ACCEPTED；Batch 4C FROZEN / ACCEPTED；workflow/code/dependency files unchanged | docs-only preflight；只确认 dependency sources、lockfile/无 lockfile 状态、可用审计工具、tool pin/checksum policy、输出卫生规则和 4F-B 输入条件；不改 workflow、不运行 scanner、不上传 artifact | Java/Maven、npm、Python/research、GitHub Actions inputs 与 tool candidates 已记录于 `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`；raw output / SBOM / lockfile hygiene 规则复用 Batch 4C；4F-B 输入条件明确 |
| 4F-B | sanitized advisory audit summary | **NOT STARTED** | 4F-A completed / accepted | 可单独评审最小 report-only audit summary；默认不上传 raw JSON / SBOM | Maven / npm / Python 只输出 sanitized summary；不 blocking vulnerability findings，只 blocking parser/tool/config failures and raw-secret output |
| 4F-C | SBOM report-only | **NOT STARTED** | 4F-A completed / accepted；4F-B baseline decision recorded | 生成 Maven / npm SBOM；Python SBOM 取决于 lock/constraints 决策 | 所有 SBOM artifact 先过 Batch 4C pre-upload redaction gate；retention bounded；不做 public release attachment |
| 4F-D | PR dependency delta review | **NOT STARTED** | 4F-A completed / accepted；PR permission review prepared | 引入 GitHub Dependency Review 或等价 PR delta check | 默认 report-only；只对新增 critical/high 且可修复项逐步 fail-closed；PR comment/annotation 权限单独评审 |
| 4F-E | GitHub Actions / CLI supply-chain pinning | **NOT STARTED** | 4F-A completed / accepted；current action / CLI inventory recorded | 评审 action SHA pinning 与 downloaded CLI checksum verification | 不同时升级 action major versions；downloaded CLI 必须 version + checksum pin；write/id-token 权限不得扩大 |
| 4F-F | Dependabot / Renovate governance | **NOT STARTED** | 4F-A completed / accepted；baseline advisory policy recorded | 配置 dependency update automation governance | 分组、节流、禁 auto-merge、major upgrade 手动 review；与 CI audit baseline 联动 |

4F-A 当前为 **IMPLEMENTED / READY FOR REVIEW**；4F-A review 接受前，4F-B / 4F-C / 4F-D / 4F-E / 4F-F 均保持 **NOT STARTED**。Batch 4F 任一后续产物上传仍必须经过 Batch 4C pre-upload redaction gate；Batch 5 仍 **PENDING**，不得进入 Playwright browser-cache、E2E backend startup 或 frontend E2E hardening。

## Validation

本轮实际执行的是 planning/docs 验证，不运行 backend Maven / frontend build / E2E / Python pytest，因为本轮禁止改代码、测试、workflow、lockfile、POM、research，并且 dependency audit implementation 未开始。

已执行：

```powershell
Get-Location
git status --short
git branch --show-current
git log --oneline -8
dir .github\workflows
dir backend
dir frontend
dir research
git ls-files "*pom.xml" "package.json" "package-lock.json" "pyproject.toml" "requirements*.txt" ".github/workflows/*.yml"
rg --files -g 'pom.xml' -g 'package.json' -g 'package-lock.json' -g 'pyproject.toml' -g 'requirements*.txt' -g '.github/workflows/*.yml' -g '!node_modules' -g '!target' -g '!build' -g '!dist' -g '!test-results'
git grep -nE "dependency-review|dependabot|renovate|cyclonedx|sbom|audit-ci|npm audit|pip-audit|osv|trivy|grype|snyk|owasp|versions-maven-plugin|maven-dependency-plugin" -- .github docs backend frontend research
rg -n "uses:|GITLEAKS_VERSION|curl --fail|upload-artifact|setup-node|setup-python|setup-java|checkout" .github\workflows\ci.yml
rg -n "<dependency>|<artifactId>|<groupId>|<version>|<scope>" backend -g pom.xml
rg -n '"(dependencies|devDependencies|lockfileVersion|packages|node_modules/)' frontend\package-lock.json frontend\package.json
rg -n "requires-python|dependencies|dev =|pytest|mypy|ruff|setuptools" research\py\pyproject.toml
```

待执行收尾验证：

```powershell
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- backend/**/db/migration
git status --short
```

## Review decision

**PLAN READY FOR REVIEW**

- NQ-CI-SECURITY-GUARD-BATCH-4F-PLAN：**PASS / ACCEPTED AS IMPLEMENTATION BASELINE**
- Batch 4F execution sequence：**SYNCED / ACCEPTED**
- Batch 4F-A dependency audit input / toolchain preflight：**IMPLEMENTED / READY FOR REVIEW**
- Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F：**NOT STARTED**
- Batch 4F dependency audit implementation：**4F-A IMPLEMENTED / READY FOR REVIEW；4F-B 至 4F-F NOT STARTED**
- Batch 4C：**FROZEN / ACCEPTED**
- Static workflow assertion：**OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**
- Batch 5：**PENDING**
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter：未开启、未接入、未实现

## Next concrete action

下一步只能是：

- `NQ-CI-SECURITY-GUARD-BATCH-4F-A-PREFLIGHT-REVIEW`
- Batch 4F sequence / plan fix
- Optional static workflow assertion planning
- Batch 5 planning
- 暂停 CI 线

不得把 Batch 4F 写成 implemented，不得新增 dependency audit job，不得修改 workflow，不得进入 Batch 5。
