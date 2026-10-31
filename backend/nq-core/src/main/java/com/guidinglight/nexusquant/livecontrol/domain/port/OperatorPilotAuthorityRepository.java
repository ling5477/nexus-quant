package com.guidinglight.nexusquant.livecontrol.domain.port;

import com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Operator pilot authority 的持久化与 lifecycle port。
 */
public interface OperatorPilotAuthorityRepository {

    OperatorPilotAuthority createOrGet(OperatorPilotAuthority authority);

    Optional<OperatorPilotAuthority> find(UUID authorityId);

    Optional<OperatorPilotAuthority> lock(UUID authorityId);

    OperatorPilotAuthority close(UUID authorityId, OperatorPilotAuthority.Status status, Instant occurredAt);
}
