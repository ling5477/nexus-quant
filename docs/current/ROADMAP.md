# Roadmap

本文件只定义下一允许动作与已验证的后续输入。current Gate、安全状态和work batch必须解析 [STATUS.md](STATUS.md) 的 `nq-current-authority` 区块。

## 当前路线

```text
GateY FROZEN / ACCEPTED / TAGGED
  ↓
GateAUDIT Phase 0 ACCEPTED / CI_GREEN / COMPLETE
  ↓
Phase 1 inventory + Phase 2 AS-IS + Phase 3 disposition COMPLETE
  ↓
F-001 / F-002 foundation / F-003 / F-004 ACCEPTED / CI_GREEN
  ↓
Phase4 remaining disposition closeout REVIEW_ACCEPTED / READY_TO_COMMIT
  ↓
NQ-GATEAUDIT-PHASE4-REMAINING-DISPOSITION-AND-CONSOLIDATION-COMMIT
  ↓
closeout commit + exact-head CI
  ↓
NQ-GATEAUDIT-PHASE5-CI-CD-DEPLOYMENT-HARDENING
```

## Phase4 accepted foundation

| Finding | Accepted pair | Current meaning |
| --- | --- | --- |
| F-001 | `95b859ee61a8e7f0a725e29877e7303ea4453b1a / 33347091147` | L3 causal order→fill→trade→ledger proof accepted |
| F-002 | `0651a7365d1a6afe453d75c8abd3975d458e0b7a / 33387882472` | Phase4 forked-JVM restart foundation accepted；不等于Phase6 full L4 |
| F-003 | `327c2229e89c076eace60046b79ec02c622a7fe4 / 33399190770` | Order/ExecutionIntent identity convergence accepted |
| F-004 | `18efc06c380d2b411ba7d5f651e7e441247a1b96 / 33358364678` | Trade/fill/ledger convergence与recovery identity accepted |

## Capability disposition matrix

