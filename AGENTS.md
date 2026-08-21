# AGENTS（Codex 开发指引 - NexusQuant）

> 目的：让 Codex / 开发者在本仓库内严格遵循当前阶段、模块边界、文档事实源、验证纪律和禁止范围。
> 当前事实源：`docs/current/`。

## 0. 项目定位与 Codex 插件路由

NexusQuant（NQ）是通用量化交易平台。每轮任务必须先读取 `docs/current/STATUS.md` 顶部 `nq-current-authority` 机器可读区块；`AGENTS.md`、skills、workflow 和模板不得复制具体 current Gate 或 next Gate。Decision Hub（DH）是多 Agent 决策平台；NQ-only 任务只能读取 NQ 侧集成边界，不得声明或修改 DH current authority。

Codex 执行任务时必须先判断任务类型，再选择最少必要插件或 skill。禁止默认调用所有插件，禁止用插件名义绕过 Gate、Freeze、安全、模块或交易边界。完整路由规则见 `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md`，Router Skill 源规格与维护规范见 `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md`，常用模板见 `docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md`，可复制 Project Instructions 见 `docs/current/CODEX_PROJECT_INSTRUCTIONS.md`。

NQ 边界：

- 不允许开启 LIVE trading。
- 不允许新增真实下单、撤单路径，除非任务明确要求且当前 Gate 允许。
- 不允许泄露 credentials、API key、exchange secret、tenant data、token、cookie。
- PAPER 和 LIVE 必须隔离；任何跨环境逻辑必须说明隔离点、失败模式和回滚方式。
- DH 不允许修改 NQ 交易状态，不允许启动 Paper Run，不允许访问凭证。
- 涉及交易、风控、权限、部署、安全的修改必须输出风险说明。
- 前端页面任务优先按 `Figma + Product Design + Build Web Apps + Browser/Chrome` 路由；若当前任务只是 Ant Design 代码落地，仍优先遵守本仓库 active skills。
- 安全审计任务优先按 `GitHub + Codex Security + CodeRabbit` 路由。
- 交易所数据任务优先按 `Binance + GitHub + Spreadsheets` 路由；只允许公共只读市场数据，不允许下单。
- 文档任务优先按 `GitHub + Documents + Notion` 路由。

DH 边界：

- DH 不允许真实连接 NQ。
- DH 不允许下单、撤单、启动 Paper Run、访问凭证、修改 NQ 交易状态。
- NQ / DH 三轮只读审计（NQ 全仓 / DH 全仓 / NQ-DH 联合边界 + 汇总）已完成；当前 DH not integrated，NQ 侧无 DH 入站端点 / 无 DH client / 无 feedback outbox。
- Integration-0 only as contract / mock / documentation work line, not runtime integration：只能准备只读边界、契约冻结、mock / stub / contract test 和安全策略文档；禁止真实联调、NQ RealClient、真实 Provider、真实交易、读取凭证、读写 NQ DB、开启 LIVE。
- DH P1-1 / P1-2 / P1-3 已关闭；DH P1-4 残留（rate limit / memory cap / replay nonce 持久化）阻塞 Integration-1，不阻塞 Integration-0。
- 不允许新增 real provider、RealClient、第三方 relay、生产交易路径。
- 必须重点检查 HMAC、timestamp、nonce、source allowlist、payload size、tenant binding、replay protection、provider trust policy、audit trail。
- Agent/API 任务优先按 `GitHub + OpenAI Developers` 路由。
- 安全任务优先按 `GitHub + Codex Security + CodeRabbit` 路由。

代码修改前检查：

- 先确认 repository、module、target files、excluded files、expected output。
- 先读 `AGENTS.md`、`README.md`、`docs/current/README.md`、相关计划或事实文档，再读目标代码。
- 不扫描 `node_modules`、`target`、`build`、`dist`、`.git`、`test-results`、`logs`、`secrets`、`credentials`。
- 不同时修改前端、后端、Python、部署、文档，除非任务明确要求。
- 不做大而全重构；每轮改动必须最小、可验证、可回滚。

代码修改后验证：

- 后端：`mvn -f backend/pom.xml test`，或说明仅运行指定模块测试的理由。
- 前端：`Set-Location frontend; npm run build; npm run test:e2e`，页面任务还应做 Browser/Chrome 验证。
- Python：`Set-Location research/py; python -m pytest -q; python -m mypy src; python -m ruff check .`。
- 文档：检查路径、链接、阶段状态、禁止边界、重复入口和“未执行验证不能写成通过”。
- 部署：检查 Docker/env example/health check/migration/rollback；禁止写入真实密钥。

