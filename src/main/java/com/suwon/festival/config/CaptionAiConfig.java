package com.suwon.festival.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.suwon.festival.repository.CaptionAiResultRepository;
import com.suwon.festival.service.caption.CachedCaptionAiClient;
import com.suwon.festival.service.caption.CaptionAiClient;
import com.suwon.festival.service.caption.GeminiCaptionClient;

// 캡션 AI 연결 (결정 docs/decisions/2026-11-24-caption-ai-gemini.md)
// - 키가 있고 스위치가 켜져 있을 때만 빈을 만듦. 없으면 InstagramPostProcessor 가 null 을 받아 지금처럼 "AI 미연결 (PENDING)"
// - 끄기: CAPTION_AI_ENABLED=false (키를 지우지 않아도 됨) — Meta 약관 문제 등으로 바로 멈춰야 할 때
// - 빈은 하나만 (보관 · 상한이 붙은 쪽). Gemini 호출부는 빈이 아님 → 다른 곳이 상한을 건너 부르지 못함
@Configuration
@ConditionalOnExpression("'${gemini.api-key:}'.trim() != '' and '${caption.ai.enabled:true}' == 'true'")
public class CaptionAiConfig {

  @Bean
  CaptionAiClient captionAiClient(@Value("${gemini.api-key}") String apiKey,
      @Value("${gemini.model:gemini-3.5-flash-lite}") String model,
      @Value("${caption.ai.daily-limit:200}") int dailyLimit,
      CaptionAiResultRepository repository) {
    GeminiCaptionClient gemini = new GeminiCaptionClient(apiKey, model);
    return new CachedCaptionAiClient(gemini, model, repository, Clock.systemUTC(), dailyLimit);
  }
}
