package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * 只读 provider observation authority 的调用期安全边界。
 *
 * <p>每次真实 prerequisite collection 都先读取 durable kill switch；只有 {@code ENGAGED}
 * 才委托既有 OKX trusted authority。UNKNOWN、缺记录、DB 失败或 DISENGAGED 都在 credential
 * JIT 之前 fail-closed。本类不缓存 credential，不持有 provider mutation port，也不在构造期访问 DB/网络。</p>
 */
public final class KillSwitchGuardedProviderObservationAuthority
        implements PilotPrerequisiteObservationAuthority {

    private final PilotPrerequisiteObservationAuthority delegate;
    private final KillSwitchService killSwitchService;

    public KillSwitchGuardedProviderObservationAuthority(
            PilotPrerequisiteObservationAuthority delegate,
            KillSwitchService killSwitchService
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.killSwitchService = Objects.requireNonNull(killSwitchService, "killSwitchService must not be null");
    }

    @Override
    public PilotObservationSet resolveTrustedObservationSet(
            LiveSession session,
            PilotScopeBinding scope,
            Instant resolvedAt
    ) {
        if (killSwitchService.snapshot().status() != KillSwitchStatus.ENGAGED) {
            throw new LiveControlException(
                    "READ_ONLY_PROVIDER_OBSERVATION_KILL_SWITCH_REQUIRED",
                    "kill switch must be engaged before trusted prerequisite collection"
            );
        }
        return delegate.resolveTrustedObservationSet(session, scope, resolvedAt);
    }
}
