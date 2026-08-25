package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority;
import com.guidinglight.nexusquant.livecontrol.domain.port.OperatorPilotAuthorityRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 显式人工 pilot authority 的短事务入口；不创建 strategy/risk/order facts。
 */
@Service
public class OperatorPilotAuthorityService {

    private static final String OPERATOR_ROLE = "OPERATOR";

    private final OperatorPilotAuthorityRepository repository;
    private final LiveControlAuthorizationPort authorization;

    public OperatorPilotAuthorityService(
            OperatorPilotAuthorityRepository repository,
            LiveControlAuthorizationPort authorization
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.authorization = Objects.requireNonNull(authorization, "authorization must not be null");
    }

    @Transactional
    public OperatorPilotAuthority materialize(
            AuthenticatedLiveControlActor actor,
            OperatorPilotAuthority authority
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(authority, "authority must not be null");
        if (authority.ownerUserId() != actor.userId()
                || authority.createdBy() != actor.userId()
                || !authority.hasCanonicalDigest()
                || !authorization.lockAndCheckRole(actor.userId(), OPERATOR_ROLE)) {
            throw rejected("OPERATOR_PILOT_AUTHORITY_MATERIALIZATION_REJECTED");
        }
        return repository.createOrGet(authority);
    }

    @Transactional(readOnly = true)
    public OperatorPilotAuthority requireActive(UUID authorityId, Instant decisionAt) {
        OperatorPilotAuthority authority = repository.find(
                        Objects.requireNonNull(authorityId, "authorityId must not be null"))
                .orElseThrow(() -> rejected("OPERATOR_PILOT_AUTHORITY_NOT_FOUND"));
        if (!authority.activeAt(Objects.requireNonNull(decisionAt, "decisionAt must not be null"))
                || !authority.hasCanonicalDigest()) {
            throw rejected("OPERATOR_PILOT_AUTHORITY_NOT_ACTIVE");
        }
        return authority;
    }

    @Transactional
    public OperatorPilotAuthority close(UUID authorityId, OperatorPilotAuthority.Status terminal, Instant occurredAt) {
        if (terminal != OperatorPilotAuthority.Status.CLOSED
                && terminal != OperatorPilotAuthority.Status.EXPIRED) {
            throw new IllegalArgumentException("operator pilot terminal status is required");
        }
        return repository.close(authorityId, terminal, Objects.requireNonNull(occurredAt));
    }

    private static LiveControlException rejected(String code) {
        return new LiveControlException(code, "operator pilot authority operation rejected");
    }
}
