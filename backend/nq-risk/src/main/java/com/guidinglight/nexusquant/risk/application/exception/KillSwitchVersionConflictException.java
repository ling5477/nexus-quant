package com.guidinglight.nexusquant.risk.application.exception;

/**
 * Kill switch optimistic-lock 冲突。
 */
public class KillSwitchVersionConflictException extends RuntimeException {

    public KillSwitchVersionConflictException(String message) {
        super(message);
    }
}
