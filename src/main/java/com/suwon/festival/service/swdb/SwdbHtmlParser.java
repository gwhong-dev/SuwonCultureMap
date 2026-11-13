package com.suwon.festival.service.swdb;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

// 수원문화DB 행사정보 HTML → 값 (11/13 실제 화면 구조 기준, 결정 docs/decisions/2026-11-13-swdb-crawling.md)
//  - 목록(?p=15&page=N): <li> 하나 = 행사 하나. reqIdx · 분류 · 제목 · 장소(주소) · 날짜(시작일) · 대표 이미지
//  - 상세(?p=15_view&reqIdx=): "행사 상세보기" 표의 th/td 쌍. 행사일시 = "2026-10-08일 10:30부터 ~ 2026-10-20일 20:30까지"
//  - 소개글 · 주관단체 및 개인(개인 이름) · 문의처(휴대폰 번호)는 읽지 않음 (저작물 · 개인정보)
//  - 구조가 바뀌어 못 읽으면 빈 목록 / null → 동기화가 그 행사를 건너뜀 (틀린 값을 만들지 않음)
public final class SwdbHtmlParser {

  // 목록 한 칸: 사진 칸(img4x3)으로 시작하는 <li>
  private static final Pattern LIST_ITEM = Pattern.compile("<li>\\s*<div class=\"img img4x3\">(.*?)</li>", Pattern.DOTALL);
  private static final Pattern REQ_IDX = Pattern.compile("reqIdx=(\\d{14,20})");
  private static final Pattern CATEGORY = Pattern.compile("class=\"listcategory\\d\">([^<]*)<");
  private static final Pattern TITLE = Pattern.compile("<strong>(.*?)</strong>", Pattern.DOTALL);
  private static final Pattern ADDRESS = Pattern.compile("icon_location\"></i>([^<]*)<");
  private static final Pattern DATE = Pattern.compile("icon_calendar\"></i>\\s*(\\d{4}-\\d{2}-\\d{2})");
  private static final Pattern IMAGE = Pattern.compile("<img src=\"(/swdb/[^\"]+)\"");

  private static final Pattern ROW = Pattern.compile(
      "<th scope=['\"]row['\"]>\\s*([^<]+?)\\s*</th>\\s*<td[^>]*>(.*?)</td>", Pattern.DOTALL);
  // "2026-10-08일 10:30부터 ~ 2026-10-20일 20:30까지" (시간 칸은 "15:00~17:0" 처럼 흐트러진 것도 있음)
  // 종료일이 빈 "2026-06-27일 15:00부터 ~ 일 까지" 도 있음 (11/13 실측 1건) → 시작일 하루로 봄
  private static final Pattern WHEN = Pattern.compile(
      "(\\d{4}-\\d{2}-\\d{2})일?\\s*([0-9:~ ]*)부터\\s*~\\s*(\\d{4}-\\d{2}-\\d{2})?일?\\s*([0-9:~ ]*)까지");
  private static final Pattern TAG = Pattern.compile("<[^>]+>");
  private static final Pattern SPACES = Pattern.compile("\\s+");
  private static final DateTimeFormatter REQ_IDX_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private SwdbHtmlParser() {
  }

  public record ListItem(String reqIdx, String category, String title, String address, LocalDate listDate,
      String imagePath) {
  }

  public record Detail(String category, LocalDate startDate, LocalDate endDate, String timeText, String place) {
  }

  public static List<ListItem> parseList(String html) {
    List<ListItem> items = new ArrayList<>();
    if (html == null) return items;
    Matcher m = LIST_ITEM.matcher(html);
    while (m.find()) {
      String li = m.group(1);
      String reqIdx = first(REQ_IDX, li);
      String title = text(first(TITLE, li));
      if (reqIdx == null || title.isEmpty()) continue; // 키 · 제목 없는 칸은 버림
      items.add(new ListItem(reqIdx, text(first(CATEGORY, li)), title, text(first(ADDRESS, li)),
          date(first(DATE, li)), first(IMAGE, li)));
    }
    return items;
  }

  // 표를 못 찾거나 행사일시를 못 읽으면 null
  public static Detail parseDetail(String html) {
    if (html == null) return null;
    Map<String, String> rows = new LinkedHashMap<>();
    Matcher m = ROW.matcher(html);
    while (m.find()) {
      rows.putIfAbsent(text(m.group(1)), text(m.group(2)));
    }
    String when = rows.get("행사일시");
    if (when == null) return null;
    Matcher w = WHEN.matcher(when);
    if (!w.find()) return null;
    LocalDate start = date(w.group(1));
    LocalDate end = w.group(3) == null ? start : date(w.group(3));
    if (start == null || end == null) return null;
    return new Detail(rows.get("활동분야"), start, end, timeText(w.group(2), w.group(4)), rows.get("행사장소"));
  }

  // 시작 · 끝 시간 → "10:30~20:30". 시간 칸 안에 이미 "~" 가 있으면 그대로, 시간이 없으면 null
  static String timeText(String startPart, String endPart) {
    String s = startPart == null ? "" : startPart.strip();
    String e = endPart == null ? "" : endPart.strip();
    if (s.isEmpty()) return null;
    if (s.contains("~")) return s;
    if (e.isEmpty() || e.equals(s)) return s;
    return s + "~" + e;
  }

  // reqIdx 앞 14자리 = 등록 시각 (예: 202611061144388032 → 2026-11-06 11:44:38). 못 읽으면 null
  public static LocalDateTime registeredAt(String reqIdx) {
    if (reqIdx == null || reqIdx.length() < 14) return null;
    try {
      return LocalDateTime.parse(reqIdx.substring(0, 14), REQ_IDX_TIME);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String first(Pattern p, String s) {
    Matcher m = p.matcher(s);
    return m.find() ? m.group(1) : null;
  }

  // 태그를 지우고 &amp; 같은 글자를 되돌린 뒤 공백 정리
  static String text(String html) {
    if (html == null) return "";
    String noTags = TAG.matcher(html).replaceAll(" ");
    String unescaped = HtmlUtils.htmlUnescape(noTags).replace(' ', ' ');
    return SPACES.matcher(unescaped).replaceAll(" ").strip();
  }

  private static LocalDate date(String s) {
    if (s == null) return null;
    try {
      return LocalDate.parse(s);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
