# NQ CI Baseline Plan

任务：NQ-CI-BASELINE-PLAN
日期：2026-06-14
状态：ACCEPTED；Batch 1 implemented / first green confirmed；Batch 2A FROZEN / ACCEPTED；Batch 2B FROZEN / ACCEPTED；Batch 2C FROZEN / ACCEPTED；2C-HYGIENE-FIX FROZEN / ACCEPTED；Batch 2D FROZEN / ACCEPTED；Batch 2E FROZEN / ACCEPTED；Batch 3 no-outbound guard FROZEN / ACCEPTED（run `27634370657`）；Batch 4A plan review ACCEPTED；Batch 4B secret scan FROZEN / ACCEPTED（run `27674393780`，frozen baseline commit `31540de8`）；Batch 4C overall security artifact/log redaction baseline FROZEN / ACCEPTED（4C-B pre-upload artifact redaction gate FROZEN / ACCEPTED，immutable green run `27701669084`，frozen baseline = `ci.yml` pre-upload redaction gate step blob `4a40ef78` / commit `c734102d`；4C-C log redaction proof FROZEN / ACCEPTED，immutable green run `27732660516`，14 类 pattern 真实值命中 = 0；overall freeze review `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`）；Batch 4F execution sequence SYNCED / ACCEPTED，4F-A FROZEN / ACCEPTED，4F-B/4F-C/4F-D/4F-E/4F-F OPTIONAL BACKLOG / NOT STARTED；Static workflow assertion OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED；Batch 5 PLAN ONLY / NOT IMPLEMENTED，plan PASS / READY FOR REVIEW

## Current state

- Project: NexusQuant / NQ。
- Current branch: `dev`。
- Current stage: GateJ completed；Next: GateK-PLAN。
- `GATEK_PLAN.md` 已 freeze / accepted；`GATEK_ARCHITECTURE_BASELINE_REVIEW` 与 `GATEK-ARCH-DOC-SYNC` 已完成。
- GateK implementation: limited to CI Batch 1 baseline only；product/runtime GateK implementation NOT STARTED。
- AI: NOT STARTED。
- DH runtime: NOT INTEGRATED / not connected to NQ。
- LIVE: DISABLED。
- real exchange permission probe adapter: NOT IMPLEMENTED。
- `.github/workflows/ci.yml` 已由 `NQ-CI-BASELINE-IMPL` Batch 1 新增，状态为 implemented / first green confirmed；GitHub Actions run `27496906788` 的 `diff-check`、`backend`、`frontend`、`research` 均为 success；Batch 2A 已新增 `postgres-flyway` job，GitHub Actions run `27501253175` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；Batch 2B 已在既有 `postgres-flyway` job 中实现 schema metadata artifact generation / upload，并由 GitHub Actions run `27521750442` first green + freeze review 固化为 FROZEN / ACCEPTED；Batch 2C repository-only real PostgreSQL smoke 已由 GitHub Actions run `27535619157` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；2C-HYGIENE-FIX 已由 GitHub Actions run `27550583713` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；Batch 2D `nq-app` context smoke 已由 GitHub Actions run `27601707199` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；Batch 2E seed watcher cleanup 已由 GitHub Actions run `27614046762` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；Batch 3 no-outbound guard 已新增 `no-outbound-guard` job 和 test-scope denylist guard，并由 GitHub Actions run `27634370657`（commit `88d976a1`）first green confirmed（6 jobs 全 green），经 Batch 3E freeze review 固化为 FROZEN / ACCEPTED；`.github` 仍不得包含其他未审查 workflow。
- Backend 是 Java 21 / Spring Boot 3.5.x / Maven multi-module；统一命令为 `mvn -f backend/pom.xml test`。
- Frontend 是 React / Vite / Ant Design / TanStack Query / Axios / Zustand / Playwright；`package.json` 当前脚本包含 `build`、`preview`、`test:e2e`。
- Research Python 使用 `research/py/pyproject.toml`，dev baseline 为 `pytest`、`mypy`、`ruff`。
- Flyway migration 当前最大版本为 `V31__schema_credential_permission_probe.sql`。
- PLAN / REVIEW 轮只规划 CI；`NQ-CI-BASELINE-IMPL` Batch 1 只允许创建最小 GitHub Actions workflow，不改代码，不新增 API，不新增 migration。

## CI goals

