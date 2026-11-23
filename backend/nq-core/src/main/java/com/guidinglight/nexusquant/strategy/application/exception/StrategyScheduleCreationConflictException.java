package com.guidinglight.nexusquant.strategy.application.exception;

/** 编号冲突或原请求仍在事务中时保留专用原因，客户端不能换号盲目重建计划。 */
public class StrategyScheduleCreationConflictException extends RuntimeException {
    private final String code;

    public StrategyScheduleCreationConflictException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
