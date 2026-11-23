package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.PriceEventSourceRecord;
import com.guidinglight.nexusquant.marketdata.domain.StoredBarIdentity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 来源记录的有界读写端口；写入前锁定当前bar，既有行情/冻结快照由原owner保持。 */
public interface PriceEventSourceRepository {
    Optional<StoredBarIdentity> findBar(long barId, boolean lockForSourceWrite);

    Optional<PriceEventSourceRecord> findById(UUID sourceId);

    Optional<PriceEventSourceRecord> findForBar(long barId, String identitySha256);

    /** 冲突不覆盖已有事实；调用方按精确身份及规范化记录判断重放或冲突。 */
    boolean insertIfAbsent(PriceEventSourceRecord source);

    /** 最多500个版本一次读取，不按bar逐根查询，也不扫描每根bar的无界历史版本。 */
    Map<Long, PriceEventSourceRecord> readCurrentVersions(List<BarVersion> versions);

    record BarVersion(long barId, String identitySha256) {}
}
