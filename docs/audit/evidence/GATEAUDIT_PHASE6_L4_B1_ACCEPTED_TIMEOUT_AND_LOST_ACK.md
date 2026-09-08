# Phase6 L4 B1 accepted timeout / lost ACK

日期：2026-09-08。任务：`NQ-GATEAUDIT-PHASE6-L4-B1-ACCEPTED-TIMEOUT-AND-LOST-ACK`。

结论：`PASS / L4_B1_ACCEPTED_TIMEOUT_PROVEN / L4_B1_LOST_ACK_PROVEN / NO_BLIND_RETRY / QUERY_FIRST_RECOVERY_PROVEN / P0_0 / P1_0`。

候选：`SELF_REVIEWED / READY_TO_COMMIT`。本轮仅测试及证据，未 stage/commit/push；本结论是 B1 本地 qualification，不是整个 L4 acceptance 或新候选 exact-head CI acceptance。

## 基线与 CI

- Branch：`audit/post-gatey-agent-baseline`；开始时工作区干净，HEAD、origin tracking ref、远端实时 ref 均为 `2b0eb0cc8d6e43f4c365339743f9fd62c9199e54`。用户指定的五项开始 Git 检查通过。
- B0 已在该 HEAD 内。首次检查未找到 B0 CI，故未开始 B1；随后用户明确授权触发当前 HEAD 的 CI。
- [NQ CI Baseline 34236471862](https://github.com/ling5477/nexus-quant/actions/runs/34236471862) 经 `gh workflow run ci.yml --ref audit/post-gatey-agent-baseline` 触发，实时 readback `headSha=2b0eb0cc8d6e43f4c365339743f9fd62c9199e54 / completed / success / 9 of 9`，failed/skipped/cancelled jobs=0。一次 readback 遇到 EOF，重试读取成功；没有重复触发。
- CI 只证明已提交基线。B0/B1 opt-in real-process 不由默认 CI 自动执行；B1 在该 CI 成功后才实现和运行。
- STATUS/ROADMAP 仍保留 pre-B0 状态，本轮未改 current authority。B1 执行依据是用户明确任务授权，不把未同步文档写成已完成 authority transition。

## Matrix 与故障

依照[已接受 L4 matrix](GATEAUDIT_PHASE6_L4_FAILURE_MATRIX_PLAN.md) §4.2/§12/§18：当前 B1 eligible rows 为 `L4-AT-01 / ORDINARY` 和 `L4-LA-02 / ORDINARY`，各三次新 DB、新 venue、新运行。TYPED 的 AT-02/LA-01 仍是 future obligation，不计 PASS 或 SKIPPED。

复用 [B0 Spring JVM](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0NqProcessMain.java)、[fixture](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0Fixture.java) 与独立 venue；NQ main 未修改，仍调用真实 OrderCommandService、RiskGate、AdapterBackedTradingVenueGateway、OkxExchangeAdapter 与 OkxRestReconcileService。父进程只通过 reader role 获取 REPEATABLE_READ 快照，不写业务终态。

- **ACCEPTED_TIMEOUT**：独立 venue 保存 live order，记录 REQUEST_RECEIVED/VENUE_ACCEPTED，不序列化 ACK、不发送 HTTP headers/body，保留 exchange 并立即释放 handler 锁。NQ 的既有 2 秒 HTTP timeout 自然发生；现有 adapter 的 `queryConfirmAfterTimeout` 用 stable client identity 发出 GET。三个实际 request→query 时间为 1.9977194、2.0070897、1.9999219 秒；没有注入 timeout exception。应用服务最终返回的 ACCEPTED 来自 query-confirm，不是正常 PLACE ACK。
- **ACK_GENERATED_THEN_LOST**：venue 先序列化 ACK，记录实际 UTF-8 body、byte count、SHA-256，但不发送任何响应字节。父进程观察 ACK barrier 和 durable SENT、确认没有 PLACE result 或查询后强杀 NQ；确认 PID 死亡才关闭 withheld exchange。新 NQ PID 使用相同 DB，经普通 RECOVER/query-first 恢复。这是 matrix 的 `ACK_GENERATED_CALLER_DEATH`，没有把仍存活调用方的 socket-close 路径或 dormant typed row 宣称已覆盖。
- 两种故障均先证明未成交时 Trade=0、Ledger=0，再向 venue 发 FILL 控制指令。fill 是该 venue 已接受订单的唯一成交，price=100、qty=0.1、fee magnitude=0.01；Controller 不插入 Trade/Ledger。

## 最终实际运行

完整快照、wire event 序列、request/order/fill identity 和源码 Git blob 身份位于 [manifest](l4-b1-attempt-02/manifest.json)，进程命令/PID、容器身份、清理读回和测试结果位于 [observations](l4-b1-attempt-02/observations.txt)。每份 proof JSON 包含 preFault、atFault、beforeFill、finalDb、afterReplay、afterRestartReplay；LA 另有 afterDeath。

| Row/repeat | NQ A PID | 确认前死亡后的 recovery PID | 记账后 replay PID | Venue PID | PLACE | Order query / fills query | 结果 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AT-01/1 | 38032 | 不适用 | 46564 | 32992 | 1 | 3 / 4 | PASS |
| AT-01/2 | 73720 | 不适用 | 33512 | 37020 | 1 | 3 / 4 | PASS |
| AT-01/3 | 13148 | 不适用 | 63184 | 36016 | 1 | 3 / 4 | PASS |
| LA-02/1 | 15956 | 25456 | 76032 | 72296 | 1 | 2 / 4 | PASS |
| LA-02/2 | 21072 | 22128 | 62564 | 68232 | 1 | 2 / 4 | PASS |
| LA-02/3 | 31488 | 76268 | 49704 | 2172 | 1 | 2 / 4 | PASS |

每次 requestReceived=true、venueAccepted=true、responseDelivered=false。AT ackGenerated=false、caller timeout=true；LA ackGenerated=true、caller 在确认前死亡。六个 database identity 与六个 venue PID 均唯一。数据库为独立 PostgreSQL 16.15 容器，Flyway migrate/validate=V48，使用 canonical 锁定镜像和 `--pull=never`。

每次最终 local/venue=FILLED，Order=1、unique Trade=1、Ledger entries=4、Ledger events=4；本金 ±10、手续费 ±0.01，净额=0。Trade.order_id 与 local Order、exchange_trade_id 与唯一 venue fill 逐一匹配。Order version 不低于 SENT checkpoint；相同进程再次 RECOVER 和记账后新 JVM RECOVER 的 Order/Trade/Ledger/position/account 完整快照均不变。每次 blind retry=0、第二 PLACE=0。

Audit/Event 为实际 producer 生成的快照，包含 OrderCreated、RiskPassed、TradeExecuted、LedgerPosted 等事实；AT 的 OrderAck 是 query-confirm 产生的内部事件，不能当作丢失的网络 ACK 已送达。execution_intents=0、execution_receipts=0，没有建立第二执行事实源。

## 验证与证据检查

最终命令：

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am '-Dnq.b1=true' '-Dnq.b0=true' '-Dtest=B1RealProcessProofTest,B0FixtureSafetyTest,B0RealProcessHarnessTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

exit=0，10 tests / failures=0 / errors=0 / skips=0，总耗时 1:55。其中 B1 是一个 suite，内部六次独立 real-process 运行，不把 suite 数当作场景重复次数。B0 安全负例 8 项和完整进程 smoke/restart/fault/cleanup 同时通过。

Attempt-01：B1 六次均 PASS，耗时 85.58 秒；之后补强证据身份/ACK digest/坏证据检查，再运行最终 attempt-02。两次均没有 production correctness finding，未修 production 后掩盖失败。原始 ignored 日志为 `artifacts/b1-attempt-01.log`、`artifacts/b1-attempt-02.log`；最终可跟踪事实已复制到上述证据目录。

[B1 checker](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B1RealProcessProofTest.java) 从实际 venue events 和只读 DB snapshots 判定，不采信 verdict 字段。每 run 8 类变异全部拒绝，共 48 次：第二 PLACE、缺失 fault checkpoint、混用 DB identity、删除 Ledger 分录、替换 fill identity、伪造 response delivery、version 回退、删除 venue events。完整读取还要求两行各三份、run DB/venue 独立、资源清理完成。LA 的 ACK digest 从所存实际 bytes 重新计算。

证据文件 hash 定义为 UTF-8 文本的 CRLF→LF 规范化 SHA-256，manifest 自身不递归入 hash；适应仓库文本换行规则，不声称 raw-byte 冻结。源码身份是 Git 规范化 blob，可用 `git hash-object -- <path>` 读回。

## C1/C2 与边界

- `git diff --name-only 612c2f5887a2e6b3a8b3138d9ae9b193c20e298f HEAD -- 'backend/*/src/main/**'` 为空；L4PlanBlockerPostgresIntegrationTest、ReconciliationCursorPostgresIntegrationTest 与该 C2 accepted commit 也完全一致。本轮工作区变更仅 test/evidence。
- 因生产候选和这两项 fixture 未变，复用[C1/C2 已接受的 OCC、CANCELLED reconciliation、cursor/总 limit 证明](GATEAUDIT_PHASE6_L4_RUNTIME_CORRECTNESS_C2_STARVATION_REMEDIATION_ATTEMPT01.md)。没有重开 C1/C2，也没有把当前 CI 中 L4PlanBlocker 的 13 个 opt-in skips 记为新通过。B1 另外实际验证本链路版本不回退与重放事实稳定。
- Real exchange calls=0、exchange credential reads=0、LIVE=0、真实 PLACE/CANCEL/transfer/withdraw=0；仅使用公开容器镜像与 synthetic HTTP。GitHub CLI 使用其正常认证进行本次用户授权的 CI 操作，不将“交易凭证读取=0”扩张成未使用 GitHub 认证。
- 复用 B0 精确 loopback origin/socket allowlist、清空继承环境、隔离工作目录、无签名 transport、真实 RiskGate 和受限 SQL roles；DISENGAGED 仅为启动前 disposable fixture。保留 B0 的边界：不是 OS 级网络防火墙或任意 native socket 证明。
- 所有 NQ/Venue PID 已独立 Get-Process 读回不存在，Docker 按 `nq.b0.identity` 标签查询无残留。每次 fixture DROP 和容器删除成功，容器删除后读回 remaining=0。没有停止共享 Docker 服务或删除缓存镜像。
- 未修改 production/migration/.github/AGENTS/Skills/current authority；未调用真实 provider。未做独立 review、未运行完整 restart matrix 或 B2–B6；本轮按用户允许的 test-only SELF_REVIEWED 收尾。

Next action：`NQ-GATEAUDIT-PHASE6-L4-B2-CANCEL-FILL-RACE-AND-PER_FILL-ACCOUNTING`。

建议 commit：`test(l4): prove accepted timeout and lost ack recovery`。未获得也未执行本地 commit/push 授权。

收尾：7 份证据文件规范化 SHA-256 与 7 个源码 Git blob 均读回一致；`git status --short`、`git diff --check`、`git diff --stat`、`git diff --name-only` 已执行，untracked 文件另行逐项检查。总范围为 3 个测试文件与 9 个证据文件；普通 diff stat 仅列出两个已跟踪测试文件。新增文本 whitespace 检查通过。全库 doc links：checked=532、warnings=123、errors=0；warnings 均来自既有历史链接，没有修改这些历史文件。
