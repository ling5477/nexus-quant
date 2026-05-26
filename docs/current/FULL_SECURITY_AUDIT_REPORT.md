# FULL SECURITY AUDIT REPORT

> 审查时间：2026-05-26  
> 审查范围：Java/Spring 后端、React/TypeScript 前端、Maven/npm 依赖、Docker、GitHub 配置、脚本、AGENTS/CLAUDE/README/docs/current、密钥形态、网络出口、启动/API 入口、AI 指令污染。  
> 边界：本轮未修改业务代码，未新增依赖，未删除文件，未执行真实交易所验收脚本，未输出敏感明文。唯一写入为本报告。

## 1. 审查结论

结论：**不通过**。

原因：

- 未发现明确 P0：未确认存在真实密钥明文泄露、未知远程执行、未知网络上报、交易路径被异常修改。
- 发现 P1：`scripts/gated_okx_dome_verify.ps1` 仍保留高风险真实 OKX 验收脚本，并调用旧 `/__gated/**` 路径；当前 `docs/current/API.md` 明确旧 `/__gated/**` 只允许存在于历史文档说明中，脚本与当前 API 边界不一致。
- 发现 P2：存在弱默认/验收配置敏感字段、`localStorage` 存储 token、交易所外联适配器默认域名、npm lock 使用第三方镜像源、PowerShell 脚本可重启服务并发起本地 HTTP 调用。
- 必须验证项中 `npm run test:e2e` 失败：本机 TCP 端口排除范围覆盖 `127.0.0.1:4173`，Vite 无法监听，测试未通过。按 NexusQuant Gate 规则：**测试失败不允许 Freeze**。

## 2. 执行环境

- 工作目录：`E:\Project\nexus-quant`
- 当前分支：`dev`
- 最近提交：`05e5fd69 docs: complete pre-freeze audit for GateJ`
- 最近 20 条提交已执行 `git log --oneline -20` 采集。
- 初始工作区：`git status --short` 无输出。
- 初始差异：`git diff --stat` 无输出，`git diff -- . ':!*.lock'` 无输出。
- 文件清单：已执行 `git ls-files`，仓库约 1167 个跟踪文件。

已执行命令：

- `git status --short`
- `git branch --show-current`
- `git log --oneline -20`
- `git diff --stat`
- `git diff -- . ':!*.lock'`
- `git ls-files`
- `Get-Content frontend/package.json`
- `Get-Content backend/pom.xml`
- `Get-Content docker-compose.yml`
- `Get-Content scripts/gated_okx_dome_verify.ps1`
- `Get-ChildItem .github -Recurse -File`
- `Get-ChildItem scripts -Recurse -File`
- `rg` / `Select-String` 多轮关键词、安全入口、网络出口、依赖与 AI 指令污染扫描
- `Get-Command gitleaks`
- `Get-Command semgrep`
- `Get-Command trivy`
- `mvn -f backend/pom.xml test`
- `npm run build`
- `npm run test:e2e`
- `Get-NetTCPConnection -LocalPort 4173`
- `netsh interface ipv4 show excludedportrange protocol=tcp`
- `netsh interface ipv6 show excludedportrange protocol=tcp`

工具降级说明：

- 首选 `idea-mcp` 在当前会话不可用，降级到 PowerShell、Git、`rg`、`Select-String` 做仓库内只读扫描。
- 检索范围：仓库跟踪文件、`backend/`、`frontend/`、`research/py/`、`scripts/`、`.github/`、`AGENTS.md`、`CLAUDE.md`、`README.md`、`docs/current/`。
- 结果可信度：中高。文件级搜索覆盖充分，但缺少 IDE 符号级索引与 gitleaks/semgrep/trivy 自动规则验证。

## 3. P0 问题

未发现确认型 P0。

已检查：

- 未发现 `git diff` 中出现未提交业务代码改动。
- 未发现 `.github/workflows/**`，无 GitHub Actions 执行链风险。
- 未发现 `curl | bash`、`iwr | iex`、`Invoke-Expression`、远程安装脚本。
- 未执行也未发现未知遥测/未知上报逻辑。
- 未发现 AI provider、AI Signal、AI Trading、OpenAI/Anthropic/LLM 业务代码接入。

保留风险：

- 自动化密钥扫描工具缺失，无法给出 gitleaks/trivy 级别的最终阴性证明。
- 本轮按要求不读取 `.env` 明文，因此不能验证本地未跟踪 `.env` 是否含真实凭证。

## 4. P1 问题

### P1-1：高风险验收脚本仍调用旧 `/__gated/**` 路径

