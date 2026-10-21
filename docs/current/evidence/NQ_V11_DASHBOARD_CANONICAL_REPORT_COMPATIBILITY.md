# Dashboard Canonical SIM 日报兼容修复

- Task: `NQ-V11-DASHBOARD-CANONICAL-REPORT-COMPATIBILITY-FIX`
- 日期：2026-10-21（Asia/Shanghai）
- Result: `IMPLEMENTED / SELF_REVIEWED`，仅本轮前端实现与隔离回归。
- Branch: `codex/nq-v11-dashboard-canonical-report`
- Starting HEAD / 实现完成时 HEAD: `2fbfdaecb9d7adefad48f69a6d05add2582fd1d8`
- 前置执行了 `git fetch origin`、状态/分支/HEAD/origin/dev/log 检查、`git switch dev`、`git pull --ff-only origin dev`，随后创建任务分支。起点工作区干净，HEAD 与 origin/dev 相同。
- 实现完成时快照：Commit `NOT_COMMITTED`、Push `NOT_PUSHED`、Release `NOT_CREATED`。后续用户已授权本轮六文件 Commit / Push / PR，并以 required CI 全绿且无新增阻断项为 Merge 前提；不发布版本。最终提交和 CI 身份以 Git / PR / Actions 为准。

## 当前合同与实现

1. 已读取 AGENTS、适用前端规范、Dashboard、查询 Hook、运行类型、两个分析工作区、现有 Dashboard/日报测试及 PR #66 合并变更。
2. 列表合同：`PaperTradingController.list` 经过可读身份过滤后使用 `PaperTradingRunResponse.from`；响应的 `Long canonicalAccountId` 直接来自运行对象。前端 `paperTradingApi.list` 原样返回该响应，`PaperTradingRunItem` 将此字段定义为可选且可空。没有根据交易所、名称或运行状态推断身份。
3. PR #66 的 `PaperRunMonitorService.requireLegacyDailyReportRun` 在 canonicalAccountId 非空时拒绝旧日报读取/生成，错误为 `STRATEGY_SIM_DAILY_REPORT_UNAVAILABLE_USE_CANONICAL_ANALYSIS`。本轮未修改或重新 qualification 已接受的后端实现。
4. `usePaperDailyReportsQuery(paperRunId, enabled = true)` 保留既有 query key、查询函数、缓存及旧调用方行为。Dashboard 仅在 `canonicalAccountId === null` 时启用查询；无焦点、字段缺失及 canonical 均不启用。
5. 禁用的 Query 仍可能有缓存，因此日报数据和错误的展示也按当前运行身份隔离。所有 Hook 无条件调用。loading 仅适用于已确认的 legacy 身份。
6. Canonical 展示双语说明及“查看模拟交易分析”，通过 `URLSearchParams` 携带 `analysisSource=sim` 和实际焦点 `paperRunId` 到 `/paper-trading/portfolio`。复用的分析页按精确 URL 身份读取，不回退到目录中另一运行。
7. Legacy 保留日报元数据、空态和真实失败。沿用原有财务字段置 null 的可信度边界：即使旧日报返回数值，也不恢复未经验证的权益、盈亏、收益率或回撤。未全局忽略 409。
8. LIVE disabled 展示、其他 Dashboard 读取、后端、API、数据库、交易及账务路径均未修改。

## 修改文件

- `frontend/src/pages/dashboard/DashboardPage.tsx`
- `frontend/src/features/paper-trading/hooks/usePaperTradingQuery.ts`
- `frontend/src/i18n/locales/zh-CN/pages.json`
- `frontend/src/i18n/locales/en-US/pages.json`
- `frontend/tests/e2e/dashboard-canonical-report-compatibility.spec.ts`
- 本证据文件。

## 验证结果

环境：Windows PowerShell、Node v24.16.0、npm 11.13.0；沿用当前依赖。浏览器使用项目 `E2E_BROWSER_CHANNEL=msedge`，没有安装或修改依赖。Browser plugin not available，使用任务要求的仓库 Playwright runner。

| 检查 | 命令（frontend 目录执行，Git 命令在仓库根目录） | 结果 |
| --- | --- | --- |
| TypeScript | `npx tsc -b --pretty false` | PASS，exit 0 |
| Frontend tests | `npm run test:unit` | PASS，85 files / 671 tests |
| Production build | `npm run build` | PASS，exit 0；保留现有大 chunk 警告 |
| Targeted Playwright | `$env:E2E_BROWSER_CHANNEL = 'msedge'; npm run test:e2e -- dashboard-canonical-report-compatibility.spec.ts --max-failures=1 --output <external-output>/dashboard-edge-attempt5` | PASS，15/15，23.7s；runner 支持测试 10/10 |
| Diff | `git diff --check`、`git diff --cached --check` | PASS；暂存后复核六个文件 |

定向测试覆盖：

