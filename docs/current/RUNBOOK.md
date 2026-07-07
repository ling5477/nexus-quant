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

## 8. GateS-0 docs-only 验证边界

GateS-0 当前为 `PLAN / NOT IMPLEMENTED`（规划 / 未实现），只做 fact-source reconciliation、planning review、read-model / frontend contract proposal 和验收清单。正常情况下只运行 docs consistency 相关命令：

```powershell
git status --short
git branch --show-current
git diff --check
git diff --stat
```

本阶段不运行真实交易所 HTTP / WebSocket，不读取 credential material，不启动 LIVE，不接 AI / DH runtime，不实现 RealClient、real provider、private trading adapter 或真实 permission probe。除非误触代码，否则不运行 Maven 全量测试、frontend build / E2E 或 Python pytest / mypy / ruff。
