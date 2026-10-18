package com.guidinglight.nexusquant.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.marketdata.api.dto.CreateMarketdataDatasetRequest;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 当前数据库身份拥有原结果；旧令牌角色不能恢复已撤销的写权限。 */
class MarketdataDatasetOperationControllerTest {
    private final MarketdataDatasetOperationService operations =
            mock(MarketdataDatasetOperationService.class);
    private final GatewayAuthFacade auth = mock(GatewayAuthFacade.class);
    private final CurrentUserProfileService profiles = mock(CurrentUserProfileService.class);
    private final MarketdataDatasetOperationController controller =
            new MarketdataDatasetOperationController(operations, auth, profiles);
    private final UUID id = UUID.randomUUID(), operationId = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-10-01T00:00:00Z");

    private CreateMarketdataDatasetRequest input() {
        return new CreateMarketdataDatasetRequest(
                "dataset", "BINANCE", "SPOT", "BTC-USDT", "1m", start, start.plusSeconds(180));
    }

    @Test
    void currentOperatorCreatesUsingAuthenticatedActorAndStableDatasetIdentity() {
        actor("OPERATOR", true, false);
        controller.create(id, input());
        verify(operations)
                .create(
                        id,
                        new CreateMarketdataDatasetCommand(
                                "dataset",
                                "BINANCE",
                                "SPOT",
                                "BTC-USDT",
                                "1m",
                                start,
                                start.plusSeconds(180),
                                "owner"),
                        42,
                        "owner");
        controller.refresh(id, operationId);
        verify(operations).refresh(operationId, id, 42, "owner");
    }

    @Test
    void currentViewerReadsOwnReceiptButCannotUseOldAdminClaimToWrite() {
        actor("VIEWER", true, false);
        when(operations.find(operationId, 42)).thenReturn(Optional.empty());
        assertEquals(404, controller.find(operationId).getStatusCode().value());
        verify(operations).find(operationId, 42);
        assertThrows(AccessDeniedException.class, () -> controller.create(id, input()));
        assertThrows(AccessDeniedException.class, () -> controller.refresh(id, operationId));
    }

    @Test
    void missingDisabledAndPasswordChangeRequiredActorsCannotReadOrWrite() {
        when(auth.currentUser()).thenReturn(Optional.empty());
        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                () -> controller.find(operationId));
        actor("ADMIN", false, false);
        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                () -> controller.create(id, input()));
        actor("ADMIN", true, true);
        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                () -> controller.refresh(id, operationId));
        verifyNoInteractions(operations);
    }

    private void actor(String role, boolean enabled, boolean mustChange) {
        when(auth.currentUser())
                .thenReturn(
                        Optional.of(
                                new TokenClaims(
                                        "owner",
                                        "owner",
                                        List.of("ADMIN"),
                                        start,
                                        start.plusSeconds(3600),
                                        "test",
                                        "old-token")));
        when(profiles.findByUsername("owner"))
                .thenReturn(
                        Optional.of(
                                new AuthUserProfile(
                                        42L,
                                        "owner",
                                        "unused-test-hash",
                                        List.of(role),
                                        enabled,
                                        mustChange,
                                        start,
                                        1)));
    }
}
