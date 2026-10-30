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

  // 알려진 HTML 태그만 지움 → "<파랑새>" 같은 작품명 꺾쇠는 남김
  private static final Pattern BR_TAG = Pattern.compile("(?i)<br\\s*/?>");
  private static final Pattern HTML_TAG = Pattern.compile(
      "(?i)</?(?:p|div|span|b|strong|i|em|u|font|a|img|table|tbody|thead|tr|td|th|ul|ol|li|h[1-6])(?:\\s[^<>]*)?/?>");

  // 시간이 "딱 한 개"일 때만 인정: 14:00 / 19:00~19:40 / 16:30-18:00 / 10:00~ / 14:00~15:00 (60분)
  // 여러 회차(14:00, 17:00), 요일별, 부가설명이 붙은 것은 일부러 인정하지 않음
  private static final Pattern SINGLE_TIME = Pattern.compile(
      "^(\\d{1,2}):(\\d{2})\\s*(?:[~\\-–－]\\s*(?:(\\d{1,2}):(\\d{2}))?)?\\s*(?:\\(\\s*\\d+\\s*(?:분|시간)\\s*\\))?$");

  // 시작·끝 시간 (끝은 없을 수 있음)
  public record TimeRange(LocalTime start, LocalTime end) {
  }

  // HTML 엔티티 복원 + 태그 제거 + 줄 끝 공백 정리
  public static String clean(String value) {
    if (value == null) return null;
    String v = HtmlUtils.htmlUnescape(value);
    v = BR_TAG.matcher(v).replaceAll("\n");
    v = HTML_TAG.matcher(v).replaceAll("");
    v = v.replace(" ", " "); // &nbsp;
    v = v.lines().map(String::strip).reduce((a, b) -> a + "\n" + b).orElse("");
    return v.strip();
  }

  public static String findPlace(List<ContentInfoDetail> details) {
    for (String label : PLACE_LABELS) {
      String v = find(details, label);
      if (v != null) return v;
    }
    return null;
  }

  public static String findTimeText(List<ContentInfoDetail> details) {
    return find(details, TIME_LABEL);
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
    if (m.group(3) == null) return Optional.of(new TimeRange(start, null));

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
