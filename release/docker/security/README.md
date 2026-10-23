# Java 21 证书阻断策略

`blocked.certs` 是供应商发布的 Linux/macOS 原始文件，不是手工拼接的指纹列表。

- 下载来源：https://docs.azul.com/assets/2026-10/emudhra-block-unix.certs
- 供应商说明：https://docs.azul.com/core/emudhra-workaround
- SHA-256：`2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10`
- 对应 OpenJDK 21 修复：https://github.com/openjdk/jdk21u/commit/85974eed594aa5a9e13a6a20713156ed8d9cd0ed （JDK-8395142，包含证书编码变体处理）。由该固定源码生成的文件与供应商下载字节相同。
- Oracle 公开关联：JDK-8395555 / CVE-2026-91403，https://www.oracle.com/java/technologies/javase/21-0-13-0-2-relnotes.html 。

NQ 保留 Eclipse Temurin Java 21，并在实际 Temurin 构建 JDK 和最终 JRE 验证此上游数据缓解。这里不宣称 Azul 为 Temurin 提供产品支持，也不把 Temurin 版本号或 Oracle 修复版本当作 NQ 已升级的证明。资格与局限记录在 `docs/current/evidence/NQ_V101_JDK_SECURITY_REMEDIATION.md`。

安装器先检查完整文件摘要和 Java 主版本，并要求现有阻断项为新文件的子集；若未来 JDK 增加其他条目，安装失败，必须重新核对供应商策略，不能删除已有保护。文件覆盖默认 `java.home/lib/security/blocked.certs`，不修改 `cacerts`、信任锚、撤销检查或 TLS 算法约束。

证书验证覆盖16张公开受影响中间证书、正常签发及交叉签名、48个签名/AlgorithmIdentifier编码变体、14条完整叶证书链，以及正常可信链。隔离补入的真实根只用于证明负向输入能到达信任锚，不进入产品信任库。更换 JDK、覆盖信任实现或改变该文件后必须重新运行对应验证。
