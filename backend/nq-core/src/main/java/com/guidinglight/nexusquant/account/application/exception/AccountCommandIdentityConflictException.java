package com.guidinglight.nexusquant.account.application.exception;

/**
 * 已成功身份不能改绑其他操作或输入，防止恢复操作变成新的账户命令。
 */
public class AccountCommandIdentityConflictException extends IllegalStateException {
    public AccountCommandIdentityConflictException() {
        super("account command identity was already used for different input");
    }
}
