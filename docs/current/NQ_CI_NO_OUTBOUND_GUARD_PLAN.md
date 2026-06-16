# NQ CI No-Outbound Guard Plan

任务：NQ-CI-NO-OUTBOUND-GUARD-BATCH-3-PLAN
日期：2026-06-16
状态：Batch 3B IMPLEMENTED / PENDING FIRST CI RUN；Batch 3D first-run review PENDING；Batch 3E freeze review PENDING；Batch 4 security guard / secret scan PENDING；Batch 5 frontend E2E hardening PENDING

## Current CI baseline

- Project: NexusQuant / NQ。
- Current branch: `dev`。
- Current stage: GateJ completed；Next: GateK-PLAN。
- `GATEK_PLAN.md` 已 freeze / accepted。
- NQ CI Batch 1：implemented / first green confirmed。
- Batch 2A PostgreSQL / Flyway empty DB smoke：FROZEN / ACCEPTED。
- Batch 2B schema artifact baseline：FROZEN / ACCEPTED。
- Batch 2C repository-only real PostgreSQL smoke：FROZEN / ACCEPTED。
- Batch 2C hygiene fix：FROZEN / ACCEPTED。
- Batch 2D `nq-app` context smoke：FROZEN / ACCEPTED。
- Batch 2E seed watcher cleanup：FROZEN / ACCEPTED。
- GateK CI Batch 2 PostgreSQL / Flyway hardening：完成。
- Batch 3 no-outbound guard：IMPLEMENTED / PENDING FIRST CI RUN。
- Batch 4 security guard / secret scan：PENDING。
- Batch 5 frontend E2E hardening：PENDING。
- AI：NOT STARTED。
- DH runtime：NOT INTEGRATED / not connected to NQ。
- LIVE：DISABLED。
- real exchange adapter / provider / RealClient：NOT IMPLEMENTED。
- 当前真实 OKX / Binance permission probe adapter：NOT IMPLEMENTED。
- 当前默认 credential permission probe port：`NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。

## Scope

Allowed in this planning batch:

- 只读检查 `.github/workflows/ci.yml`。
- 只读检查 `backend/pom.xml`、backend modules、adapter-okx、adapter-binance、adapter-api。
- 只读检查 credential permission probe port / service / tests。
- 只读检查 `application.yml` / `application-test.yml` / `application-local.yml`。
- 只读检查 `nq-app` smoke tests、repository smoke tests、backend Maven tests。
- 新增本文件并同步 `docs/current` 入口状态。

Forbidden in this planning batch:

- 不修改 `.github/workflows/ci.yml`。
- 不修改 Java / TypeScript / Python 代码。
- 不修改测试代码，不新增测试。
- 不新增 API，不新增 migration，不修改历史 migration。
- 不修改 backend production code、frontend、research、scripts、deploy。
- 不读取、打印、复制或输出真实 credential material。
- 不调用真实交易所，不下单、撤单、转账或提现。
- 不开启 LIVE，不接 AI，不接 DH runtime。
- 不实现 RealClient、真实 provider、真实 OKX / Binance permission probe adapter。
- 不把 Batch 3 写成 implemented。
- 不把 Batch 4 / Batch 5 写成 started。

## Current outbound risk inventory

| Area | Current evidence | Risk class for Batch 3 | Required proof |
| --- | --- | --- | --- |
| GitHub Actions baseline | `.github/workflows/ci.yml` currently has `diff-check`、`no-outbound-guard`、`backend`、`postgres-flyway`、`frontend`、`research` jobs. `no-outbound-guard` is implemented and pending first CI run. | P0 if any existing job can reach exchange hosts without detection. | Batch 3D must review first-run evidence before freeze. |
| Backend Maven test | `backend` job runs `mvn -f backend/pom.xml test` after CI-only PostgreSQL fixture. It does not install a network deny guard. | P1: default Maven tests can only be trusted by current test design, not by process-level enforcement. | Batch 3C must prove full backend Maven test cannot resolve/connect to denylisted exchange hosts. |
| `postgres-flyway` job | Runs empty DB Flyway smoke, schema artifacts, repository PostgreSQL smoke, and `NqAppContextPostgresSmokeTest`. | P1: Batch 2D only proves context startup and mocked WS no interaction; it explicitly defers REST adapter interception to Batch 3. | Add JVM/domain deny and log scan around app context smoke. |
| OKX adapter | `OkxExchangeAdapter` default construction reads `OkxRuntimeConfig.fromSystemEnv()` and default `baseUrl=https://www.okx.com`; constructors create HTTP clients and caches but current tests prove construction should not fetch public instruments. | P1: explicit adapter method calls, instrument cache reads, reconcile/recovery, or catalog sync can still outbound. | Guard must catch any request to `okx.com` / `www.okx.com` / `my.okx.com`, including public instruments and private trade endpoints. |
| Binance adapter | `BinanceRuntimeConfig` defaults include `https://testnet.binance.vision`, `https://api.binance.com`, and `wss://ws-api.binance.com:443/ws-api/v3`; filters cache and timestamp provider can call public endpoints when invoked. | P1: public exchangeInfo/serverTime and private WS diagnostics are possible if tests or startup invoke them. | Guard must block Binance public/private REST and WS domains and fail on skipped/manual live diagnostic leakage into default Maven. |
| Public API calls in tests | `OkxExchangeAdapterBootstrapNoOutboundTest` uses local fake server. `BinanceWsClientLiveDiagnosticTest` is gated by `Assumptions.assumeTrue(Boolean.getBoolean("nq.binance.ws.live.diagnostic"))` and reads `../../.env` only when explicitly enabled. | P2: manual live diagnostic exists and must remain excluded from default CI; direct `.env` read must never run in CI. | Static guard for live diagnostic flags and `.env` access; job env must not set `nq.binance.ws.live.diagnostic=true`. |
| WebSocket clients | OKX and Binance WS clients exist and can connect when `nq.okx.ws.enabled=true` or `nq.binance.ws.enabled=true`. Batch 2D mocks WS beans and verifies no interactions only in that smoke. | P1: default CI lacks a general WS connect deny. | Guard must include WS URLs and assert no `newWebSocketBuilder().buildAsync(...)` reaches exchange domains. |
| Scheduler / recovery / monitor | `OkxRecoveryService`, `OkxRestReconcileService`, `BinanceRestReconcileService`, `LedgerReconcileScheduler`, and monitor services exist; some use `@Scheduled`. Current app context smoke sets `spring.task.scheduling.enabled=false`, `nq.okx.recovery.enabled=false`, and WS disabled. | P1: a future profile or test can re-enable scheduling/recovery and trigger adapter methods. | Batch 3C must assert test profiles disable scheduler/recovery/monitor side effects or network-deny catches them. |
| Instrument catalog sync | `nq.instrument.catalog-sync.enabled` defaults true in `application.yml`; Batch 2D sets it false. If invoked when enabled, it can read OKX/Binance metadata through adapter caches. | P1: explicit controller/service invocation can outbound. | Guard must either disable by test profile or block outbound at JVM/job level. |
| Permission probe | `AccountModuleConfiguration` binds `ExchangeCredentialPermissionProbePort` to `NoRealExchangeCredentialPermissionProbePort`; Service blocks LIVE, withdraw risk, missing paper safety, and uses the port only after local gates. | P1 if a real probe bean is introduced or test overrides the port. | Guard must assert default bean remains NoReal and LIVE credential probe is skipped/rejected. |
| Config profile | `application.yml` default profile is `${NQ_PROFILE:local}`; `application-local.yml` uses PostgreSQL and real-looking OKX defaults; `application-test.yml` has test DB placeholders and Flyway disabled. | P2: profile defaults are useful locally but unsafe as proof. | CI must set explicit no-outbound properties rather than trusting default local/test profile. |
| CI env / secrets | Current CI uses CI-only PostgreSQL service env values. No exchange credentials are required for default CI. | P0 if real exchange env or GitHub secrets are made visible to test jobs. | Batch 3B must include env allowlist and explicit absence checks for exchange credential variable names. |

