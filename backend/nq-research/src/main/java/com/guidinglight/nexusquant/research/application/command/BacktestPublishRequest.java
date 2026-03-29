package com.guidinglight.nexusquant.research.application.command;

/**
 * BacktestPublishRequest 描述显式发布请求。
 */
public record BacktestPublishRequest(String backtestRunId, String displayName) {
}

