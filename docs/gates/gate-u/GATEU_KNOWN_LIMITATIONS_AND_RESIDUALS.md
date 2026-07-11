# GateU Known Limitations And Residuals

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

## Known Limitations

- Evaluation Artifact Preview 是 No-file baseline，当前固定 `UNAVAILABLE / UNKNOWN`；Python artifact 未接入。
- Aggregate 只汇总既有 metadata，不主动刷新、重算或修复底层 source。
- Source exception 会使请求失败，不提供 synthetic degraded-success payload。
- GateU 没有 scheduler、runner、runtime loop、operator action persistence 或交易执行能力。
- 本次重建 archive 对 GateU-1～4 只记录 CI success confirmed，不收录未由本任务硬前置提供的 exact run id。

## Allowed Residuals

- root `README.md`、`docs/current/README.md`、`docs/current/API.md` 不在本任务 allowlist，保持不变；其 GateU wording 可能仍停留在旧入口或旧阶段语境。
- 上述 residual 不覆盖 `docs/current/STATUS.md`、`ROADMAP.md`、`FACT_SOURCE_INDEX.md` 与本 archive 的 GateU 当前状态；后续只能在独立、明确授权任务中处理。
- 已存在 docs pre-closeout commit `f7d1b224`；它不是 GateU implementation baseline，也不是 release tag。

## Release Residual

`nq-gateu-freeze` 尚不存在。必须先由用户提交本次 archive completeness 补齐、推送并等待该提交对应 CI success，之后才可创建和推送 annotated tag。tag 实际创建前不得写 `TAGGED` 或 `RELEASE TAG PUSHED`。
