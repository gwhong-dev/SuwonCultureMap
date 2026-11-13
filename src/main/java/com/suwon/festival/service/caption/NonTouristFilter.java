package com.suwon.festival.service.caption;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// 관광 대상이 아닌 행사 거르기 (인스타 캡션 전용 — 재단(SWCF) 행사는 CaptionParser 를 거치지 않음)
// 시청 계정은 정책 포럼 · 기념식 · 기업 박람회도 같은 🗓️ · 📍 형식으로 올림 → 날짜 · 장소가 멀쩡해도 지도에 올리지 않음
//  - 제목 낱말 (늘 거름): 기념식 · 포럼 · 토론회 … · 기업 · 구직자 대상(융합대전 · 상담회 · 채용박람회 …)
//  - 제목 낱말 (약함): 개관 · N주년 · 정책 → 문화 낱말(전시 · 공연 · 축제 …)이 같이 있으면 남김 ("개관 10주년 특별전")
//  - 장소 낱말: 회의실
//  - "박람회 · 엑스포 · 페스타 · 쇼" 자체는 거르지 않음 (커피 · 애견용품 박람회처럼 일반인이 구경 · 구매하는 행사)
// 결정 기록: docs/decisions/2026-11-13-instagram-non-tourist.md
public final class NonTouristFilter {

  private NonTouristFilter() {
  }

  public static final String REASON_PREFIX = "관광 대상 아님: ";

  // 공백을 지우고 소문자로 비교 ("채용 박람회" = "채용박람회", "B2B" = "b2b")
  private static final List<String> TITLE_WORDS = List.of(
      "기념식", "포럼", "토론회", "세미나", "간담회", "설명회", "협약식", "발대식", "출범식", "위원회",
      "융합대전", "상담회", "채용박람회", "일자리박람회", "취업박람회", "b2b", "바이어");
  private static final List<String> WEAK_TITLE_WORDS = List.of("개관", "정책");
  private static final Pattern ANNIVERSARY = Pattern.compile("\\d+주년");
  private static final List<String> CULTURE_WORDS = List.of(
      "전시", "특별전", "공연", "축제", "콘서트", "연주", "음악회", "뮤지컬", "연극", "페스타", "페스티벌", "festival",
      "체험", "마켓");
  private static final List<String> PLACE_WORDS = List.of("회의실");

  // 걸리면 "관광 대상 아님: <낱말>", 아니면 null
  public static String reason(String title, String place) {
    String t = squash(title);
    for (String w : TITLE_WORDS) {
      if (t.contains(w)) return REASON_PREFIX + w;
    }
    String p = squash(place);
    for (String w : PLACE_WORDS) {
      if (p.contains(w)) return REASON_PREFIX + w;
    }
    if (CULTURE_WORDS.stream().anyMatch(t::contains)) return null;
    for (String w : WEAK_TITLE_WORDS) {
      if (t.contains(w)) return REASON_PREFIX + w;
    }
    var m = ANNIVERSARY.matcher(t);
    if (m.find()) return REASON_PREFIX + m.group();
    return null;
  }

  public static boolean isNonTourist(String reason) {
    return reason != null && reason.startsWith(REASON_PREFIX);
  }

  private static String squash(String s) {
    return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }
}
