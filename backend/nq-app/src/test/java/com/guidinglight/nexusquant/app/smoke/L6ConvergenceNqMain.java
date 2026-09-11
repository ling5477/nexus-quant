package com.guidinglight.nexusquant.app.smoke;

/** 显式启用短时收敛证明控制器；不启用 L6 workload。 */
public final class L6ConvergenceNqMain {
    public static void main(String[] args) throws Exception {
        L6ConvergenceControls.enabled = true;
        B0NqProcessMain.main(args);
    }
}
