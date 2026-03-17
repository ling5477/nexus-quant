# 文档目录说明

本仓库文档按“当前入口 + 历史 Gate 冻结快照”分层管理：

- `docs/current/`：当前阶段活文档入口，当前以 **GateE** 为准
- `docs/gates/gate-a/`：GateA 冻结快照
- `docs/gates/gate-b/`：GateB 冻结快照
- `docs/gates/gate-c/`：GateC 冻结快照
- `docs/gates/gate-d/`：GateD 冻结卷宗
- `docs/gates/gate-e/`：GateE 当前权威卷宗

根级 `docs/*.md` 只保留导航摘要或历史留档角色；若与 `docs/current/*` 或 `docs/gates/gate-e/*` 冲突，以后者为准。

## 使用约定

1. 开发前先读：
   - `AGENTS.md`
   - `README.md`
   - `docs/current/README.md`
   - `docs/current/GATE_CHECKLIST.md`

2. 当前 Gate 施工时：
   - 优先维护 `docs/current/*`
   - 同步维护 `docs/gates/gate-e/*`

3. GateE 当前必须维护的核心卷宗：
   - `README.md`
   - `GATE_E_CHECKLIST.md`
   - `PR_SPLIT_PLAN.md`
   - `WORK.md`
   - `DECISIONS.md`
   - `ARCHITECTURE.md`
   - `MODULES.md`
   - `CONTRACTS.md`
   - `DB_SCHEMA.md`
   - `STATE_MACHINE.md`
   - `TEST_CASES.md`
   - `SOURCES.md`
   - `EVOLUTION_RULES.md`
   - `GATE_E_CANDIDATES.md`

4. Gate 完成后：
   - `docs/current/*` 切换到下一 Gate
   - 当前 Gate 文档在 `docs/gates/gate-<x>/` 冻结保留
