package com.guidinglight.nexusquant.strategy.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** 发送前固定原编号与完整输入；actor、owner及完成状态不能由客户端提供。 */
public record StrategyScheduleCreationRequestBody(
        @NotNull UUID creationId, @Valid @NotNull StrategyScheduleCreateRequestBody request) {}