1. 建立 GateK 后续 `NQ-CI-BASELINE-IMPL` 的最小可阻塞 CI baseline。
2. 将 backend、frontend、frontend E2E、research、docs/diff check 分层，避免一个 job 失败掩盖其他层问题。
3. 明确 PostgreSQL / Flyway 验证如何进入 CI，避免 schema drift、migration order、local-only DB 依赖和 repeatability 风险。
4. 明确 no-outbound guard：默认测试不得访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken 等真实交易所。
5. 明确 security guard：secret scan、LIVE disabled、credential log redaction 和 dependency audit 的入场顺序。
6. 明确 merge policy：哪些 job 阻塞 merge，哪些允许手动 rerun，哪些可以后置。

## Non-goals

- 不创建 Batch 1 以外的 `.github/workflows/**`；本文件允许的唯一 Batch 1 workflow 是 `.github/workflows/ci.yml`。
- 不修改 Java / TypeScript / Python / 测试代码。
- 不新增 API、Controller、Service、Repository、Adapter 或 migration。
- 不修改历史 migration。
- 不修改 frontend B0 / Design System v2 分支内容。
- 不修改 backend 生产逻辑、deploy 或 scripts。
- 不开启 LIVE，不接 AI，不接 DH runtime，不实现 NQ RealClient 或真实 Provider。
- 不调用真实交易所，不实现真实 OKX / Binance permission probe adapter。
- 不读取、打印、复制或输出真实 credential material。
- 不把 Batch 1 baseline 写成完整 CI hardening implemented；PostgreSQL/Flyway、no-outbound guard、security guard、frontend E2E hardening 仍是后续批次。

## Job matrix

| Layer | Job name | Working directory | Command | Cache | Merge blocking | Flaky skip |
| --- | --- | --- | --- | --- | --- | --- |
| Diff hygiene | `nq-ci-diff-check` | repo root | `git diff --check` | none | Yes | No |
| Backend | `nq-ci-backend-test` | repo root | `mvn -f backend/pom.xml test` | Maven local repository | Yes | No |
| Frontend build | `nq-ci-frontend-build` | `frontend` | `npm ci`; `npm run build` | npm cache + Playwright optional cache | Yes | No |
| Frontend E2E | `nq-ci-frontend-e2e` | `frontend` | `npm ci`; Playwright browser install/cache; `npm run test:e2e` | npm + Playwright browser cache | Yes after backend test env is stable | No |
| Research | `nq-ci-research` | `research/py` | `python -m pip install -e ".[dev]"`; `python -m pytest -q`; `python -m mypy src`; `python -m ruff check .` | pip cache | Yes for research changes; baseline should be required before release | No |
| PostgreSQL / Flyway | `nq-ci-postgres-flyway` | repo root / backend | PostgreSQL service + direct Flyway API migrate/validate against empty DB V1-V31 | Maven + PostgreSQL service | Yes after first green review | No |
| No-outbound | `nq-ci-no-outbound-guard` | repo root / backend | fake-server / network-deny / ArchUnit / log assertions | Maven | Yes for backend/test changes after implemented | No |
| Security | `nq-ci-security-guard` | repo root | gitleaks / secret pattern scan / LIVE disabled assertions | tool cache optional | Yes for secret scan; dependency audit can start non-blocking | No |

## Backend baseline

Baseline command:

```powershell
mvn -f backend/pom.xml test
```

Planned job:

- Working directory: repo root。
- Java: 21。
- Cache: Maven local repository keyed by OS + Java version + `backend/pom.xml` and child `pom.xml` hashes.
- Blocking: yes。任何 compile/test failure、Surefire failure、Spring context failure、Flyway failure 都应阻塞 merge。
- Flaky skip: 不允许。现有 skipped 必须明确是业务输入未配置且不伪装为 passed；新增 skip 必须在 PR 中说明原因、完成条件和恢复计划。
- Output retention: 保存 Surefire reports，但不得输出或上传 credential material、raw headers、raw response、API key、secret、passphrase、token。

Backend first batch must include:

- `mvn -f backend/pom.xml test`。
- 明确 no-real-exchange 默认行为：permission probe 默认 port 仍为 `NoRealExchangeCredentialPermissionProbePort`。
- 阻塞任何真实下单、撤单、转账、提现测试路径进入默认 Maven test。

Backend deferred items:

- 更细 Maven module matrix 可以后置；第一批先以 full backend Maven test 作为 merge gate。
- Surefire report 结构化上传可以后置，但上传前必须做敏感信息过滤。

## Frontend baseline

Baseline commands:

```powershell
Set-Location frontend
npm ci
npm run build
npm run test:e2e
```

Planned jobs:

- `nq-ci-frontend-build`
  - Working directory: `frontend`。
  - Cache: npm cache keyed by `frontend/package-lock.json`。
  - Blocking: yes。
  - Failure policy: TypeScript compile failure、Vite build failure、lockfile drift 均阻塞 merge。

