package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 查询只读；创建、取消和重试均要求当前真实用户具有研究写权限。 */
@RestController
@RequestMapping("/api/strategy-experiments")
public class StrategyExperimentController {
    private final StrategyExperimentService service;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;
    private final ExperimentValidationService validations;
    private final SelectedStrategyVersionService selectedVersions;
    public StrategyExperimentController(StrategyExperimentService service,GatewayAuthFacade auth,CurrentUserProfileService profiles,ExperimentValidationService validations,SelectedStrategyVersionService selectedVersions) {
        this.service=service;this.auth=auth;this.profiles=profiles;
        this.validations=validations;
        this.selectedVersions=selectedVersions;
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
    @GetMapping("/{id}/validation-policy") public JsonNode validationPolicy(@PathVariable UUID id) {return validations.preview(id,actor(false));}
    @GetMapping("/{id}/validations") public List<ExperimentValidationService.Summary> validations(@PathVariable UUID id) {return validations.list(id,actor(false));}
    @PostMapping("/{id}/validations/operations/{operation}") public ExperimentValidationService.View validate(@PathVariable UUID id,@PathVariable UUID operation) {return validations.create(id,operation,actor(true));}
    @GetMapping("/validations/{id}") public ExperimentValidationService.View validation(@PathVariable UUID id) {return validations.get(id,actor(false));}
    @GetMapping("/validations/{id}/selected-version")
    public SelectedStrategyVersionService.View selectedVersion(@PathVariable UUID id) {
        return selectedVersions.read(id, actor(false));
    }
    @PostMapping("/validations/{id}/selected-version/activate")
    public SelectedStrategyVersionService.View activateSelectedVersion(@PathVariable UUID id,
            @RequestBody(required=false) JsonNode request) {
        long owner = actor(true);
        if (request != null) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "activation accepts no client-controlled fields");
        return selectedVersions.activate(id, owner);
    }
    @PostMapping("/validations/{id}/cancel") public ExperimentValidationService.View cancelValidation(@PathVariable UUID id) {return validations.cancel(id,actor(true));}
    @PostMapping("/validations/{id}/holdout-views/{view}") public ExperimentValidationService.View viewHoldout(@PathVariable UUID id,@PathVariable UUID view) {return validations.revealHoldout(id,view,actor(false));}
}
