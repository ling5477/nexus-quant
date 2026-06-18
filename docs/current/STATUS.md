# Current Status

## 项目定位

NexusQuant 是通用量化交易平台，第一阶段聚焦虚拟币量化交易，后续复用账户、行情、策略、回测、评估、发布、风控、交易、复盘、AI 信号协议等底座扩展到美股和 A 股。

## 当前完成状态

- GateK CI Batch 5A no-backend E2E implementation completed：**IMPLEMENTED / READY FOR FIRST-RUN**。在 `.github/workflows/ci.yml` 新增独立 job `frontend-no-backend-e2e`（`permissions: contents: read`、`timeout-minutes: 15`、Node 22、`npm ci`、`npx playwright install --with-deps chromium`、`npm run build`、loopback `vite preview` 127.0.0.1:5179）并新增 `frontend/playwright.ci.config.ts`（Chromium only / workers=1 / retries=0 / trace=screenshot=video=off / line reporter / 不用 storageState / `reuseExistingServer:false` / `forbidOnly:true`）。只跑四个 no-backend spec（仓库真实路径 `frontend/tests/e2e/`，非任务书写的 `frontend/e2e/`）：`login-page-smoke`、`design-system-table-smoke`、`design-system-live-query-smoke`、`design-system-backtest-chart-smoke`；命令显式列出四 spec，config `testMatch` 二次限定，`--list` 证明 Total: 4 tests in 4 files，未扩大到其余 23 个 spec。本地真实验证：`npm run build` 成功、四 spec **4 passed (10.2s)**、无 artifact 生成/上传。**未**启动 backend/PostgreSQL/Flyway/认证/seed/账户写入/外网/真实 provider，**未**调用 `loginToConsole()`，**未**修改 Batch 4C redaction 规则。所有 authenticated/backend-required spec 仍不在 required gate；trace/video/screenshot/artifact 上传保持禁用。**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**；Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。详见 `NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md`。
- GateK CI Batch 5 frontend E2E plan review completed：**PASS / ACCEPTED**。**Batch 5 plan = ACCEPTED AS IMPLEMENTATION BASELINE**；**Batch 5A = READY FOR IMPLEMENTATION**（最终 allowlist = `login-page-smoke` / `design-system-table-smoke` / `design-system-live-query-smoke` / `design-system-backtest-chart-smoke`，经源码核实纯 loopback / 无后端 / 无 token / 无账户写入 / 无外网，无存疑 spec 需移出）；**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。runtime no-outbound P1 仅阻断 5B，不阻断纯 no-backend 5A。本轮只更新 `docs/current`，未修改 workflow/spec/前端/后端/seed/migration/依赖，未运行 Playwright/backend/PostgreSQL/Flyway/浏览器安装，未上传任何 artifact。详见 `NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md`。Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-A = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- GateK CI Batch 5 frontend E2E hardening plan completed：**PASS / READY FOR REVIEW**。Batch 5 = **PLAN ONLY / NOT IMPLEMENTED**；首个建议基线是 4 个 no-backend Playwright spec 的 bounded allowlist，backend-required E2E 必须先完成 job-local PostgreSQL/Flyway、同步 auth/legacy fixture、真实 backend/preview readiness、runtime no-outbound enforcement 与 fresh-DB repeat proof。当前 27 个 spec 未在本轮执行，`backtest-detail-smoke.spec.ts` 页面级 case = **PENDING BACKEND ENV / NOT VERIFIED IN CI**；不得写成 passed。本轮未上传 trace/screenshot/video/HTML report/test-results/raw logs。Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-A = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- legacy console gate completed。
- RC1 completed and frozen。
- GateH-PRE completed。
- DOC-CLEAN completed。
- BASELINE-FIX completed。
- GateH-PLAN completed。
- GateH-1-WO completed。
- GateH-2-WO completed。
- GateH-3-WO completed。
- GateH completed。
- GateI-PLAN completed。
- GateI-1-WO completed。
- GateI-2-WO completed.
- GateI-3-WO completed。
- GateI-3-FIX completed。
- GateI-4-WO completed。
- GateI-4-FIX completed。
- GateI completed。
- GateJ-PLAN completed。
- GateJ-1-WO completed。
- GateJ-2-WO completed。
- GateJ-3-WO completed。
- DOC-CLEAN-2 completed。
- PRE-FREEZE-CODE-AUDIT completed。
- PRE-FREEZE-CODE-AUDIT second pass completed。
- AUDIT-FIX completed。
- GateJ-FREEZE-FIX completed。
- GateJ-FREEZE-FIX-SECOND-PASS completed。
- GateJ-FREEZE-FIX-3 completed。
- GateJ-FREEZE-FIX-4 completed。
- GateJ-FREEZE-FIX-5 local release reproducibility fix completed（ECS 复验已通过）。
- GateJ-FREEZE-FIX-6 local freeze sync guard and console text cleanup completed（ECS 复验已通过）。
- GateJ-FREEZE-FIX-7 local freeze console UI text and filter control cleanup completed（ECS 复验已通过）。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- GateJ completed。

