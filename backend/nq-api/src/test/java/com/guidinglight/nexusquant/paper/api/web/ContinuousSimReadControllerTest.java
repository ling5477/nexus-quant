package com.guidinglight.nexusquant.paper.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimQueryService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** 关闭执行入口后仍读取原状态；命令合同由独立回执Controller验证。 */
class ContinuousSimReadControllerTest {
    @Test
    @SuppressWarnings("unchecked")
    void disabledExecutionStillReadsOriginalCursor() {
        ObjectProvider<ContinuousSimRunService> driver = mock(ObjectProvider.class);
        ContinuousSimQueryService queries = mock(ContinuousSimQueryService.class);
        var controller =
                new StrategySimRunController(
                        mock(StrategySimRunService.class),
                        mock(PaperTradingRunService.class),
                        driver,
                        queries);
        assertFalse(controller.continuousCapabilities().commandsAvailable());
        assertNull(controller.continuousStatus("paper"));
        verify(queries).find("paper");
        verifyNoMoreInteractions(queries);
    }
}
