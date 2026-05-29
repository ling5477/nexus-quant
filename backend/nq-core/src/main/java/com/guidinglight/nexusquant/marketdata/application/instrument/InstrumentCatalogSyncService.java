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
     * <p>
     * Why:
     * 该接口可能读取外部交易所公开 metadata；freeze 等受限环境允许禁用同步并返回受控业务错误，
     * 调用方不得把外部 451/网络失败直接暴露成 internal server error。
     *
     * @param exchangeCode 目标交易所；为空时同步当前支持的全部交易所
     * @param traceId      链路追踪 ID
     * @return 本次同步统计
     * @throws IllegalStateException 当前运行环境禁用外部同步，或外部交易所同步暂不可用
     */
    InstrumentCatalogSyncResult sync(String exchangeCode, String traceId);
}
