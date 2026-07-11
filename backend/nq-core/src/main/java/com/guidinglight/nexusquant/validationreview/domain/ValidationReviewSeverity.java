package com.guidinglight.nexusquant.validationreview.domain;

/**
 * 人工复核优先级。
 *
 * <p>严重度只用于本地队列排序，不表示风险已批准或交易可以执行。
 */
public enum ValidationReviewSeverity {
    INFO,
    WARNING,
    HIGH,
    CRITICAL
}
