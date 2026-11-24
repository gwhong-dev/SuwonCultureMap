package com.suwon.festival.service.caption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.suwon.festival.entity.CaptionAiResult;
import com.suwon.festival.repository.CaptionAiResultRepository;

// 캡션 AI 앞단 — 결과 보관 · 하루 상한. 저장소는 메모리 지도로 흉내 (DB 없이)
class CachedCaptionAiClientTest {

  private final Map<String, CaptionAiResult> store = new HashMap<>();
  private CaptionAiResultRepository repository;
  private MutableClock clock;

  // 정해 둔 답을 돌려주고 부른 횟수를 셈. failing 이면 예외
  static class CountingAi implements CaptionAiClient {
    int calls;
    boolean failing;
    Optional<ParsedEvent> answer = Optional.empty();

    @Override
    public Optional<ParsedEvent> parse(String blockText) {
      calls++;
      if (failing) throw new GeminiCaptionClient.GeminiException("Gemini HTTP 429");
      return answer;
    }
  }

  static class MutableClock extends Clock {
    Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @BeforeEach
  void setUp() {
    repository = mock(CaptionAiResultRepository.class);
    when(repository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0, String.class))));
    when(repository.save(any(CaptionAiResult.class))).thenAnswer(i -> {
      CaptionAiResult r = i.getArgument(0);
      store.put(r.getTextHash(), r);
      return r;
    });
    clock = new MutableClock(Instant.parse("2026-11-24T03:00:00Z")); // 한국 12시
  }

  private CachedCaptionAiClient cached(CaptionAiClient ai, int limit) {
    return new CachedCaptionAiClient(ai, "gemini-test", repository, clock, limit);
  }

  @Test
  void 같은_원문은_AI를_한_번만_부르고_공백_차이는_같은_글() {
    CountingAi ai = new CountingAi();
    ai.answer = Optional.of(new ParsedEvent(true, "가을 음악회", "공연", LocalDate.of(2026, 11, 5), null, "19:00", "가상 아트홀"));
    CachedCaptionAiClient client = cached(ai, 10);

    Optional<ParsedEvent> first = client.parse("가을 음악회\n2026. 11. 5.");
    Optional<ParsedEvent> again = client.parse("  가을 음악회   2026. 11. 5. ");

    assertThat(ai.calls).isEqualTo(1);
    assertThat(again).isEqualTo(first);
    assertThat(store.values()).singleElement().satisfies(r -> {
      assertThat(r.getOutcome()).isEqualTo(CaptionAiResult.Outcome.EVENT);
      assertThat(r.getModel()).isEqualTo("gemini-test");
      assertThat(r.getTextHash()).hasSize(32);
    });
  }

  @Test
  void 행사_아님과_못_읽음도_보관해_다시_안_부름() {
    CountingAi notEvent = new CountingAi();
    notEvent.answer = Optional.of(new ParsedEvent(false, "무시할 값", null, null, null, null, null));
    CachedCaptionAiClient a = cached(notEvent, 10);
    assertThat(a.parse("공지 글")).get().satisfies(e -> {
      assertThat(e.isEvent()).isFalse();
      assertThat(e.title()).isNull(); // 행사 아님이면 읽은 값은 남기지 않음
    });
    a.parse("공지 글");
    assertThat(notEvent.calls).isEqualTo(1);

    CountingAi unreadable = new CountingAi();
    CachedCaptionAiClient b = cached(unreadable, 10);
    assertThat(b.parse("알 수 없는 글")).isEmpty();
    assertThat(b.parse("알 수 없는 글")).isEmpty();
    assertThat(unreadable.calls).isEqualTo(1);
  }

  @Test
  void 호출_실패는_보관하지_않아_다음에_다시_부름() {
    CountingAi ai = new CountingAi();
    ai.failing = true;
    CachedCaptionAiClient client = cached(ai, 10);

    assertThatThrownBy(() -> client.parse("가을 음악회")).isInstanceOf(GeminiCaptionClient.GeminiException.class);
    assertThat(store).isEmpty();

    ai.failing = false;
    client.parse("가을 음악회");
    assertThat(ai.calls).isEqualTo(2);
  }

  @Test
  void 하루_상한을_넘으면_예외_한국_날짜가_바뀌면_다시() {
    CountingAi ai = new CountingAi();
    CachedCaptionAiClient client = cached(ai, 2);

    client.parse("글 1");
    client.parse("글 2");
    assertThatThrownBy(() -> client.parse("글 3")).isInstanceOf(CachedCaptionAiClient.LimitReachedException.class);
    client.parse("글 1"); // 보관된 글은 상한과 상관없음
    assertThat(ai.calls).isEqualTo(2);

    clock.now = Instant.parse("2026-11-24T15:00:00Z"); // 한국 11/25 0시
    client.parse("글 3");
    assertThat(ai.calls).isEqualTo(3);
  }

  @Test
  void 칸_길이를_넘는_값은_잘라_보관() {
    CountingAi ai = new CountingAi();
    ai.answer = Optional.of(new ParsedEvent(true, "가".repeat(400), "공연", LocalDate.of(2026, 11, 5), null, null, null));
    cached(ai, 10).parse("긴 제목 글");
    assertThat(store.values()).singleElement()
        .satisfies(r -> assertThat(r.getTitle()).hasSize(CaptionAiResult.TITLE_MAX));
  }
}
