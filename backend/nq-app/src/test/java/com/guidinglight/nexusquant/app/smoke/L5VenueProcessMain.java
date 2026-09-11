package com.guidinglight.nexusquant.app.smoke;

/** 沿用 B0 多订单事实与 HTTP 协议，只收紧本批 test-only 资源预算。 */
public final class L5VenueProcessMain {
    public static void main(String[] args) throws Exception {
        B0SyntheticVenueMain.boundedWorkload = true;
        B0SyntheticVenueMain.main(args);
    }
}
