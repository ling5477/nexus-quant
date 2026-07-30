# NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-SANITIZED-RCA-AND-FIX — attempt-01

## 任务与结论

- 类型：NQ-only / `PRODUCTION_SAFE_RCA / SANITIZED_PREREQUISITE_DIAGNOSTICS / CONDITIONAL_MINIMAL_FIX / TESTS / COMMIT_AND_EXACT_HEAD_CI`。
- 结论：`PASS / ROOT_CAUSE_IDENTIFIED / CODE_FIX_IMPLEMENTED / CI_GREEN / DEPLOYMENT_REQUIRED / ATTEMPT_10_NOT_AUTHORIZED`（通过 / 已定位代码级根因 / 已实施代码修复 / CI 已通过 / 需要独立部署验证 / Attempt-10 未授权）。
- 起始基线：`18d11abb629fa1386b054055a33b015b9cbebf0d`；CI run `30554145423 / completed / success / 10 of 10`。
- Commit A：`1561eb60cd46dc1a4618fde6651426c41d7c4e20`；CI run `30559245227 / completed / success / 10 of 10`，`headSha` 精确匹配。
- 当前生产底层 prerequisite 阻断事实：`UNKNOWN / DEPLOYMENT_VERIFICATION_REQUIRED`。旧 release 的统一 false 结果不能证明 credential、permission 或 IP allowlist 的具体生产状态。

## Canonical 调用链

实际调用链不是 Controller / Service / Repository：

```text
scripts/gatew/gatew-okx-readonly-soak-control.ps1
→ immutable release test-support launcher
→ GateWOkxReadonlySoakCycleTest$PrerequisiteMain
→ Spring profile gatew-okx-readonly-soak
→ JdbcTemplate 对本地 PostgreSQL 执行只读聚合查询
→ loopback /actuator/health
→ closed-schema JSON readback
→ PowerShell closed-schema result
```

参数与绑定保持：`exchange=OKX`、`environment=LIVE`、release/source commit/manifest 由 immutable verifier 验证；pre-create 不接受 RunId，不读取 owner/account 私有值，不创建 runtime。

## 脱敏观察

| Check | Expected source | Observed sanitized value | Failure category | 可安全披露 | Decision |
| --- | --- | --- | --- | --- | --- |
| current release | canonical symlink/verifier | `c16f27c3c68d2484ad140d0557b879de08b7c78f` | 无 | 是 | 旧 release binding 通过 |
| NTP | host status | `yes` | 无 | 是 | 通过 |
| management health | loopback health | HTTP `200` | 无 | 是 | endpoint 可达 |
| readiness | loopback readiness | HTTP `401`；未读取 body、未获取 token | `UNKNOWN` | 仅状态码 | 不作为根因 |
| PostgreSQL | canonical Java readback | 旧合同统一为 false；listener 可见不等于查询成功 | `UNKNOWN` | 是 | 待新 release 验证 |
| canonical pre-create | control helper | exit `2`；`readyForAttemptCreation=false` | `INTERNAL_SANITIZED_READBACK_FAILURE`（代码级） | 是 | fail closed |
| GateW runtime | systemd/process audit | active units/timers/jobs/process=`0/0/0/0` | 无 | 是 | 未启动 |
| Attempt-10 | canonical state | `NOT_CREATED / START_BLOCKED` | 无 | 是 | 未创建 |

远端 runtime：OpenJDK `21.0.11`，PowerShell `7.6.3`。首次 SSH 在 10 秒客户端 timeout 前未执行远端命令；第二次连接成功。远端只调用 canonical status/pre-create 与允许的只读状态检查。

## RCA

| 根因候选 | 旧实现能否区分 | 代码审查证据 | 本轮判定 |
| --- | --- | --- | --- |
| management / PostgreSQL 不可达 | 否，均折叠为 false | PowerShell、launcher、JDBC 三层统一 fallback | 新合同分别输出安全 blocker |
| profile / Bean / launcher 缺失 | 否 | Java 未产生 readback 时无分类 | 新合同输出 `PROFILE_OR_BEAN_NOT_AVAILABLE` |
| response / release binding mismatch | 否 | descriptor、release、JSON 失败均无安全 reason | 新合同分别输出 blocker |
| account scope / credential 未配置 | 部分且不可靠 | 原 SQL 在 `WHERE` 提前过滤 scope/type/active | 改为单行聚合计数 |
| active count/type/local status | 否 | 被提前过滤后只能看到 0 条 | 新合同分别分类 |
| permission fact missing/stale | 否 | 原 readback 未读取 probe status/time | 新增 present/fresh 判定 |
| read/trade/withdraw permission | 部分 | 原逻辑未绑定 permission probe freshness | 新合同逐项 fail closed |
| IP allowlist | 否 | 原 readback未读取 required/probe status | 新合同输出 `IP_ALLOWLIST_NOT_VERIFIED` |
| 内部异常 | 否 | 统一 `PrerequisiteReadback.unavailable()` | `INTERNAL_SANITIZED_READBACK_FAILURE` |

