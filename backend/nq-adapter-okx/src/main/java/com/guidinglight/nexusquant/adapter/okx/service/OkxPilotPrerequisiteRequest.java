package com.guidinglight.nexusquant.adapter.okx.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 由服务端 immutable pilot scope 构造的 exact prerequisite observation 请求。
 */
public record OkxPilotPrerequisiteRequest(List<String> instruments) {

    public OkxPilotPrerequisiteRequest {
        Objects.requireNonNull(instruments, "instruments must not be null");
        instruments = instruments.stream()
                .map(value -> Objects.requireNonNull(value, "instrument must not be null")
                        .trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        if (instruments.size() < 1 || instruments.size() > 2
                || instruments.stream().anyMatch(value -> !value.matches("[A-Z0-9]{2,20}-USDT"))) {
            throw new IllegalArgumentException("one or two canonical OKX Spot instruments are required");
        }
    }
}
