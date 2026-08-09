# NQ-GATEW-OKX-READONLY-SOAK-SERVER-DEPLOYMENT-AND-CREDENTIAL-BOOTSTRAP — Attempt 03

## 1. Task classification

- 执行模式：`ATTEMPT_03 / RESUME_AFTER_SSH_REMEDIATION`。
- 类型：`SERVER_DEPLOYMENT / SECURITY_HARDENING / FIXED_COMMIT_RUNTIME / SERVER_ISOLATION_AUDIT`。
- 等级：L 级高风险运行任务。
- 范围：NQ-only；只允许 OKX production private read-only，禁止交易、撤单、划转、提现和账户配置修改。
- 尝试时间：`2026-07-15T13:03:16Z`。
- 最终结论：`BLOCKED / SERVER_ISOLATION_NOT_PROVEN`（阻断 / 服务器隔离与公网暴露边界未证明）。

## 2. Local and authority preflight

| Item                  | Result                                                                                             |
|-----------------------|----------------------------------------------------------------------------------------------------|
| Main repository       | `E:\Project\nexus-quant` / branch `dev`                                                            |
| Main `HEAD`           | `ae73ebc79b7bc661513b5968c505f67261b18847`                                                         |
| `origin/dev`          | `ae73ebc79b7bc661513b5968c505f67261b18847`                                                         |
| Allowed prior changes | attempt-01、attempt-02 与 GateW evidence index；无其他 mixed-worktree 内容                                 |
| Fixed worktree        | `E:\Project\nexus-quant-gatew-soak-ae73ebc7` / clean / detached HEAD                               |
| Fixed commit          | `ae73ebc79b7bc661513b5968c505f67261b18847`                                                         |
| Fixed commit CI       | `NQ CI Baseline / 29349982797 / completed / success / headSha=ae73ebc7... / 10 of 10 jobs success` |
| Current authority     | GateW `IN_PROGRESS / NOT_FROZEN`；GateW-FREEZE `NOT_STARTED`；LIVE `DISABLED`                        |
| Authority checker     | `PASS / CURRENT_AUTHORITY_CONSISTENT`；errors=0                                                     |

## 3. SSH remediation verification

- 指定 key 存在，仅由 `ssh` 使用；未读取或输出 private key 内容。
- 命令使用 `BatchMode=yes`、`IdentitiesOnly=yes`、public-key only、password authentication disabled。
- SSH：`PASS`；exit code 0。
- Hostname：`iZrj9gpab986sm4d0bb6agZ`。
- Remote user：`root`。
- OS kernel：Linux 6.8.0-63-generic x86_64。
- 未修改 `sshd`、`authorized_keys` 或 SSH key。

## 4. Server environment audit

| Field              | Verified result                                     |
|--------------------|-----------------------------------------------------|
| Server             | `47.251.74.35`                                      |
| OS                 | Ubuntu 24.04.4 LTS                                  |
| Time zone          | Asia/Shanghai                                       |
| NTP                | `System clock synchronized: yes`；NTP service active |
| Public outbound IP | `47.251.74.35`，精确匹配                                 |
| Uptime             | 60 days 23 hours                                    |
| Root disk          | 40 GiB total / 27 GiB available / 29% used          |
| Memory             | 1.6 GiB total / about 531 MiB available             |
| Swap               | 2.0 GiB total / 0 used                              |
| Java               | `NOT_INSTALLED`                                     |
| PostgreSQL client  | `NOT_INSTALLED`                                     |
| Docker             | 29.5.0                                              |
| Docker Compose     | v5.1.3                                              |
| PowerShell         | `NOT_INSTALLED`                                     |

NTP、出口 IP 与磁盘 hard gate 通过。Java 21、PostgreSQL client 和 PowerShell 7 可按任务安装，但服务器隔离 hard gate
已先失败，因此未安装依赖。

## 5. Network and workload isolation finding

主机监听审计发现：

- `0.0.0.0:22` / `[::]:22`：SSH。
- `0.0.0.0:5179` / `[::]:5179`：既有 `nq-freeze-nginx` Docker listener。
- `0.0.0.0:18808`：既有 `sub2api` Docker listener。
- `*:5201`：既有 `iperf3` service，active/enabled。
- `127.0.0.1:18888`：既有 `nq-freeze-app` Docker listener。

从任务执行主机进行 TCP reachability 验证时，`22`、`5179`、`18808`、`18888`、`5201` 均返回 reachable。`18888` 与服务器侧
localhost bind 观察存在网络层矛盾，故不能据此宣称 localhost 隔离已证明。

UFW 为 active、default deny incoming，但明确允许公网：

- `22/tcp`
- `80/tcp`
- `443/tcp`
- `8443/tcp`
- `18808/tcp`

现有运行容器：

- `sub2api`、`sub2api-postgres`、`sub2api-redis`
- `nq-freeze-nginx`、`nq-freeze-app`、`nq-freeze-postgres`

现有容器内存观测合计约 592 MiB。该服务器并非本任务假定的“仅 SSH 公网暴露、GateW soak 专用空闲节点”；停止既有容器、关闭端口或修改
UFW 会影响其他项目，超出本任务允许范围。

