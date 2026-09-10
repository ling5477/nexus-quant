package com.guidinglight.nexusquant.app.smoke;

/** 仅在隔离子进程显式启用既有只读 scheduler；不注册历史交易调度方法。 */
public final class B5SchedulerNqProcessMain {
    public static void main(String[] args) throws Exception {
        B5QualificationControls.schedulerEnabled = true;
        B0NqProcessMain.main(args);
    }
}
