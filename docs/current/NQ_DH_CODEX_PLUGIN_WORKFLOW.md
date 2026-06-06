# NQ / DH Codex Plugin Workflow

本文固化 NexusQuant（NQ）与 Decision Hub（DH）项目中的 Codex 插件路由、任务分类和标准执行流程。当前 NQ 事实源仍以 `docs/current/` 为准：GateJ completed，Next: GateK-PLAN，AI not started，DH integration not started / not connected to NQ。

## 1. 插件总体原则

- 不默认调用所有插件。
- 每次任务先分类，再选择插件；只选当前任务必要插件。
- 先限定仓库、模块、文件范围，再读取代码或文档。
- 不扫描 `node_modules`、`target`、`build`、`dist`、`.git`、`test-results`、`logs`、`secrets`、`credentials`。
- 不做大而全重构；只做本轮任务的最小可审查变更。
- 不同时修改前端、后端、Python、部署、文档，除非任务明确要求。
- 所有任务必须输出 `scope`、`files inspected`、`files changed`、`validation`、`risks`、`next action`。
- 插件路由不能覆盖 Gate、Freeze、安全、交易、凭证和模块边界。
- NQ 中 PAPER / LIVE 必须隔离；DH 不允许修改 NQ 交易状态。

## 2. 任务类型枚举

- `CODE_ANALYSIS`：代码阅读、架构评估、边界检查，不修改代码。
- `CODE_CHANGE`：代码实现、修复、重构、测试补齐。
- `FRONTEND_UI`：前端页面、交互、Ant Design 组件、浏览器验证。
- `DATA_VISUALIZATION`：图表、指标看板、可视化数据结构。
- `SECURITY_AUDIT`：权限、凭证、交易边界、供应链、CI/CD、部署安全。
- `EXCHANGE_INTEGRATION`：交易所公共市场数据、字段对比、adapter 模型建议。
- `DOCUMENTATION`：文档、报告、规则、计划、复盘。
- `SPREADSHEET_MATRIX`：字段矩阵、对照表、验收矩阵。
- `DEPLOYMENT`：部署、预览、健康检查、回滚、环境文件审查。
- `CI_CD`：持续集成、流水线、检查失败定位。
- `PRODUCT_DESIGN`：产品信息架构、页面目标、设计方向、流程原型。
- `INVESTMENT_RESEARCH`：公开权益研究、投资备忘录、财务或市场分析。
- `PRESENTATION`：PPT、演示稿、汇报材料。
- `DOMAIN_WEBSITE`：域名、网站落地页、前端预览部署。

## 3. 插件路由表

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

## 4. 插件优先级

1. GitHub
2. Browser
3. Chrome
4. Codex Security
5. Figma
6. Product Design
7. Build Web Apps
8. Build Web Data Visualization
9. CodeRabbit
10. OpenAI Developers
11. Binance
12. Documents
13. Spreadsheets
14. Notion
15. Vercel
16. Netlify
17. Canva
18. Presentations
19. CircleCI
20. Public Equity Investing
21. Investment Banking
22. Network Solutions

## 5. 标准执行流程

### 5.1 代码任务

```text
GitHub 读代码
→ 修改代码
→ 跑相关测试
→ CodeRabbit 审查
→ Codex Security 安全检查
→ 输出 diff 和验证结果
```

约束：不修改无关模块；不新增未要求 API、migration、交易路径或 provider；涉及交易、风控、权限、安全时必须输出风险说明。

### 5.2 前端任务

```text
Product Design 定义页面目标
→ Figma 设计页面结构和组件层级
→ Build Web Apps 修改 React 页面
→ Browser/Chrome 验证页面
→ 输出页面变更和验证结果
```

约束：NQ / DH 前端默认是专业金融科技后台；优先 Ant Design 企业后台模式；不得私自切换 UI 框架；不得隐藏失败、风控、LIVE、权限、审计和追踪状态。

### 5.3 数据可视化任务

```text
Spreadsheets 整理指标和字段
→ Build Web Data Visualization 设计图表
→ GitHub 修改前端代码
→ Browser/Chrome 验证
```

约束：先定义指标口径、单位、精度、空值和异常值，再实现图表；不得把未验证数据写成通过。

### 5.4 安全审计任务

```text
GitHub 读取相关代码
→ Codex Security 检查风险
→ CodeRabbit 做 PR-style review
→ 输出 P0/P1/P2/P3 风险清单
```

约束：优先检查凭证泄露、越权、交易隔离、LIVE 触达、HMAC/timestamp/nonce/replay、tenant binding、audit trail、CI/CD secrets、deployment exposure。

### 5.5 交易所集成任务

```text
Binance 查询/对比交易所字段和规则
→ GitHub 对照现有模型
→ Spreadsheets 输出字段矩阵
→ Documents 输出模型建议
```

约束：只允许公共只读市场数据查询；禁止下单、撤单、真实账户读取、密钥处理或 LIVE 交易路径。

### 5.6 文档任务

```text
GitHub 读取代码和 docs
→ Documents 输出报告
→ Notion 沉淀任务和路线图
```

约束：必须回到仓库事实源核对；未执行验证不能写成通过；阶段状态必须与 `docs/current/STATUS.md` 一致。

### 5.7 部署任务

```text
GitHub 读取部署文件
→ Codex Security 检查密钥和权限
→ Vercel/Netlify 做前端预览
→ Browser/Chrome 验证
→ Documents 输出部署说明
```

约束：禁止提交真实密钥或生产 `.env`；涉及 server deployment、Docker、CI/CD、secrets 时必须输出影响面、回滚方式和健康检查命令。

## 6. NQ 边界

- 不允许开启 LIVE trading。
- 不允许新增真实下单、撤单路径，除非任务明确要求且当前阶段允许。
- 不允许泄露 credentials、API key、exchange secret、tenant data。
- PAPER 和 LIVE 必须隔离。
- DH 不允许修改 NQ 交易状态。
- 涉及交易、风控、权限、部署、安全的修改必须输出风险说明。
- 当前 Next 是 GateK-PLAN；AI、AI 信号、AI 自动交易、AI Paper Trading、DH integration、多交易所扩展均未开始。

## 7. DH 边界

- DH 不允许真实连接 NQ。
- DH 不允许下单、撤单、启动 Paper Run、访问凭证、修改 NQ 交易状态。
- Integration-0 只能准备只读边界和契约冻结。
- 不允许新增 real provider、RealClient、第三方 relay、生产交易路径。
- 重点检查 HMAC、timestamp、nonce、source allowlist、payload size、tenant binding、replay protection、provider trust policy、audit trail。

## 8. 默认输出格式

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
