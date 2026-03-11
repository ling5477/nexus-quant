# docs/current/README.md
# Current Gate（当前阶段入口）

当前阶段：**Gate C（CEX 接入：OKX -> Binance）**

本目录是“当前 Gate 的唯一入口”。切换 Gate 时，只需要更新本目录内文件内容即可；历史 Gate 文档固定在 `docs/gates/` 下。

---

## 1. 当前 Gate 的目标

GateC 目标：在 GateB 的“幂等/状态机/事实链(event_store)/账本(ledger)/审计(audit)/风控(risk)/可恢复”底座上，接入真实 CEX（先 OKX 现货，后 Binance 现货），实现真实下单/撤单/成交同步/记账/持仓投影，并可重启恢复与对账。

关键原则：
- **REST-first**：先用 REST 跑通闭环（GateC-1），WS 仅作为后置加速（GateC-1.1），且必须保留 REST reconcile 兜底。
- **adapter 为中心**：PAPER/OKX/BINANCE 都是 adapter 实现，core/ledger/risk 不出现 venue 分支。
- **超时禁止盲重试**：必须 query-confirm（查单/挂单/成交）后再补偿动作。
- **幂等/去重/审计不可破坏**：orders 幂等、trades 去重、ledger 幂等、event_store 事实链全量留痕。

---

## 2. GateC 文档入口（冻结版）

- 总览架构：`docs/gates/gate-c/ARCHITECTURE.md`
- 契约：`docs/gates/gate-c/CONTRACTS.md`
- DB 增量：`docs/gates/gate-c/DB_SCHEMA.md`
- 决策记录：`docs/gates/gate-c/DECISIONS.md`
- 演进规则：`docs/gates/gate-c/EVOLUTION_RULES.md`
- 模块职责：`docs/gates/gate-c/MODULES.md`
- 数值精度：`docs/gates/gate-c/NUMERIC_POLICY.md`
- 恢复与对账：`docs/gates/gate-c/RECOVERY_RUNBOOK.md`
- 路线图：`docs/gates/gate-c/ROADMAP.md`
- 工作记录：`docs/gates/gate-c/WORK.md`
- 权威依据：`docs/gates/gate-c/SOURCES.md`
- 验收清单：`docs/gates/gate-c/GATE_C_CHECKLIST.md`
- PR 拆分说明：`docs/gates/gate-c/PR_SPLIT_PLAN.md`

---

## 3. 当前 Gate 唯一验收入口

- 统一验收清单：`docs/current/GATE_CHECKLIST.md`
  - 该文件是 GateC 的验收门禁（Source of Truth）。
- GateC 验收入口 `POST /__gatec/*` 仅在 `local + nq.gatec.verify.enabled=true` 时启用，生产环境不暴露；
  可重复验收脚本见 `scripts/gatec_okx_dome_verify.ps1`。

---

## 4. 当前执行顺序（只做 GateC 主线）

1) GateC-0（必须）：adapter-api 三分法 + AdapterRouter + orders.external_order_id + 回执事件化
2) GateC-1（必须）：OKX Spot REST-only 闭环（place/cancel/query/orders-pending/fills + reconcile + ledger + positions）
3) GateC-1.1（可选后置）：OKX 私有 WS（orders/account/positions 或 balance_and_position）+ REST reconcile 兜底
4) GateC-2：Binance 复用接入

---

## 5. 文档依据说明（必须）

- GateC 的所有“交易所接口/WS 通道/关键约束”的权威依据统一收敛在：
  - `docs/gates/gate-c/SOURCES.md`
- 当实现与文档不一致时：以 `docs/current/*` 为准，并在对应 Gate 文档与 SOURCES 中补齐依据链接。

---

## 6. 运行环境切换与执行前校验

GateC 运行态验收只允许在本地 `.env` 中切环境，不允许在命令行临时混入另一套 dome/real 凭证。切换后必须重启 `nq-app`，并在 `docs/gates/gate-c/WORK.md` 或对应验收记录中明确写出本轮使用的是 `dome` 还是 `real`。

### 6.1 环境切换矩阵

- `OKX Demo`
  - `NQ_OKX_ENV=dome`
  - 使用 `NQ_OKX_DOME_BASE_URL / NQ_OKX_DOME_API_KEY / NQ_OKX_DOME_API_SECRET / NQ_OKX_DOME_API_PASSPHRASE`
  - 私有 WS 默认 `NQ_OKX_DOME_WS_URL=wss://wspap.okx.com:8443/ws/v5/private`
- `OKX Real`
  - `NQ_OKX_ENV=real`
  - 使用 `NQ_OKX_REAL_BASE_URL / NQ_OKX_REAL_API_KEY / NQ_OKX_REAL_API_SECRET / NQ_OKX_REAL_API_PASSPHRASE`
  - 私有 WS 默认 `NQ_OKX_REAL_WS_URL=wss://ws.okx.com:8443/ws/v5/private`
- `Binance Testnet`
  - `NQ_BINANCE_ENV=dome`
  - `NQ_BINANCE_KEY_TYPE=hmac`
  - 使用 `NQ_BINANCE_DOME_BASE_URL / NQ_BINANCE_DOME_API_KEY / NQ_BINANCE_DOME_API_SECRET`
  - ws-api 地址必须固定为 `NQ_BINANCE_DOME_WS_URL=wss://ws-api.testnet.binance.vision/ws-api/v3`
- `Binance Real`
  - `NQ_BINANCE_ENV=real`
  - `NQ_BINANCE_KEY_TYPE=ed25519`
  - 使用 `NQ_BINANCE_REAL_BASE_URL / NQ_BINANCE_REAL_API_KEY / NQ_BINANCE_REAL_PRIVATE_KEY_PATH`
  - ws-api 地址必须固定为 `NQ_BINANCE_REAL_WS_URL=wss://ws-api.binance.com:443/ws-api/v3`

### 6.2 强制执行规则

- 每次运行态验收前，必须打印当前环境指纹：
  - OKX：`env / baseUrl / apiKey 前4后4`
  - Binance：`env / keyType / baseUrl / apiKey 前4后4`
- 切换 `dome/real` 后，必须重启 `nq-app`。示例命令：
  - `mvn -q -f backend/pom.xml -pl nq-app spring-boot:run`
- 同一轮验收不得混用 `dome` 和 `real`。
- `.env` 只允许本地修改，严禁提交。
- 后续任何新增配置都必须同时更新 `.env.example` 与本地 `.env`：
  - 每个配置项都要写中文注释，说明用途、默认值和切换条件。
  - 非敏感项要固化默认值；敏感项只保留占位提示，不得写入仓库。
- 如果某轮验收打开了 `NQ_OKX_WS_ENABLED` 或 `NQ_BINANCE_WS_ENABLED`，仍必须保留 REST reconcile 兜底，不得把 WS 当成唯一事实来源。

### 6.3 执行前检查清单

1. 确认 `.env` 中 `NQ_OKX_ENV / NQ_BINANCE_ENV / NQ_BINANCE_KEY_TYPE` 与本轮目标一致。
2. 确认 OKX 与 Binance 的 `BASE_URL / WS_URL` 指向本轮环境，不使用历史残留地址。
3. 确认敏感项只保存在本机 `.env`，不写入仓库、日志、文档。
4. 启动前打印环境指纹，启动后在日志中核对运行时指纹与 `.env` 一致。
