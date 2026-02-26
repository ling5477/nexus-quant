# AGENTS（Codex 开发指引 - NexusQuant）

> 目的：让 Codex 生成代码时严格遵循本仓库的架构/契约/正确性约束，并始终以“当前阶段入口文档”为准。  
> 文档分层：`docs/current/` 为当前阶段入口；`docs/gates/gate-a/` 为 Gate A 冻结快照（只读）。

## 1. 强制约束（必须遵守）

- 语言：除代码/技术名词外，解释与文档输出使用**简体中文**。
- 严格状态机：不得任意 setStatus；必须通过显式事件驱动迁移。
- 幂等：`client_order_id` 必须贯穿订单/事件/账本引用。
- 可审计：所有关键决策点必须记录 traceId 与原因（reason）。
- 可恢复：投影表允许丢失，但必须能从事实（事件/账本）重建。
- 可观测：日志为结构化（JSON），并统一字段（trace_id、run_id、strategy_id、account_id、symbol 等）。

## 2. 文档即事实（Source of Truth）

实现必须对齐以下文档（按优先级）：

**当前阶段入口（必读）**
- `docs/current/GATE_CHECKLIST.md`
- `docs/current/WORK_TEMPLATE.md`（模板：阶段完成后记录写入对应 gate 目录）

**历史冻结参考（只读）**
- `docs/gates/gate-a/ARCHITECTURE.md`
- `docs/gates/gate-a/MODULES.md`
- `docs/gates/gate-a/CONTRACTS.md`
- `docs/gates/gate-a/EVOLUTION_RULES.md`
- `docs/gates/gate-a/NUMERIC_POLICY.md`
- `docs/gates/gate-a/DB_SCHEMA.md`
- `docs/gates/gate-a/RECOVERY_RUNBOOK.md`
- `docs/gates/gate-a/DECISIONS.md`
- `docs/gates/gate-a/GATE_A_CHECKLIST.md`
- `docs/gates/gate-a/WORK.md`

**当前 Gate 详细规范（必读）**
- `docs/gates/gate-b/ARCHITECTURE.md`
- `docs/gates/gate-b/MODULES.md`
- `docs/gates/gate-b/CONTRACTS.md`
- `docs/gates/gate-b/EVOLUTION_RULES.md`
- `docs/gates/gate-b/DB_SCHEMA.md`
- `docs/gates/gate-b/RECOVERY_RUNBOOK.md`
- `docs/gates/gate-b/DECISIONS.md`
- `docs/gates/gate-b/GATE_B_CHECKLIST.md`
- `docs/gates/gate-b/WORK.md`

## 3. 模块实现顺序（推荐）

1. `nq-contracts` / `nq-common`
2. `nq-core`（域模型 + 状态机 + 幂等键）
3. `nq-ledger`（不可变流水 + 平衡校验）
4. `nq-risk`（规则框架 + 事件记录）
5. `nq-observability`（日志/trace/metrics）
6. `nq-config` / `nq-scheduler`（骨架）
7. `nq-app`（启动载体：装配与健康检查）
8. `nq-gateway` / `nq-auth` / `nq-security`（最小控制面）

## 4. 阶段约束（以 docs/current 为准）

> 重要：本节约束随 Gate 切换而切换，具体以 `docs/current/` 为准。
> `docs/gates/gate-a/` 为历史冻结快照（只读），不得以 GateA 禁止项限制 GateB 实现。

### 4.1 通用禁止项（所有 Gate）
- 禁止在 `nq-infra` 塞领域逻辑（infra 只做技术设施封装）。
- 禁止提交任何真实密钥/Token/密码；本地用 env 或示例文件。

### 4.2 Gate B（模拟盘闭环）禁止项
- 禁止实现真实交易所网络连接（不得调用真实 OKX/Binance HTTP/WebSocket）。
- 禁止实现复杂真实策略（仅允许最小示例/定时触发用于跑通闭环）。
- 禁止绕过状态机直接写 `orders.status`。
- 禁止破坏 `nq-contracts` 兼容性（只增不改）。

