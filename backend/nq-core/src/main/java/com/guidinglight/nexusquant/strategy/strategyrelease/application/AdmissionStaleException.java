package com.guidinglight.nexusquant.strategy.strategyrelease.application;

/** Admission facts generation 已变化；调用方只能重新预览并由用户再次确认。 */
public class AdmissionStaleException extends RuntimeException {

    public AdmissionStaleException() {
        super("ADMISSION_STALE: admission facts changed");
    }
}
