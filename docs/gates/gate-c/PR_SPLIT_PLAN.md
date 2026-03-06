# Gate C PR 拆分说明（截至 PR-W1）

> 目标：把 GateC 交付拆成可审查、可回滚、可复验的小步，避免“单 PR 大杂烩”。

---

## 1. 已完成 PR（C0 ~ C7）

### PR-C0：前置链路改造（必须）
- 范围：`nq-adapter-api`、`nq-core`、`nq-infra`、`nq-scheduler`
- 交付：adapter 三分法、`AdapterRouter`、`orders.external_order_id`、回执事件化。
- 不变量：幂等键、状态机迁移、event_store/audit 证据链。

### PR-C1：OKX 基础能力（Signer/HTTP/Instruments）
- 范围：`nq-adapter-okx`
- 交付：签名、统一 HTTP、instruments 缓存、解析单测。
- 不变量：请求可追踪、错误可定位、无盲重试。

### PR-C2：REST 交易闭环（place/cancel/reconcile 基础）
- 范围：`nq-adapter-okx`、`nq-scheduler`、`nq-core`
- 交付：place/cancel/get/list/fills 映射，`OrderAck` 落 `external_order_id`。
- 不变量：外部回执事件化、fills 去重、记账幂等。

### PR-C3：恢复入口最小实现
- 范围：`nq-scheduler`、`nq-app`
- 交付：启动/定时恢复路径接入。
- 不变量：恢复仅依赖事实链，不旁路服务层。

### PR-C4：Demo 验收入口（local only）
- 范围：`nq-app`、`docs/current`
- 交付：`/__gatec/orders|cancel|reconcile/runOnce|recovery/runOnce`。
- 不变量：controller 仅触发，不承载业务。

### PR-C5：运行态修复与 Dome 验收
- 范围：`nq-adapter-okx`、`nq-scheduler`、`nq-ledger`、`docs/current`
- 交付：fee 规范化、记账平衡修复、真实链路证据。
- 不变量：trade/ledger/position/event/audit 链完整。

### PR-C6：收尾硬化（双门禁 + 验收脚本）
- 范围：`nq-app`、`scripts/`、`docs/current`、`.env.example`
- 交付：`local + nq.gatec.verify.enabled=true` 双门禁；脚本化验收。
- 不变量：生产零暴露、验收可复现。

### PR-C7：真实盘阻塞修复（恢复/启动容错）
- 范围：`nq-adapter-okx`、`nq-scheduler`、`docs/current`、`docs/gates/gate-c`
- 交付：`OKX 51603` 容错不阻断启动；`preopen` instruments 缺精度字段跳过。
- 不变量：可审计降级、继续处理后续订单。

---

## 2. 进行中收尾动作

- 脚本：`scripts/gatec_okx_dome_verify.ps1` 已支持 `UseCase-C` 自动重启窗口复现实验（`-AutoRestart`）。
- 文档：`WORK.md` 记录真实盘复验与重启窗口自动化路径。
- WS：`PR-W1` 已落地连接治理层（连接/login/订阅管理/心跳/重连/指标），未触碰业务落库与状态机推进。

---

## 3. 后续建议 PR 拆分（GateC-1.1 WS 前置）

### PR-W1（已完成）：WS 基建与连接治理（不落业务）
- 只做连接、重连、订阅管理、心跳与指标。
- 不做订单状态推进，不改 ledger/positions。

### PR-W2（建议）：WS 事件映射 + event_store 入链
- 仅做 orders/trades/account 的标准事件 envelope 映射。
- 幂等键与 trace_id 对齐 REST 口径。

### PR-W3（建议）：WS-REST 协同与降级策略
- 断线窗口触发 REST reconcile。
- 明确重复事件去重（trade_id、ledger idempotency_key）。

---

## 4. 评审检查清单（每个 PR 必须）

- 对齐 `docs/current/GATE_CHECKLIST.md` 具体条目
- 说明改动范围与影响面
- 说明幂等/状态机/event_store/audit/ledger 不变量保障
- 附最小可复现验证命令与结果摘要
