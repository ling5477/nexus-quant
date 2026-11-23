package com.guidinglight.nexusquant.research.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * BacktestDatasetBindingRequestBody 是历史行情接入回测配置绑定 dataset的请求体。
 */
public record BacktestDatasetBindingRequestBody(
        @NotBlank(message = "datasetId must not be blank")
        String datasetId
) {
}
