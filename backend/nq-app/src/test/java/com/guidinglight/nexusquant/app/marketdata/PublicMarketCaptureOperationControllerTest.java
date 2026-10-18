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
import com.guidinglight.nexusquant.security.token.model.TokenClaims;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 原结果归属使用当前数据库用户ID；读取不因降为只读角色而丢失，提交仍校验当前角色。 */
class PublicMarketCaptureOperationControllerTest {
    private final PublicMarketCaptureOperationService operations =
            mock(PublicMarketCaptureOperationService.class);
    private final GatewayAuthFacade auth = mock(GatewayAuthFacade.class);
    private final CurrentUserProfileService profiles = mock(CurrentUserProfileService.class);
    private final PublicMarketCaptureOperationController controller =
            new PublicMarketCaptureOperationController(operations, auth, profiles);
    private final UUID id = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void authenticatedOperatorUsesDatabaseIdentityAndOriginalInput() {
        actor("OPERATOR", true);
        controller.submit(
                id,
                new PublicMarketCaptureOperationController.CaptureRequest(
                        start, start.plusSeconds(3600)));
        verify(operations).submit(id, start, start.plusSeconds(3600), 42, "owner");
    }

    @Test
    void readonlyUserCanQueryOwnReceiptButCannotSubmit() {
        actor("VIEWER", true);
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
                                        "old-role-token")));
        when(operations.find(id, 42)).thenReturn(Optional.empty());
        assertEquals(404, controller.find(id).getStatusCode().value());
        verify(operations).find(id, 42);
        assertThrows(
                AccessDeniedException.class,
                () ->
                        controller.submit(
                                id,
                                new PublicMarketCaptureOperationController.CaptureRequest(
                                        start, start.plusSeconds(3600))));
    }

    @Test
    void missingOrDisabledActorCannotAccessOperation() {
        when(auth.currentUser()).thenReturn(Optional.empty());
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.find(id));
        actor("ADMIN", false);
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.find(id));
        verifyNoInteractions(operations);
    }

    private void actor(String role, boolean enabled) {
        when(auth.currentUser())
                .thenReturn(
                        Optional.of(
                                new TokenClaims(
                                        "owner",
                                        "owner",
                                        List.of(role),
                                        start,
                                        start.plusSeconds(3600),
                                        "test",
                                        "test-id")));
        when(profiles.findByUsername("owner"))
                .thenReturn(
                        Optional.of(
                                new AuthUserProfile(
                                        42L, "owner", "test-only-hash", List.of(role), enabled)));
    }
}
