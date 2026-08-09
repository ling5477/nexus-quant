# NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW Attempt 01

## Task classification

`INDEPENDENT_CI_REVIEW + WORKFLOW_SECURITY_REVIEW + DYNAMIC_FLYWAY_CONFORMANCE + PLAYWRIGHT_TIMEOUT_CONFORMANCE + REGRESSION_TESTS + TASK_EVIDENCE`。

结论：`PASS / CI_BLOCKER_FIX_ACCEPTED / READY_TO_COMMIT`（通过 / CI blocker 修复已接受 / 可进入提交前复核）。本 evidence 不表示 fix 已 commit/push，不表示 GitHub Actions 已 rerun，也不表示 GateW-3 已 `ACCEPTED|CI_GREEN`。

## Review target and scope comparison

- Repository / branch：`E:\Project\nexus-quant / dev`。
- Current HEAD / `origin/dev`：`54c7bdd2caee5602441ce983b33c4cd2466ee263`，subject `fix(governance): model post-commit CI failures`。
- Current failed CI：run `29253811976 / NQ CI Baseline / push / completed / failure / headSha=54c7bdd2caee5602441ce983b33c4cd2466ee263`。
- Implementation comparison authority：implementation 最终报告中的精确 10 路径。

```text
.github/workflows/ci.yml
docs/current/STATUS.md
docs/current/README.md
docs/current/ROADMAP.md
docs/current/GATEW_PLAN.md
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/FACT_SOURCE_INDEX.md
docs/current/evidence/gate-w/README.md
docs/current/evidence/gate-w/NQ-GATEW-3-CI-BLOCKER-FIX.attempt-01.md
```

Scope comparison：`expected=10 / actual=10 / extra=0 / missing=0 / staged=0`。比较通过后，本 review 仅新增 `NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW.attempt-01.md` 并同步已授权 current docs/index；最终候选暂存路径为 11。

