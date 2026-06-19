# GateJ Current-Path Compatibility Stubs (retained copies)

> 本目录保留的是 **GateJ 旧 `docs/current/` 路径 compatibility stub 的归档副本**。
> 这些 stub 在 G3 redirect-first consolidation 阶段建立，用于旧路径向后兼容导航；
> 现在从 `docs/current/` 物理移出，集中归档于此，避免长期占据 current 根目录。
>
> **真正 canonical 全文永久保留在 `docs/gates/gate-j/`（byte-for-byte）。**
> 本目录每个文件只是历史兼容 stub 的保留副本（非权威），其内部链接已更新为指向 canonical GateJ 路径。
> 本 README 仅做导航，不复制正文。

## Canonical 位置

- GateJ 权威全文：`docs/gates/gate-j/<same filename>`

## 归档的兼容 stub（14）

- [AUDIT_FIX_REPORT.md](AUDIT_FIX_REPORT.md)
- [DOC_CLEAN_REPORT.md](DOC_CLEAN_REPORT.md)
- [FULL_SECURITY_AUDIT_REPORT.md](FULL_SECURITY_AUDIT_REPORT.md)
- [GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md](GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md)
- [GATEJ_FREEZE_DEPLOYMENT.md](GATEJ_FREEZE_DEPLOYMENT.md)
- [GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md](GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md)
- [GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md](GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md)
- [GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md](GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md)
- [GATEJ_FRONTEND_PLAN.md](GATEJ_FRONTEND_PLAN.md)
- [GATEJ_WORK_ORDER.md](GATEJ_WORK_ORDER.md)
- [PLAN_GATEJ.md](PLAN_GATEJ.md)
- [PRE_FREEZE_AUDIT_FIX_PLAN.md](PRE_FREEZE_AUDIT_FIX_PLAN.md)
- [PRE_FREEZE_AUDIT_REPORT.md](PRE_FREEZE_AUDIT_REPORT.md)
- [REPO_SIZE_AUDIT_REPORT.md](REPO_SIZE_AUDIT_REPORT.md)

## 仍保留在 docs/current 的 BLOCKED stub（3）

以下 3 个 GateJ stub 在 R1 暂未移出，原因是它们存在来自受保护 DIVERGED 当前活文档的普通入链（无法在本轮安全改链）：

- `docs/current/GATEJ_API_PLAN.md` — 入链来自 `docs/current/API.md`（DIVERGED，受保护）。
- `docs/current/GATEJ_DB_PLAN.md` — 入链来自 `docs/current/DB_SCHEMA.md`（DIVERGED，受保护）。
- `docs/current/GATEJ_TEST_PLAN.md` — 入链来自 `docs/current/TESTING.md`（DIVERGED，受保护）。

标记：`BLOCKED_PER_FILE / DIVERGED_INBOUND_LINK`。其 canonical 全文同样在 `docs/gates/gate-j/`。R2 review 复核处理方式，R1 不强行改链。
