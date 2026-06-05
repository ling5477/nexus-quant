# GateJ-FREEZE 最小服务器部署包说明

> 适用范围：GateJ-FREEZE 的 1h / 24h / 7d 连续运行验收与冻结准备。本文只描述部署辅助流程，不代表 GateJ 已完成冻结。

## 1. 服务器最低要求

- 云服务器：阿里云 ECS，最低 2 核 2G。
- 操作系统：Linux，建议 Ubuntu 22.04 LTS 或同等版本。
- 运行时：Docker Engine + Docker Compose v2；部署脚本同时兼容 legacy `docker-compose`。
- 网络：只开放前端端口 `5179`（或按安全组改为 `80`），且安全组只允许本人 IP 访问；PostgreSQL 不开放公网；`nq-app` 的 `18888` 只绑定宿主机 `127.0.0.1`。
- 磁盘：建议至少 40 GB，保留数据库、日志、freeze-evidence 与备份空间。

## 2. 目录结构

服务器使用固定目录 `/opt/nexus-quant`：

```text
/opt/nexus-quant/
  app/nq-app.jar
  frontend/dist/
  docker-compose.freeze.yml
  nginx/default.conf
  scripts/
  .env.freeze.example
  .env.freeze
  data/postgres/
  logs/postgres/
  logs/nq-app/
  logs/nginx/
  freeze-evidence/health/
  freeze-evidence/db/
  backups/
```

`freeze-evidence/`、`logs/`、`backups/`、数据库 dump 和 release zip 都是运行证据或产物，不得提交 Git。

## 3. 本地构建

在仓库根目录执行：

```powershell
mvn -f backend/pom.xml test
Set-Location frontend
npm run build
Set-Location ..
.\scripts\build-freeze-release.ps1
```

如果已经确认本地存在最新 `backend/nq-app/target/*.jar` 和 `frontend/dist`，可以只打包：

```powershell
.\scripts\build-freeze-release.ps1 -SkipBackendBuild -SkipFrontendBuild
```

脚本会生成：

```text
release/nq-gatej-freeze-release/
release/nq-gatej-freeze-release.zip
```

服务器不执行 Maven、npm 或前端构建。

## 4. Release 包结构

```text
nq-gatej-freeze-release/
  app/nq-app.jar
  frontend/dist/
  docker-compose.freeze.yml
  nginx/default.conf
  scripts/deploy-freeze.sh
  scripts/seed-freeze-user.sh
  scripts/health-check.sh
  scripts/backup-db.sh
  scripts/freeze-health-loop.sh
  .env.freeze.example
  RELEASE_INFO.md
```

`RELEASE_INFO.md` 记录构建时间、Git 分支、commit、边界声明和服务器入口步骤。

## 5. 上传服务器

示例命令：

```powershell
scp .\release\nq-gatej-freeze-release.zip <user>@<server-ip>:/tmp/
```

服务器上执行：

```bash
sudo mkdir -p /opt/nexus-quant
sudo unzip -o /tmp/nq-gatej-freeze-release.zip -d /opt/nexus-quant
sudo chown -R "$USER":"$USER" /opt/nexus-quant
cd /opt/nexus-quant
chmod +x scripts/*.sh
```

## 6. 解压部署配置

复制环境模板：

```bash
cd /opt/nexus-quant
cp .env.freeze.example .env.freeze
chmod 600 .env.freeze
```

编辑 `.env.freeze`，必须替换：

- `POSTGRES_PASSWORD`
- `NQ_DB_PASSWORD`
- `NQ_SECURITY_SECRET`
- `NQ_ACCOUNT_CREDENTIALS_MASTER_KEY`
- `NQ_FREEZE_ADMIN_USERNAME`
- `NQ_FREEZE_ADMIN_PASSWORD`

必须保持：

- `NQ_DB_URL=jdbc:postgresql://postgres:5432/nexus_quant`
- `NQ_PROFILE=freeze`
- `NQ_AI_ENABLED=false`
- `NQ_DH_ENABLED=false`
- `NQ_REAL_TRADING_ENABLED=false`
- `NQ_LIVE_TRADING_ENABLED=false`
- `NQ_OKX_RECOVERY_ENABLED=false`
- `NQ_BINANCE_WS_ENABLED=false`