### 4.3 Gate B 允许项（为闭环所必需）
- 允许实现 paper/simulated adapter（推荐独立 `nq-adapter-paper`）与极简撮合逻辑。
- 允许实现订单编排（状态机/幂等）、风控最小规则、记账与平衡校验、回放事件写入、审计日志落库。
---

## MCP 优先策略（IDEA MCP / idea-mcp）

### 1) 总则：涉及项目操作优先使用 idea-mcp
当任务涉及**当前项目**任一操作：代码检索、读取、编辑、重构、运行配置、项目结构分析、质量检查时，必须优先使用 IntelliJ IDEA MCP Server：`idea-mcp`。
除非触发“不可用降级条件”，否则不得跳过 MCP 直接用本地命令行检索或凭空猜测。

### 2) 强制 projectPath（避免多窗口/多项目误操作）
- 调用 `idea-mcp` 的任何工具时，若该工具支持 `projectPath` 参数，必须显式传入 `projectPath`。
- `projectPath` 取值规则（按优先级）：
    1) 若本仓库已约定根目录变量：`{{PROJECT_ROOT}}`（推荐在启动脚本或说明中固定）
    2) 否则通过 `list_directory_tree` / `get_repositories` 推断当前打开的仓库根路径，并在后续调用中固定使用同一个 `projectPath`
- 若无法确定唯一 projectPath（多项目同名/多窗口），必须停止自动修改，仅给出人工选择步骤。

### 3) 修改边界（目录白名单 / 黑名单）
为避免误改与越权，默认仅允许自动修改以下目录（白名单）：
- `backend/**`
- `docs/**`
- `.github/**`
- `codex/**`
- `scripts/**`

默认禁止自动修改以下目录（黑名单，除非用户明确要求且说明原因）：
- `**/target/**`、`**/build/**`、`**/.idea/**`、`**/.gradle/**`、`**/node_modules/**`
- `**/*.iml`、`**/*.class`、`**/*.jar`
- 任何明显的生成产物、缓存目录、IDE 配置目录

若任务要求修改白名单之外路径：必须先在回复中说明风险与原因，并等待用户明确授权后再执行。

---

## 4) idea-mcp 能力清单（按场景）

### 项目与结构
- `get_project_modules`
- `get_project_dependencies`
- `get_repositories`
- `list_directory_tree`

### 检索与阅读
- `find_files_by_glob`
- `find_files_by_name_keyword`
- `search_in_files_by_text`
- `search_in_files_by_regex`
- `get_file_text_by_path`
- `get_all_open_file_paths`

### 代码理解与质量
- `get_symbol_info`
- `get_file_problems`

### 编辑与重构
- `create_new_file`
- `replace_text_in_file`
- `rename_refactoring`
- `reformat_file`
- `open_file_in_editor`

### 执行与联调
- `get_run_configurations`
- `execute_run_configuration`
- `execute_terminal_command`

---

## 5) 不可用降级条件（满足任一条即可降级）
当 `idea-mcp` 出现以下任一情况，允许降级到本地检索/命令行方案：

1. MCP 不可访问/连接失败（含服务未启动、端口不可达、server 未注册）
2. MCP 返回无权限/拒绝访问
3. 同一问题 **连续 2 次超时**（重试两次仍超时）
4. MCP 返回结果**无法覆盖问题**（例如：检索范围明显不完整、关键文件无法读取、工具返回空但与项目状态矛盾）

> 降级仅用于“定位/检索/只读分析”。涉及编辑/重构/运行配置时，若 MCP 不可用：应停止自动修改，改为给出明确的人工步骤或最小补丁建议。

---

## 6) 本地检索降级顺序（仅用于检索/定位）
- 首选：`rg`（ripgrep）
- 若 `rg` 不可用：PowerShell `Select-String`
- 若 `Select-String` 不可用：Windows `findstr`

---

## 7) 降级披露要求（强制输出字段）
一旦发生降级，回复中必须包含以下字段（字段名保持一致）：

