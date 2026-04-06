package com.guidinglight.nexusquant.account.api.web;

import com.guidinglight.nexusquant.account.api.dto.CreateExchangeAccountRequestBody;
import com.guidinglight.nexusquant.account.api.dto.ExchangeAccountResponse;
import com.guidinglight.nexusquant.account.api.dto.UpdateExchangeAccountRequestBody;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountNotFoundException;
import com.guidinglight.nexusquant.account.application.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountUpdateCommand;
import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
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

import java.util.List;
import java.util.Objects;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ExchangeAccountController 提供账户上下文与账户管理写侧最小接口。
 */
@Validated
@RestController
@RequestMapping("/api/exchange-accounts")
@Tag(name = "Exchange Account API", description = "账户上下文与账户管理页接口。")
public class ExchangeAccountController {

    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService currentUserProfileService;
    private final ExchangeAccountQueryService exchangeAccountQueryService;
    private final ExchangeAccountCommandService exchangeAccountCommandService;

    public ExchangeAccountController(
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService currentUserProfileService,
            ExchangeAccountQueryService exchangeAccountQueryService,
            ExchangeAccountCommandService exchangeAccountCommandService
    ) {
        this.gatewayAuthFacade = Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
        this.currentUserProfileService = Objects.requireNonNull(
                currentUserProfileService,
                "currentUserProfileService must not be null"
        );
        this.exchangeAccountQueryService = Objects.requireNonNull(
                exchangeAccountQueryService,
                "exchangeAccountQueryService must not be null"
        );
        this.exchangeAccountCommandService = Objects.requireNonNull(
                exchangeAccountCommandService,
                "exchangeAccountCommandService must not be null"
        );
    }

    @GetMapping
    @Operation(
            summary = "列出当前用户账户上下文",
            description = "返回当前登录用户可见的 exchange accounts 摘要。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<ExchangeAccountResponse> list() {
        return exchangeAccountQueryService.listByOwnerUserId(resolveCurrentUserId())
                .stream()
                .map(ExchangeAccountResponse::from)
                .toList();
    }

    @GetMapping("/{accountId}")
    @Operation(
            summary = "读取账户详情",
            description = "返回当前登录用户可操作的账户详情。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "账户不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountResponse detail(@PathVariable @Positive(message = "accountId must be positive") Long accountId) {
        return ExchangeAccountResponse.from(
                exchangeAccountQueryService.findByIdForOwner(resolveCurrentUserId(), accountId)
                        .orElseThrow(() -> new ExchangeAccountNotFoundException(accountId))
        );
    }

    @PostMapping
    @Operation(
            summary = "创建账户",
            description = "创建当前登录用户的最小交易账户。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "账户冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountResponse create(@Valid @RequestBody CreateExchangeAccountRequestBody requestBody) {
        return ExchangeAccountResponse.from(exchangeAccountCommandService.create(
                resolveCurrentUserId(),
                new ExchangeAccountCreateCommand(
                        requestBody.exchangeCode(),
                        requestBody.tradeEnv(),
                        requestBody.accountAlias(),
                        requestBody.externalAccountRef()
                )
        ));
    }

    @PatchMapping("/{accountId}")
    @Operation(
            summary = "更新账户基础信息",
            description = "更新当前登录用户账户的 alias 与 external ref。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "404", description = "账户不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "账户冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ExchangeAccountResponse update(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @Valid @RequestBody UpdateExchangeAccountRequestBody requestBody
    ) {
        return ExchangeAccountResponse.from(exchangeAccountCommandService.updateProfile(
                resolveCurrentUserId(),
                accountId,
                new ExchangeAccountUpdateCommand(requestBody.accountAlias(), requestBody.externalAccountRef())
        ));
    }

    @PostMapping("/{accountId}/enable")
    @Operation(summary = "启用账户", description = "把当前登录用户账户切回 ACTIVE。", security = @SecurityRequirement(name = "bearerAuth"))
    public ExchangeAccountResponse enable(@PathVariable @Positive(message = "accountId must be positive") Long accountId) {
        return ExchangeAccountResponse.from(exchangeAccountCommandService.enable(resolveCurrentUserId(), accountId));
    }

    @PostMapping("/{accountId}/disable")
    @Operation(summary = "停用账户", description = "停用账户并清除默认标记。", security = @SecurityRequirement(name = "bearerAuth"))
    public ExchangeAccountResponse disable(@PathVariable @Positive(message = "accountId must be positive") Long accountId) {
        return ExchangeAccountResponse.from(exchangeAccountCommandService.disable(resolveCurrentUserId(), accountId));
    }

    @PostMapping("/{accountId}/set-default")
    @Operation(summary = "设为默认账户", description = "原子切换当前交易所/环境作用域下的默认账户。", security = @SecurityRequirement(name = "bearerAuth"))
    public ExchangeAccountResponse setDefault(@PathVariable @Positive(message = "accountId must be positive") Long accountId) {
        return ExchangeAccountResponse.from(exchangeAccountCommandService.setDefault(resolveCurrentUserId(), accountId));
    }

    private Long resolveCurrentUserId() {
        var currentUser = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return currentUserProfileService.findByUsername(currentUser.username())
                .map(profile -> profile.userId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
    }
}
