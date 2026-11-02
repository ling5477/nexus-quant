package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 研究观察只管理不可变目标与人工历史；经济评价始终来自原canonical分析，写入没有执行副作用。 */
@Service
public class StrategyObservationService {
    private static final Set<String> DECISIONS = Set.of(
            "CONTINUE_OBSERVATION", "STOP_OBSERVATION", "RETURN_TO_RESEARCH");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ResearchCreationOperationService operations;
    private final ResearchSimComparisonService comparison;
    private final ObjectProvider<StrategySimAnalysisQuery> analysis;
    private final TransactionTemplate reading;
    private Clock clock = Clock.systemUTC();

    public StrategyObservationService(JdbcTemplate jdbc, ObjectMapper mapper,
            ResearchCreationOperationService operations, ResearchSimComparisonService comparison,
            ObjectProvider<StrategySimAnalysisQuery> analysis, PlatformTransactionManager manager) {
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(5);
        this.mapper = mapper;
        this.operations = operations;
        this.comparison = comparison;
        this.analysis = analysis;
        reading = new TransactionTemplate(manager);
        reading.setReadOnly(true);
        reading.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reading.setTimeout(10);
    }

    /** 原operation事务负责并发重放及回滚；成功重放不因后来的游标或目录变化重新创建目标。 */
    public Detail create(UUID operation, CreateRequest request, long owner) {
        actor(owner);
        validateCreate(request);
        var receipt = operations.execute(operation, owner, "OBSERVATION", request.paperRunId(),
                mapper.valueToTree(request), () -> {
                    Instant registeredAt = clock.instant();
                    validateWindow(request, registeredAt);
                    var identity = requireRun(owner, request.paperRunId(), request.canonicalAccountId(),
                            request.strategyVersionId(), request.publishId());
                    var linked = comparison.compare(request.validationId(), request.selectedTrialId(),
                            request.strategyVersionId(), request.publishId(), request.paperRunId(), owner);
                    require(request.paperRunId().equals(linked.paperRunId()), "OBSERVATION_RESEARCH_BINDING_MISMATCH");
                    require(Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM continuous_sim_runs WHERE paper_run_id=?)",
                            Boolean.class, request.paperRunId())), "CONTINUOUS_SIM_REQUIRED");
                    if (request.supersedesObservationId() != null) {
                        Observation previous = owned(request.supersedesObservationId(), owner, false);
                        require(previous.paperRunId().equals(request.paperRunId())
                                && previous.strategyVersionId().equals(request.strategyVersionId())
                                && previous.publishId().equals(request.publishId()), "OBSERVATION_PREDECESSOR_MISMATCH");
                    }
                    var policy = mapper.createObjectNode()
                            .put("methodVersion", "CONTINUOUS_SIM_OBSERVATION_V1")
                            .put("metricSource", "CANONICAL_SIM_ANALYSIS")
                            .put("researchComparisonStatus", "NOT_COMPARABLE")
                            .put("researchComparisonReason", "DIFFERENT_MARKET_PERIODS_AND_METRIC_BASES")
                            .put("maximumDays", 90).put("maximumValuationEvents", 10000)
                            .put("maximumTrades", 10000).put("executionSideEffects", "NONE")
                            .put("stopMeaning", "CLOSE_RESEARCH_OBSERVATION_ONLY")
                            .put("sourceExperimentId", jdbc.queryForObject(
                                    "SELECT source_experiment_id::text FROM experiment_validations WHERE validation_id=?",
                                    String.class, request.validationId()))
                            .put("backtestConfigId", linked.backtestConfigId())
                            .put("backtestRunId", linked.backtestRunId());
                    UUID id = UUID.randomUUID();
                    jdbc.update("""
                        INSERT INTO strategy_observations(observation_id,operation_id,owner_user_id,paper_run_id,
                         canonical_account_id,strategy_version_id,publish_id,validation_id,selected_trial_id,
                         strategy_checksum,kind,observation_from,observation_to,time_basis,registered_at,
                         supersedes_observation_id,policy)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTUAL_EVENT_TIME',?,?,?::jsonb)
                        """, id, operation, owner, identity.paperRunId(), identity.canonicalAccountId(),
                            identity.strategyVersionId(), identity.publishId(), request.validationId(),
                            request.selectedTrialId(), linked.strategyChecksum(), request.kind(),
                            at(request.observationFrom()), at(request.observationTo()), at(registeredAt),
                            request.supersedesObservationId(), policy.toString());
                    return new ResearchCreationOperationService.Result(id.toString(), request.paperRunId());
                });
        return get(UUID.fromString(receipt.objectId()), owner);
    }

    public List<Observation> list(String run, int limit, int offset, long owner) {
        actor(owner);
        requireId(run);
        if (limit < 1 || limit > 50 || offset < 0 || offset > 950)
            throw bad("INVALID_OBSERVATION_PAGE");
        return reading.execute(status -> {
            canonical().readIdentity(owner, run).orElseThrow(StrategyObservationService::notFound);
            return jdbc.query("""
                SELECT * FROM strategy_observations WHERE paper_run_id=? AND owner_user_id=?
                ORDER BY registered_at DESC,observation_id LIMIT ? OFFSET ?
                """, (r, n) -> observation(r), run, owner, limit, offset);
        });
    }

    public Detail get(UUID id, long owner) { return get(id, owner, 0); }

    /** 历史分页只读，不把读取或重登录变成评价修订。 */
    public Detail get(UUID id, long owner, int reviewsOffset) {
        actor(owner);
        if (reviewsOffset < 0 || reviewsOffset > 9980 || reviewsOffset % 20 != 0)
            throw bad("INVALID_OBSERVATION_REVIEW_PAGE");
        return reading.execute(status -> {
            var observation = owned(id, owner, false);
            requireRun(owner, observation.paperRunId(), observation.canonicalAccountId(),
                    observation.strategyVersionId(), observation.publishId());
            var reviews = jdbc.query("""
                SELECT * FROM strategy_observation_reviews WHERE observation_id=?
                ORDER BY revision DESC LIMIT 21 OFFSET ?
                """, (r, n) -> review(r), id, reviewsOffset);
            return new Detail(observation, reviews.stream().limit(20).toList(), reviews.size() > 20);
        });
    }

    /** 快照来自服务端原投影；客户端只确认水位身份，不能提交另一份收益或权益。 */
    public ReviewDetail review(UUID observationId, UUID operation, ReviewRequest request, long owner) {
        actor(owner);
        if (request == null || !DECISIONS.contains(request.decision() == null ? "" : request.decision())
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 2000
                || request.expectedRevision() < 0 || request.expectedRevision() >= 10000
                || request.observedAt() == null || request.expectedEvaluationChecksum() == null
                || !request.expectedEvaluationChecksum().matches("[0-9a-f]{64}"))
            throw bad("INVALID_OBSERVATION_REVIEW");
        var receipt = operations.execute(operation, owner, "OBSERVATION_REVIEW", observationId.toString(),
                mapper.valueToTree(request), () -> {
                    var observation = owned(observationId, owner, true);
                    require("OPEN".equals(observation.status()), "OBSERVATION_CLOSED");
                    require(request.expectedRevision() == observation.revision(), "OBSERVATION_REVISION_CONFLICT");
                    Instant reviewedAt = clock.instant();
                    if (request.observedAt().isAfter(reviewedAt)) throw bad("OBSERVATION_AS_OF_IN_FUTURE");
                    requireRun(owner, observation.paperRunId(), observation.canonicalAccountId(),
                            observation.strategyVersionId(), observation.publishId());
                    var evaluation = canonical().readForwardEvaluation(owner, observation.paperRunId(),
                            observation.observationFrom(), observation.observationTo(), request.observedAt())
                            .orElseThrow(StrategyObservationService::notFound);
                    require("ACTUAL_EVENT_TIME".equals(evaluation.timeBasis())
                            && request.expectedEvaluationChecksum().equals(evaluation.checksum()),
                            "OBSERVATION_EVALUATION_CHANGED");
                    JsonNode snapshot = mapper.valueToTree(evaluation);
                    if (snapshot.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2097152)
                        throw bad("OBSERVATION_SNAPSHOT_LIMIT");
                    UUID review = UUID.randomUUID();
                    long revision = observation.revision() + 1;
                    jdbc.update("""
                        INSERT INTO strategy_observation_reviews(review_id,operation_id,observation_id,revision,
                         reviewer_user_id,decision,reason,reviewed_at,observed_at,evaluation_checksum,evaluation_json)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?::jsonb)
                        """, review, operation, observationId, revision, owner, request.decision(), request.reason(),
                            at(reviewedAt), at(request.observedAt()), evaluation.checksum(), snapshot.toString());
                    int updated = jdbc.update("""
                        UPDATE strategy_observations SET revision=?,status=? WHERE observation_id=?
                         AND owner_user_id=? AND revision=? AND status='OPEN'
                        """, revision, "CONTINUE_OBSERVATION".equals(request.decision()) ? "OPEN" : "CLOSED",
                            observationId, owner, request.expectedRevision());
                    require(updated == 1, "OBSERVATION_REVISION_CONFLICT");
                    return new ResearchCreationOperationService.Result(review.toString(), observationId.toString());
                });
        return readReview(observationId, UUID.fromString(receipt.objectId()), owner);
    }

    public ReviewDetail readReview(UUID observationId, UUID reviewId, long owner) {
        actor(owner);
        return reading.execute(status -> {
            var observation = owned(observationId, owner, false);
            requireRun(owner, observation.paperRunId(), observation.canonicalAccountId(),
                    observation.strategyVersionId(), observation.publishId());
            return jdbc.query("SELECT * FROM strategy_observation_reviews WHERE observation_id=? AND review_id=?",
                    (r, n) -> new ReviewDetail(review(r), json(r.getString("evaluation_json"))), observationId, reviewId)
                    .stream().findFirst().orElseThrow(StrategyObservationService::notFound);
        });
    }

    public ResearchCreationOperationService.Receipt operation(UUID id, long owner) {
        actor(owner);
        return operations.find(id, owner)
                .filter(r -> Set.of("OBSERVATION", "OBSERVATION_REVIEW").contains(r.kind()))
                .orElseThrow(StrategyObservationService::notFound);
    }

    private Observation owned(UUID id, long owner, boolean lock) {
        if (id == null) throw bad("OBSERVATION_ID_REQUIRED");
        return jdbc.query("SELECT * FROM strategy_observations WHERE observation_id=? AND owner_user_id=?"
                        + (lock ? " FOR UPDATE" : ""), (r, n) -> observation(r), id, owner)
                .stream().findFirst().orElseThrow(StrategyObservationService::notFound);
    }

    private StrategySimAnalysisQuery.Run requireRun(long owner, String run, long account, String version, String publish) {
        var identity = canonical().readIdentity(owner, run).orElseThrow(StrategyObservationService::notFound);
        require(identity.canonicalAccountId() == account && version.equals(identity.strategyVersionId())
                && publish.equals(identity.publishId()), "OBSERVATION_RUN_IDENTITY_MISMATCH");
        return identity;
    }

    private StrategySimAnalysisQuery canonical() {
        var query = analysis.getIfAvailable();
        if (query == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SIM_QUERY_UNAVAILABLE");
        return query;
    }

    private static void actor(long owner) {
        var actor = TenantContext.requireCurrent();
        if (actor.userId() != owner || actor.kind() != TenantKind.LOCAL)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OBSERVATION_RESEARCH_ACCESS_DENIED");
    }

    private static void validateCreate(CreateRequest r) {
        if (r == null || r.canonicalAccountId() <= 0 || r.validationId() == null || r.selectedTrialId() == null
                || !Set.of("FORWARD", "RETROSPECTIVE").contains(r.kind() == null ? "" : r.kind())
                || r.observationFrom() == null || r.observationTo() == null)
            throw bad("INVALID_OBSERVATION_INPUT");
        requireId(r.paperRunId()); requireId(r.strategyVersionId()); requireId(r.publishId());
    }

    static void validateWindow(CreateRequest r, Instant registeredAt) {
        Duration duration = Duration.between(r.observationFrom(), r.observationTo());
        if (duration.compareTo(Duration.ofDays(1)) < 0 || duration.compareTo(Duration.ofDays(90)) > 0
                || !r.observationFrom().equals(r.observationFrom().truncatedTo(ChronoUnit.HOURS))
                || !r.observationTo().equals(r.observationTo().truncatedTo(ChronoUnit.HOURS)))
            throw bad("OBSERVATION_RANGE_REQUIRES_1_TO_90_UTC_DAYS");
        if ("RETROSPECTIVE".equals(r.kind()) && r.observationTo().isAfter(registeredAt)
                || "FORWARD".equals(r.kind()) && r.observationFrom().isBefore(
                        registeredAt.truncatedTo(ChronoUnit.HOURS).plusSeconds(3600)))
            throw bad("OBSERVATION_REGISTRATION_TIME_MISMATCH");
    }

    private Observation observation(ResultSet r) throws SQLException {
        return new Observation(r.getObject("observation_id", UUID.class), r.getString("paper_run_id"),
                r.getLong("canonical_account_id"), r.getString("strategy_version_id"), r.getString("publish_id"),
                r.getObject("validation_id", UUID.class), r.getObject("selected_trial_id", UUID.class),
                r.getString("strategy_checksum"), r.getString("kind"), time(r, "observation_from"),
                time(r, "observation_to"), r.getString("time_basis"), r.getString("status"), r.getLong("revision"),
                time(r, "registered_at"), r.getObject("supersedes_observation_id", UUID.class), json(r.getString("policy")));
    }

    private static ReviewSummary review(ResultSet r) throws SQLException {
        return new ReviewSummary(r.getObject("review_id", UUID.class), r.getObject("observation_id", UUID.class),
                r.getLong("revision"), r.getString("decision"), r.getString("reason"), r.getLong("reviewer_user_id"),
                time(r, "reviewed_at"), time(r, "observed_at"), r.getString("evaluation_checksum"));
    }

    private JsonNode json(String text) {
        // JSONB可规范化数值表示；读取冻结比例和金额时保留十进制精度，不经过double再序列化。
        try { return mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(text); }
        catch (java.io.IOException e) { throw new IllegalStateException("OBSERVATION_SNAPSHOT_INVALID", e); }
    }
    private static Instant time(ResultSet r, String field) throws SQLException { return r.getTimestamp(field).toInstant(); }
    private static Timestamp at(Instant value) { return Timestamp.from(value); }
    private static void requireId(String id) { if (id == null || id.isBlank() || id.length() > 128) throw bad("INVALID_OBSERVATION_IDENTITY"); }
    private static void require(boolean ok, String reason) { if (!ok) throw new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private static ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "OBSERVATION_NOT_FOUND"); }

    public record CreateRequest(String paperRunId, long canonicalAccountId, String strategyVersionId, String publishId,
            UUID validationId, UUID selectedTrialId, String kind, Instant observationFrom, Instant observationTo,
            UUID supersedesObservationId) {}
    public record ReviewRequest(long expectedRevision, String decision, String reason, Instant observedAt,
            String expectedEvaluationChecksum) {}
    public record Observation(UUID observationId, String paperRunId, long canonicalAccountId, String strategyVersionId,
            String publishId, UUID validationId, UUID selectedTrialId, String strategyChecksum, String kind,
            Instant observationFrom, Instant observationTo, String timeBasis, String status, long revision,
            Instant registeredAt, UUID supersedesObservationId, JsonNode policy) {}
    public record ReviewSummary(UUID reviewId, UUID observationId, long revision, String decision, String reason,
            long reviewerUserId, Instant reviewedAt, Instant observedAt, String evaluationChecksum) {}
    public record Detail(Observation observation, List<ReviewSummary> reviews, boolean hasMoreReviews) {}
    public record ReviewDetail(ReviewSummary review, JsonNode evaluation) {}
}
