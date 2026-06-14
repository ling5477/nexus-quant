# GateK Plan

日期：2026-06-14

## 1. 当前事实

- Project: NexusQuant / NQ。
- Current stage: GateJ completed。
- GateJ-FREEZE: 30m / 1h / 24h / 7d acceptance PASS。
- Next: GateK-PLAN。
- GateK implementation: NOT STARTED。
- AI: NOT STARTED。
- DH integration: NOT INTEGRATED / not connected to NQ。
- LIVE: DISABLED。
- Multi-exchange expansion: NOT STARTED。
- Credential governance / permission probe guarded baseline 已冻结。
- 真实 OKX/Binance permission probe adapter: NOT IMPLEMENTED。
- DH Integration-0 safety gate: CLOSED / ACCEPTED，但只代表 contract / mock / docs / contract test 线已冻结，不代表 runtime integration。

## 2. GateK 定位

GateK 是 GateJ completed 之后的 planning / architecture / productization / deployment / observability / security boundary stage。GateK-PLAN 用于冻结下一阶段的工作边界、拆批次、验收标准、风险与审计前置条件。

GateK 不是实盘阶段，不是 AI runtime 阶段，不是 DH runtime integration 阶段，也不是真实交易所扩展阶段。GateK 的首要价值是把 GateJ 后续工作从“能继续做”收口为“知道先做什么、不能做什么、哪些必须先审计再实现”。

## 3. GateK 非目标

- 不实现 GateK 功能。
- 不新增 AI 模块、AI 信号、AI 自动交易或 AI Paper Trading。
- 不开启 LIVE，不新增真实下单、撤单、转账、提现或真实交易所私有调用。
- 不实现 DH runtime integration，不新增 NQ RealClient，不接真实 Provider，不做真实 HTTP 联调。
- 不实现真实 OKX/Binance permission probe adapter。
- 不新增 API、Controller、Service、Repository、Adapter 或 migration。
- 不修改 backend、frontend、research、scripts、deploy 代码。
- 不切换 Ant Design，不引入 shadcn / Tailwind 大重构。
- 不宣称 UI/UX professionalism completed，不宣称公开用户生产就绪。
- 不把 GateK-PLAN 写成 GateK implementation started。

## 4. GateK 主线拆分

### GateK-1：事实源与路线图收口

允许范围：
- 统一 `STATUS.md`、`ROADMAP.md`、根 `README.md`、`AGENTS.md`、`CLAUDE.md`、`docs/current/README.md`。
- 明确 GateJ completed、Next GateK-PLAN、GateK implementation NOT STARTED。
- 明确 AI / DH runtime / LIVE / multi-exchange expansion NOT STARTED。
- 清理旧阶段误导描述，避免把 GateK 写成 AI 已启动或 DH 已集成。

禁止范围：
- 不写实现计划已开工。
- 不移动已冻结 Gate 卷宗。
- 不把历史 docs 重新作为 current fact source。

候选任务：
- `GATEK-DOC-FACT-SYNC`：事实源、索引、阶段措辞同步。
- `GATEK-PLAN-FREEZE-REVIEW`：GateK-PLAN 冻结审查。

验收标准：
- current docs 入口阶段口径一致。
- `GateK implementation NOT STARTED`、`AI NOT STARTED`、`DH NOT INTEGRATED`、`LIVE DISABLED` 均可检索。
- 未修改代码、API、migration、部署脚本。

### GateK-2：架构清理与测试基线

允许范围：
- 梳理 NQ 模块边界、backend / frontend / research / docs 依赖关系。
- 梳理当前测试矩阵、历史 GateJ / credential governance / permission probe 文档入口。
- 明确哪些测试是当前基线，哪些是后续 hardening。

禁止范围：
- 不做跨模块重构。
- 不修改核心交易、策略、回测、风控状态机。
- 不新增 migration 或 API。