`.env.freeze.example` 只放占位符。真实 `.env.freeze` 只保存在服务器，不提交 Git。`NQ_FREEZE_ADMIN_PASSWORD` 由 `scripts/seed-freeze-user.sh` 在服务器内通过 PostgreSQL `pgcrypto` 生成 BCrypt hash 后写入 `users.password_hash`；前端页面和 release 文档不得展示真实密码或默认密码。

不要手工执行 `source .env.freeze`。`.env.freeze` 是 Docker Compose/env 模板，不是 Bash 脚本；如果 `NQ_FREEZE_ADMIN_PASSWORD` 包含 `>`、`)`、引号、空格、`$`、反引号或 `#` 等 shell 特殊字符，直接 `source` 会触发 Bash 语法错误或历史记录泄露。推荐做法：

- `.env.freeze` 中保留 `NQ_FREEZE_ADMIN_PASSWORD=CHANGE_ME_FREEZE_ADMIN_PASSWORD` 占位符。
- 执行 `./scripts/seed-freeze-user.sh` 时按交互提示输入验收密码。
- 如必须把密码写入 `.env.freeze`，只使用单行、无引号、无空格、无 shell 特殊字符的值，并继续禁止提交该文件。

Docker 镜像需要提前加载到服务器本地：

```bash
docker image inspect postgres:16
docker image inspect eclipse-temurin:21-jre
docker image inspect nginx:alpine
```

如果镜像不存在，先通过受控离线方式加载镜像；GateJ-FREEZE 部署脚本不会主动拉取镜像。

## 7. 启动与 seed freeze user

```bash
cd /opt/nexus-quant
./scripts/deploy-freeze.sh
./scripts/seed-freeze-user.sh
```

`deploy-freeze.sh` 会：

- 检查 `.env.freeze` 是否存在。
- 检查 `postgres:16`、`eclipse-temurin:21-jre`、`nginx:alpine` 是否已在本地。
- 创建 `data/`、`logs/`、`freeze-evidence/`、`backups/` 目录。
- 执行 `docker compose --env-file .env.freeze -f docker-compose.freeze.yml up -d`；如果服务器只有 `docker-compose`，脚本会自动降级。

`seed-freeze-user.sh` 会：

- 自行解析 `.env.freeze`，不会要求也不允许手工 `source .env.freeze`。
- 从 `.env.freeze` 或当前进程环境读取 `NQ_FREEZE_ADMIN_USERNAME`。
- 从 `.env.freeze`、当前进程环境或交互式隐藏输入读取 `NQ_FREEZE_ADMIN_PASSWORD`。
- 交互式隐藏输入时，密码明文不会写入 stdout/stderr；脚本只输出提示名和换行，不输出密码值。
- 使用 PostgreSQL 容器内置 `pgcrypto` 生成 BCrypt hash。
- 在单个 `psql` session 内幂等 upsert `users`，启用该用户，并授予 `ADMIN / OPERATOR / VIEWER`；脚本不使用跨 session 的临时表或 CTE 结果。
- 校验写入结果满足 BCrypt 格式且能通过同一明文匹配。

GateJ-FREEZE-FIX-4 已修复交互式隐藏输入路径：`seed-freeze-user.sh` 内部通过命令替换接收密码，视觉换行必须写入 stderr，不能写入 stdout；否则正常单行密码前会混入换行并被误判为多行。

本轮根因是服务器 `users.password_hash` 存在非 BCrypt 值，触发 `BCrypt non-hash warning`，因此必须在服务启动并完成 Flyway 后执行 seed，再做登录验证。

## 8. 登录验证

### 8.1 curl 登录接口验证

在服务器本机执行，使用 `.env.freeze` 中的验收用户名和密码替换占位符，不要把真实密码写入 Git、截图或公开日志：

```bash
curl -fsS 'http://127.0.0.1:18888/api/auth/login' \
  -H 'Content-Type: application/json' \
  --data '{"username":"<NQ_FREEZE_ADMIN_USERNAME>","password":"<NQ_FREEZE_ADMIN_PASSWORD>"}'
```

预期：返回登录 token JSON；若仍为 401，先检查 `scripts/seed-freeze-user.sh` 是否成功执行，再查看 `logs/nq-app/application.log` 中是否仍存在 `Encoded password does not look like BCrypt`。

