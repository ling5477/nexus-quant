package com.guidinglight.nexusquant.strategy.domain;

/** admitted 只来自本次提交的新行；读回既存 run 不授予执行资格。 */
public record StrategyRunAdmission(boolean admitted, StrategyRun run) { }
