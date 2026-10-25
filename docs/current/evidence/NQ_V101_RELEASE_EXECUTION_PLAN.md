# NQ-V101-RELEASE-EXECUTION-PLAN

日期：2026-10-25（Asia/Shanghai）。本文件只准备后续 `NQ-V101-OFFICIAL-GITHUB-RELEASE-DELIVERY` 的操作方案；当前没有创建 Snapshot Commit、分支、Tag、Draft Release 或远端资产，没有 Commit / Push / PR / Merge。

## 发布前提与固定输入

所有者必须明确回复 `APPROVE_PRODUCT_RELEASE`，授权对象以 [产品发布授权申请](NQ_V101_PRODUCT_RELEASE_AUTHORIZATION.md) 为准。`REJECT_PRODUCT_RELEASE` 必须停止发布。既有 Bridge 审批、PR Merge、CI SUCCESS 均不能替代。当前授权只允许准备，不执行下列发布计划。

| 对象 | 固定值 |
| --- | --- |
| Product / Version | NexusQuant Self-Hosted / 1.0.1 |
| Source Commit | `b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae` |
| Source Tree | `3b77583e0e1e0d2b52ccfa0cade1588d0075bc45` |
| Source Manifest SHA256 | `6e4268ffd9479992f225bd8a2c2470fbceefaef3fe7afedba1938af35e2157e1` |
| Release Source Hash | `c4787a97cccbe83928f5e05148d13afb084695e4b5989d1234a39c4247d7845c` |
| Package Manifest SHA256 | `f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db` |
| TAR SHA256 | `7248a47823571fb112ea91f317e092cb6c47108b934d90ef52c299adb7c9e006` |
| ZIP SHA256 | `5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb` |
| Signing Commit / Run / Attestation | `99f63020c62a89926fbe79f9322532a37ce3ed1b` / `40165347783` / `59600294` |
| Supported Host | Windows 11 amd64 / Docker Desktop 已安装且运行 / Linux Containers |
| Database / Schema / License | PostgreSQL 16.15 / V47 / Apache-2.0 |
| Public Demo / LIVE | EXCLUDED / DISABLED |

正式输入目录为 `E:/nq-v101-final-refreeze-20261025-b0433fca/`：不可变源码 `source-b0433fca6a9c/`、包目录 `package-amd64/`、原始 ZIP `NexusQuant-1.0.1-windows-amd64-formal-candidate.zip`。本轮已完整重新哈希并逐文件核对 ZIP。后续不得重新构建、重新压缩、替换安装器、修改 JDK 缓解策略或 Manifest。

## Snapshot / Tag 精确创建计划

沿用 v1.0.0 的独立无父白名单 Snapshot 合同。该合同已通过当前 Git Tag 对象、实际 Release 元数据及固定源码 `release/source-policy.md` 核对：v1.0.0 Annotated Tag 对象 `955023dc1325093de21dca85b918fc911f4a1e6a` 指向 Snapshot `be816342581c8ab09a2c9b70dd8e9d1c88dcec8c`，Tree `ba404ffe111e64ad7596bb1c53132a63c089884c`。不修改已发布 v1.0.0。

v1.0.1 计划如下：

