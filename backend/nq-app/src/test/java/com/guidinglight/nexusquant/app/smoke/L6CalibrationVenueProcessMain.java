package com.guidinglight.nexusquant.app.smoke;

/** 只有显式calibration入口绑定其容量；既有L5/B0入口保持原默认值。 */
public final class L6CalibrationVenueProcessMain {
    public static void main(String[] args) throws Exception {
        var budget = L6CalibrationBudget.frozen();
        budget.validate();
        B0SyntheticVenueMain.boundedWorkload = true;
        B0SyntheticVenueMain.boundedOrderCapacity = budget.venueLogicalOrderCapacity();
        B0SyntheticVenueMain.main(args);
    }
}
