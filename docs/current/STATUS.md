# Current Status

## 1. 当前总状态

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag：`nq-gateq-freeze`。
- GateQ archive：`docs/gates/gate-q/`。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateP release tag：`nq-gatep-freeze`。
- GateP archive：`docs/gates/gate-p/`。
- GateO 及更早 Gate：以 `docs/gates/**` 或 `docs/archive/**` 作为历史证据来源。
- GateR：`NQ-GATER-PLAN-SHADOW-RUN-OPERATIONALIZATION：PLAN READY / NOT IMPLEMENTED`（计划已就绪 / 未实现）。
- 本轮 cleanup：`NQ-DOCS-CURRENT-POST-GATEQ-CLEANUP：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实施 / 已自审 / 可进入提交前复核）。

## 2. 禁止边界

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow Live runner：`NOT STARTED`（未开始）。
- Shadow run 写侧 fact source：`NOT IMPLEMENTED`（未实现）。
- Shadow Run local fact table / record：`NOT IMPLEMENTED`（未实现）。

## 3. GateR-0 Planning Status

`docs/current/GATER_PLAN.md` 已建立 GateR-0 planning 入口，覆盖 Shadow Run 定义、状态机候选、最小数据模型候选、traceability、Paper vs Shadow consistency、risk / order intent preview 边界、候选 API / DTO / migration plan、前端候选页面、测试策略、安全边界、AI / DH runtime 边界、风险清单、Batch plan、validation commands、acceptance criteria、exit criteria 和 next concrete action。

该状态只表示 planning ready，不表示：

- GateR implementation started。
- Shadow Run local fact implemented。
- API implemented。
- migration implemented。
- frontend page implemented。
- test implemented。
- Shadow runner started。
- LIVE / AI / DH runtime started。

## 4. Post-GateQ Current Cleanup

本轮将 `docs/current` tracked Markdown 从 125 个缩减为 17 个。108 个历史过程型 current copy 已通过 `git mv` 移入 `docs/archive/current-cleanup/post-gateq/**`，不删除历史证据，不移动 `docs/gates/gate-q/**` 已归档证据，不改 release tag 历史含义。

保留在 `docs/current` 的文件只承担当前事实入口、当前状态、路线、验证、工作记录、API、DB schema、架构/模块摘要、运行手册、前端设计系统入口和 Codex workflow 入口。已冻结 Gate 的过程证据只保留 archive pointer，不在 current 保留正文。

## 5. 当前验证口径

当前 GateR-0 是 docs-only planning。未运行 Maven、frontend build、Playwright、pytest、mypy、ruff 或 GitHub Actions，因为未修改 backend、frontend、research、scripts、deploy、`.github`、migration、API、页面或测试。验证以 Git、diff、链接/路径引用、关键词边界和 forbidden-scope diff 为准，详见 [TESTING.md](TESTING.md)。