- 文件：`scripts/gated_okx_dome_verify.ps1`
- 行号：670、682、685、691、704、712、739、751、757、760、766、783、793、797、803、820、847、849、851、859、865
- 类型：隐藏/遗留执行入口风险、交易验收脚本风险
- 证据：脚本包含 `.env` 读取、进程环境变量导入、PowerShell 7 重启、服务健康检查、`Invoke-WebRequest`、`Start-Process`、真实 OKX 验收用例，并访问旧 `/__gated/**` 路径。
- 冲突：`docs/current/API.md:24` 明确“旧 `/__gated/**` 只允许出现在历史文档说明中”，但该脚本仍保留实际调用。
- 影响：误执行时可能对本地服务发起下单/撤单/恢复/对账验收请求；如果本地服务存在兼容入口或旧版本运行，风险会被放大。
- 本轮处理：未执行该脚本，未修改文件。

## 5. P2 问题

### P2-1：验收 profile 存在敏感默认配置字段

- 文件：`backend/nq-app/src/main/resources/application-gated-verify.yml`
- 行号：8、19、20、21、22
- 类型：敏感默认/验收配置风险
- 说明：包含测试/验收用途的敏感配置字段及默认值形态。本报告不输出明文。
- 影响：如果该 profile 被误用于非本地验收环境，可能形成弱默认配置风险。

### P2-2：Docker Compose 本地数据库存在弱默认口令字段

- 文件：`docker-compose.yml`
- 行号：8
- 类型：弱默认口令
- 说明：本地 PostgreSQL 使用环境变量默认值兜底。本报告不输出明文。
- 影响：本地开发可接受但不应扩散到共享、CI、远程或生产环境。

### P2-3：前端 token 存储在 `localStorage`

- 文件：`frontend/src/utils/token-storage.ts`
- 行号：14、32、42、54、64、72
- 类型：XSS 后 token 可读风险
- 说明：当前登录态保存在浏览器 `localStorage`。未发现 `dangerouslySetInnerHTML` 或 `document.cookie` 业务代码命中，但如果未来出现 XSS，token 暴露面较大。

### P2-4：交易所适配器保留外部网络出口

- 文件：
  - `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxRuntimeConfig.java:43-45`
  - `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceRuntimeConfig.java:48-51`
  - `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsProtocol.java:14-17`
- 类型：外部域名默认值 / 交易所网络出口
- 说明：OKX、Binance testnet/real REST/WS 默认域名存在于正式适配器中。这是业务设计的一部分，不等于后门；但 GateJ 当前 Paper Trading 阶段应确保不会由 Paper 主链触发真实下单。

### P2-5：npm lock 使用第三方镜像源

- 文件：`frontend/package-lock.json`
- 类型：依赖供应链镜像源风险
- 统计：`resolved` host 全部为 `registry.npmmirror.com`，共 209 项。
- 说明：未发现 npm `preinstall`、`postinstall`、`prepare` lifecycle script；但第三方镜像源仍需纳入供应链信任边界。

## 6. P3 问题

- `npm run test:e2e` 失败根因是本机端口 `4173` 位于 TCP excluded port range `4141-4240` 内，导致 Vite 监听 `127.0.0.1:4173` 返回 `EACCES`。这是环境/端口配置问题，但直接阻断 GateJ-FREEZE。
- `npm run build` 通过，但 Vite 提示主 JS chunk 约 1.48 MB，超过 500 kB，属于性能/体积风险。
- `mvn test` 通过，但输出 Mockito 动态 agent、SLF4J provider 警告。当前不阻塞，但未来 JDK 默认禁用动态 agent 后可能影响测试稳定性。
- `.github/CODEOWNERS` 仍为占位 owner，关键目录保护在实际 GitHub 仓库中可能未生效。

## 7. 网络出口审查

发现的外部域名 / 地址：

- `https://www.okx.com`：OKX REST 默认 base URL。
- `wss://wspap.okx.com:8443/ws/v5/private`：OKX demo/private WS 默认 URL。
- `wss://ws.okx.com:8443/ws/v5/private`：OKX real/private WS 默认 URL。
- `https://testnet.binance.vision`：Binance testnet REST 默认 base URL。
- `https://api.binance.com`：Binance real REST 默认 base URL。
- `wss://stream.testnet.binance.vision/ws`：Binance legacy testnet stream URL。
- `wss://stream.binance.com:9443/ws` / `wss://stream.binance.com/ws`：Binance legacy real stream URL。
- `wss://ws-api.testnet.binance.vision/ws-api/v3`：Binance testnet WS API URL。
- `wss://ws-api.binance.com:443/ws-api/v3`：Binance real WS API URL。
- `https://registry.npmmirror.com`：npm lock 依赖包 resolved 源。
- `https://maven.apache.org/xsd/maven-4.0.0.xsd`：Maven POM schema URL。
- `localhost` / `127.0.0.1`：本地后端、PostgreSQL、Vite、E2E、测试 mock server。

