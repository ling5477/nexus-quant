package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ReleaseToShadowAdmissionDecision;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreview;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewService;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Strategy Release admission preview GET contract、404 与响应安全回归。 */
class StrategyReleaseAdmissionPreviewControllerTest {

    private static final String PUBLISH_ID = "publish-preview-001";
    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private StrategyReleaseAdmissionPreviewService previewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        previewService = mock(StrategyReleaseAdmissionPreviewService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StrategyReleaseAdmissionPreviewController(previewService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnSafeEligiblePreview() throws Exception {
        when(previewService.preview(
                org.mockito.ArgumentMatchers.eq(PUBLISH_ID),
                org.mockito.ArgumentMatchers.anyString()
        ))
                .thenReturn(Optional.of(preview()));

        MvcResult result = mockMvc.perform(get(
                        "/api/strategy-releases/{publishRecordId}/shadow-admission-preview",
                        PUBLISH_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishRecordId").value(PUBLISH_ID))
                .andExpect(jsonPath("$.releaseAnchorId").value(PUBLISH_ID))
                .andExpect(jsonPath("$.strategyVersionId").value("strategy-version-preview-001"))
                .andExpect(jsonPath("$.datasetId").value(DATASET_ID.toString()))
                .andExpect(jsonPath("$.evaluationId").value("evaluation-preview-001"))
                .andExpect(jsonPath("$.bindingMode").value("RELEASE_BOUND"))
                .andExpect(jsonPath("$.releaseStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.artifactVerificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.validationDecision").value("APPROVED"))
                .andExpect(jsonPath("$.admissionDecision").value("ELIGIBLE"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("ELIGIBLE_FOR_CREATION_PLAN_ONLY"))
                .andExpect(jsonPath("$.artifactDigest").value("a".repeat(64)))
                .andReturn();

        verify(previewService).preview(
                org.mockito.ArgumentMatchers.eq(PUBLISH_ID),
                org.mockito.ArgumentMatchers.anyString()
        );
        assertNoForbiddenResponseFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldReturn404WhenPublishRecordDoesNotExist() throws Exception {
        when(previewService.preview(
                org.mockito.ArgumentMatchers.eq(PUBLISH_ID),
                org.mockito.ArgumentMatchers.anyString()
        ))
                .thenReturn(Optional.empty());

        mockMvc.perform(get(
                        "/api/strategy-releases/{publishRecordId}/shadow-admission-preview",
                        PUBLISH_ID
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldExposeOnlyOneGetEndpointWithoutCommandActions() {
        List<Method> endpoints = Arrays.stream(StrategyReleaseAdmissionPreviewController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toList();

        assertEquals(1, endpoints.size());
        Method endpoint = endpoints.getFirst();
        assertFalse(endpoint.isAnnotationPresent(PostMapping.class));
        assertFalse(endpoint.isAnnotationPresent(PatchMapping.class));
        assertFalse(endpoint.isAnnotationPresent(PutMapping.class));
        assertFalse(endpoint.isAnnotationPresent(DeleteMapping.class));
        String route = String.join(",", endpoint.getAnnotation(GetMapping.class).value())
                .toLowerCase(Locale.ROOT);
        for (String forbidden : List.of("create", "start", "execute", "trade", "order", "upload", "bind")) {
            assertFalse(route.contains(forbidden), route);
        }
    }

    private static StrategyReleaseAdmissionPreview preview() {
        return new StrategyReleaseAdmissionPreview(
                PUBLISH_ID,
                PUBLISH_ID,
                "strategy-version-preview-001",
                DATASET_ID,
                "evaluation-preview-001",
                ShadowRunReleaseBindingMode.RELEASE_BOUND,
                StrategyReleaseStatus.VERIFIED,
                StrategyArtifactVerificationResult.Status.VERIFIED,
                StrategyValidationDecision.APPROVED,
                ReleaseToShadowAdmissionDecision.Decision.ELIGIBLE,
                List.of("ELIGIBLE_FOR_CREATION_PLAN_ONLY"),
                "a".repeat(64)
        );
    }

    private static void assertNoForbiddenResponseFields(String body) {
        String normalized = body.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                "trustedroot",
                "absolutepath",
                "artifactstoragekey",
                "manifeststoragekey",
                "rawmanifest",
                "artifactcontent",
                "creationplan",
                "internalexception",
                "credential",
                "token",
                "privateendpoint"
        )) {
            assertFalse(normalized.contains(forbidden), "response must not contain " + forbidden + ": " + body);
        }
    }
}