1. 取得所有者明确产品发布授权，确认后续任务包含 Snapshot Commit、必要远端分支推送、Annotated Tag、Draft、资产上传和 Publish 的动作授权。当前不执行。
2. 在仓库外独立 staging / 临时 index 按原始 `.release-source.json` 精确构造投影，保留每个 Git blob 与 100644/100755 模式；避免 Windows `autocrlf` 或 chmod 使模式/字节漂移。不从当前开发工作树复制替代输入。
3. 文件集合必须严格等于 **2,182 个 manifest 文件 + `.release-source.json` = 2,183**。保留闭包内已有 `scripts/verify/upgrade_business_baseline.py`，不额外加入 exporter、source-manifest、签名工作流、开发证据、测试或数据。
4. 预期 Snapshot Tree：**`d5c3e2b20777440503339d467a84ccf6e968de33`**。metadata blob：`df8202de700d982b0abbaea2e524a649caf9bf34`；metadata SHA256：`f8545576f2cfed9e0f6ebd06c188304bc52286b671138de7d266a4149513d44c`。本轮仅按 Git 对象编码计算，未写入 Git object database 或 refs；详见外部 `snapshot-plan.json`。
5. metadata 的 `sourceCommit` 必须继续为 `b0433fca…`，`releaseTreeSha256` 必须继续为 `c4787a97…`。包 `SOURCE_SHA` 同样保持 `b0433fca…`。两者不改成 Snapshot SHA 或签名工作流 SHA。
6. 后续创建独立 Snapshot Commit，建议分支 `release/1.0.1`，无父；Commit message 记录发行源码、原始 Tree、source-manifest hash、release-source hash、CI 基线和包身份。Commit SHA 取决于实际提交元数据，当前为 **NOT_CREATED**，不得预填。创建后必须检查精确 Tree、完整路径/模式/blob 集及 metadata 原字节，任一不符即停止。
7. 在推送前后分别核对远端分支和 Tag 是否存在。若存在任何同名对象，先核对真实身份并停止创建；禁止强制覆盖或把既有分支重置到新对象。
8. Annotated Tag `v1.0.1` **只指向上述验收通过的实际 Snapshot Commit**。Tag message 记录 Snapshot Commit/Tree、Package SOURCE_SHA、源码 Hash、包 Manifest/TAR/ZIP、签名 Commit 及 CI，并声明实例签名不等于发布授权。记录 Tag 对象、peeled Commit 及远端对象一致性。

Snapshot 的独立历史本身不改变开发 CI 身份。当前发行源码 CI `40157573104 / b0433fca…` 与 PR #81 合并后 CI `40164710397 / 99f63020…` 均有各自证明；不虚构 Snapshot CI。后续只执行现有合同适用的精确对象/发布检查，不重跑未变化的全部技术资格；若实际出现新的强制检查缺口，则停止依赖的发布操作。

## Release 资产准备

| 计划公开名称 | 当前真实来源 | 字节数 | SHA256 / 状态 |
| --- | --- | --- | --- |
| `NexusQuant-1.0.1-windows-amd64.zip` | 原始 `NexusQuant-1.0.1-windows-amd64-formal-candidate.zip`，后续只复制或改变上传 basename | 320691114 | `5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb` |
| `NexusQuant-1.0.1-windows-amd64.zip.sha256` | 本轮外部材料目录中已生成，内容为上表摘要、两个空格、正式文件名及 LF | 101 | `768bdcbe14f78a02d83b0cd49ad2c8d0913e1a9628c302a9e2bfffbca5af6d16` |
| `NexusQuant-1.0.1-provenance.json` | 后续沿用 v1.0.0 的真实来源映射合同，取得实际 Snapshot/Tag 对象后生成 | NOT_GENERATED | 当前没有现成最终文件，不预填大小或摘要 |
| `NexusQuant-1.0.1-provenance.json.sha256` | 对上一项最终真实字节计算 | NOT_GENERATED | 当前不生成虚假摘要 |

provenance JSON 是**未签名的发布来源映射**，必须明确其性质，不宣称 GitHub runner 重新构建或独立哈希本地 TAR。它只能引用实际 Git/构建记录、原 21 个包文件摘要、发行源码和独立 Snapshot/Tag 真实身份、CI、平台、许可证及原 ZIP 摘要。`59600294` 是实例升级授权，不能冒充该产品包的构建 provenance。没有有效构建签名时明确标记，而不是制造签名字段。

最终 provenance 依赖尚未创建的 Snapshot/Tag，因此留在后续正式交付生成，并在 Draft 阶段核对实际大小、SHA256 和 sidecar。这是明确的执行依赖，不是当前包身份缺口。任何字段无法实证就停止该发布路径，不沿用 v1.0.0 provenance，也不发布带待填字段的资产。

不公开分发隔离实例原配置、备份、数据库、口令或密钥；当前固定实例 Transition/Bundle 不作为通用升级许可随安装包分发。原公开签名可通过已知 GitHub Attestation / Run 链接核验。

