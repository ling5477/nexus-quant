# Artifact Verification Security Prototype

状态：`TEST-ONLY PROTOTYPE / NON-AUTHORITATIVE`（仅测试原型 / 非权威实现）。

固定边界：

```text
NO PRODUCTION INTEGRATION
NOT TRADING AUTHORIZATION
DO NOT MERGE INTO DEV BEFORE GATEW ACCEPT
```

## 1. 目标与非目标

本原型只验证 `strategy-release-manifest.v1` 的 artifact 文件完整性安全边界：

- 文件必须位于显式 trusted root 内；
- 路径、link、普通文件类型、大小和 SHA-256 必须 fail-closed；
- 使用固定 buffer 流式读取，并限制最大读取字节数；
- 通过读取前后属性比较发现常见 TOCTOU 修改或替换；
- 复用既有 aggregate digest canonicalization；
- 错误结果不得泄漏 trusted root、文件内容或原始异常。

本原型不创建 production verifier，不注册 Spring Bean，不接入 API、数据库、Python runtime、scheduler、Shadow Run 或交易状态机。
`VERIFIED`（已验证）只表示本次 test-only 完整性条件满足，不表示策略批准、Shadow 启动、LIVE readiness 或交易授权。

## 2. Threat model

原型覆盖以下攻击或失败场景：

- 调用方提供绝对路径、Windows drive path、UNC、反斜杠、控制字符或 `..` 穿越；
- 目标或中间组件通过 symbolic link 逃离 trusted root；
- Java NIO 可识别的 reparse、junction 或其他特殊文件类型；
- 目录、缺失文件或非普通文件伪装成 artifact；
- 文件过大导致无界读取或内存压力；
- manifest 中 size 或 SHA-256 与实际文件不一致；
- pre-read 检查后、读取前发生文件修改或替换；
- 异常信息、结果对象或测试输出泄漏本地绝对路径、文件内容或原始 `IOException`。

不在本原型可证明范围内：

- JDK/文件系统 provider 无法识别的全部 Windows reparse point；
- 恶意本地高权限主体持续竞争路径组件；
- 稳定文件描述符或目录句柄级无竞态证明；
- production root provisioning、owner/ACL、mount、sandbox、审计持久化和 verifier 版本治理。

## 3. Trusted root 定义

trusted root 必须：

- 非空且已存在；
- 是目录；
- 自身不是 symbolic link；
- `BasicFileAttributes` 不为 `isOther()`；
- 通过 `toAbsolutePath().normalize()` 与 `toRealPath()` 得到 Path 语义下的边界。

结果或异常不保存、不回传 trusted root 的绝对路径。原型不读取仓库外真实 artifact，只使用 JUnit
`@TempDir` 创建的虚构测试文件。

## 4. 路径校验算法

