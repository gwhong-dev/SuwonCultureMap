package com.suwon.festival.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;

import lombok.RequiredArgsConstructor;

// 행사 1건 → 캘린더 일정으로 바꿔주는 서비스 (A안: 종일 일정)
// - 아이폰 기본 캘린더: .ics 파일 (캘린더 공용 형식, RFC 5545)
// - 구글 캘린더: "일정 추가" 화면 주소 (제목·날짜가 미리 채워짐)
// 시간·장소명은 아직 따로 저장하지 않으므로 info(메모)에 그대로 담음
@Service
@RequiredArgsConstructor
public class EventCalendarService {

  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE; // 20261107
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
      .withZone(ZoneOffset.UTC);
  private static final String GOOGLE_BASE = "https://calendar.google.com/calendar/render?action=TEMPLATE";
  private static final int GOOGLE_INFO_MAX = 500; // 주소가 너무 길면 브라우저·구글이 거부할 수 있어 메모(info)를 자름 (한글 1글자 = 주소 9자)
  private static final int ICS_LINE_MAX_BYTES = 75; // .ics 규칙: 한 줄 최대 75바이트(한글은 1글자 3바이트)

  private final EventRepository eventRepository;

  // 없는 행사 → 404, 날짜 없는 행사 → 일정을 만들 수 없으므로 404
  public Event findSchedulable(Long id) {
    Event event = eventRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "행사를 찾을 수 없습니다."));
    if (event.getStartDate() == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "날짜 정보가 없는 행사라 캘린더에 추가할 수 없습니다.");
    }
    return event;
  }

  // ===== .ics 파일 내용 =====
  public String toIcs(Event event, Instant now) {
    LocalDate start = event.getStartDate();
    LocalDate endExclusive = endExclusive(event); // 종일 일정의 끝 날짜는 "다음 날"로 적는 게 규칙

    StringBuilder sb = new StringBuilder();
    line(sb, "BEGIN:VCALENDAR");
    line(sb, "VERSION:2.0");
    line(sb, "PRODID:-//SuwonCultureMap//KO");
    line(sb, "CALSCALE:GREGORIAN");
    line(sb, "METHOD:PUBLISH");
    line(sb, "BEGIN:VEVENT");
    line(sb, "UID:event-" + event.getId() + "@suwonculturemap");
    line(sb, "DTSTAMP:" + STAMP.format(now));
    line(sb, "DTSTART;VALUE=DATE:" + DATE.format(start));
    line(sb, "DTEND;VALUE=DATE:" + DATE.format(endExclusive));
    line(sb, "SUMMARY:" + escape(event.getTitle()));
    // .ics 는 원문 링크를 URL 칸에 따로 넣으므로 메모에는 info만 (아이폰에서 링크가 두 번 보이는 문제 방지)
    String info = info(event);
    if (!info.isEmpty()) {
      line(sb, "DESCRIPTION:" + escape(info));
    }
    line(sb, "GEO:" + coord(event.getLatitude()) + ";" + coord(event.getLongitude()));
    String link = safeLink(event.getLink());
    if (link != null) {
      line(sb, "URL:" + link);
    }
    line(sb, "END:VEVENT");
    line(sb, "END:VCALENDAR");
    return sb.toString();
  }

  // ===== 구글 캘린더 "일정 추가" 주소 =====
  public String toGoogleUrl(Event event) {
    String dates = DATE.format(event.getStartDate()) + "/" + DATE.format(endExclusive(event));
    // info만 자르고 원문 링크는 항상 끝에 남김
    String details = joinDescription(cut(info(event), GOOGLE_INFO_MAX), event.getLink());
    return GOOGLE_BASE
        + "&text=" + encode(event.getTitle())
        + "&dates=" + dates
        + "&details=" + encode(details)
        + "&location=" + encode(event.getLatitude() + "," + event.getLongitude());
  }

  // 종료일이 없으면 하루짜리 일정
  private LocalDate endExclusive(Event event) {
    LocalDate end = event.getEndDate() != null ? event.getEndDate() : event.getStartDate();
    if (end.isBefore(event.getStartDate())) {
      end = event.getStartDate(); // 원본 데이터가 뒤바뀐 경우 대비
    }
    return end.plusDays(1);
  }

  private String info(Event event) {
    return event.getInfo() != null ? event.getInfo().strip() : "";
  }

  // 구글 메모: 행사 정보(시간·장소 포함) + 원문 링크 (구글 "일정 추가" 화면엔 링크 칸이 없음)
  private String joinDescription(String info, String rawLink) {
    String link = safeLink(rawLink);
    String linkLine = link != null ? "원문: " + link : "";
    if (info.isEmpty()) return linkLine;
    if (linkLine.isEmpty()) return info;
    return info + "\n\n" + linkLine;
  }

  // 원문 링크 검사: 앞뒤 공백 제거, 줄바꿈·공백이 섞였거나 http(s) 주소가 아니면 버림
  // (.ics 에 줄바꿈이 그대로 들어가면 다른 항목이 끼어들 수 있음)
  static String safeLink(String link) {
    if (link == null) return null;
    String v = link.strip();
    if (v.isEmpty() || !v.matches("(?i)https?://\\S+")) return null;
    return v;
  }

  // 글자 수 제한: 이모지처럼 2칸짜리 글자(서로게이트 쌍)가 반으로 잘리지 않게
  static String cut(String value, int max) {
    if (value.length() <= max) return value;
    int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
    return value.substring(0, end) + "…";
  }

  // 좌표는 소수 6자리(약 10cm)면 충분
  private static String coord(Double value) {
    return String.format(java.util.Locale.ROOT, "%.6f", value);
  }

  private String encode(String value) {
    // '+'는 주소에서 "공백"으로 읽히므로 따로 %2B 로 바꿈
    return UriUtils.encodeQueryParam(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%2B");
  }

  // .ics 특수문자 처리: \ ; , 와 줄바꿈
  static String escape(String value) {
    if (value == null) return "";
    return value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n");
  }

  // .ics 줄 쓰기: 75바이트를 넘으면 "줄바꿈 + 공백"으로 접음 (한글이 중간에 잘리지 않게 글자 단위로)
  static void line(StringBuilder sb, String content) {
    int bytes = 0;
    int i = 0;
    while (i < content.length()) {
      int cp = content.codePointAt(i);
      int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
      if (bytes + len > ICS_LINE_MAX_BYTES) {
        sb.append("\r\n ");
        bytes = 1; // 이어지는 줄은 맨 앞 공백 1바이트부터 셈
      }
      sb.appendCodePoint(cp);
      bytes += len;
      i += Character.charCount(cp);
    }
    sb.append("\r\n");
  }
}
