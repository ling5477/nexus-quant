# GateP Freeze Readiness Review

任务名称：`NQ-GATEP-BATCH-6-FREEZE-READINESS-REVIEW`

审查日期：2026-07-05

结论：`CONDITIONAL PASS`（有条件通过）/ `FIX REQUIRED`（需要修复）。代码、测试、API、前端和 Python offline foundation 证据未发现 P0/P1 阻断；但 root `README.md`、`docs/current/FACT_SOURCE_INDEX.md`、`docs/current/ROADMAP.md` 仍存在 GateP Batch 1 / Python Research 旧口径，冻结 closeout 前必须先做一个最小 docs consistency fix。本结论不是 GateP `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。后续 Batch 6A 已按本报告要求修复 current fact-source drift，状态见本文 §12.1。

## 1. GateP Current State

- GateO：`FROZEN`（已冻结）/ `ACCEPTED`（已接受）。
- GateP：Batch 1-5 均已有代码或文档证据；GateP 总体仍不是 `FROZEN` / `ACCEPTED`。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED`（未开始）/ mock-test-support only where applicable。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Data Quality diagnostic、permission probe observability、preflight readiness 和 Python offline foundation 都不是 trading authorization、ML ready 或 live execution ready。

## 2. Batch 1 Evidence

- Commit：`b856cf07 docs(gatep): reconcile current fact source and status`。
- Scope：root `README.md` 与 `docs/current` fact source / status reconciliation。
- Evidence：`git show --stat --name-status b856cf07` 显示只修改文档，新增 `docs/current/FACT_SOURCE_INDEX.md`，未改 backend / frontend / research / scripts / deploy / `.github` / migration。
- Boundary：Batch 1 是 fact-source closeout，不是 GateP implementation、freeze 或 acceptance。

## 3. Batch 2 Evidence

- Commit：`9a58b888 feat(marketdata): add read-only data quality overview`。
- Scope：新增 `GET /api/marketdata/quality/overview` 后端只读 API。
- Code evidence：
  - `MarketdataQualityOverviewService` 标注 `@Transactional(readOnly = true)`，只聚合本地 read model。
  - `JdbcMarketdataQualityOverviewRepository` 只读本地 DB facts。
  - `MarketdataQualityOverviewResponse` 注释明确不返回 `tradingReady` / `liveReady` / `authorizedForTrading`。
- Test evidence：
  - `MarketdataQualityOverviewServiceTest` 覆盖空数据、coverage gap、ingestion failure、stale/multi-scope。
  - `MarketdataControllerTest` 覆盖安全响应字段和 no-side-effect。
- Boundary：不新增 migration，不触发 adapter/provider/public outbound，不读取 credential，不授权交易。

## 4. Batch 3 Evidence

- Commit：`3d3ef6e7 feat(frontend): add marketdata quality center view`。
- Scope：前端 Data Quality Center 与 Runtime release matrix。
- Code evidence：
  - `frontend/src/api/marketdata.ts` 只读调用 `/marketdata/quality/overview`。
  - `/marketdata` 页面文案明确 Data Quality Center 只表示诊断，不代表 trading authorization、LIVE、permission probe 或 real provider 可用。
  - `/runtime/readiness` release matrix 将 Data quality、public marketdata、permission probe、private trading、LIVE、AI、DH runtime 分开解释。
- Test evidence：
  - `marketdata-data-quality-center-smoke.spec.ts` 断言无 `tradingReady` / `liveReady` / `authorizedForTrading` 文案，无 write endpoint、permission probe、order/cancel/withdraw/transfer 和真实 exchange host 调用。
  - `runtime-readiness-overview-smoke.spec.ts` 断言 release matrix 的 fail-closed 边界。
- Boundary：不新增后端 API，不接真实交易所，不启用 LIVE / AI / DH runtime。

## 5. Batch 4 Evidence

