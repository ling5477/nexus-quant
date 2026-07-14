package com.guidinglight.nexusquant.marketdata.domain.instrument.port;

import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;

import java.util.List;

/**
 * InstrumentCatalogReadPort 提供 instrument catalog 的有界、本地、只读查询能力。
 *
 * <p>该端口不暴露同步或持久化方法，供 diagnostic preview 等禁止副作用的调用方使用。
 * 实现必须只读取本地持久化 facts，不得隐式触发 provider/network sync。</p>
 */
public interface InstrumentCatalogReadPort {

    /**
     * 按交易所和 1..3 个 exchange symbol 精确读取本地 facts。
     *
     * @param exchangeCode    交易所编码
     * @param exchangeSymbols 1..3 个 exchange symbol
     * @return 匹配的 catalog items；无匹配时返回空集合
     * @throws IllegalArgumentException 参数为空、无边界或超出允许数量时抛出
     */
    List<InstrumentCatalogItem> findByExchangeAndSymbols(String exchangeCode, List<String> exchangeSymbols);
}
