# Current Runbook

## 1. 本地 PostgreSQL 5432 规则

- 本地开发统一使用 PostgreSQL `5432`。
- `.env.example` 默认 `NQ_DB_PORT=5432`。
- `docker-compose.yml` 默认映射 `${NQ_DB_PORT:-5432}:5432`。
- `application-local.yml` 默认连接 `localhost:5432`。

## 2. docker-compose 启动 PostgreSQL

```powershell
docker compose up -d postgres
docker compose ps postgres
```

预期：`nexusquant-postgres` 健康检查通过，宿主机 `5432` 可连接。

## 3. 本机 PostgreSQL 已存在时

- 如果本机已有 PostgreSQL 占用 `5432`，优先复用本机服务。
- 复用本机服务时不要重复启动 `docker-compose postgres`。
- 如需改端口，只允许通过本机 `.env` 设置 `NQ_DB_PORT`，不要修改仓库默认值。

## 4. 后端 local profile 启动

```powershell
mvn -f backend/pom.xml -pl nq-app spring-boot:run -Dspring-boot.run.profiles=local
```

启动后检查：

```powershell
Invoke-RestMethod http://localhost:18888/actuator/health
```

## 5. 前端启动

```powershell
Set-Location frontend
npm ci
npm run dev
```

## 6. Python research 验证

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

## 7. 常见问题

- `5432` 端口被占用：确认本机 PostgreSQL 是否已运行；若已运行则复用，若要使用 Docker 需先释放端口或仅在本机 `.env` 临时覆盖。
- Flyway migration 失败：检查 DB 是否为空库、migration 是否重复执行、当前连接的 `NQ_DB_NAME` 是否正确。
- npm 依赖缺失：在 `frontend` 下执行 `npm ci`。
- Playwright 浏览器未安装：在 `frontend` 下执行 `npx playwright install chromium`。
- `nq-app` 无法连接 DB：检查 `NQ_DB_URL`、`NQ_DB_PORT`、`NQ_DB_NAME`、`NQ_DB_USER`、`NQ_DB_PASSWORD`。
- `/api/auth/login` 失败：确认后端已启动、DB migration 已完成、local admin 用户配置与认证数据源一致。
- `/api/auth/me` 失败：确认请求携带 `<redacted-authorization-header-example>`，并先通过 `/api/auth/login` 获取 token。

<!-- nq-stage-history:start -->

## 8. GateAUDIT accepted boundary records

Phase5A、Phase5B、F008与F007已由各自immutable technical pair接受；F009已ACCEPTED / CLOSED，F001 remote enforcement已ACCEPTED / CLOSED（ruleset 22381941，dev effective 9/9），Phase5 ACCEPTED / CLOSED。

GateAUDIT 当前阶段、accepted/work batch、Phase7 状态与唯一 next action 必须从 [STATUS.md](STATUS.md) 的 machine authority 读取，[ROADMAP.md](ROADMAP.md) 只解释下一允许工作。RUNBOOK 不复制或覆盖动态 lifecycle 值。

Authority reconciliation 和后续 docs-only 同步运行以下一致性检查：

```powershell
git status --short
git branch --show-current
git diff --check
git diff --stat
```

本节不运行真实交易所 HTTP / WebSocket，不读取 credential material，不启动 LIVE，不接 AI / DH runtime。F008/F007/F009 与 C1 保持已接受；C2=`ACCEPTED / CLOSED`，固定 pair=`612c2f5887a2e6b3a8b3138d9ae9b193c20e298f / 34183851797`。Phase6 L4/L5/L6已接受，Phase6=`ACCEPTED / COMPLETE`。当前 repository schema=`V51`；历史 Phase5B V46、C1 V47 的接受事实仍按原证据保留，不推断生产 schema。本节不重跑 C2/PostgreSQL、Full Maven 或 Playwright，也不重新执行已接受的 L4/L5/L6 qualification。

<!-- nq-stage-history:end -->

## 9. Canonical production configuration

