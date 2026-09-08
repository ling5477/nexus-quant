# F2 archive integrity remediation — attempt 02

Task classification: `HIGH_RISK / TARGETED_SECURITY_REMEDIATION / NQ-only`。

Final decision: `IMPLEMENTED / PENDING_TARGETED_INDEPENDENT_REVIEW / F2_ARCHIVE_INTEGRITY_REMEDIATED / P0_0 / LOCAL_P1_0`。

这是实现者的本地修复证据，不是独立审查接受或发布证明。用户提供的前次 Independent Review 结论仍是独立历史事实：F2 因 P1 archive metadata bypass 未获接受，另有 encoding P3；F1=CLOSED、ENGINEERING_DISCIPLINE_COMPLETENESS=PASS、F7/F8/F9=ACCEPTED。本轮没有改写上一轮证据或 current authority。

## 候选与范围

- 日期：2026-09-08；仓库 `E:\Project\nexus-quant-gateaudit`。
- 分支：`audit/post-gatey-agent-baseline`。
- 起止 HEAD：`f3cc63b95d305fb227f6a91adf5f9ccb9350289e`；未 stage、commit、push。
- 修改既有候选：`scripts/ci/Test-DeliveryArtifactSafety.ps1`、`scripts/ci/tests/Test-DeliveryArtifactSafety.ps1`。
- 新增直接实现/测试：`scripts/ci/DeliveryArchiveIntegrity.ps1`、`scripts/ci/tests/Test-ArchiveIntegrityRepro.ps1`。
- 新增本文件及 [本轮原始记录目录](f2-archive-integrity-attempt02)。构建兼容性副本放在忽略的 `artifacts/f2-archive-integrity-attempt02-build`，未执行或部署 JAR。
- 起始已有改动清单与 SHA-256 见 [before-files.json](f2-archive-integrity-attempt02/before-files.json)。收尾逐文件比较，只有上述两个 F2 既有文件发生本轮变化，其余既有改动原样保留。
- 最终四文件原始工作区 SHA-256 见 [candidate-files.json](f2-archive-integrity-attempt02/candidate-files.json)。这些是本地文件字节身份，不冒充将来的 Git blob、commit 或 exact-head CI 身份。

## 原 exploit 与根因

修复前，先按任务描述构造一个包含 `application.properties` 的 stored JAR，内容为纯合成 secret-shaped sentinel。保持 local header 和 payload 不变，仅将 central directory 的 compressed size 与 uncompressed size 都置零。测试逐字节断言 sentinel 仍物理存在，不打印它。

| 复现 | PS5.1.26100.9168 修复前 | PS7.6.5 修复前 | 最终 PS5.1 / PS7 |
| --- | --- | --- | --- |
| 正常 metadata + secret | REJECT | REJECT | REJECT / REJECT |
| central sizes=0 + 原 payload | PASS（漏洞） | PASS（漏洞） | REJECT / REJECT |
| valid outer + forged inner JAR | PASS（漏洞） | PASS（漏洞） | REJECT / REJECT |