HTTP / WS client：

- Java `HttpClient`：OKX/Binance REST 与 WS adapter、测试 mock。
- Java WebSocket：Binance/OKX WS client。
- 前端 `axios.create`：`frontend/src/api/client.ts:28`，`baseURL` 来自 `frontend/src/utils/env.ts:16`，默认 `/api`。
- Vite proxy：`frontend/vite.config.ts:14`，默认代理到本地后端。
- PowerShell `Invoke-WebRequest`：`scripts/gated_okx_dome_verify.ps1:393,395,421,446`。

未发现：

- `navigator.sendBeacon` 业务代码。
- 前端业务代码中的未知遥测上报域名。
- `fetch(` 业务代码直连未知外网。

## 8. 依赖审查

Maven：

- 父工程：`backend/pom.xml`
- Spring Boot：`3.5.10`
- JJWT：`0.12.6`
- Maven modules：23 个后端 module。
- 搜索 `repositories` / `pluginRepositories` / 自定义 repository：未发现自定义 Maven 仓库。

npm：

- 前端：`frontend/package.json`
- dependencies：`@ant-design/icons`、`@tanstack/react-query`、`antd`、`axios`、`react`、`react-dom`、`react-router-dom`、`zustand`
- devDependencies：`@types/node`、`@types/react`、`@types/react-dom`、`@vitejs/plugin-react`、`playwright`、`typescript`、`vite`
- lifecycle scripts：未发现 `preinstall`、`postinstall`、`prepare`。
- lock resolved host：`registry.npmmirror.com`，209 项。

CI/CD：

- `.github/workflows/**`：不存在。
- `.github/CODEOWNERS`：存在，但 owner 为占位值。
- `.github/pull_request_template.md`：存在，未发现危险命令。

## 9. 启动入口审查

启动 / 生命周期入口：

- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/AuthSeedConfiguration.java:21`：`ApplicationRunner`，`local/test` profile。
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/AuthBootstrapAdminConfiguration.java:22`：`ApplicationRunner`，`nq.auth.bootstrap-admin.enabled=true` 时启用。
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxWsSmokeRunner.java:29`：`SmartLifecycle`，`local` + `nq.okx.ws.enabled=true`。
- `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/ws/BinanceWsSmokeRunner.java:29`：`SmartLifecycle`，`local` + `nq.binance.ws.enabled=true`。

定时任务：

- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/PaperMatchingService.java:86`
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java:83`
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRecoveryService.java:117`
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/LedgerReconcileScheduler.java:54`
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/BinanceRestReconcileService.java:99`

条件装配：

- OKX/Binance WS bridge、degrade reconcile、order acceleration 均由 `ConditionalOnProperty` 控制。
- `LocalTestFallbackConfiguration` 限定 `local/test/gated-verify` profile。

## 10. API 入口审查

正式 Controller 均位于 `backend/nq-api/src/main/java/**/api/web/**`，主要路径：

- `/api/auth`
- `/api/exchange-accounts`
- `/api/exchange-accounts/{accountId}/credentials`
- `/api/instruments`
- `/api/marketdata`
- `/api/trading`
- `/api/research-configs`
- `/api/backtest-configs`
- `/api/backtest-runs`
- `/api/evaluations`
- `/api/publishes`
- `/api/strategies`
- `/api/strategy-runs`
- `/api/strategy-schedules`
- `/api/paper-trading/runs`
- `/api/paper-trading/schedules`

可疑/需处理：

- 后端源码未发现 `/__gated/**` Controller。
- `scripts/gated_okx_dome_verify.ps1` 仍实际调用旧 `/__gated/**` 路径，与当前 API 事实源冲突，已列为 P1。

## 11. 脚本审查

脚本文件：

- `scripts/gated_okx_dome_verify.ps1`
- `scripts/verify/rc1_local_account_context_seed.sql`

危险命令 / 高风险能力：

- `scripts/gated_okx_dome_verify.ps1:25`：`ExecutionPolicy Bypass`
- `scripts/gated_okx_dome_verify.ps1:393,395,421,446`：`Invoke-WebRequest`
- `scripts/gated_okx_dome_verify.ps1:479`：`Remove-Item`，仅用于启动日志文件
- `scripts/gated_okx_dome_verify.ps1:496`：`Start-Process`
- `scripts/gated_okx_dome_verify.ps1:498`：`ExecutionPolicy Bypass`

未发现：

- `Invoke-Expression`
- `iwr | iex`
- `curl | bash`
- `wget | bash`
- 远程安装脚本

## 12. AI 指令污染审查