- Canonical systemd unit在`ExecStart`中固定`nq.production-configuration=true`和`spring.profiles.active=prod`；不要移入可变`runtime.env`，也不要用`NQ_ENVIRONMENT=SIM`代替production identity。
- `/etc/nexus-quant/runtime.env`必须由外部部署系统以最小读取权限提供`NQ_PROD_DB_URL`、`NQ_PROD_DB_USER`、`NQ_PROD_DB_PASSWORD`、`NQ_SECURITY_SECRET`、`NQ_ACCOUNT_CREDENTIALS_MASTER_KEY`；禁止把值写入release bundle、deployment contract、unit、日志或evidence。
- Spring展开include/group后的active profile set必须恰好为`{prod}`；prod与local/test/ci或任意其他profile组合均拒绝。prod即使未设置marker或marker=false也执行此校验。普通local启动保持原行为。
- `nq.security.secret`和`nq.account.credentials.master-key`允许由既有Spring externalized sources提供；最终effective值必须非空、无未解析占位符、无首尾空白且非repository-known default。密钥轮换、key-version与历史ciphertext迁移不包含在F008整改中。
- Canonical prod YAML采用单文档block mapping/scalar格式；CI直接验证五项required placeholder，无fallback。新增YAML合并、alias或其他格式前须扩展对应语义验证，当前checker对不支持的格式拒绝。
- Production datasource只允许canonical `spring.datasource.url/username/password/driver-class-name` effective contract。禁止通过Hikari-specific identity、JNDI、custom/XA DataSource或独立`spring.flyway.url/user/password`建立第二连接身份。
- 缺失、空白、非法或旁路配置必须在DataSource/Flyway bean创建前以`PROD_CONFIGURATION_INVALID`失败；不得等待DNS、TCP、authentication或Flyway network failure。

## 10. Canonical operational entrypoints

release只使用`../../scripts/deployment/New-NqCanonicalRelease.ps1`；verify与外部admission使用同目录`Test-NqCanonicalRelease.ps1`和`Test-NqCanonicalReleaseAdmission.ps1`。install、install-for-bootstrap、bootstrap-current、activate、rollback、recover统一由`Install-NqCanonicalRelease.ps1`的既有Action contract承担；恢复使用`Invoke-NqCanonicalRestoreDrill.ps1`与PG16 contract。当前生产unit唯一为`../../deploy/canonical/nq-canonical.service`。本节是路径索引，不授予部署或真实服务操作权限。

受控readonly生产图测试使用既有`scoped-okx-private-readonly` runtime mode，环境字段为`NQ_READONLY_DB_URL/USER/PASSWORD`、`NQ_RELEASE_ID`、`NQ_SOURCE_COMMIT`、`NQ_RELEASE_MANIFEST_SHA256`；值必须从显式测试或外部授权环境提供。无连接fixture只能验证该mode的本地边界；它不是第二条生产部署路径，不能与prod组合。所有历史stage profile和旧stage capability key均拒绝，普通local/test/ci/prod/paper语义不变。

该 scoped runtime 的 `NQ_RELEASE_ID` 只接受 `nq-<12 位小写源码前缀>-<16 位小写制品身份>`，`NQ_SOURCE_COMMIT` 保留完整 40 位小写 Git commit，且前 12 位必须与 release ID 匹配；`NQ_RELEASE_MANIFEST_SHA256` 必须为 64 位小写摘要。诊断分别回读三者。此检查仅证明身份结构一致，完整制品真实性仍由 manifest、外部 `EXACT_HEAD_CI` admission 与 canonical installer 验证；raw SHA 和 `nq-test-*` 不能作为该生产 runtime 的 release ID。

账户事实观察若使用服务器现有 TRADE 凭证，只能在上述单一 scoped profile、显式只读功能开关及 LIVE/真实交易 provider 关闭条件下人工触发。先用既有 `permission-probe` 只读 OKX config 并刷新本地脱敏权限元数据；仅 `SUCCEEDED / TRADE / WITHDRAW=false / IP PASSED` 且未过期时，账户事实入口才解密绑定凭证，并再次用固定 GET 核对远端权限。普通 diagnostics profile 仍只接受 READ_ONLY 凭证。canonical prod unit 固定 `{prod}`，不能通过叠加 profile 执行此观察；在服务器启动 scoped runtime 或切换 release 均属于需单独授权的部署动作。

