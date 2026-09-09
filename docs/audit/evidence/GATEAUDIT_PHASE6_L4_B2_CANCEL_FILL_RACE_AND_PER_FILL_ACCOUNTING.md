# Phase6 L4 B2 cancel/fill race 与逐笔记账

日期：2026-09-09。任务：`NQ-GATEAUDIT-PHASE6-L4-B2-CANCEL-FILL-RACE-AND-PER-FILL-ACCOUNTING`。

**FAIL / L4_B2_CORRECTNESS_FINDING / P0_0 / P1_1**

**STOP / PRODUCTION_REMEDIATION_REQUIRED**。P0/P1 数字仅指本轮已确认 findings；不代表未运行场景无缺陷。本轮不是 `L4_B2_CANDIDATE_READY`，不得进入 B2 acceptance。生产代码未修改；首个真实进程正确性失败后停止 qualification，未通过修改 production、改变断言或忽略失败制造 PASS。

## 基线与授权

- Branch=`audit/post-gatey-agent-baseline`；开始 HEAD 与 origin tracking ref 均为 `d1cedb6debfaa3dbeacb0e95ef8599c69e5f9da3`；初始 worktree clean、`git diff --check` 通过。未 fetch 或改变 Git ref。
- 当前 HEAD 的 [NQ CI Baseline 34246407667](https://github.com/ling5477/nexus-quant/actions/runs/34246407667) 实时只读核验：`completed / success / 9 of 9`，headSha 精确匹配上述 HEAD，所有 jobs success。没有触发 CI。
- [B0 evidence](GATEAUDIT_PHASE6_L4_B0_REAL_PROCESS_HARNESS.md) 与 [B1 evidence](GATEAUDIT_PHASE6_L4_B1_ACCEPTED_TIMEOUT_AND_LOST_ACK.md) 及其代码均已在当前 HEAD。B0/B1 已接受的前提来自用户本轮指令；当前 CI green 已独立读回。默认 CI 不执行 opt-in B0/B1/B2 真实进程测试，不混淆这两类证据。
- STATUS/ROADMAP 仍保留 pre-B0 旧状态；B1 evidence 已记录相同滞后。本轮依据用户明确 B2 test-only 授权执行，未伪造 current authority transition、未修改 STATUS/ROADMAP/WORKLOG。
- tracked Flyway inventory 最大 V48；本轮 disposable PostgreSQL 实际 migrate/validate 48 项通过。C1/C2 保持已接受关闭；`git diff --name-only 612c2f5887a2e6b3a8b3138d9ae9b193c20e298f HEAD -- 'backend/*/src/main/**'` 为空，不重开 C1/C2。

## P1-B2-01：撤单受理 ACK 被当作撤单终态，最后一笔成交后无法收敛

**协议依据**：2026-09-09 通过公开文档 GET 读取 [OKX POST / Cancel order](https://www.okx.com/docs-v5/en/#order-book-trading-trade-post-cancel-order)，原文：

> Cancel order returns with sCode equal to 0. It is not strictly considered that the order has been canceled. It only means that your cancellation request has been accepted by the system server. The result of the cancellation is subject to the state pushed by the order channel or the get order state.

Synthetic Venue 因而将请求受理与撮合端取消生效分开。`CANCEL_EFFECT` 只在 venue 内执行：若已全部成交，保留 `filled`；否则取消未成交余量。Controller 不直接写任何本地业务事实，也不让 venue 在已经生效的取消之后违规新增成交。

最小复现采用 B0 原订单数量 `0.1`（按任务示例 10/4/6 等比例缩小），BUY BTC-USDT LIMIT 100：

1. 真正 OrderCommandService 经 RiskGate 发 PLACE；独立 venue 接受，应用返回 ACCEPTED。
2. Venue 产生 `b2-fill-1 / qty=0.04 / fee=0 USDT`；普通 RECOVER 后本地 `PARTIALLY_FILLED / version=4`、Trade=1。
3. 真正 cancelOrder 发 HTTP CANCEL。Venue 尚未取消，只返回 `sCode=0` 受理 ACK；本地已提交 `CANCELLED / version=6`。
4. 撤单生效前，venue 产生 `b2-fill-2 / qty=0.06 / fee=-0.01 USDT`，累计成交 `0.10`；随后的 CANCEL_EFFECT 保留真实 `filled`。
5. 普通 RECOVER 返回 `1`，第二笔 Trade 和 Ledger 已 durable，但本地 Order 仍 `CANCELLED / version=6`，与 venue `filled` 不一致。测试立即以正确期望 `FILLED` 失败。

这是 **ACK 先于最终 fill discovery** 的真实跨进程时序，不是“更新事实先提交后 stale ACK 才到”的 C1 场景；OCC 没有被绕过，ACK 提交时 CAS 可以合法成功。现象不是 version 回退，而是过早确认错误终态后无法校正。

直接生产链根因（只读定位，未修改）：

- [OkxExchangeAdapter.java](../../../backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxExchangeAdapter.java) `parseCancelAck`（613–636）：`sCode=0` 映射 accepted。
- [OrderCommandWriteService.java](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java) `finalizeAcceptedCancelOrder`（446–460）：将受理 ACK 用 OCC 推进为 `CANCELLED`，没有确认取消已生效。
- [OkxRestReconcileService.java](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java) `reconcileCancelledOrder`（211–232）：仅恢复 fills/ledger，不查询 venue order truth 或重新收敛 Order；`alignOrderStatus`（299）也跳过终态。本次 venue events 显示 CANCEL ACK 后只有 QUERY_FILLS，没有 QUERY_ORDER。

整改需另行获得 production 范围授权，并保留 C1 OCC/C2 有界扫描不变量。此 evidence 不指定重写状态机或新增 reconciliation 的方案。

## 真实运行身份与结果

Attempt-02，controller/Surefire 之外各场景独立 NQ JVM、独立 venue JVM、新数据库。共享同一个本轮新建 PostgreSQL 16.15 容器；不是共享业务数据库。

| 场景 | NQ PID | Venue PID | DB | 最终结果 |
| --- | --- | --- | --- | --- |
| partial fill → cancel | 44564 | 60740 | SYNTH-L4:B2:R01:DATABASE:001 | 单次状态/数量检查通过：CANCELLED、executed=0.04、remaining=0.06 |
| cancel ACK → final fill → cancel effect → recovery | 13892 | 22824 | SYNTH-L4:B2:R02:DATABASE:001 | FAIL：venue FILLED，本地 CANCELLED；executed=0.10、remaining=0 |

- PostgreSQL=`16.15 (Debian 16.15-1.pgdg13+2)`，schema=`V48`，container=`SYNTH-L4:B2-EXPORT:R01:CONTAINER:001`，container ID=`3af9b6bf770630b914ff9ae4da7e20c43b6a5cc034f23d2870e684ae4f34dd8d`。
- JDBC admin endpoint=`jdbc:postgresql://127.0.0.1:39713/postgres`；镜像=`postgres:16@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`，canonical 锁定镜像、`--pull=never`。
- NQ 实际 composition 输出：`realRisk=true writeProxy=true gateway=AdapterBackedTradingVenueGateway jdbcUser=nq_b0_app`。真实 Spring JVM、OkxExchangeAdapter、HTTP/JSON 与 JDBC 均在路径中。
- Controller 使用 `nq_b0_reader` 只读连接与 REPEATABLE_READ snapshots；B0 fixture 仅在进程启动前初始化 disposable DB。Runtime Controller/Checker 对 orders/trades/ledger/receipts 写入=0。
- 两场景各 PLACE=1、CANCEL=1，venue order identity=`b2-venue-1`；标识在各自独立 venue/DB 内有效，不跨场景混用。

失败场景本地事实（原始快照投影见下方）：

| Fact | 实际值 |
| --- | --- |
| Order ID | SYNTH-L4:B2:R02:ORDER:001 |
| client identity | SYNTH-L4:B2:R02:CLIENT:001 |
| external_order_id / exchange_order_id | b2-venue-1 / b2-venue-1，全程未漂移 |
| Order status/version | PARTIALLY_FILLED/4 → CANCELLED/6 → CANCELLED/6 |
| original / unique executed / remaining | 0.1 / (0.04+0.06)=0.10 / 0，未 clamp |
| b2-fill-1 → Trade | SYNTH-L4:B2:R02:TRADE:001，BUY，100×0.04=4 USDT，fee=0 |
| b2-fill-2 → Trade | SYNTH-L4:B2:R02:TRADE:002，BUY，100×0.06=6 USDT，fee magnitude=0.01 USDT |
| Ledger observed | 第一笔 USDT -4/+4；第二笔 -6/+6、fee -0.01/+0.01；共 6 entries、6 ledger events |
| position / account projection | BTC position=0.1，BTC snapshot=0.1；USDT snapshot=0 |
| Intent / Receipt | 0 / 0 |

这里记录的是停止点的事实，不将 Ledger entries 求和为 0 解释成用户资产净变化正确，也不把逐笔条数固定为 4。完整 canonical accounting 与 venue 资产净额、三笔差异 fee、重复报告、restart/replay 未完成资格验证；不能据上述观察宣称这些条目 PASS。

## 范围、验证与未覆盖项

变更仅为 [B0NqProcessMain](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0NqProcessMain.java) 的 canonical CANCEL 命令、[B2SyntheticVenueMain](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B2SyntheticVenueMain.java)、[B2RealProcessProofTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B2RealProcessProofTest.java) 和本 evidence。

复现命令（应在未整改生产候选上失败，不是预期缺陷即成功的测试）：

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am '-Dnq.b2=true' '-Dtest=B2RealProcessProofTest,B0FixtureSafetyTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- Attempt-01：编译通过，B0FixtureSafetyTest 8/8；B2 在 docker run 之前因 engine 不可用报 Error；没有 NQ/Venue 业务运行。原始日志 `artifacts/b2-attempt-01.log`。
- Attempt-02：Maven exit=1，9 tests / failures=1 / errors=0 / skipped=0，总时间 28.073s。B0FixtureSafetyTest 8/8；B2 suite 中 partial-cancel 单次通过，final-fill-cancel 在正确的 FILLED 断言失败；随后立即停止。原始日志 `artifacts/b2-attempt-02.log`。
- 两种竞态到达顺序、late PLACE/CANCEL ACK、三笔 fills、完整 accounting、restart/replay、三次独立重复均 **NOT_PROVEN**，没有为完成 matrix 在 finding 后继续运行。杀进程仅用于清理，不冒充 restart proof。
- C1/C2 生产源码未变化，保留历史接受事实；本轮没有重新执行 C1/C2 qualification 或独立 review。
- 真实交易 API 调用=0、交易凭证读取=0、LIVE=0；仅 loopback synthetic PLACE/CANCEL。公开 OKX 文档 GET 与 GitHub CI 只读核验不属于交易 API 调用。没有真实 provider、transfer/withdraw 或生产 kill 操作。
- B0 的 disposable 启动前 DISENGAGED 初始化与出站 allowlist 原样复用；不是 OS 防火墙证明。未修改 production/Flyway/.github/AGENTS/Skills/current authority。

## 清理与机器依赖

4 个 NQ/Venue PID 已由 harness 强杀并 wait 确认退出，之后独立 Get-Process 查询无残留。Fixture 关闭数据库连接并 DROP；正常场景额外查询 pg_database 确认缺失，失败场景因 assertion 退出未执行该额外查询，不虚报它已执行。整个拥有容器删除并读回 remaining=0；最终 `docker ps -a --filter label=nq.b0.identity` 无残留，因此两个 disposable DB 均不再存在。

Docker 起初未运行；本轮启动时 backend 因旧 Unix socket reparse point 无法 rename 而崩溃。只停止本轮失败启动的 Docker 进程，保留并改名两个仅含 socket 的运行目录，再启动成功，engine readback=29.7.2。没有 factory reset、镜像/容器数据删除或配置修改；Docker 自动更新提示未执行安装。以下备份保留在 workspace 外，不提交：

- `C:\Users\Lingyu\AppData\Local\Docker\run.b2-backup-20260909-0833`
- `C:\Users\Lingyu\AppData\Local\Docker\run.b2-backup-20260909-attempt02`
- `C:\Users\Lingyu\AppData\Local\docker-secrets-engine.b2-backup-20260909-0833`

这些仅为旧 socket 目录备份，不含镜像或交易凭证；未读取 socket 内容。若需要回退目录名称，应先正常退出 Docker，并保留新生成目录后再还原，不能覆盖运行中的 socket。Docker Desktop 与缓存镜像作为共享依赖继续保留。

## 候选身份与交付边界

测试源码 Git canonical blob（`git hash-object` 只读生成；未 stage）：

| Source | Git blob |
| --- | --- |
| B0NqProcessMain.java | 06d99490b00f2646814a7d39e362924acb416e60 |
| B2SyntheticVenueMain.java | ba70ebb1cf3ed48c5908831082214c5be0990cbf |
| B2RealProcessProofTest.java | b06f53a2ba7ee55525d760d4769c05af6bbe0aa5 |

原始进程 logs/args/proof JSON 位于 ignored `backend/nq-app/target/b2-harness/SYNTH-L4:B2-EXPORT:R01:RUN:001/`。下方将必要原始快照字段投影到本唯一 evidence，避免最小复现仅依赖 ignored 文件。数据库/客户端标识均为随机 synthetic fixture identity。

本轮 `stage=0 / commit=NONE / push=NONE`。下一步是单独授权 production remediation；整改后重新执行 B2，并按任务要求完成真正独立正确性审查。本次不更新 acceptance 或发布结论。

## 停止点快照投影

下列数据来自 attempt-02 proof JSON：保留实际 Order checkpoints、venue ordering/fills、最终 Trade/Ledger/Risk facts；未增加模拟结果或重写原始日志。

### partial-cancel

Raw local proof SHA-256: e4c4221faa5da31256f5acbcf4fc461bdc71b30c6ba7efca5c94a038a591002f

```json
{
  "scenario": "PARTIAL_FILL_CANCEL",
  "controllerPid": 38216,
  "database": "SYNTH-L4:B2:R01:DATABASE:001",
  "nqPid": 44564,
  "venuePid": 60740,
  "venueEndpoint": "http://127.0.0.1:12230",
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "partialOrders": [
    {
      "qty": 0.1,
      "side": "BUY",
      "type": "LIMIT",
      "price": 100.0,
      "venue": "OKX",
      "reason": "RECONCILE_STATUS_ALIGN",
      "status": "PARTIALLY_FILLED",
      "symbol": "BTC-USDT",
      "version": 4,
      "order_id": "SYNTH-L4:B2:R01:ORDER:001",
      "trace_id": "SYNTH-L4:B2:R01:TRACE:001",
      "dedup_key": "1:SYNTH-L4:B2:R01:CLIENT:001",
      "trade_env": "SIM",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:50.054402+08:00",
      "request_id": "SYNTH-L4:B2:R01:REQUEST:001",
      "updated_at": "2026-09-09T08:32:50.328162+08:00",
      "exchange_code": "OKX",
      "client_order_id": "SYNTH-L4:B2:R01:CLIENT:001",
      "strategy_run_id": null,
      "exchange_order_id": "SYNTH-L4:B2:R01:VENUE:001",
      "external_order_id": "SYNTH-L4:B2:R01:VENUE:001"
    }
  ],
  "afterCancelAckOrders": [
    {
      "qty": 0.1,
      "side": "BUY",
      "type": "LIMIT",
      "price": 100.0,
      "venue": "OKX",
      "reason": "B2_CANCEL",
      "status": "CANCELLED",
      "symbol": "BTC-USDT",
      "version": 6,
      "order_id": "SYNTH-L4:B2:R01:ORDER:001",
      "trace_id": "SYNTH-L4:B2:R01:TRACE:001",
      "dedup_key": "1:SYNTH-L4:B2:R01:CLIENT:001",
      "trade_env": "SIM",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:50.054402+08:00",
      "request_id": "SYNTH-L4:B2:R01:REQUEST:001",
      "updated_at": "2026-09-09T08:32:50.433447+08:00",
      "exchange_code": "OKX",
      "client_order_id": "SYNTH-L4:B2:R01:CLIENT:001",
      "strategy_run_id": null,
      "exchange_order_id": "SYNTH-L4:B2:R01:VENUE:001",
      "external_order_id": "SYNTH-L4:B2:R01:VENUE:001"
    }
  ],
  "venueAfterCancelAck": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 60740,
    "places": 1,
    "cancels": 1,
    "pendingCancel": true,
    "order": {
      "clOrdId": "SYNTH-L4:B2:R01:CLIENT:001",
      "ordId": "SYNTH-L4:B2:R01:VENUE:001",
      "instId": "BTC-USDT",
      "state": "partially_filled",
      "px": "100",
      "sz": "0.1",
      "accFillSz": "0.04",
      "avgPx": "100",
      "uTime": "1788913970207"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B2:R01:FILL:001",
        "ordId": "SYNTH-L4:B2:R01:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "0.04",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788913970298"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 813442970472500
      },
      {
        "sequence": 3,
        "type": "FILL",
        "nanoTime": 813443061401100,
        "tradeId": "SYNTH-L4:B2:R01:FILL:001",
        "qty": "0.04",
        "fee": "0"
      },
      {
        "sequence": 4,
        "type": "QUERY_ORDER",
        "nanoTime": 813443086289300,
        "state": "partially_filled"
      },
      {
        "sequence": 5,
        "type": "QUERY_FILLS",
        "nanoTime": 813443095274200
      },
      {
        "sequence": 6,
        "type": "CANCEL_REQUEST_ACCEPTED_ACK",
        "nanoTime": 813443193929400,
        "state": "partially_filled"
      }
    ]
  },
  "recoveryResult": "RECOVER 0",
  "finalOrders": [
    {
      "qty": 0.1,
      "side": "BUY",
      "type": "LIMIT",
      "price": 100.0,
      "venue": "OKX",
      "reason": "B2_CANCEL",
      "status": "CANCELLED",
      "symbol": "BTC-USDT",
      "version": 6,
      "order_id": "SYNTH-L4:B2:R01:ORDER:001",
      "trace_id": "SYNTH-L4:B2:R01:TRACE:001",
      "dedup_key": "1:SYNTH-L4:B2:R01:CLIENT:001",
      "trade_env": "SIM",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:50.054402+08:00",
      "request_id": "SYNTH-L4:B2:R01:REQUEST:001",
      "updated_at": "2026-09-09T08:32:50.433447+08:00",
      "exchange_code": "OKX",
      "client_order_id": "SYNTH-L4:B2:R01:CLIENT:001",
      "strategy_run_id": null,
      "exchange_order_id": "SYNTH-L4:B2:R01:VENUE:001",
      "external_order_id": "SYNTH-L4:B2:R01:VENUE:001"
    }
  ],
  "finalTrades": [
    {
      "ts": "2026-09-09T08:32:50.298+08:00",
      "fee": 0.0,
      "qty": 0.04,
      "price": 100.0,
      "symbol": "BTC-USDT",
      "exchange": "OKX",
      "order_id": "SYNTH-L4:B2:R01:ORDER:001",
      "trace_id": "SYNTH-L4:B2:R01:TRACE:001",
      "trade_id": "SYNTH-L4:B2:R01:TRADE:001",
      "trade_env": "SIM",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:50.339599+08:00",
      "fee_currency": "USDT",
      "exchange_code": "OKX",
      "strategy_run_id": null,
      "exchange_order_id": "SYNTH-L4:B2:R01:VENUE:001",
      "exchange_trade_id": "SYNTH-L4:B2:R01:FILL:001",
      "external_order_id": "SYNTH-L4:B2:R01:VENUE:001"
    }
  ],
  "finalLedger": [
    {
      "ts": "2026-09-09T08:32:50.298+08:00",
      "delta": 4.0,
      "ref_id": "SYNTH-L4:B2:R01:TRADE:001",
      "currency": "USDT",
      "entry_id": "SYNTH-L4:B2:R01:ENTRY:001",
      "ref_type": "TRADE",
      "trace_id": "SYNTH-L4:B2:R01:TRACE:001",
      "direction": "CREDIT",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:50.345603+08:00",
      "balance_after": 0.0,
      "idempotency_key": "SYNTH-L4:B2:R01:TRADE:001:LEDGER:2"
    },
    {
      "ts": "2026-09-09T08:32:50.298+08:00",
      "delta": -4.0,
      "ref_id": "SYNTH-L4:B2:R01:TRADE:001",
      "currency": "USDT",
      "entry_id": "SYNTH-L4:B2:R01:ENTRY:002",
      "ref_type": "TRADE",
      "trace_id": "SYNTH-L4:B2:R01:TRACE:001",
      "direction": "DEBIT",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:50.345603+08:00",
      "balance_after": -4.0,
      "idempotency_key": "SYNTH-L4:B2:R01:TRADE:001:LEDGER:1"
    }
  ],
  "ledgerEventCount": 2,
  "riskEvents": [
    {
      "scope": "ORDER",
      "reason": "RISK_RULES_PASSED",
      "rule_id": "RISK_RULES_PASSED",
      "decision": "ALLOW",
      "scope_id": "SYNTH-L4:B2:R01:ORDER:001",
      "severity": "LOW",
      "trace_id": "SYNTH-L4:B2:R01:TRACE:001",
      "created_at": "2026-09-09T08:32:50.086733+08:00",
      "risk_event_id": "SYNTH-L4:B2:R01:RISK:001"
    }
  ],
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 60740,
    "places": 1,
    "cancels": 1,
    "pendingCancel": false,
    "order": {
      "clOrdId": "SYNTH-L4:B2:R01:CLIENT:001",
      "ordId": "SYNTH-L4:B2:R01:VENUE:001",
      "instId": "BTC-USDT",
      "state": "canceled",
      "px": "100",
      "sz": "0.1",
      "accFillSz": "0.04",
      "avgPx": "100",
      "uTime": "1788913970207"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B2:R01:FILL:001",
        "ordId": "SYNTH-L4:B2:R01:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "0.04",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788913970298"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 813442970472500
      },
      {
        "sequence": 3,
        "type": "FILL",
        "nanoTime": 813443061401100,
        "tradeId": "SYNTH-L4:B2:R01:FILL:001",
        "qty": "0.04",
        "fee": "0"
      },
      {
        "sequence": 4,
        "type": "QUERY_ORDER",
        "nanoTime": 813443086289300,
        "state": "partially_filled"
      },
      {
        "sequence": 5,
        "type": "QUERY_FILLS",
        "nanoTime": 813443095274200
      },
      {
        "sequence": 6,
        "type": "CANCEL_REQUEST_ACCEPTED_ACK",
        "nanoTime": 813443193929400,
        "state": "partially_filled"
      },
      {
        "sequence": 7,
        "type": "CANCEL_EFFECT",
        "nanoTime": 813443309080200,
        "state": "canceled"
      },
      {
        "sequence": 8,
        "type": "QUERY_FILLS",
        "nanoTime": 813443317811300
      }
    ]
  }
}
```

### final-fill-cancel

Raw local proof SHA-256: cd05eff364d2cfa39e1d09d739acf7615fec969dcfab126935764ab99e775330

```json
{
  "scenario": "FINAL_FILL_BEFORE_CANCEL_EFFECT",
  "controllerPid": 38216,
  "database": "SYNTH-L4:B2:R02:DATABASE:001",
  "nqPid": 13892,
  "venuePid": 22824,
  "venueEndpoint": "http://127.0.0.1:5164",
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "partialOrders": [
    {
      "qty": 0.1,
      "side": "BUY",
      "type": "LIMIT",
      "price": 100.0,
      "venue": "OKX",
      "reason": "RECONCILE_STATUS_ALIGN",
      "status": "PARTIALLY_FILLED",
      "symbol": "BTC-USDT",
      "version": 4,
      "order_id": "SYNTH-L4:B2:R02:ORDER:001",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "dedup_key": "1:SYNTH-L4:B2:R02:CLIENT:001",
      "trade_env": "SIM",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.283029+08:00",
      "request_id": "SYNTH-L4:B2:R02:REQUEST:001",
      "updated_at": "2026-09-09T08:32:56.518291+08:00",
      "exchange_code": "OKX",
      "client_order_id": "SYNTH-L4:B2:R02:CLIENT:001",
      "strategy_run_id": null,
      "exchange_order_id": "SYNTH-L4:B2:R02:VENUE:001",
      "external_order_id": "SYNTH-L4:B2:R02:VENUE:001"
    }
  ],
  "afterCancelAckOrders": [
    {
      "qty": 0.1,
      "side": "BUY",
      "type": "LIMIT",
      "price": 100.0,
      "venue": "OKX",
      "reason": "B2_CANCEL",
      "status": "CANCELLED",
      "symbol": "BTC-USDT",
      "version": 6,
      "order_id": "SYNTH-L4:B2:R02:ORDER:001",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "dedup_key": "1:SYNTH-L4:B2:R02:CLIENT:001",
      "trade_env": "SIM",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.283029+08:00",
      "request_id": "SYNTH-L4:B2:R02:REQUEST:001",
      "updated_at": "2026-09-09T08:32:56.629257+08:00",
      "exchange_code": "OKX",
      "client_order_id": "SYNTH-L4:B2:R02:CLIENT:001",
      "strategy_run_id": null,
      "exchange_order_id": "SYNTH-L4:B2:R02:VENUE:001",
      "external_order_id": "SYNTH-L4:B2:R02:VENUE:001"
    }
  ],
  "venueAfterCancelAck": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 22824,
    "places": 1,
    "cancels": 1,
    "pendingCancel": true,
    "order": {
      "clOrdId": "SYNTH-L4:B2:R02:CLIENT:001",
      "ordId": "SYNTH-L4:B2:R02:VENUE:001",
      "instId": "BTC-USDT",
      "state": "partially_filled",
      "px": "100",
      "sz": "0.1",
      "accFillSz": "0.04",
      "avgPx": "100",
      "uTime": "1788913976418"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B2:R02:FILL:001",
        "ordId": "SYNTH-L4:B2:R02:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "0.04",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788913976499"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 813449180879600
      },
      {
        "sequence": 3,
        "type": "FILL",
        "nanoTime": 813449262500200,
        "tradeId": "SYNTH-L4:B2:R02:FILL:001",
        "qty": "0.04",
        "fee": "0"
      },
      {
        "sequence": 4,
        "type": "QUERY_ORDER",
        "nanoTime": 813449277006000,
        "state": "partially_filled"
      },
      {
        "sequence": 5,
        "type": "QUERY_FILLS",
        "nanoTime": 813449283945800
      },
      {
        "sequence": 6,
        "type": "CANCEL_REQUEST_ACCEPTED_ACK",
        "nanoTime": 813449390195300,
        "state": "partially_filled"
      }
    ]
  },
  "recoveryResult": "RECOVER 1",
  "finalOrders": [
    {
      "qty": 0.1,
      "side": "BUY",
      "type": "LIMIT",
      "price": 100.0,
      "venue": "OKX",
      "reason": "B2_CANCEL",
      "status": "CANCELLED",
      "symbol": "BTC-USDT",
      "version": 6,
      "order_id": "SYNTH-L4:B2:R02:ORDER:001",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "dedup_key": "1:SYNTH-L4:B2:R02:CLIENT:001",
      "trade_env": "SIM",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.283029+08:00",
      "request_id": "SYNTH-L4:B2:R02:REQUEST:001",
      "updated_at": "2026-09-09T08:32:56.629257+08:00",
      "exchange_code": "OKX",
      "client_order_id": "SYNTH-L4:B2:R02:CLIENT:001",
      "strategy_run_id": null,
      "exchange_order_id": "SYNTH-L4:B2:R02:VENUE:001",
      "external_order_id": "SYNTH-L4:B2:R02:VENUE:001"
    }
  ],
  "finalTrades": [
    {
      "ts": "2026-09-09T08:32:56.499+08:00",
      "fee": 0.0,
      "qty": 0.04,
      "price": 100.0,
      "symbol": "BTC-USDT",
      "exchange": "OKX",
      "order_id": "SYNTH-L4:B2:R02:ORDER:001",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "trade_id": "SYNTH-L4:B2:R02:TRADE:001",
      "trade_env": "SIM",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.528346+08:00",
      "fee_currency": "USDT",
      "exchange_code": "OKX",
      "strategy_run_id": null,
      "exchange_order_id": "SYNTH-L4:B2:R02:VENUE:001",
      "exchange_trade_id": "SYNTH-L4:B2:R02:FILL:001",
      "external_order_id": "SYNTH-L4:B2:R02:VENUE:001"
    },
    {
      "ts": "2026-09-09T08:32:56.728+08:00",
      "fee": 0.01,
      "qty": 0.06,
      "price": 100.0,
      "symbol": "BTC-USDT",
      "exchange": "OKX",
      "order_id": "SYNTH-L4:B2:R02:ORDER:001",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "trade_id": "SYNTH-L4:B2:R02:TRADE:002",
      "trade_env": "SIM",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.746056+08:00",
      "fee_currency": "USDT",
      "exchange_code": "OKX",
      "strategy_run_id": null,
      "exchange_order_id": "SYNTH-L4:B2:R02:VENUE:001",
      "exchange_trade_id": "SYNTH-L4:B2:R02:FILL:002",
      "external_order_id": "SYNTH-L4:B2:R02:VENUE:001"
    }
  ],
  "finalLedger": [
    {
      "ts": "2026-09-09T08:32:56.499+08:00",
      "delta": 4.0,
      "ref_id": "SYNTH-L4:B2:R02:TRADE:001",
      "currency": "USDT",
      "entry_id": "SYNTH-L4:B2:R02:ENTRY:001",
      "ref_type": "TRADE",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "direction": "CREDIT",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.533688+08:00",
      "balance_after": 0.0,
      "idempotency_key": "SYNTH-L4:B2:R02:TRADE:001:LEDGER:2"
    },
    {
      "ts": "2026-09-09T08:32:56.499+08:00",
      "delta": -4.0,
      "ref_id": "SYNTH-L4:B2:R02:TRADE:001",
      "currency": "USDT",
      "entry_id": "SYNTH-L4:B2:R02:ENTRY:002",
      "ref_type": "TRADE",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "direction": "DEBIT",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.533688+08:00",
      "balance_after": -4.0,
      "idempotency_key": "SYNTH-L4:B2:R02:TRADE:001:LEDGER:1"
    },
    {
      "ts": "2026-09-09T08:32:56.728+08:00",
      "delta": 0.01,
      "ref_id": "SYNTH-L4:B2:R02:TRADE:002",
      "currency": "USDT",
      "entry_id": "SYNTH-L4:B2:R02:ENTRY:003",
      "ref_type": "TRADE",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "direction": "CREDIT",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.747937+08:00",
      "balance_after": 0.0,
      "idempotency_key": "SYNTH-L4:B2:R02:TRADE:002:LEDGER:FEE_2"
    },
    {
      "ts": "2026-09-09T08:32:56.728+08:00",
      "delta": -0.01,
      "ref_id": "SYNTH-L4:B2:R02:TRADE:002",
      "currency": "USDT",
      "entry_id": "SYNTH-L4:B2:R02:ENTRY:004",
      "ref_type": "TRADE",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "direction": "DEBIT",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.747937+08:00",
      "balance_after": -0.01,
      "idempotency_key": "SYNTH-L4:B2:R02:TRADE:002:LEDGER:FEE_1"
    },
    {
      "ts": "2026-09-09T08:32:56.728+08:00",
      "delta": -6.0,
      "ref_id": "SYNTH-L4:B2:R02:TRADE:002",
      "currency": "USDT",
      "entry_id": "SYNTH-L4:B2:R02:ENTRY:005",
      "ref_type": "TRADE",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "direction": "DEBIT",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.747937+08:00",
      "balance_after": -6.0,
      "idempotency_key": "SYNTH-L4:B2:R02:TRADE:002:LEDGER:1"
    },
    {
      "ts": "2026-09-09T08:32:56.728+08:00",
      "delta": 6.0,
      "ref_id": "SYNTH-L4:B2:R02:TRADE:002",
      "currency": "USDT",
      "entry_id": "SYNTH-L4:B2:R02:ENTRY:006",
      "ref_type": "TRADE",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "direction": "CREDIT",
      "account_id": 1,
      "created_at": "2026-09-09T08:32:56.747937+08:00",
      "balance_after": 0.0,
      "idempotency_key": "SYNTH-L4:B2:R02:TRADE:002:LEDGER:2"
    }
  ],
  "ledgerEventCount": 6,
  "riskEvents": [
    {
      "scope": "ORDER",
      "reason": "RISK_RULES_PASSED",
      "rule_id": "RISK_RULES_PASSED",
      "decision": "ALLOW",
      "scope_id": "SYNTH-L4:B2:R02:ORDER:001",
      "severity": "LOW",
      "trace_id": "SYNTH-L4:B2:R02:TRACE:001",
      "created_at": "2026-09-09T08:32:56.31387+08:00",
      "risk_event_id": "SYNTH-L4:B2:R02:RISK:001"
    }
  ],
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 22824,
    "places": 1,
    "cancels": 1,
    "pendingCancel": false,
    "order": {
      "clOrdId": "SYNTH-L4:B2:R02:CLIENT:001",
      "ordId": "SYNTH-L4:B2:R02:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "0.1",
      "accFillSz": "0.10",
      "avgPx": "100",
      "uTime": "1788913976418"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B2:R02:FILL:001",
        "ordId": "SYNTH-L4:B2:R02:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "0.04",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788913976499"
      },
      {
        "tradeId": "SYNTH-L4:B2:R02:FILL:002",
        "ordId": "SYNTH-L4:B2:R02:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "0.06",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788913976728"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 813449180879600
      },
      {
        "sequence": 3,
        "type": "FILL",
        "nanoTime": 813449262500200,
        "tradeId": "SYNTH-L4:B2:R02:FILL:001",
        "qty": "0.04",
        "fee": "0"
      },
      {
        "sequence": 4,
        "type": "QUERY_ORDER",
        "nanoTime": 813449277006000,
        "state": "partially_filled"
      },
      {
        "sequence": 5,
        "type": "QUERY_FILLS",
        "nanoTime": 813449283945800
      },
      {
        "sequence": 6,
        "type": "CANCEL_REQUEST_ACCEPTED_ACK",
        "nanoTime": 813449390195300,
        "state": "partially_filled"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 813449491305000,
        "tradeId": "SYNTH-L4:B2:R02:FILL:002",
        "qty": "0.06",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "CANCEL_EFFECT",
        "nanoTime": 813449492332600,
        "state": "filled"
      },
      {
        "sequence": 9,
        "type": "QUERY_FILLS",
        "nanoTime": 813449500064400
      }
    ]
  }
}
```

## 收尾读回

目标 doc-link checker：checked=8、warnings=0、errors=0。三个 Java 源码 blob 与上表一致；四个变更文件的 trailing whitespace 检查通过，两个 evidence JSON blocks 可解析。已执行要求的 status/diff --check/diff --stat/diff --name-only/diff；普通 diff 仅显示一个已跟踪文件，另外两个 Java 与本 evidence 为未跟踪文件，已另行逐项自查。最终 HEAD 不变，staged diff 为空，未 stage/commit/push；拥有容器最终只读查询无残留。未修改测试源码后重跑或稀释 attempt-02 的失败结果。