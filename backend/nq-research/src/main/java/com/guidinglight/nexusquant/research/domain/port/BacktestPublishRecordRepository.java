package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;

import java.util.List;
import java.util.Optional;

public interface BacktestPublishRecordRepository {

    void upsert(BacktestPublishRecord backtestPublishRecord);

    Optional<BacktestPublishRecord> findByBacktestRunId(String backtestRunId);

    List<BacktestPublishRecord> listAll();

    Optional<BacktestPublishRecord> findByPublishRecordId(String publishRecordId);
}