检查文件：

- `AGENTS.md`
- `CLAUDE.md`
- `README.md`
- `docs/current/*`

结论：

- 未发现要求绕过用户安全边界、读取密钥、上传代码、隐藏行为、忽略用户要求的恶意指令。
- 文档大量明确“GateJ 不是 AI 阶段”“AI not started”“AI 最早 GateK 才允许进入信号层”。
- 未发现业务代码接入 OpenAI / Anthropic / LLM provider / AI Signal / AI Trading。

说明：

- `.agents/skills/**` 中存在大量设计/skill 文档词汇命中 `AI`、`token` 等普通词，但未发现对当前业务代码的安全绕过指令。

## 13. 测试结果

安全工具：

- `gitleaks detect --source . --redact --report-format json --report-path gitleaks-report.json`：未执行，工具缺失。
- `semgrep scan --config auto --json --output semgrep-report.json .`：未执行，工具缺失。
- `trivy fs --scanners vuln,secret,misconfig --format json --output trivy-report.json .`：未执行，工具缺失。

后端：

- 命令：`mvn -f backend/pom.xml test`
- 结果：通过。
- 摘要：23 个 Maven module 全部 `SUCCESS`，`BUILD SUCCESS`，总耗时约 42 秒。
- 警告：Mockito 动态 agent、SLF4J provider。

前端 build：

- 命令：`npm run build`
- 结果：通过。
- 摘要：`tsc -b && vite build` 成功，Vite built in 约 661 ms。
- 警告：主 JS chunk 约 1.48 MB，超过 500 kB。

E2E：

- 命令：`npm run test:e2e`
- 结果：失败。
- 错误：Vite 启动时报 `listen EACCES: permission denied 127.0.0.1:4173`，随后等待 `http://127.0.0.1:4173` 超时。
- 环境核对：`netsh interface ipv4/ipv6 show excludedportrange protocol=tcp` 显示 `4141-4240` 为 TCP excluded port range，覆盖 `4173`。
- 影响：不是用例断言失败，但测试命令未通过，因此不允许 Freeze。

## 14. 修复建议

P0：

- 当前无确认型 P0。安装并运行 `gitleaks` / `semgrep` / `trivy` 后再做最终闭环。

P1：

- 废弃或重写 `scripts/gated_okx_dome_verify.ps1`：去除旧 `/__gated/**` 调用，改为当前正式 `/api/**`，并要求显式 dry-run / SIM 开关、显式确认、禁止默认真实交易。
- 若该脚本仅保留历史用途，应移动到归档目录并在当前入口文档中标注“不可执行”，或删除前先单独审批。

P2：

- 将 `application-gated-verify.yml` 中敏感默认值改为必须由环境变量显式提供；测试 profile 使用固定假值时也应避免看起来像可用密钥。
- Docker Compose 本地 DB 口令默认值仅保留开发环境，文档明确禁止远程/共享环境使用默认值。
- 前端 token 存储建议评估迁移到 HttpOnly Cookie 或缩短 token TTL，并维持 XSS 防护。
- npm lock 建议评审是否继续信任 `registry.npmmirror.com`；如切回官方 registry，单独提交 lock 变更并跑 build/e2e。
- 外部交易所网络出口保留允许清单与 profile 开关，确保 GateJ Paper 主链不触发真实 LIVE 下单。

P3：

- 调整 E2E 本地端口，避开 Windows excluded port range；例如将 Vite/E2E 默认端口改到未排除端口，并同步 `frontend/tests/e2e/run-e2e.mjs`、`frontend/playwright.config.ts`、`.env.example`、文档。
- 拆分前端 bundle 或配置合理 chunk strategy。
- 为 Mockito 动态 agent 警告补测试运行参数，避免未来 JDK 行为变化。
- 将 `.github/CODEOWNERS` 占位 owner 替换为真实 GitHub 用户或团队。

## 15. 最终 Gate 判断

NexusQuant Gate 判断：**不允许进入 GateJ-FREEZE**。

理由：

- P0：0 个确认型问题。
- P1：1 个，`scripts/gated_okx_dome_verify.ps1` 遗留旧 `/__gated/**` 高风险验收脚本。
- P2：已登记，部分不阻塞代码运行，但需要在进入冻结前明确处理或豁免。
- P3：E2E 测试失败，且 Gate 规则明确“如果测试失败，不允许 Freeze”。

本轮未触达禁止范围：

- 未接 AI。
- 未新增 AI 模块、AI 信号、AI 自动交易、AI Paper Trading。
- 未执行真实 LIVE 下单。
- 未调用真实交易所下单接口。
- 未修改历史 migration。
- 未修改业务代码。
- 未新增依赖。
- 未删除文件。
