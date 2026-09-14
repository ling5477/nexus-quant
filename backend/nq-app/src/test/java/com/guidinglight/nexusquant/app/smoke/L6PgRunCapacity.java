package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** 一次准备测量只授予一次启动容量；正式入口必须用本次真实值重新推导并确认同一分配。 */
final class L6PgRunCapacity {
    private final long baseline;
    private final long capacity;
    private final String modelSha;

    private L6PgRunCapacity(L6PgCapacityContract model, long baseline) {
        this.baseline = baseline;
        this.capacity = model.deriveCapacity(baseline);
        this.modelSha = model.identity().path("sha256").asText();
    }

    static L6PgRunCapacity derive(L6PgCapacityContract model, long baseline) {
        return new L6PgRunCapacity(model, baseline);
    }
    long capacity() { return capacity; }
    void requireModel(L6PgCapacityContract model) {
        L6PgCapacityContract.require(modelSha.equals(model.identity().path("sha256").asText())
                && capacity == model.deriveCapacity(baseline));
    }
    ObjectNode evidence(L6PgCapacityContract model) {
        requireModel(model);
        return model.identity().put("modelContractSha", modelSha).put("manifestSha", model.manifestSha())
                .put("baselineLifecycle", "ACTORS_READY_PAPER_READY_VENUE_OPEN_BEFORE_WORKLOAD")
                .put("preparationBaselineBytes", baseline)
                .put("projectedWarmupGrowthBytes", model.growth(0)).put("projectedActiveGrowthBytes", model.growth(1))
                .put("projectedDrainGrowthBytes", model.growth(2)).put("reserveBytes", model.reserve())
                .put("consumableAllocationBurstBytes", model.burst()).put("allocationGranularityBytes", L6PgCapacityContract.MIB)
                .put("requiredRunCapacityBytes", capacity).put("capacityImmutableAfterPgStartup", true);
    }
    ObjectNode confirmEntry(L6PgCapacityContract model, long actualBaseline, long actualCapacity) {
        var proof = evidence(model).put("formalEntryBaselineBytes", actualBaseline)
                .put("entryDerivedCapacityBytes", model.deriveCapacity(actualBaseline)).put("actualPgTmpfsCapacityBytes", actualCapacity);
        // 两次初始化不假定等值：只要真实入口不属于同一个MiB分配区间，就在T=0前拒绝。
        if (actualCapacity != capacity || model.deriveCapacity(actualBaseline) != capacity) {
            throw new IllegalStateException("BLOCKED / L6_FORMAL_ENTRY_CAPACITY_DERIVATION_MISMATCH / " + proof);
        }
        return proof.put("preflightResult", "PASS");
    }
}
