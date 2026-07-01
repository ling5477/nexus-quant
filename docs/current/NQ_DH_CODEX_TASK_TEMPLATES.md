# NQ / DH Codex Task Templates

本文提供常用任务模板。执行时必须先按 `NQ-DH Workflow Router` 分类，再按 `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md` 选择插件，不默认调用所有插件。

## 0. 文档语言规则

使用本模板生成或更新 NQ/DH 文档时，必须遵守以下语言规则：

- 文档正文必须中文为主；`README`、`STATUS`、`ROADMAP`、`TESTING`、`WORKLOG` 和 `docs/current` 说明文档不得整篇英文化。
- 允许保留英文任务名、状态枚举、类名、接口名、字段名、文件名、路径、命令、配置键、commit message 和协议原文。
- 英文状态值首次出现时必须附中文解释，例如 `PASS`（通过）、`PLAN ONLY`（仅规划）、`READY TO COMMIT`（可进入提交前复核）。
- 代码注释中的业务规则说明优先中文；协议字段、API contract、enum 可保留英文或中英双语。
- DB comment 使用中文业务语义；表名、字段名、索引名和约束名保持英文。
- 不翻译 `docs/archive/**` 与 `docs/gates/**` 历史文档；旧文档只在后续任务自然触碰时顺手修正。
- Agent 输出报告的栏目名可以保留英文，但每个栏目内容必须中文为主。
- 不得为了中文化改写英文枚举、API 字段、类名、接口名、文件名、release tag 或历史事实。

## 1. 代码审查模板

按 NQ-DH Workflow Router 执行。

```text
任务类型：CODE_ANALYSIS + SECURITY_AUDIT
项目：NQ
范围：backend/nq-app、backend/nq-trading、backend/nq-risk
目标：审查交易下单边界、PAPER/LIVE 隔离、接口越权风险。
要求：使用 GitHub + Codex Security + CodeRabbit。
禁止：不要修改代码，不要扫描 frontend、docs、node_modules。
输出：P0/P1/P2/P3 风险清单、证据文件、修复建议。
```

## 2. 前端页面优化模板

按 NQ-DH Workflow Router 执行。

```text
任务类型：FRONTEND_UI
项目：NQ
范围：frontend/src/pages/trading、frontend/src/components/trading
目标：优化 TradingWorkbenchPage，让它更像专业量化交易终端。
要求：使用 GitHub + Figma + Product Design + Build Web Apps + Browser + Chrome。
禁止：不要修改后端，不要改接口路径。
输出：页面结构、组件层级、交互说明、代码改动、浏览器验证结果。
```

## 3. 回测图表模板

按 NQ-DH Workflow Router 执行。

```text
任务类型：DATA_VISUALIZATION
项目：NQ
范围：frontend/src/pages/backtests、frontend/src/components/charts
目标：完善回测详情页，增加权益曲线、回撤曲线、收益分布、交易列表。
要求：使用 GitHub + Build Web Data Visualization + Browser + Chrome。
输出：指标定义、图表设计、代码改动、验证结果。
```

## 4. 交易所字段对比模板

按 NQ-DH Workflow Router 执行。

```text
任务类型：EXCHANGE_INTEGRATION + SPREADSHEET_MATRIX
项目：NQ
目标：整理 Binance、OKX、Bybit 的 spot kline、order、account、symbol 字段差异。
要求：使用 Binance + Spreadsheets + Documents。
禁止：不要下单，不要调用真实交易。
输出：字段矩阵、差异说明、NQ 统一模型建议。
```

## 5. Gate 冻结报告模板

按 NQ-DH Workflow Router 执行。

```text
任务类型：DOCUMENTATION
项目：NQ
范围：docs/current、backend、frontend、python
目标：生成 GateJ-FREEZE 后的整体架构总结和 GateK 开发计划。
要求：使用 GitHub + Documents + Notion。
输出：架构现状、模块边界、已完成能力、缺口、下一阶段任务。
```

## 6. 一键部署审查模板

按 NQ-DH Workflow Router 执行。

```text
任务类型：DEPLOYMENT + SECURITY_AUDIT
项目：NQ
范围：docker、scripts、.github、backend、frontend
目标：审查当前一键部署能力，判断是否满足单机部署、前后端部署、数据库迁移、健康检查、回滚。
要求：使用 GitHub + Codex Security。
禁止：不要改生产配置，不要提交真实密钥。
输出：部署链路图、缺口清单、修复顺序、验证命令。
```

## 7. DH Integration-0 模板

按 NQ-DH Workflow Router 执行。

```text
任务类型：CODE_ANALYSIS + SECURITY_AUDIT + DOCUMENTATION
项目：DH
目标：检查 DH 是否满足 Integration-0，只允许只读边界和契约冻结。
要求：使用 GitHub + Codex Security + Documents。
禁止：不要真实接入 NQ，不要新增 RealClient，不要新增真实 provider，不要触碰 LIVE trading。
输出：边界检查、违规风险、契约建议、下一步任务。
```
