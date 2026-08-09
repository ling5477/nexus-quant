# NQ-GATEW-3-CI-BLOCKER-FIX Attempt 01

## Task classification

`CI_BLOCKER_FIX + DYNAMIC_FLYWAY_VALIDATION + PLAYWRIGHT_TOOLCHAIN_TIMEOUT_HARDENING + CURRENT_AUTHORITY_RECONCILIATION + LOCAL_CI_REPRODUCTION + REGRESSION_TESTS + TASK_EVIDENCE`。

结论：`IMPLEMENTED / PENDING_REVIEW`（已实现 / 待独立复核）。本 evidence 不表示 fix 已 commit/push，不表示 GitHub Actions 已 rerun，也不表示 GateW-3 已 `ACCEPTED|CI_GREEN`。

## Scope

- NQ-only；workflow 修改仅限 `.github/workflows/ci.yml` 中两个 embedded Java helper 与 `frontend-no-backend-e2e` timeout。
- current-control 同步仅限用户 allowlist 内的 `docs/current/**` 与本 attempt evidence。
- 未修改 V34 migration、backend/frontend/research 业务代码或测试、Playwright config、package/lock、governance contract/checker、其他 workflow、archive 或 Gate evidence。

## Starting facts and exact-head CI

- Starting branch/worktree：`dev`、clean、staged empty。
- Starting HEAD：`54c7bdd2caee5602441ce983b33c4cd2466ee263`，且 `HEAD == origin/dev`；commit subject 为 `fix(governance): model post-commit CI failures`。
- Current failed run：`29253811976 / completed / failure / headSha=54c7bdd2caee5602441ce983b33c4cd2466ee263 / NQ CI Baseline / push`。
- Previous failed implementation commit/run：`8b54adc6952775dc1a939aad7b0ae849f20f42cf / 29241698510 / completed / failure`。旧 run 的 Diff check 因 migration conformance evidence EOF 空行失败；current run `29253811976` 的 Diff check 已通过，该 finding 未再出现。本轮不修改历史 evidence、不 amend/force-push 历史提交。

## Authority before and reconciliation

Before：`accepted_batch=GateW-2 / ACCEPTED|CI_GREEN`；`active_gate=GateW / IN_PROGRESS|NOT_FROZEN`；`work_batch=GateW-3 / COMMITTED|CI_FAILED|FIX_REQUIRED / 8b54adc6952775dc1a939aad7b0ae849f20f42cf / 29241698510`；`next_action=NQ-GATEW-3-CI-BLOCKER-FIX`。

After：`accepted_batch=GateW-2 / ACCEPTED|CI_GREEN`；`active_gate=GateW / IN_PROGRESS|NOT_FROZEN`；`work_batch=GateW-3 / COMMITTED|CI_FAILED|FIX_REQUIRED / 54c7bdd2caee5602441ce983b33c4cd2466ee263 / 29253811976`；`next_action=NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW`。

After 只登记 latest committed exact-head failed CI；本工作区 fix 尚未形成 commit，故不得写成 `COMMITTED|CI_PENDING` 或 `ACCEPTED|CI_GREEN`。GateW-3 尚未 accepted，order preview attempt-02 仍未授权。

## Failure A: generic Flyway helpers

### Root cause and original locations

- `BackendCiLegacyAccountFixture` 原 workflow line 367：`EXPECTED_VERSION = "33"`。
- `FlywaySmoke` 原 workflow line 558：`EXPECTED_VERSION = "33"`。
- run `29253811976` 两条路径均先打印 `Successfully applied 34 migrations ... now at version v34` 与 `Successfully validated 34 migrations`，随后抛出 `Expected Flyway current version 33 but got 34`。根因是通用 helper 固定 latest schema version，不是 V34 migration failure。

### Dynamic Flyway contract

两个 helper 均继续：

1. `flyway.migrate()`；
2. `flyway.validate()`；
3. `flyway.info().current()` 非 null 且 version 非 null；
4. `flyway.info().pending().length == 0`；
5. `cleanDisabled=true`、`outOfOrder=false`、`baselineOnMigrate=false` 保持不变；
6. 从 Flyway current info 动态取得 `currentVersion`，不从 migration 文件名推导、不固定 V34。

`BackendCiLegacyAccountFixture` 继续插入唯一 CI legacy PAPER/ACTIVE account，并断言 legacy row=1、`exchange_accounts` row=0、credential rows=0；输出改为动态 `Prepared backend CI legacy account fixture after Flyway V<currentVersion>`。fixture 数据与 credential 边界未改变。

