# GateK Architecture Baseline Review

任务：GATEK-ARCHITECTURE-BASELINE-REVIEW
日期：2026-06-14
状态：REVIEW COMPLETED / BASELINE ACCEPTED WITH P2 FOLLOW-UP
当前阶段：GateJ completed；Next: GateK-PLAN；GateK implementation NOT STARTED；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；Multi-exchange expansion NOT STARTED；real exchange permission probe adapter NOT IMPLEMENTED。

## 1. Current Architecture Baseline

NexusQuant 当前架构可以支撑 GateK 后续 planning / architecture / productization / deployment / observability / security boundary 收口，但 GateK implementation 仍未启动。本轮只做只读审查和文档落档，未修改 Java、TypeScript、Python、测试代码、部署脚本或 migration。

当前可接受基线：

- Backend：Java 21 + Spring Boot 3.5.x + Maven 多模块；`nq-app` 作为 composition root，`nq-api` 承载 HTTP 边界，`nq-core` 承载 domain/application/port，`nq-infra` 承载 JDBC/Flyway/infra implementation，`nq-adapter-api` 与 `nq-adapter-okx` / `nq-adapter-binance` 形成 adapter contract / implementation 边界。
- Frontend：React 19 + TypeScript + Vite 8 + React Router + TanStack Query + Axios + Zustand + Ant Design；NQ Console Design System v1 已形成 `frontend/src/components/nq/*`、theme token、状态 Tag、风险 Banner、PageHeader、DataTable 等统一基线。
- Research：`research/py` 仍为独立 Python 离线研究工具链，使用 `pyproject.toml` 管理 pytest / mypy / ruff，不作为 Java/Python runtime bridge，不进入 live trading / auth / recovery / ledger 主链。
- Docs：`docs/current` 仍是当前事实源；README / STATUS / ROADMAP / GATEK_PLAN 的主阶段口径一致。
- Security：PAPER / LIVE、credential material、permission probe、DH Integration-0 和 no-outbound 边界已有明确文档与部分测试护栏；真实 adapter / LIVE / DH runtime / AI 均不得直接进入 implementation。

## 2. Backend Module Boundary

### 2.1 Module Layering

| Module | Current role | GateK review judgment |
| --- | --- | --- |
| `nq-app` | Spring Boot startup、profile、Bean wiring、composition root | 可接受。`nq-app/pom.xml` 汇聚 API/Core/Infra/Adapters/Auth/Security/Gateway/Observability。后续 GateK 不应把业务规则继续放回 app。 |
| `nq-api` | Controller、DTO、HTTP adapter、OpenAPI annotations | 可接受。只读扫描未发现 `JdbcTemplate` / SQL literal / `DataSource` 直接使用；ArchUnit 已覆盖 API 不依赖 JDBC。 |
| `nq-core` | Domain、application service、port、policy | 可接受。只读扫描未发现 `nq-core` main code 直接 import infra/JDBC；permission probe Service 只依赖 port。 |
| `nq-infra` | JDBC repository、query adapter、Flyway、infra wiring | 可接受。当前仍是 SQL / repository implementation owner。 |
| `nq-adapter-api` | Exchange adapter contracts | 可接受。平台语义与交易所实现仍有 contract 层。 |
| `nq-adapter-okx` / `nq-adapter-binance` | Exchange-specific adapters | 有条件可接受。当前仍不应扩展真实 permission probe adapter；OKX bootstrap no-outbound fix 已落档，但 future real adapter 必须单独审计。 |
| `nq-research` / `nq-backtest` / `nq-eval` | Java 研究、回测、评估链路 | 可接受。后续 GateK 页面和 CI baseline 应按 owner 分组验证。 |
| `nq-risk` / `nq-ledger` / `nq-scheduler` / `nq-observability` | 风控、账本、调度、观测支撑 | 可接受。GateK-4 可观测性规划可基于现有 `nq-observability`，但不得直接做大规模性能重构。 |

### 2.2 Boundary Evidence

