# NexusQuant

NexusQuant 是通用量化交易平台，第一阶段聚焦虚拟币量化交易，后续复用账户、行情、策略、回测、评估、发布、风控、交易、复盘等底座扩展到美股和 A 股。

当前事实入口以 `docs/current/` 为准。`docs/gates/` 只保存已完成 Gate 的冻结卷宗，`docs/archive/` 只作历史归档参考。

## 当前状态

- NQ-GATEP-RELEASE-TAG-AND-ARCHIVE = PASS（通过）/ COMPLETED（已完成）/ RELEASE TAG PUSHED（release tag 已推送）；release tag：`nq-gatep-freeze`，tagged commit：`3650714ae9cd441e59eb5b09c605a14bbc9998dc`，archive pointer：`docs/gates/gate-p/README.md`。GateP freeze 只冻结真实数据质量与交易准备阶段的只读诊断、前端诊断视图、交易前置只读基线、Python offline foundation 与 current fact-source closeout，不代表真实交易授权。
- NQ-GATEQ-FREEZE-CLOSEOUT = PASS（通过）/ FROZEN（已冻结）/ ACCEPTED（已接受）/ READY FOR ARCHIVAL（可归档）；current pointer：`docs/current/GATEQ_FREEZE_CLOSEOUT.md`。GateQ final state 为 FROZEN / ACCEPTED；只冻结 GateQ-0..6 的只读验证、对照、preview、binding contract 与前端证据展示基线，不代表真实交易、LIVE、AI / DH runtime 接入、RealClient、real provider、private trading adapter 或 real permission probe 完成。
- NQ-GATEQ-PLAN-SHADOW-LIVE-READINESS = PLAN READY（规划已就绪）/ CONSUMED BY GATEQ-1..6（已由 GateQ-1..6 消费）；规划入口：`docs/current/GATEQ_PLAN.md`。GateQ plan 定义 strategy evaluation gate、Paper vs Shadow 只读对照、Python offline artifact -> Java fact-source 绑定、Shadow Live no-side-effect 边界和 batch plan；不代表真实 Shadow Live runner 或交易授权。
- NQ-GATEQ-1-STRATEGY-EVALUATION-GATE-READONLY-BASELINE = IMPLEMENTED（已实现）/ SELF-REVIEWED（已自审）/ READY TO COMMIT（可提交前复核）；新增 `GET /api/strategies/evaluation-gate` 后端只读 baseline，只聚合 strategy version / dataset / evaluation / publish / SIM Paper 既有事实。`READY_FOR_SHADOW_REVIEW` 仅代表研究与评估可进入后续 review，不代表交易授权、LIVE 已启用或策略可实盘运行。
- NQ-GATEQ-2-PAPER-SHADOW-RUN-READONLY-MODEL-AND-DTO = IMPLEMENTED（已实现）/ SELF-REVIEWED（已自审）/ READY TO COMMIT（可提交前复核）；新增 `GET /api/strategies/paper-shadow/comparison` 后端只读 baseline，只聚合 strategy version / dataset / evaluation / publish / SIM Paper 既有事实，并把 Shadow runner / shadow fact source 当前建模为 `NOT_IMPLEMENTED`（未实现）/ `BLOCKED_SHADOW_NOT_IMPLEMENTED`（Shadow 未实现阻断）。`READY_FOR_COMPARISON` 仅代表 Paper / Shadow 只读对照证据可查看，不代表交易授权、LIVE 已启用或 Shadow Live 执行就绪。
- NQ-GATEQ-3-SHADOW-LIVE-NO-SIDE-EFFECT-RUNNER-SKELETON = IMPLEMENTED（已实现）/ SELF-REVIEWED（已自审）/ READY TO COMMIT（可提交前复核）；新增 `GET /api/strategies/shadow-live/preview` 后端只读 preview skeleton，只聚合 GateQ-1 evaluation gate 与 GateQ-2 Paper/Shadow comparison 结果。`READY_FOR_NO_SIDE_EFFECT_PREVIEW` 仅代表可生成只读预览计划，不代表交易授权、LIVE 已启用、Shadow Live 交易启用或真实 runner 就绪。
- NQ-GATEQ-4-PYTHON-EVALUATION-ARTIFACT-JAVA-BINDING-CONTRACT = IMPLEMENTED（已实现）/ SELF-REVIEWED（已自审）/ READY TO COMMIT（可提交前复核）；新增 `POST /api/research/evaluation-artifacts/binding-preview` 后端只读 binding preview，只校验 request body 中 Python offline evaluation artifact JSON 的 schema、checksum、hash、metrics、offline boundary 与 traceability。`VALID_FOR_BINDING_PREVIEW` 仅代表可进入只读绑定预览，不代表 Java fact 写入、artifact 导入、策略批准、Paper/Shadow run 启动、交易授权、Python ML ready 或实盘执行就绪。
- NQ-GATEQ-5-FRONTEND-PAPER-SHADOW-COMPARISON-VIEW = IMPLEMENTED（已实现）/ SELF-REVIEWED（已自审）/ READY TO COMMIT（可提交前复核）；新增 `/strategies/validation` 前端只读视图，只消费 GateQ-1 / GateQ-2 / GateQ-3 GET API，展示策略验证链路、Paper / Shadow 对照、Shadow Live no-side-effect preview、blockers / warnings / nextSteps 与 side-effect policy。该页面不代表交易授权、LIVE 启用、Shadow Live 执行、AI 或 DH runtime 接入。
- NQ-GATEQ-6-STRATEGY-LIFECYCLE-TRACE-VIEW-ENHANCEMENT = IMPLEMENTED（已实现）/ SELF-REVIEWED（已自审）/ READY TO COMMIT（可进入提交前复核）；增强 `/strategies/validation` 前端只读追溯视图，展示 strategy version -> dataset -> evaluation gate -> publish -> paper run -> Paper / Shadow Comparison -> Shadow Live Preview -> Python Artifact Binding Preview，并新增 Evidence Matrix / 证据矩阵、状态解释和禁止动作边界。GateQ-4 artifact binding 在本页为 `PENDING_FRONTEND_SUPPORT` / `NOT_CONNECTED`，不新增上传、导入、后端 API 或交易能力。
- Current fact source：GateO `FROZEN`（已冻结）/ `ACCEPTED`（已接受）；GateP `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `TAGGED`（已打 tag）；Batch 1-6A `COMPLETED`（已完成）；GateQ-0..6 `COMPLETED`（已完成）；GateQ final freeze closeout `PASS / FROZEN / ACCEPTED / READY FOR ARCHIVAL`（通过 / 已冻结 / 已接受 / 可归档）；GateQ final state `FROZEN / ACCEPTED`（已冻结 / 已接受）；GateR `PLAN / NOT STARTED`（规划 / 未开始）only；LIVE `DISABLED`（关闭）；AI `NOT STARTED`（未开始）；DH runtime `NOT INTEGRATED`（未集成）；Integration-1 `NOT STARTED`（未开始）/ mock-test-support only；RealClient / real provider / private trading adapter / real permission probe `NOT_IMPLEMENTED`（未实现）。
- GateH completed
- GateI completed
- GateJ-PLAN completed
- GateJ-1-WO completed
- GateJ-2-WO completed
- GateJ-3-WO completed
- DOC-CLEAN-2 completed
- PRE-FREEZE-CODE-AUDIT second pass completed（无 P0；E2E 与 Python 基线已由 Codex 实际重跑通过，详见 `docs/gates/gate-j/PRE_FREEZE_AUDIT_REPORT.md`）
- GateJ-FREEZE-FIX second pass completed（详见 `docs/gates/gate-j/GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`）
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed
- GateJ completed（详见 `docs/gates/gate-j/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`）
- GateK finalized / frozen / archived / tagged（tag：`nq-gatek-freeze`）
- GateM = Exchange / MarketData Runtime Readiness
- GateM runtime readiness FINALIZED / FROZEN / ACCEPTED / TAGGED（tag：`nq-gatem-freeze`；no-real runtime readiness baseline；current summary 见 `docs/current/`；GateM archive closed，22/22 approved candidates 已归档到 `docs/gates/gate-m/`）
- NQ-NEXT-PHASE-PLAN = PASS / PLAN ONLY / READY TO COMMIT；推荐下一阶段为 GateN Public MarketData / Exchange Sandbox Planning（GateN-4 fixture smoke test-only implementation 已完成；GateN-5 runtime UI sandbox source display 已完成；GateN freeze review 已完成；GateN release tag 已完成；production adapter/API/runtime implementation NOT STARTED）
- NQ-GATEN-PUBLIC-MARKETDATA-SANDBOX-PLAN = FINALIZED（最终定版）/ FROZEN（已冻结）/ ACCEPTED（已接受）/ CLOSED（已关闭）/ TAGGED（已打 tag）；GateN public marketdata / exchange sandbox no-real baseline 已冻结并打 tag `nq-gaten-freeze`（GateN-4 fixture smoke test-only implementation 已完成；GateN-5 runtime UI sandbox source display 已完成；production adapter/API/runtime implementation NOT STARTED）
- NQ-GATEN-0-EXCHANGE-DOCS-AND-EXISTING-ADAPTER-RECONCILIATION = PASS / RECONCILIATION BASELINE / READY TO COMMIT；复核早期 OKX / Binance 官方文档整理、现有 public marketdata adapter/interface/API/test 证据、历史 live-0/spike 证据与 private trading 禁止边界；不启动 GateN implementation
- NQ-GATEN-1-PUBLIC-MARKETDATA-CONTRACT-PLAN-REVIEW = PASS / CONTRACT PLAN REVIEW / READY TO COMMIT；定义 public-only marketdata internal contract、source taxonomy、freshness/health/gap model、rate-limit/timeout/retry model、public/private separation rules 与 GateN-2 fake-server/no-egress 输入；不启动 GateN implementation
- NQ-GATEN-2-FAKE-SERVER-NO-EGRESS-PUBLIC-MARKETDATA-TEST-PLAN = PASS / TEST PLAN BASELINE / READY TO COMMIT；规划 fake-server contract、no-egress boundary、forbidden endpoint list、test matrix、fixture taxonomy、readiness simulation 与 GateN-3 entry criteria；不启动 GateN implementation
- NQ-GATEN-3-PUBLIC-MARKETDATA-ADAPTER-SKELETON-PLAN-REVIEW = PASS / SKELETON PLAN REVIEW / READY TO COMMIT；定义 public-only adapter skeleton 最小接口、adapter class/package proposal、DTO/capability/readiness model、no-egress constraints 与 GateN-4 entry criteria；不启动 GateN implementation
- NQ-GATEN-4-MARKETDATA-SANDBOX-FIXTURE-SMOKE-PLAN-REVIEW = PASS / FIXTURE SMOKE PLAN REVIEW / READY TO COMMIT；定义 deterministic fixture smoke 最小范围、fixture hygiene、readiness simulation matrix、timeout/rate-limit/malformed payload simulation、no-egress validation plan 与 GateN-5 entry criteria；不启动 GateN implementation
- NQ-GATEN-4-MARKETDATA-SANDBOX-FIXTURE-SMOKE-IMPLEMENTATION-PLAN = PASS / IMPLEMENTATION PLAN READY / READY TO COMMIT；规划 deterministic fixture / local fake-server / no-egress fixture smoke 的最小未来实现切片、允许文件范围、fixture set、readiness expectation matrix、no-egress verification design 与 future validation commands
- NQ-GATEN-4-MARKETDATA-SANDBOX-FIXTURE-SMOKE-IMPLEMENTATION = IMPLEMENTED / SELF-REVIEWED / ACCEPTED；新增 deterministic OKX / Binance fixture resources 与 test-only no-egress fixture smoke，覆盖 public marketdata shape、readiness mapping、fixture hygiene、real-host denial、private/signed route fail-closed、fake-server unavailable fallback blocked；不启动 production adapter/API/runtime implementation
- NQ-GATEN-5-RUNTIME-UI-SANDBOX-SOURCE-DISPLAY-PLAN-REVIEW = PASS / RUNTIME UI SANDBOX SOURCE DISPLAY PLAN REVIEW / READY TO COMMIT；规划 sandbox source taxonomy、readiness status、diagnostic reason、fixture/no-egress 标签、页面入口和 forbidden UI wording；不启动 frontend/API/runtime implementation
- NQ-GATEN-5-RUNTIME-UI-SANDBOX-SOURCE-DISPLAY-IMPLEMENTATION = IMPLEMENTED / SELF-REVIEWED / ACCEPTED；在既有 `/marketdata` Data Quality / Readiness 区域增加 compact sandbox/source display block，复用现有 readiness / bars facts，缺失后端字段显示 `PENDING_BACKEND_SUPPORT`；不新增 backend API、真实外联、fake-server runtime、adapter skeleton、LIVE、AI 或 DH runtime
- NQ-GATEN-FREEZE = PASS / FROZEN / ACCEPTED / CLOSED；冻结 GateN public marketdata / exchange sandbox no-real baseline；不代表 production readiness、LIVE authorization、real provider readiness、private trading authorization、real permission probe 或 trading authorization
- NQ-GATEN-RELEASE-TAG-AND-ARCHIVE = PASS / COMPLETED / RELEASE TAG PUSHED / READY TO COMMIT；release tag：`nq-gaten-freeze`；tag object：`d191474bd3ec0fb52566896fd9ef081eb843b520`；tagged commit：`361d2ac7bb595f72067b0e2c2d0485361e9a0540`
- NQ-GATES-JKMN-FREEZE-CI-EVIDENCE-RECONCILIATION = PASS / EVIDENCE RECONCILED / GATEO-PLAN CONDITIONALLY ALLOWED；GateJ / GateK / GateM freeze evidence = VERIFIED；GateN no-real sandbox baseline = PARTIAL / ACCEPTED WITH EXPLICIT CI VISIBILITY RESIDUAL（tag / archive / local freeze validation / later dev CI 存在，但 tagged commit direct CI run 不可见且已显式接受为 residual）；该条为 GateO plan 入场前置历史状态，当前 GateO final status 已 FROZEN / ACCEPTED
- NQ-GATEO-FREEZE-REVIEW = PASS（通过）/ ACCEPTED（已接受）；GateO final status = FROZEN（已冻结）/ ACCEPTED（已接受）；O-FREEZE = PASS / ACCEPTED；O-1/O-2/O-3/O-4/O-5 均 FROZEN / ACCEPTED；O-5D-R1 DataOrigin implementation = OPTIONAL / NOT STARTED；LIVE DISABLED；AI NOT STARTED；DH runtime NOT_INTEGRATED；RealClient / real provider / real permission probe NOT_IMPLEMENTED；public marketdata readiness 不等于 trading authorization
- NQ-GATEO-PLAN-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND = PASS（通过）/ PLAN ONLY（仅规划）/ NOT IMPLEMENTED（未实现）；GateO O-0 planning baseline 已落档，范围为公开行情受控外联与数据质量运行化阶段规划；O-1 controlled public outbound guard baseline 已冻结接受；O-2 Data Quality Center baseline 已 PASS / ACCEPTED / FROZEN；O-3 final status 已 FROZEN / ACCEPTED；O-4 MarketData Quality UI baseline 已 FROZEN / ACCEPTED；O-4A review 已 PASS / ACCEPTED；O-4B read-only UI implementation 已 COMPLETED / ACCEPTED；O-4E freeze review 已 PASS / ACCEPTED；O-5 manual public outbound smoke plan 已 COMPLETED / PLAN ONLY / NOT IMPLEMENTED；O-5A 已 PASS / ACCEPTED；O-5B runner binding plan 已 PASS / ACCEPTED；O-5B-R1 runner binding implementation 已 IMPLEMENTED / SELF-REVIEWED / COMMITTED；O-5B-R2 runner binding review 已 PASS / ACCEPTED；O-5B manual smoke result 已 COMPLETED / RESULT REVIEWED / ACCEPTED；O-5C first smoke result review 已 PASS / ACCEPTED；O-5D DataOrigin.PUBLIC_OUTBOUND decision 已 PASS / ACCEPTED，decision = ALLOW_FUTURE_IMPLEMENTATION；O-5E manual public outbound smoke freeze review 已 PASS / ACCEPTED；O-5 final status 已 FROZEN / ACCEPTED；GateO final status 已 FROZEN / ACCEPTED，O-FREEZE 已 PASS / ACCEPTED，O-5D-R1 DataOrigin implementation 仍 OPTIONAL / NOT STARTED，public marketdata readiness 不等于 trading authorization
- NQ-GATEO-O5E-MANUAL-PUBLIC-OUTBOUND-SMOKE-FREEZE-REVIEW = PASS（通过）/ ACCEPTED（已接受）；只冻结 O-5 manual public outbound smoke baseline，P0/P1=0；O-5 final status = FROZEN / ACCEPTED；该条为 O-5E 当轮状态，当前 O-FREEZE 已 PASS / ACCEPTED，GateO final status 已 FROZEN / ACCEPTED；本轮未执行真实 HTTP、未重跑 O-5B smoke、未改代码、未实现 `DataOrigin.PUBLIC_OUTBOUND`
- NQ-GATEO-O5D-DATAORIGIN-PUBLIC-OUTBOUND-DECISION-REVIEW = PASS（通过）/ ACCEPTED（已接受）；decision = `ALLOW_FUTURE_IMPLEMENTATION`（允许后续单独实现）。基于 O-5B/O-5C accepted smoke evidence，允许后续另起任务把公开行情只读外联来源建模为 `DataOrigin.PUBLIC_OUTBOUND`；本轮未改代码、未新增 enum/DTO/mapper/API/UI/test，未执行真实 HTTP；该语义只允许用于 data quality / readiness / UI diagnostic context，不表示 trading authorization；LIVE 仍 DISABLED；不表示 permission granted、credential configured、real provider 可用于交易、可下单/撤单/转账/提现
- NQ-GATEO-O5C-FIRST-SMOKE-RESULT-REVIEW = PASS（通过）/ ACCEPTED（已接受）；只读复核 O-5B manual public readonly smoke redacted summary、runner allowlist/denylist、credential/no-signed/no-private/no-trading 边界与 forbidden-area diff，P0/P1=0；O-5B smoke result ACCEPTED；O-5D 已 PASS / ACCEPTED，decision = ALLOW_FUTURE_IMPLEMENTATION；O-5E 已 PASS / ACCEPTED；该条为 O-5C 当轮状态，当前 O-FREEZE 已 PASS / ACCEPTED，GateO final status 已 FROZEN / ACCEPTED；public marketdata readiness 不等于 trading authorization
- NQ-GATEO-O5B-MANUAL-PUBLIC-OUTBOUND-SMOKE-EXECUTION = COMPLETED（已完成）/ RESULT REVIEWED（结果已复核）/ ACCEPTED（已接受）；使用已 review 的 `GateOManualPublicOutboundSmokeTest` 执行一次 public readonly smoke，`SERVER_TIME / INSTRUMENTS / TICKER / OHLCV` 均 `httpStatus=200` 且 `errorCategory=NONE`；未读取 credential、未签名、未访问 private endpoint、未产生交易副作用；该结果已由 O-5C、O-5D decision review 与 O-5E freeze review 消费，并已由 GateO freeze review 消费；public marketdata readiness 不等于 trading authorization
- NQ-GATEO-O5B-R2-MANUAL-RUNNER-BINDING-REVIEW = PASS（通过）/ ACCEPTED（已接受）；R1 commit `35413109 test(gateo): bind manual public outbound smoke runner` 已提交，runner binding 安全复核 P0/P1=0；该条为 O-5B execution 前的历史 review 记录，当前事实以上方 GateO freeze / O-5E / O-5D / O-5C / O-5B 状态为准；O-5 final status 已 `FROZEN / ACCEPTED`，GateO final status 已 `FROZEN / ACCEPTED`，public marketdata readiness 不等于 trading authorization
- NQ-GATEO-O1-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND-FREEZE-REVIEW = PASS（通过）/ ACCEPTED（已接受）/ FROZEN（已冻结）；冻结已提交的 controlled public outbound guard baseline（commit `8638dec0`），覆盖 manual profile / feature flag、默认 disabled fallback、public REST allowlist、private/signed denylist、endpoint authority escape guard、redaction/log summary、bounded timeout/retry/backoff、fake-server/no-egress tests 与 Data Quality linkage；当轮未执行真实 public outbound smoke，未新增对外 API / migration / frontend / research / CI workflow，未读取 credential；后续 O-5B manual smoke execution、O-5C result review、O-5D decision review、O-5E freeze review 与 GateO freeze review 已完成并接受
- NQ-GATEO-O2-DATA-QUALITY-CENTER-FREEZE-REVIEW = PASS（通过）/ ACCEPTED（已接受）/ FROZEN（已冻结）；冻结已提交的 Data Quality Center baseline（commit `4d659d72`），覆盖后端 Data Quality 纯模型、O-1 result mapper、freshness/gap/source health 规则和单元测试；未新增 API / migration / frontend / research / scripts / deploy / CI workflow，未真实外联，未读取 credential，未启用 LIVE / AI / DH runtime，未实现 RealClient / real provider / real permission probe
- NQ-GATEO-O3E-MARKETDATA-READINESS-API-FREEZE-REVIEW = PASS（通过）/ ACCEPTED（已接受）/ FROZEN（已冻结）；冻结 commit `7a42ca03 feat(marketdata): extend readiness API read model` 中既有 `GET /api/marketdata/readiness` read-only response baseline；P0/P1=0；scoped Maven 与后端全量 Maven 均 PASS / BUILD SUCCESS；O-4 final status 已 FROZEN / ACCEPTED；O-5B manual smoke execution、O-5C result review、O-5D decision review、O-5E freeze review 与 GateO freeze review 已完成并接受；GateO final status 已 FROZEN / ACCEPTED，public marketdata readiness 不等于 trading authorization
- NQ-GATEO-O4E-MARKETDATA-QUALITY-UI-FREEZE-REVIEW = PASS（通过）/ ACCEPTED（已接受）；冻结 commit `e62f1e43 feat(frontend): add marketdata quality readiness view` 中已提交的 `/marketdata` Quality / Readiness 只读 UI baseline；O-4 final status 为 FROZEN / ACCEPTED；O-4B read-only UI implementation 为 COMPLETED / ACCEPTED；当轮未改 backend / API / migration，未执行 O-5 manual public outbound smoke；该后续已由 O-5B/O-5C 消费，public marketdata readiness 不等于 trading authorization
- NQ-GATEO-O5-MANUAL-PUBLIC-OUTBOUND-SMOKE-PLAN = COMPLETED（已完成）/ PLAN ONLY（仅规划）/ NOT IMPLEMENTED（未实现）；O-5A plan review 已 PASS / ACCEPTED；O-5B-R1 runner binding implementation 已 IMPLEMENTED / SELF-REVIEWED / COMMITTED；O-5B-R2 runner binding review 已 PASS / ACCEPTED；O-5B manual smoke result 已 COMPLETED / RESULT REVIEWED / ACCEPTED；O-5C first smoke result review 已 PASS / ACCEPTED；O-5D DataOrigin.PUBLIC_OUTBOUND decision 已 PASS / ACCEPTED，decision = ALLOW_FUTURE_IMPLEMENTATION；O-5E 已 PASS / ACCEPTED；O-5 final status 已 FROZEN / ACCEPTED；O-FREEZE 已 PASS / ACCEPTED；GateO final status 已 FROZEN / ACCEPTED
- NQ-GATEO-O5B-R1-MANUAL-PUBLIC-OUTBOUND-RUNNER-BINDING-IMPLEMENTATION = IMPLEMENTED（已实现）/ SELF-REVIEWED（已自审）/ COMMITTED（已提交）；新增默认跳过的 test-only manual JUnit runner `GateOManualPublicOutboundSmokeTest`，绑定 manual profile、feature flag、allowlist/denylist、HTTP 前 safety gates 和 redacted evidence；本轮不执行真实 HTTP，不读取 credential，不改 production code/CI/API/migration；已由 NQ-GATEO-O5B-R2-MANUAL-RUNNER-BINDING-REVIEW 接受，后续只允许单独 manual public readonly smoke execution
- NQ-GATEO-O4-MARKETDATA-QUALITY-UI-PLAN = FROZEN（已冻结）/ ACCEPTED（已接受）；O-4B read-only UI implementation = COMPLETED（已完成）/ ACCEPTED（已接受）；已复用现有 `/marketdata` 页面，只读消费 `GET /api/marketdata/readiness`，展示 source health / freshness / gap / error / dataOrigin 和 null 字段“暂无稳定事实”规则；当轮未改 backend / API / migration，未执行 O-5 manual public outbound smoke；该后续已由 O-5B/O-5C 消费
- NQ-GATEO-O4A-MARKETDATA-QUALITY-UI-CONTRACT-PLAN-REVIEW = PASS（通过）/ ACCEPTED（已接受）；已复核 O-4 UI contract、页面/路由、readiness API 字段、状态文案、安全边界和测试矩阵；修正 `docs/current/API.md` readiness enum drift；O-4B implementation 允许进入但仅限 read-only UI，不得改 backend/API/migration 或执行 O-5 public smoke
- NQ-GATEO-O3B-MARKETDATA-READINESS-READ-ONLY-API-IMPLEMENTATION = COMPLETED（已完成）/ ACCEPTED（已接受）；扩展既有 `GET /api/marketdata/readiness` read model，保留旧字段并追加 O-2 Data Quality 语义字段；仍只读本地 DB facts，不新增 endpoint、migration、frontend、research、scripts、deploy、CI workflow，不真实外联，不读取 credential；已由 O-3E freeze review 接受，public marketdata readiness 不等于 trading authorization
- NQ-GATEO-O3-MARKETDATA-RUNTIME-READINESS-API-PLAN = PASS（通过）/ PLAN ONLY（仅规划）/ NOT IMPLEMENTED（未实现）；该 planning-only baseline 已由 O-3B implementation 消费，原决策为优先扩展现有 `GET /api/marketdata/readiness` read model，不重复造主 endpoint
- NQ-DH-I1-P0-FACTSOURCE-REBASE-CONTINUE = CLOSED / ACCEPTED / DOCS-ONLY；Integration-1 dry-run plan baseline accepted；前置条件固定为 `NQ GateN + DH Stage4 Decision Pipeline MVP CLOSED`；implementation / runtime / real HTTP / real provider / LIVE 均 NOT STARTED。GateO 当前主线不被本 P0 回滚或覆盖。
- NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN = COMPLETED / PLAN ONLY / NOT IMPLEMENTED；规划 Integration-1 contract dry-run 的 schema、canonical `X-NQ-DH-*` header、tenant/requestId/traceId/timestamp/nonce/HMAC、错误码、audit、batch 和 no-side-effect 测试前置；不新增 API / migration / code / test，不启动 runtime，不真实 HTTP，不接 RealClient/provider/LIVE/AI/LangGraph，下一步只能 P1-A contract schema / fixture plan review
- NQ-DH-I1-MOCK-BASELINE = IMPLEMENTED / TEST_SUPPORT_ONLY / MOCK_ONLY；IMP1/IMP2/IMP3 mock baseline guards、stub recorder 和 joint mock contract fixtures 已在 PR 分支收口；Integration-1 limited runtime planning 已 CLOSED / ACCEPTED / PLAN_ONLY / NO_RUNTIME；不启动 NQ runtime DH client、DH dry-run runtime endpoint、real HTTP、real provider、AI / LangGraph 或 LIVE。
- Future AI Paper Trading candidate is not current GateM
- AI not started
- DH runtime not integrated / not connected to NQ
- LIVE disabled
- RealClient / real provider / real exchange adapter not implemented

GateM 当前权威定义为 Exchange / MarketData Runtime Readiness，不是 AI Paper Trading 阶段。GateM 已 final / frozen / accepted / tagged 为 no-real runtime readiness baseline（release tag：`nq-gatem-freeze`）：adapter readiness、MarketData readiness、NoReal contract、Paper-to-Real boundary、Runtime Guarded UI 和 Operational Readiness 均已收口；这些事实不代表真实交易所接入、LIVE、AI 或 DH runtime 已启动。GateM 后下一阶段规划已完成，GateN Public MarketData / Exchange Sandbox Planning 已作为 no-real baseline 冻结并打 release tag（`nq-gaten-freeze`）；GateN-4 fixture smoke test-only implementation 已完成；GateN-5 runtime UI sandbox source display 已完成，但 production adapter/API/runtime implementation 仍未开始。AI、AI 信号、AI 自动交易、AI Paper Trading、DH runtime integration、LIVE、真实交易所 adapter / RealClient / real provider 仍未开始或未实现。

## 当前能力摘要

- 交易工作台已完成（GateH）。
- OKX / Binance SPOT 历史 OHLCV K 线接入已完成（GateH）。
- marketdata dataset 与 backtest config 绑定已完成（GateH）。
- `strategy_versions` 与 publish workflow 已完成（GateI）。
- backtest config / evaluation / traceability 增强已完成（GateI）。
- SIM / Paper Trading 运行闭环已完成（GateI）。
- Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘、异常停机最小结构已完成（GateI）。
- Paper Trading 调度 / 心跳 / 日报 / 告警 / 恢复事件 / 稳定性验收结构 / HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警最小落库已完成（GateJ-1/2/3）。
- GateJ-FREEZE 30m / 1h / 24h / 7d 连续运行验收已通过，GateJ completed。
- GateM Exchange / MarketData Runtime Readiness 已 FINALIZED / FROZEN / ACCEPTED / TAGGED（tag：`nq-gatem-freeze`）：adapter readiness service / guard / status API / readiness panel / MarketData readiness / operational readiness / backend E2E 均保持 fail-closed，不授权 real exchange / LIVE。
- GateN Public MarketData / Exchange Sandbox 已 FINALIZED / FROZEN / ACCEPTED / CLOSED / TAGGED（tag：`nq-gaten-freeze`）：deterministic fixture smoke 与 sandbox/source display 均保持 no-real / no-egress / diagnostic-only，不授权 real provider / LIVE / private trading / trading authorization。
- GateO Public MarketData Controlled Outbound & Data Quality Runtime 已 FROZEN / ACCEPTED：O-1 受控外联、O-2 Data Quality、O-3 readiness API、O-4 Quality UI、O-5 manual public readonly smoke 均已冻结；该状态不授权 LIVE、trading authorization、real provider、permission probe、AI 或 DH runtime。
- GateP 主线是真实数据质量与交易准备：Batch 1 事实源与状态收口已完成；Batch 2 Market Data Data Quality Center 后端只读切片已完成；Batch 3 前端 Data Quality Center 与 Runtime 放行矩阵已完成；Batch 4 单交易所账户权限与风险前置只读基线已完成；Batch 5 Python offline research foundation 已完成；Batch 6 freeze readiness review 的 P1 drift 已由 Batch 6A 修复；GateP final freeze closeout 已 `PASS / FROZEN / ACCEPTED / READY FOR ARCHIVAL`；release tag `nq-gatep-freeze` 已推送，历史归档在 `docs/gates/gate-p/`。上述事实不代表真实交易、不启用 LIVE、不授权 private trading。
- Python Research 当前是 reproducible offline experiment foundation：已具备 dataset manifest、experiment metadata、evaluation metrics skeleton 与 CLI run summary；仍不是 research platform ready、ML ready 或 live execution ready。

## 当前明确不做

- AI / AI 信号 / AI 自动交易 / AI Paper Trading
- 真实 LIVE 下单与真实交易所下单接口调用
- GateQ archive / tag 未执行；GateR implementation 未启动；真实 Shadow Live runner implementation 仍未启动（GateQ-3 只实现 no-side-effect preview skeleton，不代表 Shadow Live 交易启用）
- RealClient / real provider / private trading adapter / real permission probe
- 美股 / A 股
- 合约全量
- 高频
- 复杂因子平台
- 外部通知（邮件 / Slack / 钉钉 / 企业微信 / Telegram / Webhook / 短信）
- 自动恢复策略引擎

## 当前文档入口

- `docs/current/README.md`：当前事实入口索引
- `docs/current/FACT_SOURCE_INDEX.md`：当前事实源优先级、GateO/GateP/LIVE/AI/DH/Integration/RealClient 边界与禁止误写清单
- `docs/current/STATUS.md`：当前项目状态
- `docs/current/ROADMAP.md`：总路线
- `docs/current/GATEQ_FREEZE_CLOSEOUT.md`：GateQ final freeze closeout 当前权威入口；状态为 `PASS / FROZEN / ACCEPTED / READY FOR ARCHIVAL`，GateQ final state 为 `FROZEN / ACCEPTED`。
- `docs/current/GATEQ_FREEZE_READINESS_REVIEW.md`：GateQ freeze readiness 前置证据；状态为 `PASS / READY FOR FREEZE CLOSEOUT`，已由 final closeout 消费。
- `docs/current/GATEQ_PLAN.md`：GateQ planning-only 入口；定义 Shadow Live 只读影子运行、Paper vs Shadow 边界、strategy evaluation gate、traceability model、Python artifact binding、candidate API/pages/tests 与 Q0..FREEZE batch plan；状态为 `PLAN READY / CONSUMED BY GATEQ-1..6`
- `docs/current/API.md`：已记录 GateQ-1 `GET /api/strategies/evaluation-gate`、GateQ-2 `GET /api/strategies/paper-shadow/comparison`、GateQ-3 `GET /api/strategies/shadow-live/preview` 与 GateQ-4 `POST /api/research/evaluation-artifacts/binding-preview` 只读 API；这些 endpoints 只回答研究评估、Paper/Shadow 对照、no-side-effect preview 准备度和 Python offline artifact 绑定预览，不输出交易授权、LIVE 已启用、Shadow Live 交易启用或 Java fact 写入。
- `docs/gates/gate-p/README.md`：GateP historical archive；GateP release tag、freeze closeout、Batch 1-6A evidence matrix 与 testing summary 已归档。当前摘要：GateP final state = FROZEN / ACCEPTED / TAGGED；release tag = `nq-gatep-freeze`；Data Quality / Permission Readiness / Risk Preflight 不等于 trading authorization；Python Research 不等于 ML ready 或 live execution ready
- `docs/current/GATEK_PLAN.md`：GateK planning-only 阶段规划；明确 GateK implementation、AI、DH runtime、LIVE、multi-exchange expansion 均未启动
- `docs/current/GATEK_ARCHITECTURE_BASELINE_REVIEW.md`：GateK architecture baseline review；审查 backend/frontend/research/docs/test/security 边界，结论为 P0/P1=0、P2 follow-up required，未启动 GateK implementation
- `docs/current/NQ_GATES_JKMN_FREEZE_CI_EVIDENCE_RECONCILIATION.md`：GateJ/K/M/N freeze、CI、no-real/no-outbound 与 GateO-PLAN 入场边界证据收口；GateN explicit CI visibility residual 仍保留
- `docs/gates/gate-o/README.md`：GateO historical archive；GateO process and evidence docs 已归档，不再作为 `docs/current` 过程文档入口。当前摘要：GateO final status = FROZEN / ACCEPTED；public readonly smoke accepted；`DataOrigin.PUBLIC_OUTBOUND` implementation 仍 OPTIONAL / NOT STARTED；public marketdata readiness 不等于 trading authorization
- `docs/gates/gate-j/PLAN_GATEJ.md`：GateJ 规划（historical archive）
- `docs/gates/gate-j/GATEJ_WORK_ORDER.md`：GateJ 工作单（含 GateJ-FREEZE 范围；historical archive）
- `docs/gates/gate-j/PRE_FREEZE_AUDIT_REPORT.md`：GateJ-FREEZE 前置代码 / 文档 / 实现真实性审查报告（historical archive）
- `docs/gates/gate-j/PRE_FREEZE_AUDIT_FIX_PLAN.md`：PRE-FREEZE-CODE-AUDIT 修复计划与 GateJ-FREEZE 入场条件（historical archive）
- `docs/gates/gate-j/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`：GateJ-FREEZE 1h/24h/7d 验收记录模板（historical archive）
- `docs/gates/gate-j/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`：GateJ-FREEZE 最终验收报告（historical archive）
- `docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`
- `docs/gates/gate-m/README.md`：GateM historical archive；archive closed，22/22 approved candidates moved；Batch 1 已归档 freeze readiness / freeze review / release tag evidence（`nq-gatem-freeze`），Batch 2 已归档 Runtime Guarded UI evidence，Batch 3 已归档 Operational Readiness evidence，Batch 4 已归档 GateM-2 MarketData readiness evidence
- `docs/gates/gate-n/README.md`：GateN historical archive；archive closed，11/11 approved GateN process docs moved（tag：`nq-gaten-freeze`）；current authority 仍保留在 `docs/current/`
- `docs/current/NQ_NEXT_PHASE_PLAN.md`：GateM 后下一阶段 planning-only 文档；推荐 GateN Public MarketData / Exchange Sandbox Planning；GateN-4 fixture smoke test-only implementation 已完成，GateN-5 runtime UI sandbox source display 已完成，GateN freeze review 已完成，GateN release tag 已完成，production adapter/API/runtime implementation NOT STARTED
- `docs/current/CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md`：credential active material selection Batch 5-E-A 只读审计报告
- `docs/current/CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md`：credential active uniqueness Batch 5-E-C 只读审计报告
- `docs/current/CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md`：credential enable governance Batch 5-F-A 只读审计报告；Batch 5-F-B 已完成 schema-only `ENABLED` audit event 准备，Batch 5-F-C 已实现最小 enable command
- `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`：credential governance Batch 5-G 冻结复核报告；Batch 5-G-A 已完成 P3 文案 cleanup，后续允许进入真实交易所权限探活设计审计
- `docs/current/CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md`：credential permission probe 当前权威冻结结论；no-real-exchange guarded backend baseline 已接受冻结，真实 OKX/Binance adapter 仍未实现
- `docs/current/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`：credential permission probe code/API/test 历史设计审计与实现记录，保留 no-real-exchange tests 证据链
- `docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`：credential permission probe 历史设计审计与 V31 schema-only 记录，保留 future real adapter 入场条件
- `docs/current/NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md`：OKX bootstrap no-outbound 只读审计报告；记录 local integration test 启动期 OKX public instruments 外联触发路径、根因、Binance 对照和后续 FIX 建议
- `docs/current/DOC_CLEAN_REPORT.md`：最近一次文档清理报告
- GateH 冻结卷宗：`docs/gates/gate-h/`
- GateI 冻结卷宗：`docs/gates/gate-i/`
- GateJ 冻结卷宗：`docs/gates/gate-j/`
- GateM 冻结/发布历史卷宗：`docs/gates/gate-m/`

## 当前验证基线

后端：

```powershell
mvn -f backend/pom.xml test
```

前端：

```powershell
Set-Location frontend
npm run build
npm run test:e2e
```

Python：

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

详细验证记录见 `docs/current/TESTING.md`。

## 剩余已知风险

- `npm audit` 仍有既有告警。
- Vite chunk > 500 kB 警告仍存在。
- Ant Design React 19 compatibility / deprecation warning（`Card.bordered`、`Modal.destroyOnClose`）仍存在。
- E2E 本轮二次审查实际结果为 24 passed / 1 skipped；唯一 skipped 为 `E2E_TRADE_ORDER_ID` 未配置的既有订单详情链路，与 GateJ 主链无关。
- Python 本轮二次审查实际结果为 pytest 2 passed、mypy success、ruff all checks passed；默认 WindowsApps `python` alias 不可用，人工复跑需使用真实 Python 解释器或修正 PATH。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed；本轮最终文档阶段未重新执行 build/deploy/restart。
- UI/UX smoke review 结论为 Functional stability PASS、UI/UX professionalism FAIL；该项登记为 post-freeze remediation，不能宣称 UI/UX 专业化已完成。
- 当前不应描述为面向公开用户的生产就绪。
