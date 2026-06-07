package com.guidinglight.nexusquant.research.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.SourceStrategySnapshot;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.domain.port.ResearchConfigRepository;
import com.guidinglight.nexusquant.research.domain.port.SourceStrategySnapshotRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ResearchConfigService 提供 GateF-1 的研究配置管理能力。
 * <p>
 * Why:
 * GateF-1 需要先把“研究配置引用哪一个已冻结策略定义”固化下来，
 * 这样 GateF-2 开始建设回测执行链时，才能基于稳定快照而不是运行时可变定义继续扩展。
 */
@Service
public class ResearchConfigService {

    private final ResearchConfigRepository researchConfigRepository;
    private final SourceStrategySnapshotRepository sourceStrategySnapshotRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ResearchConfigService(
            ResearchConfigRepository researchConfigRepository,
            SourceStrategySnapshotRepository sourceStrategySnapshotRepository,
            ObjectMapper objectMapper
    ) {
        this(researchConfigRepository, sourceStrategySnapshotRepository, objectMapper, Clock.systemUTC());
    }

    public ResearchConfigService(
            ResearchConfigRepository researchConfigRepository,
            SourceStrategySnapshotRepository sourceStrategySnapshotRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.researchConfigRepository = Objects.requireNonNull(
                researchConfigRepository,
                "researchConfigRepository must not be null"
        );
        this.sourceStrategySnapshotRepository = Objects.requireNonNull(
                sourceStrategySnapshotRepository,
                "sourceStrategySnapshotRepository must not be null"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建研究配置。
     * Why:
     * 创建时必须复制 source strategy 快照，避免 research_config 在后续读取时隐式依赖 strategy_definitions 的可变内容。
     */
    public ResearchConfig create(ResearchConfigCreateRequest request) {
        validateCreateRequest(request);
        SourceStrategySnapshot sourceStrategy = sourceStrategySnapshotRepository.findByStrategyId(
                        requireText(request.sourceStrategyId(), "sourceStrategyId")
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "source strategy definition not found: " + request.sourceStrategyId()
                ));
        Instant now = Instant.now(clock);
        ResearchConfig researchConfig = new ResearchConfig(
                "rcf-" + UUID.randomUUID(),
                sourceStrategy.strategyId(),
                serializeStrategySnapshot(sourceStrategy),
                requireText(request.name(), "name"),
                normalizeNullableText(request.description()),
                normalizeJson(request.parameterSchema()),
                normalizeJson(request.parameterDefaults()),
                normalizeJson(request.datasetSpec()),
                now,
                now
        );
        researchConfigRepository.insert(researchConfig);
        return researchConfig;
    }

    /**
     * 查询单个研究配置。
     * Why:
     * HTTP 查询和回测配置创建都需要以 researchConfigId 为稳定入口，而不是重新回查 strategy_definitions。
     */
    public ResearchConfig getByResearchConfigId(String researchConfigId) {
        return researchConfigRepository.findByResearchConfigId(requireText(researchConfigId, "researchConfigId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "research config not found: " + researchConfigId
                ));
    }

    /**
     * 查询研究配置列表。
     * Why:
     * 控制台联调早期只提供最小列表面，因此只暴露 `sourceStrategyId` 这一真实存在的轻量过滤维度，
     * 避免提前引入复杂 DSL、分页协议或额外聚合读模型。
     *
     * @param sourceStrategyId 上游策略定义 ID，可空
     * @return 满足条件的研究配置列表，默认按仓储既有顺序返回
     */
    public List<ResearchConfig> list(String sourceStrategyId) {
        String normalizedSourceStrategyId = normalizeOptionalText(sourceStrategyId);
        return researchConfigRepository.list(normalizedSourceStrategyId);
    }

    public List<ResearchConfig> listAll() {
        return list(null);
    }

    /**
     * 归档研究配置。
     * Why:
     * archive 是显式生命周期命令，不是删除。归档后默认列表隐藏该配置，
     * 但详情和历史回测 / 评估 / 发布追溯仍必须可读取。重复归档保持幂等，
     * 不覆盖首次归档时间、操作者或原因。
     *
     * @param researchConfigId 研究配置 ID
     * @param archivedBy 归档操作者标识，空值归一为 `system`
     * @param archiveReason 归档原因，可空，不得包含密钥、token 等敏感信息
     * @return 归档后的研究配置详情
     */
    public ResearchConfig archive(String researchConfigId, String archivedBy, String archiveReason) {
        String normalizedId = requireText(researchConfigId, "researchConfigId");
        String normalizedActor = normalizeArchiveActor(archivedBy);
        String normalizedReason = normalizeArchiveReason(archiveReason);
        ResearchConfig current = getByResearchConfigId(normalizedId);
        if (current.isArchived()) {
            return current;
        }
        Instant now = Instant.now(clock);
        boolean updated = researchConfigRepository.archive(
                normalizedId,
                now,
                normalizedActor,
                normalizedReason
        );
        if (!updated) {
            ResearchConfig latest = getByResearchConfigId(normalizedId);
            if (latest.isArchived()) {
                return latest;
            }
            throw new IllegalStateException("research config archive failed: " + normalizedId);
        }
        return getByResearchConfigId(normalizedId);
    }

    private void validateCreateRequest(ResearchConfigCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireText(request.sourceStrategyId(), "sourceStrategyId");
        requireText(request.name(), "name");
        normalizeJson(request.parameterSchema());
        normalizeJson(request.parameterDefaults());
        normalizeJson(request.datasetSpec());
    }

    private String serializeStrategySnapshot(SourceStrategySnapshot sourceStrategySnapshot) {
        try {
            return objectMapper.writeValueAsString(sourceStrategySnapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize source strategy snapshot", ex);
        }
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        try {
            return objectMapper.readTree(value.trim()).toString();
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid json payload", ex);
        }
    }

    private String normalizeNullableText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeArchiveActor(String value) {
        String normalized = value == null || value.isBlank() ? "system" : value.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("archivedBy length must be less than or equal to 128");
        }
        return normalized;
    }

    private String normalizeArchiveReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 1024) {
            throw new IllegalArgumentException("archiveReason length must be less than or equal to 1024");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("token")
                || lower.contains("api secret")
                || lower.contains("api_secret")
                || lower.contains("private key")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.contains("mnemonic")
                || normalized.contains("私钥")
                || normalized.contains("密钥")
                || normalized.contains("助记词")) {
            throw new IllegalArgumentException("archiveReason must not contain sensitive credential material");
        }
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}



