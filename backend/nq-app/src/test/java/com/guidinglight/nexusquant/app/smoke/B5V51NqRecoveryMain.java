package com.guidinglight.nexusquant.app.smoke;

/** 仅为自有 B0 JVM 启用生产恢复启动/tick，继续使用原安全夹具和合成 venue。 */
public final class B5V51NqRecoveryMain {
    public static void main(String[] args) throws Exception {
        B5QualificationControls.strategyRecoveryEnabled = true;
        B0NqProcessMain.main(args);
    }
}
