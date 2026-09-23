# GateAUDIT known limitations and residuals

Canonical baseline 是 [Phase7-A residual matrix](../../audit/evidence/GATEAUDIT_PHASE7_A_FINAL_BASELINE_INVENTORY.md)。Phase7-B 只关闭 mandatory historical projection obligation；其余 16 个 identity 保持原分类，不因 pre-tag closeout 被删除、合并、静默关闭或升级为 P1。

## Phase7-B final residual matrix — 17 rows

| # | Residual / obligation | Final classification | Owner / preservation rule |
| ---: | --- | --- | --- |
| 1 | `HISTORICAL_PROJECTION_REPAIR_REQUIRED` | `CLOSED_BY_BASELINE_VERIFICATION / REPAIR_NOT_REQUIRED` | accounting projection integrity owner；Position 与 latest Snapshot exact comparison 已一致，mandatory `1→0` |
| 2 | P5-F005 platform attestation | `DEFERRED / NON_BLOCKING / DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION / id-token NOT_GRANTED` | future platform attestation owner；不得写 CLOSED |
| 3 | ordinary concurrent INSERT loser | `P2 / OPEN / NON_BLOCKING` | canonical order/trade persistence owner；仅在 duplicate mutation/accounting、data loss 或 severity escalation 时重评 |
| 4 | wildcard-import residual | `P3 / OPEN / NON_BLOCKING` | Java hygiene owner；不为 freeze 做无关清理 |
| 5 | GateY `Order.externalOrderId=NULL` | `P2 / ORDER_VENUE_IDENTITY_MODEL_CONSISTENCY_RESIDUAL` | historical production fact immutable；禁止修改生产订单清零 |
| 6 | pre-B0 F3 restore proof identity | `OPEN / P2 / NON_BLOCKING_FOR_B0` | deployment/restore contract owner；不否定 Phase5B acceptance |
| 7 | pre-B0 F4 SBOM array shape | `OPEN / P2 / NON_BLOCKING_FOR_B0` | SBOM/provenance tooling owner；仅在 normalization 变更时处理 |
| 8 | pre-B0 F5 Java shadow classification | `OPEN / P2 / NON_BLOCKING_FOR_B0` | Java governance owner；保留原语义 |
| 9 | pre-B0 F6 manual seed SQL scope | `OPEN / P2 / NON_BLOCKING_FOR_B0` | fixture/database tooling owner；SQL 未执行，future seed 前须先收窄 scope，禁止用于生产或含非-fixture admin 的库 |
| 10 | typed compatibility rows / P1-1 / PB1 | `RETIRED_COMPATIBILITY_ONLY / NOT_CURRENTLY_ELIGIBLE` | compatibility owner；只有路径真实重新 canonical 后才重做 reachability |
| 11 | intent-worker rows / PB2 | `DORMANT_NO_CURRENT_ENTRYPOINT / NOT_CURRENTLY_ELIGIBLE` | sender/worker owner；真正接入后重评，不计 PASS/SKIP/closure |
| 12 | 14 historical inactive scenarios | `FUTURE_OBLIGATION / NOT_CURRENTLY_ELIGIBLE` | future qualification owner；不进入当前 eligible 分母 |
| 13 | scheduler/lease/leader、Venue restart、超出 accepted L5/L6 envelope 的规模、长期和扩展故障 | `FUTURE_OBLIGATION` | future runtime/qualification owner；新 owner、入口、scope、authority 同时存在后另建 proof |
| 14 | historical FAIL/BLOCKED/remediation；5421ms root cause | `HISTORICAL_ONLY / APPEND_ONLY / UNKNOWN` | historical evidence owner；禁止改写 PASS、CLOSED 或已知根因 |
| 15 | legacy Phase3 IDs F-005/F-011 | `LEGACY_FINDING_IDENTITY_UNRECOVERABLE / RETIRED` | 只有找到可验证 canonical source 才重审，禁止与 P5-F005 混同 |
| 16 | AntD deprecation warning | `OBSERVATION` | frontend dependency owner；独立 framework upgrade scope，不阻断 Phase7 |
| 17 | JS bundle-size warning | `OBSERVATION` | frontend performance owner；独立 bundling scope，不阻断 Phase7 |

Phase7-B canonical result 位于 [historical projection verification](../../audit/evidence/GATEAUDIT_PHASE7_B_HISTORICAL_PROJECTION_BASELINE_VERIFICATION.md)：`MANDATORY_CLOSURE=0`、`OTHER_RESIDUAL_RECLASSIFICATIONS=0`。Phase7-C readiness P0/P1=`0/0` 不表示 P2/P3/deferred/future/historical identities 已消失。

## Scope limitations

GateAUDIT 没有证明或授权 generic LIVE、第二 pilot、多账户/多交易所、高可用、无限规模、超出 accepted duration 的 soak、自动 real-trading scheduling、AI/DH trading、transfer 或 withdraw。Repository schema/current code 事实不能外推 production deployment state；历史 production V46 也不能冒充当前 repository schema。