当前生产已完成一次性 bootstrap，后续不得重复 bootstrap。2026-09-27 的新只读资格验证已通过：`REAL_ACCOUNT_READONLY_FACTS_QUALIFIED`。coverage unblock 技术 PR #40 的合并提交/精确 HEAD CI 为 `e9df23f079f0aaec1eff5b38269f4b18c7e9dae3 / 36295009039 / 9 of 9 SUCCESS`；canonical release=`nq-e9df23f079f0-5a401cd983263e6d` 已普通 install/activate，PG16/V54 validate 通过，migration 执行数为 0。复用现有 owner 登录来源，密码、角色和 owner 链未变；permission probe 为 `SUCCEEDED / TRADE / WITHDRAW=false / IP PASSED`。新 snapshot 的模式 2、BTC/USDT 与全部返回余额、positions、挂单、费率、交易所时间、当前公开规则和偏差均为 OBSERVED；positions=`NO_ACTIVE_POSITION / 0`，open orders=`0`，divergence=`DIVERGED`，完整分类不要求 MATCH。观察时间为 `2026-09-27T05:38:23.820031102Z`；私有事实 60s、公开规则 24h，验收时全部新鲜，历史快照不能作为后续实时输入。成功轮 10 次固定 GET、其他 endpoint 0、交易 mutation 0，五类 canonical 交易事实数量及内容不变。runtime 已停止，Java/MainPID=0，LIVE=DISABLED，kill=ENGAGED。首轮采集器阶段计数错误导致的失败原位保留，修正后以独立 run 身份重新验证；不追认旧失败为成功。详情见[模式 2 与公开规则只读资格证据](evidence/gate-z/OKX_ACCOUNT_FACTS_READONLY_COVERAGE.md)。 再次维护 owner 密码、改变账户模式/API Key 权限或修复交易事实均需对应明确授权；不得用这些写入强行取得 MATCH。每次新的正式采集须固定候选与采集器，失败结果不覆盖。正常预算为 permission probe 1 次 config GET，account-facts 9 次 GET（5 项 private、3 次 public time、1 次 public instrument rule），合计 10；完整 endpoint/query 见上述证据。

2026-09-27 的账户差异根因检查已将技术 PR #42 的 release `nq-f7a5faa782e5-20670623964ad074` 普通 install/activate；PG16/V54 只读 validate 通过、migration 执行数 0。唯一一次新观察返回 `UNKNOWN / BALANCE_SEMANTIC_MISMATCH`，正式采集器在解析 `UNKNOWN` 字段的空 `expiresAt` 时触发 `AttributeError`，原始验收为 `QUALIFICATION_BLOCKED_COLLECTOR_ATTRIBUTEERROR`。补充同次只读诊断确认 7 种外部正余额资产、5 种无本地快照行、本地 BTC/USDT 快照已超过 31 天；这些事实不证明余额同义或时间因果。已确认固定 GET 10 次、交易 mutation 0、五类 canonical 交易事实内容未变；最终 runtime 停止、Java/MainPID=0、LIVE=DISABLED、kill=ENGAGED。保留原失败，不以事后分析追认 PASS；下一次正式采集必须在单独解阻账户快照语义并固定新候选后进行。详情见[只读根因检查证据](evidence/gate-z/OKX_ACCOUNT_DIVERGENCE_READONLY_ROOT_CAUSE.md)。

### 现有用户密码的一次性维护

仅在已明确授权恢复现有用户登录时，使用已通过合并后精确 HEAD CI、manifest 与 admission 校验的制品。`ExistingUserPasswordRotationMain` 是独立 Java 维护入口，不启动 Spring、HTTP、Flyway、seed 或交易组件；普通 `java -jar` 启动不会执行密码维护。禁止使用 bootstrap-admin 或临时 SQL 替代窄更新端口。

先在受控环境中核对精确 userId、username、enabled、角色及账户/credential owner 绑定，并在内存中将当前 BCrypt hash 转为 SHA-256 fingerprint。请求文件和两个密码来源文件都必须是 root 所有的普通文件、权限严格为 `0600`，所有父目录必须由 root 所有且不可被 group/other 写入；不接受软链接。新密码为 16–72 字符、UTF-8 至多 72 字节，不能包含控制字符或复用旧密码。建议服务器生成 48 字符高熵 ASCII secret 并保存在批准的持久 secret source；不在聊天、参数、Git、CI、日志或 evidence 中传递其内容。

