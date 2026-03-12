# GateD COMPENSATION_SYNC
# GateD 补偿与同步说明

## 1. 目标

GateD 补偿链路的目标不是“补丁越多越安全”，而是建立**明确、有限、可追踪**的收敛规则。

---

## 2. 核心场景

### 2.1 下单后未知状态
场景：place 请求超时、网络异常、HTTP 5xx、客户端中断。

规则：
1. 禁止立即重下
2. 先根据 `clientOrderId / externalOrderId` 执行 query-confirm
3. 若查到外部事实，则按事实推进状态
4. 若短窗口内查不到，再进入受限重试或人工检查策略

### 2.2 非终态订单周期收敛
场景：订单长期停留在 `SUBMITTING / ACCEPTED / PARTIALLY_FILLED / CANCEL_SUBMITTING`

规则：
1. reconcile job 周期扫描非终态订单
2. 对每笔订单执行 `query order + pull fills`
3. 对新增 fills 执行去重与 posting
4. 推进订单终态或保持原状态并记录审计

### 2.3 启动恢复
场景：应用重启、部署重启、进程崩溃恢复

规则：
1. startup recovery 扫描非终态订单和未完成投影
2. 对订单执行 query-confirm
3. 对 fills 执行补拉与去重
4. 对 projection 执行对齐
5. 整个过程必须写审计与事件

### 2.4 私有 WS 断连降级
场景：ws 连接断开、listen key 失效、订阅失败、鉴权失败

规则：
1. 记录 `WS_DISCONNECTED / WS_DEGRADE_STARTED`
2. 触发一次受限窗口 reconcile
3. 重连后重新订阅
4. 记录 `WS_RECONNECTED / WS_DEGRADE_COMPLETED`

---

## 3. 关键约束

- 补偿链路不允许重复下单
- 补偿链路不允许直接写最终投影而跳过事实处理
- 补偿动作必须带 `traceId`
- 每次 degrade / recovery / reconcile 都必须在 `audit_logs` 与 `event_store` 留痕

---

## 4. 推荐作业

- `ReconcileNonTerminalOrdersJob`
- `StartupRecoveryJob`
- `WsDegradeRecoveryCoordinator`
- `AccountSnapshotSyncJob`
- `PositionSnapshotSyncJob`

---

## 5. 需要收敛的现状问题

- scheduler 中同时堆着 paper、reconcile、recovery、ws 协同，边界偏胖
- 某些恢复逻辑更像业务事实推进，GateD 要把入口收口到 core
- query-confirm 规则需要文档化与统一日志字段

