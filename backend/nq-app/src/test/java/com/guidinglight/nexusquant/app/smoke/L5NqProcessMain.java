package com.guidinglight.nexusquant.app.smoke;

/** 只启用无故障命令与只读采样，沿用 B0 的隔离启动和真实业务装配。 */
public final class L5NqProcessMain {
    public static void main(String[] args) throws Exception {
        L5QualificationControls.enabled = true;
        B0NqProcessMain.main(args);
    }
}
