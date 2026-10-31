package com.guidinglight.nexusquant.account.application.exception;

/** 仅公开固定原因代码，私有HTTP、JDBC和解密异常不得回显原始原因链。 */
public final class AccountFeeSyncException extends RuntimeException {
    public AccountFeeSyncException(String reason) {
        super(reason);
    }
}