受保护的 JSON 请求只包含 `databaseUrl`（显式 loopback PostgreSQL URL，不含 query）、`databaseUser`、`databasePasswordFile`、`newPasswordFile`、`exactUserId`、`expectedUsername`、`expectedHashSha256`。三个文件路径必须不同。调用形式如下，其中参数仅为动作和请求文件路径：

```text
java -Dloader.main=com.guidinglight.nexusquant.app.maintenance.ExistingUserPasswordRotationMain \
  -cp <verified-release>/app/nq-app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  --execute-existing-user-password-rotation <protected-absolute-request-path>
```

成功输出仅为 `PASSWORD_ROTATED` 与 userId。repository 在单一事务中只更新 `users.password_hash/updated_at`，WHERE 同时绑定 id、username、enabled=true 和完整旧 hash；影响行数必须恰为一。身份或旧 hash 变化即拒绝，重复调用同一请求返回 `STALE_AUTH_IDENTITY`，没有自动重试。失败输出不包含异常详情或输入值；连接/语句均有超时。基础设施错误可能发生在 commit 响应丢失之后，必须先只读核对 hash fingerprint，再决定后续动作，不能盲目重新轮换。

执行后回读全部身份、角色、owner 关联，确认仅密码 hash 和更新时间发生预期变化。然后通过正常 `/api/auth/login` 和 `/api/auth/me` 验证目标 owner；旧会话 token 的撤销不属于此密码恢复能力，执行前应保持应用停止，成功后由既有 token 到期合同约束。新密码作为唯一可用登录来源保留在批准的受控文件中，临时数据库密码副本和请求文件在验证成功后删除；不得自动删除用户唯一可用登录凭据。

### Legacy current 到 canonical current 的一次性生产步骤（已完成，保留合同）

一次性执行时必须绑定当时实际交付的 `sourceCommit`、`releaseId`、release manifest SHA-256、EXACT_HEAD_CI admission SHA-256 和合并后 `dev` exact-head CI；这些值从同一个已验收候选及外部 admission 取得，不从旧服务器 SHA 推导。每一步记录命令、时间、脱敏结果和操作者，保留失败事实；不得记录 credential、环境文件或 key 内容。实际已执行范围与结果以上述当前证据为准；未执行的观察步骤不能记为成功。

