# NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START — Attempt 02

## Task classification

- 主类型：`REAL_OKX_READONLY_SOAK_START_PRECHECK`；辅助类型：
  `PRECREATE_SECURITY_GATE / SANITIZED_PREREQUISITE_ORDER_AUDIT`。
- 归属：NQ-only、GateW `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- 本 attempt 只记录 Attempt-09 启动前 prerequisite 顺序缺陷；没有创建 REAL run、没有启动 unit/clock、没有调用 OKX。

## Starting baseline

- Branch/HEAD/origin：`dev / bacd4752781b73e5eebaca171f7047da69bc9b8d`，`HEAD == origin/dev`。
- Exact-head CI：`NQ CI Baseline` run `29782483798 / completed / success / 10 of 10`。
- Server current：`/opt/nexus-quant/releases/0e8e2c128c456542b3f7695c9620e4d170c3f4f6`。
- 启动前 server facts：active GateW units=`0`、MainPID=`0`、residual=`0`、REAL runs=`0`、
  Attempt-09 name matches=`0`、runtime drop-ins=`0`、kill switch=`ENGAGED`。

## Blocking evidence

- 当前 immutable control helper 只能在 `prepare` 创建 runId、state/control/evidence 目录并启动 systemd worker 后，
  由 worker 执行 sanitized prerequisite；不存在无需 runId 的正式 pre-create action。
- 因此无法满足“sanitized prerequisite PASS 后才允许创建 REAL run”的 hard gate；若继续将先产生 run/runtime side effect，
  再验证 PostgreSQL、management health、kill switch 与 credential metadata。
- operator 还需手工展开 DB URL/user 输入，缺少固定、root-owned、闭合 schema 的 normalized descriptor。
- 本 attempt 在任何 `New-RunId`、`Ensure-Directory`、unit start、acceptance clock 或 OKX/provider 路径前停止。

## Safety result

- Decision：`BLOCKED / PRECREATE_SANITIZED_PREREQUISITE_REQUIRED`（阻塞 / 需要 pre-create 脱敏前置 gate）。
- Attempt-09：`NOT_CREATED / NOT_STARTED`（未创建 / 未启动）。
- REAL run / acceptance clock：`0 / NOT_STARTED`（0 / 未启动）。
- OKX / credential material：`NOT_CALLED / NOT_READ / NOT_OUTPUT`（未调用 / 未读取 / 未输出）。
- active units / MainPID / residual / drop-ins：`0 / 0 / 0 / 0`。

## Remediation handoff

后续单任务 `NQ-GATEW-PRECREATE-SANITIZED-PREREQUISITE-REMEDIATION-AND-RELEASE-REBUILD` 必须实现独立
pre-create action、root-owned normalized DB descriptor、固定脱敏结果 schema、REAL prepare-before-run gate、immutable release
重建、exact-head CI、服务器部署与完整 formal offline acceptance。该 remediation 完成前不得创建 Attempt-09。

## Decision

`BLOCKED / PRECREATE_SANITIZED_PREREQUISITE_REQUIRED / ATTEMPT_09_NOT_CREATED /
ACCEPTANCE_CLOCK_NOT_STARTED / OKX_NOT_CALLED`。
