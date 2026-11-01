package com.suwon.festival.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 자동 동기화를 켠 환경(운영)에서만 스케줄러를 사용
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.sync.enabled", havingValue = "true")
public class SyncScheduleConfig {
}
