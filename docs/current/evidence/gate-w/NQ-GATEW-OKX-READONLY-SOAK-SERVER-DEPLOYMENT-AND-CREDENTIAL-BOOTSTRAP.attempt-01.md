# NQ-GATEW-OKX-READONLY-SOAK-SERVER-DEPLOYMENT-AND-CREDENTIAL-BOOTSTRAP — Attempt 01

## 1. Task classification

- 类型：
  `SERVER_DEPLOYMENT / SECURITY_HARDENING / FIXED_COMMIT_RUNTIME / EXISTING_CREDENTIAL_GOVERNANCE_BOOTSTRAP / REAL_OKX_PRIVATE_READONLY_VALIDATION / SEVEN_DAY_SOAK_START`。
- 等级：L 级高风险运行任务。
- 范围：NQ-only；仅允许 OKX production private read-only，禁止交易、撤单、划转、提现、账户配置修改与任何 LIVE 交易授权。
- 尝试时间：`2026-07-15T11:21:51Z`。
- 最终结论：`BLOCKED / SERVER_SSH_ACCESS_REQUIRED`（阻断 / 需要恢复服务器 SSH 访问）。

## 2. Scope and authority

- 仓库：`E:\Project\nexus-quant`。
- fixed worktree：`E:\Project\nexus-quant-gatew-soak-ae73ebc7`。
- fixed commit：`ae73ebc79b7bc661513b5968c505f67261b18847`。
- 目标服务器：`47.251.74.35`，既有 SSH 配置解析为 `lingyu@47.251.74.35:22`。
- 当前 authority：GateW `IN_PROGRESS / NOT_FROZEN`，GateW-FREEZE `NOT_STARTED`，LIVE `DISABLED`；未修改
  `docs/current/STATUS.md` 或 `docs/current/ROADMAP.md`。
- 用户本轮显式授权真实 OKX private read-only probe；本 attempt 在到达任何服务器或 OKX 调用前即因 SSH hard gate 停止。

## 3. Preflight evidence

| Command / evidence                                                                               | Result         | Notes                                                             |
|--------------------------------------------------------------------------------------------------|----------------|-------------------------------------------------------------------|
| `Get-Location; git status --short; git branch --show-current`                                    | `PASS`         | 主工作区为 `E:\Project\nexus-quant`，分支 `dev`，工作区干净                     |
| fixed worktree `git status --short`                                                              | `PASS`         | 无输出，worktree clean                                                |
| fixed worktree `git rev-parse HEAD`                                                              | `PASS`         | 精确为 `ae73ebc79b7bc661513b5968c505f67261b18847`                    |
| fixed worktree `git branch --show-current`                                                       | `PASS`         | 无分支名，确认 detached HEAD                                             |
| `ssh -G 47.251.74.35`                                                                            | `PASS`         | 仅记录非敏感连接事实：user `lingyu`、hostname `47.251.74.35`、port `22`        |
| `ssh -o BatchMode=yes -o ConnectTimeout=15 lingyu@47.251.74.35 "hostname && whoami && uname -a"` | `BLOCKED`      | `ssh: connect to host 47.251.74.35 port 22: Connection timed out` |
| `gh run view 29349982797 --json ...`                                                             | `NOT_VERIFIED` | 本机到 `api.github.com:443` 连接失败；未把任务输入中的 CI 事实冒充为本轮在线复核结果           |

## 4. Hard-gate decision

附件要求 SSH 登录失败时立即返回 `BLOCKED / SERVER_SSH_ACCESS_REQUIRED`，且禁止猜测其他用户名、端口或认证方式。故本 attempt
未继续执行环境审计、依赖安装、目录创建、上传、数据库初始化、credential bootstrap、OKX probe 或 supervisor 启动。

未尝试：

