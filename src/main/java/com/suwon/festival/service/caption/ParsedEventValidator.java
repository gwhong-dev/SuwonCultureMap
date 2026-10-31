package com.suwon.festival.service.caption;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// 정리 결과(규칙 · AI 모두)를 코드가 다시 검사 (설계 문서 5번 "다시 검사" 표)
public final class ParsedEventValidator {

  private ParsedEventValidator() {
  }

  // DB 칸 길이 (Event.title · category 기본 255, place 500, timeText 1000)
  static final int TITLE_MAX = 255;
  static final int PLACE_MAX = 500;
  static final int TIME_TEXT_MAX = 1000;

  // 프런트 CATEGORY_STYLE(index.html) 4종 + 스포츠 · 교육. 이 밖의 값은 "기타"
  // (스포츠 · 교육은 지금 프런트에서 "기타" 마커로 보임 → 6단계에서 스타일 추가)
  public static final Set<String> CATEGORIES = Set.of("행사", "공연", "전시", "축제", "스포츠", "교육", "기타");

  public record Result(CandidateStatus status, ParsedEvent event, String reason, List<String> warnings) {
  }

  public static Result validate(ParsedEvent e, String blockText) {
    List<String> warnings = new ArrayList<>();
    if (e == null) return fail("결과 없음", warnings);
    if (e.isEvent() == null) return fail("행사 여부를 모름", warnings);
    // 행사 아님: 받은 값은 저장하지 않음 (검사 · 자르기 안 된 값이 다음 단계로 가지 않게)
    if (!e.isEvent()) return new Result(CandidateStatus.SKIPPED, null, "행사 아님", warnings);

    String title = CaptionText.blankToNull(e.title());
    if (title == null) return fail("제목 없음", warnings);
    title = CaptionText.limit(title, TITLE_MAX);

    String category = CaptionText.blankToNull(e.category());
    if (category == null || !CATEGORIES.contains(category)) {
      if (category != null) warnings.add("모르는 카테고리 → 기타: " + CaptionText.limit(category, 30));
      category = "기타";
    }

    String text = CaptionText.normalize(blockText);
    String place = CaptionText.limit(CaptionText.blankToNull(CaptionText.normalize(e.placeName())), PLACE_MAX);
    if (place != null && !squash(text).contains(squash(place))) {
      warnings.add("장소가 원문에 없음 → 비움");
      place = null; // 지어낸 장소로 엉뚱한 좌표가 찍히는 것 방지 → 다음 단계에서 NO_COORD
    }
    String timeText = CaptionText.limit(CaptionText.blankToNull(e.timeText()), TIME_TEXT_MAX);

    LocalDate start = e.startDate();
    LocalDate end = e.endDate();
    if (start == null) {
      return new Result(CandidateStatus.NO_DATE,
          new ParsedEvent(true, title, category, null, null, timeText, place), "시작일 없음", warnings);
    }
    if (end == null) end = start;
    if (end.isBefore(start)) return fail("종료일이 시작일보다 앞섬", warnings);

    // 환각 방지: 날짜가 "이 블록 원문"에 있어야 함 (낱개 숫자 · 소수 일치는 불인정)
    // 시작일은 연도 · 월 · 일이 한 덩어리(YYYY. M. D. / YYYY년 M월 D일)로 있어야 함
    //  → 본문 다른 곳의 "2025년 수상작" 같은 연도와 월 · 일을 짜 맞추는 것 방지
    if (!monthDayInText(text, start)) return fail("시작일이 원문에 없음", warnings);
    if (!fullDateInText(text, start)) return fail("시작일 연도가 원문에 없음", warnings);
    if (!end.equals(start)) {
      if (!monthDayInText(text, end)) return fail("종료일이 원문에 없음", warnings);
      // 종료일 연도: 시작일과 같은 해, 해 넘김(+1), 또는 원문에 연도까지 적힌 경우만
      int endYear = end.getYear();
      boolean endYearOk = endYear == start.getYear()
          || (endYear == start.getYear() + 1 && monthDayBefore(end, start))
          || fullDateInText(text, end);
      if (!endYearOk) return fail("종료일 연도가 원문에 없음", warnings);
    }

    return new Result(CandidateStatus.PARSED,
        new ParsedEvent(true, title, category, start, end, timeText, place), null, warnings);
  }

  private static Result fail(String reason, List<String> warnings) {
    return new Result(CandidateStatus.FAILED, null, reason, warnings);
  }

  // 날짜 뒤 경계: 끝 · 공백 · "." · "일" · "(" · "~" · "," · ")" · "-" — "1.5시간" · "3.5만원" 같은 소수는 불인정
  private static final String DAY_END = "(?![\\d:])(?=$|[\\s.일(~,)\\-])";

  // "11. 7." / "11.7" / "11월 7일" / "11/7" (앞에 0 붙은 것 포함)
  static boolean monthDayInText(String text, LocalDate d) {
    Pattern p = Pattern.compile("(?<![\\d:])0?" + d.getMonthValue() + "\\s*(?:\\.|/|월)\\s*0?" + d.getDayOfMonth() + DAY_END);
    return p.matcher(text).find();
  }

  // "2026. 11. 7." / "2026.11.07" / "2026년 11월 7일" / "2026-11-07"
  static boolean fullDateInText(String text, LocalDate d) {
    Pattern p = Pattern.compile("(?<!\\d)" + d.getYear() + "\\s*(?:\\.|/|-|년)\\s*0?" + d.getMonthValue()
        + "\\s*(?:\\.|/|-|월)\\s*0?" + d.getDayOfMonth() + DAY_END);
    return p.matcher(text).find();
  }

  private static boolean monthDayBefore(LocalDate a, LocalDate b) {
    return a.getMonthValue() < b.getMonthValue()
        || (a.getMonthValue() == b.getMonthValue() && a.getDayOfMonth() < b.getDayOfMonth());
  }

  private static String squash(String s) {
    return s.replaceAll("\\s+", "");
  }
}
