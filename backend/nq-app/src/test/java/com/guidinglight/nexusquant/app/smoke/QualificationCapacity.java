package com.guidinglight.nexusquant.app.smoke;

/** 显式阶段授权与单run预算分离；容量不授权生产额外订单。 */
record QualificationCapacity(Mode mode, int runOrderBudget, int maximumPossibleDistinctOrdersForRun,
                             int venueLogicalOrderCapacity) {
    enum Mode { L5, L6_CALIBRATION, L6_FORMAL }
    int globalStageSafetyCap() { return mode == Mode.L5 ? 300 : 3000; }
    static Mode resolve(String name) {
        try { return Mode.valueOf(name); }
        catch (RuntimeException e) { throw new IllegalStateException("QUALIFICATION_BUDGET_CAPACITY_CONTRACT_INVALID", e); }
    }
    void validate() {
        if (mode != null && mode != Mode.L5 && maximumPossibleDistinctOrdersForRun > 3000)
            throw new IllegalStateException("L6_RUN_ORDER_BUDGET_EXCEEDS_FROZEN_SAFETY_CAP");
        if (mode == null || runOrderBudget <= 0 || runOrderBudget > maximumPossibleDistinctOrdersForRun
                || maximumPossibleDistinctOrdersForRun > venueLogicalOrderCapacity
                || venueLogicalOrderCapacity > globalStageSafetyCap())
            throw new IllegalStateException("QUALIFICATION_BUDGET_CAPACITY_CONTRACT_INVALID");
    }
    @FunctionalInterface interface RuntimeStart { void run() throws Exception; }
    void start(RuntimeStart runtime) throws Exception { validate(); runtime.run(); }
    void reserve(long existing, int maximumNewIdentities) {
        if (existing < 0 || maximumNewIdentities < 0 || existing + maximumNewIdentities > runOrderBudget)
            throw new IllegalStateException("QUALIFICATION_PRODUCER_BUDGET_EXHAUSTED");
    }
    static QualificationCapacity l5() { return new QualificationCapacity(Mode.L5, 300, 300, 300); }
    static QualificationCapacity formal() { return new QualificationCapacity(Mode.L6_FORMAL, 240, 240, 240); }
}
