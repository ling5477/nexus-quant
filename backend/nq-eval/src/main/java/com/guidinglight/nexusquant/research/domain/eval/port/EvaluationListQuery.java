package com.guidinglight.nexusquant.research.domain.eval.port;

import com.guidinglight.nexusquant.research.domain.eval.EvaluationStatus;

import java.util.Locale;

/** 父配置精确过滤，运行ID保留原页面子串语义；控制字符在归一化前拒绝。 */
public record EvaluationListQuery(
        String researchConfigId,
        String backtestConfigId,
        String backtestRunId,
        String evaluationStatus,
        int limit,
        int offset) {
    public EvaluationListQuery {
        researchConfigId = normalize(researchConfigId, 128);
        backtestConfigId = normalize(backtestConfigId, 128);
        backtestRunId = normalize(backtestRunId, 128);
        evaluationStatus = normalize(evaluationStatus, 32);
        if (evaluationStatus != null)
            evaluationStatus =
                    EvaluationStatus.valueOf(evaluationStatus.toUpperCase(Locale.ROOT)).name();
        if (limit < 1 || limit > 100 || offset < 0)
            throw new IllegalArgumentException("invalid evaluation page window");
    }

    private static String normalize(String value, int maximum) {
        if (value == null) return null;
        if (value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid evaluation filter");
        if (value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum)
            throw new IllegalArgumentException("invalid evaluation filter");
        return normalized;
    }
}
