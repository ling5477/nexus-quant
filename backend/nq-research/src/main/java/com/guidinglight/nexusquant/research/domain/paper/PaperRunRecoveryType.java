package com.guidinglight.nexusquant.research.domain.paper;

public enum PaperRunRecoveryType {
    MANUAL_RECOVER,
    RETRY_FAILED_STEP,
    HEARTBEAT_LAG_RECOVER,
    SCHEDULE_FIRE_RECOVER
}