候选任务：
- `GATEK-ARCHITECTURE-BASELINE-REVIEW`：架构与模块边界 review。
- `NQ-CI-BASELINE-PLAN`：CI 基线规划。

验收标准：
- 有模块边界矩阵、测试矩阵和 hardening backlog。
- 明确后端、前端、E2E、Python、docs-only 各自验证命令。
- 所有实现项保持 review-before-implementation。

### GateK-3：前端产品化与 Design System 深化

允许范围：
- 延续 NQ Console Design System。
- 优先规划 Backtest Detail、Strategy、Risk、Market Data、Operation / Monitor 页面产品化。
- 保持 Paper Trading / 回测 / 策略 / 风控 / 运行监控为核心。
- 梳理 loading / empty / error / disabled / risky operation 状态。

禁止范围：
- 不做 AI / Agent / DH 完整页面 mock。
- 不换 Ant Design。
- 不引入 shadcn / Tailwind 大重构。
- 不做成熟交易终端拖拽工作区。
- 不隐藏风险、失败、拒绝、停用、审计和追踪信息。

候选任务：
- `NQ-FRONTEND-GATEK-BUILD-MATRIX`：页面施工矩阵。
- `NQ-CONSOLE-DESIGN-SYSTEM-GATEK-REVIEW`：Design System 继承与缺口 review。

验收标准：
- 每个页面有业务目标、核心状态、数据区、操作区、风险态、验收命令。
- 明确哪些页面可进入 implementation，哪些只能保持 planning-only。
- 不新增后端契约，除非单独 review 后另起任务。

### GateK-4：可观测性 / 性能基线 / 运维部署

允许范围：
- 规划接口耗时、慢接口、P95/P99、SQL 耗时、调度耗时、交易所 API 耗时、traceId 串联。
- 梳理 docker compose、初始化、健康检查、日志、配置模板、回滚说明。
- 规划 NQ-CI-BASELINE，不一次做完整复杂 CI/CD。

禁止范围：
- 不做大规模性能重构。
- 不新增生产观测平台依赖。
- 不改部署脚本或环境模板，除非后续单独授权。
- 不写真实密钥、token、cookie、私钥或 passphrase。

候选任务：
- `NQ-OBSERVABILITY-BASELINE-PLAN`：可观测性基线规划。
- `NQ-DEPLOYMENT-BASELINE-PLAN`：部署与运维基线规划。
- `NQ-CI-BASELINE-PLAN`：最小 CI 基线规划。

验收标准：
- 有指标字典、日志字段、traceId 传播边界、慢接口阈值建议。
- 有部署前置检查、健康检查、回滚与配置脱敏规则。
- 明确 planning-only 与 implementation 分界。

### GateK-5：安全 hardening / credential / no-outbound

允许范围：
- 梳理 PAPER / LIVE 硬隔离。
- 梳理 credential 使用边界、API / audit / response 脱敏。
- 梳理 no-outbound 测试隔离。
- 梳理 OKX bootstrap no-outbound 专项是否进入 GateK。
- 梳理真实 permission probe adapter 前置条件。

禁止范围：
- 不读取、打印、复制、输出真实 API key、secret、token、私钥、助记词、passphrase。
- 不调用真实 OKX/Binance 私有接口。
- 不实现真实 permission probe adapter。
- 不绕过 no-real-exchange guard。
- 不把 `permission_scope = NULL` 当成 `TRADE`。

候选任务：
- `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW`：测试隔离专项 review。
- `NQ-CREDENTIAL-PERMISSION-PROBE-REAL-ADAPTER-DESIGN-REVIEW`：真实 adapter 设计审计。
- `NQ-CREDENTIAL-NO-EGRESS-TEST-PLAN`：fake-server / no-egress 测试计划。

验收标准：
- 真实 adapter 进入任何实现前必须有 design review、fake-server tests、no-egress tests、脱敏 audit 设计和 rollback plan。
- PAPER / LIVE、SIM / LIVE、test / runtime 边界明确。
- 明确哪些安全任务必须另起安全审计。