## 发布步骤与验收

按顺序执行：所有者明确授权 → 验证并创建独立 Snapshot → 验证 Annotated Tag → 创建 GitHub Draft → 上传全部已核验资产 → 远端资产摘要核对 → Release Notes 核对 → Publish → 发布后复核。

1. 重新读取 dev、PR #81、精确 CI、Tag 与 Release；读操作失败不能当作不存在。同名对象已存在则停止核对，不能覆盖。本轮原生 fetch 失败已记录；后续需成功 fetch 或按精确 GitHub API 及本地对象验证明确补足，不把陈旧 origin/dev 当作最新远端。
2. 重算原 ZIP、Manifest、TAR 与 21 文件摘要，确认所有者批准对象未漂移。复制 ZIP 至上传 staging 时保留原字节，复制后重哈希，不解包重压。
3. 创建并验证上述 Snapshot 与 Tag。保留实际 Commit、Tree、Tag object、peeled target、远端 refs 及执行者授权记录。
4. 以 `NexusQuant v1.0.1` 创建 Draft，目标为已验证 Tag，不使用默认 dev 或 GitHub 自动生成说明。正文采用 [Release Notes 定稿](NQ_V101_RELEASE_NOTES_FINAL.md) 的准确内容；发布时仅可同步真实 Snapshot/Tag 身份及发布时态，范围和摘要不可变。
5. 上传四项真实最终资产；检查每项 `state=uploaded`、完整 size 与 SHA256。GitHub `digest` 有值时与本地严格一致；若服务不提供 digest，须实际完整下载并计算摘要，不能仅依赖名称、ETag、前缀/尾部 range 或客户端进度。
6. 远端取回 Draft 正文，核对支持平台、首次 Bridge 限制、镜像 TAR/ZIP 区分、实例独立授权、LIVE/Public Demo 范围、CVE 缓解措辞、历史例外、所有不可变身份及完整资产集合。确保产品发布所有者授权已记录。
7. 所有核对通过后才将 Draft 发布为正式、非 prerelease 的 v1.0.1。发布后读取 Release ID、published_at、draft/prerelease 标志、Tag object/peeled target、正文及所有资产摘要；记录本地完整验证、服务端摘要、匿名完整下载或部分访问检查的实际覆盖，不能夸大下载验证。

## 失败处理与回退

- 上传失败或留下 `starter` / digest-null 的不完整占位时，保持 Draft，不 Publish。先核对该任务 Release/asset ID，后续在明确资产操作授权内处理本任务占位；不得触及其他版本或已发布资产。大文件上传使用明确超时；UNKNOWN 结果先查询远端再决定有界重试，避免重复创建。
- 任何 Manifest / TAR / ZIP / 资产摘要或 Source/Snapshot/Tag 映射不符，立即停止，保留失败证据；不重建、重压缩或手工改 Manifest 以匹配预期。
- Tag 目标错误或 Tag 已存在时停止，不强制重推、删除重建或覆盖修复；由所有者决定后续版本/撤回方案。
- 发布前失败保留可核对的 Draft 和对象状态，不自动删除或伪装为发布完成；后续按明确授权处理。
- 已发布后发现问题，采用明确撤回公告或新补丁版本流程；不静默替换 ZIP / provenance，不改写已发布 Tag。v1.0.0 保持不变。
- 安装实例升级失败时按原 Bridge 已知事务、完整集群副本及恢复合同处理；本计划不执行任何用户库恢复，也不提供数据库降级或仅换旧镜像的指令。

## 当前状态

```text
Release Assets: ORIGINAL_ZIP_VERIFIED;CHECKSUM_PREPARED;FINAL_PROVENANCE_DEFERRED_UNTIL_REAL_SNAPSHOT_TAG
Snapshot: EXACT_TREE_PLANNED / NOT_CREATED
Tag: NOT_CREATED
Draft / Release: NOT_CREATED
Product Release Authorization: NOT_GRANTED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
```