- Commit：`d4592e3e feat(trading): add read-only preflight readiness baseline`。
- Scope：新增 `GET /api/trading/preflight/readiness` 只读权限与风险前置基线。
- Code evidence：
  - `TradingPreflightController` 只委托 read-only service，不调用下单、撤单、permission probe、adapter 或 credential material。
  - `TradingPreflightReadinessService` 聚合 account metadata、credential metadata、permission probe latest summary 和 Data Quality diagnostic；默认 fail-closed。
  - Response 固定展示 `LIVE_DISABLED`、`REAL_PROVIDER_NOT_IMPLEMENTED`、`PRIVATE_TRADING_NOT_IMPLEMENTED`、`RISK_PREFLIGHT_BLOCKED` 等 blocker，不提供授权字段。
- Test evidence：
  - `TradingPreflightReadinessServiceTest` 覆盖账号/凭证缺失、credential metadata-only、Data Quality diagnostic 不构成交易授权。
  - `TradingPreflightControllerTest` 断言响应不包含 `tradingReady` / `liveReady` / `authorizedForTrading`。
- Boundary：不实现真实 permission probe，不调用 OKX/Binance/Bybit/Gate/Coinbase/Kraken HTTP client，不读取 credential material，不下单。

## 6. Batch 5 Evidence

- Commit：`e57d9b0c feat(research): add reproducible offline experiment foundation`。
- Scope：Python offline research dataset manifest / experiment metadata / evaluation skeleton / CLI summary。
- Code evidence：
  - `research/py/src/nq_research/dataset/manifest.py` 构建 dataset manifest 与 checksum。
  - `research/py/src/nq_research/experiment/metadata.py` 构建 experiment metadata 和参数 hash。
  - `research/py/src/nq_research/evaluation/metrics.py` 提供 evaluation metrics skeleton。
  - `research/py/src/nq_research/reporting/summary.py` 与 `cli.py` 输出本地 CSV summary。
- Test evidence：
  - `test_research_foundation.py` 覆盖 manifest 稳定性、checksum、metadata 参数 hash、evaluation skeleton、CLI 输出、缺字段 CSV、空 CSV。
  - 测试 monkeypatch `socket.create_connection` 和 `subprocess.run`，保证 no-network / no-Java-runtime boundary。
- Boundary：不新增后端 API、前端页面、migration、真实交易所 SDK、Java runtime 写链路、LIVE、AI runtime 或 DH runtime；不是 ML ready 或 live execution ready。

## 7. Boundary Review

| Boundary | Review result |
| --- | --- |
| LIVE | `DISABLED`，未发现本轮新增启用路径。 |
| AI | `NOT STARTED`，未发现 AI runtime / AI signal / AI execution 接入。 |
| DH runtime | `NOT INTEGRATED`，Integration-1 仍为 mock-test-support / no-runtime 语境。 |
| RealClient / real provider | `NOT IMPLEMENTED`，未发现 Batch 1-5 新增 real provider enablement。 |
| private trading adapter | `NOT IMPLEMENTED`，未发现私有交易 adapter 启用。 |
| real permission probe | `NOT IMPLEMENTED`，只存在 metadata / observability / fail-closed 语境；Batch 4 不调用真实 probe。 |
| credential material | 未发现输出或读取凭证明文；Batch 4 只读 credential metadata / summary。 |
| order / cancel / transfer / withdraw | 未发现新增或触发路径。 |
| migration | `git diff -- "backend/**/db/migration"` 为空；Batch 1-5 commit scope 未新增 migration。 |
| CI workflow | `git diff -- .github` 为空；Batch 1-5 未改 workflow。 |
| Python outbound / trading SDK | Batch 5 为本地 CSV / 文件产物，测试阻断 network 和 Java subprocess。 |
| readiness / diagnostic wording | 代码与大部分 current docs 明确不等于 trading authorization；但部分入口文档仍有 GateP Batch 1 / Python Research 旧口径，见 P1。 |

## 8. API / Frontend / Python / Docs Consistency Review

