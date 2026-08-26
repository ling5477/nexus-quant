package com.guidinglight.nexusquant.livecontrol.infra.okx;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateRealTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CredentialScopedOkxSpotProviderTransportTest {

    @Test
    void readsPublicClockWithoutEnteringCredentialJitCallback() {
        UUID sessionId = UUID.randomUUID();
        LiveControlRepository sessions = mock(LiveControlRepository.class);
        LiveSession session = mock(LiveSession.class);
        OkxPrivateCredentialExecutor credentials = mock(OkxPrivateCredentialExecutor.class);
        OkxPrivateRealTransport realTransport = mock(OkxPrivateRealTransport.class);
        var command = new OkxSpotProviderTransport.ClockCommand(
                new OkxSpotProviderTransport.TransportContext(
                        sessionId, "credential-reference", "trace", "request", Instant.EPOCH),
                new OkxSpotProviderTransport.ResponseReadLimit(4096, 10));
        var expected = new OkxSpotProviderTransport.ClockResponse(
                new OkxSpotProviderTransport.ResponseMetadata(
                        OkxSpotProviderOperation.READ_CLOCK, 64, null, Instant.EPOCH),
                Instant.EPOCH, Instant.EPOCH, java.time.Duration.ZERO, null);
        when(sessions.findSession(sessionId)).thenReturn(Optional.of(session));
        when(realTransport.readClock(command)).thenReturn(expected);
        var transport = new CredentialScopedOkxSpotProviderTransport(sessions, credentials, realTransport);

        assertSame(expected, transport.readClock(command));

        verify(sessions).findSession(sessionId);
        verify(realTransport).readClock(command);
        verifyNoInteractions(credentials);
    }
}