## 当前执行状态

- 2026-05-30 GateJ-FREEZE UI + UX smoke review 已完成并形成 `GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md`：本次为 Chrome 浏览器只读巡检，不是运行稳定性验收；Functional stability: PASS，UI/UX professionalism: FAIL；当前 7d 连续运行验收继续，不因 UI/UX 问题中断。
- GateJ-FREEZE 最终验收事实：30m observation PASS，1h acceptance PASS，24h acceptance PASS，7d acceptance PASS，GateJ completed: yes。
- FIX-5 / FIX-6 / FIX-7 已完成并通过 ECS 复验；安全组已确认 `5179` 只允许本人 IP 访问。
- UI/UX smoke review 发现的 Dashboard 工程实现文案、freeze 写按钮可点击、Instrument Catalog 同步入口未前端禁用、Paper Trading / Schedules / Runs 缺摘要等问题应作为 post-freeze remediation 跟踪，不应写成后端或运行稳定性 FAIL。
- Current stage: GateJ completed。
- Next: GateK-PLAN。
- GateK implementation: not started。GateK-PLAN 只做 GateJ 后的 planning / architecture / productization / deployment / observability / security boundary 收口，不代表实现已启动。
- GateK CI Batch 4F-A dependency audit input / toolchain preflight freeze review completed：**PASS / ACCEPTED / FROZEN**（preflight `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`；preflight review `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md`；freeze review `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md`）。Batch 4F plan review = **PASS / ACCEPTED**；Batch 4F plan = **ACCEPTED AS IMPLEMENTATION BASELINE**；Batch 4F execution sequence = **SYNCED / ACCEPTED**；Batch 4F-A preflight = **FROZEN / ACCEPTED**。Python local audit = **NOT READY**，P2 保留为 4F-B execution prerequisite；4F-B 若覆盖 Python，必须使用已确认的真实解释器路径或 `actions/setup-python@v5`。4F-B sanitized summary 的 10 个 mandatory fields 已冻结，`scope` 为 bounded field；vulnerability findings 仅 report-only/advisory。Batch 4F-B 至 4F-F = **NOT STARTED**。Batch 4C overall = **FROZEN / ACCEPTED**；Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 5 = **PENDING**。本轮未改 workflow / code / test / migration / frontend / research / scripts / deploy，未运行 dependency audit、scanner、SBOM、构建或测试，未开启 LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。
- GateK CI Batch 4C overall security artifact/log redaction baseline freeze review completed：**PASS / ACCEPTED / FROZEN**（overall review `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`；Batch 4C-B pre-upload artifact redaction gate **FROZEN / ACCEPTED**，immutable green run `27701669084`；Batch 4C-C log redaction proof **FROZEN / ACCEPTED**，immutable green run `27732660516`，7/7 jobs green，14 类 high-risk pattern 真实值命中 = 0；4C-B / 4C-C P0/P1=0）。Batch 4C overall = **FROZEN / ACCEPTED**；Static workflow assertion 仍 **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 4F-A preflight 后续已 **FROZEN / ACCEPTED**，Python local audit = **NOT READY**，4F-B 至 4F-F = **NOT STARTED**；Batch 5 **PENDING**。本轮未改 workflow / code / test / migration / frontend / research / scripts / deploy，未上传 logs artifact，未开启 LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。
- GateJ-3-WO 已完成。
- PRE-FREEZE-CODE-AUDIT second pass 已完成：无 P0；Claude 第一轮 P1-1 / P1-2 验证缺口已由 Codex 实际重跑关闭；P1-3 不阻塞；P1-4 已闭环 GATEJ_FREEZE_ACCEPTANCE_TEMPLATE。详见 `PRE_FREEZE_AUDIT_REPORT.md` 与 `PRE_FREEZE_AUDIT_FIX_PLAN.md`。
- FULL_SECURITY_AUDIT 报告中的 P1 已由 AUDIT-FIX 关闭：旧 OKX dome 验收脚本已移出 `scripts/` 可执行区并归档到 `docs/archive/scripts/`，原路径只保留阻断 stub；`/__gated/**` 仍仅为历史路径。
- GateJ-FREEZE-FIX 已修复 ECS freeze 登录页敏感信息暴露与登录 401 根因：生产构建登录页不再展示 legacy console gate、本地端口、默认账号密码、认证 API 与 Authorization header 示例；freeze profile 不再执行 local 默认用户 seed；新增 `scripts/seed-freeze-user.sh` 通过服务器环境变量生成 BCrypt hash 并写入验收用户。
- GateJ-FREEZE-FIX-SECOND-PASS 已完成：`frontend/dist` 与新 release 解压内容未命中敏感登录页泄露串；freeze compose/template 使用 `NQ_PROFILE=freeze`；Git 未追踪 release/dist/env/jar/zip/dump/log/evidence；详见 `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`。
- GateJ-FREEZE-FIX-3 已完成：`scripts/seed-freeze-user.sh` 不再使用跨 statement 生命周期不稳定的临时表，改为单个 `psql` session/transaction 内 upsert freeze 用户、启用用户、重绑角色并校验 BCrypt；部署文档明确禁止手工 `source .env.freeze`，特殊字符密码建议通过 seed 脚本交互式隐藏输入。
- GateJ-FREEZE-FIX-4 已完成：修复 `seed-freeze-user.sh` 交互式隐藏输入路径，避免 `read` 后视觉换行进入命令替换返回值并被单行校验误判；ECS 仍需用真实 Bash/TTY 复验后才能继续 GateJ-FREEZE 首次启动验收。
- GateJ-FREEZE-FIX-5 本地修复已完成：新增 `.gitattributes` 强制 shell/yaml/PowerShell 换行策略，仓库 `scripts/*.sh` 已归一为 LF，`scripts/build-freeze-release.ps1` 在 zip 前对 staging `scripts/*.sh` 做 LF 兜底转换；新 release 本地解压检查确认 zip 内 `.sh` 不含 CRLF。ECS 仍需重新上传新 release 后直接执行 `bash -n`、`backup-db.sh`、`freeze-health-loop.sh` 与 `health-check-7d.log` 写入 `UP` 验证，未通过前不得进入 GateJ-FREEZE 首次启动验收。
- GateJ-FREEZE-FIX-6 本地修复已完成：freeze profile 默认禁用 Instrument Catalog 外部同步，`/api/instruments/sync` 在禁用或 Binance exchangeInfo 失败时返回 409 受控错误，不再进入 `api_unhandled_exception`；前端 Instrument Catalog 与 Header 已清理 `GateH-PRE` / `LOCAL` 可见残留，dist/release 扫描未命中禁止串。ECS 仍需重新上传新 release 后验证浏览器同步 Catalog 不再显示 internal server error，日志不再出现 `api_unhandled_exception path=/api/instruments/sync`。
- GateJ-FREEZE-FIX-7 本地修复已完成：清理 freeze 控制台页面中 `GateH-1`、`GateH-2`、`GateI-3`、`GateH-PRE`、`GateG`、`LOCAL`、`Gate-3` 与开发接口说明残留；将 Marketdata / Strategies / Schedules / Runs / Paper Trading / Evaluations / Publishes 等页面枚举筛选改为 Ant Design Select，并将 Marketdata 与 Backtests 时间输入改为 DatePicker 后转换 ISO 字符串提交。ECS 仍需重新上传新 release 后浏览器复验页面文案与筛选控件，并确认 Instrument Catalog sync 仍是受控提示。
- E2E/Vite 本地端口已从 `4173` 调整为 `5179`，避开 Windows TCP excluded range `4141-4240`；AUDIT-FIX 完整 E2E 已通过。
- 后端 `mvn -f backend/pom.xml test` BUILD SUCCESS（23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors）。
- 前端 `npm run build` 通过（仍有 Vite chunk > 500 kB P2 警告）。
- E2E `npm run test:e2e` 本轮实际执行通过：24 passed / 1 skipped / 0 failed；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，GateJ 主链未 skip。
- Python `pytest / mypy / ruff` 本轮实际执行通过：pytest 2 passed，mypy 8 source files no issues，ruff all checks passed。
- Flyway 当前版本 V25（gate j3 paper run recovery stability）。
- GateJ-FREEZE 连续运行验收已完成：起点 2026-05-29 14:53:20 +08:00；7d checkpoint 2026-06-05 14:53:24 +08:00；health-loop 样本数 2025；health-loop 最新样本 2026-06-05 15:40:58 +08:00。
- after-7d checkpoint 中 `docker compose logs --since=7d` 不被当前 Compose 识别，已补跑合法窗口 `--since=168h`；`/opt/nexus-quant/freeze-evidence/reports/after-7d/nq-app-error-scan-168h.txt` 的 `wc -l = 0`，未命中 `api_unhandled_exception`、`Binance request failed`、`status=451`、`BCrypt`、`Encoded password`、`authentication required`、`ERROR`、`Exception`、`OutOfMemory`、`OOM`。
- nginx / nq-app / postgres 均为 Up 7 days，其中 postgres healthy；18888 health 为 UP，5179 health 为 UP；after-7d.sql 已生成，266K；磁盘约 30G 可用，使用率约 21%；Swap 0B 使用；5179 安全组已确认只允许本人 IP 访问。
- UI/UX smoke review：Functional stability PASS，UI/UX professionalism FAIL；该问题不影响 GateJ-FREEZE 稳定性验收，但必须作为 post-freeze remediation，不能宣称 UI/UX 专业化已完成。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- No new backend/frontend business code, API, migration, deployment, AI, DH, or real trading path was added in GateJ-FREEZE-FINAL-DOC。
- Codex workflow documentation hardening completed：已新增 NQ/DH 插件路由、Workflow Router Skill 说明、任务模板和 Project Instructions 文档；本轮只做规则文档固化，未修改业务代码、部署配置、API、migration、AI、DH 或真实交易路径。
- Codex workflow documentation consistency fix completed：已将 Router Skill 状态与 `AGENTS.md` active skills 对齐，`nq-dh-workflow-router` 作为当前项目 active skill 使用；`CODEX_PROJECT_INSTRUCTIONS.md` 已补充 Router 前置分类规则。本轮只修改 Markdown 文档，未修改业务代码、部署配置、API、migration、AI、DH 或真实交易路径。
- Codex workflow output format consistency fix completed：标准输出字段已统一为 `Findings`，不再把 `Summary` 作为必填输出字段。本轮只修改 Markdown / Skill 文档，未修改业务代码、部署配置、API、migration、AI、DH 或真实交易路径。
- Credential revocation governance Batch 5-C completed：后端已接入 `credential_status` 生命周期字段、active material 默认只读取 `ACTIVE`、新增 `revoke / disable / expire` 最小 API 与 append-only audit log 写入；本轮未新增 migration、前端、Python、部署、AI、DH、LIVE 或真实交易所私有链路。
- AI 尚未开始。GateK-PLAN 仅做边界规划，不启动 AI 信号、AI runtime 或 AI Paper Trading。
- GateJ 不是 AI 阶段。GateJ 只做 Paper Trading 稳定运行。