## Authority before

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-3
work_batch_status=COMMITTED|CI_FAILED|FIX_REQUIRED
work_batch_commit=54c7bdd2caee5602441ce983b33c4cd2466ee263
work_batch_ci_run=29253811976
next_action=NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW
```

`gh run view` 与 failed job logs 独立确认：两个 Flyway job 都已成功 migrate/validate 34 migrations，随后因 `Expected Flyway current version 33 but got 34` 失败；Batch 5A 从 `13:27:28Z` 执行 `npx playwright install --with-deps chromium`，在 Ubuntu fonts 下载期间于 `13:42:20Z` 被 15 分钟 job timeout 取消。

## Flyway dynamic-version and safety review

两个通用 helper 均满足：

1. 保留 `flyway.migrate()` 与 `flyway.validate()`；
2. `flyway.info().current()` 和 `current.getVersion()` 必须非 null，否则抛出异常；
3. `flyway.info().pending().length == 0`，否则抛出异常；
4. 输出实际 `currentVersion`；
5. 不扫描 migration 文件名、不读取版本环境变量、不捕获/忽略 Flyway exception；
6. 不再含 `EXPECTED_VERSION`、固定 `33`/`34` latest-version comparison。

安全配置保持为 `locations("classpath:db/migration")`、`baselineOnMigrate(false)`、`cleanDisabled(true)`、`outOfOrder(false)`；未出现 `ignoreMigrationPatterns`、`validateOnMigrate(false)`、`repair()` 或 `clean()`。

### BackendCiLegacyAccountFixture

- 只插入 legacy `accounts` 的 `PAPER / ACTIVE` row；不创建 `exchange_accounts` 或 credential。
- 断言保持 `legacy=1 / exchange_accounts=0 / credentials=0`，均为精确比较并 fail-closed。
- 输出为 `Prepared backend CI legacy account fixture after Flyway V<currentVersion>`。

### FlywaySmoke

- migrate/validate 先于 current/pending 检查。
- `flyway_schema_history` 的 installed rank、version、description、type、script、checksum、success 输出与顺序保持。
- 原关键表、compatibility、schema artifact 与 fail-closed 断言没有 diff；只删除固定 latest-version 数字。
- 无 `|| true`、空 catch 或 `continue-on-error`。

### Fixed migration contracts

以下文件 `git diff` 无输出：

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/marketdata/VenueRuleFactsPostgresIntegrationTest.java`：V1→V34、V33→V34 语义保持；
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/VenueRuleFactsMigrationContractTest.java`：V34 contract 保持。

## Playwright timeout, fail-closed, and allowlist review

- `frontend-no-backend-e2e` job 的 `timeout-minutes: 60` 位于 job 属性层。
- `Install Playwright Chromium` 的 `timeout-minutes: 30` 位于 step 属性层；命令仍是 `npx playwright install --with-deps chromium`。
- 执行顺序保持 `npm ci → install Chromium/system deps → build → four-spec E2E`；cleanup 保持 `if: always()`。
- 安装失败或 30 分钟超时会使 step/job 失败；cleanup 不会把失败改写为通过。
- 未新增 browser/apt cache、retry loop、container、fallback browser、skip、`continue-on-error` 或上传 report/trace/video/screenshot。既有 `setup-node` npm dependency cache 未变，不是本轮新增 browser/apt cache。
- E2E 命令仍精确列出：
  - `tests/e2e/login-page-smoke.spec.ts`
  - `tests/e2e/design-system-table-smoke.spec.ts`
  - `tests/e2e/design-system-live-query-smoke.spec.ts`
  - `tests/e2e/design-system-backtest-chart-smoke.spec.ts`
- `playwright.ci.config.ts`、frontend source、package/lock 均无 diff。
- `actions/checkout@v4`、`actions/setup-node@v4`、`actions/setup-java@v4`、`ubuntu-latest`、Node 22 与 Playwright 1.58.2 均未修改。

YAML scope 使用精确 diff、逐行缩进与 `git diff --check` 验证；本机没有可用 `actionlint`/YAML parser。job/step 目标行的结构与 GitHub Actions schema 一致，该工具缺失不是 blocker。

## Embedded Java and disposable PostgreSQL validation

- 两个 heredoc 均从当前修改后的 workflow 精确提取到 `$env:TEMP`，未在仓库创建临时 Java source。
- workflow 等价 `mvn process-classes + maven-dependency-plugin:3.8.1:build-classpath` 两次均 23/23 reactor modules SUCCESS。
- `BackendCiLegacyAccountFixture` 与 `FlywaySmoke` 均 `javac exit=0`。
- Disposable database：`postgres:16 / PostgreSQL 16.14 / 127.0.0.1:5907 / tmpfs data directory / Docker volume count=0`；使用运行时随机 test credential，未输出真实值。
- 使用两个独立 fresh database：
  - Backend fixture：V1→V34 migrate PASS、validate PASS、current=34、pending=0、legacy=1、exchange_accounts=0、credentials=0、history rows=34；
  - FlywaySmoke：V1→V34 migrate PASS、validate PASS、current=34、pending=0、helper history rows=34、SQL latest version=34。
- 容器、官方镜像首次匿名 volume 探测产物和 `$env:TEMP` Java/class 均已精确删除；最终验证容器不存在。

RCA retained：PowerShell 首次未整体引用 `-Dmdep.outputFile`，Maven 在 goal 前拒绝参数；引用后通过。官方 `postgres:16` 声明隐式 `VOLUME`，首次探测被 fail-closed 且清理；最终使用 tmpfs 满足无 volume 条件。

## Regression and governance validation

| Validation | Result |
| --- | --- |
| `npm ci` | PASS；183 packages |
| `npm run build` | PASS；Vite 8.0.3；3904 modules transformed |
| `npx playwright --version` | `1.58.2` |
| 指定 four-spec E2E | `4 passed (11.4s)` |
| `mvn -f backend/pom.xml test` | 23/23 reactor modules SUCCESS；`BUILD SUCCESS`；01:15 |
| `test-governance-workflow-lifecycle.ps1` | `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`；`PASS / TASK_EVIDENCE_POLICY_VALID` |
| `test-current-authority-next-action.ps1` | `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` |
| `check-current-authority.ps1` | `PASS / CURRENT_AUTHORITY_CONSISTENT` |
| `check-doc-links.ps1 -Roots docs/current` | 76 checked；0 errors；1 个既有 GateJ warning；PASS |
| `git diff --check` | PASS |

Backend Maven 固定 `CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`。未访问 OKX，未连接共享/生产数据库，未读取 credential。

## Immutable evidence verification

| Evidence | SHA-256 before | SHA-256 after |
| --- | --- | --- |
| `NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-01.md` | `72E58FD75339CCA661BB4AFC085D15CA516F1727E2ADBFFAA2DCE55AD070DAE1` | `72E58FD75339CCA661BB4AFC085D15CA516F1727E2ADBFFAA2DCE55AD070DAE1` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-SCHEMA-SECURITY-REVIEW.attempt-01.md` | `9404DDCCB79357DF1052D76E4815A02207081AE5235C13E3A1197BA250BF26AF` | `9404DDCCB79357DF1052D76E4815A02207081AE5235C13E3A1197BA250BF26AF` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION.attempt-01.md` | `9975B2983A7E8D07EE40BBFE44D0CA07E562E8E1667048284850A419C0A151E9` | `9975B2983A7E8D07EE40BBFE44D0CA07E562E8E1667048284850A419C0A151E9` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW.attempt-01.md` | `6852971D74874A6C70A645D239EFF9F531289E16B5ED92625A4664103BE8643E` | `6852971D74874A6C70A645D239EFF9F531289E16B5ED92625A4664103BE8643E` |
| `NQ-GOVERNANCE-POST-COMMIT-CI-FAILED-STATE-HARDENING.attempt-01.md` | `A70C035D32F87E249D5E5C7A8810C9F75965E2854563E2E34B1AAEE723F09D97` | `A70C035D32F87E249D5E5C7A8810C9F75965E2854563E2E34B1AAEE723F09D97` |
| `NQ-GATEW-3-CI-BLOCKER-FIX.attempt-01.md` | `19CFFFC81044C2869CB96376CDD3501C64F2551AF424866DBAF86EBE56C88E6D` | `19CFFFC81044C2869CB96376CDD3501C64F2551AF424866DBAF86EBE56C88E6D` |

