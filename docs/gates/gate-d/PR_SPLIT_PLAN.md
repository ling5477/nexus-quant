# GateD PR_SPLIT_PLAN

> GateD 提交拆分计划。  
> 原则：单个 PR 只解决一类边界问题，做到能 review、能回滚、能定位。
> 状态约定：`[x] 已完成`、`[~] 进行中`、`[ ] 未开始`。  
> 当前状态基线：**截至 2026-03-13 的已实现与已验证事实**。

---

## 当前推进状态

- [x] PR-1：文档与阶段入口对齐
- [~] PR-2：contracts / core 执行入口收敛
- [x] PR-3：pre-trade 风控规则链
- [~] PR-4：状态机、事件与执行回执收敛
- [~] PR-5：scheduler / recovery / reconcile / degrade 收敛
- [~] PR-6：ledger / projection / db schema
- [~] PR-7：app / api 验收入口与查询视图
- [ ] PR-8：integration tests / freeze docs

---

## PR-1：文档与阶段入口对齐（已完成）

### 目标
- 修正 GateD 阶段定义
- 建立 GateD 完整文档目录
- 同步更新 `AGENTS.md`、`README.md`、`docs/current/*`

### 涉及文件
- `AGENTS.md`
- `README.md`
- `docs/current/*`
- `docs/gates/gate-d/*`
- `docs/ROADMAP.md`
- `docs/gates/gate-b/ROADMAP.md`
- `docs/gates/gate-c/ROADMAP.md`

### 不包含
- 任何核心代码逻辑改造

---

## PR-2：contracts / core 执行入口收敛（进行中）

### 目标
- 统一执行应用服务
- 收敛 place / cancel / ack / reject / trade-report / query-confirm 入口
- 明确 core 与 adapter / scheduler / ledger 边界

### 涉及模块
- `nq-core`
- `nq-contracts`
- `nq-adapter-api`

### 不包含
- 风控规则实现
- Flyway 迁移
- 大规模恢复逻辑重写

---

## PR-3：pre-trade 风控规则链（已完成）

### 目标
- 从 `NoopRiskGate` 过渡到规则链
- 引入 rule registry、拒绝码、统一返回模型

### 涉及模块
- `nq-risk`
- `nq-core`
- 文档：`RISK_RULES.md`

### 不包含
- 多交易所深扩边
- 复杂组合风控

---

## PR-4：状态机、事件与执行回执收敛（进行中）

### 目标
- 冻结订单状态机
- 明确本地状态与外部事实状态
- 收敛重复回报、乱序回报、终态保护

### 涉及模块
- `nq-core`
- `nq-adapter-api`
- `nq-adapter-okx`
- `nq-adapter-binance`
- 文档：`STATE_MACHINE.md`

---

## PR-5：scheduler / recovery / reconcile / degrade 收敛（进行中）

### 目标
- scheduler 瘦身
- 明确 reconcile、recovery、query-confirm、degrade 的职责与调用关系
- 补充运行与排障手册

### 涉及模块
- `nq-scheduler`
- `nq-core`
- `nq-observability`
- 文档：`COMPENSATION_SYNC.md`、`RECOVERY_RUNBOOK.md`

---

## PR-6：ledger / projection / db schema（进行中）

### 目标
- fills 去重
- ledger posting 幂等
- position / account snapshot 持久化与投影增强
- 新增 GateD 迁移脚本

### 涉及模块
- `nq-ledger`
- `nq-infra`
- 文档：`DB_SCHEMA.md`、`NUMERIC_POLICY.md`

---

## PR-7：app / api 验收入口与查询视图（进行中）

### 目标
- 建立 GateD 最小验收入口
- 收敛阶段性接口与正式查询入口
- 完成最小本地验证闭环

### 涉及模块
- `nq-app`
- `nq-api`

### 当前进展
- `__gated` canonical 验收入口、order/trade/position/account 本地闭环已完成
- OKX 验收脚本已改为 canonical non-fallback 启动路径，并显式支持 `.env -> NQ_OKX_ENV=dome|real -> NQ_OKX_API_*` 统一运行时变量映射
- `serviceBaseUrl` 已从旧 `http://localhost:28081` 收口为 `-BaseUrl -> NQ_GATED_SERVICE_BASE_URL / NQ_APP_BASE_URL -> http://localhost:${NQ_APP_PORT|18888}`，health timeout 已被排除
- 脚本 `accountId` 已收口为 `-AccountId -> NQ_GATED_ACCOUNT_ID / NQ_OKX_VERIFY_ACCOUNT_ID / NQ_ACCOUNT_ID -> 1001`，官方脚本默认不再写死旧 `2001`
- 本次官方脚本在 `verifyAccountId=1001` 下已拿到真重启真实样本：
  - UseCase-A：`place=200 / cancel=200 / reconcile=200(new_trades=0) / order=200(CANCELLED) / trade=404`
  - UseCase-B：`place=200 / reconcile=200(new_trades=2) / order=200(FILLED) / trade=200`
  - UseCase-C：`place=200 / recovery=200(processed_events=2, processed_ledger=0, invalid_transitions=0) / reconcile=200(new_trades=0) / cancel=200 / order=200(CANCELLED) / trade=404`
- 说明：`UC-D9` 的最小 `LIMIT -> cancel` 与真重启后的 `recovery / reconcile / cancel / query` 已取得官方脚本正向样本；UseCase-B 的 `reconcile new_trades=2 / trade=200` 已通过 DB 明细解释为同一订单下两条不同 `exchange_trade_id` 的真实成交，不再是待解释主阻塞。PR-7 继续保持进行中，剩余缺口只收敛为 `query-confirm` timeout 分支的显式日志样本；当前在 `NQ_OKX_TIMEOUT_MS=50 / 5 / 1` 的连续实验下仍未自然触发该分支，因此后续需要单独设计 timeout 样本，而不是继续修改 health、accountId 或真重启链路

---

## PR-8：integration tests / freeze docs（未开始）

### 目标
- 跑通 GateD 用例
- 更新 `WORK.md`
- 形成冻结结论

### 涉及模块
- `nq-app`
- `nq-core`
- `nq-risk`
- `nq-scheduler`
- `nq-ledger`
- 文档：`TEST_CASES.md`、`GATE_D_CHECKLIST.md`、`WORK.md`

---

## PR 通用要求

每个 PR 都必须：

- 标注对应 checklist 条目
- 说明不包含的范围
- 写出验证方式
- 若改动契约 / 状态机 / DB / 恢复逻辑，必须同步更新文档
- 不接受“文档之后补”的口头承诺
