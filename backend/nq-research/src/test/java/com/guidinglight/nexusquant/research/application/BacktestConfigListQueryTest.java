package com.guidinglight.nexusquant.research.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.research.domain.BacktestConfigPage;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigListQuery;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigListQueryPort;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 窗口与父对象失败合同在单测中锁定；权限、金额和SQL分页由真实Servlet/PG证明。 */
class BacktestConfigListQueryTest {
    @Test
    void rejectsInvalidWindowsAndRawControlCharactersBeforeTrimming() {
        var query =
                new BacktestConfigListQuery(
                        " parent ", " ID_%! ", " name ", 100, Integer.MAX_VALUE);
        assertEquals("parent", query.researchConfigId());
        assertEquals("ID_%!", query.backtestConfigId());
        assertEquals("name", query.name());
        assertNull(new BacktestConfigListQuery(" ", null, "", 1, 0).researchConfigId());
        for (int limit : new int[] {0, -1, 101}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BacktestConfigListQuery(null, null, null, limit, 0));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new BacktestConfigListQuery(null, null, null, 10, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BacktestConfigListQuery("x".repeat(129), null, null, 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BacktestConfigListQuery(null, "x".repeat(129), null, 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BacktestConfigListQuery(null, null, "x".repeat(256), 10, 0));
        for (String control : List.of("\n", "\t", "text\u0000")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BacktestConfigListQuery(control, null, null, 10, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BacktestConfigListQuery(null, control, null, 10, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BacktestConfigListQuery(null, null, control, 10, 0));
        }
    }

    @Test
    void rejectsMissingRowsInsideReportedTotalButAllowsAnEmptyTail() {
        assertEquals(501, new BacktestConfigPage(List.of(), 501, 10, 501).total());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BacktestConfigPage(List.of(), 501, 10, 500));
    }

    @Test
    void missingParentRemains404AndNeverRunsAFullTableFallback() {
        var queryPort = mock(BacktestConfigListQueryPort.class);
        var parents = mock(ResearchConfigService.class);
        when(parents.getByResearchConfigId("missing"))
                .thenThrow(new IllegalArgumentException("research config not found"));
        var service = new BacktestConfigListService(queryPort, parents);
        var error =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.list(
                                        new BacktestConfigListQuery("missing", null, null, 10, 0)));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(queryPort);
    }

    @Test
    void queryFailureIsNotConvertedInto404OrAnEmptyPage() {
        var queryPort = mock(BacktestConfigListQueryPort.class);
        var parents = mock(ResearchConfigService.class);
        var query = new BacktestConfigListQuery(null, null, null, 10, 0);
        var original = new IllegalStateException("database read failed");
        when(queryPort.readPage(query)).thenThrow(original);
        var service = new BacktestConfigListService(queryPort, parents);
        assertSame(original, assertThrows(IllegalStateException.class, () -> service.list(query)));
    }
}
