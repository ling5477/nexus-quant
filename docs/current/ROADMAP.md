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
  ↓
d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903 ACCEPTED / CI_GREEN
  ↓
NQ-GATEAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE
  ↓
a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848 ACCEPTED / CI_GREEN
  ↓
GateAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED ACCEPTED / CLOSED
614359fc7f25227f736fbb1c11c7d584da1f0627 / 33978394774 ACCEPTED / CI_GREEN
  ↓
GateAUDIT-PHASE5-F007-MINIMUM-OPERATIONAL-OBSERVABILITY ACCEPTED / CLOSED
0e2efdeb236c185dbace67bb22f94c6af64a563a / 34009290836 ACCEPTED / CI_GREEN
  ↓
GateAUDIT-PHASE5-F009-LEGACY-GATE-SPECIFIC-ACTIVE-ASSET-CONSOLIDATION ACCEPTED / CLOSED
dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455 ACCEPTED / CI_GREEN
  ↓
P5-F001 ACCEPTED / CLOSED / ruleset 22381941 / refs/heads/dev / effective 9/9
  ↓
Phase5 ACCEPTED / CLOSED / remaining blocking 0 / F005 DEFERRED NON_BLOCKING
  ↓
Phase6 IN_PROGRESS / NOT_FROZEN
  ↓
L4 plan ACCEPTED / CI_GREEN
d79408228ce31c97802afbb674eb2e3d0a2e7bfd / 34071672665
  ↓
C1(P1-2) ACCEPTED / CI_GREEN / CLOSED
  ↓
C2(P1-3) ACCEPTED / CI_GREEN / CLOSED
  ↓
Phase6 L4 ACCEPTED / B0–B5 ACCEPTED / B6 AGGREGATE_ACCEPTED
3d103cea2072b3c2d9d1009cc5841c18a958ee80 / 34501806297 / 9 of 9 SUCCESS
  ↓
Phase6 L5/L6 NEXT / NOT_STARTED
  ↓
NQ-GATEAUDIT-PHASE6-L5-L6-SCOPE-AND-QUALIFICATION-PLAN
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
| F-013 current authority/current-doc consistency | canonical owners曾有10条stale claim | drift/stale/history-authority=`0/0/0` | `IMPLEMENT_NOW` | current authority correctness | current docs owner / governance contract | Phase4 closeout | Phase4 |
| Phase4 proof foundation | F-001～F-004 accepted | four immutable pairs green | `NOT_REQUIRED` | 不重复实现或Review accepted proof | GateAUDIT | accepted pair失效时才重开 | Phase4 |
| Legacy Phase3 identifier F-005 | title/source/owner/consumer均不可恢复 | 不产生任何新语义或implementation mapping | `LEGACY_FINDING_IDENTITY_UNRECOVERABLE / RETIRED` | 保留历史ledger记录，但禁止Phase5继承或猜测未知语义 | Historical evidence only | 仅在找到可验证canonical source时重新审计identity | Retired |
| Legacy Phase3 identifier F-011 | title/source/owner/consumer均不可恢复 | 不产生任何新语义或implementation mapping | `LEGACY_FINDING_IDENTITY_UNRECOVERABLE / RETIRED` | 保留历史ledger记录，但禁止Phase5继承或猜测未知语义 | Historical evidence only | 仅在找到可验证canonical source时重新审计identity | Retired |
| All historical-stage active runtime assets | legacy entrypoints已退役；capability callers已迁移；兼容合同逐项保留 | canonical release/deploy/restore与稳定runtime mode | `ACCEPTED / CLOSED` | F009 local review与最终9/9 exact-head CI已接受；不改变accepted deployment语义 | Phase5 legacy consolidation owner | 仅当accepted technical pair失效时重开 | Phase5 |
| Historical plans与attempt evidence | 明确non-authoritative、保留append-only链接 | history不得参与authority/runtime | `NOT_REQUIRED` | 已通过fact-source分类隔离，不改写历史正文 | docs/archive owners | 仅引用迁移有独立授权时 | Historical |
| Supply-chain immutable action pinning | 24处Actions、工具consumer与3处PostgreSQL image已由lock和exact-head CI验证 | immutable action/SBOM/provenance baseline | `NOT_REQUIRED` | Phase5A baseline已接受，不重复实现 | Phase5 CI owner | acceptance pair失效时才重开 | Phase5A |
| Canonical deployment rebuild | immutable pair=`a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848` | single canonical deploy/release/restore path | `NOT_REQUIRED` | P5-F002与P5-F003已由9/9 exact-head CI接受并关闭 | Phase5 deployment/recovery owner | acceptance pair失效时才重开 | Phase5B |
| Minimum observability baseline | F007最小运行观测已由immutable technical pair接受 | deployment health/log/metric/alert minimum | `NOT_REQUIRED` | F007已ACCEPTED / CLOSED，不重复实现 | Phase5 observability owner | accepted pair失效时才重开 | Phase5 |
| Selected frontend E2E expansion | Phase5A historical pair接受`5 specs / 20 cases`；Phase5B current baseline=`5 specs / 27 cases` | Phase5 selected critical-flow matrix | `NOT_REQUIRED` | 当前loopback=`25/25`、real-backend=`2/2`、skip/fixme=`0/0`，不重复扩展 | Phase5 frontend QA owner | acceptance pair或critical scope失效时才重开 | Phase5A/Phase5B |
| Current eligible L4 correctness obligations | 28/28 accepted；missing=0、invalid=0 | 最终technical SHA及B6 aggregate evidence | `ACCEPTED` | B0–B5与B6聚合均已接受；inactive/future不计PASS | Phase6 qualification owner | 不重开L4 | Phase6 L4 |
| L5/L6 scale、故障与长期运行验证 | NEXT / NOT_STARTED | Phase6 L4 ACCEPTED | `PLAN_NEXT` | 先解析真实scope、测试边界与exit criteria | Phase6 qualification owner | 下一正式scope与qualification计划 | Phase6 L5/L6 |
| 第二pilot、通用LIVE、真实交易扩展、transfer/withdraw | 未授权 | explicit future authority + safety review | `REJECT` | 超出GateAUDIT与Phase4/5/6 proof边界 | future trading governance | 新任务与显式授权同时存在 | Not scheduled |

