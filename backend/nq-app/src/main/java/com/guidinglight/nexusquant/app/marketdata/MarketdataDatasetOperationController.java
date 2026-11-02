package com.guidinglight.nexusquant.app.marketdata;

import com.guidinglight.nexusquant.app.marketdata.MarketdataDatasetOperationService.OperationView;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.marketdata.api.dto.CreateMarketdataDatasetRequest;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 请求回执按当前用户ID隔离；读取原结果不依赖当前是否仍具有写角色。 */
@RestController
@RequestMapping("/api/marketdata")
public final class MarketdataDatasetOperationController {
    private final MarketdataDatasetOperationService operations;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;

    public MarketdataDatasetOperationController(
            MarketdataDatasetOperationService operations,
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles) {
        this.operations = operations;
        this.auth = auth;
        this.profiles = profiles;
    }

    @PostMapping("/datasets/{datasetId}/creation")
    public OperationView create(
            @PathVariable UUID datasetId,
            @Valid @RequestBody CreateMarketdataDatasetRequest request) {
        AuthUserProfile actor = operator();
        var command =
                new CreateMarketdataDatasetCommand(
                        request.datasetName(),
                        request.exchangeCode(),
                        request.marketType(),
                        request.symbol(),
                        request.interval(),
                        request.startTime(),
                        request.endTime(),
                        actor.username());
        return operations.create(datasetId, command, actor.userId(), actor.username());
    }

    @PostMapping("/datasets/{datasetId}/quality-refreshes/{operationId}")
    public OperationView refresh(@PathVariable UUID datasetId, @PathVariable UUID operationId) {
        AuthUserProfile actor = operator();
        return operations.refresh(operationId, datasetId, actor.userId(), actor.username());
    }

    @GetMapping("/dataset-operations/{operationId}")
    public ResponseEntity<OperationView> find(@PathVariable UUID operationId) {
        return ResponseEntity.of(operations.find(operationId, profile().userId()));
    }

    private AuthUserProfile operator() {
        AuthUserProfile result = profile();
        if (!result.roles().contains("ADMIN") && !result.roles().contains("OPERATOR"))
            throw new AccessDeniedException("operator role required");
        return result;
    }

    private AuthUserProfile profile() {
        AuthUserProfile result =
                auth.currentUser()
                        .flatMap(claims -> profiles.findByUsername(claims.username()))
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        if (result.userId() == null
                || result.userId() <= 0
                || !result.enabled()
                || result.mustChangePassword()) {
            throw new AuthenticationCredentialsNotFoundException("authentication required");
        }
        return result;
    }
}
