package com.guidinglight.nexusquant.strategy.application.command;

/**
 * StrategyVersionCreateRequest 描述创建策略版本的应用层命令。
 *
 * @param strategyCode 策略编码，必须对应已有 `strategy_definitions.strategy_code`
 * @param versionName 版本展示名称，不参与策略执行算法
 * @param status 版本状态，可空；为空时默认 DRAFT
 * @param paramSnapshotJson 参数快照 JSON，空值会归一化为 `{}`
 * @param configSnapshotJson 配置快照 JSON，空值会使用当前 strategy definition 配置
 * @param sourceSnapshotJson 来源快照 JSON，空值会归一化为 `{}`
 * @param createdBy 创建人，用于审计；为空时使用 system
 */
public record StrategyVersionCreateRequest(
        String strategyCode,
        String versionName,
        String status,
        String paramSnapshotJson,
        String configSnapshotJson,
        String sourceSnapshotJson,
        String createdBy
) {
}
