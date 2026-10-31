package com.suwon.festival.service.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.caption.CaptionParser.BlockResult;
import com.suwon.festival.service.caption.CaptionParser.Source;

// 캡션 정리기 전체 흐름 — 가상 캡션 + 가짜 AI
class CaptionParserTest {

  // 받은 블록 원문을 기록하고, 정해 둔 답을 돌려주는 가짜 AI
  static class FakeAi implements CaptionAiClient {
    final List<String> received = new ArrayList<>();
    final ParsedEvent answer;

    FakeAi(ParsedEvent answer) {
      this.answer = answer;
    }

    @Override
    public Optional<ParsedEvent> parse(String blockText) {
      received.add(blockText);
      return Optional.ofNullable(answer);
    }
  }

  static final String ROUNDUP = String.join("\n",
      "가상시 11월 행사 모음",
      "",
      "가을 음악회",
      "🗓️ 2026. 11. 5.(목) ~ 11. 7.(토) 19:00~21:00",
      "📍 장소: 가상 아트홀",
      "",
      "겨울 빛 축제",
      "🗓 2026. 12. 28.(월) ~ 1. 3.(일)",
      "📍 가상 광장",
      "",
      "주말 장터",
      "🗓️ 매주 금~일요일",
      "⏰ 10:00~18:00",
      "📍 가상 공원",
      "",
      "요일 틀린 전시",
      "🗓️ 2026. 11. 5.(금)",
      "📍 가상 미술관");

  @Test
  void 모음_글은_블록마다_규칙으로_정리하고_AI는_부르지_않음() {
    FakeAi ai = new FakeAi(null);
    List<BlockResult> rs = new CaptionParser(ai).parse(ROUNDUP);

    assertThat(rs).hasSize(4);
    assertThat(rs).allMatch(r -> r.source() == Source.RULE);
    assertThat(ai.received).isEmpty();

    BlockResult concert = rs.get(0);
    assertThat(concert.status()).isEqualTo(CandidateStatus.PARSED);
    assertThat(concert.event().title()).isEqualTo("가을 음악회");
    assertThat(concert.event().category()).isEqualTo("공연");
    assertThat(concert.event().placeName()).isEqualTo("가상 아트홀");
    assertThat(concert.event().timeText()).isEqualTo("19:00~21:00");

    BlockResult winter = rs.get(1);
    assertThat(winter.status()).isEqualTo(CandidateStatus.PARSED);
    assertThat(winter.event().category()).isEqualTo("축제");
    assertThat(winter.event().endDate()).isEqualTo(LocalDate.of(2027, 1, 3));

    BlockResult market = rs.get(2);
    assertThat(market.status()).isEqualTo(CandidateStatus.NO_DATE);
    assertThat(market.reason()).contains("반복");
    assertThat(market.event().timeText()).contains("10:00~18:00");

    BlockResult wrongDay = rs.get(3);
    assertThat(wrongDay.status()).isEqualTo(CandidateStatus.FAILED);
    assertThat(wrongDay.reason()).contains("요일");
  }

  @Test
  void 날짜_줄이_있는_공지는_규칙이_판단을_미루고_AI_미연결이면_PENDING() {
    String notice = "가상 버스 운행 안내\n🗓️ 2026. 11. 3.(화)\n자세한 내용은 홈페이지";
    List<BlockResult> rs = new CaptionParser(null).parse(notice);
    assertThat(rs).hasSize(1);
    assertThat(rs.get(0).status()).isEqualTo(CandidateStatus.PENDING);
    assertThat(rs.get(0).source()).isEqualTo(Source.NONE);
  }

  @Test
  void 공지를_AI가_행사_아님으로_판단하면_SKIPPED() {
    String notice = "가상 버스 운행 안내\n🗓️ 2026. 11. 3.(화)";
    FakeAi ai = new FakeAi(new ParsedEvent(false, null, null, null, null, null, null));
    List<BlockResult> rs = new CaptionParser(ai).parse(notice);
    assertThat(rs.get(0).status()).isEqualTo(CandidateStatus.SKIPPED);
    assertThat(rs.get(0).source()).isEqualTo(Source.AI);
  }

  @Test
  void AI에는_캡션_전체가_아니라_블록_원문만_넘김() {
    String caption = String.join("\n",
        "가상 모음",
        "",
        "가을 음악회",
        "🗓️ 2026. 11. 5.(목)",
        "📍 가상 아트홀",
        "",
        "가상 도로 통제 안내",
        "🗓️ 2026. 11. 9.(월)");
    FakeAi ai = new FakeAi(null);
    new CaptionParser(ai).parse(caption);
    assertThat(ai.received).hasSize(1);
    assertThat(ai.received.get(0)).contains("통제").doesNotContain("가을 음악회");
  }

  @Test
  void 줄글_캡션은_AI가_정리하고_결과는_다시_검사() {
    String prose = "가상 공원에서 2026년 11월 21일 가을 마켓이 열려요!";
    FakeAi ok = new FakeAi(new ParsedEvent(true, "가을 마켓", "행사", LocalDate.of(2026, 11, 21), null, null, "가상 공원"));
    BlockResult r = new CaptionParser(ok).parse(prose).get(0);
    assertThat(r.source()).isEqualTo(Source.AI);
    assertThat(r.status()).isEqualTo(CandidateStatus.PARSED);
    assertThat(r.event().endDate()).isEqualTo(LocalDate.of(2026, 11, 21));

    // AI 가 원문에 없는 날짜를 지어냄 → FAILED
    FakeAi lying = new FakeAi(new ParsedEvent(true, "가을 마켓", "행사", LocalDate.of(2026, 11, 22), null, null, null));
    assertThat(new CaptionParser(lying).parse(prose).get(0).status()).isEqualTo(CandidateStatus.FAILED);
  }