- `nq-ci-frontend-e2e`
  - Working directory: `frontend`。
  - Cache: npm cache + Playwright browser cache。
  - Browser install: first implementation may run Playwright install explicitly or rely on cached browser; cache miss must not be treated as test skip。
  - Current runner: `npm run test:e2e` starts Vite on `127.0.0.1:5179` and then runs Playwright with `E2E_EXTERNAL_DEV_SERVER=true`。
  - Backend dependency: current E2E uses real backend login/API path, so CI must either start `nq-app` local profile with PostgreSQL or introduce a separately reviewed mock server / preview-server mode。
  - Blocking: yes once backend test environment is deterministic；before that, a planning review may introduce a temporary non-blocking dry run, but it must not be reported as passed baseline。

Frontend B0 / Design System v2:

- B0 frontend branch / Draft PR remains outside this `dev` planning task。
- After B0 merges to `dev`, frontend CI should include its build and E2E assertions through the same `nq-ci-frontend-build` and `nq-ci-frontend-e2e` jobs。
- Do not mix frontend B1/B2/B3 page work into this CI plan or implementation batch。

## Research baseline

Baseline commands:

```powershell
Set-Location research/py
python -m pip install -e ".[dev]"
python -m pytest -q
python -m mypy src
python -m ruff check .
```

Planned job:

- Working directory: `research/py`。
- Python: 3.11+。
- Cache: pip cache keyed by `research/py/pyproject.toml`。
- Blocking: yes for research changes；recommended as required baseline before release/freeze。
- Sample data: current tracked sample data is `research/py/fixtures/btcusdt_1m_sample.csv`；CI must only use tracked fixtures or generated test data。
- External data sources: forbidden by default. Research CI must not download live exchange data, call provider APIs, or read credential/environment secrets。
- Determinism: tests should use fixed sample data and fixed random seed when randomness is introduced；non-deterministic tests must not be merge-blocking until stabilized。

## PostgreSQL / Flyway plan

Current facts:

- `docker-compose.yml` provides PostgreSQL `postgres:17.7` by default, mapped to `${NQ_DB_PORT:-5432}:5432`。
- Current maximum migration is V31。
- Batch 2A added tracked `postgres-flyway` workflow job; first green run confirmed in GitHub Actions run `27501253175` and freeze review accepted it as the current PostgreSQL / Flyway smoke baseline。

PostgreSQL service option:

- Pros: simple GitHub Actions service container, close to local `docker-compose.yml`, easy to inspect logs。
- Cons: relies on CI service readiness, port/env wiring, and cleanup discipline；less isolated if parallel jobs share names/ports incorrectly。
- Recommended first implementation for `NQ-CI-POSTGRES-FLYWAY` because it is explicit and easy to review.

Testcontainers option:

- Pros: test owns database lifecycle, better isolation, future repository integration tests can be self-contained。
- Cons: requires adding/using Testcontainers dependencies and possibly code/test changes; not allowed in this planning-only task and should be separate implementation review。
- Recommended later if repository-layer integration tests expand。

Flyway validation:

- CI must validate migrations V1 to V31 in order on an empty PostgreSQL database。
- CI must fail on checksum mismatch, out-of-order migration, missing migration, failed repeat run, or schema history drift。
- Batch 2A uses `postgres:16` service and a temporary Java smoke runner that calls Flyway `migrate` + `validate` directly against `classpath:db/migration`。
- Batch 2A does not start `nq-app` Spring context, does not insert legacy account seed, does not run repository real DB tests, and does not use Testcontainers。
- A later hardening batch should add schema metadata artifact and drift review without adding runtime behavior。

Repository layer:

- Many current repository tests use recording/mocked `JdbcTemplate` and do not require real PostgreSQL。
- Repository tests that prove SQL compatibility, constraints, JSONB behavior, locking, and Flyway-created schema should run against real PostgreSQL in the `NQ-CI-POSTGRES-FLYWAY` layer。
- Do not silently rely on H2 or mocks for PostgreSQL-specific features.

`nq-app` Spring context:

- Required for frontend E2E with real backend and for integration smoke that proves Flyway + Spring wiring。
- Must run with local/CI profile that keeps LIVE disabled, disables real provider side effects, and uses fake/no-outbound guards。

## No-outbound guard plan

Rules:

