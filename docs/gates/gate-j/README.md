# GateJ Freeze Snapshot

本目录是 GateJ completed 后的只读历史快照，用于保留 GateJ 完成时的规划、API、DB、前端、测试、work order、运行验收结论、当前事实和工作记录参考。

## 状态

- Current stage: GateJ completed。
- Next: GateK-PLAN。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- UI/UX professionalism remains post-freeze remediation。
- 本目录不代表当前开发入口，当前事实仍以 `docs/current/` 为准。

## GateJ 范围

GateJ 是 Paper Trading 稳定运行阶段，覆盖以下已完成范围：

- GateJ-1：Paper Trading 调度、心跳、日报与基础监控。
- GateJ-2：告警、监控运行记录、调度失败与心跳滞后可见性。
- GateJ-3：异常恢复、失败重试、稳定性验收结构、HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警最小落库。
- GateJ-FREEZE：30m / 1h / 24h / 7d 连续运行验收与冻结。

## GateJ-FREEZE 验收事实

| 项目 | 结果 |
| --- | --- |
| 起点 | 2026-05-29 14:53:20 +08:00 |
| 7d checkpoint | 2026-06-05 14:53:24 +08:00 |
| health-loop 样本数 | 2025 |
| health-loop 最新样本 | 2026-06-05 15:40:58 +08:00 |
| 30m observation | PASS |
| 1h acceptance | PASS |
| 24h acceptance | PASS |
| 7d acceptance | PASS |
| nginx | Up 7 days |
| nq-app | Up 7 days |
| postgres | Up 7 days healthy |
| 18888 health | UP |
| 5179 health | UP |
| after-7d.sql | 已生成，266K |
| 5179 安全组 | 已确认只允许本人 IP 访问 |

## GateJ 不包含

- 不包含 AI。
- 不包含 AI 信号。
- 不包含 AI 自动交易。
- 不包含 AI Paper Trading。
- 不包含 DH integration。
- 不包含 DH connected to NQ。
- 不包含多交易所扩展。
- 不包含真实 LIVE 下单。
- 不包含真实交易所下单接口调用。
- 不包含美股/A 股。
- 不包含合约全量。
- 不包含高频。
- 不包含复杂因子平台。
- 不代表 UI/UX 专业化已完成。
- 不代表公开用户生产就绪。

## 归档文件

本次 GateJ freeze snapshot 复制自 `docs/current/` 中与 GateJ 完成事实相关的 Markdown 文档：

- `PLAN_GATEJ.md`
- `GATEJ_API_PLAN.md`
- `GATEJ_DB_PLAN.md`
- `GATEJ_FRONTEND_PLAN.md`
- `GATEJ_TEST_PLAN.md`
- `GATEJ_WORK_ORDER.md`
- `GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`
- `GATEJ_FREEZE_DEPLOYMENT.md`
- `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`
- `GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`
- `GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md`
- `API.md`
- `DB_SCHEMA.md`
- `STATUS.md`
- `TESTING.md`
- `WORKLOG.md`
- `ROADMAP.md`
- `ARCHITECTURE.md`
- `MODULES.md`
- `RUNBOOK.md`
- `PRE_FREEZE_AUDIT_REPORT.md`
- `PRE_FREEZE_AUDIT_FIX_PLAN.md`
- `AUDIT_FIX_REPORT.md`
- `FULL_SECURITY_AUDIT_REPORT.md`
- `REPO_SIZE_AUDIT_REPORT.md`
- `DOC_CLEAN_REPORT.md`
- `FREEZE_SUMMARY.md`

## 未纳入 Git 快照

以下内容不进入本目录：

- runtime evidence tar.gz。
- 数据库 dump。
- 日志大文件。
- release zip。
- jar。
- dist。
- `.env.freeze`。
- freeze-evidence。

## 使用规则

- 只读参考，不在本目录继续推进 GateK。
- 不从本目录恢复开发任务。
- 当前事实以 `docs/current/` 为唯一入口。
- GateK 必须从 `docs/current/` 新开 `GateK-PLAN`，不能把本目录当作可编辑工作区。
