# 文档目录说明

本仓库文档按“当前入口 + 历史 Gate 冻结快照”分层管理：

- `docs/current/`：当前阶段活文档入口，当前以 GateD 为准
- `docs/gates/gate-a/`：GateA 冻结快照
- `docs/gates/gate-b/`：GateB 冻结快照
- `docs/gates/gate-c/`：GateC 冻结快照

根级 `docs/*.md` 中多数旧文档主要用于 archive 参考；其中 `docs/ARCHITECTURE.md` 与 `docs/MODULES.md` 保留为顶层导航摘要，但同样不是当前事实来源。若与 `docs/current/*` 或 `docs/gates/*` 冲突，以后者为准。
- `docs/gates/gate-d/`：GateD 当前权威卷宗与冻结预备目录

## 使用约定

1. 开发前先读：
   - `AGENTS.md`
   - `README.md`
   - `docs/current/README.md`
   - `docs/current/GATE_CHECKLIST.md`

2. 当前 Gate 施工时：
   - 优先维护 `docs/current/*`
   - 同步维护 `docs/gates/gate-d/*`

3. GateD 当前除主文档外，还必须维护：
   - `DECISIONS.md`
   - `EVOLUTION_RULES.md`
   - `NUMERIC_POLICY.md`
   - `PR_SPLIT_PLAN.md`
   - `RECOVERY_RUNBOOK.md`

4. Gate 完成后：
   - `docs/current/*` 切换到下一 Gate
   - 当前 Gate 文档在 `docs/gates/gate-<x>/` 冻结保留
