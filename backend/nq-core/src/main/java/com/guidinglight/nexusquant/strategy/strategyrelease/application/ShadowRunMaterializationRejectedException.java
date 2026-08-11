package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import java.util.List;

/** 当前 server-owned admission 不是 ELIGIBLE，materialization 必须保持零写入。 */
public class ShadowRunMaterializationRejectedException extends RuntimeException {

    private final List<String> reasonCodes;

    public ShadowRunMaterializationRejectedException(List<String> reasonCodes) {
        super("SHADOW_MATERIALIZATION_ADMISSION_BLOCKED: release is not eligible for materialization");
        this.reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public List<String> reasonCodes() {
        return reasonCodes;
    }
}
