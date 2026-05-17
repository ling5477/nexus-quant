package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionRun;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MarketdataIngestionJobRepository 定义 GateH-2 ingestion jobs/runs 的持久化端口。
 * <p>
 * Why:
 * application service 需要同时写任务状态与运行统计，但不能直接依赖 JDBC；所有 SQL 细节由 infra 实现。
 */
public interface MarketdataIngestionJobRepository {

    MarketdataIngestionJob createJob(MarketdataIngestionJob job);

    List<MarketdataIngestionJob> listJobs();

    Optional<MarketdataIngestionJob> findJob(UUID jobId);

    Optional<Instant> findLatestSuccessfulActualEnd(UUID jobId);

    void updateJobStatus(UUID jobId, MarketdataIngestionStatus status, Instant updatedAt);

    MarketdataIngestionRun createRun(MarketdataIngestionRun run);

    MarketdataIngestionRun finishRun(MarketdataIngestionRun run);

    List<MarketdataIngestionRun> listRuns(UUID jobId);
}
