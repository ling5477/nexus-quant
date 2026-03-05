# AGENTS.md
# AGENTS（Codex 开发指引 - NexusQuant）

> 目的：让 Codex / 开发者在仓库内生成与修改代码时，严格遵循本仓库的架构/契约/正确性约束，并始终以“当前阶段入口文档”为准。  
> 文档分层：`docs/current/` 为**当前阶段唯一入口（Source of Truth）**；`docs/gates/gate-*/` 为**历史 Gate 冻结快照（只读参考）**。

---

## 1. 强制约束（必须遵守）

- 语言：除代码/技术名词外，解释与文档输出使用**简体中文**。
- 严格状态机：不得任意 `setStatus`；必须通过显式事件驱动迁移（命令/回执/同步器确认）。
- 幂等：`client_order_id` 必须贯穿订单/事件/账本引用，并作为外部 clientId 映射（如 OKX clOrdId / Binance clientOrderId）。
- 可审计：所有关键决策点必须记录 `trace_id` 与原因（reason），写入 `audit_logs` + `event_store`（必要时写 `risk_events`）。
- 可恢复：投影表允许丢失，但必须能从事实（`event_store` / `ledger_entries`）重建；恢复流程必须有文档与验收。
- 可观测：日志字段统一（`trace_id、run_id、strategy_id、account_id、symbol、venue` 等），禁止“只有一条字符串日志”。
- 交易所差异隔离：交易所方言只允许出现在 `nq-adapter-*`；`nq-core/nq-ledger/nq-risk` 禁止出现 `if (venue==...)` 之类分支。

---

## 2. 文档即事实（Source of Truth）

实现必须对齐以下文档（按优先级）：

### 2.1 当前阶段入口（必读，唯一事实来源）
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`

### 2.2 当前 Gate 的权威依据（必须能追溯）
- `docs/gates/gate-c/SOURCES.md`（当前 Gate 为 GateC 时）
- 未来 Gate 切换后，应在对应 gate 目录下维护同名 `SOURCES.md`

> 规则：当 `docs/current/*` 与 `docs/gates/*` 不一致时，**以 `docs/current/*` 为准**。

---

## 3. 模块实现顺序（推荐）

1) `nq-contracts` / `nq-common`
2) `nq-core`
3) `nq-ledger`
4) `nq-risk`
5) `nq-observability`
6) `nq-infra`
7) `nq-adapter-api`
8) `nq-adapter-okx` / `nq-adapter-binance`
9) `nq-scheduler`
10) `nq-app`

---

## 4. Gate 通用硬规则（不随阶段变化）

### 4.1 执行链路规则（通用）
- 任何“下单/撤单/查单/拉成交”的外部交互必须通过 `nq-adapter-api` 接口完成。
- 任何外部回执必须事件化（OrderAck/Reject/TradeExecuted/CancelAck…）并写入 `event_store`（推荐存 envelope 全量 JSON）。

### 4.2 超时与重试规则（通用）
- 外部请求超时/网络异常：**禁止盲重试**。必须先 `query-confirm`（查单/挂单/成交）确认外部事实，再进行补偿动作。

### 4.3 数据与精度规则（通用）
- 禁止 float/double 参与价格/数量/金额计算；必须 BigDecimal（或 long scale 方案，但需统一政策）。
- TIMESTAMPTZ 入参统一 `Timestamp.from(Instant)`。
- JSONB 写入统一 `CAST(? AS jsonb)`。

---

## 5. PR 要求（强制）

- PR 必须对应 `docs/current/GATE_CHECKLIST.md` 的条目（写在 PR 描述里）。
- 若修改了：契约（contracts）、DB（Flyway）、状态机、幂等键、恢复流程 —— 必须同步更新 `docs/current/*`，并在对应 Gate 的 `SOURCES.md` 补齐依据（必要时补链接）。

---

## 6. 快速验证（通用）

- `mvn -q -f backend/pom.xml test`
- `docker compose up -d postgres`
- `mvn -q -f backend/pom.xml -pl nq-app spring-boot:run`

## 7. 常见禁止项（强制）

- 禁止在 `nq-core/nq-ledger/nq-risk` 出现交易所方言分支（venue if/else）。
- 禁止绕过 `OrderCommandService` 直接改 orders 状态。
- 禁止 adapter 直接写 ledger/positions（adapter 只负责对接与映射，不负责记账与投影）。
- 禁止为了“先跑通”而删掉审计/幂等/状态机/事实链：这些是 NexusQuant 的底座，不是可选项。

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