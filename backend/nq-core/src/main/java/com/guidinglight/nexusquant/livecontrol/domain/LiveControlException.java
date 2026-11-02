package com.guidinglight.nexusquant.livecontrol.domain;

/** LIVE control-plane fail-closed 业务异常。 */
public final class LiveControlException extends RuntimeException {

    private final String code;

    public LiveControlException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
