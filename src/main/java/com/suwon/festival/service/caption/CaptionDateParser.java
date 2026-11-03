package com.suwon.festival.service.caption;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 날짜 줄 읽기: "YYYY. M. D.(요일) ~ M. D.(요일) HH:MM~HH:MM" 꼴 (설계 문서 3-1)
//
// 원칙 (설계 문서 5번)
// - 연도는 "같은 줄의 시작일"에서만 가져온다. 줄에 연도가 전혀 없으면 추측하지 않음 → NO_DATE
// - 종료일 연도가 생략되면 시작일 연도를 쓰고, 종료가 시작보다 앞서면 해를 넘긴 것으로 보고 +1
// - (요일)이 적혀 있으면 달력과 맞는지 확인 → 안 맞으면 INVALID
// - 시작일 없이 "~ 종료일"만, "YYYY. M월"(일 없음), "매주 금~일" 반복 → NO_DATE (시간 원문은 보관)
// - 여러 회차("11. 5.(목), 11. 12.(목)")는 날짜마다 요일이 맞을 때만 시작 = 처음 · 종료 = 마지막. 기간 + 다른 날짜는 AI 몫
public final class CaptionDateParser {

  private CaptionDateParser() {
  }

  // NO_DATE   : 날짜가 정말 없음 (반복 · 일 없음 · 시작일 없음 · 연도 없음) → 보류
  // UNREADABLE: 날짜가 있어 보이는데 규칙이 못 읽음 (다른 모양 · 여러 회차 · 종료일 모양) → AI 몫
  public enum Kind { OK, NO_DATE, UNREADABLE, INVALID }

  public record Result(Kind kind, LocalDate start, LocalDate end, String timeText, String reason) {
  }

  private static final String WEEKDAY = "(?:\\(\\s*([월화수목금토일])\\s*\\))?";