### 8.2 浏览器登录验证

浏览器访问：

```text
http://<server-ip>:5179/
```

登录页只能展示 NexusQuant 控制台、用户名、密码、登录按钮、错误提示和 traceId。不得展示 legacy console gate、本地端口、默认账号密码、登录 API 路径或 Authorization header 示例。

## 9. 健康检查

一次性检查：

```bash
cd /opt/nexus-quant
./scripts/health-check.sh
```

输出包含：

- `docker ps`
- `free -h`
- `df -h`
- `http://127.0.0.1:18888/actuator/health`
- `nq-app` 最近 100 行日志

前端访问：

```text
http://<server-ip>:5179/
```

如 `.env.freeze` 将 `FRONTEND_HTTP_PORT` 改为 `80`，访问：

```text
http://<server-ip>/
```

## 10. 1h / 24h / 7d 验收流程

GateJ-FREEZE 首次启动验收顺序必须固定为：

1. `docker compose up -d postgres/app/nginx`，通过 `./scripts/deploy-freeze.sh` 执行。
2. `seed freeze user`，通过 `./scripts/seed-freeze-user.sh` 执行。
3. `curl` 登录接口验证。
4. 浏览器登录验证。
5. 健康检查、备份和连续采样。

### 10.1 验收前

```bash
cd /opt/nexus-quant
./scripts/backup-db.sh before-freeze
./scripts/health-check.sh | tee freeze-evidence/health/before-freeze-health.log
```

### 10.2 启动连续采样

```bash
cd /opt/nexus-quant
nohup ./scripts/freeze-health-loop.sh > freeze-evidence/health/freeze-health-loop.out 2>&1 &
echo $! > freeze-evidence/health/freeze-health-loop.pid
```

`freeze-health-loop.sh` 每 5 分钟追加记录到：

```text
/opt/nexus-quant/freeze-evidence/health/health-check-7d.log
```

每次记录包含时间、actuator health、`docker ps`、`free -h`、`df -h`。

### 10.3 1 小时验收

```bash
cd /opt/nexus-quant
./scripts/health-check.sh | tee freeze-evidence/health/after-1h-health.log
./scripts/backup-db.sh after-1h
```

判定要求：

- 在线率 100%。
- 无 CRITICAL 告警。
- 无 FAILED 调度触发。

### 10.4 24 小时验收

```bash
cd /opt/nexus-quant
./scripts/health-check.sh | tee freeze-evidence/health/after-24h-health.log
./scripts/backup-db.sh after-24h
```

判定要求：

- 在线率 >= 99%。
- 失败触发 <= 2 次。
- 任何失败必须记录原因，不能写成通过。

### 10.5 7 天验收

```bash
cd /opt/nexus-quant
./scripts/health-check.sh | tee freeze-evidence/health/after-7d-health.log
./scripts/backup-db.sh after-7d
```

判定要求：

- 在线率 >= 99%。
- 失败触发 <= 5 次。
- 恢复成功率 >= 90%。
- 无 AI、DH、REAL/LIVE 下单、真实交易所下单接口调用痕迹。

验收记录使用 `docs/current/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md` 另存填写。GateJ 整体未通过前，不创建 `docs/gates/gate-j/`。

## 11. 日志和证据包保留规则

必须保留在服务器或外部安全介质：

- `/opt/nexus-quant/freeze-evidence/health/*.log`
- `/opt/nexus-quant/freeze-evidence/db/*.sql`
- `/opt/nexus-quant/logs/postgres/`
- `/opt/nexus-quant/logs/nq-app/`
- `/opt/nexus-quant/logs/nginx/`
- `/opt/nexus-quant/backups/`

不得提交 Git：

- `jar`
- `frontend/dist`
- `zip`
- `logs`
- `dump`
- `freeze-evidence`
- `.env.freeze`

## 12. 禁止事项

- 不改 Java 业务代码。
- 不改 React 业务代码。
- 不新增 API。
- 不新增 migration。
- 不接入 AI、AI 信号、AI 自动交易、AI Paper Trading。
- 不接入 DH。
- 不接入真实交易。
- 不调用真实交易所下单接口。
- 不把失败验证写成通过。
- 不把 GateJ 写成 completed，除非 1h / 24h / 7d 验收全部通过并完成冻结。
