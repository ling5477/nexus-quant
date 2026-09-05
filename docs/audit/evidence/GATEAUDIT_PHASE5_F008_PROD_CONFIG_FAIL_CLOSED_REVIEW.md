# GateAUDIT Phase5 F008 production configuration fail-closed review

## 1. Decision

```text
FAIL /
PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_REVIEW_REJECTED /
P0_0 /
P1_3 /
P2_1 /
P3_0 /
NOT_READY_TO_COMMIT
```

P5-F008 remains `REMEDIATED_PENDING_INDEPENDENT_REVIEW`; it is not `REVIEW_ACCEPTED`, `ACCEPTED`, or `CLOSED`.

Required next task：`NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REMEDIATION`。只整改本证据的3个P1与1个P2，然后重新执行独立Review。

## 2. Review independence

- Formal reviewer：fresh-context sub-agent，`fork_turns=none`。
- `did_not_implement_candidate=YES`
- Candidate `AGENTS.md` / `CLAUDE.md` / Skills used as review authority：`NO`
- Implementation evidence：仅作为`CLAIMED_RESULT / REVIEW_SUBJECT`读取，不作为通过证明。
- Review observations来自actual diff/source、Spring wiring、外部disposable candidate copies、packaged/release JAR、fresh PG16、M01～M25与repository governance。
- Reviewer未修改14个candidate文件；review evidence由主线程在before/after fingerprint一致后创建。

## 3. Repository and candidate integrity

```text
branch=audit/post-gatey-agent-baseline
HEAD=aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc
origin/audit/post-gatey-agent-baseline=aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc
origin/dev=4c19cb775ebb18b4288400a5a1a402145c2fe30a
origin/dev_ancestor=YES
candidate_files=14
expected_missing=0
unexpected=0
staged=0
candidate_fingerprint_before=2378d135c6db0d520cb9737b3906b424a4efbbaa3a519252656896d1901ac95c
candidate_fingerprint_after=2378d135c6db0d520cb9737b3906b424a4efbbaa3a519252656896d1901ac95c
candidate_changed_by_reviewer=NO
```

Expected/actual scope完全一致：4个new candidate files、10个changed candidate files；P5-F007、P5-F009、frontend、Phase6、migration、frozen archive与历史Phase5B evidence无candidate scope drift。

## 4. Findings

### P1-01 — CANONICAL_PRODUCTION_PROFILE_MIXING_ACCEPTED

- Root control：`backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/env/ProductionConfigurationApplicationContextInitializer.java:109-115`
- Exact condition：guard只检查active profiles是否“包含`prod`”，不拒绝同时存在的`local`或`test`。
- Actual reproduction：外部candidate copy以canonical marker、`prod`与Spring profile include组合启动，context成功且active profiles同时包含production与non-production profile。
- Mandatory mutation：`M25=ACCEPTED`。
- Impact：canonical production identity可装载non-production overlay，不满足production profile fail-closed contract。
- Minimal remediation：marker为true时将active profile set限制为明确批准的production set；补`prod+local`、`prod+test`、profile include/group/alias永久负例。

### P1-02 — PRODUCTION_JWT_PUBLIC_DEFAULT_SECRET

- Root control：`backend/nq-app/src/main/resources/application.yml:77-80`
- Consumers：`backend/nq-security/src/main/java/com/guidinglight/nexusquant/security/token/JwtTokenService.java:54-76`、`backend/nq-security/src/main/java/com/guidinglight/nexusquant/security/web/JwtAuthenticationFilter.java:53-64`
- Exact condition：prod继承repository-known JWT HMAC default；F008 guard未要求production override。
- Actual reproduction：独立prod context解析到repository default；使用相同runtime contract生成的synthetic ADMIN-role token被runtime parser接受，filter直接把token roles映射为Spring authorities，未发现user存在性/服务端role回查。
- Impact：production在未外部覆盖时存在authentication/authorization forgery。
- Minimal remediation：将production JWT signing secret纳入同一early secret-safe required contract；拒绝missing/blank/known default，补role-forgery与redaction负例。