- Default `mvn test` must not access OKX / Binance / Bybit / Gate / Coinbase / Kraken or any real exchange host。
- Tests must not perform real HTTP probe, private REST call, websocket connection, order, cancel, transfer, withdraw, or credential permission probe。
- Tests must not read real API key / secret / passphrase / private key / token / cookie。
- OKX public bootstrap no-outbound remains a CI risk class even after the documented fix; CI should prove no startup outbound, not assume it。
- Permission probe default must remain `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。

Detection layers:

1. Fake server tests: adapters that need HTTP behavior must point to local fake servers and assert hit counts / request paths。
2. Network deny: install JVM-level guard or process-level deny for known exchange hosts in no-outbound tests.
3. ArchUnit / static boundary: assert default test/runtime wiring does not instantiate real provider clients in baseline tests unless explicitly allowed.
4. Log assertions: Surefire reports and app logs must not contain outbound failures such as `UnknownHostException`, `ConnectException`, `No route to host`, real exchange request URL, or private endpoint access.
5. Environment isolation: CI must not provide real exchange API credentials to test jobs; any required env values must be fake placeholders with obvious non-production names.

Blocking policy:

- `NQ-CI-NO-OUTBOUND-GUARD` should become merge-blocking for backend/test changes once implemented。
- Any real outbound attempt in default CI is P0 unless the job is explicitly named as a manual live diagnostic and is excluded from PR merge gates. Manual live diagnostics are outside this GateK baseline.

## Security guard plan

Secret scan:

- Add `gitleaks` or equivalent secret scan in `NQ-CI-SECURITY-GUARD` first batch if tool availability is stable。
- At minimum scan tracked files for `.env`, `*.key`, `*.pem`, token, password, passphrase, API key, API secret, private key, mnemonic, cookie, and credential material patterns。
- CI must not print secret values. Findings should report file/path/rule only.

Forbidden files:

- `.env` files, private `*.key`, private `*.pem`, token dumps, credential dumps, cookies, production logs, and real exchange credential material must not be committed。
- `frontend/.env.example` and config templates may exist only with placeholders.

LIVE disabled guard:

- CI should include a static/docs guard that fails if current facts are changed to `LIVE enabled`, `GateK implementation started`, `AI started`, `DH integrated`, or `real adapter implemented` outside an approved implementation task。
- Backend tests must keep `LIVE_CREDENTIAL_BLOCKED` and no-real permission probe behavior covered。

Credential log redaction:

- CI should scan test reports/logs for raw credential terms and known fake-value leakage patterns。
- API/audit/log assertions should remain in backend tests; the CI security guard should catch regressions in generated reports.

Dependency audit:

- `npm audit`, Maven dependency vulnerability scanning, and Python dependency audit are useful but may be noisy。
- Recommendation: add dependency audit as non-blocking P2 observation first, then promote selected high/critical policies to blocking after baseline triage。

Docker/config template security:

- Docker compose and config template security checks should be P2/P3 follow-up after core secret scan and no-outbound gates are stable。
- Do not modify compose/scripts in the CI baseline planning task.

## Branch / PR policy

Recommended `dev` branch protection:

- Require PR before merge。
- Require `nq-ci-diff-check`、`nq-ci-backend-test`、`nq-ci-frontend-build`。
- Require `nq-ci-frontend-e2e` after CI backend/PostgreSQL environment is stable。
- Require `nq-ci-postgres-flyway` once implemented。
- Require `nq-ci-no-outbound-guard` once implemented。
- Require `nq-ci-security-guard` secret scan immediately if stable；dependency audit can start non-blocking。
- Require review for workflow changes and security-sensitive config changes。

Docs-only PR:

- Must run `git diff --check` and docs/stage wording guard。
- Recommended to skip full backend/frontend/research only when diff is strictly docs/current/README/AGENTS style documentation and the PR clearly states no code/config/test/migration changes。
- Docs-only skip must not be described as full CI passed.

Frontend-only PR:

- Must run frontend build and E2E。
- Backend full test may be skipped only if diff proves no backend/API/test/migration changes and E2E uses an already stable backend fixture; otherwise run backend test。

Backend-only PR:

- Must run backend full Maven test。
- If API contract, auth, account context, or data response changes can affect UI, frontend build/E2E should run。

Research-only PR:

- Must run research pytest/mypy/ruff。
- Backend/frontend can be skipped only if no shared docs/API/config/test fixtures are changed。

Manual rerun:

- Allowed for infra flake, cache miss, service startup race, or external GitHub runner issue。
- Not allowed to mask deterministic failures。

Skip rules:

- `skip ci` or job-level skip cannot be used to claim success。
- Any skipped required job must appear as not executed, with reason and follow-up condition.

## Required secrets / forbidden secrets

Required secrets for baseline:

- None for default PR CI。
- Default CI must not require OKX / Binance / Bybit / Gate / Coinbase / Kraken credentials。
- Default CI must not require production database credentials, production JWT secrets, cloud tokens, or live exchange credentials。

Allowed env values:

- Local PostgreSQL service env with non-production CI-only values。
- Fake credentials only when tests explicitly assert redaction or validation behavior。
- `CI=true` and test profile flags that disable real provider side effects。

Forbidden secrets:

- Real API key、API secret、passphrase、private key、mnemonic、token、cookie。
- Real exchange credential material。
- Production `.env`。
- Private `*.key` / `*.pem`。
- Any credential payload printed in logs, reports, comments, screenshots, or uploaded artifacts。

## Risk list P0/P1/P2/P3

| Priority | Risk | Impact | Required handling |
| --- | --- | --- | --- |
| P0 | CI job calls real exchange host | Violates no-outbound and may leak behavior | Add fake-server/network-deny/log guard before making backend CI authoritative |
| P0 | LIVE enabled wording or config enters CI/default tests | Breaks GateK boundary | Static guard + backend LIVE blocked tests |
| P0 | Secret material committed or printed | Credential incident | gitleaks/secret scan + artifact redaction |
| P0 | CI baseline Batch 1 被误读为完整 CI hardening | Governance drift | 固定写 Batch 1 implemented / first green confirmed；PostgreSQL/Flyway、no-outbound、secret scan、frontend E2E hardening 仍 pending |
| P1 | PostgreSQL/Flyway not validated in CI | Migration drift reaches dev | Add PostgreSQL service + V1-V31 migration validation |
| P1 | E2E depends on absent backend | Frontend gate becomes flaky or permanently red | Define backend startup or mock-server strategy before blocking |
| P1 | `npm ci` / Playwright browser install cache is unstable | Frontend CI noise | Cache by lockfile and browser version; no skip as pass |
| P2 | Dependency audit starts blocking before triage | Noise blocks dev | Start non-blocking, promote high-confidence rules |
| P2 | Full CI too expensive for docs-only PRs | Slow review loop | Path-based policy with explicit skip wording |
| P3 | Test reports are uploaded without retention/redaction policy | Artifact hygiene risk | Add retention and redaction rules in security batch |

## Implementation batches

### Batch 1: NQ-CI-BASELINE-IMPL

Status: IMPLEMENTED / FIRST GREEN CONFIRMED。

Implemented workflow:

- `.github/workflows/ci.yml`
- Triggers: `pull_request` to `dev`、`push` to `dev`、manual `workflow_dispatch`。
- Jobs: `diff-check`、`backend`、`frontend`、`research`。
- First green run: GitHub Actions run `27496906788` passed all four jobs: `Diff check`、`Backend Maven test`、`Frontend build`、`Research quality gate`。
- First-run fix: `backend` job uses an ephemeral PostgreSQL service and CI-only legacy `accounts` seed watcher only to satisfy existing local-profile Spring context tests executed by `mvn -f backend/pom.xml test` on a fresh GitHub runner。
- Not included: frontend E2E、PostgreSQL/Flyway、no-outbound guard implementation、gitleaks / secret scan、dependency audit。

Must implement:

- `nq-ci-diff-check`。
- `nq-ci-backend-test`: `mvn -f backend/pom.xml test`。
- `nq-ci-frontend-build`: `npm ci` + `npm run build`。
- `nq-ci-research`: `pytest` + `mypy` + `ruff`。
- Basic path policy and merge-blocking rules。

Must not implement in Batch 1:

- Real exchange diagnostics。
- LIVE / AI / DH runtime checks beyond static forbidden wording guards。
- Testcontainers adoption if it requires dependency/code changes。
- Deployment or script rewrites。

### Batch 2: NQ-CI-POSTGRES-FLYWAY

Status: Batch 2A FROZEN / ACCEPTED；Batch 2B FROZEN / ACCEPTED；Batch 2C FROZEN / ACCEPTED；Batch 2D FROZEN / ACCEPTED；Batch 2E FROZEN / ACCEPTED。

Planning document:

- `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md`

Batch 2A implemented:

- `.github/workflows/ci.yml` job `postgres-flyway`。
- PostgreSQL service container `postgres:16` with CI-only `nq_ci` / `nq_ci_user` / `nq_ci_password`。
- Direct Flyway API empty-db migration validation from V1 to V31。
- Prints `flyway_schema_history` in job logs。
- No seed, no app context, no repository real DB smoke, no Testcontainers, no `baselineOnMigrate`, no Flyway `clean`。
- First green evidence: run `27501253175` / commit `7836640ebae46d6fc62771611f5215661b3267dc` completed / success；job `PostgreSQL / Flyway smoke` completed / success；logs show `Successfully applied 31 migrations ... now at version v31`, `Successfully validated 31 migrations`, and `Flyway empty database smoke reached V31`。

Batch 2B freeze evidence:

- Run `27521750442` / commit `c62ebddd5a522bbdf72bc018064b9eb36d8fe9e1` completed / success.
- Job `PostgreSQL / Flyway smoke` completed / success.
- Artifact `nq-postgres-flyway-schema-artifacts` uploaded with id `7628309014`, size `74662` bytes, retention through `2026-06-29T03:14:04Z`.
- Artifact download contained exactly `flyway-info.txt`, `schema-tables.txt`, `schema-columns.txt`, `schema-constraints.txt`, `schema-indexes.txt`, `schema-comments.txt`, and `schema-dump.sql`; schema dump data-row and high-risk credential pattern checks passed.
- Freeze review accepted Batch 2B as the current `dev` PostgreSQL / Flyway schema artifact minimal baseline.

Batch 2C freeze evidence:

- Run `27535619157` / commit `9adb71b8dc56a0bf881952da918ebaab5fdbeb7f` completed / success.
- Job `PostgreSQL / Flyway smoke` / `81384164182` completed / success.
- Step `Run repository PostgreSQL smoke` success；`JdbcRepositoryPostgresSmokeTest` Surefire summary was `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.
- Freeze review accepted Batch 2C as the current `dev` repository-only real PostgreSQL smoke baseline.
- 2C-HYGIENE-FIX added job-step masking for `NQ_FLYWAY_DB_URL`, `NQ_FLYWAY_DB_USER`, and `NQ_FLYWAY_DB_PASSWORD`; first GitHub Actions run `27550583713` completed / success, masking step passed, and later step logs mask the three `NQ_FLYWAY_DB_*` values as `***` or avoid direct printing. Freeze review accepted it as the current Batch 2C CI log hygiene baseline.
- Residual GitHub service-level Docker/env output and the masking step's own automatic `env:` display may still show disposable CI-only fake DB values before masking is active; accepted as P2 hygiene residual, not real credential leakage.

