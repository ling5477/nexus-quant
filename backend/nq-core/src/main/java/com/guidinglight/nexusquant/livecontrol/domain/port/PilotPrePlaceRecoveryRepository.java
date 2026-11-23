package com.guidinglight.nexusquant.livecontrol.domain.port;

import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

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
            Objects.requireNonNull(decisionId);
            Objects.requireNonNull(predecessorLeaseId);
            Objects.requireNonNull(predecessorSessionId);
            if (replacementOrdinal <= 0) {
                throw new IllegalArgumentException("replacementOrdinal must be positive");
            }
        }
    }
}
