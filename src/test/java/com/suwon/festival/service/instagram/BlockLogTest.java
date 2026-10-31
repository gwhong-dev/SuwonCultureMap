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

  static final String KEY = "0123456789abcdef0123456789abcdef";

  @Test
  void 정리된_블록은_제목_날짜_장소_이유_키가_한_줄에() {
    var e = new ParsedEvent(true, "가을 음악회", "공연", LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 7), null, "가상 광장");
    var r = new BlockResult(new CaptionBlock("가을 음악회", List.of(), "본문 전체 원문 문장"), Source.RULE,
        CandidateStatus.PARSED, e, null, List.of());

    String line = BlockLog.line("1790000000000001", KEY, r, InstagramBlock.Status.NO_COORD, "수원 안 검색 결과 없음");

    assertThat(line).contains("NO_COORD", "1790000000000001", "키 01234567", "가을 음악회", "2026-11-05~2026-11-07",
        "가상 광장", "수원 안 검색 결과 없음");
    assertThat(line).doesNotContain("본문 전체 원문", "89abcdef0123"); // 본문 X, 키는 앞 8자만
    assertThat(line).doesNotContain("\n");
  }

  @Test
  void 제목_줄이_없는_못_읽은_블록은_빈칸_대신_키로_찾음() {
    // 날짜 라벨 묶음이 없는 블록은 분리기가 제목을 정하지 않음 (title = null)
    var r = new BlockResult(new CaptionBlock(null, List.of(), "가상 안내\n세부 내용"), Source.NONE,
        CandidateStatus.PENDING, null, "AI 미연결 (라벨 줄 없음)", List.of());
    String line = BlockLog.line("1", KEY, r, InstagramBlock.Status.PENDING, "AI 미연결 (라벨 줄 없음)");
    assertThat(line).contains("PENDING", "키 01234567", "제목 -", "날짜 -", "장소 -");
    assertThat(line).doesNotContain("가상 안내", "세부 내용");
  }

  @Test
  void 줄바꿈_제어문자_유니코드_줄구분_방향제어는_공백으로() {
    assertThat(BlockLog.clean("가\n나\r\n다\t라", 40)).isEqualTo("가 나 다 라");
    assertThat(BlockLog.clean("가 나\u0085다‮라", 40)).isEqualTo("가 나 다 라");
    assertThat(BlockLog.clean(null, 40)).isEqualTo("-");
    assertThat(BlockLog.clean("  ", 40)).isEqualTo("-");
  }

  @Test
  void 긴_글은_자르고_이모지는_반으로_안_자름() {
    assertThat(BlockLog.clean("가".repeat(100), BlockLog.TITLE_MAX)).hasSize(BlockLog.TITLE_MAX + 1).endsWith("…");
    // 39글자 + 이모지(2칸) → 40칸째가 이모지 앞쪽 반이면 이모지를 통째로 뺌
    String cut = BlockLog.clean("가".repeat(39) + "🎵끝", BlockLog.TITLE_MAX);
    assertThat(cut).isEqualTo("가".repeat(39) + "…");
  }
}
