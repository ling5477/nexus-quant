package com.guidinglight.nexusquant.research.application.paper;

public record PaperTradingRunCreateCommand(
        String publishId,
        String tradeEnv,
        String exchangeCode,
        String marketType,
        String symbol,
        String intervalCode,
        String configSnapshotJson,
        String createdBy
) {}