原始输出：[before PS5.1](f2-archive-integrity-attempt02/repro-before-ps51.txt)、[before PS7](f2-archive-integrity-attempt02/repro-before-ps7.txt)、[final PS5.1](f2-archive-integrity-attempt02/repro-final-ps51.txt)、[final PS7](f2-archive-integrity-attempt02/repro-final-ps7.txt)。复现命令为 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci/tests/Test-ArchiveIntegrityRepro.ps1 -ExpectVulnerable` / `pwsh -NoProfile -File ... -ExpectVulnerable`；修复后去掉 `-ExpectVulnerable`。两种预期模式均退出 0。

最初仅置零 central uncompressed size 的 stored 变体原本就被拒绝；相应失败测试输出保留在 `repro-size-only-ps51.txt` / `repro-size-only-ps7.txt`，不能把它误报为成功复现。最终测试另行覆盖单改 central uncompressed size，以及同时伪造 local/central uncompressed size。

Root cause / Metadata trusted：旧路径相信 `ZipArchiveEntry.Length` 及标准库按 central compressed size 给出的读取视图，没有核对 local header、物理 payload 边界和实际 CRC。只把 declared size 当预算或空内容事实不足以证明内容已扫描。

## 新完整性规则

New integrity rule：先证明支持范围内的结构一致，再从受约束的物理压缩区间实际读取/展开、核对真实展开长度与 CRC，最后扫描文本或递归处理内层；任何不可证明状态均 `REJECT / MALFORMED_OR_UNVERIFIABLE_ARCHIVE`。

- Local/central validation：校验 EOCD、单卷身份、实际解析 entry 数、central directory 的位置和长度、local header、原始名称字节、flags、compression method、CRC 和长度。按 local offset 排序后要求物理区域连续、无重叠、无缺口，最后恰好到 central directory；拒绝隐藏 local entry、越界、截断、重复规范化名称、歧义 EOCD。
- Data-descriptor handling：支持 12-byte unsigned / 16-byte signed ZIP32 descriptor。允许 local CRC/size 为零或最终值；descriptor 必须与 central CRC/size 一致并准确占满到下一 local header / directory 的区间，随后再核对实际展开内容。不是机械要求 descriptor 模式的所有 local 字段都等于 central。
- Compression：只支持 stored / deflate。实际展开复用 `System.IO.Compression.DeflateStream`，没有自行实现解压器。受限输入流逐字节供给 deflate，避免预读吞掉尾随垃圾；若输入耗尽后解压器仍请求数据，则拒绝未结束的流。解压完成后压缩区间必须恰好消费完，实际长度与 CRC 必须匹配。
- ZIP64 边界：不支持完整 ZIP64、多卷、加密、替代名称扩展、未知压缩方法、带前缀的自解压布局或未声明 UTF-8 的非 ASCII 名称；直接 fail-closed。实际 Tomcat JAR 使用的 **冗余本地 ZIP64 两长度字段** 仅在本身为 ZIP32 且两值与已校验长度完全一致时接受，专门测试不一致拒绝。
- Helper 必要性：CRC 循环、无预读的压缩区间 stream 及格式字段校验放在一个约 190 行的同目录 PowerShell `Add-Type` helper，避免 PowerShell 逐字节解释执行；未引入外部 archive framework。初版 `.cs` 输入被 stage-assets 拒绝后，改为当前支持的 `.ps1` 形式；未改 checker 或 exception。

## 实际字节计数与编码

Actual-byte accounting：每个 entry 都实际读完并核对 CRC/长度，包括 binary、目录和声明为空的 entry。declared expanded size 从不决定“不读”或“跳过”。文本、嵌套制品使用实际展开的 byte array 长度累计预算；声明为零但实际输出超预算的 fixture 必须在读取时以对应 limit 拒绝。顶层也执行 bounded read；文件长度仅作提前拒绝优化。

Resource bounds 保持：entries=50,000；单 entry=64 MiB；expanded text total=128 MiB；nested archive total=512 MiB；top-level archive=256 MiB；depth=3。测试利用只能收紧的参数覆盖六种预算和精确边界，并覆盖跨 entry / archive / nested 的累计；未声称实际构造了 512 MiB 压力制品。深度 3 正常、深度 4 拒绝使用默认值验证。

Nested archive behavior：ZIP/JAR 使用同一校验器递归；内层任一 malformed/unverifiable、预算超限、秘密或编码失败均使整个制品拒绝。没有 inner empty / silently skip 分支。

Encoding policy：应用配置和一般文本默认严格 UTF-8；UTF-8/16LE/16BE/32LE/32BE BOM 显式选择严格 decoder，BOM 后非法字节拒绝 `INVALID_TEXT_ENCODING`。不使用 StreamReader 自动切换或 replacement decode。普通 binary 不做 UTF 解码，但仍验证实际长度、CRC 和单 entry 预算。

为兼容正常 Java 制品，实际嵌套在 `BOOT-INF/lib/*.jar` 内、具有 Java 包路径的 `messages[_ll[_CC]].properties` 资源束采用显式 Latin-1 策略，仍扫描全部内容与计入预算。这是按实际递归上下文选择的策略，不是根据失败进行编码回退或跳过；应用 `BOOT-INF/classes` 配置、伪造含 `!` 的路径均不获该策略。BOM 始终覆盖 Latin-1 并严格解码。已验证 Latin-1 clean PASS、ASCII secret REJECT、非法 UTF-8/UTF-16 BOM REJECT。

## 最终验证

除另注外，所有命令退出 0。日志位于本轮记录目录；`powershell` 调用均带 `-NoProfile -ExecutionPolicy Bypass`，`pwsh` 均带 `-NoProfile`。

| 验证入口 | 环境与结果 | 原始输出 |
| --- | --- | --- |
| `-File scripts/ci/tests/Test-DeliveryArtifactSafety.ps1` | PS5.1 / PS7：每侧 19 positive PASS、62 negative REJECT | `artifact-final-ps51.txt` / `artifact-final-ps7.txt` |
| `-File scripts/ci/tests/Test-DeliveryEvidence.ps1` | PS5.1 / PS7：clean delivery、normalized、deterministic、readback、provenance negative、tamper、secret 全部符合预期；包含上述 81 用例 | `delivery-final-ps51.txt` / `delivery-final-ps7.txt` |
| `-File scripts/ci/tests/Test-ArchiveIntegrityRepro.ps1` | PS5.1 / PS7：正常 secret、forged JAR、nested forged JAR 均 REJECT | `repro-final-ps51.txt` / `repro-final-ps7.txt` |
| `-File scripts/ci/Test-DeliveryArtifactSafety.ps1 -EvidenceRoot artifacts/f2-archive-integrity-attempt02-build` | PS5.1 / PS7：两个真实已有构建制品均 PASS | `build-final-ps51.txt` / `build-final-ps7.txt` |
| `pwsh -NoProfile -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1` | canonical delivery mutations **135/135 REJECTED**；SUPPLY_CHAIN_TEST PASS | `canonical-ps7.txt` |
| `D:\Tool\Python\python.exe scripts/docs/check-stage-assets.py` | scanned=1802、reviewed_exceptions=173、errors=0 | `stage-assets-final.txt` |
| `D:\Tool\Python\python.exe -m unittest discover -s scripts/docs/tests -p test_stage_assets.py` | 49 tests、OK、2 skipped：主机不允许创建 symlink | `stage-assets-tests.txt` |

Canonical mutation 与 stage-assets unit suite 运行后没有修改其脚本或输入合同；后续 F2 helper、编码和 fixture 调整不影响这两组覆盖，因此复用本轮有效证据。最终 F2、delivery evidence、P1 reproduction、实际制品和 stage-assets gate 均在最终四文件上重跑。

Positive / false-positive tests：clean JSON/properties/JAR/nested、empty entry/archive、精确文本预算、有效 Unicode、signed/unsigned descriptor、合法冗余 local ZIP64、Spring Boot 布局 fixture、既有 clean delivery fixture。真实已有制品未重新构建：

- 当前 `backend/nq-app/target/nq-app-0.1.0-SNAPSHOT.jar`：175099 bytes，112 entries，SHA-256 `8B9384F001E65A587D8F875BE78FC620879D3333333D7D8595B06F239B47423A`。
- 既有 `artifacts/phase5b-canonical-release-local/app/nq-app.jar`：36710019 bytes，318 outer entries（194 个 BOOT-INF 路径），SHA-256 `0DC12C72C34BACA7B2CEF27C4FF45B2F5B16F378DE03CAA9325014E78EB9E726`。

Negative tests：secret JSON/properties/JAR/nested；central-only size corruption；local/central size、name、flags、method、CRC 不一致；双方一致但实际 CRC 错；offset、gap、truncation、deflate 未结束和尾随垃圾；descriptor CRC/size corruption；unsupported ZIP64；各预算；非法 UTF-8/16/32；Latin-1 policy 边界。

中间失败也保留：PS5.1 新测试脚本缺 BOM 的解析错误、fixture `$name` 被测试参数作用域遮蔽、首次 `.cs` stage-assets 拒绝、实际 Boot 的 Latin-1 资源束及冗余 ZIP64 兼容性失败。它们都已由本轮修改和最终测试解决，不把中间失败日志误写为最终通过。前次失败 Independent Review 的原文文件保持不变。

未运行 Full Maven、Playwright、C2 PostgreSQL、B0、生产操作或发布；未触碰 .github、business code、migration、AGENTS、Skills、engineering references、Java validator、F1、F7/F8/F9 docs、C2 或 current authority。

## 回滚与结论

回滚只能恢复本轮起点，不能 `git restore` 到 HEAD 丢掉之前未提交的 F2 实现。两份原始字节备份为 `scanner-before.ps1.txt` 和 `tests-before.ps1.txt`；必要时分别恢复到对应脚本，再移除本轮新增 helper 与 reproduction test。本轮证据作为历史保留。此处未执行回滚。

F1：维持用户提供的 CLOSED；Engineering discipline completeness：维持 PASS；F7/F8/F9：维持 ACCEPTED。均未重新评审或改写。

F2：本地归档完整性修复完成，待 targeted independent review。Encoding P3：本地严格解码修复完成，待同次独立确认。P0=0、LOCAL_P1=0；本轮自查未发现未解决的局部 P2/P3，不代表仓库全局清零，也不改写先前范围外 residual。

Next action（唯一）：`NQ-GATEAUDIT-PHASE6-PRE-B0-F2-ARCHIVE-INTEGRITY-INDEPENDENT-REVIEW`。应由未参与本候选实现的 reviewer 核对最终四文件及证据身份，并重点独立检查 structural / descriptor / deflate EOF、实际字节预算、嵌套与编码策略。本实现对话未进行或冒充该独立审查。
