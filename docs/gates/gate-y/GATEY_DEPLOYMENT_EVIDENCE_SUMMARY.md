# GateY Deployment Evidence Summary

Production pilot release 为 `8e3dd0cf6104eb85f36a0e434ca51ea9d903705a`，release manifest SHA-256 为 `d49ca03a39df8e7de15a2bb03651381ce4c1df8db1682d63e285fdd37b61e046`。该 V46 immutable release 已完成 install/verify、atomic current activation、health/DB、NRestarts=0 与 Stop 验证。

## Accepted deployment checks

- Immutable manifest/artifact closed set verified。
- Root ownership、POSIX mode、link integrity 与 service-user write denial verified。
- Atomic current activation、health/DB 与 NRestarts=0 verified。
- Canonical Stop/VerifyStopped 后 MainPID=0。

Final close 只消费 durable reconciliation facts，没有执行 migration、创建 backup、重新 PLACE、CANCEL、transfer 或 withdraw。运行完成后 runtime stopped、MainPID=0，临时 table/column privileges=`0/0`，kill=`ENGAGED`，LIVE=false。

历史 deployment blocker、release reproducibility、inactive install、bootstrap、V43～V46 与 code-only deployment/remediation 全部保存在 `source/task-evidence/**`，失败历史未删除或改写为首轮成功。

本 freeze 不重新部署 pilot runtime，不访问服务器或 credential，不修改生产 env/systemd/database。已验证 release 只作为 GateY historical evidence；它不授权未来重新激活或扩大真实交易范围。

## Rollback boundary

历史 rollback/reproducibility/blocker 证据均保留。Tag 后不得移动 release tag；后续只能用新的 immutable release 与 forward remediation 处理问题。
