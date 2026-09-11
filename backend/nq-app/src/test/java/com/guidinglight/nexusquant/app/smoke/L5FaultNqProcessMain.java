package com.guidinglight.nexusquant.app.smoke;

/** 独立的故障资格启动开关；工作负载仍由原L5命令、采样器和业务装配执行。 */
public final class L5FaultNqProcessMain {
    public static void main(String[] args) throws Exception {
        L5QualificationControls.repeatedFault = true;
        L5NqProcessMain.main(args);
    }
}
