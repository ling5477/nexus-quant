package com.guidinglight.nexusquant.marketdata.application.instrument;

/**
 * InstrumentCatalogSyncService 定义 PRE-2 的 instrument/symbol 同步入口。
 * <p>
 * Why:
 * API 需要触发 symbol sync，但真正的 adapter 读取逻辑不应写进 controller；
 * 同时 sync 能力又不应该绑死某个具体 adapter 实现，因此这里先定义稳定的应用层契约。
 */
public interface InstrumentCatalogSyncService {

    /**
     * 执行 instrument catalog 同步。
     *
     * @param exchangeCode 目标交易所；为空时同步当前支持的全部交易所
     * @param traceId      链路追踪 ID
     * @return 本次同步统计
     */
    InstrumentCatalogSyncResult sync(String exchangeCode, String traceId);
}
