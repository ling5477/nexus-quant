# NQ-GATEW-2 OKX Spot Private Read-only Probe Implementation Attempt 01

## Decision

```text
BLOCKED / CREDENTIAL_BOUNDARY_UNSAFE
```

本文件只记录此前 implementation attempt 的阻断事实，不表示 GateW-2 已实现、已测试或已获准访问真实 credential。

## Starting Baseline

- branch：`dev`。
- starting HEAD：`31c8171df26bc1eb9f93da19cf0576c0ac48116b`。
- `origin/dev`：`31c8171df26bc1eb9f93da19cf0576c0ac48116b`。
- exact-HEAD CI：`NQ CI Baseline` run `29219687588`，`completed / success`，`headSha=31c8171df26bc1eb9f93da19cf0576c0ac48116b`。
- GateW-1 commit：`31c8171df26bc1eb9f93da19cf0576c0ac48116b`，包含 typed capability matrix 与 endpoint guard。

## Blocking Conflict

GateW plan 要求 GateW-2 在真实 credential/private read-only probe 前完成独立 security review；此前 implementation 提示词把 implementation 放在该 review 之前。继续实现会绕过 credential、private transport、官方协议和 Spring 装配的高风险前置，因此 fail-closed 停止。

## Read-only Checks Completed Before Blocking

- 核验 Git 分支、clean worktree、HEAD/origin 对齐和 GateW-1 exact-HEAD CI。
- 阅读 GateW plan/current authority、GateW-1 capability matrix/endpoint guard、credential domain/repository/JDBC、NoReal probe、历史 OKX signer/client/boundary 与 Spring composition root。
- 识别到现有 JDBC decrypt 链把 plaintext 暴露为 immutable `String`，现有通用 OKX client 接收任意 method/path，现有 probe service 在事务内调用 port 并持久化状态；上述路径均不能直接复用于新的 GateW-2 最小 probe。

## Actions Not Taken

- 未读取、选择、解密或验证任何真实 credential；未读取 `.env`、key/pem、secret、credential、log、dump 或 backup 文件。
- 未调用 OKX API，未创建 HTTP client，未执行 private request。
- 未修改 Java、API、migration、frontend、CI 或其他仓库文件。
- 未运行 Maven：该 attempt 在任何代码修改前因安全前置顺序冲突而停止，运行代码回归不能解除 credential boundary blocker。

## Unblock Requirement

先完成并提交 `NQ-GATEW-2-SECURITY-REVIEW`，冻结 credential selection/decrypt、typed operation allowlist、official protocol、transport、profile、redaction、non-persistence 与 manual smoke 边界；该 security review commit 取得 exact-HEAD CI green 后，才能重新编写并执行 GateW-2 implementation task。

## Boundary Confirmation

`REAL_SMOKE=NOT_RUN`。LIVE、Shadow trading、AI、DH runtime、Integration runtime 均未启用；无 private client、无 real permission probe、无下单/撤单/转账/提现。
