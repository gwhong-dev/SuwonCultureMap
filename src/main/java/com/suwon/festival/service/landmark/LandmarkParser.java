package com.suwon.festival.service.landmark;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.suwon.festival.entity.Landmark;

// 재단 명소 XML(59_C · 63_C) → 저장할 값 (실제 응답 11/13 확인: 59_C 48건 · 63_C 27건)
//  - 좌표: 재단 응답은 lat 칸에 경도(127.x), lng 칸에 위도(37.x). 값 범위로 판정해 뒤집음 → 재단이 고쳐도 그대로 맞음
//  - 좌표가 빈 곳(10건)은 null → LandmarkSyncService 가 카카오 장소 검색으로 보충
//  - 이름: 제목 "화령전(華寧殿) - 비공개" → 이름 "화령전(華寧殿)" + 비공개 true (빼지 않음)
//  - 본문(description): HTML 이 이중으로 이스케이프된 국문 + 영문. 영문 단락(<div class="line"> 구분선 뒤)은 버림
//    · 첫머리의 짧은 줄("1794년(정조 18) 창건" "한국전쟁 때 문루 파괴" "1975년 문루 복원")은 history 한 줄로
//  - description 은 행사 XML 처럼 Jackson 으로 받지 않고 원문에서 idx 로 잘라 씀 (태그가 날것으로 와도 구조 파싱이 꼬이지 않게)
public final class LandmarkParser {

  private LandmarkParser() {
  }

  // 저장할 값 1건 (좌표는 바로잡은 위도 · 경도, 없으면 null)
  public record Parsed(String sourceKey, Double lat, Double lng, Landmark.Content content) {
  }

  static final int DESCRIPTION_MAX = 8000; // TEXT 칸(64KB) 안, 국문 3바이트 기준 넉넉하게
  static final int HISTORY_LINE_MAX = 40;  // 창건 · 복원 줄 최대 글자 수
  static final int HISTORY_LINES_MAX = 5;

  // 한국 안 대략 범위 — 이 범위로 어느 칸이 위도인지 판정
  static final double LAT_MIN = 33, LAT_MAX = 39;
  static final double LNG_MIN = 124, LNG_MAX = 132;

  private static final DateTimeFormatter PUB_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final Pattern ITEM = Pattern.compile("<item\\b[^>]*>(.*?)</item>", Pattern.DOTALL);
  private static final Pattern IDX = Pattern.compile("<idx>\\s*(\\d+)\\s*</idx>");
  private static final Pattern DESCRIPTION = Pattern.compile("<description\\b[^>]*>(.*?)</description>", Pattern.DOTALL);

