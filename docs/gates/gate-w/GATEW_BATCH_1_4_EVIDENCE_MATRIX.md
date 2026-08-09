# GateW Evidence Matrix

以下表格汇总 GateW 主要 accepted baseline 与保留的失败路径；96 份不可覆盖 task evidence
的完整索引位于 [source/task-evidence/README.md](source/task-evidence/README.md)。失败、阻断、拒绝与 remediation attempt
均保留，不被最终 acceptance 覆盖。

| Batch / Route         | 结果                                 | Commit / Release                           | Exact-head CI                      | 范围与边界                                           |
|-----------------------|--------------------------------------|--------------------------------------------|------------------------------------|------------------------------------------------------|
| GateW-PLAN            | `ACCEPTED / CI_GREEN`                | `5661a13e236ce067edad9ae5789c97ae3ae2e7bb` | `29199785253`                      | 单 venue read-only 与 no-trading 计划                |
| GateW-1               | `ACCEPTED / CI_GREEN`                | `31c8171df26bc1eb9f93da19cf0576c0ac48116b` | `29219687588`                      | capability matrix / endpoint guard                   |
| GateW-2               | `ACCEPTED / CI_GREEN`                | `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553` | `29230512781`                      | typed private read-only；`REAL_SMOKE=NOT_RUN`        |
| GateW-3               | `ACCEPTED / CI_GREEN`                | `178b4951ba1406748170022c9940f84beaa8ab81` | `29332316101`                      | venue facts、preview、reconciliation、risk preflight |
| GateW-4               | `ACCEPTED / CI_GREEN`                | `07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c` | `29339016784`                      | durable safety、restore/incident 与 local soak       |
| Attempt-09            | `FAILED / ACCEPTANCE_REJECTED`       | historical evidence preserved              | historical CI/evidence             | 有效时长不足；失败与 incident review 不删除          |
| Attempt-10/11/12      | `FAILED / STOPPED`                   | 独立 immutable releases / RunIds           | 各自 exact-head CI 已归档          | 启动失败后 terminalize 并回滚；禁止复用              |
| Attempt-13 runtime    | `COMPLETED / ACCEPTED / SEALED`      | `b103069d8bfcecccba0b4d590317ddccc66898b9` | runtime release evidence preserved | 168h read-only，零交易写侧                           |
| Attempt-13 acceptance | `ACCEPTED / CI_GREEN / FREEZE_READY` | `20cf7970dfb414868da3e42dddaefc5965246570` | `31295184056`                      | 656 samples、hash chain、canonical seal              |
| Authority sync        | `PASS / CI_GREEN`                    | `9a90379196ce4fe0cefe3e737b354a5b94f27fa5` | `31295604792`                      | current authority 固定到 freeze closeout action      |
| Manifest remediation  | `PASS / CI_GREEN`                    | `ecd3b4397d51fd48260de2f7954df191541b101f` | `31298470955`                      | GateW strict override / 12 roles                     |

所有 acceptance 仅覆盖已列明能力和 evidence integrity，不产生 LIVE、private trading、资金划转、真实订单或 unattended
execution 授权。known residual 见 [限制清单](GATEW_KNOWN_LIMITATIONS_AND_RESIDUALS.md)。
