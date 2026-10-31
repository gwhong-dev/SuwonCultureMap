package com.suwon.festival.service.caption;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// 규칙 기반 파서: 🗓️ · 📍 라벨 줄이 있는 블록을 읽는다 (설계 문서 5번 "규칙 기반 파서가 먼저")
//
// 결과 3가지
// - Confident : 규칙만으로 끝냄 (제목 + 날짜 줄 + 장소 줄, 공지 낱말 없음)
// - Invalid   : 날짜 줄이 틀림(없는 날짜 · 요일 불일치 등) → AI 에 넘기지 않고 FAILED
// - Unsure    : 규칙으로 판단 못 함 → AI 몫 (AI 미연결이면 PENDING)
public final class RuleCaptionParser {

  private RuleCaptionParser() {
  }

  public sealed interface Outcome permits Confident, Invalid, Unsure {
  }

  // note: 날짜를 못 읽은 이유 (NO_DATE 일 때만)
  public record Confident(ParsedEvent event, String note) implements Outcome {
  }

  public record Invalid(String reason) implements Outcome {
  }

  public record Unsure(String reason) implements Outcome {
  }

  // 날짜 줄이 있어도 행사가 아닌 공지가 있음(설계 문서 3-1 샘플 3 · 4) → 이런 낱말이 보이면 규칙은 판단을 미룸
  private static final List<String> NOTICE_WORDS = List.of(
      "운행", "파업", "통제", "휴관", "휴무", "운영 중단", "운영중단", "공지", "안내드립니다", "변경 안내", "모집 마감");

  // 제목 앞 번호: "1. " "① " (이모지 · ▶ ■ 같은 기호는 cleanTitle 의 반복문이 뗌)
  private static final Pattern TITLE_PREFIX = Pattern.compile("^(?:\\d{1,2}[.)]\\s+|[①-⑳]\\s*)");

  public static Outcome parse(CaptionBlock block) {
    if (block.labelLines().isEmpty()) return new Unsure("라벨 줄 없음");

    String dateLine = first(block, CaptionLabel.DATE);
    String placeLine = first(block, CaptionLabel.PLACE);
    String timeLine = first(block, CaptionLabel.TIME);

    // 날짜 줄이 2개 이상(신청 기간 + 행사일, 회차별, 빈 줄 없이 붙은 두 행사) → 규칙이 고르지 않음
    if (block.labelLines().stream().filter(l -> CaptionLabel.of(l) == CaptionLabel.DATE).count() > 1) {
      return new Unsure("날짜 줄이 여러 개");
    }
    String title = cleanTitle(block.title());
    if (title == null) return new Unsure("제목 줄 없음");
    // 원래 제목 줄 (머리말 작품명이 제목이 된 경우에도 공지 낱말 · 카테고리는 이 줄까지 봄)
    String titleLine = block.text().lines().findFirst().orElse("");
    // 공지 낱말은 제목 + 라벨 줄에서만 찾음 (본문의 "교통 통제" · 해시태그 "#공지" 때문에 행사를 미루지 않게)
    if (containsNotice(block.title() + "\n" + titleLine + "\n" + String.join("\n", block.labelLines()))) {
      return new Unsure("공지로 보이는 낱말");
    }
    if (placeLine == null) return new Unsure("장소 줄 없음");

    CaptionDateParser.Result date = CaptionDateParser.parse(CaptionLabel.content(dateLine));
    if (date.kind() == CaptionDateParser.Kind.INVALID) return new Invalid(date.reason());
    if (date.kind() == CaptionDateParser.Kind.UNREADABLE) return new Unsure(date.reason());

    String timeText = date.timeText();
    if (timeLine != null) {
      String t = CaptionText.blankToNull(CaptionLabel.content(timeLine));
      timeText = timeText == null ? t : (t == null ? timeText : timeText + " / " + t);
    }

    String place = CaptionText.blankToNull(CaptionLabel.content(placeLine));
    ParsedEvent event = new ParsedEvent(true, title, category(title + "\n" + titleLine), date.start(), date.end(), timeText, place);
    return new Confident(event, date.kind() == CaptionDateParser.Kind.NO_DATE ? date.reason() : null);
  }

  // 낱말로 고르는 카테고리 (모르면 "행사"). 허용 목록은 ParsedEventValidator.CATEGORIES
  static String category(String title) {
    if (hasAny(title, "축제", "페스티벌", "festival")) return "축제";
    if (hasAny(title, "전시", "展", "갤러리")) return "전시";
    if (hasAny(title, "공연", "콘서트", "연주", "음악회", "뮤지컬", "연극", "오페라", "버스킹")) return "공연";
    if (hasAny(title, "경기", "마라톤", "리그", "체육대회", "홈경기")) return "스포츠";
    if (hasAny(title, "강좌", "강연", "교육", "특강", "아카데미", "체험교실")) return "교육";
    return "행사";
  }

  static String cleanTitle(String raw) {
    String t = CaptionText.blankToNull(raw);
    if (t == null) return null;
    // 앞쪽 이모지 · 기호 제거 (꺾쇠 · 괄호로 시작하는 작품명은 남김)
    int i = 0;
    while (i < t.length()) {
      int cp = t.codePointAt(i);
      if (Character.isLetterOrDigit(cp) || "<[(\"'「『【〈《".indexOf(cp) >= 0) break;
      i += Character.charCount(cp);
    }
    t = t.substring(i).strip();
    t = TITLE_PREFIX.matcher(t).replaceFirst("").strip();
    return CaptionText.blankToNull(t);
  }

  private static boolean containsNotice(String text) {
    for (String w : NOTICE_WORDS) {
      if (text.contains(w)) return true;
    }
    return false;
  }

  private static boolean hasAny(String text, String... words) {
    String lower = text.toLowerCase(Locale.ROOT);
    for (String w : words) {
      if (lower.contains(w)) return true;
    }
    return false;
  }

  private static String first(CaptionBlock block, CaptionLabel label) {
    for (String line : block.labelLines()) {
      if (CaptionLabel.of(line) == label) return line;
    }
    return null;
  }
}
