package com.guidinglight.nexusquant.research.port;

import com.guidinglight.nexusquant.research.model.BacktestPublishRecord;

import java.util.Optional;

public interface BacktestPublishRecordRepository {

    void upsert(BacktestPublishRecord backtestPublishRecord);

    Optional<BacktestPublishRecord> findByBacktestRunId(String backtestRunId);
}
