package com.guidinglight.nexusquant.marketdata.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 不可变研究输入的存储边界；所有者来自可信作用域，不接受请求自报身份。 */
public interface ResearchDatasetVersionPort {
    Optional<JsonNode> find(UUID datasetId);
    JsonNode freeze(JsonNode dataset, List<HistoricalBar> source, Instant frozenAt);
    List<HistoricalBar> read(JsonNode dataset);
    /** 导出原规范字节，保留观察时间与回放时间的独立摘要。 */
    default JsonNode export(UUID datasetId) {
        throw new UnsupportedOperationException("RESEARCH_EXPORT_UNAVAILABLE");
    }
}
