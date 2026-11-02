package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.port.DiscountAssetPriceSnapshotRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/** 原文本按字节持久化；身份列和文本同时核验，避免可重算SHA掩盖所有者或数据集漂移。 */
@Repository
public class JdbcDiscountAssetPriceSnapshotRepository
        implements DiscountAssetPriceSnapshotRepository {
    private final JdbcTemplate jdbc;

    public JdbcDiscountAssetPriceSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(5);
    }

    @Override
    public void insert(DiscountAssetPriceSnapshot value) {
        DiscountAssetPriceSnapshot.restore(value.sourceJson(), value.sourceSha256());
        jdbc.update(
                """
                INSERT INTO discount_asset_price_snapshots(snapshot_id,dataset_id,owner_user_id,exchange_code,asset_currency,
                    quote_currency,coverage_from,coverage_to,frozen_at,point_count,source_sha256,source_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                value.snapshotId(),
                value.datasetId(),
                value.ownerUserId(),
                value.exchangeCode(),
                value.assetCurrency(),
                value.quoteCurrency(),
                Timestamp.from(value.from()),
                Timestamp.from(value.to()),
                Timestamp.from(value.frozenAt()),
                value.points().size(),
                value.sourceSha256(),
                value.sourceJson());
    }

    @Override
    public Optional<DiscountAssetPriceSnapshot> findByIdForOwner(long owner, UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM discount_asset_price_snapshots WHERE owner_user_id=? AND snapshot_id=? LIMIT 1",
                        (rs, row) -> {
                            DiscountAssetPriceSnapshot value =
                                    DiscountAssetPriceSnapshot.restore(
                                            rs.getString("source_json"),
                                            rs.getString("source_sha256"));
                            if (!value.snapshotId().equals(rs.getObject("snapshot_id", UUID.class))
                                    || !value.datasetId()
                                            .equals(rs.getObject("dataset_id", UUID.class))
                                    || value.ownerUserId() != rs.getLong("owner_user_id")
                                    || !value.exchangeCode().equals(rs.getString("exchange_code"))
                                    || !value.assetCurrency().equals(rs.getString("asset_currency"))
                                    || !value.quoteCurrency().equals(rs.getString("quote_currency"))
                                    || !value.from()
                                            .equals(rs.getTimestamp("coverage_from").toInstant())
                                    || !value.to()
                                            .equals(rs.getTimestamp("coverage_to").toInstant())
                                    || !value.frozenAt()
                                            .equals(rs.getTimestamp("frozen_at").toInstant())
                                    || value.points().size() != rs.getInt("point_count")) {
                                throw new IllegalArgumentException(
                                        "FEE_DISCOUNT_PRICE_SOURCE_INVALID");
                            }
                            return value;
                        },
                        owner,
                        id)
                .stream()
                .findFirst();
    }
}
