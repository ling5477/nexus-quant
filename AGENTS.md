# AGENTS.md
# AGENTS（Codex 开发指引 - NexusQuant）

> 目的：让 Codex / 开发者在仓库内生成与修改代码时，严格遵循本仓库的架构、契约、阶段门禁与当前卷宗边界。
> 文档分层：`docs/current/` 为**当前阶段唯一入口（Source of Truth）**；`docs/gates/gate-*/` 为**冻结卷宗（只读参考）**。

---

## 1. 强制约束（必须遵守）

- 语言：除代码、配置键、接口字段、类名外，解释与文档输出使用**简体中文**。
- 当前阶段：**GateG（前端控制台与联调）**。
- 唯一入口：`docs/current/README.md` 与 `docs/current/GATE_CHECKLIST.md`。
- GateG 目标固定为：**前端工程骨架、登录与鉴权守卫、布局与菜单、策略 / 调度 / 运行页面、研究 / 回测 / 评估 / 发布页面、交易验证操作页、Playwright 回归**。
- GateG 不以前置数据库大改为条件；联调过程中只允许补最小前端向接口，禁止借 GateG 发散成新的后端大重构。
- 严格状态机、幂等、事实链、账本、审计、恢复、可观测等 GateD~GateF 已冻结约束继续生效。
- 交易所差异隔离仍只允许留在 `nq-adapter-*`，禁止把交易所方言带进前端视图契约。
- 正式 HTTP API 统一使用 `/api/**`；旧 `/__gated/**` 只允许出现在历史文档说明中。
- 正式认证方式固定为：`POST /api/auth/login` + `Authorization: Bearer <token>` + `GET /api/auth/me`。

---

## 2. 当前阶段切换说明

- GateD 已冻结，`docs/gates/gate-d/*` 仅作历史参考。
- GateE 已冻结，`docs/gates/gate-e/*` 仅作策略接入与调度编排的事实卷宗。
- GateF 已完成并冻结，`docs/gates/gate-f/*` 作为最近完成阶段保留。
- 当前 `docs/current/*` 表示 GateG 当前入口，`docs/gates/gate-g/*` 表示 GateG 主卷宗。
- 当前 source of truth 优先级：
  1. `docs/current/*`
  2. `docs/gates/gate-g/*`
  3. `docs/gates/gate-f/*`
  4. `docs/gates/gate-e/*`
  5. 根 `README.md / docs/*.md` 导航摘要

---

## 3. 文档即事实（Source of Truth）

