package com.guidinglight.nexusquant.strategy.strategyrelease.application;

/** 服务端已验证 release identity 尚未完成安全 first-binding，禁止签发 ELIGIBLE guard。 */
public class AdmissionGuardUninitializedException extends RuntimeException {

    public AdmissionGuardUninitializedException() {
        super("ADMISSION_GUARD_UNINITIALIZED: verified release identity is not bound");
    }
}
