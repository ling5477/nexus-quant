package com.guidinglight.nexusquant.strategy.application.pyartifactpreview;

/**
 * PythonEvaluationArtifactMetricSummaryStatus 描述 offline metric summary 的诊断覆盖状态。
 *
 * <p>PRESENT 只表示离线指标摘要字段存在，不表示真实收益；FAKE_FIXTURE_ONLY 必须被展示为测试 fixture，
 * 不能写成真实策略表现。
 */
public enum PythonEvaluationArtifactMetricSummaryStatus {
    PRESENT,
    INCOMPLETE,
    FAKE_FIXTURE_ONLY,
    MISSING,
    UNKNOWN
}
