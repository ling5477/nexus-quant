# REPO-SIZE-AUDIT Report

> 审查日期：2026-05-28
> 审查分支：`dev`
> 范围限定：只清理 Git 追踪中的生成物、缓存、日志、本地环境文件和历史大文件线索；不修改业务代码、API、migration 或前端页面功能；不删除 `docs/current` 与 `docs/gates` 的正式文档。

## 1. 结论

- 当前 Git object pack 大小为 `51.35 MiB`，`.git` 目录本地实际大小约 `56.17 MiB`，与 GitHub 仓库 size 约 `53 MB` 基本一致。
- 当前 Git 追踪文件总量约 `5.37 MiB`；当前工作区大头是本地 `frontend/node_modules`、`frontend/dist` 等未追踪目录，不应提交。
- 本轮发现并清理 2 个已被 Git 追踪的 TypeScript build info 生成物：`frontend/tsconfig.app.tsbuildinfo`、`frontend/tsconfig.node.tsbuildinfo`。
- 当前历史对象中仍存在过去提交过的 `frontend/node_modules/`、`frontend/dist/`、`frontend/test-results/*.zip` 等大 blob。若目标是降低 GitHub 显示仓库 size，需要单独安排 `git filter-repo` 历史重写；本轮不执行历史改写。
- `.gitignore` 已补齐 GateJ-FREEZE 证据包、构建产物、日志、数据库 dump、归档包和本地密钥规则；未忽略 `*.sql`，Flyway migration 仍可正常提交。

## 2. 仓库当前大小

`git count-objects -vH`：

| 项 | 值 |
| --- | --- |
| loose objects | `2928` |
| loose size | `4.35 MiB` |
| packed objects | `26200` |
| packs | `9` |
| pack size | `51.35 MiB` |
| garbage | `1` |
| garbage size | `120.00 KiB` |

本地目录大小：

| 路径 | 大小 |
| --- | ---: |
| `.git` | `56.17 MiB` |
| Git 追踪文件合计（清理后） | `5.37 MiB` |
| 工作区 `frontend` | `200.98 MiB` |
| 工作区 `backend` | `10.17 MiB` |
| 工作区 `docs` | `1.30 MiB` |
| 工作区 `artifacts` | `1.85 MiB` |

说明：`artifacts/` 是本地交付物目录，已被 `.gitignore` 忽略。本轮未删除本地文件。

## 3. 当前 Git 追踪文件大小 Top 50

以下为清理前扫描的 Git 追踪文件 Top 50，用于定位已追踪生成物。

