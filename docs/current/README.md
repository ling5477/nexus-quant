# Current Stage

`docs/current/` 是 NexusQuant 当前事实入口。当前状态是 **GateJ completed；Next: GateK-PLAN；AI not started**。

## 当前状态

- GateH completed。
- GateI completed。
- GateJ-PLAN completed。
- GateJ-1-WO completed。
- GateJ-2-WO completed。
- GateJ-3-WO completed。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- GateJ completed。
- Next: GateK-PLAN。
- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- UI/UX professionalism remains post-freeze remediation。
- NQ / DH 三轮只读审计已完成；DH not integrated；Integration-0 allowed only as contract / mock / documentation work line, not runtime integration（详见 `STATUS.md`）。
- NQ-DH Integration-0 契约冻结已完成（contract / mock / docs，未实现集成）；DH P1-4 残留阻塞 Integration-1，详见 `NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`。
- NQ-DH Integration-0 mock / contract test 详细矩阵（15 项 × 16 字段 + 共享 fixture + forbidden side-effect checklist + 下一步代码任务草案）已设计，只写计划未写测试代码，详见 `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`。
- NQ-DH Integration-0 contract test 代码已实现（仅 `backend/nq-app/src/test/**`，test-only，INT0-T01..T15 共 16 用例，全部 mock/stub/内存校验，无真实集成）；`mvn -f backend/pom.xml test` 通过，nq-app 51 tests。
- NQ-DH Integration-0 safety gate：**CLOSED / ACCEPTED**（详见 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`）。Runtime integration / Integration-1 / AI 仍 NOT STARTED；DH NOT INTEGRATED；LIVE DISABLED；Integration-1 前置为 DH P1-4 residual（rate limit / memory cap / replay nonce persistence）。
- 当前本地 PostgreSQL 默认端口固定为 `5432`。

## 当前不是

- 当前不是 AI 自动交易阶段。
- 当前不允许 AI 直接下单。
- 当前不允许真实 LIVE 下单。
- AI 接入必须等 Paper Trading 稳定后再进入（最早 GateK）。
- GateJ 是 Paper Trading 稳定运行阶段，不是 AI 阶段。
- GateJ-FREEZE 已完成连续运行验收与冻结，不夹带 AI、不夹带新业务功能。
- 当前不是 GateK 实现阶段；Next 只是 GateK-PLAN。
- 当前不代表 UI/UX 专业化已完成。
- 当前不应描述为面向公开用户的生产就绪。

## 项目路线

```text
DOC-CLEAN / BASELINE-FIX completed
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI：虚拟币量化 V1 完整闭环 completed
  ↓
GateJ：Paper Trading 稳定运行 completed
  ↓
GateK：AI 信号接入规划（NEXT）
  ↓
GateL：AI Paper Trading
  ↓
GateM：AI 小资金 LIVE
  ↓
GateN：美股适配
  ↓
