# GateW Attempt-12 prerequisite schema 整改与 Attempt-13 授权证据

## Task classification

- 任务：`NQ-GATEW-ATTEMPT-12-PREREQUISITE-SCHEMA-REMEDIATION-AND-ATTEMPT-13-AUTHORIZATION`。
- 类型：NQ-only / production startup failure evidence / minimal schema remediation / governance authority sync。
- 目标：终态记录 Attempt-12 的真实失败，只接受已证明的 sanitized schema 单点修复，并为全新 Attempt-13/RunId 建立 fail-closed 启动授权。

## Attempt-12 production evidence

- Release/source commit：`d45fa921eccfe56e4c107037818749b971e28317`。
- Exact-head CI：`30705301218 / completed / success / 10 jobs / bad=0`。
- 唯一 RunId：`gatew-soak-20260801T164322Z-79ed8c0b`。
- Worker PID：`470754`；canonical start 只调用一次。
- 终态：lifecycle=`FAILURE_STOPPED`，exit=`exited/2`，samples/failures=`0/0`。
- 首 heartbeat、hash-chain 起点、`acceptanceStartAt`、`plannedAcceptanceAt` 均不存在；168h clock=`NOT_STARTED`。
- Credential/network/OKX/order/cancel/transfer/withdraw calls=`0`；credential/raw response exposure=`0`。
- LIVE=`DISABLED`；kill switch=`ENGAGED`。

## Root cause

生产 journal 的精确错误为 `prerequisite readback schema is invalid`。Java `PrerequisiteMain` 已输出 23-field sanitized contract，而正式 PowerShell worker 仍以旧 9-field exact schema 校验，因而在 credential、network 与 OKX 调用之前 fail-close。根因是单一 producer/consumer schema drift，不是 168h 观察阻塞，也不是服务器仍在持续部署。

## Minimal remediation and validation

- Remediation commit：`e8c334886ae6614133b0bf3f0083bc1893a11e01`。
- Message：`fix(gatew): align worker prerequisite schema`。
- Exact-head CI：`30709995836 / completed / success / 10 jobs / bad=0`。
- Worker self-test：PowerShell 5.1/7 各 `63/63`。
- Control：`81/81`；remediation：`37/37`；security：`12/12`；fail-close：`8/8`；release reproducibility：`34/34`。
- 变更仅让 worker 接受并精确验证 Java 已冻结的 23-field sanitized contract；未修改 endpoint allowlist、credential reference、permission policy、cadence、kill switch、LIVE 或交易写侧。

## Rollback and retained evidence

- Current/unit links 已恢复 `/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f`。
- Manifest：`eaf83f95f51fc938d55c4c0235eee86e9de78c67990e142cf3d0b6c62c9e8977`。
- Descriptor v1 hash：`2cf895fa6c5de38ff45f62ef681fd5a4af3d0d86e273362021d3e7e4d028ca9a`。
- 失败时 descriptor v2 备份保留于 `/tmp/nq-gatew-attempt12-precreate-v2-failed-backup.json`，hash=`a72cd4c6613bd688719e376ce2de5bcd91fdf0c948e28927b2c0dd43fec105d6`。
- Active GateW units=`0`；Attempt-12 release/evidence 保留，禁止删除、修改、复用 RunId 或自动重试。

## Governance and authority after

- Accepted batch：`GateW-ATTEMPT-12-PREREQUISITE-SCHEMA-REMEDIATION / ACCEPTED|CI_GREEN`。
- Work batch：`GateW-ATTEMPT-13-PREPARATION-AND-START / ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED`。
- Attempt-12：`FAILED / STOPPED`；production deployment=`STOPPED`。
- Attempt-13：`NOT_CREATED / AUTHORIZED`；production deployment=`NOT_STARTED`。
- 唯一下一动作：`NQ-GATEW-ATTEMPT-13-PREPARATION-AND-START`。
- Governance contract 新增 Attempt-13 preparation/running/blocked exact triples、独立 `attempt13Runtime` 与 `ATTEMPT_13_CREATED` 有序事件；cross-attempt、wrong batch/order、LIVE enabled、kill switch disengaged 与 non-exact-head evidence 均 fail-closed。

## 168h task boundary

本启动任务的完成条件只到：唯一 worker 运行、首条有效 heartbeat/hash-chain、fresh-SSH 读回、`acceptanceStartAt` 与 `plannedAcceptanceAt` 建立、RUNNING authority/evidence 及 exact-head CI GREEN。它不要求 Codex 或人工连续在线观察 168 小时。`plannedAcceptanceAt` 到达后，由独立 `NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE` 任务读取完整 evidence 并执行期满验收。

## Findings and decision

- P0：0。
- P1：0；Attempt-12 schema drift 已由 exact-head CI green 的最小修复关闭。
- P2：SSH 长命令可能断开，后续以 keepalive 与 fresh readback 判定，不重放 canonical start。
- P3：生产 journal 只允许精确 unit/time/error-code selector。

Final decision：`PASS / ATTEMPT_12_TERMINALIZED / ROOT_CAUSE_CONFIRMED / PREREQUISITE_SCHEMA_ALIGNED / RUNTIME_EXACT_HEAD_CI_GREEN / ATTEMPT_13_AUTHORIZED / LIVE_DISABLED / KILL_SWITCH_ENGAGED`。

本 authority-sync 提交与其 exact-head CI 在写入时为 `NOT_RUN`；取得 10/10 GREEN 前不得连接生产启动 Attempt-13。
