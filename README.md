# NexusQuant

NexusQuant 是通用量化交易平台，第一阶段聚焦虚拟币量化交易，后续复用账户、行情、策略、回测、评估、发布、风控、交易、复盘等底座扩展到美股和 A 股。

当前事实入口以 `docs/current/` 为准。`docs/gates/` 只保存已完成 Gate 的冻结卷宗，`docs/archive/` 只作历史归档参考。

## 当前状态

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
- NQ-GATES-JKMN-FREEZE-CI-EVIDENCE-RECONCILIATION = PASS / EVIDENCE RECONCILED / GATEO-PLAN CONDITIONALLY ALLOWED；GateJ / GateK / GateM freeze evidence = VERIFIED；GateN no-real sandbox baseline = PARTIAL / ACCEPTED WITH EXPLICIT CI VISIBILITY RESIDUAL（tag / archive / local freeze validation / later dev CI 存在，但 tagged commit direct CI run 不可见且已显式接受为 residual）；GateO O-1 minimal implementation P1 已修复并等待 re-review，GateO stage 仍 NOT COMPLETED
- NQ-GATEO-PLAN-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND = PASS（通过）/ PLAN ONLY（仅规划）/ NOT IMPLEMENTED（未实现）；GateO O-0 planning baseline 已落档，范围为公开行情受控外联与数据质量运行化阶段规划；O-1 最小实现 P1 已修复，GateO stage 仍 NOT COMPLETED，public marketdata readiness 不等于 trading authorization
- NQ-GATEO-O1-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND-IMPLEMENTATION = IMPLEMENTED（已实现）/ P1 FIXED（P1 已修复）/ READY FOR RE-REVIEW（可重新复核）；已落地 manual profile / feature flag、默认 disabled fallback、public REST allowlist、private/signed denylist、endpoint authority escape guard、redaction/log summary、bounded timeout/retry/backoff、fake-server/no-egress tests 与 Data Quality linkage；未执行真实 public outbound smoke，未新增对外 API / migration / frontend / research / CI workflow，未读取 credential，当前不得提交，下一步必须重新 review
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

## 当前明确不做

- AI / AI 信号 / AI 自动交易 / AI Paper Trading
- 真实 LIVE 下单与真实交易所下单接口调用
- 美股 / A 股
- 合约全量
- 高频
- 复杂因子平台
- 外部通知（邮件 / Slack / 钉钉 / 企业微信 / Telegram / Webhook / 短信）
- 自动恢复策略引擎

## 当前文档入口

- `docs/current/README.md`：当前事实入口索引
- `docs/current/STATUS.md`：当前项目状态
- `docs/current/ROADMAP.md`：总路线
- `docs/current/GATEK_PLAN.md`：GateK planning-only 阶段规划；明确 GateK implementation、AI、DH runtime、LIVE、multi-exchange expansion 均未启动
- `docs/current/GATEK_ARCHITECTURE_BASELINE_REVIEW.md`：GateK architecture baseline review；审查 backend/frontend/research/docs/test/security 边界，结论为 P0/P1=0、P2 follow-up required，未启动 GateK implementation
- `docs/current/NQ_GATES_JKMN_FREEZE_CI_EVIDENCE_RECONCILIATION.md`：GateJ/K/M/N freeze、CI、no-real/no-outbound 与 GateO-PLAN 入场边界证据收口；GateN explicit CI visibility residual 仍保留
- `docs/current/GATEO_PLAN.md`：GateO O-0 planning-only baseline、O-1 implementation baseline 与后续 O-2/O-3/O-4/O-5/O-FREEZE 计划；O-1 已 P1 fixed / ready for re-review，O-5 manual real public smoke 仍 NOT STARTED
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
