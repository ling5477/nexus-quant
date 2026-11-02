package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.notification.application.UserNotificationService;
import com.guidinglight.nexusquant.notification.domain.port.UserNotificationReadPort;
import com.guidinglight.nexusquant.notification.infra.JdbcUserNotificationReadPort;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 装配现有SIM事件的只读通知投影；已读偏好由认证域保存，不在这里处理或关闭业务告警。
 */
@Configuration
public class UserNotificationConfiguration {
    @Bean
    public UserNotificationReadPort userNotificationReadPort(JdbcTemplate jdbc) {
        return new JdbcUserNotificationReadPort(jdbc);
    }

    @Bean
    public UserNotificationService userNotificationService(UserNotificationReadPort source) {
        return new UserNotificationService(source);
    }
}
