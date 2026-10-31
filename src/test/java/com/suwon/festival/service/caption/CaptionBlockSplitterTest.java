package com.suwon.festival.service.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

// 행사 블록 나누기 — 가상 캡션 (실제 캡션 원문은 저장소에 넣지 않음, 설계 문서 1번)
class CaptionBlockSplitterTest {

  static final String ROUNDUP = String.join("\n",
      "가상시 11월 행사 모음 🎉",
      "",
      "가을 음악회",
      "🗓️ 2026. 11. 5.(목) ~ 11. 7.(토) 19:00~21:00",
      "📍 장소: 가상 아트홀",
      "",
      "겨울 빛 축제",
      "🗓 2026. 12. 28.(월) ~ 1. 3.(일)",
      "📍 가상 광장",
      "설명 한 줄",
      "",
      "#가상 #해시태그");

  @Test
  void 모음_글은_날짜_라벨_묶음마다_블록_하나() {
    List<CaptionBlock> blocks = CaptionBlockSplitter.split(ROUNDUP);
    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0).title()).isEqualTo("가을 음악회");
    assertThat(blocks.get(1).title()).isEqualTo("겨울 빛 축제");
    assertThat(blocks.get(0).labelLines()).hasSize(2);
  }

  @Test
  void 블록_원문은_제목부터_다음_제목_직전까지_머리말은_빠짐() {
    List<CaptionBlock> blocks = CaptionBlockSplitter.split(ROUNDUP);
    assertThat(blocks.get(0).text()).startsWith("가을 음악회").doesNotContain("행사 모음").doesNotContain("겨울");
    assertThat(blocks.get(1).text()).contains("설명 한 줄");
    // 다른 블록의 날짜가 섞이지 않아야 원문 대조가 블록 단위로 동작
    assertThat(blocks.get(1).text()).doesNotContain("11. 5.");
  }

  @Test
  void 변형_문자_있는_이모지와_없는_이모지를_같게_봄() {
    // 🗓️(U+1F5D3 U+FE0F) / 🗓(U+1F5D3)
    assertThat(CaptionLabel.of(CaptionText.normalize("🗓️ 2026. 11. 5."))).isEqualTo(CaptionLabel.DATE);
    assertThat(CaptionLabel.of("🗓 2026. 11. 5.")).isEqualTo(CaptionLabel.DATE);
  }

  @Test
  void 날짜_라벨이_없으면_캡션_전체가_블록_하나() {
    List<CaptionBlock> blocks = CaptionBlockSplitter.split("가상 여행지 소개\n📍 가상 해변\n#여행");
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).labelLines()).isEmpty(); // 📍만 있고 날짜가 없음 → 규칙 몫 아님
    assertThat(blocks.get(0).title()).isNull();
  }

  @Test
  void 빈_캡션은_블록_없음() {
    assertThat(CaptionBlockSplitter.split(null)).isEmpty();
    assertThat(CaptionBlockSplitter.split("  \n ")).isEmpty();
  }

  @Test
  void 글자_라벨도_인식하고_내용만_꺼냄() {
    assertThat(CaptionLabel.of("일시: 2026. 11. 5.")).isEqualTo(CaptionLabel.DATE);
    assertThat(CaptionLabel.of("장소 : 가상 홀")).isEqualTo(CaptionLabel.PLACE);
    assertThat(CaptionLabel.content("📍 장소: 가상 아트홀")).isEqualTo("가상 아트홀");
    assertThat(CaptionLabel.of("그냥 문장")).isNull();
  }
}
