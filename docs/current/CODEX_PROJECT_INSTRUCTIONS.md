# Codex Project Instructions for NQ / DH

以下内容可复制到 Codex Project Instructions，用于 NQ / DH 项目的默认执行规则。

## 项目背景

- NQ = NexusQuant，量化交易平台。
- DH = Decision Hub，多 Agent 决策平台。
- 当前 NQ：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ。
- 当前 DH：只允许只读边界和契约冻结；不允许真实接入 NQ，不允许真实 provider，不允许 LIVE trading。
- NQ 技术栈：Java 21、Spring Boot 3.5.x、Maven 多模块、React、Vite、Ant Design、TanStack Query、Playwright、PostgreSQL、Flyway、pytest、mypy、ruff。

## 插件路由表

| 任务类型 | 插件 |
| --- | --- |
| `CODE_ANALYSIS` | GitHub |
| `CODE_CHANGE` | GitHub；CodeRabbit when PR-style review is needed |
| `FRONTEND_UI` | GitHub；Figma；Product Design；Build Web Apps；Browser；Chrome |
| `DATA_VISUALIZATION` | GitHub；Build Web Data Visualization；Browser；Chrome；Spreadsheets |
| `SECURITY_AUDIT` | GitHub；Codex Security；CodeRabbit |
| `EXCHANGE_INTEGRATION` | Binance；GitHub；Documents；Spreadsheets |
| `DOCUMENTATION` | GitHub；Documents；Notion |
| `SPREADSHEET_MATRIX` | GitHub；Spreadsheets；Documents |
| `DEPLOYMENT` | GitHub；Vercel or Netlify for frontend preview only；Browser；Chrome；Codex Security when secrets, CI/CD, Docker, or server deployment are involved |
| `CI_CD` | GitHub；CircleCI only when explicitly requested；Codex Security |
| `PRODUCT_DESIGN` | Figma；Product Design；Canva only for visual materials |
| `INVESTMENT_RESEARCH` | Public Equity Investing；Documents；Spreadsheets |
| `PRESENTATION` | Presentations；Documents；Canva |
| `DOMAIN_WEBSITE` | Network Solutions；Vercel；Netlify；Documents |

## Active Skill 前置规则

每次 NQ / DH 任务必须先使用或遵守：

- `AGENTS.md`
- `nq-dh-workflow-router`
- `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md`
- `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md`
- `docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md`

固定前置流程：

1. 先判断任务是否属于 NQ / DH。
2. 如果属于 NQ / DH，先按 `nq-dh-workflow-router` 分类任务。
3. 按任务类型选择插件组合。
4. 先限定 repository、module、target files、excluded files、expected output。
5. 不默认调用所有插件。
6. 不扫描全仓库。
7. 不修改无关模块。
8. 不触碰 LIVE trading、credentials、production env、真实交易路径。
9. 输出必须包含：
   - Task classification
   - Plugins selected
   - Scope
   - Files inspected
   - Files changed
   - Findings
   - Validation
   - Risks
   - Next concrete action

## 文档预算规则

- Code-first default：普通任务必须优先产出代码、测试或可验证行为；docs 不得成为默认产物。
- Review-only no-diff：review-only / audit-only 默认不修改文件，只输出结论；只有阶段 freeze、合同冻结、高风险计划或用户明确要求时才允许写 docs。
- Docs budget：
  - 普通代码任务默认不改 docs；确需记录时最多追加 `docs/current/WORKLOG.md` 一行。
  - 测试基线任务可改 `docs/current/TESTING.md` + `docs/current/WORKLOG.md`。
  - 阶段完成或 Gate freeze 才可同步 `docs/current/STATUS.md`、`docs/current/ROADMAP.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`。
  - `README.md` 只在入口、架构、启动方式或阶段总状态变化时修改。
  - 专项 PLAN 只用于 CI、migration、security、LIVE、credential、API contract 等高风险 epic。
- Freeze compression：plan / review / first-run / rerun / freeze 不得每一步都同步 5-6 份 docs；中间步骤只记录在专项 plan 或 `WORKLOG.md`，最终 freeze 再同步 `STATUS.md` / `TESTING.md`。
- No document inertia：不得为了“保持文档一致”而新增 docs-only 任务；必须由代码、测试、CI、migration、安全边界或用户明确要求触发。
- Prompt rule：后续给 Codex/Claude 的任务提示词必须明确 docs 预算，例如“docs 默认不改；如需记录，只允许 WORKLOG 一行”。

## 禁止事项

- 不默认调用所有插件。
- 不扫描 `node_modules`、`target`、`build`、`dist`、`.git`、`test-results`、`logs`、`secrets`、`credentials`。
- 不做大而全重构。
- 不同时修改前端、后端、Python、部署、文档，除非任务明确要求。
- 不提交密钥、token、cookie、exchange secret、tenant data、生产 `.env`。
- 未执行验证不能写成通过。
- 不把 GateK-PLAN 写成 GateK implementation started。

## NQ 边界

- 不允许开启 LIVE trading。
- 不允许新增真实下单、撤单路径，除非任务明确要求且当前阶段允许。
- 不允许泄露 credentials、API key、exchange secret、tenant data。
- PAPER 和 LIVE 必须隔离。
- DH 不允许修改 NQ 交易状态。
- 涉及交易、风控、权限、部署、安全的修改必须输出风险说明。
- AI、AI 信号、AI 自动交易、AI Paper Trading、DH integration、多交易所扩展均未开始，除非新的事实源明确更新。

## DH 边界

- DH 不允许真实连接 NQ。
- DH 不允许下单、撤单、启动 Paper Run、访问凭证、修改 NQ 交易状态。
- Integration-0 只能准备只读边界和契约冻结。
- 不允许新增 real provider、RealClient、第三方 relay、生产交易路径。
- 重点检查 HMAC、timestamp、nonce、source allowlist、payload size、tenant binding、replay protection、provider trust policy、audit trail。

## 默认执行流程

1. 判断任务是否属于 NQ / DH。
2. 如果属于 NQ / DH，先按 `nq-dh-workflow-router` 分类任务。
3. 按路由表选择必要插件。
4. 明确 repository、module、target files、excluded files、expected output。
5. 读取 `AGENTS.md`、`README.md`、`docs/current/README.md` 和目标文件。
6. 执行最小范围变更或审查。
7. 按任务类型验证。
8. 输出范围、证据、变更、验证、风险、下一步。

## 验证要求

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

Docs：

```powershell
git status --short
```

并检查链接、路径、阶段状态、禁止边界、重复入口和未执行验证表述。是否写入 `WORKLOG.md` / `TESTING.md` 必须遵守文档预算规则，轻量流程修正和 review-only 默认不新增记录。

Deployment：

- 检查 Docker、env example、health check、migration、rollback。
- 涉及 secrets、CI/CD、Docker、server deployment 时，必须加入 Codex Security 审查。
- 禁止提交真实密钥或生产环境配置。

## 默认输出格式

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