- `backend/pom.xml` 明确 22 个 Maven modules；`nq-app` 依赖所有运行时模块，是 composition root。
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/ModuleBoundaryArchTest.java` 已覆盖：
  - `nq-api` 不依赖 `org.springframework.jdbc..` / `..infra..jdbc..`。
  - trading application 不依赖 adapter API 或 runtime concrete。
  - `Jdbc*` class 必须位于 `..infra..`。
  - API source 不应包含 SQL keywords。
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java` 已覆盖：
  - domain package 不依赖 Spring。
  - main source package 不应 split package across modules。
- 本轮只读扫描：
  - `rg "import .*infra|springframework\.jdbc|javax\.sql|JdbcTemplate|DataSource" backend/nq-core/src/main/java backend/nq-api/src/main/java`：无命中。
  - `rg "JdbcTemplate|SELECT |INSERT |UPDATE |DELETE |FROM |INTO " backend/nq-api/src/main/java`：无命中。

### 2.3 Adapter / Paper / LIVE / Credential Baseline

- Adapter 仍应只做交易所适配，不定义平台交易主语义，不承载 credential governance 主流程。
- PAPER / LIVE 仍需硬隔离；当前文档和前端都应继续把 LIVE 表达为 disabled / warning，不得把 LIVE 作为可用能力。
- Credential permission probe 当前接受冻结的只是 guarded backend baseline：默认 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`；真实 OKX/Binance adapter NOT IMPLEMENTED。
- `permission_scope = NULL` 仍不得视为 `TRADE`；permission probe summary 不得被 AI、DH、LIVE 或交易链路直接当作授权依据。

### 2.4 Backend GateK Readiness

结论：Backend 模块边界可支撑 GateK-2 / GateK-4 / GateK-5 后续规划与逐步实现。当前未发现必须在 GateK implementation 前阻塞修复的 P0/P1 模块依赖倒挂。

## 3. Frontend Baseline

### 3.1 Stack And Data Boundary

- `frontend/package.json` 确认当前 stack：React 19、Vite 8、Ant Design 5、TanStack Query 5、Axios、Zustand、Playwright、TypeScript。
- `frontend/src/app/providers/AppProviders.tsx` 统一配置 `ConfigProvider`、TanStack Query、auth bootstrap 和全局 API error notification。
- `frontend/src/api/*` 是 HTTP client 封装入口；`frontend/src/router/navigation.tsx` 是页面导航矩阵。
- `frontend/playwright.config.ts` 使用 Chromium、单 worker、Vite dev server、`baseURL=http://127.0.0.1:5179`，符合当前 E2E 基线。

### 3.2 NQ Console Design System

当前 Design System 基线可继续作为 GateK 前端统一基线：

- `frontend/src/components/nq/NqPageHeader.tsx`
- `frontend/src/components/nq/NqStatusTag.tsx`
- `frontend/src/components/nq/NqEnvironmentBadge.tsx`
- `frontend/src/components/nq/NqRiskBanner.tsx`
- `frontend/src/components/nq/NqMetricCard.tsx`
- `frontend/src/components/nq/NqDataTable.tsx`
- `frontend/src/theme/*`
- `frontend/src/styles/tokens.css`

GateK 后续页面产品化应继续使用 Ant Design + NQ components，不应引入 shadcn/Tailwind 大重构、拖拽式成熟交易工作区或 AI mock 页面。

### 3.3 Navigation / Misleading Entry Review

当前导航包含 Dashboard、账户管理、交易工作台、Instrument Catalog、Marketdata、策略定义、调度计划、运行记录、研究配置、回测配置、评估结果、发布结果、模拟交易。未发现 AI / DH runtime 页面入口。前端已在登录页、Dashboard、Paper Trading、Design System token 中多处表达 `PAPER ONLY` / `LIVE DISABLED` / `GateJ completed` / `Next: GateK-PLAN`。

需注意的 P2/P3 后续项：

- `TradingWorkbenchPage` 和 `AccountsPage` 仍展示 `LIVE` 作为环境字段或选择项；这不等于 LIVE enabled，但 GateK 前端产品化时应继续强化禁用态、风险提示和 server-side gate 对齐。
- Backtest / Strategy / Risk / MarketData / Monitor 后续施工前置基本清晰，但仍需要单独输出 `NQ-FRONTEND-GATEK-BUILD-MATRIX`，定义每页业务目标、数据契约、状态、风险操作、E2E 验收和禁止新增 API 的边界。

结论：Frontend baseline 可支撑 GateK-3 页面矩阵和 UI/UX remediation 规划；当前未发现 P0/P1 误导入口。

## 4. Research Baseline

`research/py` 仍是独立离线研究工具链：

- `research/py/pyproject.toml`：Python >= 3.11，dev 依赖为 pytest / mypy / ruff。
- `research/py/README.md` 明确不接入 live trading / auth / recovery / ledger 主链，不作为 Java / Python runtime bridge。
- `research/py/tests/*` 当前是轻量 CLI / sample strategy baseline。

GateK CI baseline 应纳入：

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

结论：Research 边界清晰；未发现 backend runtime 直接耦合风险。P2 风险是 Windows 本机 Python alias 曾导致历史验证失败，CI 需要固定真实 Python 解释器和 dependency install。

## 5. Docs / Facts Baseline

### 5.1 Current Fact Alignment

README、`docs/current/README.md`、`docs/current/STATUS.md`、`docs/current/ROADMAP.md`、`docs/current/GATEK_PLAN.md` 当前主事实一致：

- GateJ completed。
- Next: GateK-PLAN。
- GateK implementation not started。
- AI not started。
- DH integration not started / not connected to NQ；DH runtime not integrated。
- LIVE disabled。
- Multi-exchange expansion not started。
- real exchange permission probe adapter not implemented。

阶段误写扫描：

```powershell
rg "GateK implementation|AI started|DH integrated|LIVE enabled|real adapter implemented" README.md AGENTS.md CLAUDE.md docs/current
```

命中项为否定式、禁止说明、风险说明或历史语境；本轮未发现当前事实源把 GateK implementation、AI、DH runtime、LIVE 或 real adapter 写成已启动/已实现。

### 5.2 P2 Documentation Drift

本轮发现 `docs/current/ARCHITECTURE.md` 和 `docs/current/MODULES.md` 存在旧阶段残留：

- `ARCHITECTURE.md` 仍写有“尚未完成虚拟币 V1 闭环”等旧表述，与当前 GateI/GateJ completed 事实不完全一致。
- `MODULES.md` 仍写有“后续 GateH-PLAN”“当前阶段不新增 GateH 业务实现”等历史措辞。
- 这些文档不改变 README / STATUS / ROADMAP / GATEK_PLAN 的当前主事实，但作为 `docs/current` 文件，GateK implementation 前应单独做 `GATEK-ARCH-DOC-SYNC` 或并入 `GATEK-DOC-FACT-SYNC` 的 follow-up。

结论：Docs 当前主事实可接受；P2 文档漂移不阻塞 architecture baseline review，但应在 GateK implementation 前收口。

## 6. Test Baseline

### 6.1 Current Baseline Commands

Backend：

```powershell
mvn -f backend/pom.xml test
```

Frontend：

```powershell
Set-Location frontend
npm run build
npm run test:e2e
```

Research：

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

### 6.2 Tests Required For GateK CI Baseline

P0 CI baseline candidates:

- Backend full Maven: `mvn -f backend/pom.xml test`。
- Backend architecture tests: `ModuleBoundaryArchTest`、`PackageBoundaryArchTest` must remain part of full Maven。
- Permission probe no-real-exchange baseline: Service / WebMvc / JDBC / NoReal / adapter boundary tests must stay in full Maven。
- Frontend build: `npm run build`。
- Frontend E2E smoke: `npm run test:e2e` with documented backend dependency and stable local port 5179。
- Research quality: `python -m pytest -q`、`python -m mypy src`、`python -m ruff check .`。

P1 no-outbound hardening candidates:

- OKX bootstrap no-outbound local context test must stay active.
- Permission probe no-real-exchange guard must stay active.
- Future real adapter requires fake-server / no-egress tests before any implementation.
- Full Maven logs should not include real private endpoint access, credential material, order/cancel/transfer/withdraw calls, or uncontrolled public exchange bootstrap.

Skip policy:

- Skips are allowed only when tied to explicit environment fixture absence, for example `E2E_TRADE_ORDER_ID` or missing seeded research/backtest records.
- Skips cannot be used to claim unverified order detail, backtest binding, LIVE, DH runtime, AI, real adapter, or no-outbound behavior as passed.

### 6.3 CI / Workflow Gap

`git ls-files .github/workflows` currently returns no tracked workflows. This is not a GateK architecture P0/P1 blocker, but it means `NQ-CI-BASELINE-PLAN` is a required next task before GateK implementation claims repeatable CI coverage.

## 7. Security Baseline

### 7.1 Accepted Security Baseline

- PAPER / LIVE：LIVE remains disabled；LIVE-related work requires separate security review。
- Credential material：API response / audit metadata must remain masked; no raw request, raw response, headers, signature, API key, secret, passphrase, private key, token, cookie, mnemonic, or credential material may be logged or returned。
- Permission probe：guarded backend baseline accepted; default no-real port returns `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`; real OKX/Binance adapter not implemented。
- No-outbound：OKX bootstrap eager refresh issue has a documented fix and no-outbound tests; future adapter/probe work must preserve no-egress testing.
- DH Integration-0：contract / mock / stub / contract test / security docs only; no runtime integration, no RealClient, no provider, no real HTTP, no NQ DB read/write, no credential access, no trading side effect.
- Deployment：freeze compose uses required env vars, local app binding for backend health, internal postgres network, and log volumes; future deployment/log sink work must keep secrets out of env examples, logs, release artifacts and external sinks.

### 7.2 Security Risks

| Severity | Finding | Decision |
| --- | --- | --- |
| P0 | None. No evidence of AI/DH runtime/LIVE/real adapter started in current baseline. | Not blocking. |
| P1 | None. No module boundary or security baseline issue blocks GateK planning continuation. | Not blocking. |
| P2 | `docs/current/ARCHITECTURE.md` / `MODULES.md` still contain older Gate wording; could confuse future workstream entry if read alone. | Follow-up doc sync required before implementation tasks rely on those files. |
| P2 | No tracked `.github/workflows`; CI baseline is not yet executable in repository. | `NQ-CI-BASELINE-PLAN` required before CI implementation. |
| P2 | Vite chunk > 500 kB warning remains known frontend build risk. | Track under frontend/productization or performance baseline; not a review blocker. |
| P2 | Real adapter / no-egress / fake-server gates remain future work. | Required before any real permission probe adapter implementation. |
| P3 | CODEOWNERS still contains placeholder `@YOUR_GITHUB_USERNAME`. | Replace during CI/GitHub governance setup; not a runtime blocker. |

## 8. GateK Workstream Mapping

| Workstream | Baseline status | Required next task |
| --- | --- | --- |
| GateK-1 Facts / roadmap | Main facts aligned; ARCHITECTURE/MODULES have P2 drift | `GATEK-ARCH-DOC-SYNC` or follow-up `GATEK-DOC-FACT-SYNC` |
| GateK-2 Architecture / test baseline | Backend/frontend/research boundaries acceptable | `NQ-CI-BASELINE-PLAN` |
| GateK-3 Frontend productization | Design System v1 accepted; page matrix still needed | `NQ-FRONTEND-GATEK-BUILD-MATRIX` |
| GateK-4 CI / observability / deployment | No tracked CI workflow; deploy freeze baseline documented | `NQ-CI-BASELINE-PLAN` -> `NQ-OBSERVABILITY-BASELINE-PLAN` -> `NQ-DEPLOYMENT-BASELINE-PLAN` |
| GateK-5 Security / credential / no-outbound | No-real permission probe accepted; OKX no-outbound documented | `NQ-CREDENTIAL-NO-EGRESS-TEST-PLAN` and real adapter design review only |
| GateK-6 NQ-DH Integration-0 | Contract / mock / tests accepted; runtime still not integrated | `NQ-DH-INT0-GATEK-REGISTRATION` and Integration-1 planning-only audit |

## 9. Required Next Tasks

Recommended executable order:

1. `GATEK-ARCH-DOC-SYNC`：docs/current `ARCHITECTURE.md` / `MODULES.md` 阶段措辞同步，保持 docs-only。
2. `NQ-CI-BASELINE-PLAN`：定义 minimal CI commands、cache、service dependencies、skip policy、artifact/log redaction and failure gates。
3. `NQ-FRONTEND-GATEK-BUILD-MATRIX`：输出 Backtest / Strategy / Risk / MarketData / Monitor 页面施工矩阵，不新增后端契约。
4. `NQ-OBSERVABILITY-BASELINE-PLAN`：定义 metrics、traceId、slow API / SQL / scheduler thresholds，planning-only。
5. `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW` follow-up：确认 no-outbound regression remains in CI baseline。
6. `NQ-DEPLOYMENT-BASELINE-PLAN`：部署、健康检查、回滚、日志脱敏和 secret injection plan。
7. `NQ-CREDENTIAL-PERMISSION-PROBE-REAL-ADAPTER-DESIGN-REVIEW`：只读设计审计；实现前必须先有 fake-server/no-egress tests。
8. `NQ-DH-INT0-GATEK-REGISTRATION`：只登记 contract line，不做 runtime。

## 10. Explicit Non-Goals

本轮不做且后续不得由本报告隐含授权：

- 不实现 GateK 功能。
- 不修改 Java / TypeScript / Python 代码。
- 不新增 API、Controller、Service、Repository、Adapter 或 migration。
- 不修改历史 migration。
- 不修改测试代码。
- 不修改部署脚本。
- 不实现 AI、AI signal、AI Paper Trading 或 AI runtime。
- 不实现 DH runtime integration、NQ RealClient、真实 Provider、真实 HTTP channel。
- 不实现真实 OKX/Binance permission probe adapter。
- 不开启 LIVE，不下单、不撤单、不转账、不提现。
- 不读取、打印、复制或输出真实 credential material。
- 不引入 shadcn/Tailwind 大重构、拖拽交易工作区或 AI mock 页面。

## 11. Validation Commands

本轮 review-only 验证以 Git diff、禁止范围 diff 和阶段/安全措辞扫描为准；未运行 backend/frontend/Python build/test，因为本轮只修改文档。

必须执行：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
```

只读审查已使用：

```powershell
git ls-files backend | Select-Object -First 40
git ls-files frontend | Select-Object -First 40
git ls-files research | Select-Object -First 40
git ls-files .github deploy scripts | Select-Object -First 80
git ls-files .github/workflows
rg "GateK implementation|AI started|DH integrated|LIVE enabled|real adapter implemented" README.md AGENTS.md CLAUDE.md docs/current
rg "TODO|FIXME|P0|P1|no-outbound|LIVE|credential|permission probe|Integration-0" docs/current backend frontend research
```

## 12. Review Decision

Review decision：ACCEPTED WITH P2 FOLLOW-UP。

P0=0，P1=0。当前 NQ architecture baseline 可以作为 GateK 后续 workstream 的审查基线，但不等于 GateK implementation started。进入任何 implementation 前，必须先关闭或明确登记 P2 follow-up：docs/current architecture/module wording sync、minimal CI baseline plan、frontend page matrix、no-egress/fake-server plan、observability/deployment planning 和 DH Integration-0 registration。

Next concrete action：执行 `GATEK-ARCH-DOC-SYNC` 或 `NQ-CI-BASELINE-PLAN`，仍保持 review/planning-only；不得直接进入 GateK implementation、AI、DH runtime、LIVE 或真实 adapter。
