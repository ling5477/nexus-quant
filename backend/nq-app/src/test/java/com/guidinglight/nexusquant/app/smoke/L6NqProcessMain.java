package com.guidinglight.nexusquant.app.smoke;

/** 仅在隔离入口启用正常计时器；不开放故障命令或改变生产周期。 */
public final class L6NqProcessMain {
    public static void main(String[] args) throws Exception {
        L6QualificationControls.enabled = true;
        System.setProperty("spring.datasource.driver-class-name", L6AccountingDriver.class.getName());
        B0NqProcessMain.main(args);
    }
}
