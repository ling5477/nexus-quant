# AGENTS.md
# AGENTS（Codex 开发指引 - NexusQuant）

> 目的：让 Codex / 开发者在仓库内生成与修改代码时，严格遵循本仓库的架构、契约、阶段门禁与当前卷宗边界。
> 文档分层：`docs/current/` 为**当前阶段唯一入口（Source of Truth）**；`docs/gates/gate-*/` 为**冻结卷宗（只读参考）**。

---

## 1. 强制约束（必须遵守）

- 语言：除代码、配置键、接口字段、类名外，解释与文档输出使用**简体中文**。
- 当前阶段：**RC1（项目收口重构批次）**。
- 唯一入口：`docs/current/README.md`、`docs/current/REFACTOR_BATCH_RC1.md`、`docs/current/RC1_CHECKLIST.md`。
- RC1 目标固定为：**仓库清理、表结构重构、账户与凭证模型建立、模块边界整理、包结构收口、历史残留清理、市场数据域落点、前端基础重构、ArchUnit 与全量验证**。
- RC1 完成前，**禁止恢复 GateH 新功能开发**。
- RC1 不做新交易所接入、不做复杂研究功能扩展、不做大规模 UI 美化。
- 正式 HTTP API 统一使用 `/api/**`；旧 `/__gated/**` 只允许出现在历史文档说明中。
- 正式认证方式继续沿用：`POST /api/auth/login` + `Authorization: Bearer <token>` + `GET /api/auth/me`，但认证数据源要切换到 DB-backed `users/roles/user_roles`。
- 交易所环境 canonical 口径固定为 `SIM / LIVE`；`DOME / REAL` 只允许存在于 legacy 导入映射层。

---

## 2. 当前阶段切换说明

- GateD 已冻结，`docs/gates/gate-d/*` 仅作历史参考。
- GateE 已冻结，`docs/gates/gate-e/*` 仅作历史参考。
- GateF 已完成并冻结，`docs/gates/gate-f/*` 只读参考。
- GateG 已完成并冻结，`docs/gates/gate-g/*` 只读参考。
- GateH 已暂停，`docs/gates/gate-h/*` 只作为暂停卷宗保留。
- 当前 `docs/current/*` 表示 RC1 当前入口。
- 当前 source of truth 优先级：
  1. `docs/current/*`
  2. `docs/gates/gate-h/*`
  3. `docs/gates/gate-g/*`
  4. `docs/gates/gate-f/*`
  5. `docs/gates/gate-e/*`
  6. 根 `README.md / docs/*.md` 导航摘要

---

## 3. 文档即事实（Source of Truth）

### 3.1 当前阶段入口（必读）
- `docs/current/README.md`
- `docs/current/REFACTOR_BATCH_RC1.md`
- `docs/current/RC1_CHECKLIST.md`
- `docs/current/MODULES.md`
- `docs/current/WORK_TEMPLATE.md`

### 3.2 当前 RC1 权威文档
- `docs/current/REFACTOR_BATCH_RC1.md`
- `docs/current/RC1_CHECKLIST.md`
- `docs/current/MODULES.md`

### 3.3 暂停 / 冻结卷宗（只读参考）
- `docs/gates/gate-h/*`
- `docs/gates/gate-g/*`
- `docs/gates/gate-f/*`

> 规则：当 `docs/current/*` 与 `docs/gates/*` 不一致时，以 `docs/current/*` 为准。

---

## 4. 当前执行顺序（RC1）

1. RC1-0：文档切换
2. RC1-1：仓库清理
3. RC1-2：表结构与账户/凭证主模型
4. RC1-3：Java 模块与包结构收口
5. RC1-4：前端基础重构
6. RC1-5：marketdata 域与 Python 研究骨架
7. RC1-6：残留清理、ArchUnit 与全量验证

---

## 5. RC1 代码约束（强制）

