package com.suwon.festival.service.instagram;

import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.service.caption.CaptionParser.BlockResult;
import com.suwon.festival.service.caption.ParsedEvent;

// 블록 1개의 처리 결과를 로그 한 줄로 (동기화 때 "무엇이 왜 빠졌는지" 터미널에서 보려고)
// - 남기는 것: 상태 · 게시물 id · 제목 · 날짜 · 장소 · 이유 (추출한 값). 캡션 본문 · 토큰은 남기지 않음
// - 제목 · 장소 · 이유는 앞 일부만 자르고 줄바꿈 · 제어문자를 공백으로 바꿈 (로그 줄이 깨지거나 위조되는 것 방지)
// - 못 읽은 블록은 추출값이 없어 블록 맨 윗줄(제목 후보)을 씀
// - 소리를 줄이려면: logging.level.com.suwon.festival.instagram.blocks=WARN (application.properties)
final class BlockLog {

  // 로그 이름을 따로 둠 → 이 로그만 켜고 끌 수 있음
  static final String LOGGER_NAME = "com.suwon.festival.instagram.blocks";

  static final int TITLE_MAX = 40;
  static final int PLACE_MAX = 40;
  static final int REASON_MAX = 80;

  private BlockLog() {
  }

  static String line(String mediaId, BlockResult r, InstagramBlock.Status status, String reason) {
    ParsedEvent e = r.event();
    String title = e != null && e.title() != null ? e.title() : r.block().title();
    String place = e != null ? e.placeName() : null;
    String date = e != null && e.startDate() != null
        ? e.startDate() + (e.endDate() != null && !e.endDate().equals(e.startDate()) ? "~" + e.endDate() : "")
        : "-";
    return "블록 " + status + " | 게시물 " + mediaId + " | 제목 " + clean(title, TITLE_MAX) + " | 날짜 " + date
        + " | 장소 " + clean(place, PLACE_MAX) + " | " + clean(reason == null ? "" : reason, REASON_MAX);
  }

  // 줄바꿈 · 제어문자 → 공백, 앞 max 글자만
  static String clean(String s, int max) {
    if (s == null || s.isBlank()) return "-";
    String one = s.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
    return one.length() <= max ? one : one.substring(0, max) + "…";
  }
}
