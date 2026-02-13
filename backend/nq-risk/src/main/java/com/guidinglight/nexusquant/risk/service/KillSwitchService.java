package com.guidinglight.nexusquant.risk.service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KillSwitchService 提供全局熔断开关占位。
 *
 * Why:
 * docs/RECOVERY_RUNBOOK.md 指出恢复流程需要“冻结写入”，Kill Switch 是最小控制点。
 */
public class KillSwitchService {

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    /**
     * 开启熔断，阻止新下单。
     */
    public void enable() {
        enabled.set(true);
    }

    /**
     * 关闭熔断，恢复正常提交。
     */
    public void disable() {
        enabled.set(false);
    }

    /**
     * @return true 表示当前处于熔断状态
     */
    public boolean isEnabled() {
        return enabled.get();
    }
}