### 5.1 frontend
- RC1 前端技术栈继续固定为：React 19 + TypeScript + Vite 8 + React Router + TanStack Query + Axios + Zustand + Ant Design + Playwright。
- 必须建立正式账户上下文，不再把“手工输入 `accountId`”作为长期主模式。
- 账户 / 交易所 / 环境切换必须成为正式 UI 概念。
- 所有 API 调用继续统一走 `frontend/src/api/*` 封装，不允许页面内散写请求。
- 页面拆分优先按业务域与页面壳复用，不再继续堆叠巨型页面。

### 5.2 backend
- `nq-core` 不再包含 JDBC 实现。
- `nq-api` 不再直接写 SQL。
- controller 不再直接依赖 scheduler 具体实现。
- 包结构按业务域优先整理：`account / auth / strategy / trading / research / marketdata`。
- 交易所凭证必须进入数据库密文存储模型；正式运行 profile 禁止 legacy env 主读。

---

## 6. PR 要求（强制）

- PR / 提交必须对应 `RC1-0 ~ RC1-6` 的条目。
- 提交信息必须标明本次归属的 `RC1-x` 子批次。
- 每次提交说明必须写清：
  - 改动内容
  - 改动原因
  - 影响范围
  - 删除项
  - 验证结果
  - 兼容字段 / 兼容接口退役计划

---

## 7. 快速验证（RC1 当前阶段）

```powershell
git diff -- AGENTS.md README.md docs/current docs/gates/gate-h docs/gates/gate-g
rg -n "RC1|GateH|exchange_accounts|exchange_account_credentials|marketdata|account context" AGENTS.md README.md docs/current docs/gates/gate-h docs/gates/gate-g
```

---

## 8. Codex 执行工作流（必须照做）

1. 先读 `AGENTS.md`、`README.md`、`docs/current/*`
2. 再读 `docs/gates/gate-h/*` 与 `docs/gates/gate-g/*`，只用于核对暂停边界与冻结基线
3. 再读目标代码文件
4. 先补文档，再改代码，再补测试
5. 提交结果时明确本批归属 `RC1-0 / RC1-1 / RC1-2 / RC1-3 / RC1-4 / RC1-5 / RC1-6`

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

## RC1 项目收口重构批次（停止 GateH，先做结构收口）

当前主线不是 GateH 新功能开发，当前主线为 **RC1 项目收口重构批次**。
RC1 完成前，暂停 GateH 的新增功能、页面扩展、新交易所接入、复杂研究流程深化与非必要 UI 美化。
所有工作优先围绕：**仓库清理、表结构收口、账户与凭证模型建立、模块边界整理、包结构收口、历史残留删除、市场数据域落点、前端基础骨架重构、全量回归验证** 展开。

### RC1 阶段强制执行原则

1. 不允许继续沿用“边加功能边自然生长”的方式推进项目。
2. 优先收口结构，再继续后续 Gate 规划。
3. 任何改动都必须先判断它属于：
  - 结构收口
  - 历史清理
  - 表结构重构
  - 边界隔离
  - 前端基础重构
  - 回归验证
4. 不属于 RC1 范围的新功能需求，一律延后到 RC1 完成后再规划。
5. 能复用的复用，但必须是**受控复用**；允许保留必要冗余，但必须是**受控冗余**，不允许无序扩散。

### RC1 阶段的 skill 路由规则

#### 1. 表结构 / Migration / DDL 审查
涉及以下任务时，优先使用 `review-ddl-and-migration`：
- 新增或修改表结构
- 新增 migration
- 审查字段、索引、唯一约束、外键、回填方案
- 审查兼容字段退役计划
- 审查用户 / 账户 / 凭证 / 环境（SIM/LIVE）相关建模

RC1 中该 skill 重点覆盖：
- `exchange_accounts`
- `exchange_account_credentials`
- owner_user_id / exchange_code / trade_env / is_default / status
- 凭证密文存储与校验状态字段
- marketdata 第一批表结构

#### 2. Java 模块边界、包结构、服务层重构
涉及以下任务时，优先组合使用：
- `spring-boot-module-review`
- `refactor-service-layer-java`
- `wire-api-module`

