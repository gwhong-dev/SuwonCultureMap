package com.suwon.festival.service.caption;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 캡션의 "라벨 줄" 인식 (🗓️ 날짜, 📍 장소, ⏰ 시간, 그 밖의 안내 줄)
// 줄은 CaptionText.normalize() 를 거친 뒤 넘겨야 함 (변형 문자 제거)
public enum CaptionLabel {
  DATE, PLACE, TIME, OTHER;

  private static final List<String> DATE_EMOJI = List.of("🗓", "📅", "📆");
  private static final List<String> PLACE_EMOJI = List.of("📍"); // 📌는 공지 머리표로도 흔히 써서 제외
  private static final List<String> TIME_EMOJI = List.of("⏰", "🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗", "🕘", "🕙", "🕚", "🕛", "⌚");
  private static final List<String> OTHER_EMOJI = List.of("💰", "💸", "🎫", "🎟", "☎", "📞", "📱", "🔗", "🙋", "👥");

  // "일시: ..." 처럼 글자 라벨 + 구분자(콜론 · 세로선)
  // - 앞에 "공연 · 전시 · 행사 …"가 붙은 라벨도 인식 (예: "공연장소 | 수원SK아트리움", "전시기간 : …")
  // - group(1) 은 붙은 말을 뺀 기본 라벨 → 종류 판단에 씀
  private static final Pattern TEXT_LABEL = Pattern.compile(
      "^(?:공연|전시|행사|교육|체험|관람|상영|운영)?"
          + "(일시|기간|날짜|일정|장소|위치|시간|대상|문의|참가비|관람료|입장료|요금|신청|접수|주최|주관)"
          + "\\s*[:：|｜]\\s*");

  // 라벨이 아니면 null
  public static CaptionLabel of(String line) {
    String t = line.strip();
    if (t.isEmpty()) return null;
    if (startsWithAny(t, DATE_EMOJI)) return DATE;
    if (startsWithAny(t, PLACE_EMOJI)) return PLACE;
    if (startsWithAny(t, TIME_EMOJI)) return TIME;
    if (startsWithAny(t, OTHER_EMOJI)) return OTHER;
    Matcher m = TEXT_LABEL.matcher(t);
    if (m.find()) {
      return switch (m.group(1)) {
        case "일시", "기간", "날짜", "일정" -> DATE;
        case "장소", "위치" -> PLACE;
        case "시간" -> TIME;
        default -> OTHER;
      };
    }
    return null;
  }

  // 라벨 이모지 · "장소:" 같은 글자를 떼고 내용만
  public static String content(String line) {
    String t = line.strip();
    for (List<String> group : List.of(DATE_EMOJI, PLACE_EMOJI, TIME_EMOJI, OTHER_EMOJI)) {
      for (String e : group) {
        if (t.startsWith(e)) {
          t = t.substring(e.length()).strip();
          break;
        }
      }
    }
    Matcher m = TEXT_LABEL.matcher(t);
    if (m.find()) t = t.substring(m.end());
    return t.strip();
  }

  private static boolean startsWithAny(String t, List<String> prefixes) {
    for (String p : prefixes) {
      if (t.startsWith(p)) return true;
    }
    return false;
  }
}
