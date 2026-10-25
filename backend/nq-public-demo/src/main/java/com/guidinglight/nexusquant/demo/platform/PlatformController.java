package com.guidinglight.nexusquant.demo.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 平台 API 只暴露最小汇总和启停命令，不接受租户上下文或经济字段。 */
@RestController
@RequestMapping("/api/platform")
public class PlatformController {
    private final PlatformReadService reads;
    private final PlatformUserService users;

    public PlatformController(PlatformReadService reads, PlatformUserService users) {
        this.reads = reads;
        this.users = users;
    }

    @GetMapping("/tenants")
    public Object tenants(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="") String search, @RequestParam(required=false) Boolean enabled,
            @RequestParam(defaultValue="registeredAt,desc") String sort) {
        return reads.list(page, size, search, enabled, sort);
    }

    @GetMapping("/tenants/{tenantId}/summary")
    public Object summary(@PathVariable UUID tenantId) { return reads.summary(tenantId); }

    @GetMapping("/tenants/{tenantId}/sim-performance")
    public Object performance(@PathVariable UUID tenantId) { return reads.performance(tenantId); }

    @PostMapping("/users/{userId}/disable")
    public JsonNode disable(@PathVariable long userId, @RequestBody JsonNode body) { return change(userId,"DISABLE",body); }

    @PostMapping("/users/{userId}/enable")
    public JsonNode enable(@PathVariable long userId, @RequestBody JsonNode body) { return change(userId,"ENABLE",body); }

    private JsonNode change(long userId, String action, JsonNode body) {
        if (body == null || !body.isObject()) throw invalid();
        body.fieldNames().forEachRemaining(field -> { if (!Set.of("operationId","reason").contains(field)) throw invalid(); });
        UUID id;
        try { id = UUID.fromString(body.path("operationId").asText()); }
        catch (IllegalArgumentException failure) { throw invalid(); }
        if (!body.path("reason").isTextual()) throw invalid();
        return users.change(id,userId,action,body.get("reason").textValue());
    }

    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"PLATFORM_INPUT_INVALID"); }
}