`IMMUTABLE_DIFF_LINES=0`。未修正任何历史 evidence EOF，implementation evidence 保持不可变。

## Findings

- P0：0。
- P1：0。
- P2：root `README.md` line 9 是既有、out-of-scope 的 GateW summary drift，仍称 GateW-1 未初始化。该文件无本轮 diff，且明确不决定 current authority；按 allowlist 只记录，不修改。
- P3：GitHub Actions Node 20 runtime deprecation warning；本轮不升级 action。既有 Vite chunk-size、SLF4J/Mockito/JDK agent warning 非本轮 blocker。

## Minimal fixes and known limitations

- 最小实现修复：0；未发现需要修改 workflow implementation 的 P0/P1。
- Review 写操作只创建本 evidence，并同步已授权 current authority/index/append-only ledger；未修改 implementation evidence。
- Windows 本地结果不能证明 Ubuntu apt/mirror 已恢复。`npx playwright install --with-deps chromium` 的 30 分钟 step / 60 分钟 job timeout 只能由 fix commit exact-head GitHub CI 最终验收。
- Root README P2 需要后续单独授权的 governance/current-entry sync；不阻塞本 fix commit。

## Rollback

commit 前只回退本 review 新增 evidence、evidence index row、append-only review ledger entries和 review-state/next-action 文本；保留原 implementation 10 路径与历史 evidence。不得使用 `git reset --hard` 或 `git checkout -- .`。提交后若需回滚，创建独立 revert commit 并重新运行本 evidence 中的全部验证。

## Boundary confirmation

`NO MIGRATION CHANGE / NO BUSINESS CODE CHANGE / NO BUSINESS TEST CHANGE / NO FRONTEND SOURCE CHANGE / NO PLAYWRIGHT CONFIG CHANGE / NO PACKAGE OR LOCK CHANGE / NO RETRY OR CONTINUE-ON-ERROR / NO OKX CALL / NO API KEY / NO CREDENTIAL / NO PRIVATE ENDPOINT / NO LIVE / NO ORDER PREVIEW / NO ORDER SUBMISSION / NO CI RERUN / NO STAGE / NO COMMIT / NO PUSH / NO PR / NO TAG`。

## Authority after

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-3
work_batch_status=COMMITTED|CI_FAILED|FIX_REQUIRED
work_batch_commit=54c7bdd2caee5602441ce983b33c4cd2466ee263
work_batch_ci_run=29253811976
next_action=NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH
```

CI fix 已 review accepted，但尚未提交。当前 committed exact-head CI 仍为 FAILED；GateW-3 尚未 accepted；order preview attempt-02 尚未授权。

## Final decision

`NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW：PASS / CI_BLOCKER_FIX_ACCEPTED / READY_TO_COMMIT`。

推荐 commit message：`fix(ci): harden Flyway and Playwright setup`。

下一动作：`NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH`。