- API：`docs/current/API.md` 已记录 Batch 2 `GET /api/marketdata/quality/overview` 和 Batch 4 `GET /api/trading/preflight/readiness`，并明确二者只读、不读取 credential material、不表示 trading authorization。
- Frontend：Batch 3 页面与 E2E 均表达 diagnostic-only / no-write / no-real-exchange / no authorization。
- Python：Batch 5 offline foundation 与测试一致；不写成 ML ready 或 live execution ready。
- Docs current：
  - `docs/current/README.md`、`docs/current/STATUS.md`、`docs/current/API.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md` 基本能表达 Batch 2-5 已完成且 GateP 未冻结。
  - `README.md` 仍写 GateP 当前只进入 Batch 1，并写 Python Research 仍需 dataset manifest / evaluation skeleton / experiment metadata。
  - `docs/current/FACT_SOURCE_INDEX.md` 仍写 GateP `PLANNING / BATCH 1 FACT SOURCE CLOSEOUT`，并把 Python Research 写成 minimal skeleton / dataset manifest、evaluation skeleton、experiment metadata 均为后续工作。
  - `docs/current/ROADMAP.md` 仍写 GateP 当前仅 Batch 1，下一批入口为 Batch 2。

## 9. Test Evidence Review

本轮已实际复跑建议验证命令；下表中的 `PASS`（通过）表示命令成功或审查通过：

| Command | Result | Notes |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-api,nq-core,nq-app -am test` | PASS | 23 个 reactor module `SUCCESS`；`nq-core` 89 tests / 0 failures，`nq-app` 105 tests / 0 failures / 3 skipped；保留既有 SLF4J / Mockito dynamic agent warning。 |
| `npm --prefix frontend run build` | PASS | `tsc -b && vite build` 通过；保留 Vite large chunk warning。 |
| `python -m pytest research/py` | PASS | 10 passed。 |
| `python -m ruff check research/py` | PASS | All checks passed。 |
| `python -m mypy research/py` | PASS | Success: no issues found in 20 source files。 |

本轮也已复核用户要求的 git / diff / rg 命令：

- `git status --short`：写前 clean。
- `git log --oneline -20`：最近提交包含 Batch 1-5 与 GateO archive closeout。
- `git diff --check`：写前 PASS。
- `git diff --stat`：写前 empty。
- `git diff -- backend` / `frontend` / `research` / `scripts` / `deploy` / `.github` / `backend/**/db/migration`：写前均 empty。
- 指定 GateP / LIVE / AI / DH / RealClient / real provider / permission probe / trading authorization / Python research 等关键词 `rg`：PASS / REVIEWED；输出很大，已抽取 current 文档漂移与边界语境。
- 写后 `git status --short`：仅 5 个允许的 current docs 修改和新增 `docs/current/GATEP_FREEZE_READINESS_REVIEW.md`。
- 写后 `git diff --check`：PASS；仅 Git 提示 LF/CRLF 工作区换行 warning，非 whitespace error。
- 写后 forbidden-area diff：`backend` / `frontend` / `research` / `scripts` / `deploy` / `.github` / `backend/**/db/migration` 均 empty。

## 10. P0 / P1 / P2 / P3 Findings

### P0

- 无。

### P1

1. Current fact-source drift blocks freeze closeout.
   - Evidence：`README.md:9-10`、`README.md:81-82` 仍将 GateP 写成只到 Batch 1，并写 Python Research 仍缺 dataset manifest / evaluation skeleton / experiment metadata。
   - Evidence：`docs/current/FACT_SOURCE_INDEX.md:22-32`、`docs/current/FACT_SOURCE_INDEX.md:57-95` 仍将 GateP 写成 Batch 1 fact source closeout，并把 Batch 2-5 写成后续入口。
   - Evidence：`docs/current/ROADMAP.md:44-49` 仍写 GateP 当前仅 Batch 1，下一批入口为 Batch 2。
   - Impact：freeze closeout 会以 current docs 为事实源；这些旧口径会让后续任务误判 Batch 2-5 未完成，或误判 Python offline foundation 尚未具备 manifest / metadata / evaluation skeleton。
   - Required fix：另起最小 docs-only 修复批次，允许修改 root `README.md`、`docs/current/FACT_SOURCE_INDEX.md`、`docs/current/ROADMAP.md`，并同步 `docs/current/README.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`；不得改代码。

### P2

- 无代码 P2。文档层面除了 P1 old-current drift，未发现 API / TESTING / WORKLOG / STATUS 将 diagnostic 写成 authorization。

### P3

- 非阻断 warning：Maven 保留既有 SLF4J / Mockito dynamic agent / JDK dynamic agent warning；frontend build 保留 Vite large chunk warning。
- Broad `rg` 输出较大，审查按 current / code / tests / gates 语境抽样定位；未将历史冻结或否定语境自动判为失败。

## 11. Freeze Readiness Verdict

`NQ-GATEP-BATCH-6-FREEZE-READINESS-REVIEW：CONDITIONAL PASS / FIX REQUIRED`

含义：代码、测试与大部分文档证据支持 GateP Batch 1-5 已形成冻结前基线；但 current fact-source drift 是 freeze closeout 前必须关闭的 P1 文档阻断。GateP 现在不能直接进入 freeze closeout，必须先做最小 docs consistency fix。

## 12. Required Fixes Before Freeze

推荐最小修复任务：

```text
任务名称：
NQ-GATEP-BATCH-6A-CURRENT-FACT-SOURCE-DRIFT-FIX

