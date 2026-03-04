# docs/gates/gate-c/RECOVERY_RUNBOOK.md
# Gate C RECOVERY RUNBOOK（CEX 恢复与对账）

恢复目标：
- 不重复下单（client_order_id 幂等）
- 不重复成交（trades 去重）
- 不重复记账（ledger idempotency_key）
- 非终态订单能继续推进
- WS（若启用）异常可降级

---

## 1. 启动恢复流程（REST-only 版，GateC-1 必须）

1) 打印连接指纹（server addr/port/db + datasource_url）
2) 扫描本地非终态 orders（NEW/RISK_PASSED/SENT/ACCEPTED/PARTIALLY_FILLED）
3) 调用交易所 listOpenOrders（OKX orders-pending）获取 live orders（对齐 instType=SPOT）
4) 对每个订单（按 account+symbol 分组处理）：
   - query order（trade/order）确认状态（尤其处理“下单超时但实际成功”）
   - pull fills（trade/fills）补写 trades（依赖 UNIQUE 去重）
   - 对每笔 fill 触发 ledger posting（依赖 idempotency_key 幂等）
   - 推进本地状态机到正确状态（终态：FILLED/CANCELED/REJECTED）
5) 可选：执行 reconcile（ledger vs positions/account_snapshots），差异写 audit+risk + event_store

强约束：
- placeOrder 超时：必须 query-confirm；禁止盲重试
- external_order_id：一旦确认外部订单存在必须落库，供后续关联

---

## 2. WS 启用后的恢复（GateC-1.1）

- 启动时仍然先执行 REST 恢复流程（上节 1）
- WS 连接仅用于增量加速
- WS 断线后必须触发一次 REST reconcile（限定时间窗/非终态订单集合）

---

## 3. 常见故障与处理

- Invalid signature / timestamp expired：
   - 写 audit+risk（含原因与指纹），并阻断下单（KillSwitch 可触发）
- placeOrder 超时：
   - query-confirm 后再处理（确认存在则补写 external_order_id 并继续同步）
- 限频：
   - 退避/降级并写审计；连续超限可触发 KillSwitch（必须留证据链）
- WS 断线（若启用）：
   - 降级 REST reconcile + 重连重订阅