### 3.1 当前阶段入口（必读）
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/current/GATEG_INPUTS.md`
- `docs/current/MODULES.md`
- `docs/current/WORK_TEMPLATE.md`

### 3.2 当前 Gate 权威文档（GateG）
- `docs/gates/gate-g/README.md`
- `docs/gates/gate-g/GATE_G_CHECKLIST.md`
- `docs/gates/gate-g/PR_SPLIT_PLAN.md`
- `docs/gates/gate-g/ARCHITECTURE.md`
- `docs/gates/gate-g/MODULES.md`
- `docs/gates/gate-g/CONTRACTS.md`
- `docs/gates/gate-g/TEST_CASES.md`
- `docs/gates/gate-g/WORK.md`
- `docs/gates/gate-g/SOURCES.md`

### 3.3 最近冻结 Gate（GateF，只读参考）
- `docs/gates/gate-f/*`

> 规则：当 `docs/current/*` 与 `docs/gates/*` 不一致时，以 `docs/current/*` 为准。

---

## 4. 当前执行顺序（GateG）

1. GateG-DOC-1：主卷宗、输入边界、PR 计划、页面与联调清单
2. GateG-1：前端工程骨架
3. GateG-2：登录、鉴权守卫、布局、菜单
4. GateG-3：策略 / 调度 / 运行页面
5. GateG-4：研究 / 回测 / 评估 / 发布页面
6. GateG-5：交易验证操作页
7. GateG-6：Playwright 回归

---

## 5. GateG 代码约束（强制）

### 5.1 frontend
- GateG 前端技术栈固定为：React 19 + TypeScript + Vite 8 + React Router + TanStack Query + Axios + Zustand + Ant Design + Playwright。
- 页面组织以业务域分组，不允许把所有接口都塞进单一页面。
- 登录态统一由 token storage + route guard + `/api/auth/me` 初始化完成，不允许页面各自重复鉴权。
- 所有 API 调用统一走 `frontend/src/api/*` 封装，不允许页面内直接散写请求。
- 所有列表 / 详情 / tab 页面命名、字段与路由必须对齐后端正式 `/api/**` 契约。

### 5.2 backend
- GateG 期间后端只允许补前端联调缺口，不允许大改 GateD~GateF 冻结主链。
- 非必要不改表；确需改动时，必须证明属于 GateG 页面联调最低必需补口。
- 认证、trace、错误模型继续沿用现有实现，不重新发明第二套协议。

---

## 6. PR 要求（强制）

- PR 必须对应 `docs/current/GATE_CHECKLIST.md` 或 `docs/gates/gate-g/GATE_G_CHECKLIST.md` 的条目。
- 涉及页面、路由、菜单、接口联调时，必须同步更新：
  - `docs/gates/gate-g/CONTRACTS.md`
  - `docs/gates/gate-g/TEST_CASES.md`
  - `docs/gates/gate-g/WORK.md`
- 涉及当前阶段边界变更时，必须同步更新：
  - `README.md`
  - `docs/current/README.md`
  - `docs/current/GATE_CHECKLIST.md`
  - `docs/gates/gate-g/README.md`

---

## 7. 快速验证（GateG 当前阶段）

```powershell
git diff -- AGENTS.md README.md docs/current docs/gates/gate-g docs/gates/gate-f
rg -n "GateG|前端|鉴权|Playwright|策略|回测|交易验证" AGENTS.md README.md docs/current docs/gates/gate-g docs/gates/gate-f
```

---

## 8. Codex 执行工作流（必须照做）

1. 先读 `AGENTS.md`、`README.md`、`docs/current/*`
2. 再读 `docs/gates/gate-g/*`
3. 再读目标代码文件
4. 先补文档，再改代码，再补测试
5. 提交结果时明确本批归属 GateG-DOC-1 / GateG-1 / GateG-2 / GateG-3 / GateG-4 / GateG-5 / GateG-6

## 9. MCP / Skills 使用规范（项目级强制）

### 0) 总原则
- 涉及当前项目的检索、读取、编辑、重构、运行、结构分析：**优先使用 `idea-mcp`**。
- 任何工具调用必须遵循：**最小权限、最小改动、可审计**。
- 不允许“只描述不执行”：能用工具验证的结论必须用工具验证。

### 0.1 强制 projectPath（避免多窗口/多项目误操作）
- 调用 `idea-mcp` 的任何工具时，若该工具支持 `projectPath` 参数，必须显式传入。
- `projectPath` 取值规则（按优先级）：
  1) 若本仓库已约定根目录变量：`{{PROJECT_ROOT}}`（推荐在启动脚本或说明中固定）
  2) 否则通过 `get_repositories` / `list_directory_tree` 推断当前打开仓库根路径，并在后续调用中固定使用同一个 `projectPath`
- 若无法确定唯一 `projectPath`（多窗口/多项目歧义），必须停止自动修改，只给出明确的人工选择步骤。

### 0.2 修改边界（目录白名单 / 黑名单）
默认仅允许自动修改以下目录（白名单）：
- `backend/**`
- `frontend/**`
- `research/**`
- `infra/**`
- `docs/**`
- `.github/**`
- `codex/**`
- `scripts/**`

默认禁止自动修改以下目录（黑名单；除非用户明确要求且说明原因）：
- `**/target/**`、`**/build/**`、`**/.idea/**`、`**/.gradle/**`、`**/node_modules/**`
- `**/*.iml`、`**/*.class`、`**/*.jar`
- 任何明显的生成产物、缓存目录、IDE 配置目录

若任务要求修改白名单之外路径：必须先在回复中说明风险与原因，并等待用户明确授权后再执行。

---

### 1) 工具分级与用途边界（强制按场景选工具）

#### A. 工程内操作（最高优先级）
**`idea-mcp`**
- 用于：项目结构、检索、阅读、编辑、重构、运行配置、问题检查。
- 约束：
  - 若工具支持 `projectPath`，必须显式传入。
  - 自动修改仅限白名单目录（见下文“修改边界”）。
  - 编辑流程必须：读文件 → 改 → 格式化 → problems 检查（必要时）。

#### B. 代码托管与变更协作（仅限 GitHub 场景）
**`github`**
- 用于：PR/Issue 读取、diff/变更审阅、提交记录查询、仓库信息读取。
- 禁止：未经明确指令自动创建/合并 PR、强推、删除分支、改仓库设置。
- 安全：Token 必须用环境变量提供（例如 `GITHUB_MCP_PAT`），不得写入仓库文件。

#### C. 外部资料检索（只用于“资料/对照”，不直接改代码）
**`brave-search`**
- 用于：查外部信息、对照官方文档、定位第三方库用法、搜错误码/报错。
- 禁止：把搜索结果当最终事实；必须给出来源并在项目内用工具验证可落地性。
- 说明：需要 `BRAVE_API_KEY`（环境变量），启动慢则在 config.toml 配 `startup_timeout_sec`。

#### D. Web 自动化/抓取（仅在确有必要时）
**`playwright`**
- 用于：自动打开网页、抓取动态内容、模拟交互获取信息（例如登录后页面的公开信息不适用）。
- 禁止：自动进行敏感操作（提交表单、支付、账号设置更改等）。
- 说明：启动慢则在 config.toml 配 `startup_timeout_sec`。

#### E. OpenAI 官方文档查询（API/CLI 参数必须以此为准）
**`openai-docs-skill`（Skill）**
- 用于：查询 OpenAI 文档、Responses API、Codex CLI、MCP/skill 官方说明。
- 约束：涉及 OpenAI 参数/行为结论，若可查证必须先查 docs 再给结论。

---


### 1.1 `idea-mcp` 常用能力清单（按场景）
- 项目与结构：`get_project_modules`、`get_project_dependencies`、`get_repositories`、`list_directory_tree`
- 检索与阅读：`find_files_by_glob`、`find_files_by_name_keyword`、`search_in_files_by_text`、`search_in_files_by_regex`、`get_file_text_by_path`、`get_all_open_file_paths`
- 代码理解与质量：`get_symbol_info`、`get_file_problems`
- 编辑与重构：`create_new_file`、`replace_text_in_file`、`rename_refactoring`、`reformat_file`、`open_file_in_editor`
- 执行与联调：`get_run_configurations`、`execute_run_configuration`、`execute_terminal_command`

### 2) 不可用降级条件（通用）
当首选工具出现以下任一情况，允许降级到下一优先级工具：
1. 不可访问/连接失败
2. 无权限/拒绝访问
3. 同一问题连续 2 次超时
4. 返回结果无法覆盖问题（范围不完整/关键文件不可读/结果与事实矛盾）

---

### 3) 降级顺序（按场景固定）

#### 工程内检索（定位文件/符号）
1) `idea-mcp`（search/find）
2) `rg`
3) `Select-String`（PowerShell）
4) `findstr`

#### 外部资料核对
1) `openai-docs-skill`（涉及 OpenAI）
2) `brave-search`
3) 手动引用（必须带来源且标注可信度）

---

### 4) 降级披露要求（强制）
一旦发生降级，回复必须包含：
- **降级原因**
- **使用的工具**
- **检索范围**
- **结果可信度**（高/中/低 + 简述原因）

---

### 5) 启动与密钥规则（强制）
- 所有 API Key / Token 仅允许通过 **环境变量** 注入：
  - GitHub：`GITHUB_MCP_PAT`
  - Brave：`BRAVE_API_KEY`
  - 其他：按各 MCP server 文档约定
- 禁止把密钥写入：仓库文件、Markdown、脚本、截图、日志输出。

---

### 6) 编辑与运行纪律（强制）
- 编辑：先读 → 再改 → 再格式化 → 必要时 problems 检查
- 运行：先列出 run configs → 再执行 → 汇报退出状态与关键输出

#### 编辑类任务（强制流程）
1. 先 `get_file_text_by_path` 读取目标文件，确认上下文
2. 再执行 `replace_text_in_file` / `rename_refactoring` / `create_new_file`
3. 修改后必须 `reformat_file`
4. 最后用 `get_file_problems`（必要时）确认无明显错误/警告激增
5. 回复中列出：修改的文件清单 + 变更摘要（必要时给出关键片段）

#### 运行/联调类任务（强制流程）
1. 先 `get_run_configurations` 确认可用目标
2. 再 `execute_run_configuration`（必要时设置合理超时）
3. 回复中汇报：退出状态 + 关键输出摘要（必要时附报错关键信息）

---

### 7) Skills 路由规则

当任务命中以下场景时，必须优先使用对应 skill，不要跳过：

#### 前端任务
- 新增页面、根据接口落地页面：`build-page-from-api`
- 新建业务组件、弹窗、抽屉、筛选块：`scaffold-component`
- 接入后端接口、补 query/mutation、整理 query key：`wire-api-module`
- 修复前端页面、路由、状态、表单、Ant Design 交互问题：`fix-ui-bug`
- 关键链路回归、Playwright 用例补齐：`e2e-regression`
- 前端改动收口审查、合并前检查：`frontend-review`

#### 后端任务
- 修复 Java 后端问题、异常链、事务、幂等、状态流转问题：`fix-prod-bug-java`
- 审查 DDL、migration、索引、约束、backfill：`review-ddl-and-migration`
- 补 JUnit、golden case、关键回归测试：`write-junit-and-golden-tests`
- 收口 service 层、拆分巨型类、整理事务边界：`refactor-service-layer-java`
- 审查 Spring Boot 模块边界、装配、配置和依赖关系：`spring-boot-module-review`
- 做 Controller -> Service -> Repository -> DB 闭环回归：`integration-regression-java`

#### Python 辅助任务
- 编写批处理、修数、迁移、导入导出脚本：`build-batch-script-python`
- 为 Python 脚本和工具补 pytest 回归：`write-pytest-regression`

#### 执行要求
1. 先判断任务属于哪一类，再进入对应 skill。
2. 一个任务只允许以一个主 skill 为主线，其他 skill 只作为补充。
3. 不允许跳过 skill 直接自由发挥，除非任务明显不属于任何已定义 skill。
4. 完成后必须输出：新增文件、修改文件、验证步骤、风险与未覆盖项。
