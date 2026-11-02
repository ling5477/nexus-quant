package com.guidinglight.nexusquant.scheduler.api.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobControl;

/** 保留原控制响应的所有字段，附加代码目录的间隔约束；约束不是另一份数据库控制事实。 */
public record ScheduledJobResponse(
        @JsonUnwrapped ScheduledJobControl control, long minFixedDelayMs, long maxFixedDelayMs) {}
