package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.time.Instant;

/**
 * Exact binding 的 server-owned 权威事实解析边界。
 *
 * <p>实现只能读取本地 durable account/credential reference、release admission、pilot scope、
 * prerequisite、risk、kill 与 immutable runtime identity；不得读取 credential material、调用 provider
 * 或创建任何 execution/order/ledger 事实。</p>
 */
public interface ExactPilotBindingAuthority {

    ExactPilotBinding.AuthoritativeFacts resolveForCreation(
            AuthenticatedLiveControlActor actor,
            ExactPilotBindingCommand command,
            Instant decisionAt
    );

    ExactPilotBinding.AuthoritativeFacts resolveCurrent(
            AuthenticatedLiveControlActor actor,
            ExactPilotBinding binding,
            Instant decisionAt
    );
}
