# NQ-GATEW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT-FIX — Attempt 01

## Task classification

- 归属：NQ-only。
- 类型：`REPRODUCIBLE_BUILD_FIX / RELEASE_MANIFEST_CONTRACT_FIX / SUPPLY_CHAIN_REGRESSION / FACT_SOURCE_SYNC / TASK_EVIDENCE / COMMIT_AND_EXACT_HEAD_CI`。
- 当前阶段：实现、Commit A exact-head CI 与最终双 detached worktree 证明均已完成；待 Commit B authority/evidence sync 的 exact-head CI。

## Root cause

source commit `61f0b94fadbc87b883a7365eaacc4e8f63829a88` 的 hashed manifest 写入实际构建时间 `createdAt`，verifier 又对整个文件计算 SHA-256，导致同一 exact commit 的 release identity 随时间变化。进一步回归还确认两项同类不确定性：PowerShell `Sort-Object` 的 culture-sensitive 顺序，以及 .NET Framework/现代 .NET `ZipArchive` header/compression bytes 差异。

旧 manifest 输出：

- 原记录：`f2ec7b00238cb2b718a82d298edc549d41833975ff42f2c8e5412e4db8b704fd`。
- rebuild-1：`b25b065c...ed12`（任务输入只提供缩写）。
- rebuild-2：`9c904671...a7d2`（任务输入只提供缩写）。
- 历史分类：`HISTORICAL_NON_REPRODUCIBLE_BUILD_OUTPUT / NOT_DEPLOYABLE_BASELINE`（历史不可复现构建输出 / 不可部署基线）。

## Deterministic contracts

- 时间：manifest schema `nq-gatew-release-v2` 删除 `createdAt`，只保留 exact source commit 的 Git epoch 转换结果 `sourceCommitTimestamp`，固定 UTC 秒级格式 `yyyy-MM-ddTHH:mm:ssZ`。
- JSON：UTF-8 without BOM、LF、固定 property 顺序、ordinal artifact path 顺序、InvariantCulture 数字、lowercase JSON boolean、无尾随空格；verifier 解析后按共享 module 重序列化并逐字节比对。
- JAR：共享 canonical ZIP writer 固定 stored entry、UTF-8 flag、DOS epoch、CRC32、local/central headers、external attributes 与 ordinal entry 顺序，不依赖运行时 `ZipArchive` 输出。
- transport bundle：canonical uncompressed USTAR；只含 `release-manifest.json` 与 manifest 声明的 artifact closed set；固定 ordinal entry 顺序、mode、uid/gid=0、owner/group=`root`、entry mtime=source commit epoch、header/padding/terminator。
- 临时随机路径仅用于未提交的构建 staging，不进入 manifest、artifact、tar 或 release identity。
- `build-receipt.json`：未生成；不进入 artifact closed set、不安装。

## Pre-CI validation

| 验证 | 结果 |
| --- | --- |
| PowerShell 5.1 / 7 AST | `PASS` |
| reproducibility regression | 双引擎各 `16 cases PASS`；间隔 2 秒、不同路径、locale/timezone、不同 commit、dirty worktree、missing/extra/noncanonical/tamper 均覆盖 |
| tamper | `BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH` |
| builder self-test | PowerShell 5.1 / 7 `PASS` |
| installer self-test | PowerShell 5.1 / 7 `PASS` |
| governance next-action/lifecycle/task-evidence | PowerShell 5.1 / 7 `PASS` |
| 真实 candidate build | PowerShell 5.1 / 7 manifest 都为 `0275fa23f45a787d8cdd0c90a0e0e957330bf75937454de168c8c729e61a522a` |
| 真实 candidate canonical tar | PowerShell 5.1 / 7 bundle 都为 `b9a61a6aabfd502771d4e3e1b4f1a2b0a56e85efc11a49643e1e3a0b44fb0626` |
| 真实 candidate artifacts | 两引擎均 `131`，逐 path/size/mode/SHA-256 一致 |

上述 candidate 值只证明 pre-Commit A diff 的跨引擎实现，不是正式部署基线。Commit A CI 成功后，从同一 Commit A 的两份 detached worktree 得到正式证明：

- 新 source commit：`c16f27c3c68d2484ad140d0557b879de08b7c78f`。
- Commit A CI：run `30537845010 / completed / success / 10 of 10`，`headSha` 精确等于 Commit A。
- worktree A：`E:\Project\nexus-quant-gatew-repro-a-c16f`，detached Commit A，PowerShell 5.1，build start `2026-07-30T11:18:55.7893302Z`。
- worktree B：`E:\Project\nexus-quant-gatew-repro-b-c16f`，detached Commit A，PowerShell 7，build start `2026-07-30T11:19:31.3714507Z`。
- build start separation：`35.582s`。
- manifest SHA-256 A / B：`eaf83f95f51fc938d55c4c0235eee86e9de78c67990e142cf3d0b6c62c9e8977` / 相同；bytes identical=`true`。
- bundle SHA-256 A / B：`60a11dde87a4cbfcff8adbd32966b3dd28463d3399b8ba25db01eb836ed0ec1b` / 相同；bytes identical=`true`。
- artifact count A / B：`131 / 131`；path/size/mode/SHA-256 descriptor identical=`true`。
- closed set：manifest + declared artifacts 与两份 USTAR entry set/order 完全一致；绝对路径、`createdAt`、`build-receipt.json` 均不存在。
- source commit timestamp：`2026-07-30T11:15:08Z`，精确匹配 Commit A Git epoch。
- tamper：exit `2`，`BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH`。

## Findings

- P0：无。
- P1：无。
- P2：无开放项。
- P3：无。

## Boundary

- 服务器变更：`0`。
- SSH：未使用。
- 上传 / 安装 / systemd：未执行。
- Attempt-09：保持 `REJECTED`，未修改历史 evidence。
- Attempt-10：`false / NOT_CREATED / NOT_AUTHORIZED`。
- OKX / credential / database / LIVE / trading write：未访问。
- GateW freeze/archive/tag：未进入。

## Current decision

`IMPLEMENTED / REPRODUCIBLE_RELEASE_VERIFIED / COMMITTED / CI_GREEN / DEPLOYMENT_RETRY_PENDING / ATTEMPT_10_NOT_AUTHORIZED`。
