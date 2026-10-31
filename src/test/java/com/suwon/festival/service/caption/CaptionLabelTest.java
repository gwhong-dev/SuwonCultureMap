package com.suwon.festival.service.caption;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// 라벨 줄 인식 — 실측(10/31, 시설 계정)에서 장소 앞 라벨이 안 떼져 좌표를 못 찾은 모양 포함
class CaptionLabelTest {

  @Test
  void 공연장소_세로선_라벨을_뗀다() {
    String line = "📍 공연장소 | 수원SK아트리움 대공연장";
    assertThat(CaptionLabel.of(line)).isEqualTo(CaptionLabel.PLACE);
    assertThat(CaptionLabel.content(line)).isEqualTo("수원SK아트리움 대공연장");
  }

  @Test
  void 전시장소_콜론_라벨을_뗀다() {
    String line = "📍 전시장소 : 수원전통문화관 기획전시실 진수원";
    assertThat(CaptionLabel.content(line)).isEqualTo("수원전통문화관 기획전시실 진수원");
  }

  @Test
  void 이모지_없는_붙은_라벨도_종류를_안다() {
    assertThat(CaptionLabel.of("공연장소 | 수원SK아트리움")).isEqualTo(CaptionLabel.PLACE);
    assertThat(CaptionLabel.of("전시기간 : 2026.10.04 ~ 11.01")).isEqualTo(CaptionLabel.DATE);
    assertThat(CaptionLabel.of("운영시간｜10:00~18:00")).isEqualTo(CaptionLabel.TIME);
    assertThat(CaptionLabel.of("관람료: 무료")).isEqualTo(CaptionLabel.OTHER);
  }

  @Test
  void 기존_라벨은_그대로() {
    assertThat(CaptionLabel.content("장소: 화성행궁")).isEqualTo("화성행궁");
    assertThat(CaptionLabel.content("📍 화성행궁")).isEqualTo("화성행궁");
  }

  @Test
  void 구분자_없는_문장은_라벨이_아니다() {
    assertThat(CaptionLabel.of("공연장소가 바뀌었습니다")).isNull();
    assertThat(CaptionLabel.of("전시 소개")).isNull();
  }
}
