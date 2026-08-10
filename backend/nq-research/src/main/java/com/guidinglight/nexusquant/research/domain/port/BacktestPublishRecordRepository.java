package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;

import java.util.List;
import java.util.Optional;

public interface BacktestPublishRecordRepository {

    /**
     * 原子写入 publish fact；允许未绑定 FAILED row 在转为 SUCCEEDED 时首次绑定 locator pair。
     * 已绑定 pair 只能按原值幂等重放，不一致、清空或重绑必须 fail-closed。
     */
    void upsert(BacktestPublishRecord backtestPublishRecord);

    Optional<BacktestPublishRecord> findByBacktestRunId(String backtestRunId);

    List<BacktestPublishRecord> listAll();

    Optional<BacktestPublishRecord> findByPublishRecordId(String publishRecordId);
}

