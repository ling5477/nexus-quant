# NQ-DH Workflow Router Skill

本文是 NQ-DH Workflow Router Skill 的源规格与维护规范。`nq-dh-workflow-router` 已作为当前项目 active skill 使用，用于让 NQ / DH 任务自动分类、自动选择相关插件、限定范围、约束验证和固定输出格式，并避免默认调用所有插件。

后续如需重建、迁移或复制该 Skill，应以本文档为准；外部 Codex App 如需手动创建 Skill，也应从本文档复制规格并同步 `AGENTS.md` 中的 active skills 规则。

## Skill 名称

`NQ-DH Workflow Router`

项目内 active skill 名称为：

`nq-dh-workflow-router`

## 触发条件

当任务涉及 NexusQuant、NQ、Decision Hub、DH、量化交易平台、前端优化、架构评估、部署、安全审计、交易所接入、Gate/FREEZE 规划时，自动使用该 Skill。

## 文档语言规则

当任务类型为 `DOCUMENTATION`、`DOCS_GOVERNANCE`、`PLAN`、`REVIEW`、`FREEZE`、`STATUS_SYNC`、`TESTING_SYNC`、`WORKLOG_SYNC` 或需要更新 `README`、`STATUS`、`ROADMAP`、`TESTING`、`WORKLOG`、`docs/current` 说明文档、skill、模板、报告时，必须执行以下规则：

- 文档正文必须中文为主，不得把 current docs、README、STATUS、ROADMAP、TESTING、WORKLOG 整篇写成英文模板。
- 英文任务名、状态枚举、类名、接口名、字段名、文件名、路径、命令、配置键、commit message 和协议原文可以保留英文。
- 英文状态值首次出现时必须附中文解释；例如 `PASS`（通过）、`FROZEN`（已冻结）、`ACCEPTED`（已接受）。
- 代码注释中的业务规则说明优先中文；协议字段、API contract、enum 可以保留英文或中英双语。
- DB comment 使用中文业务语义；表名、字段名、索引名和约束名保持英文。
- 不翻译 `docs/archive/**` 或 `docs/gates/**` 历史文档；旧文档只在后续任务自然触碰时顺手修正。
- Agent 输出报告的栏目名可以保留英文，但栏目内容必须中文为主。

## Skill 执行步骤

### Step 1：任务分类

从以下类型中选择一个主类型。复合任务允许记录辅助类型，但主类型只能有一个：

- `CODE_ANALYSIS`
- `CODE_CHANGE`
- `FRONTEND_UI`
- `DATA_VISUALIZATION`
- `SECURITY_AUDIT`
- `EXCHANGE_INTEGRATION`
- `DOCUMENTATION`
- `SPREADSHEET_MATRIX`
- `DEPLOYMENT`
- `CI_CD`
- `PRODUCT_DESIGN`
- `INVESTMENT_RESEARCH`
- `PRESENTATION`
- `DOMAIN_WEBSITE`

### Step 2：选择插件

严格按照 `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md` 中的插件路由表选择插件。禁止调用无关插件，禁止因为插件可用就默认启用。

### Step 3：限定范围

每次执行前必须明确：

- repository
- module
- target files
- excluded files
- expected output

默认排除：

- `node_modules`
- `target`
- `build`
- `dist`
- `.git`
- `test-results`
- `logs`
- `secrets`
- `credentials`

### Step 4：执行

- 只使用当前任务相关插件。
- 不扫描全仓库，除非任务明确要求全仓库审查。
- 不修改无关模块。
- 不修改 LIVE trading、credentials、production env、真实交易路径。
- NQ 中不得把 GateK-PLAN 写成 GateK implementation started。
- DH 中不得真实接入 NQ、真实 provider、RealClient 或第三方 relay。

### Step 5：验证

根据任务类型运行对应验证：

- Backend：`mvn -f backend/pom.xml test` 或指定模块测试。
- Frontend：`Set-Location frontend; npm run build; npm run test:e2e`，页面任务加 Browser/Chrome 验证。
- Python：`Set-Location research/py; python -m pytest -q; python -m mypy src; python -m ruff check .`。
- Docs：检查链接、路径、阶段状态是否一致，确认未执行验证没有写成通过。
- Deployment：检查 docker、env example、health check、migration、rollback。

### Step 6：输出

必须固定输出：

1. Task classification
2. Plugins selected
3. Scope
4. Files inspected
5. Files changed
6. Findings
7. Validation
8. Risks
9. Next concrete action

## NQ 特殊约束

- 不允许开启 LIVE trading。
- 不允许新增真实下单、撤单路径，除非任务明确要求且当前阶段允许。
- 不允许泄露 credentials、API key、exchange secret、tenant data。
- PAPER 和 LIVE 必须隔离。
- DH 不允许修改 NQ 交易状态。
- 涉及交易、风控、权限、部署、安全的修改必须输出风险说明。

## DH 特殊约束

- DH 不允许真实连接 NQ。
- DH 不允许下单、撤单、启动 Paper Run、访问凭证、修改 NQ 交易状态。
- Integration-0 只能准备只读边界和契约冻结。
- 不允许新增 real provider、RealClient、第三方 relay、生产交易路径。
- 安全审查必须覆盖 HMAC、timestamp、nonce、source allowlist、payload size、tenant binding、replay protection、provider trust policy、audit trail。
