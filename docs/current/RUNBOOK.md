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

## 8. GateAUDIT Phase5 current boundary

Phase5A、Phase5B、F008与F007已由各自immutable technical pair接受；当前work batch与下一动作从[STATUS.md](STATUS.md)读取，F009已ACCEPTED / CLOSED，F001 remote enforcement已ACCEPTED / CLOSED（ruleset 22381941，dev effective 9/9），Phase5 ACCEPTED / CLOSED。Authority reconciliation和后续docs-only同步运行以下一致性检查：

```powershell
git status --short
git branch --show-current
git diff --check
git diff --stat
```

本阶段不运行真实交易所 HTTP / WebSocket，不读取 credential material，不启动 LIVE，不接 AI / DH runtime。F008/F007/F009 与 C1 保持已接受；C2=`ACCEPTED / CLOSED`，固定 pair=`612c2f5887a2e6b3a8b3138d9ae9b193c20e298f / 34183851797`。当前执行 pre-B0 CI safety/current authority remediation，`IMPLEMENTED / PENDING_INDEPENDENT_REVIEW`；下一动作与任务映射见[STATUS.md](STATUS.md)和[ROADMAP.md](ROADMAP.md)。当前 repository schema=`V48`；历史 Phase5B V46、C1 V47 的接受事实仍按原证据保留，不推断生产 schema。本轮不重跑 C2/PostgreSQL、Full Maven 或 Playwright。B0和L4 qualification未开始。

## 9. Canonical production configuration

- Canonical systemd unit在`ExecStart`中固定`nq.production-configuration=true`和`spring.profiles.active=prod`；不要移入可变`runtime.env`，也不要用`NQ_ENVIRONMENT=SIM`代替production identity。
- `/etc/nexus-quant/runtime.env`必须由外部部署系统以最小读取权限提供`NQ_PROD_DB_URL`、`NQ_PROD_DB_USER`、`NQ_PROD_DB_PASSWORD`、`NQ_SECURITY_SECRET`、`NQ_ACCOUNT_CREDENTIALS_MASTER_KEY`；禁止把值写入release bundle、deployment contract、unit、日志或evidence。
- Spring展开include/group后的active profile set必须恰好为`{prod}`；prod与local/test/ci或任意其他profile组合均拒绝。prod即使未设置marker或marker=false也执行此校验。普通local启动保持原行为。
- `nq.security.secret`和`nq.account.credentials.master-key`允许由既有Spring externalized sources提供；最终effective值必须非空、无未解析占位符、无首尾空白且非repository-known default。密钥轮换、key-version与历史ciphertext迁移不包含在F008整改中。
- Canonical prod YAML采用单文档block mapping/scalar格式；CI直接验证五项required placeholder，无fallback。新增YAML合并、alias或其他格式前须扩展对应语义验证，当前checker对不支持的格式拒绝。
- Production datasource只允许canonical `spring.datasource.url/username/password/driver-class-name` effective contract。禁止通过Hikari-specific identity、JNDI、custom/XA DataSource或独立`spring.flyway.url/user/password`建立第二连接身份。
- 缺失、空白、非法或旁路配置必须在DataSource/Flyway bean创建前以`PROD_CONFIGURATION_INVALID`失败；不得等待DNS、TCP、authentication或Flyway network failure。

## 10. Canonical operational entrypoints

release只使用`../../scripts/deployment/New-NqCanonicalRelease.ps1`；verify与外部admission使用同目录`Test-NqCanonicalRelease.ps1`和`Test-NqCanonicalReleaseAdmission.ps1`。install、activate、rollback、recover统一由`Install-NqCanonicalRelease.ps1`的既有Action contract承担；恢复使用`Invoke-NqCanonicalRestoreDrill.ps1`与PG16 contract。当前生产unit唯一为`../../deploy/canonical/nq-canonical.service`。本节是路径索引，不授予部署或真实服务操作权限。

受控readonly生产图测试使用既有`scoped-okx-private-readonly` runtime mode，环境字段为`NQ_READONLY_DB_URL/USER/PASSWORD`、`NQ_RELEASE_ID`、`NQ_SOURCE_COMMIT`、`NQ_RELEASE_MANIFEST_SHA256`；值必须从显式测试或外部授权环境提供。本轮只用无连接fixture验证该mode；它不是第二条生产部署路径，不能与prod组合。所有历史stage profile和旧stage capability key均拒绝，普通local/test/ci/prod/paper语义不变。

防回归命令（只读扫描与离线fixture）：

```powershell
python scripts/docs/check-stage-assets.py
python -m unittest discover -s scripts/docs/tests -p test_stage_assets.py
```
