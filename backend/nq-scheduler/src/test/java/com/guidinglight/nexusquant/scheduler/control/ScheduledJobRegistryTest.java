package com.guidinglight.nexusquant.scheduler.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.scheduling.LedgerReconcileScheduler;
import com.guidinglight.nexusquant.scheduler.validationevidence.model.ValidationEvidenceRefreshResult;
import com.guidinglight.nexusquant.scheduler.validationevidence.scheduling.ValidationEvidenceScheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** 验证固定注册身份及业务非成功结果不会被记成成功。 */
class ScheduledJobRegistryTest {
    @Test
    void ledgerDifferenceIsReportedAsFailure() {
        LedgerReconcileScheduler ledger = mock(LedgerReconcileScheduler.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("ledger", ledger);
        ScheduledJobRegistry registry = registry(beans);

        when(ledger.reconcileOnce()).thenReturn(2, 0);
        assertEquals(new ScheduledJobRegistry.JobResult("FAILED", "LEDGER_RECONCILIATION_DIFFERENCE"),
                registry.require("LEDGER_RECONCILIATION").runOnce().get());
        assertEquals(ScheduledJobRegistry.JobResult.success(),
                registry.require("LEDGER_RECONCILIATION").runOnce().get());
    }

    @Test
    void validationFailureAndSkipRemainExplicit() {
        ValidationEvidenceScheduler validation = mock(ValidationEvidenceScheduler.class);
        ValidationEvidenceRefreshResult result = mock(ValidationEvidenceRefreshResult.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("validation", validation);
        ScheduledJobRegistry registry = registry(beans);
        when(validation.runOnce()).thenReturn(result);

        when(result.result()).thenReturn(ValidationEvidenceRefreshResult.Result.FAILED);
        assertEquals(new ScheduledJobRegistry.JobResult("FAILED", "VALIDATION_EVIDENCE_FAILED"),
                registry.require("VALIDATION_EVIDENCE_REFRESH").runOnce().get());
        when(result.result()).thenReturn(ValidationEvidenceRefreshResult.Result.DEGRADED);
        assertEquals(new ScheduledJobRegistry.JobResult("FAILED", "VALIDATION_EVIDENCE_DEGRADED"),
                registry.require("VALIDATION_EVIDENCE_REFRESH").runOnce().get());
        when(result.result()).thenReturn(ValidationEvidenceRefreshResult.Result.SKIPPED_LOCK_NOT_ACQUIRED);
        assertEquals(new ScheduledJobRegistry.JobResult("SKIPPED", "VALIDATION_EVIDENCE_SKIPPED_LOCK_NOT_ACQUIRED"),
                registry.require("VALIDATION_EVIDENCE_REFRESH").runOnce().get());
    }

    @Test
    void unknownAndDeferredJobsFailClosed() {
        ScheduledJobRegistry registry = registry(new StaticListableBeanFactory());
        assertEquals(8, registry.all().size());
        assertThrows(IllegalArgumentException.class, () -> registry.require("UNKNOWN_JOB"));
        assertEquals(false, registry.require("OKX_RECOVERY").eligible().getAsBoolean());
        assertEquals(false, registry.require("STRATEGY_RECOVERY").eligible().getAsBoolean());
        assertThrows(IllegalArgumentException.class,
                () -> registry.require("PAPER_MATCHING").validateDelay(400));
    }

    private static ScheduledJobRegistry registry(StaticListableBeanFactory beans) {
        return new ScheduledJobRegistry(
                beans.getBeanProvider(ContinuousSimRunService.class),
                beans.getBeanProvider(PaperMatchingService.class),
                beans.getBeanProvider(LedgerReconcileScheduler.class),
                beans.getBeanProvider(ValidationEvidenceScheduler.class));
    }
}
