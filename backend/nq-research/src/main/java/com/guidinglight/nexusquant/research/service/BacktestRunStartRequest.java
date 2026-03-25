package com.guidinglight.nexusquant.research.service;

/**
 * BacktestRunStartRequest 描述启动回测运行时需要的最小输入。
 */
public record BacktestRunStartRequest(String backtestConfigId) {
}
