# GateJ-FREEZE-FIX-SECOND-PASS 审查报告

日期：2026-05-28

## 1. 审查结论

PASS。

允许重新部署 GateJ-FREEZE-FIX release。GateJ 仍未 completed；重新部署后必须先执行首次启动验收，再进入 1h / 24h / 7d 连续运行验收。

## 2. 扫描范围

- 前端源码：`frontend/src`、`frontend/index.html`、`frontend/.env.example`、`frontend/vite.config.*`、`frontend/playwright.config.*`、`frontend/README.md`。
- 前端构建产物：`frontend/dist`。
- release 包：`release/nq-gatej-freeze-release.zip` 解压到 `release/second-pass-scan` 后扫描。
- 后端配置与 auth 初始化：`backend/nq-app/src/main/resources/application*.yml`、`SecurityConfiguration`、`AuthSeedConfiguration`、`AuthBootstrapAdminConfiguration`、`AuthSeedService`、`JdbcAuthUserRepository`。
- 部署文件：`deploy/docker-compose.freeze.yml`、`deploy/.env.freeze.example`、`deploy/nginx/default.conf`、`scripts/*.sh`、`scripts/*.ps1`。
- 当前文档：`README.md`、`AGENTS.md`、`CLAUDE.md`、`docs/current/*`。
- Git 追踪：`.gitignore`、`git ls-files`。

## 3. 命中项列表与判定

| 类别 | 代表位置 | 判定 | 说明 |
| --- | --- | --- | --- |
| 旧 legacy console gate 标记 | `frontend/vite.config.*`、`frontend/playwright.config.*`、部分后端注释、旧 E2E suite 名称 | 已修复 | 已改为中性描述，不影响业务逻辑。 |
| 默认测试密码关键词 | `frontend/.env.example`、`frontend/README.md` | 已修复 | 示例配置和 README 不再写默认密码；测试代码中的固定测试口令仅用于本地测试，不进入 dist/release。 |
| 默认测试密码关键词 | `frontend/tests/e2e/support.ts`、`AuthSecurityWebMvcTest` | 允许 | 测试夹具和认证单测允许出现，不进入生产/freeze 构建和 release 运行入口。 |
| 认证 API 路径 | 后端 `SecurityConfiguration`、auth API、测试、docs/current 历史验证记录 | 允许 | 后端代码、测试和 API/验证文档允许出现；生产登录页和 dist/release 可见文本无命中。 |
| `18888` | `docker-compose.freeze.yml`、`deploy/nginx/default.conf`、`health-check.sh`、`freeze-health-loop.sh`、`application*.yml`、部署文档 | 允许 | 端口用于后端监听、内网代理和本机 health check；登录页和 dist/release 前端无命中。 |
| `application-local` / local profile | `application-local.yml`、本地 RUNBOOK/历史验证记录 | 允许 | local profile 只保留在本地配置和历史验证上下文；freeze compose 和 `.env.freeze.example` 已使用 `NQ_PROFILE=freeze`。 |
| BCrypt 报错文本 | `docs/current/GATEJ_FREEZE_DEPLOYMENT.md`、`WORKLOG.md` | 允许 | 作为根因记录存在于文档，不进入运行产物；freeze seed 已改为生成 BCrypt hash。 |
| AI/DH/REAL/LIVE true 开关 | 全范围扫描 | 允许 | 未命中 true 开关；freeze 模板保持禁用。 |

## 4. 登录页泄露项复检结论

通过。

- `frontend/src/pages/login/LoginPage.tsx` 只保留 NexusQuant 控制台、用户名、密码、登录按钮和错误提示。
- `frontend/dist` 对敏感/旧联调关键词扫描无命中。
- release 包内 `frontend/dist` 对同一关键词扫描无命中。
- 登录页不再提供默认账号、默认密码、本地端口、认证接口路径或 Authorization header 示例。

## 5. freeze profile 复检结论

通过。

- `backend/nq-app/src/main/resources/application-freeze.yml` 已存在并随 jar 打包。
- `deploy/docker-compose.freeze.yml` 使用 `NQ_PROFILE: "${NQ_PROFILE:-freeze}"`。
- `deploy/.env.freeze.example` 使用 `NQ_PROFILE=freeze`。
- freeze profile 下 `nq.security.users: []`，不会执行 local 默认用户 seed。

