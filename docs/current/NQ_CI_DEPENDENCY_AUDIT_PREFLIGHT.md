# NQ CI Dependency Audit Preflight

任务：NQ-CI-SECURITY-GUARD-BATCH-4F-A-DEPENDENCY-AUDIT-PREFLIGHT

状态：**PASS / READY FOR REVIEW**

本文件建立 GateK CI Batch 4F-A dependency audit input / toolchain preflight 基线。4F-A 只确认后续 4F-B advisory audit summary 所需输入、工具链可用性、供应链 pinning 现状和输出卫生规则；不运行 Maven vulnerability audit、`npm audit`、`pip-audit`、OSV、Snyk、Trivy、Grype、OWASP dependency-check 或任何外部扫描器；不生成 SBOM；不修改 workflow；不上传 artifact；不修改 POM / package / lockfile / pyproject / requirements。

## Current facts

- Branch: `dev`.
- GateJ: completed.
- GateK planning baseline: FROZEN / ACCEPTED.
- Batch 4C security artifact/log redaction baseline: FROZEN / ACCEPTED.
- Batch 4F plan: ACCEPTED AS IMPLEMENTATION BASELINE.
- Batch 4F execution sequence: SYNCED / ACCEPTED.
- Batch 4F-A: **IMPLEMENTED / READY FOR REVIEW**.
- Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F: **NOT STARTED**.
- Static workflow assertion: OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED.
- Batch 5 frontend E2E hardening: PENDING.
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter: disabled / not started / not integrated / not implemented.

## Scope

Allowed:

- Read tracked `pom.xml`, `package.json`, `package-lock.json`, `pyproject.toml`, `requirements*.txt`, constraints/lockfile candidates, and `.github/workflows/*.yml`.
- Read local command availability and versions for Java / Maven / Node / npm / Python / pip.
- Update `docs/current/**` only.

Forbidden:

- No `.github/workflows/ci.yml` changes.
- No CI job additions.
- No dependency audit scanner execution.
- No SBOM generation.
- No artifact / raw report / JSON / dependency tree / lockfile / SBOM upload.
- No scanner or dependency installation.
- No `npm audit fix`.
- No dependency upgrades.
- No backend / frontend / research / scripts / deploy / migration / test changes.
- No Batch 4F-B through 4F-F implementation.
- No Batch 5.
- No LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter.

## Dependency input inventory

### Java / Maven

Tracked Maven input:

- `backend/pom.xml`: root Maven reactor parent, packaging `pom`.
- 22 tracked child `pom.xml` files under `backend/nq-*`.
- All 22 child modules declare parent artifact `nexus-quant-backend` version `0.1.0-SNAPSHOT`.
- Root module order:
  - `nq-app`
  - `nq-common`
  - `nq-contracts`
  - `nq-research`
  - `nq-backtest`
  - `nq-eval`
  - `nq-infra`
  - `nq-ledger-contracts`
  - `nq-ledger`
  - `nq-risk`
  - `nq-core`
  - `nq-config`
  - `nq-scheduler-contracts`
  - `nq-scheduler`
  - `nq-observability`
  - `nq-adapter-api`
  - `nq-adapter-okx`
  - `nq-adapter-binance`
  - `nq-auth`
  - `nq-security`
  - `nq-gateway`
  - `nq-api`

This preflight did not copy full POM contents and did not build a dependency tree.

### Frontend / npm

Tracked npm input:

- `frontend/package.json`
  - name: `nexus-quant-frontend`
  - version: `0.1.0`
  - private: `true`
  - scripts present: `dev`, `build`, `preview`, `test:e2e`
  - dependency sections observed without copying package lists: 9 dependencies, 7 devDependencies
- `frontend/package-lock.json`
  - lockfileVersion: `3`
  - root package: `nexus-quant-frontend`
  - root version: `0.1.0`
  - package entries: 214

This preflight did not copy lockfile content and did not run `npm audit`.

### Python / research

Tracked Python/research input:

- `research/py/pyproject.toml`
  - project name: `nexus-quant-research`
  - version: `0.1.0`
  - requires-python: `>=3.11`
  - runtime dependencies: `[]`
  - optional dev dependencies are declared under `[project.optional-dependencies]`.
- No tracked `requirements*.txt`.
- No tracked `constraints*.txt`.
- No tracked Python lockfile candidates (`poetry.lock`, `Pipfile.lock`).

Boundary:

- Python/research has no deterministic lockfile in the current tracked baseline.
- 4F-B may only produce advisory/report-only Python status unless a later batch first defines deterministic constraints or lockfile policy.

### GitHub Actions / CLI supply-chain

Tracked workflow input:

- `.github/workflows/ci.yml`.

Current action reference style:

- `actions/checkout@v4`
- `actions/setup-java@v4`
- `actions/upload-artifact@v4`
- `actions/setup-node@v4`
- `actions/setup-python@v5`

Current downloaded CLI state:

- Secret scan installs gitleaks CLI with version pin `8.18.4`.
- The workflow downloads the release asset with `curl --fail --silent --show-error --location`.
- No SHA256 checksum verification was found for the downloaded gitleaks asset.
- This is a 4F-E supply-chain pinning input, not a 4F-A fix.

Current artifact boundary:

- The existing workflow has `actions/upload-artifact@v4` for PostgreSQL schema artifacts.
- Retention is bounded by event/ref expression: dev push = 14 days, otherwise 7 days.
- Any future 4F artifact / SBOM / report upload must first pass Batch 4C pre-upload redaction gate and keep bounded retention.

## Local toolchain preflight

Observed command availability:

| Tool | Local command fact |
| --- | --- |
| Java | `java.exe` found at `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`; `java -version` returned Java `21.0.8` LTS. |
| Maven | `mvn.cmd` found at `D:\Tool\Maven\apache-maven-3.9.12\bin\mvn.cmd`; `mvn -version` returned Apache Maven `3.9.12`, Java `21.0.8`. |
| Node | `node.exe` found at `D:\Tool\nodejs\node.exe`; `node --version` returned `v24.13.0`. |
| npm | `npm.ps1` found at `D:\Tool\nodejs\npm.ps1`; `npm --version` returned `11.10.0`. |
| Python | `python.exe` resolves to WindowsApps stub path; `python --version` failed to start in this shell. |
| pip | `pip` was not returned by `Get-Command`; `python -m pip --version` failed because `python.exe` failed to start. |

Interpretation:

- Java / Maven / Node / npm local preflight is available.
- Python / pip local command path is not usable in this shell; do not write Python audit as locally runnable until the interpreter path is fixed or GitHub Actions `actions/setup-python@v5` is used.
- No tools were installed.
- No scanner was downloaded.
- No vulnerability audit command was run.

## 4F-B handoff standard

4F-B is the only next implementation batch after this preflight review is accepted.

### Candidate inputs

- Java / Maven input: `backend/pom.xml` and all tracked `backend/**/pom.xml`.
- npm input: `frontend/package.json` and `frontend/package-lock.json`.
- Python input: `research/py/pyproject.toml`; no tracked requirements / constraints / lockfile currently exists.
- Workflow/supply-chain input: `.github/workflows/ci.yml` action references and downloaded CLI inventory.

### Candidate commands

4F-B may propose commands in its own implementation plan, but must keep vulnerability findings report-only at first. Candidate command families must be reviewed before use:

- Maven advisory audit command family: selected Maven vulnerability scanner or plugin, configured to avoid dependency tree dumps in logs.
- npm advisory command family: `npm audit` summary mode only, without `npm audit fix`.
- Python advisory command family: `pip-audit` or equivalent only after interpreter availability and no-lockfile behavior are explicitly handled.

4F-B must not run these commands until its own scoped implementation starts.

### Sanitized summary fields allowed

4F-B output may include only bounded summary fields:

- ecosystem: `maven`, `npm`, `python`, or `github-actions`.
- input file path.
- tool name and tool version.
- command success/failure status.
- advisory count by severity.
- advisory IDs only when needed, without full package tree.
- affected direct package name if the tool can provide it without dumping transitive tree context.
- remediation category: `none`, `manual review`, `dependency update needed`, `tool/config failure`, or `ignored by policy`.
- parser/tool/config failure reason in sanitized text.

### Prohibited output

4F-B must not print, save, or upload:

- raw audit JSON.
- full dependency tree.
- full Maven effective POM.
- full `package-lock.json`.
- raw SBOM.
- generated SBOM.
- full environment variables.
- token mask original context.
- npm / Maven / pip config containing credentials.
- repository secret values or masked-value surrounding context.
- local absolute paths beyond minimal repository-relative file names.

### Blocking boundary

- Vulnerability findings in 4F-B are advisory/report-only.
- 4F-B may block only on scanner/tool parser failure, unsafe raw output, credential-like raw output, malformed dependency files, or policy violation.
- 4F-B must not modify `pom.xml`, `package.json`, `package-lock.json`, `pyproject.toml`, `requirements*.txt`, constraints, or lockfiles.
- 4F-B must not run `npm audit fix`.
- 4F-B must not upgrade dependencies.

### Artifact boundary

- 4F-B default output should stay in logs as sanitized bounded summary.
- Any future artifact upload must be separately reviewed, pass Batch 4C redaction gate, and use bounded retention.
- Raw dependency report / raw SBOM / raw lockfile upload remains forbidden.

## Findings

### P0

- 无。

### P1

- 无。

### P2

- Python local command path is not usable in this shell: `python --version` and `python -m pip --version` failed through WindowsApps stub. 4F-B must either use a known real interpreter path or rely on GitHub Actions `actions/setup-python@v5`; do not claim local Python audit readiness until fixed.

### P3

- GitHub Actions use major tags instead of SHA pins. This is documented input for 4F-E and is not fixed in 4F-A.
- Gitleaks CLI has version pin `8.18.4` but no SHA256 checksum verification for the downloaded release asset. This is documented input for 4F-E and is not fixed in 4F-A.
- Python/research has no tracked lockfile or constraints file. 4F-B Python output should remain advisory/report-only unless deterministic resolution policy is defined.

## Validation

Executed:

```powershell
git status --short
git branch --show-current
git log --oneline -8
git ls-files "*pom.xml" "package.json" "package-lock.json" "pyproject.toml" "requirements*.txt" ".github/workflows/*.yml"
git ls-files "*package.json" "*package-lock.json" "*pyproject.toml" "*requirements*.txt" "*constraints*.txt" "*poetry.lock" "*Pipfile.lock"
Get-Command java,mvn,node,npm,python,pip -ErrorAction SilentlyContinue | Select-Object Name,Source,Version
java -version
mvn -version
node --version
npm --version
python --version
python -m pip --version
git grep -nE "uses:|gitleaks|checksum|sha256|curl|Invoke-WebRequest|npm ci|mvn |python -m" -- .github/workflows docs/current
```

Not executed:

- No Maven vulnerability audit.
- No `npm audit`.
- No `pip-audit`.
- No OSV / Snyk / Trivy / Grype / OWASP dependency-check.
- No SBOM generation.
- No dependency installation.
- No scanner download.
- No artifact upload.
- No backend / frontend / Python test/build command.

## Review decision

**NQ-CI-SECURITY-GUARD-BATCH-4F-A-PREFLIGHT：PASS / READY FOR REVIEW**

**Batch 4F-A：IMPLEMENTED / READY FOR REVIEW**

**Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F：NOT STARTED**

**Batch 4C：FROZEN / ACCEPTED**

**Static workflow assertion：OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**

**Batch 5：PENDING**

**LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter：未开启、未接入、未实现**

## Next concrete action

- `NQ-CI-SECURITY-GUARD-BATCH-4F-A-PREFLIGHT-REVIEW`
- or 4F-A docs fix if review finds a blocker
- or pause CI line

Do not start 4F-B until 4F-A review accepts this preflight baseline.