Completed / frozen:

- Batch 2D `nq-app` context smoke 已由 GitHub Actions run `27601707199` confirmed，`NqAppContextPostgresSmokeTest` tests=1 / skipped=0 / failures=0 / errors=0，并经 freeze review 固化为 FROZEN / ACCEPTED。
- Batch 2E CI-only seed watcher cleanup 已由 GitHub Actions run `27614046762` confirmed backend Maven test and `postgres-flyway` job success，并经 freeze review 固化为 FROZEN / ACCEPTED。

### Batch 3: NQ-CI-NO-OUTBOUND-GUARD

Status: FROZEN / ACCEPTED（GitHub Actions run `27634370657` completed / success；Batch 3E freeze review P0/P1/P2=0）。Planning / implementation document: `docs/current/NQ_CI_NO_OUTBOUND_GUARD_PLAN.md`。

Implemented baseline:

- Known exchange host deny list: OKX, Binance, Bybit, Bitget, Gate, Coinbase, Kraken, Crypto.com, Hyperliquid and required host variants。
- `No-outbound guard` GitHub Actions job with forbidden exchange credential env checks and explicit denylist coverage check。
- Test-scope `ExchangeNoOutboundGuard` / `NoOutboundExchangeGuardTest` fail closed before DNS/HTTP/WS connect。
- `NqAppContextPostgresSmokeTest` installs the guard at context initialization and asserts WS clients are mocked/no-interaction。
- Permission probe default NoReal guard is asserted in the app context smoke; LIVE credential probe rejection remains covered by backend service tests。
- First GitHub Actions evidence confirmed by run `27634370657` (6 jobs green; `NoOutboundExchangeGuardTest` 3/0/0/0; `NqAppContextPostgresSmokeTest` 1/0/0/0 with guard installed); Batch 3D first-run review accepted and Batch 3E freeze review passed (P0/P1/P2=0). Status FROZEN / ACCEPTED as the current `dev` no-outbound guard baseline.