## Phase5 inputs

- 当前accepted CI基线为9 jobs；Phase5A exact-head pair=`d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903`，run=`completed / success / 9 of 9 / failed 0 / skipped 0`。
- Legacy Phase3 IDs F-005/F-011的canonical source不可恢复，已退休；Phase5只使用以下inventory seed，不继承未知语义。
- Gate-specific release/deploy helpers只作为输入inventory；Phase5不得为兼容历史路径修改canonical implementation。
- Phase5A已建立immutable supply-chain pinning、internal SBOM/provenance与selected E2E scope；Phase5B canonical deployment与current-schema backup/restore已由`a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848`接受。当前critical E2E baseline=`5 specs / 27 cases`；P5-F008已由`614359fc7f25227f736fbb1c11c7d584da1f0627 / 33978394774`接受并关闭，P5-F007已由`0e2efdeb236c185dbace67bb22f94c6af64a563a / 34009290836`接受并关闭，F009已由`dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455`接受并关闭；F001远端enforcement已由ruleset `22381941 / refs/heads/dev / effective 9/9`接受并关闭，F005 platform attestation仍非阻断延期。
- LIVE保持`DISABLED`、kill switch保持`ENGAGED`；不读取credential、不触发真实provider。

### Phase5 finding seed

以下 finding 来自 `NQ-GATEAUDIT-PHASE5-CI-CD-DEPLOYMENT-HARDENING` 只读 inventory；Phase5A已接受F001 local baseline与F004/F005/F006，Phase5B已接受并关闭F002/F003，F008已通过独立Final Closure Review与9/9 exact-head CI，正式`ACCEPTED / CLOSED`，F007与F009均已正式`ACCEPTED / CLOSED`。F001已通过本轮post-remote readback正式ACCEPTED/CLOSED；F005保留internal acceptance与明确非阻断延期。

