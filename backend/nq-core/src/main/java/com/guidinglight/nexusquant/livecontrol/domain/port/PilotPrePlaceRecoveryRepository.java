package com.guidinglight.nexusquant.livecontrol.domain.port;

import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Attempt级零intent pre-PLACE recovery判定；不提供lease复活或PLACE重试能力。 */
public interface PilotPrePlaceRecoveryRepository {

    Optional<Authorization> decide(
            long ownerId,
            long exchangeAccountId,
            long credentialReferenceId,
            String instrument,
            BigDecimal maxNotional,
            UUID decisionId,
            String requestId,
            String traceId,
            Instant decidedAt
    );

    boolean lockAndValidateSessionRecovery(LiveSession session, UUID decisionId);

    Optional<UUID> lockExpiredPreparationSession(
            long ownerId,
            long exchangeAccountId,
            long credentialReferenceId,
            String instrument,
            BigDecimal maxNotional,
            UUID decisionId
    );

    record Authorization(
            UUID decisionId,
            UUID predecessorLeaseId,
            UUID predecessorSessionId,
            int replacementOrdinal
    ) {
        public Authorization {
            java.util.Objects.requireNonNull(decisionId);
            java.util.Objects.requireNonNull(predecessorLeaseId);
            java.util.Objects.requireNonNull(predecessorSessionId);
            if (replacementOrdinal != 1) {
                throw new IllegalArgumentException("replacementOrdinal must equal one");
            }
        }
    }
}