- **降级原因**：例如 `idea-mcp 连接失败 / 无权限 / 连续两次超时 / 结果不覆盖问题`
- **检索方式**：`rg` / `Select-String` / `findstr`
- **检索范围**：例如 `backend/`、`docs/`、全仓库等
- **结果可信度**：`高/中/低`（并简述理由：是否全量扫描、是否可能漏掉、是否依赖索引等）

---

## 8) 执行与编辑纪律（防止“只说不做”与误改）

### 检索类任务
- 必须实际调用 `idea-mcp`（或降级检索）得到结论。
- 输出必须包含：**命中文件路径** + **关键命中片段**（若工具能提供行号/范围则一并给出）。

### 编辑类任务（强制流程）
1. 先 `get_file_text_by_path` 读取目标文件，确认上下文
2. 再执行 `replace_text_in_file` / `rename_refactoring` / `create_new_file`
3. 修改后必须 `reformat_file`
4. 最后用 `get_file_problems`（必要时）确认无明显错误/警告激增
5. 回复中列出：修改的文件清单 + 变更摘要（必要时给出关键片段）

### 运行/联调类任务（强制流程）
1. 先 `get_run_configurations` 确认可用目标
2. 再 `execute_run_configuration`（必要时设置合理超时）
3. 回复中汇报：**退出状态** + **关键输出摘要**（必要时附报错关键信息）

## Appendix：原英文版本（保留参考）

# Repository Guidelines

## Project Structure & Module Organization
This repository is currently documentation-first. Use `docs/` as the source of truth:
- `docs/ARCHITECTURE.md`: target mono-repo layout and module boundaries
- `docs/CONTRACTS.md`: HTTP/event contracts and trace rules
- `docs/DECISIONS.md`: ADRs that must be updated before major design changes
- `docs/GATE_A_CHECKLIST.md`: Gate A acceptance checklist

Planned structure (per architecture baseline): `backend/` (Java services), `research/` (Python research), `frontend/`, `infra/`, and `docs/`.

## Build, Test, and Development Commands
Use PowerShell from repo root:
- `Get-ChildItem docs` - quick documentation sanity check.
- `cd backend; mvn -q test` - run backend unit tests (after backend scaffold exists).
- `docker compose up -d postgres` - start local PostgreSQL for Flyway migration testing.
- `docker compose down` - stop local infrastructure.

If a command is not available yet, align the missing files first with `docs/ARCHITECTURE.md` and `docs/GATE_A_CHECKLIST.md`.

## Coding Style & Naming Conventions
- Java package base: `com.guidinglight.nexusquant`.
- Module naming: `nq-*` (for example, `nq-core`, `nq-auth`, `nq-gateway`).
- Use `BigDecimal` for price/qty/amount and `Instant` (UTC) for timestamps.
- Prefer 4-space indentation, UTF-8, and descriptive names (`OrderStateMachine`, `RecoveryService`).
- Keep changes minimal and scoped; avoid cross-module refactors in a single commit.

## Testing Guidelines
- Add unit tests for every core logic change, especially state machine, idempotency, ledger balance, and recovery.
- Use regression tests for bug fixes.
- Test naming: `*Test` for unit tests; method names should describe behavior (for example, `shouldRejectInvalidTransition`).
- Main verification command: `cd backend; mvn -q test`.

## Commit & Pull Request Guidelines
Current history is bootstrap-level (`init`), so contributors should standardize now:
- Use Conventional Commits: `feat(scope): ...`, `fix(scope): ...`, `test(scope): ...`, `docs(scope): ...`.
- Keep one concern per commit (feature vs formatting vs docs).
- PRs should include: summary, changed paths, linked issue/ADR, and test evidence (command + result).

## Security & Configuration Tips
- Never commit secrets (tokens, keys, passwords).
- Use environment variables for credentials and local overrides.
- Preserve traceability: keep `X-Trace-Id` propagation and audit-related changes aligned with `docs/CONTRACTS.md`.