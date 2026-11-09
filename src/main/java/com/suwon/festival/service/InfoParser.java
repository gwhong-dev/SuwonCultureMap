package com.suwon.festival.service;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

import com.suwon.festival.dto.xml.ContentInfoDetail;

// 재단 XML의 (항목, 내용) 쌍에서 장소·시간을 꺼내고, 글자를 정리하는 도구 (캘린더 B안)
//
// 핵심 원칙
// 1) 줄바꿈으로 합친 info 글을 다시 쪼개지 않고, 합치기 "전"의 XML 쌍에서 바로 꺼낸다
//    (내용 안에 줄바꿈이 있는 행사가 있어서, 합친 글을 쪼개면 다음 줄이 엉뚱한 항목이 됨)
// 2) 확실하지 않으면 비워 둔다(null / empty) → 캘린더는 지금처럼 종일 일정으로 내려감
public final class InfoParser {

  private InfoParser() {
  }

  // 실제 데이터 141건 점검 결과: "장소" 141건으로 통일. 없을 때만 주소 계열을 씀
  private static final List<String> PLACE_LABELS = List.of("장소", "주소/소재지", "주소");
  private static final String TIME_LABEL = "시간";
  // 상세 정보 분해 (V7). 운영 재단 121건 점검(11/9): 이용료 92 · 관람료 28 · 참가비용 1, 문의처 119, 주최/주관 121 · 주관 14
  // 앞 라벨부터 찾고, 라벨은 정확히 같을 때만 ("주차요금" 같은 것은 안 걸림)
  private static final List<String> FEE_LABELS = List.of("이용료", "관람료", "참가비용", "참가비", "입장료", "요금");
  private static final List<String> CONTACT_LABELS = List.of("문의처", "문의");
  private static final List<String> HOST_LABELS = List.of("주최/주관", "주최", "주관");
  private static final String WEEKDAY_LABEL = "요일";

  // 알려진 HTML 태그만 지움 → "<파랑새>" 같은 작품명 꺾쇠는 남김
  // <br>, <br/>, <br clear="all"> 등
  private static final Pattern BR_TAG = Pattern.compile("(?i)<br\\b[^<>]*>");
  // 속성은 "이름=값" 모양일 때만 태그로 인정 → "<A Christmas Carol>", "<I Love Suwon>" 같은 영어 작품명은 남김
  private static final Pattern HTML_TAG = Pattern.compile(
      "(?i)</?(?:p|div|span|b|strong|i|em|u|s|sup|sub|font|a|img|hr|center|table|tbody|thead|tr|td|th|ul|ol|li|h[1-6])"
          + "(?:\\s+[^<>]*=[^<>]*)?\\s*/?>");
  private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");

  // DB 칸 길이 (Event.place / Event.timeText) — 넘으면 저장 실패하므로 미리 자름
  static final int PLACE_MAX = 500;
  static final int TIME_TEXT_MAX = 1000;
  static final int FEE_MAX = 500;
  static final int CONTACT_MAX = 200;
  static final int HOST_MAX = 500;

  // 시간이 "딱 한 개"일 때만 인정: 14:00 / 19:00~19:40 / 16:30-18:00 / 10:00~ / 14:00~15:00 (60분) / 14:00 (60분)
  // 여러 회차(14:00, 17:00), 요일별, 부가설명이 붙은 것은 일부러 인정하지 않음
  // 구분 기호: ~ - – － ～ 〜 (반각·전각)
  private static final Pattern SINGLE_TIME = Pattern.compile(
      "^(\\d{1,2}):(\\d{2})\\s*(?:[~\\-–－～〜]\\s*(?:(\\d{1,2}):(\\d{2}))?)?\\s*(?:\\(\\s*(\\d+)\\s*(분|시간)\\s*\\))?$");

  // 시작·끝 시간 (끝은 없을 수 있음)
  public record TimeRange(LocalTime start, LocalTime end) {
  }

  // HTML 엔티티 복원 + 태그 제거 + 줄 끝 공백 정리
  public static String clean(String value) {
    if (value == null) return null;
    String v = HtmlUtils.htmlUnescape(value);
    v = HTML_COMMENT.matcher(v).replaceAll("");
    v = BR_TAG.matcher(v).replaceAll("\n");
    v = HTML_TAG.matcher(v).replaceAll("");
    v = v.replace(" ", " "); // &nbsp;
    v = v.lines().map(String::strip).reduce((a, b) -> a + "\n" + b).orElse("");
    return v.strip();
  }

