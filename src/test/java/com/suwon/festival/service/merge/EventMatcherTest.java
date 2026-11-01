package com.suwon.festival.service.merge;

import static com.suwon.festival.service.merge.MergeFixtures.LAT;
import static com.suwon.festival.service.merge.MergeFixtures.LNG;
import static com.suwon.festival.service.merge.MergeFixtures.at;
import static com.suwon.festival.service.merge.MergeFixtures.insta;
import static com.suwon.festival.service.merge.MergeFixtures.swcf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 같은 행사 규칙 (설계 docs/event-merge.md 3번 · 6번)
class EventMatcherTest {

  @Nested
  class 날짜 {

    @Test
    void 기간이_겹치면_같음() {
      assertThat(EventMatcher.datesOverlap(
          swcf(1, "t", "2026-11-01", "2026-11-30"), insta(2, "t", "2026-11-15", null, false))).isTrue();
    }

    @Test
    void 마지막_날이_닿기만_해도_겹침() {
      assertThat(EventMatcher.datesOverlap(
          swcf(1, "t", "2026-11-01", "2026-11-05"), insta(2, "t", "2026-11-05", "2026-11-09", false))).isTrue();
    }

    @Test
    void 하루_차이로_안_겹치면_다름() {
      assertThat(EventMatcher.datesOverlap(
          swcf(1, "t", "2026-11-01", "2026-11-05"), insta(2, "t", "2026-11-06", null, false))).isFalse();
    }

    @Test
    void 종료일이_없으면_시작일_하루() {
      assertThat(EventMatcher.datesOverlap(
          swcf(1, "t", "2026-11-05", null), insta(2, "t", "2026-11-05", null, false))).isTrue();
      assertThat(EventMatcher.datesOverlap(
          swcf(1, "t", "2026-11-05", null), insta(2, "t", "2026-11-06", null, false))).isFalse();
    }

    @Test
    void 시작일이_없으면_다름() {
      assertThat(EventMatcher.datesOverlap(
          swcf(1, "t", null, "2026-11-05"), insta(2, "t", "2026-11-05", null, false))).isFalse();
      assertThat(EventMatcher.datesOverlap(
          swcf(1, "t", "2026-11-05", null), insta(2, "t", null, null, false))).isFalse();
    }

    @Test
    void 해_넘김_기간도_겹침() {
      assertThat(EventMatcher.datesOverlap(
          swcf(1, "t", "2026-12-28", "2027-01-03"), insta(2, "t", "2027-01-01", null, false))).isTrue();
    }

    @Test
    void 종료일이_시작일보다_앞이면_시작일_하루() {
      assertThat(EventMatcher.datesOverlap(
          swcf(1, "t", "2026-11-05", "2026-11-01"), insta(2, "t", "2026-11-03", null, false))).isFalse();
    }
  }

  @Nested
  class 장소 {

    // 위도 1도 ≈ 111.2km → 0.0026도 ≈ 289m, 0.0028도 ≈ 311m
    @Test
    void 거리_300m_경계() {
      assertThat(EventMatcher.distanceMeters(LAT, LNG, LAT + 0.0026, LNG)).isCloseTo(289, within(2.0));
      var a = at(swcf(1, "t", "2026-11-05", null), "가", LAT, LNG);
      assertThat(EventMatcher.samePlace(a, at(insta(2, "t", "2026-11-05", null, false), "나", LAT + 0.0026, LNG)))
          .isTrue();
      assertThat(EventMatcher.samePlace(a, at(insta(2, "t", "2026-11-05", null, false), "나", LAT + 0.0028, LNG)))
          .isFalse();
    }

    @Test
    void 멀어도_정리한_장소명이_같으면_같음() {
      var a = at(swcf(1, "t", "2026-11-05", null), "가상 아트홀", LAT, LNG);
      var b = at(insta(2, "t", "2026-11-05", null, false), "<가상아트홀>", LAT + 0.01, LNG);
      assertThat(EventMatcher.samePlace(a, b)).isTrue();
    }

    @Test
    void 장소명이_둘_다_비고_멀면_다름() {
      var a = at(swcf(1, "t", "2026-11-05", null), null, LAT, LNG);
      var b = at(insta(2, "t", "2026-11-05", null, false), " ", LAT + 0.01, LNG);
      assertThat(EventMatcher.samePlace(a, b)).isFalse();
    }