`FlywaySmoke` 继续输出完整 `flyway_schema_history`，保留关键 schema/migration、legacy compatibility、account/exchange-account/credential 与 validate 路径；只删除 latest-version 固定数字，输出改为动态 `Flyway empty database smoke reached V<currentVersion>`。

专项 `VenueRuleFactsPostgresIntegrationTest` 的 V1→V34、V33→V34 路径和 `VenueRuleFactsMigrationContractTest` 的 V34 contract 保持未修改；未机械替换 backend 中的 V33/V34。

## Failure B: Playwright toolchain timeout

### Root cause

`frontend-no-backend-e2e` 原 job timeout 为 15 分钟。run `29253811976` 的 `Install Playwright Chromium` 从 `13:27:28Z` 运行到 `13:42:20Z`；Ubuntu mirror 仍在下载 `fonts-ipafont-gothic`、`fonts-freefont-ttf`、`fonts-tlwg-loma-otf`、`fonts-unifont` 与 `fonts-wqy-zenhei` 时出现 `The operation was canceled`。后续 build/E2E 被跳过，`if: always()` cleanup 成功执行。

### Minimal timeout hardening

- Job timeout：15 → 60 分钟，为依赖安装、build、4-spec E2E 与 cleanup 保留有界总预算。
- `Install Playwright Chromium` step timeout：新增 30 分钟；安装失败或超时继续 fail-closed。
- 安装命令保持 `npx playwright install --with-deps chromium`：继续只安装 Chromium，并显式安装 Linux 系统依赖；不得用 runner 预装库替代该边界。
- 未增加 cache、container/Docker image、retry、`continue-on-error`、artifact、spec、runner/action/package/Playwright 升级；这些方案会引入新的依赖与证据边界，超出本轮已确认 RCA。
- `Node.js 20 deprecated / actions/checkout@v4` 仅记录为 P3，不在本轮升级 action 或 Node。

## Immutable evidence baseline

