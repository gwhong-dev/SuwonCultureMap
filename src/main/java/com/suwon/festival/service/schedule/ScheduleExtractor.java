package com.suwon.festival.service.schedule;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.suwon.festival.service.caption.CaptionText;

// 행사 칸 값 · 본문 → 실제 진행 날짜 (설계 docs/event-schedule.md 3번). DB · 네트워크를 쓰지 않는 순수 로직
//
// 읽는 순서 (앞에서 정해지면 멈춤): ① 하루짜리 ② 날짜 나열 ③ 매주 요일 ④ 요일 범위 · 쉬는 요일 ⑤ 매일 ⑥ 모름
// 원칙: 틀린 날짜보다 모름이 낫다. 애매하면 추측하지 않고 UNKNOWN → 화면은 기간 + 원문
public final class ScheduleExtractor {

  private ScheduleExtractor() {
  }

  private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
      "월", DayOfWeek.MONDAY, "화", DayOfWeek.TUESDAY, "수", DayOfWeek.WEDNESDAY,
      "목", DayOfWeek.THURSDAY, "금", DayOfWeek.FRIDAY, "토", DayOfWeek.SATURDAY, "일", DayOfWeek.SUNDAY);
  private static final String WD = "[월화수목금토일]";
  private static final String OFF_WORD = "(?:미운영|휴관|휴무|휴장|운영\\s*제외|운영하지)";

  // 이 항목의 본문 줄만 읽음 → 연계 프로그램 · 안내문 속 날짜를 행사 날짜로 오해하지 않게
  private static final Pattern ALLOWED_LABEL = Pattern.compile(
      "^(?:운영\\s*일자?|운영\\s*일정|운영\\s*일시|행사\\s*일정|행사\\s*일시|공연\\s*일시|진행\\s*일시|일\\s*시|일\\s*정"
          + "|기\\s*간|관람\\s*기간|전시\\s*기간|해설\\s*기간|체험\\s*요일|관람\\s*시간)\\s*(?:[:：|｜]|l\\s)");
  // 신청 · 예매 날짜는 행사 날짜가 아님 (실데이터: 날짜 368개 중 41개가 기간 밖, 대부분 이 경우)
  private static final Pattern FORBIDDEN = Pattern.compile("접수|신청|예매|티켓|모집|환불");

  // 날짜 조각: 요일까지 적힌 것만 인정 (요일이 달력과 맞는지 확인할 수 있어야 함)
  // "2026. 5. 16.(토)" · "5.16(토)" · "5월 16일(토)"
  private static final Pattern DATE_TOKEN = Pattern.compile(
      "(?<![\\d.:])(?:(20\\d{2})\\s*[.년]\\s*)?(\\d{1,2})\\s*[.월]\\s*(\\d{1,2})\\s*일?\\s*\\.?\\s*\\(\\s*(" + WD + ")\\s*\\)");
  private static final Pattern TIME_RANGE = Pattern.compile("\\d{1,2}:\\d{2}\\s*[~-]\\s*(?:\\d{1,2}:\\d{2})?");
  private static final Pattern TOTAL_COUNT = Pattern.compile("총\\s*(\\d{1,3})\\s*회");

  // "금~일요일" · "토/일요일" · "금, 토요일" · "금·토·일"
  private static final Pattern WEEKLY_DAYS = Pattern.compile(
      "^\\s*(" + WD + "(?:요일)?(?:\\s*[~/,·및]\\s*" + WD + "(?:요일)?)*)");
  // "5·6·9·10월" · "5월 / 9월" · "9월~10월" · "8~11월" (매주 앞에 붙는 달 한정)
  private static final Pattern MONTHS = Pattern.compile(
      "(\\d{1,2}월?(?:\\s*[·,/~]\\s*\\d{1,2}월?)*월)\\s*(?:중\\s*)?$|(\\d{1,2}월)\\s*(?:중\\s*)?$");
  private static final Pattern NUMBER = Pattern.compile("\\d{1,2}");
  // 앞이 숫자 · 한글이면 요일이 아님: "9월 16:00" · "매일 휴관"
  private static final Pattern WEEKDAY_RANGE = Pattern.compile(
      "(?<![\\d가-힣])(" + WD + ")(?:요일)?\\s*~\\s*(" + WD + ")요일");
  private static final Pattern OFF_DAY = Pattern.compile(
      "(?<![\\d가-힣])(" + WD + ")(?:요일)?\\s*(?:및\\s*(?:법정\\s*)?공휴일\\s*)?(?:은|는|에는)?\\s*" + OFF_WORD);
  // "토요일 14:00 ~ 17:30 (월요일 및 공휴일 미운영)" — 쉬는 요일만 보고 펼치면 6일이 되지만 실제는 토요일 행사
  private static final Pattern FIXED_WEEKDAY = Pattern.compile("^\\s*" + WD + "요일\\s*\\d");
  private static final Pattern HOLIDAY_OFF = Pattern.compile("공휴일.*" + OFF_WORD);

  // 한 단계의 결과. failReason 이 있으면 뒤 단계로 넘어가지 않고 UNKNOWN (예: 요일이 바뀌는 행사를 쉬는 요일 규칙이 잘못 읽지 않게)
  private record Reading(List<LocalDate> dates, String source, Integer expectedCount, String failReason) {

    static Reading of(List<LocalDate> dates, String source) {
      return new Reading(dates, source, totalCount(source), null);
    }

    static Reading fail(String reason) {
      return new Reading(List.of(), null, null, reason);
    }
  }

  public static ScheduleResult extract(ScheduleInput in) {
    LocalDate start = in.start();
    if (start == null) return ScheduleResult.unknown("시작일 없음");
    LocalDate end = in.end() != null ? in.end() : start;
    if (end.isBefore(start)) return ScheduleResult.unknown("종료일이 시작일보다 앞섬");
    if (end.equals(start)) return ScheduleResult.dates(List.of(start), null, false);

    String time = flatten(in.timeText());
    String weekday = flatten(in.weekdayText());
    List<String> bodyLines = bodyLines(in.body());
    List<String> labeled = bodyLines.stream().filter(ScheduleExtractor::isAllowedLine).toList();

    Reading listed = first(dateList(time, start, end), oneOf(labeled, l -> dateList(l, start, end), "날짜 나열"));
    if (listed != null) return finish(listed, start, end, bodyLines, false);

    Reading weekly = first(weekly(weekday, start, end), oneOf(labeled, l -> weekly(l, start, end), "매주"));
    if (weekly != null) {
      String conflict = weekly.failReason() == null ? rowConflict(weekly.dates(), bodyLines, start, end) : null;
      return conflict != null ? ScheduleResult.unknown(conflict) : finish(weekly, start, end, bodyLines, true);
    }

    Reading byDays = first(weekdayRule(time, start, end), first(weekdayRule(weekday, start, end),
        oneOf(labeled, l -> weekdayRule(l, start, end), "요일 범위")));
    if (byDays != null) return finish(byDays, start, end, bodyLines, true);

    for (String line : labeled) {
      if (line.contains("매일")) return finish(Reading.of(expand(start, end, EnumSet.allOf(DayOfWeek.class), Set.of()), line),
          start, end, bodyLines, true);
    }
    return ScheduleResult.unknown("읽을 수 있는 일정 표현 없음");
  }

  private static ScheduleResult finish(Reading r, LocalDate start, LocalDate end, List<String> bodyLines, boolean byWeekday) {
    if (r.failReason() != null) return ScheduleResult.unknown(r.failReason());
    String problem = ScheduleValidator.check(start, end, r.dates(), r.expectedCount());
    if (problem != null) return ScheduleResult.unknown(problem);
    // 날짜를 직접 적은 경우는 공휴일을 계산할 일이 없음. 요일로 펼친 경우만 표시
    boolean holiday = byWeekday && (r.source().contains("공휴일")
        || bodyLines.stream().anyMatch(l -> HOLIDAY_OFF.matcher(l).find()));
    return ScheduleResult.dates(r.dates(), CaptionText.limit(r.source(), 1000), holiday);
  }

  // ===== ② 날짜 나열: "~" 로 이어지지 않은 날짜 2개 이상 =====
  private static Reading dateList(String line, LocalDate start, LocalDate end) {
    if (line == null) return null;
    // 시간의 "~" · "-" 는 지우고 봄 → 남은 것은 날짜 범위 (범위의 양 끝을 회차로 읽으면 안 됨)
    String text = TIME_RANGE.matcher(line).replaceAll(" ");
    if (text.contains("~") || text.contains("-")) return null;

    Matcher m = DATE_TOKEN.matcher(text);
    TreeSet<LocalDate> dates = new TreeSet<>();
    int tokens = 0;
    while (m.find()) {
      tokens++;
      LocalDate date = resolve(m, start, end);
      if (date == null) return Reading.fail("없는 날짜: " + m.group());
      if (date.getDayOfWeek() != WEEKDAYS.get(m.group(4))) return Reading.fail("요일이 달력과 다름: " + m.group());
      // "10. 18.(일) 우천예보 취소" → 그 날짜만 뺌 ("취소될 수 있습니다" 안내문은 제외)
      int next = m.end();
      Matcher after = DATE_TOKEN.matcher(text);
      String tail = text.substring(next, after.find(next) ? after.start() : text.length());
      if (!tail.contains("취소") || tail.contains("취소될") || tail.contains("취소 될")) dates.add(date);
    }
    return tokens >= 2 ? Reading.of(List.copyOf(dates), line) : null;
  }

  // 연도가 없으면 기간에 들어가는 해를 고름 (해 넘김 행사). 어느 해에도 안 들어가면 시작 연도 → 검사에서 기간 밖으로 걸림
  private static LocalDate resolve(Matcher m, LocalDate start, LocalDate end) {
    int month = Integer.parseInt(m.group(2));
    int day = Integer.parseInt(m.group(3));
    if (m.group(1) != null) return dateOf(Integer.parseInt(m.group(1)), month, day);
    for (int year = start.getYear(); year <= end.getYear(); year++) {
      LocalDate date = dateOf(year, month, day);
      if (date != null && !date.isBefore(start) && !date.isAfter(end)) return date;
    }
    return dateOf(start.getYear(), month, day);
  }

  private static LocalDate dateOf(int year, int month, int day) {
    try {
      return LocalDate.of(year, month, day);
    } catch (DateTimeException e) {
      return null;
    }
  }

  // ===== ③ 매주 요일 =====
  private static Reading weekly(String line, LocalDate start, LocalDate end) {
    if (line == null) return null;
    int at = line.indexOf("매주");
    if (at < 0) return null;
    String prefix = line.substring(0, at);
    String after = line.substring(at + 2);
    Matcher m = WEEKLY_DAYS.matcher(after);
    if (!m.find()) return null;
    String rest = after.substring(m.end());
    // "매주 월요일 휴관" 은 진행 요일이 아니라 쉬는 요일 → ④ 에서 읽음
    if (rest.stripLeading().matches("(?s)^(?:은|는|에는)?\\s*" + OFF_WORD + ".*")) return null;
    // "매주 수요일, 토요일 ※ 2026년 9월부터 목요일, 토요일" → 도중에 요일이 바뀜
    if (rest.contains("부터")) return Reading.fail("도중에 요일이 바뀜");
    return Reading.of(expand(start, end, weekdaysOf(m.group(1)), monthsOf(prefix)), line);
  }

  // "매주" 문구와 회차 표가 어긋나면 어느 쪽도 믿지 않음. 표에 나온 달에 한해 비교 (본문이 갱신되며 지난 달 표가 사라지는 일이 있음)
  private static String rowConflict(List<LocalDate> weekly, List<String> bodyLines, LocalDate start, LocalDate end) {
    Set<LocalDate> rows = new TreeSet<>();
    for (String line : bodyLines) {
      if (FORBIDDEN.matcher(line).find()) continue;
      Matcher m = DATE_TOKEN.matcher(line);
      if (!m.find() || m.start() != 0 || line.contains("~")) continue;
      LocalDate date = resolve(m, start, end);
      if (date != null && !date.isBefore(start) && !date.isAfter(end)) rows.add(date);
    }
    for (LocalDate row : rows) {
      if (!inMonth(weekly, row).equals(inMonth(rows, row))) return "매주 문구와 회차 표가 어긋남";
    }
    return null;
  }

  private static List<LocalDate> inMonth(Collection<LocalDate> dates, LocalDate month) {
    return dates.stream().filter(d -> YearMonth.from(d).equals(YearMonth.from(month))).sorted().toList();
  }

  private static Set<DayOfWeek> weekdaysOf(String text) {
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    String plain = text.replace("요일", ""); // "토요일" 의 "일" 을 일요일로 읽지 않게
    Matcher range = Pattern.compile("(" + WD + ")\\s*~\\s*(" + WD + ")").matcher(plain);
    while (range.find()) days.addAll(cycle(range.group(1), range.group(2)));
    Matcher single = Pattern.compile(WD).matcher(plain);
    while (single.find()) days.add(WEEKDAYS.get(single.group()));
    return days;
  }

  // "금~일" · "토~월"(주를 넘어감) 모두 시작 요일부터 끝 요일까지
  private static Set<DayOfWeek> cycle(String from, String to) {
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    DayOfWeek day = WEEKDAYS.get(from);
    days.add(day);
    while (day != WEEKDAYS.get(to)) {
      day = day.plus(1);
      days.add(day);
    }
    return days;
  }

  private static Set<Integer> monthsOf(String prefix) {
    Matcher m = MONTHS.matcher(DATE_TOKEN.matcher(prefix).replaceAll(" ").stripTrailing());
    if (!m.find()) return Set.of();
    String text = m.group(1) != null ? m.group(1) : m.group(2);
    List<Integer> numbers = new ArrayList<>();
    Matcher n = NUMBER.matcher(text);
    while (n.find()) numbers.add(Integer.parseInt(n.group()));
    Set<Integer> months = new LinkedHashSet<>(numbers);
    if (text.contains("~") && numbers.size() == 2) {
      for (int month = numbers.get(0); month <= numbers.get(1); month++) months.add(month);
    }
    return months;
  }

  private static List<LocalDate> expand(LocalDate start, LocalDate end, Set<DayOfWeek> days, Set<Integer> months) {
    return start.datesUntil(end.plusDays(1))
        .filter(d -> days.contains(d.getDayOfWeek()))
        .filter(d -> months.isEmpty() || months.contains(d.getMonthValue()))
        .toList();
  }

  // ===== ④ 요일 범위 · 쉬는 요일 =====
  private static Reading weekdayRule(String line, LocalDate start, LocalDate end) {
    if (line == null || FIXED_WEEKDAY.matcher(line).find()) return null;
    Set<DayOfWeek> off = EnumSet.noneOf(DayOfWeek.class);
    Matcher offDay = OFF_DAY.matcher(line);
    while (offDay.find()) off.add(WEEKDAYS.get(offDay.group(1)));
    if (!off.isEmpty()) {
      // "화~토요일 … / 일요일 … / 월요일 휴관" 처럼 범위가 같이 있어도 쉬는 요일이 더 분명함
      Set<DayOfWeek> open = EnumSet.allOf(DayOfWeek.class);
      open.removeAll(off);
      return Reading.of(expand(start, end, open, Set.of()), line);
    }
    Matcher range = WEEKDAY_RANGE.matcher(line);
    if (!range.find()) return null;
    return Reading.of(expand(start, end, cycle(range.group(1), range.group(2)), Set.of()), line);
  }

  // ===== 공통 =====
  private interface LineReader {
    Reading read(String line);
  }

  // 본문의 여러 줄이 서로 다른 날짜를 말하면 여러 프로그램이 섞인 것 → 고르지 않음
  private static Reading oneOf(List<String> lines, LineReader reader, String kind) {
    Reading found = null;
    for (String line : lines) {
      Reading r = reader.read(line);
      if (r == null) continue;
      if (r.failReason() != null) return r;
      if (found != null && !found.dates().equals(r.dates())) return Reading.fail(kind + " 줄이 서로 다름");
      if (found == null) found = r;
    }
    return found;
  }

  private static Reading first(Reading a, Reading b) {
    return a != null ? a : b;
  }

  private static boolean isAllowedLine(String line) {
    return ALLOWED_LABEL.matcher(line).find() && !FORBIDDEN.matcher(line).find();
  }

  private static List<String> bodyLines(String body) {
    if (body == null) return List.of();
    return body.lines().map(ScheduleExtractor::normalize).filter(l -> !l.isEmpty()).toList();
  }

  private static String flatten(String value) {
    if (value == null) return null;
    String v = normalize(value.replaceAll("\\s*\\R\\s*", " "));
    return v.isEmpty() ? null : v;
  }

  // 물결 · 줄표 · 가운뎃점 변형을 한 글자로
  private static String normalize(String value) {
    return value.replaceAll("[～〜∼]", "~").replaceAll("[–—－]", "-").replaceAll("[‧ㆍ・･]", "·")
        .replace(' ', ' ').strip();
  }

  private static Integer totalCount(String source) {
    Matcher m = TOTAL_COUNT.matcher(source);
    return m.find() ? Integer.valueOf(m.group(1)) : null;
  }
}
