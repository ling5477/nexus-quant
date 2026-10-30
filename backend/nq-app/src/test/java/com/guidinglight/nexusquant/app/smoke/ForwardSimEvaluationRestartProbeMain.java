package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.research.StrategyObservationService;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import java.time.Instant;
import java.util.UUID;

/** 独立JVM读取原人工评价历史，不重新评价、推进SIM或复制经济事实。 */
public final class ForwardSimEvaluationRestartProbeMain {
    public static void main(String[] args) {
        if (args.length != 5) throw new IllegalArgumentException("owner, observation, review, checksum and synthetic asOf required");
        String url = System.getenv("TASK_FORWARD_URL");
        if (url == null || !url.startsWith("jdbc:postgresql://127.0.0.1:"))
            throw new IllegalArgumentException("isolated loopback database required");
        var owner = new TenantIdentity(TenantId.LOCAL, "NQ_LOCAL", TenantKind.LOCAL, Long.parseLong(args[0]));
        try (var app = ForwardSimEvaluationFixture.start(url, System.getenv("TASK_FORWARD_USER"), System.getenv("TASK_FORWARD_PASSWORD"));
                var scope = TenantContext.open(owner)) {
            ForwardSimEvaluationFixture.clock(app, Instant.parse(args[4]));
            var result = app.getBean(StrategyObservationService.class).readReview(UUID.fromString(args[1]), UUID.fromString(args[2]), owner.userId());
            if (!args[3].equals(result.review().evaluationChecksum()) || !args[3].equals(result.evaluation().path("checksum").asText()))
                throw new AssertionError("original persisted review checksum changed across JVM restart");
            System.out.println("FORWARD_REVIEW_RESTART_VERIFIED pid=" + ProcessHandle.current().pid());
        }
    }
}
