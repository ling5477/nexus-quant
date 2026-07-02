# NQ-DH Integration-1 Dry-run Plan RebaseN

> 任务：`NQ-DH-INTEGRATION1-DRYRUN-PLAN-REBASEN` / `NQ-DH-I1-P0-FACTSOURCE-REBASE-CONTINUE`
> 仓库视角：NexusQuant（NQ）
> 日期：2026-07-02
> 状态：`PLAN BASELINE ACCEPTED / I1-P0 FACTSOURCE REBASE CLOSED`
> 下一步：`NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN / NOT STARTED`

## 1. P0 事实源结论

本文件只记录 NQ 侧对 Integration-1 dry-run planning baseline 与 P0 factsource rebase close 的当前事实源。它不启动 Integration-1 implementation，不实现 runtime，不新增 API、migration、client、provider、dispatcher、test code 或真实 HTTP。

```text
NQ Integration-1 rebase input: GateN public marketdata / exchange sandbox no-real baseline frozen and tagged.
NQ broad current work line: GateO current work line remains separate and is not overwritten by this P0 factsource rebase.
DH prerequisite: DH Stage4 Decision Pipeline MVP CLOSED / ACCEPTED.
Integration-1 dry-run plan baseline: ACCEPTED.
NQ-DH-I1-P0-FACTSOURCE-REBASE-CONTINUE: CLOSED / ACCEPTED.
Old NQ-DH-GATEK-INTEGRATION1-PLAN-PACK: SUPERSEDED / REBASE_REQUIRED.
Integration-1 implementation: NOT STARTED.
Integration-1 runtime: NOT STARTED.
Runtime integration: NOT STARTED.
Real HTTP: NOT STARTED.
Real provider: NOT STARTED.
DH integrated: NO.
AI / Agent runtime: NOT STARTED.
LangGraph runtime: NOT STARTED.
LIVE: DISABLED.
```

说明：GateO 是 NQ 当前公开行情受控外联与数据质量运行化主线；本 P0 不回滚 GateO，也不把 GateO 写成 Integration-1 runtime。Integration-1 dry-run 的前置条件固定写为 `NQ GateN + DH Stage4 Decision Pipeline MVP CLOSED`，含义是使用 GateN no-real sandbox frozen baseline 作为 dry-run planning 的 NQ 侧输入。

## 2. Stage / Gate 边界

```text
NQ 自身阶段使用 Gate 体系，例如 GateN / GateO。
DH 自身阶段使用 Stage 体系，例如 DH-STAGE4-DECISION-PIPELINE-MVP。
NQ-DH 集成任务可以引用 NQ GateN rebase，但不得把 DH 自身阶段写成 GateK、GateL 或 GateN。
当前前置条件：NQ GateN + DH Stage4 Decision Pipeline MVP CLOSED。
禁止前置条件：NQ GateN + DH GateK CLOSED。
```

旧 `DH-GATEK-DECISION-PIPELINE-MVP`、`DH GateK Decision Pipeline MVP`、`docs/gates/dh-gatek-decision-pipeline-mvp/` 只能作为 DH 历史错误命名或 `SUPERSEDED / NAMING_REPLACED` 说明出现。旧 `NQ-DH-GATEK-INTEGRATION1-PLAN-PACK` 只能作为 historical reference，且必须保持 `SUPERSEDED / REBASE_REQUIRED`。

## 3. Integration-1 dry-run 状态

```text
Plan baseline accepted: YES.
P0 factsource rebase closed: YES.
P1 contract dry-run plan allowed: YES, but plan-only.
Implementation allowed: NO.
Runtime allowed: NO.
Real HTTP allowed: NO.
Real provider allowed: NO.
LIVE allowed: NO.
```

P1 只能规划 NQ 如何构造 dry-run `DecisionRequest`、禁止字段、schema 扩展策略、mock/no-outbound 约束和记录不执行语义。P1 不允许实现 NQ dispatcher，不允许调用 DH runtime，不允许新增真实 HTTP，不允许让 DH 输出进入 order、risk mutation、paper run start、LIVE 或 private trading 路径。

## 4. NQ 侧允许与禁止

P0 已完成的允许范围：

```text
docs/current factsource sync
old GateK integration wording classification
current README / STATUS / ROADMAP / TESTING / WORKLOG / WORK_ORDER index update
NQ GateN frozen baseline as Integration-1 dry-run input
DH Stage4 CLOSED as Integration-1 prerequisite
```

持续禁止：

```text
production code
测试代码
contracts / golden_cases 修改
新增 API path / Controller / migration
真实 HTTP
真实 DH runtime 调用
真实 NQ runtime integration
真实交易所调用
RealClient / real provider / real permission probe
credential / token / cookie / API secret / passphrase 读取或输出
AI / Agent runtime
LangGraph runtime
LIVE
NQ DB 读写
订单、风控、账本、Paper Run 或策略状态 mutation
```

## 5. Readiness decision

```text
ALLOW_I1_P0_CLOSE: YES
ALLOW_I1_P1_CONTRACT_PLAN: YES
ALLOW_INTEGRATION1_DRYRUN_IMPLEMENTATION: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 6. 下一步

```text
NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN / NOT STARTED
```

如果后续发现 current factsource 又把 DH Stage4 写成 DH GateK、把 dry-run 写成 runtime、或把 implementation/runtime/real HTTP/provider/LIVE 写成 started，则必须先回到：

```text
NQ-DH-I1-P0-FACTSOURCE-REBASE-FIX-2
```
