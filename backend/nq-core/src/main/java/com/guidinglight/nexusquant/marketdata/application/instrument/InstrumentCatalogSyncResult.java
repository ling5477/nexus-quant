package com.guidinglight.nexusquant.marketdata.application.instrument;

import java.time.Instant;
import java.util.List;

/**
 * InstrumentCatalogSyncResult 描述一次 instrument/symbol sync 的稳定输出。
 * <p>
 * Why:
 * GateH-PRE 需要把 symbol sync 变成可验证动作，而不是“启动时大概做了点什么”。
 * 这份结果会直接提供给 API、审计文档和后续前端运营入口。
 *
 * @param exchangeCodes 本次参与同步的交易所
 * @param rowsRead      读取到的 instrument 数量
 * @param rowsInserted  新增数量
 * @param rowsUpdated   更新数量
 * @param startedAt     开始时间
 * @param finishedAt    结束时间
 */
public record InstrumentCatalogSyncResult(
        List<String> exchangeCodes,
        int rowsRead,
        int rowsInserted,
        int rowsUpdated,
        Instant startedAt,
        Instant finishedAt
) {
}
