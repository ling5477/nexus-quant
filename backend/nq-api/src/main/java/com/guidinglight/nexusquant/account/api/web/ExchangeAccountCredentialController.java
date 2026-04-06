package com.guidinglight.nexusquant.account.api.web;

import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountActiveCredentialResponse;
import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountCredentialSummaryResponse;
import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountCredentialUpsertRequestBody;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.auth.application.GatewayAuthFacade;
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
            description = "无 active 凭证时返回 activeCredential = null。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "查询成功")
    public ExchangeAccountActiveCredentialResponse active(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId
    ) {
        var activeCredential = exchangeAccountCredentialCommandService.findActiveSummaryOrNull(resolveCurrentUserId(), accountId);
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
            description = "执行当前 active 凭证的结构性校验并回写 verification 状态。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "校验动作已完成"),
            @ApiResponse(responseCode = "404", description = "账户或 active 凭证不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountCredentialSummaryResponse verify(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId
    ) {
        return ExchangeAccountCredentialSummaryResponse.from(
                exchangeAccountCredentialVerificationService.verifyActive(resolveCurrentUserId(), accountId)
        );
    }

    private Long resolveCurrentUserId() {
        var currentUser = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return currentUserProfileService.findByUsername(currentUser.username())
                .map(profile -> profile.userId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
    }
}
