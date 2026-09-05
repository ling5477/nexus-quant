# GateAUDIT Phase5 F008 production configuration fail-closed implementation evidence

## 1. Result and baseline

```text
result=IMPLEMENTED / PHASE5_F008_PROD_CONFIG_FAIL_CLOSED_CANDIDATE_COMPLETE
finding=P5-F008 / REMEDIATED_PENDING_INDEPENDENT_REVIEW
P0=0
P1=0
P2=0
P3=0
formal_next_action=NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW
commit=NONE
push=NONE
```

- Branch：`audit/post-gatey-agent-baseline`
- Baseline HEAD / origin：`aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc`
- `origin/dev`：`4c19cb775ebb18b4288400a5a1a402145c2fe30a`，是 baseline HEAD 的 ancestor。
- Starting worktree / staged：`CLEAN / 0`
- Starting authority：accepted pair保持 `GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE / ACCEPTED|CI_GREEN`；F008 work batch 为 `NOT_STARTED`，next action 为本 implementation。
- 安全边界：未访问生产服务器、生产 PostgreSQL、production `runtime.env`、credential manager、真实凭证或真实交易所；LIVE/private trading/PLACE/CANCEL/transfer/withdraw 全部为 0。

## 2. Configuration inventory

| Property / path | Source | Profile | Default / fallback | Effective consumer | Precedence | Secret | Prod required | Before behavior | Disposition |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `spring.profiles.active` | `application.yml` via `NQ_PROFILE` | global | `local` | Spring ConfigData | lower than command line | no | canonical runtime requires `prod` | canonical unit未固定profile，可静默使用local | systemd command line固定`prod` |
| `nq.production-configuration` | canonical systemd `ExecStart` | canonical prod | none | startup guard | command line | no | yes for canonical runtime | absent | fixed `true` marker；缺少prod profile时拒绝 |
| `spring.datasource.url` | `application-prod.yml` via `NQ_PROD_DB_URL`，或 Spring externalized source | prod | unsafe `db-prod` fallback | Boot `DataSourceProperties` → Hikari | Binder最终effective precedence | no | yes | 缺少变量仍得到貌似有效URL | no fallback；required、PostgreSQL parser、host/path/query校验 |
| `spring.datasource.username` | `application-prod.yml` via `NQ_PROD_DB_USER`，或 Spring externalized source | prod | unsafe default username | Boot `DataSourceProperties` → Hikari | Binder最终effective precedence | no | yes | 缺少变量仍得到默认user | no fallback；required/nonblank/no edge whitespace |
| `spring.datasource.password` | `application-prod.yml` via `NQ_PROD_DB_PASSWORD`，或 Spring externalized source | prod | `change_me` | Boot `DataSourceProperties` → Hikari | Binder最终effective precedence | yes | yes | 缺少变量仍得到placeholder | no fallback；required/nonblank/no edge whitespace/no placeholder |
| `spring.datasource.driver-class-name` | `application-prod.yml` | prod | fixed PostgreSQL driver | Boot `DataSourceProperties` | Binder最终effective precedence | no | yes | 未纳入F008校验 | 必须保持canonical PostgreSQL driver |
| `spring.datasource.hikari.*` identity、JNDI/custom type/XA identity | CLI/system/env/JSON/config imports | prod | none in repository | Hikari second binding / JNDI / custom DataSource | 可在通用DataSourceProperties之后生效 | password fields are secret | forbidden | 可绕过通用三项校验 | 同一guard通过Binder relaxed binding拒绝 |
| `spring.flyway.url/user/password` | optional Spring externalized source | prod | repository无独立值 | Flyway | externalized precedence | password is secret | forbidden | 可形成独立连接身份 | prod下显式拒绝；Flyway继承validated DataSource |
| `spring.flyway.enabled` | `application-prod.yml` | prod | `false` | Flyway auto-configuration | normal Spring precedence | no | no | false | unchanged；F008不重设migration ownership |
| `NQ_DB_URL/USER/PASSWORD` | `application-local.yml` | local | localhost developer defaults | local DataSource | local profile only | password is local-only | no | local convenience | unchanged；prod placeholders保持更高profile source且无fallback |
| `SPRING_DATASOURCE_*` / CLI / JVM system properties / `SPRING_APPLICATION_JSON` | Spring externalized configuration | any | none | same effective `spring.datasource.*` | Spring/Binder precedence | password is secret | allowed only through canonical keys | 未统一验证 | 统一进入同一Binder-based guard |