最终代码级根因：`INTERNAL_SANITIZED_READBACK_FAILURE`，具体为 sanitized helper contract defect。原 SQL 和三层 fallback 把相互不同的安全前置事实统一折叠为 false，导致无法安全完成 RCA。

生产底层事实仍为 `UNKNOWN`：只有 Commit A 构建的新 immutable release 在独立部署验证任务中运行 canonical helper 后，才能根据 `blockerCodes` 判断具体运营前置条件；本任务未使用旧 release 验证新代码。

## 最小修复

- 单次只读聚合查询只返回计数、closed-set 状态与时间比较所需元数据；不读取 credential payload、credential id、owner/account 值。
- 新增 permission fact present/fresh、read permission、IP allowlist、closed-set `blockerCodes` 与随机无编码 `diagnosticId`。
- freshness 不发明额外 TTL：probe 时间不得早于 credential/account metadata 更新时间，也不得晚于本地时钟 5 分钟以上。
- release binding、PostgreSQL、management、kill switch、credential metadata 全部保持 hard gate；任何未知或异常继续 `readyForAttemptCreation=false`。
- PowerShell fallback 只输出封闭 reason code；raw exception、stack trace、SQL、JDBC 信息均不输出。
- 未新增 migration、API、Bean、DB write、OKX 调用或交易能力。

## 验证

| 验证 | 结果 |
| --- | --- |
| focused Maven support + fail-close | `BUILD SUCCESS`；60 tests，0 failures，0 errors，1 skipped |
| control helper PowerShell parser | 0 syntax errors |
| formal control self-test | `PASS`；56 cases；no network；credential accessed=false |
| remediation regression | `PASS`；32 cases；Attempt-10=false |
| security regression | `PASS`；12 cases |
| release reproducibility regression | `PASS`；16 cases；fixture manifest `8fae6c5...54de`、bundle `65940e1f...77c1`；tamper fail closed |
| governance next-action/lifecycle/current authority | `PASS` |
| IDE Java inspection | 新增 actionable warning 已清理；剩余为既有 SQL datasource 未绑定、既有 test helper warning |
| Commit A exact-head CI | run `30559245227 / completed / success / 10 of 10` |

失败记录：

- 首次 focused Maven 命令未给 `-Dsurefire.failIfNoSpecifiedTests=false` 加 PowerShell 引号，被 Maven 当作 lifecycle phase，exit `1`；修正命令 quoting 后通过。
- 本地 `mvn -f backend/pom.xml test` exit `1`：唯一错误为既有 `ResearchBacktestHappyPathLocalTest` 第 59 行查询 `accounts` 得到 0 行；隔离重跑同样失败。CI 的 `Backend Maven test` 明确先执行 `Prepare backend CI legacy account fixture`，随后 Commit A exact-head CI 全绿。未修改越界测试，未手工写本地 DB。
- `idea-mcp` 集成终端因 PowerShell executable 路径解析错误不可用，命令执行降级为仓库内直接 PowerShell；IDE 读取、格式化与 inspection 仍正常。

## 敏感数据与副作用边界

```text
credential material read=0
raw provider response read=0
manual SQL=0
OKX calls=0
production/server database writes=0
server mutations=0
Attempt-10 created=false
acceptance clock created=false
GateW units started=0
new runtime/state created=0
```

本地 Maven 测试可能按既有测试合同操作本地测试数据库；这不属于生产/server 写入，且本轮未手工写数据库。

## 风险、限制与回滚

- P0：无。
- P1：无；生产具体 blocker 未知是有意保留的部署前限制，不得猜测。
- P2：本地全量 Maven 依赖 legacy account seed；CI 已提供正式 fixture 并通过。
- P3：IDE 对未绑定 datasource 的 SQL inspection 仍有既有假阳性。
- 回滚：回退 Commit A `1561eb60cd46dc1a4618fde6651426c41d7c4e20`；服务器仍运行旧 release，本任务没有服务器状态可回滚。
- 下一动作：`NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATION-DEPLOYMENT-VERIFICATION`。该任务只能部署并验证新 immutable release 的 canonical sanitized readback；仍不得直接创建或启动 Attempt-10。