| Rank | Size | Path |
| ---: | ---: | --- |
| 1 | `0.1100 MiB` | `docs/current/WORKLOG.md` |
| 2 | `0.1053 MiB` | `frontend/package-lock.json` |
| 3 | `0.0921 MiB` | `.agents/skills/ui-ux-pro-max/data/styles.csv` |
| 4 | `0.0791 MiB` | `docs/gates/gate-d/WORK.md` |
| 5 | `0.0745 MiB` | `frontend/src/pages/paper-trading/PaperTradingPage.tsx` |
| 6 | `0.0648 MiB` | `docs/gates/gate-c/WORK.md` |
| 7 | `0.0623 MiB` | `docs/gates/gate-i/WORKLOG.md` |
| 8 | `0.0473 MiB` | `docs/current/PRE_FREEZE_AUDIT_REPORT.md` |
| 9 | `0.0442 MiB` | `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsClient.java` |
| 10 | `0.0425 MiB` | `docs/archive/rc1/RC1_7_PACKAGE_MAPPING.md` |
| 11 | `0.0420 MiB` | `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxExchangeAdapter.java` |
| 12 | `0.0416 MiB` | `.agents/skills/ui-ux-pro-max/scripts/design_system.py` |
| 13 | `0.0400 MiB` | `frontend/tsconfig.node.tsbuildinfo` |
| 14 | `0.0399 MiB` | `docs/archive/scripts/gated_okx_dome_verify.ps1` |
| 15 | `0.0372 MiB` | `docs/gates/gate-h/WORKLOG.md` |
| 16 | `0.0369 MiB` | `docs/gates/gate-d/DECISIONS.md` |
| 17 | `0.0335 MiB` | `docs/current/TESTING.md` |
| 18 | `0.0327 MiB` | `backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/application/backtest/BacktestExecutionService.java` |
| 19 | `0.0316 MiB` | `frontend/src/pages/trading/TradingWorkbenchPage.tsx` |
| 20 | `0.0304 MiB` | `.agents/skills/ui-ux-pro-max/data/typography.csv` |
| 21 | `0.0296 MiB` | `.agents/skills/ui-ux-pro-max/data/ui-reasoning.csv` |
| 22 | `0.0290 MiB` | `frontend/src/pages/backtests/BacktestsPage.tsx` |
| 23 | `0.0290 MiB` | `backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingController.java` |
| 24 | `0.0284 MiB` | `.agents/skills/ui-ux-pro-max/data/products.csv` |
| 25 | `0.0273 MiB` | `backend/nq-backtest/src/test/java/com/guidinglight/nexusquant/research/application/backtest/BacktestExecutionServiceTest.java` |
| 26 | `0.0264 MiB` | `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceExchangeAdapter.java` |
| 27 | `0.0264 MiB` | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java` |
| 28 | `0.0237 MiB` | `docs/current/DB_SCHEMA.md` |
| 29 | `0.0223 MiB` | `backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/TradingVerificationController.java` |
| 30 | `0.0223 MiB` | `frontend/src/pages/strategies/StrategiesPage.tsx` |
| 31 | `0.0218 MiB` | `frontend/src/pages/marketdata/MarketdataPage.tsx` |
| 32 | `0.0215 MiB` | `frontend/src/pages/accounts/AccountsPage.tsx` |
| 33 | `0.0211 MiB` | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/TradingVerificationControllerLocalTest.java` |
| 34 | `0.0208 MiB` | `docs/gates/gate-i/TESTING.md` |
| 35 | `0.0204 MiB` | `.agents/skills/impeccable/SKILL.md` |
| 36 | `0.0200 MiB` | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/OrderCommandServiceTest.java` |
| 37 | `0.0190 MiB` | `backend/nq-ledger/src/main/java/com/guidinglight/nexusquant/ledger/service/TradeLedgerPostingService.java` |
| 38 | `0.0189 MiB` | `backend/nq-eval/src/test/java/com/guidinglight/nexusquant/research/application/eval/BacktestEvaluationServiceTest.java` |
| 39 | `0.0188 MiB` | `docs/gates/gate-b/WORK.md` |
| 40 | `0.0188 MiB` | `docs/current/API.md` |
| 41 | `0.0187 MiB` | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/StrategyScheduleScanServiceTest.java` |
| 42 | `0.0185 MiB` | `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxWsClient.java` |
| 43 | `0.0184 MiB` | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java` |
| 44 | `0.0180 MiB` | `backend/nq-adapter-binance/src/test/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsClientTest.java` |
| 45 | `0.0180 MiB` | `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/paper/PaperRunStabilityCheckServiceTest.java` |
| 46 | `0.0179 MiB` | `.agents/skills/ui-ux-pro-max/data/ux-guidelines.csv` |
| 47 | `0.0171 MiB` | `backend/nq-infra/src/main/resources/db/migration/V5__gate_e_schema_contract_alignment.sql` |
| 48 | `0.0170 MiB` | `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java` |
| 49 | `0.0169 MiB` | `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/BinanceWsOrderAccelerationService.java` |
| 50 | `0.0167 MiB` | `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/BinanceRestReconcileService.java` |

## 4. 目录大小统计

### 4.1 Git 追踪文件按顶层目录统计

| Directory | Size |
| --- | ---: |
| `backend` | `2.58 MiB` |
| `docs` | `1.30 MiB` |
| `.agents` | `0.82 MiB` |
| `frontend` | `0.66 MiB` |
| `AGENTS.md` | `0.01 MiB` |
| `research` | `0.01 MiB` |
| `.env.example` | `0.01 MiB` |
| `scripts` | `0.01 MiB` |
| `CLAUDE.md` | `0.01 MiB` |
| `README.md` | `<0.01 MiB` |
| `.gitignore` | `<0.01 MiB` |
| `docker-compose.yml` | `<0.01 MiB` |
| `.github` | `<0.01 MiB` |

### 4.2 工作区目录统计

| Path | Size | Exists | Git policy |
| --- | ---: | --- | --- |
| `frontend` | `200.98 MiB` | yes | 目录可存在，但其中 `node_modules/dist/test-results` 不提交 |
| `frontend/node_modules` | `198.90 MiB` | yes | 不提交 |
| `frontend/dist` | `1.41 MiB` | yes | 不提交 |
| `frontend/test-results` | `<0.01 MiB` | yes | 不提交 |
| `frontend/playwright-report` | `0 MiB` | no | 不提交 |
| `frontend/coverage` | `0 MiB` | no | 不提交 |
| `frontend/.vite` | `0 MiB` | no | 不提交 |
| `backend` | `10.17 MiB` | yes | `target/` 不提交 |
| `docs` | `1.30 MiB` | yes | `docs/current` 与 `docs/gates` 正式文档保留 |
| `docs/current` | `0.34 MiB` | yes | 当前事实源，保留 |
| `docs/gates` | `0.82 MiB` | yes | 历史冻结快照，保留 |
| `artifacts` | `1.85 MiB` | yes | 本地交付物，不提交 |
| `freeze-evidence` | `0 MiB` | no | GateJ-FREEZE 证据包，不提交 |
| `backups` | `0 MiB` | no | 本地备份，不提交 |

## 5. 违规追踪文件检查与清理

本轮检查的 Git 追踪路径模式：

- `backend/**/target/`
- `frontend/node_modules/`
- `frontend/dist/`
- `frontend/test-results/`
- `frontend/playwright-report/`
- `frontend/coverage/`
- `frontend/.vite/`
- `frontend/*.tsbuildinfo`
- `*.jar`, `*.war`, `*.class`
- `*.zip`, `*.tar.gz`, `*.dump`, `*.backup`, `*.bak`
- `*.log`, `*.out`, `*.err`, `nohup.out`
- `.env`, `.env.*`（保留 `.env.example` 与 `frontend/.env.example` 模板）
- `Private_key.txt`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `*.keystore`
- `backups/`, `freeze-evidence/`, `artifacts/`

发现并清理的已追踪文件：

| Path | 原因 | 处理 |
| --- | --- | --- |
| `frontend/tsconfig.app.tsbuildinfo` | TypeScript 增量编译缓存 | `git rm --cached`，本地文件保留 |
| `frontend/tsconfig.node.tsbuildinfo` | TypeScript 增量编译缓存 | `git rm --cached`，本地文件保留 |

未发现仍被 Git 追踪的 `target/`、`node_modules/`、`dist/`、`test-results/`、`playwright-report/`、`coverage/`、`.vite/`、日志、dump、密钥、证书或 GateJ-FREEZE 证据目录。

## 6. `.gitignore` 新增规则

本轮新增或补齐规则：

```gitignore
frontend/playwright-report/
frontend/coverage/
frontend/.vite/
frontend/*.tsbuildinfo
*.tsbuildinfo
*.out
*.err
nohup.out
Private_key.txt
*.pem
*.key
*.p12
*.jks
*.keystore
*.jar
*.war
*.class
*.zip
*.tar.gz
*.dump
*.backup
*.bak
!backend/.mvn/wrapper/maven-wrapper.jar
backups/
freeze-evidence/
```

确认事项：

- 未新增 `*.sql` 忽略规则，Flyway migration 仍可提交。
- `.env.example` 与 `frontend/.env.example` 继续允许提交，用于环境模板。
- `backend/.mvn/wrapper/maven-wrapper.jar` 继续允许提交，避免 Maven wrapper 被误忽略。

## 7. 历史大文件与 `git filter-repo` 判断

历史 blob Top 30 中出现以下不应长期保留在历史里的路径：

| Size | Path |
| ---: | --- |
| `23.1870 MiB` | `frontend/node_modules/@rolldown/binding-win32-x64-msvc/rolldown-binding.win32-x64-msvc.node` |
| `9.0576 MiB` | `frontend/node_modules/lightningcss-win32-x64-msvc/lightningcss.win32-x64-msvc.node` |
| `8.6904 MiB` | `frontend/node_modules/typescript/lib/typescript.js` |
| `7.2343 MiB` | `frontend/node_modules/antd/dist/antd-with-locales.js.map` |
| `7.0968 MiB` | `frontend/node_modules/antd/dist/antd-with-locales.min.js.map` |
| `6.5154 MiB` | `frontend/node_modules/antd/dist/antd.js.map` |
| `6.4716 MiB` | `frontend/node_modules/antd/dist/antd.min.js.map` |
| `5.9253 MiB` | `frontend/node_modules/typescript/lib/_tsc.js` |
| `5.9233 MiB` | `frontend/node_modules/.vite/deps/antd.js.map` |
| `4.8771 MiB` | `frontend/node_modules/antd/dist/antd-with-locales.js` |
| `4.3881 MiB` | `frontend/node_modules/antd/dist/antd.js` |
| `3.1097 MiB` | `frontend/node_modules/.vite/deps/@ant-design_icons.js.map` |
| `3.1031 MiB` | `frontend/node_modules/.vite/deps/antd.js` |
| `2.5993 MiB` | `frontend/node_modules/antd/node_modules/@ant-design/icons/dist/index.umd.js` |
| `2.5683 MiB` | `frontend/node_modules/@ant-design/icons/dist/index.umd.js` |
| `2.3245 MiB` | `frontend/node_modules/.vite/deps/@ant-design_icons.js` |
| `1.7880 MiB` | `frontend/node_modules/typescript/lib/lib.dom.d.ts` |
| `1.7643 MiB` | `frontend/node_modules/antd/dist/antd-with-locales.min.js` |
| `1.7066 MiB` | `frontend/test-results/strategies-query-GateG-3-strategies-query-登录后查询策略列表并校验列表渲染-chromium/trace.zip` |
| `1.6329 MiB` | `frontend/test-results/research-detail-GateG-4B-research-detail-登录后打开研究配置详情抽屉-chromium/trace.zip` |
| `1.5468 MiB` | `frontend/test-results/research-query-GateG-3B-research-query-登录后查询研究配置列表并校验列表渲染-chromium/trace.zip` |
| `1.4676 MiB` | `frontend/node_modules/antd/dist/antd.min.js` |
| `1.4207 MiB` | `frontend/node_modules/.vite/deps/react-dom_client.js.map` |
| `1.3663 MiB` | `frontend/node_modules/@babel/parser/lib/index.js.map` |
| `1.3370 MiB` | `frontend/node_modules/playwright/lib/transform/babelBundleImpl.js` |
| `1.3289 MiB` | `frontend/dist/assets/index-oibiYc-b.js` |
| `1.3289 MiB` | `frontend/dist/assets/index-BZaufrIM.js` |
| `1.2016 MiB` | `frontend/node_modules/vite/dist/node/chunks/node.js` |
| `1.1656 MiB` | `frontend/test-results/trade-validation-query-Gat-72da0-tion-query-登录后查询订单并打开交易验证详情-chromium/trace.zip` |
| `1.0915 MiB` | `frontend/test-results/strategies-detail-GateG-4A-strategies-detail-登录后打开策略详情抽屉-chromium/trace.zip` |

判断：

- 需要 `git filter-repo` 才能降低 GitHub 显示仓库 size，因为当前大头在历史 blob，不在当前追踪文件。
- 本轮不执行 `git filter-repo`，原因是历史改写会重写 commit hash，需要团队统一窗口、通知所有协作者重新 clone 或硬切分支。
- 建议另开维护任务执行历史清理，目标路径至少包括：

```powershell
git filter-repo --path frontend/node_modules --path frontend/dist --path frontend/test-results --path frontend/playwright-report --path frontend/coverage --path frontend/.vite --invert-paths
```

执行历史改写前必须先备份远端、确认没有未合并分支依赖旧历史，并在改写后重新跑：

```powershell
git count-objects -vH
mvn -f backend/pom.xml test
Set-Location frontend
npm run build
```

## 8. GateJ-FREEZE 证据包提交规则

GateJ-FREEZE 后续会产生 1h / 24h / 7d 连续运行验收记录、日志、截图、数据库 dump、证据包或临时归档。以下内容不得提交到 Git：

- `freeze-evidence/`
- `artifacts/`
- `backups/`
- `*.dump`
- `*.backup`
- `*.bak`
- `*.zip`
- `*.tar.gz`
- `*.log`
- `*.out`
- `*.err`
- `nohup.out`

如果 GateJ-FREEZE 需要保留结论，应提交精简后的 Markdown 验收摘要或冻结文档；原始日志、dump、截图包和长期运行证据应放在 Git 外部存储，并在文档中记录校验摘要、路径约定和生成命令，不把大文件提交进仓库。

## 9. 验证记录

本轮已执行：

```powershell
git branch --show-current
git status --short
git count-objects -vH
git ls-files
git rev-list --objects --all
git rm --cached -- frontend/tsconfig.app.tsbuildinfo frontend/tsconfig.node.tsbuildinfo
```

最终验证：

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status` | 通过 | 变更范围限定为 `.gitignore`、`docs/current/REPO_SIZE_AUDIT_REPORT.md`、两个 `tsbuildinfo` 索引删除；未包含业务代码变更。 |
| `git ls-files <forbidden-patterns>` | 通过 | 返回 `NO_TRACKED_FORBIDDEN_FILES`。 |
| `git ls-files -ci --exclude-standard` | 通过 | 无输出，说明当前没有仍被 Git 追踪且被忽略规则命中的文件。 |
| `git check-ignore -v ...` | 通过 | `tsbuildinfo`、`frontend/dist`、`artifacts`、`freeze-evidence`、`backups` 均命中预期 `.gitignore` 规则。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor 23 个 module 全部 `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。存在既有 Maven settings warning、SLF4J/Mockito/JDK 动态 agent warning，本轮不处理。 |
| `Set-Location frontend; npm run build` | 通过 | `tsc -b && vite build` 成功；输出 `dist/assets/index-CLLFLWD4.js 1,478.51 kB / gzip 446.09 kB`；仍有既有 Vite chunk > 500 kB 警告。 |

`npm run build` 会生成 `frontend/dist/` 与 `frontend/*.tsbuildinfo`，这些本地文件已被 `.gitignore` 覆盖，不会再次进入 Git 追踪。

## 10. 边界确认

- 未修改业务代码。
- 未修改 API。
- 未新增或修改 Flyway migration。
- 未修改前后端业务逻辑。
- 未删除 `docs/current` 正式文档。
- 未删除 `docs/gates` 历史冻结快照。
- 未创建 `docs/gates/gate-j/`。
- 未执行 GateJ-FREEZE 连续运行验收。
- 未接入 AI、AI 信号、AI 自动交易或 AI Paper Trading。