| ID | Severity | Finding | Status | Evidence summary |
| --- | --- | --- | --- | --- |
| P5-F001 | P1 | `CI_REQUIRED_CHECK_ENFORCEMENT_ABSENT` | `ACCEPTED / CLOSED` | Local canonical baseline已接受；remote ruleset=`22381941`、target=`refs/heads/dev`、ACTIVE/effective checks=`9/9`，missing/unexpected/duplicate=`0/0/0`，app=15368；本轮GET readback无漂移，详见[F001 post-remote acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F001_POST_REMOTE_AUTHORITY_ACCEPTANCE.md) |
| P5-F002 | P1 | `CANONICAL_RELEASE_DEPLOYMENT_PATH_ABSENT` | `ACCEPTED / CLOSED` | Exact-head `33615809848`接受COMMITTED_CLEAN source、external admission、immutable install、atomic activation、authorized rollback/recovery与跨进程单一authority chain |
| P5-F003 | P1 | `CURRENT_SCHEMA_RESTORE_NOT_PROVEN` | `ACCEPTED / CLOSED` | PostgreSQL 16.15 server/pg_dump/pg_restore完成V1→V46、backup integrity/restore、Flyway pending=0、canary、repository/app-context smoke；PG17 wrong-major拒绝；exact-head CI接受 |
| P5-F004 | P2 | `SUPPLY_CHAIN_IDENTITIES_MUTABLE` | `ACCEPTED / CLOSED` | lock对24处Actions、gitleaks/CycloneDX真实consumer与3处PostgreSQL image双向enforce；exact-head CI已验证digest pull、consumer与fail-closed contracts |
| P5-F005 | P2 | `SBOM_PROVENANCE_ATTESTATION_ABSENT` | `INTERNAL_SBOM_PROVENANCE_ACCEPTED`；`DEFERRED / NON_BLOCKING` | backend/frontend artifact、SBOM、manifest与provenance已完成pre-upload admission、upload与post-upload readback；platform attestation继续`DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION` |
| P5-F006 | P2 | `CI_DUPLICATION_AND_CRITICAL_E2E_COVERAGE_GAP` | `ACCEPTED / CLOSED` | Phase5A historical baseline=`5 specs / 20 cases`；最新Phase5B accepted baseline=`5 specs / 27 cases`，loopback 25/25、real-backend 2/2，两个NoSkip reporter均PASS |
| P5-F007 | P2 | `MINIMUM_OPERATIONAL_OBSERVABILITY_INCOMPLETE` | `ACCEPTED / CLOSED` | implementation/accepted technical head=`0e2efdeb236c185dbace67bb22f94c6af64a563a`，exact-head CI=`34009290836 / SUCCESS / 9 of 9 / failed 0 / skipped 0`，observability tests=10/10、Full Maven=1783 tests/0 failures/0 errors/53 conditional test skips，P0/P1=0/0；详见[post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F007_POST_CI_AUTHORITY_ACCEPTANCE.md) |
| P5-F008 | P2 | `PROD_CONFIGURATION_FAIL_CLOSED_GAP` | `ACCEPTED / CLOSED` | Final Closure Review=`PASS / P0_0 / P1_0`；implementation=`716199a7cb836a5eaf43a88b0de6db0f47a75e91`；accepted technical head=`614359fc7f25227f736fbb1c11c7d584da1f0627`，exact-head CI=`33978394774 / SUCCESS / 9 of 9`；mandatory Maven、YAML semantic validator、135/135 mutations拒绝及R06/R09/R10拒绝链通过 |
| P5-F009 | P2 | `LEGACY_GATE_SPECIFIC_ACTIVE_ASSET_DEBT` | `ACCEPTED / CLOSED` | Local review CLOSED / P0_0 / P1_0，三项P1均CLOSED；accepted technical pair=`dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455`，9/9 jobs success、failed/skipped/cancelled=0；首次失败delivery与remediation链见[F009 post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F009_POST_CI_AUTHORITY_ACCEPTANCE.md) |

### Phase5 closure reconciliation

- Total=9；完整accepted/closed=8（F001/F002/F003/F004/F006/F007/F008/F009）；open/unclosed=1，仅F005 deferred/non-blocking；blocked=0，非延期open=0，remaining blocking=0。Deferred是open/unclosed的子集，不重复计数；互斥口径为8 closed + 1 deferred = 9。
- P5-F001 remote enforcement已接受；原P1 finding关闭，不改其historical severity。F005保留`INTERNAL_SBOM_PROVENANCE_ACCEPTED`，platform attestation=`DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION / NON_BLOCKING / id-token NOT_GRANTED`，未自动关闭或授予权限。
- Closure依据：既有本节要求remaining blocking=0，F005此前已明确非阻断延期；current machine lifecycle只约束工作状态/next-action，未规定所有deferred finding必须关闭。Phase5A/Phase5B与F007/F008/F009 accepted prerequisites均已具备，未发现其他Phase5 closure prerequisite；不修改lifecycle定义。
- Phase5=`ACCEPTED / CLOSED`；逐项accepted evidence与remaining action见[F001 post-remote acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F001_POST_REMOTE_AUTHORITY_ACCEPTANCE.md)。F005 remains deferred as explicitly non-blocking follow-on work.

