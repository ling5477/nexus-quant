package com.guidinglight.nexusquant.app.smoke;

/** 仅增加 canonical ENGAGE 控制，不开放解除 Kill 或任何故障入口。 */
public final class L5KillNqProcessMain {
    public static void main(String[] args) throws Exception {
        L5QualificationControls.enabled = true;
        L5QualificationControls.killTransition = true;
        B0NqProcessMain.main(args);
    }
}