1. **A / 只读 precheck**：在 `/opt/nexus-quant` 读取 `readlink current`、`readlink -f current`、`stat` 当前链接、目标与 `releases` 目录、release 目录名、`systemctl is-active`，只检查 `activation-journal.json`、`activation-head.json`、`.activation-authority.key` 是否存在。确认服务停止、LIVE 关闭及 kill switch engaged；任何身份、owner、mode 或既有 canonical history 冲突均停止。不要读取 key、`runtime.env` 或 `secrets.env`。
2. **B–C / 候选及 admission**：从合并后精确 HEAD 的 CI 取得 deployable canonical release 与外部 `EXACT_HEAD_CI` admission，核对 manifest 的 `releaseId`、source commit/tree、artifact digest、requiredSchemaTarget、PostgreSQL major。由受信部署系统将 `<releaseId>.json` 与 `<releaseId>.sha256` 放到 `/etc/nexus-quant/release-admission/`，按 installer 既有 root/0644 信任要求验证；不得复制、改名或伪造旧 SHA release 的 manifest/admission。
3. **D–E / 数据库与安装**：先只读核对 PostgreSQL 16、Flyway 成功版本与 manifest 的 `requiredSchemaTarget`，执行 Flyway validate 并确认无失败 migration；任何 checksum、失败记录或 pending 差异均停止。若需 forward migration，先按既有备份、迁移和恢复合同另行完成，不能由 bootstrap 跳过。使用已验证候选自带的 `bin/Install-NqCanonicalRelease.ps1`，以 `-Action install-for-bootstrap -InstallationRoot /opt/nexus-quant -SourceRoot <verified-release-root> -ExpectedSourceCommit <exact-source-commit> -ConfirmProduction` 安装 immutable canonical target；普通 `install` 在 legacy current 下仍拒绝。切换前用独立 `Test-NqCanonicalRelease.ps1` 与 `Test-NqCanonicalReleaseAdmission.ps1` 验证已安装 target 及外部 admission；普通 installer `verify` 在 legacy current 下保持拒绝。
4. **F / 原子 bootstrap**：紧接切换前，用同一 installer 的 `-Action observe-database -InstallationRoot /opt/nexus-quant -PsqlPath <trusted-psql> -DatabaseHost <host> -DatabasePort <port> -DatabaseName <name> -DatabaseUser <user> -DatabaseStatePath /opt/nexus-quant/database-state.json -ConfirmProduction` 生成签名只读数据库观察，记录 PostgreSQL major、schema target 和失败 migration 数；认证材料只由受控外部环境提供。观察须在 15 分钟内。随后执行 `-Action bootstrap-current -InstallationRoot /opt/nexus-quant -ReleaseId <releaseId> -ExpectedSourceCommit <exact-source-commit> -DatabaseStatePath /opt/nexus-quant/database-state.json -ConfirmProduction`。它只接受真实 40 位小写 SHA 旧目录和无 canonical activation history 的安装，写入签名 `UNMANAGED_NON_CANONICAL` 前任记录与 prepared journal，原子替换 current，再完成 journal/head。
5. **G / pointer 和 authority readback**：核对 `readlink -f current` 精确等于 `/opt/nexus-quant/releases/<releaseId>`，通过普通 `preflight`、`verify` 与 signed activation head/journal 的 release/transaction/generation 一致性检查；确认前任记录只表达 unmanaged legacy 身份。若 prepared 残留，按同一目标重试显式 `bootstrap-current`；若指针已 canonical，可用 `recover` 完成 head。不同目标、损坏签名或不明指针均停下，保留现场。
6. **H–I / scoped 只读 runtime**：在 canonical release 上核对 Java 21、loopback PostgreSQL、`NQ_READONLY_DB_URL/USER/PASSWORD`、`NQ_RELEASE_ID`、`NQ_SOURCE_COMMIT`、`NQ_RELEASE_MANIFEST_SHA256` 与已验 release 一致，`scoped-okx-private-readonly` 是唯一 active profile，`nq.okx.private-readonly-diagnostics.permission-probe.enabled=true` 且 expected IP 明确，LIVE、real provider/client/exchange、trading components、order/cancel/transfer/withdraw 均关闭。使用受控外部 secret source 启动独立的 loopback scoped 进程；不要重配或启动固定 `{prod}` 的 canonical systemd unit。读取 `/actuator/health`、`/actuator/info`、`/actuator/readonlyproviderobservation`，核对 release identity、能力、kill 和 mutation runtime 边界。
7. **J–K / 固定 private READ**：以已认证的账户 owner 身份，对 `/api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe` 发一次人工 POST，再读取 `/permission-probe/latest`；只在 `SUCCEEDED / TRADE / WITHDRAW=false / IP PASSED` 且未过期时，对同一账户和凭证的 `/account-facts/observe` 发一次人工 POST。只保留脱敏的余额、费率、来源、时间与偏差判定，不记录凭证或完整 provider 响应。
8. **L–N / 副作用与终态**：对照观察前后的 Order、ExecutionIntent、Receipt、Trade、Ledger、transfer、withdraw 和 provider POST 计数及持久事实；诊断计数若为 `NOT_INSTRUMENTED`，不得当作零，应由独立持久化/网络证据补足。停止 scoped 进程，确认服务状态、LIVE=`DISABLED`、kill switch=`ENGAGED`，再决定是否接受账户事实资格。
9. **O / 恢复边界**：切换前失败时 legacy current 保持可用；前任记录已签名但 journal 尚未写入时，确认 current 仍为相同旧目标、前任记录的 SHA/路径/目标身份和本次 source commit 均匹配，再以同一目标重试显式 `bootstrap-current`。切换后但完成写入失败时按签名 prepared journal 与精确同目标恢复，绝不猜测 release。bootstrap 的 legacy 前任不是 canonical rollback target，普通自动 rollback 只针对后续已验证 canonical release；若需恢复 legacy 代码，只能另行授权灾难人工恢复路径。

防回归命令（只读扫描与离线fixture）：

```powershell
python scripts/docs/check-stage-assets.py
python -m unittest discover -s scripts/docs/tests -p test_stage_assets.py
```
