## NQ-GATEL-1B-NO-REAL-HARDENING-PLAN-REVIEW（2026-06-22）

结论：**PASS / ACCEPTED AS PLAN REVIEW BASELINE；REVIEW ONLY / PLAN ONLY / NOT IMPLEMENTED**。四项 P1 仍 OPEN / RETAINED。

- 已执行：预检 `Get-Location` / `git status --short` / `git branch --show-current` / `git log --oneline -5`；分支 `dev`，审查前 working tree clean。
- 已执行：只读复核 GateL-1B plan、GateL-1 review/freeze、GateL plan 与 current 状态文档。
- 已执行：定向核对 Binance endpoint default、OKX/Binance process credential source、order `rawPayload` 与 Noop marketdata success 四项 P1 仍存在。
- 已确认：A/B/C/D 均有测试、验收、回滚；A/B 拆开；C producer suppression 与字段删除拆开；D 复用现有 contract，不新增 DTO/API；无需 migration。
- 未执行：Maven/frontend/Python；原因是本轮 docs-only，implementation NOT STARTED。
- 未访问网络、交易所、DB、容器或 GitHub Actions；未读取 credential material。

下一步仅 `NQ-GATEL-1B-NO-REAL-HARDENING-PLAN-FREEZE`。Freeze 通过前不得进入 1B-A；adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED；LIVE/AI/DH runtime 未启用或未接入。

---

## NQ-GATEL-1B-NO-REAL-HARDENING-PLAN（2026-06-22）

结论：**PASS / PLAN READY FOR REVIEW；PLANNING ONLY / NOT IMPLEMENTED**。本轮仅规划 A-D，不修代码，不关闭 P1。

- 已执行：预检 `Get-Location` / `git status --short` / `git branch --show-current`；分支 `dev`，预检 working tree clean。
- 已执行：只读核对 GateL-1 review/freeze/plan/current docs，以及 adapter-api/OKX/Binance 白名单源码与相关测试结构。
- 已确认：现有 `MarketDataSubscriptionAck + AdapterError` 能表达 `subscribed=false / NO_REAL_DISABLED / retryable=false`，无需新增 HTTP API；四项均无需 migration。
- 已规划：A endpoint、B credential source、C raw payload、D Noop status 的测试、验收、回滚和分批 review gate。
- 未执行：Maven/frontend/Python；原因是本轮 docs-only、implementation NOT STARTED。
- 未访问网络、交易所、DB、容器、GitHub Actions；未读取 credential material。

下一步仅 `NQ-GATEL-1B-NO-REAL-HARDENING-PLAN-REVIEW`。P1/P2 OPEN；adapter readiness NOT READY；LIVE/AI/DH runtime 未启用或未接入。

---

## NQ-GATEL-1A-EXCHANGE-ADAPTER-CONTRACT-REVIEW-FREEZE（2026-06-22）

结论：**PASS / REVIEW FACT BASELINE FROZEN / ACCEPTED**。冻结的是 GateL-1 review 事实、P1/P2 与处理顺序；adapter readiness = **NOT READY / NOT FROZEN**，P1/P2 全部 OPEN / RETAINED。

- 已执行：`Get-Location`、`git status --short`、`git branch --show-current`；分支 `dev`，预检 working tree clean。
- 已执行：只读复核 GateL-1 review、GateL plan、current 状态文档。
- 已执行：白名单文件定向 `rg -n`，确认 Binance 默认外部 endpoint、OKX/Binance process credential parsing、`rawPayload`、Noop marketdata success 四项 P1。
- 已执行：Markdown links、stage wording、P1/P2 retained、follow-up order、docs-only scope、secret value pattern、`git diff --check`。
- 未执行 Maven/frontend/Python：本轮 docs-only，无 runtime 代码变更。
- 未访问网络、真实交易所、DB、容器、GitHub Actions；未读取 credential material。

下一步：`NQ-GATEL-1B-NO-REAL-HARDENING-PLAN`；不得直接实现。GateL implementation NOT STARTED；LIVE DISABLED；AI NOT STARTED；DH runtime NOT INTEGRATED。

---

## NQ-GATEL-1-EXCHANGE-ADAPTER-CONTRACT-REVIEW（2026-06-22）

结论：**CONDITIONAL PASS / DOCS-CONTRACT ONLY**。本轮仅文档审查，未改代码、API、migration、workflow 或运行配置。

- 已执行：`Get-Location`、`git status --short`、`git branch --show-current`；分支 `dev`，审查前 working tree clean。
- 已执行：允许模块内 `rg --files`、符号/调用点检索、关键 adapter/core/risk/ledger/API 文件逐行只读核对。
- 已执行：current docs 路径/链接、GateL/GateM/LIVE/AI/DH/RealClient 状态文案、diff 范围、whitespace 检查。
- 未执行：Maven、frontend build/E2E、Python tests。原因：本轮 docs-only，无 runtime 代码变更。
- 未执行：网络、真实交易所、数据库、容器、GitHub Actions；未读取 credential material。
- 过程偏差：一次探索性 `rg` 误用 `backend` 根目录，返回白名单外少量文件名/命中行；未打开这些文件、未读取敏感路径/值，结论证据仅采用允许模块。后续检索已恢复白名单范围。
- 验证结论：P0=0；P1=4；review 交付可条件通过，现有 adapter contract 不具备 future-real readiness。

边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；GateL implementation NOT STARTED。

---

## NQ-GATEK-POST-FREEZE-HANDOFF-PLAN（2026-06-22）

结论：**NQ-GATEK-POST-FREEZE-HANDOFF-PLAN = PASS / READY FOR NEXT PHASE**；**NEXT PHASE = READY TO PLAN**。docs-only handoff，未跑新构建、未触发新 GitHub Actions（仅引用既有 green evidence），未改代码 / workflow / 配置 / 测试。

引用既有 CI evidence（只读，不新增）：

| 收口项 | commit / run | 结论 |
| --- | --- | --- |
| GateK CI/security final freeze | `8d126f9f` | FROZEN / ACCEPTED |
| OKX bootstrap no-outbound freeze | `8a2fbe4a` | FROZEN / ACCEPTED |
| endpoint defense impl + CI | `c749cef7` / run `27926903155`（9 jobs success） | IMPLEMENTED / CI GREEN |
| endpoint defense addendum | `7d9330c3` | FROZEN / ACCEPTED；P2 CLOSED |
| Batch 5B-SMOKE evidence | run `27903497008`（9 jobs success） | FROZEN / ACCEPTED |
| Batch 5B-ENV evidence | run `27876451289`（8 jobs success） | FROZEN / ACCEPTED |

测试边界口径（frozen，未变）：no-outbound guard + EnvSafetyValidator + NoReal probe + OKX runtime sentinel default + test/ci/paper/local no-real + secret scan/redaction + frontend no-backend E2E。Findings：P0=0；P1=0；P2=CLOSED；P3=1（`application-ci.yml`/`application-paper.yml` 命名差异，非阻断）。无阻断项进入下一阶段规划。详见 `NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md`。

边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。

---

## NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-POST-FREEZE-ADDENDUM（2026-06-22）

结论：**NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE = FROZEN / ACCEPTED**；**P2 OkxRuntimeConfig default real endpoint defense = CLOSED / ACCEPTED**。docs-only addendum，未改代码 / workflow / 配置 / 测试。

CI evidence（target run，只读复核）：

| 字段 | 值 |
| --- | --- |
| run ID | `27926903155` |
| workflow | `NQ CI Baseline` |
| event / branch | push / dev |
| headSha | `c749cef7b9731284208acccadf321cf89c5e4fbe`（= fix commit `c749cef7`） |
| status / conclusion | completed / **success** |
| jobs | 9/9 success：diff-check / no-outbound-guard / ci-security-smoke / backend / postgres-flyway / frontend / frontend-no-backend-e2e / research / secret-scan |

Test evidence（CI backend job + 本地复核）：`OkxRuntimeConfigTest` success（4/0/0/0）、`OkxExchangeAdapterBootstrapNoOutboundTest` success（1/0/0/0）、`NoRealExchangeCredentialPermissionProbePortTest` success（1/0/0/0）、`EnvSafetyValidatorTest` success（8/0/0/0）、`NoOutboundExchangeGuardTest` success（3/0/0/0）、`OkxBootstrapNoOutboundLocalContextTest` success（1/0/0/0）、full backend `mvn test` BUILD SUCCESS —— sentinel 默认值未导致构造期失败、启动期 0 outbound。

Findings：P0=0；P1=0；**P2=CLOSED / ACCEPTED**；P3=1（`application-ci.yml`/`application-paper.yml` 命名差异，非阻断）。详见 addendum 卷宗 `NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_ADDENDUM.md`。

边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。

---

## NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-IMPL（2026-06-22）

结论：**NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE = IMPLEMENTED / PENDING CI RUN**。Path A 实施：`OkxRuntimeConfig` 默认 endpoint 改为 `disabled://` sentinel。未改 `EnvSafetyValidator` / workflow / `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy。

本地验证（2026-06-22）：

| 命令 / 测试 | 结果 |
| --- | --- |
| `mvn -pl nq-adapter-okx -am test -Dtest=OkxRuntimeConfigTest,OkxExchangeAdapterBootstrapNoOutboundTest -Dsurefire.failIfNoSpecifiedTests=false` | `OkxRuntimeConfigTest` 4/0/0/0 + `OkxExchangeAdapterBootstrapNoOutboundTest` 1/0/0/0，BUILD SUCCESS |
| `mvn -pl nq-app,nq-infra -am test -Dtest=NoRealExchangeCredentialPermissionProbePortTest,EnvSafetyValidatorTest,NoOutboundExchangeGuardTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.no-outbound.guard.required=true` | NoReal 1/0/0/0 + EnvSafety 8/0/0/0 + NoOutbound 3/0/0/0（11/0/0/0），BUILD SUCCESS |
| `mvn -f backend/pom.xml test`（全量） | **BUILD SUCCESS**，0 fail / 0 error；含 `OkxBootstrapNoOutboundLocalContextTest` 1/0/0/0、`MarketdataControllerLocalIntegrationTest` 1/0/0/0、`ResearchBacktestHappyPathLocalTest` 1/0/0/0 绿（既有条件性 skip：live-diagnostic / postgres-smoke-required / CI-guard-required env-absence assumeTrue，未变） |
| 静态 grep `git grep -F -e "https://www.okx.com" -e "wss://wspap.okx.com" -e "wss://ws.okx.com" -- backend/nq-adapter-okx docs/current` | 仅命中 `OkxRuntimeConfigTest`（显式 env override 用例）+ `docs/current` 历史/说明文档；无真实默认常量 |
| 静态 grep `git grep -F -e "disabled://okx-not-configured" -e "disabled://okx-ws-not-configured" -- backend/nq-adapter-okx docs/current` | 命中 `OkxRuntimeConfig.java:47-49`（默认常量）+ `OkxRuntimeConfigTest`（sentinel 断言）|
| 禁止范围 diff | ci.yml / migration / frontend / research / scripts / deploy / `.env.example` / `application*.yml` / `EnvSafetyValidator` / `NoOutboundExchangeGuardTest` 全空 |

Findings：P0=0；P1=0；P2=IMPLEMENTED / PENDING CI RUN；P3=1（`application-ci.yml`/`application-paper.yml` 命名差异，非阻断）。CI 真实运行待 `NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-CI-RUN-REVIEW` 采证；之后以 post-freeze addendum 触发 `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND` 复审 + freeze addendum。详见 `NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md`。

边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。

---

## NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-FREEZE（2026-06-22）

结论：**NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND = FROZEN / ACCEPTED**。docs-only freeze，未跑新构建（沿用复审轮本地证据）、未触发新的 GitHub Actions、未改 workflow / backend / Java / TS / Python / `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy、未新增/未修改测试、未修复 P2。冻结 review commit `0b9c0b20`（review HEAD `e3b12e33`）。

Test evidence（复审轮本地只读复核，CI / no-outbound 环境，无真实外联、无真实凭证读取）：

| 测试 | 结果 |
| --- | --- |
| `NoRealExchangeCredentialPermissionProbePortTest` | 1/0/0/0 |
| `EnvSafetyValidatorTest` | 8/0/0/0 |
| `NoOutboundExchangeGuardTest` | 3/0/0/0（0 skipped，CI-required env-absence 断言执行通过） |
| 构建 | `BUILD SUCCESS` |

Findings：P0=0；P1=0；P2=1（非阻断，`OkxRuntimeConfig` 代码级真实 host 默认值未纳入启动期 `EnvSafetyValidator` endpoint 校验，转 backlog **NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-PLAN**，本轮不修复）；P3=1（非阻断，`application-ci.yml` / `application-paper.yml` 命名差异）。Regression boundary：后续改动 OKX runtime config / exchange adapter construction / no-outbound guard / EnvSafetyValidator / profile defaults / permission probe / CI env guard 须重新 review + freeze。详见 freeze 卷宗 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md`。

边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe（NoReal probe remains SKIPPED）。

---

## NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW-COMMIT-GATE（2026-06-22）

结论：**NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND = PASS / READY FOR FREEZE**。docs-only commit gate，未改 workflow / backend / Java / TS / Python / `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy、未新增/未修改测试、未 freeze、未修复 P2。复审 HEAD `e3b12e33`，分支 `dev`，复审前 working tree clean。

本地只读复核测试（CI / no-outbound 环境，无真实外联、无真实凭证读取）：

| 命令 / 测试 | 结果 | 证据 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-app,nq-infra -am test -Dtest=NoRealExchangeCredentialPermissionProbePortTest,EnvSafetyValidatorTest,NoOutboundExchangeGuardTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.no-outbound.guard.required=true` | **通过** | `NoRealExchangeCredentialPermissionProbePortTest` 1/0/0/0 + `EnvSafetyValidatorTest` 8/0/0/0 + `NoOutboundExchangeGuardTest` 3/0/0/0（0 skipped，CI-required env-absence 断言已执行通过），`BUILD SUCCESS`。 |
| `git status --short` / `git diff --check` / 禁止范围 diff | **通过** | 复审轮 working tree clean；commit gate 轮仅 `docs/current/*` diff；`.github/workflows/ci.yml` / `backend` / `backend/**/db/migration` / `frontend research scripts deploy` / `.env.example` 均无 diff。 |

Findings：P0=0；P1=0；P2=1（`OkxRuntimeConfig` 代码级真实 host 默认值未纳入启动期 `EnvSafetyValidator` endpoint 校验，非阻断纵深防御项，后续单独任务，本轮不修复）；P3=1（`application-ci.yml` / `application-paper.yml` 命名预期差异，CI 以 `CI=true` + test/no-outbound 语义生效，非阻断）。详见 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md` §13。

边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe（NoReal probe remains SKIPPED）。

---

## NQ-CI-SECURITY-FINAL-FREEZE-GATE（2026-06-21）

结论：**GateK CI/security = FROZEN / ACCEPTED**。docs-only freeze，未跑本地构建、未触发新的 GitHub Actions（仅只读复核既有 green run），未改 workflow / backend / migration / frontend / research / scripts / deploy / `.env.example`、未新增测试。

evidence（只读复核 success）：5B-SMOKE run `27903497008`（headSha `9b467fbc` / 9 jobs all success，ci-security-smoke 内 12 tests / 0 fail）、5B-ENV run `27876451289`（headSha `8ba140d9`）、docs-only freeze run `27904207910`（headSha `3158e8ad`）。

Batch matrix：Batch 1 green；Batch 2A–2E / 3 / 4B / 4C / 4F-A / 5A / 5B-ENV / 5B-SMOKE = FROZEN / ACCEPTED；Batch 5B = CLOSED / ACCEPTED；4F-B..4F-F / static assertion = OPTIONAL BACKLOG / NOT IMPLEMENTED（NOT BLOCKING）。

边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe（NoReal probe remains SKIPPED）。

---

## NQ-CI-SECURITY-BATCH-5B-SMOKE-FREEZE（2026-06-21）

结论：**Batch 5B-SMOKE = FROZEN / ACCEPTED**；**Batch 5B = CLOSED / ACCEPTED**；Freeze = FROZEN / ACCEPTED。docs-only freeze，未跑本地构建、未触发新的 GitHub Actions、未改 workflow / backend / migration / frontend / research / scripts / deploy / `.env.example`、未新增测试、未补修 implementation。

冻结依据：implementation commit `9b467fbc` + first run evidence run `27903497008`（NQ CI Baseline / push / headSha `9b467fbc21e3ce685572dc3ec84104fd945fa0fb` / completed / success），9 jobs 全 success，ci-security-smoke 内 12 tests / 0 fail（NoReal 1 + EnvSafety 8 + NoOutbound 3），NoReal permission probe remains SKIPPED。`.github/workflows/ci.yml` 自 `9b467fbc` 后未变。

边界：No real credential read；No outbound call；No LIVE；No Paper / DH / AI runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe；freeze 无 DB / runtime / credential / provider / exchange 副作用。

---

## NQ-CI-SECURITY-BATCH-5B-SMOKE-FIRST-RUN-EVIDENCE（2026-06-21）

结论：**First run evidence = PASS / READY FOR REVIEW**；Freeze = NOT STARTED。CI run 取证，未跑本地构建、未补修实现、未改 workflow / backend / migration / frontend / research / scripts / deploy / `.env.example`、未新增测试。

run：`NQ CI Baseline` / push / dev / completed / success，run ID `27903497008`，headSha `9b467fbc21e3ce685572dc3ec84104fd945fa0fb`，URL `https://github.com/ling5477/nexus-quant/actions/runs/27903497008`（createdAt 2026-06-21T11:54:52Z / updatedAt 2026-06-21T11:56:34Z）。

9 jobs 全部 success：diff-check / no-outbound-guard / ci-security-smoke / backend / postgres-flyway / frontend / frontend-no-backend-e2e / research / secret-scan。

ci-security-smoke job 内 smoke 测试（CI 实跑，非本地）：

- `NoRealExchangeCredentialPermissionProbePortTest`（nq-infra）：Tests run 1，Failures 0，Errors 0，Skipped 0。
- `EnvSafetyValidatorTest`（nq-app）：Tests run 8，Failures 0，Errors 0，Skipped 0。
- `NoOutboundExchangeGuardTest`（nq-app）：Tests run 3，Failures 0，Errors 0，Skipped 0。
- 合计 12 tests / 0 failures；BUILD SUCCESS。

边界：NoReal permission probe remains SKIPPED；No real credential read；No outbound call；No LIVE；No Paper / DH / AI runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。

---

## NQ-CI-SECURITY-BATCH-5B-SMOKE-IMPLEMENTATION（2026-06-21）

结论：**IMPLEMENTED / READY FOR REVIEW**。Batch 5B-SMOKE = IMPLEMENTED / READY FOR REVIEW；Implementation = DONE / READY FOR REVIEW；First run evidence = NOT STARTED；Freeze = NOT STARTED。

实现范围：`.github/workflows/ci.yml` 新增独立最小 `ci-security-smoke` job（CI env-name assertion step + 复用既有安全 smoke 测试），未新增业务测试、未引入真实 adapter / provider / exchange client、未修改 migration / frontend / research / scripts / deploy / `.env.example`。

本地最小验证命令与结果（跨 nq-app + nq-infra 两个 module，未触发 GitHub Actions）：

    mvn -f backend/pom.xml -pl nq-app,nq-infra -am test -Dtest=EnvSafetyValidatorTest,NoOutboundExchangeGuardTest,NoRealExchangeCredentialPermissionProbePortTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.no-outbound.guard.required=true

- `EnvSafetyValidatorTest`（nq-app）：8/0/0/0（fail-closed 矩阵 + placeholder credential safe/unsafe）。
- `NoOutboundExchangeGuardTest`（nq-app）：3/0/0/0（denylist host fail-closed + localhost 放行 + CI env-name 断言）。
- `NoRealExchangeCredentialPermissionProbePortTest`（nq-infra）：1/0/0/0（NoReal probe 返回 SKIPPED / REAL_EXCHANGE_PROBE_DISABLED，不解析 / 不连接真实交易所 host）。
- 合计 **12 tests / 0 failures**。

边界声明：NoReal permission probe remains SKIPPED；No real credential read；No outbound call；No LIVE；No Paper / DH / AI runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe；未运行或触发 GitHub Actions（first run evidence 仍 NOT STARTED）。

---

## NQ-CI-SECURITY-BATCH-5B-SMOKE-IMPLEMENTATION-PLAN（2026-06-21）

结论：**IMPLEMENTATION PLAN READY / READY FOR REVIEW**。docs-only implementation plan，本轮未执行 implementation，未新增 CI job，未新增测试，未修改 workflow / backend / migration / frontend / research / scripts / deploy / `.env.example`，未运行或触发 GitHub Actions。

状态：Batch 5B-ENV = **FROZEN / ACCEPTED**；Batch 5B-SMOKE-PREFLIGHT = **REVIEWED / ACCEPTED**；Batch 5B-SMOKE implementation = **NOT STARTED**；next job name = **ci-security-smoke**；P2 已转化为 implementation execution checklist；P3 job name drift 已关闭。

本轮验证范围：文档路径、阶段状态、禁止边界、入口一致性和 scoped diff。未运行 Maven / npm / pytest / GitHub Actions，原因是本轮只改 docs-current planning/status 文档，不改代码、workflow、测试、migration 或运行时配置。

边界声明：NoReal permission probe remains SKIPPED；No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。

---

## NQ-CI-SECURITY-BATCH-5B-SMOKE-PREFLIGHT-PLAN（2026-06-21）

结论：**PASS / READY FOR REVIEW**。docs-only preflight / plan，未跑后端 Maven、前端 build/e2e、Python pytest/mypy/ruff；原因是本轮明确禁止实现 smoke、修改 workflow/code/config/migration 或启动真实外联。

状态：Batch 5B-ENV = **FROZEN / ACCEPTED**；Batch 5B-SMOKE = **PLANNED / NOT STARTED**。

本地只读验证：

| Command | Result |
| --- | --- |
| `git status --short` | 仅 `docs/current` 计划文档变更与新增 `NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md`。 |
| `git diff --check` | 先发现 docs EOF 空行，已最小修复并重跑；最终 exit 0（如出现 LF/CRLF warning，不作为阻塞项）。 |
| `git diff --stat` | 仅 `docs/current` 状态 / 计划文档统计变更。 |
| `git diff -- "backend/**/db/migration"` | 空。 |
| `git diff -- frontend research scripts deploy` | 空。 |
| `git diff -- .github/workflows/ci.yml` | 空。 |
| `git diff -- backend` | 空。 |
| `git diff -- .env.example` | 空。 |

边界声明：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。

---

## NQ-CI-SECURITY-BATCH-5B-ENV-FREEZE（2026-06-21）

结论：**PASS / FROZEN / ACCEPTED**。docs-only freeze，未跑本地测试（无代码 / workflow / 配置 / migration 变更）；冻结依据是不可变 green run 证据。

freeze evidence（GitHub Actions immutable run，re-verified）：

| 项 | 值 |
| --- | --- |
| run ID | `27876451289` |
| workflow / event | NQ CI Baseline / push |
| headSha | `8ba140d96d84b7e2ae5f379043779bfeb925e2fc`（dev HEAD `06d8fc62` 之 ancestor；其后仅纯文档提交） |
| status / conclusion | completed / **success** |
| 8 jobs | diff-check / no-outbound-guard / backend / postgres-flyway / frontend / frontend-no-backend-e2e / research / secret-scan = all success |

测试证据（green run 日志，no-outbound-guard 与 backend job 均含）：

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in ...EnvSafetyValidatorTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in ...NoOutboundExchangeGuardTest
```

本地只读验证（freeze docs 轮）：`git status --short` 仅 `docs/current/*`（含新增 `NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md`）；`git diff --check` exit 0；`git diff -- "backend/**/db/migration"` 空；`git diff -- frontend research scripts deploy` 空；`git diff -- .github/workflows/ci.yml` 空；`git diff -- backend` 空；`git diff -- .env.example` 空。pushed `ci.yml` 静态确认：`no-outbound-guard`/`backend` job 0 处注入这三个变量，且自 green run `8ba140d9` 起 `ci.yml` 未变更；trigger `pull_request:[dev]`+`push:[dev]`+`workflow_dispatch` 保留；8 job 未删；未新增 secret。

状态：Batch 5B-ENV = **FROZEN / ACCEPTED**；Batch 5B-SMOKE = **STILL BLOCKED**。

---

## NQ-CI-SECURITY-BATCH-5B-ENV-FIX-CI-RERUN-REVIEW（2026-06-21）

结论：**PASS / ACCEPTED**。fix commit `8ba140d9` 的目标 rerun 全绿。

目标 run（fix commit 之后 `dev` 最新 run，非旧 plan-review / 非 RED 前 green / 非非目标 SHA）：

| 项 | 值 |
| --- | --- |
| run ID | `27876451289` |
| workflow | NQ CI Baseline |
| event | push |
| headSha | `8ba140d96d84b7e2ae5f379043779bfeb925e2fc`（== `dev` HEAD == `origin/dev`） |
| status / conclusion | completed / **success** |

8 job 全 success：diff-check、no-outbound-guard（恢复）、backend（恢复）、postgres-flyway、frontend、frontend-no-backend-e2e、research、secret-scan。

测试证据（no-outbound-guard job log，`-Dnq.no-outbound.guard.required=true`）：

```text
[INFO] Running ...EnvSafetyValidatorTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in ...EnvSafetyValidatorTest
[INFO] Running ...NoOutboundExchangeGuardTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in ...NoOutboundExchangeGuardTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
```

`NoOutboundExchangeGuardTest` 3 run / 0 skip → `shouldRejectExchangeCredentialEnvWhenCiGuardIsRequired` 实跑通过，不再因 `NQ_LIVE_ENABLED` / `NQ_REAL_PROVIDER_ENABLED` / `NQ_REAL_CLIENT_ENABLED` 非空失败；`backend` job 全量绿。

本地只读验证（review 文档轮）：`git status --short` 仅 `docs/current/*`；`git diff --check` exit 0；`git diff -- "backend/**/db/migration"` 空；`git diff -- frontend research scripts deploy` 空；`git diff -- .github/workflows/ci.yml` 空（与 HEAD 一致，未改 workflow）。pushed `ci.yml` 静态确认：`no-outbound-guard`/`backend` job 0 处注入这三个变量；trigger `pull_request:[dev]`+`push:[dev]`+`workflow_dispatch` 保留；8 job 未删；未新增 secret。

状态：Batch 5B-ENV = FIX RERUN GREEN / READY FOR FREEZE（尚未 freeze）；Batch 5B-SMOKE = STILL BLOCKED。

---

## NQ-CI-SECURITY-BATCH-5B-ENV-FIRST-RUN-FIX（2026-06-20）

结论：**FIXED LOCALLY / PENDING CI RERUN**。5B-ENV 合入 `dev`（HEAD `2bb1248a`）后 first run RED（run `27875157176`），失败 job `Backend Maven test` + `No-outbound guard`，失败测试 `NoOutboundExchangeGuardTest.shouldRejectExchangeCredentialEnvWhenCiGuardIsRequired`（断言 `CI no-outbound guard forbids exchange credential/live env: NQ_LIVE_ENABLED`）。

root cause = workflow injected env names forbidden by existing no-outbound guard：`.github/workflows/ci.yml` 在 `no-outbound-guard` 与 `backend` job 的 `env:` 注入了 `NQ_LIVE_ENABLED/NQ_REAL_PROVIDER_ENABLED/NQ_REAL_CLIENT_ENABLED="false"`，被既有 guard 测试列为 CI 模式下禁止存在（值 `"false"` 同样违规）。

fix = remove forbidden env-name injections from workflow jobs, not relax test：删除两个 job 的这三项 env 注入；未改测试 / `EnvSafetyValidator` / `EnvSafetyGuardConfiguration` / `application*.yml` / `.env.example`。

本地验证（env 中未注入 `CI` / `NQ_LIVE_ENABLED` / `NQ_REAL_PROVIDER_ENABLED` / `NQ_REAL_CLIENT_ENABLED`，已回显确认）：

```text
mvn -f backend/pom.xml -pl nq-app -am test \
  -Dtest=NoOutboundExchangeGuardTest,EnvSafetyValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dnq.no-outbound.guard.required=true
=> EnvSafetyValidatorTest 8/0/0/0；NoOutboundExchangeGuardTest 3/0/0/0；合计 11 tests / 0 failures / 0 errors / 0 skipped；Reactor 23/23 SUCCESS；BUILD SUCCESS
```

补充验证：`git diff --check` exit 0；`git diff -- "backend/**/db/migration"` 为空；`git diff -- frontend research scripts deploy` 为空；`grep` 确认这三个变量不再以 job-env 形式出现（仅保留在说明注释与 `forbidden_true_names` 校验步骤中，后者是断言非 `"true"`，非注入）。

说明（边界诚实）：本地 shell 未设置这些 env，故本地 test 在 fix 前后均会通过；本 fix 的真实作用面是 CI（CI 曾注入这些 env）。因此**未据本地结果宣称 CI green**；CI 真实全绿以下一次 GitHub Actions `dev` run 为准，绿前不得把 5B-ENV 写成 green / frozen。

workflow trigger 仍为：`pull_request:[dev]` + `push:[dev]` + `workflow_dispatch`；job 全集（diff-check / no-outbound-guard / backend / postgres-flyway / frontend / frontend-no-backend-e2e / research / secret-scan）未被删除；未新增 GitHub secret；未启动 5B-SMOKE。

---

## NQ-CI-SECURITY-BATCH-5B-ENV-FIRST-RUN-REVIEW（2026-06-20）

结论：**BLOCKED / NO TARGET GITHUB ACTIONS RUN**。目标 implementation commit `0ef4dbbeb769bf31a9efa768911ccc79b600383d` 没有 GitHub Actions run；`gh run list --commit 0ef4dbbeb769bf31a9efa768911ccc79b600383d` 返回空数组。

当前可见非目标 run：branch `docs/ci-5b-env-plan-review` 最近 run `27838086804` 是 old plan-review SHA `266cffd9...`，不能作为 5B-ENV implementation first-run evidence。

状态：Batch 5B-ENV = IMPLEMENTED / PENDING FIRST CI RUN；first-run review = BLOCKED / NO TARGET RUN；Batch 5B-SMOKE = STILL BLOCKED。

## NQ-CI-SECURITY-BATCH-5B-ENV-IMPL（2026-06-20）

结论：**IMPLEMENTED / PENDING FIRST CI RUN**。Batch 5B-ENV 已完成本地最小实现；Batch 5B-SMOKE = **STILL BLOCKED**。

已执行目标回归：

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-app -am test "-Dtest=EnvSafetyValidatorTest,NoOutboundExchangeGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnq.no-outbound.guard.required=true"` | **通过** | `EnvSafetyValidatorTest` 8 tests + `NoOutboundExchangeGuardTest` 3 tests，合计 11/0/0/0，`BUILD SUCCESS`。 |

完整收尾验证（同日补充）：

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| git status --short | **通过** | 仅本轮允许范围变更和新增 5B-ENV guard/profile/test 文件。 |
| git diff --check | **通过** | exit 0；仅 LF/CRLF 工作树提示，无 whitespace error。 |
| git diff --stat | **通过** | tracked diff 10 files；新增 Java/config/test 文件为 untracked，见 status。 |
| mvn -f backend/pom.xml test | **通过** | Reactor 23/23 SUCCESS，BUILD SUCCESS；测试汇总无 failures/errors，既有 2 skipped 保持。 |
| git diff -- backend db migration pathspec | **通过** | 空；未触碰 migration。 |
| git diff -- frontend research scripts deploy | **通过** | 空；未触碰 frontend / research / scripts / deploy。 |

边界声明：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。

## NQ-CI-SECURITY-BATCH-5B-ENV-PLAN-REVIEW（2026-06-20）

结论：**PASS / ACCEPTED**。Batch 5B-ENV plan = **ACCEPTED / READY FOR IMPLEMENTATION**；Batch 5B-ENV implementation = **NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**。

本轮为 review-only / docs-only，未运行后端 Maven、前端 build/e2e、Python pytest/mypy/ruff，也未执行真实 HTTP 探活；原因是任务明确禁止实现 env guard、修改 workflow/code/migration、启动 5B-SMOKE 或做真实外联。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| git status --short | **通过** | 切换到 docs/ci-5b-env-plan-review 后 clean baseline。 |
| git diff --check | **通过** | exit 0。 |
| git diff --stat | **通过** | clean baseline 时为空；本 review 后仅 docs/current review/status 文档变更。 |
| git diff origin/dev...HEAD --name-status | **通过** | PR diff 仅 6 个 docs/current 文件：baseline plan、5B-ENV plan、README、ROADMAP、TESTING、WORKLOG。 |
| git diff -- .github/workflows | **通过** | 空；No workflow changed。 |
| git diff -- backend frontend research scripts deploy | **通过** | 空；No code changed。 |
| git diff -- "backend/**/db/migration" | **通过** | 空；No migration changed。 |

边界声明：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider。

# Testing

本文记录统一验证命令和当前基线验证结果。未执行的验证不能写成通过。

## NQ-CI-SECURITY-BATCH-5B-ENV-PLAN（2026-06-19）

结论：**PASS / READY FOR REVIEW（plan-only）**。本轮为 CI/security planning + 环境边界只读盘点 + 文档登记，**未运行**后端 / 前端 / Python / CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

未跑测试原因：本轮只新增 `docs/current/NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md` 并追加 `NQ_CI_BASELINE_PLAN.md` / `README.md` / `ROADMAP.md` / `TESTING.md` / `WORKLOG.md`；不触碰 `mvn` / `npm` / `pytest` 链路，按文档规则可不跑全量测试。

只读盘点与 git 实测复核：

~~~text
git branch --show-current → dev
只读检查 → .github/workflows/ci.yml（8 jobs）、backend application*.yml（local/test/prod/freeze/gated-verify）、frontend playwright.ci.config.ts、research/py、.env.example、docs/current/*
CI 真实 secret 注入 → 0（仅 CI 控制值 + disposable DB（已 ::add-mask::）+ 公开 host denylist）
permission probe 默认 → NoRealExchangeCredentialPermissionProbePort → SKIPPED / REAL_EXCHANGE_PROBE_DISABLED（已确认）
未读取真实 .env / secrets / credentials / logs / dumps / backups → 确认
git diff --check → 期望 PASS（仅 LF/CRLF warning）
git diff -- .github/workflows → 期望 empty
git diff -- backend frontend research scripts deploy → 期望 empty
git diff -- backend/**/db/migration → 期望 empty
变更范围 → 仅 docs/current（新增 1 + 修改 5）
~~~

P0/P1/P2/P3：P0=0；P1=2（无统一 ci/paper profile、无运行态 env 冲突 fail-closed）；P2=3（real base-url 默认值误导、no-outbound 仅 test-scope、占位标记不统一）；P3=2（5A 状态措辞漂移、控制变量多为新增）。Batch 5B-ENV = PLAN ONLY；Batch 5B-SMOKE = BLOCKED BY 5B-ENV。

## NQ-DOCS-CURRENT-LEANUP-R3-FINAL-FREEZE（2026-06-19）

结论：**PASS / ACCEPTED / FROZEN**。docs-only final freeze of R1 (`ca77460f`) + R2 (`d4095ded`)，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 与链接实测复核：

~~~text
git branch --show-current → dev；工作区 clean
HEAD → d4095ded docs(governance): review docs/current cleanup (R2)
cleanup-result current markdown 基线 → 96 → 46
current root tracked .md（live，含 R1 报告 + R2 review）→ 47（R3 提交后 → 48）
docs/evidence/governance/*.md → 18（17 + README）
docs/evidence/compatibility/gatej-current-stubs/*.md → 15（14 + README）
docs/evidence/compatibility/ci-current-stubs/*.md → 21（20 + README）
gate-j canonical files → 28（未改）；docs/evidence/ci NQ_CI_*.md → 20（未改）
R1 commit → 51 R（17 governance R100 + 34 stub R077..R089），0 真实 delete，0 forbidden-scope
R2 commit → 1 A（R2 review doc）+ 3 M（STATUS/TESTING/WORKLOG），0 forbidden-scope
moved GateJ stub canonical 链接 ../../../gates/gate-j/X.md → 0 broken
moved CI stub canonical 链接 ../../ci/X.md → 0 broken
fragment 入链（移出对象 <file>.md#）→ 0
live 链接指向 moved 文件旧 current 路径 → 0
BLOCKED 3（GATEJ_API_PLAN/DB_PLAN/TEST_PLAN）→ 仍在 current，入链同目录解析正常
CI authority 2 + RUNBOOK + 5 导航 README → 存在
git diff --check → PASS（仅 LF/CRLF warning）
docs/gates·evidence-ci·archive·baselines·.agents·templates·ci.yml·backend·frontend·research·scripts·deploy·migration diff → empty
~~~

**NQ Docs Current Cleanup = FROZEN / ACCEPTED / CLOSED**；Round = 3 / 3；Round 4 = NOT ALLOWED；current markdown count = 46（cleanup-result 基线，live 48）；moved = 51；known compatibility residual = 3；P3 informational = 2；未删除历史证据；未创建 deletion list；未改代码/workflow/migration。NQ GateK CI mainline = COMPLETED / ACCEPTED；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-CURRENT-LEANUP-R2-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED**。docs-only review of R1 commit `ca77460f`，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 与链接实测复核：

~~~text
git branch --show-current → dev；工作区 clean
HEAD → ca77460f docs(governance): physically reduce docs/current (cleanup R1)
docs/current root tracked .md → 46（提交后核验，与 R1 一致）
docs/evidence/governance/*.md → 18（17 + README）
docs/evidence/compatibility/gatej-current-stubs/*.md → 15（14 + README）
docs/evidence/compatibility/ci-current-stubs/*.md → 21（20 + README）
R1 commit rename 语义 → 51 R（17 governance R100 byte-identical + 34 stub R077..R089）；0 真实 delete
R1 commit forbidden-scope 路径 → 0（git show --name-only 过滤为空）
moved GateJ stub canonical 链接 ../../../gates/gate-j/X.md → 逐文件解析 0 broken
moved CI stub canonical 链接 ../../ci/X.md → 逐文件解析 0 broken
fragment 入链（三组移出对象 <file>.md#）→ 0
live 链接指向 moved 文件旧 current 路径 → 0
BLOCKED 3（GATEJ_API_PLAN/DB_PLAN/TEST_PLAN）→ 仍在 current，入链 API.md/DB_SCHEMA.md/TESTING.md 同目录解析正常
CI authority 2 + RUNBOOK → 仍在 current，未改
current/README.md required 导航引用 → 18 处齐全
git diff --check → PASS（仅 LF/CRLF warning）
~~~

**NQ Docs Current Cleanup = ACCEPTED / READY FOR FINAL FREEZE**；Round = 2 / 3（R3 = FINAL FREEZE）；current markdown = 46；moved = 51；known compatibility residual = 3；未删除历史证据；未改代码/workflow/migration。NQ GateK CI mainline = COMPLETED / ACCEPTED；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-CURRENT-LEANUP-R1-IMPLEMENTATION（2026-06-19）

结论：**PASS / READY FOR REVIEW**（含 3 个 BLOCKED_PER_FILE）。docs-only current 目录物理瘦身，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 与链接实测验证：

~~~text
git branch --show-current → dev
docs/current 根 .md before → 96；after → 46（移出 51 + 新增 1 报告）
governance evidence moved → 17 → docs/evidence/governance/
GateJ stub moved → 14 → docs/evidence/compatibility/gatej-current-stubs/
CI stub moved → 20 → docs/evidence/compatibility/ci-current-stubs/
BLOCKED → 3（GATEJ_API_PLAN / GATEJ_DB_PLAN / GATEJ_TEST_PLAN，DIVERGED_INBOUND_LINK）
fragment 入链（三组移出对象 <file>.md#）→ 0 / 0 / 0
git status 摘要 → 17 R（governance 纯 rename）+ 34 RM（stub rename+自链接深度补偿）+ 2 M（docs/README、current/README）+ 3 ??（新 README）+ 新增报告/状态记录
stub 自链接验证 → GateJ ../../../gates/gate-j/X.md（可解析）；CI ../../ci/X.md（可解析）
canonical 目标存在 → docs/gates/gate-j/* OK；docs/evidence/ci/* OK
docs/gates/** diff → empty（canonical GateJ 未改）
docs/evidence/ci/** diff → empty（canonical CI evidence 未改）
G1 五份冻结对象正文 diff → empty
.github/workflows/ci.yml diff → empty
backend frontend research scripts deploy + backend/**/db/migration diff → empty
git diff --check → PASS（仅 LF/CRLF warning）
~~~

**NQ Docs Current Cleanup = IMPLEMENTED / READY FOR REVIEW**；Round = 1 / 3（R2 = REVIEW，R3 = FINAL FREEZE）；docs/current PHYSICALLY REDUCED；未删除历史正文；未改代码/workflow/migration；G1～G6 baseline 仍为历史参考。NQ GateK CI mainline = COMPLETED / ACCEPTED；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-FINAL-FREEZE-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED / FROZEN**。docs-only governance final freeze review，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 与 G1～G6 链路实测验证：

~~~text
git status --short → 初始空（G6 已提交 e7159b67）；改动仅 4 个允许文件
git branch --show-current → dev
git log --oneline -30 → HEAD = e7159b67 docs(governance): review G6 default-empty deletion batch
G1 五份冻结对象 diff → empty（zero drift）
G2 Rule 16 五级优先级 → docs/DOC_RULES.md 完整未削弱
G2 API.md / DB_SCHEMA.md GateI 链接 → ../gates/gate-i/（相对）；leading-slash malformed = 0
G3 canonical GateJ files → docs/gates/gate-j/ 28 files
G3 GateJ compatibility stub → 17，指向 ../gates/gate-j/
G3 RUNBOOK.md → current-control（# Current Runbook，62 行），未 stub 化
G3 9 份 DIVERGED current 活文档 → 未误处理
G4 canonical CI evidence → docs/evidence/ci/ 20 个 NQ_CI_*.md
G4 CI source stub → 20，指向 ../evidence/ci/（示例 12 行 stub）
G4 CI current authority ×2 → EXISTS（NQ_CI_BASELINE_PLAN / NQ_CI_SECURITY_GUARD_PLAN）
G4 CI_BASELINE_INDEX.md / docs/evidence/ci/README.md → 仅导航，不取代 current authority
G5 executable candidates → 0；implementation → SKIPPED / NOT APPLICABLE
G6 DELETE_CANDIDATES → 0；deletion list → 未创建
保留对象 → docs/gates(28)/archive(22)/evidence/ci(21)/baselines/CI authority×2/RUNBOOK/17 stub/20 stub/9 DIVERGED 全部 EXISTS
git diff --check → PASS（仅 LF/CRLF warning）
docs/gates docs/archive docs/evidence docs/baselines .agents templates diff → empty
.github/workflows/ci.yml diff → empty
backend frontend research scripts deploy + backend/**/db/migration diff → empty
~~~

**NQ Docs Governance Consolidation = FROZEN / ACCEPTED**；**G1～G5 = FROZEN / ACCEPTED**；**G5 executable candidates = 0**；**G5 implementation = SKIPPED / NOT APPLICABLE**；**G6 deletion batch = DEFAULT EMPTY / ACCEPTED**；**DELETE_CANDIDATES = 0**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G6-DEFAULT-EMPTY-DELETION-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED**。docs-only deletion-batch default-empty review，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 与保留对象实测验证：

~~~text
git status --short → 初始空；改动仅 4 个允许文件
git branch --show-current → dev
git log --oneline -20 → HEAD = fcb40f22 docs(governance): freeze G5 directory closure no-op
DELETE_CANDIDATES → 0
deletion list created → no
deletion proposal in cycle → 0
Migration Map DELETE NOW → 0（全表仅 5 种允许取值）
ARCHIVE_CANDIDATE = deletable now → no（already-archived / RETAIN_IN_PLACE）
FUTURE_MOVE_CANDIDATE / superseded = delete → no（move ≠ delete；redirect 后只移除重复副本，权威永久保留）
G5 executable candidates = 0 → 不可推导删除
retained docs/gates/** → EXISTS（gate-j 28 files）
retained docs/archive/** → EXISTS（22 files）
retained docs/evidence/ci/** → EXISTS（21 files）
retained docs/baselines/CI_BASELINE_INDEX.md → EXISTS
retained CI current authority ×2 → EXISTS（NQ_CI_BASELINE_PLAN / NQ_CI_SECURITY_GUARD_PLAN）
retained RUNBOOK.md → EXISTS
retained G3 GateJ stub → 17
retained G4 CI source stub → 20
retained DIVERGED current → 9
git diff --check → PASS（仅 LF/CRLF warning）
git diff --name-status → 仅 STATUS / TESTING / WORKLOG + 新增 G6 review file
G1 五份冻结对象 diff → empty
docs/gates docs/archive docs/evidence docs/baselines .agents templates diff → empty
.github/workflows/ci.yml diff → empty
backend frontend research scripts deploy diff → empty
backend/**/db/migration diff → empty
~~~

**G1～G5 = FROZEN / ACCEPTED**；**G5 executable candidates = 0**；**G5 implementation = SKIPPED / NOT APPLICABLE**；**G6 deletion batch = DEFAULT EMPTY / ACCEPTED**；**DELETE_CANDIDATES = 0**；**NQ Docs Governance Consolidation = READY FOR FINAL FREEZE REVIEW**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G5-FREEZE-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED / FROZEN**。docs-only freeze review，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 实测验证：

~~~text
git status --short
git branch --show-current → dev
git log --oneline -20
Migration Map exact G5 future-move query → 0
FUTURE_MOVE_CANDIDATE sections → 1
FUTURE_MOVE_CANDIDATE batch → G4 only
§1B / §1C G5 optional text → explanatory only, migration batch NONE
§1D → G4, not G5
G5 candidate matrix → empty by design and frozen
ELIGIBLE_FOR_G5_IMPLEMENTATION → 0
BLOCKED_PER_FILE → 0
RETAIN_IN_PLACE for G5 candidates → 0
G5 implementation → SKIPPED / NOT APPLICABLE
G5 moved files / redirected files / created target directories / deletion candidates → 0
misleading wording check → no "G5 implementation ready" or "G5 migration ready"
git diff --check → PASS for tracked modifications; LF/CRLF warnings only
changed current docs trailing whitespace check → 0
git status --short → only allowed current docs, including new G5 freeze review file
git diff --name-status → tracked diff only: STATUS / TESTING / WORKLOG
G1 frozen objects diff → empty
docs/gates docs/archive .agents templates diff → empty
.github/workflows/ci.yml diff → empty
backend frontend research scripts deploy diff → empty
backend/**/db/migration diff → empty
~~~

**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED**；**G4 CI evidence routing = FROZEN / ACCEPTED**；**G5 directory closure preflight = FROZEN / ACCEPTED**；**G5 executable candidates = 0**；**G5 implementation = SKIPPED / NOT APPLICABLE**；**G6 deletion batch = READY FOR DEFAULT-EMPTY REVIEW**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G5-DIRECTORY-CLOSURE-PREFLIGHT-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED**。docs-only review，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 实测验证：

~~~text
git status --short → clean before review edits
git branch --show-current → dev
HEAD before review = 8917d99d docs(governance): preflight G5 directory closure
Migration Map exact G5 future-move query → 0
FUTURE_MOVE_CANDIDATE sections → 1
FUTURE_MOVE_CANDIDATE batch → G4 only
§1B / §1C G5 optional text → explanatory only, migration batch NONE
G5 candidate matrix → empty by design
ELIGIBLE_FOR_G5_IMPLEMENTATION → 0
BLOCKED_PER_FILE → 0
RETAIN_IN_PLACE for G5 candidates → 0
ordinary inbound link audit objects → 0
fragment inbound link audit objects → 0
target conflict audit objects → 0
git diff --check → PASS
G1 frozen objects diff → empty
docs/gates docs/archive .agents templates diff → empty
.github/workflows/ci.yml diff → empty
backend frontend research scripts deploy diff → empty
backend/**/db/migration diff → empty
latest preflight commit touched only 4 allowed files → PASS
~~~

**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED**；**G4 CI evidence routing = FROZEN / ACCEPTED**；**G5 directory closure preflight = ACCEPTED / READY FOR FREEZE REVIEW**；**G5 executable candidates = 0**；**G6 deletion batch = NOT STARTED / DEFAULT EMPTY**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。
## NQ-DOCS-GOVERNANCE-G4-FREEZE-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED / FROZEN**。docs-only freeze review，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 实测验证：

~~~text
Migration Map §1D candidate trace → 22
20 routed pre-routing source blob == target blob → CANONICAL_BLOB_OK=20
20 old-path stub template check → STUB_TEMPLATE_OK=20
20 old-path source fragment grep → FRAGMENT_HITS=0
2 current authority protection → AUTHORITY_RETAINED=2
docs/evidence/ci NQ_CI file count → 20
NQ_CI docs outside docs/current or docs/evidence/ci → 0
CI_BASELINE_INDEX semantic check → PASS
CI evidence README semantic check → PASS
G1 frozen object hash-object check → G1_FROZEN_OBJECTS_OK=5
G3 17 stub / RUNBOOK / DIVERGED header check → GATEJ_STUB_OK=17
protected path diff → PROTECTED_DIFF_EMPTY=true
~~~

**G4 CI evidence routing = FROZEN / ACCEPTED**；**G5 = READY FOR IMPLEMENTATION**；**G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED；Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G4-CI-EVIDENCE-ROUTING-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED**。docs-only review，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 实测验证：

```text
HEAD = 783bfa68 docs(governance): route CI evidence to canonical records
Migration Map §1D candidate trace → PASS
20 routed pre-routing source blob == target blob → ROUTED_OK=20/20
2 current authority protection → AUTHORITY_RETAINED 2/2
docs/evidence/ci NQ_CI file count → 20
CI_BASELINE_INDEX semantic check → PASS
CI evidence README semantic check → PASS
fragment 入链 → FRAGMENT_HITS=0
G1 五份冻结对象 diff → 空
docs/gates docs/archive .agents templates diff → 空
workflow/code/deploy/migration diff → 空
GateJ 17 stub / RUNBOOK / strict DIVERGED current docs diff → 空
```

**G4 CI evidence routing = ACCEPTED / READY FOR FREEZE REVIEW**；**G5~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G4-CI-EVIDENCE-ROUTING（2026-06-19）

结论：**IMPLEMENTED / READY FOR REVIEW**。docs-only routing，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 实测验证：

```text
Migration Map G4 extraction → 22 candidates
REDIRECT_STUB_CREATED → 20
BLOCKED_PER_FILE / CURRENT_AUTHORITY → 2
fragment 入链 → 0 / 22
source blob == target blob → 20 / 20
old-path stub relative link → 20 / 20
canonical CI evidence dir → docs/evidence/ci/
CI baseline index → docs/baselines/CI_BASELINE_INDEX.md
G1 五份冻结对象 diff → 空
docs/gates docs/archive .agents templates diff → 空
.github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration" diff → 空
GateJ 17 stub / RUNBOOK / strict DIVERGED diff → 空
```

**G4 CI evidence routing = IMPLEMENTED / READY FOR REVIEW**；**G5~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G3-FREEZE-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED / FROZEN**。docs-only freeze review，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 实测验证：

```text
branch = dev
HEAD = 83afb990 docs(governance): accept G3 GateJ redirect consolidation
17 stub/canonical/template loop → FREEZE_STUB_CANONICAL_PASS 17/17
fragment 入链 → FRAGMENT_HITS=0
G3 implementation/review records → G3_RECORDS_PRESENT
DOC_RULES Rule 16 → RULE16_PRESENT
current API / DB_SCHEMA malformed leading-slash link → 0
git diff --check → clean
17 stub diff → STUB_DIFF_EMPTY
docs/gates docs/archive .agents templates diff → 空
G1 五份冻结对象 diff → 空
.github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration" diff → 空
RUNBOOK diff → 空
API/ARCHITECTURE/DB_SCHEMA/MODULES/README/ROADMAP diff → 空
```

**G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED**；**G4 = READY FOR IMPLEMENTATION**；**G5~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G3-GATEJ-REDIRECT-FIRST-CONSOLIDATION-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED**。docs-only review，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 实测验证：

```text
branch = dev
HEAD = 102c824d docs(governance): consolidate GateJ current copies with redirects
17 stub/canonical/pre-conversion blob loop → STUB_CANONICAL_REVIEW_PASS 17/17
fragment 入链 → FRAGMENT_HITS=0
G3 implementation report → G3_REPORT_COMPLETENESS_PASS
git diff --check → clean
docs/gates docs/archive .agents templates diff → 空
G1 五份冻结对象 diff → 空
.github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration" diff → 空
RUNBOOK diff → 空
API/ARCHITECTURE/DB_SCHEMA/MODULES/README/ROADMAP diff → 空
```

**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 GateJ redirect-first consolidation = ACCEPTED / READY FOR FREEZE REVIEW**；**G4~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G3-GATEJ-REDIRECT-FIRST-CONSOLIDATION（2026-06-19）

结论：**PASS / READY FOR REVIEW**。docs-only redirect-first consolidation，**未运行**后端/前端/Python/CI 测试（无代码、无 workflow、无 migration、无依赖变更）。

git 实测验证：

```text
branch = dev
HEAD baseline blob check：17/17 docs/current/<file> == docs/gates/gate-j/<file>，且 gate-j worktree canonical 未漂移
current stub check：17/17 符合 redirect-first 模板，含 ../gates/gate-j/<file> 相对链接
Authority/Migration：Authority Index GateJ 行与 Migration Map §1E 仍标 NON_AUTHORITATIVE / FUTURE_SUPERSEDE_CANDIDATE / G3
fragment 入链：git grep "<name>.md#" → 0
full current path 入链：存在普通路径/导航文本引用，均无 fragment，旧路径由 stub 兼容
git diff --check → 通过（仅 LF→CRLF 工作树提示，exit code 0）
G1 五份冻结对象 diff → 空
docs/gates docs/archive .agents templates diff → 空
.github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration" diff → 空
git diff --name-status → 仅 M，无 D/R；git status --short 含新增 G3 报告
```

**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 GateJ redirect-first consolidation = IMPLEMENTED / READY FOR REVIEW**；**G4~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G2-FREEZE-REVIEW（2026-06-19）

结论：**PASS / ACCEPTED / FROZEN**。只读冻结复核（semantic baseline，非 blob lock），docs-only，**未运行**后端/前端/Python/CI 测试。P0=0 / P1=0 / P2=0 / P3=3（信息性）。

git 实测复核：

```text
G1 五份冻结对象 diff 7eb7ae53..HEAD → 空（零 drift）
docs/gates docs/archive .agents templates ci.yml backend frontend research scripts deploy "backend/**/db/migration" diff 7eb7ae53..HEAD → 空
current-control malformed leading-slash 链接 → 0；../gates/gate-i/GATEI_{API,DB}_PLAN.md 目标存在、可解析
冻结快照 ./GATEI_* → gate-h/gate-j 各 1，未改写
G2 状态 → ACCEPTED / READY FOR FREEZE REVIEW（无 “G2 = FROZEN” 误写，仅否定语境出现该串）
Rule 16 → 五级优先级完整无矛盾
5A 显式声明非 authenticated/backend coverage；5B-ENV/5B-SMOKE/4F/static 未误标 completed
NQ_DOCS_EVIDENCE_INDEX.md（冻结对象）→ 零 drift；278/283 未改写
git diff --check → 无空白错误
```

G2 = **semantic baseline freeze**（断言+导航+Rule 16+link hygiene），current-control 文档仍可正常追加更新；失效条件 8 项 / 允许维护 6 项见 `NQ_DOCS_G2_FREEZE_REVIEW.md`。**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 = READY FOR IMPLEMENTATION**；**G4~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。


## NQ-DOCS-GOVERNANCE-G2-CURRENT-CONTROL-DRIFT-REPAIR-REVIEW（2026-06-18）

结论：**PASS / ACCEPTED**。只读评审 G2 commit `3c1f5ec0`，docs-only，**未运行**后端/前端/Python/CI 测试。P0=0 / P1=0 / P2=0 / P3=2（信息性）。

git 实测复核：

```text
G1 五份冻结对象 diff 7eb7ae53..HEAD → 空（零 drift）
docs/gates docs/archive .agents templates diff 7eb7ae53..HEAD → 空
.github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration" → 空
current-control malformed leading-slash 链接 → 0；../gates/gate-i/GATEI_{API,DB}_PLAN.md 目标存在、相对路径可解析
冻结快照 ./GATEI_* 历史链接 → gate-h/gate-j 各 1，未改写
G2 状态口径 → 无 “G2 = FROZEN” 误写；仅 “G2 = IMPLEMENTED / READY FOR REVIEW”
DOC_RULES Rule 16 → 五级优先级完整
NQ_DOCS_EVIDENCE_INDEX.md（冻结对象）→ 零 drift
278 / 283 → 未改写（仅治理/evidence 上下文引用）
git diff --check → 无空白错误
```

**G2 current-control drift repair = ACCEPTED / READY FOR FREEZE REVIEW**；**G1 authority/evidence index = FROZEN / ACCEPTED**；**G3~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G2-CURRENT-CONTROL-DRIFT-REPAIR（2026-06-18）

结论：**G2 = IMPLEMENTED / READY FOR REVIEW**。docs-only，**未运行**后端/前端/Python/CI 测试（无代码变更）。

git 实测验证：

```text
malformed leading-slash 链接：rg "\]\(/[^)]*\.md" docs/current/API.md docs/current/DB_SCHEMA.md → 0（修复前 2，已改为相对 ../gates/gate-i/）
G1 五份冻结对象 working-tree diff：git diff --name-only -- <5 objects> → 空
278 / 283：未改写（仅出现在 G1 冻结文档与本轮 evidence 说明，未重算）
docs/gates docs/archive .agents templates diff → 空
.github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration" diff → 空
git diff --name-status → 仅 docs/README.md / docs/DOC_RULES.md / docs/current/{README,STATUS,ROADMAP,TESTING,WORKLOG,API,DB_SCHEMA}.md + 新增 NQ_DOCS_G2_CURRENT_CONTROL_REPAIR.md
git diff --check → 无空白错误
```

**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 = IMPLEMENTED / READY FOR REVIEW**；**G3~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G1-FREEZE-REVIEW（2026-06-18）

结论：**PASS / ACCEPTED / FROZEN**。只读冻结复核，docs-only，**未运行**后端/前端/Python/CI 测试（无代码变更）。P0=0 / P1=0 / P2=0 / P3=2（信息性）。

git 实测复核：

```text
HEAD = a01579739ef176b0443103d69c55d8bf6845c0b6 (dev)
5 冻结对象自 c3a2cf83 零 drift：git diff --name-only c3a2cf83..HEAD -- <5 objects> → 空
冻结 blob：PLAN 0ee21735 / AUTHORITY 71e31b5d / EVIDENCE 8b18e36d / MIGRATION 6eb2706d / G1_IMPL 4dece64e
计数边界：原始基线 278（冻结）；G1 implementation snapshot 283 = 278 + 5 增量（冻结）；live 工作树 284（=283 + G1_REVIEW，review evidence 不回写 283）
authority index 表 → 14 领域唯一权威无并列
current↔gate-j blob → 18 IDENTICAL（superseded 17 + RUNBOOK retain）/ 9 DIVERGED
migration map → 10 字段齐全；§4 gates/archive/.agents/templates 全 RETAIN_IN_PLACE/NONE/NOT_APPLICABLE；DELETE NOW 肯定用法 = 0
evidence index → 9 类入口齐全；backlog（5B-ENV/5B-SMOKE/4F-B~4F-F/static）均 NOT STARTED/BLOCKED
governance commit e3b12e33..c3a2cf83 -- docs/gates docs/archive ci.yml backend frontend research scripts deploy templates .agents → 空
git diff --check → 无空白错误；禁止范围 diff → 空
```

**NQ Docs Governance Plan = FROZEN FOR G1 BASELINE**；**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 = READY FOR IMPLEMENTATION**；**G3~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G1-AUTHORITY-EVIDENCE-INDEX-REVIEW（2026-06-18）

结论：**PASS / ACCEPTED**。只读评审，docs-only，**未运行**后端/前端/Python/CI 测试（无代码变更）。P0=0 / P1=0 / P2=0 / P3=3（信息性）。

git 实测复核：

```text
工作树 md/txt = 283（基线 278 + 增量 5）；current 根 80 / frontend 3 / gates 152 / archive 21 / templates 4 / .agents 13 / scattered 10
基线自洽 75+3+10+152+21+13+4 = 278 ✓；工作树 278+5 = 283 ✓
current↔gate-j blob 比对 → 18 IDENTICAL（superseded 17 + RUNBOOK retain）/ 9 DIVERGED（独立复跑确认）
MIGRATION_MAP §1E superseded 去重 → 17 唯一 .md（无重复/遗漏）
authority index → 14 领域，每领域唯一 current authority，无并列
evidence index → 9 类入口齐全；backlog（5B-ENV/5B-SMOKE/4F-B~4F-F/static）均 NOT STARTED/BLOCKED，无 completed 误标；只链接不复制
migration map → 10 字段齐全；gates/archive/.agents/templates 全 RETAIN_IN_PLACE/NONE/NOT_APPLICABLE；DELETE NOW 仅否定语境（无肯定用法）
rg "277|290|16 IDENTICAL|16 份" 5 份治理文档 → 仅 run-id 子串 + 已废弃订正说明
git diff --name-only e3b12e33..c3a2cf83 -- (禁止范围) → 空（governance commit 未触碰 code/workflow/gates/archive/templates/.agents）
git diff --check → 无空白错误
```

**NQ Docs Governance Plan = ACCEPTED AS IMPLEMENTATION BASELINE**；**G1 authority/evidence index = ACCEPTED / READY FOR FREEZE REVIEW**；**G2~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-G1-AUTHORITY-EVIDENCE-INDEX（2026-06-18）

结论：**G1 = IMPLEMENTED / READY FOR REVIEW**。docs-only，**未运行**后端/前端/Python/CI 测试（无代码变更）。新增 4 份 G1 索引文档，收敛 P2-1/P2-2/P2-3，**未移动/删除/重命名/归档任何文档**。

git 实测验证：

```text
git ls-files "*.md" "*.txt"（排除 node_modules/target/build/dist/test-results） → 基线 278；现 HEAD 279（+review）；G1 后工作树 283（+4 G1 doc）
docs/current 根 75（基线）/ frontend 3 / gates 152 / archive 21 / templates 4 / .agents 13 / scattered 10  →  和 = 278 ✓
current↔gate-j blob 比对 → 18 IDENTICAL（superseded 17 + RUNBOOK retain-in-place）/ 9 DIVERGED（分层事实）
migration map 覆盖性 → 75+3+10+152+21+13+4 = 278 基线全覆盖，0 orphan
rg "277|290" 新增 4 份 G1 doc → 0 命中（NQ_DOCS_GOVERNANCE_PLAN.md 仅余 run id `27750279096` 内的子串，非计数口径）
git diff --check → 无空白错误
git diff -- .github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration" → 空（禁止范围零改动）
```

**NQ Docs Governance Plan = P2 CONDITIONS CLOSED / READY FOR G1 REVIEW**；**G1 = IMPLEMENTED / READY FOR REVIEW**；**G2~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

## NQ-DOCS-GOVERNANCE-INVENTORY-PLAN-REVIEW（2026-06-18）

结论：**PASS / ACCEPTED WITH P2 CONDITIONS**。只读评审 `NQ_DOCS_GOVERNANCE_PLAN.md`，docs-only，**未运行**后端/前端/Python/CI 测试（无代码变更）。P0=0 / P1=0 / P2=3 / P3=2。详见 `NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`。

git 实测复核（**纠正**计划 §2 的计数，结论以 git-verified 为准）：

```text
git ls-files "*.md" "*.txt" (排除 node_modules/target/build/dist/test-results)
                                                    → 278 份（计划称 277，低 1）
docs/current 根 .md            → 75（计划称 74）   docs/current/frontend → 3（计划称 15，重大偏差）
docs/gates → 152（一致）        docs/archive → 21（计划称 22）  templates → 4  .agents → 13  repo-root → 3
覆盖性                          → 0 orphan：每个 md/txt 都落在某盘点前缀下（分类覆盖完整）
docs/current 根 vs docs/gates/gate-j 同名 blob 比对 → 18 IDENTICAL / 9 DIVERGED
                                  其中 17 = GateJ superseded duplicate（计划全文称 16，少计）；
                                  第 18 份 RUNBOOK.md blob 一致但属 CURRENT_CONTROL 保留（非去重）；
                                  9 DIVERGED = API/ARCHITECTURE/DB_SCHEMA/MODULES/README/ROADMAP/STATUS/TESTING/WORKLOG（分层事实，与计划一致）
broken markdown 链接复核        → 6 处全部命中：API.md:171 / DB_SCHEMA.md:239 前导 /（目标存在，G2 docs-only）；
                                  gate-h|gate-j 的 API.md:133 / DB_SCHEMA.md:177 共 4 处 ./GATEI_*（目标不存在，冻结快照，redirect 处理）
git diff --check                → 无空白错误
git diff -- .github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration" → 空（禁止范围零改动）
```

**NQ Docs Governance Plan = ACCEPTED AS IMPLEMENTATION BASELINE**（带 P2 收口条件）；**G1 authority/evidence index = READY FOR IMPLEMENTATION**（G1 内须用 git-verified 计数与 17 份去重列表）；**G2~G6 = NOT STARTED**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。本轮无移动/删除/重命名文档，无历史 freeze/review 事实修改。

## NQ-DOCS-GOVERNANCE-INVENTORY-PLAN（2026-06-18）

结论：**PASS / READY FOR REVIEW**（documentation governance plan ready，未收口）。本轮为只读文档盘点 + 规划，**未运行**后端/前端/Python/CI 测试（无代码变更，无需构建验证）。

只读检查与"验证"：

```text
git ls-files "*.md" "*.txt"                         → 277 份（排除 node_modules/target/build/dist/test-results）
docs/current 根 .md                                 → 74 ；docs/current/ 共 89 ；docs/gates 152 ；docs/archive 22
docs/current 根 vs docs/gates/gate-j 同名 blob 比对  → 16 IDENTICAL（GateJ 重复）/ 9 DIVERGED（current 活文档 vs 快照）
broken markdown [](*.md) 链接扫描（全 docs）         → checked=24 broken=6（2 current malformed 前导 /；4 在冻结 gate-h/gate-j 快照）
docs/README.md 导航 backtick 路径存在性              → 全部存在（含 GateJ 计划文档，确认重复/漂移）
git diff --check                                     → 无空白错误
git diff -- .github/workflows/ci.yml backend frontend research scripts deploy "backend/**/db/migration" → 空（禁止范围零改动）
```

本轮无移动/删除/重命名文档，无历史 freeze/review 事实修改。详见 `NQ_DOCS_GOVERNANCE_PLAN.md`。

## NQ-CI-BATCH-5A-FREEZE-REVIEW（2026-06-18）

结论：**PASS / ACCEPTED / FROZEN**。**Batch 5A = FROZEN / ACCEPTED**；**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**；Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

两次 immutable GitHub Actions green run（**非本地结果**）+ 零 drift：

```text
Run 1 (首跑)  : run 27750279096 / commit 861c3e78 (impl) / job 82098741200 → success / 4 passed (7.3s)
Run 2 (freeze): run 27750976632 / commit 3d26c84d (first-run-review docs-only) / push→dev / completed success / job 82101090359 → 4 passed (6.8s)
drift check   : ci.yml blob 6941d60ade2bfce456e203f708b633e595285178  (861c3e78 == 3d26c84d, IDENTICAL)
                playwright.ci.config.ts blob d039fe82fbf7db6f55c3e6fc089bac59a2fe9014  (861c3e78 == 3d26c84d, IDENTICAL)
                git diff --name-only 861c3e78 3d26c84d = 仅 5 个 docs/current 文件 (docs-only)
Run 2 核验    : permissions Contents: read / Metadata: read ; Node 22.22.3 ; npm ci added 183 ;
                playwright install --with-deps chromium → Chromium 1208 only (Firefox/Webkit 0) ; vite build 成功 ;
                显式四 spec → Running 4 tests using 1 worker → 4 passed ; 其余 23 spec 0 次 / 无 skip-as-pass ;
                /api postgres jdbc flyway docker loginToConsole seed storageState okx binance upload-artifact = 0 ;
                无 service 容器 ; cleanup rm -rf test-results-ci playwright-report test-results 运行
```

bootstrap（checkout / Node 下载 / npm registry / Chromium CDN）属 CI 引导网络访问，业务层出站 = 0；GitHub mask 的 `***`、checkout extraheader、Node URL API 文案均非业务 token/`/api`/出站。审查仅用 `gh`（run/job 元数据 + immutable 日志只读）与 `git`（blob 比对），未用本地结果替代 immutable green run。冻结基线 = 两 blob + 四 spec allowlist；任何改动使冻结失效需重审。详见 `NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md`。

## NQ-CI-BATCH-5A-FIRST-RUN-REVIEW（2026-06-18）

结论：**PASS / READY FOR FREEZE REVIEW**。**Batch 5A = FIRST RUN PASSED / READY FOR FREEZE REVIEW**；**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**；Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

验证对象为 GitHub Actions immutable 首跑（**非本地结果**）：

```text
run        : 27750279096 (workflow "NQ CI Baseline", event push, branch dev) → completed / success
commit     : 861c3e78ddd1733292c5376a1f059532fd6dc846 (= origin/dev HEAD, 0/0)
job        : Frontend no-backend E2E (Batch 5A) id 82098741200 → success, 约 56s (< 15min timeout)
permissions: Contents: read / Metadata: read
node       : 22.22.3 ; npm ci added 183 ; playwright install --with-deps chromium → Chromium 1208 only (Firefox/Webkit 0)
build      : tsc -b && vite build → built in 1.53s
e2e cmd    : npx playwright test --config=playwright.ci.config.ts <四个 spec 显式列出>
e2e result : Running 4 tests using 1 worker → 4 passed (7.3s) ; 其余 23 spec 0 次出现 / 无 skip-as-pass
boundary   : /api postgres jdbc flyway docker loginToConsole seed storageState okx binance = 0 ; 无 service 容器 ; 无 upload-artifact ; cleanup rm -rf 成功
```

bootstrap（checkout / Node 下载 / npm registry / Chromium CDN）属 CI 引导网络访问，业务层出站 = 0。`Authorization`×1 为 checkout 的 git extraheader（GitHub mask），`token`×3 为 GITHUB_TOKEN 头与 `token: ***`（已 mask），`api`×1 为 Node URL API 弃用警告，均非业务调用或凭证泄露。审查仅用 `gh`（run/job 元数据 + immutable 日志只读）与 `git`，未用本地 4 passed 替代首跑证据。详见 `NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md`。

## NQ-CI-BATCH-5A-NO-BACKEND-E2E-IMPL（2026-06-18）

结论：**PASS / READY FOR FIRST-RUN**。**Batch 5A = IMPLEMENTED / READY FOR FIRST-RUN**；**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**；Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

新增 CI job `frontend-no-backend-e2e` + `frontend/playwright.ci.config.ts`，只跑四个 no-backend spec（真实路径 `frontend/tests/e2e/`）：`login-page-smoke`、`design-system-table-smoke`、`design-system-live-query-smoke`、`design-system-backtest-chart-smoke`。

本地真实执行结果：

```text
playwright test --config=playwright.ci.config.ts --list   → Total: 4 tests in 4 files（仅四 allowlist spec，未扩大）
npm ci                                                     → 成功（本机原缺 echarts，clean install 补齐；未改 package.json/lockfile）
npm run build                                              → 成功（tsc -b && vite build）
playwright test --config=playwright.ci.config.ts <四个 spec 显式列出>  → 4 passed (10.2s)
```

执行边界：本地 E2E 基于 production build + loopback `vite preview`（127.0.0.1:5179），**未**启动 backend / PostgreSQL / Flyway / 认证 / seed；**未**调用 `loginToConsole()`；**未**运行其余 23 个 spec；运行后 `test-results` / `test-results-ci` 为空临时目录已删除，未生成/上传 HTML report / trace / video / screenshot / 任何 artifact。GitHub Actions first-run（含 `npx playwright install --with-deps chromium` 真实安装与 ubuntu runner 执行）仍待 CI 首跑确认，本轮不写成 CI passed。

禁止范围校验：`git diff -- backend frontend/src frontend/tests frontend/package.json frontend/package-lock.json research scripts deploy pom.xml pyproject.toml` 为空；`git diff --check` 无空白错误；改动仅 `.github/workflows/ci.yml`（+56 行）与新增 `frontend/playwright.ci.config.ts` 及 `docs/current/**`。

## NQ-CI-BATCH-5-FRONTEND-E2E-PLAN-REVIEW（2026-06-18）

结论：**PASS / ACCEPTED**。**Batch 5 plan = ACCEPTED AS IMPLEMENTATION BASELINE**；**Batch 5A = READY FOR IMPLEMENTATION**；**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**；Batch 4F-A = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；Batch 4C = **FROZEN / ACCEPTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

实际执行（只读源码核实，未运行运行时）：审查 `frontend/playwright.config.ts`、4 个候选 5A spec、`frontend/src/router/routes.tsx`、`DesignSystemDemoPage.tsx`、`useLiveQuery.ts`、`BacktestCurveChart.tsx`、`AppProviders.tsx`、`main.tsx`、`frontend/vite.config.ts`、`frontend/package.json` scripts、`marketdata-ingestion-smoke.spec.ts` 与 `NQ_CI_FRONTEND_E2E_PLAN.md`。本轮**未运行** `npm run test:e2e`、`npm run build`、backend、PostgreSQL、Flyway 或浏览器安装，原因是 plan-review-only 且禁止进入 Batch 5 implementation；未生成或上传 trace、screenshot、video、HTML report、test-results 或 raw logs。

核实结论：

- 4 个 no-backend spec 确证为纯 loopback / no-backend：`/dev/design-system` 与 `/login` 是顶层公开路由（无 `RequireAuth`）；隔离 context 无 storageState 无 token，`AuthBootstrap.currentUserQuery` disabled，不发 `/api`；`LiveQueryDemo.queryFn` 为本地 `setTimeout` promise，`useLiveQuery` 不自发请求，`BacktestCurveChart` 无 fetch/axios/useQuery。最终 allowlist 无存疑 spec 需移出。
- `vite.config.ts` `/api` proxy 仅在 `server`，`preview` 无 proxy；5B 不可假设 preview 代理 `/api`。
- `marketdata-ingestion-smoke` run-once 容忍外网失败，与 fail-closed no-outbound 冲突，必须持续排除。
- 所有调用 `loginToConsole()` 的页面级 spec 仍依赖真实 backend/PostgreSQL/Flyway/auth/legacy account/SIM exchange account；`backtest-detail-smoke.spec.ts` 两个页面级 case 仍为 **PENDING BACKEND ENV / NOT VERIFIED IN CI**；历史本地通过未被重写为 Batch 5 CI passed。

## NQ-CI-BATCH-5-FRONTEND-E2E-PLAN（2026-06-18）

结论：**PASS / READY FOR REVIEW**。Batch 5 = **PLAN ONLY / NOT IMPLEMENTED**；Batch 4F-A = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；Batch 4C = **FROZEN / ACCEPTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

实际执行：只读检查 Playwright config、27 个 spec、helpers/fixtures、package/Vite runner、backend local/test profile、auth seed、Batch 2/3 guard、`.github/workflows/ci.yml` 与 Batch 1-4 current docs；执行文档路径/状态/范围 diff 与 `git diff --check`。本轮**未运行** `npm run test:e2e`、backend、PostgreSQL、Flyway 或浏览器安装，原因是 planning-only 且禁止进入 Batch 5 implementation；未生成或上传 trace、screenshot、video、HTML report、test-results 或 raw logs。

验证结论：当前 4 个 no-backend spec 可进入未来 5A bounded allowlist，但本轮状态仍为 NOT EXECUTED IN CI；所有调用 `loginToConsole()` 的页面级 spec 依赖真实 backend/PostgreSQL/Flyway/auth/legacy account/SIM exchange account。`backtest-detail-smoke.spec.ts` 两个页面级 case 明确为 **PENDING BACKEND ENV / NOT VERIFIED IN CI**。历史本地通过记录未被重写为 Batch 5 CI passed。

## NQ-CI-SECURITY-GUARD-BATCH-4F-A-FREEZE-REVIEW（2026-06-18）

结论：**PASS / ACCEPTED / FROZEN**。Batch 4F-A preflight = **FROZEN / ACCEPTED**；Python local audit = **NOT READY**；Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = **NOT STARTED**；Batch 4C = **FROZEN / ACCEPTED**；Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 5 = **PENDING**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

实际执行的只读验证：

```powershell
Get-Location
git status --short
git branch --show-current
git log --oneline -10
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- "backend/**/db/migration"
git ls-files
java -version
mvn -version
node --version
npm --version
python --version
python -m pip --version
rg -n "uses:|gitleaks|8\.18\.4|sha256|checksum|retention-days|setup-python" .github/workflows/ci.yml
```

结果摘要：

- branch=`dev`；编辑前工作区 clean；workflow、backend、frontend、research、scripts、deploy、migration diff 均为 0。
- Maven XML 结构化核验：root modules=22，tracked child POM=22，missing=0，extra=0，invalid parent=0。
- npm JSON 结构化核验：lockfileVersion=3，package entries=214；默认 `ConvertFrom-Json` 因 root package 空字符串 key 失败，改用 `-AsHashTable` 后重验通过；未输出完整 lockfile。
- Java=`21.0.8` LTS，Maven=`3.9.12`；只证明本地工具可用，不代表 vulnerability audit 已执行或通过。
- Python path 为 WindowsApps stub；`python --version` 与 `python -m pip --version` 均 exit `9009`，因此 Python local audit 保持 NOT READY。
- Python tracked input 仅 `research/py/pyproject.toml`；无 tracked requirements、constraints 或 Python lockfile。
- official actions 使用 major tags；gitleaks=`8.18.4` 且无 release asset SHA256 verification；均保留为 4F-E 输入。
- 4F-B 十个 mandatory sanitized fields、bounded `scope`、report-only policy、blocking boundary、Batch 4C redaction gate 与 bounded retention 均已核对。
- credential hygiene 覆盖 `docs/current` 与 `.github` 86 个 tracked files；高置信完整 credential pattern 命中 0，未输出匹配正文。

未执行：

- 未运行 Maven vulnerability audit、`npm audit`、`pip-audit`、OSV、Snyk、Trivy、Grype、OWASP dependency-check 或外部 scanner。
- 未生成、保存或上传 SBOM、raw JSON、dependency tree、完整 lockfile 或 dependency report。
- 未运行 backend Maven test、frontend build/E2E 或 Python pytest/mypy/ruff；原因：本轮为 docs-only freeze review，明确禁止扫描、构建、测试和 4F-B 实现。
- 未修改 `.github/workflows/ci.yml`、依赖文件、代码、测试、migration、frontend、research、scripts 或 deploy。

## NQ-CI-SECURITY-GUARD-BATCH-4F-A-PREFLIGHT-REVIEW（2026-06-18）

结论：**PASS / ACCEPTED**。Batch 4F-A preflight = **ACCEPTED / READY FOR FREEZE REVIEW**；允许进入 4F-A freeze review。Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = **NOT STARTED**；Batch 4C = **FROZEN / ACCEPTED**；Batch 5 = **PENDING**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

实际执行的只读验证：

```powershell
git status --short
git branch --show-current
git log --oneline -8
git show --name-status --format=fuller 7e7079a3
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- "backend/**/db/migration"
git diff -- "backend/**/pom.xml" frontend/package.json frontend/package-lock.json research/py/pyproject.toml
git ls-files "*pom.xml" "package.json" "package-lock.json" "pyproject.toml" "requirements*.txt" "constraints*.txt" ".github/workflows/*.yml"
Get-Command java,mvn,node,npm,python,pip -ErrorAction SilentlyContinue
java -version
mvn -version
node --version
npm --version
python --version
python -m pip --version
rg -n "uses:|GITLEAKS_VERSION|curl --fail|sha256|checksum|upload-artifact|retention-days" .github/workflows/ci.yml
```

结果摘要：

- branch = `dev`；review 前工作区 clean。
- 4F-A implementation commit `7e7079a3` 仅修改 9 个 `docs/current` 文件；未修改 workflow、依赖文件、代码、测试或 migration。
- `backend/pom.xml` 为 packaging=`pom` 的 root reactor parent；22 个 root modules 与 22 个 tracked child POM 一一对应；22 个 child parent group/artifact/version/relativePath 全部一致。
- Java `21.0.8`、Maven `3.9.12` 仅为 local command availability；未运行 vulnerability audit，不能写成 audit verified。
- frontend package/lockfile 存在；lockfileVersion=`3`，package entries=`214`；未复制完整 lockfile、dependency tree 或 npm config。
- Python tracked input 仅 `research/py/pyproject.toml`；无 requirements、constraints 或 Python lockfile；WindowsApps stub 导致两条 Python version 命令 exit `9009`。
- 4F-B 若覆盖 Python，必须使用真实解释器路径或 GitHub Actions `actions/setup-python@v5` 确定环境。
- official actions 使用 major tags；gitleaks version pin=`8.18.4` 且无 asset SHA256 verification；均归入 4F-E。
- Review-time clarification：4F-B sanitized summary 必须包含 bounded `scope`；不得展开 dependency tree。
- 未安装或运行 scanner；未运行 dependency audit；未生成/上传 SBOM；未上传 artifact。

未执行：

- 未运行 Maven vulnerability plugin、`npm audit`、`pip-audit`、OSV、Snyk、Trivy、Grype 或 OWASP dependency-check。
- 未运行 backend/frontend/research build 或 test；原因：本轮为 docs-only preflight review，且禁止进入 4F-B 实现。
- 未修改 `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、tests 或 dependency input files。

## NQ-CI-SECURITY-GUARD-BATCH-4F-A-DEPENDENCY-AUDIT-PREFLIGHT（2026-06-18）

本轮是 GateK CI Batch 4F-A dependency audit input / toolchain preflight。结论：**PASS / READY FOR REVIEW**。Batch 4F-A = **IMPLEMENTED / READY FOR REVIEW**；Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = **NOT STARTED**；Batch 4C = **FROZEN / ACCEPTED**；Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 5 = **PENDING**；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

实际执行的只读验证：

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

结果摘要：

- `git status --short`：执行前 clean。
- `git branch --show-current`：`dev`。
- `git log --oneline -8`：HEAD 为 `4fea308d docs(ci): sync Batch 4F dependency audit sequence`。
- Maven input：`backend/pom.xml` + 22 个 tracked child `pom.xml`，root reactor modules 已清点。
- npm input：`frontend/package.json` + `frontend/package-lock.json`；lockfileVersion = 3；lockfile package entries = 214。
- Python input：`research/py/pyproject.toml`；无 tracked `requirements*.txt` / `constraints*.txt` / Python lockfile。
- GitHub Actions input：`.github/workflows/ci.yml`；actions 当前使用 major tag；gitleaks CLI version pin = `8.18.4`，未发现 release asset SHA256 checksum verification。
- Java / Maven / Node / npm：本机版本可读取。
- Python / pip：`python` 解析到 WindowsApps stub；`python --version` 与 `python -m pip --version` 失败，未写成可用。

未执行：

- 未运行 Maven vulnerability audit、`npm audit`、`pip-audit`、OSV、Snyk、Trivy、Grype、OWASP dependency-check 或其他外部扫描器。
- 未生成 SBOM。
- 未安装 scanner 或依赖。
- 未上传 artifact、raw report、JSON、dependency tree、lockfile 或 SBOM。
- 未修改 `.github/workflows/ci.yml`、POM、package、lockfile、pyproject、requirements、backend、frontend、research、scripts、deploy、migration 或测试。
- 未执行 `mvn test` / `npm run build` / `npm run test:e2e` / Python pytest/mypy/ruff；原因：本轮只做 dependency audit preflight 文档基线，且明确禁止实现扫描、workflow、代码和测试改动。

## NQ-CI-SECURITY-GUARD-BATCH-4F-EXECUTION-SEQUENCE-SYNC（2026-06-18）

本轮是 GateK CI Batch 4F **pre-implementation documentation sync**：只修正 Batch 4F-A 至 4F-F 的任务编号、顺序、范围与状态，不修改 workflow，不新增 CI job，不运行 dependency audit，不改代码、测试、依赖文件或锁文件。该轮结论 **PASS**，当时将 Batch 4F-A 标为首个可实施批次；当前已由 `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md` 推进为 **IMPLEMENTED / READY FOR REVIEW**；Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = **NOT STARTED**；Batch 4C = **FROZEN / ACCEPTED**；Batch 5 = **PENDING**。

4F-A 原始定义核对：

- `NQ_CI_DEPENDENCY_AUDIT_PLAN.md` 原本存在 `4F-A plan review`，但该项属于已完成的 plan review，不是后续 execution batch。
- 本轮将 execution sequence 单独同步为：4F-A dependency audit input / toolchain preflight → 4F-B sanitized advisory audit summary → 4F-C SBOM report-only → 4F-D PR dependency delta review → 4F-E GitHub Actions / CLI supply-chain pinning → 4F-F Dependabot / Renovate governance。

复核命令（已执行 / 本节记录本轮最终复核要求）：

```powershell
git status --short
git branch --show-current
git diff --check
git diff --stat
rg -n "4F-A|4F-B|4F-C|4F-D|4F-E|4F-F|dependency audit|SBOM|Dependabot|Renovate|Batch 5" `
  docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md `
  docs/current/NQ_CI_SECURITY_GUARD_PLAN.md `
  docs/current/NQ_CI_BASELINE_PLAN.md `
  docs/current/NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md `
  docs/current/README.md `
  docs/current/STATUS.md `
  docs/current/TESTING.md `
  docs/current/WORKLOG.md
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- "backend/**/db/migration"
```

未执行：

- 未运行 `npm audit`、`pip-audit`、Maven vulnerability audit、SBOM generation 或外部扫描。
- 未运行 backend Maven / frontend build / E2E / Python pytest / mypy / ruff，因为本轮只改文档状态和执行顺序，不改代码、workflow、测试或依赖文件。
- 未上传 artifact、SBOM、raw JSON、dependency tree、lockfile 或审计报告。

边界确认：

- 未修改 `.github/workflows/ci.yml`；未新增 GitHub Actions job。
- 未修改 backend / frontend / research / scripts / deploy / migration / 测试。
- 未修改 `pom.xml`、`package.json`、`package-lock.json`、`pyproject.toml` 或 requirements 文件。
- Batch 4F 任一后续产物上传仍必须经过 Batch 4C redaction gate。
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter 均未开启、未接入、未实现。

## NQ-CI-SECURITY-GUARD-BATCH-4F-PLAN-REVIEW（2026-06-18）

本轮是 GateK CI Batch 4F **dependency audit / supply-chain audit plan review**：只读评审 `NQ_CI_DEPENDENCY_AUDIT_PLAN.md` 是否可作为 implementation baseline。结论 **PASS / ACCEPTED**；Batch 4F plan = **ACCEPTED AS IMPLEMENTATION BASELINE**；Batch 4F implementation = **NOT STARTED**；Batch 4C = **FROZEN / ACCEPTED**；Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 5 = **PENDING**。LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter 均未开启、未接入、未实现。

复核结论：

- 计划覆盖 Java / Maven、frontend / npm、Python / research、GitHub Actions supply-chain、action SHA pinning、CLI checksum pinning、SBOM、Dependency Review、Dependabot / Renovate、CI blocking/advisory 边界。
- 计划明确 dependency tree / lockfile / SBOM / vuln report 默认不是 credential，但属于 sensitive engineering artifact；raw dependency report / raw SBOM / raw lockfile 不得直接上传，必须复用 Batch 4C pre-upload redaction baseline。
- 计划未要求 `npm audit fix`、未要求直接升级依赖、未要求修改 POM / package lock / pyproject / requirements。
- 计划没有把既有 npm advisories 直接设为 blocking；Python research 无 lockfile 的边界被列为 advisory/report-only 起步。
- GitHub Actions major tag / gitleaks checksum pinning gap 被列为后续 hardening，不是本轮实现。

复核命令（已执行）：

```powershell
Get-Location
git status --short
git branch --show-current
git log --oneline -8
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- "backend/**/db/migration"
rg -n "Java|Maven|frontend|npm|Python|research|GitHub Actions|action|SHA|checksum|SBOM|Dependency Review|Dependabot|Renovate|Blocking|Advisory|report-only|lockfile|package-lock|raw|Batch 4C|Batch 5|npm audit fix|upgrade|pyproject|gitleaks|major tag|major-tag|checksum" docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md
rg --files -g 'pom.xml' -g 'package.json' -g 'package-lock.json' -g 'pyproject.toml' -g 'requirements*.txt' -g '.github/workflows/*.yml' -g '!node_modules' -g '!target' -g '!build' -g '!dist' -g '!test-results'
```

敏感信息检查：

- 宽松前缀扫描仅输出 file/line/rule，命中为 workflow/docs 中的规则定义、前缀说明、allowlist、false-positive 和 proof 文本；未输出 secret value。
- 高置信 credential 正则结果：`NO_HIGH_CONFIDENCE_CREDENTIAL_PATTERN_HITS`。

结果摘要：

- 预检：`Get-Location` = `F:\project\nexus-quant`；branch `dev`；编辑前 `git status --short` clean。
- `git diff --check`：通过。
- `git diff -- .github/workflows/ci.yml`：空。
- `git diff -- backend frontend research scripts deploy`：空。
- `git diff -- "backend/**/db/migration"`：空。
- 依赖入口盘点：Maven POM、`frontend/package.json`、`frontend/package-lock.json`、`research/py/pyproject.toml`；无 tracked `requirements*.txt`。

未执行：

- 未运行 backend Maven / frontend build / E2E / Python pytest / mypy / ruff，因为本轮只读 plan review，未改代码、workflow、测试、migration、frontend、research、scripts、deploy。
- 未运行 `npm audit`、Maven vulnerability audit、`pip-audit`、SBOM generation、Dependency Review、Dependabot / Renovate。
- 未调用外部 dependency audit 上传服务；未上传 artifact。

边界确认：

- 未修改 `.github/workflows/ci.yml`；未新增 GitHub Actions job。
- 未修改 backend / frontend / research / scripts / deploy；未新增 migration；未改测试。
- 未修改 `pom.xml`、`package.json`、`package-lock.json`、`pyproject.toml` 或 requirements 文件。
- 未开启 LIVE / AI / DH runtime；未实现 RealClient / real provider / real exchange adapter。

## NQ-CI-SECURITY-GUARD-BATCH-4F-DEPENDENCY-AUDIT-PLAN（2026-06-18）

本轮是 GateK CI Batch 4F **dependency audit / supply-chain audit planning-only**：只规划 Java/Maven、frontend/npm、Python/research、GitHub Actions supply-chain、action SHA pinning、SBOM、Dependency Review、Dependabot/Renovate、CI blocking/advisory 分层、raw dependency report / SBOM / artifact hygiene、与 Batch 4C / Batch 5 的边界。结论 **PLAN READY FOR REVIEW / PLAN ONLY / NOT IMPLEMENTED**，P0/P1 planning blockers = 0。Batch 4F dependency audit = **PLAN ONLY / NOT IMPLEMENTED**；Batch 4C = **FROZEN / ACCEPTED**；Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 5 = **PENDING**。

只读依据：

- `.github/workflows/ci.yml` 使用 GitHub official actions major tag（`checkout@v4`、`setup-java@v4`、`upload-artifact@v4`、`setup-node@v4`、`setup-python@v5`）；gitleaks CLI 固定 `8.18.4` 但未做 SHA256 checksum pin。
- Java/Maven 依赖入口为 `backend/pom.xml` + 22 个 child `pom.xml`；现有 `maven-dependency-plugin:3.8.1:build-classpath` 只用于 classpath 准备，不是漏洞审计。
- frontend 依赖入口为 `frontend/package.json` + `frontend/package-lock.json` lockfile v3；既有 `npm audit` advisory summary 仍按非阻断风险记录。
- research Python 依赖入口为 `research/py/pyproject.toml`；runtime `dependencies = []`，dev extra 为 `pytest>=8.0`、`mypy>=1.8`、`ruff>=0.8`；无 requirements 文件。

复核命令（已执行）：

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
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- backend/**/db/migration
git status --short
```

结果摘要：

- 预检：`Get-Location` = `F:\project\nexus-quant`；branch `dev`；编辑前 `git status --short` clean。
- Dependency audit 现状 grep：已有 docs 只把 `npm audit` / Maven dependency check / `pip-audit` 记录为 Batch 4F optional / later plan；未发现已实现的 dependency audit CI job、SBOM job、Dependency Review、Dependabot 或 Renovate config。
- 依赖入口盘点：找到 Maven POM、`frontend/package.json`、`frontend/package-lock.json`、`research/py/pyproject.toml`；无 tracked `requirements*.txt`。
- 收尾 diff 验证：`git diff --check` 通过；`.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration 均无 diff；`git status --short` 仅显示允许的 `docs/current` 文档变更。

未执行：

- 未运行 backend Maven / frontend build / E2E / Python pytest / mypy / ruff。本轮是 docs-only planning，不改 workflow、代码、测试、migration、frontend、research、scripts、deploy、POM、lockfile 或 pyproject。
- 未运行 `npm audit`、Maven vulnerability audit、`pip-audit`、SBOM generation、Dependency Review、Dependabot / Renovate。
- 未调用外部真实 dependency audit 上传服务；未上传 artifact。

边界确认：

- 未修改 `.github/workflows/ci.yml`；未新增 GitHub Actions job。
- 未改 backend / frontend / research / scripts / deploy；未新增 migration；未改测试。
- 未改 `frontend/package-lock.json`、`backend/**/pom.xml`、`research/py/pyproject.toml`。
- 未上传 raw dependency report / dependency tree / lockfile / SBOM。
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter 均未开启、未接入、未实现。

## NQ-CI-SECURITY-GUARD-BATCH-4C-FREEZE-REVIEW（2026-06-18）

本轮是 GateK CI Batch 4C overall **security artifact/log redaction baseline freeze review**：只判断已冻结的 4C-B pre-upload artifact redaction gate 与 4C-C log redaction proof 是否可以共同收口为 Batch 4C overall baseline。结论 **PASS / ACCEPTED / FROZEN**，P0/P1 blockers = 0。Batch 4C overall = **FROZEN / ACCEPTED**；Batch 4C-B = **FROZEN / ACCEPTED**；Batch 4C-C = **FROZEN / ACCEPTED**；Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 4F = **OPTIONAL / NOT STARTED**；Batch 5 = **PENDING**。

冻结依据：

- Batch 4C-B pre-upload artifact redaction gate 已 FROZEN / ACCEPTED（immutable green run `27701669084`，workflow blob `4a40ef78`，commit `c734102d` introduced the gate，P0/P1=0）。
- Batch 4C-C log redaction proof 已 FROZEN / ACCEPTED（immutable green run `27732660516`，7/7 jobs green，14 类 high-risk pattern 真实值命中 = 0，P0/P1/P2 blockers = 0）。
- 当前 `dev` 包含 4C-B freeze 记录与 4C-C freeze review 文档。
- `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration 当前无 diff。
- credential grep 命中仅为 workflow regex、规则定义、前缀说明、allowlist、false-positive 描述或历史 proof 文本；未发现真实 value-bearing credential material。

复核命令（已执行）：

```powershell
Get-Location
git status --short
git branch --show-current
git log --oneline -8
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- backend/**/db/migration
git grep -l -E "AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|sk-ant-|sk-proj-|github_pat_|ghp_|gho_|BEGIN [A-Z ]*PRIVATE KEY" -- docs/current .github
git grep -c -E "AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|sk-ant-|sk-proj-|github_pat_|ghp_|gho_|BEGIN [A-Z ]*PRIVATE KEY" -- docs/current .github
```

结果摘要：

- 预检：`Get-Location` = `F:\project\nexus-quant`；`git status --short` clean（编辑前）；branch `dev`。
- `git log --oneline -8` 包含 `ad8f9a2c docs(ci): freeze Batch 4C-B pre-upload artifact redaction gate baseline` 与 `ba91baca docs(ci): freeze Batch 4C-C log redaction proof`。
- `git diff --check` / `git diff --stat` clean（编辑前）。
- `.github/workflows/ci.yml` / backend / frontend / research / scripts / deploy / migration：0 diff（编辑前）。
- credential grep：候选文件为 workflow/docs 中的规则定义、前缀描述、allowlist / false-positive 说明和历史 proof 文本；未发现真实 value-bearing credential material；本轮只输出文件、计数和 `file:line:rule` 分类，未打印完整命中行或 secret value。

未执行：

- 未运行 backend Maven / frontend build / E2E / Python pytest / mypy / ruff。本轮是 docs-only freeze review，不改业务代码、测试、migration、frontend、research。
- 未调用 GitHub Actions run log 下载命令；本轮复用已冻结 4C-C proof 文档中的 immutable green run `27732660516` 证据。
- 未读取本地 logs；未上传 logs artifact。

边界确认：

- 未修改 `.github/workflows/ci.yml`；未新增 static assertion step；未新增 GitHub Actions job。
- 未改 backend / frontend / research / scripts / deploy；未新增 migration。
- 未读取本地 logs，未上传 logs artifact，未打印 secret value / 完整命中行。
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter 均未开启、未接入、未实现。

## NQ-CI-SECURITY-GUARD-BATCH-4C-C-FREEZE-REVIEW（2026-06-18）

本轮是 GateK CI Batch 4C-C **log redaction proof freeze review**：只判断已完成的 log proof 是否可以冻结为子基线。结论 **PASS / ACCEPTED / FROZEN**，P0/P1/P2 blockers = 0。Batch 4C-C = **FROZEN / ACCEPTED**；历史状态（4C-C 子冻结当时）：Batch 4C overall = **NOT FROZEN**；后续已由 `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md` 收口为 **FROZEN / ACCEPTED**。Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 4F = **OPTIONAL / NOT STARTED**；Batch 5 = **PENDING**。

冻结依据：

- immutable proof run `27732660516`：commit `a6d4bf74`，event `push / dev`，status `completed / success`，7/7 jobs green。
- `ci.yml` blob `4a40ef78` 在当前 HEAD（`d39cb3b1`）、`d3e828c0`、`a6d4bf74`、`66cb3d40`、`c734102d` 均一致，proof run 对当前 `dev` workflow 等价有效。
- 7 jobs 均纳入 proof：Diff check / No-outbound guard / Backend Maven test / PostgreSQL / Flyway smoke / Frontend build / Research quality gate / Secret scan。
- 14 类 high-risk pattern 真实值命中 = 0；false positive 分类完整且非阻断。
- proof 不打印真实 secret value，不打印可能含值的完整 matching line。

复核命令（已执行）：

```powershell
git status --short
git branch --show-current
git log --oneline -5
git diff --check
git diff --stat
git rev-parse HEAD:.github/workflows/ci.yml d39cb3b1:.github/workflows/ci.yml d3e828c0:.github/workflows/ci.yml a6d4bf74:.github/workflows/ci.yml 66cb3d40:.github/workflows/ci.yml c734102d:.github/workflows/ci.yml
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
gh run view 27732660516 --json databaseId,headSha,headBranch,event,status,conclusion,workflowName,jobs,createdAt,updatedAt,url
git grep -l -E "AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,}|sk-ant-|sk-proj-|github_pat_|ghp_|gho_|BEGIN [A-Z ]*PRIVATE KEY" -- docs/current .github
```

结果摘要：

- 预检：`git status --short` clean（编辑前）；branch `dev`。
- `git diff --check` / `git diff --stat` clean（编辑前）。
- workflow blob：全部为 `4a40ef78...`。
- `.github/workflows/ci.yml` / backend / frontend / research / scripts / deploy：0 diff（编辑前）。
- GitHub run metadata：`27732660516` completed / success，7 jobs success。
- credential grep：候选文件为 workflow/docs 中的规则定义、前缀描述、allowlist / false-positive 说明和历史 proof 文本；未发现真实 value-bearing credential material。

未执行：

- 未运行 backend Maven / frontend build / E2E / Python pytest / mypy / ruff。本轮是 docs-only freeze review，不改业务代码、测试、migration、frontend、research。
- 未下载或持久化完整 CI logs；未上传 logs artifact。

边界确认：

- 未修改 `.github/workflows/ci.yml`；未新增 static assertion step；未新增 GitHub Actions job。
- 未改 backend / frontend / research / scripts / deploy；未新增 migration。
- 未读取本地 logs，未上传 logs artifact，未打印 secret value / 完整命中行。
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter 均未开启、未接入、未实现。

## NQ-CI-SECURITY-GUARD-BATCH-4C-C-LOG-REDACTION-PROOF-IMPL（2026-06-18）

本轮是 GateK CI Batch 4C-C **log redaction proof implementation**：基于最近一次 green GitHub Actions run 的 per-job logs 产出 review-time log redaction proof。结论 **LOG PROOF COMPLETED / PENDING FREEZE REVIEW**，P0/P1 = 0。**未改 `.github/workflows/ci.yml`**（静态断言列为可选 future hardening）/ 代码 / 测试 / migration / frontend / research / scripts / deploy；本轮仅在允许的 `docs/current` CI 文档记录 proof。**Batch 4C-C 不写 FROZEN；Batch 4C 整体仍 NOT FROZEN**；4C-B 仍 FROZEN / ACCEPTED；4B 仍 FROZEN / ACCEPTED；4F / Batch 5 仍 NOT STARTED / PENDING。

Proof run：`27732660516`（commit `a6d4bf74`，event push / branch dev，completed / success，7/7 jobs green）。ci.yml blob `4a40ef78` 在 HEAD（`d3e828c0`）/ `a6d4bf74` / `66cb3d40` / `c734102d` 四处一致——proof run 的 ci.yml 与当前 HEAD 字节一致。HEAD 自身 run（`27733445791`）评审时 in_progress，按计划取 latest green run（blob 一致故等价）。取证：`gh run view 27732660516 --log` 拉临时文件扫描后即删除，未读本地 logs、未持久化日志到仓库、未上传 logs artifact；扫描只取 count / sanitized category，从不打印命中真实值。

per-job + pattern 复核（14 类，真实值命中 = 0）：

| 复核项 | 结果 | 证据（sanitized）|
| --- | --- | --- |
| 7 jobs 全 green 且全复核 | 通过 | Diff check / No-outbound guard / Backend Maven test / PostgreSQL / Flyway smoke / Frontend build / Research quality gate / Secret scan 全 success |
| 完整 AKIA / ASIA + 16 | 0 真实值 | 仅 gate/secret-scan step-script 正则定义回显（FP）|
| sk- / sk-ant- / sk-proj- 长串 | 0 真实值 | 仅 step-script 正则定义回显（FP）|
| github_pat_ / ghp_ / gho_ 长串 | 0 真实值 | `GITHUB_TOKEN` 平台 mask 为 `***`（≥53 处 `***`）|
| xoxb- / xoxp- 长串 | 0 | 无命中 |
| 完整 PEM（含 `-----`）| 0 真实值 | 仅 step-script dash-omitted 正则定义回显（FP）|
| value-bearing 凭证赋值真实值 | 0 真实值 | 仅 step-script 赋值正则定义 + disposable 短值 |
| credentials-in-URL | 0 | 无命中 |
| signature 真实值 | 0 | 无命中 |
| raw request / raw response 真实报文 | 0 | 无命中 |
| encrypted_payload / decrypted_payload 真实值 | 0 | 无命中（DH 仅契约字段名，未进 CI runtime）|
| Spring Boot generated password | 0 真实凭证 | 6 次「generated security password」=ephemeral dev password，值未打印（P3）|
| disposable CI PostgreSQL 值 | 0 真实凭证 | `123456`×5（backend）/ `nq_ci_password`×2（postgres-flyway，service-init 在 mask 前显示）；明文已在公开 ci.yml（P3）|
| platform token mask | 生效 | `***` mask active |
| printenv / set -x / env dump | 0 | 无 `+ cmd` set-x 回显、无 printenv 调用、无 env dump |
| pre-upload gate green | 通过 | `no high-risk credential pattern ... (text-only, fail closed)`，artifact 74666 bytes 上传 |
| secret-scan green | 通过 | gitleaks 8.18.4 `--redact` `no leaks found`，backstop `no non-allowlisted matches`，sanitized 失败分支未执行；唯一 `RuleID=` 命中为 jq 模板 step-script 回显（line 222 cyan `##[group]Run`）|
| finding / proof 不输出 secret value | 通过 | 全程 count / sanitized category；Spring password、disposable 值均 redact |

复核命令（已执行）：

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
git grep -nE "AKIA[0-9A-Z]{16}" docs/current
gh run view 27732660516 --json status,conclusion,jobs
gh run view 27732660516 --log   # 临时文件扫描后删除；未读本地 logs、未持久化、未上传
rg "printenv|set -x|env dump|add-mask|redact|redaction|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|connection string|signature|raw request|raw response|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action|AKIA|ASIA|LIVE|RealClient" .github docs/current backend frontend research
```

`git status --short` clean（取证前）；`git diff --check` clean；forbidden 区域（backend / frontend / research / scripts / deploy / migration / ci.yml）0 diff；`git grep -nE 'AKIA[0-9A-Z]{16}' docs/current` = 0。本轮未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff（proof 取自远端 CI run 日志，不本地重跑）。

Review decision：LOG PROOF COMPLETED / PENDING FREEZE REVIEW。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-C-FREEZE-REVIEW`（基于 immutable green run `27732660516`）、（可选）静态断言轮、Batch 4F later plan、Batch 5 planning，或暂停 CI 线。Batch 4C-C 不得写 FROZEN；Batch 4C 整体不得写 FROZEN；4F / Batch 5 不得写 started。

## NQ-CI-SECURITY-GUARD-BATCH-4C-C-PLAN-REVIEW（2026-06-18）

本轮是 GateK CI Batch 4C-C **plan review**：评审 `docs/current/NQ_CI_LOG_REDACTION_PROOF_PLAN.md` 是否可作为 Batch 4C-C review / proof baseline。结论 **PASS / ACCEPTED AS PROOF / REVIEW BASELINE**，P0/P1 = 0，28 项评审全部满足。**只读评审**（HEAD `a6d4bf74`，工作区 clean），未改 `.github/workflows/ci.yml` / 代码 / 测试 / migration / frontend / research / scripts / deploy，未新增 log 扫描 job / step；本轮仅在允许的 `docs/current` CI 文档内追加 plan-review 记录。**Batch 4C-C 仍 PLAN ONLY / NOT IMPLEMENTED；Batch 4C 整体仍 NOT FROZEN**；Batch 4C-B 仍 FROZEN / ACCEPTED；4B 仍 FROZEN / ACCEPTED；4F / Batch 5 仍 NOT STARTED / PENDING。

ci.yml 只读复核锚点（28 项详见 `NQ_CI_LOG_REDACTION_PROOF_PLAN.md`「Plan review」段）：

| 复核项 | 结果 | 证据（ci.yml HEAD `a6d4bf74`）|
| --- | --- | --- |
| 无 `set -x` / `printenv` / `env` dump | 通过 | `rg` 于 ci.yml 0 命中；7 jobs 均 `set -euo pipefail` |
| `permissions` 仅 `contents: read` | 通过 | line 12-13（顶层）/ 777-778（secret-scan）|
| 无 `id-token` / `continue-on-error` / `GITLEAKS_LICENSE` / `gitleaks-action` | 通过 | `rg` 0 命中 |
| `::add-mask::` disposable DB 值 | 通过 | line 365-367 |
| secret-scan `--redact` + sanitized | 通过 | line 886（`--redact`）/ 896-902（RuleID/File/Lines/Fingerprint；never Secret/Match/matched line/commit/author）|
| pre-upload gate finding `rule \| file` | 通过 | line 577 / 618 / 659（`grep -rIlE -l`）/ 668 |
| 唯一 `upload-artifact` | 通过 | line 676（`nq-postgres-flyway-schema-artifacts`，`if-no-files-found: error`、retention 14/7）|
| backend `123456` 未 mask（P3 属实） | 通过 | line 174 / 188 未 mask vs line 367 已 mask |
| docs/current 无完整 AWS-key 字面量 | 通过 | `git grep -nE 'AKIA[0-9A-Z]{16}' docs/current` = 0（含本 plan）|

复核命令（已执行）：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
git grep -nE "AKIA[0-9A-Z]{16}" docs/current
rg "printenv|set -x|env dump|add-mask|redact|redaction|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|connection string|signature|raw request|raw response|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action|AKIA|ASIA|LIVE|RealClient" .github docs/current backend frontend research
```

`git status --short` clean（评审前）；`git diff --check` clean；forbidden 区域（ci.yml / backend / frontend / research / scripts / deploy / migration）0 diff；rg 命中均为 docs 事实源 / ci.yml 既有项 / credential-governance 代码引用，无真实 credential material。本轮 docs-only / review-only，未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff。

Review decision：PASS / ACCEPTED AS PROOF / REVIEW BASELINE。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-C` 实现轮、Batch 4C-C plan fix、Batch 4F later plan、Batch 5 planning，或暂停 CI 线。

## NQ-CI-SECURITY-GUARD-BATCH-4C-C-LOG-REDACTION-PROOF-PLAN（2026-06-18）

本轮是 GateK CI Batch 4C-C **log redaction proof planning**：规划「GitHub Actions logs 不输出真实 credential material」的证明方式，新增 `docs/current/NQ_CI_LOG_REDACTION_PROOF_PLAN.md` 并同步 5 个 CI 事实源文档。结论 **PLAN ONLY / NOT IMPLEMENTED**，P0/P1 planning blockers = 0。**只改 `docs/current` 文档**，未改 `.github/workflows/ci.yml` / 代码 / 测试 / migration / frontend / research / scripts / deploy；未新增 log 扫描 job / step；未上传 artifact。**Batch 4C 整体仍 NOT FROZEN**（4C-C 仅完成 planning）；Batch 4C-B pre-upload artifact redaction gate 仍 FROZEN / ACCEPTED；Batch 4B 仍 FROZEN / ACCEPTED；4F / Batch 5 仍 NOT STARTED / PENDING。

前置：本地 `dev` 原落后 `origin/dev` 6 commits（缺 4C-A 接受 + 4C-B 实现→冻结链，含 immutable run `27701669084`）。经用户确认后以 `git merge --ff-only origin/dev` 干净 fast-forward 到 `ad8f9a2c`（0 本地提交、工作区 clean、merge-base == 原 HEAD），再在对齐后的正确基线上规划，避免在 pre-4C-B 旧副本上改这 7 个文件造成冲突。

只读验证（已执行，HEAD `ad8f9a2c`）：

| 验证项 | 结果 | 证据 |
| --- | --- | --- |
| `git status --short` | clean（编辑前） | 工作区无遗留改动；fast-forward 后 clean |
| `git diff --check` | clean | 无 whitespace error |
| forbidden 区域 0 diff | 通过 | `git diff -- .github/workflows/ci.yml backend frontend research scripts deploy backend/**/db/migration` 全空 |
| ci.yml 无 `printenv` / `env` dump / `set -x` | 通过 | `rg "printenv\|set -x\|env dump" .github/workflows/ci.yml` 无命中（7 jobs 均 `set -euo pipefail`） |
| `::add-mask::` 存在 | 通过 | `postgres-flyway` job 屏蔽 `NQ_FLYWAY_DB_URL` / `NQ_FLYWAY_DB_USER` / `NQ_FLYWAY_DB_PASSWORD`（line 365-367） |
| 无 `continue-on-error` / `id-token` / `GITLEAKS_LICENSE` / `gitleaks-action` | 通过 | `rg` 于 ci.yml 0 命中 |
| `permissions` 仅 `contents: read` | 通过 | 顶层 + secret-scan job 两处 |
| `backend` job disposable DB 值未 mask | 记录为 P3 | `NQ_DB_PASSWORD` / `POSTGRES_PASSWORD` = `123456`（disposable CI-only、非真实凭证，与 `postgres-flyway` 已 mask 不对称） |
| docs/current 无完整 AWS-key 字面量 | 通过 | `git grep -nE 'AKIA[0-9A-Z]{16}\|ASIA[0-9A-Z]{16}'` = 0（4C-B first-run-fix 仍生效） |
| 无真实 credential material | 通过 | rg 命中均为 docs 事实源 / regex pattern / DH 契约字段名 / JWT 代码引用；whole-tree gitleaks 0 findings + backstop 0 已在 Batch 4B / 4C-B 冻结证据中验证 |

复核命令（已执行）：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "printenv|set -x|env dump|add-mask|redact|redaction|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|connection string|signature|raw request|raw response|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action|AKIA|ASIA|LIVE|RealClient" .github docs/current backend frontend research
```

本轮 docs-only / planning-only，未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff（且明确禁止改 workflow / 代码 / 测试 / migration）。4C-C 实现轮验证（本轮不执行）：对目标 GitHub Actions run 以 review-time `gh run view --log` 拉取 7 job logs，按 Pattern checklist 产出 log redaction proof 表。

Review decision：PLAN READY FOR REVIEW。P0/P1 planning blockers = 0。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-C-PLAN-REVIEW`、Batch 4C-C plan fix、4C-C 实现轮、Batch 4F later plan、Batch 5 planning，或暂停 CI 线。Batch 4C-C 不得写成 implemented；Batch 4C 整体不得写成 FROZEN；4F / Batch 5 不得写成 started。

## NQ-CI-SECURITY-GUARD-BATCH-4C-E-PRE-UPLOAD-REDACTION-GATE-FREEZE-REVIEW（2026-06-17）

本轮是 GateK CI Batch 4C-E freeze review：基于 **immutable run `27701669084`**（commit `66cb3d40`）冻结 **Batch 4C-B pre-upload artifact redaction gate** 子基线。结论 **PASS / FROZEN / ACCEPTED**，P0/P1/P2 blockers = 0。只评审 + 改允许的 `docs/current`，未改 `.github/workflows/ci.yml` / 代码 / 测试 / migration / gitleaks 规则；未新增 allowlist、未关闭 security guard。**Batch 4C 整体仍 NOT FROZEN**（4C-C log redaction proof 未开始）；4C-C / 4F / Batch 5 仍 NOT STARTED / PENDING。

frozen baseline = `.github/workflows/ci.yml` `postgres-flyway` job 的 `Pre-upload redaction gate (PostgreSQL schema artifacts)` step（ci.yml blob `4a40ef78`，commit `c734102d` 引入）。已校验 `git rev-parse HEAD:.github/workflows/ci.yml` == `66cb3d40:` == `c734102d:` == `4a40ef78`，即 green-confirmed 的 gate 与当前 `dev` HEAD 字节一致。

| 评审项（25 项） | 结果 | 证据 |
| --- | --- | --- |
| 1. run `27701669084` completed / success | 通过 | `gh run view`：`status=completed`、`conclusion=success`、`headSha=66cb3d40`、branch dev、event push。 |
| 2. 7/7 jobs green | 通过 | Diff check / No-outbound guard / Backend Maven test / PostgreSQL / Flyway smoke / Frontend build / Research quality gate / Secret scan 全 success。 |
| 3. Secret scan green | 通过 | job `81939453367` success；6 个 step 全 success。 |
| 4. gitleaks no leaks found | 通过 | `INF no leaks found` + `gitleaks: no leaks found in tracked working tree.`；backstop `no non-allowlisted matches`。 |
| 5. docs/current 无完整 AKIA 字面量 | 通过 | `git grep -nE 'AKIA[0-9A-Z]{16}' docs/current` = 0（exit 1）。 |
| 6. postgres-flyway job green | 通过 | job `81939453552` success。 |
| 7. pre-upload redaction gate step green | 通过 | step 10 `Pre-upload redaction gate (PostgreSQL schema artifacts)` success。 |
| 8. gate 在 upload 前执行 | 通过 | step 顺序 9 Generate → **10 Pre-upload redaction gate** → 11 Upload。 |
| 9. 唯一 artifact 路径 | 通过 | run artifacts API `total_count=1`，唯一 = `nq-postgres-flyway-schema-artifacts`。 |
| 10. required artifacts 存在 / 非空 | 通过 | gate 内 `test -s` 7 文件（`flyway-info` / `schema-tables` / `schema-columns` / `schema-constraints` / `schema-indexes` / `schema-comments` / `schema-dump.sql`）均过。 |
| 11. binary text-only guard 未误杀 | 通过 | gate 输出 `... (text-only, fail closed)`；schema artifacts 全 text，binary 分支未触发。 |
| 12. data-row 检查通过 | 通过 | `grep -qE '(^INSERT INTO ...)'` 静默，无 data-row finding。 |
| 13. credential pattern 检查通过 | 通过 | 22 条 per-rule `grep -rIlE -l` 0 命中，gate 输出 `no high-risk credential pattern`。 |
| 14. gate finding 不输出 secret value / matched line | 通过 | gate 仅输出 `rule | file`（本次无 finding）；data-row 用 `-q`、credential 用 `-l`，从不回显匹配行 / 值。 |
| 15. artifact upload 成功 | 通过 | `Artifact nq-postgres-flyway-schema-artifacts has been successfully uploaded! Final size is 74664 bytes`。 |
| 16. 未上传 raw gitleaks JSON report | 通过 | report 仅写 `${RUNNER_TEMP}/...gitleaks-report.json`，`ci.yml` 唯一 `upload-artifact`（line 676）path = `artifacts/postgres-flyway/`，未引用 report。 |
| 17. 未新增 surefire / frontend / research artifact | 通过 | `ci.yml` 仅 1 处 `upload-artifact`；run artifacts `total_count=1`。 |
| 18. 未用 repository secrets / write / id-token / continue-on-error | 通过 | `ci.yml` 仅两处 `permissions: contents: read`（line 12 顶层 / line 777 secret-scan）；无 `continue-on-error` / `id-token` / write perms / `secrets.` 引用。 |
| 19. 未扫描禁止目录 | 通过 | gate 只扫 `artifacts/postgres-flyway/`；secret-scan safe-file 列表排除 `.env*` / secrets / credentials / `*.pem` / `*.key` / target / node_modules / dist / build / logs / dumps / backups / `.git`（`excluded=3`）。 |
| 20. 未读取 / 输出真实 credential material | 通过 | gate / secret-scan 均 `--redact` / `rule|file` only；rg 命中均为 regex 模式 / 文档字段名 / JWT 代码引用。 |
| 21. 未调用真实交易所 | 通过 | no-outbound guard job green（无 credential env、denylist 覆盖、guard test 通过）；Batch 3 仍 frozen。 |
| 22. 未开启 LIVE / AI / DH runtime | 通过 | docs 内 LIVE 相关均断言 disabled；无运行态启用。 |
| 23. 未实现 RealClient / real provider / real probe adapter | 通过 | 无代码改动（forbidden 区域 0 diff）。 |
| 24. 4C-C / 4F / Batch 5 仍 NOT STARTED / PENDING | 通过 | 文档保持 NOT STARTED / PENDING，未写 started。 |
| 25. Batch 4C 整体仍 NOT FROZEN | 通过 | 仅冻结 4C-B pre-upload gate 子基线；4C-C log redaction proof 未开始。 |

复核命令（已执行）：

```powershell
git status --short
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- .github/workflows/ci.yml
git diff -- backend / frontend / research / scripts / deploy / backend/**/db/migration
git grep -nE "AKIA[0-9A-Z]{16}" docs/current
git rev-parse HEAD:.github/workflows/ci.yml ; 66cb3d40: ; c734102d:   # 三处 blob == 4a40ef78
gh run view 27701669084 --json status,conclusion,jobs
gh run view --job 81939453367 --log   # secret-scan：no leaks found，无 RuleID finding
gh run view --job 81939453552 --log   # postgres-flyway：gate 在 upload 前、74664 bytes 上传
gh api repos/<owner>/<repo>/actions/runs/27701669084/artifacts   # total_count=1
```

Review decision: **PASS / FROZEN / ACCEPTED**。P0/P1/P2 blockers = 0。Batch 4C-B pre-upload artifact redaction gate 成为当前 `dev` 的 pre-upload artifact redaction baseline（frozen baseline = `ci.yml` pre-upload redaction gate step blob `4a40ef78` / commit `c734102d`，immutable run `27701669084` 确认）。Batch 4C 整体仍 NOT FROZEN（4C-C 未开始）。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-C`（log redaction proof planning）、`NQ-CI-SECURITY-GUARD-BATCH-4F`（dependency audit later plan）、Batch 5 planning，或暂停 CI 线。不得把 Batch 4C 整体写成 FROZEN；不得把 4C-C / 4F / Batch 5 写成 started。

## NQ-CI-SECURITY-GUARD-BATCH-4C-B-SECOND-PASS-FIRST-RUN-REVIEW（2026-06-17）

本轮是 GateK CI Batch 4C-B second-pass first-run review：评审 doc-only fix（commit `66cb3d40`）后的 second-pass GitHub Actions run（`27701669084`）。结论 **PASS / ACCEPTED FOR FIRST GREEN RUN AFTER FIX** → Batch 4C-B **FIRST GREEN RUN CONFIRMED AFTER DOC FIX**。只评审 + 改允许的 docs，未改 `ci.yml` / 代码 / 测试 / migration。Batch 4C 整体仍 NOT FROZEN；4C-C / 4F / Batch 5 仍 NOT STARTED / PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | **success** | second-pass run `27701669084`（commit `66cb3d40`）completed / success，7/7 jobs green。 |
| Secret scan | **success** | gitleaks 8.18.4 / `--redact` / `contents: read`；`tracked=1304 safe=1301 excluded=3`；`no leaks found`；backstop `no non-allowlisted matches`。**不再命中 `TESTING.md` aws-access-token**（无 `RuleID=` finding，无值输出）。 |
| AKIA 字面量清除 | 已确认 | `git grep -nE 'AKIA[0-9A-Z]{16}' docs/current` = 0（committed tree）。 |
| PostgreSQL / Flyway smoke | success | gate 所在 job 全绿（1m14s）。 |
| pre-upload redaction gate step | **success** | `✓ Pre-upload redaction gate`，输出 `no high-risk credential pattern in artifacts/postgres-flyway (text-only, fail closed)`；无 data-row / credential finding；binary 未误杀；未输出 secret value / matched line。 |
| gate-before-upload 顺序 | 已确认 | `✓ Generate ... artifacts` → `✓ Pre-upload redaction gate` → `✓ Upload ... artifacts`。 |
| artifact 上传 | 已确认 | `nq-postgres-flyway-schema-artifacts`（74664 bytes）成功上传；仍唯一 upload-artifact；未上传 raw gitleaks report；未新增 surefire / frontend / research artifact。 |
| no-outbound / 其余 4 job | success | Diff check / No-outbound guard / Backend Maven test / Frontend build / Research quality gate 全绿（未回归）。 |
| 安全边界 | 通过 | `contents: read`；无 repository secret / write / id-token / continue-on-error / `gitleaks-action` / `GITLEAKS_LICENSE`；`--no-git --redact`；未扫描禁止目录；未调用真实交易所；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter。 |
| 本地 build/test | 未运行 | review-only / docs-only；评审基于 immutable GitHub Actions run 日志。 |

复核命令（已执行）：

```powershell
git status --short
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- .github/workflows/ci.yml
git diff -- backend / frontend / research / scripts / deploy / backend/**/db/migration
git grep -nE "AKIA[0-9A-Z]{16}" docs/current
gh run view 27701669084
gh run view 27701669084 --job <secret-scan> --log   # sanitized；no RuleID finding
gh run view 27701669084 --job <postgres-flyway> --log
```

Review decision: PASS / ACCEPTED FOR FIRST GREEN RUN AFTER FIX。secret-scan 不再误报、postgres-flyway pre-upload gate 仍 green、artifact 仍在 gate 后正常上传、其余 job 未回归。Batch 4C-B 推进为 FIRST GREEN RUN CONFIRMED AFTER DOC FIX；不得直接写 FROZEN / ACCEPTED。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-E`（pre-upload gate freeze review）、`NQ-CI-SECURITY-GUARD-BATCH-4C-C`（log redaction proof planning），或暂停 CI 线。不得把 Batch 4C-C / 4F / Batch 5 写成 started。

## NQ-CI-SECURITY-GUARD-BATCH-4C-B-FIRST-RUN-FIX（2026-06-17）

本轮是 GateK CI Batch 4C-B first-run fix：最小 doc-only fix，消除本文件「gate dry-run — fake secret」单元格内一处 AWS access key id 形态字面量对 gitleaks `aws-access-token` 的误报（first run `27698183911` 唯一 finding，非真实凭证、非 gate 缺陷）。结论 **FIRST-RUN-FIX APPLIED / PENDING SECOND-PASS CI RUN**。未改 `.github/workflows/ci.yml`、gitleaks 规则 / 配置、gate；未新增 allowlist；未关闭 security guard。Batch 4C 整体仍 NOT FROZEN；4C-C / 4F / Batch 5 仍 NOT STARTED / PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `git branch --show-current` = `dev`。 |
| 修复目标 | 已修复 | 本文件「gate dry-run — fake secret」单元格内完整 AWS-key 字面量改写为 shaped placeholder 文字描述（`AKIA` 前缀 + 16 位占位，不写完整字面量）。 |
| 完整字面量清除 | 通过 | `git grep -nE 'AKIA[0-9A-Z]{16}' docs/current` = 0 命中；docs/current 内无其它 `ASIA` / `sk-ant-` / `sk-proj-` / `github_pat_` / `gh[pousr]_{30,}` 完整凭证形态字面量。 |
| ci.yml 未改 | 已确认 | `git diff -- .github/workflows/ci.yml` 为空；未改 gitleaks 规则 / 配置 / allowlist / default ruleset。 |
| security guard 未弱化 | 通过 | 未放宽 gitleaks 规则、未 broad allowlist、未 allowlist 整个 `TESTING.md`、未关闭 default ruleset、未用 continue-on-error。 |
| 安全边界 | 通过 | 未读取/输出真实 credential material；未扫描禁止目录；未上传 artifact；未用 repository secret / write / id-token；未调用真实交易所；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter。 |
| 本地 gitleaks | 未运行 | 本地 Windows 无 gitleaks 二进制（与 Batch 4B 一致）；gitleaks 层最终结果待 GitHub Actions second-pass run 确认。 |
| 本地 build/test | 未运行 | docs-only fix；未运行 backend Maven / frontend / Python。 |

复核命令（已执行 / 待执行）：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
git grep -nE "AKIA[0-9A-Z]{16}" docs/current
rg "aws-access-token|AKIA|ASIA|sk-ant-|sk-proj-|github_pat_|ghp_|gho_|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action" docs/current .github
```

Review decision: FIRST-RUN-FIX APPLIED / PENDING SECOND-PASS CI RUN。doc-only 修复完成，完整 AWS-key 字面量已清除，未改 `ci.yml` / gate / gitleaks 规则 / allowlist。下一步只能是 second-pass first-run review（确认重跑 secret-scan job green、postgres-flyway pre-upload gate 仍 green、其余 job 未回归），或失败则 second-pass fix，或暂停 CI 线。不得把 Batch 4C-B 写成 FIRST GREEN / FROZEN；不得混入 Batch 4C-C / 4F / Batch 5。

## NQ-CI-SECURITY-GUARD-BATCH-4C-D-FIRST-RUN-REVIEW（2026-06-17）

本轮是 GateK CI Batch 4C-D first-run review：评审第一次包含 pre-upload artifact redaction gate 的 GitHub Actions run（`27698183911`，commit `c734102d`）。结论 **FAIL / FIRST-RUN-FIX REQUIRED**：pre-upload gate 本身 first-run GREEN，但整体 run 失败于 secret-scan job 的一处无关文档 gitleaks 误报。只评审 + 改允许的 docs，未改 `ci.yml` / 代码 / 测试 / migration。Batch 4C 整体仍 NOT FROZEN；4C-C / 4F / Batch 5 仍 NOT STARTED / PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | **failure** | run `27698183911` completed / failure；6/7 job green，唯一失败 = Secret scan job。 |
| PostgreSQL / Flyway smoke | success | gate 所在 job 全绿（1m26s）。 |
| pre-upload redaction gate step | **success** | step `✓ Pre-upload redaction gate (PostgreSQL schema artifacts)`，日志 `no high-risk credential pattern in artifacts/postgres-flyway (text-only, fail closed)`。 |
| gate-before-upload 顺序 | 已确认 | `✓ Generate ... artifacts` → `✓ Pre-upload redaction gate` → `✓ Upload ... artifacts`。 |
| 唯一 artifact 上传 | 已确认 | `nq-postgres-flyway-schema-artifacts`（74663 bytes）成功上传；仍唯一 upload-artifact；未上传 raw gitleaks report；`if-no-files-found: error` / retention 有界不变；未新增 surefire / frontend / research artifact。 |
| binary/zip 误杀 | 未发生 | schema 全 text，gate text-only 断言通过，未误杀。 |
| gate finding 输出 | 未触发 | gate 无 finding（clean），未进入 `rule | file` 分支；未输出 secret value / matched line。 |
| Secret scan | **failure** | gitleaks step `leaks found: 1`，fail closed。 |
| 失败分类 | 已确认 | gitleaks default-ruleset FP：4C-B 文档更新把 AWS 官方示例 access key id（`AKIA` 前缀 + 16 字符）写进 `docs/current/TESTING.md`，`aws-access-token` 命中。非 gate 缺陷、非真实泄露（P0=0）。 |
| 日志脱敏 | 已确认 | sanitized 输出仅 `RuleID=aws-access-token File=docs/current/TESTING.md Lines=16-16 Fingerprint=...`；`--redact` 生效，未输出 secret value / matched line / Match / Secret / commit / author。 |
| no-outbound / 其余 5 job | success | Diff check / No-outbound guard / Backend Maven test / Frontend build / Research quality gate 全绿（除 secret-scan 外未回归）。 |
| 安全边界 | 通过 | `contents: read`；无 repository secret / write / id-token / continue-on-error / `gitleaks-action` / `GITLEAKS_LICENSE`；未扫描禁止目录；未调用真实交易所；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter。 |
| 本地 build/test | 未运行 | review-only / docs-only；评审基于 immutable GitHub Actions run 日志。 |

复核命令（已执行）：

```powershell
git status --short
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- .github/workflows/ci.yml
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
gh run view 27698183911
gh run view 27698183911 --job <secret-scan> --log   # sanitized RuleID/File/Lines/Fingerprint only
gh run view 27698183911 --job <postgres-flyway> --log
rg "upload-artifact|Pre-upload redaction gate|redact|redaction|secret|...|LIVE|RealClient" .github docs/current backend frontend research
```

Review decision: FAIL / FIRST-RUN-FIX REQUIRED。pre-upload redaction gate 本身 first-run GREEN（gate step success、upload 前执行、artifact 正常上传、无 finding、无值输出），但整体 run 红，acceptance「GitHub Actions run green」未满足，不得写成 FIRST GREEN RUN CONFIRMED / FROZEN。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-B-FIRST-RUN-FIX`（doc-only：中和 `TESTING.md` 内 AWS 示例 access key id，不改 `ci.yml` / gate / 不放宽核心规则 / 不 broad allowlist），修复后重跑 CI。不得混入 Batch 4C-C / 4F / Batch 5。

## NQ-CI-SECURITY-GUARD-BATCH-4C-B-PRE-UPLOAD-REDACTION-GATE-IMPL（2026-06-17）

本轮是 GateK CI Batch 4C-B implementation：在 `.github/workflows/ci.yml` `postgres-flyway` job 把 `Check PostgreSQL schema artifacts` 改造为 upload 前 fail-closed **`Pre-upload redaction gate`**（binary 拒绝 + data-row 静默检查 + 收敛 credential pattern，finding 只 `rule | file`）。仅改 `ci.yml`，未改业务代码 / 测试 / migration / frontend / research / scripts / deploy。状态 **IMPLEMENTED / PENDING FIRST CI RUN**；Batch 4C 整体仍 NOT FROZEN；4C-C / 4F / Batch 5 NOT STARTED / PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `git branch --show-current` = `dev`；编辑前 `git status --short` 为空。 |
| Forbidden 区域 0 diff | 已确认 | `git diff -- backend / frontend / research / scripts / deploy / backend/**/db/migration` 均空；`git diff --check` 无 whitespace 错误；仅 `.github/workflows/ci.yml` 改动（83 insert / 7 delete），无新增 tracked 文件。 |
| YAML 结构校验 | 通过 | node 解析：7 jobs（diff-check / no-outbound-guard / backend / postgres-flyway / frontend / research / secret-scan），唯一 `upload-artifact`，无 tab 字符。 |
| bash 语法 | 通过 | `bash -n`（提取 gate 逻辑）syntax OK。 |
| gate dry-run — clean | 通过 | 合成 schema-like 文本 artifacts（含 `password_hash` 列名、散文 "API key for ..."、无凭证 URL `https://...` / `jdbc:postgresql://...`）→ GATE-PASS / exit 0，无误报。 |
| gate dry-run — fake secret | 通过 | 合成 fake artifact（AWS access key id shaped placeholder：`AKIA` 前缀 + 16 位大写字母/数字占位，不写完整字面量以免触发 gitleaks `aws-access-token` / URL 内嵌 `user:pass@` / `encrypted_payload=` 赋值）→ fail closed / exit 1，输出仅 `rule | file`；断言三类 secret 值（AWS key 占位 / url password / payload 值）均未出现在输出。 |
| gate dry-run — binary | 通过 | 合成 `trace.zip`（含 NUL/二进制字节）→ `file` 判为 binary，gate 拒绝 / exit 1，仅打印文件名。 |
| secret-scan 自命中回归 | 通过 | 复刻 secret-scan custom backstop 对修改后 `ci.yml` 扫描 → 0 非 allowlisted 命中（dash-omitted PEM、未达长度的 AKIA/ASIA/gh/xox/sk 字面量、无 quoted-value 的 assignment 均不触发）。 |
| gate-before-upload 顺序 | 已确认 | `Pre-upload redaction gate` step（line 569）在 `Upload PostgreSQL schema artifacts`（line 675）+ `actions/upload-artifact@v4`（line 676）之前。 |
| 边界 | 通过 | `permissions: contents: read`；无 repository secret / write / id-token / continue-on-error / `gitleaks-action` / `GITLEAKS_LICENSE`；未上传 raw gitleaks JSON report；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter；未调用真实交易所。 |
| 未验证项 | 已披露 | gitleaks default-ruleset 对修改后 ci.yml 完整 FP 面、真实 PostgreSQL schema 对新增 pattern 的命中面（共享子集已由既有 schema-check 在 Batch 4B 绿灯佐证）待 GitHub Actions 首跑（4C-D）；本地 Windows 未跑 gitleaks（与 Batch 4B 一致）。未运行 backend Maven / frontend build / E2E / Python（本轮只改 CI workflow）。 |

复核命令（已执行）：

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
rg "upload-artifact|redact|redaction|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|connection string|signature|raw request|raw response|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action" .github docs/current
```

Review decision: IMPLEMENTED / PENDING FIRST CI RUN。pre-upload redaction gate 已实现并在 upload 前 fail closed；finding 不输出 secret value / matched line；未上传 raw gitleaks report / 未脱敏 artifact；未使用 repository secret / write / id-token / continue-on-error。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-D`（首跑评审）、首跑失败则 4C-B first-run-fix、或 `NQ-CI-SECURITY-GUARD-BATCH-4C-C`（log redaction proof），或暂停 CI 线。不得写成 FROZEN / ACCEPTED；不得把 Batch 4C-C / 4F / Batch 5 写成 started。

## NQ-CI-SECURITY-GUARD-BATCH-4C-A-PLAN-REVIEW（2026-06-17）

本轮是 GateK CI Batch 4C-A plan review（review-only）：按 23 项 checklist 复核 `NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md` 是否可作为 Batch 4C-B / 4C-C implementation baseline。结论 **PASS / ACCEPTED AS IMPLEMENTATION BASELINE**，P0/P1=0。只评审 + 改允许的 docs，不改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts、deploy。Batch 4C 仍 PLAN ONLY / NOT IMPLEMENTED；Batch 4B 仍 FROZEN / ACCEPTED；Batch 4F OPTIONAL / NOT STARTED；Batch 5 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `git branch --show-current` = `dev`；编辑前 `git status --short` 为空；`git diff --check` clean。 |
| Forbidden 区域 0 diff | 已确认 | `git diff -- .github/workflows/ci.yml / backend / frontend / research / scripts / deploy / backend/**/db/migration` 均空。 |
| Artifact inventory 准确 | 通过 | `ci.yml` 唯一 `actions/upload-artifact@v4`（第 600 行）= `nq-postgres-flyway-schema-artifacts`（7 files），upload 前有 fail-closed redaction step；surefire / frontend / research outputs 未上传；gitleaks JSON report 写 `RUNNER_TEMP` 未上传。 |
| Pre-upload gate 先例 | 已确认 | `Check PostgreSQL schema artifacts`（data-row + credential pattern，fail closed）被识别为通用 gate 先例。 |
| P2 风险识别 | 通过 | 无通用 pre-upload gate、schema-check pattern 窄于 4B backstop、3 处同源漂移、raw report 误上传风险均识别；明文禁止上传 raw gitleaks JSON report；artifact scan 只扫 CI 生成可控输出、禁止扫描本地禁止目录。 |
| Log risk inventory | 通过 | 覆盖 env dump / `set -x` / raw request-response / connection string / signature / credential material / encrypted_payload-decrypted_payload；CI log proof 只 review-time `gh run view --log`，不读本地 logs；finding 只输出 file/path/rule。 |
| Credential pattern 复用 | 通过 | 复用 Batch 4B backstop + schema-check 既有项，规划同源 parity，避免第 4 套漂移；PEM 规则取更宽者（P3 提示）。 |
| 权限 / 边界 | 通过 | 保留 `contents: read`；禁止 repository secret / write / id-token / continue-on-error；4C 不重复 4B、不做 4F / Batch 5；Batch 5 Playwright report 须先过 4C gate；仍禁止 LIVE / AI / DH / RealClient / real provider；允许进入 4C-B。 |
| 安全边界 | 通过 | 未读取 / 输出真实 credential material；未把禁止目录作为数据源扫描；未上传 artifact；未调用真实交易所；未开启 LIVE / AI / DH。 |
| 本地 build/test | 未运行 | review-only / docs-only，禁止改 workflow / 代码 / 测试 / migration；未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff。 |

复核命令（只读，已执行）：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "artifact|upload-artifact|redact|redaction|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|connection string|signature|raw request|raw response|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action" .github docs/current backend frontend research
```

rg 仅用于 tracked safe paths；未扩展到 `.env` / secrets / logs / dumps / backups / target / node_modules / dist / build / `.git`。

Review decision: PASS / ACCEPTED AS IMPLEMENTATION BASELINE。P0/P1=0；记录 2 项非阻断 P3 实现提示（二进制 / zip 产物扫描策略、PEM 规则取更宽者）。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4C-B`（pre-upload redaction gate minimal implementation）、Batch 4C plan fix，或暂停 CI 线。Batch 4C 不得写成 implemented；Batch 4F / Batch 5 不得写成 started。

## NQ-CI-SECURITY-GUARD-BATCH-4C-PLAN（2026-06-17）

本轮是 GateK CI Batch 4C planning-only：规划 artifact / log redaction proof（CI 生成 artifacts / test reports / schema artifacts / logs / 未来 frontend-research outputs 上传或输出前不含真实 credential material）。不修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts、deploy。Batch 4C 当前 PLAN ONLY / NOT IMPLEMENTED；Batch 4B 仍 FROZEN / ACCEPTED；Batch 4F OPTIONAL / NOT STARTED；Batch 5 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `git branch --show-current` = `dev`；编辑前 `git status --short` 为空。 |
| Artifact 盘点 | 已执行 | `.github/workflows/ci.yml` 仅 1 处 `upload-artifact`（`nq-postgres-flyway-schema-artifacts`，7 files），upload 前已有专用 `Check PostgreSQL schema artifacts` redaction step（data-row + credential pattern，fail closed）。 |
| 未上传产物 | 已确认 | gitleaks JSON report 写 `RUNNER_TEMP` 未上传（`--redact`）；surefire reports / frontend build / research outputs 当前均未上传。 |
| Log 风险盘点 | 已执行 | 无 `printenv` / `env` dump / `set -x` / `continue-on-error` / `id-token` / write perms；`postgres-flyway` 用 `::add-mask::` 屏蔽 disposable CI-only DB 值；`permissions` 仅 `contents: read`。 |
| Plan file | 已新增 | `docs/current/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`，状态固定 PLAN ONLY / NOT IMPLEMENTED，拆分 4C-A/4C-B/4C-C/4C-D/4C-E。 |
| 安全边界 | 通过 | 未读取 / 输出真实 credential material；未把禁止目录作为数据源扫描；未上传 artifact；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter；未调用真实交易所。 |
| 本地 build/test | 未运行 | 本轮 docs-only / planning-only，禁止改 workflow / 代码 / 测试 / migration；未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff。 |

计划验证命令：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "artifact|upload-artifact|redact|redaction|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|connection string|signature|raw request|raw response|continue-on-error|id-token|GITLEAKS_LICENSE|gitleaks-action" .github docs/current backend frontend research
```

rg 仅用于 tracked safe paths；未扩展到 `.env` / secrets / logs / dumps / backups / target / node_modules / dist / build / `.git`。

Review decision: PLAN READY FOR REVIEW。P0/P1 planning blockers = 0。下一步只能是 Batch 4C-A plan review、Batch 4C plan fix、Batch 4C-B pre-upload redaction gate minimal implementation，或暂停 CI 线。Batch 4C / 4F / Batch 5 不得写成 implemented / started。

## NQ-CI-SECURITY-GUARD-BATCH-4B-FREEZE-REVIEW（2026-06-17）

本轮是 GateK CI Batch 4B freeze review：冻结 minimal secret scan baseline。基于 immutable run `27674393780`（commit `31540de8`，重新拉取 job logs + HEAD config 校验）。结论 **PASS / FROZEN / ACCEPTED**，P0/P1/P2 blockers = 0。只评审 + 改 docs，不改 workflow / 代码 / 测试 / migration；不进入 Batch 4C / 4F / Batch 5。

| 复核项 | 结果 | 证据 |
| --- | --- | --- |
| ci.yml 自 green run 未变 | 通过 | `git diff 31540de8 HEAD -- .github/workflows/ci.yml` 为空（其后仅 docs 提交 `7369ed4f`）；frozen baseline = commit `31540de8` 的 secret-scan job。 |
| Run 27674393780 | green | conclusion `completed / success`；7 jobs 全 `success`。 |
| Diff check / No-outbound guard / Backend Maven test / PostgreSQL-Flyway smoke / Frontend build / Research quality gate | green | 全 success，既有 baseline 未回归。 |
| Secret scan job | green | job `81846054679`，7 steps 全 success。 |
| gitleaks 版本 | 通过 | `Installed gitleaks version: 8.18.4`（pinned CLI，非 `gitleaks-action`，无 `GITLEAKS_LICENSE`）。 |
| gitleaks detect | 通过 | `--no-git --redact` -> `scan completed in 868ms` -> `gitleaks: no leaks found in tracked working tree.`。 |
| 扫描范围 | 通过 | `tracked=1303 safe_scanned=1300 excluded=3`（排除恰为三个 `.env.example` 模板）；tracked safe paths only、no full-history scan、未扫描禁止目录。 |
| custom backstop | 通过 | `Custom regex backstop: no non-allowlisted matches over tracked safe tree.`。 |
| allowlist 精确性 | 通过 | HEAD ci.yml：`useDefault = true`；gate-c allowlist 单文件（1 条）；4 Binance fake-key / PEM 协议常量 path allowlist（gitleaks）+ backstop `allow_pem` 同 4 文件；未 broad allowlist、未关 default ruleset。 |
| fail closed | 通过 | `--exit-code 1`；0 finding 时 `no leaks found` 退出 0，有 finding 时 sanitized 输出后 `exit 1`。 |
| 权限 / 边界 | 通过 | `GITHUB_TOKEN Permissions: Contents: read, Metadata: read`（无 write / id-token）；无 `continue-on-error` / repository secret / `gitleaks-action` / `GITLEAKS_LICENSE`；`token: ***` mask；`fetch-depth: 1`。 |
| secret value 泄露 | 无 | 日志未输出 secret value / matched line / Secret / Match / commit / author（0 finding，sanitized 失败分支未触发；相关字样仅 runner 回显的 step 脚本本体）。 |
| credential / LIVE 边界 | 通过 | 无真实 API key / secret / passphrase / token / private key / credential material；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter；未调用真实交易所。 |
| 本地 diff 边界 | 通过 | `git diff -- .github/workflows/ci.yml` / `backend` / `frontend` / `research` / `scripts` / `deploy` / `backend/**/db/migration` 均空；仅允许的 5 个 `docs/current` 文件变更。 |

复核命令：

```powershell
git status --short; git diff --check; git diff --stat
git show --stat --oneline --name-only HEAD
git diff 31540de8 HEAD -- .github/workflows/ci.yml
git diff -- .github/workflows/ci.yml; git diff -- backend; git diff -- frontend; git diff -- research; git diff -- scripts; git diff -- deploy; git diff -- backend/**/db/migration
gh run view 27674393780 --json jobs
gh run view 27674393780 --log --job 81846054679
```

必录 P3（非阻断，留作后续 hardening）：forward-slash `paths` allowlist 在 Windows 反斜杠本地不匹配（只影响本地复现，不影响 Linux CI）；gitleaks release binary 无 SHA256 checksum pinning（仅版本 + `gitleaks version` 校验）；gitleaks 配置 inline 写入 `RUNNER_TEMP`，无 tracked single-source。

Review decision: PASS / FROZEN / ACCEPTED。P0/P1/P2 = 0。Batch 4B minimal secret scan 成为当前 `dev` security guard secret-scan baseline，frozen baseline = commit `31540de8` + first-run / second-run / freeze docs。下一步只能是 Batch 4C planning、Batch 4F later plan、Batch 5 planning，或暂停 CI 线。不得把 Batch 4C / 4F / Batch 5 写成 started。

## NQ-CI-SECURITY-GUARD-BATCH-4B-SECOND-RUN-REVIEW（2026-06-17）

本轮是 GateK CI Batch 4B second-run review：评审 first-run fix（commit `31540de8`）后的 GitHub Actions 第二次运行。结论 **PASS / ACCEPTED FOR FIRST GREEN RUN AFTER FIX**：second run `27674393780` completed / success，7/7 jobs green。只评审 + 改 docs，未改 workflow / 业务代码。Batch 4B 推进为 SECOND RUN GREEN / FIRST GREEN CONFIRMED AFTER FIX（**未 FROZEN**，freeze 是 Batch 4E）。

| 评审项 | 结果 | 证据 |
| --- | --- | --- |
| GitHub Actions second run | **通过** | run `27674393780`，commit `31540de8`，push / dev，completed / **success**。 |
| Diff check | 通过 | success。 |
| No-outbound guard | 通过 | success（Batch 3 baseline 未回归）。 |
| Backend Maven test | 通过 | success。 |
| PostgreSQL / Flyway smoke | 通过 | success。 |
| Frontend build | 通过 | success。 |
| Research quality gate | 通过 | success。 |
| **Secret scan** | **通过** | job `81846054679`，7 steps 全 success（install / build list / gitleaks detect / custom backstop）。 |
| gitleaks 版本 | 通过 | `Installed gitleaks version: 8.18.4`（pinned CLI，非 `gitleaks-action`，无 `GITLEAKS_LICENSE`）。 |
| gitleaks detect | 通过 | `--no-git --redact` -> `scan completed in 868ms` -> `gitleaks: no leaks found in tracked working tree.`。 |
| 扫描范围 | 通过 | `tracked=1303 safe_scanned=1300 excluded=3`（排除恰为三个 `.env.example` 模板）；tracked safe paths only、no full-history scan。 |
| custom regex backstop | 通过 | step #6 实际执行（gitleaks step 通过后）-> `Custom regex backstop: no non-allowlisted matches over tracked safe tree.`。 |
| allowlist 精确性 | 通过 | gate-c allowlist 为单文件 `.*docs/gates/gate-c/WORK\.md$`（HEAD ci.yml 仅 1 条 gate 路径）；4 Binance fake key / PEM 协议常量 path allowlist 不变；未 broad allowlist、未关 default ruleset。 |
| 日志无 secret 泄露 | 通过 | 0 finding，未进入 sanitized 失败分支；日志未输出 secret value / matched line / Secret / Match / commit / author。日志中 `Sanitized finding metadata` / `RuleID=` / `BEGIN PRIVATE KEY` 仅为 runner 回显的 step 脚本本体，非执行输出、非真实凭证。 |
| 权限 / 边界 | 通过 | `GITHUB_TOKEN Permissions: Contents: read, Metadata: read`（无 write / id-token）；`token: ***` mask；`fetch-depth: 1`（shallow）；无 `continue-on-error`；无 repository secret 注入。 |
| 既有 job 未回归 | 通过 | 本轮只改 secret-scan job（first-run fix）；6 个既有 job 全 green。 |
| 安全边界 | 通过 | 未读取 / 输出真实 credential material；未扫描禁止目录；未调用真实交易所；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter。 |

复核命令：

```powershell
git status --short; git diff --check; git show --stat --oneline --name-only HEAD
git diff -- backend; git diff -- frontend; git diff -- research; git diff -- scripts; git diff -- deploy; git diff -- backend/**/db/migration
gh run view 27674393780 --json jobs
gh run view 27674393780 --log --job 81846054679   # secret-scan (+ 复核 backend / postgres-flyway / no-outbound-guard 均 success)
```

未验证项：无（second run 真实在 GitHub runner 执行并全绿）。Batch 4E freeze review 时可再复核 immutable run 证据。

Review decision: PASS / ACCEPTED FOR FIRST GREEN RUN AFTER FIX。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4E`（freeze review）、`NQ-CI-SECURITY-GUARD-BATCH-4C`（artifact / log redaction planning），或暂停 CI 线。不得把 Batch 4B 直接写成 FROZEN / ACCEPTED；不得把 Batch 4C / 4F / Batch 5 写成 started。

## NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-FIX（2026-06-17）

本轮是 GateK CI Batch 4B first-run fix：最小修复 secret-scan job 首跑失败。先让 gitleaks finding 可见（不泄露 secret value），再做最小精确处置。结论 **FIRST-RUN-FIX APPLIED / PENDING SECOND CI RUN**。只改 `.github/workflows/ci.yml` 的 secret-scan job + 允许的 5 个 docs；未进入 Batch 4C / 4F / Batch 5，未改业务代码。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `git branch --show-current` = `dev`；编辑前工作区仅 4B impl 提交后状态。 |
| 失败定位（本地复现） | 已确认 | 本地 MINGW64 下载 pinned gitleaks `8.18.4` Windows CLI，复刻 CI 扫描（同排除清单 + staging + `--redact`），从 redacted JSON 报告只取 RuleID / File / Line（不读 Secret / Match）。 |
| 唯一 CI finding | 已确认 | `docs/gates/gate-c/WORK.md`，RuleID `generic-api-key`，约 line 325，是非敏感 WebSocket client request UUID（`client.request.id`）；该 frozen 卷宗真实凭证已 `apiKey=<masked>`（line 327）。**false positive，非真实 credential（P0=0）**。 |
| 本地多出的 4 finding | 已解释 | 本地额外 4 个 `private-key`（Binance fake 测试私钥 / PEM 协议常量）是 Windows 反斜杠路径致 forward-slash `paths` allowlist 本地不匹配的假象；CI（Linux 正斜杠）下已被现有 allowlist 抑制——故 CI 仅 `leaks found: 1`。 |
| 可见性修复 | 已实现 | gitleaks step 失败分支从 redacted JSON 报告输出 sanitized metadata：仅 RuleID / File（去 staging 前缀）/ StartLine-EndLine / Fingerprint；**不输出 Secret / Match / 匹配行 / commit / author**；保持 `--redact`、fail closed（`exit 1`）、tracked safe paths only、no full-history scan、不上传报告。 |
| 精确 allowlist | 已实现 | gitleaks inline 配置 `paths` 增加单文件 `.*docs/gates/gate-c/WORK\.md$`（带注释说明 FP）；未关 default ruleset、未 broad allowlist、未删测试样例、未改 frozen 卷宗本身、未放宽核心规则。 |
| 本地复跑验证 | **通过** | 用 separator-tolerant（`.`）等价 config 复跑 `gitleaks detect --no-git --redact`：`no leaks found` / rc=0 / 0 findings（4 Binance + gate-c 全部精确 allowlist 抑制）。 |
| 提交版 config 校验 | 通过 | forward-slash 提交版 config 单独 load：parses without panic（`no leaks found` on empty dir，rc=0）。其 Linux 有效性由 first run `27662197509`（forward-slash Binance 已抑制、仅剩 gate-c）佐证。 |
| custom backstop | 通过 | 本地 file-driven 复刻仍 0 命中（gate-c UUID 不在 backstop 凭证关键字范围）。 |
| YAML 语法 | 通过 | IntelliJ `get_file_problems`（errorsOnly）对 `ci.yml` 返回 0 errors；heredoc 终止符缩进正确。 |
| 边界 | 通过 | secret-scan job 仍 `contents: read`、无 repository secret / `gitleaks-action` / `GITLEAKS_LICENSE` / `id-token` / write / `continue-on-error`；未读取 / 输出真实 credential material；未扫描禁止目录；未调用真实交易所；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter。 |

本地验证命令（要点）：

```powershell
git status --short; git diff --check; git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- backend; git diff -- frontend; git diff -- research; git diff -- scripts; git diff -- deploy; git diff -- backend/**/db/migration
# 本地 pinned gitleaks 8.18.4 (Windows CLI) 复刻扫描 -> 定位 + 验证 0 findings（--redact，仅取 RuleID/File/Line）
```

未验证项：GitHub Actions 第二次运行 secret-scan job green（本地无法直接证明 Linux forward-slash 抑制；由 first-run 证据 + 本地等价 config 0 findings 间接佐证，需 second CI run 确认）。

Review decision: FIRST-RUN-FIX APPLIED / PENDING SECOND CI RUN。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4B-SECOND-RUN-REVIEW`、second-run fix（若仍失败），或暂停 CI 线。不得把 Batch 4B 写成 FIRST GREEN / FROZEN；不得混入 Batch 4C / 4F / Batch 5。

## NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-REVIEW（2026-06-17）

本轮是 GateK CI Batch 4B first-run review：只评审 secret-scan job 首次 GitHub Actions run，不进入 Batch 4C / 4F / Batch 5，不改业务代码、不改 workflow。结论 **FAIL / FIRST-RUN-FIX REQUIRED**：first run `27662197509` 6/7 jobs green，仅 `Secret scan` job 失败于 gitleaks `leaks found: 1`（default-ruleset false positive）。

| 评审项 | 结果 | 证据 |
| --- | --- | --- |
| GitHub Actions run | **失败** | run `27662197509`，commit `6db97535`，event push / branch dev，completed / **failure**。 |
| Diff check | 通过 | success。 |
| No-outbound guard | 通过 | success（Batch 3 baseline 未回归）。 |
| Backend Maven test | 通过 | success。 |
| PostgreSQL / Flyway smoke | 通过 | success。 |
| Frontend build | 通过 | success。 |
| Research quality gate | 通过 | success。 |
| **Secret scan** | **失败** | 唯一失败 job；失败 step = `Run pinned gitleaks secret scan (tracked working tree, no history)`。 |
| gitleaks 安装 / 版本 | 通过 | install step success；`GITLEAKS_VERSION 8.18.4` 版本校验通过；非 install / 版本错误。 |
| gitleaks detect 执行 | 已执行 | 日志 `scan completed in 911ms` 后 `WRN leaks found: 1`；脚本按设计 `rc != 0 -> exit 1` fail closed。 |
| 失败类别 | gitleaks FP | gitleaks default 规则比 custom backstop 窄正则更宽，命中 1 处未覆盖内容；非 binary install / tracked-list staging / YAML / heredoc / 脚本错误。custom backstop step 因 gitleaks step 先失败被 skip。 |
| 诊断缺口 | 已确认 | gitleaks step 未带 `-v` / `--verbose`，只打印 `leaks found: N` 摘要，未输出 RuleID / File / Line；JSON 报告写 `RUNNER_TEMP` 未上传（Batch 4C 未开始）；当前无法从 CI 日志定位 FP 具体 rule / file。 |
| secret value 泄露 | 无 | `--redact` 生效，日志仅 `leaks found: 1`，未输出任何 secret value。 |
| job 边界 | 合规 | `permissions: contents: read`；无 repository secret；无 `gitleaks-action` / `GITLEAKS_LICENSE` / `id-token` / write / `continue-on-error`；无 full-history scan（已对 commit `6db97535` 的 `ci.yml` 复核）。 |
| 安全边界 | 通过 | 未读取 / 输出真实 credential material；未扫描禁止目录；未调用真实交易所；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter。 |
| 本地诊断（best-effort） | 部分 | 本地 Windows 无 gitleaks（`python` 为 Store stub），无法精确复现 default-ruleset 的 entropy 判定；`fx-forbidden-fields.json` / `fx-feedback-invalid.json` 均用 `FAKE-PLACEHOLDER`（已 allowlist），非 culprit；具体 FP 待 FIX 用 `-v` 暴露。 |

复核命令：

```powershell
git status --short
git diff --check
git show --stat --oneline HEAD
git diff -- backend; git diff -- frontend; git diff -- research; git diff -- scripts; git diff -- deploy; git diff -- backend/**/db/migration
gh run view 27662197509 --json jobs
gh run view 27662197509 --log --job <secret-scan-job-id>   # secret-scan / backend / postgres-flyway / no-outbound-guard
```

未验证项：FP 的具体 RuleID / File（需 FIX 加 `-v` 暴露后确认）；secret-scan job 在加 `-v` / 精确 allowlist 后的 green run。

Review decision: FAIL / FIRST-RUN-FIX REQUIRED。下一步只能是 `NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-FIX`（先让 finding 可见，再 path + rule + fingerprint 精确 allowlist 或收敛 ruleset，禁止放宽核心规则 / 删测试样例 / broad allowlist），修复后重跑 CI 与 second-pass review。不得把 Batch 4B 写成 FIRST GREEN / FROZEN；不得混入 Batch 4C / 4F / Batch 5。

## NQ-CI-SECURITY-GUARD-BATCH-4B-SECRET-SCAN-IMPL（2026-06-17）

本轮是 GateK CI Batch 4B 最小 secret scan implementation：在 `.github/workflows/ci.yml` 新增 `secret-scan` job（pinned gitleaks CLI binary + custom regex backstop），只扫当前 tracked working tree，不读本地真实 `.env` / secrets / logs / dumps / backups / target / node_modules / dist / build / `.git`，不注入 repository secret，不用 `gitleaks-action`，不依赖 `GITLEAKS_LICENSE`。状态 IMPLEMENTED / PENDING FIRST CI RUN；Batch 4C / 4F 与 Batch 5 仍未开始。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `git branch --show-current` = `dev`；编辑前 `git status --short` 为空。 |
| Workflow 新增 job | 已实现 | `secret-scan` job：`permissions: contents: read`、无 repository secret、无 `continue-on-error`、fail closed。 |
| gitleaks 安装方式 | 已实现 | pinned `8.18.4` CLI binary，`curl`（无 token）下载 GitHub release，安装后 `gitleaks version` 必须等于 `8.18.4`；不使用 `gitleaks-action`、不需 `GITLEAKS_LICENSE`。 |
| 扫描范围 | 已实现 | `git ls-files` -> 排除 `.env*` / secrets / credentials / `*.pem` / `*.key` / `*.p12` / `*.jks` / `*.keystore` / target / node_modules / dist / build / coverage / logs / dumps / backups / `.git`；`gitleaks detect --no-git --redact`，禁止 full-history scan。 |
| 排除核对 | 通过 | 本地 `git ls-files` 共 1303 tracked，排除后 1300 safe；被排除的恰为三个 `.env.example` 模板（`.env.example` / `frontend/.env.example` / `deploy/.env.freeze.example`）。 |
| gitleaks allowlist | 已实现 | inline 配置 `useDefault = true` + 精确 allowlist：4 个 Binance fake-key / PEM 协议常量文件 by path + `REPLACE_WITH_LOCAL` / `CHANGE_ME` / `FAKE-PLACEHOLDER` 占位 marker by value；核心规则未放宽。 |
| custom regex backstop | 已实现 | 覆盖 `sk-ant-` / `sk-proj-` / `sk-` / `github_pat_` / `gh[pousr]_` / AKIA / ASIA / PEM private key（RSA / EC / OPENSSH / DSA / PGP）/ `xoxb-` / `xoxp-` / value-bearing mnemonic / value-bearing 凭证赋值；只输出 `file | pattern`，绝不输出命中值；value-bearing pattern 过滤 placeholder；`pem_private` 对 4 个 Binance 文件 path 精确 allowlist。 |
| backstop 本地复刻验证 | **通过** | 用与 workflow 完全一致的 file-driven 逻辑（patterns 经 quoted heredoc）跑当前 tracked safe tree：**0 非 allowlisted 命中**；新增 `secret-scan` job 与 `NQ_CI_SECURITY_GUARD_PLAN.md` 均未自命中（plan 内 PEM 字面量已软化为 `BEGIN PRIVATE KEY`）。 |
| 误报治理核对 | 通过 | 命中的 4 个 Binance fake PEM / 协议常量文件全部 path 精确 allowlist；`fx-forbidden-fields.json`（字段名 + `FAKE-PLACEHOLDER`）经 value-bearing mnemonic 细化后不再误报，无需 allowlist。 |
| gitleaks CLI 本地执行 | **未运行（已披露）** | 本地 Windows 开发环境 `python` 为 Microsoft Store stub（exit 49）、无预装 gitleaks；未在本地跑 gitleaks。gitleaks layer 的完整 FP 面留待 GitHub Actions first run（Batch 4D）确认。 |
| YAML 语法 | 通过 | IntelliJ inspection（`get_file_problems` errorsOnly）对 `.github/workflows/ci.yml` 返回 0 errors；heredoc 终止符 `TOML` / `PATTERNS` 与 run 内容同为 10 空格缩进，YAML block-scalar dedent 后落在第 0 列。 |
| 边界 | 通过 | 未改 Java / TS / Python 代码、测试、migration、backend production、frontend、research、scripts、deploy；未新增 tracked 文件（gitleaks 配置 / backstop pattern 均 inline 到 `RUNNER_TEMP`）；未注入 repository secret；未用 write / id-token；未开启 LIVE / AI / DH；未实现 RealClient / real provider / real probe adapter；未调用真实交易所。 |

本地验证命令：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
git ls-files
# custom regex backstop dry run（file-driven，复刻 workflow 逻辑）-> 0 非 allowlisted 命中
```

未验证项：gitleaks CLI 实际扫描结果（本地无法安装，留待 first CI run）；`secret-scan` job 在 GitHub runner 的安装 / 下载 / staging / scan 端到端执行；已知 first-run 风险候选——docs 内 commit SHA / artifact `sha256:` digest、CI-only `123456` PostgreSQL 占位、`ci.yml` 自身的 pattern 字符串若被 gitleaks default 规则误报（custom backstop 已确认不自命中）。

Review decision: IMPLEMENTED / PENDING FIRST CI RUN。下一步：首次 run 成功则 `NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-REVIEW`，失败则 `NQ-CI-SECURITY-GUARD-BATCH-4B-FIRST-RUN-FIX`。不得写成 FROZEN / ACCEPTED / fully implemented；Batch 4C / 4F / Batch 5 不得写成 started。

## NQ-CI-SECURITY-GUARD-BATCH-4A-PLAN-REVIEW（2026-06-17）

本轮是 GateK CI Batch 4A security guard / secret scan plan review：只评审 `NQ_CI_SECURITY_GUARD_PLAN.md` 是否可作为 Batch 4B / 4C implementation baseline，并按 25 项 checklist 复核 secret scan 范围、credential pattern、artifact / log redaction、GitHub Actions permissions、dependency audit 与 Batch 5 边界。结论 `PASS / ACCEPTED AS IMPLEMENTATION BASELINE`，P0/P1 = 0。本轮只改 docs，不改 workflow / 代码 / 测试 / migration / frontend / research / scripts / deploy。Batch 4 仍 PLAN ONLY / NOT IMPLEMENTED；Batch 5 仍 PENDING。

| 评审项 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `git branch --show-current` = `dev`；编辑前 `git status --short` 为空。 |
| Secret scan scope（1-3） | 通过 | plan 限定 tracked safe paths；显式排除 `.git` / `target` / `node_modules` / `dist` / `build` / `coverage` / `logs` / `dumps` / `backups`；不读本地真实 `.env` / secret。 |
| Scanner 选择（4-6） | 通过 | pinned gitleaks（评审收紧为 pinned 版本 / CLI binary）+ custom regex backstop 复用现有 redaction 正则；禁止 trufflehog verify / 外部验证请求。 |
| 误报治理（7-9） | 通过 | path + rule + fingerprint 精确 allowlist；禁止放宽核心规则；finding 只 file/path/rule，不输出 secret value。 |
| Credential pattern（10-12） | 通过 | 覆盖 API key / secret / passphrase / token / private key / PEM / JWT / GitHub token / AWS / OpenAI / Anthropic / exchange credential / Slack / mnemonic / cookie / keystore；`encrypted_payload` / `decrypted_payload` 区分字段名引用 vs 真实值；占位例外限定 `REPLACE_WITH_LOCAL` / `CHANGE_ME` / 空赋值 / fake 测试值 / CI-only DB placeholder。 |
| Artifact / log（13-15） | 通过 | upload 前 redaction 通用规则；logs 禁 env dump / raw req-resp / signature / connection string / secret；backend 报告 + frontend / research 产物若上传须 redaction。 |
| Permissions（16-19） | 通过 | `contents: read` 最小化；禁止 write / id-token（除非单独 review）；禁止 repository secret 注入 test job；禁止 `continue-on-error` 掩盖 security failure。 |
| Dependency audit（20-21） | 通过 | Batch 4 baseline 不含 blocking dependency audit；归可选 Batch 4F，非阻断起步，不混入 secret scan baseline。 |
| Batch 边界（22-25） | 通过 | 不重复 Batch 3 no-outbound；不做 frontend E2E hardening；Batch 5 仍 PENDING；允许进入 Batch 4B implementation。 |
| Tracked secret sweep | 通过 | 高风险字面量（含 `sk-ant-` / `github_pat_`）仅命中 Binance fake 测试私钥与 `PRIVATE_KEY_BEGIN` 协议常量；`git ls-files` secret-like 文件仅三个 allowlisted `.env.example` 模板；无真实 credential。 |
| 评审新增 P3 | 已记录 | 2 项 gitleaks 实现提示（扫描目标限定 tracked tree、优先 CLI binary 规避 `GITLEAKS_LICENSE` repo-secret），非阻断；已写入 plan findings 与实现段落。 |
| 安全边界 | 通过 | 未读取 / 打印 / 复制真实 credential material；未把禁止目录作为数据源扫描；未开启 LIVE / AI / DH runtime；未实现 RealClient / real provider / real probe adapter；未调用真实交易所。 |
| 本地 build/test | 未运行 | 本轮 review-only / docs-only，禁止改 workflow / 代码 / 测试 / migration；未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff。 |

评审验证命令：

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
git ls-files
rg "apiKey|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|AKIA|sk-|sk-ant-|xox|ghp_|gho_|github_pat_|JWT|OPENAI_API_KEY|ANTHROPIC_API_KEY|BINANCE|OKX|LIVE|RealClient" .github backend frontend research docs/current
```

rg 仅用于 tracked safe paths；未扩展到 `.env` / secrets / logs / dumps / backups / `target` / `node_modules` / `dist` / `build` / `.git`。命中 fake test key / template placeholder / PEM constant 均按 allowlist 误报治理策略处理，不删测试样例。

Review decision: PASS / ACCEPTED AS IMPLEMENTATION BASELINE。P0/P1 = 0；P3 = 5（含评审新增 2 项），非阻断。下一步只能是 Batch 4B implementation（建议先落实 2 项 P3 实现提示）、Batch 4A plan fix，或暂停 CI 线。Batch 4 / Batch 5 不得写成 implemented / started。

## NQ-CI-SECURITY-GUARD-BATCH-4-PLAN（2026-06-17）

本轮是 GateK CI Batch 4 security guard / secret scan planning-only：只规划后续如何在 CI 中扫描 tracked source / config / workflow / docs 的密钥泄露、敏感文件误提交、artifact / log 泄露、GitHub Actions 过大权限和 dependency audit 边界。不修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 4 当前为 PLAN ONLY / NOT IMPLEMENTED；Batch 5 frontend E2E hardening 仍 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `git branch --show-current` = `dev`；编辑前 `git status --short` 为空。 |
| Workflow 只读检查 | 已执行 | `.github/workflows/ci.yml` 现有 6 jobs（`diff-check`、`backend`、`postgres-flyway`、`frontend`、`research`、`no-outbound-guard`）；顶层 `permissions: contents: read`；无 repository secret 注入；无专用 secret scan job；本轮未修改 workflow。 |
| 既有 redaction 先例 | 已确认 | `postgres-flyway` job 已含 schema artifact data-row + 高风险 credential pattern fail-closed 检查，作为 Batch 4 secret/artifact scan 先例。 |
| `.env.example` 模板 | 已确认 | `.env.example`、`frontend/.env.example`、`deploy/.env.freeze.example` 为 tracked 占位模板（`REPLACE_WITH_LOCAL_*` / `CHANGE_ME_*` / 空 API key），需 Batch 4B allowlist 防误报。 |
| `.gitignore` 边界 | 已确认 | 已 ignore `target` / `node_modules` / `dist` / `coverage` / `test-results` / `*.log` / `.env` / `*.pem` / `*.key` / `*.dump` / `*.backup` / `artifacts` / `backups` 等噪声与敏感目录。 |
| Tracked secret sweep | 通过 | `git ls-files` 无真实 `.env` / `*.key` / `*.pem` / keystore / dump；高风险字面量（`AKIA` / `sk-` / `ghp_` / `gho_` / `xox` / PEM）仅命中 Binance fake 测试私钥与 `PRIVATE_KEY_BEGIN` 协议常量；`encrypted_payload` / `decrypted_payload` / `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `RealClient` 命中均为 DH 契约字段名 / boundary "NOT IMPLEMENTED" 声明 / credential-governance 代码，无真实泄露。 |
| Plan file | 已新增 | `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`，状态固定 PLAN ONLY / NOT IMPLEMENTED，拆分 Batch 4A-4E（+ 可选 4F dependency audit）。 |
| 安全边界 | 通过 | 未读取 / 打印 / 复制真实 credential material；未把禁止目录作为数据源扫描；未开启 LIVE / AI / DH runtime；未实现 RealClient / real provider / real probe adapter；未调用真实交易所。 |
| 本地 build/test | 未运行 | 本轮 docs-only / planning-only，且禁止改 workflow / 代码 / 测试 / migration；未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff。 |

计划验证命令：

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
rg "apiKey|secret|passphrase|token|private key|BEGIN PRIVATE KEY|encrypted_payload|decrypted_payload|AKIA|sk-|xox|ghp_|gho_|JWT|OPENAI_API_KEY|ANTHROPIC_API_KEY|BINANCE|OKX|LIVE|RealClient" .github backend frontend research docs/current
```

rg 仅用于 tracked safe paths；未扩展到 `.env` / secrets / logs / dumps / backups / `target` / `node_modules` / `dist` / `build` / `.git`。

Review decision: PLAN READY FOR REVIEW。P0/P1 planning blockers = 0。下一步只能是 Batch 4A plan review、Batch 4 plan fix、Batch 4B secret scan minimal implementation，或暂停 CI 线。Batch 4 / Batch 5 不得写成 implemented / started。

## NQ-CI-NO-OUTBOUND-GUARD-BATCH-3E-FREEZE-REVIEW（2026-06-17）

本轮是 GateK CI Batch 3E no-outbound guard freeze review。基于 immutable GitHub Actions run `27634370657`（commit `88d976a1`，重新拉取 job logs 复核），确认 Batch 3 no-outbound guard baseline 可冻结。结论 `PASS / FROZEN / ACCEPTED`，P0/P1/P2 blockers = 0。Batch 4 / Batch 5 仍 PENDING；本轮只改 docs，不改 workflow / 代码 / 测试 / migration。

| 复核项 | 结果 | 证据 |
| --- | --- | --- |
| Run 27634370657 | green | conclusion `completed / success`；6 jobs 全 `success`。 |
| Diff check job | green | success。 |
| No-outbound guard job | green | success；三步（env-absence、denylist coverage、guard test）均 success。 |
| NoOutboundExchangeGuardTest | 3/0/0/0 | `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`；`CI=true` 下 env-absence 用例实际执行。 |
| Denylist coverage | 24/24 | guard job denylist coverage step 实际枚举全部 24 个 host variants（OKX / Binance / Binance testnet / Binance WS / Bybit / Bitget / Gate / Coinbase / Kraken / Crypto.com / Hyperliquid）。 |
| Backend Maven test job | green | `mvn -f backend/pom.xml test` `BUILD SUCCESS`，`nq-app` 56/0/0/1。 |
| PostgreSQL / Flyway job | green | `NqAppContextPostgresSmokeTest` 1/0/0/0（真实 CI PostgreSQL，guard 安装、`deniedSelections()==0`、WS mock 无 interaction、`permissionProbePort` 为 `NoRealExchangeCredentialPermissionProbePort`）；`JdbcRepositoryPostgresSmokeTest` 1/0/0/0。 |
| Schema artifacts | 通过 | 上传 7 files（Artifact ID `7674040595`，74673 bytes），redaction check step green。 |
| Frontend build job | green | success。 |
| Research quality gate job | green | success。 |
| No real exchange connect | 确认 | 所有 job 日志无 `UnknownHostException` / `ConnectException` / `No route to host` / 真实交易所 connect；唯一 host 字符串为 benign `apiKey=missing` fingerprint 与 `okx_recovery_startup_skipped`。 |
| Credential / LIVE boundary | 确认 | 无真实 API key / secret / passphrase / token / private key / credential material；`gho_` token mask 为 `***`；LIVE / AI / DH runtime / RealClient / real provider / real probe adapter 均未开启或未实现。 |

frozen baseline = commit `88d976a1`（workflow + test-scope guard）+ first-run / freeze docs。状态推进 `FIRST GREEN RUN CONFIRMED` -> `FROZEN / ACCEPTED`。

必录 P3（非阻断，留作 Batch 3 parity/hygiene follow-up）：denylist 三处同源（ci.yml env / ci.yml `required_hosts` / Java `ExchangeNoOutboundGuard`）无自动 parity check；`no-outbound-guard` required branch protection 取决于仓库设置；`ProxySelector` 不覆盖未来 raw `Socket` / `SocketChannel` transport；GitHub-provided actions Node.js 20 deprecation 为 advisory。

下一步：`NQ-CI-SECURITY-GUARD-BATCH-4-PLAN`、Batch 3 parity/hygiene follow-up，或暂停 CI 线。

## NQ-CI-NO-OUTBOUND-GUARD-BATCH-3-FIRST-RUN-REVIEW（2026-06-17）

本轮是 GateK CI Batch 3 no-outbound guard first-run review（对应 Batch 3D）。GitHub Actions run `27634370657`（event `push`，branch `dev`，commit `88d976a1`）`completed / success`，6 jobs 全 green。结论 `PASS / ACCEPTED FOR FIRST GREEN RUN`，状态推进为 `FIRST GREEN RUN CONFIRMED`；freeze 仍是 Batch 3E，不得写成 `FROZEN / ACCEPTED`。Batch 4 / Batch 5 仍 PENDING。

| 检查项 | 结果 | CI 证据 |
| --- | --- | --- |
| GitHub Actions run | green | run `27634370657` completed / success；`gh run watch` exit 0。 |
| Diff check job | green | success（6s）。 |
| No-outbound guard job | green | success（21s）；`Verify no exchange credential env`、`Verify exchange denylist coverage`、`Run no-outbound guard tests` 三步均 success。 |
| Backend Maven test job | green | success（1m22s）；`mvn -f backend/pom.xml test` `BUILD SUCCESS`，`nq-app` 56 tests / 0 failures / 0 errors / 1 skipped（CI `CI=true` 使 env-absence 用例执行，故较本地少 1 skip）。 |
| PostgreSQL / Flyway job | green | success（1m24s）；artifact `nq-postgres-flyway-schema-artifacts` 上传通过 redaction check。 |
| Frontend build job | green | success（22s）。 |
| Research quality gate job | green | success（16s）。 |
| `NoOutboundExchangeGuardTest` | 3/0/0/0 | guard job 内 `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`；env-absence 用例在 CI 实际执行。 |
| `NqAppContextPostgresSmokeTest` | 1/0/0/0 | `postgres-flyway` job 内 tests=1 / skipped=0 / failures=0 / errors=0（6.288s）；guard 已安装，`deniedSelections()==0`、WS mock 无 interaction、`permissionProbePort instanceof NoRealExchangeCredentialPermissionProbePort` 全部成立。 |
| Denylist coverage | 完整 | 覆盖 OKX / Binance / Binance testnet / Binance WS / Bybit / Bitget / Gate / Coinbase / Kraken / Crypto.com / Hyperliquid。 |
| No real exchange connect | 确认 | 所有 job 日志无 `UnknownHostException` / `ConnectException` / `No route to host` / 真实交易所 connect；唯一交易所 host 字符串为 benign `apiKey=missing` fingerprint 与 `okx_recovery_startup_skipped`。 |
| Credential / LIVE boundary | 确认 | 无真实 API key / secret / passphrase / token / private key / credential material；`gho_` token 在 checkout step mask 为 `***`；LIVE / AI / DH runtime / RealClient / real provider / real probe adapter 均未开启或未实现。 |

P3 hygiene（非阻断）：GitHub-provided actions（`checkout@v4`、`setup-java@v4`、`setup-node@v4`、`setup-python@v5`、`upload-artifact@v4`）触发 Node.js 20 deprecation 警告，仅 advisory；denylist 在 ci.yml env / ci.yml bash array / `ExchangeNoOutboundGuard` 三处同源，存在未来漂移风险。两者留作 Batch 3 parity/hygiene follow-up，不在本轮修改。

下一步：`NQ-CI-NO-OUTBOUND-GUARD-BATCH-3E-FREEZE-REVIEW`、Batch 3 parity/hygiene follow-up，或暂停 CI 线。

## NQ-CI-NO-OUTBOUND-GUARD-BATCH-3B-IMPL（2026-06-17）

本轮是 GateK CI Batch 3B no-outbound guard 最小实现：新增 merge-blocking `No-outbound guard` job，并在 `nq-app` test scope 增加 deterministic exchange denylist guard。状态为 `IMPLEMENTED / PENDING FIRST CI RUN`；Batch 4 security guard / secret scan 和 Batch 5 frontend E2E hardening 仍 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| CI guard job | 已实现 | `.github/workflows/ci.yml` 新增 `no-outbound-guard` job；不注入 repository secrets；不访问真实交易所；显式检查 forbidden exchange credential / LIVE / real provider env names 为空。 |
| Denylist coverage | 已实现 | workflow 与 `ExchangeNoOutboundGuard` 显式覆盖 OKX / Binance / Binance testnet / Binance WS / Bybit / Bitget / Gate / Coinbase / Kraken / Crypto.com / Hyperliquid host set。 |
| Runtime guard | 已实现 | `ExchangeNoOutboundGuard` 通过 test-scope `ProxySelector` 在 DNS / HTTP / WS connect 前 fail closed；`NoOutboundExchangeGuardTest` 用受控 denylisted-host probe 证明 fail closed。 |
| App context smoke guard | 已实现 | `NqAppContextPostgresSmokeTest` 在 context 初始化前安装同一 guard；继续禁用 scheduling / recovery / catalog sync / WS；断言 OKX / Binance WS mock 无 interaction。 |
| Permission probe boundary | 已实现 | app context smoke 断言默认 `ExchangeCredentialPermissionProbePort` 为 `NoRealExchangeCredentialPermissionProbePort`；既有 service tests 继续覆盖 LIVE credential probe rejected。 |
| Target guard test | 通过 | `mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NoOutboundExchangeGuardTest '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.no-outbound.guard.required=true'`：3 tests / 0 failures / 0 errors / 0 skipped，`BUILD SUCCESS`。 |
| App smoke selection | 通过（本地 skipped） | `mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'`：1 test selected / skipped=1（本地无 CI DB required properties），`BUILD SUCCESS`；真实 startup proof 等待 GitHub Actions first run。 |
| Full backend Maven | 通过 | `mvn -f backend/pom.xml test`：23 个 reactor module 全部 `SUCCESS`，最终 `BUILD SUCCESS`；`nq-app` 56 tests / 0 failures / 0 errors / 2 skipped。 |

Pending first CI run: GitHub Actions first run 尚未执行；不得写成 FIRST GREEN / FROZEN / ACCEPTED。下一步只能是 Batch 3 first-run review、Batch 3 first-run fix，或暂停 CI 线。

## NQ-CI-NO-OUTBOUND-GUARD-BATCH-3-PLAN（2026-06-16）

本轮是 GateK CI Batch 3 no-outbound guard planning-only：只规划后续如何证明 CI / Maven test / app context smoke 默认不会访问真实交易所、不会读取真实凭证、不会触发 LIVE / real provider / RealClient 路径。不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 3 当前为 PLAN ONLY / NOT IMPLEMENTED；Batch 4 security guard / secret scan 和 Batch 5 frontend E2E hardening 仍 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| Workflow 只读检查 | 已执行 | `.github/workflows/ci.yml` 当前有 `diff-check`、`backend`、`postgres-flyway`、`frontend`、`research`；无 dedicated no-outbound job，本轮未修改 workflow。 |
| Backend / adapter inventory | 已执行 | 复核 `backend/pom.xml`、adapter-okx、adapter-binance、adapter-api、HTTP / WS client、scheduler / recovery / catalog sync、permission probe service / port / tests。 |
| Profile inventory | 已执行 | 复核 `application.yml` / `application-test.yml` / `application-local.yml`；确认默认 local profile 与真实 exchange endpoint 默认值不能作为 CI no-outbound proof。 |
| Permission probe boundary | 已确认 | `AccountModuleConfiguration` 默认绑定 `NoRealExchangeCredentialPermissionProbePort`；Service 保留 LIVE / withdraw / paper safety gate；真实 OKX/Binance probe adapter 仍 NOT IMPLEMENTED。 |
| Plan file | 已新增 | `docs/current/NQ_CI_NO_OUTBOUND_GUARD_PLAN.md`，状态固定为 PLAN ONLY / NOT IMPLEMENTED。 |
| 本地 build/test | 未运行 | 本轮只改 docs/current 文档，且明确禁止实现 guard、修改 workflow、代码、测试或 migration；未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff。 |

计划验证命令：

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

Review decision: PLAN READY FOR REVIEW。P0/P1 planning blockers = 0。下一步只能是 Batch 3A plan review、Batch 3A plan fix、Batch 3B implementation，或暂停 CI 线。

## NQ-CI-POSTGRES-FLYWAY-2E-FREEZE-REVIEW（2026-06-16）

本轮是 GateK CI Batch 2E freeze review：只冻结 Batch 2E seed watcher cleanup baseline，并同步允许的 `docs/current` 状态记录。不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 2E 当前为 FROZEN / ACCEPTED；Batch 3-5 仍 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 通过 | Run `27614046762`，workflow `NQ CI Baseline`，branch `dev`，commit `50c4c65e956b207bcfa47b4ed2027b452d3809fc`，completed / success。 |
| Diff check | 通过 | Job `81645397268` completed / success。 |
| Backend Maven test | 通过 | Job `81645397239` completed / success；steps `Prepare backend CI legacy account fixture` and `Run backend tests` both success。 |
| Frontend build | 通过 | Job `81645397229` completed / success。 |
| Research quality gate | 通过 | Job `81645397244` completed / success。 |
| PostgreSQL / Flyway smoke | 通过 | Job `81645397302` completed / success；empty DB Flyway smoke、schema artifacts、repository PostgreSQL smoke、`nq-app` context smoke all success。 |
| Explicit fixture | 通过 | Backend job fixture runs Flyway migrate / validate to V31 before backend tests, inserts only `ci-backend-test-account` into legacy `accounts` with `PAPER / ACTIVE`, and fail-closes on linked `exchange_accounts` rows or any `exchange_account_credentials` row。 |
| Seed watcher removal | 通过 | Backend job has no background seed watcher, no `public.accounts` polling, no `ci-local-account`, no `seed_pid`, and no watcher wait / exit-status merge。 |
| Research happy path | 通过 | `ResearchBacktestHappyPathLocalTest` ran with tests=1 / failures=0 / errors=0 / skipped=0。 |
| Backend reactor | 通过 | Backend Maven reactor 23/23 modules SUCCESS；`nq-app` SUCCESS；Maven `BUILD SUCCESS`。 |
| Batch 2A/2B/2C/2D baseline | 通过 | `postgres-flyway` job kept accepted 2A / 2B / 2C / 2D steps green；`NqAppContextPostgresSmokeTest` tests=1 / skipped=0 / failures=0 / errors=0。 |
| Security boundary | 通过 | No API key、secret、passphrase、token、private key、credential material、encrypted_payload、decrypted_payload was written by 2E。No LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。No OKX / Binance / Bybit / Gate / Coinbase / Kraken call introduced by 2E。 |
| Local diff boundary | 通过 | `git diff -- .github` / `backend` / `frontend` / `research` / `scripts` / `deploy` / `backend/**/db/migration` all empty after freeze review edits；only allowed `docs/current` files changed。 |
| Log access | 通过 | GitHub MCP provided run jobs and decoded backend / postgres-flyway job logs for run `27614046762`。 |

Review decision: PASS / FROZEN / ACCEPTED。P0=0，P1=0。Batch 2E seed watcher cleanup baseline 已冻结为当前 `dev` CI baseline；Batch 3 no-outbound guard、Batch 4 security guard / secret scan、Batch 5 frontend E2E hardening 仍 PENDING。下一步只能是 Batch 3 pre-planning、Batch 4 / Batch 5 later planning，或暂停 CI 线。

## NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-REVIEW-AFTER-FIX（2026-06-16）

本轮是 GateK CI Batch 2E first-run review after fix：只评审 first-run fix 后的 GitHub Actions run `27614046762`，并同步允许的 `docs/current` 状态记录。不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。该 review 当时将 Batch 2E 标记为 FIRST GREEN RUN CONFIRMED；后续已由 `NQ-CI-POSTGRES-FLYWAY-2E-FREEZE-REVIEW` 关闭为 FROZEN / ACCEPTED；Batch 3-5 仍 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 通过 | Run `27614046762`，workflow `NQ CI Baseline`，branch `dev`，commit `50c4c65e956b207bcfa47b4ed2027b452d3809fc`，completed / success。 |
| Diff check | 通过 | Job `81645397268` completed / success；changed-file whitespace gate passed。 |
| Backend Maven test | 通过 | Job `81645397239` completed / success；steps `Prepare backend CI legacy account fixture` and `Run backend tests` both success。 |
| Explicit fixture | 通过 | Backend job fixture runs Flyway migrate / validate to V31 before backend tests, inserts only `ci-backend-test-account` into legacy `accounts` with `PAPER / ACTIVE`, and fail-closes on matching `exchange_accounts` rows or any `exchange_account_credentials` row。 |
| Seed watcher removal | 通过 | Backend job has no background seed watcher, no `public.accounts` polling, no `ci-local-account`, no `seed_pid`, and no watcher wait / exit-status merge。 |
| Research happy path | 通过 | `ResearchBacktestHappyPathLocalTest` ran with tests=1 / failures=0 / errors=0 / skipped=0。 |
| Backend reactor | 通过 | Backend Maven reactor 23/23 modules SUCCESS；`nq-app` SUCCESS；Maven `BUILD SUCCESS`。 |
| Frontend build | 通过 | Job `81645397229` completed / success；`npm ci` + `npm run build` passed；only known Vite chunk-size warning and existing `npm audit` advisory summary appeared。 |
| Research quality gate | 通过 | Job `81645397244` completed / success；pytest `2 passed`，mypy `Success: no issues found in 8 source files`，ruff `All checks passed!`。 |
| PostgreSQL / Flyway smoke | 通过 | Job `81645397302` completed / success；empty DB Flyway smoke、schema artifacts、repository PostgreSQL smoke、`nq-app` context smoke all success。 |
| Batch 2A/2B/2C/2D baseline | 通过 | `postgres-flyway` job kept accepted 2A / 2B / 2C / 2D steps green；`NqAppContextPostgresSmokeTest` tests=1 / skipped=0 / failures=0 / errors=0。 |
| Security boundary | 通过 | No API key、secret、passphrase、token、private key、credential material、encrypted_payload、decrypted_payload。No LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。No OKX / Binance / Bybit / Gate / Coinbase / Kraken calls introduced by 2E。 |
| Local diff boundary | 通过 | `git diff -- backend` / `frontend` / `research` / `scripts` / `deploy` / `backend/**/db/migration` all empty after review edits；only allowed `docs/current` files changed。 |
| Log access note | 已披露 | GitHub MCP provided run jobs and decoded backend / postgres-flyway job logs. A later `gh run view --log` retry hit GitHub unauthenticated rate limiting, so detailed log review used GitHub MCP output plus workflow static inspection。 |

Review decision at that checkpoint: PASS / ACCEPTED FOR FIRST GREEN RUN。Batch 2E 当时为 FIRST GREEN RUN CONFIRMED，不能写成 FROZEN / ACCEPTED。该限制已由后续 freeze review 关闭；当前 Batch 2E 为 FROZEN / ACCEPTED。

## NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-FIX（2026-06-16）

本轮是 GateK CI Batch 2E first-run fix：先取得 GitHub Actions run `27610448572` 的 Backend Maven test 失败日志，再只在 `.github/workflows/ci.yml` backend job 增加同步 post-Flyway CI-only legacy `accounts` fixture。不修改 Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 2E 当前为 FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN；Batch 3-5 仍 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `Get-Location` = `F:\project\nexus-quant`；`git branch --show-current` = `dev`；初始 `git status --short` 为空。 |
| Failure log access | 通过 | GitHub MCP 读取 run `27610448572` / job `81633181802` decoded logs；`gh` logs endpoint 此前为 403，但本轮已取得 Maven / Surefire failure lines。 |
| 失败测试定位 | 已确认 | Maven module `nq-app`；class `ResearchBacktestHappyPathLocalTest`；method `shouldRunMinimalDbBackedResearchBacktestEvalHappyPath`；line `59`。 |
| SQL / stack trace | 已确认 | `SELECT account_id FROM accounts ORDER BY account_id LIMIT 1` 返回 0 行；`JdbcTemplate.queryForObject` 抛 `EmptyResultDataAccessException: Incorrect result size: expected 1, actual 0`。 |
| Surefire summary | 已确认 | `Tests run: 53, Failures: 0, Errors: 1, Skipped: 1`；Reactor 中仅 `nq-app` failure。 |
| Root cause | 已确认 | 删除 background watcher 后，GitHub fresh PostgreSQL service DB 缺少 legacy `accounts` fixture；这是 `ResearchBacktestHappyPathLocalTest` fixture ownership 问题，不是 `postgres-flyway` job 回退，不是 `exchange_accounts` backfill 或 credential rows 问题。 |
| Workflow fix | 已执行 | 新增 `Prepare backend CI legacy account fixture` step：先 Flyway migrate/validate 到 V31，再插入 `ci-backend-test-account` 到 legacy `accounts`，并校验没有创建 `exchange_accounts` 或 `exchange_account_credentials` rows。 |
| Seed watcher boundary | 通过 | 未恢复 background watcher；未恢复 `public.accounts` polling、`ci-local-account`、`seed_pid`、`wait` 或 watcher exit-status merge。 |
| Credential / exchange boundary | 通过 | Fixture 不写 `exchange_account_credentials`，不写 `apiKey` / secret / passphrase / token / private key / credential material；不创建真实 exchange account；不启用 LIVE / AI / DH runtime / RealClient / real provider；不调用真实交易所。 |
| Local validation | 通过 | `mvn -f backend/pom.xml test` BUILD SUCCESS；23/23 reactor modules SUCCESS；`nq-app` SUCCESS；Total time `01:28 min`。本地 run 使用 localhost PostgreSQL 17.7；`NqAppContextPostgresSmokeTest` 未设置 `nq.app.context.smoke.required=true`，按预期 skipped=1。 |
| Pending first CI run | 待确认 | 需要下一次 GitHub Actions run 确认 `Backend Maven test` 与 `PostgreSQL / Flyway smoke` 均 success 后，才能进入 2E first-run review；当前不得写 FIRST GREEN / FROZEN / ACCEPTED。 |

Review decision: FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-REVIEW`，或如果下一次 CI 仍失败则继续 scoped `NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-FIX`。

## NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-REVIEW（2026-06-16）

本轮是 GateK CI Batch 2E first-run review：只评审删除 backend CI seed watcher 后的 GitHub Actions run，不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 2E 当前为 FAIL / FIRST-RUN-FIX REQUIRED；Batch 3-5 仍 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `Get-Location` = `F:\project\nexus-quant`；`git branch --show-current` = `dev`；初始 `git status --short` 为空。 |
| GitHub Actions run | 失败 | Run `27610448572`，workflow `NQ CI Baseline`，branch `dev`，commit `d149952bbd39883847302996b0930437890b8121`，completed / failure。 |
| Diff check | 通过 | Job `81633181839` completed / success。 |
| Backend Maven test | 失败 | Job `81633181802` completed / failure；step `Run backend tests` failed with exit code 1。`gh run view --log-failed` 返回 HTTP 403，当前 reviewer 无法读取 Maven stack trace 或失败测试名。 |
| Frontend build | 通过 | Job `81633181721` completed / success。 |
| Research quality gate | 通过 | Job `81633181760` completed / success。 |
| PostgreSQL / Flyway smoke | 通过 | Job `81633181744` completed / success；empty DB Flyway smoke、schema artifacts、repository PostgreSQL smoke、`nq-app` context smoke 均 success。 |
| Seed watcher removal evidence | 部分通过 | `.github/workflows/ci.yml` 中 watcher 已删除；run metadata 显示 backend step 只剩 `Run backend tests`。由于日志 403，本轮无法从 backend log 直接搜索 `ci-local-account` / `public.accounts` / `seed_pid`。 |
| Credential / exchange boundary | 通过 | 未发现新增 seed users、legacy accounts、exchange accounts、credential rows 或 credential material；未接 LIVE / AI / DH runtime / RealClient / real provider；未调用真实交易所。 |

Review decision: FAIL / FIRST-RUN-FIX REQUIRED。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-FIX`；修复前必须先取得 backend Maven log，记录具体失败测试、SQL / stack trace 和根因。

## NQ-CI-POSTGRES-FLYWAY-2E-IMPL（2026-06-16）

本轮是 GateK CI Batch 2E implementation：只清理 `.github/workflows/ci.yml` backend job 中的 CI-only background seed watcher，并同步 `docs/current` 状态记录。不修改 Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 2E 当前为 IMPLEMENTED / PENDING FIRST CI RUN；Batch 3-5 仍 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `Get-Location` = `F:\project\nexus-quant`；`git branch --show-current` = `dev`；`git status --short` 为空。 |
| Workflow cleanup | 已执行 | 删除 `backend` job / `Run backend tests` step 中的 background seed watcher；该 step 现在直接执行 `mvn -f backend/pom.xml test`。 |
| Seed watcher removal | 已确认 | 删除 Docker polling、`public.accounts` 等待、`ci-local-account` insert、`seed_pid`、`wait` 和 watcher exit-status merge 逻辑。 |
| Fallback SQL | 未添加 | 删除 watcher 后本地 backend Maven test 通过，不需要迁移完成后的显式 CI-only fixture SQL。 |
| `mvn -f backend/pom.xml test` | **通过** | BUILD SUCCESS；23/23 reactor modules SUCCESS；Total time `02:22 min`。本地 run 使用 localhost PostgreSQL 17.7 跑 local-profile Spring tests；`NqAppContextPostgresSmokeTest` 因未设置 `nq.app.context.smoke.required=true` 按预期 skipped=1。 |
| Batch 2A-2D regression scope | 未运行 CI | 本轮未触发 GitHub Actions；`postgres-flyway` job 未改，仍需 first CI run review 确认 backend job 和 `postgres-flyway` job 都保持 green。 |
| Credential / exchange boundary | 通过 | 未创建 seed users、legacy accounts、exchange accounts、credential rows 或 credential material；未接 LIVE / AI / DH runtime / RealClient / real provider；未调用真实交易所。 |

Review decision: PASS / IMPLEMENTED / PENDING FIRST CI RUN。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2E-FIRST-RUN-REVIEW` 或 2E first-run fix。

## NQ-CI-POSTGRES-FLYWAY-2E-PLAN（2026-06-16）

本轮是 GateK CI Batch 2E planning-only：只读审计 CI-only seed watcher / AuthSeed / bootstrap admin / repository smoke / app context smoke / application yml / migration 边界，并新增 2E plan 文档。不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 2E 仍为 PLAN ONLY / NOT IMPLEMENTED；Batch 3-5 仍 PENDING。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 写操作前预检 | 通过 | `Get-Location` = `F:\project\nexus-quant`；`git branch --show-current` = `dev`；`git status --short` 为空。 |
| Workflow 只读审计 | 已执行 | `.github/workflows/ci.yml` 中 `backend` job 仍有 CI-only seed watcher；`postgres-flyway` job 不使用该 watcher。 |
| Seed watcher inventory | 已完成 | watcher 等待 `accounts` 表出现后插入 `ci-local-account` 到 legacy `accounts`。 |
| V12 migration 边界 | 已确认 | V12 会从 legacy `accounts` 回填 `exchange_accounts`；未发现 watcher 路径写入 `exchange_account_credentials`。 |
| AuthSeed / bootstrap admin 边界 | 已确认 | `AuthSeedConfiguration` 仅 `local` / `test`；`AuthBootstrapAdminConfiguration` 仅 `nq.auth.bootstrap-admin.enabled=true`。Batch 2D `ci-app-smoke` 避开 AuthSeed 并显式关闭 bootstrap admin。 |
| Batch 2A-2D dependency review | 已确认 | 2A empty DB smoke、2B artifacts、2C repository smoke、2D `nq-app` context smoke 均不依赖 backend job seed watcher。 |
| P0/P1 | 0 | 未发现阻断性安全 / 交易 / 凭证 / 生产风险。 |
| 本地构建 / 测试 | 未运行 | 本轮 docs-only / planning-only，且禁止改 workflow / code / test / migration；未运行 backend Maven、frontend build / E2E、Python pytest / mypy / ruff。 |

Review decision: PASS / PLAN READY FOR REVIEW。`docs/current/NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md` 可作为 2E implementation baseline，但本轮未实现 2E。

## NQ-CI-POSTGRES-FLYWAY-2D-FREEZE-REVIEW（2026-06-16）

本轮是 GateK CI Batch 2D freeze review：冻结 `nq-app` context smoke baseline。只同步允许的 `docs/current` 状态记录，不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 2D 可标记为 FROZEN / ACCEPTED；Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | **通过** | Run `27601707199`，workflow `NQ CI Baseline`，branch `dev`，commit `cbc03013bd393e2534befa10b25cc5b4c62b54a4`，status `completed`，conclusion `success`。 |
| `postgres-flyway` job | **通过** | Job `PostgreSQL / Flyway smoke` / `81604024163` completed / success；all steps success。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；Batch 2A migration smoke 未回归。 |
| Schema artifacts | 通过 | Generate / check / upload steps success；artifact `nq-postgres-flyway-schema-artifacts` id `7660159897`，digest `sha256:45b0e86ab0f499d70d04e02b7845850af11a98b3fd091f1e9c1f4b4718dc6f05`。 |
| Repository PostgreSQL smoke | 通过 | Step `Run repository PostgreSQL smoke` success；Batch 2C repository smoke 未回归。 |
| `nq-app` context smoke step | **通过** | Step `Run nq-app PostgreSQL context smoke` success。 |
| `NqAppContextPostgresSmokeTest` | **通过** | CI log shows active profile `ci-app-smoke`；Surefire summary tests=1 / skipped=0 / failures=0 / errors=0；`nq-app SUCCESS`；Maven `BUILD SUCCESS`。 |
| Seed / AuthSeed boundary | 通过 | 未发现 `AuthSeedConfiguration` 执行证据；未发现 admin / operator / viewer seed users、legacy accounts、exchange accounts 或 credential rows 创建证据。 |
| Security boundary | 通过 / P3 residual | 未发现真实 credential material；disposable CI-only PostgreSQL service values 与 generated development security password 作为 P3 log hygiene residual 延后。 |
| Batch boundary | 通过 | Batch 2D 只冻结 context startup baseline；不证明 Batch 3 no-outbound guard。Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。 |

GitHub Actions job details / steps / decoded logs / artifact metadata 通过 `gh` 与 GitHub MCP 复核。`gh run view --log --job 81604024163` 因 GitHub REST logs endpoint 返回 `HTTP 403: Must have admin rights to Repository`；已降级使用 GitHub MCP decoded logs，可信度高，因为 `gh` run metadata、MCP jobs / steps / logs 和 artifact metadata 一致。

本轮未运行本地 `mvn -f backend/pom.xml test`、frontend build / E2E、Python pytest / mypy / ruff；原因是本轮为 CI freeze review + docs/current 状态记录，且目标 CI required path 已在 GitHub Actions run `27601707199` 通过。

Review decision: PASS / FROZEN / ACCEPTED。Batch 2D 冻结为当前 `dev` `nq-app` context smoke baseline。Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2E-PLAN`、Batch 3 pre-planning，或按用户选择暂停 CI 线。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW after FIRST-RUN-FIX #3（2026-06-16）

本轮是 GateK CI Batch 2D first-run review：只评审 FIRST-RUN-FIX #3 推送后的 GitHub Actions run，并同步允许的 `docs/current` 状态记录。不修改 workflow、Java / TypeScript / Python 生产代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 2D 只能写为 IMPLEMENTED / FIRST GREEN RUN CONFIRMED，尚未 FROZEN / final ACCEPTED。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | **通过** | Run `27601707199`，workflow `NQ CI Baseline`，branch `dev`，commit `cbc03013bd393e2534befa10b25cc5b4c62b54a4`，status `completed`，conclusion `success`。 |
| `postgres-flyway` job | **通过** | Job `PostgreSQL / Flyway smoke` / `81604024163` completed / success；all steps success。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；Batch 2A migration smoke 未回归。 |
| Schema artifacts | 通过 | Generate / check / upload steps success；artifact `nq-postgres-flyway-schema-artifacts` id `7660159897`，digest `sha256:45b0e86ab0f499d70d04e02b7845850af11a98b3fd091f1e9c1f4b4718dc6f05`。 |
| Repository PostgreSQL smoke | 通过 | Step `Run repository PostgreSQL smoke` success；Batch 2C repository smoke 未回归。 |
| `nq-app` context smoke step | **通过** | Step `Run nq-app PostgreSQL context smoke` success。 |
| `NqAppContextPostgresSmokeTest` | **真实执行 / 未 skip / 通过** | CI log 显示 active profile `ci-app-smoke`；Surefire summary tests=1 / skipped=0 / failures=0 / errors=0；`nq-app SUCCESS`；Maven `BUILD SUCCESS`。 |
| Profile boundary | 通过 | 未使用 `local` profile；未 as-is 复用 current `test` profile；CI required path 使用 GitHub Actions PostgreSQL service DB properties。 |
| Seed / AuthSeed boundary | 通过 / 未发现触发 | `AuthSeedConfiguration` 仍由 profile 边界排除；未发现 admin / operator / viewer seed users、legacy accounts、exchange accounts 或 credential rows 创建证据。 |
| Security boundary | 通过 / P3 residual | 未发现真实生产 credential material；GitHub platform logging 仍显示 disposable CI-only PostgreSQL service values before / during masking，Spring Boot 仍打印 generated development security password，记录为 P3 log hygiene residual。 |
| Batch boundary | 通过 | Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。 |

GitHub Actions job details / steps / decoded logs / artifact metadata 通过 GitHub MCP 复核。`gh run view --log --job 81604024163` 因 GitHub REST logs endpoint 返回 `HTTP 403: Must have admin rights to Repository`；已降级使用 GitHub MCP decoded logs，可信度高，因为 `gh` run metadata、MCP jobs / steps / logs 和 artifact metadata 一致。

本轮未运行本地 `mvn -f backend/pom.xml test`、frontend build / E2E、Python pytest / mypy / ruff；原因是本轮为 CI first-run review + docs/current 状态记录，且目标 CI required path 已在 GitHub Actions run `27601707199` 通过。

Review decision: PASS / ACCEPTED FOR FIRST GREEN RUN。Batch 2D 当前为 FIRST GREEN RUN CONFIRMED，但尚未 FROZEN / final ACCEPTED。Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2D-FREEZE-REVIEW`、`NQ-CI-POSTGRES-FLYWAY-2E-PLAN` 或 Batch 3 pre-planning。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX after NotAMockException（2026-06-16）

本轮是 GateK CI Batch 2D first-run fix：只修复 `NqAppContextPostgresSmokeTest` 在 CI 中对真实 REST adapter 执行 Mockito verify 导致的 `NotAMockException`。不进入 Batch 2E，不进入 Batch 3-5，不修改 backend production code、workflow、migration、frontend、research、scripts 或 deploy。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `Get-Location` / `git branch --show-current` / `git status --short` | 通过 | 当前目录 `F:\project\nexus-quant`；分支 `dev`；编辑前工作区干净。 |
| `idea-mcp build_project`（目标测试文件） | 通过 | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/NqAppContextPostgresSmokeTest.java` 构建检查 `isSuccess=true`，无 problems。 |
| `mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'` | **BUILD SUCCESS** | `NqAppContextPostgresSmokeTest` tests=1 / failures=0 / errors=0 / skipped=1。本地无 CI DB properties 且未设置 `nq.app.context.smoke.required=true`，所以只证明编译 + Surefire selection；CI required path 仍需 GitHub Actions 验证 skipped=0。 |
| 未加引号 Maven 命令 | 失败 / 已 RCA | `-Dsurefire.failIfNoSpecifiedTests=false` 在 PowerShell 中被解析为非法 lifecycle phase `.failIfNoSpecifiedTests=false`；已用单引号包住该参数重跑并通过。 |
| `git diff --check` | 通过 | 无 whitespace error；仅出现 Windows 工作区 LF -> CRLF 提示。 |
| `git diff --stat` | 已检查 | 仅目标 nq-app test 与允许的 `docs/current` 文件变更。 |
| `git diff -- backend/**/db/migration` / `frontend` / `research` / `scripts` / `deploy` | 通过 | 输出为空；未触达 migration、frontend、research、scripts、deploy。 |

修复要点：

- 删除 REST adapter Mockito verification 路径；不再对可能是真实 bean 的 `OkxExchangeAdapter` / `BinanceExchangeAdapter` 做 `verify(...)`。
- 保持 `@ActiveProfiles("ci-app-smoke")` 与 `webEnvironment = MOCK`。
- 增加 active profile 断言，确保 smoke 仍运行在 CI-only profile。
- 对 WS `@MockitoBean` 先用 `mockingDetails(...).isMock()` 确认为 mock，再保留 `verifyNoInteractions(okxWsClient, binanceWsClient)`。
- 未调用 `placeOrder` / `cancelOrder` / `getOrder` / private REST / WS 方法。
- Batch 3 no-outbound guard 仍 PENDING；本轮不证明完整 no-outbound。

Review decision: PASS / FIRST-RUN-FIX APPLIED / PENDING FIRST CI RUN。Next concrete action: re-run `NQ CI Baseline` on `dev`，然后执行 `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW`。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW after FIRST-RUN-FIX #2（2026-06-16）

本轮是 GateK CI Batch 2D first-run review：只评审 FIRST-RUN-FIX #2 推送后的 GitHub Actions run，不修改 workflow、Java / TypeScript / Python 生产代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 2D 不得写成 FIRST GREEN RUN CONFIRMED、FROZEN 或 ACCEPTED。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | **失败** | Run `27596768301`，workflow `NQ CI Baseline`，branch `dev`，commit `5b6ec1aafa43d483e8ea0a6385efa09f9d0ec392`，status `completed`，conclusion `failure`。 |
| `postgres-flyway` job | **失败** | Job `PostgreSQL / Flyway smoke` / `81588559094` completed / failure；唯一失败 step 是 `Run nq-app PostgreSQL context smoke`。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；V1-V31 migration smoke 未回归。 |
| Schema artifacts | 通过 | Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts` success；artifact `nq-postgres-flyway-schema-artifacts` / id `7658307273` uploaded。 |
| Repository PostgreSQL smoke | 通过 | Step `Run repository PostgreSQL smoke` success；Batch 2C repository smoke 未回归。 |
| `nq-app` context smoke step | **失败** | Step `Run nq-app PostgreSQL context smoke` failed。 |
| `NqAppContextPostgresSmokeTest` | 真实执行 / 未 skip / 失败 | CI log 显示 active profile `ci-app-smoke`；Surefire summary tests=1 / skipped=0 / failures=0 / errors=1。 |
| Failure root cause | 已定位 | Servlet web context 已启动，测试体失败于 `NotAMockException`：`verify(...)` 的 `OkxExchangeAdapter` 不是 Mockito mock，说明 previous named bean override strategy 在 CI context 中不可靠。 |
| Profile boundary | 通过 / 未首绿 | 未使用 `local` profile；未 as-is 复用 current `test` profile；使用 `nq.app.context.smoke.required=true` 和 CI PostgreSQL service DB properties。 |
| Seed / AuthSeed boundary | 未发现触发 | `AuthSeedConfiguration` 仍由 profile 边界排除；未发现 admin / operator / viewer seed users、legacy accounts、exchange accounts 或 credential rows 创建证据。 |
| Security boundary | 不通过 / P2-P3 residual | 未发现真实生产 credential material；但 CI logs 仍包含 disposable CI PostgreSQL service connection material 的平台级显示，且 Spring Boot 打印 generated development security password；不满足本轮严格 log hygiene 验收项。 |
| Batch boundary | 通过 | Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。 |

本轮执行 / 复核命令：

```powershell
Get-Location
git status --short
git branch --show-current
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- backend/**/db/migration
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- backend
gh run list --repo ling5477/nexus-quant --branch dev --workflow "NQ CI Baseline" --limit 10 --json databaseId,headSha,headBranch,event,status,conclusion,createdAt,updatedAt,displayTitle,name,workflowName,url
gh run view 27596768301 --repo ling5477/nexus-quant --json databaseId,headSha,headBranch,event,status,conclusion,createdAt,updatedAt,displayTitle,url,jobs
rg "@ActiveProfiles\(\"local\"\)|AuthSeedConfiguration|ApplicationRunner|CommandLineRunner|Scheduled|Scheduler|Testcontainers|OKX|Binance|Bybit|Gate|Coinbase|Kraken|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|printenv|^\s*env\s*$|continue-on-error|skipTests" backend .github docs/current
```

GitHub Actions job details / steps / decoded logs / artifact metadata 通过 GitHub MCP 复核。`gh run view --log --job 81588559094` 因 GitHub REST logs endpoint 返回 `HTTP 403: Must have admin rights to Repository`；已降级使用 GitHub MCP decoded logs，可信度高，因为 `gh` run metadata、MCP jobs / steps / logs 和 artifact metadata 一致。

本轮未运行本地 `mvn -f backend/pom.xml test`、frontend build / E2E、Python pytest / mypy / ruff；原因是本轮为 CI first-run review + docs/current 状态记录，且 CI 已在 Batch 2D step 失败。下一步只能进入 targeted first-run fix 后重新验证。

Review decision: FAIL / FIRST-RUN-FIX REQUIRED。Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX` only。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW 验证记录（2026-06-16）

本轮是 GateK CI Batch 2D first-run review：只评审包含 Batch 2D 变更的 GitHub Actions run，不修改 workflow、Java / TypeScript / Python 生产代码、测试代码、migration、frontend、research、scripts 或 deploy。Batch 2D 不得写成 FIRST GREEN RUN CONFIRMED、FROZEN 或 ACCEPTED。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 失败 | Run `27590822405`，workflow `NQ CI Baseline`，branch `dev`，commit `521e100b58ec2ee2b06463bf7558ff65a9630cf4`，status `completed`，conclusion `failure`。 |
| `postgres-flyway` job | 失败 | Job `PostgreSQL / Flyway smoke` / `81570960942` completed / failure。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；日志显示 31 migrations applied / validated，current version V31。 |
| Schema artifacts | 通过 | Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts` success；artifact `nq-postgres-flyway-schema-artifacts` / id `7656304957` uploaded。 |
| Repository PostgreSQL smoke | 通过 | Step `Run repository PostgreSQL smoke` success；`JdbcRepositoryPostgresSmokeTest` tests=1 / skipped=0 / failures=0 / errors=0，Maven `BUILD SUCCESS`。 |
| `nq-app` context smoke step | 失败 | Step `Run nq-app PostgreSQL context smoke` failed。 |
| `NqAppContextPostgresSmokeTest` | 真实执行 / 未 skip / 失败 | CI log 显示 active profile `ci-app-smoke`；Surefire summary tests=1 / skipped=0 / failures=0 / errors=1。 |
| Failure root cause | 已定位 | Spring context failed while creating `AdapterBackedTradingVenueGateway` through the trading strategy dependency chain；nested cause `IllegalArgumentException: venue must not be blank`。 |
| Profile boundary | 通过 / 未首绿 | 未使用 `local` profile；未 as-is 复用 current `test` profile；使用 `nq.app.context.smoke.required=true` 和 CI PostgreSQL service DB properties。 |
| Seed / AuthSeed boundary | 未发现触发 | 未发现 `AuthSeedConfiguration` 执行、admin / operator / viewer seed users、legacy accounts、exchange accounts 或 credential rows 创建证据；但由于 context startup 失败，不能声明完整 app smoke 通过。 |
| Security boundary | 不通过 | CI logs 仍出现 disposable CI PostgreSQL service connection material / full connection string in service initialization or automatic step environment display；不是真实生产 credential material，但不满足本轮“no JDBC password / full connection string / env dump”验收项。 |
| Batch boundary | 通过 | Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。 |

本轮执行 / 复核命令：

```powershell
Get-Location
git status --short
git branch --show-current
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- backend/**/db/migration
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff --name-status HEAD^ HEAD -- backend/**/db/migration
git diff --name-status HEAD^ HEAD -- frontend
git diff --name-status HEAD^ HEAD -- research
git diff --name-status HEAD^ HEAD -- scripts
git diff --name-status HEAD^ HEAD -- deploy
git diff --check HEAD^ HEAD
git diff --stat HEAD^ HEAD
gh run list --branch dev --limit 10 --json databaseId,displayTitle,headSha,status,conclusion,workflowName,createdAt,updatedAt,event,url
gh run view 27590822405 --json databaseId,status,conclusion,headSha,workflowName,displayTitle,event,url,jobs
gh run view 27590822405 --job 81570960942 --log
rg "@ActiveProfiles\("local"\)|AuthSeedConfiguration|ApplicationRunner|CommandLineRunner|Scheduled|Scheduler|Testcontainers|OKX|Binance|Bybit|Gate|Coinbase|Kraken|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key" backend .github docs/current --glob "!backend/**/target/**"
```

GitHub Actions artifacts were reviewed through the GitHub connector; run `27590822405` uploaded only `nq-postgres-flyway-schema-artifacts` and did not upload a dedicated Surefire report artifact. Surefire was reviewed from the Maven console summary in the failed step.

本轮未运行本地 `mvn -f backend/pom.xml test`、frontend build / E2E、Python pytest / mypy / ruff；原因是本轮为 CI first-run review + docs/current 状态记录，且 CI 已在 Batch 2D step 失败。下一步只能进入 targeted first-run fix 后重新验证。

Review decision: FAIL / FIRST-RUN-FIX REQUIRED。Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX` only。

## NQ-CI-POSTGRES-FLYWAY-2D-IMPL 验证记录（2026-06-16）

本轮是 GateK CI Batch 2D implementation：实现最小 `nq-app` Spring context smoke，状态只能写为 IMPLEMENTED / PENDING FIRST CI RUN。不得写成 FROZEN / ACCEPTED，不进入 Batch 2E，不进入 Batch 3-5。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 预检 | 通过 | `Get-Location` 为 `F:\project\nexus-quant`；`git branch --show-current` 为 `dev`；编辑前 `git status --short` 无输出。 |
| App context smoke | 已实现 | 新增 `NqAppContextPostgresSmokeTest`，使用 `@SpringBootTest(webEnvironment = NONE)`。 |
| Profile / properties | 已实现 | 使用 `@ActiveProfiles("ci-app-smoke")` 和 explicit CI datasource properties；不使用 `local`，不 as-is 复用 current `test` profile。 |
| Flyway strategy | 已实现 | CI step 复用同一 `postgres-flyway` job 中已迁移 schema；context smoke 设置 `spring.flyway.enabled=false`，不重复迁移。 |
| Seed / AuthSeed boundary | 已实现 | 避开 `local` / `test`，不触发 `AuthSeedConfiguration`；不创建 admin/operator/viewer seed users、legacy accounts、exchange accounts 或 credential rows。 |
| Adapter / .env boundary | 已实现 | OKX / Binance adapter 与 WS client 使用 `MockitoBean` test doubles 替换，避免真实构造器读取 `.env` 或构造真实 exchange client path。 |
| CI wiring | 已实现 | 在 `postgres-flyway` job 的 Flyway / artifact / 2C repository smoke 后追加 `Run nq-app PostgreSQL context smoke` step；不使用 `continue-on-error`、`skipTests`、Testcontainers、bare `env`、`printenv` 或 full environment dump。 |
| Local Maven validation | 通过 / compile + selection only | `mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'` BUILD SUCCESS；`NqAppContextPostgresSmokeTest` tests=1 / skipped=1，因为本地未设置 `nq.app.context.smoke.required=true` 和 CI DB properties。 |
| Pending first CI run | 是 | 必须等待 GitHub Actions first run review 才能确认 `NqAppContextPostgresSmokeTest` 在 CI PostgreSQL service DB 上真实启动成功。 |

本轮要求执行 / 复核命令：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- backend/nq-infra/src/main/resources/db/migration
git diff -- backend/nq-app/src/main/resources/db/migration
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
rg "@ActiveProfiles\("local"\)|AuthSeedConfiguration|ApplicationRunner|CommandLineRunner|Scheduled|Scheduler|Testcontainers|OKX|Binance|Bybit|Gate|Coinbase|Kraken|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key" backend .github docs/current
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'
```

Local CI PostgreSQL app context smoke limitation:

- 本地未提供 GitHub Actions PostgreSQL service DB 和 `nq.app.context.smoke.*` properties，因此本地 selected Maven command 只能验证 test 编译 / Surefire selection，不能证明 CI PostgreSQL context startup。
- 真实 context startup 必须由 GitHub Actions `postgres-flyway` job 的 `Run nq-app PostgreSQL context smoke` step 首次运行确认。
- CI step 显式设置 `nq.app.context.smoke.required=true`，因此 GitHub Actions 中该测试不得 skip / soft-fail；缺少 datasource properties 或 context 启动失败会导致 Maven step 失败。

Review decision at implementation time: PASS / IMPLEMENTED / PENDING FIRST CI RUN。该 implementation-time decision 已由上方 `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW` 覆盖；当前状态为 FAIL / FIRST-RUN-FIX REQUIRED，下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX`。

## NQ-CI-POSTGRES-FLYWAY-2D-PLAN 验证记录（2026-06-15）

本轮是 GateK CI Batch 2D planning-only：只规划未来最小 `nq-app` context smoke，不修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、backend production code、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| Batch 2D 状态 | PLAN ONLY / NOT IMPLEMENTED | 新增 `NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`，只写方案，不新增 workflow / test / code。 |
| App context inventory | 已复核 | source-only scan 识别 3 个 full `@SpringBootTest` + `local` profile 测试，以及 `local` / `test` MVC slice 测试；现有 local/test 不适合作为 2D CI profile。 |
| AuthSeed boundary | 已复核 | `AuthSeedConfiguration` 为 `@Profile({"local", "test"})` + `ApplicationRunner`；2D plan 明确 first slice 必须避开 local/test，不隐式创建 auth users / legacy accounts / credentials。 |
| Runner / scheduler / provider boundary | 已复核 | 识别 `AuthBootstrapAdminConfiguration`、`ExchangeAdapterConfiguration`、catalog sync、OKX recovery、WS flags、scheduled services 和 no-real permission probe port；2D plan 要求显式禁用相关 side effects。 |
| Security boundary | 已复核 | 2D plan 禁止 OKX / Binance / Bybit / Gate / Coinbase / Kraken 外联、LIVE、AI、DH runtime、RealClient、real provider、真实 permission probe adapter 和真实 credential material。 |
| Batch boundary | 通过 | Batch 2A/2B/2C/2C-HYGIENE 保持 FROZEN / ACCEPTED；Batch 2E 仍 NOT STARTED；Batch 3-5 仍 PENDING。 |

本轮执行 / 复核命令：

```powershell
git status --short
rg "@SpringBootTest|ActiveProfiles|AuthSeedConfiguration|ApplicationRunner|CommandLineRunner|Scheduler|Scheduled|RealClient|provider|exchange|LIVE|OKX|Binance|Bybit|Gate|Coinbase|Kraken" backend docs/current
rg "apiKey|secret|passphrase|token|private key|mnemonic|credential material" backend .github docs/current
```

并执行 source-only follow-up scans / reads，覆盖 `.github/workflows/ci.yml`、backend poms、`backend/nq-app/src/main/resources/application*.yml`、`backend/nq-app/src/test/**`、context / seed / runner / scheduler / adapter / permission probe 相关代码与既有 Batch 2C / baseline 文档。

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是本轮为 docs-only / planning-only，不修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy，也不启动 `nq-app` context。

Review decision: PASS / PLAN ONLY / NOT IMPLEMENTED。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2D-PLAN-REVIEW` 或 2D plan fix。

## NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FREEZE-REVIEW 验证记录（2026-06-15）

本轮是 GateK CI Batch 2C hygiene freeze review：只冻结 `2C-HYGIENE-FIX` 为当前 Batch 2C CI log hygiene baseline，并同步允许的 `docs/current` 文档。未修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、backend production code、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 2C-HYGIENE-FIX | FROZEN / ACCEPTED | 已实现 job-step masking；不改变 Batch 2C repository-only smoke 语义。 |
| GitHub Actions run | 通过 | Run `27550583713`，workflow `NQ CI Baseline`，branch `dev`，commit `bcc751e7a7f4f6a60ccb877603cfbf809d55b632`，status `completed`，conclusion `success`。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / `81435457348` completed / success。 |
| Masking step | 通过 | Step `Mask CI-only PostgreSQL connection values` completed / success；GitHub MCP decoded log 复核后续 step env 中三个 `NQ_FLYWAY_DB_*` 值显示为 `***` 或不直接打印。 |
| Flyway / artifacts / repository smoke | 通过 | Flyway empty DB smoke、schema artifact generation / check / upload、repository PostgreSQL smoke 均 success。 |
| `JdbcRepositoryPostgresSmokeTest` | 通过 | GitHub MCP decoded log 显示 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，Maven `BUILD SUCCESS`。 |
| Residual P2 | Accepted | Service container initialization 和 masking step automatic `env:` display 仍可显示 disposable CI-only fake DB values；不是真实 credential material，不升级为 P1/P0。 |
| Security boundary | 通过 | 未发现真实 credential material；未新增 `printenv` / bare `env` / full environment dump；未新增 `continue-on-error`、`skipTests` 或 soft-fail。 |
| Batch boundary | 通过 | Batch 2C 保持 FROZEN / ACCEPTED；Batch 2D / 2E remain NOT STARTED；Batch 3-5 remain PENDING；未启动 `nq-app` context，未触发 `AuthSeedConfiguration`，未访问真实交易所，未开启 LIVE，未接 AI / DH runtime。 |

本轮执行 / 复核命令：

```powershell
git status --short
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- .github
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
gh run view 27550583713 --repo ling5477/nexus-quant --json status,conclusion,headSha,headBranch,displayTitle,event,createdAt,updatedAt,jobs
gh run view 27550583713 --repo ling5477/nexus-quant --job 81435457348
gh run view 27550583713 --repo ling5477/nexus-quant --log --job 81435457348
rg "printenv|^\s*env\s*$|continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current
rg "2C-HYGIENE|FROZEN|ACCEPTED|Batch 2D|Batch 2E|Batch 3|no-outbound|security scan|frontend E2E" docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md docs/current/NQ_CI_BASELINE_PLAN.md docs/current/TESTING.md docs/current/WORKLOG.md
```

`gh run view --log --job 81435457348` 因 GitHub REST logs endpoint 返回 `HTTP 403: Must have admin rights to Repository`；已使用 GitHub MCP decoded logs 复核 masking step logs 和 repository smoke step logs。可信度：高，因为 `gh` run / job metadata、GitHub MCP jobs / steps / logs 三者一致。

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是本轮为 CI freeze review + docs/current 状态记录，不修改 workflow、Java / TypeScript / Python / test / migration / frontend / research / scripts / deploy，不启动 `nq-app` context。

Review decision: PASS / FROZEN / ACCEPTED。`2C-HYGIENE-FIX` 冻结为当前 Batch 2C CI log hygiene baseline。P0/P1 为 0；P2 residual accepted。Batch 2D / 2E remain NOT STARTED；Batch 3-5 remain PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2D-PLAN`, `NQ-CI-POSTGRES-FLYWAY-2E-PLAN`, or Batch 3 pre-planning。

## NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIRST-RUN-REVIEW 验证记录（2026-06-15）

本轮是 GateK CI Batch 2C hygiene first-run review：只评审包含 `2C-HYGIENE-FIX` 的 GitHub Actions run，确认 masking 不破坏 CI，并判断 CI-only PostgreSQL URL / user / password 的后续 step log 可见性是否降低。未修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、backend production code、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 通过 | Run `27550583713`，workflow `NQ CI Baseline`，branch `dev`，commit `bcc751e7a7f4f6a60ccb877603cfbf809d55b632`，status `completed`，conclusion `success`。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / `81435457348` completed / success。 |
| Masking step | 通过 | Step `Mask CI-only PostgreSQL connection values` completed / success；注册 `NQ_FLYWAY_DB_URL` / `NQ_FLYWAY_DB_USER` / `NQ_FLYWAY_DB_PASSWORD` masking。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；V1-V31 migration smoke 未回归。 |
| Schema artifacts | 通过 | Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts` success；artifact `nq-postgres-flyway-schema-artifacts` / id `7639914125` uploaded，size `74668` bytes，digest `sha256:f12207d6a9f305ce42726110a65cb8c7d99f166008167c552f786425de5e46a0`，expires `2026-06-29T13:45:04Z`。 |
| Repository PostgreSQL smoke | 通过 | Step `Run repository PostgreSQL smoke` success；job log 显示 `JdbcRepositoryPostgresSmokeTest` 在 CI PostgreSQL service 上真实运行，Surefire summary 为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，Maven `BUILD SUCCESS`。 |
| Log hygiene | Accepted P2 residual | Masking step 之后的后续 step env 对三个 `NQ_FLYWAY_DB_*` 值显示为 `***` 或不直接打印；GitHub service container 初始化和 masking step 自身 automatic `env:` display 仍可能在 masking 生效前显示 disposable CI-only fake DB values。 |
| Security boundary | 通过 | 未发现真实 credential material；未新增 `printenv` / bare `env` / full environment dump；未新增 `continue-on-error`、`skipTests` 或 soft-fail。 |
| Batch boundary | 通过 | Batch 2C repository-only smoke 语义未改变；未启动 `nq-app` context，未触发 `AuthSeedConfiguration`，未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken，未开启 LIVE，未接 AI / DH runtime，未实现 RealClient / real provider / real exchange adapter。 |

本轮执行 / 复核命令：

```powershell
git status --short
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
gh run list --repo ling5477/nexus-quant --workflow "NQ CI Baseline" --branch dev --limit 5
gh run view 27550583713 --repo ling5477/nexus-quant --json status,conclusion,headSha,headBranch,displayTitle,event,createdAt,updatedAt,jobs
rg "printenv|^\s*env\s*$|continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current
```

GitHub Actions job details / steps / decoded logs / artifact metadata 通过 GitHub MCP 复核。`gh run view --log` 因 GitHub REST logs endpoint 返回 `HTTP 403: Must have admin rights to Repository`；已降级使用 GitHub MCP decoded logs，可信度高，因为 `gh` run metadata、MCP jobs / steps / logs 和 artifact metadata 一致。

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是本轮为 CI first-run review + docs/current 状态记录，不修改 workflow、Java / TypeScript / Python / test / migration / frontend / research / scripts / deploy，不启动 `nq-app` context。

Review decision: PASS / FIRST GREEN RUN CONFIRMED。Batch 2C 保持 FROZEN / ACCEPTED；Batch 2D / 2E remain NOT STARTED；Batch 3-5 remain PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

Freeze follow-up: closed by `NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FREEZE-REVIEW` with PASS / FROZEN / ACCEPTED. Current next action is `NQ-CI-POSTGRES-FLYWAY-2D-PLAN`, `NQ-CI-POSTGRES-FLYWAY-2E-PLAN`, or Batch 3 pre-planning。

## NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIX 验证记录（2026-06-15）

本轮是 GateK CI Batch 2C P2 log hygiene fix：只处理 `postgres-flyway` job 中 CI-only PostgreSQL URL / user / password 在 GitHub Actions logs 的可见性。已在 job steps 最早位置增加 `::add-mask::`，不改变 Flyway smoke、schema artifact generation / redaction checks、repository smoke、required failure policy 或 Batch 2C FROZEN / ACCEPTED 语义。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| Workflow hygiene fix | 已实现 | `.github/workflows/ci.yml` 新增 first step `Mask CI-only PostgreSQL connection values`，对 `NQ_FLYWAY_DB_URL` / `NQ_FLYWAY_DB_USER` / `NQ_FLYWAY_DB_PASSWORD` 执行 GitHub Actions masking。 |
| Service-level exposure | Accepted P2 residual | GitHub service container 初始化早于 job steps；若 service command output 仍显示 `nq_ci` / `nq_ci_user` / `nq_ci_password`，仍记录为 CI-only fake value exposure，不升级为 P1/P0。 |
| Batch 2C semantics | 未改变 | Flyway migrate / validate、schema artifacts、artifact redaction check、artifact upload 和 `JdbcRepositoryPostgresSmokeTest` Maven command 均保持原语义。 |
| Local CI reproduction | 不要求 | 本轮不要求本地复现 GitHub service log；first GitHub Actions run verification 已由 run `27550583713` 的 `2C-HYGIENE-FIRST-RUN-REVIEW` 关闭。 |

本轮执行 / 复核命令：

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
rg "printenv|^\s*env\s*$|continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current
```

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是本轮只修改 GitHub Actions log hygiene step 与 `docs/current` 文档，不修改 Java / TypeScript / Python / 测试代码 / migration / backend production code / frontend / research / scripts / deploy，不启动 `nq-app` context。

Closed CI verification：GitHub Actions run `27550583713` 已复核 `postgres-flyway` job success，`JdbcRepositoryPostgresSmokeTest` 仍为 `tests=1 / skipped=0 / failures=0 / errors=0`，并确认 masking step 之后三个 `NQ_FLYWAY_DB_*` 值在后续 step logs 中显示为 `***` 或不直接打印。

## NQ-CI-POSTGRES-FLYWAY-2C-FREEZE-REVIEW 验证记录（2026-06-15）

本轮是 GateK CI Batch 2C freeze review：只冻结 repository-only real PostgreSQL smoke baseline，并同步允许的 `docs/current` 文档。未修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、backend production code、frontend、research、scripts 或 deploy。预检时工作区已有非本轮 `backend/nq-auth/src/main/java/com/guidinglight/nexusquant/auth/application/DbAuthService.java` import 排序 diff，本轮未触碰该文件。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 通过 | Run `27535619157`，workflow `NQ CI Baseline`，branch `dev`，commit `9adb71b8dc56a0bf881952da918ebaab5fdbeb7f`，status `completed`，conclusion `success`。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / `81384164182` completed / success；`gh run view` 与 GitHub MCP job list 一致。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；artifact `flyway-info.txt` 复核 31 rows，首行为 `V1__init.sql`，末行为 `V31__schema_credential_permission_probe.sql`，全部 success。 |
| Schema artifacts | 通过 | Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts` success；artifact `7633555246` 未过期，包含且仅包含 7 个 schema-only 文件。 |
| Repository PostgreSQL smoke | 通过 | GitHub MCP decoded log 显示 `JdbcRepositoryPostgresSmokeTest` 在 CI PostgreSQL service 上真实运行；Surefire summary 为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，Maven `BUILD SUCCESS`。 |
| CI PostgreSQL service | 通过 | `postgres:16` service container reached `healthy`；repository smoke 使用 disposable CI DB `nq_ci` / `nq_ci_user` / `nq_ci_password`。 |
| Schema-only / redaction | 通过 | 下载 artifact 复核：`schema-dump.sql` 中 `INSERT INTO`、`COPY ... FROM stdin`、`-- Data for Name:` 均为 0；artifact high-risk `.env` / credential / raw request / raw response assignment pattern 为 0。 |
| Boundary review | 通过 | 2C smoke stays in `nq-infra` repository scope；不启动 `nq-app` context、不使用 `@SpringBootTest`、不触发 `AuthSeedConfiguration`、不复用 Batch 1 seed watcher、不纳入 credential repository。 |
| P2 log hygiene | Accepted P2 / cleanup frozen | GitHub Actions 自动 step env / service command output 显示 CI-only PostgreSQL URL / user / password；这些是 disposable CI fake service DB values，不是真实 credential material，不阻塞 freeze。`NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIX` 已完成 first run review，并经 freeze review 固化为 FROZEN / ACCEPTED。 |
| Forbidden-area diff | 有既有脏改，不属本轮 | `.github`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` diff 为空；`backend` diff 仅为预先存在的 `DbAuthService.java` import 排序变更，本轮未修改。 |

本轮执行 / 复核命令：

```powershell
git status --short
git branch --show-current
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- .github
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
gh run view 27535619157 --repo ling5477/nexus-quant --json status,conclusion,headSha,headBranch,displayTitle,event,createdAt,updatedAt,jobs
gh run view 27535619157 --repo ling5477/nexus-quant --job 81384164182
gh run download 27535619157 --repo ling5477/nexus-quant -n nq-postgres-flyway-schema-artifacts -D <temp-dir>
rg -e '@SpringBootTest' -e 'AuthSeedConfiguration' -e 'ActiveProfiles\("local"\)' -e 'ActiveProfiles\("test"\)' -e 'Testcontainers' -e 'OKX' -e 'Binance' -e 'Bybit' -e 'Gate' -e 'Coinbase' -e 'Kraken' -e 'LIVE=true' -e 'LIVE_ENABLED' -e 'apiKey' -e 'secret' -e 'passphrase' -e 'token' -e 'private key' backend .github docs/current
rg -e 'Batch 2C' -e 'FIRST GREEN' -e 'FROZEN' -e 'ACCEPTED' -e 'Batch 2D' -e 'Batch 2E' -e 'no-outbound' -e 'security scan' -e 'frontend E2E' -e 'AuthSeedConfiguration' -e 'SpringBootTest' docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md docs/current/NQ_CI_BASELINE_PLAN.md docs/current/TESTING.md docs/current/WORKLOG.md
```

`gh run view --log --job 81384164182` 因 GitHub REST logs endpoint 返回 `HTTP 403: Must have admin rights to Repository`；已用 GitHub MCP decoded logs 复核同一 job 的 full log。可信度：高，因 GitHub MCP job/log、`gh` run metadata、artifact metadata 和 artifact ZIP 内容一致。

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是本轮为 CI freeze review + docs/current 状态记录，不修改 workflow、Java / TypeScript / Python / test / migration / frontend / research / scripts / deploy。

Review decision: PASS / FROZEN / ACCEPTED。Batch 2C 冻结为当前 `dev` repository-only real PostgreSQL smoke baseline。Batch 2D / 2E remain NOT STARTED；Batch 3-5 remain PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

Hygiene follow-up: first-run review and freeze review are now closed. Current next action is `NQ-CI-POSTGRES-FLYWAY-2D-PLAN`, `NQ-CI-POSTGRES-FLYWAY-2E-PLAN`, or Batch 3 pre-planning。

## NQ-CI-POSTGRES-FLYWAY-2C-FIRST-RUN-REVIEW 验证记录（2026-06-15）

本轮是 GateK CI Batch 2C first-run review：只评审包含 repository-only real PostgreSQL smoke 的 GitHub Actions run，并同步允许的 `docs/current` 文档。未修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、backend production code、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 通过 | Run `27535619157`，workflow `NQ CI Baseline`，branch `dev`，commit `9adb71b8dc56a0bf881952da918ebaab5fdbeb7f`，status `completed`，conclusion `success`。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / `81384164182` completed / success。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；artifact `flyway-info.txt` 复核 V1-V31 共 31 条 migration row，首版本 `1`，末版本 `31`，全部 success。 |
| Schema artifacts | 通过 | Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts` success；artifact `7633555246` 未过期，ZIP 恰含 7 个 schema-only 文件。 |
| Repository PostgreSQL smoke | 通过 | Step `Run repository PostgreSQL smoke` success；job log 显示 `JdbcRepositoryPostgresSmokeTest` 在 CI PostgreSQL service 上真实运行，Surefire summary 为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，Maven `BUILD SUCCESS`。 |
| CI PostgreSQL service | 通过 | `postgres:16` service container health reached healthy；repository smoke 使用同一 disposable CI DB 生命周期，在 artifact 生成后运行。 |
| Schema-only / redaction | 通过 | `schema-dump.sql` 中 `INSERT INTO`、`COPY ... FROM stdin`、`-- Data for Name:` 均为 0；artifact high-risk `.env` / credential / raw request / raw response assignment pattern 均为 0。 |
| Log hygiene | 有 P2 记录 | GitHub Actions 自动 step env / service command output 会显示 CI-only PostgreSQL URL / user / password；未发现真实 credential material，但 freeze review 前需决定是否收口该日志暴露。 |
| Boundary review | 通过 | 2C source / workflow 复核确认 repository smoke 不启动 `nq-app` context、不使用 `@SpringBootTest`、不触发 `AuthSeedConfiguration`、不复用 Batch 1 seed watcher、不纳入 credential repository。 |
| Forbidden-area diff | 通过 | `git diff -- backend/nq-infra/src/main/resources/db/migration`、`frontend`、`research`、`scripts`、`deploy` 均为空；本轮只修改允许的 `docs/current` 文档。 |

本轮执行 / 复核命令：

```powershell
Get-Location
git status --short
git branch --show-current
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- backend/nq-infra/src/main/resources/db/migration
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
rg -e '@SpringBootTest' -e 'AuthSeedConfiguration' -e 'ActiveProfiles\("local"\)' -e 'ActiveProfiles\("test"\)' -e 'Testcontainers' -e 'OKX' -e 'Binance' -e 'Bybit' -e 'Gate' -e 'Coinbase' -e 'Kraken' -e 'LIVE=true' -e 'LIVE_ENABLED' -e 'apiKey' -e 'secret' -e 'passphrase' -e 'token' -e 'private key' backend .github docs/current
```

GitHub Actions run details / jobs / logs 通过 GitHub MCP、GitHub REST runs API 和 artifact ZIP 复核。`gh` CLI 不存在；GitHub REST job-log endpoint 返回 `403 Must have admin rights to Repository`，因此 job logs 使用 GitHub MCP decoded logs，run list / artifact metadata 使用 GitHub REST / MCP，artifact ZIP 使用 MCP 下载引用复核。可信度：高，因 run/job/step 状态、job log Surefire 摘要和 artifact 内容三者一致。

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是本轮为 CI first-run review + docs/current 状态记录，不修改 workflow、Java / TypeScript / Python / test / migration / frontend / research / scripts / deploy。

Review decision: PASS / ACCEPTED FOR FIRST GREEN RUN。Batch 2C 当时状态为 IMPLEMENTED / FIRST GREEN RUN CONFIRMED；后续已由 `NQ-CI-POSTGRES-FLYWAY-2C-FREEZE-REVIEW` 冻结为 FROZEN / ACCEPTED，P2 log hygiene finding 已由 `NQ-CI-POSTGRES-FLYWAY-2C-HYGIENE-FIX` / first-run review / freeze review 收口为 accepted P2 residual。Batch 2D / 2E remain NOT STARTED；Batch 3-5 remain PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

Follow-up: Batch 2C freeze review and hygiene freeze review are now closed. Current next action is `NQ-CI-POSTGRES-FLYWAY-2D-PLAN`, `NQ-CI-POSTGRES-FLYWAY-2E-PLAN`, or Batch 3 pre-planning。

## NQ-CI-POSTGRES-FLYWAY-2C-IMPL 验证记录（2026-06-15）

本轮是 GateK CI Batch 2C implementation：在既有 `postgres-flyway` job 中追加 repository-only real PostgreSQL smoke，并新增 `nq-infra` test-only smoke。当前状态只能写为 IMPLEMENTED / PENDING FIRST CI RUN；不得写成 FROZEN / ACCEPTED。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 预检 | 通过 | `Get-Location` 为 `F:\project\nexus-quant`；`git branch --show-current` 为 `dev`；编辑前 `git status --short` 为空。 |
| Repository smoke implementation | 已实现 | 新增 `JdbcRepositoryPostgresSmokeTest`，覆盖 `JdbcAuditLogRepository`、`JdbcRiskEventRepository`、`JdbcMarketdataBarRepository`；只使用 `DriverManagerDataSource` / `JdbcTemplate` / `TransactionTemplate`，不启动 `nq-app` context。 |
| Fixture / cleanup | 已实现 | 使用 `ci-repo-smoke-*` fake fixture；所有 insert/upsert/read 在事务中执行并 `setRollbackOnly()`；不上传数据 artifact。 |
| CI wiring | 已实现 | `postgres-flyway` job 在 Flyway migrate / validate 与 2B schema artifact upload 后执行 Maven Surefire include；同一 job / service 生命周期内复用已迁移 disposable DB，不假设跨 job 共享 DB。 |
| POM dependency | 已调整 | `backend/nq-infra/pom.xml` 新增 test-scope `org.postgresql:postgresql`，仅用于 repository smoke 的 JDBC driver；未新增生产依赖。 |
| PowerShell command retry | 已记录 | 首次本地 Maven 命令未给带点号的 `-D` property 加引号，PowerShell 将参数拆为 `.failIfNoSpecifiedTests=false`，命令失败；已用引号复跑通过。 |
| Minimal Maven validation | 通过 | `mvn -f backend/pom.xml -pl nq-infra -am test -Dtest=JdbcRepositoryPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'`：BUILD SUCCESS；`JdbcRepositoryPostgresSmokeTest` 1 skipped（未提供 DB properties，本地默认不要求 PostgreSQL）。 |
| Local real PostgreSQL smoke | 未执行 | 本轮未向本机 PostgreSQL 注入 `nq.postgres.smoke.*` properties；GitHub Actions service-container 真 DB 执行等待 first CI run。 |

本轮已执行 / 待执行命令：

```powershell
Get-Location
git status --short
git branch --show-current
mvn -f backend/pom.xml -pl nq-infra -am test -Dtest=JdbcRepositoryPostgresSmokeTest '-Dsurefire.failIfNoSpecifiedTests=false'
```

收尾验证已执行：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- backend/nq-infra/src/main/resources/db/migration
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
rg "@SpringBootTest|AuthSeedConfiguration|ActiveProfiles\(\"local\"\)|ActiveProfiles\(\"test\"\)|Testcontainers|OKX|Binance|Bybit|Gate|Coinbase|Kraken|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key" backend .github docs/current
```

收尾验证结果：

- `git status --short`：显示本轮允许文件变更；新增 smoke test 文件为 untracked。
- `git diff --check`：通过，退出码 0；仅有 Windows LF/CRLF 工作区提示。
- `git diff --stat`：已检查 tracked diff；新增 untracked test 文件由 `git status --short` / `git ls-files --others --exclude-standard` 确认。
- `git diff -- backend/nq-infra/src/main/resources/db/migration`：输出为空，未修改 migration。
- `git diff -- frontend`、`git diff -- research`、`git diff -- scripts`、`git diff -- deploy`：输出均为空。
- 用户要求的 broad `rg` 已执行；命中包含历史文档、既有 credential / exchange 代码、以及本轮 Maven 生成的 `target` 报告噪音，不作为本轮新增边界穿越证据。
- Source-only / changed-files follow-up `rg --glob '!**/target/**' ...` 已执行；本轮新增测试与 CI step 未命中 `@SpringBootTest`、`AuthSeedConfiguration`、`ActiveProfiles("local")`、`ActiveProfiles("test")`、`Testcontainers`、`LIVE=true`、`LIVE_ENABLED` 或真实 credential material 输出。命中项仅为文档禁止说明、既有 artifact redaction grep，以及既有 credential repository mock 测试中的 fake JSON。

Boundary confirmation:

- 未启动 `nq-app` full context。
- 未使用 `@SpringBootTest`。
- 未触发 `AuthSeedConfiguration`。
- 未复用 Batch 1 CI-only seed watcher。
- 未新增 legacy account seed。
- 未新增 migration，未修改历史 migration。
- 未修改 backend production code。
- 未修改 frontend / research / scripts / deploy。
- 未实现 Batch 2D / 2E。
- 未实现 Batch 3 no-outbound guard、Batch 4 security guard / secret scan、Batch 5 frontend E2E hardening。
- 未开启 LIVE，未接 AI，未接 DH runtime，未实现 RealClient / real provider / real exchange adapter。
- 未读取、打印、复制或输出真实 credential material。

Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2C-FIRST-RUN-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2C-FIRST-RUN-FIX`。

## NQ-CI-POSTGRES-FLYWAY-2C-PLAN-REVIEW 验证记录（2026-06-15）

本轮是 GateK CI Batch 2C review-only：评审 `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md` 是否可作为 repository real PostgreSQL smoke implementation baseline。本轮只同步允许的 `docs/current` 文档；未修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 预检 | 通过 | `Get-Location` 为 `F:\project\nexus-quant`；`git branch --show-current` 为 `dev`；编辑前 `git status --short` 为空。 |
| Plan review | 通过 | `NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md` 覆盖 repository test inventory、2C-1 / 2C-2 / 2C-3 切片、seed / fixture、transaction / cleanup、rollback、安全和 batch 边界。 |
| Repository inventory review | 通过 | Source-only `rg` 复核显示 `nq-infra` repository 测试主要为 `RecordingJdbcTemplate` / `RecordingNamedParameterJdbcTemplate` / `Mockito.mock(JdbcTemplate)`；2C plan 对 mock / Recording 与 real PostgreSQL smoke 的区分准确。 |
| Spring context boundary review | 通过 | `nq-app` 中 `MarketdataControllerLocalIntegrationTest`、`ResearchBacktestHappyPathLocalTest`、`OkxBootstrapNoOutboundLocalContextTest` 使用 `@SpringBootTest` + `@ActiveProfiles("local")`；2C plan 正确划入 2D，不纳入 2C。 |
| Auth seed / runner risk | 通过 | `AuthSeedConfiguration` 是 `local` / `test` profile 的 `ApplicationRunner`；2C plan 明确不启动 `nq-app` context、不触发 `AuthSeedConfiguration` 或 runner。 |
| 2C-1 candidates | 通过 | audit log、risk event、event store、marketdata bars 均为 `nq-infra` repository / JDBC 路径，可覆盖 JSONB、insert、`ON CONFLICT`、timestamp / quoted `"interval"` 行为；不需要 app context 或 exchange adapter。 |
| Credential repository deferral | 通过 | `JdbcExchangeAccountCredentialRepository` / test 涉及 `pgp_sym_encrypt`、`pgp_sym_decrypt`、`CAST(? AS jsonb)` 和 credential material shape；2C plan 正确推迟到 2C-2+ 并要求 fake material / 脱敏 / cleanup 单独评审。 |
| Seed / fixture boundary | 通过 | 默认不使用 legacy account seed、不复用 Batch 1 seed watcher、不触发 `AuthSeedConfiguration`；如需 fixture，只允许 CI-only fake fixture 并 rollback / cleanup。 |
| Transaction / cleanup boundary | 通过 | 计划优先 transaction rollback，必要时按 unique test id explicit cleanup；不运行 Flyway `clean`，不污染 2A/2B schema artifacts。 |
| Security boundary | 通过 | 计划禁止 OKX / Binance / Bybit / Gate / Coinbase / Kraken 外联，禁止 LIVE、AI、DH runtime、RealClient、real provider、真实 credential、`.env` 读取和 data dump artifact。 |
| Batch boundary | 通过 | 2C 仅 repository real PostgreSQL smoke；2D/2E 仍 NOT STARTED，Batch 3-5 仍 PENDING。 |
| Forbidden-area diff | 通过 | `git diff -- .github`、`backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` 均为空。 |

本轮执行 / 复核命令：

```powershell
Get-Location
git status --short
git branch --show-current
git ls-files .github
git diff --check
git diff --stat
git diff -- .github
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "Repository|Jdbc|RecordingJdbcTemplate|SpringBootTest|ActiveProfiles|Testcontainers|PostgreSQL|Flyway|seed|AuthSeedConfiguration|repository real PostgreSQL|Batch 2C|Batch 2D|Batch 2E" backend docs/current
rg "LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|OKX|Binance|Bybit|Gate|Coinbase|Kraken" backend .github docs/current
```

Source-only follow-up scans used `--glob '!**/target/**'` to avoid build output noise and to verify repository / Spring context / credential repository evidence. Some exploratory PowerShell regex commands failed due quote escaping; equivalent `rg -e` source-only commands were rerun and used for the review conclusion.

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是本轮只做 docs review / freeze wording sync，未修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy。

Review decision: PASS / FROZEN / ACCEPTED。Batch 2C plan 可作为 implementation baseline；Batch 2C implementation remains NOT STARTED；Batch 2D / 2E remain NOT STARTED；Batch 3-5 remain PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2C-IMPL`, `NQ-CI-POSTGRES-FLYWAY-2C-PLAN-FIX`, or separate 2D / 2E / Batch 3 pre-planning。

## NQ-CI-POSTGRES-FLYWAY-2C-PLAN 验证记录（2026-06-15）

本轮是 GateK CI Batch 2C planning-only：只规划 repository real PostgreSQL smoke，不修改 workflow，不改 Java / TypeScript / Python 代码，不改测试代码，不新增 API，不新增 migration，不修改历史 migration，不改 backend 生产逻辑、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 预检 | 通过 | `Get-Location` 为 `F:\project\nexus-quant`；`git branch --show-current` 为 `dev`；编辑前 `git status --short` 为空。 |
| Current docs review | 通过 | 已复核 `AGENTS.md`、`README.md`、`docs/current/README.md`、`NQ_CI_POSTGRES_FLYWAY_PLAN.md`、`NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`、`NQ_CI_BASELINE_PLAN.md`。 |
| Workflow read-only review | 通过 | 只读复核 `.github/workflows/ci.yml`；当前有 `backend` PostgreSQL service + CI-only seed watcher，以及 `postgres-flyway` 2A/2B job；本轮未修改 workflow。 |
| Maven / config review | 通过 | 已复核 `backend/pom.xml`、`backend/nq-app/pom.xml`、`backend/nq-infra/pom.xml`、`application.yml`、`application-local.yml`、`application-test.yml`。 |
| Repository test inventory | 通过 | `nq-infra` repository 测试主要使用 `RecordingJdbcTemplate` / `Mockito.mock(JdbcTemplate)`；未发现现成 Testcontainers / real PostgreSQL repository test baseline。 |
| Spring context boundary | 通过 | `nq-app` 中 `MarketdataControllerLocalIntegrationTest`、`ResearchBacktestHappyPathLocalTest`、`OkxBootstrapNoOutboundLocalContextTest` 使用 `@SpringBootTest` + `local` profile，划入 2D，不纳入 2C。 |
| Seed boundary | 通过 | 2C plan 默认不使用 legacy account seed、不复用 Batch 1 seed watcher、不触发 `AuthSeedConfiguration`；如 future fixture 必需，只允许 CI-only fake fixture 并 rollback / cleanup。 |
| Security boundary | 通过 | 2C plan 禁止 OKX / Binance / Bybit / Gate / Coinbase / Kraken 外联，禁止 LIVE、AI、DH runtime、RealClient、real provider、真实 credential。 |
| Forbidden-area diff | 通过 | `git diff -- .github`、`backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` 均为空。 |
| Docs-only diff | 通过 | `git status --short` 仅显示允许的 `docs/current` 修改和新增文件；`git diff --stat` 覆盖 tracked docs diff。 |
| Whitespace check | 通过 | `git diff --check` 通过；另用 `rg "[ \t]+$"` 检查本轮新增 / 修改 docs，无 trailing whitespace 命中。 |

本轮执行 / 复核命令：

```powershell
Get-Location
git status --short
git branch --show-current
git diff --check
git diff --stat
git diff -- .github
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "Repository|Jdbc|RecordingJdbcTemplate|SpringBootTest|ActiveProfiles|Testcontainers|PostgreSQL|Flyway|seed|AuthSeedConfiguration" backend docs/current
rg "LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|OKX|Binance|Bybit|Gate|Coinbase|Kraken" backend .github docs/current
rg "[ \t]+$" docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md docs/current/NQ_CI_BASELINE_PLAN.md docs/current/README.md docs/current/TESTING.md docs/current/WORKLOG.md
```

Source-only follow-up scans also used `--glob '!**/target/**'` to avoid build output noise. PowerShell direct path globs such as `backend/**/src/test` were not used for final evidence because Windows treats them as invalid path arguments; equivalent `rg --glob` filters were used.

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是本轮只新增 / 同步 `docs/current` planning 文档，未修改 workflow、代码、测试、migration、frontend、research、scripts 或 deploy。

Review decision: PLAN READY FOR REVIEW。Batch 2C remains NOT IMPLEMENTED；Batch 2D / 2E remain NOT STARTED；Batch 3-5 remain PENDING；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；real exchange adapter / provider / RealClient NOT IMPLEMENTED。

Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2C-PLAN-REVIEW` or `NQ-CI-POSTGRES-FLYWAY-2C-PLAN-FIX`。

## NQ-CI-POSTGRES-FLYWAY-2B-FREEZE-REVIEW 验证记录（2026-06-15）

本轮是 GateK CI Batch 2B freeze review：冻结 PostgreSQL / Flyway schema artifact baseline，确认它成为当前 `dev` CI 的 schema artifact 最小验证基线。本轮只同步允许的 `docs/current` 文档；未修改 `.github/workflows/ci.yml`，未修改 Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| 预检 | 通过 | `Get-Location` 为 `F:\project\nexus-quant`；`git branch --show-current` 为 `dev`；编辑前 `git status --short` 为空。 |
| GitHub Actions run | 通过 | GitHub 插件复核 run `27521750442` latest attempt jobs 全部 completed / success；artifact metadata 绑定 branch `dev` 与 commit `c62ebddd5a522bbdf72bc018064b9eb36d8fe9e1`。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / `81340926116` completed / success。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；job log 显示 `Flyway empty database smoke reached V31`。 |
| Schema artifact generation | 通过 | Steps `Generate PostgreSQL schema artifacts`、`Check PostgreSQL schema artifacts`、`Upload PostgreSQL schema artifacts` 均 success。 |
| Artifact metadata | 通过 | Artifact `nq-postgres-flyway-schema-artifacts` / id `7628309014`，size `74662` bytes，digest `sha256:a06957e02f55761047aff197d5954b2fbb2e2269f590b598b79549a5e72155e5`，`expired=false`，`expires_at=2026-06-29T03:14:04Z`，符合 `dev` push 14-day retention。 |
| Artifact file list | 通过 | In-memory ZIP review confirmed exactly 7 required files: `flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`、`schema-dump.sql`；无 missing / extra / empty file。 |
| Flyway V1-V31 artifact | 通过 | `flyway-info.txt` 有 31 条非空 migration rows，首版本 `1`，末版本 `31`。 |
| `schema-dump.sql` schema-only check | 通过 | In-memory review 对 `INSERT INTO`、`COPY ... FROM stdin`、`-- Data for Name:`、dump data terminator pattern 的命中数为 0。 |
| Artifact redaction | 通过 | In-memory review 对 `.env`、API key、secret、passphrase、token、cookie、private key、mnemonic、credential material、raw request / raw response high-risk pattern 的命中数为 0。 |
| Workflow boundary | 通过 | `rg` 复核 `.github/workflows/ci.yml`：artifact 使用 metadata 查询与 `pg_dump --schema-only --no-owner --no-privileges`；未发现 `printenv` / bare `env` / `continue-on-error`。 |
| Forbidden-area diff | 通过 | `git diff -- .github`、`backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` 均为空。 |
| Stage wording scan | 通过 | `rg` 复核 Batch 2B / 2C / 2D / 2E / Batch 3-5 文档口径；2B 冻结后，2C/2D/2E 仍 NOT STARTED，Batch 3-5 仍 PENDING。 |

本轮执行 / 复核命令：

```powershell
git status --short
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- .github
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "Batch 2B|FIRST GREEN|FROZEN|ACCEPTED|Batch 2C|Batch 2D|Batch 2E|no-outbound|security scan|frontend E2E" docs/current/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md docs/current/NQ_CI_BASELINE_PLAN.md docs/current/TESTING.md docs/current/WORKLOG.md
rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current
```

本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；原因是 freeze review 只冻结已成功的 GitHub Actions run / artifact 证据并同步文档，未修改 workflow、Java / TypeScript / Python / test / migration / frontend / research / scripts / deploy。

Review decision: PASS / FROZEN / ACCEPTED。Batch 2B 已冻结为当前 `dev` 的 PostgreSQL / Flyway schema artifact minimal baseline。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2C-PLAN`、后续发现回归时的 `NQ-CI-POSTGRES-FLYWAY-2B-FIX`，或 Batch 3 前置 planning；不得直接进入真实交易所、LIVE、AI 或 DH runtime。

## NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW 验证记录（2026-06-15）

本轮是 GateK CI Batch 2B first-run review：只评审 GitHub Actions run `27521750442` 的 schema / Flyway artifact 生成、上传、retention 和 redaction 结果，并同步允许的 `docs/current` 文档。未修改 `.github/workflows/ci.yml`，未修改 Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 通过 | Run `27521750442`，workflow `NQ CI Baseline`，branch `dev`，commit `c62ebddd5a522bbdf72bc018064b9eb36d8fe9e1`，status `completed`，conclusion `success`。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / `81340926116` completed / success。 |
| Flyway empty DB smoke | 通过 | Step `Run empty database Flyway smoke` success；job log 显示 `Flyway empty database smoke reached V31`。 |
| Artifact generation | 通过 | Step `Generate PostgreSQL schema artifacts` success。 |
| Artifact check | 通过 | Step `Check PostgreSQL schema artifacts` success；blocking check 未发现 data rows 或 high-risk credential pattern。 |
| Artifact upload | 通过 | Step `Upload PostgreSQL schema artifacts` success；log 显示 7 files uploaded。 |
| Artifact metadata | 通过 | Artifact `nq-postgres-flyway-schema-artifacts` / id `7628309014`，size `74662` bytes，digest `sha256:a06957e02f55761047aff197d5954b2fbb2e2269f590b598b79549a5e72155e5`，`expires_at=2026-06-29T03:14:04Z`，符合 `dev` push 14-day retention。 |
| Artifact download check | 通过 | 下载 ZIP 后确认仅包含 `flyway-info.txt`、`schema-tables.txt`、`schema-columns.txt`、`schema-constraints.txt`、`schema-indexes.txt`、`schema-comments.txt`、`schema-dump.sql`，无 missing / extra / empty file。 |
| `schema-dump.sql` data rows | 通过 | 本地检查 `INSERT` / `COPY ... FROM stdin` / data dump marker 命中数为 0。 |
| Artifact redaction | 通过 | 本地检查 `.env`、API key、secret、passphrase、token、cookie、private key、mnemonic、credential material、raw request / response pattern 命中数为 0。 |
| Boundary scan | 通过 | `postgres-flyway` 未启动 `nq-app` context，未跑 repository real DB smoke，未插入 seed，未启用 Testcontainers，未实现 no-outbound guard / secret scan / frontend E2E hardening。 |

本轮执行 / 复核命令：

```powershell
git status --short
git diff --check
git diff --stat
git show --stat --oneline --name-only HEAD
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current
```

Boundary confirmation:

- Batch 2B first green run confirmed；尚未 freeze / accepted。
- Batch 2C repository real PostgreSQL smoke：NOT STARTED。
- Batch 2D `nq-app` context smoke：NOT STARTED。
- Batch 2E CI-only seed watcher cleanup：NOT STARTED。
- Batch 3 no-outbound guard：PENDING。
- Batch 4 security guard / secret scan：PENDING。
- Batch 5 frontend E2E hardening：PENDING。
- AI：NOT STARTED；DH runtime：NOT INTEGRATED；LIVE：DISABLED；real exchange adapter：NOT IMPLEMENTED。

Review decision: PASS / ACCEPTED FOR FIRST GREEN RUN。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2B-FREEZE-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2C-PLAN`。

## 统一验证命令

### 后端验证

```powershell
mvn -f backend/pom.xml test
```

### 前端验证

```powershell
Set-Location frontend
npm ci
npm run build
npm run test:e2e
```

### Python 验证

首次本地验证前安装 dev 依赖：

```powershell
Set-Location research/py
python -m pip install -e ".[dev]"
```

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

### 本地启动验证

```powershell
docker compose up -d postgres
```

启动 `nq-app` local profile 后检查：

```powershell
Invoke-RestMethod http://localhost:18888/actuator/health
```

并检查：

- `POST /api/auth/login`
- `GET /api/auth/me`

## 本地 PostgreSQL 规则

- 本地 PostgreSQL 默认端口是 `5432`。
- 使用本机 PostgreSQL 时，不重复启动 `docker-compose postgres`。
- 使用 `docker-compose postgres` 时，确认本机 `5432` 未被占用。

## 本次实际验证记录

## NQ-CI-POSTGRES-FLYWAY-2B-IMPL 验证记录（2026-06-15）

本轮是 GateK CI Batch 2B implementation：只在既有 `.github/workflows/ci.yml` 的 `postgres-flyway` job 中增加 schema artifact generation / upload，并同步允许的 `docs/current` 文档。未修改 Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

本轮本地未运行 GitHub Actions PostgreSQL service container，也未触发 `actions/upload-artifact`；`postgres-flyway` artifact first CI run 仍 pending，必须由后续 `NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW` 复核。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区变更仅限 `.github/workflows/ci.yml` 与允许的 `docs/current` 文件。 |
| `git diff --check` | 已执行 | 用于检查 whitespace error。 |
| `git diff --stat` | 已检查 | 用于确认变更范围。 |
| `git diff -- backend` | 通过 | 输出为空，未改 backend。 |
| `git diff -- frontend` | 通过 | 输出为空，未改 frontend。 |
| `git diff -- research` | 通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 通过 | 输出为空，未改 deploy。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| forbidden keyword scan | 已执行 | `rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current`；workflow 不允许新增真实交易所 / LIVE / skip / soft-fail 行为，docs 命中只能是禁止说明、历史记录或边界说明。 |
| artifact command boundary | 已检查 | Workflow 使用 libpq connection string 调用 `psql`，未把 JDBC URL 传给 `psql`；未使用 `env` / `printenv` 输出 full environment。 |
| schema-only dump boundary | 已检查 | `pg_dump` 命令包含 `--schema-only --no-owner --no-privileges`。 |
| data row boundary | 已检查 | Artifact 查询来源限定为 `flyway_schema_history`、`information_schema`、`pg_constraint` / `pg_class` / `pg_namespace`、`pg_indexes`、`obj_description` / `col_description`；未查询业务表 row values。 |
| redaction boundary | 已检查 | 新增 artifact check 会阻塞 high-risk credential material pattern，并检查 `schema-dump.sql` 不含 `INSERT` / `COPY ... FROM stdin` / data dump marker。 |

安全边界：

- 未启动 `nq-app` context，未触发 `AuthSeedConfiguration`。
- 未跑 repository real PostgreSQL smoke，未插入 seed，未启用 Testcontainers。
- 未实现 no-outbound guard、gitleaks / secret scan 或 frontend E2E hardening。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider 或真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken adapter。
- 未读取、打印、复制或输出真实 credential material。

Review decision: IMPLEMENTED / PENDING FIRST CI RUN。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW`；如果 first run 失败，则只能做 `NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-FIX`。

## NQ-CI-POSTGRES-FLYWAY-2B-PLAN 验证记录（2026-06-14）

本轮是 GateK CI Batch 2B planning-only：只新增 / 同步 `docs/current` 文档，规划 Flyway / schema artifact、retention、redaction 和 `DB_SCHEMA.md` drift review。未修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 编辑前工作区干净；本地 `dev` 在 2A freeze commit 后比 `origin/dev` ahead 1。 |
| `.github/workflows/ci.yml` 只读复核 | 已执行 | 当前仅有 Batch 2A `postgres-flyway` job；本轮未修改 workflow。 |
| `DB_SCHEMA.md` / migration 只读复核 | 已执行 | 当前最大 migration 为 `V31__schema_credential_permission_probe.sql`；2B 只规划 artifact / drift review，不新增或修改 migration。 |
| Batch 2B 状态检查 | 通过 | `NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md` 明确 `PLAN ONLY / NOT IMPLEMENTED`。 |
| Batch boundary | 通过 | Batch 2C/2D/2E 仍 NOT STARTED；Batch 3 no-outbound、Batch 4 security guard / secret scan、Batch 5 frontend E2E hardening 仍 PENDING。 |
| Security boundary | 通过 | Artifact plan 明确不保存 `.env`、API key、secret、passphrase、token、cookie、private key、credential material、raw request / response 或 data rows；LIVE DISABLED，AI NOT STARTED，DH runtime NOT INTEGRATED。 |

Review decision: PLAN READY FOR REVIEW。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2B-PLAN-REVIEW`，或评审接受后的 `NQ-CI-POSTGRES-FLYWAY-2B-IMPL`；不得混入 2C/2D/2E、Batch 3-5、LIVE、AI、DH runtime 或真实交易所路径。

## NQ-CI-POSTGRES-FLYWAY-2A-FREEZE-REVIEW 验证记录（2026-06-14）

本轮是 GateK docs-only / CI freeze review：只冻结 Batch 2A PostgreSQL / Flyway empty DB migration smoke baseline，不修改 workflow、Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git fetch origin` / `git pull --ff-only origin dev` | 通过 | 本地 `dev` 已同步到 `origin/dev`，包含前端 PR #1 与 PR #2 合并后的文档事实源。 |
| First-run review commit | 通过 | 已提交 `docs(gatek): confirm PostgreSQL Flyway CI first green run`，只包含允许的 5 个 `docs/current` 文件。 |
| GitHub Actions run `27501253175` | 通过 | `NQ CI Baseline` completed / success；`postgres-flyway` job completed / success。 |
| Flyway V1-V31 review | 通过 | 日志证据显示 empty DB 从 V1 迁移到 V31，并 `Successfully validated 31 migrations`。 |
| No baseline / clean boundary | 通过 | Workflow 使用 `baselineOnMigrate(false)`、`cleanDisabled(true)`；未发现 `cleanDisabled(false)`。 |
| Seed / context boundary | 通过 | 未插入 legacy account seed / test fixture seed / real account seed / real exchange seed；未启动 `nq-app` full context，未触发 `AuthSeedConfiguration`。 |
| Expansion boundary | 通过 | 未跑 repository real DB smoke、frontend E2E 或 Testcontainers；Batch 2B/2C/2D/2E 仍 NOT STARTED，Batch 3-5 仍 PENDING。 |
| Security boundary | 通过 | 未注入真实交易所 credential，未开启 LIVE，未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken；AI NOT STARTED，DH runtime NOT INTEGRATED。 |

Review decision: PASS / FROZEN / ACCEPTED。Batch 2A 已冻结为当前 `dev` 的 PostgreSQL / Flyway empty DB migration smoke baseline。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2B-PLAN`。

## NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW 验证记录（2026-06-14）

本轮是 GateK CI Batch 2A first-run review：只复核 GitHub Actions `postgres-flyway` 首次运行结果，并同步允许的 `docs/current` 文档。未修改 `.github/workflows/ci.yml`、Java / TypeScript / Python 代码、测试代码、migration、backend 生产逻辑、frontend、research、scripts 或 deploy。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run | 通过 | `NQ CI Baseline` run `27501253175`，`push` to `dev`，commit `7836640ebae46d6fc62771611f5215661b3267dc`，completed / success。 |
| `postgres-flyway` job | 通过 | Job `PostgreSQL / Flyway smoke` / id `81284424653` completed / success；`Initialize containers`、`Prepare Flyway runtime classpath`、`Run empty database Flyway smoke` 均 success。 |
| Flyway empty DB smoke | 通过 | 日志显示 `Schema history table ... does not exist yet`、`Current version ... << Empty Schema >>`、V1-V31 逐版 migration、`Successfully applied 31 migrations ... now at version v31`。 |
| Flyway validate | 通过 | 日志显示 migration 前后均有 validate，最终 `Successfully validated 31 migrations`。 |
| `flyway_schema_history` | 通过 | 日志输出 `installed_rank|version|description|type|script|checksum|success`，覆盖 row 1/V1 到 row 31/V31，且 success 均为 `true`。 |
| Batch 2A smoke marker | 通过 | 日志输出 `Flyway empty database smoke reached V31`。 |
| No baseline / clean boundary | 通过 | Workflow 静态复核为 `baselineOnMigrate(false)`、`cleanDisabled(true)`；未发现 `cleanDisabled(false)`。 |
| No seed boundary | 通过 | `postgres-flyway` job 未插入 legacy account seed、test fixture seed、真实账户 seed 或真实交易所 seed；Batch 1 backend seed watcher 未进入该 job。 |
| No app / repository / E2E expansion | 通过 | `postgres-flyway` job 未启动 `nq-app` full context，未触发 `AuthSeedConfiguration`，未跑 repository real DB smoke，未跑 frontend E2E。 |
| No Testcontainers / skip / continue-on-error | 通过 | Workflow 未启用 Testcontainers，未使用 `continue-on-error`，未用 skip 伪装通过，未使用 `skipTests`。 |
| Security boundary | 通过 | Workflow 仅使用 CI-only PostgreSQL service env；未注入真实交易所 credential，未开启 LIVE，未访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。 |
| `git status --short` | 通过 | first-run review 编辑前工作区干净；编辑后仅包含允许的 `docs/current` 文档。 |
| `git diff --check` | 通过 | 未发现 whitespace error。 |
| `git diff --stat` | 已检查 | first-run review 只同步 current docs。 |
| `git show --stat --oneline --name-only HEAD` | 已检查 | HEAD 为 `7836640e ci(gatek): add PostgreSQL Flyway migration smoke`，包含 `.github/workflows/ci.yml` 与允许的 current docs。 |
| forbidden-area diff | 通过 | `git diff -- backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` 均为空。 |
| keyword scan | 已执行 | `rg "continue-on-error|skipTests|baselineOnMigrate|cleanDisabled\(false\)|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current` 已执行；workflow 仅命中 `baselineOnMigrate(false)`，docs 命中为历史记录、安全边界或禁止项说明。 |

Review decision: PASS / ACCEPTED。Batch 2A 可冻结为 PostgreSQL / Flyway empty DB migration smoke baseline。下一步只能是 `NQ-CI-POSTGRES-FLYWAY-2A-FREEZE-REVIEW` 或 `NQ-CI-POSTGRES-FLYWAY-2B-PLAN`。

## NQ-CI-POSTGRES-FLYWAY-2A-IMPL 验证记录（2026-06-14）

本轮是 GateK CI Batch 2A implementation：只修改 `.github/workflows/ci.yml` 新增 `postgres-flyway` job，并同步 current docs。Batch 2A 只覆盖 PostgreSQL service + Flyway empty DB V1-V31 migration smoke；未实现 Batch 2B schema artifact/docs、Batch 2C repository real PostgreSQL smoke、Batch 2D `nq-app` context smoke、Batch 2E seed watcher cleanup、Batch 3 no-outbound guard、Batch 4 security guard / secret scan、Batch 5 frontend E2E hardening。

本轮 implementation 当时未运行 GitHub Actions 本体，`postgres-flyway` first CI run 当时 pending；该 pending 状态已由 `NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW` 关闭。未运行 backend full Maven test、frontend build / E2E、Python pytest / mypy / ruff；原因是本轮未修改 Java / TypeScript / Python / test / migration / backend production code。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 确认工作区变更仅限 `.github/workflows/ci.yml` 与允许的 `docs/current` 文件。 |
| `git diff --check` | 通过 | 未发现 whitespace error。 |
| `git diff --stat` | 已检查 | 变更集中在 CI workflow 与 current docs。 |
| `git diff -- backend` | 通过 | 输出为空，未改 backend Java / resources / tests。 |
| `git diff -- frontend` | 通过 | 输出为空，未改 frontend。 |
| `git diff -- research` | 通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 通过 | 输出为空，未改 deploy。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| Workflow boundary review | 通过 | 新增 `postgres-flyway` job 使用 `postgres:16`、`nq_ci` / `nq_ci_user` / `nq_ci_password`、Java 21、Maven cache；通过临时 Java smoke runner 调用 Flyway `migrate` + `validate`，校验 current version 为 V31 并打印 `flyway_schema_history`。 |
| `mvn -f backend/pom.xml -pl nq-app -am process-classes org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-DincludeScope=runtime" "-Dmdep.outputFile=target/flyway-classpath.txt"` | 通过 | 23 个 reactor module `SUCCESS`，生成 `backend/nq-app/target/flyway-classpath.txt`；该命令只准备 classpath / resources，不启动 PostgreSQL、不运行 tests、不启动 app context。首次未加 PowerShell 引号的本地干跑失败为 shell 参数解析问题，workflow bash 命令不受影响。 |
| Seed boundary review | 通过 | `postgres-flyway` 不插入 legacy account seed、test fixture seed、real account seed 或 real exchange seed；不依赖 Batch 1 CI-only seed watcher。 |
| App context / repository boundary review | 通过 | `postgres-flyway` 不启动 `nq-app` full context，不运行 `@SpringBootTest`，不触发 `AuthSeedConfiguration`，不跑 repository real PostgreSQL smoke。 |
| Testcontainers / Flyway safety review | 通过 | 未启用 Testcontainers；未使用 `baselineOnMigrate`；未运行 Flyway `clean`；未设置 `continue-on-error`。 |
| Security keyword scan | 已执行 | `rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current` 已执行；命中项用于边界复核，workflow 未注入真实交易所 credential，未开启 LIVE，未加入 Batch 3/4/5。 |
| Workflow lint | 未执行 | 本机未安装 `actionlint`，Ruby 不可用，系统 Python 与 Codex bundled Python 均无 PyYAML，bundled Node 未发现 `yaml` / `js-yaml`；本轮未伪造 workflow lint 通过，语法仍以 GitHub Actions first run 为准。 |

边界确认：

- Batch 2A implemented；first CI run 当时 pending，已由 `NQ-CI-POSTGRES-FLYWAY-2A-FIRST-RUN-REVIEW` 关闭。
- 未修改 Java / TypeScript / Python / test code。
- 未新增 API，未新增 migration，未修改历史 migration。
- 未修改 backend 生产逻辑、frontend、research、scripts、deploy。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。

## NQ-CI-POSTGRES-FLYWAY-PLAN 验证记录（2026-06-14）

本轮是 GateK CI Batch 2 planning-only / docs-only：只新增 `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md` 并同步 current docs 入口，不修改 `.github/workflows/ci.yml`，不修改 backend、frontend、research、scripts、deploy、测试代码、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；这些命令不适用于只写 Batch 2 方案的文档轮次。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 预检通过 | 编辑前工作树为空。 |
| `git diff --check` | 预检通过 | 编辑前无 whitespace error。 |
| `git diff --stat` | 预检已执行 | 编辑前无 tracked diff。 |
| `git ls-files .github` | 已检查 | 当前 tracked `.github` 包含 `CODEOWNERS`、`pull_request_template.md`、`workflows/ci.yml`。 |
| `git ls-files "backend/**/db/migration/**"` | 已检查 | 当前最大 migration 为 `V31__schema_credential_permission_probe.sql`。 |
| `git ls-files "backend/**/src/test/**"` | 已检查 | 确认 backend test tree；`nq-app` 存在 local profile Spring context tests，`nq-infra` repository tests 多为 Recording / mock JDBC。 |
| `git ls-files "backend/**/application*.yml" "backend/**/application*.yaml" "backend/**/application*.properties"` | 已检查 | 当前 application configs 位于 `backend/nq-app/src/main/resources/`；local profile PostgreSQL + Flyway enabled，test profile PostgreSQL placeholder + Flyway disabled。 |
| `git diff -- backend` | 预检通过 | 输出为空，未改 backend。 |
| `git diff -- frontend` | 预检通过 | 输出为空，未改 frontend。 |
| `git diff -- research` | 预检通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 预检通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 预检通过 | 输出为空，未改 deploy。 |
| `git diff -- .github` | 预检通过 | 输出为空，未改 workflow。 |
| `git diff -- backend/**/db/migration` | 预检通过 | 输出为空，未新增或修改 migration。 |
| Broad PostgreSQL/Flyway scan | 已执行 | 按用户指定 `rg` 执行；该 broad scan 会命中 `backend/target` 生成报告，后续证据提取已用排除 `target/build/dist` 的版本复跑。 |
| Security keyword scan | 已执行 | 命中项均为禁止说明、字段名、fake fixture、历史记录或 no-real boundary；本轮未读取或输出真实 credential material。 |

边界确认：

- Batch 2 只写为 planning documented，implementation not started。
- 未修改 `.github/workflows/ci.yml`。
- 未修改 backend / frontend / research / scripts / deploy。
- 未新增 API、migration 或测试。
- 未开启 LIVE，未接 AI，未接 DH runtime。
- 未实现 NQ RealClient、真实 Provider、真实 OKX/Binance permission probe adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。

## GATEK-ARCHITECTURE-BASELINE-REVIEW 验证记录（2026-06-14）

本轮为 GateK review-only / docs-only：只审查 architecture baseline、module boundary、test baseline、docs/facts 和 security baseline，并新增 / 同步文档。未修改 backend、frontend、research、scripts、deploy、测试代码、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；验收以只读审查、Git diff、forbidden-area diff、阶段措辞和敏感边界检查为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 只包含允许的 README / docs/current 文档变更；新增 `docs/current/GATEK_ARCHITECTURE_BASELINE_REVIEW.md` 由 status 确认。 |
| `git diff --check` | 通过 | 退出码 0；仅输出既有 Windows 工作区 LF/CRLF 提示，无 whitespace error。 |
| `git diff --stat` | 已检查 | tracked diff 仅覆盖 README / docs/current 文档；Git 默认不统计 untracked 新报告文件。 |
| `git diff -- backend` | 通过 | 输出为空，未改后端。 |
| `git diff -- frontend` | 通过 | 输出为空，未改前端。 |
| `git diff -- research` | 通过 | 输出为空，未改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改脚本。 |
| `git diff -- deploy` | 通过 | 输出为空，未改部署目录。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `git ls-files backend/frontend/research/.github/deploy/scripts` | 已检查 | backend/frontend/research/deploy/scripts 结构符合当前基线；`.github/workflows` 当前无 tracked workflow。 |
| Backend boundary scan | 已检查 | `nq-core` / `nq-api` main code 未命中 JDBC / infra 直接依赖；`nq-api` SQL literal 抽查为空；ArchUnit boundary tests 已存在。 |
| Frontend stack scan | 已检查 | `package.json` 维持 React / Vite / Ant Design / TanStack Query / Axios / Zustand / Playwright；未发现 shadcn / Tailwind 体系接入。 |
| Research baseline scan | 已检查 | `research/py/pyproject.toml` 维持 pytest / mypy / ruff dev baseline；README 明确不作为 Java / Python runtime bridge。 |
| Stage wording scan | 已检查 | `rg "GateK implementation|AI started|DH integrated|LIVE enabled|real adapter implemented" README.md AGENTS.md CLAUDE.md docs/current` 命中均为否定式、禁止说明、风险说明或历史语境。 |
| Security / no-outbound scan | 已检查 | Permission probe freeze review、OKX bootstrap no-outbound review、Integration-0 docs 均保持 no-real / no-runtime / no-LIVE 边界；未读取或输出真实 credential material。 |

边界确认：

- 未修改 Java / TypeScript / Python / 测试代码 / 部署脚本 / migration。
- 未新增 API / migration。
- 未启动 GateK implementation / AI / DH runtime / LIVE / real adapter。
- 未调用真实交易所，未下单、撤单、转账、提现。
- 未读取、打印、复制或输出真实 credential material。

## NQ-CREDENTIAL-PERMISSION-PROBE-FREEZE-P3-CLEANUP 验证记录（2026-06-14）

本轮为 P3 cleanup：只修复 NoReal fake result 的 `requestId` / `traceId` 字段质量，并收口 permission probe 文档层级。未新增功能、API、migration、前端、Python 或部署脚本；未接真实交易所、AI、DH 或 LIVE。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-infra,nq-core,nq-api,nq-app -am test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `git status --short` | 通过 | 只包含本轮允许范围文件；另有进入本轮前已存在的 `docs/current/API.md` 与 `docs/current/DB_SCHEMA.md` GateI 归档链接修正，本轮保留且未回退。 |
| `git diff --check` | 通过 | 退出码 0；仅 Git LF/CRLF 工作区提示。 |
| `git diff --stat` | 已执行 | diff 只覆盖允许的 NoReal port、NoReal unit test、README 和 docs/current 文档。 |
| `git diff -- backend/nq-infra/src/main/resources/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `git diff -- frontend` | 通过 | 输出为空，未修改前端。 |
| `git diff -- research` | 通过 | 输出为空，未修改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未修改脚本或部署入口。 |
| `rg "permission probe implemented|real exchange permission probe|OKX permission probe adapter|Binance permission probe adapter" docs/current README.md` | 已检查 | 命中均为 guarded baseline、NOT IMPLEMENTED、future review 或历史证据说明；未把真实交易所 adapter 写成 implemented。 |
| `rg "GateK implementation|AI started|DH integrated|LIVE enabled" docs/current README.md` | 已检查 | 命中均为否定式、禁止说明或“not started / not integrated / disabled”口径。 |
| `rg "apiKey|secret|passphrase|private key|mnemonic|signature|headers|raw response" docs/current backend/nq-infra/src/main/java backend/nq-infra/src/test/java` | 已检查 | 命中均为敏感信息禁入说明、脱敏边界、测试护栏或既有配置字段名；本轮未新增真实 credential material。 |

边界预期：

- NoReal port requestId 与 traceId 不再混同。
- NoReal port 仍不创建 HTTP client、不访问 OKX/Binance、不下单、不撤单、不转账、不提现。
- 文档当前状态统一：guarded backend implementation FROZEN / ACCEPTED；real exchange adapter NOT IMPLEMENTED；default behavior 为 NoReal `SKIPPED`；LIVE probe DISABLED / REJECTED。

## NQ-CREDENTIAL-PERMISSION-PROBE-FREEZE-REVIEW 验证记录（2026-06-14）

本轮只做 credential permission probe no-real-exchange / guarded backend freeze review 和文档同步；未修改 Java、测试代码、migration、API 语义、前端、Python 或部署脚本。冻结口径：permission probe guarded backend implementation FROZEN / ACCEPTED；real exchange permission probe adapter NOT IMPLEMENTED；默认 runtime 行为为 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`；LIVE credential probe DISABLED / REJECTED；AI / DH / LIVE NOT STARTED。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 审查开始前为空；文档同步后仅包含本轮允许的 docs/current / README 文档变更。 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app,nq-adapter-okx,nq-adapter-binance -am test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 backend reactor module `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `git diff --check` | 通过 | 无 whitespace error；如出现 LF/CRLF 提示，仅为 Git 行尾转换提示。 |
| `git diff --stat` | 已执行 | 仅统计本轮允许的文档变更。 |
| `git diff -- backend/nq-infra/src/main/resources/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `git diff -- frontend` | 通过 | 输出为空，未修改前端。 |
| `git diff -- research` | 通过 | 输出为空，未修改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未修改脚本或部署入口。 |

边界扫描：

- P0/P1=0；P2 无阻塞项；P3 仅保留 NoReal port requestId / traceId 混同和文档 gate 顺序轻微差异。
- no-real-exchange 证据充分：默认 bean 为 `NoRealExchangeCredentialPermissionProbePort`；NoReal test 使用 `ProxySelector` guard；Service tests 覆盖 LIVE/inactive/non-ACTIVE/Paper gate/withdraw risk/latest no-port；WebMvc tests 覆盖 response 脱敏和 request body 拒绝 credential material；adapter boundary tests 只覆盖错误分类和 forbidden endpoint，不实现真实 HTTP adapter。
- 未调用真实交易所；未实现真实 OKX/Binance permission probe adapter；未读取或输出真实 credential material。
- 阶段措辞保持 GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE disabled。

## NQ-FRONTEND-LOGIN-PAGE-PROFESSIONALIZATION 验证记录（2026-06-13）

本轮只改登录页、登录相关 E2E 和当前验证文档；未修改 backend、API、鉴权逻辑、token 存储、migration、deploy、scripts、Paper Trading、Dashboard、Backtest、Strategy、Risk、AI、DH 或 LIVE 交易逻辑。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build` | 通过 | frontend 下执行，`tsc -b && vite build` 成功；仍有既有 Vite chunk > 500 kB 警告。 |
| `npm run test:e2e -- tests/e2e/login-page-smoke.spec.ts --project=chromium` | 通过 | 新增登录页 smoke 单独通过，1 passed；验证登录页关键文案、Gate/LIVE/PAPER 状态、安全提示和空凭证输入。 |
| `npm run test:e2e` | 通过 | frontend 下执行完整 E2E，25 passed / 1 skipped；唯一 skipped 仍为未配置订单 ID 的既有订单详情链路。 |
| 后端本地启动 | 通过 | 首次按 Runbook `-pl nq-app` 启动失败，因本地 Maven 仓缺少 reactor 模块产物；改用 `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` 启动，`/actuator/health` 返回 `UP`。 |
| Browser 运行态验证 | 通过（降级） | Product Design Browser 初始化连续超时；按降级规则使用 Playwright browser 工具打开 `http://127.0.0.1:5179/login`，桌面 1440x900 与移动 390x844 均无水平溢出，登录卡片、安全提示和 Gate/LIVE/PAPER 文案可见。 |
| `git diff --check` | 通过 | 退出码 0，无 whitespace error；仅有 LF/CRLF 工作区提示。 |
| `git status --short` | 通过 | 工作区仅包含本轮允许范围文件：登录页、全局登录样式、登录 E2E helper、新增登录页 smoke、`WORKLOG.md`、`TESTING.md`。 |
| `git diff --stat` | 已执行 | 当前 tracked diff 统计为 5 个文件；Git 默认不统计 untracked 文件，新增 `frontend/tests/e2e/login-page-smoke.spec.ts` 由 `git status --short` 确认。 |

补充说明：

- 完整 E2E 输出仍包含既有 Ant Design React 19 compatibility、`Card.bordered`、`Modal.destroyOnClose`、`useForm` 和 `Descriptions` span 警告；本轮登录页已将新增 Card 改为 `variant="borderless"`，未新增登录页 `bordered` 警告。
- 本轮未执行 Maven / Python 全量验证；原因是未修改 backend / Python 代码。本轮为 E2E 临时启动过后端 local profile，并在验证后停止本轮启动的 `nq-app` 与 Vite 进程。

## NQ-FRONTEND-PAPER-TRADING-CONSOLE-DEEPEN 验证记录（2026-06-13）

- `npm run build`（frontend，含 `tsc -b`）：通过。
- `git diff --check`：通过（仅 LF/CRLF 行尾提示，无空白错误）。
- `npm run test:e2e`（本地拉起 `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run` + 本地 PostgreSQL 5432）：**24 passed / 1 skipped / 0 failed**，与上一轮基线一致，未倒退。
  - 7 个 paper-trading spec 全部通过，覆盖创建/启动/停止、订单/成交/持仓/快照、风控 run-once、资金/持仓曲线、交易复盘、紧急停机、告警 ACK/RESOLVE、日报生成、调度创建/执行一次/禁用、心跳、恢复/重试/监控守护、稳定性验收。
  - 迭代中修复两处与本轮重构直接相关的失败：
    1. 行内按钮被 `position:sticky` 页头拦截点击 → 给左侧 run 列表加内部滚动 `scroll={{y:420}}`，定位时滚动表体而非窗口。
    2. 顶部状态条新增展示风控 checkType 导致 `BASIC_HEALTH_CHECK` 多匹配 → spec 改 `.first()`。
- 视觉冒烟：Playwright 截图确认内联控制台（顶部状态条 / 左列表焦点高亮 / 中部曲线与日报 / 右侧操作区与告警面板）渲染正常。
- 后端 / Python：未跑（本轮未改 backend/python 代码）。

## NQ-FRONTEND-DESIGN-SYSTEM-V1-AND-TRADING-UI-REFACTOR 验证记录（2026-06-13）

- `npm install echarts`（frontend）：通过，新增 echarts ^6.1.0，lock 同步更新。
- `npm run build`（frontend，含 `tsc -b` 类型检查）：通过（vite 8 构建成功；chunk >500kB 警告为 echarts 体积所致，构建前已存在同类警告基线）。
- `npm run typecheck` / `npm run lint`：脚本不存在（package.json 未定义），类型检查由 `npm run build` 内的 `tsc -b` 覆盖。
- `npm run test:e2e`（本地拉起 `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run` + 本地 PostgreSQL 5432）：**24 passed / 1 skipped / 0 failed**。
  - 全部 7 个 paper-trading spec、dashboard smoke、strategies / research / backtests / evaluations / publishes / accounts / trading-workbench spec 通过，证明本轮 UI 重构未破坏既有交互契约。
  - 前置修复：`tests/e2e/support.ts` 登录 fixture 自 288c28f8（2026-05-28）起断裂（登录文案改为 "NexusQuant 控制台"/"登录" 且移除表单凭证预填，fixture 未同步），修复前 24 个用例全部在登录步骤失败。
  - 原存量 2 个失败：`marketdata-dataset-smoke` / `marketdata-ingestion-smoke`，根因为 dc1288e0（2026-05-29）给 Marketdata 表单加 开始/结束时间 必填规则但未同步 spec（spec 未填日期，提交被表单校验拦截）。已通过同步 DatePicker 必填输入修复；未降低页面校验，未跳过测试（只改两个 spec，未改 MarketdataPage 业务代码）。
- 视觉冒烟：Playwright 截图验证登录页与 Dashboard 深色主题、安全横幅、指标条、空态渲染正常。
- 后端 / Python：未跑（本轮未改 backend/python 代码）。

## NQ-CREDENTIAL-PERMISSION-PROBE-CODE-API-TEST-DESIGN-REVIEW 验证记录（2026-06-12）

本轮只读审计 credential permission probe code/API/test 实现方案，新增设计审计报告并同步 README/WORKLOG/TESTING/plan 状态。未修改 Java、Repository、Service、Controller、DTO、API、migration、前端、Python 或部署脚本；未调用真实交易所；未实现 permission probe。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过，含既有非本轮改动 | 当前命中本轮允许文档：`README.md`、`docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`、`docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`、`docs/current/README.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、新增 `docs/current/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`；另有预检时已存在的 `backend/nq-adapter-binance/.../BinanceFiltersCacheTest.java`，本轮未触碰或回退。 |
| `git diff --check` | 通过 | 退出码 0，无 whitespace error；仅有 Git 将 LF 转为 CRLF 的工作区提示。 |
| `git diff --stat` | 通过，含既有非本轮改动 | 当前工作区总 stat 包含 7 个 tracked 文件、96 insertions / 6 deletions；其中 `BinanceFiltersCacheTest.java` 为预检时已存在的非本轮 Java 改动；新增报告文件未 staged，因此不出现在 `git diff --stat` 中，由 `git status --short` 确认。 |
| Maven / frontend / Python | 未执行 | docs-only；未修改业务代码、测试代码、配置、migration、前端、Python 或部署脚本，不把未执行测试写成通过。 |
| 真实交易所调用 | 未执行 | 本轮未调用 OKX、Binance、Bybit、Gate 或任何真实交易所；未读取或输出真实密钥。 |

## NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-FIX 验证记录（2026-06-12）

本轮修复 OKX instruments cache 构造期 eager refresh，补充 no-outbound 回归测试，并同步审计报告状态。未新增 migration，未修改前端、Python 或部署脚本，未调用真实交易所，未接 AI / DH / LIVE。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-adapter-okx,nq-app -am test` | 通过 | `BUILD SUCCESS`；`nq-adapter-okx` 27 tests / 0 failures；`nq-app` 52 tests / 0 failures；新增 no-outbound app context 测试通过。 |
| `git diff --check` | 通过 | 退出码 0，无 whitespace error；仅有 Git 将 LF 转为 CRLF 的工作区提示。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 backend module 全部 `SUCCESS`，`BUILD SUCCESS`；`nq-app` 52 tests / 0 failures；总耗时 02:43。 |
| migration diff 检查 | 通过 | `git diff --name-only -- backend/nq-infra/src/main/resources/db/migration` 无输出。 |
| frontend diff 检查 | 通过 | `git diff --name-only -- frontend` 无输出。 |
| research diff 检查 | 通过 | `git diff --name-only -- research` 无输出。 |
| deploy scripts diff 检查 | 通过 | `git diff --name-only -- scripts` 无输出；未修改部署脚本。 |
| no-outbound 证据 | 通过 | `OkxInstrumentsCacheTest` / `OkxExchangeAdapterBootstrapNoOutboundTest` 用 fake client/server 证明构造期 0 次 public GET、首次显式读取才刷新；`OkxBootstrapNoOutboundLocalContextTest` 用 `ProxySelector` 探针证明 local Spring context 启动期访问 `www.okx.com` public instruments 次数为 0，且日志不含 `okx_adapter_bootstrap_fallback_enabled`。 |
| 日志 / surefire 报告关键字扫描 | 通过 | 未命中 `okx_adapter_bootstrap_fallback_enabled`、`www.okx.com/api/v5/public/instruments` 或 `api/v5/public/instruments?instType=SPOT`。 |
| 真实交易所调用 | 未执行 | 本轮测试不依赖真实 OKX/Binance 网络，不读取或输出真实密钥。 |

## NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW-DOC 验证记录（2026-06-12）

本轮只将 OKX bootstrap no-outbound 只读审计结论落到 `docs/current`，新增 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md` 并更新 README/WORKLOG/TESTING 索引。未修改 Java、配置、migration、测试、frontend、Python 或部署脚本，未调用 OKX、Binance 或任何真实交易所，未实现 fix。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 退出码 0，无 whitespace error；仅有 Git 将 LF 转为 CRLF 的工作区提示。 |
| `git diff --stat` | 通过 | 已跟踪 diff 集中在 `README.md`、`docs/current/README.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`；新增报告文件因未 staged 不在该命令统计中，由 `git status --short` 单独确认。 |
| `git status --short` | 通过 | 仅命中允许范围：4 个 Markdown 修改文件 + 1 个新增 `docs/current/NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md`。 |
| 全量测试 | 未执行 | docs-only；未修改业务代码、测试代码、配置、migration、frontend、Python 或部署脚本。 |
| 真实交易所调用 | 未执行 | 本轮未调用 OKX、Binance 或任何真实交易所；未读取或输出真实密钥。 |

## NQ-DH-INTEGRATION0-SAFETY-GATE-CLOSE 验证记录（2026-06-12）

本轮只做 Integration-0 safety gate close / acceptance report（新增 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md` 并更新 STATUS/README/ROADMAP/WORKLOG/TESTING），未修改任何 Java、测试代码、frontend、Python、API、migration 或部署脚本，故本轮未运行全量测试，验收依据引用上一轮已通过结果。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 仅命中本轮新增/修改的 `docs/current` Markdown。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git diff --stat` | 通过 | 改动集中在 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md` 与 STATUS/README/ROADMAP/WORKLOG/TESTING。 |
| 全量测试 | 未执行 | docs-only；未改业务/测试代码；引用上一轮 `mvn -f backend/pom.xml test` BUILD SUCCESS（nq-app 51 tests / 0 failures，Integration-0 16 passed，ArchUnit 全绿）作为验收依据。 |
| 验收口径检查 | 通过 | Integration-0 = PASS/CLOSED/ACCEPTED；Runtime integration / Integration-1 / AI NOT STARTED；DH NOT INTEGRATED；LIVE DISABLED；未误写真实集成。 |

## NQ-DH-INTEGRATION0-CONTRACT-TEST-IMPL 验证记录（2026-06-12）

本轮把 Integration-0 contract test matrix（INT0-T01..T15）落成可运行测试代码与脱敏 fixture，仅新增 `backend/nq-app/src/test/**`，未修改任何 `src/main`、API、migration 或部署。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS；`nq-app` 51 tests / 0 failures / 0 errors（原 35 + 本轮 16）。 |
| `NqDhIntegration0*Test` 定向 | 通过 | 16 tests / 0 failures（ContractValidation 6 + Security 8 + NoSideEffect 2）。 |
| ArchUnit 边界 | 通过 | ModuleBoundaryArchTest / PackageBoundaryArchTest 全绿；新增 `..app.integration0..` 测试包未触碰受护栏边界。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git status --short` | 通过 | 仅命中 `backend/nq-app/src/test/**`（测试代码与 fixtures）。 |
| 生产代码边界 | 通过 | 未修改 `src/main`，未新增 API / migration / Controller / Service / Repository / DTO / RealClient / 真实 Provider。 |
| 真实通道边界 | 通过 | 未做真实 HTTP / 真实 NQ / 真实交易所；未读取真实密钥（固定假值）；未开启 LIVE。 |

说明：nonce store 为 test-only 内存实现；Integration-1 前必须补持久化 nonce、rate limit、memory cap（DH P1-4 residual），不在本轮范围。

## NQ-DH-INTEGRATION0-MOCK-CONTRACT-TEST-DESIGN 验证记录（2026-06-11）

本轮将 Integration-0 已冻结的 15 项 contract test 拆成详细矩阵（每项 16 字段）+ 共享 fixture + forbidden side-effect checklist + 验收/blocker 清单 + 下一步代码任务草案，写入 `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md` 并更新 README/ROADMAP/WORKLOG/TESTING。本轮**只做设计不写测试代码**，未修改 Java、frontend、Python、API、migration、测试代码或部署脚本，故未运行全量测试。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 仅命中本轮修改的事实源 Markdown 文件。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git diff --stat` | 通过 | 改动集中在 `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md` 与 README/ROADMAP/WORKLOG/TESTING。 |
| 全量测试 | 未执行 | docs + contract test design only，未写测试代码、未改业务代码/API/migration/部署。 |
| 代码文件创建检查 | 通过 | `futureCodeLocationSuggestion` 仅为建议路径，未创建任何 `.java` 或测试代码文件。 |
| 集成/口径边界检查 | 通过 | 未实现集成、未接真实 HTTP/RealClient/Provider、未开启 LIVE；未把本轮写成 implemented，未把 Integration-0 写成真实集成。 |

## NQ-DH-INTEGRATION-0-CONTRACT-FREEZE 验证记录（2026-06-11）

本轮只做 Integration-0 契约冻结与安全策略 / contract test 计划文档，新增 3 份 `NQ_DH_INTEGRATION0_*.md` 并更新 README/ROADMAP/WORKLOG/TESTING；未修改任何 Java、frontend、Python、API、migration、测试或部署代码，故未运行全量测试（符合 AGENTS.md「只改文档可不跑全量测试，须写清未跑原因」规则）。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 仅命中本轮新增/修改的事实源 Markdown 文件。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git diff --stat` | 通过 | 改动集中在 `docs/current/NQ_DH_INTEGRATION0_*.md` 与 README/ROADMAP/WORKLOG/TESTING。 |
| 全量测试 | 未执行 | 本轮 docs + contract design only，未修改业务代码、API、migration、测试或部署脚本。 |
| 集成边界检查 | 通过 | 未实现集成；未新增 RealClient / 真实 Provider / 真实 HTTP；未做真实联调；未开启 LIVE。 |
| 阶段口径检查 | 通过 | 未把本轮写成 implemented；未把 Integration-0 写成真实集成；未把 DH 写成 integrated；未把 AI 写成 started；未把 LIVE 写成 enabled。 |

## DOC-SYNC-GATEK-PRE-AND-INT0-REGISTRATION 验证记录（2026-06-11）

本轮只同步 NQ / DH 三轮审计结论与阶段事实到事实源文档，未修改任何 Java、前端、Python、部署、API、migration 或测试代码，故未运行全量测试（符合 AGENTS.md「只改文档可不跑全量测试，但须写清未跑原因」规则）。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 通过 | 仅命中本轮同步的事实源 Markdown 文件。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `git diff --stat` | 通过 | 改动集中在 NQ `CLAUDE.md` / `AGENTS.md` / `docs/current/{STATUS,README,ROADMAP,WORKLOG,TESTING}.md`。 |
| 全量测试 | 未执行 | 本轮 docs-only，未修改业务代码、API、migration、测试或部署脚本。 |
| 阶段口径检查 | 通过 | 未把 GateK-PLAN 写成 GateK implementation；未把 Integration-0 写成真实集成；未把 AI 写成 started；未把 DH 写成 integrated；未把 LIVE 写成 enabled。 |

## Credential Permission Probe Schema 验证记录（2026-06-08）

本轮新增 permission probe schema-only migration，并同步 `docs/current` 文档和 README 索引；未实现 permission probe，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本，未接 AI、DH、LIVE。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；`Total time: 02:24 min`，`Finished at: 2026-06-08T13:26:33+08:00`。 |
| Flyway migration 验证 | 通过 | Maven 中 `nq-app` local integration test 成功验证 31 个 migrations，并从 V30 迁移到 V31。 |
| migration 范围检查 | 通过 | 本轮只新增 `V31__schema_credential_permission_probe.sql`；未修改历史 migration。 |
| Java/API 范围检查 | 通过 | 本轮未修改 Java、Repository、Service、Controller、DTO、测试业务语义或新增 API。 |
| 前端/Python/部署范围检查 | 通过 | 本轮未修改 frontend、research、scripts 或部署相关路径。 |
| permission probe 实现边界 | 通过 | 未实现 permission probe，未新增 permission probe endpoint，未新增 Java enum 或 API DTO。 |
| 真实交易所触达隔离 | 有残余风险 | 本轮 migration/docs 未实现或主动调用 permission probe；但全量 Maven 中既有 `MarketdataControllerLocalIntegrationTest` 在 local profile 启动时触发 OKX public instruments bootstrap fallback，并因 `No route to host` 失败。该日志不涉及 credential/private endpoint/下单/撤单/转账/提现，但不能把本次验证写成完全零真实交易所触达尝试。 |

## Credential Permission Probe Design Review 验证记录（2026-06-08）

本轮只读设计审计真实交易所 credential permission probe，并新增设计审计文档与索引记录；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本；未调用真实交易所，未实现 permission probe。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| 允许路径范围检查 | 通过 | 本轮只修改 `docs/current` 文档和 README 索引。 |
| migration 范围检查 | 通过 | 本轮未新增 migration，未修改历史 migration。 |
| Java/API 范围检查 | 通过 | 本轮未修改 Java、Repository、Service、Controller、DTO、测试业务语义或新增 API。 |
| 前端/Python/部署范围检查 | 通过 | 本轮未修改 frontend、research、scripts 或部署相关路径。 |
| 真实交易所 / AI / DH / LIVE 边界检查 | 通过 | 未调用 OKX、Binance、Bybit、Gate 或任何真实交易所；未接 AI、DH、LIVE；未实现 permission probe。 |
| Maven 测试 | 未执行 | 本轮只做 `CODE_ANALYSIS + DOCUMENTATION`，未修改业务代码、migration、API、前端、Python 或部署脚本；不把未执行测试写成通过。 |

## DB Schema Credential Governance Doc Cleanup Batch 5-G-A 验证记录（2026-06-08）

本轮只修复 Batch 5-G freeze review 发现的 P3 文案问题：修正 credential disable endpoint OpenAPI description 的过期描述；为 Batch 5-F-A enable governance review 增加历史快照说明；同步 freeze review、README 索引、WORKLOG 和 TESTING。本轮未新增 migration，未修改 credential 业务逻辑，未修改 Repository / Service / DTO / 测试业务语义，未新增 API，未修改前端、Python 或部署脚本。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| `mvn -f backend/pom.xml -pl nq-api -am test` | 通过 | 20 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；`Total time: 03:36 min`，`Finished at: 2026-06-08T12:02:21+08:00`。 |
| migration 范围检查 | 通过 | 本轮未新增 migration，未修改历史 migration。 |
| Java/API 范围检查 | 通过 | 仅修改 `ExchangeAccountCredentialController.java` 的 OpenAPI description 文案；未修改 credential 业务逻辑、Repository、Service、DTO、测试业务语义或新增 API。 |
| 前端/Python/部署范围检查 | 通过 | 本轮未修改 frontend、research、scripts 或部署相关路径。 |
| 全量后端测试 `mvn -f backend/pom.xml test` | 未执行 | 本轮编译验证范围未因改动扩大；已按任务要求执行 `nq-api -am` 测试并通过，不把未执行的全量后端测试写成通过。 |

## DB Schema Credential Governance Freeze Review Batch 5-G 验证记录（2026-06-08）

本轮只读复核 Batch 5-A ~ 5-F-C credential governance，并新增冻结复核文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| migration 范围检查 | 通过 | 本轮未新增 migration，未修改历史 migration；只读复核 V29 / V30。 |
| Java/API 范围检查 | 通过 | 本轮未修改 Java、Repository、Service、Controller、DTO 或 API；发现一个 P3 过期 OpenAPI description，已记录到 freeze review，不在本轮修改 Java。 |
| 前端/Python/部署范围检查 | 通过 | 本轮未修改 frontend、research、scripts 或部署相关路径。 |
| credential governance 必查项 | 通过，含 P3 note | API response 脱敏、audit metadata 脱敏、lifecycle tests、active material selection、rotate/enable 状态语义、permission_scope 与 failed_auth_count 边界均通过；仅存在过期文案 P3。 |
| 后端 Maven 测试 | 未执行 | 本轮只做 `CODE_ANALYSIS + DOCUMENTATION`，未修改业务代码、migration、API、前端、Python 或部署脚本；不把本轮未执行测试写成通过。上一轮 5-F-C 的 Maven 通过记录保留在下方对应章节。 |

## DB Schema Credential Enable Command Batch 5-F-C 验证记录（2026-06-08）

本轮实现最小 credential enable command，并同步 `docs/current` 文档；未新增 migration，未修改历史 migration，未修改前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app -am test` | 通过 | 实际 reactor 覆盖 23 个后端模块，`BUILD SUCCESS`；新增关键测试包括 `ExchangeAccountCredentialCommandServiceTest` 15 tests / 0 failures、`JdbcExchangeAccountCredentialRepositoryTest` 2 tests / 0 failures、`ExchangeAccountCredentialControllerWebMvcTest` 4 tests / 0 failures。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个后端模块均为 `SUCCESS`，最终 `BUILD SUCCESS`，总耗时 `02:11 min`，完成时间 `2026-06-08T11:31:38+08:00`。 |
| `git diff --check` | 通过 | 无 whitespace error。 |
| migration 范围检查 | 通过 | 本轮未新增 migration，未修改历史 migration；Batch 5-F-C 复用 Batch 5-F-B 已准备的 `V30__schema_credential_enable_audit_event.sql`。 |
| Java/API enable 回归覆盖 | 通过 | 覆盖 `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/enable`、reason 必填、同 account + credentialType 其他 ACTIVE 冲突、`ACTIVE / REVOKED / ROTATED / EXPIRED` 拒绝、结构性校验失败保持 `DISABLED`、response 脱敏。 |
| 禁止范围检查 | 通过 | 未修改前端、Python、部署脚本；未新增真实交易所权限探活、reveal/decrypt/includeSecret endpoint、AI、DH、LIVE 或真实交易路径；未把 GateK-PLAN 写成实现已启动。 |

验证过程中的已知非本轮问题 / 既有 warning：

- Maven settings.xml 仍提示 `Unrecognised tag: 'profiles'`。
- 部分测试仍有既有 SLF4J provider、Mockito dynamic agent warning。
- `TradingVerificationControllerLocalTest.shouldReturnUnifiedInternalError` 会按测试预期触发统一 internal error 日志，测试结果仍为 0 failure。
- local profile 下 OKX adapter bootstrap 仍可能因本地网络返回 fallback warning，不影响本轮 credential enable command 测试通过结论。

## DB Schema Credential Enable Audit Event Schema Batch 5-F-B 验证记录（2026-06-08）

本轮新增 schema-only migration，为 `credential_audit_logs.event_type` CHECK 增加 `ENABLED`，并同步 `docs/current` 文档；未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无 whitespace error。 |
| migration diff 范围检查 | 通过 | 只新增 `V30__schema_credential_enable_audit_event.sql`；未修改历史 migration。 |
| Java/API 范围检查 | 通过 | 未修改 Java；只读检索未发现 credential enable endpoint 或 `enableCredential` 方法。 |
| 文档索引范围检查 | 通过 | 仅补齐 `README.md` 与 `docs/current/README.md` 中 Batch 5-F-B schema-only 当前事实索引；未写成 enable implemented。 |
| 禁止范围检查 | 通过 | 未新增 enable endpoint；未新增 rotate / revoke / disable / expire 行为；未修改前端、Python、部署；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动；未把本轮写成 enable implemented。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；后端模块测试通过。 |

## DB Schema Credential Enable Governance Review Batch 5-F-A 验证记录（2026-06-07）

本轮只读审计 credential enable / re-enable 生命周期设计，并同步 `docs/current` 文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 工作树范围检查 | 通过 | 本轮只修改 `docs/current` 文档和必要 README 索引；未修改 backend Java、API、frontend、Python 或部署脚本。 |
| migration diff 范围检查 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff；未新增 migration，未修改历史 migration。 |
| 禁止范围检查 | 通过 | 未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动；未把本轮审计写成 enable 已实现。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。 |

## DB Schema Credential Active Credential Uniqueness Review Batch 5-E-C 验证记录（2026-06-07）

本轮只读评估 active credential 唯一性模型，并同步 `docs/current` 文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| migration diff 范围检查 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff；未新增 migration，未修改历史 migration。 |
| 禁止范围检查 | 通过 | 未修改 Java、Repository、Service、Controller、DTO、API、frontend、Python 或部署脚本；未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。 |

## DB Schema Credential Active Material Deterministic Selection Batch 5-E-B 验证记录（2026-06-07）

本轮接入 deterministic active summary / active material selection：无 `credentialType` 多 ACTIVE type 返回 conflict，显式 `credentialType` 只选择对应 ACTIVE credential；未新增 migration，未修改前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api -am test` | 通过 | 相关 reactor module 均为 `SUCCESS`，`BUILD SUCCESS`；覆盖 Repository / Service / Controller active selection 回归。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，以及既有 controller local test 的预期 internal error 日志，不影响通过结论。 |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 新增 migration 范围检查 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff；未新增 migration，未修改历史 migration。 |
| 禁止范围检查 | 通过 | 未修改 frontend、Python 或部署脚本；未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动。 |
| active selection 回归覆盖 | 通过 | 覆盖单 active 兼容、多 active no-type conflict、指定 `credentialType` 查询/校验、指定不存在 type、inactive lifecycle 不可读、rotate 后同 type 只读新 credential、API response 脱敏、不依赖 `permission_scope`。 |

## DB Schema Credential Active Material Selection Review Batch 5-E-A 验证记录（2026-06-07）

本轮只读审计 credential active summary / active material 选择语义，并同步 `docs/current` 文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 工作树范围检查 | 通过 | 本轮只修改文档文件；未新增 migration，未修改 backend Java、API、frontend、Python 或部署脚本。 |
| 禁止范围检查 | 通过 | 未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易；未把 GateK-PLAN 写成实现已启动。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。 |

## DB Schema Credential Rotate Command Batch 5-D-B 验证记录（2026-06-07）

本轮实现显式 credential rotate command，并同步 `docs/current` 文档；未新增 migration，未修改前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | 初次执行因新增测试的 no-handler 断言不匹配 standalone MockMvc 行为失败；修正为反射检查无 `enable` 方法后复跑通过。最终 23 个 reactor module 均为 `SUCCESS`，`BUILD SUCCESS`。 |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 新增 migration 范围检查 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff；未新增 migration，未修改历史 migration。 |
| 禁止范围扫描 | 通过 | 未修改 frontend、Python 或部署脚本；未新增 enable endpoint；未调用真实交易所；未接 AI、DH、LIVE 或真实交易。 |
| rotate 回归覆盖 | 通过 | 覆盖 ACTIVE rotate 成功、旧 `ROTATED`、新 `ACTIVE`、old/new audit log、active material 只返回新 credential、非 ACTIVE 派生拒绝、reason 缺失/敏感词拒绝、重复 rotate 旧 credential 拒绝、API response 脱敏、audit metadata 不含敏感字段。 |

## DB Schema Credential Rotate Governance Review Batch 5-D-A 验证记录（2026-06-07）

本轮只读审计 credential rotate 生命周期设计，并同步 `docs/current` 文档；未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 工作树范围检查 | 通过 | 本轮只修改 `docs/current` 文档。 |
| 禁止范围检查 | 通过 | 未新增 migration，未修改 backend Java、API、frontend、Python 或部署脚本；未新增 rotate endpoint 或 enable endpoint；未接 AI、DH、LIVE 或真实交易。 |
| 阶段与禁写状态检查 | 通过 | 未把 GateK-PLAN 写成实现已启动，未把 AI、DH、LIVE 或 rotate 写成已启用或已实现；相关命中均为禁止项或未实现说明。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮只做 code analysis + documentation，未修改业务代码、API、migration、前端、Python 或部署脚本。 |

## DB Schema Credential Revocation Governance Batch 5-B 验证记录（2026-06-07）

本轮新增 `V29__schema_credential_revocation_governance.sql` 并同步 credential revocation / DB schema governance 文档；验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 新增 migration 范围检查 | 通过 | 本轮只新增 `backend/nq-infra/src/main/resources/db/migration/V29__schema_credential_revocation_governance.sql`，未修改历史 migration。 |
| 禁止范围扫描 | 通过 | 未修改 Java、Repository、Service、Controller、DTO、前端、Python 或部署脚本；未新增 API；未实现 revoke/rotate endpoint；未接 AI、DH、LIVE 或真实交易。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；该结果只证明当前后端测试和 Flyway 迁移装配通过，不代表 revoke/rotate 业务行为已实现。 |

## DB Schema Governance Batch 4-B 验证记录（2026-06-07）

本轮为 `research_configs` / `backtest_configs` 增加受控归档命令；未新增 migration，未修改历史 migration。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 禁止范围扫描 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff，未新增 migration；新增代码只触达 research/backtest config archive 命令、DTO、Repository、Service、Controller 和测试。文档中出现 credentials、positions、risk_events、orders/trades/ledger/audit、facts、marketdata timeseries、AI、DH、LIVE、真实交易等词均为禁止范围或历史边界说明。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，不影响本次通过结论。 |

## DB Schema Governance Batch 4-A 验证记录（2026-06-07）

本轮接管 `research_configs` / `backtest_configs` V28 status/archive 字段的 Repository 与 Service 语义；未新增 migration，未修改历史 migration。验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| 禁止范围扫描 | 通过 | `backend/nq-infra/src/main/resources/db/migration` 无 diff，未新增 migration；代码改动只触达 research/backtest 配置 domain、Repository、Service、DTO 和测试。文档中出现 credentials、positions、risk_events、orders/trades/ledger/audit、facts、marketdata timeseries、AI、DH、LIVE、真实交易等词均为禁止范围或历史边界说明。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，不影响本次通过结论。 |

## DB Schema Governance Batch 3-B 验证记录（2026-06-06）

本轮新增 `V28__schema_research_backtest_config_governance.sql` 并同步 DB schema governance 文档；验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| V28 禁止范围扫描 | 通过 | 新 migration 未命中禁止表名、AI、DH、LIVE、真实交易、逻辑删除或 retention purge 相关结构变更；只命中两张目标配置表自身的约束名。 |
| `mvn -f backend/pom.xml test` | 通过 | 23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`；测试过程仍有既有 SLF4J provider、Mockito dynamic agent warning，不影响本次通过结论。 |

## DB Schema Governance Batch 3-A 验证记录（2026-06-06）

本轮新增 `V27__schema_master_table_governance.sql` 并同步 DB schema governance 文档；验证结论以本节命令实际结果为准。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出 Windows 换行提示，无 whitespace error。 |
| V27 禁止范围扫描 | 通过 | 未命中禁止表、事件、时序、AI、DH、真实交易、逻辑删除或 retention 相关结构变更。 |
| `mvn -f backend/pom.xml test` | 初次失败后修复重跑通过 | 初次在 `nq-app` 暴露既有 package/path 不一致问题；已修复 `TradingMaintenanceService`、`ManualStrategyTriggerGateway`、`OrderCommandStrategyExecutionGateway` 的 package/import。 |
| `mvn -f backend/pom.xml clean test` | 通过 | 清理旧 package 残留 class 后，23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。 |
| `mvn -f backend/pom.xml test` | 通过 | 修复后按用户要求重跑原命令，23 个 reactor module 均为 `SUCCESS`，最终 `BUILD SUCCESS`。 |

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区包含本次 docs/config 修改与 `git mv` 归档，详见 `WORKLOG.md` |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；local integration 日志确认连接 `jdbc:postgresql://localhost:5432/nexus_quant` |
| `npm ci` | 通过 | 首次因 `D:\Tool\NodeJs\node_cache` 写入权限/占用失败；提权重跑后成功安装 177 packages；`npm audit` 提示 4 个漏洞（2 moderate、2 high），本任务未执行 `npm audit fix` |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；Vite 提示 bundle chunk 超过 500 kB，属于既有构建体积风险 |
| `npm run test:e2e` | 通过 | BASELINE-FIX-2 后通过；8 个 Playwright 用例中 5 passed、3 skipped。E2E runner 会启动 Vite、设置外部 dev server 模式、运行 Playwright、最后停止 Vite |
| `python -m pip install -e ".[dev]"` | 未在当前环境完成 | 已在 `pyproject.toml` 补充 dev extras；当前本机 editable install 两次卡在 build/editable 阶段超时。为完成当前验证，使用等价工具安装命令补齐当前用户环境 |
| `python -m pip install pytest mypy ruff` | 通过 | 提权执行成功；下载较慢并发生断点续传，最终安装 `pytest-9.0.3`、`mypy-2.1.0`、`ruff-0.15.13` |
| `python -m pytest -q` | 通过 | `2 passed in 0.01s` |
| `python -m mypy src` | 通过 | `Success: no issues found in 8 source files` |
| `python -m ruff check .` | 通过 | `All checks passed!` |
| 本地启动验证 | 通过 | `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` 启动成功；`/actuator/health` 返回 `UP`；`POST /api/auth/login` 和 `GET /api/auth/me` 成功，当前默认账户恢复为 `rc1-admin-default / 900001` |

## 当前剩余风险

- 未执行 `docker compose up -d postgres`：当前本机已有 PostgreSQL `5432` 可用，后端测试和 local profile 均已连接该实例。
- `npm audit` 仍提示 4 个漏洞（2 moderate、2 high），后续单独处理。
- Vite build 仍提示 chunk 超过 500 kB，后续单独处理。
- E2E 中 3 个详情/交易链路用例按当前环境数据条件 skip，不代表对应业务链路已完整验证。

## GateJ-FREEZE-FINAL-DOC 验证记录（2026-06-05）

本轮只做最终验收文档整理和 `docs/gates/gate-j` 冻结快照，不执行 build/deploy/restart，不修改后端/前端业务代码、API、migration、脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| GateJ-FREEZE 30m observation | PASS | 连续运行验收已完成。 |
| GateJ-FREEZE 1h acceptance | PASS | 连续运行验收已完成。 |
| GateJ-FREEZE 24h acceptance | PASS | 连续运行验收已完成。 |
| GateJ-FREEZE 7d acceptance | PASS | 7d checkpoint 为 2026-06-05 14:53:24 +08:00；health-loop 最新样本为 2026-06-05 15:40:58 +08:00。 |
| health-loop 样本数 | 2025 | 起点为 2026-05-29 14:53:20 +08:00。 |
| 168h nq-app 错误补扫 | 通过 | `docker compose logs --since=7d` 不被当前 Compose 识别，已补跑 `--since=168h`；`nq-app-error-scan-168h.txt` 的 `wc -l = 0`。 |
| 18888 health | UP | freeze 后端 health 正常。 |
| 5179 health | UP | freeze 前端 health 正常。 |
| nginx / nq-app / postgres | Up 7 days | postgres 为 healthy。 |
| after-7d.sql | 已生成 | 文件大小 266K；不进入 Git 冻结快照。 |
| 5179 安全组 | 通过 | 已确认只允许本人 IP 访问。 |
| UI/UX smoke review | Functional stability PASS；UI/UX professionalism FAIL | 不影响 GateJ-FREEZE 稳定性验收；登记为 post-freeze remediation。 |
| build/deploy/restart | 未执行 | 用户明确禁止，本轮只做文档冻结。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮未改业务代码、前端代码、API、migration、脚本或部署配置；不执行 build/deploy/restart。 |

边界确认：

- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- GateK not started；Next 仅为 GateK-PLAN。
- No new backend/frontend business code, API, migration, deployment, AI, DH, or real trading path was added in this final documentation stage。

## Codex Workflow 文档固化验证记录（2026-06-06）

本轮只新增和更新 Codex 插件路由、工作流、任务模板、Project Instructions 与索引文档，不修改后端/前端业务代码、API、migration、Python、脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| 同名文档存在性检查 | 已执行 | 目标 4 个新文档此前不存在，本轮新建；`docs/current/README.md` 已存在，本轮追加入口。 |
| `docs/current/README.md` 链接检查 | 已执行 | 已追加 `AGENTS.md`、插件工作流、Router Skill、任务模板、Project Instructions 的相对链接入口。 |
| 阶段边界检查 | 已执行 | 文档保持 GateJ completed、Next: GateK-PLAN、AI not started、DH integration not started / not connected to NQ。 |
| 禁止范围检查 | 已执行 | 明确禁止 LIVE trading、真实下单/撤单路径、真实 DH 接入、real provider、RealClient、credentials 泄露。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮仅修改 Markdown 文档，未修改业务代码、API、migration、前端页面、Python 或部署配置。 |

## Codex Workflow 文档一致性小修验证记录（2026-06-06）

本轮只修复 Codex Workflow Router Skill 状态表述和 Project Instructions 前置规则，不修改后端/前端业务代码、API、migration、Python、部署脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 用于确认本轮 Markdown 文档变更范围。 |
| `git diff --check` | 已执行 | 用于检查空白错误。 |
| Router Skill 状态表述检查 | 已执行 | `NQ_DH_WORKFLOW_ROUTER_SKILL.md` 已写明 `nq-dh-workflow-router` 当前按 `AGENTS.md` 作为 active skill 使用。 |
| Project Instructions 前置规则检查 | 已执行 | `CODEX_PROJECT_INSTRUCTIONS.md` 已补充 `nq-dh-workflow-router` 前置分类、范围限定和固定输出字段。 |
| 阶段边界检查 | 已执行 | 文档保持 GateJ completed、Next: GateK-PLAN、AI not started、DH integration not started / not connected to NQ。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮仅修改 Markdown 文档，未修改业务代码、API、migration、前端页面、Python、部署脚本或部署配置。 |

## Codex Workflow 输出字段口径小修验证记录（2026-06-06）

本轮只统一 Codex Workflow 标准输出字段，将必填输出字段统一为 `Findings`，不再把 `Summary` 作为必填字段；不修改后端/前端业务代码、API、migration、Python、部署脚本或部署配置。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 用于确认本轮 Markdown / Skill 文档变更范围。 |
| `git diff --check` | 已执行 | 用于检查空白错误。 |
| 输出字段口径检查 | 已执行 | `AGENTS.md`、`.agents/skills/nq-dh-workflow-router/SKILL.md`、`NQ_DH_CODEX_PLUGIN_WORKFLOW.md`、`NQ_DH_WORKFLOW_ROUTER_SKILL.md`、`CODEX_PROJECT_INSTRUCTIONS.md` 的标准输出格式均使用 `Findings`。 |
| 阶段边界检查 | 已执行 | 文档保持 GateJ completed、Next: GateK-PLAN、AI not started、DH integration not started / not connected to NQ。 |
| 后端/前端/Python 全量测试 | 未执行 | 本轮仅修改 Markdown / Skill 文档，未修改业务代码、API、migration、前端页面、Python、部署脚本或部署配置。 |

## GateH-1-WO 验证记录

日期：2026-05-17

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；新增 trading workspace 订单列表 controller 测试通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；E2E 后已停止监听 `18888` 的临时 Java 进程 |
| `npm run test:e2e` | 通过 | 10 个 Playwright 用例中 7 passed、3 skipped |

GateH-1 E2E 覆盖：

- `/trading` 正式交易工作台可进入。
- 页面显示正式账户上下文与 SIM / LIVE。
- 订单列表表格可加载，空态可见。
- 下单前检查抽屉展示风控摘要和服务端风控不可绕过状态。
- `/trade-validation` 旧路径仍可访问，并展示过渡入口提示。
- `E2E_TRADE_ORDER_ID` 未配置时，真实订单详情链路按原因 skip。

GateH-1 剩余验证风险：

- 当前本地没有配置 `E2E_TRADE_ORDER_ID`，因此订单详情真实数据链路未在本次 E2E 中执行，通过 skip 明确记录。
- Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning 和 Vite chunk > 500 kB 警告仍存在，本轮不处理。

## GateH-2-WO 验证记录

日期：2026-05-17

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateH-2 migration、API、adapter bridge 与既有 local integration 均通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `17` |
| `npm run test:e2e` | 通过 | 12 个 Playwright 用例中 9 passed、3 skipped；新增 `marketdata-bars-query-smoke` 与 `marketdata-ingestion-smoke` 均通过 |

GateH-2 E2E 覆盖：

- `/marketdata` 可打开。
- 页面展示 GateH-2 固定查询维度：OKX/BINANCE、SPOT、BTC-USDT、1m。
- K 线查询不报错，并展示 Bars 表格空态/数据态。
- 可通过页面创建 `marketdata_ingestion_jobs`。
- 可通过页面触发 `run-once`。
- 页面可查询 job/run 状态与运行结果。

GateH-2 交易所访问说明：

- 本轮 E2E 不依赖外网交易所稳定性。
- `run-once` 走本地后端真实 API 与 adapter 路径；当交易所接口返回空数据或外网不可用时，运行记录仍保存明确状态和统计。
- 本轮未执行真实生产交易所长时间回填或大范围历史数据下载。

GateH-2 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning。

## GateH-3-WO 验证记录

日期：2026-05-17

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateH-3 migration、dataset API、backtest dataset binding API、run snapshot 字段和既有回测链路均通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `18` |
| `npm run test:e2e` | 通过 | 14 个 Playwright 用例中 10 passed、4 skipped；新增 `marketdata-dataset-smoke` 通过，`backtest-dataset-binding-smoke` 因当前本地库没有可绑定 backtest config 种子而 skip |

GateH-3 E2E 覆盖：

- `/marketdata` 可创建 dataset。
- dataset 可展示覆盖范围、状态、质量状态、bar/gap 统计。
- dataset 可触发 `refresh-quality`。
- `/backtests` 已提供 dataset 绑定入口。
- 当前本地库没有 `research_configs/backtest_configs` 种子，`backtest-dataset-binding-smoke` 未执行 UI 绑定提交；后端 controller 测试已覆盖 `PATCH /api/backtest-configs/{configId}/dataset`。

GateH-3 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning。

## GateI-PLAN 验证记录

日期：2026-05-18

本轮只修改文档，不重新执行全量后端、前端、Python 测试。

必须检查项：

- `git status --short --branch`：已执行，当前仅规划文档变更。
- `docs/current/PLAN_GATEI.md`：存在。
- `docs/current/GATEI_API_PLAN.md`：存在。
- `docs/current/GATEI_DB_PLAN.md`：存在。
- `docs/current/GATEI_FRONTEND_PLAN.md`：存在。
- `docs/current/GATEI_TEST_PLAN.md`：存在。
- `docs/current/GATEI_WORK_ORDER.md`：存在。
- `docs/current/STATUS.md`：已写清 GateH completed、当前执行 GateI-PLAN、AI 仍未开始。
- 未新增业务代码、migration、API 实现或前端页面实现。
- 未接入 AI。

沿用当前验证基线：

- 后端 `mvn -f backend/pom.xml test` 已通过。
- 前端 `npm run build` 已通过。
- E2E `npm run test:e2e` 已通过，结果为 10 passed / 4 skipped。
- Python `pytest`、`mypy`、`ruff` 已通过。

## GateI-1-WO 验证记录

日期：2026-05-18

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；新增策略版本 service 测试、发布绑定 service 测试、既有 local integration 测试均通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `19` |
| `npm run test:e2e` | 通过 | 16 个 Playwright 用例中 13 passed、3 skipped；新增 `strategy-version-smoke` 与 `publish-version-smoke` 均通过 |

GateI-1 E2E 覆盖：

- `/strategies` 可打开并查询策略定义。
- 当本地库缺少策略定义时，E2E 通过正式 `POST /api/strategies` 创建最小 SIM 策略定义 fixture。
- 策略详情可展示“策略版本”和“创建策略版本”区域。
- 可创建 `ACTIVE` 策略版本，并展示参数快照、配置快照和状态。
- `/publishes` 可展示策略版本 ID 与版本快照入口。

GateI-1 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 compatibility warning、`Card.bordered` deprecation warning、`useForm` warning。

GateI-1 边界确认：

- 未进入 GateI-2/3/4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未修改策略核心算法、交易核心状态机或回测核心算法。

## GateI-2-WO 验证记录

日期：2026-05-19

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateI-2 migration V20、回测配置绑定、run 快照固化、evaluation 指标增强和既有 local integration 均通过 |
| `npm ci` | 通过 | 恢复前端依赖；原因是本地 `node_modules/typescript` 目录不完整导致首次 build 找不到 `typescript/bin/tsc`；命令完成后仍有 4 个 npm audit 告警，本轮不处理 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run '-Dspring-boot.run.profiles=local'` | 通过 | 为 E2E 临时启动后端；`/actuator/health` 返回 `UP`；Flyway 当前版本到 `20` |
| `npm run test:e2e` | 通过 | 全量 Playwright 17 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateI-2 backtest/evaluation 主链 |

GateI-2 E2E 变更：

- 新增 `frontend/tests/e2e/backtest-config-enhanced-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/evaluation-report-enhanced-smoke.spec.ts`。
- 新增 `frontend/tests/e2e/gatei2-fixtures.ts`，通过正式 API 导入本地 fixture bars、创建 dataset、strategy version、research config、backtest config、run 和 evaluation，不依赖外网交易所。
- 更新 `frontend/tests/e2e/support.ts`，按账户 alias 解析真实 `exchangeAccountId`，避免本地自增 ID 漂移导致登录前置失败。
- 本地验证库补入 E2E legacy strategy account 种子 `accounts.account_id=3001`，用于满足既有 `strategy_definitions.account_id` 外键；该操作不是 migration，不进入产品数据结构。

GateI-2 E2E 已覆盖：

- `/backtests` 页面展示 strategy version / dataset 追溯信息。
- 回测配置详情展示 strategy version snapshot、param snapshot、dataset snapshot、config snapshot。
- 回测运行详情展示 run 级 strategy version snapshot、dataset snapshot、param snapshot、config snapshot。
- `/evaluations` 页面展示 total return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON。
- 无数据时页面保留明确 empty 状态。

GateI-2 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未配置 `E2E_TRADE_ORDER_ID`，既有交易订单详情 E2E 仍按明确原因 skip；不影响 GateI-2 主链。

GateI-2 边界确认：

- 未进入 GateI-3/4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增 SIM/Paper Trading 运行闭环。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

## GateI-3-WO 验证记录

日期：2026-05-19

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；GateI-3 Flyway V21 编译通过；新增 `PaperTradingRunServiceTest` 4 个用例覆盖创建、启动、停止、状态拒绝；既有 35 个 nq-app suite 测试全通过 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |

GateI-3 E2E 说明：

- 新增 `frontend/tests/e2e/paper-trading-run-smoke.spec.ts`，覆盖：Paper Trading 页面打开、列表查询、创建 Paper run、启动 Paper run、停止 Paper run、查看 orders/trades/positions 空态、查看快照标签。
- 新增 `frontend/tests/e2e/paper-trading-fixtures.ts`，通过正式 API 完整链路准备 fixture：fixture bars 导入 → strategy → strategy version → research config → backtest config → strategy version 绑定 → backtest run → start → evaluate → publish；最终返回可用的 `publishId`。
- E2E 不依赖外网交易所；不调用真实 LIVE 下单接口。
- E2E 需要后端 local profile 启动且 Flyway 到 V21；本轮提交前未在干净本地 5432 实例上执行该完整 E2E（具体执行需要先启动后端、确保 fixture 账户种子 3001 存在）。

GateI-3 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用 BASELINE-FIX 已通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未在本轮启动后端 local profile 并执行 `npm run test:e2e`；E2E spec 已就绪，等待 GateI-3-FIX 或下次完整本地验证窗口执行。

GateI-3 边界确认：

- 未进入 GateI-4。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增美股/A 股、合约全量、高频或复杂因子平台。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未调用真实交易所下单接口。

## GateI-3-FIX 验证记录

日期：2026-05-20

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，35 tests，0 failures |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway V21 已应用 |
| `npm run test:e2e` | 通过 | 18 passed / 1 skipped |

GateI-3-FIX 修复内容：

- `paper-trading-run-smoke.spec.ts`：`getByLabel('发布 ID')` → `getByPlaceholder('发布记录 ID（publishId）')`，修复 Ant Design Form.Item label 关联问题。
- `paper-trading-run-smoke.spec.ts`：Modal OK 按钮从 `getByRole('button', {name: '确 定'})` → `getByRole('button', {name: 'OK', exact: true})`，修复无中文 locale 时按钮文本为 "OK" 且与 "OKX" 冲突。
- `paper-trading-run-smoke.spec.ts`：移除 `waitForResponse` 对 GET 列表刷新的显式等待，改用 `await expect(row).toBeVisible({timeout: 15_000})` 等待 UI 更新。
- `paper-trading-run-smoke.spec.ts`：Drawer 内断言从 `page.getByText('Paper Run ID')` → `page.getByLabel('Paper Trading 详情').getByText('Paper Run ID')`，避免与表头重复元素冲突。
- `paper-trading-run-smoke.spec.ts`：按钮选择器使用 `.or()` 兼容 `getByRole('link')` 和 `getByRole('button')`，适配 Ant Design Table 内 `type="link"` 按钮的实际 role。

GateI-3-FIX E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run（POST /api/paper-trading/runs 返回 CREATED + 快照绑定）。
- 可启动 Paper run（POST .../start 返回 RUNNING）。
- 可停止 Paper run（POST .../stop 返回 STOPPED）。
- 详情抽屉可打开，展示 Paper Run ID、状态、快照。
- 订单/成交/持仓标签页展示明确空态。
- 快照标签页展示 Publish Snapshot 和 Strategy Version Snapshot。
- 不依赖外网交易所。
- 不调用真实 LIVE 下单。
- 使用本地 account_id=3001 种子。

GateI-3-FIX skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateI-3 主链。

GateI-3-FIX 结论：

- GateI-3-WO + GateI-3-FIX 已完成。
- 后端测试通过、前端 build 通过、E2E 18 passed / 1 skipped。
- 允许进入 GateI-4-WO，但只能在本轮变更审查/提交后单独开工。
- GateI-4 只能做风控回写、资金曲线、持仓曲线、交易复盘与异常停机，不能夹带 AI。

## GateI-4-WO 验证记录

日期：2026-05-20

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；35 tests / 0 failures，含 PaperTradingMonitorServiceTest 5 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告，本轮不处理 |
| `npm run test:e2e` | 未执行 | 本轮未启动本地后端 local profile；spec 已扩展，等待 GateI-4-FIX 窗口执行 |

GateI-4 新增测试覆盖：

- `PaperTradingMonitorServiceTest`：5 个用例覆盖 runRiskCheckOnce 正常写入、listRiskResults 空态、emergencyStop APPLIED（RUNNING → STOPPED）、emergencyStop FAILED（非 RUNNING）、listEmergencyStops 空态。
- E2E spec 已扩展 GateI-4 链路（风控检查 / 5 个新 Tab / 紧急停机），待本地后端启动后执行。

GateI-4 skipped 说明：

- E2E 未执行：本轮未启动本地后端 local profile + Flyway V22，spec 已就绪。

GateI-4 结论：

- 后端测试通过、前端 build 通过。
- E2E 待 GateI-4-FIX 窗口执行。
- GateI 仍未整体完成；不创建 `docs/gates/gate-i`。

## GateI-4-FIX 验证记录

日期：2026-05-21

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，35 tests / 0 failures |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `22` |
| 5 张 GateI-4 表存在 | 通过 | `paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events` 全部存在 |
| `npm run test:e2e` | 通过 | 19 passed / 1 skipped；新增 GateI-4 monitor smoke 用例通过 |

GateI-4-FIX 修复内容：

- 改 GateI-4 E2E 用例：从 `request` fixture 调用 API（不共享 token）改为通过 UI 操作完成全链路。
- 改 PaperTradingPage：将"执行风控检查"和"紧急停机"按钮从 `PaperListSection` children 移到外层（空态时仍可见）。
- 改 Modal 调用方式：`Modal.confirm` → `App.useApp().modal.confirm`，确保在 App context 下正确渲染。
- 修复 PASSED 文本断言：使用 `.first()` 避免多元素冲突。

GateI-4-FIX skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateI 主链。

GateI-4-FIX 结论：

- GateI-4-WO + GateI-4-FIX 已完成。
- GateI 全部子阶段已完成：GateI-1-WO → GateI-2-WO → GateI-3-WO → GateI-3-FIX → GateI-4-WO → GateI-4-FIX。
- **GateI completed。**
- Next: GateJ-PLAN（Paper Trading 稳定运行）。
- GateJ 不是 AI 阶段；GateK-PLAN 不启动 AI，AI 相关工作仍需后续另起 Gate / review。

## GateJ-PLAN 验证记录

日期：2026-05-21

本轮只修改文档，不重新执行全量后端、前端、Python 测试。

沿用 GateI completed 验证基线：

- 后端 `mvn -f backend/pom.xml test`：35 tests / 0 failures。
- 前端 `npm run build`：通过。
- E2E `npm run test:e2e`：19 passed / 1 skipped。
- Python `pytest`、`mypy`、`ruff`：通过。

本轮只改文档，未跑全量测试原因：无业务代码变更、无 migration 变更、无 API 变更、无前端页面变更。

GateJ 测试规划入口为 [GATEJ_TEST_PLAN.md](./GATEJ_TEST_PLAN.md)。

GateJ 规划 E2E 矩阵：

- paper-schedule-smoke
- paper-heartbeat-smoke
- paper-daily-report-smoke
- paper-alert-smoke
- paper-recovery-smoke
- paper-stability-check-smoke

GateJ 规划连续运行验收：

- 1 小时短验收
- 24 小时中验收
- 7 天稳定性验收

## GateJ-1-WO 验证记录

日期：2026-05-21

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，35 tests / 0 failures；含 PaperRunScheduleServiceTest 11 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `23` |
| `npm run test:e2e` | 通过 | 20 passed / 1 skipped；新增 paper-trading-schedule-smoke 通过 |

GateJ-1 E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run 并启动。
- 详情抽屉可打开。
- 调度计划 Tab 可展示空态。
- 可创建调度计划（ENABLED 状态）。
- 可执行一次调度（run-once），fire 记录为 SUCCEEDED。
- 可查看触发记录。
- 可禁用调度（DISABLED）。
- 心跳 Tab 可展示空态。
- 可执行心跳检查（run-once），heartbeat 状态为 OK。
- 不依赖外网交易所。
- 不调用真实 LIVE 下单。

GateJ-1 skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateJ-1 主链。

GateJ-1 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。

GateJ-1 边界确认：

- 未进入 GateJ-2/3/FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增日报、告警、恢复、稳定性验收。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

## GateJ-2-WO 验证（2026-05-21）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，35 tests / 0 failures；含 PaperRunMonitorServiceTest 12 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `24` |
| `npm run test:e2e` | 通过 | 22 passed / 1 skipped；新增 paper-trading-daily-report-smoke / paper-trading-alert-smoke 通过 |

GateJ-2 E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run 并启动。
- 详情抽屉可打开。
- 日报 Tab 可展示空态。
- 可生成今日日报（status = GENERATED）。
- 可重复生成同一日期日报（幂等）。
- 告警 Tab 可展示空态。
- 可创建测试告警（SYSTEM_NOTICE / LOW / OPEN）。
- 可确认告警（OPEN → ACKED，acknowledgedBy 写入）。
- 可解决告警（ACKED → RESOLVED，resolvedAt 写入）。
- 不依赖外网交易所。
- 不调用真实 LIVE 下单。

GateJ-2 skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateJ-2 主链。

GateJ-2 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning。

GateJ-2 边界确认：

- 未进入 GateJ-3 / GateJ-FREEZE。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未新增恢复、稳定性验收、外部通知（邮件、Slack、钉钉）。
- 未引入图表库。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。

## GateJ-3-WO 验证（2026-05-22）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS；新增 PaperRunRecoveryServiceTest 9 用例、PaperRunStabilityCheckServiceTest 10 用例、PaperRunMonitorRunServiceTest 8 用例 |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；仍有 Vite chunk > 500 kB 警告 |
| 后端 local profile 启动 | 通过 | `/actuator/health` 返回 `UP`；Flyway 当前版本 `25` |
| `npm run test:e2e` | 通过 | 24 passed / 1 skipped；新增 paper-trading-recovery-smoke / paper-trading-stability-check-smoke 通过 |

GateJ-3 E2E 覆盖：

- `/paper-trading` 页面可打开。
- 可创建 Paper run 并启动。
- 详情抽屉可打开。
- 恢复事件 Tab 可展示空态。
- 可执行恢复（MANUAL_RECOVER），写入 recovery event。
- 可执行重试失败步骤（RETRY_FAILED_STEP），写入 recovery event。
- 可执行监控守护一次（HEARTBEAT_LAG 自动告警最小落库）。
- 告警 Tab 可看到 HEARTBEAT_LAG 自动告警。
- 稳定性验收 Tab 可展示空态。
- 可生成最近 24h 稳定性验收（无心跳 → FAILED，验证第一版口径）。
- 同窗口重复生成幂等。
- 不依赖外网交易所，不调用真实 LIVE 下单。

GateJ-3 skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有交易订单详情链路，不影响 GateJ-3 主链。

GateJ-3 未执行项：

- Python `pytest`、`mypy`、`ruff` 本轮未重新执行；本轮未修改 `research/py`，沿用既有通过基线。
- 未处理 `npm audit` 依赖漏洞。
- 未处理 Vite chunk > 500 kB 警告。
- 未处理 Ant Design React 19 / `Card.bordered` / `Modal.destroyOnClose` deprecation warning。
- 未执行 GateJ-FREEZE 的 1h/24h/7d 连续运行验收（属 GateJ-FREEZE 范围）。

GateJ-3 边界确认：

- 未进入 GateJ-FREEZE 正式验收归档。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
- 未做外部通知（邮件 / Slack / 钉钉 / 企业微信 / Telegram / Webhook / 短信）。
- 未做自动恢复策略引擎。
- 未调用真实 LIVE 下单接口。
- 未修改交易核心状态机。
- 未修改策略核心算法。
- 未修改回测核心算法。
- 未引入图表库。

## PRE-FREEZE-CODE-AUDIT 验证记录（2026-05-22）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 仅 docs/current 与根目录入口文档变更，无业务代码、migration、API 实现、前端页面实现变更 |
| `mvn -f backend/pom.xml test` | 通过 | BUILD SUCCESS，0 failures / 0 errors（archunit ModuleBoundaryArchTest 6 用例 + PackageBoundaryArchTest 1 用例通过；nq-app suite 35 全通过；Paper 单元测试 PaperTradingRunService 4 + PaperTradingMonitorService 5 + PaperRunScheduleService 11 + PaperRunMonitorService 12 + PaperRunRecoveryService 9 + PaperRunStabilityCheckService 10 + PaperRunMonitorRunService 8 全部通过）|
| `npm run build` | 通过 | `tsc -b && vite build` 成功；dist/index.js ≈ 1.48 MB（gzip 446 kB）；仍有 chunk > 500 kB 警告 |
| `npm run test:e2e` | 本轮未实际执行 | 沿用 GateJ-3-WO 24 passed / 1 skipped 通过基线；P1-1 要求 GateJ-FREEZE 入场前补跑（启动后端 local profile + 5432 + 种子 `account_id=3001` 后执行）|
| `python -m pytest -q` | 本轮未实际执行 | 当前 shell `python.exe` 仅 Windows App Execution Alias stub，调用 exit 49；沿用 BASELINE-FIX-2 / GateJ-3 通过基线；P1-2 要求 GateJ-FREEZE 入场前在真实 Python 环境补跑 |
| `python -m mypy src` | 本轮未实际执行 | 同上；P1-2 |
| `python -m ruff check .` | 本轮未实际执行 | 同上；P1-2 |

未跑验证不写成通过：本轮未执行的 E2E 与 Python 三件套均明确标记为「未在本轮重跑」，并通过 PRE_FREEZE_AUDIT_FIX_PLAN.md P1-1 / P1-2 列入 GateJ-FREEZE 入场前必做项。

PRE-FREEZE-CODE-AUDIT 结论：

- 后端单元测试全部通过；前端 build 通过。
- 文档、代码、DB、API、前端、E2E spec、Python 模块、Paper/LIVE 隔离、AI 边界、模块边界一致。
- 无 P0 阻塞性问题。
- P1 共 4 条：P1-1 / P1-2 是 GateJ-FREEZE 入场前必做的验证补跑；P1-3 不阻塞；P1-4 已闭环。
- 允许进入 GateJ-FREEZE，但 GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

## PRE-FREEZE-CODE-AUDIT second pass 验证记录（2026-05-22）

本轮由 Codex 执行二次审查与实际验证。未修业务代码，未新增 API / migration / 前端页面实现，未接 AI，未执行 GateJ-FREEZE 1h/24h/7d 连续运行验收。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module 全部 `SUCCESS`；`nq-app` suite `35 tests / 0 failures / 0 errors / 0 skipped`；Paper 相关 service 测试均通过 |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；`dist/assets/index-CLLFLWD4.js` 约 1,478.51 kB（gzip 446.09 kB）；Vite chunk > 500 kB 警告仍存在，作为 P2 |
| `cd frontend && npm run test:e2e` | 通过 | 后端 local profile 启动成功，`/actuator/health` 返回 `UP`，Flyway 当前版本 `25`；完整 Playwright 25 tests total，24 passed / 1 skipped / 0 failed |
| `cd research/py && python -m pytest -q` | 通过 | 使用真实 Python 解释器执行；`2 passed in 0.03s` |
| `cd research/py && python -m mypy src` | 通过 | `Success: no issues found in 8 source files` |
| `cd research/py && python -m ruff check .` | 通过 | `All checks passed!` |

E2E skipped 说明：

- `trading workspace / 配置订单 ID 时可打开订单详情`：未配置 `E2E_TRADE_ORDER_ID`，为既有订单详情链路，不影响 GateJ 主链。
- GateJ 主链 smoke 已全部执行并通过：schedule/heartbeat、daily report、alert、recovery、stability check、monitor run-once。

环境说明：

- 默认 shell `python` 指向 `C:\Users\lingy\AppData\Local\Microsoft\WindowsApps\python.exe`，不是可用解释器；本轮使用 workspace bundled Python 临时置于 `PATH` 首位后执行同样的 `python -m ...` 命令。
- 首次 E2E 启动后端时遇到 Maven 本地仓库目录冲突；提权重跑后该问题消失。随后一次 PowerShell 参数引用错误导致 Maven 将 profile 参数误识别为 lifecycle phase；修正引用后后端启动与完整 E2E 均通过。上述两次失败未进入业务 E2E 断言，不计为业务功能失败。

PRE-FREEZE-CODE-AUDIT second pass 结论：

- 后端、前端 build、完整 E2E、Python pytest/mypy/ruff 均已实际执行并通过。
- API / DB / Paper-LIVE 隔离 / AI 边界二次抽查未发现 P0/P1。
- 允许进入 GateJ-FREEZE，但 GateJ-FREEZE 必须单独开工，只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

## AUDIT-FIX 验证记录（2026-05-26）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 仅 AUDIT-FIX 范围文件变更，外加上一轮新增安全审查报告 |
| `git diff --stat` | 已执行 | 用于确认变更范围 |
| `git diff -- scripts/gated_okx_dome_verify.ps1 docs/archive/scripts/gated_okx_dome_verify.ps1 frontend/playwright.config.ts frontend/tests/e2e/run-e2e.mjs docs/current/API.md docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md` | 已执行 | 用于确认 P1 stub / 归档、E2E 端口与文档事实源变更 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 chunk > 500 kB 警告 |
| `cd frontend && npm run test:e2e` | 通过 | 首次在后端未启动时失败于 `127.0.0.1:18888 ECONNREFUSED`；启动后端 local profile 后复跑通过，Vite 监听 `127.0.0.1:5179`，结果 24 passed / 1 skipped / 0 failed |

端口修复说明：

- `4173` 位于当前 Windows TCP excluded range `4141-4240` 内，会导致 Vite 监听 `127.0.0.1:4173` 返回 `EACCES`。
- E2E/Vite 端口统一调整为 `5179`，Playwright `baseURL`、run-e2e 启动参数、Vite dev / preview 默认端口和 `.env.example` 保持一致。
- 唯一 skipped 用例仍为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，不影响 GateJ 主链。

## GateJ-FREEZE-FIX 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module 全部 `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 成功；仍有既有 Vite chunk > 500 kB 警告 |
| `rg -n "<redacted-local-test-password>\|18888\|legacy console gate\|/api/auth/login\|<redacted-authorization-header-prefix>" frontend/dist` | 通过 | 无命中；`rg` 返回 1 表示未找到匹配项 |
| `rg -n "/api/auth/me" frontend/dist` | 通过 | 无命中；额外确认登录页不再暴露当前用户接口路径 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 首次因沙箱无法写入本机 Maven repository tracking file 失败；提权重跑成功，生成 `release/nq-gatej-freeze-release.zip` |
| `jar tf backend/nq-app/target/nq-app-0.1.0-SNAPSHOT.jar \| Select-String application-freeze.yml` | 通过 | jar 内包含 `BOOT-INF/classes/application-freeze.yml` |

脚本语法说明：

- 当前 Windows 环境只有 `C:\WINDOWS\system32\bash.exe`，调用 `bash -n` 会进入 WSL 未安装提示，未能在本机执行 bash 语法检查。
- `seed-freeze-user.sh` 已通过文本审查、release 包纳入检查和服务器执行流程文档约束；最终 shell 运行需在 Linux ECS 上随重新部署验证。

本轮未执行：

- 未重新执行 `npm run test:e2e`：本轮改动限定在登录页展示、freeze profile、部署脚本与 freeze 文档；按任务验收要求执行了后端测试、前端 build、dist 敏感串扫描和 release 打包。
- 未执行 Python `pytest/mypy/ruff`：本轮未修改 `research/py`。

## GateJ-FREEZE-FIX-SECOND-PASS 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区包含 GateJ-FREEZE-FIX 与本轮 second pass 文档/注释/测试描述清理；未提交 release/dist/env/jar/zip/dump/log/evidence |
| 源码敏感词扫描 | 已执行 | 阻塞残留已修复；剩余命中均为允许项或历史文档记录，详见 `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md` |
| `rg ... frontend/dist` | 通过 | 无敏感/旧联调关键词命中 |
| release zip 解压后 `rg ... release/second-pass-scan` | 通过 | 除允许的 `18888` 部署端口配置外，无敏感/旧联调关键词命中 |
| `.gitignore` 检查 | 通过 | release/dist/target/env/log/dump/evidence 已覆盖 |
| `git ls-files` 污染检查 | 通过 | 未发现不该追踪的 release/dist/env/jar/zip/dump/log/evidence |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip` |

## GateJ-FREEZE-FIX-3 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `bash -n scripts/seed-freeze-user.sh` | 未通过当前本机执行 | 当前 Windows `bash` 指向未安装 Linux 发行版的 WSL stub；本机未安装 Git Bash，Docker daemon 也未运行，无法获得可用 Bash。脚本已按 Bash 语法静态审查，需在 Linux ECS 或可用 Bash 环境复跑该命令。 |
 | `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
 | `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |

| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`，release info 已包含禁止 `source .env.freeze` 与交互式 seed 密码说明。 |

GateJ-FREEZE-FIX-3 变更限定在 seed 脚本、freeze env 模板、freeze 部署文档、release info 和当前事实源文档；未新增 API、migration 或业务功能，未接入 AI/DH/真实交易。

## GateJ-FREEZE-FIX-4 验证记录（2026-05-28）

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `bash -n scripts/seed-freeze-user.sh` | 未通过当前本机执行 | 当前 Windows `bash` 仍指向未安装 Linux 发行版的 WSL stub；本机未安装 Git Bash，Docker daemon 未运行，无法获得可用 Bash。ECS 或可用 Bash 环境必须复跑。 |
| `unset NQ_FREEZE_ADMIN_PASSWORD` 后交互式执行 seed | 待 ECS 复验 | 本轮修复点是 `read -s -p` 后的视觉换行改写 stderr，避免命令替换捕获换行并误判多行；需在 Linux ECS 上用真实 TTY 复验。 |
| 进程环境方式执行 seed | 待 ECS 复验 | 当前本机无运行中的 freeze PostgreSQL 容器，需在 ECS 上复验。 |
| `hash_prefix` 为 `$2a$` 或 `$2b$` | 待 ECS 复验 | 需在 ECS PostgreSQL 容器内查询，禁止输出完整 hash。 |
| `curl` 登录 200 且不打印 token | 待 ECS 复验 | 需在 ECS 本机验证并只输出 HTTP status。 |

ECS 建议复验命令：

```bash
cd /opt/nexus-quant
bash -n scripts/seed-freeze-user.sh

unset NQ_FREEZE_ADMIN_PASSWORD
# 确保 .env.freeze 中 NQ_FREEZE_ADMIN_PASSWORD 缺失、注释或保留 CHANGE_ME 占位符，再交互式输入验收密码。
bash scripts/seed-freeze-user.sh

NQ_FREEZE_ADMIN_PASSWORD='<single-line-password>' bash scripts/seed-freeze-user.sh

docker compose --env-file .env.freeze -f docker-compose.freeze.yml exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
  "SELECT substring(password_hash from 1 for 4) AS hash_prefix FROM users WHERE username = '${NQ_FREEZE_ADMIN_USERNAME}' AND enabled = TRUE;"

status="$(
  curl -sS -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    --data "{\"username\":\"${NQ_FREEZE_ADMIN_USERNAME}\",\"password\":\"<single-line-password>\"}" \
    'http://127.0.0.1:18888/api/auth/login'
)"
test "$status" = "200"
```

本轮本地可验证项：

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |

| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |

## GateJ-FREEZE-FIX-5 验证记录（2026-05-29）

本轮修复 release 包内 `.sh` CRLF 换行导致 ECS Bash 解析 `set -euo pipefail` 失败的问题。修复范围限定在换行策略、release 打包脚本和当前事实源文档；未新增 API、migration 或业务功能，未接入 AI/DH/真实交易。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| 仓库 `scripts/*.sh` CRLF 字节检查 | 通过 | `backup-db.sh`、`deploy-freeze.sh`、`freeze-health-loop.sh`、`health-check.sh`、`seed-freeze-user.sh` 均为 `HasCRLF=False`。 |
| `git diff --check` | 通过 | 无空白错误；仅有 `build-freeze-release.ps1` 将按 `.gitattributes` 维持 CRLF 的 Git 提示。 |
| `mvn -f backend/pom.xml test` | 通过 | 首次 120s 超时未得出测试失败结论；提高超时后复跑通过，Reactor `BUILD SUCCESS`，23 个 backend module `SUCCESS`，`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`；打包脚本在 zip 前对 staging `scripts/*.sh` 做 LF 归一化兜底。 |
| release zip 解压后 CRLF 检查 | 通过 | 解压到本机临时目录后，zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`30,979,533` bytes

ECS 待复验：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
bash scripts/backup-db.sh before-freeze
nohup bash scripts/freeze-health-loop.sh > /opt/nexus-quant/freeze-evidence/health/freeze-health-loop.out 2>&1 &
grep -n '"status":"UP"\|UP' /opt/nexus-quant/freeze-evidence/health/health-check-7d.log | tail
```

结论：本地 release 可复现性已修复；ECS 尚未在本轮环境执行，未完成 ECS 复验前不得进入 GateJ-FREEZE 首次启动验收。

## GateJ-FREEZE-FIX-6 验证记录（2026-05-29）

本轮修复 ECS freeze 控制台点击 Instrument Catalog “同步 Catalog”后因 Binance `exchangeInfo` 返回 451 被抛成 500 的问题，并清理生产/freeze 可见页面中的旧阶段与本地环境文案。修复范围限定在 freeze 验收阻塞问题；未新增 API、migration 或业务功能，未接入 AI/DH/真实交易。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-api,nq-scheduler -am test` | 通过 | 覆盖 `/api/instruments/sync` 409 受控错误与 `AdapterInstrumentCatalogSyncService` 禁用/外部异常转换测试。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |
| `frontend/dist` 禁止串扫描 | 通过 | 未命中 `GateG`、`GateH-PRE`、`ChangeMe123`、`admin / ChangeMe123`、`/api/auth/login`、`/api/auth/me`、`Authorization: Bearer`。 |
| release zip 解压后禁止串扫描 | 通过 | 解压目录未命中上述禁止串。 |
| release zip 解压后 CRLF 检查 | 通过 | zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`30,980,280` bytes

ECS 待复验：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
docker compose --env-file .env.freeze -f docker-compose.freeze.yml restart nq-app nginx
curl -fsS http://127.0.0.1:18888/actuator/health
# 浏览器进入 Instrument Catalog：查询允许为空；点击同步 Catalog 不得显示 internal server error。
# 后端日志不得出现：api_unhandled_exception path=/api/instruments/sync
```

结论：本地已修复 freeze release 中 Instrument Catalog sync 的 500 风险与前端旧文案残留；ECS 尚未在本轮环境执行，未完成 ECS 复验前不得进入 GateJ-FREEZE 首次启动验收。

## GateJ-FREEZE-FIX-7 验证记录（2026-05-29）

本轮修复 freeze 控制台旧 Gate 文案、开发接口说明和不专业筛选控件。修复范围限定在前端 UI 展示与筛选控件；未新增 API、migration 或后端业务流程，未接入 AI/DH/真实交易。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `cd frontend && npm run build` | 通过 | 首次因 `PaperTradingPage` 漏加 `Select` import 失败，补齐后通过；仍有既有 Vite chunk > 500 kB 警告。 |
| `frontend/dist` 残留扫描 | 通过 | 大小写敏感扫描未命中 `GateH-1`、`GateH-2`、`GateI-3`、`GateH-PRE`、`GateG`、`LOCAL`、`Gate-3`、`GET /api`、`POST /api`、`publishId 过滤`、`本地筛选字段`、`真实请求参数`。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 首次因沙箱无法写入本机 Maven repository tracking file 失败；提权重跑通过并重新生成 release zip。 |
| release zip 解压后 frontend/dist 残留扫描 | 通过 | 解压目录 `frontend/dist` 未命中上述旧 Gate / LOCAL / 开发接口说明残留。 |
| release zip 解压后 CRLF 检查 | 通过 | zip 内 5 个 `scripts/*.sh` 均为 `HasCRLF=False`。 |

新 release 包：

- 路径：`release/nq-gatej-freeze-release.zip`
- 大小：`31,014,538` bytes

ECS 待复验：

```bash
cd /opt/nexus-quant
for f in scripts/*.sh; do echo "CHECK $f"; bash -n "$f" || exit 1; done
docker compose --env-file .env.freeze -f docker-compose.freeze.yml up -d --force-recreate nq-app nginx
curl -fsS http://127.0.0.1:18888/actuator/health
curl -fsS http://127.0.0.1:5179/actuator/health
```

浏览器复验：

- 页面不再出现旧 Gate / LOCAL / API 开发说明残留。
- 重点页面枚举筛选项为 Select，时间字段为 DatePicker。
- Instrument Catalog “同步 Catalog” 仍显示受控提示，不显示 internal server error。
- 后端日志不得出现 `ERROR` / `Exception` / `api_unhandled_exception path=/api/instruments/sync`。

结论：本地 release 已可上传 ECS 复验；ECS 浏览器与日志复验通过前不得进入 GateJ-FREEZE 首次启动验收。

## Credential Revocation Governance Batch 5-C 验证记录（2026-06-07）

本轮接入 credential lifecycle 最小后端能力：`credential_status` 读取、`revoke / disable / expire` command API、active material 生命周期过滤和 append-only audit log 写入。未新增 migration、前端、Python、部署、AI、DH、LIVE 或真实交易所私有链路。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 失败后已修复 | 首次失败点为 `ExchangeAccountCredentialControllerWebMvcTest` 中 `Instant` 在 standalone MockMvc 下输出 epoch seconds；补齐 Jackson Java time converter 后不再复现。 |
| `mvn -f backend/pom.xml -pl nq-api -am test` | 通过 | 覆盖 Credential API WebMvc 测试和 API 依赖模块。 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api -am test` | 通过 | 覆盖 Service lifecycle 流转、JDBC SQL、API command endpoint、active material 过滤和敏感字段缺失断言。 |

最终收口验证：

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |

## NQ-CREDENTIAL-PERMISSION-PROBE-CODE-API-TEST-IMPLEMENTATION 验证记录（2026-06-13）

本轮实现 V31 permission probe 最小后端 code/API/test 能力，默认 no-real-exchange port 返回 `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`，不访问 OKX/Binance 或其他真实交易所；未新增 migration、前端、Python、部署脚本、AI、DH、LIVE 或真实交易路径。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-core -am -Dtest=CredentialPermissionProbeServiceTest test` | 失败，非代码失败 | Reactor 前置模块没有匹配测试，Surefire 将 no matching tests 视为失败。 |
| `mvn -f backend/pom.xml -pl nq-core -am -Dtest=CredentialPermissionProbeServiceTest '-Dsurefire.failIfNoSpecifiedTests=false' test` | 通过 | `CredentialPermissionProbeServiceTest` 9 tests / 0 failures / 0 errors；覆盖 LIVE/inactive/non-ACTIVE/Paper gate/withdraw risk、STARTED/SUCCEEDED/FAILED/SKIPPED audit、failed_auth_count 策略、scope null、IN_PROGRESS 并发和 latest no-port。 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app,nq-adapter-okx,nq-adapter-binance -am test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 52 tests / 0 failures / 0 errors。 |
| `git diff --check` | 通过 | 无空白错误；仅有 Git 换行转换提示。 |
| `git diff -- backend/nq-infra/src/main/resources/db/migration` | 通过 | 输出为空，未新增/修改 migration。 |
| `git diff -- frontend` | 通过 | 输出为空，未改前端。 |
| `git diff -- research` | 通过 | 输出为空，未改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改部署/脚本。 |

边界扫描：

- 阶段措辞扫描通过：未新增 GateK implementation started / AI started / DH integrated / LIVE enabled 的正向语义；命中项均为 `not started`、`not integrated`、`disabled` 或禁止说明。
- Permission probe 相关 surefire reports 未命中 `www.okx.com` / `api.binance.com`。
- 全量 surefire reports 未命中 `No route to host`、`ConnectException`、`UnknownHostException`、`request failed`、真实 endpoint 请求或 `api.binance.com`。
- 全量 `nq-app` surefire reports 仍包含既有 OKX adapter 配置摘要 `baseUrl=https://www.okx.com`，这是 local profile fingerprint，不是本轮 permission probe 访问证据。

## NQ-GATEK-PLAN 验证记录（2026-06-14）

本轮是 docs-only planning：只新增 / 同步 GateK-PLAN 文档和 current facts 入口，不修改 backend、frontend、research、scripts、deploy、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；验收以文档边界、Git diff 和阶段措辞检查为准。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git diff --check` | 通过 | 仅输出既有 LF/CRLF 工作区提示，无 whitespace error。 |
| `git diff --stat` | 已检查 | 仅文档变更；新增文件通过 `git status --short` 确认。 |
| `git status --short` | 已检查 | 仅允许文档范围内变更和新增 `docs/current/GATEK_PLAN.md`。 |
| `git diff -- backend` | 通过 | 输出为空，未改后端。 |
| `git diff -- frontend` | 通过 | 输出为空，未改前端。 |
| `git diff -- research` | 通过 | 输出为空，未改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改脚本。 |
| `git diff -- deploy` | 通过 | 输出为空，未改部署目录。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |

阶段与安全边界：

- GateK 只写为 planning / architecture / productization / deployment / observability / security boundary stage。
- GateK implementation 明确为 not started。
- AI 明确为 not started。
- DH 明确为 not integrated / not connected to NQ；Integration-0 只作为 contract / mock / docs / contract test line。
- LIVE 明确为 disabled。
- 未读取、打印、复制或输出 credential material、`.env`、`*.key`、`*.pem`、`*.log`。

## GATEK-PLAN-FREEZE-REVIEW 验证记录（2026-06-14）

本轮是 docs-only freeze review：只审查和修正 GateK-PLAN 与入口事实源文档，不修改 backend、frontend、research、scripts、deploy、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；验收以文档边界、Git diff、forbidden-area diff、阶段措辞和敏感信息扫描为准。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已检查 | 仅允许文档范围内变更。 |
| `git diff --check` | 通过 | 无 whitespace error；如出现 LF/CRLF 提示，按既有 Windows 工作区提示处理。 |
| `git diff --stat` | 已检查 | 仅文档变更。 |
| `git diff -- backend` | 通过 | 输出为空，未改后端。 |
| `git diff -- frontend` | 通过 | 输出为空，未改前端。 |
| `git diff -- research` | 通过 | 输出为空，未改 Python research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改脚本。 |
| `git diff -- deploy` | 通过 | 输出为空，未改部署目录。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `rg ".env|.key|.pem|private key|api secret|passphrase|mnemonic|password" README.md AGENTS.md CLAUDE.md docs/current` | 已检查 | 命中项仅允许为否定式、禁止说明、字段名、占位符或历史脱敏说明；不得包含真实 credential material。 |

阶段与安全边界：

- GateK-PLAN 明确为 planning / architecture / productization / deployment / observability / security boundary stage。
- GateK implementation 明确为 not started。
- AI 明确为 not started，GateK-PLAN 不启动 AI 信号、AI runtime 或 AI Paper Trading。
- DH 明确为 not integrated / not connected to NQ；Integration-0 只作为 contract / mock / docs / contract test line。
- LIVE 明确为 disabled。
- 真实 OKX/Binance permission probe adapter 明确为 not implemented。

## GATEK-ARCH-DOC-SYNC 验证记录（2026-06-14）

本轮是 docs-only architecture wording sync：只同步 `docs/current/ARCHITECTURE.md`、`docs/current/MODULES.md`、`docs/current/README.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`，不修改 backend、frontend、research、scripts、deploy、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；验收以文档边界、Git diff、forbidden-area diff 和阶段措辞扫描为准。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已检查 | 本轮修改 5 个允许文档；工作区另有非本轮的 `docs/current/frontend/**` staged / modified 文件。 |
| `git diff --check` | 通过 | 仅出现既有 Windows LF/CRLF 提示，无 whitespace error。 |
| `git diff --stat` | 已检查 | 包含本轮 5 个允许文档；另显示非本轮的 `docs/current/frontend/NQ_DESIGN_TOKENS_V2.md` 与 `docs/current/frontend/NQ_FRONTEND_BUILD_MATRIX.md`。 |
| `git diff -- backend` | 通过 | 输出为空，未改 backend。 |
| `git diff -- frontend` | 通过 | 输出为空，未改 top-level frontend。 |
| `git diff -- research` | 通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 通过 | 输出为空，未改 deploy。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| `rg "GateK implementation|AI started|DH integrated|LIVE enabled|real adapter implemented" README.md AGENTS.md CLAUDE.md docs/current` | 已检查 | 命中项均为 not started / disabled / not integrated / not implemented / 禁止说明 / 历史语境，未发现正向误写。 |
| `rg "GateH|Gate I|GateJ|GateK|V1" docs/current/ARCHITECTURE.md docs/current/MODULES.md` | 已检查 | GateH / V1 均为 previous completed phase / archived history 或 GateI/GateJ completed 语境。 |

阶段与安全边界：

- GateK-PLAN 明确为 planning baseline，不是 GateK implementation started。
- AI 明确为 not started。
- DH runtime 明确为 not integrated / not connected to NQ。
- LIVE 明确为 disabled。
- 真实 OKX/Binance permission probe adapter 明确为 not implemented。

## NQ-CI-BASELINE-PLAN 验证记录（2026-06-14）

本轮是 CI planning-only / docs-only：只新增 `docs/current/NQ_CI_BASELINE_PLAN.md` 并同步 current docs 入口，不创建 `.github/workflows/**`，不修改 backend、frontend、research、scripts、deploy、测试代码、API、migration 或真实交易所 adapter。因此本轮未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`、Python `pytest / mypy / ruff`；这些命令只被规划为后续 `NQ-CI-BASELINE-IMPL` 的 CI baseline。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已检查 | 编辑前为空；编辑后仅允许 docs/current 文档变更。 |
| `git diff --check` | 通过 | 编辑前通过；编辑后复跑，若出现 LF/CRLF 提示按既有 Windows 工作区提示处理，不能写成 whitespace failure。 |
| `git diff --stat` | 已检查 | 用于确认 diff 只覆盖 docs/current 文档。 |
| `git ls-files .github` | 已检查 | 当前 tracked `.github` 只有 `.github/CODEOWNERS` 与 `.github/pull_request_template.md`；无 tracked workflow。 |
| `git ls-files backend/frontend/research \| head` | 原命令失败 | PowerShell 环境无 `head`；已用 `Select-Object -First 20` 等价复跑。 |
| `git ls-files backend/frontend/research \| Select-Object -First 20` | 已检查 | 确认 backend、frontend、research tracked 结构入口。 |
| `rg "name:|on:|jobs:" .github docs/current README.md` | 已检查 | 未发现 `.github/workflows` job 定义；命中主要来自文档模板和计划文本。 |
| CI baseline keyword scan | 已检查 | 用排除 `frontend/node_modules`、`target`、`build`、`dist` 的 `rg` 复跑，确认 Maven/npm/E2E/Python/Flyway/PostgreSQL/no-outbound/LIVE/NoReal 当前事实。 |
| 禁止范围 diff 检查 | 已检查 | `backend`、`frontend`、`research`、`scripts`、`deploy`、`.github`、`backend/**/db/migration` 均要求输出为空。 |

阶段与安全边界：

- NQ CI baseline 只写为 plan，不写成 implemented。
- `.github/workflows/**` 未创建。
- GateK implementation 明确为 not started。
- AI 明确为 not started。
- DH runtime 明确为 not integrated / not connected to NQ。
- LIVE 明确为 disabled。
- real exchange permission probe adapter 明确为 not implemented。
- 本轮未读取或输出真实 credential material。

## NQ-CI-BASELINE-IMPL 验证记录（2026-06-14）

本轮是 GateK CI baseline Batch 1 implementation：只新增 `.github/workflows/ci.yml`，并同步 `docs/current` 文档。Batch 1 只覆盖 GitHub Actions 最小 baseline：diff check、backend Maven test、frontend `npm ci` + build、research pytest / mypy / ruff。未实现 PostgreSQL/Flyway hardening、no-outbound guard、gitleaks / secret scan、dependency audit、frontend E2E hardening；未修改 backend、frontend、research、scripts、deploy、测试代码、API 或 migration。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| Workflow file path | 已新增 | `.github/workflows/ci.yml`。 |
| Workflow jobs | 已配置 | `diff-check`、`backend`、`frontend`、`research`；research job 对 mypy / ruff 使用 cache-independent flags，避免本地 cache 权限影响检查结论。 |
| GitHub Actions first run | Pending | 本地无法实际触发 GitHub Actions；需 push 或 PR 到 `dev` 后观察首次 `NQ CI Baseline` run。 |
| `git status --short` | 已检查 | 只允许 `.github/workflows/` untracked 与 `docs/current/NQ_CI_BASELINE_PLAN.md`、`docs/current/README.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md` 变更。 |
| `git diff --check` | 通过 | 退出码 0；仅出现 Windows LF/CRLF 工作区提示，不视为 whitespace failure。 |
| `git diff --stat` | 已检查 | tracked diff 只覆盖 4 个 docs/current 文档；`.github/workflows/ci.yml` 是新增 untracked 文件，需由 `git status --short` 确认。 |
| `git ls-files .github` | 已检查 | tracked `.github` 仍只有 `.github/CODEOWNERS` 与 `.github/pull_request_template.md`；新增 workflow 尚未 staged。 |
| `git diff -- backend` | 通过 | 输出为空，未改 backend。 |
| `git diff -- frontend` | 通过 | 输出为空，未改 frontend。 |
| `git diff -- research` | 通过 | 输出为空，未改 research。 |
| `git diff -- scripts` | 通过 | 输出为空，未改 scripts。 |
| `git diff -- deploy` | 通过 | 输出为空，未改 deploy。 |
| `git diff -- backend/**/db/migration` | 通过 | 输出为空，未新增或修改 migration。 |
| Forbidden keyword scan | 已检查 | `rg "skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current/NQ_CI_BASELINE_PLAN.md docs/current/TESTING.md docs/current/WORKLOG.md`：workflow 无命中；docs 命中均为禁止项、pending 风险、历史记录或安全边界说明。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor 23 modules `SUCCESS`，`BUILD SUCCESS`；未使用 `skipTests`。 |
| `npm ci` | 通过 | 在 `frontend` 下执行，依赖安装成功。 |
| `npm run build` | 通过 | 在 `frontend` 下执行，Vite build 成功；仅有 chunk size warning。 |
| `python -m pytest -q` | 通过 | 在 `research/py` 下执行，2 passed。 |
| `python -m mypy src` | 本机默认 cache 失败 | 本机 Python 3.14.2 + mypy 2.1.0 打开 sqlite cache 失败；未写成通过。 |
| `python -m mypy src --no-sqlite-cache` | 通过 | 类型检查本身通过，`Success: no issues found in 8 source files`；workflow 使用该命令，CI 仍需首次 GitHub Actions run 验证 Linux/Python 3.11 环境。 |
| `python -m ruff check .` | 本机 cache 写入失败 | 本机 `.ruff_cache` 临时文件写入被拒绝；未写成通过。 |
| `python -m ruff check . --no-cache` | 通过 | Lint 本身通过，`All checks passed!`；workflow 使用该命令，CI 仍需首次 GitHub Actions run 验证 Linux/Python 3.11 环境。 |

未覆盖项：

- PostgreSQL/Flyway：仍为 Batch 2 pending。
- no-outbound guard implementation：仍为 Batch 3 pending。
- gitleaks / secret scan / dependency audit：仍为 Batch 4 pending。
- frontend E2E hardening：仍为 Batch 5 pending。

安全边界：

- CI workflow 不注入交易所 credential。
- CI workflow 不设置 LIVE enablement。
- CI workflow 不包含真实交易所 diagnostic、order、cancel、transfer、withdraw 或 real adapter job。
- 本轮未读取、打印、复制或输出真实 credential material。

## NQ-CI-BASELINE-FIRST-RUN-FIX 验证记录（2026-06-14）

首次 GitHub Actions run `27496510294` 已触发，`diff-check`、`frontend`、`research` 通过，`backend` job 在 `Run backend tests` step 失败。失败命令为 `mvn -f backend/pom.xml test`，失败 module 为 `nq-app`；失败类包括 `MarketdataControllerLocalIntegrationTest`、`OkxBootstrapNoOutboundLocalContextTest`、`ResearchBacktestHappyPathLocalTest`，均为 `local` profile full Spring context 测试。

Root cause：GitHub runner 没有本地 PostgreSQL，而 `application-local.yml` 默认 datasource 指向 `jdbc:postgresql://localhost:5432/nexus_quant`；本机验证通过依赖本机已有 PostgreSQL。第一次修复在 backend job 增加 ephemeral PostgreSQL service 与对应 `NQ_DB_*` env。第二次 run 中 PostgreSQL 与 Flyway 已可用，但全新 DB 缺少 legacy `accounts` seed，`ResearchBacktestHappyPathLocalTest` 在 `SELECT account_id FROM accounts ORDER BY account_id LIMIT 1` 处失败。因此补充 CI-only seed watcher：在 Flyway 创建 `accounts` 表后插入一条最小 `PAPER / ACTIVE` legacy account。这不是 PostgreSQL/Flyway hardening：未新增 Flyway 专项验证 job，未新增 migration order / schema drift / repeatability 检查，Batch 2 仍 pending。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| Failed CI run summary | 已检查 | Run `27496510294`；failed job `Backend Maven test`；failed step `Run backend tests`；command `mvn -f backend/pom.xml test`。 |
| `gh run view 27496510294` | 已检查 | `diff-check`、`frontend`、`research` 成功；`backend` 失败。 |
| GitHub job logs | 已检查 | GitHub connector 读取 backend job logs；确认 `nq-app` local Spring context tests 因 runner 环境缺 PostgreSQL 失败。 |
| Fix | 已实施 | `.github/workflows/ci.yml` backend job 增加 `postgres:16` service、health check、`NQ_DB_URL` / `NQ_DB_USER` / `NQ_DB_PASSWORD`，并增加 CI-only seed watcher 插入最小 legacy account。 |
| First green run | 已确认 | Fix 已 push；后续 run `27496906788` 已在 `NQ-CI-BASELINE-FIRST-RUN-REVIEW` 中确认四个 job success。 |

边界：

- 未修改 backend / frontend / research 代码。
- 未修改测试代码。
- 未新增 API 或 migration。
- 未修改 scripts / deploy。
- 未加入 no-outbound guard implementation、gitleaks / secret scan、dependency audit 或 frontend E2E hardening。
- 未使用 `skipTests` 或 `continue-on-error`。
- 未注入真实 credential，未开启 LIVE，未调用真实交易所。

## NQ-CI-BASELINE-FIRST-RUN-REVIEW 验证记录（2026-06-14）

本轮只评审 `NQ CI Baseline` 首次 green run，不修改 workflow、backend、frontend、research、测试代码、API、migration、scripts 或 deploy。GitHub Actions run `27496906788` 已由 GitHub connector 复核，四个 Batch 1 job 均为 `completed / success`。

| 命令 / 检查 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run `27496906788` | 通过 | `Diff check`、`Backend Maven test`、`Frontend build`、`Research quality gate` 全部 success。 |
| Workflow scope review | 通过 | `.github/workflows/ci.yml` 只包含 Batch 1：diff check、backend Maven test、frontend build、research quality gate；未加入 PostgreSQL/Flyway hardening、no-outbound guard、secret scan、dependency audit 或 frontend E2E hardening。 |
| Backend job review | 通过 | 保留 `mvn -f backend/pom.xml test`；未使用 `-DskipTests`；未使用 `continue-on-error`；CI-only seed watcher 只等待 Flyway 创建 `accounts` 表并插入最小 `PAPER / ACTIVE` legacy account，不进入生产代码、migration 或 runtime seed 逻辑。 |
| Frontend job review | 通过 | 执行 `npm ci` 与 `npm run build`；未触碰 frontend B0 Draft PR、B1/B2/B3 页面施工或 AppProviders 全局替换。 |
| Research job review | 通过 | 执行 `pytest`、`mypy --no-sqlite-cache`、`ruff --no-cache`；no-cache 参数用于规避 runner / 本机 cache 权限噪音，不降低检查强度。 |
| Forbidden diff | 通过 | `git diff -- backend`、`frontend`、`research`、`scripts`、`deploy`、`backend/**/db/migration` 均为空。 |
| Forbidden keyword scan | 已检查 | workflow 未命中 `skipTests`、`continue-on-error`、`LIVE=true`、`LIVE_ENABLED`、真实交易所调用或真实 credential 字段；docs/current 命中均为禁止、历史或 pending 风险说明。 |

Review decision：Batch 1 baseline 可冻结为当前 `dev` 的最小 CI 基线。

仍 pending：

- Batch 2 PostgreSQL/Flyway hardening。
- Batch 3 no-outbound guard。
- Batch 4 secret scan / security guard。
- Batch 5 frontend E2E hardening。

## NQ-FRONTEND-B0-DESIGN-TOKENS-V2 验证记录（2026-06-14）

本轮是 frontend-only 改动：新增 v2 设计系统模块 `frontend/src/nq-design-system/`、自检演示页 `frontend/src/pages/dev/`，并在 `frontend/src/router/routes.tsx` 注册公开自检路由 `/dev/design-system`。接线作用域限定在该路由（v2 `ConfigProvider`/`applyNqCssVars`/`registerNqEchartsTheme`），未改全局 `AppProviders`、未动 v1 页面、未改后端/契约/migration。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 类型检查 0 error；`✓ built in ~1s`。>500 kB 单 chunk warning 为既有单包结构（echarts 在 v1 已打包），非本轮回归。 |
| 真机自检：`vite preview` + Playwright Chromium 截图 `/dev/design-system` | **通过** | 0 console error / 0 page error。INTL_CRYPTO 默认 `--nq-up=#33d6a6`(绿)/`--nq-down=#ff5c6c`(红)，`.nq-up` 实算 `rgb(51,214,166)`；切换 CN_STOCK 后翻转为 `--nq-up=#ff5c6c`(红)，`.nq-up` 实算 `rgb(255,92,108)`，数字 + K 线 swatch + ECharts PnL 柱同步翻转。 |
| 视觉断言（同上截图） | **通过** | LIVE（实心红+点）≠ PAPER（描边）；四件状态组件 + AppShell + 暗色分层 + CJK 14px + 数字 tabular-nums 正常；`body` 背景仍为 v1 `#0d1219`，作用域接线未泄漏到 v1。 |
| `npm run test:e2e` | **未运行** | 现有 E2E 多数 spec 依赖后端（`127.0.0.1:18888`，本环境未启动）；本轮只新增公开自检路由与独立模块，未改既有页面/全局主题，既有 E2E 语义不受影响。Playwright Chromium 已就绪，后端就绪后由用户侧执行全量 E2E。 |
| `git status --short` | 已检查 | 仅 `frontend/src/nq-design-system/`、`frontend/src/pages/dev/`（新增）、`frontend/src/router/routes.tsx`（修改）+ 本轮 `WORKLOG.md`/`TESTING.md`。`dist` / `tsbuildinfo` 已 gitignore，未入库；临时截图脚本已删除。 |

阶段与安全边界：

- 只做 B0（READY_NOW）基础系统，未做 B1+ 业务页面，未做 AI/Agent/DH 页面（B8 仍 BLOCKED）。
- 未接真实 WebSocket/SSE/交易所 adapter；实时数据本阶段只留 TanStack Query polling / 手动刷新规范。
- LIVE 明确为 disabled；未下单、撤单、转账、提现。
- 未读取、打印、输出真实 API key、secret、token、私钥、助记词、passphrase。

## NQ-FRONTEND-B0-LOGIN-AND-EXCEPTION-PAGES（B0.1）验证记录（2026-06-14）

本轮 frontend-only：重做登录页 + 四个异常页 + 404，复用 `@/nq-design-system` v2。在独立 git worktree（`feat/nq-frontend-b0-login-exception`，基于 `feat/nq-frontend-ds-v2`）执行，与 Codex 的 `dev` HEAD 隔离。未改后端/契约/migration/鉴权逻辑。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 0 error；`✓ built in 880ms`。>500 kB 单 chunk warning 为既有单包结构，非本轮回归。 |
| `login-page-smoke.spec.ts`（Playwright Chromium，外部 vite preview，无后端） | **1 passed** | 断言新登录页：NexusQuant + 定位 + 4 能力 + 空账号/密码 + 安全边界；并负向断言 `GateJ completed` / `Next: GateK-PLAN` / `DEV / PAPER / LOCAL controlled access` 不出现。 |
| 真机自检：Playwright Chromium 截图（9 路由） | **通过** | 0 console / 0 page error。登录页桌面端整体居中双区（非靠右）、主视觉无 Gate/DEV/PAPER/LOCAL；移动端上下堆叠、卡片置顶首屏；`/exception/auth` 三 reason 各异、`/exception/forbidden` 缺少角色+申请指引(403)、`/exception/error` Request ID+时间+返回入口(500)、`/exception/welcome` 第一步动作、404 统一异常层。暗色对比度 / 主色 #5b8cff / 中文 14px / 圆角 4-6 均符合 token。 |
| `npm run test:e2e`（全量） | **未跑** | 多数 spec 依赖后端（`:18888`，本环境未启动）；本轮仅单独运行无后端依赖的 login smoke 并通过，且未改既有业务页面/全局主题。 |
| `git status --short`（worktree） | 已检查 | 仅 B0.1 源文件变更；`tsc -b` 回生的 `playwright.config.*` / `vite.config.*`（CRLF）已 `git checkout` 还原，未入提交。 |

阶段与安全边界：

- 仍属 B0（READY_NOW）：登录页 + 四个异常页 + 404；未做 B1+ 业务页面，未做 AI/Agent/DH 页面（B8 仍 BLOCKED）。
- 未改鉴权逻辑（`authApi` / `auth-store` / `RequireAuth` 原样复用）；登录不展示默认凭证/明文，不新增凭证处理路径。
- 异常页本轮只交付表现层 + 公开路由；真实触发接线属后续切片。
- LIVE 明确为 disabled；未接真实 socket/交易所；未改后端 API。

## NQ-FRONTEND-TABLE-DENSITY-B0.2 验证记录（2026-06-14）

本轮 frontend-only：在 `@/nq-design-system` 新增表格密度 token + 列格式组件(数字右对齐/tabular/金额/百分比/状态/涨跌列),并在 `/dev/design-system` 自检。基于最新 `origin/dev` 在独立 worktree 执行。未改后端/契约/migration/GateK 事实源,未迁移既有业务页。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 0 error；`✓ built in 844ms`。>500 kB 单 chunk warning 为既有单包结构,非本轮回归。 |
| `npm run test:e2e -- tests/e2e/design-system-table-smoke.spec.ts tests/e2e/login-page-smoke.spec.ts --project=chromium`（dev server,无后端） | **2 passed** | 表格 smoke:密度 standard→compact class 切换、金额列 `64,231.50 USDT`、涨跌 up 色 `rgb(51,214,166)` 且 up≠down(独立于 success/danger);login smoke 保持通过。 |
| 真机自检：Playwright Chromium 截图 `/dev/design-system` | **通过** | 0 console / 0 page error;表格密度切换、数字右对齐 tabular、金额/百分比/涨跌/状态列渲染正常,涨跌色随惯例翻转。 |
| `npm run test:e2e`（全量） | **未跑** | 多数 spec 依赖后端(`:18888`,本环境未启动);本轮仅跑无后端依赖的 design-system / login smoke 并通过,未改既有业务页面/全局主题。 |

阶段与安全边界：

- B0.2 仅产出可复用基础能力(表格密度 + 列格式)+ 自检,未做 B1+ 业务页面,未迁移既有页面,未做 AI/Agent/DH 页面（B8 仍 BLOCKED）。
- 涨跌列必须使用行情方向色(`var(--nq-up/--nq-down/--nq-flat)`),与 success/danger 解耦,随惯例开关一处翻转。
- 未接真实 socket/交易所;未碰 LIVE;未改后端 API;未全局替换 AppProviders。

## NQ-FRONTEND-USE-LIVE-QUERY-B0.3 验证记录（2026-06-14）

本轮 frontend-only：新增 `useLiveQuery`(TanStack Query 之上的 polling/手动刷新/freshness 归一化)+ `/dev/design-system` 自检。基于最新 `origin/dev` 在独立 worktree 执行。当前阶段只 polling+手动刷新,**不接 WebSocket/SSE**;未改后端/契约/migration/GateK 事实源,未迁移既有业务页。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 0 error;`✓ built in ~1s`。>500 kB 单 chunk warning 为既有单包结构,非本轮回归。 |
| `npm run test:e2e -- design-system-live-query-smoke.spec.ts design-system-table-smoke.spec.ts login-page-smoke.spec.ts --project=chromium`（dev server,无后端） | **3 passed** | live-query smoke:fresh→disabled(暂停)→fresh(恢复+立即刷新)→error(模拟错误)→fresh,DataFreshness 同步 Fresh/Disabled/Error;table/login smoke 保持通过。 |
| 真机调试：Playwright Chromium `/dev/design-system` | **通过** | 0 console error;status 持续 fresh,轮询每 3s 更新,`Fresh (Xs ago · Yms)` latency 实测 387ms→219ms。 |
| `npm run test:e2e`（全量） | **未跑** | 多数 spec 依赖后端(`:18888`,本环境未启动);本轮仅跑无后端依赖的 design-system / live-query / login smoke 并通过,未改既有业务页面/全局主题。 |

阶段与安全边界：

- B0.3 仅产出实时数据抽象(`useLiveQuery`)+ 自检,未做 B1+ 业务页面,未迁移既有页面,未做 AI/Agent/DH 页面（B8 仍 BLOCKED）。
- 当前只 polling + 手动刷新,**不接 WebSocket/SSE**;失败经 `errorReason` 显式暴露,不静默。
- 未碰 LIVE;未接真实 socket/交易所;未改后端 API;未全局替换 AppProviders(QueryClient 复用既有 Provider)。

## NQ-FRONTEND-BACKTEST-DETAIL-VISUALIZATION-B1 验证记录（2026-06-14）

本轮新增回测详情可视化页(`/backtests/:backtestConfigId`)+ `BacktestCurveChart` 组件。**只复用真实 API**(backtest-configs / evaluations / marketdata datasets);权益/回撤时间序列后端无端点 → 防御式解析 report/metrics JSON,缺则显式 unavailable,**不编造**。基于最新 `origin/dev` 在独立 worktree 执行。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 0 error。>500 kB 单 chunk warning 为既有单包结构,非本轮回归。 |
| `npm run test:e2e -- design-system-backtest-chart-smoke.spec.ts design-system-live-query-smoke.spec.ts design-system-table-smoke.spec.ts login-page-smoke.spec.ts --project=chromium`（dev server,无后端） | **4 passed** | backtest chart smoke:样本权益/回撤渲染 canvas + 无序列显式 unavailable;其余 B0.x smoke 保持通过。 |
| 真机自检：Playwright Chromium `/dev/design-system` 回测曲线区 | **通过** | 0 console error;权益(primary 面积)/回撤(danger 面积,负值)/unavailable 占位渲染正常。 |
| BacktestDetailPage 浏览器 e2e | **未跑(诚实标注)** | 该页在 `RequireAuth` 下,依赖后端(`:18888`)+ 登录态,本环境均不可用;其组件(曲线/B0.2 列/useLiveQuery)已由 design-system smoke 覆盖,页面经 tsc 与 hook 顺序复核。需后端就绪环境补 backtest detail e2e。 |
| `npm run test:e2e`（全量） | **未跑** | 多数 spec 依赖后端,本环境未启动。 |

API/数据缺口(必须报告,未伪装):

- 权益/回撤**时间序列**:后端无 backtest 端点(仅聚合指标 + 不透明 report/metrics JSON)。本轮防御式解析 `equityCurve/equity/equitySeries`、`drawdownCurve/drawdown/drawdownSeries`,有则渲染、无则 unavailable。建议后端补 `GET /backtest-runs/{id}/equity-curve` 等端点或固化 reportJson 序列结构。
- `*Rate` 字段单位口径按比例值 ×100 展示并在 UI 注明,需后端确认口径。

阶段与安全边界:

- 只做 B1 回测详情;未做其它业务大页面,未迁移 Dashboard/Strategy/Risk/Paper。
- 未用 mock 假数据伪装后端就绪;缺字段/缺端点显式 empty/unavailable。
- 未接 AI/DH/LIVE/real exchange/WebSocket/SSE;未改后端 API;未全局替换 AppProviders。

## NQ-BACKTEST-EQUITY-DRAWDOWN-SERIES-API-PLAN 验证记录（2026-06-15）

本轮 **docs-only / planning-only**:为 B1 权益/回撤曲线规划后端时间序列契约,只读后端审计 + 写 plan 文档,**未改代码、未新增 migration、未实现 API**。因此未运行 `mvn -f backend/pom.xml test`、`npm run build`、`npm run test:e2e`(无代码变更)。

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| 只读后端审计 | 已执行 | `rg` + 读取 `BacktestRunController` / `BacktestFactQueryService` / `SimPnlSnapshot(Response)` / `JdbcSimPnlSnapshotRepository` / `DrawdownCalculator` / `V8` migration。 |
| 端点存在性 | 已确认 | `GET /api/backtest-runs/{runId}/pnl-snapshots` 已实现,返回 `sim_pnl_snapshots` 权益/PnL 序列。 |
| 表存在性 | 已确认 | `sim_pnl_snapshots`(V8 gate_f3),索引 `(backtest_run_id, snapshot_time)`。 |
| 结论 | 已记录 | 无需新增后端 API/表/migration;B1 曲线 unavailable 属前端未接线;前端消费(B1.1)为 planning 未实现。 |
| `git status --short` | 已检查 | 仅 5 个 docs/current 文档变更。 |

阶段与安全边界:

- planning only,未把前端 B1.1 写成 implemented;已存在的后端端点据实记录。
- 未改 Java/TS/Python;未新增/改 migration;未改前端页面;未接 AI/DH/LIVE/real exchange/socket。

## NQ-FRONTEND-BACKTEST-EQUITY-CURVE-WIRING-B1.1 验证记录（2026-06-15）

本轮前端 only:把回测详情权益/回撤曲线接到既有 `GET /api/backtest-runs/{runId}/pnl-snapshots`(equity 直接映射、drawdown 客户端派生 equity−运行峰值)。未新增后端 API/migration,未用假数据。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 0 error。 |
| `npm run test:e2e -- design-system-backtest-chart-smoke + live-query + table + login --project=chromium`（dev server,无后端） | **4 passed** | backtest chart smoke:有序列渲染 canvas + 无序列(无 run/空快照)显式 unavailable;其余 B0.x smoke 通过。 |
| BacktestDetailPage 页面级 e2e(有/无真实 pnl snapshots) | **未跑(诚实标注)** | 该页 `RequireAuth` 下依赖后端(`:18888`)+ 登录态,本环境不可用;曲线组件 + 映射由 design-system smoke + tsc 覆盖;页面级需带后端环境补 fixture(run + sim_pnl_snapshots / 空快照)。 |
| `npm run test:e2e`（全量） | **未跑** | 多数 spec 依赖后端。 |

阶段与安全边界:

- 曲线来源为真实端点 `pnl-snapshots`(sim_pnl_snapshots);无 run / 空快照显式 unavailable,**不编造**。
- drawdown 客户端派生 `equity − 运行峰值`(≤0),口径同后端 `DrawdownCalculator`。
- 未新增后端 API;未接 AI/DH/LIVE/real exchange/WebSocket/SSE;未全局替换 AppProviders;指标/快照/摘要区不回退。

## NQ-FRONTEND-BACKTEST-DETAIL-E2E-B1.2 验证记录（2026-06-15）

本轮新增 BacktestDetailPage 页面级 E2E(`backtest-detail-smoke.spec.ts`)+ 修复 `support.ts` 登录助手(B0.1 改版后旧英文选择器失效)。走真实后端 + 真实 fixture,未伪造。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `npm run build`（`tsc -b && vite build`） | **通过** | tsc 0 error;本轮仅改 tests/e2e,src 未动。 |
| `playwright test --list`（全 27 文件 / 31 用例,无 server） | **全部编译/收集通过** | 含新增 2 用例;确认 `support.ts` 修复 + 新 spec import/类型正确,且未破坏其它 spec 编译。 |
| 无后端 smoke(`login-page-smoke` + `design-system-backtest-chart` + `live-query` + `table`) | **4 passed** | 确认本轮改动未回退既有 backend-free smoke。 |
| `backtest-detail-smoke.spec.ts`（页面级,有/无 run 两例) | **本环境未运行(阻塞)** | 后端 `127.0.0.1:18888` 不可达(`curl` 000)。阻塞原因 = **后端未启动**,非测试失败、非 fixture 不足。需带后端环境执行。 |
| `npm run test:e2e`（全量） | **未跑** | 同因后端不可用。 |

阻塞 / fixture 条件(供带后端环境):

- 启动后端 `:18888` + PostgreSQL;`E2E_USERNAME/E2E_PASSWORD`(默认 admin/ChangeMe123!)。
- 用例 1(有快照)由 `prepareGateI2EvaluationFixture` 全自动 seed(config→run→start 执行写 sim_pnl_snapshots→evaluate)。
- 用例 2(无 run)由 `prepareGateI2BacktestTraceFixture` seed(仅 config,绑定 dataset/strategy version)。
- 跑:`npm run test:e2e -- tests/e2e/backtest-detail-smoke.spec.ts --project=chromium`。

数据 fixture 说明(诚实):

- "已评估但 sim_pnl_snapshots 为空的 run"无法经现有 API 复现(执行后的 run 必写逐 bar 权益快照),故"空序列→unavailable"用真实可达的**无 run/无评估**路径(`所选评估缺少 backtestRunId`)验证。组件级空/无序列 unavailable 由 `design-system-backtest-chart-smoke` 覆盖。
- `support.ts` 旧英文登录选择器(`Username/Password/Sign in`)在 B0.1 改版后已失效,本轮修复为 `账号/密码/登录`,使全部 backend 集成 e2e 在后端可用时能正常登录前置。

阶段与安全边界:

- 未改后端/migration/research/deploy/scripts;未新增后端 API;未接 AI/DH/LIVE/real exchange/socket;未伪造数据。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-FIX 验证记录（2026-06-16）

修复 Batch 2D `nq-app` context smoke 首次 CI 失败（`AdapterBackedTradingVenueGateway: venue must not be blank`）。仅改 1 个 nq-app test 文件，未改生产代码 / migration / workflow。

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-app -am test -Dtest=NqAppContextPostgresSmokeTest -Dsurefire.failIfNoSpecifiedTests=false` | **BUILD SUCCESS** | `NqAppContextPostgresSmokeTest` tests=1 / failures=0 / errors=0 / **skipped=1**。本地无 `nq.app.context.smoke.required`，类被 `@EnabledIfSystemProperty` 跳过；仅证明编译 + Surefire 选择。 |
| `git status --short` / `git diff --check` / `git diff --stat` | **通过** | 仅 `NqAppContextPostgresSmokeTest.java` 改动（+75 / -3）；无 whitespace 错误。 |
| `git diff -- backend/**/db/migration` / `frontend` / `research` / `scripts` / `deploy` | **空** | 未触达禁止范围。 |

修复要点：

- 失败根因：生产 `AdapterBackedTradingVenueGateway`（eager singleton）在 context refresh 期对每个 `TradingAdapter` bean 调用 `venue()` 建路由表；裸 `@MockitoBean` adapter 返回 blank venue → `venue must not be blank`。
- 修复（test-only）：嵌套 `@TestConfiguration` 以预 stub 的 mock 覆盖 `okxTradingAdapter` / `binanceTradingAdapter`，`venue()` 固定为 `CI-SMOKE-FAKE-OKX` / `CI-SMOKE-FAKE-BINANCE`；`spring.main.allow-bean-definition-overriding=true` 仅覆盖这两个具名 bean。
- 断言：`verify(..., never()).placeOrder/cancelOrder/getOrder(...)` + 对 WS client 的 `verifyNoInteractions`（gateway 合法调用 `venue()`，不能对 adapter 用 blanket `verifyNoInteractions`）。

CI 待确认（real PostgreSQL context 启动）：

- 本地无法验证 CI required path；需下一次 GitHub Actions `postgres-flyway` job 的 `Run nq-app PostgreSQL context smoke`（`nq.app.context.smoke.required=true`）确认 **skipped=0 / errors=0**。
- 在该 run 变绿并经 freeze review 前，Batch 2D 不得写成 FIRST GREEN / FROZEN / ACCEPTED。

阶段与安全边界：

- 未改后端生产代码 / migration / research / deploy / scripts / workflow；未新增 API；未用 `local` profile；未触发 `AuthSeedConfiguration`；未创建 seed users / accounts / exchange accounts / credential rows；未接 AI/DH/LIVE/real exchange；未读取或输出真实 credential material。

## NQ-CI-POSTGRES-FLYWAY-2D-FIRST-RUN-REVIEW (+ FIRST-RUN-FIX #2) 验证记录（2026-06-16）

评审 first-run fix（commit `7156b32c`）后的 CI run，结果 FAIL，暴露第二个根因并应用第二次 test-only 修复。

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| GitHub Actions run `27592872701`（commit `7156b32c`，push，dev） | **completed / failure**（1m54s） | venue 错误已消失；context 越过 gateway。 |
| `PostgreSQL / Flyway smoke` job `81577141123` | **failure**，仅 `Run nq-app PostgreSQL context smoke` | Flyway V1-V31 / schema artifacts / repository smoke（`JdbcRepositoryPostgresSmokeTest` 1/0/0/0）均仍 success。 |
| `NqAppContextPostgresSmokeTest`（CI） | tests=1 / **skipped=0** / failures=0 / **errors=1** | active profile `ci-app-smoke`；真实执行（非 skip）。 |
| 第二根因 | `securityFilterChain` 装配失败 | `webEnvironment=NONE` → 非 web → `HttpSecurity`（`@ConditionalOnWebApplication(type=SERVLET)`）缺失。 |
| `mvn ... -Dtest=NqAppContextPostgresSmokeTest -Dsurefire.failIfNoSpecifiedTests=false`（本地，第二次修复后） | **BUILD SUCCESS** | tests=1 / failures=0 / errors=0 / **skipped=1**（本地无 CI DB props，跳过；仅证明编译 + 选择）。 |
| `git status/diff --check/--stat`、migration/frontend/research/scripts/deploy diff | **通过 / 空** | 仅 test + docs 改动；未触达禁止范围。 |

第二次修复（test-only）：`webEnvironment = NONE` → `WebEnvironment.MOCK` 并删除 `spring.main.web-application-type=none`，加载完整 servlet web 上下文（含 Spring Security filter chain），不起 server / 不开端口 / 不调 controller；对齐既有 `local` full-context 测试（默认 `MOCK`）。

CI 待确认：真实 servlet-web context 启动需下一次 GitHub Actions `postgres-flyway` job 的 `Run nq-app PostgreSQL context smoke`（`nq.app.context.smoke.required=true`）确认 **skipped=0 / errors=0**。在该 run 变绿前，Batch 2D 不得写成 FIRST GREEN / FROZEN / ACCEPTED。

CI log hygiene（复核）：本次失败 step 输出仅 Spring/Surefire stack trace 与 `@TestPropertySource` 属性回显（含 fake `ci-app-smoke` master-key / security secret 占位值，非真实 credential）；service-container 一次性 `POSTGRES_PASSWORD` 仍由 GitHub "Initialize containers" 在 step 前回显（平台行为，P3 残留，已记录）。无真实 credential material、无完整 JDBC password / 连接串经 step 主动输出。

阶段与安全边界：未改后端生产代码 / migration / research / deploy / scripts / workflow；未新增 API；未用 `local` profile；未触发 `AuthSeedConfiguration`；未创建 seed users / accounts / exchange accounts / credential rows；未接 AI/DH/LIVE/real exchange；未读取或输出真实 credential material。

---

## NQ-DOCS-GOVERNANCE-G5-DIRECTORY-CLOSURE-PREFLIGHT（2026-06-19）

结论：**PASS / READY FOR REVIEW**。docs-only preflight，**未运行**后端/前端/Python/CI 测试（无代码、workflow、migration、依赖或运行时逻辑变更）。

git 实测验证：

```text
Migration Map exact query:
recommended_action = FUTURE_MOVE_CANDIDATE
migration_batch    = G5

G5_FUTURE_MOVE_COUNT = 0
FUTURE_MOVE_SECTIONS = 1
FUTURE_MOVE_SECTIONS_BATCH = G4 only
G5_TEXT_LINES = 4

G5 candidate matrix:
total = 0
ELIGIBLE_FOR_G5_IMPLEMENTATION = 0
BLOCKED_PER_FILE = 0
RETAIN_IN_PLACE = 0
ordinary inbound links = 0
fragment inbound links = 0
target conflicts = 0
```

边界验证：本轮未移动、删除、重命名、复制、归档、stub 化任何文档；未创建 target 目录或 canonical 文件；未修改 G1 五份冻结对象、G2/G3/G4 冻结对象、docs/gates、docs/archive、.agents、templates、workflow、backend、frontend、research、scripts、deploy、migration 或依赖。G6 仍为 **NOT STARTED / DEFAULT EMPTY**。

阶段状态：**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED**；**G4 CI evidence routing = FROZEN / ACCEPTED**；**G5 directory closure preflight = IMPLEMENTED / READY FOR REVIEW**；**G6 deletion batch = NOT STARTED / DEFAULT EMPTY**。NQ GateK CI mainline = COMPLETED / ACCEPTED；Batch 5A = FROZEN / ACCEPTED；Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED；Batch 5B-SMOKE = BLOCKED BY 5B-ENV；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。

补充验证（2026-06-19）：`git diff --check` exit 0；G1 五份冻结对象 diff 为空；`docs/gates docs/archive .agents templates` diff 为空；`.github/workflows/ci.yml` diff 为空；`backend frontend research scripts deploy` diff 为空；`backend/**/db/migration` diff 为空。新增 preflight 文件单独检查 trailing whitespace = 0，single LF at EOF。

---

## NQ-CI-SECURITY-BATCH-5B-SMOKE-IMPLEMENTATION-PLAN（2026-06-21）

结论：**IMPLEMENTATION PLAN READY / READY FOR REVIEW**。docs-only implementation plan，本轮未执行 implementation，未新增 CI job，未新增测试，未修改 workflow / backend / migration / frontend / research / scripts / deploy / `.env.example`，未运行或触发 GitHub Actions。

计划验收要点：

- Batch 5B-ENV = **FROZEN / ACCEPTED**。
- Batch 5B-SMOKE-PREFLIGHT = **REVIEWED / ACCEPTED**。
- Batch 5B-SMOKE implementation = **NOT STARTED**。
- 下一轮 job name 定稿为 **ci-security-smoke**。
- P2 已转化为 implementation execution checklist；P3 job name drift 已关闭。
- NoReal permission probe remains SKIPPED；No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。

本轮验证范围：文档路径、阶段状态、禁止边界、入口一致性和 scoped diff。未运行 Maven / npm / pytest / GitHub Actions，原因是本轮只改 docs-current planning/status 文档，不改代码、workflow、测试、migration 或运行时配置。

---

## NQ-GATEL-PLAN（2026-06-22）

结论：**PLANNING ONLY / READY FOR REVIEW**；GateL implementation **NOT STARTED**。docs-only planning，本轮未实现任何 GateL 能力，未改代码 / API / migration / workflow / frontend / research / scripts / deploy / `.env.example`，未运行或触发 GitHub Actions。

本轮验证范围（只读）：

- adapter / marketdata / permission probe / paper execution / risk / ledger 现有 no-real 资产盘点（确认均为 no-real / stub / fixture / disabled 边界，非待新建）。
- GateL planning 文档路径、阶段状态、禁止边界、10 项硬性问题答案、入口一致性。
- scoped diff 仅落在 `docs/current/`。

未运行 Maven / npm / pytest / mypy / ruff / GitHub Actions，原因：本轮只改 docs/current planning/status 文档，不改代码、workflow、测试、migration 或运行时配置；GateL implementation NOT STARTED。

边界确认：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。NoReal permission probe remains SKIPPED。

---

## NQ-GATEL-CANONICAL-ROUTE-SYNC（2026-06-22）

结论：**PASS / DOCS-ONLY**。docs-only route sync，修正 GateL canonical 定义冲突；未改代码 / API / migration / workflow / 测试 / frontend / research / scripts / deploy，未运行或触发 GitHub Actions。

本轮验证范围（只读 + 一致性核对）：

- grep GateL / AI Paper Trading / AI 小资金 / DH runtime / LIVE / real exchange，定位冲突点（root README、docs-current README / ROADMAP / STATUS / GATEL_PLAN）。
- canonical 一致性核对：6 份 docs 的 GateL 定义统一为 **No-Real Exchange / MarketData Readiness**；旧口径「GateL = AI Paper Trading」已全部改写；AI Paper Trading → GateM（NOT STARTED）；AI 小资金 LIVE → GateN；美股 → GateO；A 股 → GateP。
- 残留冲突核对：docs/current 内 `GateL：AI Paper Trading` / `GateL 进入 AI Paper Trading` 旧定义已清零（root README 无 GateL 定义，未改）。
- scoped diff 仅落在 `docs/current/`（root README 未改）。

未运行 Maven / npm / pytest / mypy / ruff / GitHub Actions，原因：本轮只改 docs/current 路线/定义文档，不改代码、workflow、测试、migration 或运行时配置。

边界确认：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。NoReal permission probe remains SKIPPED。