| Capability | Current State | Required Baseline | Disposition | Reason | Owner | Trigger | Phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F-013 current authority/current-doc consistency | canonical owners曾有10条stale claim | drift/stale/history-authority=`0/0/0` | `IMPLEMENT_NOW` | current authority correctness | `nq-docs-writer` / governance contract | Phase4 closeout | Phase4 |
| Phase4 proof foundation | F-001～F-004 accepted | four immutable pairs green | `NOT_REQUIRED` | 不重复实现或Review accepted proof | GateAUDIT | accepted pair失效时才重开 | Phase4 |
| F-005 Phase3 deferred finding | current ledger仅保留`P2 / DEFERRED`标识，canonical semantic title/source mapping缺失 | 恢复原finding title、evidence path、active consumer与owner后才能设计 | `DEFER_UNTIL_TRIGGER` | 定义不完整时禁止推断、实现、删除或宣称关闭 | Phase5 entry audit owner | `NQ-GATEAUDIT-PHASE5-CI-CD-DEPLOYMENT-HARDENING`开始后、首个implementation commit前完成source mapping | Phase5 entry |
| F-011 Phase3 deferred finding | current ledger仅保留`P2 / DEFERRED`标识，canonical semantic title/source mapping缺失 | 恢复原finding title、evidence path、active consumer与owner后才能设计 | `DEFER_UNTIL_TRIGGER` | 定义不完整时禁止推断、实现、删除或宣称关闭 | Phase5 entry audit owner | `NQ-GATEAUDIT-PHASE5-CI-CD-DEPLOYMENT-HARDENING`开始后、首个implementation commit前完成source mapping | Phase5 entry |
| Gate-specific GateW/GateY release/deploy helpers | 默认不运行；仍有脚本间caller和rollback/release引用 | canonical deployment baseline | `DEFER_UNTIL_TRIGGER` | 当前删除会破坏cross-script contracts | Phase5 deployment owner | Phase5 canonical deployment rebuild开始且replacement通过回归 | Phase5 |
| Historical plans与attempt evidence | 明确non-authoritative、保留append-only链接 | history不得参与authority/runtime | `NOT_REQUIRED` | 已通过fact-source分类隔离，不改写历史正文 | docs/archive owners | 仅引用迁移有独立授权时 | Historical |
| Supply-chain immutable action pinning | actions仍以major tag引用并产生deprecation warning | immutable action/SBOM/provenance baseline | `IMPLEMENT_LATER` | 属CI/CD hardening，不是Phase4 correctness blocker | Phase5 CI owner | Phase5任务启动 | Phase5 |
| Canonical deployment rebuild | Gate-specific tooling仍分散 | single canonical deploy/release path | `IMPLEMENT_LATER` | 本轮禁止deployment rebuild | Phase5 deployment owner | Phase5任务启动 | Phase5 |
| Minimum observability baseline | 当前有Actuator/log/audit局部能力 | deployment health/log/metric/alert minimum | `IMPLEMENT_LATER` | 需绑定canonical deployment | Phase5 observability owner | canonical deployment candidate存在 | Phase5 |
| Selected frontend E2E expansion | 现有build、no-backend与adapter readiness E2E green | Phase5 selected critical-flow matrix | `IMPLEMENT_LATER` | 当前无frontend behavior diff | Phase5 frontend QA owner | Phase5 test-scope确定 | Phase5 |
| accepted-timeout / lost ACK / cancel-fill race / kill-in-flight / external-side-effect+DB failure / multi-instance lease | 尚未证明 | real-process deterministic L4 matrix | `PROVE_FIRST` | F-002只接受restart foundation | Phase6 qualification owner | Phase5 deployment+observability baseline accepted | Phase6 L4 |
| L5/L6 scale、chaos与长期qualification | 未执行 | Phase6 L4 accepted | `PROVE_FIRST` | 不得越过L4直接实现 | Phase6 qualification owner | L4 accepted后 | Phase6 L5/L6 |
| 第二pilot、通用LIVE、真实交易扩展、transfer/withdraw | 未授权 | explicit future authority + safety review | `REJECT` | 超出GateAUDIT与Phase4/5/6 proof边界 | future trading governance | 新任务与显式授权同时存在 | Not scheduled |

## Phase5 inputs

- 当前CI基线为11 jobs且F003 exact-head run `33399190770`全绿。
- Phase5 entry audit必须先恢复F-005/F-011原finding定义与source mapping；定义恢复前不得把它们映射到任一implementation row。
- Gate-specific release/deploy helpers只作为输入inventory；Phase5不得为兼容历史路径修改canonical implementation。
- 需要建立canonical deployment、minimum observability、immutable supply-chain pinning与selected E2E scope。
- LIVE保持`DISABLED`、kill switch保持`ENGAGED`；不读取credential、不触发真实provider。

## Phase6 deferred proofs

- accepted-timeout与lost ACK；
- cancel/fill race与partial-fill continuation；
- kill-in-flight、external side effect + DB failure；
- multi-instance/lease/duplicate worker；
- L5/L6 scale、chaos与长期qualification。

这些项目在Phase5 baseline接受前均不得写成已实现或已证明。

## 下一允许动作

- 当前唯一动作：`NQ-GATEAUDIT-PHASE4-REMAINING-DISPOSITION-AND-CONSOLIDATION-COMMIT`，matcher type=`COMMIT`。
- 只提交review接受的closeout candidate并取得exact-head CI；随后进入 `NQ-GATEAUDIT-PHASE5-CI-CD-DEPLOYMENT-HARDENING`。

## Persistent boundary

- `LIVE=DISABLED`、kill switch=`ENGAGED`；禁止再次pilot、PLACE、CANCEL、transfer、withdraw或credential/生产服务器/生产数据库访问。
- GateY frozen archive与published tags不可改写。
- Phase4 closeout不实施Phase5/Phase6 capability。
