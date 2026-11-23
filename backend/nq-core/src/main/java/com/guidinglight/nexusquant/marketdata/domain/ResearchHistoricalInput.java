package com.guidinglight.nexusquant.marketdata.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;

/** 长研究输入只扩展研究消费；持久化引用必须由端口解析，不能交给SIM的V1读取器。 */
public final class ResearchHistoricalInput {
    public static final String VERSION = "HISTORICAL_RESEARCH_INPUT_V2";
    public static final int MAX_BARS = 4320;
    public static final int CHUNK_BARS = 500;
    public static final int MAX_CHUNK_BYTES = 512 * 1024;
    public static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;

    private ResearchHistoricalInput() {}

    public static boolean referenced(JsonNode dataset) {
        return VERSION.equals(dataset.path("frozenInput").path("version").asText());
    }

    /** 仅供冻结仓储生成规范分块；返回值不是可直接保存到配置的大输入合同。 */
    public static ObjectNode freeze(JsonNode dataset, List<HistoricalBar> source,
            Instant frozenAt, ObjectMapper mapper) {
        if (source.size() <= FrozenHistoricalInput.MAX_BARS)
            throw new IllegalStateException("RESEARCH_VERSION_REQUIRES_LONG_INPUT");
        return FrozenHistoricalInput.freezeBounded(dataset, source, frozenAt, mapper, MAX_BARS, VERSION);
    }

    /** 分块先校验摘要与结构后重组；再次核验原观察时点、内容和完整覆盖。 */
    public static List<HistoricalBar> readMaterialized(JsonNode dataset, ObjectMapper mapper) {
        if (dataset.path("barCount").asLong() <= FrozenHistoricalInput.MAX_BARS)
            throw new IllegalStateException("RESEARCH_VERSION_REQUIRES_LONG_INPUT");
        return FrozenHistoricalInput.readBounded(dataset, mapper, MAX_BARS, VERSION);
    }
}
