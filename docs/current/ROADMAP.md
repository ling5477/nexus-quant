# Roadmap

## 总路线

```text
GateR frozen / accepted / tagged
  ↓
GateS frozen / accepted / tagged
  ↓
GateT frozen / accepted / tagged
  ↓
GateU-1..5 completed
  ↓
GateU FREEZE READY / NOT TAGGED
  ↓
GateV NOT STARTED
```

更早完成阶段的历史证据入口为 `docs/gates/**` 或 `docs/archive/**`，不覆盖 `docs/current` 的当前状态。

## 当前阶段

- GateU：`FREEZE READY / NOT TAGGED`（已具备冻结条件 / 尚未打 tag）。
- GateU-1～GateU-5：`COMPLETED`（已完成）。
- GateU baseline：`9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`。
- GateU baseline CI：run `29108265105`，`NQ CI Baseline`，`completed / success`。
- GateU archive pointer：`docs/gates/gate-u/README.md`。
- GateU tag：`nq-gateu-freeze` 尚不存在。
- GateV：`NOT STARTED`（未开始）。

## GateU Closeout Summary

| Batch | 状态 | 目标 |
| --- | --- | --- |
| GateU-1 | `COMPLETED` | 统一 read-model evidence metadata 与 calculator；Shadow Validation Workflow / Shadow Run metadata |
| GateU-2 | `COMPLETED` | Consistency Evidence metadata |
| GateU-3 | `COMPLETED` | Incident / Replay Review metadata |
| GateU-4 | `COMPLETED` | Evaluation Artifact Preview No-file metadata |
| GateU-5 | `COMPLETED` | 五来源 Validation Operations Runtime Evidence aggregate GET 与前端总览 |
| GateU-FREEZE | `FREEZE READY / NOT TAGGED` | 最小证据归档与 release tag 准备；commit / push / tag 未执行 |

## 下一步规则

1. 用户精确暂存并复核本轮允许文档。
2. 用户提交并推送 `docs(gateu): freeze validation runtime evidence baseline`。
3. 等待该新提交对应的 `NQ CI Baseline` 为 `completed / success`。
4. 由用户创建并推送 annotated tag `nq-gateu-freeze`，随后验证 local / remote tag 与 peeled commit。
5. 在 tag 实际推送前不得写成 `TAGGED`；不得启动 GateV，不得继续新增 read-model。

## 当前边界

- LIVE：`DISABLED`（关闭）。
- Shadow trading：`NOT ENABLED`（未启用）。
- GateU runtime evidence 仅为 GET-only / read-only / no-side-effect / not trading authorization。
- 不新增 migration、写 SQL、scheduler、runner、内部 HTTP、credential、private endpoint、real provider、RealClient 或真实交易路径。
- AI：`NOT STARTED`；DH runtime：`NOT INTEGRATED`；GateV：`NOT STARTED`。
