package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.caption.CandidateStatus;
import com.suwon.festival.service.caption.CaptionBlock;
import com.suwon.festival.service.caption.CaptionParser.BlockResult;
import com.suwon.festival.service.caption.CaptionParser.Source;
import com.suwon.festival.service.caption.ParsedEvent;

// 블록 키: 글 안 순서가 아니라 값(제목 + 시작일 + 장소)으로
class BlockKeysTest {

  static BlockResult parsed(String title, LocalDate start, String place, String text) {
    return new BlockResult(new CaptionBlock(title, List.of(), text), Source.RULE, CandidateStatus.PARSED,
        new ParsedEvent(true, title, "공연", start, start, null, place), null, List.of());
  }

  static BlockResult unread(String text) {
    return new BlockResult(new CaptionBlock(null, List.of(), text), Source.NONE, CandidateStatus.PENDING,
        null, "AI 미연결", List.of());
  }

  @Test
  void 공백_차이는_같은_키_장소가_다르면_다른_키() {
    LocalDate d = LocalDate.of(2026, 11, 5);
    String a = BlockKeys.of(parsed("가을 음악회", d, "가상 아트홀", "원문 A"));
    String b = BlockKeys.of(parsed(" 가을  음악회 ", d, "가상 아트홀", "원문이 달라도"));
    String other = BlockKeys.of(parsed("가을 음악회", d, "가상 광장", "원문 A"));
    assertThat(a).isEqualTo(b).hasSize(BlockKeys.HASH_LENGTH);
    assertThat(a).isNotEqualTo(other);
  }

  @Test
  void 못_읽은_블록은_원문_해시_원문은_키에_안_남음() {
    String k = BlockKeys.of(unread("가상 안내 원문"));
    assertThat(k).hasSize(BlockKeys.HASH_LENGTH).doesNotContain("가상");
    assertThat(BlockKeys.of(unread("가상 안내 원문"))).isEqualTo(k);
  }

  @Test
  void 출처_키는_게시물_id_최대_길이여도_100자_이내() {
    String k = BlockKeys.sourceKey("9".repeat(64), BlockKeys.of(unread("x")));
    assertThat(k).hasSizeLessThanOrEqualTo(100);
  }
}