- Canonical 空运行/有成交，旧日报 HTTP calls = 0；Dashboard 不生成财务零占位卡。
- 列表包含多个运行时，按现有焦点规则选中的 B 被准确传到正式分析页，实际展示 B 的分析响应；目录中的 A 不会替代 B。
- 导航参数对空格、中文和 `&=` 正确编码。
- Legacy 有日报时仅一次请求，最新日报日期显示正确，财务未知值仍为 `-`；无日报保持空态。
- Legacy 409 显示真实错误且请求一次；500/UNKNOWN_ERROR 沿用现有有界重试，共两次，并最终显示真实错误。
- 无焦点 → Canonical、未知字段 → 显式 legacy null。
- Legacy 日报/错误 → Canonical A → Canonical B → 另一 Legacy；不同身份不串数据、错误或日期；回到缓存仍新鲜的 legacy 不重复请求。
- Legacy 请求未完成时切到 Canonical，随后返回的数据不会出现在新运行。
- 向 canonical 的同 key 注入旧日报缓存及错误，禁用查询和展示边界仍有效。
- alerts、heartbeats、risk-results、recovery-events 仍有读取；心跳读取失败仍可见。
- 中英文提示和 LIVE disabled 正常；全部用例断言 Unexpected API mutations = 0、canonical legacy daily-report HTTP calls = 0、pageerror = 0。

测试通过真实浏览器加载正式组件、Query、路由和 API client；HTTP 响应由隔离夹具提供，不是生产业务事实或后端联调证明。为在同一页面精确刷新列表和注入历史缓存，仅在测试拦截的 AppProviders 模块响应中导出既有 QueryClient，未修改生产模块、增加接口或替换 Dashboard。

## 失败、观察和未验证范围

- 初次运行缺失 Playwright `chromium_headless_shell-1208`，所有用例未启动浏览器；随后使用本机 Edge。此前失败未计入成功。
- 调试期间分别修正 JSON import attribute、误拦 `/src/api/` 的测试路由、使用保留 URL 字符的辅助运行 ID 对既有非编码读取的影响、错误端点名称、StrictMode 取消后重发的分析读取计数，以及 500 夹具误用 STATE_CONFLICT 导致的重试预期不符。最终测试保留精确日报请求计数；现有分析页带 signal 的 StrictMode 读取允许最多两次，且只能指向目标身份。
- 额外执行 `npm run test:e2e -- dashboard-runtime-readiness-summary-smoke.spec.ts paper-trading-daily-report-smoke.spec.ts --output <external-output>/dashboard-existing-regression`：**2 failed**，不能称所有历史 E2E 通过。
  - NON_BLOCKING：现有 Dashboard runtime 测试的通用 stub 将 `/api/user/preferences` 返回为 `[]`，与当前 `DashboardWatchlist` 的 `watchlist.length` 合同不符，渲染失败。相关夹具及组件均未由本轮修改；新增定向夹具使用完整偏好合同，并验证 LIVE disabled 和其他状态卡。未扩展修复该历史测试。
  - NON_BLOCKING / `NOT_RUN / BACKEND_UNAVAILABLE`：真实后端日报测试在登录阶段遇到 `ECONNREFUSED 127.0.0.1:18888`。以上保留测试进程原始失败；业务用例因环境前置条件不满足而未运行，不判为业务功能失败。没有启动共享后端或执行其后创建账户/运行/日报的写入夹具。
- 浏览器出现已有 Ant Design Card `bordered` 弃用警告；本轮未整体重构布局或升级组件 API。
- 未执行 Maven、PostgreSQL、Flyway、稳定性运行、远程 CI、独立审查或生产操作；未授权或启用 LIVE。
- Findings（本轮实现自审）：P0=0，P1=0，P2=0；上述历史测试/环境限制独立保留，不表示远程交付或产品发布已通过。

## 候选与外部证据

外部输出根目录：`C:/Users/Lingyu/.codex/visualizations/2026/10/21/01a15d71-a87f-7c01-b10f-1f1f9c347554`。保留初次浏览器失败 `dashboard-playwright`、调试失败 `dashboard-edge-attempt1` 至 `attempt4`、通过结果 `dashboard-edge-attempt5`、历史回归失败 `dashboard-existing-regression`。通过轮已截图并人工查看 `canonical-dashboard.png`：焦点 canonical-b、双语对应中文说明、分析链接清晰可读，没有旧日报错误或财务占位指标。

已验证实现/测试文件 SHA256（不包含本证据自身）：

| 文件 | SHA256 |
| --- | --- |
| DashboardPage.tsx | `30580CA7DF49419C057099ED3F61AA049C51D5D74821B9EAB02FEF9E23857A90` |
| usePaperTradingQuery.ts | `16A56C1AAE3EF7A6742ADB7AF44BDBDBA5E6F8842B34D8B547018947A644909D` |
| zh-CN/pages.json | `2B7EA5098E0BFF4A4E4E38A31183C752927D2B51E34AFE4729C0DA670E794F80` |
| en-US/pages.json | `E5F25A293C368B5A59CAA278AF4051F4837E8769DB7D696D4748D77BBD5137CC` |
| dashboard-canonical-report-compatibility.spec.ts | `E4D0F9C9BAFB803B715F8CC3CE1C8283BCAA5196F4C06667E5B6CBC151D1B718` |

交付前检查：当前 `.github/workflows/ci.yml` 的两组 critical E2E allowlist 不包含上述两项历史测试；旧 Dashboard 夹具问题保留为非 required 的测试债务，未标为通过。真实后端日报链保持 `NOT_RUN / BACKEND_UNAVAILABLE`。

Next Action：按用户授权精确提交六个文件、Push、创建面向 dev 的 PR；核对 required CI 和新增阻断项后正常 Merge，再核验合并后的 CI。本任务不单独发布产品版本。