## 6. seed-freeze-user.sh 复检结论

通过。

- 脚本通过 `.env.freeze` 或进程环境读取 `NQ_FREEZE_ADMIN_USERNAME` / `NQ_FREEZE_ADMIN_PASSWORD`。
- 脚本使用 PostgreSQL 容器内置 `pgcrypto`：`crypt(..., gen_salt('bf', 10))` 生成 BCrypt-compatible hash。
- 脚本幂等 upsert `users`，启用验收用户，并授予 `ADMIN / OPERATOR / VIEWER`。
- 脚本执行后校验 hash 格式和明文匹配，不要求服务器安装 Python / Node / Maven / standalone bcrypt 工具。
- 脚本不 echo 明文密码。

## 7. release 包敏感串扫描结论

通过。

- 新 release 包：`release/nq-gatej-freeze-release.zip`。
- 解压扫描目录：`release/second-pass-scan`。
- release 包内未命中默认密码、旧 legacy console gate 标记、登录页认证 API 提示、Authorization header 示例、local profile 启动项、AI/DH/REAL/LIVE true 开关。
- release 包内仅命中 `18888`，位置为 `docker-compose.freeze.yml`、`nginx/default.conf`、`health-check.sh`、`freeze-health-loop.sh`，均为允许的后端内网代理/本机 health check 配置。

## 8. Git 追踪污染检查结论

通过。

- `.gitignore` 覆盖：
  - `backend/**/target/`
  - `frontend/dist/`
  - `.env.*`，并仅放行 `deploy/.env.freeze.example`
  - `*.jar`
  - `*.zip`
  - `*.dump`
  - `backups/`
  - `freeze-evidence/`
  - `release/`
- `git ls-files` 检查未发现被追踪的 release、frontend dist、`.env.freeze`、jar、zip、dump、backup、log、freeze-evidence。
- `backend/nq-infra/src/main/resources/db/migration/*.sql` 与 `scripts/verify/*.sql` 是源码/迁移脚本，不属于 dump 污染。

## 9. 测试结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区包含 GateJ-FREEZE-FIX 与本轮二次审查修复/报告改动；未提交 release/dist/jar/log/env。 |
| 源码敏感词扫描 | 已执行 | 发现的生产/freeze 阻塞残留已修复；剩余命中均为允许项或历史文档记录。 |
| `rg ... frontend/dist` | 通过 | 无敏感/旧联调关键词命中。 |
| release zip 解压后 `rg ... release/second-pass-scan` | 通过 | 除允许的 `18888` 部署端口配置外，无敏感/旧联调关键词命中。 |
| `.gitignore` 检查 | 通过 | release/dist/target/env/log/dump/evidence 已覆盖。 |
| `git ls-files` 污染检查 | 通过 | 未发现不该追踪的 release/dist/env/jar/zip/dump/log/evidence。 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 backend module `SUCCESS`；`nq-app` 35 tests / 0 failures / 0 errors。 |
| `cd frontend && npm run build` | 通过 | Vite build 成功；仍有既有 chunk > 500 kB 警告。 |
| `.\scripts\build-freeze-release.ps1` | 通过 | 重新生成 `release/nq-gatej-freeze-release.zip`。 |

## 10. 是否允许重新部署

允许重新部署 GateJ-FREEZE-FIX release。

重新部署后服务器执行顺序必须为：

1. `./scripts/deploy-freeze.sh`
2. `./scripts/seed-freeze-user.sh`
3. curl 登录接口验证
4. 浏览器登录验证
5. `./scripts/health-check.sh`
6. 再进入 1h / 24h / 7d 连续运行验收

## 11. 边界确认

- 未新增业务功能。
- 未新增 API。
- 未新增 migration。
- 未接入 AI。
- 未接入 DH。
- 未启动或接入真实交易。
- 未修改交易核心状态机、策略核心算法或回测核心算法。
- 未提交 release zip、jar、dist、logs、dump、freeze-evidence 或 `.env.freeze`。
