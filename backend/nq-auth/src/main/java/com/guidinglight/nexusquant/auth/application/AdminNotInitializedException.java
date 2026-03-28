package com.guidinglight.nexusquant.auth.application;

/**
 * AdminNotInitializedException 表示当前数据库尚未初始化管理员。
 */
public class AdminNotInitializedException extends RuntimeException {

    public AdminNotInitializedException() {
        super("admin is not initialized");
    }
}