    @Test
    void 좌표가_없으면_장소명으로만() {
      var a = at(swcf(1, "t", "2026-11-05", null), "가상 아트홀", null, null);
      assertThat(EventMatcher.samePlace(a, at(insta(2, "t", "2026-11-05", null, false), "가상아트홀", LAT, LNG)))
          .isTrue();
      assertThat(EventMatcher.samePlace(a, at(insta(2, "t", "2026-11-05", null, false), "다른 홀", LAT, LNG)))
          .isFalse();
    }
  }

  @Nested
  class 제목 {

    @Test
    void 정리하면_같은_제목() {
      assertThat(EventMatcher.similarTitle("2026 수원국악한마당", "<2026 수원 국악 한마당>")).isTrue();
      assertThat(EventMatcher.similarTitle("가을 음악회 🎶 #수원", "\"가을음악회\"")).isTrue();
    }

    @Test
    void 포함은_짧은_쪽_4글자부터() {
      assertThat(EventMatcher.similarTitle("수원국악", "2026 수원국악 페스티벌 본공연")).isTrue();  // 4글자
      assertThat(EventMatcher.similarTitle("국악제", "2026 수원국악제 페스티벌 본공연")).isFalse(); // 3글자
    }

    @Test
    void 유사도_0_6_경계() {
      // 가을밤음악회 ↔ 가을밤의음악회 = 8/11 ≈ 0.73 → 같음
      assertThat(EventMatcher.similarTitle("가을밤 음악회", "가을밤의 음악회")).isTrue();
      // 수원국악한마당 ↔ 수원국악축제 = 6/11 ≈ 0.55 → 다름 (포함도 아님)
      assertThat(EventMatcher.similarTitle("수원국악한마당", "수원국악축제")).isFalse();
    }

    @Test
    void 앞부분만_같고_나머지가_다르면_다름() {
      // 시설명 · 연도가 앞에 붙으면 전체 유사도가 부풀려짐 (실데이터: 유사도 0.67 인 서로 다른 재단 행사)
      assertThat(EventMatcher.similarTitle("정조테마공연장 2026 작은 음악회", "정조테마공연장 2026 마당놀이터")).isFalse();
      // 뒤 한 글자 차이는 오타 → 같음
      assertThat(EventMatcher.similarTitle("수원국악한마당축제", "수원국악한마당축졔")).isTrue();
    }

    @Test
    void 숫자만_남는_제목은_다른_제목을_포함으로_잡지_않음() {
      assertThat(EventMatcher.similarTitle("2026", "2026 화성행궁 야간개장")).isFalse();
      assertThat(EventMatcher.similarTitle("2026 #뷰티썸수원", "2026 화성행궁 야간개장")).isFalse();
      assertThat(EventMatcher.similarTitle("2026 #뷰티썸수원", "2026 뷰티썸 수원")).isTrue();
    }

    @Test
    void 빈_제목은_다름() {
      assertThat(EventMatcher.similarTitle("", "")).isFalse();
      assertThat(EventMatcher.similarTitle(null, "가을 음악회")).isFalse();
      assertThat(EventMatcher.similarTitle("🎶 #수원", "🎶 #수원")).isFalse();
    }
  }

  @Nested
  class 종합 {

    @Test
    void 재단과_인스타_같은_공연은_같은_행사() {
      assertThat(EventMatcher.isSameEvent(
          swcf(1, "2026 수원국악한마당", "2026-11-05", "2026-11-07"),
          insta(2, "<2026 수원 국악 한마당>", "2026-11-06", null, false))).isTrue();
    }

    // 설계 3번 "합치지 않는 경우" 3종
    @Test
    void 같은_장소_같은_날_다른_공연은_합치지_않음() {
      assertThat(EventMatcher.isSameEvent(
          swcf(1, "가을 음악회", "2026-11-05", null),
          insta(2, "겨울 빛 축제", "2026-11-05", null, false))).isFalse();
    }

    @Test
    void 같은_제목_다른_회차_날짜는_합치지_않음() {
      assertThat(EventMatcher.isSameEvent(
          insta(1, "가을 음악회", "2026-11-05", null, false),
          insta(2, "가을 음악회", "2026-11-12", null, false))).isFalse();
    }

    @Test
    void 같은_제목_같은_날_다른_장소는_합치지_않음() {
      var a = at(swcf(1, "가을 음악회", "2026-11-05", null), "가상 아트홀", LAT, LNG);
      var b = at(insta(2, "가을 음악회", "2026-11-05", null, false), "가상 광장", LAT + 0.02, LNG);
      assertThat(EventMatcher.isSameEvent(a, b)).isFalse();
    }
  }
}
