package com.guidinglight.nexusquant.research.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BacktestStrategyVersionBindingRequestBody 描述 GateI-2 回测配置绑定策略版本的请求体。
 *
 * Why:
 * strategy version 绑定只需要稳定版本 ID，参数快照和版本快照由后端从 strategy_versions 读取并固化；
 * 前端或调用方不能自行提交快照，避免请求体伪造导致回测输入不可追溯。
 */
@Schema(name = "BacktestStrategyVersionBindingRequestBody", description = "回测配置绑定策略版本请求体")
public record BacktestStrategyVersionBindingRequestBody(
        @Schema(description = "策略版本 ID")
        @NotBlank(message = "strategyVersionId must not be blank")
        @Size(max = 128, message = "strategyVersionId length must be less than or equal to 128")
        String strategyVersionId
) {
}