默认输出格式：

```text
Task classification:
Plugins selected:
Scope:
Files inspected:
Files changed:
Findings:
Validation:
Risks:
Next concrete action:
```

## 1. 动态阶段与事实源

- `docs/current/STATUS.md` 是 current Gate、release tag、next Gate、LIVE、AI、DH 与 Integration 状态的唯一 authority。
- `docs/current/ROADMAP.md` 只定义下一允许动作，不得覆盖 STATUS。
- root/current README 只做入口、短摘要和 archive pointer。
- `API.md`、`DB_SCHEMA.md`、`ARCHITECTURE.md`、`MODULES.md` 只描述当前能力，不决定 current Gate。
- `TESTING.md`、`WORKLOG.md` 是 append-only evidence ledger，不参与当前阶段判定。
- `docs/gates/**` 与 `docs/archive/**` 是 historical evidence，不覆盖 current authority。
- 如果 current 文档互相冲突，立即输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`；不得凭旧 Gate 文字自行选择阶段。
- 示例和模板必须使用 `<CURRENT_GATE>`、`<NEXT_GATE>` 占位符，不得把具体历史 Gate 伪装成当前事实。

### Gate freeze / archive hard gate

- 普通 Gate freeze 允许一次生成完整 pre-tag archive，不要求拆成 plan/review/freeze 连续文档任务。
- `scripts/docs/gate-archive-manifest.json` 是 archive role 的机器可读 hard gate。
- Task allowlist 缺少 mandatory role：`BLOCKED / ARCHIVE_ALLOWLIST_INCOMPLETE`。
- 文件缺失、role 缺失或只有 thin current pointer：`BLOCKED / ARCHIVE_MANIFEST_INCOMPLETE`。
- Archive commit CI 成功后才可创建 tag；tag 后只同步 current authority，不要求 tagged commit 预先记录尚未生成的 tag object SHA。
- Freeze/tag 前必须运行 `scripts/docs/check-gate-archive.ps1`、`check-current-authority.ps1` 与 `check-doc-links.ps1`。
- `inventory -> review -> move` 三轮只适用于大规模历史迁移、多 Gate 混合迁移、`docs/current` 物理瘦身、高风险删除或大批量重定位。

## 2. GateI 完成范围

GateI 已整体完成，覆盖以下内容：

- 策略版本与发布绑定（GateI-1）。
- 回测追溯与评估指标增强（GateI-2）。
- SIM/Paper Trading 运行闭环（GateI-3）。
- Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘、异常停机（GateI-4）。
- 后端 35 tests / 0 failures。
- 前端 build 通过。
- E2E 19 passed / 1 skipped。

## 3. 严格禁止范围

- 不接 AI。
- 不新增 AI 模块。
- 不做 AI 信号。
- 不做 AI 自动交易。
- 不做 AI Paper Trading。
- 不做真实 LIVE 下单。
- 不调用真实交易所下单接口。
- 不做美股/A 股。
- 不做合约全量。
- 不做高频。
- 不做复杂因子平台。
- 不改交易核心状态机。
- 不改策略核心算法。
- 不改回测核心算法。
- 不绕过账户上下文。
- 不允许新增无注释表或无注释字段。
- 不修改历史 migration。
- 不把失败验证写成通过。

## 4. 文档规则

- `docs/current` 是当前事实源。
- `docs/gates` 只放已完成 Gate 的冻结卷宗。
- `docs/archive` 只归档，不作为当前开发依据。
- 已完成 Gate 的冻结卷宗统一保存在 `docs/gates/gate-*`；具体 current/next Gate 不在本规则文件硬编码。
- 新 Gate 或新 WO 开始前必须先阅读 `docs/current` 对应计划文档。
- Code-first default：普通任务必须优先产出代码、测试或可验证行为；docs 不得成为默认产物。
- Review-only no-diff：review-only / audit-only 任务默认不修改文件，只输出结论；只有阶段 freeze、合同冻结、高风险计划或用户明确要求时才允许写 docs。
- Docs budget：
  - 普通代码任务默认不改 docs；确需记录时最多追加 `docs/current/WORKLOG.md` 一行。
  - 测试基线任务可改 `docs/current/TESTING.md` + `docs/current/WORKLOG.md`。
  - 阶段完成或 Gate freeze 才可同步 `docs/current/STATUS.md`、`docs/current/ROADMAP.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`。
  - `README.md` 只在入口、架构、启动方式或阶段总状态变化时修改。
  - 专项 PLAN 只用于 CI、migration、security、LIVE、credential、API contract 等高风险 epic。
- Freeze compression：plan / review / first-run / rerun / freeze 不得每一步都同步 5-6 份 docs；中间步骤只记录在专项 plan 或 `WORKLOG.md`，最终 freeze 再同步 `STATUS.md` / `TESTING.md`。
- No document inertia：不得为了“保持文档一致”而新增 docs-only 任务；必须由代码、测试、CI、migration、安全边界或用户明确要求触发。
- Prompt rule：后续任务提示词必须明确 docs 预算，例如“docs 默认不改；如需记录，只允许 WORKLOG 一行”。
- 文档描述必须与代码和测试状态一致；未执行验证不能写成通过。
- Language rule：文档正文必须中文为主；`README`、`STATUS`、`ROADMAP`、`TESTING`、`WORKLOG` 和 `docs/current` 说明文档不得整篇英文化。
- 允许保留英文任务名、状态枚举、类名、接口名、字段名、文件名、路径、命令、配置键、commit message 和协议原文。
- 英文状态值首次出现时必须附中文解释，例如 `PASS`（通过）、`FROZEN`（已冻结）、`READY TO COMMIT`（可进入提交前复核）。
- 代码注释中的业务规则说明优先中文；协议字段、API contract、enum 可保留英文或中英双语。
- DB comment 使用中文业务语义；表名、字段名、索引名和约束名保持英文。
- 不翻译 `docs/archive/**` 与 `docs/gates/**` 历史文档；旧文档只在后续任务自然触碰时顺手修正。
- Agent 输出报告的栏目名可以保留英文，但每个栏目内容必须中文为主。

## 5. 数据库规则

- 本地 PostgreSQL 默认端口为 `5432`。
- 新增 Flyway migration 不允许修改历史 migration。
- 所有新增表必须有 `COMMENT ON TABLE`。
- 所有新增字段必须有 `COMMENT ON COLUMN`。
- JSONB 字段必须说明用途和边界，且不得保存密钥、token、cookie。
- 状态字段必须说明允许值。

## 6. 模块边界

- `nq-api` 不写 SQL。
- `nq-core` 不依赖 JDBC。
- `nq-infra` 承载 JDBC 实现。
- adapter 只做交易所适配，不直接写库。
- frontend 服务端数据使用 Axios + TanStack Query。
- Zustand 只放 auth/account-context 等全局状态。
- Python research 工具链不能被破坏。
- 正式 HTTP API 统一使用 `/api/**`。
- 交易所环境 canonical 口径固定为 `SIM / LIVE`；legacy `DOME / REAL` 只允许存在于导入映射层。

## 7. 每轮验证要求

后端：

```powershell
mvn -f backend/pom.xml test
```

前端：

```powershell
Set-Location frontend
npm run build
npm run test:e2e
```

Python：

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

如果只改文档，可以不跑全量测试；是否记录到 `WORKLOG.md` / `TESTING.md` 必须遵守上方 Docs budget，轻量流程修正和 review-only 默认不新增记录。

## 8. 提交前检查

提交前必须至少执行：

```powershell
git status --short
```

检查项：

- 不提交 `tsbuildinfo`、生成产物、临时日志。
- 不提交本地密钥、`.env`、凭证。
- 不把 skipped / failed 写成 passed。
- 不把尚未完成阶段写成 completed。
- 不创建 `<CURRENT_GATE>` 的 frozen archive，直到实现、验证与 freeze 前置满足 manifest hard gate。

## 9. Codex 执行纪律

- 默认使用简体中文说明计划、过程和结论。
- 先读 `AGENTS.md`、`CLAUDE.md`、`README.md`、`docs/current/*`，再读目标代码或文档。
- 默认最小变更，避免无关重构。
- 不回退用户已有改动。
- 能用工具验证的结论必须用工具验证。
- 每次交付必须说明修改文件、验证结果、剩余风险和是否触达禁止范围。

---

### 1) 工具分级与用途边界（强制按场景选工具）

#### A. 工程内操作（最高优先级）
**`idea-mcp`**
- 用于：项目结构、检索、阅读、编辑、重构、运行配置、问题检查。
- 约束：
  - 若工具支持 `projectPath`，必须显式传入。
  - 自动修改仅限白名单目录。
  - 编辑流程必须：读文件 → 改 → 格式化 → problems 检查（必要时）。

#### B. 仓库文件直接读写 / 兜底文件操作
**`filesystem`**
- 用于：当 `idea-mcp` 不可用，或需要直接处理普通文本/配置/脚本/说明文件时的兜底读写。
- 禁止：绕过 `idea-mcp` 直接对 Java/TS 核心业务代码做大规模盲改。
- 约束：仅作为工程内文件级兜底工具，不替代 `idea-mcp` 的符号级理解与重构能力。

#### C. 代码托管与变更协作（仅限 GitHub 场景）
**`github`**
- 用于：PR/Issue 读取、diff/变更审阅、提交记录查询、仓库信息读取。
- 禁止：未经明确指令自动创建/合并 PR、强推、删除分支、改仓库设置。
- 安全：Token 仅允许通过环境变量提供，例如 `GITHUB_MCP_PAT`。

#### D. 外部资料检索（只用于资料/对照，不直接改代码）
**`brave-search`**
- 用于：查外部信息、官方文档、第三方库用法、错误码、兼容性问题。
- 禁止：把搜索结果直接当项目事实；必须回到仓库或运行结果验证可落地性。
- 说明：需要 `BRAVE_API_KEY`。

#### E. 浏览器调试 / 前端运行态排查
**`chrome-devtools`**
- 用于：查看页面 DOM、网络请求、Console、Storage、路由跳转、前端运行时错误。
- 适用场景：
  - 页面白屏
  - 接口已发出但页面没渲染
  - 路由守卫异常
  - 表单交互异常
  - Ant Design 组件行为与预期不一致
- 禁止：执行敏感线上操作。

#### F. 数据库核对 / SQL 验证
**`postgres`**
- 用于：核对表结构、索引、约束、数据分布、执行 SQL 验证 migration/backfill/查询逻辑。
- 适用场景：
  - DDL 审查
  - migration 验证
  - 查询性能初查
  - 闭环联调时校验 DB 状态
- 禁止：未经明确授权直接修改生产数据。

#### G. 容器 / 本地基础设施联调
**`MCP_DOCKER`**
- 用于：查看容器状态、日志、网络、卷、镜像、Compose 相关运行信息。
- 适用场景：
  - 本地 PostgreSQL / 中间件 / 服务容器启动失败
  - 健康检查失败
  - 联调依赖未就绪
- 禁止：未经明确说明删除镜像、清卷、破坏性 prune。

#### H. CSS / 动画 / 视觉效果辅助
**`icss`**
- 用于：复杂 CSS 布局、渐变、遮罩、滤镜、动画、玻璃拟态、纯 CSS 特效实现参考。
- 定位：辅助 MCP，不是主实现工具。
- 禁止：代替业务 skill 负责页面开发主线。

---

### 1.1 `idea-mcp` 常用能力清单（按场景）
- 项目与结构：`get_project_modules`、`get_project_dependencies`、`get_repositories`、`list_directory_tree`
- 检索与阅读：`find_files_by_glob`、`find_files_by_name_keyword`、`search_in_files_by_text`、`search_in_files_by_regex`、`get_file_text_by_path`、`get_all_open_file_paths`
- 代码理解与质量：`get_symbol_info`、`get_file_problems`
- 编辑与重构：`create_new_file`、`replace_text_in_file`、`rename_refactoring`、`reformat_file`、`open_file_in_editor`
- 执行与联调：`get_run_configurations`、`execute_run_configuration`、`execute_terminal_command`

### 2) 不可用降级条件（通用）
当首选工具出现以下任一情况，允许降级到下一优先级工具：
1. 不可访问 / 连接失败
2. 无权限 / 拒绝访问
3. 同一问题连续 2 次超时
4. 返回结果无法覆盖问题（范围不完整 / 关键文件不可读 / 结果与事实矛盾）

---

### 3) 降级顺序（按场景固定）

#### 工程内检索（定位文件 / 符号）
1) `idea-mcp`
2) `filesystem`
3) `rg`
4) `Select-String`
5) `findstr`

#### 前端运行态排查
1) `chrome-devtools`
2) `idea-mcp`
3) `filesystem`

#### 数据库结构 / 查询验证
1) `postgres`
2) `idea-mcp`
3) `filesystem`

#### 本地依赖 / 容器联调
1) `MCP_DOCKER`
2) `idea-mcp`
3) 终端命令

#### 外部资料核对
1) `brave-search`
2) 手动引用（必须带来源且标注可信度）

---

### 4) 降级披露要求（强制）
一旦发生降级，回复必须包含：
- 降级原因
- 使用的工具
- 检索范围
- 结果可信度（高 / 中 / 低 + 原因）

---

### 5) 启动与密钥规则（强制）
- 所有 API Key / Token 仅允许通过环境变量注入：
  - GitHub：`GITHUB_MCP_PAT`
  - Brave：`BRAVE_API_KEY`
  - 其他：按各 MCP server 文档约定
- 禁止把密钥写入仓库文件、Markdown、脚本、截图、日志输出。

---

### 6) 编辑与运行纪律（强制）
- 编辑：先读 → 再改 → 再格式化 → 必要时 problems 检查
- 运行：先列出 run configs → 再执行 → 汇报退出状态与关键输出

#### 编辑类任务（强制流程）
1. 先读取目标文件，确认上下文
2. 再执行修改 / 重构 / 新建文件
3. 修改后必须格式化
4. 最后确认无明显错误 / 警告激增
5. 回复中列出：修改文件清单 + 变更摘要

#### 运行 / 联调类任务（强制流程）
1. 先确认可用目标（run config / 容器 / DB / 页面）
2. 再执行联调
3. 回复中汇报：退出状态 + 关键输出摘要 + 关键异常

---

### 7) Agent Skills Routing（合并后 skills 规则）

#### 7.1 Active skills（唯一默认启用集合）

当前 active skills 仅允许以下 12 个：

1. `nq-dh-workflow-router`
2. `nq-docs-writer`
3. `frontend-product-ui-design`
4. `ui-visual-system-polish`
5. `frontend-antd-page-builder`
6. `frontend-quality-regression`
7. `java-backend-maintenance`
8. `java-backend-regression-tests`
9. `db-schema-migration-review`
10. `python-ops-tooling`
11. `python-project-development`
12. `nq-java-engineering-standard`

使用原则：

- 只选择与本轮任务直接相关的 skill，不要一次性激活所有 skills。
- 一个任务最多一个主 skill；其他 skill 只能作为补充，并说明为什么需要。
- NQ / DH / Gate / FREEZE / 插件路由相关任务先使用 `nq-dh-workflow-router` 做任务分类、范围限定和边界检查，再选择具体执行 skill。
- 新增、修改、审查或重构 Java / Spring Boot / JDBC / transaction / concurrency / logging / exception / Maven Java dependency / Java test / Checkstyle / PMD / SpotBugs / ArchUnit 时，加载 `nq-java-engineering-standard`；纯前端、纯 Python、无 Java 的 docs/Authority/read-only/Git 任务不自动触发。
- 纯文档、docs-only、plan/review/freeze、stage transition archive、archive inventory / plan review / move batch / closeout、fact-source sync、STATUS / TESTING / WORKLOG / API / DB_SCHEMA / frontend docs / CI docs 同步任务使用 `nq-docs-writer`；若任务同时涉及代码实现、DB、CI、安全、credential、LIVE 或 real provider，`nq-docs-writer` 只作为文档辅助 skill。
- 如果 skill 路由与当前 Gate 边界、安全边界、技术栈边界冲突，优先遵守 Gate / Freeze / Work Order / 安全 / 技术栈规则。
- 不得用 skill 名义绕过禁止项：不接 AI/DH、不接真实 provider、不接 NQ RealClient、不触碰 LIVE 交易、不新增未要求的 API / migration / 业务能力。

#### 7.2 Optional skills（默认不启用）

- `.agents/optional-skills/` 下的 skill 不默认启用。
- `shadcn` / 其他非 Ant Design UI skill 只有在用户明确要求、或目标项目本身已使用对应框架时才允许使用。
- NexusQuant / Decision Hub 当前前端默认使用 React + TypeScript + Ant Design 企业后台栈，不得私自切换 UI 框架或引入新的 UI 体系。

#### 7.3 前端任务路由

- 页面产品化、业务 UX、信息架构、核心状态模型、空态 / 错误态 / 禁用态 / 风险态、前端中文文案：使用 `frontend-product-ui-design`。
- 视觉层级、排版、色彩、专业金融后台质感、响应式、设计系统一致性、页面 polish：使用 `ui-visual-system-polish`。
- Ant Design 页面开发、组件组合、API 接入、类型定义、TanStack Query hooks、Axios client 接线、页面落地：使用 `frontend-antd-page-builder`。
- 前端 bug、路由 / 表单 / Ant Design 行为异常、E2E、Playwright、构建回归、UI 行为回归、提交前前端质量收口：使用 `frontend-quality-regression`。

做前端页面时，默认按以下顺序思考，但只激活本轮需要的 skill：

```text
frontend-product-ui-design
  -> frontend-antd-page-builder
  -> ui-visual-system-polish
  -> frontend-quality-regression
```

#### 7.4 后端 / DB / Python 任务路由

- Java / Spring Boot / 模块边界 / Service 修复 / 异常链 / 事务 / 并发幂等 / 状态流转：使用 `java-backend-maintenance`。
- JUnit、golden cases、Controller / Service / Repository 集成回归、bug 修复后回归测试：使用 `java-backend-regression-tests`。
- Flyway / Liquibase migration、DDL、索引、约束、默认值、COMMENT、schema 审查、回填脚本审查：使用 `db-schema-migration-review`。
- Python 运维脚本、批处理、数据清洗、导入导出、迁移辅助、pytest、ruff、mypy：使用 `python-ops-tooling`。
- 正式 Python package/library/service/CLI、多模块工程、`pyproject.toml`、实现与 tests 联动、依赖管理、typing、async、持久化/网络 adapter、正式 research/backtest framework：使用 `python-project-development`。

Python 任务先按工程边界路由：单一独立脚本、一次性处理、临时分析和 migration/helper script 使用 `python-ops-tooling`；形成长期维护 package 或命中多模块、实现与测试、依赖、架构、service/CLI/async/adapter 任一工程特征时使用 `python-project-development`。

做后端 DB 相关改动时，默认按以下顺序思考，但只激活本轮需要的 skill：

```text
db-schema-migration-review
  -> java-backend-maintenance
  -> java-backend-regression-tests
```

#### 7.5 NexusQuant / Decision Hub 前端风格

- 默认是专业金融科技后台，不是营销页。
- 高信息密度但不拥挤，弱装饰、强层级。
- 强状态表达：运行、停止、失败、风控拒绝、恢复中、重试中、过期、未配置、无权限必须清晰可见。
- 强风控和异常可见性：不得为了页面好看隐藏风险、失败、拒绝、停用、审计和追踪信息。
- 使用 Ant Design 企业后台风格与既有组件模式。
- 禁止营销页式大标题、大渐变、大插画、无意义动效、过度动效和隐藏风险状态。

#### 7.6 前端页面验收标准

新增或调整前端页面时，默认检查：

- 有明确业务目标说明。
- 有核心状态摘要。
- 有清晰筛选区、主数据区、详情区、操作区。
- loading / empty / error / disabled / risky operation 状态完整。
- 危险操作有二次确认。
- REAL / LIVE / 风控失败 / 恢复 / 重试 / 停止类操作必须有明确风险提示和影响范围说明。
- 服务端数据使用 TanStack Query；Zustand 只放 auth、account-context 等轻量全局状态。
- 不新增 API，不改后端契约，不新增 migration，除非用户明确要求。

#### 7.7 MCP 辅助规则

以下 MCP 只作为辅助，不改变主 skill：

- 前端运行态问题：`chrome-devtools`
- 复杂 CSS / 动画参考：`icss`
- 查询 DB 结构 / 数据：`postgres`
- 本地依赖与容器联调：`MCP_DOCKER`
- 读写普通文件或兜底检索：`filesystem`

#### 7.8 输出要求

完成后必须输出：

1. 主 skill 是什么，为什么命中；如未使用 skill，说明原因。
2. 辅助 skill / MCP 是什么，为什么需要；如未使用，说明未使用。
3. 新增文件。
4. 修改文件。
5. 验证步骤。
6. 风险与未覆盖项。
7. 若发现与现有规则冲突，必须说明冲突点，并以现有 Gate 边界、安全边界、技术栈边界优先。
