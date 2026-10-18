package com.guidinglight.nexusquant.research.application.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.research.domain.ResearchResultPage;
import com.guidinglight.nexusquant.research.domain.eval.port.EvaluationListQuery;
import com.guidinglight.nexusquant.research.domain.port.PublishListQuery;

import org.junit.jupiter.api.Test;

import java.util.List;

/** 筛选及窗口边界锁定原业务含义；排序、权限与MVCC另由真实PG/Servlet验证。 */
class ResultListPagingQueryTest {
    @Test
    void preservesLiteralRunAndSourceFiltersAndNormalizesOnlyActualStatusValues() {
        var evaluation =
                new EvaluationListQuery(
                        " parent ", " config ", " Run_%! ", " succeeded ", 100, Integer.MAX_VALUE);
        assertEquals("parent", evaluation.researchConfigId());
        assertEquals("Run_%!", evaluation.backtestRunId());
        assertEquals("SUCCEEDED", evaluation.evaluationStatus());
        var publish =
                new PublishListQuery(
                        " R_%! ", null, " Source_%! ", " Exact-Version ", " failed ", 100, 0);
        assertEquals("R_%!", publish.researchConfigId());
        assertEquals("Source_%!", publish.sourceStrategyId());
        assertEquals("Exact-Version", publish.strategyVersionId());
        assertEquals("FAILED", publish.publishStatus());
        assertNull(new EvaluationListQuery(" ", null, null, "", 1, 0).researchConfigId());
    }

    @Test
    void rejectsUnboundedWindowsUnknownStatusesOversizedFieldsAndRawControlCharacters() {
        for (int limit : new int[] {-1, 0, 101}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EvaluationListQuery(null, null, null, null, limit, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PublishListQuery(null, null, null, null, null, limit, 0));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationListQuery(null, null, null, null, 10, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PublishListQuery(null, null, null, null, null, 10, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationListQuery(null, null, null, "RUNNING", 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PublishListQuery(null, null, null, null, "PENDING", 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationListQuery("x".repeat(129), null, null, null, 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PublishListQuery(null, null, null, "x".repeat(129), null, 10, 0));
        for (String control : List.of("\n", "\t", "value\u0000")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new EvaluationListQuery(null, null, control, null, 10, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PublishListQuery(null, null, control, null, null, 10, 0));
        }
    }

    @Test
    void incompletePageCannotMasqueradeAsACompleteResultWindow() {
        assertEquals(521, new ResearchResultPage<>(List.of(), 521, 10, 530).total());
        assertEquals(
                List.of("last"), new ResearchResultPage<>(List.of("last"), 521, 10, 520).items());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchResultPage<>(List.of(), 521, 10, 520));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchResultPage<>(List.of("a", "b"), 1, 10, 0));
    }
}