1. 在调用 `Path` 解析前拒绝空路径、NUL/换行/回车等控制字符、`/` 或 `\` 开头、反斜杠、Windows drive prefix、UNC 和重复 `/`。
2. 只接受 `[A-Za-z0-9._-]+` path segment；拒绝空 segment、`.` 和独立 `..`。
3. 使用 `root.resolve(relativePath).normalize()`，再用 `Path.startsWith(root)` 做 containment check；不使用 字符串前缀判断。
4. 从 trusted root 开始逐级读取 `BasicFileAttributes`，统一使用 `LinkOption.NOFOLLOW_LINKS`。
5. 拒绝任一级 symbolic link、`isOther()` 或不可遍历的中间组件。
6. 对最终 `toRealPath()` 再次确认仍位于 trusted root 的 real path 下。
7. 最终目标必须是普通文件。

## 5. Link / reparse 处理

原型同时使用：

- `Files.isSymbolicLink`；
- `BasicFileAttributes.isSymbolicLink()`；
- `BasicFileAttributes.isOther()`；
- `LinkOption.NOFOLLOW_LINKS`；
- 逐级组件检查和 real-path containment。

当前 Windows 环境无法获得 symbolic link 创建权限，三个真实 symlink 测试使用 JUnit assumption 跳过，状态为
`NOT_RUN / SYMLINK_PRIVILEGE_UNAVAILABLE`（未运行 / symbolic link 权限不可用），不得写成通过。

Java NIO 能识别为 symbolic link 或 `isOther()` 的 junction/reparse 会被拒绝；JDK/provider 不能可靠识别的类型是 P2
residual。正式 GateX production verifier 前必须做 Windows 专项审查，不能声称本原型已完整解决所有 reparse point。

## 6. 流式 SHA-256 与最大文件限制

- 使用 `Files.newByteChannel`、`READ` 和 `NOFOLLOW_LINKS` 打开文件；
- 使用 8192-byte 固定 `ByteBuffer`；
- 每块增量更新 SHA-256，不使用 `Files.readAllBytes`，不把文件整体载入内存；
- 读取前先拒绝属性 size 超过 `maxAllowedBytes` 的文件；
- 流式读取时再次累计字节数，超过上限立即 fail-closed；
- 同时要求实际字节数等于 `expectedSizeBytes`；
- 摘要只接受 64 位小写十六进制 SHA-256；
- 摘要比较使用 `MessageDigest.isEqual`；
- `REJECTED` 或 `UNKNOWN` 结果不携带实际摘要、实际大小或 path identity。

## 7. TOCTOU 检测策略

原型在文件读取前后分别读取 `BasicFileAttributes`，比较：

- `fileKey`（平台提供时）；
- `size`；
- `lastModifiedTime`；
- `isRegularFile`。

package-private test hook 位于 pre-read attributes 与安全打开之间，只用于确定性模拟修改和替换。发现差异返回：

```text
REJECTED / ARTIFACT_CHANGED_DURING_VERIFICATION
```

两次 stat 只能降低常见 TOCTOU 风险，不能等同于基于稳定文件描述符或 `SecureDirectoryStream` 的完整防护。 如果 `fileKey`
不可用、时间戳粒度不足且替换文件保持相同 size/time，双重属性检查仍可能无法区分。

## 8. Aggregate digest 规则引用

本原型严格复用
`RESEARCH_TO_SHADOW_CONTRACT_PREPARATION.md` 和 `ManifestPrototypeContract.computeArtifactDigest` 的现有规则：

1. 按 `logicalName`、再按 `relativePath` 升序排序；
2. 每项依次拼接 `logicalName`、`relativePath`、lowercase `sha256`、十进制 `sizeBytes`、`mediaType`；
3. U+001F 分隔字段，LF 分隔记录，末尾不添加 LF；
4. 对 UTF-8 bytes 计算 SHA-256，输出 64 位小写十六进制。

原型拒绝 canonical 字段中的控制字符和 U+001F，避免字段边界歧义。测试证明输入顺序不影响结果，并证明五个 canonical 字段任一变化都会改变
digest。本任务没有发明第二套摘要规则。

## 9. Finding taxonomy 与错误脱敏

test-only taxonomy：

```text
INVALID_RELATIVE_PATH
PATH_ESCAPES_TRUSTED_ROOT
TRUSTED_ROOT_INVALID
SYMLINK_NOT_ALLOWED
SPECIAL_FILE_NOT_ALLOWED
ARTIFACT_NOT_FOUND
ARTIFACT_NOT_REGULAR_FILE
ARTIFACT_TOO_LARGE
SIZE_MISMATCH
DIGEST_MISMATCH
ARTIFACT_CHANGED_DURING_VERIFICATION
PLATFORM_LINK_GUARANTEE_UNAVAILABLE
CANONICALIZATION_FAILED
VERIFICATION_IO_FAILED
```

失败结果只保留安全显示后的 `logicalName`、`relativePath`、`findingCode` 和固定短原因；其余字段为
`null`。绝对/drive/UNC/backslash/control 路径统一显示为 `<invalid-relative-path>`。实现不回传原始异常 message、stack
trace、trusted root、用户目录、临时目录或 artifact 内容。

## 10. 测试结果

执行环境：Windows、Java 21、JUnit 5、`@TempDir`；测试未访问网络或仓库外真实文件。

```text
TrustedRootArtifactVerifierPrototypeTest:
22 tests / 0 failures / 0 errors / 3 skipped

*PrototypeTest:
35 tests / 0 failures / 0 errors / 3 skipped

nq-core -am:
381 tests / 0 failures / 0 errors / 3 skipped
```

三个 skipped 均为真实 symbolic link 测试，原因是当前 Windows 权限不可用。路径拒绝、普通文件、size、 max bytes、流式
SHA-256、TOCTOU、aggregate digest 和错误脱敏测试均已执行并通过。

## 11. 跨平台限制

- symbolic link 创建是否允许取决于 Windows Developer Mode、进程权限和文件系统；
- reparse/junction 的 `isSymbolicLink` / `isOther` 表达由 JDK/provider 决定；
- `fileKey` 可为空，`lastModifiedTime` 粒度由文件系统决定；
- `NOFOLLOW_LINKS` 安全打开语义不可用时返回
  `UNKNOWN / PLATFORM_LINK_GUARANTEE_UNAVAILABLE`，不退化为不安全读取；
- 当前没有 Linux/WSL、NTFS junction、mount point、SMB/NFS 或恶意并发压力验证。

## 12. 正式 GateX production verifier 前置条件

1. GateW 已接受且 current authority 明确允许 GateX；
2. 单独完成 Windows reparse/junction 与目标部署文件系统专项审查；
3. 采用稳定目录句柄/文件描述符策略，或明确证明 provider 的等价语义；
4. 固化 trusted root provisioning、owner/ACL、mount 和不可写边界；
5. 配置保守的 per-file/total bytes、file count、timeout 和并发上限；
6. 固化 verifier version、审计事件、幂等、重试和失败补偿；
7. 进行 production security review、跨平台集成测试和独立 migration/API review；
8. 保持 `diagnosticOnly=true`、`notTradingAuthorization=true`，不得将 integrity verification 升级为交易授权。