CI inventory：`Backend regression` 使用 disposable PostgreSQL 16，并在 Maven step 内把 `NQ_DB_*` 映射为 `SPRING_DATASOURCE_*`；未使用prod profile，因此不要求production credentials。Required jobs继续为9个，名称不变。

## 3. Effective datasource and fail point

- Guard：`ProductionConfigurationApplicationContextInitializer`，通过 `META-INF/spring.factories` 注册。
- Activation：active profile包含`prod`时验证datasource；canonical production marker为`true`但prod profile缺失时直接拒绝。
- Property model：所有scalar由Spring Boot `Binder`读取，覆盖CLI、system properties、环境变量、`SPRING_APPLICATION_JSON`、profile/import及relaxed aliases；不是未消费的自定义properties façade。
- Alternate identity：拒绝Hikari `jdbc-url/username/password/driver/data-source-class/data-source-jndi/data-source-properties`、top-level JNDI/custom type、XA identity/properties。
- URL：要求`jdbc:postgresql://`，无userinfo；用当前PostgreSQL driver `parseURL`做无网络语法解析，并额外拒绝空host、非法port、多段/空白DB path，以及URL中的credential、host、port、dbname、service override参数。
- Fail point：initializer在application context refresh及DataSource/Hikari/Flyway/repository bean创建前执行。负例CountingDataSource的constructor/connection计数均为0，因此DNS/TCP/Flyway DB attempt为0。
- Error：统一为`PROD_CONFIGURATION_INVALID`加property name/classification；不拼接配置值或nested cause。

## 4. Production negative matrix

| Case | Result | Outbound |
| --- | --- | --- |
| URL missing / blank / whitespace | REJECTED | DataSource construct=0，connect=0 |
| USER missing / blank / whitespace | REJECTED | DataSource construct=0，connect=0 |
| PASSWORD missing / blank / whitespace / placeholder | REJECTED | DataSource construct=0，connect=0 |
| malformed / non-PostgreSQL URL | REJECTED | DataSource construct=0，connect=0 |
| empty host / out-of-range port / blank DB / multi-segment DB path | REJECTED | DataSource construct=0，connect=0 |
| URL userinfo / credential query | REJECTED | DataSource construct=0，connect=0 |
| URL host/port/dbname/service query override，包括PG aliases | REJECTED | DataSource construct=0，connect=0 |
| Hikari relaxed/camel aliases、JNDI/custom type/XA identity/maps | REJECTED | before DataSource creation |
| independent Flyway URL/user/password | REJECTED | Flyway attempt=0 |
| canonical production marker + prod profile absent | REJECTED | local DataSource construct/connect=0/0 |

`sslmode=require`等不改变连接身份的PostgreSQL URL option仍可通过；未建立hostname allowlist。

## 5. Positive and regression matrix

- Synthetic prod：`prod` + marker + synthetic PostgreSQL URL/user/password，通过配置解析；不创建DataSource、不连接数据库。
- Canonical env mapping：synthetic `NQ_PROD_DB_*`经`application-prod.yml`实际映射至`spring.datasource.*`并通过，不是unused validation。
- Effective precedence：较高优先级canonical Spring properties覆盖低优先级旧值后通过；高优先级blank值拒绝。
- Local/test/CI：无production marker/profile时不要求prod credentials；`application-local.yml`未修改，full reactor中的local/test/CI contexts通过。
- PG16：cached digest-pinned PostgreSQL 16.15 disposable container；fresh V1→V46成功，pending=0语义保持；full backend与required app-context smoke通过。
- Flyway：prod保持`enabled=false`；F008未改变Phase5B独立migration/restore sequence。
- Secret safety：异常、captured logs、Surefire结果和本evidence不包含synthetic secret值；只记录property names与redacted classification。

