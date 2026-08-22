package com.guidinglight.nexusquant.livecontrol.domain.port;

import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotScopeAuthorization;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;

import java.time.Instant;

/** V39 event-backed exact operator scope authorization port；不拥有交易执行语义。 */
public interface ExactPilotScopeAuthorizationRepository {

    ExactPilotScopeAuthorization recordApproved(
            ExactPilotScopeAuthorization authorization,
            LiveSession lockedSession,
            ExactPilotBinding.Correlation creatorCorrelation,
            ExactPilotBinding.Correlation approverCorrelation,
            Instant approvedAt,
            Instant expiresAt
    );

    void requireApproved(
            long creatorPrincipal,
            ExactPilotBindingCommand command,
            ExactPilotBinding.AuthoritativeFacts currentFacts,
            Instant decisionAt
    );
}