### GateK-6：NQ-DH Integration-0 契约冻结，不做真实集成

允许范围：
- 只允许 contract freeze / mock / stub / contract test / security docs。
- 只允许只读边界与 forbidden side-effect checklist。
- 只允许登记 Integration-0 工作线状态和 Integration-1 前置条件。

禁止范围：
- 不允许 DH 下单、撤单、启动 Paper Run、修改策略状态、读取 NQ 凭证、读写 NQ DB。
- 不允许 NQ RealClient。
- 不允许真实 Provider。
- 不允许真实 HTTP。
- 不允许 Integration-1 runtime。
- 不允许把 Integration-0 acceptance 写成 DH integrated。

候选任务：
- `NQ-DH-INT0-GATEK-REGISTRATION`：只读 Integration-0 工作线登记。
- `NQ-DH-INTEGRATION1-PLANNING-ONLY-AUDIT`：Integration-1 planning-only audit。

验收标准：
- Integration-0 文档和状态只表达 contract / mock / test acceptance。
- Integration-1 前置 blocker 明确，包括 DH P1-4 residual、header 对齐、真实通道安全审计。
- 未新增真实通道、HTTP client、Provider、API、migration 或 runtime side effect。

## 5. GateK 任务矩阵

| Priority | Task | Workstream | Scope | Mode | Acceptance |
| --- | --- | --- | --- | --- | --- |
| P0 | `GATEK-DOC-FACT-SYNC` | GateK-1 | current facts / indexes | docs-only | 入口文档一致，禁止阶段误写 |
| P0 | `GATEK-ARCHITECTURE-BASELINE-REVIEW` | GateK-2 | module / dependency / test baseline | review-only | 输出架构边界与测试矩阵 |
| P0 | `NQ-CI-BASELINE-PLAN` | GateK-2 / GateK-4 | minimal CI plan | planning-only | 明确命令、缓存、失败门禁 |
| P1 | `NQ-OBSERVABILITY-BASELINE-PLAN` | GateK-4 | metrics / logs / traceId | planning-only | 输出指标字典和采样边界 |
| P1 | `NQ-FRONTEND-GATEK-BUILD-MATRIX` | GateK-3 | page matrix | planning-only | 明确页面优先级和验收 |
| P1 | `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW` | GateK-5 | no-outbound review | security review | 确认外联风险与 fake-server 策略 |
| P1 | `NQ-DEPLOYMENT-BASELINE-PLAN` | GateK-4 | runbook / health / rollback | planning-only | 输出部署基线和回滚清单 |
| P2 | `NQ-DH-INT0-GATEK-REGISTRATION` | GateK-6 | Integration-0 status | docs-only | 只登记 contract line，不做 runtime |
| P2 | `GATEK-PLAN-FREEZE-REVIEW` | all | plan freeze | review-only | P0/P1/P2 risk closure decision |

## 6. 任务优先级与建议执行顺序

1. `GATEK-DOC-FACT-SYNC`：事实源与索引收口。
2. `GATEK-ARCHITECTURE-BASELINE-REVIEW`：架构与模块边界 review。
3. `NQ-CI-BASELINE-PLAN`：CI 基线规划。
4. `NQ-OBSERVABILITY-BASELINE-PLAN`：可观测性基线规划。
5. `NQ-FRONTEND-GATEK-BUILD-MATRIX`：前端页面施工矩阵。
6. `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW`：测试隔离专项 review。
7. `NQ-DEPLOYMENT-BASELINE-PLAN`：部署与运维基线规划。
8. `NQ-DH-INT0-GATEK-REGISTRATION`：只读 Integration-0 工作线登记，不做 runtime。
9. `GATEK-PLAN-FREEZE-REVIEW`：GateK-PLAN 冻结审查。

## 7. 必须另起安全审计的任务

