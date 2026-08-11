package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ShadowRunMaterializationActor;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseShadowRunMaterializationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Strategy Release-to-Shadow CREATE-only HTTP command。
 *
 * <p>请求体为空；唯一客户端业务事实为 publishRecordId，command identity 复用仓库标准
 * {@code Idempotency-Key} header。actor/roles/trace 均来自服务端 context，不能由客户端覆盖。
 */
@Validated
@RestController
@RequestMapping("/api/strategy-releases")
@Tag(name = "Strategy Release API", description = "Strategy Release 与 Shadow 准入及受控创建接口。")
public class StrategyReleaseShadowRunMaterializationController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService currentUserProfileService;
    private final StrategyReleaseShadowRunMaterializationService materializationService;

    public StrategyReleaseShadowRunMaterializationController(
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService currentUserProfileService,
            StrategyReleaseShadowRunMaterializationService materializationService
    ) {
        this.gatewayAuthFacade = Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
        this.currentUserProfileService = Objects.requireNonNull(
                currentUserProfileService,
                "currentUserProfileService must not be null"
        );
        this.materializationService = Objects.requireNonNull(
                materializationService,
                "materializationService must not be null"
        );
    }

    /**
     * 重新执行 server-owned admission 并创建一个保持 CREATED 的 Shadow Run。
     *
     * @param publishRecordId canonical publish anchor
     * @param idempotencyKey operator command identity；相同值重放同一 run，新值允许合法 rerun
     * @return CREATED / RELEASE_BOUND Shadow Run；绝不启动 runner 或 scheduler
     */
    @PostMapping("/{publishRecordId}/shadow-runs")
    @Operation(
            summary = "物化已验证 Strategy Release 为未启动 Shadow Run",
            description = "服务端重新验证 release/artifact/validation/policy，原子且幂等地创建 CREATED Shadow Run；"
                    + "不启动 runner/scheduler，不触发交易、LIVE 或外部网络。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功或幂等重放"),
            @ApiResponse(responseCode = "400", description = "Idempotency-Key 不合法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "未认证", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "写权限不足", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "publish record 不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "幂等 provenance 冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "当前 admission 为 BLOCKED", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StrategyReleaseShadowRunMaterializationResponse materialize(
            @PathVariable String publishRecordId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
    ) {
        String traceId = TraceIdContext.getOrCreate();
        AuthUserProfile profile = currentProfile();
        ShadowRunMaterializationActor actor = new ShadowRunMaterializationActor(
                profile.userId(),
                profile.roles()
        );
        return materializationService.materialize(publishRecordId, idempotencyKey, actor, traceId)
                .map(StrategyReleaseShadowRunMaterializationResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "strategy release publish record not found"
                ));
    }

    private AuthUserProfile currentProfile() {
        var token = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return currentUserProfileService.findByUsername(token.username())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
    }
}