## 6. Hard-gate decision and stopped work

任务要求只允许 SSH 管理端口对公网开放，并禁止修改无关系统组件。由于既有公网监听和共享工作负载同时存在，本轮不能安全地继续部署，也不能擅自停止服务或收紧防火墙。

因此以下操作全部为 `NOT_RUN`：

- 未安装 Java 21、PostgreSQL client 或 PowerShell 7。
- 未创建 `nqgatew` 用户或 `/opt/nexus-quant/gatew-soak`。
- 未构建或上传 fixed commit artifact、migration、harness 或配置。
- 未创建 `nq_gatew_okx_readonly_soak` 或专用 DB credential，未执行 Flyway V1→V35。
- 未启动 localhost-only NQ credential 管理单元或 SSH tunnel。
- 未读取、录入、解密或验证 OKX credential。
- 未调用 OKX `account/config` 或 `account/balance`。
- 未执行 kill-switch fixture、harness self-test、首样本或 168 小时 supervisor。

## 7. Credential and trading boundary

- Secret exposure count：`0`。
- Forbidden endpoint count：`0`。
- OKX network call count：`0`。
- 远端写操作：`0`。
- 交易、撤单、划转、提现或账户配置修改：`0`。
- API Key、Secret、Passphrase、数据库密码、SSH private key 与加密主密钥均未进入对话、命令参数、Git、日志、evidence 或临时文件。
- LIVE 保持 `DISABLED`；未修改 GateW authority、harness、endpoint allowlist、evidence schema、V35 或 governance contract。

## 8. Runtime result

| Field                                              | Result                     |
|----------------------------------------------------|----------------------------|
| Runtime user / deployment directory                | `NOT_CREATED`              |
| NQ localhost port                                  | `NOT_STARTED`              |
| PostgreSQL isolation                               | `NOT_PROVEN / NOT_CREATED` |
| Soak database / Flyway                             | `NOT_CREATED / NOT_RUN`    |
| Kill-switch default / fixture                      | `NOT_VERIFIED / NOT_RUN`   |
| Credential API/controller                          | `NOT_INSPECTED`            |
| Credential bootstrap / encrypted storage           | `NOT_RUN`                  |
| Required fields / forbidden direct-secret fields   | `NOT_CHECKED`              |
| OKX Read / Trade / Withdraw / IP allowlist         | `NOT_VERIFIED`             |
| Real probe                                         | `NOT_RUN`                  |
| Harness self-test                                  | `NOT_RUN`                  |
| RunId / StartedAt / PlannedEndAt / PID             | `NONE`                     |
| Runtime evidence / sample / heartbeat / hash chain | `NOT_CREATED / NOT_RUN`    |
| Final summary                                      | `NOT_CREATED`              |

## 9. Findings

- P0：无。
- P1：公网可达的既有非 SSH listeners 与 UFW allow rules 违反 soak 节点的只允许 SSH 暴露边界；服务器隔离未证明。
- P1：服务器同时运行 Sub2API 和既有 NQ freeze workloads，不符合专用隔离节点假定；本任务无权停止、迁移或重配这些服务。
- P2：可用内存约 531 MiB，新增 Java + PostgreSQL 的 168 小时稳定余量未证明；在共享负载不变时存在资源竞争风险。
- P2：外部 `18888` TCP reachable 与服务器侧 localhost bind 观察矛盾，需要由云安全组/NAT/网络路径审计解释。
- P3：Java、`psql`、PowerShell 7 缺失；仅在服务器隔离问题关闭后才允许最小安装。

## 10. Files changed and validation scope

- 保留 attempt-01 与 attempt-02，不修改其历史结论。
- 新增本 attempt-03 evidence。
- 更新 `docs/current/evidence/gate-w/README.md` 索引。
- 不更新 TESTING/WORKLOG：没有执行代码测试、Flyway、OKX probe 或 soak，不新增重复 ledger 噪音。
- 未修改 backend、frontend、research、scripts、deploy、`.github`、migration、fixed worktree 或 authority。

## 11. Stop and rollback method

本轮远端只执行只读审计，没有安装、创建、上传、启动、停止或修改任何服务器资源，因此无需远端 rollback。不要通过本任务停止既有容器、禁用
`iperf3`、修改 UFW、安全组或其他项目端口。

## 12. Next action

需要用户在以下两种路径中选择并完成独立基础设施处理：

1. 提供满足“仅 SSH 公网暴露、无其他项目 workload、资源余量充分”的专用 GateW soak 节点；或
2. 由服务器/云资源 owner 在独立授权任务中迁移或隔离现有 Sub2API、nq-freeze、iperf3 与公网端口，并提供云安全组、主机 listener
   和资源余量复核证据。

隔离 hard gate 通过后，重新执行服务器审计，再进入依赖安装、non-root 用户、隔离 PostgreSQL、credential bootstrap 与真实 OKX
read-only soak。不得在当前服务器状态下降低规则继续运行。