### Batch 4: NQ-CI-SECURITY-GUARD

Status: Batch 4A plan review ACCEPTED；Batch 4B secret scan minimal implementation **FROZEN / ACCEPTED**（run `27674393780`，frozen baseline commit `31540de8`）；Batch 4C overall security artifact/log redaction baseline **FROZEN / ACCEPTED**：4C-A plan review ACCEPTED；4C-B pre-upload artifact redaction gate **FROZEN / ACCEPTED**（immutable green run `27701669084`，frozen baseline = `ci.yml` pre-upload redaction gate step blob `4a40ef78` / commit `c734102d`）；4C-C log redaction proof **FROZEN / ACCEPTED**（freeze review，immutable green run `27732660516`，14 类 pattern 真实值命中 = 0，P0/P1/P2 blockers = 0）；overall freeze review **PASS / ACCEPTED / FROZEN**（`NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`）。Batch 4F dependency audit plan review **PASS / ACCEPTED**，execution sequence **SYNCED / ACCEPTED**，4F-A **FROZEN / ACCEPTED**（`NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md`），4F-B/4F-C/4F-D/4F-E/4F-F **OPTIONAL BACKLOG / NOT STARTED**；Static workflow assertion OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。Planning / implementation 文档：`docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`、`docs/current/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`、`docs/current/NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md`、`docs/current/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`、`docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md`、`docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`、`docs/current/NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md`。

