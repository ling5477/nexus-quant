# GateJ-FREEZE 最小服务器部署包说明

> 适用范围：GateJ-FREEZE 的 1h / 24h / 7d 连续运行验收与冻结准备。本文只描述部署辅助流程，不代表 GateJ 已完成冻结。

## 1. 服务器最低要求

- 云服务器：阿里云 ECS，最低 2 核 2G。
- 操作系统：Linux，建议 Ubuntu 22.04 LTS 或同等版本。
- 运行时：Docker Engine + Docker Compose v2；部署脚本同时兼容 legacy `docker-compose`。
- 网络：只开放前端端口 `5179`（或按安全组改为 `80`）；PostgreSQL 不开放公网；`nq-app` 的 `18888` 只绑定宿主机 `127.0.0.1`。
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
- `NQ_LOCAL_ADMIN_PASSWORD_HASH`
- `NQ_LOCAL_OPERATOR_PASSWORD_HASH`
- `NQ_LOCAL_VIEWER_PASSWORD_HASH`

必须保持：

- `NQ_DB_URL=jdbc:postgresql://postgres:5432/nexus_quant`
- `NQ_AI_ENABLED=false`
- `NQ_DH_ENABLED=false`
- `NQ_REAL_TRADING_ENABLED=false`
- `NQ_LIVE_TRADING_ENABLED=false`
- `NQ_OKX_RECOVERY_ENABLED=false`
- `NQ_BINANCE_WS_ENABLED=false`

如果 bcrypt hash 或其他值包含 `$`、空格等特殊字符，建议在 `.env.freeze` 中使用单引号或双引号包裹，避免 shell 工具误解析。

Docker 镜像需要提前加载到服务器本地：

```bash
docker image inspect postgres:16
docker image inspect eclipse-temurin:21-jre
docker image inspect nginx:alpine
```

如果镜像不存在，先通过受控离线方式加载镜像；GateJ-FREEZE 部署脚本不会主动拉取镜像。

## 7. 启动服务

```bash
cd /opt/nexus-quant
./scripts/deploy-freeze.sh
```

脚本会：

- 检查 `.env.freeze` 是否存在。
- 检查 `postgres:16`、`eclipse-temurin:21-jre`、`nginx:alpine` 是否已在本地。
- 创建 `data/`、`logs/`、`freeze-evidence/`、`backups/` 目录。
- 执行 `docker compose --env-file .env.freeze -f docker-compose.freeze.yml up -d`；如果服务器只有 `docker-compose`，脚本会自动降级。

## 8. 健康检查

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

## 9. 1h / 24h / 7d 验收流程

### 9.1 验收前

```bash
cd /opt/nexus-quant
./scripts/backup-db.sh before-freeze
./scripts/health-check.sh | tee freeze-evidence/health/before-freeze-health.log
```

### 9.2 启动连续采样

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

### 9.3 1 小时验收

```bash
cd /opt/nexus-quant
./scripts/health-check.sh | tee freeze-evidence/health/after-1h-health.log
./scripts/backup-db.sh after-1h
```

判定要求：

- 在线率 100%。
- 无 CRITICAL 告警。
- 无 FAILED 调度触发。

### 9.4 24 小时验收

```bash
cd /opt/nexus-quant
./scripts/health-check.sh | tee freeze-evidence/health/after-24h-health.log
./scripts/backup-db.sh after-24h
```

判定要求：

- 在线率 >= 99%。
- 失败触发 <= 2 次。
- 任何失败必须记录原因，不能写成通过。

### 9.5 7 天验收

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

## 10. 日志和证据包保留规则

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

## 11. 禁止事项

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
