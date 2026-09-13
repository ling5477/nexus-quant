package com.guidinglight.nexusquant.app.smoke;

/** 模式只提供阶段标签与截止时刻；所有模式共用同一采集实现。 */
interface L6SamplingSchedule {
    long total();
    String samplePhase(long elapsedNanos);
}
