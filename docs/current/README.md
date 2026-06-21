# Current Stage

`docs/current/` 是 NexusQuant 的**当前事实入口**：只保留当前控制文档、当前权威基线和必要运行手册。
历史过程证据、治理 review/freeze、旧路径 compatibility stub 已移出 current，归档到 `docs/evidence/`（见下方“历史证据位置”）。

当前阶段：**GateJ completed；Next: GateK-PLAN；NQ GateK CI mainline COMPLETED / ACCEPTED**。
AI not started；DH not integrated；LIVE disabled；real provider / RealClient / real exchange adapter 未实现。

## 当前控制入口

- 当前状态：[STATUS.md](STATUS.md)
- 路线图：[ROADMAP.md](ROADMAP.md)
- 测试：[TESTING.md](TESTING.md)
- 工作日志：[WORKLOG.md](WORKLOG.md)
- 架构：[ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md)
- API / DB：[API.md](API.md) / [DB_SCHEMA.md](DB_SCHEMA.md)
- 运行手册：[RUNBOOK.md](RUNBOOK.md)

## 文档治理权威基线

- [NQ_DOCS_GOVERNANCE_PLAN.md](NQ_DOCS_GOVERNANCE_PLAN.md)
- [NQ_DOCS_AUTHORITY_INDEX.md](NQ_DOCS_AUTHORITY_INDEX.md)
- [NQ_DOCS_EVIDENCE_INDEX.md](NQ_DOCS_EVIDENCE_INDEX.md)
- [NQ_DOCS_MIGRATION_MAP.md](NQ_DOCS_MIGRATION_MAP.md)
- [NQ_DOCS_G1_IMPLEMENTATION.md](NQ_DOCS_G1_IMPLEMENTATION.md)

## CI current authority

- [NQ_CI_BASELINE_PLAN.md](NQ_CI_BASELINE_PLAN.md)
- [NQ_CI_SECURITY_GUARD_PLAN.md](NQ_CI_SECURITY_GUARD_PLAN.md)
- [NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md](NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md)：Batch 5B-ENV env/secret/no-outbound guard（plan ACCEPTED；implementation **FROZEN / ACCEPTED**，freeze evidence run `27876451289`；current Batch 5B-SMOKE **FROZEN / ACCEPTED**，见 `NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md`）。
- [NQ_CI_SECURITY_BATCH_5B_ENV_PLAN_REVIEW.md](NQ_CI_SECURITY_BATCH_5B_ENV_PLAN_REVIEW.md)：Batch 5B-ENV plan review（PASS / ACCEPTED）。
- [NQ_CI_SECURITY_BATCH_5B_ENV_FIRST_RUN_REVIEW.md](NQ_CI_SECURITY_BATCH_5B_ENV_FIRST_RUN_REVIEW.md)：Batch 5B-ENV first-run review（first run RED → fix-forward → fix rerun GREEN → FROZEN，见 §12）。
- [NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md](NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md)：Batch 5B-ENV freeze 卷宗（**FROZEN / ACCEPTED**，evidence run `27876451289` / headSha `8ba140d9`；freeze-time smoke remained blocked；current Batch 5B-SMOKE **FROZEN / ACCEPTED**，见 `NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md`）。
- [NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md](NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md)：Batch 5B-SMOKE preflight plan（**REVIEWED / ACCEPTED**；该 plan 编写时 smoke 为 PLANNED / NOT STARTED，current Batch 5B-SMOKE **FROZEN / ACCEPTED**；no-real / no-outbound / no-secret-read）。
- [NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN.md](NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN.md)：Batch 5B-SMOKE implementation plan（**REVIEWED / ACCEPTED**）；Batch 5B-SMOKE = **FROZEN / ACCEPTED**（ci.yml `ci-security-smoke` job，复用 EnvSafety / no-outbound / NoReal 最小 smoke；first run evidence PASS（run `27903497008`，9 jobs success）；freeze **FROZEN / ACCEPTED**，卷宗 `NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md`）；Batch 5B = **CLOSED / ACCEPTED**。
- [NQ_CI_SECURITY_FINAL_BASELINE_REVIEW.md](NQ_CI_SECURITY_FINAL_BASELINE_REVIEW.md)：GateK CI/security final baseline review（**PASS / READY FOR FINAL FREEZE**；Batch 5B = CLOSED / ACCEPTED；final freeze 见 `NQ_CI_SECURITY_FINAL_FREEZE.md`）。
- [NQ_CI_SECURITY_FINAL_FREEZE.md](NQ_CI_SECURITY_FINAL_FREEZE.md)：GateK CI/security final freeze 卷宗（**GateK CI/security = FROZEN / ACCEPTED**；Batch 1–5 全部 FROZEN/ACCEPTED 或 CLOSED；evidence run 27903497008 / 27876451289 / 27904207910 success）。
- CI baseline 导航索引：`docs/baselines/CI_BASELINE_INDEX.md`

