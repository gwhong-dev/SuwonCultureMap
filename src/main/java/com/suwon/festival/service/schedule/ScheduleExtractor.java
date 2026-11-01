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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.suwon.festival.service.caption.CaptionText;

// 행사 칸 값 · 본문 → 실제 진행 날짜 (설계 docs/event-schedule.md 3번). DB · 네트워크를 쓰지 않는 순수 로직
//
// 읽는 순서 (앞에서 정해지면 멈춤): ① 하루짜리 ② 날짜 나열 ③ 매주 요일 ④ 요일 범위 · 쉬는 요일 ⑤ 매일 ⑥ 모름
// 원칙: 틀린 날짜보다 모름이 낫다.
// 핵심 장치: 각 단계는 아는 조각(날짜 · 요일 · 시간 · 구분자)을 지운 뒤 한글 · 숫자가 남으면 믿지 않음
//  → "(10. 3.(토) 휴관)" · "(7~8월 제외)" · "우천 시 연기" 처럼 뜻을 바꾸는 말을 규칙이 몰라도 틀린 날짜가 되지 않음
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
  // 신청 · 예매 날짜는 행사 날짜가 아님 (실데이터: 날짜 368개 중 41개가 기간 밖, 대부분 이 경우). 칸 값에도 똑같이 적용
  private static final Pattern FORBIDDEN = Pattern.compile("접수|신청|예매|예약|티켓|모집|환불");

  // 날짜 조각: 요일까지 적힌 것만 인정 (요일이 달력과 맞는지 확인할 수 있어야 함)
  // "2026. 5. 16.(토)" · "5.16(토)" · "5월 16일(토)"
  private static final Pattern DATE_TOKEN = Pattern.compile(
      "(?<![\\d.:])(?:(20\\d{2})\\s*[.년]\\s*)?(\\d{1,2})\\s*[.월]\\s*(\\d{1,2})\\s*일?\\s*\\.?\\s*\\(\\s*(" + WD + ")\\s*\\)");
  private static final Pattern DATE_RANGE = Pattern.compile(DATE_TOKEN.pattern() + "\\s*[~-]\\s*" + DATE_TOKEN.pattern());
  // 끝 시간까지 있어야 시간 범위. "5. 1.(금) 10:00 ~ 10. 31.(토)" 의 물결은 날짜 범위라 지우면 안 됨
  private static final Pattern TIME_RANGE = Pattern.compile("\\d{1,2}:\\d{2}\\s*[~-]\\s*\\d{1,2}:\\d{2}");
  private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}");
  private static final Pattern ENTRY_CLOSE = Pattern.compile("\\(\\s*입장\\s*마감\\s*\\d{1,2}:\\d{2}\\s*\\)");
  private static final Pattern TOTAL_COUNT = Pattern.compile("\\[?총\\s*(\\d{1,3})\\s*회(?:\\s*운영)?\\]?");
  // "10. 18.(일) 우천예보 취소" — 날짜 바로 뒤에 붙은 취소만 인정. "(취소) 10. 18." · "우천 시 취소" 는 남은 글자로 걸림
  private static final Pattern CANCELLED = Pattern.compile("^\\s*(?:우천(?:예보)?\\s*)?취소(?![가-힣])");
  private static final Pattern LEFTOVER = Pattern.compile("[가-힣\\d]");

  private static final String DAY = WD + "(?:요일)?";
  // "금~일요일" · "토/일요일" · "금, 토요일" · "금·토·일". 뒤에 한글이 붙으면 요일이 아님: "매주 일정이" · "매주 토론" · "매주 금토일"
  private static final Pattern WEEKLY_DAYS = Pattern.compile(
      "^\\s*(" + DAY + "(?![가-힣])(?:\\s*[~/,·및]\\s*" + DAY + "(?![가-힣]))*)");
  private static final Pattern WEEKDAY_CHAR = Pattern.compile(WD);
  private static final Pattern WEEKDAY_SPAN = Pattern.compile("(" + WD + ")\\s*~\\s*(" + WD + ")");
  private static final Pattern WEEKLY_REST_OK = Pattern.compile("공휴일|\\(\\s*\\d+\\s*시간\\s*소요\\s*\\)|\\(\\s*예정\\s*\\)");
  private static final Pattern OFF_START = Pattern.compile("^\\s*(?:은|는|에는)?\\s*" + OFF_WORD);
  // 매주 앞의 달 한정: "5·6·9·10월" · "5월 / 9월" · "9월~10월" · "2026년 8~11월" · "5~7월, 9~11월"
  private static final String MONTHS = "(?:\\d{1,2}월?\\s*[·,/~]\\s*)*\\d{1,2}월";
  private static final Pattern WEEKLY_PREFIX_MONTHS = Pattern.compile(
      "\\s*(?:20\\d{2}년\\s*)?(" + MONTHS + ")?\\s*(?:기간\\s*)?(?:중)?\\s*");
  private static final Pattern WEEKLY_PREFIX_RANGE = Pattern.compile("[\\s~(]*(?:기간\\s*)?(?:중)?\\s*");
  private static final Pattern NUMBER = Pattern.compile("\\d{1,2}");

  // 앞이 숫자 · 한글이면 요일이 아님: "9월 16:00" · "9 월 휴관" · "매일 휴관"
  private static final String NOT_AFTER = "(?<![\\d가-힣])(?<!\\d\\s)";
  private static final Pattern WEEKDAY_RANGE = Pattern.compile(NOT_AFTER + "(" + WD + ")(?:요일)?\\s*~\\s*(" + WD + ")요일");
  private static final Pattern OFF_DAYS = Pattern.compile(
      NOT_AFTER + "(" + DAY + "(?:\\s*(?:,|·|및)\\s*" + DAY + ")*)\\s*(?:및\\s*(?:법정\\s*)?공휴일\\s*)?(?:은|는|에는)?\\s*" + OFF_WORD);
  // 단독 요일: "일요일 09:30" · "금 19:30"
  private static final Pattern SINGLE_DAY = Pattern.compile(NOT_AFTER + "(?:(" + WD + ")요일|(" + WD + ")(?=\\s*\\d{1,2}:\\d{2}))");
  private static final Pattern EVERY_DAY = Pattern.compile("^\\s*매일\\s*$");

  // 한 단계의 결과. failReason 이 있으면 뒤 단계로 넘어가지 않고 UNKNOWN
  private record Reading(List<LocalDate> dates, String source, Integer expectedCount, String failReason) {

    static Reading of(List<LocalDate> dates, String source) {
      Matcher m = TOTAL_COUNT.matcher(source);
      return new Reading(dates, source, m.find() ? Integer.valueOf(m.group(1)) : null, null);
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

    String time = field(in.timeText());
    String weekday = field(in.weekdayText());
    List<String> bodyLines = bodyLines(in.body());
    List<String> labeled = bodyLines.stream().filter(ScheduleExtractor::isAllowedLine).toList();
    boolean holiday = holidayMentioned(in);

    Reading weeklyFound = first(weekly(weekday, start, end), oneOf(labeled, l -> weekly(content(l), l, start, end), "매주"));

    Reading listed = first(dateList(time, time, start, end), labeledList(labeled, start, end));
    if (listed != null) {
      // 나열과 매주 문구가 같이 있으면 어느 쪽이 전체 일정인지 모름
      if (listed.failReason() == null && weeklyFound != null) return ScheduleResult.unknown("날짜 나열과 매주 문구가 함께 있음");
      return finish(listed, start, end, false);
    }

    if (weeklyFound != null) {
      String conflict = weeklyFound.failReason() == null ? rowConflict(weeklyFound.dates(), bodyLines, start, end) : null;
      return conflict != null ? ScheduleResult.unknown(conflict) : finish(weeklyFound, start, end, holiday);
    }

    Reading byDays = first(weekdayRule(time, time, start, end), first(weekdayRule(weekday, weekday, start, end),
        oneOf(labeled, l -> weekdayRule(content(l), l, start, end), "요일 범위")));
    if (byDays != null) return finish(byDays, start, end, holiday);

    for (String line : labeled) {
      if (EVERY_DAY.matcher(stripTimes(content(line))).find()) {
        return finish(Reading.of(expand(start, end, EnumSet.allOf(DayOfWeek.class), Set.of()), line), start, end, holiday);
      }
    }
    return ScheduleResult.unknown("읽을 수 있는 일정 표현 없음");
  }

  private static ScheduleResult finish(Reading r, LocalDate start, LocalDate end, boolean holidayNote) {
    if (r.failReason() != null) return ScheduleResult.unknown(r.failReason());
    String problem = ScheduleValidator.check(start, end, r.dates(), r.expectedCount());
    if (problem != null) return ScheduleResult.unknown(problem);
    return ScheduleResult.dates(r.dates(), CaptionText.limit(r.source(), 1000), holidayNote);
  }

  // 요일로 펼치면 공휴일을 계산하지 않음 → 원문 어디든 공휴일이 나오면 화면에 표시 (설계 6번)
  private static boolean holidayMentioned(ScheduleInput in) {
    return (in.timeText() != null && in.timeText().contains("공휴일"))
        || (in.weekdayText() != null && in.weekdayText().contains("공휴일"))
        || (in.body() != null && in.body().contains("공휴일"));
  }

  // ===== ② 날짜 나열: "~" 로 이어지지 않은 날짜 2개 이상 =====
  // 범위는 null → 다음 단계. 날짜는 여러 개인데 다른 글자가 섞이면 실패 (쉬는 날 · 연기 날짜를 진행으로 읽지 않게)
  private static Reading dateList(String text, String source, LocalDate start, LocalDate end) {
    if (text == null) return null;
    String t = TIME.matcher(TIME_RANGE.matcher(text).replaceAll(" ")).replaceAll(" ");
    if (t.contains("~") || t.contains("-")) return null;

    Matcher m = DATE_TOKEN.matcher(t);
    TreeSet<LocalDate> dates = new TreeSet<>();
    StringBuilder rest = new StringBuilder();
    int tokens = 0;
    int last = 0;
    while (m.find()) {
      tokens++;
      rest.append(t, last, m.start());
      last = m.end();
      LocalDate date = resolve(m, start, end);
      if (date == null) return Reading.fail("없는 날짜: " + m.group());
      if (date.getDayOfWeek() != WEEKDAYS.get(m.group(4))) return Reading.fail("요일이 달력과 다름: " + m.group());
      Matcher cancelled = CANCELLED.matcher(t.substring(m.end()));
      if (cancelled.find()) {
        last = m.end() + cancelled.end();
      } else {
        dates.add(date);
      }
    }
    if (tokens < 2) return null;
    rest.append(t.substring(last));
    if (LEFTOVER.matcher(TOTAL_COUNT.matcher(rest).replaceAll(" ")).find() || rest.indexOf("(") >= 0) {
      return Reading.fail("날짜 나열에 다른 글자가 섞임");
    }
    return Reading.of(List.copyOf(dates), source);
  }

  // 본문의 나열 줄끼리 다르거나, 날짜 하나짜리 라벨 줄이 같이 있으면 어느 쪽이 전체인지 모름
  private static Reading labeledList(List<String> labeled, LocalDate start, LocalDate end) {
    Reading found = oneOf(labeled, l -> dateList(content(l), l, start, end), "날짜 나열");
    if (found == null || found.failReason() != null) return found;
    for (String line : labeled) {
      String t = TIME_RANGE.matcher(content(line)).replaceAll(" ");
      Matcher m = DATE_TOKEN.matcher(t);
      if (m.find() && !m.find() && !t.contains("~") && !t.contains("-")) return Reading.fail("날짜 나열 줄과 단일 날짜 줄이 함께 있음");
    }
    return found;
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

  // ===== ③ 매주 요일: [기간 또는 달] 매주 [요일] [시간] 모양만 =====
  private static Reading weekly(String text, LocalDate start, LocalDate end) {
    return weekly(text, text, start, end);
  }

  private static Reading weekly(String text, String source, LocalDate start, LocalDate end) {
    if (text == null) return null;
    int at = text.indexOf("매주");
    if (at < 0) return null;
    if (text.lastIndexOf("매주") != at) return Reading.fail("매주가 여러 번 나옴");
    String after = text.substring(at + 2);
    Matcher m = WEEKLY_DAYS.matcher(after);
    if (!m.find()) return Reading.fail("매주 뒤의 요일을 못 읽음");
    String rest = after.substring(m.end());
    // "매주 월요일 휴관" 은 진행 요일이 아니라 쉬는 요일 → ④ 에서 읽음
    if (OFF_START.matcher(rest).find()) return null;
    if (rest.contains("부터")) return Reading.fail("도중에 요일이 바뀜");
    String restLeft = WEEKLY_REST_OK.matcher(TOTAL_COUNT.matcher(stripTimes(rest)).replaceAll(" ")).replaceAll(" ");
    if (LEFTOVER.matcher(restLeft).find()) return Reading.fail("매주 문구에 조건이 붙음");
    Set<Integer> months = monthsBefore(text.substring(0, at));
    if (months == null) return Reading.fail("매주 앞의 조건을 못 읽음");
    return Reading.of(expand(start, end, weekdaysOf(m.group(1)), months), source);
  }

  // 빈 집합 = 달 한정 없음, null = 못 읽음 ("9월부터" · "7·8월 제외" · "~ 10월")
  private static Set<Integer> monthsBefore(String prefix) {
    Matcher tokens = DATE_TOKEN.matcher(prefix);
    if (tokens.find()) {
      return WEEKLY_PREFIX_RANGE.matcher(DATE_TOKEN.matcher(prefix).replaceAll(" ")).matches() ? Set.of() : null;
    }
    Matcher m = WEEKLY_PREFIX_MONTHS.matcher(prefix);
    if (!m.matches()) return null;
    return m.group(1) == null ? Set.of() : parseMonths(m.group(1));
  }

  private static Set<Integer> parseMonths(String text) {
    Set<Integer> months = new LinkedHashSet<>();
    for (String part : text.split("[·,/]")) {
      List<Integer> numbers = new ArrayList<>();
      Matcher n = NUMBER.matcher(part);
      while (n.find()) numbers.add(Integer.parseInt(n.group()));
      if (numbers.stream().anyMatch(x -> x < 1 || x > 12)) return null;
      if (numbers.size() == 1) {
        months.add(numbers.get(0));
      } else if (numbers.size() == 2 && part.contains("~")) {
        // "11~2월" 은 해를 넘김
        for (int month = numbers.get(0); month != numbers.get(1); month = month % 12 + 1) months.add(month);
        months.add(numbers.get(1));
      } else {
        return null;
      }
    }
    return months;
  }

  // "매주" 문구와 회차 표가 어긋나면 어느 쪽도 믿지 않음. 표에 나온 달에 한해 비교 (본문이 갱신되며 지난 달 표가 사라지는 일이 있음)
  private static String rowConflict(List<LocalDate> weekly, List<String> bodyLines, LocalDate start, LocalDate end) {
    Set<LocalDate> rows = new TreeSet<>();
    for (String line : bodyLines) {
      if (FORBIDDEN.matcher(line).find()) continue;
      for (String cell : line.split("\\|")) {
        String t = TIME_RANGE.matcher(cell).replaceAll(" ");
        Matcher m = DATE_TOKEN.matcher(t);
        if (t.contains("~") || !m.find() || !t.substring(0, m.start()).isBlank()) continue;
        LocalDate date = resolve(m, start, end);
        if (date != null && !date.isBefore(start) && !date.isAfter(end)) rows.add(date);
      }
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
    Matcher span = WEEKDAY_SPAN.matcher(plain);
    while (span.find()) days.addAll(cycle(span.group(1), span.group(2)));
    Matcher single = WEEKDAY_CHAR.matcher(plain);
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

  private static List<LocalDate> expand(LocalDate start, LocalDate end, Set<DayOfWeek> days, Set<Integer> months) {
    return start.datesUntil(end.plusDays(1))
        .filter(d -> days.contains(d.getDayOfWeek()))
        .filter(d -> months.isEmpty() || months.contains(d.getMonthValue()))
        .toList();
  }

  // ===== ④ 요일 범위 · 쉬는 요일 =====
  // 인정하는 모양: 쉬는 요일만 / 범위 하나만 / 진행 요일 + 쉬는 요일이 7요일을 정확히 나눔 ("화~토 … 일 … 월요일 휴관")
  // → "토요일 14:00 (월요일 미운영)" · "수~금요일 (월요일 휴관)" · "월~금 … / 토요일 …" 은 모름
  private static Reading weekdayRule(String text, String source, LocalDate start, LocalDate end) {
    if (text == null) return null;
    String t = ENTRY_CLOSE.matcher(DATE_RANGE.matcher(text).replaceAll(" ")).replaceAll(" ").replace("매주", " ");

    Set<DayOfWeek> off = EnumSet.noneOf(DayOfWeek.class);
    Matcher offDays = OFF_DAYS.matcher(t);
    while (offDays.find()) off.addAll(weekdaysOf(offDays.group(1)));
    t = offDays.replaceAll(" ");

    Set<DayOfWeek> open = EnumSet.noneOf(DayOfWeek.class);
    int ranges = 0;
    Matcher range = WEEKDAY_RANGE.matcher(t);
    while (range.find()) {
      ranges++;
      open.addAll(cycle(range.group(1), range.group(2)));
    }
    t = range.replaceAll(" ");

    int singles = 0;
    Matcher single = SINGLE_DAY.matcher(t);
    while (single.find()) {
      singles++;
      open.add(WEEKDAYS.get(single.group(1) != null ? single.group(1) : single.group(2)));
    }
    t = single.replaceAll(" ");

    if (off.isEmpty() && open.isEmpty()) return null;
    if (LEFTOVER.matcher(stripTimes(t)).find()) return Reading.fail("요일 표현에 다른 글자가 섞임");

    Set<DayOfWeek> days = EnumSet.allOf(DayOfWeek.class);
    if (off.isEmpty()) {
      if (ranges != 1 || singles != 0) return Reading.fail("요일별 시간표 또는 요일 고정");
      days = open;
    } else if (open.isEmpty()) {
      days.removeAll(off);
    } else {
      Set<DayOfWeek> both = EnumSet.copyOf(open);
      both.retainAll(off);
      Set<DayOfWeek> all = EnumSet.copyOf(open);
      all.addAll(off);
      if (!both.isEmpty() || all.size() != 7) return Reading.fail("진행 요일과 쉬는 요일이 맞지 않음");
      days = open;
    }
    return Reading.of(expand(start, end, days, Set.of()), source);
  }

  // ===== 공통 =====

  // 본문의 여러 줄이 서로 다른 날짜를 말하면 여러 프로그램이 섞인 것 → 고르지 않음
  private static Reading oneOf(List<String> lines, Function<String, Reading> reader, String kind) {
    Reading found = null;
    for (String line : lines) {
      Reading r = reader.apply(line);
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

  private static String stripTimes(String text) {
    return TIME.matcher(TIME_RANGE.matcher(text).replaceAll(" ")).replaceAll(" ");
  }

  private static boolean isAllowedLine(String line) {
    return ALLOWED_LABEL.matcher(line).find() && !FORBIDDEN.matcher(line).find();
  }

  // 라벨을 뗀 줄 내용
  private static String content(String line) {
    Matcher m = ALLOWED_LABEL.matcher(line);
    return m.find() ? line.substring(m.end()) : line;
  }

  private static List<String> bodyLines(String body) {
    if (body == null) return List.of();
    return body.lines().map(ScheduleExtractor::normalize).filter(l -> !l.isEmpty()).toList();
  }

  // 칸 값: 여러 줄을 한 줄로. 신청 · 예매 내용이면 읽지 않음
  private static String field(String value) {
    if (value == null) return null;
    String v = normalize(value.replaceAll("\\s*\\R\\s*", " "));
    return v.isEmpty() || FORBIDDEN.matcher(v).find() ? null : v;
  }

  // 물결 · 줄표 · 가운뎃점 변형을 한 글자로
  private static String normalize(String value) {
    return value.replaceAll("[～〜∼]", "~").replaceAll("[–—－―−‐ㅡ]", "-").replaceAll("[‧ㆍ・･]", "·")
        .replace(' ', ' ').strip();
  }
}
