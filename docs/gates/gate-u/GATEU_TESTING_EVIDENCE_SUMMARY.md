# GateU Testing Evidence Summary

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

## Accepted Results

| Layer | Command / evidence | Result | Scope |
| --- | --- | --- | --- |
| Backend | `mvn -ntp -f backend/pom.xml -pl nq-core,nq-api,nq-infra,nq-app -am test` | `BUILD SUCCESS` | 23-module reactor；GateU read-model、query、controller 与既有回归 |
| Frontend | `npm --prefix frontend run build` | `PASS` | TypeScript 与 Vite production build |
| E2E | 两个指定 Chromium smoke | `4 passed` | Strategy Validation runtime evidence 与 Shadow Run detail |
| CI | GitHub Actions `NQ CI Baseline` run `29108265105` | `completed / success` | `headSha=9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d` |

GateU-1～4 各 batch CI success 已确认；本次重建归档不纳入其 exact run id，避免把未在本任务硬前置中给出的 run id 写成新的 archive fact。

## Covered Behavior

- 统一 metadata calculator 的 availability/freshness 与时间边界。
- 五来源固定顺序、每来源一次、全 available/fresh 才成功聚合。
- unavailable/partial/unknown/stale/future timestamp 与 source failure 的 fail-closed 行为。
- GET-only controller、trace id、response safety flags、forbidden trading/credential fields 不出现。
- 前端固定五来源、No-file 来源、`PARTIAL / UNKNOWN` 展示与手动 refetch。

## Warnings And Not Run

已知非阻断 warning：SLF4J provider、Mockito dynamic agent / Byte Buddy、Vite chunk size、Ant Design v5 / React 19 compatibility。本次 archive completeness docs-only 修复未重跑 Maven、frontend build、Playwright，也未运行 Python pytest/mypy/ruff；引用的是上述已完成验证结果。
