package com.suwon.festival.service.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

// 제목 · 장소명 정리 + 글자쌍 유사도 (설계 3번 정리 규칙)
class MergeTextTest {

  @Test
  void 괄호_공백_대소문자가_달라도_같게_정리() {
    assertThat(MergeText.normalize("<2026 수원 국악 한마당>")).isEqualTo("2026수원국악한마당");
    assertThat(MergeText.normalize("2026 수원국악한마당")).isEqualTo("2026수원국악한마당");
    assertThat(MergeText.normalize("〈가을〉《밤》「음악」[회](Live)")).isEqualTo("가을밤음악회live");
  }

  @Test
  void 따옴표_이모지_해시태그_제거() {
    assertThat(MergeText.normalize("🎶 \"가을 음악회\" ✨ #수원 #공연")).isEqualTo("가을음악회");
    assertThat(MergeText.normalize("‘가을’ “음악회”")).isEqualTo("가을음악회");
  }

  @Test
  void HTML_표기는_풀어서_비교() {
    assertThat(MergeText.normalize("수원&#39;s 가을")).isEqualTo(MergeText.normalize("수원's 가을"));
    assertThat(MergeText.normalize("A&amp;B &#x27;콘서트&#x27;")).isEqualTo("ab콘서트");
  }

  @Test
  void 두_번_인코딩된_표기는_한_번만_풀고_결과가_항상_같음() {
    assertThat(MergeText.unescapeHtml("&amp;lt;공연&amp;gt;")).isEqualTo("&lt;공연&gt;");
    assertThat(MergeText.normalize("&amp;lt;공연&amp;gt;")).isEqualTo("lt공연gt");
    assertThat(MergeText.unescapeHtml("&amp;#39;")).isEqualTo("&#39;");
  }

  @Test
  void 단어_중간_샵은_해시태그가_아님() {
    assertThat(MergeText.normalize("제#2회 음악회")).isEqualTo("제2회음악회");
    assertThat(MergeText.normalize("#수원 가을 음악회")).isEqualTo("가을음악회");
  }

  @Test
  void null_은_빈_문자열() {
    assertThat(MergeText.normalize(null)).isEmpty();
  }

  @Test
  void 글자쌍_유사도() {
    assertThat(MergeText.dice("가을음악회", "가을음악회")).isEqualTo(1.0);
    assertThat(MergeText.dice("가을음악회", "겨울빛축제")).isEqualTo(0.0);
    // 가을밤음악회(5쌍) ↔ 가을밤의음악회(6쌍), 겹침 4 → 8/11
    assertThat(MergeText.dice("가을밤음악회", "가을밤의음악회")).isCloseTo(8.0 / 11, within(1e-9));
    // 1글자는 같을 때만
    assertThat(MergeText.dice("가", "가")).isEqualTo(1.0);
    assertThat(MergeText.dice("가", "가나")).isEqualTo(0.0);
  }
}