Batch 4B implemented baseline（`.github/workflows/ci.yml` 新增 `secret-scan` job）：

- pinned gitleaks `8.18.4` CLI binary（非 `gitleaks-action`，无 `GITLEAKS_LICENSE`，下载不传 token），安装后校验 `gitleaks version`。
- 扫描范围限定当前 tracked working tree（`git ls-files` + 排除 `.env*` / secrets / credentials / `*.pem` / `*.key` / `*.p12` / `*.jks` / `*.keystore` / `target` / `node_modules` / `dist` / `build` / `coverage` / logs / dumps / backups / `.git`），`gitleaks detect --no-git --redact`，禁止 full-history scan。
- inline gitleaks 配置（`useDefault = true` + 精确 allowlist：4 个 Binance fake-key / PEM 协议常量文件 by path + `REPLACE_WITH_LOCAL` / `CHANGE_ME` / `FAKE-PLACEHOLDER` 占位 marker），核心规则未放宽。
- custom regex backstop（`sk-ant-` / `sk-proj-` / `github_pat_` / `gh[pousr]_` / `AKIA` / `ASIA` / PEM private key / `xoxb-` / `xoxp-` / value-bearing mnemonic / value-bearing 凭证赋值），只输出 `file | pattern`，绝不输出命中值。
- job `permissions: contents: read`；不注入 repository secret；无 `continue-on-error`；secret scan 失败 fail closed。
- 本地 custom backstop 复刻验证 0 非 allowlisted 命中；gitleaks first green run evidence 待 Batch 4D。

后续（NOT STARTED）：

- Batch 4C：artifact upload 前 redaction 通用规则 + log redaction proof + LIVE/boundary static guard。**Overall security artifact/log redaction baseline FROZEN / ACCEPTED**（详见 `docs/current/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md` 与 `docs/current/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`，拆分 4C-A plan review / 4C-B pre-upload gate impl+freeze / 4C-C log redaction proof+freeze / overall freeze review / future optional static assertion）：4C-A plan review ACCEPTED；4C-B pre-upload redaction gate **FROZEN / ACCEPTED**（immutable run `27701669084`，frozen baseline = `ci.yml` pre-upload redaction gate step blob `4a40ef78` / commit `c734102d`）；4C-C log redaction proof **FROZEN / ACCEPTED**（`NQ-CI-SECURITY-GUARD-BATCH-4C-C-FREEZE-REVIEW`，2026-06-18，P0/P1/P2 blockers = 0；基于 immutable green run `27732660516`，7 jobs 全复核，14 类 pattern 真实值命中 = 0，仅 disposable CI 值 / Spring ephemeral dev password / platform `***` mask / step-script 回显非阻断 FP；proof 不输出 secret value / 完整匹配行；本轮未改 ci.yml、未读本地 logs、未上传 logs artifact）。Batch 4C overall freeze review **PASS / ACCEPTED / FROZEN**。Static workflow assertion 仍为 OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- Batch 4F（可选）：dependency audit / supply-chain audit plan review PASS / ACCEPTED，execution sequence SYNCED / ACCEPTED（`NQ_CI_DEPENDENCY_AUDIT_PLAN.md` / `NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md`）。4F-A dependency audit input / toolchain preflight IMPLEMENTED / READY FOR REVIEW（`NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`）；4F-B sanitized advisory audit summary、4F-C SBOM report-only、4F-D PR dependency delta review、4F-E GitHub Actions / CLI supply-chain pinning、4F-F Dependabot / Renovate governance 均 NOT STARTED。4F-A review 接受前不得启动 4F-B 至 4F-F；任一后续 artifact / SBOM / report 上传仍须经过 Batch 4C redaction gate；不进入 Batch 5。