## NQ / DH 三轮审计同步（2026-06-11，DOC-SYNC-GATEK-PRE-AND-INT0-REGISTRATION）

本轮只做事实源文档同步，不修改任何代码，不启动 Integration-0 实现，不启动 GateK 实现。

三轮只读审计已完成：

- 第一轮：NQ 全仓只读审计 completed。
- 第二轮：DH 全仓只读审计 completed。
- 第三轮：NQ-DH 联合边界审计 completed（见 DH 仓库 `docs/current/NQ_DH_INTEGRATION_SECURITY_AUDIT_REPORT.md`）。
- 三轮审计汇总 completed。

NQ 当前阶段口径（必须按此理解，不得误判）：

- Current: GateJ completed。
- Next: GateK-PLAN。
- GateK implementation: not started。
- AI: not started。
- DH: not integrated（NQ 侧仍无 DH 入站端点、无 DH client、无 feedback outbox）。
- LIVE: disabled。
- Integration-0: allowed only as contract / mock / documentation work line, not runtime integration。

Integration-0 允许范围（仅文档与契约线，不是真实集成）：

- 只读边界规划、契约冻结、mock / stub / contract test、安全策略文档。
- 不允许真实联调、NQ RealClient、真实 Provider、真实交易、读取凭证、读写 NQ DB、开启 LIVE。

