package com.guidinglight.nexusquant.account.api.web;

import com.guidinglight.nexusquant.account.api.dto.CredentialVersionResponse;
import com.guidinglight.nexusquant.account.application.service.CredentialOperationService;
import com.guidinglight.nexusquant.account.domain.CredentialOperation;
import com.guidinglight.nexusquant.account.domain.CredentialOperationReceipt;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 新操作合同保留旧接口；脱敏历史与结果读取均从当前主体的账户范围查询。 */
@Validated
@RestController
@RequestMapping("/api/exchange-accounts/{accountId}/credentials")
public class CredentialOperationController {
    private final CredentialOperationService service;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;

    public CredentialOperationController(
            CredentialOperationService service,
            GatewayAuthFacade auth,
            CurrentUserProfileService users) {
        this.service = service;
        this.auth = auth;
        this.users = users;
    }

    /** 明确操作身份及目标版本后委托生命周期服务；请求可能带敏感材料，禁止日志或错误响应回显。 */
    @PostMapping("/operations/{operationId}")
    public Response execute(
            @PathVariable @Positive Long accountId,
            @PathVariable UUID operationId,
            @Valid @RequestBody Request r) {
        var actor = actor();
        return Response.from(
                service.execute(
                        actor.id(),
                        accountId,
                        operationId,
                        new CredentialOperation(
                                r.type(),
                                r.credentialId(),
                                r.credentialType(),
                                r.apiKey(),
                                r.secretKey(),
                                r.passphrase(),
                                r.privateKeyPem(),
                                r.reason()),
                        actor.name()));
    }

    /** 读取原操作脱敏成功回执；当前未记录不证明轮换、校验或状态变更已经失败。 */
    @GetMapping("/operations/{operationId}")
    public Response result(@PathVariable @Positive Long accountId, @PathVariable UUID operationId) {
        return service.find(actor().id(), accountId, operationId)
                .map(Response::from)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "credential operation result is not recorded"));
    }

    /** 保留全部生命周期版本并按游标分页；只输出白名单摘要，下一页身份取本页最后一条。 */
    @GetMapping
    public History history(
            @PathVariable @Positive Long accountId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "20") int limit) {
        var rows = service.history(actor().id(), accountId, before, limit);
        boolean more = rows.size() > limit;
        var page = rows.stream().limit(limit).map(CredentialVersionResponse::from).toList();
        return new History(page, more, more ? page.getLast().credentialId() : null);
    }

    private Actor actor() {
        var user =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        var id =
                users.findByUsername(user.username())
                        .map(profile -> profile.userId())
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        return new Actor(id, user.username());
    }

    private record Actor(Long id, String name) {}

    /** 材料只用于当次受控命令；字符串表示必须脱敏，验证失败不得序列化被拒绝的原值。 */
    public record Request(
            @NotNull CredentialOperation.Type type,
            @Positive Long credentialId,
            @NotBlank @Size(max = 32) String credentialType,
            @Size(max = 512) String apiKey,
            @Size(max = 4096) String secretKey,
            @Size(max = 512) String passphrase,
            @Size(max = 16384) String privateKeyPem,
            @Size(max = 1024) String reason) {
        @Override
        public String toString() {
            return "CredentialOperationRequest[type=" + type + ", material=REDACTED]";
        }
    }

    public record Response(
            UUID operationId,
            CredentialOperation.Type type,
            String status,
            Instant completedAt,
            CredentialVersionResponse result) {
        static Response from(CredentialOperationReceipt r) {
            return new Response(
                    r.operationId(),
                    r.type(),
                    "SUCCEEDED",
                    r.completedAt(),
                    CredentialVersionResponse.from(r.result()));
        }
    }

    public record History(
            List<CredentialVersionResponse> items, boolean hasMore, Long nextBefore) {}
}
