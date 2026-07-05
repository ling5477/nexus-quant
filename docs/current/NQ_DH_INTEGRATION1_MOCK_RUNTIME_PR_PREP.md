# NQ-DH-I1-MOCK-RUNTIME-PR-PREP

日期：2026-07-05。

## Scope

本文件只准备 NQ-DH Integration-1 mock runtime / test-only milestone 的 NQ PR 材料。范围是 PR diff review、cross-repo consistency review、security boundary recheck、validation summary、PR title/body 和 readiness decision。

本轮不实现功能、不修改 Java 生产代码、不修改测试代码、不新增测试、不真实调用 DH、不真实 HTTP、不接 provider、不访问 localhost runtime、不修改 contracts/OpenAPI/json-schema/golden_cases/migration、不启动 Agent / LangGraph、不开启 LIVE、不 merge。

## Commit hygiene

```text
NQ worktree: E:/Project/nexus-quant-i1-dryrun
Branch: nq-dh-i1-joint-runtime-dryrun-test-impl
PR base: origin/dev
HEAD: 8424db53
origin/dev: 1a749690
origin/nq-dh-i1-joint-runtime-dryrun-test-impl: 8424db53
merge-base(origin/dev, HEAD): 3d3ef6e7
```

- NQ worktree 写入前 `git status --short` 无输出，branch 正确，远端 tracking branch 已存在且 head 对齐。
- DH dev 写入前 `git status --short` 无输出，上一轮 close review docs 已提交，HEAD 与 `origin/dev` 均为 `b5803bc`。
- NQ dev 只读确认，最终复核 `dev` 与 `origin/dev` 均为 `1a749690`，`git status -sb` 显示 clean；NQ-DH / Integration-1 scoped diff 为空。本轮未修改 NQ dev。
- 本轮未发现 NQ worktree 或 DH dev 存在未提交的非本轮 docs diff；`COMMIT_HYGIENE_BLOCKED` 不触发。

## PR diff review

命令范围：`origin/dev...HEAD`。

```text
Commit range:
8424db53 docs(nq-dh): close Integration-1 mock runtime milestone
12810e5e docs(nq-dh): close Integration-1 joint dry-run test review
3e8a834f test(nq-dh): align joint dry-run contract blockers
0385be09 docs(nq-dh): add Integration-1 joint dry-run test work order
f6d1c3bb docs(nq-dh): close Integration-1 NQ dry-run client review
d2875e56 feat(nq-dh): add limited Integration-1 dry-run client
be9bc42f docs(nq-dh): add Integration-1 NQ runtime client work order
```

初始 PR diff summary：

```text
43 files changed, 4758 insertions(+), 49 deletions(-)
```

允许类别：

- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/integration/dh/**`：allowed NQ isolated integration/dh package。
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/integration/dh/**`：allowed NQ integration/dh tests。
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/integration1/**`：allowed NQ Integration-1 guard tests。
- `backend/nq-app/src/main/resources/application.yml` 与 `application-prod.yml`：allowed application disabled-by-default config。
- `docs/current/**`：allowed docs/current。

Forbidden diff check：

```text
backend/**/db/migration: EMPTY
frontend: EMPTY
research: EMPTY
scripts: EMPTY
deploy: EMPTY
.github: EMPTY
contracts: EMPTY
golden_cases: EMPTY
uncategorized: EMPTY
```

结论：`PR_PREP_BLOCKED_FORBIDDEN_DIFF` 不触发。

## Security boundary review

确认项：

- 无真实 HTTP transport；isolated production package 未出现 `WebClient` / `RestTemplate` / `OkHttp` / `java.net.http.HttpClient`。
- `DhDryRunTransport` 仅为 port/interface；测试使用 fake / in-memory transport。
- `application.yml` 默认 `enabled=false`、`client.enabled=false`、`production-enabled=false`、`kill-switch=true`。
- `application-prod.yml` 显式 `enabled=false`、`client.enabled=false`、`production-enabled=false`、`kill-switch=true`。
- 无真实 DH URL；NQ runtime endpoint 默认空值；测试 URL 仅为 `.invalid` 或 forbidden-shape 断言。
- 无 localhost runtime dependency。
- 无 provider call、credential forwarding、order mutation、execution call、risk mutation、ledger mutation、account mutation、paper run start、live run start、exchange adapter call。
- 无 BUY / SELL / PLACE_ORDER / CANCEL_ORDER 输出路径；相关命中均为 forbidden capability、fail-closed validation 或测试断言。
- `LONG_BIAS / SHORT_BIAS` 仍为 bias-only，不映射为 `BUY / SELL`。
- `source=NQ_DRYRUN` 仍 review-gated，production 不启用。
- Runtime integration 仍 `NOT STARTED`；DH integrated 仍 `NO`；LIVE 仍 `DISABLED`。

## Cross-repo consistency review

- DH companion commits 已存在：`b5803bc docs(dh): close Integration-1 mock runtime milestone` 及前置 endpoint / joint test / blocker fix commits。
- DH HMAC wire-level source value fix 已提交；`HmacNqDryRunAuthenticator` 注释和实现记录 source allowlist / tenant-source pair 使用验签后的 wire value exact match。
- DH endpoint close review 已提交；DH limited dry-run endpoint 为 `CLOSED / ACCEPTED`。
- Joint runtime dry-run test close review 已提交；blockers `SIGNATURE_MATERIAL_SOURCE_NORMALIZATION_MISMATCH` 与 `SCHEMA_VERSION_MISMATCH` 均为 `FIXED`。
- NQ `DEFAULT_SCHEMA_VERSION=1.0.0` 与 DH endpoint docs 中 response `schemaVersion=1.0.0` 一致。
- contracts/OpenAPI/json-schema/golden_cases 未 formalize，本 PR 不声称 formalized。

## Validation evidence

本轮重新执行轻量 git / diff / rg 边界检查。Maven 未重跑；沿用 mock runtime close review 前一轮已记录结果。

```text
Maven：未重跑；沿用 mock runtime close review 前一轮已记录结果。
```

| Item | Result |
| --- | --- |
| NQ worktree `git status --short` | PASS / 写入前 clean；写入后仅本轮 docs/current PR prep diff |
| NQ worktree `git branch --show-current` | PASS / `nq-dh-i1-joint-runtime-dryrun-test-impl` |
| NQ worktree `git fetch origin` | PASS |
| NQ `git diff --check` | PASS / working-tree check 无 whitespace error |
| NQ additional PR-range `git diff --check origin/dev...HEAD` | WARNING / existing `docs/current/NQ_DH_INTEGRATION1_NQ_CLIENT_CLOSE_REVIEW.md` reports one blank line at EOF；本轮未改该非白名单文件 |
| NQ PR diff stat/name-only | REVIEWED / all files categorized as allowed |
| NQ forbidden path diff | PASS / EMPTY |
| NQ boundary `rg` | REVIEWED / hits are forbidden wording, config defaults, test assertions, or existing non-PR contexts; no real HTTP/provider/trading side effect evidence |
| DH `git status --short` | PASS / 写入前 clean；写入后仅本轮 docs/current companion sync |
| DH forbidden-scope diff | PASS / EMPTY before write |
| DH boundary `rg` | REVIEWED / PowerShell `dh-*` glob failed as literal path, rerun with explicit module directories; hits are existing docs/tests/contracts/golden forbidden context, not this PR enabling runtime |
| NQ dev read-only status | PASS / FINAL CLEAN / NQ-DH and Integration-1 scoped unstaged and staged diff empty |
| NQ quality profile | MISSING / NOT EFFECTIVE QUALITY GATE |

沿用测试证据：

```text
NQ backend full test: PASS / BUILD SUCCESS（上一轮记录）
NQ Integration0 scoped: PASS / BUILD SUCCESS（上一轮记录）
NQ Integration1 scoped: PASS / BUILD SUCCESS（上一轮记录）
NQ dry-run targeted tests: PASS / BUILD SUCCESS（上一轮记录）
DH companion tests: PASS / BUILD SUCCESS for dh-api, dh-usecase, and -Pquality validate（DH companion commits）
NQ quality profile: missing / not effective quality gate
```

## PR title

```text
test(nq-dh): add Integration-1 mock dry-run runtime boundary
```

## PR body

```markdown
## Scope
- NQ-DH Integration-1 mock/runtime test-only baseline
- DH limited dry-run endpoint already closed on DH side
- NQ limited dry-run client isolated in integration/dh
- Joint fake-transport / MockMvc / in-memory validation