## GateK / 规划与其他当前控制文档

- [GATEK_PLAN.md](GATEK_PLAN.md)：GateK planning-only；不代表 GateK implementation started。
- [GATEK_ARCHITECTURE_BASELINE_REVIEW.md](GATEK_ARCHITECTURE_BASELINE_REVIEW.md)：GateK architecture baseline review（P0/P1=0，ACCEPTED WITH P2 FOLLOW-UP）。
- [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md)：前端设计系统当前事实。
- [BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md](BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md)：回测权益/回撤曲线后端契约 planning（planning only，未实现）。
- DB / Credential 治理：`DB_SCHEMA_GOVERNANCE_PLAN.md`、`DB_SCHEMA_GOVERNANCE_REVIEW.md`、`CREDENTIAL_*`。
- NQ-DH Integration-0（contract / mock / docs，未实现 runtime 集成）：`NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`、`NQ_DH_INTEGRATION0_SECURITY_POLICY.md`、`NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`、`NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`。

## Codex Workflow 入口

- `../../AGENTS.md`：仓库级 Codex 开发指引、NQ/DH 边界与执行纪律。
- `NQ_DH_CODEX_PLUGIN_WORKFLOW.md`、`NQ_DH_WORKFLOW_ROUTER_SKILL.md`、`NQ_DH_CODEX_TASK_TEMPLATES.md`、`CODEX_PROJECT_INSTRUCTIONS.md`。

## 历史证据位置

当前 current 不再堆放历史过程证据；按主题分别归档：

- GateJ canonical records：`docs/gates/gate-j/`
- 早期 Gate 历史卷宗：`docs/gates/gate-h/`、`docs/gates/gate-i/`、`docs/gates/gate-a..g/`
- CI historical evidence：`docs/evidence/ci/`（导航 `docs/evidence/ci/README.md`）
- Documentation governance evidence（G1～G6 plan/review/freeze/implementation/final freeze）：`docs/evidence/governance/`（导航 `docs/evidence/governance/README.md`）
- Compatibility stubs（旧 current 路径兼容 stub 归档副本）：`docs/evidence/compatibility/`
  - GateJ：`docs/evidence/compatibility/gatej-current-stubs/`
  - CI：`docs/evidence/compatibility/ci-current-stubs/`

## 当前不是

- 当前不是 AI 自动交易阶段；不允许 AI 直接下单；不允许真实 LIVE 下单。
- AI 接入必须等 Paper Trading 稳定后再进入（最早 GateK 之后单独评审）。
- 当前不是 GateK 产品 / runtime 实现阶段；CI mainline 完成不代表 AI、DH runtime、LIVE 或真实交易所扩展启动。
- 当前不代表 UI/UX 专业化已完成，也不应描述为面向公开用户的生产就绪。

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
GateK：规划 / 架构 / 产品化 / 部署化 / 可观测性 / 安全边界收口（NEXT）
  ↓
GateL：AI Paper Trading → GateM：AI 小资金 LIVE → GateN：美股适配 → GateO：A 股适配
```
