package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingValidation;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlCommand;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlResult;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationResult;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotScopeAuthorization;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;

import java.util.Objects;

/**
 * Formal local control surface orchestration；trusted collection 复用 PilotScope control plane，binding 不消费。
 */
public final class ExactPilotScopeControlSurfaceService implements ExactPilotScopeControlPlane {

    private final PilotScopeControlPlane pilotScopeControlPlane;
    private final ExactPilotScopeAuthorizationService scopeAuthorizationService;
    private final ExactPilotBindingControlPlane bindingControlPlane;

    public ExactPilotScopeControlSurfaceService(
            PilotScopeControlPlane pilotScopeControlPlane,
            ExactPilotScopeAuthorizationService scopeAuthorizationService,
            ExactPilotBindingControlPlane bindingControlPlane
    ) {
        this.pilotScopeControlPlane = Objects.requireNonNull(
                pilotScopeControlPlane, "pilotScopeControlPlane must not be null");
        this.scopeAuthorizationService = Objects.requireNonNull(
                scopeAuthorizationService, "scopeAuthorizationService must not be null");
        this.bindingControlPlane = Objects.requireNonNull(
                bindingControlPlane, "bindingControlPlane must not be null");
    }

    @Override
    public ExactPilotScopeControlResult materializeAndBind(
            AuthenticatedLiveControlActor creator,
            AuthenticatedLiveControlActor approver,
            ExactPilotScopeControlCommand command
    ) {
        Objects.requireNonNull(creator, "creator must not be null");
        Objects.requireNonNull(approver, "approver must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (creator.userId() == approver.userId()) {
            throw new LiveControlException(
                    "EXACT_PILOT_SCOPE_SELF_APPROVAL_FORBIDDEN",
                    "creator and approver must be independent principals");
        }
        scopeAuthorizationService.preflightPrincipals(creator, approver);
        PilotScopeMaterializationResult materialized = pilotScopeControlPlane.materialize(
                creator, command.materialization());
        requireMaterializationMatches(command, materialized);
        pilotScopeControlPlane.approve(approver, command.pilotApproval());
        ExactPilotBindingCommand bindingCommand = command.binding().toCommand(
                materialized.sessionId(), materialized.pilotScopeId(), materialized.observationSetId());
        ExactPilotScopeAuthorization authorization = scopeAuthorizationService.authorizeAndApprove(
                creator, approver, bindingCommand, command.exactScopeApproval());
        ExactPilotBinding binding = bindingControlPlane.create(creator, bindingCommand);
        ExactPilotBindingValidation validation = bindingControlPlane.validate(
                creator, binding.sessionId(), binding.id());
        if (validation.lifecycle() != ExactPilotBinding.Lifecycle.VERIFIED
                || validation.tradingAuthorized()) {
            throw new LiveControlException(
                    "EXACT_PILOT_BINDING_POST_MATERIALIZATION_INVALID",
                    "materialized binding is not valid and unconsumed");
        }
        return new ExactPilotScopeControlResult(
                materialized.sessionId(), materialized.pilotScopeId(), materialized.observationSetId(),
                materialized.pilotScopeHash(), authorization.scopeDigest(), binding.id(),
                binding.bindingDigest(), validation.lifecycle(), false, false, false);
    }

    private static void requireMaterializationMatches(
            ExactPilotScopeControlCommand command,
            PilotScopeMaterializationResult result
    ) {
        if (!command.materialization().sessionId().equals(result.sessionId())
                || !command.materialization().pilotScopeId().equals(result.pilotScopeId())
                || !command.materialization().expectedPilotScopeHash().equals(result.pilotScopeHash())) {
            throw new LiveControlException(
                    "EXACT_PILOT_SCOPE_MATERIALIZATION_MISMATCH",
                    "materialized pilot scope differs from explicit operator input");
        }
    }
}