## Phase6 L4 accepted / L5-L6 next

Phase6=`IN_PROGRESS / NOT_FROZEN`，L4=`ACCEPTED`；B0–B5=`ACCEPTED`，B6=`AGGREGATE_ACCEPTED`。最终technical pair=`3d103cea2072b3c2d9d1009cc5841c18a958ee80 / 34501806297 / 9 of 9 SUCCESS`，current eligible matrix=`28/28 / missing=0 / invalid=0`，P0=0、P1=0；接受依据为[B6 aggregate evidence](../audit/evidence/GATEAUDIT_PHASE6_L4_B6_AGGREGATE_QUALIFICATION_ACCEPTANCE.md)，不由本次docs-only同步替换。P2 ordinary concurrent INSERT loser与P3 wildcard-import residual保持`OPEN / NON_BLOCKING`。

Phase6 L5/L6=`NEXT / NOT_STARTED`，目标为后续规模、故障与长期运行验证。下一正式任务先从当前仓库及既有Phase6 planning evidence解析L5/L6真实scope、规模/故障/soak边界、复用能力、新实现需求与exit criteria；不创造L5-1、L5-2或L6-1，不重开L4。

| Observation | Current disposition | Current blocker / owner | Boundary |
| --- | --- | --- | --- |
| P1-2 stale PLACE/CANCEL ACK | REMEDIATED / REVIEWED / CI_GREEN / CLOSED | CLOSED / C1 | durable generation与atomic status/version CAS拒绝旧ACK与ABA，保留较新terminal；关闭绑定implementation、独立review及exact-head CI |
| P1-3 CANCELLED reconciliation | ACCEPTED / CI_GREEN / CLOSED | CLOSED / C2 | immutable pair=`612c2f5887a2e6b3a8b3138d9ae9b193c20e298f / 34183851797`；不重开实现或 PostgreSQL 验证 |
| P1-1 typed terminal mapping | RETIRED_COMPATIBILITY_ONLY | NON_BLOCKING_FOR_CURRENT_CANONICAL_RUNTIME | 历史缺陷观察保留，未宣称修复或false positive |
| PB1 retained pilot recovery | RETIRED_COMPATIBILITY_ONLY | NON_BLOCKING_FOR_CURRENT_CANONICAL_RUNTIME | 不把普通kill恢复positive control误作PB1缺陷复现 |
| PB2 sender dispatch | DORMANT_NO_CURRENT_ENTRYPOINT | NON_BLOCKING_FOR_CURRENT_CANONICAL_RUNTIME | 当前无sender入口，历史观察保留 |

Future trigger：对应retired/dormant路径再次成为canonical时，重新运行R1–R4 reachability，不能自动恢复blocking finding或新增compatibility caller。历史失败pair=`378de657ac33b9f9fd666288d489181ac0147b2e / 34038345304`保持FAILED DELIVERY；接受的是后续remediation pair，详见[authority acceptance evidence](../audit/evidence/GATEAUDIT_PHASE6_L4_PLAN_POST_CI_AUTHORITY_TRANSITION_TO_C1.md)。

当前依赖为 **L4 ACCEPTED → L5/L6 scope与qualification计划**；14个历史inactive scenario继续保留future obligation，不算PASS或静默skip。

C1接受身份：implementation=`41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd`；既有独立review=`PASS / PHASE6_L4_C1_VERSIONED_OCC_INDEPENDENT_REVIEW_ACCEPTED / P0_0 / P1_0 / READY_FOR_C1_DELIVERY`；acceptance head=`eb9740b7519f48ffc1e32968cbb0950261b871ef`、CI=`34098902705 / 9/9 SUCCESS`。首次delivery=`41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd / 34086018265 = FAILED DELIVERY`另行保留；完整证据见[C1 authority acceptance](../audit/evidence/GATEAUDIT_PHASE6_L4_C1_POST_CI_AUTHORITY_TRANSITION_TO_C2.md)。C1历史接受身份不由后续文档同步替代。C2已由`612c2f5887a2e6b3a8b3138d9ae9b193c20e298f / 34183851797`接受关闭，本轮不重复其实现或正确性审查。

