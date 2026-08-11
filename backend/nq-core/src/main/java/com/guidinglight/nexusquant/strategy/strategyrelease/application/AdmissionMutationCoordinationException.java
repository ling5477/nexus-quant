package com.guidinglight.nexusquant.strategy.strategyrelease.application;

/** fail-closed admission mutation coordination failure. */
public class AdmissionMutationCoordinationException extends RuntimeException {

    public AdmissionMutationCoordinationException(String message) {
        super(message);
    }
}
