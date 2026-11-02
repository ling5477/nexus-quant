package com.guidinglight.nexusquant.ledger.service.port;

import com.guidinglight.nexusquant.ledger.contracts.model.SimAssetFundingPlan;

import java.time.Instant;
import java.util.Optional;

/** 初始化身份的持久化边界；receipt 不承载余额，正式金额只由 Ledger 读取。 */
public interface SimAssetFundingRepository {
    void assertBoundSimAccount(long accountId, String paperRunId, boolean newFunding);

    void assertEmptyAccount(long accountId);

    Optional<Receipt> findReceipt(long accountId, String paperRunId);

    void insertReceipt(Receipt receipt);

    void assertCompletePosting(Receipt receipt);

    record Receipt(
            long accountId,
            String paperRunId,
            SimAssetFundingPlan plan,
            Instant fundedAt,
            String traceId,
            String cashEntryId,
            String cashContraEntryId,
            String assetEntryId,
            String assetContraEntryId) {}
}
