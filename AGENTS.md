# AGENTS.md
# AGENTS（Codex 开发指引 - NexusQuant）

> 目的：让 Codex / 开发者在仓库内生成与修改代码时，严格遵循本仓库的架构、契约、正确性与阶段门禁约束，并始终以“当前阶段入口文档”为准。
> 文档分层：`docs/current/` 为**当前阶段唯一入口（Source of Truth）**；`docs/gates/gate-*/` 为**历史 Gate 冻结快照（只读参考）**。

---

## 1. 强制约束（必须遵守）

- 语言：除代码、配置键、接口字段、类名外，解释与文档输出使用**简体中文**。
- 当前阶段：**GateD（统一执行闭环与执行域硬化）**。
- 唯一入口：`docs/current/README.md` 与 `docs/current/GATE_CHECKLIST.md`。
- 严格状态机：禁止任意 `setStatus`；订单状态只能通过显式事件、同步确认、回执映射推进。
- 幂等：`client_order_id`、`request_id`、`trace_id` 必须贯穿订单、事件、账本、补偿链路。
- 可审计：所有关键决策点必须记录 `trace_id`、`reason`、`account_id`、`symbol`、`venue`，并写入 `audit_logs` 与 `event_store`。
- 可恢复：投影表允许丢失，但必须能从事实链（`event_store`、`ledger_entries`、`orders`、`trades`）重建；恢复流程必须文档化且可演练。
- 可观测：日志字段必须统一，禁止只有纯字符串日志；至少输出 `trace_id、request_id、client_order_id、external_order_id、account_id、symbol、venue`。
- 交易所差异隔离：交易所方言只允许出现在 `nq-adapter-*`；`nq-core / nq-ledger / nq-risk / nq-scheduler` 禁止出现 `if (venue == ...)` 分支。
- 禁止盲重试：外部请求超时、断链、未知状态时，必须先 `query-confirm`，不得直接重复下单。
- 资金与精度：价格、数量、金额必须用 `BigDecimal`（或统一 long-scale 方案，但不得混用）；禁止 float / double 参与业务计算。

---

## 2. 文档即事实（Source of Truth）

实现必须对齐以下文档，优先级从高到低：