- 未探测其他 SSH 用户或端口，未修改密码、SSH 配置、安全组、防火墙或服务器认证方式。
- 未执行服务器 `date -u`、`timedatectl`、`df`、`free`、`ss`、Java/PostgreSQL/Docker/PowerShell 版本检查。
- 未创建 `/opt/nexus-quant/gatew-soak/`，未创建系统用户，未上传任何构建物或配置。
- 未创建 `nq_gatew_okx_readonly_soak`，未执行 Flyway V1→V35，未触碰 V35。
- 未启动 NQ localhost-only credential 管理单元，未建立 SSH 隧道。
- 未读取、接收、写入或解密 API Key、Secret、Passphrase、加密主密钥、数据库密码或 SSH 私钥。
- 未调用 credential create/rotate API，未查询 `encrypted_payload`，未执行任何 OKX HTTP 请求。
- 未调用 `GET /api/v5/account/config` 或 `GET /api/v5/account/balance`，未生成真实 sample。
- 未运行 kill-switch soak fixture，未启动 supervisor，未创建 `runId`、PID、heartbeat 或 runtime evidence 目录。

## 5. Credential and safety boundary

- Secret exposure count：`0`。
- Forbidden endpoint count：`0`。
- 交易、撤单、划转、提现、账户配置修改：`0`。
- `LIVE`：保持 `DISABLED`；未新增或修改任何运行配置。
- credential material 未进入对话、Git、`.env`、命令行参数、shell history、日志、evidence、临时文件或数据库。
- 未读取 `.env`、`secrets`、`credentials`、private key 或服务器日志。

## 6. Runtime result

| Field                                            | Result                    |
|--------------------------------------------------|---------------------------|
| Server audit                                     | `NOT_RUN`                 |
| Server OS / NTP / public egress IP               | `NOT_VERIFIED`            |
| Java / PostgreSQL / PowerShell / disk            | `NOT_VERIFIED`            |
| Deployment directory / runtime user              | `NOT_CREATED`             |
| Public listening ports                           | `NOT_VERIFIED`            |
| PostgreSQL isolation / Flyway                    | `NOT_RUN`                 |
| Kill-switch default / fixture                    | `NOT_VERIFIED / NOT_RUN`  |
| Credential API path / bootstrap                  | `NOT_INSPECTED / NOT_RUN` |
| Encrypted storage verification                   | `NOT_RUN`                 |
| Required fields / forbidden direct-secret fields | `NOT_CHECKED`             |
| OKX Read / Trade / Withdraw / IP allowlist       | `NOT_VERIFIED`            |
| Real probe                                       | `NOT_RUN`                 |
| RunId / StartedAt / PlannedEndAt / PID           | `NONE`                    |
| Initial sample / heartbeat / hash-chain          | `NOT_CREATED / NOT_RUN`   |
| Final summary                                    | `NOT_CREATED`             |

## 7. Findings

- P0：无。
- P1：`SERVER_SSH_ACCESS_REQUIRED`。既有 SSH 目标在 15 秒内无法建立 TCP/SSH 连接，阻断全部服务器部署与真实只读验证。
- P2：fixed commit CI run `29349982797` 的本轮在线复核因本机到 GitHub API 网络失败而未完成；重试部署前需重新核验
  exact-head CI。
- P3：无。

## 8. Files changed and validation

- 新增本 attempt evidence。
- 更新 `docs/current/evidence/gate-w/README.md` 索引。
- 未修改 backend、frontend、research、scripts、deploy、`.github`、migration、harness、fixed worktree、authority、TESTING 或
  WORKLOG。
- 代码测试、Flyway、真实 OKX probe 与 soak 均未运行；本轮仅做文档一致性和 Git scope 验证。

## 9. Rollback / stop method

服务器、数据库、credential、OKX 与 supervisor 均无写操作，因此无需远端 rollback 或 stop。若要撤销本地文档变更，仅删除本
attempt 文件并移除 evidence 索引对应行；不得使用 `git reset --hard`。

## 10. Next action

由用户或服务器管理员恢复既有 `lingyu@47.251.74.35:22` 的可达性，并在本机确认同一 SSH 配置可登录；不要在对话中提供密码、私钥或任何
credential。恢复后以同一任务执行 `attempt-02`，从 SSH 只读登录、服务器环境/NTP/出口 IP 审计和 fixed commit CI 在线复核重新开始。在这些
hard gate 通过前，不得安装依赖、部署、录入 credential、调用 OKX 或启动 168 小时 soak。
