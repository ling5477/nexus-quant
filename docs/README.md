# 文档目录说明

本仓库文档按“当前入口 + 当前 Gate 卷宗 + 历史冻结卷宗”分层管理：

- `docs/current/`：当前阶段入口，当前表示 **GateG-DOC-1 已完成**
- `docs/gates/gate-g/`：GateG 当前权威卷宗
- `docs/gates/gate-f/`：GateF 冻结卷宗
- `docs/gates/gate-e/`：GateE 冻结卷宗
- `docs/gates/gate-d/`：GateD 冻结卷宗
- `docs/gates/gate-a/`：GateA 冻结快照
- `docs/gates/gate-b/`：GateB 冻结快照
- `docs/gates/gate-c/`：GateC 冻结快照

根级 `docs/*.md` 只保留导航摘要；若与 `docs/current/*` 冲突，以 `docs/current/*` 为准。

## 使用约定

1. 开发前先读：
   - `AGENTS.md`
   - `README.md`
   - `docs/current/README.md`
   - `docs/current/GATE_CHECKLIST.md`

2. 当前阶段：
   - `docs/current/*` 表示 GateG 当前入口
   - `docs/current/GATEG_INPUTS.md` 记录 GateG 输入清单
   - `docs/gates/gate-g/*` 表示 GateG 主卷宗

3. 最近已冻结 Gate：
   - `docs/gates/gate-f/*` 为最近完成且冻结的 GateF 卷宗
   - 只读参考，不再继续扩功能

4. Gate 完成后：
   - `docs/current/*` 切换到下一阶段
   - 已完成阶段保留在 `docs/gates/gate-<x>/` 冻结卷宗中