| Evidence | SHA-256 before | SHA-256 after |
| --- | --- | --- |
| `NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-01.md` | `72E58FD75339CCA661BB4AFC085D15CA516F1727E2ADBFFAA2DCE55AD070DAE1` | `72E58FD75339CCA661BB4AFC085D15CA516F1727E2ADBFFAA2DCE55AD070DAE1` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-SCHEMA-SECURITY-REVIEW.attempt-01.md` | `9404DDCCB79357DF1052D76E4815A02207081AE5235C13E3A1197BA250BF26AF` | `9404DDCCB79357DF1052D76E4815A02207081AE5235C13E3A1197BA250BF26AF` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION.attempt-01.md` | `9975B2983A7E8D07EE40BBFE44D0CA07E562E8E1667048284850A419C0A151E9` | `9975B2983A7E8D07EE40BBFE44D0CA07E562E8E1667048284850A419C0A151E9` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW.attempt-01.md` | `6852971D74874A6C70A645D239EFF9F531289E16B5ED92625A4664103BE8643E` | `6852971D74874A6C70A645D239EFF9F531289E16B5ED92625A4664103BE8643E` |
| `NQ-GOVERNANCE-POST-COMMIT-CI-FAILED-STATE-HARDENING.attempt-01.md` | `A70C035D32F87E249D5E5C7A8810C9F75965E2854563E2E34B1AAEE723F09D97` | `A70C035D32F87E249D5E5C7A8810C9F75965E2854563E2E34B1AAEE723F09D97` |

`IMMUTABLE_DIFF_LINES=0`。

## Validation evidence

| Validation | Result | Scope / evidence |
| --- | --- | --- |
| Git/exact-head CI | PASS | `dev` clean/staged empty；`HEAD == origin/dev == 54c7bdd...`；run `29253811976` metadata/log 与 failure RCA 已核验 |
| Embedded Java extraction | PASS | 两个 source 均从修改后 `.github/workflows/ci.yml` 真实 heredoc 提取到 `$env:TEMP`；未手写替代类 |
| `BackendCiLegacyAccountFixture` compile | PASS | workflow 等价 `process-classes` + runtime classpath；source SHA-256 `6B67A23444A9E152BE34A0B9FD333A27184E159D700311D1EE9D66C36932052C` |
| `FlywaySmoke` compile | PASS | workflow 等价 `process-classes` + runtime classpath；source SHA-256 `119A457FC2DD9BDA0CFD9A8655A42834B1C018234CB67AE7FC1CBFA06FE005E9` |
| Disposable PostgreSQL | PASS | PostgreSQL 16.14；无 volume；`127.0.0.1` 随机端口；两个容器完成后均删除 |
| Fresh migration / validate | PASS | empty schema V1→V34；34 migrations applied/validated；current version=34；pending=0 |
| Backend fixture runtime | PASS | dynamic Flyway V34；legacy fixture rows=1；exchange-account rows=0；credential rows=0 |
| Flyway smoke runtime | PASS | dynamic Flyway V34；schema history rows=34；pending=0 |
| Frontend install | PASS | `npm ci`；按现有 lock 安装 183 packages；package/lock 未修改 |
| Frontend build | PASS | Vite 8.0.3；3904 modules transformed；production build success |
| Playwright | PASS | version 1.58.2；四个 allowlisted Chromium spec `4 passed (20.8s)` |
| Full Maven | PASS | `mvn -f backend/pom.xml test`；23/23 reactor modules SUCCESS；`BUILD SUCCESS`；01:08 |
| Governance regression | PASS | `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`、`PASS / TASK_EVIDENCE_POLICY_VALID`、`PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` |
| Authority/doc links | PASS | `PASS / CURRENT_AUTHORITY_CONSISTENT`；`PASS / DOC_LINKS_VALID`，76 links、0 errors、1 个既有 GateJ historical warning |
| Static/scope/immutable | PASS | 10-file exact allowlist；staged empty；`git diff --check`；workflow fixed-version matches=0；Batch 5A timeout contract PASS；forbidden-scope diff=0；`IMMUTABLE_DIFF_LINES=0`；attempt evidence single EOF newline |

### Validation RCA retained

1. 首次 Maven classpath 准备因 PowerShell 未整体引用 `-Dmdep.outputFile=...`，Maven 在执行 goal 前报 `Unknown lifecycle phase ".outputFile=..."`；整体引用两个 `-D` 参数后成功。
2. 首次 heredoc 提取器错误地要求空行也具有十空格缩进，在 workflow line 362 fail-closed；修正为仅允许零长度空行或十空格非空行后，两类真实 source 编译通过。
3. 首个附加 SQL 用 `max(version)` 对 Flyway 文本版本做字典序聚合，返回 `9`；该查询不参与 helper 判定。新 disposable 空库使用 `ORDER BY installed_rank DESC LIMIT 1` 纠正为 `CURRENT_VERSION=34`，并再次证明 34 rows、pending=0。

## Known limitations

- Windows 本地验证证明现有 E2E spec 与 Chromium 运行；没有、也不能把 Windows 结果冒充 Ubuntu `--with-deps` apt/mirror 验证。
- 本轮未 rerun GitHub Actions。只有 review 接受后的 fix commit exact-head CI 才能最终证明 Ubuntu system dependency 安装在 30/60 分钟预算内完成。
- Latest committed exact-head CI 仍是 `29253811976 / failure`；GateW-3 仍未 accepted。

## Boundary confirmation

`NO MIGRATION CHANGE / NO BUSINESS CODE CHANGE / NO BUSINESS TEST CHANGE / NO FRONTEND SOURCE CHANGE / NO PACKAGE CHANGE / NO API CHANGE / NO OKX CALL / NO API KEY / NO CREDENTIAL / NO PRIVATE ENDPOINT / NO LIVE / NO ORDER PREVIEW / NO ORDER SUBMISSION / NO CI RERUN`。

Maven 环境固定 `CI=true`、`NQ_NO_OUTBOUND=true`、`NQ_AI_ENABLED=false`、`NQ_DH_RUNTIME_ENABLED=false`、`NQ_REAL_EXCHANGE_ENABLED=false`。未连接共享或生产数据库；未读取 credential 文件或输出 secret。

## Findings

- P0：0。
- P1：0。
- P2：0；Ubuntu apt/mirror 最终结果属于 fix commit CI 待证事实，不是已验证 defect。
- P3：GitHub runner 报 `Node.js 20 is deprecated`，涉及 `actions/checkout@v4` 与 `actions/setup-node@v4`；按任务边界不升级。

## Rollback

review/commit 前可只回退本任务实际 allowlist diff：恢复两个 helper 的原代码与 Batch 5A 15 分钟 timeout，并删除本 attempt evidence/current-control 同步；不得使用 `git reset --hard`、`git checkout -- .` 或删除历史 evidence。提交后如需回滚，应创建独立 revert commit，并重新执行 embedded Java、PostgreSQL、frontend、Maven、governance 与 exact-head CI。

## Next action

`NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW`。