## What changed
- isolated NQ dry-run client / DTO / signing / validation / recorder
- disabled-by-default config
- test-only joint dry-run validation
- HMAC source wire-value alignment
- schemaVersion=1.0.0 alignment
- docs/current status updates

## Security boundary
- no real DH call
- no real HTTP
- no provider
- no LIVE
- no order / execution / risk / ledger / account / paper / live side effect
- LONG_BIAS / SHORT_BIAS are bias-only, not BUY / SELL
- runtime integration remains NOT STARTED
- DH integrated remains NO

## Validation
- NQ backend full test: PASS / BUILD SUCCESS (recorded in close review; not rerun in PR prep)
- NQ Integration0 scoped: PASS / BUILD SUCCESS (recorded in close review; not rerun in PR prep)
- NQ Integration1 scoped: PASS / BUILD SUCCESS (recorded in close review; not rerun in PR prep)
- NQ dry-run targeted tests: PASS / BUILD SUCCESS (recorded in close review; not rerun in PR prep)
- DH companion tests: PASS / BUILD SUCCESS in DH companion commits
- NQ quality profile: missing / not effective quality gate

## Not included
- real HTTP
- real DH runtime call
- provider integration
- contracts/OpenAPI/json-schema formalization
- golden_cases changes
- migrations
- LIVE
- Agent / LangGraph
```

## Readiness decision

```text
ALLOW_NQ_MOCK_RUNTIME_PR_CREATE: YES
ALLOW_NQ_MOCK_RUNTIME_PR_MERGE_NOW: NO
ALLOW_REAL_DH_CALL_NOW: NO
ALLOW_REAL_HTTP_NOW: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_SCHEMA_FORMALIZATION_NOW: NO
ALLOW_CONTRACTS_MODIFICATION_NOW: NO
ALLOW_GOLDEN_CASES_MODIFICATION_NOW: NO
ALLOW_DH_CODE_CHANGE_NOW: NO
ALLOW_NQ_CODE_CHANGE_NOW: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## Boundary confirmation

本轮未改 Java 生产代码、未改测试代码、未新增测试、未修改 NQ dev、未 merge、未真实调用 DH、未真实 HTTP、未访问 localhost runtime、未接 provider、未读取凭证、未输出密钥、未改 contracts/OpenAPI/json-schema/golden_cases/migration、未接 Agent / LangGraph、未开启 LIVE、未触碰 order / execution / risk / ledger / account / paper / live。

## Risks

- `origin/dev` 已前进，PR create 允许，但 merge 前必须重新跑 CI / PR checks，并复核 base 更新后的冲突与 diff。
- 额外 PR-range whitespace check 命中一个既有 docs/current 文件 EOF blank line；本轮因白名单限制未修复，若 PR gate 强制 `git diff --check origin/dev...HEAD`，需单独授权 docs cleanup。
- NQ `quality` profile missing，不能写成 quality gate PASS。
- NQ dev 最终只读复核为 clean；若 PR create 前再次出现 unrelated dirty，只能按 scoped NQ-DH / Integration-1 diff 重新判定，不得覆盖用户改动。

## Next concrete action

```text
NQ-DH-I1-MOCK-RUNTIME-PR-CREATE
```
