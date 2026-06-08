package com.guidinglight.nexusquant.account.api.web;

import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountActiveCredentialResponse;
import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountCredentialEnableRequestBody;
import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountCredentialLifecycleRequestBody;
import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountCredentialRotateRequestBody;
import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountCredentialSummaryResponse;
import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountCredentialUpsertRequestBody;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialRotateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ExchangeAccountCredentialController 提供账户凭证最小写侧与结构性校验入口。
 */
@Validated
@RestController
@RequestMapping("/api/exchange-accounts/{accountId}/credentials")
@Tag(name = "Exchange Account Credential API", description = "账户凭证写侧与结构性校验接口。")
public class ExchangeAccountCredentialController {

    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService currentUserProfileService;
    private final ExchangeAccountCredentialCommandService exchangeAccountCredentialCommandService;
    private final ExchangeAccountCredentialVerificationService exchangeAccountCredentialVerificationService;
    private final int credentialKeyVersion;

    public ExchangeAccountCredentialController(
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService currentUserProfileService,
            ExchangeAccountCredentialCommandService exchangeAccountCredentialCommandService,
            ExchangeAccountCredentialVerificationService exchangeAccountCredentialVerificationService,
            @Value("${nq.account.credentials.key-version:1}") int credentialKeyVersion
    ) {
        this.gatewayAuthFacade = Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
        this.currentUserProfileService = Objects.requireNonNull(
                currentUserProfileService,
                "currentUserProfileService must not be null"
        );
        this.exchangeAccountCredentialCommandService = Objects.requireNonNull(
                exchangeAccountCredentialCommandService,
                "exchangeAccountCredentialCommandService must not be null"
        );
        this.exchangeAccountCredentialVerificationService = Objects.requireNonNull(
                exchangeAccountCredentialVerificationService,
                "exchangeAccountCredentialVerificationService must not be null"
        );
        this.credentialKeyVersion = credentialKeyVersion;
    }

