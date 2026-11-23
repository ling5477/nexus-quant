package com.guidinglight.nexusquant.marketdata.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.application.exception.PriceEventSourceConflictException;
import com.guidinglight.nexusquant.marketdata.domain.PriceEventSourceRecord;
import com.guidinglight.nexusquant.marketdata.domain.port.PriceEventSourceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/** 接入来源记录只写独立元数据；价格、原始供应响应、可见时间与冻结研究事实保持原owner。 */
@Service
public class PriceEventSourceService {
    private final PriceEventSourceRepository repository;
    private final PriceEventSourceCodec codec;

    public PriceEventSourceService(PriceEventSourceRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.codec = new PriceEventSourceCodec(mapper);
    }

    /** 来源ID重放与同一bar版本复用均按规范化内容确认；并发不同记录拒绝覆盖。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public PriceEventSourceRecord submit(
            long actor,
            UUID sourceId,
            long barId,
            String expectedSha,
            String sourceName,
            String documentJson) {
        if (actor <= 0
                || sourceId == null
                || barId <= 0
                || expectedSha == null
                || !expectedSha.matches("[a-f0-9]{64}")
                || sourceName == null
                || sourceName.isBlank()
                || sourceName.length() > 128
                || sourceName.contains("/")
                || sourceName.contains("\\"))
            throw new IllegalArgumentException("INVALID_PRICE_EVENT_SOURCE_IDENTITY");
        var bar =
                repository
                        .findBar(barId, true)
                        .orElseThrow(
                                () -> new IllegalArgumentException("PRICE_EVENT_BAR_NOT_FOUND"));
        if (!"OK".equals(bar.bar().qualityStatus()))
            throw new IllegalArgumentException("PRICE_EVENT_BAR_QUALITY_INCOMPLETE");
        if (bar.bar().closeTime().isAfter(Instant.now()))
            throw new IllegalArgumentException("PRICE_EVENT_BAR_NOT_CLOSED");
        if (!codec.barSha256(bar).equals(expectedSha))
            throw new PriceEventSourceConflictException();
        var parsed = codec.parse(documentJson, bar);
        if (parsed.closePriceAt().isAfter(Instant.now()))
            throw new IllegalArgumentException("INVALID_PRICE_EVENT_SOURCE_DOCUMENT");
        var record =
                new PriceEventSourceRecord(
                        sourceId,
                        barId,
                        expectedSha,
                        sourceName,
                        parsed.documentSha256(),
                        parsed.documentJson(),
                        codec.barSnapshot(bar),
                        parsed.openPriceAt(),
                        parsed.highPriceAt(),
                        parsed.lowPriceAt(),
                        parsed.closePriceAt(),
                        actor,
                        Instant.now().truncatedTo(ChronoUnit.MICROS));
        var prior = repository.findById(sourceId);
        if (prior.isPresent()) return compatible(prior.get(), record, true);
        if (repository.insertIfAbsent(record)) return record;
        // READ_COMMITTED下冲突等待结束后重新读取赢家；只读收敛，不执行覆盖性重试。
        var winner = repository.findById(sourceId);
        return winner.isPresent()
                ? compatible(winner.get(), record, true)
                : compatible(
                        repository
                                .findForBar(barId, expectedSha)
                                .orElseThrow(PriceEventSourceConflictException::new),
                        record,
                        false);
    }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<PriceEventSourceRecord> read(UUID sourceId) {
        return repository.findById(sourceId);
    }

    /** 查询未知保存结果不写入；同版已有等价来源可确认复用，404不能证明在途请求不会稍后提交。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public Verification verify(
            long actor,
            UUID id,
            long barId,
            String expectedSha,
            String sourceName,
            String documentJson) {
        var exact = repository.findById(id);
        var found = exact.isPresent() ? exact : repository.findForBar(barId, expectedSha);
        if (found.isEmpty()) return new Verification("NOT_FOUND", null);
        var row = found.get();
        if (row.barId() != barId
                || !row.barIdentitySha256().equals(expectedSha)
                || !row.sourceName().equals(sourceName)
                || (exact.isPresent() && row.createdByUserId() != actor)
                || !codec.matches(documentJson, row)) return new Verification("CONFLICT", null);
        return new Verification("MATCH_FOUND", row);
    }

    public record Verification(String status, PriceEventSourceRecord source) {}

    private PriceEventSourceRecord compatible(
            PriceEventSourceRecord existing, PriceEventSourceRecord request, boolean exactId) {
        if (existing.barId() != request.barId()
                || !existing.barIdentitySha256().equals(request.barIdentitySha256())
                || !existing.documentSha256().equals(request.documentSha256())
                || !existing.sourceName().equals(request.sourceName())
                || (exactId && existing.createdByUserId() != request.createdByUserId()))
            throw new PriceEventSourceConflictException();
        return existing;
    }
}
