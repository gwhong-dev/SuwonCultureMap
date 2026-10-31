package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.service.caption.CandidateStatus;
import com.suwon.festival.service.caption.CaptionBlock;
import com.suwon.festival.service.caption.CaptionParser.BlockResult;
import com.suwon.festival.service.caption.CaptionParser.Source;
import com.suwon.festival.service.caption.ParsedEvent;

// 블록 로그 한 줄: 필요한 값은 보이고, 줄바꿈 · 긴 글은 정리되고, 본문은 안 나감
class BlockLogTest {

  @Test
  void 정리된_블록은_제목_날짜_장소_이유가_한_줄에() {
    var e = new ParsedEvent(true, "가을 음악회", "공연", LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 7), null, "가상 광장");
    var r = new BlockResult(new CaptionBlock("가을 음악회", List.of(), "본문 전체 원문 문장"), Source.RULE,
        CandidateStatus.PARSED, e, null, List.of());

    String line = BlockLog.line("1790000000000001", r, InstagramBlock.Status.NO_COORD, "수원 안 검색 결과 없음");

    assertThat(line).contains("NO_COORD", "1790000000000001", "가을 음악회", "2026-11-05~2026-11-07", "가상 광장",
        "수원 안 검색 결과 없음");
    assertThat(line).doesNotContain("본문 전체 원문");
    assertThat(line).doesNotContain("\n");
  }

  @Test
  void 못_읽은_블록은_맨_윗줄을_제목으로_쓰고_날짜_장소는_빈칸() {
    var r = new BlockResult(new CaptionBlock("가상 안내", List.of(), "가상 안내\n세부 내용"), Source.NONE,
        CandidateStatus.PENDING, null, "AI 미연결", List.of());
    String line = BlockLog.line("1", r, InstagramBlock.Status.PENDING, "AI 미연결");
    assertThat(line).contains("PENDING", "가상 안내", "날짜 -", "장소 -");
  }

  @Test
  void 줄바꿈_제어문자는_공백으로_긴_글은_자름() {
    assertThat(BlockLog.clean("가\n나\r\n다\t라", 40)).isEqualTo("가 나 다 라");
    assertThat(BlockLog.clean("가".repeat(100), BlockLog.TITLE_MAX)).hasSize(BlockLog.TITLE_MAX + 1).endsWith("…");
    assertThat(BlockLog.clean(null, 40)).isEqualTo("-");
    assertThat(BlockLog.clean("  ", 40)).isEqualTo("-");
  }
}
