package com.suwon.festival.service.caption;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 관광 대상이 아닌 행사 거르기 (인스타 캡션 전용 — 재단(SWCF) 행사는 CaptionParser 를 거치지 않음)
// 시청 계정은 정책 포럼 · 기념식 · 기업 박람회도 같은 🗓️ · 📍 형식으로 올림 → 날짜 · 장소가 멀쩡해도 지도에 올리지 않음
//  - 제목 낱말 (늘 거름): 기념식 · 포럼 · 토론회 … · 기업 · 구직자 대상(융합대전 · 상담회 · 채용박람회 …)
//  - 제목 낱말 (약함): 개관 · N주년 · 정책 → 문화 낱말(전시 · 공연 · 축제 · 투어 …)이 같이 있으면 남김
//    ("개관 10주년 특별전" · "수원화성 축성 230주년 기념 야간 투어")
//  - 장소 낱말: 회의실
//  - "박람회 · 엑스포 · 페스타 · 쇼" 자체는 거르지 않음 (커피 · 애견용품 박람회처럼 일반인이 구경 · 구매하는 행사)
//  - 낱말은 띄어쓰기를 살린 제목에서 찾음 → "기념 식물" · "공개 관람" · "우정 책방" 이 기념식 · 개관 · 정책으로 걸리지 않음
//    붙여도 띄어도 쓰는 복합어(채용 박람회)만 공백을 지우고 찾음
// 결정 기록: docs/decisions/2026-11-13-instagram-non-tourist.md
public final class NonTouristFilter {

  private NonTouristFilter() {
  }

  public static final String REASON_PREFIX = "관광 대상 아님: ";

  private static final List<String> TITLE_WORDS = List.of(
      "기념식", "포럼", "토론회", "세미나", "간담회", "설명회", "공청회", "심포지엄", "컨퍼런스", "총회",
      "협약식", "발대식", "출범식", "선포식", "시상식", "준공식", "개소식", "제막식", "위원회",
      "융합대전", "상담회", "b2b", "바이어");
  private static final List<String> COMPOUND_WORDS = List.of("채용박람회", "일자리박람회", "취업박람회");
  private static final List<String> WEAK_TITLE_WORDS = List.of("개관", "정책");
  private static final Pattern ANNIVERSARY = Pattern.compile("\\d+\\s*주년");
  // 약한 낱말을 풀어 주는 쪽이라 공백을 지우고 넓게 찾음
  private static final List<String> CULTURE_WORDS = List.of(
      "전시", "특별전", "공연", "축제", "콘서트", "연주", "음악회", "뮤지컬", "연극", "페스타", "페스티벌", "festival",
      "체험", "마켓", "투어", "재현", "문화제", "야간개장", "퍼레이드", "행렬", "불꽃", "관람", "대회");
  private static final List<String> PLACE_WORDS = List.of("회의실");

  // 걸리면 "관광 대상 아님: <낱말>", 아니면 null
  public static String reason(String title, String place) {
    String t = spaced(title);
    String squashed = t.replace(" ", "");
    for (String w : TITLE_WORDS) {
      if (t.contains(w)) return REASON_PREFIX + w;
    }
    for (String w : COMPOUND_WORDS) {
      if (squashed.contains(w)) return REASON_PREFIX + w;
    }
    String p = spaced(place);
    for (String w : PLACE_WORDS) {
      if (p.contains(w)) return REASON_PREFIX + w;
    }
    if (CULTURE_WORDS.stream().anyMatch(squashed::contains)) return null;
    for (String w : WEAK_TITLE_WORDS) {
      if (t.contains(w)) return REASON_PREFIX + w;
    }
    Matcher m = ANNIVERSARY.matcher(t);
    if (m.find()) return REASON_PREFIX + m.group().replace(" ", "");
    return null;
  }

  public static boolean isNonTourist(String reason) {
    return reason != null && reason.startsWith(REASON_PREFIX);
  }

  // 공백 묶음은 한 칸으로, 소문자로 ("B2B" = "b2b")
  private static String spaced(String s) {
    return s == null ? "" : s.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }
}
