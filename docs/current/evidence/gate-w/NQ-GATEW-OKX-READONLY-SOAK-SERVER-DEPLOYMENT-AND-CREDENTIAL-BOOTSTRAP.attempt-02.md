# NQ-GATEW-OKX-READONLY-SOAK-SERVER-DEPLOYMENT-AND-CREDENTIAL-BOOTSTRAP — Attempt 02

## 1. Task classification

- 类型：`SERVER_DEPLOYMENT / SECURITY_HARDENING / SSH_AUTHENTICATION_HARD_GATE`。
- 等级：L 级高风险运行任务。
- 范围：NQ-only；延续 attempt-01，按用户新提供的 `root@47.251.74.35:22` 重新验证 SSH 前置。
- 尝试时间：`2026-07-15T11:29:25Z`。
- 最终结论：`BLOCKED / SERVER_SSH_ACCESS_REQUIRED`（阻断 / 需要提供可用的服务器 SSH 认证）。

## 2. Verified facts

- 主工作区：`E:\Project\nexus-quant`，分支 `dev`；attempt-01 与 GateW evidence 索引仍在 staged 状态，未覆盖历史 attempt。
- fixed worktree：`E:\Project\nexus-quant-gatew-soak-ae73ebc7`。
- fixed commit：`ae73ebc79b7bc661513b5968c505f67261b18847`；detached HEAD；worktree clean。
- 用户明确提供 SSH 目标：`root@47.251.74.35:22`。
- 使用 `BatchMode=yes` 和 15 秒连接超时执行只读登录探测；服务器端口可达，但认证失败。

## 3. SSH evidence

| Command                                                                                              | Result                                                       | Classification                         |
|------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|----------------------------------------|
| `ssh -o BatchMode=yes -o ConnectTimeout=15 -p 22 root@47.251.74.35 "hostname && whoami && uname -a"` | `root@47.251.74.35: Permission denied (publickey,password).` | `BLOCKED / SERVER_SSH_ACCESS_REQUIRED` |

该结果与 attempt-01 的 TCP timeout 不同：本轮已证明 `47.251.74.35:22` 网络可达，但本机当前没有可由非交互命令使用且被服务器接受的
root SSH 认证。未尝试其他用户名、端口、密码猜测、认证降级或 SSH 配置修改。

## 4. Stop decision

SSH authentication hard gate 未通过，故未执行任何远端命令或写操作：

- 未读取服务器 hostname、OS、NTP、磁盘、内存、监听端口、Java、PostgreSQL、Docker 或 PowerShell 状态。
- 未安装依赖，未创建系统用户、目录、服务、数据库或防火墙规则。
- 未上传 fixed commit 构建物、harness、migration 或配置。
- 未读取、接收或使用 root 密码、SSH 私钥、API Key、Secret、Passphrase、加密主密钥或数据库密码。
- 未启动 NQ，未建立 SSH 隧道，未调用 credential create/rotate API。
- 未调用 OKX，未执行只读 probe，未生成 sample，未启动 supervisor。

## 5. Safety boundary

- Secret exposure count：`0`。
- Forbidden endpoint count：`0`。
- 远端写操作：`0`。
- OKX HTTP request：`0`。
- 交易、撤单、划转、提现或账户配置修改：`0`。
- LIVE：保持 `DISABLED`；GateW authority 未修改。

## 6. Findings

- P0：无。
- P1：`SERVER_SSH_ACCESS_REQUIRED`。root SSH 端口可达，但 public-key/password authentication 均未由当前非交互会话满足，阻断服务器审计和部署。
- P2：fixed commit CI run `29349982797` 仍需在网络可用时在线复核；本 attempt 未执行该复核。
- P3：无。

## 7. Files changed and validation scope

- 新增本 attempt-02 evidence。
- 更新 `docs/current/evidence/gate-w/README.md` 索引。
- attempt-01 保持不可覆盖。
- 未修改 backend、frontend、research、scripts、deploy、`.github`、migration、harness、fixed worktree、authority、TESTING 或
  WORKLOG。
- 本轮只需执行 docs authority/link、Git staged allowlist、diff check 和敏感值扫描；代码、Flyway、OKX 与 soak 验证均为
  `NOT_RUN`。

## 8. Rollback / stop method

服务器没有发生任何写操作，无需远端 rollback。若撤销本地文档变更，仅移除本 attempt-02 文件与 evidence 索引对应行；不得覆盖
attempt-01，不得使用 `git reset --hard`。

## 9. Next action

在本机配置并验证可供自动命令使用的 root key-based SSH 登录，使以下命令能够在不向 Codex 暴露密码或私钥的前提下成功返回：

```powershell
ssh -o BatchMode=yes -p 22 root@47.251.74.35 "hostname && whoami && uname -a"
```

完成后继续同一任务的 attempt-03，从服务器只读环境、NTP、监听端口、依赖与公网出口 IP 审计开始。SSH hard gate 通过前，不得部署、建库、录入
credential、调用 OKX 或启动 soak。