### Batch 5: Frontend E2E hardening

Status: **plan PASS / ACCEPTED；Batch 5A IMPLEMENTED / READY FOR FIRST-RUN；5B-ENV P1 PREREQUISITE / NOT STARTED；5B-SMOKE BLOCKED BY 5B-ENV**。Planning document: `docs/current/NQ_CI_FRONTEND_E2E_PLAN.md`；plan review: `docs/current/NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md`；5A implementation: `docs/current/NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md`。

Batch 5A implemented slice（IMPLEMENTED / READY FOR FIRST-RUN，尚未经 GitHub Actions first-run review）:

- 新增独立 job `frontend-no-backend-e2e`（`permissions: contents: read`、`timeout-minutes: 15`、Node 22、`npm ci`、`npx playwright install --with-deps chromium`、`npm run build`、loopback `vite preview` 127.0.0.1:5179、`if: always()` 清理临时 output 不上传）与 `frontend/playwright.ci.config.ts`（Chromium only / workers=1 / retries=0 / trace=screenshot=video=off / line reporter / 不用 storageState / `reuseExistingServer:false` / `forbidOnly:true`）。
- 唯一 allowlist 四个 no-backend spec（仓库真实路径 `frontend/tests/e2e/`，非 `frontend/e2e/`）：`login-page-smoke`、`design-system-table-smoke`、`design-system-live-query-smoke`、`design-system-backtest-chart-smoke`；命令显式列出四 spec，config `testMatch` 二次限定，`--list` = Total: 4 tests in 4 files，未扩大到其余 23 个 spec。
- 本地真实验证：build 成功、四 spec 4 passed (10.2s)、无 artifact 生成/上传；未启动 backend/PostgreSQL/Flyway/认证/seed/外网/真实 provider，未调用 `loginToConsole()`。
- Batch 4C redaction 未弱化；本轮未新增任何 upload 路径。

Frozen planning decisions:

- First implementation slice is a bounded 4-spec no-backend allowlist against loopback preview; current historical local results are not Batch 5 CI evidence。
- Backend-required E2E must use its own job-local PostgreSQL 16/fresh DB, Flyway migrate+validate, synchronous CI-only auth/legacy fixture, reviewed backend profile and runtime no-outbound enforcement; it must not reuse another job's database or restore seed watcher polling。
- Initial output is sanitized console summary only; trace/video/screenshot/HTML/test-results/raw logs are not uploaded。Any future artifact requires separate Batch 4C-compatible sanitization/proof and bounded retention。
- Required allowlist forbids environment-dependent skip and automatic retry masking；frontend feature/page development is excluded。

## Validation commands

Planning/doc validation for this task:

```powershell
git status --short
git diff --check
git diff --stat
git ls-files .github
git ls-files backend | Select-Object -First 20
git ls-files frontend | Select-Object -First 20
git ls-files research | Select-Object -First 20
rg "name:|on:|jobs:" .github docs/current README.md
rg --glob '!frontend/node_modules/**' --glob '!**/target/**' --glob '!**/build/**' --glob '!**/dist/**' "mvn|npm run build|test:e2e|pytest|mypy|ruff|flyway|postgres|Testcontainers|OKX|Binance|NoReal|LIVE" docs/current backend frontend research README.md
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- backend/**/db/migration
```

Implementation validation for future `NQ-CI-BASELINE-IMPL`:

```powershell
mvn -f backend/pom.xml test
Set-Location frontend
npm ci
npm run build
npm run test:e2e
Set-Location ..\research\py
python -m pip install -e ".[dev]"
python -m pytest -q
python -m mypy src
python -m ruff check .
```

## Next concrete action

Next concrete action: 等待 GitHub Actions `frontend-no-backend-e2e` first-run（Batch 5A）并做 first-run review；连续两次 immutable green 后再议 required gate。不得直接进入 Batch 5B-ENV / 5B-SMOKE / 5C / 5D / 5E，不得启动 Batch 4F-B 至 4F-F。Batch 3、Batch 4B、Batch 4C 与 Batch 4F-A 保持 FROZEN / ACCEPTED；Batch 5A = IMPLEMENTED / READY FOR FIRST-RUN；Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV。

Do not mix Batch 4 security scan hardening、Batch 5 frontend E2E hardening、frontend B1/B2/B3 work、AI、DH runtime、LIVE、real providers 或 real exchange permission probe adapter into Batch 3 no-outbound work.
