# GateY Minimal Live Pilot End-to-End — attempt-01

## 当前结论

`REVIEW ACCEPTED / READY TO COMMIT`（定向复核已接受 / 可提交）。本文件是 implementation→CI→deployment→pilot 的单一持续 evidence；当前只完成 source implementation、本地验证与 targeted review。CI、生产部署、credential JIT、OKX 调用和真实 pilot 均未执行。

```text
P0=0
P1=0
implementationCommit=UNCOMMITTED
exactHeadCi=NOT_RUN
productionDeployment=NOT_STARTED
operatorPilotParameters=NOT_PROVIDED
credentialJitReads=0
okxCalls=0
PLACE=0
CANCEL=0
transfer=0
withdraw=0
LIVE=DISABLED
kill=ENGAGED
```

## 实现范围

- 新增 V42 三表：`pilot_execution_leases`、`pilot_execution_lease_intents`、`pilot_execution_lease_events`；全局唯一 lease，单 lease 最多一个 PLACE/一个 CANCEL，lifecycle/optimistic version/append-only/immutability 均由 PostgreSQL 约束。
- 新增 `PilotExecutionLease` domain/port/JDBC/service；默认 kill 为 ENGAGED，只有 exact ACTIVE/CONSUMED lease 可短时 DISENGAGE，reason 精确绑定 lease ID。
- 复用既有 `LiveSessionStateMachine`，单一 authenticated OPERATOR 写 internal approval event，不新增治理状态；旧独立审批能力保留为可选扩展，不再是 minimal pilot hard gate。
- 新增七参数 operator command：exchange account、credential reference、instrument、side、limit price、quantity、configured max notional；程序只计算 notional，不选择任何交易值。
- 新增 default-off scoped OKX composition，只暴露 typed Spot LIMIT PLACE、query、optional CANCEL、order status 与 fills；无 raw endpoint、MARKET、batch、algo、margin、derivatives、transfer、withdraw 或 fallback。
- PLACE 先写 Order/ExecutionIntent、bind+consume lease、durable `SEND_STARTED`，随后最多调用一次 provider PLACE；UNKNOWN 只 query，不 retry PLACE。
- SEND_STARTED 前在同一数据库事务重读 session/kill/lease/account/credential/permission/IP/withdraw/instrument/fee/balance/clock/max-notional facts。
- crash/UNKNOWN 保留 CONSUMED lease并立即 re-engage kill；同参数重入只允许 query-first recovery。既有 trade 也会继续补做幂等 ledger posting，避免 trade insert 成功、ledger 失败后的恢复断点。
- control script 复用 root-owned runtime/secrets env、显式 allowlist 清除额外环境、canonical verifier、manifest identity 与 machine-id SHA-256 server identity；不输出 credential 或 machine-id 原文。

## V42 与数据库审查

- V1～V41 未修改；V42 使用 `SET LOCAL lock_timeout='5s'` 与 `statement_timeout='60s'`。
- 三张表及全部字段均有中文 COMMENT；不保存 credential material、raw provider response、header 或签名。
- PostgreSQL 17.7 随机 schema：4 tests，0 failures/errors/skips；覆盖 V39/V40 upgrade、fresh replay、Flyway validate、lock timeout、append-only/immutability、global single pilot 与 concurrent double PLACE exactly-one。
- 本机 `localhost:5432/nexus_quant` 的旧 V42 草稿为空表；仅本地重建最终全局唯一索引并将 Flyway checksum repair 为 `-1136714581`。未连接或修改生产数据库。

## 验证

| Command / check | Result |
| --- | --- |
| focused Java | PASS（通过）；29 tests，0 failures/errors/skips |
| PostgreSQL required smoke | PASS（通过）；LiveSession fact model 4/4，Flyway continuous 1/1 |
| full Maven | PASS（通过）；23 modules，0 failures/errors；既有条件性 skipped 保留 |
| GateY contracts | PASS（通过）；exact scope 7、minimal pilot 19、release 31、runtime deployment 51、GateY4/GateY5 |
| GateW frozen | PASS（通过）；37/37、12/12、34/34 |
| Java standard | PASS（通过）；release 21 / Spring Boot 3.5.10 |
| Java Shadow | `VIOLATION_FOUND`（仅 Shadow）；官方输出 `NEW_CODE_VIOLATION_COUNT=0`，existing baseline/ruleset expansion 保留 |
| `git diff --check` | PASS（通过）；仅 line-ending 提示，无 whitespace error |

执行历史：首次 focused Maven 因 PowerShell dotted `-D` 参数未加引号而未进入 lifecycle；加引号后通过。首次 PostgreSQL run 暴露一个旧独立审批预期断言，与本轮 single-operator simplification 冲突；精确更新该断言后 4/4 通过。首次本地索引重建在中文 COMMENT 处被 Windows psql encoding 拒绝，事务未确认；随后用 ASCII DDL 精确重建本地索引。主 worktree Shadow 被既有不可读 artifact 干扰，detached 临时 worktree 对同一 dirty source 扫描后确认 new-code=0并已删除。

## Targeted P0/P1 Review

- 第二单：global unique lease + `(lease_id, action)` PK + intent CAS + SEND_STARTED + no-place-retry；P0/P1=0。
- kill 永久开放：lease expiry/send-time re-read/startup/finally recovery + exact reason/session binding；P0/P1=0。
- scope/max-notional/MARKET 绕过：Order/Intent/Binding/Lease/DB observations exact compare，LIMIT-only typed provider；P0/P1=0。
- crash：PLACE 后不确定结果保留 CONSUMED recovery fact，kill 立即 ENGAGED，后续只 query；P0/P1=0。
- reconciliation：client/exchange order identity、price/qty/executed/remainder/fill/fee/final state、trade/ledger/audit 均强制；existing trade 可修复 ledger；P0/P1=0。

## 未完成 hard gates

1. 精确暂存、commit、push 与 exact-head CI GREEN。
2. 构建 V42/15-artifact immutable release并按既有 deployment contract 部署，验证 runtime exact-head、Flyway V42、health、LIVE=false、kill=ENGAGED。
3. operator 必须显式提供七项参数；当前缺失，因此 CI/deployment 后若仍未提供，结论必须为 `BLOCKED / OPERATOR_PILOT_PARAMETERS_REQUIRED`。
4. 只有参数齐全且 production prerequisite 全部 PASS 后，才允许 exactly-one real LIMIT；本文件当前绝不声明真实 pilot PASS。

推荐 commit：`feat(gatey): add crash-safe minimal live pilot execution`。
