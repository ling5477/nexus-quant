# Current Fact Source Index

本文是 NexusQuant 当前事实源索引。用途是给 GateR planning、历史 Gate archive、LIVE / AI / DH / Integration-1 / real provider 边界判断提供统一入口，避免把已冻结证据、mock/test-support、readiness、preview 或 comparison 误写成 runtime 授权。

## 1. 当前事实源优先级

当事实冲突时，按以下顺序解释当前状态：

1. 当前代码和实际验证结果。
2. [STATUS.md](STATUS.md)：当前项目状态。
3. [README.md](README.md)：current 入口和 archive pointer。
4. [ROADMAP.md](ROADMAP.md)：当前路线与下一阶段边界。
5. [TESTING.md](TESTING.md)：当前验证记录和未运行说明。
6. [WORKLOG.md](WORKLOG.md)：当前任务记录。
7. [API.md](API.md)：已实现 HTTP API 当前事实，不把未来 API 写成已实现。
8. [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 Flyway schema 当前事实，不把未来 schema 写成已实现。
9. [../gates/gate-q/README.md](../gates/gate-q/README.md)：GateQ 历史归档入口。
10. [../gates/gate-p/README.md](../gates/gate-p/README.md)：GateP 历史归档入口。
11. [../gates/gate-o/README.md](../gates/gate-o/README.md)：GateO 历史归档入口。
12. [../archive/current-cleanup/post-gateq/README.md](../archive/current-cleanup/post-gateq/README.md)：本轮 post-GateQ current cleanup 审计和移动索引。

`docs/gates/**` 与 `docs/archive/**` 是历史证据或归档引用，不覆盖 `docs/current` 当前事实入口。已从 `docs/current` 移出的过程型长文档不得再作为 current authority 引用。

## 2. 当前阶段声明

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档），release tag `nq-gateq-freeze`。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档），release tag `nq-gatep-freeze`。
- GateO 及更早 Gate：历史证据来源为 `docs/gates/**` 或 `docs/archive/**`。
- GateR：`PLAN / NOT STARTED`（规划 / 未开始）。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。

## 3. Historical Archive Pointers

| 历史阶段 | 入口 |
| --- | --- |
| GateQ | `docs/gates/gate-q/README.md` |
| GateP | `docs/gates/gate-p/README.md` |
| GateO | `docs/gates/gate-o/README.md` |
| GateN | `docs/gates/gate-n/README.md` |
| GateM | `docs/gates/gate-m/README.md` |
| GateJ/K/L current copies moved by this cleanup | `docs/archive/current-cleanup/post-gateq/README.md` |
| CI / credential / DB governance / NQ-DH Integration history moved by this cleanup | `docs/archive/current-cleanup/post-gateq/README.md` |

## 4. 禁止误写清单

- 不得把 GateR 写成 `STARTED`（已开始）或 `IMPLEMENTED`（已实现）。
- 不得把 LIVE 写成 ready / enabled。
- 不得把 AI 写成 started。
- 不得把 DH runtime 写成 integrated。
- 不得把 Integration-1 mock/test-support 写成 runtime started。
- 不得把 RealClient、real provider、private trading adapter 或 real permission probe 写成 implemented。
- 不得把 public marketdata readiness、Data Quality、permission readiness、risk preflight、preview、comparison、binding preview 或 archive closeout 写成 trading authorization。
- 不得把 Python offline foundation 写成 ML ready 或 live execution ready。
