package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.TradeReplayRecord;

import java.util.List;

public interface TradeReplayRecordRepository {

    void insert(TradeReplayRecord record);

    List<TradeReplayRecord> listByRunId(String paperRunId);
}