### P1-03 — CI_PROD_FALLBACK_REGRESSION_NOT_ENFORCED

- Root control：`backend/nq-app/src/main/resources/application-prod.yml:5-10`
- CI sink：`scripts/ci/Test-CanonicalDeliveryWorkflow.ps1:313-378`
- Exact condition：required CI检查initializer、registration、unit与deployment contract，但未绑定prod YAML的no-fallback表达式。
- Actual reproduction：外部candidate copy分别恢复URL与username fallback后，canonical validator仍exit 0、9 jobs/25 capabilities、原F008 50 tests全绿；review-only startup实际采用fallback并成功。
- Mandatory mutations：`M04=ACCEPTED`、`M05=ACCEPTED`。
- Impact：原P5-F008 fail-open缺口可被未来改动重新引入而required CI不阻断。
- Minimal remediation：让required capability读取真实`application-prod.yml`并永久测试URL/user/password fallback restoration；mutation必须全部REJECTED。

### P2-01 — PRODUCTION_ACCOUNT_CREDENTIAL_PUBLIC_DEFAULT_KEY

- Root control：`backend/nq-app/src/main/resources/application.yml:72-75`
- Consumer：`backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java:205-220`
- Exact condition：prod继承repository-known account credential master key；该值进入pgcrypto encryption/decryption。
- Impact：若另有credential ciphertext或DB read前提，公开default削弱credential confidentiality。
- Minimal remediation：将production credential master key纳入required/default-rejected/redacted startup contract；既有ciphertext rotation另行设计，不在本Review实现。

### P0 / P3

- P0：0
- P3：0

## 5. Mandatory mutation matrix

| ID | Result | Review observation |
| --- | --- | --- |
| M01 | REJECTED | URL requirement removal blocked |
| M02 | REJECTED | Username requirement removal blocked |
| M03 | REJECTED | Password requirement removal blocked |
| M04 | **ACCEPTED** | URL fallback restoration not enforced |
| M05 | **ACCEPTED** | Username fallback restoration not enforced |
| M06 | REJECTED | Placeholder password blocked |
| M07 | REJECTED | Canonical runtime without prod blocked |
| M08 | REJECTED | Canonical runtime forced to local blocked |
| M09 | REJECTED | Direct datasource environment override validated |
| M10 | REJECTED | Command-line datasource override validated |
| M11 | REJECTED | JSON datasource override validated |
| M12 | REJECTED | JVM system datasource override validated |
| M13 | REJECTED | JDBC URL credential identity blocked |
| M14 | REJECTED | Independent Flyway identity blocked |
| M15 | REJECTED | JNDI datasource path blocked |
| M16 | REJECTED | Alternate datasource type blocked |
| M17 | REJECTED | Registration removal blocked |
| M18 | REJECTED | Registration corruption blocked |
| M19 | REJECTED | Deferred guard caused targeted test failures |
| M20 | REJECTED | Raw resolved value in failure caused redaction tests to fail |
| M21 | REJECTED | CI ownership weakening blocked |
| M22 | REJECTED | Conditional backend regression blocked |
| M23 | REJECTED | Continue-on-error/failure-ignore blocked |
| M24 | REJECTED | Canonical marker removal blocked |
| M25 | **ACCEPTED** | Marker + prod + non-production included profile accepted |

Summary：`25/25 EXECUTED / 22 REJECTED / 3 ACCEPTED`。任一accepted security bypass足以拒绝F008 Review。

## 6. Effective datasource, ordering, packaging and secret observations

