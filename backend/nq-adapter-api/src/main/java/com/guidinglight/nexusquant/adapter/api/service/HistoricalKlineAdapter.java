package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineBar;
import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest;

import java.util.List;

/**
 * HistoricalKlineAdapter 定义交易所历史 K 线读取能力。
 * <p>
 * Why:
 * GateH-2 只允许 adapter 处理交易所协议和字段映射；任务状态、DB 幂等与质量统计由 core/infra 处理。
 */
public interface HistoricalKlineAdapter {

    String exchangeCode();

    List<HistoricalKlineBar> fetchHistoricalKlines(HistoricalKlineRequest request);
}
