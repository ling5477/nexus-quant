package com.guidinglight.nexusquant.runtime.api.web;

import com.guidinglight.nexusquant.runtime.api.OperationalReadinessService;
import com.guidinglight.nexusquant.runtime.api.dto.OperationalReadinessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OperationalReadinessController exposes the GateM-6B disabled capability summary.
 *
 * <p>Why: {@code GET /api/runtime/operational-readiness} gives the UI and operators a safe,
 * read-only backend source for LIVE / AI / DH / real-provider / startup boundary status. It delegates
 * to {@link OperationalReadinessService}, which has no adapter, exchange, permission probe, DB, or
 * file dependency and cannot change trading behavior.
 */
@RestController
@RequestMapping("/api/runtime")
@Tag(name = "Runtime Operational Readiness API", description = "只读运行边界与禁用能力摘要")
public class OperationalReadinessController {

    private final OperationalReadinessService operationalReadinessService;

    public OperationalReadinessController(OperationalReadinessService operationalReadinessService) {
        this.operationalReadinessService = Objects.requireNonNull(
                operationalReadinessService,
                "operationalReadinessService must not be null"
        );
    }

    /**
     * Returns the fail-closed operational readiness summary.
     *
     * @return safe DTO summary; no runtime values, no external calls, no mutation
     */
    @GetMapping("/operational-readiness")
    @Operation(
            summary = "查询运行边界与禁用能力摘要",
            description = "只读返回 LIVE / AI / DH / real provider / startup / profile / config / log 边界摘要。"
                    + "当前 baseline 全部 fail-closed；不读取运行敏感值、不触达 adapter、不发起外部交易所调用。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public OperationalReadinessResponse operationalReadiness() {
        return operationalReadinessService.currentSummary();
    }
}