  @Test
  void AI도_못_읽으면_FAILED_호출_실패는_다음에_다시_PENDING() {
    String prose = "그냥 소개 글";
    assertThat(new CaptionParser(new FakeAi(null)).parse(prose).get(0).status()).isEqualTo(CandidateStatus.FAILED);

    CaptionAiClient broken = t -> {
      throw new IllegalStateException("network");
    };
    BlockResult r = new CaptionParser(broken).parse(prose).get(0);
    assertThat(r.status()).isEqualTo(CandidateStatus.PENDING);
    assertThat(r.reason()).doesNotContain("network"); // 예외 메시지(토큰 등이 섞일 수 있음)는 결과에 남기지 않음
  }

  @Test
  void 규칙이_틀렸다고_본_날짜는_AI에_넘기지_않음() {
    String caption = "요일 틀린 전시\n🗓️ 2026. 11. 5.(금)\n📍 가상 미술관";
    FakeAi ai = new FakeAi(new ParsedEvent(true, "요일 틀린 전시", "전시", LocalDate.of(2026, 11, 5), null, null, null));
    BlockResult r = new CaptionParser(ai).parse(caption).get(0);
    assertThat(r.status()).isEqualTo(CandidateStatus.FAILED);
    assertThat(ai.received).isEmpty();
  }

  @Test
  void 한_블록에_날짜_줄이_여러_개면_규칙이_고르지_않음() {
    FakeAi ai = new FakeAi(null);
    CaptionParser parser = new CaptionParser(ai);
    // 신청 기간 + 행사일
    String apply = "가을 음악회\n🗓️ 신청: 2026. 10. 1. ~ 10. 20.\n🗓️ 행사: 2026. 11. 5.(목)\n📍 가상 아트홀";
    // 회차별 날짜
    String rounds = "가을 음악회\n🗓️ 1회차 2026. 11. 5.(목)\n🗓️ 2회차 2026. 11. 12.(목)\n📍 가상 아트홀";
    // 빈 줄 없이 붙은 두 행사
    String glued = "가을 음악회\n🗓️ 2026. 11. 5.(목)\n📍 가상 아트홀\n🗓️ 2026. 11. 7.(토)\n📍 가상 광장";
    for (String c : List.of(apply, rounds, glued)) {
      List<BlockResult> rs = parser.parse(c);
      assertThat(rs).hasSize(1);
      assertThat(rs.get(0).source()).isEqualTo(Source.AI);
      assertThat(rs.get(0).status()).isNotEqualTo(CandidateStatus.PARSED);
    }
    assertThat(ai.received).hasSize(3);
  }

  @Test
  void 본문의_통제_낱말이나_해시태그_공지는_행사를_미루지_않음() {
    String c = "겨울 빛 축제\n🗓️ 2026. 11. 5.(목)\n📍 가상 광장\n행사 당일 주변 도로 교통 통제\n#공지";
    BlockResult r = new CaptionParser(null).parse(c).get(0);
    assertThat(r.source()).isEqualTo(Source.RULE);
    assertThat(r.status()).isEqualTo(CandidateStatus.PARSED);
  }

  @Test
  void 연도는_있는데_모양이_다른_날짜는_AI에_넘김() {
    FakeAi ai = new FakeAi(null);
    new CaptionParser(ai).parse("가을 음악회\n🗓️ 2026년 11월 5일(목)\n📍 가상 아트홀");
    assertThat(ai.received).hasSize(1);
  }

  @Test
  void 제목_앞_이모지와_번호는_떼고_카테고리는_낱말로() {
    assertThat(RuleCaptionParser.cleanTitle("🎤 1. 가을 음악회")).isEqualTo("가을 음악회");
    assertThat(RuleCaptionParser.cleanTitle("<가상 작품> 전시")).isEqualTo("<가상 작품> 전시");
    assertThat(RuleCaptionParser.category("가상시 체육대회")).isEqualTo("스포츠");
    assertThat(RuleCaptionParser.category("가상 기념식")).isEqualTo("행사");
  }

  @Test
  void 작품명_제목이어도_카테고리는_원래_제목_줄까지_봄() {
    String c = "〈가상의 밤〉\n공연도 즐기고 가을밤도 즐기는 시간!\n🗓️ 2026. 11. 5.(목) 19:30\n📍 장소: 가상 아트홀";
    BlockResult r = new CaptionParser(null).parse(c).get(0);
    assertThat(r.status()).isEqualTo(CandidateStatus.PARSED);
    assertThat(r.event().title()).isEqualTo("가상의 밤");
    assertThat(r.event().category()).isEqualTo("공연");
  }

  @Test
  void 작품명_제목이어도_원래_제목_줄의_공지_낱말은_걸러냄() {
    String c = "<가상 공연>\n공연 일정 변경 안내\n🗓️ 2026. 11. 5.(목)\n📍 가상 아트홀";
    BlockResult r = new CaptionParser(null).parse(c).get(0);
    assertThat(r.status()).isEqualTo(CandidateStatus.PENDING);
  }
}
