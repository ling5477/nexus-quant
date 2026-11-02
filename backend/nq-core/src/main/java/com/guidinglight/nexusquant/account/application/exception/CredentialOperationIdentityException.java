package com.guidinglight.nexusquant.account.application.exception;

/** 已存在操作身份的冲突不能当作原操作失败，客户端必须保留身份并查询。 */
public class CredentialOperationIdentityException extends IllegalStateException {
    public CredentialOperationIdentityException() {
        super("credential operation identity cannot be replayed; query the original result");
    }
}