  // 시작일: 연도 필수
  private static final Pattern FULL = Pattern.compile(
      "(?<!\\d)(\\d{4})\\s*\\.\\s*(\\d{1,2})\\s*\\.\\s*(\\d{1,2})(?!\\d)\\s*\\.?\\s*" + WEEKDAY);
  // 종료일: 연도 생략 가능. 반드시 "~" 바로 뒤
  private static final Pattern RANGE_END = Pattern.compile(
      "^\\s*~\\s*(?:(\\d{4})\\s*\\.\\s*)?(\\d{1,2})\\s*\\.\\s*(\\d{1,2})(?!\\d)\\s*\\.?\\s*" + WEEKDAY);
  // 연도 없는 "M. D." (시작일 없는 줄 판단 · 날짜 여러 개 판단용)
  private static final Pattern MONTH_DAY = Pattern.compile("(?<![\\d:])(\\d{1,2})\\s*\\.\\s*(\\d{1,2})(?![\\d:])");
  private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}");
  // 여러 회차의 다음 날짜: 구분자(, · /) 뒤 연도 생략 가능, 요일 필수
  private static final Pattern NEXT_DATE = Pattern.compile(
      "[\\s,·/]*(?:(\\d{4})\\s*\\.\\s*)?(\\d{1,2})\\s*\\.\\s*(\\d{1,2})(?!\\d)\\s*\\.?\\s*\\(\\s*([월화수목금토일])\\s*\\)");
  private static final Pattern LEADING_TIME = Pattern.compile("\\d{1,2}:\\d{2}");
  private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");

  private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
      "월", DayOfWeek.MONDAY, "화", DayOfWeek.TUESDAY, "수", DayOfWeek.WEDNESDAY,
      "목", DayOfWeek.THURSDAY, "금", DayOfWeek.FRIDAY, "토", DayOfWeek.SATURDAY, "일", DayOfWeek.SUNDAY);

  // line: 라벨을 뗀 날짜 줄 내용 (CaptionLabel.content)
  public static Result parse(String line) {
    String v = CaptionText.normalize(line).strip();
    if (v.isEmpty()) return noDate(null, "날짜 줄이 비어 있음");

    Matcher full = FULL.matcher(v);
    if (!full.find()) {
      if (v.contains("매주")) return noDate(v, "반복 일정 (날짜 없음)");
      if (v.matches(".*\\d{4}\\s*\\.\\s*\\d{1,2}\\s*월.*")) return noDate(v, "일(日) 없음");
      if (v.startsWith("~")) return noDate(v, "시작일 없음");
      if (!YEAR.matcher(v).find()) return noDate(v, "연도 없음 (추측하지 않음)");
      return unreadable(v, "연도는 있는데 날짜 모양을 못 읽음");
    }
    // "~ 2026. 11. 30.까지" 처럼 연도 있는 날짜가 "~" 뒤에만 있으면 그건 종료일 → 시작일 없음
    if (v.substring(0, full.start()).contains("~")) return noDate(v, "시작일 없음");

    LocalDate start;
    try {
      start = LocalDate.of(num(full.group(1)), num(full.group(2)), num(full.group(3)));
    } catch (DateTimeException e) {
      return invalid("없는 날짜(시작일)");
    }
    if (!weekdayMatches(start, full.group(4))) return invalid("시작일 요일이 달력과 다름");

    LocalDate end = start;
    String rest = v.substring(full.end());
    Matcher endM = RANGE_END.matcher(rest);
    boolean hasTilde = rest.stripLeading().startsWith("~");
    if (hasTilde && !endM.find()) {
      // "~ 7.(토)" 처럼 종료일 모양을 못 읽음 → 하루짜리로 두면 틀린 기간이 지도에 오름
      return unreadable(v, "종료일 모양을 못 읽음");
    }
    if (hasTilde) {
      boolean yearGiven = endM.group(1) != null;
      int year = yearGiven ? num(endM.group(1)) : start.getYear();
      try {
        end = LocalDate.of(year, num(endM.group(2)), num(endM.group(3)));
      } catch (DateTimeException e) {
        return invalid("없는 날짜(종료일)");
      }
      if (end.isBefore(start)) {
        if (yearGiven) return invalid("종료일이 시작일보다 앞섬");
        try {
          end = end.plusYears(1); // 해 넘김 (예: 12월 ~ 1월)
        } catch (DateTimeException e) {
          return invalid("없는 날짜(종료일)");
        }
      }
      if (!weekdayMatches(end, endM.group(4))) return invalid("종료일 요일이 달력과 다름");
      rest = rest.substring(endM.end());
    }

    // 범위가 아닌 날짜가 더 있으면(여러 회차) 전부 읽어 시작일 = 처음, 종료일 = 마지막 (날짜 목록은 ScheduleExtractor 가 같은 줄에서 다시 읽음)
    if (FULL.matcher(rest).find() || MONTH_DAY.matcher(rest).find()) {
      if (hasTilde) return unreadable(v, "날짜가 여러 개 (기간 + 다른 날짜)");
      return dateList(v, start, full.group(4) != null, rest);
    }

    // "매주 금~일" 은 시간이 없어도 보관 → 진행 날짜를 요일로 펼칠 때 씀 (설계 docs/event-schedule.md 9번)
    String timeText = TIME.matcher(rest).find() || rest.contains("매주")
        ? CaptionText.blankToNull(rest.replaceFirst("^[\\s,·/]+", "")) : null;
    return new Result(Kind.OK, start, end, timeText, null);
  }

  // 여러 회차: "2026. 11. 5.(목), 11. 12.(목) 14:00" — 날짜마다 요일이 있고(달력 확인), 날짜 · 구분자 뒤에 시간만 올 때
  private static Result dateList(String v, LocalDate start, boolean startHasWeekday, String rest) {
    if (!startHasWeekday) return unreadable(v, "날짜가 여러 개인데 요일이 없음");
    LocalDate last = start;
    String tail = rest;
    Matcher m = NEXT_DATE.matcher(tail);
    while (m.lookingAt()) {
      boolean yearGiven = m.group(1) != null;
      LocalDate date;
      try {
        date = LocalDate.of(yearGiven ? num(m.group(1)) : last.getYear(), num(m.group(2)), num(m.group(3)));
        if (!yearGiven && date.isBefore(last)) date = date.plusYears(1); // 해 넘김
      } catch (DateTimeException e) {
        return invalid("없는 날짜(회차)");
      }
      if (!date.isAfter(last)) return unreadable(v, "회차 날짜가 차례대로가 아님");
      if (!weekdayMatches(date, m.group(4))) return invalid("회차 요일이 달력과 다름");
      last = date;
      tail = tail.substring(m.end());
      m = NEXT_DATE.matcher(tail);
    }
    tail = tail.replaceFirst("^[\\s,·/]+", "");
    if (!tail.isEmpty() && !LEADING_TIME.matcher(tail).lookingAt()) return unreadable(v, "회차 날짜 뒤에 다른 글자");
    if (FULL.matcher(tail).find() || MONTH_DAY.matcher(tail).find()) return unreadable(v, "회차 날짜 모양을 못 읽음");
    return new Result(Kind.OK, start, last, CaptionText.blankToNull(tail), null);
  }

  private static boolean weekdayMatches(LocalDate date, String weekday) {
    return weekday == null || WEEKDAYS.get(weekday) == date.getDayOfWeek();
  }

  private static int num(String s) {
    return Integer.parseInt(s);
  }

  private static Result noDate(String timeText, String reason) {
    return new Result(Kind.NO_DATE, null, null, timeText, reason);
  }

  private static Result unreadable(String timeText, String reason) {
    return new Result(Kind.UNREADABLE, null, null, timeText, reason);
  }

  private static Result invalid(String reason) {
    return new Result(Kind.INVALID, null, null, null, reason);
  }
}
