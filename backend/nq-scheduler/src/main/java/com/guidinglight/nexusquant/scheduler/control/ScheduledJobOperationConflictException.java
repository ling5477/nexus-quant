package com.guidinglight.nexusquant.scheduler.control;

/** 对外保留稳定冲突原因；同编号输入冲突不能被客户端当作安全拒绝而丢弃原身份。 */
public class ScheduledJobOperationConflictException extends RuntimeException {
    private final String code;

    public ScheduledJobOperationConflictException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