DH 侧事实（来自第二轮与第三轮审计）：

- DH 当前无真实 NQ 调用、无真实 Provider、无交易能力。
- DH P1-1 / P1-2 / P1-3 已关闭（认证+租户隔离、HMAC/timestamp/nonce 防重放+source allowlist+payload 上限、ProviderTrustPolicy）。
- DH P1-4 部分关闭：限流（rate limit）、内存仓储上限（memory cap）、replay nonce 持久化仍缺失；该残留不阻塞 Integration-0，但阻塞 Integration-1。

## NQ-DH Integration-0 safety gate（2026-06-12，CLOSED / ACCEPTED）

- Integration-0 safety gate close / acceptance：**PASS / CLOSED / ACCEPTED**，详见 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`。
- 已完成链路：三轮审计 + 汇总 → 事实源同步 → 契约冻结 → contract test 矩阵设计 → contract test 代码实现（NQ 16 + DH 16）→ implementation review（PASS）→ 本次验收关闭。
- 验收依据：NQ `mvn -f backend/pom.xml test` BUILD SUCCESS（nq-app 51 tests / 0 failures，Integration-0 16 passed，ArchUnit 全绿）；DH `mvn test` BUILD SUCCESS（dh-domain 86 tests / 0 failures，Integration-0 16 passed，ArchitectureTest 12 条全绿，PostgresContainerSmokeTest 既有环境性 skip）。两侧均覆盖 INT0-T01..T15，含 negative path、audit event shape、forbidden side-effect。
- 边界保持：Runtime integration NOT STARTED；Integration-1 NOT STARTED；DH NOT INTEGRATED；AI NOT STARTED；LIVE DISABLED；无生产代码 / API / migration / RealClient / 真实 Provider / 真实 HTTP / 真实 NQ / 真实交易所 / 凭证读取 / NQ DB 读写 / 交易副作用。
- Integration-1 前置 blocker：DH P1-4 residual（rate limit / memory cap / replay nonce persistence，修复后须重跑 contract tests，T06 须以持久化 nonce 重跑，并新增 429 限流与 bounded store 测试）；header `X-DH-NQ-*` 与 `X-NQ-DH-*` 对齐；真实通道安全前置（单独开工 + 设计审计 + staging/paper-only + LIVE disabled + 无凭证落日志 + no trading side-effect + 安全审查）。
- 下一步只允许：Integration-0 acceptance/归档、Integration-1 planning-only audit、DH P1-4 residual fix planning、GateK-PLAN 文档规划。禁止直接 Integration-1 实现 / 真实只读通道 / 真实 HTTP / RealClient / Provider / LIVE / AI 自动交易。

## 当前未完成状态

- 虚拟币量化 V1 已在 GateI 完整闭环完成；当前未完成的是公开生产就绪、UI/UX 专业化收口、AI/LIVE/美股/A 股等后续阶段。
- Paper Trading 稳定运行 GateJ 已完成；UI/UX professionalism 仍是 post-freeze remediation。
- 尚未进入 AI 自动交易。
- 尚未进入美股/A 股适配。

## 后续路线

```text
DOC-CLEAN / BASELINE-FIX
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI-PLAN
  ↓
