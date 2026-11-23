package com.guidinglight.nexusquant.scheduler.control;

/** 应用组合层只能登记固定任务，调度、锁、超时和控制仍由唯一 Dispatcher 拥有。 */
public interface ScheduledJobContributor {
    ScheduledJobRegistry.Job job();
}
