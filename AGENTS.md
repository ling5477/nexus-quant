# AGENTS（Codex 开发指引 - NexusQuant）

> 目的：让 Codex / 开发者在本仓库内严格遵循当前阶段、模块边界、文档事实源、验证纪律和禁止范围。
> 当前事实源：`docs/current/`。

## 1. 当前阶段

Current stage: GateJ completed

Previous completed stages:

- DOC-CLEAN
- BASELINE-FIX
- GateH
- GateI-PLAN
- GateI-1-WO
- GateI-2-WO
- GateI-3-WO
- GateI-3-FIX
- GateI-4-WO
- GateI-4-FIX
- GateI
- GateJ-PLAN
- GateJ-1-WO
- GateJ-2-WO
- GateJ-3-WO
- DOC-CLEAN-2
- PRE-FREEZE-CODE-AUDIT
- PRE-FREEZE-CODE-AUDIT-SECOND-PASS
- AUDIT-FIX
- GateJ-FREEZE-FIX
- GateJ-FREEZE-FIX-SECOND-PASS
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance
- GateJ

Next allowed: GateK-PLAN。GateJ-FREEZE 30m / 1h / 24h / 7d acceptance 已通过，GateJ completed，详见 `docs/current/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`。GateK-PLAN 只允许规划 AI 信号接入，不代表 GateK 实现、AI 接入、DH 集成或真实交易已启动。

GateJ-FREEZE 最终状态：
- 30m / 1h / 24h / 7d 连续运行验收 passed。
- `docs/gates/gate-j/` 已允许作为 GateJ completed 冻结卷宗。
- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- UI/UX professionalism remains post-freeze remediation。

GateJ-FREEZE 禁止范围：
- 不接 AI、不做 AI 信号 / AI 自动交易 / AI Paper Trading。
- 不做 GateK 任何实现。
- 不新增业务功能、API、migration。
- 不改前端页面功能。
- 不做真实 LIVE 下单、不调用真实交易所下单接口。
- 不把 GateK 写成 started，除非后续单独开工。
- 不宣称 UI/UX 专业化已完成。
- 不宣称公开用户生产就绪。

GateJ 是 Paper Trading 稳定运行阶段，不是 AI 阶段。AI 最早 GateK 才允许进入信号层。

GateJ 只允许 Paper Trading 稳定运行。GateJ 严禁：

- 接 AI。
- 做 AI 信号、AI 自动交易、AI Paper Trading。
- 做真实 LIVE 下单。
- 调用真实交易所下单接口。
- 做美股/A 股。
- 做合约全量。
- 做高频。
- 做复杂因子平台。
- 修改历史 migration。
- 新增无注释表或无注释字段。

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
- GateI 已完成并冻结，归档在 `docs/gates/gate-i`。
- GateH 已完成并冻结，归档在 `docs/gates/gate-h`。
- GateJ 已完成并冻结，归档在 `docs/gates/gate-j`。
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
- 不创建 `docs/gates/gate-j`，直到 GateJ 整体完成并冻结。

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

### 7) Agent Skills Routing（合并后 skills 规则）

#### 7.1 Active skills（唯一默认启用集合）

当前 active skills 仅允许以下 8 个：

1. `frontend-product-ui-design`
2. `ui-visual-system-polish`
3. `frontend-antd-page-builder`
4. `frontend-quality-regression`
5. `java-backend-maintenance`
6. `java-backend-regression-tests`
7. `db-schema-migration-review`
8. `python-ops-tooling`

使用原则：

- 只选择与本轮任务直接相关的 skill，不要一次性激活所有 skills。
- 一个任务最多一个主 skill；其他 skill 只能作为补充，并说明为什么需要。
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