任务类型：
DOCS_ONLY + CURRENT_FACT_SOURCE_FIX + FREEZE_READINESS_UNBLOCK

目标：
修复 GateP Batch 6 freeze readiness review 发现的 P1 文档漂移，只更新 root README、docs/current/FACT_SOURCE_INDEX.md、docs/current/ROADMAP.md 以及必要的 docs/current/README.md、STATUS.md、TESTING.md、WORKLOG.md 记录；不得修改 backend/frontend/research/scripts/deploy/.github/migration。

必须修复：
1. root README 不再写 GateP 当前仅 Batch 1。
2. root README 不再写 Python Research 仍缺 dataset manifest / evaluation skeleton / experiment metadata。
3. FACT_SOURCE_INDEX 更新为 Batch 1-5 已完成、Batch 6 review conditional pass / fix required，GateP 仍未 FROZEN / ACCEPTED。
4. ROADMAP 更新为 Batch 1-5 completed / freeze readiness conditional pass，下一步是 Batch 6A docs-only drift fix 后再进入 freeze closeout。
5. 保留 LIVE DISABLED、AI NOT STARTED、DH runtime NOT INTEGRATED、Integration-1 NOT STARTED / mock-test-support only、RealClient / real provider / private trading adapter / real permission probe NOT IMPLEMENTED。

验证：
git status --short
git diff --check
git diff --stat
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- "backend/**/db/migration"
rg -n "GateP|Batch 1|Batch 2|Batch 3|Batch 4|Batch 5|FROZEN|ACCEPTED|LIVE|AI|DH|Integration-1|RealClient|real provider|private trading|permission probe|trading authorization|ML ready|live execution" README.md docs/current

最终状态只能写：
NQ-GATEP-BATCH-6A-CURRENT-FACT-SOURCE-DRIFT-FIX：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT
```

## 12.1 Batch 6A Follow-up Status

`NQ-GATEP-BATCH-6A-CURRENT-FACT-SOURCE-DRIFT-FIX` 已执行：root `README.md`、`docs/current/FACT_SOURCE_INDEX.md`、`docs/current/ROADMAP.md` 的 GateP Batch 1 / Python Research 旧口径已修复，并最小同步 `docs/current/README.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。本 follow-up 只关闭 P1 current fact-source drift，不把 GateP 写成 `FROZEN`（已冻结）或 `ACCEPTED`（已接受），不改变 LIVE / AI / DH / RealClient / real provider / private trading / real permission probe 禁止边界。

## 13. Next Concrete Action

Batch 6A 修复通过后，再另起 `NQ-GATEP-FREEZE-CLOSEOUT` 或复跑 freeze readiness review；不要在 Batch 6A 内把 GateP 写成 `FROZEN` / `ACCEPTED`。

## 14. Commit Recommendation

推荐 commit message：

```text
docs(gatep): review freeze readiness
```

本提交只应包含本审查报告和允许的 `docs/current` 指针/验证记录；不应包含业务代码、测试代码、migration、CI workflow 或生成产物。