- 真实 OKX/Binance permission probe adapter。
- 任意真实交易所私有接口调用。
- 任意 LIVE 相关能力，即使只读也必须先审计。
- 任意 credential 解密、签名、脱敏、audit payload 变更。
- NQ-DH 真实 HTTP 通道、NQ RealClient、Provider、relay、webhook。
- no-outbound 测试隔离修复涉及 adapter bootstrap 或外部 API client 生命周期时。
- 部署环境、`.env`、secret injection、release package、日志采集与外部 sink 变更。

## 8. 必须先 review 后 implementation 的任务

- CI baseline 从 plan 进入配置实现。
- Observability baseline 从指标字典进入代码埋点。
- Frontend 页面矩阵进入页面实现，尤其涉及风险操作、账户上下文、credential、LIVE 文案。
- Deployment baseline 进入 compose / runbook / health check / rollback 脚本变更。
- Credential hardening、permission probe、no-egress gate 进入代码实现。
- Integration-1 任意 runtime 前置工作。

## 9. 只允许 planning-only 的任务

- GateK-PLAN 本身。
- Integration-1 planning-only audit。
- 真实 adapter 前置条件梳理。
- AI 信号协议概念边界梳理。
- LIVE readiness gap list。
- Multi-exchange expansion gap list。

## 10. GateK 风险清单

| Risk | Severity | Boundary | Required handling |
| --- | --- | --- | --- |
| GateK-PLAN 被误读为 implementation started | P0 | stage wording | 每次文档同步必须显式写 NOT STARTED |
| AI not started 被误写为 AI started | P0 | AI boundary | 所有 AI 相关内容只能写 planning / future |
| DH Integration-0 acceptance 被误读为 DH integrated | P0 | DH boundary | 固定写 contract / mock / docs only |
| LIVE disabled 被误写为 enabled | P0 | trading boundary | LIVE 相关任务必须另起安全审计 |
| 真实 permission probe adapter 提前实现 | P0 | credential / exchange | 必须先 design review + fake-server/no-egress tests |
| OKX bootstrap 测试外联 | P1 | no-outbound | 先做隔离 review，不直接修实现 |
| 前端产品化隐藏风险状态 | P1 | UX / safety | 风险、失败、拒绝、停用、审计必须可见 |
| 可观测性规划滑向大规模性能重构 | P2 | architecture | GateK 先定指标和基线，不做大重构 |
| CI 一次性复杂化 | P2 | CI/CD | 先做 minimal baseline，复杂矩阵后置 |

## 11. GateK backlog

- Architecture baseline checklist。
- Test matrix and hardening backlog。
- CI minimal baseline design。
- Observability metric dictionary。
- Slow API / SQL / scheduler threshold proposal。
- Deployment runbook baseline。
- Frontend page build matrix。
- NQ Console Design System gap list。
- Credential hardening gap list。
- Permission probe real adapter design review package。
- No-outbound fake-server test plan。
- Integration-0 registration and Integration-1 blocker register。
- GateK plan freeze review report。

## 12. GateK 完成标准

GateK 只有在以下条件满足后才能声明 completed：

- GateK workstreams 均有明确完成或 deferred 决策。
- GateK-1 facts / roadmap / index sync completed。
- GateK-2 architecture / test baseline review completed。
- GateK-3 frontend productization matrix completed，且未误写 AI / DH runtime 页面已实现。
- GateK-4 CI / observability / deployment baseline plans completed。
- GateK-5 security hardening backlog completed，所有真实 adapter / LIVE / credential / no-egress 实现前置审计已登记。
- GateK-6 Integration-0 registration completed，未启动 Integration-1 runtime。
- `git diff -- backend/frontend/research/scripts/deploy/migration` 在 planning-only 轮次保持为空。
- 没有真实 credential material、`.env`、`*.key`、`*.pem`、`*.log` 被纳入提交。
- GateK freeze review 明确允许进入下一阶段，且未把 GateK-PLAN 写成 GateK implementation started。
