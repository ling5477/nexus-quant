package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;

/** 查询只读；创建、取消和重试均要求当前真实用户具有研究写权限。 */
@RestController
@RequestMapping("/api/strategy-experiments")
public class StrategyExperimentController {
    private final StrategyExperimentService service;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;
    public StrategyExperimentController(StrategyExperimentService service,GatewayAuthFacade auth,CurrentUserProfileService profiles) {
        this.service=service;this.auth=auth;this.profiles=profiles;
    }
    private long actor(boolean write) {
        var actor=auth.currentUser().flatMap(c->profiles.findByUsername(c.username()))
                .orElseThrow(()->new AuthenticationCredentialsNotFoundException("authentication required"));
        if(actor.userId()==null || !actor.enabled() || actor.mustChangePassword())
            throw new AuthenticationCredentialsNotFoundException("authentication required");
        if(write && !actor.roles().contains("ADMIN") && !actor.roles().contains("OPERATOR"))
            throw new AccessDeniedException("operator role required");
        return actor.userId();
    }
    @GetMapping public List<StrategyExperimentService.Summary> list(@RequestParam(defaultValue="20") int limit,@RequestParam(defaultValue="0") int offset) {
        return service.list(actor(false),limit,offset);
    }
    @GetMapping("/{id}") public StrategyExperimentService.View get(@PathVariable UUID id) {return service.get(id,actor(false));}
    @PostMapping("/preview") public List<JsonNode> preview(@RequestBody StrategyExperimentService.Request request) {actor(true);return service.preview(request);}
    @PostMapping("/operations/{id}") public StrategyExperimentService.View create(@PathVariable UUID id,@RequestBody StrategyExperimentService.Request request) {return service.create(id,request,actor(true));}
    @PostMapping("/{id}/cancel") public StrategyExperimentService.View cancel(@PathVariable UUID id) {return service.cancel(id,actor(true));}
    @PostMapping("/{id}/trials/{trial}/attempts/{attempt}") public StrategyExperimentService.View retry(@PathVariable UUID id,@PathVariable UUID trial,@PathVariable UUID attempt) {return service.retry(id,trial,attempt,actor(true));}
}
