# Gate B RECOVERY RUNBOOK（恢复与回放手册）

> 目标：应用重启后不丢单、不重复下单、状态机可继续推进，账本可校验可追溯。

---

## 1. 恢复不变量（必须满足）
1) 相同 client_order_id 的下单请求不得产生重复订单
2) 非终态订单可继续推进（至少可恢复到“待撮合/待对账”）
3) 不允许产生重复 trade/ledger_entries（幂等/去重）
4) traceId 必须可定位整条链路（orders/trades/ledger/audit）

---

## 2. 启动恢复流程（建议）

### 2.1 恢复未终态订单
- 查询 orders where status in (NEW, RISK_PASSED, SENT, ACCEPTED, PARTIALLY_FILLED)
- 对每个订单：
    - 若需要撮合：加入撮合队列（或标记待撮合）
    - 若需要对账：加入对账队列

### 2.2 恢复 scheduler 任务
- 若 scheduler 任务是“固定频率”：
    - 重启自动恢复（无需持久化任务）
- 若 scheduler 任务是“按订单动态”：
    - 必须从 DB 重建（推荐 Gate B 用固定频率 + DB 扫描）

### 2.3 Ledger 校验与补偿
- 对最近 N 分钟/小时的 trades 检查是否已 ledger_posted
- 若发现 trade 无 ledger：
    - 触发补记账（幂等：trade_id 唯一约束或 ledger_event 去重）

---

## 3. 常见故障排查

### 3.1 UNIQUE 冲突（client_order_id）
- 现象：下单报 unique violation
- 处理：服务层捕获并查询既有订单返回；同时 audit 记录

### 3.2 记账不平衡
- 现象：ledger_post failed / 校验失败
- 处理：记录 risk_event + audit_log；停止该订单后续推进（或进入 FAILED 状态）

### 3.3 状态机非法迁移
- 现象：抛 IllegalTransition
- 处理：audit 记录 + 触发风险事件；检查调用链是否绕过状态机

### 3.4 traceId 丢失
- 现象：日志/表记录无法串联
- 处理：检查 TraceIdFilter / scheduler trace 生成逻辑；强制在关键写入处落 trace_id

---

## 4. 手工修复原则（高压线）
- 禁止直接改余额/持仓来“修复”
- 允许追加 ledger_event/ledger_entries 作为补偿（并记录原因）
- 所有手工修复必须写入 audit_logs（含操作者与原因）