### 2.1 当前阶段入口（必读，唯一事实来源）
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`

### 2.2 当前 Gate 权威文档（必须能追溯）
- `docs/gates/gate-d/README.md`
- `docs/gates/gate-d/ARCHITECTURE.md`
- `docs/gates/gate-d/CONTRACTS.md`
- `docs/gates/gate-d/MODULES.md`
- `docs/gates/gate-d/DB_SCHEMA.md`
- `docs/gates/gate-d/STATE_MACHINE.md`
- `docs/gates/gate-d/RISK_RULES.md`
- `docs/gates/gate-d/COMPENSATION_SYNC.md`
- `docs/gates/gate-d/TEST_CASES.md`
- `docs/gates/gate-d/SOURCES.md`
- `docs/gates/gate-d/WORK.md`

### 2.3 历史 Gate 冻结快照（只读参考）
- `docs/gates/gate-a/**`
- `docs/gates/gate-b/**`
- `docs/gates/gate-c/**`

> 规则：当 `docs/current/*` 与 `docs/gates/*` 不一致时，**以 `docs/current/*` 为准**。

---

## 3. GateD 的工作边界

GateD 只做“执行闭环”与“执行域硬化”，包括：

- 统一执行入口（place / cancel / query / reconcile / recovery）
- pre-trade 风控硬规则
- Paper / OKX / Binance 的统一执行抽象
- 订单状态机收敛
- 成交、账本、持仓、账户投影联动
- WS 加速 + REST 兜底
- recovery / reconcile / degrade / query-confirm
- 审计、事件链、指标与日志闭环

GateD 明确**不做**：

- 回测平台
- 因子研究
- 组合优化
- Alpha 研究系统
- 前端控制台扩建
- Kafka / Debezium / K8s 等生产化大基建
- 合约 / 杠杆 / 期货 / 期权

---

## 4. 模块实现顺序（GateD 推荐）

1. `nq-core`
2. `nq-risk`
3. `nq-adapter-api`
4. `nq-adapter-okx`
5. `nq-scheduler`
6. `nq-ledger`
7. `nq-app`
8. `nq-infra`
9. `nq-observability`
10. `nq-adapter-binance`
11. `nq-api`

> 解释：GateD 先收敛执行中心，再补风控，再统一执行端口，再收敛补偿链路。先把骨头长对，再给它穿衣服。

---

## 5. GateD 代码约束（强制）

### 5.1 nq-core
- `OrderCommandService` 可以保留，但职责必须收敛为执行域应用服务，不允许继续无边界膨胀。
- 必须形成统一入口：place / cancel / acknowledge / reject / trade-report / query-confirm。
- 禁止在 controller / scheduler 中重复实现订单状态推进逻辑。

### 5.2 nq-risk
- `NoopRiskGate` 只能保留给测试桩或显式 local profile，不能作为默认实装。
- GateD 必须实现规则链：交易开关、精度、最小名义金额、最大下单额、重复请求、限频。
- 风控返回必须包含 `ruleCode / ruleName / rejectReason / hardReject`。

### 5.3 nq-scheduler
- 只承载任务触发、窗口扫描、恢复编排。
- 不允许演变为新的业务核心。
- 不允许绕过 `nq-core` 或 `nq-ledger` 直接推进状态或写投影。

### 5.4 nq-adapter-api / nq-adapter-*
- 统一 port 只负责外部交互与映射。
- adapter 不允许直接写 ledger、positions、account_snapshots。
- adapter 返回必须归一到统一模型，不允许把交易所私货扩散到 core。

### 5.5 nq-ledger
- 只负责成交、账本、持仓、账户投影及其幂等。
- 账本失败必须可见、可追踪、可补偿。

### 5.6 nq-app / nq-api
- `nq-app` 只做 wiring、阶段验收入口、运行 profile 约束。
- `nq-api` 负责正式查询视图，不承担底层恢复或补偿逻辑。

---

## 6. PR 要求（强制）

- PR 必须对应 `docs/current/GATE_CHECKLIST.md` 的条目，并在 PR 描述中写明勾选项。
- 若修改以下任一内容，必须同步更新当前文档：
  - 契约
  - 状态机
  - Flyway
  - 风控规则
  - recovery / reconcile
  - 幂等键
  - 日志字段
- 若改动会影响当前阶段边界，必须同步更新：
  - `docs/current/README.md`
  - `docs/gates/gate-d/README.md`
  - `docs/gates/gate-d/MODULES.md`
  - `docs/gates/gate-d/WORK.md`

---

## 7. 快速验证（GateD 通用）

### 7.1 最小命令
```powershell
mvn -q -f backend/pom.xml test
docker compose up -d postgres
mvn -q -f backend/pom.xml -pl nq-app spring-boot:run
```

### 7.2 GateD 最小验收顺序
1. 启动应用并确认 health `UP`
2. 执行一个 paper LIMIT -> cancel
3. 执行一个 paper MARKET -> fill
4. 核查 `orders / trades / ledger_entries / positions / event_store / audit_logs`
5. 执行一次 `reconcileOnce`
6. 执行一次 `recoveryOnce`
7. 核对未出现重复成交、重复记账、状态回退

---

## 8. 常见禁止项（强制）

- 禁止在 `nq-core / nq-ledger / nq-risk / nq-scheduler` 出现交易所方言分支。
- 禁止绕过统一执行入口直接改 `orders` 状态。
- 禁止 adapter 直接写 ledger / position / account projection。
- 禁止为“先跑通”删掉审计、幂等、状态机、事实链。
- 禁止在 recovery / reconcile 中直接重复下单。
- 禁止把 GateD 需求偷渡成回测 / 研究平台任务。

---

## 9. Codex 执行工作流（必须照做）

### 第一步：读文档
按顺序读取：
1. `AGENTS.md`
2. `README.md`
3. `docs/current/README.md`
4. `docs/current/GATE_CHECKLIST.md`
5. 目标改动相关的 GateD 文档
6. 再读目标代码文件

### 第二步：确认边界
输出本次任务属于：
- 文档修订
- 契约改动
- 执行域改动
- 风控改动
- 补偿改动
- 仅测试/验证

### 第三步：先文档，后代码
- 先补 `docs/current/*` 或 `docs/gates/gate-d/*`
- 再改代码
- 最后回填 `docs/gates/gate-d/WORK.md`

### 第四步：最小修改集
- 只改与当前 Gate 条目直接相关的文件
- 禁止顺手大重构
- 禁止顺手改无关模块

### 第五步：验证
至少给出：
- 修改文件清单
- 对应 GateD checklist 条目
- 验证方式
- 未完成项 / 风险项

---

## 10.  MCP / Skills 使用规范（项目级强制）

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

