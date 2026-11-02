package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 观察与人工记录只由显式命令写入；页面读取、导航和恢复查询不产生研究或交易事实。 */
@RestController
@RequestMapping("/api/strategy-observations")
public class StrategyObservationController {
    private final StrategyObservationService observations;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;

    public StrategyObservationController(StrategyObservationService observations, GatewayAuthFacade auth,
            CurrentUserProfileService profiles) {
        this.observations = observations; this.auth = auth; this.profiles = profiles;
    }

    @GetMapping
    public List<StrategyObservationService.Observation> list(@RequestParam String paperRunId,
            @RequestParam(defaultValue="20") int limit, @RequestParam(defaultValue="0") int offset) {
        return observations.list(paperRunId, limit, offset, actor(false));
    }
    @GetMapping("/{id}")
    public StrategyObservationService.Detail get(@PathVariable UUID id,
            @RequestParam(defaultValue="0") int reviewsOffset) {
        return observations.get(id, actor(false), reviewsOffset);
    }
    @GetMapping("/{id}/reviews/{review}")
    public StrategyObservationService.ReviewDetail review(@PathVariable UUID id, @PathVariable UUID review) {
        return observations.readReview(id, review, actor(false));
    }
    @GetMapping("/operations/{operation}")
    public ResearchCreationOperationService.Receipt operation(@PathVariable UUID operation) {
        return observations.operation(operation, actor(false));
    }
    @PostMapping("/operations/{operation}")
    public StrategyObservationService.Detail create(@PathVariable UUID operation,
            @RequestBody StrategyObservationService.CreateRequest request) {
        return observations.create(operation, request, actor(true));
    }
    @PostMapping("/{id}/reviews/operations/{operation}")
    public StrategyObservationService.ReviewDetail record(@PathVariable UUID id, @PathVariable UUID operation,
            @RequestBody StrategyObservationService.ReviewRequest request) {
        return observations.review(id, operation, request, actor(true));
    }

    private long actor(boolean write) {
        var actor = auth.currentUser().flatMap(c -> profiles.findByUsername(c.username()))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        if (actor.userId() == null || !actor.enabled() || actor.mustChangePassword())
            throw new AuthenticationCredentialsNotFoundException("authentication required");
        if (write && !actor.roles().contains("ADMIN") && !actor.roles().contains("OPERATOR"))
            throw new AccessDeniedException("operator role required");
        return actor.userId();
    }
}