GateO：A 股适配
```

## 当前事实文件

- `STATUS.md`：当前项目状态与未完成项。
- `ARCHITECTURE.md`：当前架构事实与依赖边界。
- `MODULES.md`：模块 owner、职责和禁止反向依赖。
- `API.md`：当前 API 分类入口。
- `DB_SCHEMA.md`：数据库事实入口。
- `CREDENTIAL_REVOCATION_GOVERNANCE_REVIEW.md`：Credential revocation Batch 5-A 只读审计报告。
- `CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`：Credential revocation Batch 5-B schema completed、Batch 5-C code/API/test completed、Batch 5-D-B explicit rotate command implemented 与 Batch 5-E-B deterministic active material selection implemented 事实。
- `CREDENTIAL_ROTATE_GOVERNANCE_REVIEW.md`：Credential rotate Batch 5-D-A 只读审计报告；作为 5-D-B 实现前的审计快照保留。
- `CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md`：Credential active material Batch 5-E-A 只读审计报告；记录 active summary / active material 多 credential type 选择风险与 5-E-B 建议。
- `CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md`：Credential active credential Batch 5-E-C 只读审计报告；记录 account 全局 active 唯一约束取舍、5-E-D migration 决策和 Batch 5-F enable 审计前置条件。
- `CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md`：Credential enable Batch 5-F-A 只读审计报告；记录可恢复状态、不可恢复状态和 Batch 5-F-B schema-only `ENABLED` audit event 准备事实；Batch 5-F-C 已实现最小 enable command。
- `CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`：Credential governance Batch 5-G 冻结复核报告；Batch 5-G-A 已完成 P3 文案 cleanup，冻结 Batch 5-A ~ 5-F-C 的 schema、API、Service、Repository、audit log、测试和文档边界。
- `CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`：Credential permission probe 只读设计审计与 schema-only 记录；V31 已完成 schema 准备，但 permission probe 未实现，未调用真实交易所。
- `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md`：OKX adapter bootstrap no-outbound 只读审计报告；记录 local integration test 启动期 OKX public instruments 外联触发路径、根因、Binance 对照和后续 FIX 建议；本轮未修改代码、未调用真实交易所。
- `NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`：NQ-DH Integration-0 契约冻结主文档（禁止能力 / 可开放能力 / header / 数据契约 / 不可信输入 / 验收 / Integration-1 blockers）；contract-only，未实现集成。
- `NQ_DH_INTEGRATION0_SECURITY_POLICY.md`：NQ-DH Integration-0 安全策略（header / 签名 / 防重放 / tenant / payload / 脱敏 / 审计）；契约设计，未实现代码。
- `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`：NQ-DH Integration-0 mock / contract test 设计（15 项）+ 详细矩阵（实现已落地，见提交 `fc922d06`）。
- `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`：NQ-DH Integration-0 safety gate close / acceptance（PASS / CLOSED / ACCEPTED）；Runtime integration / Integration-1 / AI not started，DH not integrated，LIVE disabled。
- `TESTING.md`：统一验证命令与本次验证记录。
- `RUNBOOK.md`：本地启动与常见问题。
- `ROADMAP.md`：总路线。
- `WORKLOG.md`：执行日志。

## Codex Workflow 入口

- `../../AGENTS.md`：仓库级 Codex 开发指引、NQ/DH 边界与执行纪律。
- `NQ_DH_CODEX_PLUGIN_WORKFLOW.md`：NQ/DH 插件路由、任务类型和标准工作流。
- `NQ_DH_WORKFLOW_ROUTER_SKILL.md`：`nq-dh-workflow-router` active skill 的源规格与维护规范。
- `NQ_DH_CODEX_TASK_TEMPLATES.md`：常用代码审查、前端优化、图表、交易所字段、Gate 报告、部署审查和 DH Integration-0 模板。
- `CODEX_PROJECT_INSTRUCTIONS.md`：可复制到 Codex Project Instructions 的完整规则。

## 当前 GateJ 规划文件

- `PLAN_GATEJ.md`：GateJ 阶段规划。
- `GATEJ_API_PLAN.md`：GateJ API 规划。
- `GATEJ_DB_PLAN.md`：GateJ DB 规划。
- `GATEJ_FRONTEND_PLAN.md`：GateJ 前端规划。
- `GATEJ_TEST_PLAN.md`：GateJ 测试规划。
- `GATEJ_WORK_ORDER.md`：GateJ 工作单（含 GateJ-FREEZE 范围）。
- `GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`：GateJ-FREEZE 最终验收报告。
- `DOC_CLEAN_REPORT.md`：最近一次文档清理报告。

## 历史 Gate 冻结卷宗

已完成 Gate 的计划文档不在 `docs/current/` 重复，统一保存在 `docs/gates/`：

- `docs/gates/gate-h/`：GateH 冻结卷宗。
- `docs/gates/gate-i/`：GateI 冻结卷宗。
- `docs/gates/gate-j/`：GateJ 冻结卷宗。
- `docs/gates/gate-a..g/`：早期 Gate 历史卷宗。
