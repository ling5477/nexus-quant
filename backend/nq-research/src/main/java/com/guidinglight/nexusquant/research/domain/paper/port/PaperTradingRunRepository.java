package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaperTradingRunRepository {

    void insert(PaperTradingRun run);

    Optional<PaperTradingRun> findById(String paperRunId);

    List<PaperTradingRun> list(String publishId, String status);

    /**
     * 只读历史Paper分析窗口；生产实现在SQL中先排除canonical SIM及不可见费用来源，再按更新时间/身份降序限量。
     * owner必须来自认证主体，limit为1—201；额外一条只判断窗口是否仍有后续记录，不参与统计。
     */
    default List<PaperTradingRun> listLegacyForAnalysis(long owner, int limit) {
        throw new UnsupportedOperationException("legacy paper analysis read is not implemented");
    }

    boolean updateStatus(
            String paperRunId,
            PaperTradingRunStatus status,
            Instant startedAt,
            Instant stoppedAt,
            Instant updatedAt);
}
