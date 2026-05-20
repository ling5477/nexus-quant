# AGENTS（Codex 开发指引 - NexusQuant）

> 目的：让 Codex / 开发者在本仓库内严格遵循当前阶段、模块边界、文档事实源、验证纪律和禁止范围。
> 当前事实源：`docs/current/`。

## 1. 当前阶段

Current stage: GateI-3-WO preparation

Previous completed stages:

- DOC-CLEAN
- BASELINE-FIX
- GateH
- GateI-PLAN
- GateI-1-WO
- GateI-2-WO

Next: `GateI-3-WO`。

GateI 当前仍处于 active/current 阶段，尚未整体完成。GateI-3-WO 只允许做 SIM / Paper Trading 运行闭环。

## 2. GateI-3 允许范围

GateI-3-WO 只能覆盖以下内容：

- Paper Trading run 模型。
- 发布版本创建 Paper run。
- Paper run 启动、停止、查询。
- Paper run 固化 publish / strategy version / dataset / param / config snapshot。
- Paper orders / trades / positions 最小闭环。
- 前端 Paper Trading 入口。
- E2E smoke。
- `docs/current` 更新。

## 3. 严格禁止范围

- 不接 AI。
- 不新增 AI 模块。
- 不做 AI 信号。
- 不做 AI 自动交易。
- 不做 AI Paper Trading。
- 不进入 GateI-4。
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
- GateI 仍未完成，不要创建 `docs/gates/gate-i`。
- GateH 已完成，如尚未归档，应补 `docs/gates/gate-h` freeze snapshot。
- 新 Gate 或新 WO 开始前必须先阅读 `docs/current` 对应计划文档。
- 每轮完成后必须按实际改动更新 `docs/current/STATUS.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`。
- 文档描述必须与代码和测试状态一致；未执行验证不能写成通过。

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

如果只改文档，可以不跑全量测试，但必须在 `WORKLOG.md` / `TESTING.md` 中写清未跑原因。

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
- 不创建 `docs/gates/gate-i`，直到 GateI 整体完成并冻结。

## 9. Codex 执行纪律

- 默认使用简体中文说明计划、过程和结论。
- 先读 `AGENTS.md`、`README.md`、`docs/current/*`，再读目标代码或文档。
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

### 7) Skills 路由规则（更新版）

#### 7.1 总原则
1. 先判断任务属于哪一层：**业务实现 / 交互设计 / 通用组件 / 视觉抛光 / CSS 特效 / 后端修复 / 测试回归**。
2. 一个任务只允许一个主 skill，其他 skill 或 MCP 只能作为补充。
3. 不允许跳过 skill 直接自由发挥，除非任务明显不属于任何已定义 skill。
4. `UI-UX-Pro-Max`、`shadcn-ui`、`impeccable` 不替代原有业务 skill，它们属于前端增强层。
5. `icss` 只能作为辅助 MCP，不作为主 skill。

#### 7.2 前端任务路由

##### A. 页面与业务功能实现
- 新增页面、根据接口落地页面：`build-page-from-api`
- 新建业务组件、弹窗、抽屉、筛选块：`scaffold-component`
- 接入后端接口、补 query/mutation、整理 query key：`wire-api-module`
- 修复前端页面、路由、状态、表单、Ant Design 交互问题：`fix-ui-bug`
- 关键链路回归、Playwright 用例补齐：`e2e-regression`
- 前端改动收口审查、合并前检查：`frontend-review`

##### B. 交互与视觉设计层
以下场景主 skill 改为 `UI-UX-Pro-Max`：
- 页面从 0 到 1 设计
- 需要先做信息层级、区域布局、交互动线
- 需要统一空态、错误态、加载态、禁用态
- 需要优化工作台、列表页、详情页、表单页整体体验
- 需要先出结构方案，再进入业务编码

##### C. 通用组件与设计系统层
以下场景主 skill 改为 `shadcn-ui`：
- 抽象可复用组件，不是一次性业务页
- 统一 Button / Dialog / Drawer / Form / Table / Tabs / Sheet 等模式
- 设计组件 props、variant、size、受控/非受控边界
- 构建通用过滤器块、页面骨架、操作栏、数据展示组件

##### D. 抛光与一致性收口层
以下场景主 skill 改为 `impeccable`：
- 功能已完成，进入 polish 阶段
- 统一间距、排版、层级、圆角、阴影、hover/focus 反馈
- 提升整体完成度与一致性
- 做交付前视觉细节收口

#### 7.3 后端任务路由
- 修复 Java 后端问题、异常链、事务、幂等、状态流转问题：`fix-prod-bug-java`
- 审查 DDL、migration、索引、约束、backfill：`review-ddl-and-migration`
- 补 JUnit、golden case、关键回归测试：`write-junit-and-golden-tests`
- 收口 service 层、拆分巨型类、整理事务边界：`refactor-service-layer-java`
- 审查 Spring Boot 模块边界、装配、配置和依赖关系：`spring-boot-module-review`
- 做 Controller -> Service -> Repository -> DB 闭环回归：`integration-regression-java`

#### 7.4 Python 辅助任务路由
- 编写批处理、修数、迁移、导入导出脚本：`build-batch-script-python`
- 为 Python 脚本和工具补 pytest 回归：`write-pytest-regression`

#### 7.5 MCP 辅助规则
以下 MCP 只作为辅助，不改变主 skill：
- 前端运行态问题：`chrome-devtools`
- 复杂 CSS / 动画：`icss`
- 查询 DB 结构 / 数据：`postgres`
- 本地依赖与容器联调：`MCP_DOCKER`
- 读写普通文件或兜底检索：`filesystem`

#### 7.6 组合约束
- 合法：
  - `build-page-from-api` + `UI-UX-Pro-Max`
  - `scaffold-component` + `shadcn-ui`
  - `frontend-review` + `impeccable`
  - `fix-ui-bug` + `chrome-devtools`
  - `fix-ui-bug` + `icss`
  - `review-ddl-and-migration` + `postgres`
  - `integration-regression-java` + `MCP_DOCKER`
- 非法：
  - 两个业务主 skill 同时并列为主线
  - `icss` 作为主 skill
  - `impeccable` 代替业务功能开发主线
  - `filesystem` 代替 `idea-mcp` 做大规模源码重构

#### 7.7 输出要求
完成后必须输出：
1. 主 skill 是什么，为什么命中
2. 辅助 skill / MCP 是什么，为什么需要
3. 新增文件
4. 修改文件
5. 验证步骤
6. 风险与未覆盖项
