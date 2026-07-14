package com.guidinglight.nexusquant.risk.service;

/**
 * Kill switch 的稳定作用域。
 *
 * <p>当前只允许全局交易安全作用域；新增账户级或 venue 级作用域必须另行评审，避免调用方
 * 通过选择更窄或未知 scope 绕过全局阻断。</p>
 */
public enum KillSwitchScope {
    GLOBAL_TRADING
}
