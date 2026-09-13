package com.guidinglight.nexusquant.app.smoke;

/** 正式L6使用自身run预算，不继承L5上限；执行队列及协议仍复用原实现。 */
public final class L6FormalVenueProcessMain {
    public static void main(String[] args) throws Exception {
        var capacity = QualificationCapacity.formal();
        capacity.validate();
        B0SyntheticVenueMain.boundedWorkload = true;
        B0SyntheticVenueMain.boundedOrderCapacity = capacity.venueLogicalOrderCapacity();
        B0SyntheticVenueMain.main(args);
    }
}
