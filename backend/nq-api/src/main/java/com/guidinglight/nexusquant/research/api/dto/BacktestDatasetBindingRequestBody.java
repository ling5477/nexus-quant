package com.guidinglight.nexusquant.research.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * BacktestDatasetBindingRequestBody 是 GateH-3 回测配置绑定 dataset 的请求体。
 */
public record BacktestDatasetBindingRequestBody(
        @NotBlank(message = "datasetId must not be blank")
        String datasetId
) {
}