GateI：虚拟币量化 V1 完整闭环
  ↓
GateJ：Paper Trading 稳定运行 completed
  ↓
GateK：规划 / 架构 / 产品化 / 部署化 / 可观测性 / 安全边界收口（NEXT）
  ↓
GateL：AI Paper Trading
  ↓
GateM：AI 小资金 LIVE
  ↓
GateN：美股适配
  ↓
GateO：A 股适配
```

## 本地环境约定

- PostgreSQL 默认端口：`5432`。
- `local` profile 默认连接 `localhost:5432`。
- `docker-compose` 默认映射 `5432:5432`。

## 当前验证基线

- 后端 `mvn -f backend/pom.xml test` 已通过。
- 前端 `npm run build` 已通过。
- E2E `npm run test:e2e` 已通过，结果为 5 passed / 3 skipped。
- GateH-2 后 E2E `npm run test:e2e` 已通过，结果为 9 passed / 3 skipped。
- GateH-3 后 E2E `npm run test:e2e` 已通过，结果为 10 passed / 4 skipped。
- GateH-3 的 backtest dataset binding UI smoke 因当前本地库没有可绑定 backtest config 种子而 skip；后端 controller 测试覆盖绑定 API。
- GateI-1 后端 `mvn -f backend/pom.xml test` 已通过。
- GateI-1 前端 `npm run build` 已通过。
- GateI-1 E2E `npm run test:e2e` 已通过，结果为 13 passed / 3 skipped。
- GateI-2 后端 `mvn -f backend/pom.xml test` 已通过。
- GateI-2 前端 `npm run build` 已通过。
- GateI-2 后端 local profile 启动已通过，Flyway 当前版本为 `20`。
- GateI-2 E2E `npm run test:e2e` 已通过，结果为 17 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，不影响 GateI-2 主链。
- GateI-3 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests，0 failures）。
- GateI-3 前端 `npm run build` 已通过。
- GateI-3 E2E `npm run test:e2e` 已通过，结果为 18 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateI-3 主链。
- GateI-3 Flyway 当前版本为 `21`。
- GateI-4 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests，0 failures，含 PaperTradingMonitorServiceTest 5 用例）。
- GateI-4 前端 `npm run build` 已通过。
- GateI-4 Flyway 当前版本为 `22`。
- GateI-4-FIX E2E `npm run test:e2e` 已通过，结果为 19 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路。
- Python `pytest`、`mypy`、`ruff` 已通过。
- GateJ-1 后端 `mvn -f backend/pom.xml test` 已通过（35 tests / 0 failures）。
- GateJ-1 前端 `npm run build` 已通过。
- GateJ-1 E2E `npm run test:e2e` 已通过，结果为 20 passed / 1 skipped。
- GateJ-1 Flyway 当前版本为 `23`。
- GateJ-2 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests / 0 failures，含 PaperRunMonitorServiceTest 12 用例）。
- GateJ-2 前端 `npm run build` 已通过。
- GateJ-2 E2E `npm run test:e2e` 已通过，结果为 22 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateJ-2 主链。
- GateJ-2 Flyway 当前版本为 `24`。
- GateJ-3 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，含 PaperRunRecoveryServiceTest 9 用例 + PaperRunStabilityCheckServiceTest 10 用例 + PaperRunMonitorRunServiceTest 8 用例）。
- GateJ-3 前端 `npm run build` 已通过。
- GateJ-3 E2E `npm run test:e2e` 已通过，结果为 24 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateJ-3 主链。
- GateJ-3 Flyway 当前版本为 `25`。

## PRE-FREEZE-CODE-AUDIT 验证记录（2026-05-22）

- 后端 `mvn -f backend/pom.xml test`：通过（BUILD SUCCESS，0 failures、0 errors；archunit 模块边界与包边界全部通过）。
- 前端 `npm run build`：通过（Vite 通过，dist/index.js ≈ 1.48 MB，仍有 chunk > 500 kB 警告）。
- `npm run test:e2e`：本轮未实际重跑（沿用 GateJ-3-WO 24 passed / 1 skipped 基线）；P1-1 要求 GateJ-FREEZE 入场前补跑。
- Python `pytest / mypy / ruff`：本轮未实际重跑（当前 shell 仅 WindowsApps stub，无真实 Python 解释器；沿用 BASELINE-FIX-2 / GateJ-3 通过基线）；P1-2 要求 GateJ-FREEZE 入场前补跑。
- 详见 `PRE_FREEZE_AUDIT_REPORT.md` 与 `PRE_FREEZE_AUDIT_FIX_PLAN.md`。

## PRE-FREEZE-CODE-AUDIT second pass 验证记录（2026-05-22）

- 后端 `mvn -f backend/pom.xml test`：通过（Reactor BUILD SUCCESS；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors）。
- 前端 `npm run build`：通过（Vite build 成功；仍有 chunk > 500 kB 警告）。
- E2E `npm run test:e2e`：通过（后端 local profile 启动成功，Flyway 当前版本 25；完整 Playwright 24 passed / 1 skipped / 0 failed；唯一 skipped 为 `E2E_TRADE_ORDER_ID` 未配置的既有订单详情链路）。
- Python `python -m pytest -q`：通过（2 passed）。
- Python `python -m mypy src`：通过（Success: no issues found in 8 source files）。
- Python `python -m ruff check .`：通过（All checks passed）。
- API / DB / Paper-LIVE 隔离 / AI 边界二次抽查未发现 P0/P1。
- 结论：允许进入 GateJ-FREEZE，但 GateJ-FREEZE 必须单独开工，只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

## AUDIT-FIX 验证记录（2026-05-26）

- P1 关闭：`scripts/gated_okx_dome_verify.ps1` 已变为安全阻断 stub，旧脚本归档到 `docs/archive/scripts/gated_okx_dome_verify.ps1`；当前可执行 API 不包含 `/__gated/**`。
- E2E 端口修复：`frontend/playwright.config.ts`、`frontend/tests/e2e/run-e2e.mjs`、`frontend/vite.config.ts`、`frontend/.env.example` 已统一从 `4173` 调整为 `5179`。
- 验证结果：`mvn -f backend/pom.xml test` 通过；`cd frontend && npm run build` 通过；启动后端 local profile 后 `cd frontend && npm run test:e2e` 通过，结果 24 passed / 1 skipped / 0 failed。
- 本轮不新增 API、不新增 migration、不修改交易下单/风控/撮合/恢复/调度核心逻辑、不接 AI。
- 验证结果详见 `AUDIT_FIX_REPORT.md`。

## GateI 当前边界

- GateI 已整体完成。
- GateI-1 实现策略版本与发布记录绑定。
- GateI-2 实现回测配置、评估指标、结果追溯增强。
- GateI-3 实现 SIM/Paper Trading 运行闭环最小版本。
- GateI-4 实现风控回写、资金曲线、持仓曲线、交易复盘、异常停机最小结构。
- AI、AI 信号、AI 自动交易、AI Paper Trading 仍未开始。
- GateJ-3-WO 已完成（异常恢复、失败重试、稳定性验收结构、HEARTBEAT_LAG/SCHEDULE_FIRE_FAILED 自动告警最小落库）。
- DOC-CLEAN-2 已完成（删除 docs/current/ 中 GateH/GateI 计划副本）。
- PRE-FREEZE-CODE-AUDIT second pass 已完成（无 P0；E2E 与 Python 基线均已实际重跑通过，详见 PRE_FREEZE_AUDIT_REPORT.md）。
- GateI 的历史下一步 GateJ 已完成；当前状态是 GateJ completed / Next: GateK-PLAN，AI 仍 not started。
