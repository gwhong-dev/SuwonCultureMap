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

  // --- 작품명 제목 (단일 행사 글: 라벨 위 줄이 소개 문장일 때) ---

  static final String FACILITY = String.join("\n",
      "[가상아트홀] 〈가상의 밤〉 기획공연",
      "",
      "공연도 즐기고 가을밤도 즐기는 시간!",
      "🗓️ 2026. 11. 5.(목) 19:30",
      "📍 장소: 가상 아트홀");

  @Test
  void 단일_행사_글은_머리말_작품명을_제목으로() {
    List<CaptionBlock> blocks = CaptionBlockSplitter.split(FACILITY);
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).title()).isEqualTo("가상의 밤");
    // 블록 원문은 그대로 (원래 제목 줄부터)
    assertThat(blocks.get(0).text()).startsWith("공연도 즐기고");
  }

  @Test
  void 꺾쇠_세_종류_모두_인식하고_대괄호는_작품명이_아님() {
    assertThat(CaptionBlockSplitter.workNames("<가상 A> 〈가상 B〉 《가상 C》 [가상홀]"))
        .containsExactly("가상 A", "가상 B", "가상 C");
    assertThat(CaptionBlockSplitter.workNames("<- 링크 클릭 / <a>")).isEmpty(); // 짝 없음 · 1글자
  }

  @Test
  void 제목_줄에_이미_작품명이_있으면_그대로() {
    String c = "〈머리말 이름〉\n<가상 작품> 전시\n🗓️ 2026. 11. 5.(목)\n📍 가상 홀";
    assertThat(CaptionBlockSplitter.split(c).get(0).title()).isEqualTo("<가상 작품> 전시");
  }

  @Test
  void 머리말_작품명이_두_종류면_제목_줄_유지() {
    String c = "<가상 A> 와 <가상 B>\n함께 즐겨요\n🗓️ 2026. 11. 5.(목)\n📍 가상 홀";
    assertThat(CaptionBlockSplitter.split(c).get(0).title()).isEqualTo("함께 즐겨요");
  }

  @Test
  void 같은_작품명이_여러_번_나오면_한_종류로_봄() {
    String c = "<가상 A>\n#<가상 A>\n함께 즐겨요\n🗓️ 2026. 11. 5.(목)\n📍 가상 홀";
    assertThat(CaptionBlockSplitter.split(c).get(0).title()).isEqualTo("가상 A");
  }

  @Test
  void 모음_글은_머리말_작품명을_쓰지_않음() {
    String c = "<가상 11월 모음>\n\n가을 음악회\n🗓️ 2026. 11. 5.(목)\n📍 가상 홀\n\n겨울 축제\n🗓️ 2026. 12. 1.(화)\n📍 가상 광장";
    List<CaptionBlock> blocks = CaptionBlockSplitter.split(c);
    assertThat(blocks.get(0).title()).isEqualTo("가을 음악회");
    assertThat(blocks.get(1).title()).isEqualTo("겨울 축제");
  }

  @Test
  void HTML_태그_화살표_링크_안내는_작품명이_아님() {
    assertThat(CaptionBlockSplitter.workNames("<br> </b> <a href=x> <- 아래 참고 -> <<프로필 링크>> <http://x>")).isEmpty();
    // 영문 · 숫자 섞인 작품명은 인정 (HTML 태그 이름만 거름)
    assertThat(CaptionBlockSplitter.workNames("<가상 2026> 《Jazz 가상밤》 <Blue Night>"))
        .containsExactly("가상 2026", "Jazz 가상밤", "Blue Night");
  }
}
