# Java 工程规范与 Shadow 治理

本目录保存 NexusQuant 的 Java/Spring 平台规范、Alibaba Huangshan 适配、领域/架构 overlay、豁免清单和 Shadow baseline。它们是工程治理资产，不覆盖 `docs/current/STATUS.md`、冻结合同、Schema、Golden Case、状态机或业务规则。

## 文件职责

- `common-java-engineering-standard.md`：NQ/DH 字节一致的通用规则；领域差异不得进入此文件。
- `platform-profile.json`：从 POM、effective dependency、CI 与本机工具链探测的实际平台事实。
- `java-platform-profile.md` / `spring-platform-profile.md`：只描述当前 profile 支持的 Java/Spring 能力与边界。
- `architecture-overlay.md`：从当前模块、文档与 ArchUnit 得出的 NQ 实际架构。
- `nq-java-domain-overlay.md`：NQ 数值、时间、交易模式、订单、外部副作用和审计规则。
- `alibaba-huangshan-rule-mapping.yaml`：当前黄山版 319 条规则的逐条 disposition、compatibility 与 enforcement 映射。
- `alibaba-songshan-rule-mapping.yaml`：标记为 `SUPERSEDED`（已被替代）的上一版 mapping，仅保留 lineage。
- `songshan-to-huangshan-diff.yaml`：308→319 条规则的完整 lineage 与 semantic diff。
- `java-rule-exceptions.yaml`：显式、限域、可过期的规则豁免；默认无豁免。
- `java-shadow-scope.json`：由当前架构文档导出的扫描层级、source root 与基础设施时间边界；检查器不得在代码中猜模块。
- `source-provenance.json` / `source-history.json`：当前黄山版来源和 Songshan→Huangshan lineage。
- `shadow-baseline.json`：当前历史违规的确定性基线，由 Shadow checker 生成。

## 执行方式

```powershell
pwsh -NoProfile -File scripts/java-standard/verify-java-engineering-standard.ps1
pwsh -NoProfile -File scripts/java-standard/invoke-java-shadow-scan.ps1 -OutputPath artifacts/java-shadow/shadow-report.json
```

规则违规只产生 `VIOLATION_FOUND`，当前不阻断主构建；配置损坏、检查器失败或报告无法生成会阻断。任何硬门禁升级都必须是后续独立授权任务。

Shadow 使用 `platform-profile.json` 与 `java-shadow-scope.json`，区分 `EXISTING_BASELINE_FINDING`、`RULESET_EXPANSION_FINDING` 和 `NEW_CODE_FINDING`。领域合同、事务一致性、状态机、Repository 语义和其他无法可靠静态判断的规则保持 `REVIEW` / `ARCH_TEST`，不得用全仓字符串硬匹配替代架构判断。

## 来源与改写声明

当前外部参考基线为阿里巴巴官方 `alibaba/p3c` 仓库的《Java 开发手册·黄山版》v1.7.1；嵩山版 v1.7.0 已标记 `SUPERSEDED`。本目录只保留规则索引、归一化 hash、项目摘要和适配决定，不复制手册全文。项目改写优先服从当前 Authority、冻结合同、实际平台与 NQ 安全边界。