## Exchange domain boundary

Batch 3 guard must deny and scan at least these domains and host variants:

- `okx.com`
- `www.okx.com`
- `my.okx.com`
- `binance.com`
- `api.binance.com`
- `fapi.binance.com`
- `dapi.binance.com`
- `bybit.com`
- `bitget.com`
- `gate.io`
- `coinbase.com`
- `kraken.com`
- `crypto.com`
- `hyperliquid.xyz`

Implementation may include additional known exchange/testnet/WS hosts, but must not narrow the above list without a plan review.

## Credential / LIVE boundary inventory

- Default CI must not read real API key、secret、passphrase、token、cookie、private key、mnemonic or credential dumps。
- Default CI must not scan `.env` / `secrets` / dumps / logs / backups as data sources; Batch 4 will own full secret scanning policy.
- Default CI must not output credential material into job logs, Surefire reports, schema artifacts, screenshots, or uploaded artifacts.
- Permission probe default remains `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。
- LIVE credential probe remains disabled / rejected by Service gate (`LIVE_CREDENTIAL_BLOCKED`) and by absence of real adapter binding.
- CI-only PostgreSQL values (`nq_ci`, `nq_ci_user`, `nq_ci_password`, `postgres`, `123456`) are disposable test infrastructure placeholders, not exchange credentials; they still must be masked where feasible and never be presented as production secrets.
- Batch 3 must verify job env does not expose real exchange variables such as `NQ_OKX_API_KEY`, `NQ_OKX_API_SECRET`, `NQ_OKX_API_PASSPHRASE`, `NQ_BINANCE_DOME_API_KEY`, `NQ_BINANCE_REAL_API_KEY`, `NQ_BINANCE_*_API_SECRET`, `NQ_BINANCE_*_PRIVATE_KEY`, `NQ_BINANCE_*_PRIVATE_KEY_PATH`, or live diagnostic enable flags.

## No-outbound guard plan

| Layer | Plan | Decision |
| --- | --- | --- |
| JVM layer network deny | Introduce a test-scoped JVM guard that intercepts URL/URI/ProxySelector/socket connect attempts and fails on denylisted exchange hosts. It must cover REST and WebSocket clients built through `java.net.http.HttpClient`. | Required in Batch 3B/3C. This is the primary deterministic proof. |
| Test profile disables adapters | Add/verify explicit CI properties for `spring.task.scheduling.enabled=false`, `nq.instrument.catalog-sync.enabled=false`, `nq.okx.recovery.enabled=false`, `nq.okx.ws.enabled=false`, `nq.binance.ws.enabled=false`, and no live diagnostic flags. | Required but not sufficient alone. Profiles can drift; network deny remains authoritative. |
| Mock / no-real port binding | Assert Spring default `ExchangeCredentialPermissionProbePort` bean is `NoRealExchangeCredentialPermissionProbePort`; no real OKX/Binance probe adapter bean is present. | Required in Batch 3C. |
| Environment variable allowlist | Fail CI if backend/no-outbound jobs contain exchange credential env names or live diagnostic flags outside fake placeholders. | Required in Batch 3B. |
| Outbound domain denylist | Use the exchange domain boundary above. Matching must include exact host and subdomain suffix matching where appropriate. | Required in Batch 3B. |
| Log scan | Scan Maven / Surefire / app-context logs for real exchange URLs, `UnknownHostException`, `ConnectException`, `No route to host`, `api.binance.com`, `www.okx.com`, private endpoints, and diagnostic evidence strings. | Required in Batch 3B/3D. |
| Test-level assertions | Keep local fake server tests for adapter behavior and add no-outbound assertions for app context / Maven default profile as separate tests. Manual live diagnostics must be skipped unless an explicit property is set, and CI must assert the property is absent. | Required in Batch 3C. |
| GitHub Actions job-level guard | Add a dedicated `no-outbound` job or guarded backend step with no exchange credentials, explicit denylist env, and fail-closed log scan. It should become required only after first green + freeze review. | Required in Batch 3B, promoted after Batch 3D/3E. |

## Batch 3B implementation baseline

本轮 `NQ-CI-NO-OUTBOUND-GUARD-BATCH-3B-IMPL` 已完成最小 no-outbound guard baseline，状态为 `IMPLEMENTED / PENDING FIRST CI RUN`。该状态只表示 workflow 与 test-scope guard 已落地，尚未取得 GitHub Actions first green evidence；不得写成 `FROZEN / ACCEPTED`。

已实现内容：

- `.github/workflows/ci.yml` 新增 merge-blocking `No-outbound guard` job。
- `No-outbound guard` job 不注入 repository secrets，不需要真实 credential，不访问真实交易所。
- job 显式检查 forbidden exchange credential / LIVE / real provider env names 为空。
- job 显式检查 denylist 覆盖 OKX / Binance / Binance testnet / Binance WS / Bybit / Bitget / Gate / Coinbase / Kraken / Crypto.com / Hyperliquid host set。
- job 运行 `NoOutboundExchangeGuardTest`，通过 `ExchangeNoOutboundGuard` 在 `ProxySelector` selection 阶段对受控 denylisted-host probe fail closed。
- `NqAppContextPostgresSmokeTest` 在 Spring context 初始化前安装同一 `ExchangeNoOutboundGuard`，并继续断言 OKX / Binance WS client 为 mock 且无 interaction。
- `NqAppContextPostgresSmokeTest` 新增默认 `ExchangeCredentialPermissionProbePort` 类型断言，固定为 `NoRealExchangeCredentialPermissionProbePort`。
- 既有 `CredentialPermissionProbeServiceTest` 继续覆盖 `LIVE_CREDENTIAL_BLOCKED`，证明 LIVE credential probe 在调用 port 前被 Service gate 拒绝。

Denylist baseline：

- `okx.com`
- `www.okx.com`
- `my.okx.com`
- `binance.com`
- `api.binance.com`
- `fapi.binance.com`
- `dapi.binance.com`
- `testnet.binance.vision`
- `ws-api.binance.com`
- `ws-api.testnet.binance.vision`
- `stream.binance.com`
- `stream.binancefuture.com`
- `bybit.com`
- `api.bybit.com`
- `bitget.com`
- `gate.io`
- `api.gateio.ws`
- `coinbase.com`
- `api.coinbase.com`
- `kraken.com`
- `api.kraken.com`
- `crypto.com`
- `hyperliquid.xyz`
- `api.hyperliquid.xyz`

Batch 3B local validation：

```powershell
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NoOutboundExchangeGuardTest '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.no-outbound.guard.required=true'
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'
```

结果：`NoOutboundExchangeGuardTest` 3 tests / 0 failures / 0 errors / 0 skipped，`BUILD SUCCESS`；`NqAppContextPostgresSmokeTest` 本地无 CI DB required properties，按设计 skipped=1，`BUILD SUCCESS`。真实 app context no-outbound proof 需等待 GitHub Actions `postgres-flyway` job first run。

## Batch 3 implementation strategy

### Batch 3A: no-outbound inventory / plan review

- Status target: PLAN REVIEW / ACCEPTED or PLAN FIX REQUIRED.
- Scope: documentation and read-only source review only.
- Success:
  - P0/P1=0 for the plan.
  - Current outbound risk inventory is accepted.
  - Domain denylist and credential boundary are accepted.
  - No workflow/code/test/migration changes.

### Batch 3B: workflow / CI guard minimal implementation

- Status: IMPLEMENTED / PENDING FIRST CI RUN.
- Scope:
  - Add the minimal CI no-outbound guard wiring.
  - Add env allowlist / live diagnostic flag absence checks.
  - Add denylist/log scan in a dedicated CI path.
- Success:
  - Guard fails closed on a controlled denylisted-host probe.
  - Guard does not require real credentials or real exchange access.
  - No Batch 4 full secret scan and no Batch 5 frontend E2E hardening.

### Batch 3C: backend test isolation proof

- Status target: IMPLEMENTED / PENDING FIRST CI RUN or FIRST GREEN RUN CONFIRMED after CI evidence.
- Scope:
  - Prove `mvn -f backend/pom.xml test` and `nq-app` app context smoke do not connect to exchange hosts.
  - Prove NoReal permission probe binding and LIVE probe rejection remain default.
  - Prove scheduler/recovery/catalog/WS paths are disabled or blocked under CI test profiles.
- Success:
  - Maven/Surefire evidence shows no denied outbound attempts and no real endpoint request strings.
  - Manual live diagnostic test remains default-skipped and cannot read `.env` in CI.

### Batch 3D: first-run review

- Status target: PASS / ACCEPTED FOR FIRST GREEN RUN or FAIL / FIRST-RUN-FIX REQUIRED.
- Scope:
  - Review first GitHub Actions run, jobs, steps, logs, and artifacts.
  - Verify the guard ran in CI and was not skipped / soft-failed.
- Success:
  - P0/P1=0.
  - CI evidence proves no real exchange access, no credential env exposure, and no LIVE.
  - Failures produce targeted first-run fix tasks only.

### Batch 3E: freeze review

- Status target: FROZEN / ACCEPTED.
- Scope:
  - Freeze the accepted no-outbound guard baseline.
  - Update `docs/current` current facts and next actions.
- Success:
  - Batch 3 becomes current CI no-outbound baseline.
  - Batch 4 / Batch 5 remain PENDING.
  - Required-check promotion decision is documented.

## Batch 4 / Batch 5 boundary

- Batch 3 does not implement full secret scan, gitleaks policy, dependency audit, forbidden-file sweep, or artifact-wide credential scanning. Those belong to Batch 4.
- Batch 3 does not implement frontend E2E hardening, Playwright browser cache, backend startup for E2E, frontend mock-server strategy, or flaky skip policy. Those belong to Batch 5.
- Batch 3 does not implement real exchange adapter, real provider, RealClient, LIVE, real OKX/Binance permission probe adapter, or manual live diagnostics.
- Batch 4 should own security / secret scan hardening after Batch 3 no-outbound baseline is reviewed.
- Batch 5 should own frontend E2E hardening after backend/CI isolation is stable.

## Security boundary

- No real credentials required or allowed.
- No `.env` reading in CI. Any code path that reads `.env` must stay manually gated and absent from default test execution.
- No exchange domains may be contacted by default CI or default Maven test.
- No LIVE, no AI, no DH runtime, no real provider, no RealClient.
- No order, cancel, transfer, withdrawal, private REST, private WS, credential probe, or permission probe to real exchange hosts.
- CI logs must not include raw request / raw response / headers / signatures / decrypted payload / encrypted payload / credential material.

## P0/P1/P2/P3 findings

| Priority | Finding | Decision |
| --- | --- | --- |
| P0 | None for this planning-only baseline. | P0 planning blockers = 0. |
| P1 | None for this planning-only baseline. | P1 planning blockers = 0. |
| P2 | No current dedicated CI no-outbound guard exists. | Accept as Batch 3 implementation target; do not claim implemented. |
| P2 | Batch 2D app context smoke explicitly defers REST adapter no-outbound interception to Batch 3. | Batch 3C must close this proof gap. |
| P2 | Manual Binance WS live diagnostic can read `../../.env` if explicitly enabled. | Default CI must assert the enabling property is absent; full secret scan remains Batch 4. |
| P2 | Scheduler/recovery/catalog sync paths can invoke adapters if enabled or explicitly called. | CI test profile and JVM deny guard must cover these paths. |
| P2 | `application.yml` default profile is `local`, and local config includes real exchange endpoint defaults. | Explicit CI profile/properties are required for proof; local defaults are not proof. |
| P2 | Domain list may need future expansion for testnet / WS host variants. | Minimum list is frozen here; future implementation may add more hosts. |
| P3 | Existing CI-only PostgreSQL placeholders can be confused with secrets. | Continue documenting them as disposable CI-only DB values, not exchange credential material. |

## Validation plan

Planning/doc validation for this task:

```powershell
git status --short
git diff --check
git diff --stat
git diff -- .github
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "OKX|Binance|Bybit|Bitget|Gate|Coinbase|Kraken|Crypto|Hyperliquid|WebSocket|RestTemplate|WebClient|HttpClient|OkHttp|apiKey|secret|passphrase|token|private key|LIVE|RealClient|permission-probe|NoReal|scheduler|recovery|monitor" backend .github docs/current
```

Implementation validation for Batch 3B/3C:

```powershell
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest -Dnq.app.context.smoke.required=true
```

CI validation must additionally prove the denylist guard runs and fails closed. First-run evidence belongs to Batch 3D review.

## Boundary confirmation

- Batch 3B 是 IMPLEMENTED / PENDING FIRST CI RUN。
- 已修改 workflow 与 backend/nq-app test-scope guard / smoke test。
- 未修改 backend production code / migration / frontend / research / scripts / deploy。
- 未新增 API。
- 未读取或输出真实 credential material。
- 未调用真实交易所。
- 未开启 LIVE / AI / DH runtime。
- 未实现 RealClient / real provider / real OKX / Binance permission probe adapter。
- Batch 4 / Batch 5 仍 PENDING。

## Review decision

IMPLEMENTED / PENDING FIRST CI RUN。P0/P1 blockers: 0。

本轮已完成最小 CI no-outbound guard baseline；尚未取得 GitHub Actions first run evidence，不能冻结。

## Next concrete action

Next concrete action: `NQ-CI-NO-OUTBOUND-GUARD-BATCH-3-FIRST-RUN-REVIEW`, `NQ-CI-NO-OUTBOUND-GUARD-BATCH-3-FIRST-RUN-FIX`, or pause the CI line.
