# GateAUDIT runtime and scheduling evidence

Canonical runtime 由 Java scheduler、reconciliation、recovery、risk、execution、ledger 与 observability modules 组成；accepted scope 由 [Phase6 final acceptance](../../audit/evidence/GATEAUDIT_PHASE6_L5_L6_FINAL_ACCEPTANCE.md) 和 [F007 observability acceptance](../../audit/evidence/GATEAUDIT_PHASE5_F007_POST_CI_AUTHORITY_ACCEPTANCE.md) 拥有。

## Accepted bounded runtime scope

- Phase5 F007 接受 scheduler/worker、reconciliation、ledger recovery 与 critical alert 的最小 operational observability。
- Phase6 L4 接受 real-process B0–B6 correctness、restart/recovery、terminal convergence、durable event、kill/admission 与 aggregate matrix。
- Phase6 L5 接受 bounded concurrency/backlog、repeated fault、same-fill/accounting correctness 与 Kill-under-load；ordinary INSERT loser residual 保持 P2/non-blocking。
- L6-A 接受 `60min / 10-40-10 / 360 of 360`；L6-B 接受 `180min / 10-160-10 / 1080 of 1080`、3 restart、12 continuity 与 88 snapshot replay。
- Historical failures、partial durations、remediation 与 5421ms cause=`UNKNOWN` 保持 append-only；后续成功不追认旧 attempt 为 PASS。
- Kill switch 当前为 `ENGAGED`，LIVE=`DISABLED`，shadow trading=`NOT_ENABLED`。

Phase7-D 后的唯一 runtime delta 是 [logging sensitive-data protection](../../audit/evidence/GATEAUDIT_LOGGING_SENSITIVE_DATA_PROTECTION_IMPLEMENTATION.md)：初始 [negative proof](../../audit/evidence/GATEAUDIT_LOGGING_SENSITIVE_DATA_NEGATIVE_PROOF_AUDIT.md) 在 prod console 最终输出发现 13/13 synthetic canary；修复后复验 0/13，technical pair=`48c1b1cd4c84e1429be82093e460984b6d1c805c / 35817828506`，authority pair=`2b565eca6e9e34f6faf856ccd05f49b43f37ed7b / 35820007679`。这只关闭敏感输出边界，不声称 JSON logging、rotation、retention、async MDC 或完整日志平台能力已实现；没有新增 scheduler、reconciliation、recovery 或 trading mutation path。

## Scheduling and recovery boundary

已接受的 scheduling/reconciliation/recovery 仅覆盖 evidence 中明确的 bounded入口、负载、duration、fault 与 restart。它不表示通用 LIVE automation、unattended real trading、无限 backlog、高可用、多 venue、多账户或超出 L5/L6 envelope 的长期运行已证明。

Future scheduler/lease/leader、Venue 自身 restart persistence、扩展规模/长期/故障组合继续为 `FUTURE_OBLIGATION`。新入口只有在 owner、scope、authority 与 proof 同时建立后才能重新 qualification；不得为 coverage 复活 retired/dormant path。

Phase7-D 不启动 scheduler、worker、backend runtime、database container 或 exchange transport，不执行 qualification 或生产 mutation。Archive/checker/CI 只验证 repository candidate，不改变运行时安全状态。
