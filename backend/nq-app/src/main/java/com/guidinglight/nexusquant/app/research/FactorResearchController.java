package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 上传仅作本次只读预览；限制原始请求字节，拒绝重复字段和尾随 JSON。 */
@RestController
@RequestMapping("/api/marketdata/datasets/{datasetId}/factor-research")
public class FactorResearchController {
    private final FactorResearchService service;
    private final ObjectMapper mapper;

    public FactorResearchController(FactorResearchService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @GetMapping("/input")
    public JsonNode export(@PathVariable UUID datasetId) {
        TenantContext.requireCurrent();
        try { return service.export(datasetId); }
        catch (IllegalStateException error) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "FROZEN_RESEARCH_INPUT_UNAVAILABLE", error); }
    }

    @PostMapping(value = "/preview", consumes = "application/json")
    public JsonNode preview(@PathVariable UUID datasetId, HttpServletRequest request) throws IOException {
        TenantContext.requireCurrent();
        byte[] body = request.getInputStream().readNBytes(FactorResearchService.MAX_ARTIFACT_BYTES + 1);
        if (body.length > FactorResearchService.MAX_ARTIFACT_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FACTOR_ARTIFACT_BYTE_LIMIT_EXCEEDED");
        try { return service.preview(datasetId, mapper.readTree(body)); }
        catch (IllegalArgumentException | IOException | java.time.DateTimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FACTOR_ARTIFACT_INVALID_OR_IDENTITY_MISMATCH", error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "FROZEN_RESEARCH_INPUT_UNAVAILABLE", error);
        }
    }
}
