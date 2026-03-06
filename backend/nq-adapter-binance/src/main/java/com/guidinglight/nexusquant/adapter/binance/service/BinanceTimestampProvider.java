package com.guidinglight.nexusquant.adapter.binance.service;

/**
 * BinanceTimestampProvider 提供可注入的毫秒时间戳。
 *
 * Why:
 * Binance signed REST 请求必须带 `timestamp`，单测若依赖系统时钟会导致签名不可复验。
 */
@FunctionalInterface
public interface BinanceTimestampProvider {

    /**
     * @return 当前请求使用的 epoch millis
     */
    long nowMillis();
}