## 6. Canonical deployment and CI

- `nq-canonical.service`保留`EnvironmentFile=/etc/nexus-quant/runtime.env`；`ExecStart`使用最高优先级command-line固定production marker与prod profile。
- `deployment-contract.json`只记录required key names、external injection、no alternate identity/no split-brain/no secret bundling及fail-closed语义；未记录secret value。
- 未实现第二套shell secret validator：Java guard是mandatory single source；deployment contract/CI validator只验证canonical identity和required key names。
- `production-configuration-fail-closed` capability owner为现有required `Backend regression` full Maven step；未新增job。
- Canonical validator同时固定initializer、registration、test和systemd/contract形状；backend step的removed/conditional/soft-fail/failure-ignored mutations全部拒绝。
- Required jobs=`9`，job names changed=`0`，critical capabilities=`25 / missing 0 / unknown 0`，mutation suite=`72/72 REJECTED`。

## 7. Independent security-focused review

首轮只读review：`FAIL / P0_0 / P1_2 / P2_2`。发现Hikari/JNDI后绑定旁路、URL parser/query identity旁路、initializer ordering trust boundary与CI owner名实不符。

整改后独立复审：`PASS / P0_0 / P1_0 / P2_0 / P3_0`。Reviewer使用无网络PoC确认：relaxed/camel scalar、nested maps、Hikari/JNDI/custom/XA、generic driver、URL credential/host/port/dbname/service overrides全部REJECT；CI owner与72 mutations匹配真实backend consumer。该task-local review不替代下一正式governance REVIEW action。

## 8. Validation ledger

| Command / check | Final result |
| --- | --- |
| Targeted F008 + env safety Maven | `60 tests / 0 failures / 0 errors / 0 skipped / BUILD SUCCESS` |
| `mvn -f backend/pom.xml test` with exact CI-safe env + fresh disposable PG16 | `23/23 modules SUCCESS / nq-app 376 tests / 0 failures / 0 errors / 35 existing skips / BUILD SUCCESS` |
| Required `NqAppContextPostgresSmokeTest` on disposable PG16 | `1/1 PASS / BUILD SUCCESS` |
| `Test-NqCanonicalRelease.Tests.ps1` under `pwsh` | `66 cases PASS` |
| `Test-CanonicalDeliveryWorkflow.ps1` | `PASS / required jobs 9 / critical capabilities 25` |
| `Test-CanonicalDeliveryWorkflow.Tests.ps1` | `72/72 mutations REJECTED` |
| PowerShell parser | `2 files / 0 errors` |
| Independent security-focused re-review | `PASS / P0_0 / P1_0 / P2_0 / P3_0` |

Preserved RCA：首次targeted命令被Windows PowerShell错误拆分未进入测试；首次full Maven误触本机已有PG17/V44不一致库，随后停止使用该库；首次disposable full Maven多注入三个禁止出现的env names，NoOutbound guard正确失败；PS5.1把fixture CRLF warning升格为NativeCommandError。最终验证均使用fresh disposable PG16与CI同款env/pwsh，未清理、修改或继续访问本机已有数据库。

## 9. Scope and authority after

- F008 source/config/test/deployment/CI contract与本证据/current owner docs：changed。
- P5-F007、P5-F009、frontend、Phase6、migration files、GateY frozen archive、历史Phase5B evidence：unchanged。
- Production/LIVE/credential/private provider/exchange：not accessed。
- Disposable container：task-specific exact name；测试后不存在。

```text
accepted_batch=GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-REVIEW
```