- Effective consumer：Spring Environment → Binder → Boot DataSourceProperties/Hikari；generic properties、relaxed aliases、Hikari/JNDI/custom/XA与Flyway identities已独立检查。
- Corrected packaged executable JAR：M09～M12均约1秒内以`PROD_CONFIGURATION_INVALID`退出，`application started=false`。
- Canonical TEST_ONLY release JAR与built JAR bytes/SHA一致；release JAR自动发现initializer并同样fail closed。
- Startup ordering：invalid config在DataSource construction/connect前拒绝；M19被targeted tests捕获。
- JDBC URL：malformed/non-PG/userinfo/credential/host/port/dbname/service identity paths按当前tests与review mutations拒绝；legitimate PostgreSQL forms未发现blocking regression。
- Flyway：prod `enabled=false`保持；独立URL/user/password拒绝；未重新设计Phase5B migration ownership。
- Secret review：corrected packaged/release logs、exceptions与原candidate reports中的unique synthetic review value raw occurrences=`0`；M20被redaction tests拒绝。本review evidence不记录synthetic或真实secret值。
- Systemd：direct Java `ExecStart`、marker/prod显式、`EnvironmentFile`保持external、command line无DB credential、无shell interpolation。
- Phase5B contract：PostgreSQL 16、release admission、immutable release、atomic activation、rollback authorization与restore proof字段未被F008改写。

## 7. Executable validation

| Validation | Independent result |
| --- | --- |
| Original F008 targeted | `50 tests / 0 failures / 0 errors / 0 skips / 23 reactor modules SUCCESS` |
| Full Maven with fresh disposable PG16.15 | `23/23 modules BUILD SUCCESS / nq-app 376 / 0 failures / 0 errors / 35 existing skips` |
| Required PG16 app-context smoke | `1/1 PASS` |
| Required PG16 repository smoke | `1/1 PASS` |
| Canonical delivery validator | `9 jobs / 25 capabilities / missing 0 / unknown 0 / PASS` |
| Existing canonical mutation suite | `72/72 REJECTED` |
| Packaged/release JAR initializer discovery | `PASS` |
| M01～M25 | `25 executed / 22 rejected / 3 accepted` |
| `git diff --check` | PASS（仅line-ending warning） |
| Frontend E2E | `NOT_REQUIRED / NOT_RUN` |
| Remote exact-head CI | `NOT_RUN` |

Local review procedure RCA：一条discarded packaged-JAR helper误用了PowerShell保留变量，导致首次M09未收到prod/marker参数并短暂以默认local连接本机PG17.7/5432；日志显示Flyway仅validate V46、no migration，约5秒后精确终止。该命令不计验证PASS，可能发生普通startup read，未观察到migration或外网。后续使用非保留参数名、profile/guard assertions与fresh isolated PG16完成纠正验证。

Review containers与external mutation fixtures最终residue=`0`。Codex Security scan artifact目录由workbench保留，不属于candidate或临时mutation residue。

## 8. Codex Security artifacts

- Scan ID：`ecaf2305-7f69-4314-8208-bdd98b011c1b`
- Target digest：`codex-security-snapshot/v1:sha256:7cc627ef1d226734b037dfbd8c584d2cb4a00fcdfe17514e79e23a5e5e280da9`
- Report：`C:\Users\Lingyu\AppData\Local\Temp\codex-security-scans-0RZ03e\nexus-quant-gateaudit\aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc_20260902T131310Z_c0bknhae\report.md`
- Findings：3 high / 1 medium；coverage=`complete`。
- TAC advisory：`USER_NOT_LOGGED_IN / unable to verify`；不作为授权或scan gate。
- Token usage：`unavailable`，不估算。

## 9. Authority and Git disposition

Review失败路径遵循existing repository precedent：candidate源码与`docs/current/STATUS.md` authority保持不变，不在Review中伪造新的machine lifecycle status。

```text
accepted_batch=GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
machine_next_action=NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW
review_required_next_action=NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REMEDIATION
```

`machine_next_action`暂不改写，因为当前通用governance lifecycle没有`REVIEW_REJECTED`状态；不得用`CI_FAILED`伪装未运行的CI，也不得添加Task-ID-specific exception。Remediation任务应在现有machine contract允许的状态迁移内先处理该治理表达，再实现本证据的最小修复。

```text
staged=0
commit=NONE
push=NONE
reviewer_candidate_modifications=0
```
