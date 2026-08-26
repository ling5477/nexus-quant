# GateY 冻结证据归档入口

本目录是 GateY `OKX Spot / BTC-USDT / BUY LIMIT / <= 10 USDT` 单次人工受控最小实盘 pilot 的 strict pre-tag archive。本归档保留 GateY-1～6F、全部 BLOCKED / FAIL / remediation、V43～V46、credential correction、trusted bootstrap、release reproducibility、non-web security、operator authority、lease recovery、canonical legacy bridge、最终真实订单与 reconciliation 证据；它是 historical evidence，不覆盖 `docs/current/STATUS.md` 的 current authority。

## Release handoff

- archive starting HEAD：`65caaf7fd3038658b0f4f24566efd2960e606d43`。
- starting exact-head CI：`NQ CI Baseline` run `32981327378`，`completed / success / 10 jobs / bad=0`。
- production pilot release：`8e3dd0cf6104eb85f36a0e434ca51ea9d903705a`；release manifest SHA-256=`d49ca03a39df8e7de15a2bb03651381ce4c1df8db1682d63e285fdd37b61e046`。
- pilot final：PLACE=1、PLACE retry=0、CANCEL=0、Trade=1、Ledger entries=4、activeLease=0、LIVE=false、kill=`ENGAGED`。
- canonical annotated tag：`nq-gatey-freeze`；pre-tag 阶段为 `TAG PENDING`（tag 待创建），只能在真实 freeze commit 的 exact-head CI 全绿后创建并推送。
- freeze commit、tag object、peeled commit 与 remote tag 结果由后续真实 Git/CI 事实确定；本归档不预言自身 commit SHA 或未来 tag object。

## 归档导航

- [冻结 closeout](GATEY_FREEZE_CLOSEOUT.md)
- [冻结 readiness review](GATEY_FREEZE_READINESS_REVIEW.md)
- [GateY 实施计划](GATEY_PLAN.md)
- [GateY-1 数据模型 work order](GATEY_1_LIVE_SESSION_DATA_MODEL_WORK_ORDER.md)
- [GateY-6 micro-live work order](GATEY_6_EXPLICIT_MICRO_LIVE_AUTHORIZATION_WORK_ORDER.md)
- [GateY batch evidence matrix](GATEY_BATCH_1_6_EVIDENCE_MATRIX.md)
- [测试与 CI 证据](GATEY_TESTING_EVIDENCE_SUMMARY.md)
- [后端、DB 与 migration 证据](GATEY_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md)
- [API 证据](GATEY_API_EVIDENCE_SUMMARY.md)
- [前端证据](GATEY_FRONTEND_EVIDENCE_SUMMARY.md)
- [runtime 与 scheduling 边界](GATEY_RUNTIME_SCHEDULING_BOUNDARY_SUMMARY.md)
- [部署证据](GATEY_DEPLOYMENT_EVIDENCE_SUMMARY.md)
- [最小实盘 pilot 证据](GATEY_MINIMAL_LIVE_PILOT_EVIDENCE_SUMMARY.md)
- [边界声明](GATEY_BOUNDARY_STATEMENT.md)
- [已知限制与 residual](GATEY_KNOWN_LIMITATIONS_AND_RESIDUALS.md)
- [全部 task evidence 索引](source/task-evidence/README.md)

本卷覆盖 manifest 的 8 个 mandatory roles，以及 GateY strict override 要求的 backend/DB、API、frontend、runtime、deployment、minimal-live-pilot 和两份 work-order roles。`source/task-evidence/**` 保存不可覆盖的 PASS / FAIL / BLOCKED / retry / remediation 历史与两份 sanitized JSON manifest，不参与 archive role 计数。

Pre-tag verification 已通过：16 个 required roles 独立，75 份 source evidence 有效，archive warnings/errors=`0/0`；current authority errors=0；document links `404 checked / 128 historical warnings / 0 errors`；governance lifecycle/next-action/archive-manifest regressions均通过；GateY frozen regressions=`7/100/31/51 + GateY4 + GateY5`，GateW frozen regressions=`37/12/34`；custom secret backstop=`103 files / 0 findings`。这些结果只授权形成 freeze commit，tag 仍须等待该 commit 的 exact-head CI。

本 archive 不授权第二笔 pilot、通用 LIVE、自动策略实盘、多账户、多交易所、合约/杠杆、transfer/withdraw、AI/DH trading 或无人值守运行。tag 推送后禁止删除、移动、覆盖或 force update；后续只能使用 forward addendum、hotfix 或 superseding tag。
