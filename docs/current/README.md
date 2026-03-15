# docs/current/README.md
# Current Gate（当前阶段入口）

当前阶段：**GateE（v1.4：策略接入与调度编排）**。

当前状态：**待启动 / 启动中**。

GateD 已冻结。当前 source of truth 已切换到 GateE，GateD 卷宗只作为冻结证据保留，不再承载后续阶段新增内容。

---

## 1. 当前阶段结论

当前主战场已切到 GateE。

当前入口只保留三类信息：
- GateE 主目标与边界
- GateE-0 当前优先级与阻塞清单
- GateE 卷宗与 GateD 冻结证据的跳转导航

### 1.1 当前阶段摘要（截至 2026-03-15）

- [x] GateD 已冻结
- [x] GateE 主定义已固定为 `v1.4（策略接入与调度编排）`
- [x] GateE-0 已确定为前置治理批，不改写 GateE 主目标
- [~] 当前先执行 GateE-0，为 GateE 主体开路
- [ ] GateE 主体实现尚未开始

---

## 2. 当前阶段目标

### 2.1 GateE 主目标
- 策略接入
- 策略注册与运行状态管理
- 调度编排主链

### 2.2 GateE-0 前置治理
只做：
- Binance background reconcile 噪音治理
- schema / metadata 收口
- 返回模型一致性收尾

### 2.3 明确边界
- GateE-0 不是 GateE 主体
- GateE 主体不是“治理收尾阶段”
- GateD 文档只作冻结证据，不再回写新阶段内容

---

## 3. 当前优先级

### Top 1
- Binance background reconcile 噪音治理

### Top 2
- schema / metadata 收口

### Top 3
- 返回模型一致性收尾

说明：
- 以上三项全部属于 GateE-0 前置治理，只是为 GateE 主体开路，不构成 GateE 主定义本身。

---

## 4. 当前入口跳转

- 当前主入口：`docs/gates/gate-e/README.md`
- 当前阶段摘要：`docs/current/GATE_CHECKLIST.md`
- GateE 候选清单：`docs/gates/gate-e/GATE_E_CANDIDATES.md`
- GateD 冻结证据：`docs/gates/gate-d/FREEZE_SUMMARY.md`

---

## 5. 使用方式

1. 先读 `AGENTS.md`
2. 再读 `README.md`
3. 再读 `docs/current/README.md`
4. 再读 `docs/current/GATE_CHECKLIST.md`
5. 进入 `docs/gates/gate-e/*`

当前不再新增 GateD 主线实现；若发现历史噪音或未完项，先判断它属于 GateE-0、GateE 主体，还是仅为冻结证据。