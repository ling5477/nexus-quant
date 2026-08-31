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
Phase4 remaining disposition closeout ACCEPTED / CI_GREEN
  ↓
7ca1fc92f8900e3e9d19184fccd40569f233823f / 33405549149
  ↓
NQ-GATEAUDIT-PHASE5A-CANONICAL-CI-AND-SUPPLY-CHAIN
```

## Phase4 accepted foundation

| Finding | Accepted pair | Current meaning |
| --- | --- | --- |
| F-001 | `95b859ee61a8e7f0a725e29877e7303ea4453b1a / 33347091147` | L3 causal order→fill→trade→ledger proof accepted |
| F-002 | `0651a7365d1a6afe453d75c8abd3975d458e0b7a / 33387882472` | Phase4 forked-JVM restart foundation accepted；不等于Phase6 full L4 |
| F-003 | `327c2229e89c076eace60046b79ec02c622a7fe4 / 33399190770` | Order/ExecutionIntent identity convergence accepted |
| F-004 | `18efc06c380d2b411ba7d5f651e7e441247a1b96 / 33358364678` | Trade/fill/ledger convergence与recovery identity accepted |
| Phase4 closeout | `7ca1fc92f8900e3e9d19184fccd40569f233823f / 33405549149` | Remaining disposition accepted；Phase4 complete，P0/P1=`0/0` |

## Capability disposition matrix

| Capability | Current State | Required Baseline | Disposition | Reason | Owner | Trigger | Phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F-013 current authority/current-doc consistency | canonical owners曾有10条stale claim | drift/stale/history-authority=`0/0/0` | `IMPLEMENT_NOW` | current authority correctness | `nq-docs-writer` / governance contract | Phase4 closeout | Phase4 |
| Phase4 proof foundation | F-001～F-004 accepted | four immutable pairs green | `NOT_REQUIRED` | 不重复实现或Review accepted proof | GateAUDIT | accepted pair失效时才重开 | Phase4 |
| Legacy Phase3 identifier F-005 | title/source/owner/consumer均不可恢复 | 不产生任何新语义或implementation mapping | `LEGACY_FINDING_IDENTITY_UNRECOVERABLE / RETIRED` | 保留历史ledger记录，但禁止Phase5继承或猜测未知语义 | Historical evidence only | 仅在找到可验证canonical source时重新审计identity | Retired |
| Legacy Phase3 identifier F-011 | title/source/owner/consumer均不可恢复 | 不产生任何新语义或implementation mapping | `LEGACY_FINDING_IDENTITY_UNRECOVERABLE / RETIRED` | 保留历史ledger记录，但禁止Phase5继承或猜测未知语义 | Historical evidence only | 仅在找到可验证canonical source时重新审计identity | Retired |
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

- 当前CI基线为11 jobs；Phase4 closeout exact-head run `33405549149`为`completed / success / 11 jobs / bad=0`。
- Legacy Phase3 IDs F-005/F-011的canonical source不可恢复，已退休；Phase5只使用以下inventory seed，不继承未知语义。
- Gate-specific release/deploy helpers只作为输入inventory；Phase5不得为兼容历史路径修改canonical implementation。
- 需要建立canonical deployment、minimum observability、immutable supply-chain pinning与selected E2E scope。
- LIVE保持`DISABLED`、kill switch保持`ENGAGED`；不读取credential、不触发真实provider。

### Phase5 finding seed

以下 finding 来自 `NQ-GATEAUDIT-PHASE5-CI-CD-DEPLOYMENT-HARDENING` 只读 inventory；状态均为 `OPEN / NOT_IMPLEMENTED`。

| ID | Severity | Finding | Evidence summary |
| --- | --- | --- | --- |
| P5-F001 | P1 | `CI_REQUIRED_CHECK_ENFORCEMENT_ABSENT` | GitHub只读查询确认`dev`与当前audit branch均无branch protection，repository ruleset为空 |
| P5-F002 | P1 | `CANONICAL_RELEASE_DEPLOYMENT_PATH_ABSENT` | `.github/workflows/ci.yml`不构建/验证release；GateW/GateY/freeze helper仅在legacy闭环内互调 |
| P5-F003 | P1 | `CURRENT_SCHEMA_RESTORE_NOT_PROVEN` | GateW restore止于V35、GateY restore止于V39，而current schema为V46 |
| P5-F004 | P2 | `SUPPLY_CHAIN_IDENTITIES_MUTABLE` | GitHub Actions使用major tags，PostgreSQL images使用mutable tags，gitleaks archive无checksum验证 |
| P5-F005 | P2 | `SBOM_PROVENANCE_ATTESTATION_ABSENT` | current CI/release无canonical SBOM、artifact attestation或exact successful CI provenance |
| P5-F006 | P2 | `CI_DUPLICATION_AND_CRITICAL_E2E_COVERAGE_GAP` | no-outbound/security与frontend build重复；blocking E2E未覆盖完整critical business subset |
| P5-F007 | P2 | `MINIMUM_OPERATIONAL_OBSERVABILITY_INCOMPLETE` | scheduler/worker、reconciliation、ledger recovery与critical alert缺统一运行观测 |
| P5-F008 | P2 | `PROD_CONFIGURATION_FAIL_CLOSED_GAP` | prod datasource仍有host/user/password fallback，缺失配置不会在配置解析期fail closed |
| P5-F009 | P2 | `LEGACY_GATE_SPECIFIC_ACTIVE_ASSET_DEBT` | active tree仍保留GateW/GateY/freeze release/deploy/systemd入口且无current canonical replacement |

## Phase6 deferred proofs

- accepted-timeout与lost ACK；
- cancel/fill race与partial-fill continuation；
- kill-in-flight、external side effect + DB failure；
- multi-instance/lease/duplicate worker；
- L5/L6 scale、chaos与长期qualification。

这些项目在Phase5 baseline接受前均不得写成已实现或已证明。

## 下一允许动作

- Phase4 immutable acceptance pair=`7ca1fc92f8900e3e9d19184fccd40569f233823f / 33405549149`；不得由本次authority reconciliation commit/CI替代。
- 当前workstream：`NQ-GATEAUDIT-PHASE5A-CANONICAL-CI-AND-SUPPLY-CHAIN`，状态=`READY_TO_START / NOT_IMPLEMENTED`。
- machine next action：`NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-IMPLEMENTATION`，matcher type=`IMPLEMENTATION`。Phase5 implementation必须另行限定batch、验证与rollback。

## Persistent boundary

- `LIVE=DISABLED`、kill switch=`ENGAGED`；禁止再次pilot、PLACE、CANCEL、transfer、withdraw或credential/生产服务器/生产数据库访问。
- GateY frozen archive与published tags不可改写。
- 本次authority reconciliation不实施Phase5/Phase6 capability。