适用场景：
- `nq-core` 中 JDBC 实现迁出
- `nq-api` 直写 SQL 下沉
- controller 与 scheduler 具体实现解耦
- 按业务域重整包结构
- 拆分 `nq-app` 过胖装配配置类
- 审查无用 Bean、重复配置、历史残留实现

RC1 中 Java 结构重构必须遵守：
- `nq-core` 只留业务核心，不保留 JDBC repository 实现
- `nq-api` 不直接写 SQL
- `nq-api` 不直接依赖 `nq-scheduler` 的具体实现类
- 包结构采用“业务域优先，类型次级分层”，至少覆盖：
  - `account`
  - `auth`
  - `strategy`
  - `trading`
  - `research`
  - `marketdata`

#### 3. 前端基础重构
涉及以下任务时，优先组合使用：
- `frontend-review`
- `scaffold-component`
- `build-page-from-api`

适用场景：
- 页面大文件拆分
- 抽查询区 / 表格区 / 详情抽屉壳
- 搭账户上下文
- 搭账户与凭证管理页面骨架
- 页面与后端新接口联调
- 清理重复逻辑、废弃页面模式

RC1 中前端重构重点覆盖：
- `TradeValidationPage`
- `StrategiesPage`
- `SchedulesPage`
- `ResearchPage`
- `BacktestsPage`

RC1 前端必须遵守：
- 不再把“手工输入 accountId”作为长期主模式
- 建立正式的账户上下文
- 账户 / 交易所 / 环境切换成为正式概念
- 优先抽公共骨架，而不是继续复制页面

#### 4. Python 研究骨架与批处理脚本
涉及以下任务时，优先使用：
- `build-batch-script-python`
- `write-pytest-regression`

适用场景：
- market data ingest 脚本
- research/py 子工程骨架收口
- pytest 回归
- Python 数据加载、回测、策略运行基础能力

RC1 中 Python 侧重点：
- `research/py` 从样例目录升级为正式研究子工程
- 补 `tests`
- 补 `pytest`
- 补 `ruff`
- 补 `mypy`
- 建立 `data / strategy / backtest` 包层

#### 5. 回归验证与收口验收
涉及以下任务时，优先组合使用：
- `integration-regression-java`
- `write-junit-and-golden-tests`
- `e2e-regression`

适用场景：
- 模块迁移后的回归验证
- JUnit / golden 补测
- 前端 E2E smoke 回归
- 收口批次的最终验收

RC1 最终必须验证：
- migration 可执行
- 后端可构建、可启动
- 前端可 build
- E2E smoke 可通过
- 核心边界具备自动化约束

### RC1 阶段的辅助修复 skill

以下 skill 可以使用，但不作为 RC1 设计主导，只用于收口过程中处理回归问题：
- `fix-prod-bug-java`
- `fix-ui-bug`

使用原则：
- 仅用于修复重构过程中暴露出的启动失败、依赖注入异常、SQL 映射错误、接口回归、UI 回归
- 不得把它们当作架构设计工具
- 架构与结构决策始终以 RC1 文档和当前主线规则为准

### RC1 阶段输出要求

1. 每次提交必须说明本次改动属于 RC1 的哪个子批次。
2. 每次提交必须写清：
  - 改了什么
  - 为什么改
  - 是否影响表结构
  - 是否影响模块边界
  - 是否删除了历史残留
  - 做了哪些验证
3. 涉及删除历史实现时，必须明确归类为：
  - 直接删除
  - 迁移到测试夹具 / 历史归档
  - 保留但仅限 local/test/fallback
4. 涉及兼容字段或兼容接口时，必须写清退役计划。
5. RC1 完成前，不得擅自恢复 GateH 开发。

### RC1 结束条件

只有在以下条件全部满足后，才允许重新规划 GateH：
- 用户 / 账户 / 凭证 / 环境模型正式成立
- `nq-core / nq-api / nq-infra / nq-scheduler / nq-app` 边界清晰
- 前端存在正式账户上下文和账户凭证管理入口
- marketdata 域已建立
- Python 研究子工程骨架已建立
- 历史残留实现已明显清理
- 全量构建、启动、测试、E2E smoke 通过
- RC1 文档完整闭环
