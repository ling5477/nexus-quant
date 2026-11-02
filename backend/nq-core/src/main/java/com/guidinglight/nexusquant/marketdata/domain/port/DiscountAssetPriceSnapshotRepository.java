package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;

import java.util.Optional;
import java.util.UUID;

/** 不可变换算来源的持久化端口；不得更新原快照，也不得把价格记录用作余额事实。 */
public interface DiscountAssetPriceSnapshotRepository {
    void insert(DiscountAssetPriceSnapshot snapshot);

    Optional<DiscountAssetPriceSnapshot> findByIdForOwner(long ownerUserId, UUID snapshotId);
}
