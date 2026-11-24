package com.suwon.festival.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.suwon.festival.repository.CaptionAiResultRepository;
import com.suwon.festival.service.caption.CachedCaptionAiClient;
import com.suwon.festival.service.caption.CaptionAiClient;

// 캡션 AI 연결 조건 — 키 없음 · 공백 · 스위치 끔이면 빈이 없어 지금처럼 PENDING, 키가 있으면 보관 · 상한이 붙은 쪽 하나만
class CaptionAiConfigTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(CaptionAiConfig.class)
      .withBean(CaptionAiResultRepository.class, () -> mock(CaptionAiResultRepository.class));

  @Test
  void 키가_없거나_공백이면_연결_안_함() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(CaptionAiClient.class));
    runner.withPropertyValues("gemini.api-key=").run(ctx -> assertThat(ctx).doesNotHaveBean(CaptionAiClient.class));
    runner.withPropertyValues("gemini.api-key=   ").run(ctx -> assertThat(ctx).doesNotHaveBean(CaptionAiClient.class));
  }

  @Test
  void 스위치를_끄면_키가_있어도_연결_안_함() {
    runner.withPropertyValues("gemini.api-key=test-key", "caption.ai.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(CaptionAiClient.class));
  }

  @Test
  void 키가_있으면_보관_상한이_붙은_쪽_하나만() {
    runner.withPropertyValues("gemini.api-key=test-key").run(ctx -> {
      assertThat(ctx).hasSingleBean(CaptionAiClient.class);
      assertThat(ctx.getBean(CaptionAiClient.class)).isInstanceOf(CachedCaptionAiClient.class);
    });
  }
}
