# GateX API Evidence Summary

GateX-4 建立 `publishRecordId → server-resolved release → artifact verification → canonical validation/admission → GET API` 的只读闭环。调用方不能提交文件系统 path、manifest truth、trusted root 或 release identity；这些事实均由服务端解析。

API 只返回 admission preview 与可解释状态。`ELIGIBLE` 仅表示当前 server-owned facts 可以形成内存 creation plan，不创建或启动 Shadow Run，不表示远端账户、余额、permission、risk、fee 或 minimum notional 已验证。

## 写侧保护

GateX-5 的 materialization 写入口受 RBAC 和 application role guard 双层保护：anonymous=401、VIEWER=403，OPERATOR/ADMIN 也必须通过 command-time canonical admission 才能创建 `CREATED / RELEASE_BOUND` fact。

错误路径保持 fail closed：artifact/manifest/identity/guard/revision 不一致使用项目内部错误语义，不向前端暴露 SQL、内部路径、异常栈、credential 或原始外部响应。

## Validation and non-goals

分页、批量外部调用与 private exchange 不在该 API 链路中；没有循环 API、N+1 查询、无边界读取或外部网络调用。API acceptance 不改变 LIVE、交易或 Shadow runner 权限。

相关 WebMvc、RBAC、validation 与 targeted E2E 已进入 task evidence 和 exact-head CI。Freeze 后该 API contract 只能通过后续独立任务演进，不能借 GateX tag 绕过 admission 或权限边界。