交付历史68/68=3个known-defect reproduction PASS + 1个kill-engaged normal regression PASS + 64个既有回归PASS。该历史PASS只证明当时两个P1成功复现和既有行为保留，不是L4 qualification acceptance；当前C1/C2已分别接受，旧P1-3 reproduction描述仅属于历史交付，不作为当前开放缺陷；本轮不重跑这些测试。

## 下一允许动作

- Phase5B immutable technical acceptance pair=`a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848`；不得由本次current-fact synchronization commit或其CI替代。
- F008 immutable technical acceptance pair=`614359fc7f25227f736fbb1c11c7d584da1f0627 / 33978394774`；implementation=`716199a7cb836a5eaf43a88b0de6db0f47a75e91`，后续head属于`accepted CI test-harness compatibility remediation`，不是新的implementation finding。详见[post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F008_POST_CI_AUTHORITY_ACCEPTANCE.md)。
- F007 immutable technical acceptance pair=`0e2efdeb236c185dbace67bb22f94c6af64a563a / 34009290836`；implementation与accepted technical head相同，不由本轮docs-only authority commit替代。
- F009 immutable technical acceptance pair=`dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455`；首次delivery=`85d11984d0c65b464ffe4858fe7fd1da51885f12`、failed CI=`34024011663`保留，不由后续authority commit替代。
- F001 remote acceptance绑定ruleset `22381941 / refs/heads/dev / ACTIVE / effective 9/9`；本次只读readback与authority synchronization commit是独立事件，不能写成新的remote mutation。
- 当前workstream及下一动作=`NQ-GATEAUDIT-PHASE6-L5-L6-SCOPE-AND-QUALIFICATION-PLAN`，status=`NOT_STARTED / NONE / NOT_RUN`。本次仅同步L4接受事实与精确docs交付；不启动下一任务，不新增L4 review或qualification。
- Phase5 ACCEPTED/CLOSED，remaining blocking=0；F005仍DEFERRED/NON_BLOCKING，平台attestation须未来显式授权。详见[F001 post-remote acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F001_POST_REMOTE_AUTHORITY_ACCEPTANCE.md)。


## Pre-B0 residual P2

以下均为 `OPEN / P2 / NON_BLOCKING_FOR_B0`，本任务未修改：

| Finding | 保留的触发条件 |
| --- | --- |
| F3 restore proof identity | POST_RESTORE_VALIDATION 输入错误 commit/proof schema/schema target 或相等的空 canary 时仍可能接受。 |
| F4 SBOM array shape | 规范化遇到单元素数组或空嵌套数组时可能输出对象/null。 |
| F5 Java shadow committed-change classification | 新增违规提交后，干净 checkout 的 git status 不再将其标记为 NEW_CODE_FINDING。 |
| F6 manual seed SQL scope | 在含非 fixture admin 账号的数据库手工运行 seed，其默认账号 UPDATE 范围过宽；静态风险，未执行。 |

上述pre-B0历史残余来源保留；其旧审查/交付next action已由最终L4接受事实推进，不再作为当前前置。不宣称整个GateAUDIT已结束。

## Persistent boundary

- `LIVE=DISABLED`、kill switch=`ENGAGED`；禁止再次pilot、PLACE、CANCEL、transfer、withdraw或credential/生产服务器/生产数据库访问。
- GateY frozen archive与published tags不可改写。
- P5-F001/P5-F007/P5-F008/P5-F009均为`ACCEPTED / CLOSED`；Phase5已关闭，Phase6 IN_PROGRESS/NOT_FROZEN，L4 ACCEPTED，L5/L6 NEXT/NOT_STARTED。remote enforcement为APPLIED/VERIFIED/ACCEPTED，platform attestation保持DEFERRED/NON_BLOCKING。

## F009 accepted delivery lineage

- Local review=`CLOSED / PASS / P0_0 / P1_0`；P1-1/P1-2/P1-3均CLOSED，不追加review。
- Failed delivery=`85d11984d0c65b464ffe4858fe7fd1da51885f12 / 34024011663`；precommit ROADMAP authority delta造成stage-asset exception digest stale。
- Remediation与accepted technical head=`dbb8b9c6a2319338f5ca90b566ad494142a55e20`；exact-head CI=`34024427455 / completed / success / 9 of 9 / failed 0 / skipped 0 / cancelled 0`。
- 本轮authority/docs commit仅同步已接受事实与ROADMAP guard binding，不替换technical pair，不重跑technical qualification。
