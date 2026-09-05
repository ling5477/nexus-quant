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

Phase5A与Phase5B已由各自immutable pair接受；当前F008 production configuration fail-closed为`REVIEW_ACCEPTED|READY_TO_COMMIT`（独立Review已接受，待提交与exact-head CI）。Authority reconciliation和后续docs-only同步运行以下一致性检查：

```powershell
git status --short
git branch --show-current
git diff --check
git diff --stat
```

本阶段不运行真实交易所 HTTP / WebSocket，不读取 credential material，不启动 LIVE，不接 AI / DH runtime。F008正式Review/acceptance完成前不得写成closed；P5-F007/P5-F009与Phase6继续deferred。仅docs/索引变化时不机械运行完整Maven。

## 9. Canonical production configuration

- Canonical systemd unit在`ExecStart`中固定`nq.production-configuration=true`和`spring.profiles.active=prod`；不要移入可变`runtime.env`，也不要用`NQ_ENVIRONMENT=SIM`代替production identity。
- `/etc/nexus-quant/runtime.env`必须由外部部署系统以最小读取权限提供`NQ_PROD_DB_URL`、`NQ_PROD_DB_USER`、`NQ_PROD_DB_PASSWORD`、`NQ_SECURITY_SECRET`、`NQ_ACCOUNT_CREDENTIALS_MASTER_KEY`；禁止把值写入release bundle、deployment contract、unit、日志或evidence。
- Spring展开include/group后的active profile set必须恰好为`{prod}`；prod与local/test/ci或任意其他profile组合均拒绝。prod即使未设置marker或marker=false也执行此校验。普通local启动保持原行为。
- `nq.security.secret`和`nq.account.credentials.master-key`允许由既有Spring externalized sources提供；最终effective值必须非空、无未解析占位符、无首尾空白且非repository-known default。密钥轮换、key-version与历史ciphertext迁移不包含在F008整改中。
- Canonical prod YAML采用单文档block mapping/scalar格式；CI直接验证五项required placeholder，无fallback。新增YAML合并、alias或其他格式前须扩展对应语义验证，当前checker对不支持的格式拒绝。
- Production datasource只允许canonical `spring.datasource.url/username/password/driver-class-name` effective contract。禁止通过Hikari-specific identity、JNDI、custom/XA DataSource或独立`spring.flyway.url/user/password`建立第二连接身份。
- 缺失、空白、非法或旁路配置必须在DataSource/Flyway bean创建前以`PROD_CONFIGURATION_INVALID`失败；不得等待DNS、TCP、authentication或Flyway network failure。
