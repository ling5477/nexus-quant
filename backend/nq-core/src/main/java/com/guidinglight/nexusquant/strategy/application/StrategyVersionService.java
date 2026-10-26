package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersion;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionSnapshot;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * StrategyVersionService 提供策略版本策略版本管理主链。
 *
 * Why:
 * 策略定义本身会随配置和启停变化而变化；回测、发布和后续 Paper run 需要引用稳定版本。
 * 本服务只管理版本快照，不改变策略核心算法，也不触发回测、交易或 AI。
 */
@Service
public class StrategyVersionService {

    private final StrategyDefinitionRepository strategyDefinitionRepository;
    private final StrategyVersionRepository strategyVersionRepository;
    private final ObjectReader snapshotReader;
    private final Clock clock;

    @Autowired
    public StrategyVersionService(
            StrategyDefinitionRepository strategyDefinitionRepository,
            StrategyVersionRepository strategyVersionRepository,
            ObjectMapper objectMapper) {
        this(
                strategyDefinitionRepository,
                strategyVersionRepository,
                objectMapper,
                Clock.systemUTC());
    }

    StrategyVersionService(
            StrategyDefinitionRepository strategyDefinitionRepository,
            StrategyVersionRepository strategyVersionRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.strategyDefinitionRepository =
                Objects.requireNonNull(
                        strategyDefinitionRepository,
                        "strategyDefinitionRepository must not be null");
        this.strategyVersionRepository =
                Objects.requireNonNull(
                        strategyVersionRepository, "strategyVersionRepository must not be null");
        // 冻结参数不能先转为double；独立不可变读取器保留数值精度，避免改变共享解析器的其他消费者。
        this.snapshotReader =
                Objects.requireNonNull(objectMapper, "objectMapper must not be null")
                        .reader()
                        .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建策略版本并固化参数、配置和来源快照。
     *
     * @param request 创建命令；`strategyCode` 必须对应已有策略定义
     * @return 新创建的策略版本
     * @throws IllegalArgumentException 当策略不存在、状态非法或 JSON 快照非法时抛出
     */
    public StrategyVersion create(StrategyVersionCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String strategyCode = requireText(request.strategyCode(), "strategyCode");
        StrategyDefinition strategyDefinition =
                strategyDefinitionRepository
                        .findByStrategyCode(strategyCode)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "strategy definition not found: " + strategyCode));
        int nextVersion =
                strategyVersionRepository.maxVersion(strategyDefinition.strategyCode()) + 1;
        Instant now = Instant.now(clock);
        String paramSnapshot = normalizeJson(request.paramSnapshotJson());
        String configSnapshot =
                request.configSnapshotJson() == null || request.configSnapshotJson().isBlank()
                        ? normalizeJson(strategyDefinition.configSnapshot())
                        : normalizeJson(request.configSnapshotJson());
        String sourceSnapshot = normalizeJson(request.sourceSnapshotJson());
        if (TenantContext.current().filter(identity ->
                identity.kind() == TenantKind.DEMO).isPresent()) {
            try {
                com.fasterxml.jackson.databind.JsonNode source = snapshotReader.readTree(sourceSnapshot);
                if (!SpotSmaTargetStrategy.EXECUTABLE.equals(source.path("executable").asText()))
                    throw new IllegalArgumentException("TENANT_ACCESS_DENIED");
            } catch (java.io.IOException failure) { throw new IllegalArgumentException("TENANT_ACCESS_DENIED",failure); }
        }
        StrategyVersion version =
                new StrategyVersion(
                        "sv-" + UUID.randomUUID(),
                        strategyDefinition.strategyCode(),
                        nextVersion,
                        requireText(request.versionName(), "versionName"),
                        request.status() == null || request.status().isBlank()
                                ? StrategyVersionStatus.DRAFT
                                : StrategyVersionStatus.parse(request.status()),
                        paramSnapshot,
                        configSnapshot,
                        sourceSnapshot,
                        checksum(
                                strategyDefinition.strategyCode(),
                                nextVersion,
                                paramSnapshot,
                                configSnapshot,
                                sourceSnapshot),
                        request.createdBy() == null || request.createdBy().isBlank()
                                ? "system"
                                : request.createdBy().trim(),
                        now,
                        now);
        try {
            var source = snapshotReader.readTree(sourceSnapshot);
            if (SpotTargetStrategy.CHANNEL_BREAKOUT
                    .equals(strategyDefinition.strategyType())
                    || SpotTargetStrategy.CHANNEL_BREAKOUT
                    .equals(source.path("executable").asText())) {
                if (!strategyDefinition.strategyType().equals(source.path("executable").asText())) {
                    throw new IllegalArgumentException("STRATEGY_TYPE_EXECUTABLE_MISMATCH");
                }
                var snapshot = JsonNodeFactory.instance.objectNode();
                snapshot.put("strategyVersionId", version.strategyVersionId());
                snapshot.put("checksum", version.checksum());
                snapshot.set("sourceSnapshotJson", source);
                snapshot.set("paramSnapshotJson", snapshotReader.readTree(paramSnapshot));
                SpotTargetStrategy.fromSnapshot(
                        snapshot.toString(), new ObjectMapper());
            }
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("invalid strategy snapshot", failure);
        }
        strategyVersionRepository.insert(version);
        return version;
    }

    /**
     * 查询某个策略编码下的版本列表。
     *
     * @param strategyCode 策略编码
     * @return 版本列表，按版本号倒序排列
     */
    public List<StrategyVersion> listByStrategyCode(String strategyCode) {
        return strategyVersionRepository.listByStrategyCode(
                requireText(strategyCode, "strategyCode"));
    }

    /**
     * 查询策略版本详情，并校验其归属策略编码。
     *
     * @param strategyCode 策略编码
     * @param strategyVersionId 策略版本 ID
     * @return 策略版本详情
     * @throws IllegalArgumentException 当版本不存在或不属于该策略时抛出
     */
    public StrategyVersion getById(String strategyCode, String strategyVersionId) {
        String normalizedStrategyCode = requireText(strategyCode, "strategyCode");
        StrategyVersion version = findById(requireText(strategyVersionId, "strategyVersionId"));
        if (!normalizedStrategyCode.equals(version.strategyCode())) {
            throw new IllegalArgumentException(
                    "strategy version does not belong to strategy: " + normalizedStrategyCode);
        }
        return version;
    }

    /**
     * 按版本 ID 查询策略版本详情。
     *
     * @param strategyVersionId 策略版本 ID
     * @return 策略版本详情
     */
    public StrategyVersion findById(String strategyVersionId) {
        return strategyVersionRepository
                .findById(requireText(strategyVersionId, "strategyVersionId"))
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "strategy version not found: " + strategyVersionId));
    }

    /**
     * 构建发布记录可固化的策略版本快照。
     *
     * @param strategyVersionId 策略版本 ID
     * @return 可序列化快照，不包含敏感信息
     */
    public StrategyVersionSnapshot snapshot(String strategyVersionId) {
        StrategyVersion version = findById(strategyVersionId);
        return new StrategyVersionSnapshot(
                version.strategyVersionId(),
                version.strategyCode(),
                version.version(),
                version.versionName(),
                version.status().name(),
                version.paramSnapshotJson(),
                version.configSnapshotJson(),
                version.sourceSnapshotJson(),
                version.checksum());
    }

    private String normalizeJson(String json) {
        String value = json == null || json.isBlank() ? "{}" : json.trim();
        try {
            return snapshotReader.readTree(value).toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("snapshot json must be valid JSON", ex);
        }
    }

    private String checksum(
            String strategyCode,
            int version,
            String paramSnapshot,
            String configSnapshot,
            String sourceSnapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes =
                    digest.digest(
                            (strategyCode
                                            + "|"
                                            + version
                                            + "|"
                                            + paramSnapshot
                                            + "|"
                                            + configSnapshot
                                            + "|"
                                            + sourceSnapshot)
                                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
