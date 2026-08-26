# GateY Runtime and Scheduling Boundary Summary

GateY runtime 的已接受范围严格限定为唯一人工受控 Attempt-01。最终状态：runtime stopped、MainPID=0、Session=`LIVE_RECONCILED`、Authority=`CLOSED`、Lease=`CLOSED`、activeLease=0、kill=`ENGAGED`、LIVE=false。

## Frozen counters

- PLACE=1。
- PLACE retry=0。
- CANCEL=0。
- Transfer/Withdraw=0/0。
- Attempt-02 与 second PLACE 均未创建/执行。

固定执行计数：PLACE=1、PLACE retry=0、CANCEL=0、Transfer=0、Withdraw=0、Attempt-02=`NOT_CREATED`、第二 PLACE=`NOT_EXECUTED`。Query-only reconciliation 与 durable-first close 不获得 mutation retry 权限。

GateY 未授权 scheduler 自动启动、worker 自动重启、unattended execution、第二 session/pilot、批量订单、跨账户/跨 venue routing 或任何资金移动。terminal session 不回到 LIVE_ACTIVE；新的执行必须是未来独立 Gate、独立 scope、独立 approval 与独立用户授权。

Freeze 过程中不启动 controller、worker、scheduler、server runtime 或 exchange transport。archive/tag/checker 操作只处理仓库和 GitHub release evidence，不改变生产运行状态。

## Failure mode

任何 identity/scope/authority/lease/kill/reconciliation 不一致都保持 fail closed；不得以调度恢复、自动 restart 或第二 PLACE 修复历史 session。