  public static String findPlace(List<ContentInfoDetail> details) {
    for (String label : PLACE_LABELS) {
      String v = find(details, label);
      if (v != null) return limit(v, PLACE_MAX);
    }
    return null;
  }

  public static String findFee(List<ContentInfoDetail> details) {
    return limit(findFirst(details, FEE_LABELS), FEE_MAX);
  }

  public static String findContact(List<ContentInfoDetail> details) {
    return limit(findFirst(details, CONTACT_LABELS), CONTACT_MAX);
  }

  public static String findHost(List<ContentInfoDetail> details) {
    return limit(findFirst(details, HOST_LABELS), HOST_MAX);
  }

  private static String findFirst(List<ContentInfoDetail> details, List<String> labels) {
    for (String label : labels) {
      String v = find(details, label);
      if (v != null) return v;
    }
    return null;
  }

  public static String findTimeText(List<ContentInfoDetail> details) {
    return limit(find(details, TIME_LABEL), TIME_TEXT_MAX);
  }

  // 저장용 길이 제한 없는 값. 잘리면 날짜 나열의 일부만 읽힐 수 있어 진행 날짜 추출에 씀
  public static String findFullTimeText(List<ContentInfoDetail> details) {
    return find(details, TIME_LABEL);
  }

  // "요일" 칸: 실제 진행 요일이 "시간" 칸이 아니라 여기에만 적힌 행사가 있음 (설계 docs/event-schedule.md 2-2)
  public static String findWeekdayText(List<ContentInfoDetail> details) {
    return find(details, WEEKDAY_LABEL);
  }

  // 글자 수 제한 (이모지처럼 2칸짜리 글자가 반으로 잘리지 않게)
  static String limit(String value, int max) {
    if (value == null || value.length() <= max) return value;
    int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
    return value.substring(0, end);
  }

  // 항목 이름이 정확히 같은 첫 번째 값 (정리 후 비어 있으면 없는 것으로)
  private static String find(List<ContentInfoDetail> details, String label) {
    if (details == null) return null;
    for (ContentInfoDetail d : details) {
      if (d == null || d.infoItem == null) continue;
      if (label.equals(d.infoItem.strip())) {
        String v = clean(d.infoContent);
        if (v != null && !v.isEmpty()) return v;
      }
    }
    return null;
  }

  // 시간 원문 → 시작·끝 시간. 확실한 형식 한 개일 때만 값이 있음
  public static Optional<TimeRange> parseTime(String timeText) {
    if (timeText == null) return Optional.empty();
    String v = timeText.strip();
    if (v.contains("\n")) return Optional.empty(); // 여러 줄 = 여러 조건
    Matcher m = SINGLE_TIME.matcher(v);
    if (!m.matches()) return Optional.empty();

    LocalTime start = toTime(m.group(1), m.group(2));
    if (start == null) return Optional.empty();
    if (m.group(3) == null) {
      // 끝 시간이 없을 때 "(60분)"·"(1시간)" 이 있으면 그 길이로 끝 시간 계산 (자정 넘으면 모르는 것으로)
      if (m.group(5) != null) {
        int amount = Integer.parseInt(m.group(5));
        int minutes = "시간".equals(m.group(6)) ? amount * 60 : amount;
        if (minutes > 0 && start.toSecondOfDay() / 60 + minutes < 24 * 60) {
          return Optional.of(new TimeRange(start, start.plusMinutes(minutes)));
        }
      }
      return Optional.of(new TimeRange(start, null));
    }

    LocalTime end = toTime(m.group(3), m.group(4));
    if (end == null || !end.isAfter(start)) return Optional.empty(); // 자정 넘김·역전 등은 확실하지 않음
    return Optional.of(new TimeRange(start, end));
  }

  private static LocalTime toTime(String h, String m) {
    int hour = Integer.parseInt(h);
    int minute = Integer.parseInt(m);
    if (hour > 23 || minute > 59) return null;
    return LocalTime.of(hour, minute);
  }
}
