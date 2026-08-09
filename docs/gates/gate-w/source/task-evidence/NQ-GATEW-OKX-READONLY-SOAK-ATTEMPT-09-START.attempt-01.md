# NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START — Attempt 01

## Task classification

- 主类型：`REAL_OKX_READONLY_SOAK_START_PRECHECK`；辅助类型：`SERVER_BASELINE_AUDIT / SECURITY_HARD_GATE`。
- 归属：NQ-only、GateW `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。
- 本 attempt 只记录 Attempt-09 启动前 hard gate 的实际裁决；没有创建 run、没有调用 OKX，也没有启动真实 acceptance clock。

## Scope

- 起始分支与 HEAD：`dev / 0698f23df2fc395715b5599a7e22ab84f6cd3032`，与 `origin/dev` 一致。
- 起始 exact-head CI：`NQ CI Baseline` run `29749018941 / completed / success / 10 of 10`。
- 服务器起始 runtime：`/opt/nexus-quant/releases/0698f23df2fc395715b5599a7e22ab84f6cd3032`。
- 启动前只读核对 GateW units、MainPID、residual process、runtime drop-in、kill-switch/clock tooling contract 与禁止边界。

## Blocking evidence

- 服务器存在 17 个历史 GateW offline acceptance run 的 stale runtime drop-in，共 34 个 `offline.conf` 文件；虽然当时
  active GateW units=`0`、MainPID=`0`、residual=`0`，但启动基线不满足 zero-drop-in hard gate。
- REAL bootstrap/sample 合同仍把 kill switch 的 `DISENGAGED` 当作正常条件，不能证明 `REAL_READONLY_SOAK` 全程要求
  `GLOBAL_TRADING=ENGAGED`。
- prepare 阶段会提前投影 acceptance start/planned time，不能证明全部 prerequisite 满足后才以 create-once 方式启动时钟。
- 因以上结构性阻塞，Attempt-09 没有进入 prepare/start；没有生成 Attempt-09 runId、sample、acceptance clock 或 provider
  evidence。

## Safety result

- Decision：`BLOCKED / SERVER_BASELINE_NOT_READY`（阻塞 / 服务器基线未就绪）。
- Attempt-09：`NOT_CREATED`（未创建）。
- Acceptance clock：`NOT_STARTED`（未启动）。
- OKX：`NOT_CALLED`（未调用）。
- Credential material：`NOT_READ / NOT_OUTPUT`（未读取 / 未输出）。
- LIVE、下单、撤单、转账、提现、AI、DH runtime：均未启用或触达。

## Remediation handoff

- stale drop-in 只允许在 unit inactive、MainPID/residual 全零且路径精确归属 GateW offline acceptance 后清理；清理前必须形成
  owner/mode/SHA-256 inventory 与可恢复备份。
- kill switch、acceptance clock、脱敏 prerequisite readback 与 immutable release 必须在独立 remediation candidate/final
  离线验收通过后，才允许重新尝试 Attempt-09。
- 本 attempt 的阻塞裁决不可改写为 PASS；后续修复证据记录在
  `NQ-GATEW-SOAK-START-CONTRACT-REMEDIATION-AND-IMMUTABLE-RELEASE-REBUILD.attempt-01.md`。

## Decision

`BLOCKED / SERVER_BASELINE_NOT_READY / ATTEMPT_09_NOT_CREATED / ACCEPTANCE_CLOCK_NOT_STARTED / OKX_NOT_CALLED`（阻塞 /
服务器基线未就绪 / Attempt-09 未创建 / acceptance clock 未启动 / 未调用 OKX）。
