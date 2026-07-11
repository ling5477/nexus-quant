package com.guidinglight.nexusquant.validationreview.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewAction;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewActor;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewOperationsService;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewOperationalAuditService;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCaseQuery;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewSeverity;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * GateV-2 durable validation review 的 query 与有限 lifecycle REST API。
 *
 * <p>Controller 只从既有 authentication/profile context 解析 actor/roles；tenant 固定在 core，
 * 客户端无法覆盖 actor、owner、tenant、requestId 或 traceId。所有写侧复用 GateV-1 状态机与事务。
 */
@Validated
@RestController
@RequestMapping("/api/validation-review-cases")
@Tag(name = "Validation Review API", description = "GateV-2 本地人工复核 lifecycle 接口。")
public class ValidationReviewController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService currentUserProfileService;
    private final ValidationReviewOperationsService operationsService;
    private final ValidationReviewOperationalAuditService operationalAuditService;
    private final ApiExceptionHandler apiExceptionHandler;

    public ValidationReviewController(
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService currentUserProfileService,
            ValidationReviewOperationsService operationsService,
            ValidationReviewOperationalAuditService operationalAuditService,
            ApiExceptionHandler apiExceptionHandler
    ) {
        this.gatewayAuthFacade = Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
        this.currentUserProfileService = Objects.requireNonNull(
                currentUserProfileService,
                "currentUserProfileService must not be null"
        );
        this.operationsService = Objects.requireNonNull(operationsService, "operationsService must not be null");
        this.operationalAuditService = Objects.requireNonNull(
                operationalAuditService,
                "operationalAuditService must not be null"
        );
        this.apiExceptionHandler = Objects.requireNonNull(apiExceptionHandler, "apiExceptionHandler must not be null");
    }

    /**
     * 为 Spring JSON binding 阶段拒绝的 lifecycle body 补充脱敏 operational audit。
     *
     * <p>Why：损坏 JSON 在进入 application service 前即被拒绝；本地 handler 只从可信 URI、认证
     * context 与 trace context 派生 allowlisted audit 字段，然后复用全局 handler 的既有 envelope。
     * 原始 body、header 与 parser exception 不进入 audit。
     *
     * @param ex Spring JSON binding exception；仅交给既有 envelope mapper
     * @param request 当前 HTTP request
     * @return 既有 {@code MALFORMED_REQUEST} ApiErrorResponse
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse auditMalformedLifecycleBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        MalformedLifecyclePath path = MalformedLifecyclePath.parse(request.getRequestURI());
        if (path != null) {
            ValidationReviewActor actor = resolveActor();
            operationalAuditService.recordRejected(
                    path.caseId(),
                    path.action(),
                    null,
                    path.action().targetState(),
                    actor.userId(),
                    UUID.randomUUID().toString(),
                    TraceIdContext.getOrCreate(),
                    "REVIEW_REQUEST_INVALID"
            );
        }
        return apiExceptionHandler.handleHttpMessageNotReadable(ex, request);
    }

    /**
     * 查询当前角色 SQL scope 内的 bounded case list。
     *
     * <p>OPERATOR 的 owner scope 和 ADMIN 的 tenant scope 均由服务端决定；本方法只做参数映射，
     * 不产生 case、event 或 operational audit 写侧。
     *
     * @param state 可空 case state 筛选
     * @param severity 可空 severity 筛选
     * @param ownerId 仅 ADMIN 可用的 owner 筛选
     * @param limit 1..100，默认 50
     * @param offset 0..10000，默认 0
     * @return 按 updatedAt、id 降序排列的安全 case DTO
     */
    @GetMapping
    @Operation(summary = "查询 validation review cases", security = @SecurityRequirement(name = "bearerAuth"))
    public List<ValidationReviewCaseResponse> list(
            @RequestParam(required = false) ValidationReviewState state,
            @RequestParam(required = false) ValidationReviewSeverity severity,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        ValidationReviewCaseQuery query = new ValidationReviewCaseQuery(state, severity, ownerId, limit, offset);
        return operationsService.listCases(resolveActor(), query).stream()
                .map(ValidationReviewCaseResponse::from)
                .toList();
    }

    /**
     * 查询 SQL scope 内的 case detail。
     *
     * @param caseId path case id
     * @return 不含 evidence anchor、credential 或交易授权字段的 case DTO
     * @throws com.guidinglight.nexusquant.validationreview.domain.ValidationReviewException case 不存在或 scope 不可见
     */
    @GetMapping("/{caseId}")
    @Operation(summary = "查询 validation review case", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "case 不存在或 scope 不可见", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ValidationReviewCaseResponse detail(@PathVariable UUID caseId) {
        return ValidationReviewCaseResponse.from(operationsService.detail(resolveActor(), caseId));
    }

    /**
     * 查询 SQL scope 内按 created_at/id 升序排列的 event timeline。
     *
     * <p>返回值不暴露 request hash、idempotency key 或 raw metadata，且 GET 不产生任何写侧。
     *
     * @param caseId path case id
     * @return 最多 100 条稳定排序的最小 event DTO
     */
    @GetMapping("/{caseId}/events")
    @Operation(summary = "查询 validation review events", security = @SecurityRequirement(name = "bearerAuth"))
    public List<ValidationReviewEventResponse> events(@PathVariable UUID caseId) {
        return operationsService.events(resolveActor(), caseId).stream()
                .map(ValidationReviewEventResponse::from)
                .toList();
    }

    /**
     * 执行 {@code OPEN -> ACKNOWLEDGED}。
     *
     * @param caseId path case id
     * @param idempotencyKey case-local 幂等键；缺失或复用冲突由统一错误 envelope 返回
     * @param body expectedVersion、reason 与可选脱敏 metadata
     * @return accepted 或首次 accepted snapshot 的幂等 replay
     */
    @PostMapping("/{caseId}/acknowledge")
    public ValidationReviewCaseResponse acknowledge(
            @PathVariable UUID caseId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ValidationReviewLifecycleRequestBody body
    ) {
        return transition(caseId, ValidationReviewAction.ACKNOWLEDGE, idempotencyKey, body);
    }

    /**
     * 执行 {@code OPEN/ACKNOWLEDGED -> ESCALATED}。
     *
     * @param caseId path case id
     * @param idempotencyKey case-local 幂等键
     * @param body expectedVersion、reason 与可选脱敏 metadata
     * @return accepted 或首次 accepted snapshot 的幂等 replay
     */
    @PostMapping("/{caseId}/escalate")
    public ValidationReviewCaseResponse escalate(
            @PathVariable UUID caseId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ValidationReviewLifecycleRequestBody body
    ) {
        return transition(caseId, ValidationReviewAction.ESCALATE, idempotencyKey, body);
    }

    /**
     * 执行 {@code ACKNOWLEDGED/ESCALATED -> RESOLVED}。
     *
     * @param caseId path case id
     * @param idempotencyKey case-local 幂等键
     * @param body expectedVersion、reason 与可选脱敏 metadata
     * @return accepted 或首次 accepted snapshot 的幂等 replay
     */
    @PostMapping("/{caseId}/resolve")
    public ValidationReviewCaseResponse resolve(
            @PathVariable UUID caseId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ValidationReviewLifecycleRequestBody body
    ) {
        return transition(caseId, ValidationReviewAction.RESOLVE, idempotencyKey, body);
    }

    /**
     * 执行 {@code RESOLVED -> CLOSED}；CLOSED 后的任何动作继续由 GateV-1 状态机拒绝。
     *
     * @param caseId path case id
     * @param idempotencyKey case-local 幂等键
     * @param body expectedVersion、reason 与可选脱敏 metadata
     * @return accepted 或首次 accepted snapshot 的幂等 replay
     */
    @PostMapping("/{caseId}/close")
    public ValidationReviewCaseResponse close(
            @PathVariable UUID caseId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ValidationReviewLifecycleRequestBody body
    ) {
        return transition(caseId, ValidationReviewAction.CLOSE, idempotencyKey, body);
    }

    private ValidationReviewCaseResponse transition(
            UUID caseId,
            ValidationReviewAction action,
            String idempotencyKey,
            ValidationReviewLifecycleRequestBody body
    ) {
        String traceId = TraceIdContext.getOrCreate();
        String requestId = UUID.randomUUID().toString();
        Long expectedVersion = body == null ? null : body.expectedVersion();
        String reason = body == null ? null : body.reason();
        return ValidationReviewCaseResponse.from(operationsService.transition(
                resolveActor(),
                caseId,
                action,
                expectedVersion,
                reason,
                body == null ? null : body.metadata(),
                idempotencyKey,
                requestId,
                traceId
        ).reviewCase());
    }

    private ValidationReviewActor resolveActor() {
        var token = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        AuthUserProfile profile = currentUserProfileService.findByUsername(token.username())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return new ValidationReviewActor(profile.userId(), Set.copyOf(profile.roles()));
    }

    private record MalformedLifecyclePath(UUID caseId, ValidationReviewAction action) {
        private static MalformedLifecyclePath parse(String requestUri) {
            if (requestUri == null) {
                return null;
            }
            String[] segments = requestUri.split("/");
            if (segments.length != 5 || !"api".equals(segments[1])
                    || !"validation-review-cases".equals(segments[2])) {
                return null;
            }
            try {
                ValidationReviewAction action = switch (segments[4]) {
                    case "acknowledge" -> ValidationReviewAction.ACKNOWLEDGE;
                    case "escalate" -> ValidationReviewAction.ESCALATE;
                    case "resolve" -> ValidationReviewAction.RESOLVE;
                    case "close" -> ValidationReviewAction.CLOSE;
                    default -> null;
                };
                return action == null ? null : new MalformedLifecyclePath(UUID.fromString(segments[3]), action);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
