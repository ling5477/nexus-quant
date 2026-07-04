package com.guidinglight.nexusquant.trading.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.trading.api.dto.TradingPreflightReadinessResponse;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightReadinessQuery;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightReadinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

import java.util.Objects;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TradingPreflightController 暴露只读账户权限与风险前置诊断 API。
 *
 * <p>Why: GateP Batch 4 需要一个安全事实面解释当前真实交易阻断原因。该 controller 只解析当前用户
 * 与 query 参数后委托 read-only service，不调用下单、撤单、permission probe、adapter 或 credential material。
 */
@Validated
@RestController
@RequestMapping("/api/trading/preflight")
@Tag(name = "Trading Preflight API", description = "只读账户权限与风险前置诊断接口。")
public class TradingPreflightController {

    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService currentUserProfileService;
    private final TradingPreflightReadinessService tradingPreflightReadinessService;

    public TradingPreflightController(
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService currentUserProfileService,
            TradingPreflightReadinessService tradingPreflightReadinessService
    ) {
        this.gatewayAuthFacade = Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
        this.currentUserProfileService = Objects.requireNonNull(
                currentUserProfileService,
                "currentUserProfileService must not be null"
        );
        this.tradingPreflightReadinessService = Objects.requireNonNull(
                tradingPreflightReadinessService,
                "tradingPreflightReadinessService must not be null"
        );
    }

    /**
     * 读取当前账户权限与风险前置 baseline。
     *
     * @param exchangeCode 可选交易所代码，默认 OKX
     * @param accountId 可选 exchange account id；为空时按当前用户和 exchangeCode 选择默认/首个账户
     * @param marketType 可选市场类型，默认 SPOT
     * @param symbol 可选交易对，仅作为 data quality / risk diagnostic scope
     * @param strategyId 可选策略 ID，仅回显诊断范围，不触发策略读取或执行
     * @return 只读 readiness / blocker summary，不代表交易授权
     */
    @GetMapping("/readiness")
    @Operation(
            summary = "读取账户权限与风险前置只读基线",
            description = "只读聚合当前账户、credential metadata、permission probe 状态、LIVE/real provider/private trading 阻断与 data quality diagnostic；不触发真实交易所请求或权限探活。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TradingPreflightReadinessResponse readiness(
            @RequestParam(required = false) String exchangeCode,
            @RequestParam(required = false) @Positive(message = "accountId must be positive") Long accountId,
            @RequestParam(required = false) String marketType,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String strategyId
    ) {
        return TradingPreflightReadinessResponse.from(tradingPreflightReadinessService.readiness(
                new TradingPreflightReadinessQuery(
                        resolveCurrentUserId(),
                        exchangeCode,
                        accountId,
                        marketType,
                        symbol,
                        strategyId
                )
        ));
    }

    private Long resolveCurrentUserId() {
        var currentUser = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return currentUserProfileService.findByUsername(currentUser.username())
                .map(AuthUserProfile::userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
    }
}
