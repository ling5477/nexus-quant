package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.ResearchHistoricalInput;
import com.guidinglight.nexusquant.marketdata.domain.port.ResearchDatasetVersionPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 不可变长研究版本只保存一次规范内容；原配置与Run持有紧凑、可复核的引用。 */
@Repository
public class JdbcResearchDatasetVersionPort implements ResearchDatasetVersionPort {
    private static final int MAX_CHUNKS =
            (ResearchHistoricalInput.MAX_BARS + ResearchHistoricalInput.CHUNK_BARS - 1)
                    / ResearchHistoricalInput.CHUNK_BARS;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcResearchDatasetVersionPort(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(5);
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true, timeout = 10)
    public Optional<JsonNode> find(UUID datasetId) {
        TenantIdentity owner = owner();
        return jdbc.query("""
                SELECT reference_json FROM research_dataset_versions
                WHERE tenant_id=? AND owner_user_id=? AND dataset_id=? LIMIT 1
                """, (row, index) -> parse(row.getString(1)),
                owner.tenantId().value(), owner.userId(), datasetId).stream().findFirst();
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 30)
    public JsonNode freeze(JsonNode dataset, List<HistoricalBar> source, Instant frozenAt) {
        TenantIdentity owner = owner();
        // 页读取和发布必须同一可重复读事务；加入较弱外层事务时拒绝混合多个数据版本。
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !Integer.valueOf(Connection.TRANSACTION_REPEATABLE_READ).equals(
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new IllegalStateException("RESEARCH_FREEZE_REQUIRES_REPEATABLE_READ");
        UUID datasetId = UUID.fromString(dataset.path("datasetId").asText());
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                "research-dataset:" + owner.tenantId() + ":" + owner.userId() + ":" + datasetId);
        var existing = find(datasetId);
        if (existing.isPresent()) return existing.get();
        long rawBytes = 0;
        for (HistoricalBar bar : source) {
            rawBytes += bar.rawPayloadJson() == null ? 0 : bytes(bar.rawPayloadJson());
            if (rawBytes > ResearchHistoricalInput.MAX_TOTAL_BYTES)
                throw new IllegalStateException("RESEARCH_INPUT_BYTE_LIMIT_EXCEEDED");
        }
        ObjectNode full = ResearchHistoricalInput.freeze(dataset, source, frozenAt, mapper);
        ArrayNode rows = (ArrayNode) full.path("bars");
        ArrayNode observed = (ArrayNode) full.path("sourceObservedAt");
        UUID versionId = UUID.randomUUID();
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("version", ResearchHistoricalInput.VERSION);
        manifest.put("datasetVersionId", versionId.toString());
        manifest.put("tenantId", owner.tenantId().toString());
        manifest.put("ownerUserId", owner.userId());
        manifest.set("dataset", datasetIdentity(dataset));
        manifest.put("barCount", rows.size());
        manifest.put("sha256", full.path("sha256").asText());
        manifest.put("sourceSha256", full.path("sourceSha256").asText());
        manifest.put("frozenAt", frozenAt.toString());
        manifest.put("source", full.path("source").asText());
        manifest.put("replayVisibilityVersion", full.path("replayVisibilityVersion").asText());
        manifest.put("replayVisibilitySource", full.path("replayVisibilitySource").asText());
        ArrayNode descriptors = manifest.putArray("chunks");
        List<Chunk> chunks = new ArrayList<>();
        int totalBytes = 0;
        for (int start = 0, ordinal = 0; start < rows.size(); start += ResearchHistoricalInput.CHUNK_BARS, ordinal++) {
            int end = Math.min(rows.size(), start + ResearchHistoricalInput.CHUNK_BARS);
            ObjectNode body = mapper.createObjectNode();
            body.put("ordinal", ordinal);
            body.put("startBar", start);
            body.put("barCount", end - start);
            ArrayNode bars = body.putArray("bars"), times = body.putArray("sourceObservedAt");
            for (int index = start; index < end; index++) {
                bars.add(rows.get(index)); times.add(observed.get(index));
            }
            String canonical = body.toString();
            int size = bytes(canonical);
            if (size > ResearchHistoricalInput.MAX_CHUNK_BYTES)
                throw new IllegalStateException("RESEARCH_CHUNK_BYTE_LIMIT_EXCEEDED");
            totalBytes += size;
            if (totalBytes > ResearchHistoricalInput.MAX_TOTAL_BYTES)
                throw new IllegalStateException("RESEARCH_INPUT_BYTE_LIMIT_EXCEEDED");
            String checksum = sha256(canonical);
            descriptors.addObject().put("ordinal", ordinal).put("startBar", start)
                    .put("barCount", end - start).put("bytes", size).put("sha256", checksum);
            chunks.add(new Chunk(ordinal, start, end - start, size, checksum, canonical));
        }
        manifest.put("totalBytes", totalBytes);
        String manifestJson = manifest.toString();
        if (bytes(manifestJson) > 65_536) throw new IllegalStateException("RESEARCH_MANIFEST_LIMIT_EXCEEDED");
        ObjectNode reference = full.deepCopy();
        reference.remove(List.of("bars", "sourceObservedAt"));
        reference.put("datasetVersionId", versionId.toString());
        reference.put("manifestSha256", sha256(manifestJson));
        reference.put("barCount", rows.size());
        reference.put("chunkCount", chunks.size());
        reference.put("totalBytes", totalBytes);
        jdbc.update("""
                INSERT INTO research_dataset_versions(dataset_version_id,tenant_id,owner_user_id,dataset_id,
                  bar_count,chunk_count,total_bytes,content_sha256,source_sha256,manifest_sha256,
                  frozen_at,manifest_json,reference_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, versionId, owner.tenantId().value(), owner.userId(), datasetId, rows.size(), chunks.size(),
                totalBytes, full.path("sha256").asText(), full.path("sourceSha256").asText(),
                reference.path("manifestSha256").asText(), Timestamp.from(frozenAt), manifestJson, reference.toString());
        for (Chunk chunk : chunks) {
            jdbc.update("""
                    INSERT INTO research_dataset_chunks(dataset_version_id,ordinal,start_bar,bar_count,
                      byte_count,content_sha256,canonical_json) VALUES(?,?,?,?,?,?,?)
                    """, versionId, chunk.ordinal(), chunk.start(), chunk.count(), chunk.bytes(),
                    chunk.sha256(), chunk.canonical());
        }
        return reference;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public List<HistoricalBar> read(JsonNode dataset) {
        var tenant = TenantContext.requireTenantId();
        if (TenantContext.current().isEmpty() && TenantContext.execution().isEmpty())
            throw new IllegalStateException("RESEARCH_VERSION_SCOPE_REQUIRED");
        JsonNode reference = dataset.path("frozenInput");
        if (!ResearchHistoricalInput.referenced(dataset))
            throw new IllegalStateException("RESEARCH_VERSION_REQUIRED");
        UUID id = UUID.fromString(reference.path("datasetVersionId").asText());
        var headers = jdbc.query("""
                SELECT owner_user_id,manifest_json,reference_json,manifest_sha256,bar_count,chunk_count,total_bytes
                FROM research_dataset_versions WHERE dataset_version_id=? AND tenant_id=? LIMIT 1
                """, (row, index) -> new Header(row.getLong(1), row.getString(2), row.getString(3),
                row.getString(4), row.getInt(5), row.getInt(6), row.getInt(7)), id, tenant.value());
        if (headers.size() != 1) throw new IllegalStateException("RESEARCH_VERSION_NOT_FOUND");
        Header header = headers.getFirst();
        if (TenantContext.current().isPresent() && TenantContext.requireCurrent().userId() != header.owner())
            throw new IllegalStateException("RESEARCH_VERSION_NOT_FOUND");
        JsonNode manifest = parse(header.manifest());
        if (!reference.equals(parse(header.reference()))
                || !sha256(header.manifest()).equals(header.manifestSha256())
                || !header.manifestSha256().equals(reference.path("manifestSha256").asText())
                || !datasetIdentity(dataset).equals(manifest.path("dataset"))
                || !id.toString().equals(manifest.path("datasetVersionId").asText())
                || !tenant.toString().equals(manifest.path("tenantId").asText())
                || header.owner() != manifest.path("ownerUserId").asLong()
                || !dataset.path("snapshotAt").asText().equals(reference.path("frozenAt").asText())
                || header.count() != reference.path("barCount").asInt()
                || header.chunks() < 1 || header.chunks() > MAX_CHUNKS
                || header.count() < 721 || header.count() > ResearchHistoricalInput.MAX_BARS
                || header.bytes() > ResearchHistoricalInput.MAX_TOTAL_BYTES)
            throw new IllegalStateException("RESEARCH_VERSION_IDENTITY_MISMATCH");
        JsonNode descriptors = manifest.path("chunks");
        if (!descriptors.isArray() || descriptors.size() != header.chunks())
            throw new IllegalStateException("RESEARCH_CHUNK_COVERAGE_INVALID");
        var chunks = jdbc.query("""
                SELECT ordinal,start_bar,bar_count,byte_count,content_sha256,canonical_json
                FROM research_dataset_chunks WHERE dataset_version_id=? ORDER BY ordinal LIMIT ?
                """, (row, index) -> new Chunk(row.getInt(1), row.getInt(2), row.getInt(3), row.getInt(4),
                row.getString(5), row.getString(6)), id, MAX_CHUNKS + 1);
        if (chunks.size() != header.chunks()) throw new IllegalStateException("RESEARCH_CHUNK_COVERAGE_INVALID");
        ObjectNode full = mapper.createObjectNode();
        for (String field : List.of("version", "sha256", "sourceSha256", "frozenAt", "source",
                "replayVisibilityVersion", "replayVisibilitySource")) full.set(field, reference.path(field));
        ArrayNode bars = full.putArray("bars"), observations = full.putArray("sourceObservedAt");
        int count = 0, totalBytes = 0;
        for (int ordinal = 0; ordinal < chunks.size(); ordinal++) {
            Chunk chunk = chunks.get(ordinal);
            JsonNode expected = descriptors.get(ordinal), body = parse(chunk.canonical());
            if (chunk.ordinal() != ordinal || chunk.start() != count || chunk.count() < 1
                    || chunk.count() > ResearchHistoricalInput.CHUNK_BARS
                    || count + chunk.count() > ResearchHistoricalInput.MAX_BARS
                    || chunk.bytes() != bytes(chunk.canonical()) || chunk.bytes() > ResearchHistoricalInput.MAX_CHUNK_BYTES
                    || !sha256(chunk.canonical()).equals(chunk.sha256())
                    || expected.path("ordinal").asInt(-1) != ordinal || expected.path("startBar").asInt(-1) != count
                    || expected.path("barCount").asInt(-1) != chunk.count()
                    || expected.path("bytes").asInt(-1) != chunk.bytes()
                    || !expected.path("sha256").asText().equals(chunk.sha256())
                    || body.path("ordinal").asInt(-1) != ordinal || body.path("startBar").asInt(-1) != count
                    || body.path("barCount").asInt(-1) != chunk.count()
                    || !body.path("bars").isArray() || body.path("bars").size() != chunk.count()
                    || !body.path("sourceObservedAt").isArray() || body.path("sourceObservedAt").size() != chunk.count())
                throw new IllegalStateException("RESEARCH_CHUNK_IDENTITY_MISMATCH");
            totalBytes += chunk.bytes(); count += chunk.count();
            if (totalBytes > ResearchHistoricalInput.MAX_TOTAL_BYTES)
                throw new IllegalStateException("RESEARCH_INPUT_BYTE_LIMIT_EXCEEDED");
            body.path("bars").forEach(bars::add); body.path("sourceObservedAt").forEach(observations::add);
        }
        if (count != header.count() || totalBytes != header.bytes()
                || manifest.path("totalBytes").asInt(-1) != totalBytes)
            throw new IllegalStateException("RESEARCH_CHUNK_COVERAGE_INVALID");
        ObjectNode materialized = dataset.deepCopy();
        materialized.set("frozenInput", full);
        return ResearchHistoricalInput.readMaterialized(materialized, mapper);
    }

    private TenantIdentity owner() {
        TenantIdentity identity = TenantContext.requireCurrent();
        if (identity.kind() != TenantKind.LOCAL) throw new IllegalStateException("RESEARCH_VERSION_SCOPE_INVALID");
        return identity;
    }

    private ObjectNode datasetIdentity(JsonNode dataset) {
        ObjectNode identity = mapper.createObjectNode();
        for (String field : List.of("datasetId", "provider", "resourcePath", "exchangeCode", "marketType",
                "symbol", "interval", "startTime", "endTime", "status", "qualityStatus", "barCount", "gapCount", "source"))
            identity.set(field, dataset.path(field));
        return identity;
    }

    private JsonNode parse(String json) {
        try { return mapper.readTree(json); }
        catch (java.io.IOException error) { throw new IllegalStateException("RESEARCH_VERSION_JSON_INVALID", error); }
    }

    private static int bytes(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }

    private static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private record Chunk(int ordinal, int start, int count, int bytes, String sha256, String canonical) {}
    private record Header(long owner, String manifest, String reference, String manifestSha256,
                          int count, int chunks, int bytes) {}
}
