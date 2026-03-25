package com.guidinglight.nexusquant.app.web;

/**
 * GateFBacktestRunStartRequest 是发起回测运行的 HTTP 请求体。
 */
public record GateFBacktestRunStartRequest(String backtestConfigId) {
}
