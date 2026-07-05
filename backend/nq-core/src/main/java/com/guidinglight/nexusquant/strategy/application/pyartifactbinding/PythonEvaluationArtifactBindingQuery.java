package com.guidinglight.nexusquant.strategy.application.pyartifactbinding;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * PythonEvaluationArtifactBindingQuery 是 GateQ-4 Python offline evaluation artifact 绑定预览查询模型。
 *
 * <p>Why: Java 侧只验证 request body 中的 artifact JSON 与用户显式提供的 expected anchors，
 * 不读取本地路径、不导入文件、不写数据库，也不把 Python artifact 提升为 Java fact source。
 *
 * @param artifact request body 中的 Python offline artifact JSON；为空时 fail-closed
 * @param expectedDatasetId Java 侧预期 datasetId；必须与 artifact datasetId 一致
 * @param expectedStrategyVersionId Java 侧 strategyVersionId 目标，仅用于 scope / traceability 回显
 * @param expectedStrategyVersion 预期 Python strategy_version；必须与 artifact 一致
 * @param expectedEvaluationVersion 预期 evaluation_version；必须与 artifact 一致
 * @param expectedChecksum 预期 artifact checksum；必须与 artifact checksum 一致
 * @param expectedParametersHash 预期 parameters_hash；必须与 artifact 一致
 * @param source 允许值为 PYTHON_OFFLINE；其他来源按 boundary violation fail-closed
 * @param dryRun false 会 fail-closed；null 视为 preview endpoint 固有 dry-run
 */
public record PythonEvaluationArtifactBindingQuery(
        JsonNode artifact,
        String expectedDatasetId,
        String expectedStrategyVersionId,
        String expectedStrategyVersion,
        String expectedEvaluationVersion,
        String expectedChecksum,
        String expectedParametersHash,
        String source,
        Boolean dryRun
) {
}
