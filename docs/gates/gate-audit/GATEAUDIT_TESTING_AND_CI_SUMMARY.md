# GateAUDIT testing and CI summary

本 summary 区分 historical technical CI、Phase7 governance CI、当前 source-head CI、Phase7-D delivery CI 与 future freeze exact-head CI。每个 run 只证明自己的 head，后续文档或 authority CI 不覆盖历史 technical acceptance。

## Historical technical acceptance

| Scope | Accepted head | Exact-head CI | Result |
| --- | --- | ---: | --- |
| Phase0 | `40e1077e1fe735a3d250f094caaa24e437e8ea3f` | `33306024232` | completed / success / 11 of 11 |
| Phase4 F-001/F-002/F-003/F-004/closeout | `95b859ee...` / `0651a736...` / `327c2229...` / `18efc06c...` / `7ca1fc92...` | `33347091147` / `33387882472` / `33399190770` / `33358364678` / `33405549149` | exact-head green |
| Phase5A | `d1d20f4087cd337e0b21037b38b377bcbe25499f` | `33505000903` | completed / success / 9 of 9 |
| Phase5B | `a12ec821fee9dcadaa11428f1db0a065614fb58b` | `33615809848` | completed / success / 9 of 9 |
| Phase5 F008/F007/F009 | `614359fc...` / `0e2efdeb...` / `dbb8b9c6...` | `33978394774` / `34009290836` / `34024427455` | exact-head green |
| Phase6 L4 | `3d103cea2072b3c2d9d1009cc5841c18a958ee80` | `34501806297` | completed / success / 9 of 9 |
| Phase6 L5 | `23548b75093a62d7614e16f8abcaf9ff2ea32ed7` | `34608208969` | completed / success / 9 of 9 |
| Phase6 L6-A | `23a0b46b96950aab9f0b8309d3beb8f446c73dce` | `34922938228` | completed / success / 9 of 9 |
| Phase6 L6-B/L6 | `dbf9662add09388cd77ca7552de276bb019f0f74` | `35043157675` | attempt 2 / completed / success / 9 of 9 |
| Frontend localization/Error Catalog | `1b4c87129f2a79e13e379aa56501042ddd5bd42f` | `35684433673` | completed / success / 9 of 9 |
| NQ Console Visual System V3 | `07453f8b16e798bd580070a3727aa9eb7e88a193` | `35720426791` | completed / success / 9 of 9 |

Canonical qualification detail remains in [Phase6 final acceptance](../../audit/evidence/GATEAUDIT_PHASE6_L5_L6_FINAL_ACCEPTANCE.md), [Error Catalog verification](../../error-catalog/VERIFICATION.md), and [UI V3 acceptance](../../audit/evidence/GATEAUDIT_FRONTEND_CONSOLE_VISUAL_SYSTEM_V3_ACCEPTANCE.md). Historical failed, blocked, partial and remediation attempts remain append-only and are not converted to PASS by later CI.

## Phase7 governance and source-head CI

| Phase7 step | Head | CI | Classification |
| --- | --- | ---: | --- |
| Phase7-A final inventory | `baa01f0f0034bb46a24f9fe8f62acf60bb56e3f6` | `35729125034` | immutable inventory pair |
| B–F taxonomy normalization | `8868edb248b614e360377317c9c17e8f1d7d8404` | `35734048380` | governance contract normalization pair |
| Phase7-B projection verification | `fea0f1ce228ac7079a7873393daba2d1294fec58` | `35748394188` | immutable verification pair |
| Phase7-C readiness review | `82f1afc43664a327eea2fcfd7046bcef099e621b` | `35761394744` | immutable review pair |
| Phase7-C authority source head | `c6195b5cbdc2f2708079d48963c95c06ab885955` | `35762845196` | completed / success / 9 of 9；Phase7-D input |

## Pending identities

```text
PHASE7_D_ARCHIVE_COMMIT=TO_BE_BOUND_BY_GIT
PHASE7_D_EXACT_HEAD_CI=PENDING_DELIVERY
PHASE7_E_FREEZE_COMMIT=NOT_CREATED
PHASE7_E_FREEZE_EXACT_HEAD_CI=NOT_CREATED
ANNOTATED_TAG_OBJECT=NOT_CREATED
REMOTE_TAG=NOT_CREATED
```

Phase7-D 本地只运行 archive/governance/docs/stage/secret/diff 专项验证，不重新运行 Full Maven、frontend E2E、L5、L6、historical projection oracle、PostgreSQL restore 或真实 provider。Phase7-D commit 仍必须取得自己的 `NQ CI Baseline / exact-head / completed / success / 9 of 9`，而 future Phase7-E 必须对实际 `dev` freeze candidate 再取得新 exact-head CI。
