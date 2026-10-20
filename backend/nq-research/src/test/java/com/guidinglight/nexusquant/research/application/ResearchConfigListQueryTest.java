package com.guidinglight.nexusquant.research.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.research.domain.ResearchConfigPage;
import com.guidinglight.nexusquant.research.domain.port.ResearchConfigListQuery;

import org.junit.jupiter.api.Test;

import java.util.List;

/** 查询合同的边界不依赖HTTP；SQL筛选、同刻排序和真实分页由PG/Servlet证明。 */
class ResearchConfigListQueryTest {
    @Test
    void normalizesFiltersWithoutInterpretingWildcards() {
        var query =
                new ResearchConfigListQuery(" source ", "id_%!", "  %_!  ", 100, Integer.MAX_VALUE);
        assertEquals("source", query.sourceStrategyId());
        assertEquals("id_%!", query.researchConfigId());
        assertEquals("%_!", query.name());
        assertNull(new ResearchConfigListQuery(" ", null, "", 1, 0).name());
    }

    @Test
    void rejectsUnboundedWindowsAndInvalidFilterLengthsOrControlCharacters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchConfigListQuery(null, null, null, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchConfigListQuery(null, null, null, 101, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchConfigListQuery(null, null, null, 10, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchConfigListQuery("x".repeat(129), null, null, 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchConfigListQuery(null, null, "x".repeat(256), 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchConfigListQuery(null, "a\u0000b", null, 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchConfigListQuery(null, null, "\n", 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchConfigListQuery(null, "a\u0000", null, 10, 0));
    }

    @Test
    void emptyPageCannotMasqueradeAsMissingRowsInsideTheReportedTotal() {
        assertEquals(50, new ResearchConfigPage(List.of(), 50, 10, 50).total());
        assertThrows(
                IllegalArgumentException.class, () -> new ResearchConfigPage(List.of(), 50, 10, 0));
    }
}