    @GetMapping("/active")
    @Operation(
            summary = "读取当前 active 凭证摘要",
            description = "无 active 凭证时返回 activeCredential = null；多 ACTIVE credential type 且未指定 credentialType 时返回状态冲突。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "多 active credential type 冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountActiveCredentialResponse active(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @RequestParam(required = false) String credentialType
    ) {
        var activeCredential = exchangeAccountCredentialCommandService.findActiveSummaryOrNull(
                resolveCurrentUserId(),
                accountId,
                credentialType
        );
        return new ExchangeAccountActiveCredentialResponse(
                accountId,
                activeCredential == null ? null : ExchangeAccountCredentialSummaryResponse.from(activeCredential)
        );
    }

    @PostMapping
    @Operation(
            summary = "新增或轮换凭证",
            description = "始终以新增版本 + active 切换方式处理，不覆盖旧记录。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "写入成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "账户不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "凭证冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountCredentialSummaryResponse upsert(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @Valid @RequestBody ExchangeAccountCredentialUpsertRequestBody requestBody
    ) {
        return ExchangeAccountCredentialSummaryResponse.from(exchangeAccountCredentialCommandService.upsert(
                resolveCurrentUserId(),
                accountId,
                new ExchangeAccountCredentialUpsertCommand(
                        requestBody.credentialType(),
                        requestBody.apiKey(),
                        requestBody.secretKey(),
                        requestBody.passphrase(),
                        requestBody.privateKeyPem()
                ),
                credentialKeyVersion
        ));
    }

    @PostMapping("/verify")
    @Operation(
            summary = "测试连接（结构性校验）",
            description = "执行当前 active 凭证的结构性校验并回写 verification 状态；多 ACTIVE credential type 时必须指定 credentialType。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "校验动作已完成"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "账户或 active 凭证不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "多 active credential type 冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountCredentialSummaryResponse verify(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @RequestParam(required = false) String credentialType
    ) {
        return ExchangeAccountCredentialSummaryResponse.from(
                exchangeAccountCredentialVerificationService.verifyActive(resolveCurrentUserId(), accountId, credentialType)
        );
    }

    @PostMapping("/{credentialId}/revoke")
    @Operation(
            summary = "不可恢复撤销凭证",
            description = "把指定凭证标记为 REVOKED，写入 revoked_at / revoked_by / revoke_reason，并追加 credential_audit_logs。不会删除凭证或返回敏感材料。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "撤销完成或已处于 REVOKED"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "账户或凭证不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountCredentialSummaryResponse revoke(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @PathVariable @Positive(message = "credentialId must be positive") Long credentialId,
            @Valid @RequestBody(required = false) ExchangeAccountCredentialLifecycleRequestBody requestBody
    ) {
        CurrentCredentialActor actor = resolveCurrentCredentialActor();
        return ExchangeAccountCredentialSummaryResponse.from(exchangeAccountCredentialCommandService.revoke(
                actor.userId(),
                accountId,
                credentialId,
                actor.actor(),
                lifecycleReason(requestBody)
        ));
    }

    @PostMapping("/{credentialId}/rotate")
    @Operation(
            summary = "显式轮换指定凭证",
            description = "锁定并校验指定 ACTIVE credential，按旧 credentialType 创建新 ACTIVE credential，把旧 credential 标记为 ROTATED，并写入 ROTATED / CREATED audit log。不会返回或记录敏感材料。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "轮换成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "账户或凭证不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountCredentialSummaryResponse rotate(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @PathVariable @Positive(message = "credentialId must be positive") Long credentialId,
            @Valid @RequestBody ExchangeAccountCredentialRotateRequestBody requestBody
    ) {
        CurrentCredentialActor actor = resolveCurrentCredentialActor();
        return ExchangeAccountCredentialSummaryResponse.from(exchangeAccountCredentialCommandService.rotate(
                actor.userId(),
                accountId,
                credentialId,
                new ExchangeAccountCredentialRotateCommand(
                        requestBody.apiKey(),
                        requestBody.secretKey(),
                        requestBody.passphrase(),
                        requestBody.privateKeyPem(),
                        requestBody.reason()
                ),
                actor.actor(),
                credentialKeyVersion
        ));
    }

    @PostMapping("/{credentialId}/disable")
    @Operation(
            summary = "临时禁用凭证",
            description = "把指定凭证标记为 DISABLED 并追加 credential_audit_logs；后续如需恢复，必须通过独立 enable 命令完成本地结构性校验。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "禁用完成或已处于 DISABLED"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "账户或凭证不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountCredentialSummaryResponse disable(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @PathVariable @Positive(message = "credentialId must be positive") Long credentialId,
            @Valid @RequestBody(required = false) ExchangeAccountCredentialLifecycleRequestBody requestBody
    ) {
        CurrentCredentialActor actor = resolveCurrentCredentialActor();
        return ExchangeAccountCredentialSummaryResponse.from(exchangeAccountCredentialCommandService.disable(
                actor.userId(),
                accountId,
                credentialId,
                actor.actor(),
                lifecycleReason(requestBody)
        ));
    }

    @PostMapping("/{credentialId}/enable")
    @Operation(
            summary = "重新启用临时禁用的凭证",
            description = "只允许 DISABLED 且 inactive 的 credential 经本地结构性校验后恢复为 ACTIVE；credentialType 从 credentialId 派生，不调用真实交易所，不返回或记录敏感材料。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "重新启用成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "账户或凭证不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突或结构性校验失败", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountCredentialSummaryResponse enable(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @PathVariable @Positive(message = "credentialId must be positive") Long credentialId,
            @Valid @RequestBody ExchangeAccountCredentialEnableRequestBody requestBody
    ) {
        CurrentCredentialActor actor = resolveCurrentCredentialActor();
        return ExchangeAccountCredentialSummaryResponse.from(exchangeAccountCredentialCommandService.enable(
                actor.userId(),
                accountId,
                credentialId,
                actor.actor(),
                requestBody.reason()
        ));
    }

    @PostMapping("/{credentialId}/expire")
    @Operation(
            summary = "标记凭证过期",
            description = "把指定凭证标记为 EXPIRED 并追加 credential_audit_logs。不会删除凭证或调用真实交易所。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "过期标记完成或已处于 EXPIRED"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "账户或凭证不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountCredentialSummaryResponse expire(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @PathVariable @Positive(message = "credentialId must be positive") Long credentialId,
            @Valid @RequestBody(required = false) ExchangeAccountCredentialLifecycleRequestBody requestBody
    ) {
        CurrentCredentialActor actor = resolveCurrentCredentialActor();
        return ExchangeAccountCredentialSummaryResponse.from(exchangeAccountCredentialCommandService.expire(
                actor.userId(),
                accountId,
                credentialId,
                actor.actor(),
                lifecycleReason(requestBody)
        ));
    }

    private Long resolveCurrentUserId() {
        return resolveCurrentCredentialActor().userId();
    }

    private CurrentCredentialActor resolveCurrentCredentialActor() {
        var currentUser = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        Long userId = currentUserProfileService.findByUsername(currentUser.username())
                .map(profile -> profile.userId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return new CurrentCredentialActor(userId, currentUser.username() == null || currentUser.username().isBlank()
                ? "system"
                : currentUser.username().trim());
    }

    private String lifecycleReason(ExchangeAccountCredentialLifecycleRequestBody requestBody) {
        return requestBody == null ? null : requestBody.reason();
    }

    private record CurrentCredentialActor(Long userId, String actor) {
    }
}
