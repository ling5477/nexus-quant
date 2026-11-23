package com.guidinglight.nexusquant.marketdata.domain;

/** 仅指向已入库的行情版本；来源与质量参与身份，避免更新后的同一bar沿用旧来源时点。 */
public record StoredBarIdentity(long barId, HistoricalBar bar, String source) {}