  private static final Pattern CDATA = Pattern.compile("<!\\[CDATA\\[|]]>");
  private static final Pattern HIDDEN = Pattern.compile("(?is)<!--.*?-->|<(script|style)\\b[^>]*>.*?</\\1\\s*>");
  // 영문 단락 앞 구분선 <div class="line">
  private static final Pattern ENGLISH_DIVIDER = Pattern.compile("(?i)<div\\b[^>]*\\bclass\\s*=\\s*[\"']?line\\b");
  private static final Pattern LINE_END = Pattern.compile("(?i)<(?:br|/p|/tr|/li|/div|/h[1-6])\\b[^>]*>");
  private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^<>]*>");
  private static final Pattern HANGUL = Pattern.compile("[가-힣]");
  private static final Pattern LATIN_WORDS = Pattern.compile("[A-Za-z]{2,}");
  // 창건 · 복원 줄에 꼭 하나는 있어야 하는 말 (연도 · 문화재 지정)
  private static final Pattern HISTORY_MARK = Pattern.compile("\\d{3,4}년|제\\s?\\d+\\s?호|사적|보물|국보");
  private static final Pattern NON_PUBLIC = Pattern.compile("\\s*(?:[-–—]\\s*비공개|[(\\[]\\s*비공개\\s*[)\\]])\\s*$");

  public static List<Parsed> parse(String xml, LandmarkFeed feed) throws Exception {
    Rss rss = mapper().readValue(xml, Rss.class);
    // 점검 안내 · 오류 페이지처럼 목록 모양이 아니면 실패 (행사 동기화와 같은 기준)
    if (rss == null || rss.channel == null) {
      throw new IllegalStateException("재단 응답에 channel 이 없음 (점검 · 오류 페이지일 수 있음)");
    }
    if (rss.channel.item == null) return List.of();
    Map<String, String> bodies = descriptions(xml);
    List<Parsed> out = new ArrayList<>();
    for (Item item : rss.channel.item) {
      if (item == null || item.idx == null || item.idx.isBlank() || item.title == null || item.title.isBlank()) {
        continue; // 번호 · 이름이 없으면 가리킬 수 없음
      }
      out.add(toParsed(item, bodies.get(item.idx.strip()), feed));
    }
    return out;
  }

  private static Parsed toParsed(Item item, String rawDescription, LandmarkFeed feed) {
    String title = HtmlUtils.htmlUnescape(item.title).strip();
    boolean nonPublic = title.contains("비공개");
    String name = limit(NON_PUBLIC.matcher(title).replaceFirst("").strip(), Landmark.NAME_MAX);

    List<String> lines = textLines(rawDescription);
    int historyEnd = historyEnd(lines);
    String history = historyEnd == 0 ? null : limit(String.join(" · ", lines.subList(0, historyEnd)), Landmark.HISTORY_MAX);
    String body = String.join("\n", lines.subList(historyEnd, lines.size()));

    double[] coord = item.coordinate == null ? null : coordinates(item.coordinate.lat, item.coordinate.lng);

    Landmark.Content content = new Landmark.Content(name, feed.kind, history, nonPublic,
        limit(toHttps(firstPhoto(item)), Landmark.URL_MAX), limit(blankToNull(item.link), Landmark.URL_MAX),
        body.isBlank() ? null : limit(body, DESCRIPTION_MAX), pubDate(item.pubDate));
    return new Parsed(item.idx.strip(), coord == null ? null : coord[0], coord == null ? null : coord[1], content);
  }

  // 두 칸 중 위도 범위(33~39)인 값을 위도로. 둘 다 숫자가 아니거나 범위 밖이면 null
  static double[] coordinates(String a, String b) {
    Double x = number(a);
    Double y = number(b);
    if (x == null || y == null) return null;
    if (isLat(x) && isLng(y)) return new double[] { x, y };
    if (isLat(y) && isLng(x)) return new double[] { y, x }; // 재단 응답 (11/13 기준 전부 이쪽)
    return null;
  }

  private static boolean isLat(double v) {
    return v >= LAT_MIN && v <= LAT_MAX;
  }

  private static boolean isLng(double v) {
    return v >= LNG_MIN && v <= LNG_MAX;
  }

  private static Double number(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      double v = Double.parseDouble(s.strip());
      return Double.isFinite(v) ? v : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // 원문 XML 에서 항목별 본문 (본문 속 "<idx>" 글자를 번호로 읽지 않게 본문을 뺀 나머지에서 찾음 — ScheduleBodyText 와 같은 방식)
  static Map<String, String> descriptions(String xml) {
    Map<String, String> out = new HashMap<>();
    if (xml == null) return out;
    Matcher item = ITEM.matcher(xml);
    while (item.find()) {
      String inner = item.group(1);
      Matcher d = DESCRIPTION.matcher(inner);
      if (!d.find()) continue;
      Matcher idx = IDX.matcher(inner.substring(0, d.start()) + inner.substring(d.end()));
      if (idx.find()) out.put(idx.group(1), d.group(1));
    }
    return out;
  }

  // 본문 → 국문 줄 목록 (빈 줄 없음)
  static List<String> textLines(String raw) {
    if (raw == null) return List.of();
    // 이중 이스케이프 (&lt;p&gt; · &amp;lsquo;) → 두 번 풂
    String v = HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(CDATA.matcher(raw).replaceAll("")));
    Matcher divider = ENGLISH_DIVIDER.matcher(v);
    if (divider.find()) v = v.substring(0, divider.start());
    v = HIDDEN.matcher(v).replaceAll(" ");
    v = v.replaceAll("\\R", " "); // 원문 줄바꿈은 브라우저에서 공백
    v = LINE_END.matcher(v).replaceAll("\n");
    v = TAG.matcher(v).replaceAll(" ").replace('\u00A0', ' ');
    List<String> lines = new ArrayList<>();
    for (String line : v.split("\n")) {
      String l = line.replaceAll("\\s+", " ").strip();
      if (l.isEmpty()) continue;
      // 구분선이 없는 본문에 영문 단락이 섞여 와도 버림 (한글이 하나도 없고 영어 낱말이 있는 줄)
      if (!HANGUL.matcher(l).find() && LATIN_WORDS.matcher(l).find()) continue;
      lines.add(l);
    }
    return lines;
  }

  // 첫머리의 짧고 문장이 아닌 줄들 = 창건 · 복원 줄. 그중 연도 · 지정 표시가 하나도 없으면 0 (첫 줄부터 본문)
  static int historyEnd(List<String> lines) {
    int end = 0;
    boolean marked = false;
    while (end < lines.size() && end < HISTORY_LINES_MAX) {
      String l = lines.get(end);
      if (l.length() > HISTORY_LINE_MAX || l.endsWith(".") || l.endsWith("다")) break;
      if (HISTORY_MARK.matcher(l).find()) marked = true;
      end++;
    }
    return marked ? end : 0;
  }

  // 지오코딩용 한글 이름: "용연(龍淵)" → "용연", "북동적대 北東敵臺" → "북동적대"
  public static String koreanName(String name) {
    if (name == null) return "";
    String v = name.replaceAll("[(（][^()（）]*[)）]", " ");
    v = v.replaceAll("[\\u4E00-\\u9FFF\\u3400-\\u4DBF]+", " ");
    return v.replaceAll("\\s+", " ").strip();
  }

  // 재단 사진 주소는 http:// 로 옴 → https 화면에서 차단되지 않게 https 로 (EventSyncService.toHttps 와 같은 규칙)
  static String toHttps(String url) {
    if (url != null && url.regionMatches(true, 0, "http://", 0, 7)) {
      return "https://" + url.substring(7);
    }
    return url;
  }

  private static String firstPhoto(Item item) {
    if (item.photos == null || item.photos.photoInfo == null) return null;
    for (PhotoInfo p : item.photos.photoInfo) {
      if (p != null && p.photoUrl != null && !p.photoUrl.isBlank()) return p.photoUrl.strip();
    }
    return null;
  }

  private static LocalDateTime pubDate(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return LocalDateTime.parse(s.strip(), PUB_DATE);
    } catch (DateTimeParseException e) {
      return null; // 참고용 칸이라 형식이 틀려도 행은 저장
    }
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  private static String limit(String s, int max) {
    if (s == null || s.length() <= max) return s;
    int end = max;
    if (Character.isHighSurrogate(s.charAt(end - 1))) end--;
    return s.substring(0, end);
  }

  private static XmlMapper mapper() {
    XmlMapper m = new XmlMapper();
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return m;
  }

  // XML 모양 (description 은 일부러 안 받음 — 위 설명). 좌표는 빈 칸이 있어 글자로 받음
  @JacksonXmlRootElement(localName = "rss")
  static class Rss {
    public Channel channel;
  }

  static class Channel {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "item")
    public List<Item> item;
  }

  static class Item {
    public String idx;
    public String title;
    public Photos photos;
    public Coordinate coordinate;
    public String link;
    public String pubDate;
  }

  static class Photos {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "photoInfo")
    public List<PhotoInfo> photoInfo;
  }

  static class PhotoInfo {
    public String photoUrl;
  }

  static class Coordinate {
    public String lat;
    public String lng;
  }
}
