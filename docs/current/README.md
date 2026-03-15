# docs/current/README.md
# Current Gate（当前阶段入口）

当前阶段：**GateD（统一执行闭环与执行域硬化）**。

当前状态：**已冻结，GateE 待启动**。

本目录是当前阶段入口；当下一阶段正式启动时，只更新本目录内容。GateD 的冻结卷宗保存在 `docs/gates/gate-d/`，GateE 的待启动输入保存在 `docs/gates/gate-e/`。

---

## 1. 当前阶段结论

GateD 已完成冻结收尾。

当前入口只保留三类信息：
- GateD 已闭环的主线事实
- GateD 不再阻塞的非阻塞治理项
- GateE 待启动的输入导航

### 1.1 当前阶段状态摘要（截至 2026-03-15）

> 本摘要用于入口级快速判断，细项以 `docs/current/GATE_CHECKLIST.md` 与 `docs/gates/gate-d/FREEZE_SUMMARY.md` 为准。
> 状态约定：`[x] 已完成`、`[~] 部分完成`、`[ ] 未完成`。

- [x] pre-trade 风控规则链已完成
- [x] lifecycle 主通道已收口
- [x] adapter canonical 契约冻结已完成
- [x] `__gated` canonical 入口已完成
- [x] order / trade / position / account 本地最小闭环已完成
- [x] account snapshot 本地产出链已完成
- [x] 请求层 canonical `orderType / quantity` 已完成
- [x] 现行脚本与示例 canonical 化已完成
- [x] current / top-level / archive 文档边界已建立
- [x] 真实 OKX 主验收通道已收口
- [x] `UC-D1 / Paper LIMIT -> cancel` 已收口
- [x] `UC-D10 / Binance LIMIT -> cancel` 已收口
- [x] 全仓 `mvn test / mvn verify`、Flyway init / upgrade 与 freeze docs 已完成
- [x] GateD 已冻结，GateE 待启动
- [~] 深层兼容债务、Binance background reconcile 审计噪音、指标完善顺延到 GateE / 后续治理批

---

## 2. GateD 的边界

### 2.1 GateD 包含
- 统一执行编排
- 统一 adapter 契约收敛
- 风控硬规则
- 订单状态机硬化
- 订单 / 成交 / 账本 / 持仓 / 账户快照联动
- reconcile / recovery / degrade / query-confirm
- Paper 与真实交易所的统一执行接口
- trace / audit / event_store / 基础观测规范

### 2.2 GateD 不包含
- 回测系统
- 因子研究
- Alpha 研究平台
- 前端控制台扩建
- Kafka / Debezium / K8s / Grafana 等生产大基建
- 合约、杠杆、期货、期权执行域

---

## 3. 当前入口文档

- 当前 checklist：`docs/current/GATE_CHECKLIST.md`
- GateD 冻结摘要：`docs/gates/gate-d/FREEZE_SUMMARY.md`
- GateD 卷宗入口：`docs/gates/gate-d/README.md`
- GateE 待启动说明：`docs/gates/gate-e/README.md`
- GateE 候选清单：`docs/gates/gate-e/GATE_E_CANDIDATES.md`

---

## 4. 使用方式

### 第一步：判断当前阶段
- 当前不再新增 GateD 主线实现；任何剩余项先判断是否属于 GateE / 后续治理批。

### 第二步：读入口文档
1. `AGENTS.md`
2. `README.md`
3. `docs/current/README.md`
4. `docs/current/GATE_CHECKLIST.md`
5. `docs/gates/gate-d/FREEZE_SUMMARY.md`
6. 如需启动下一阶段，再读 `docs/gates/gate-e/*`

### 第三步：执行规则
- GateD 只允许冻结收尾、归档与复盘类动作
- 不把非阻塞治理项重新写回 GateD 主阻塞
- GateE 启动前，优先使用 `GATE_E_CANDIDATES.md` 进行最小切片
