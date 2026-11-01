package com.suwon.festival.service.schedule;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

// 재단 XML 원문에서 행사별 본문(description)을 글자로 꺼냄 (설계 docs/event-schedule.md 3번 "본문 가져오기")
// - description 은 태그가 섞여 있어 Jackson 으로 받지 않음 (EventItem.java 주석) → <item> 단위로 잘라 idx 로 연결
// - 저장하지 않음: 동기화 때마다 XML 전체를 다시 받으므로 재처리에 저장이 필요 없음
public final class ScheduleBodyText {

  private ScheduleBodyText() {
  }

  private static final Pattern ITEM = Pattern.compile("<item\\b[^>]*>(.*?)</item>", Pattern.DOTALL);
  private static final Pattern IDX = Pattern.compile("<idx>\\s*(\\d+)\\s*</idx>");
  private static final Pattern DESCRIPTION = Pattern.compile("<description\\b[^>]*>(.*?)</description>", Pattern.DOTALL);

  private static final Pattern CDATA = Pattern.compile("<!\\[CDATA\\[|]]>");
  private static final Pattern HIDDEN = Pattern.compile("(?is)<!--.*?-->|<(script|style)\\b[^>]*>.*?</\\1\\s*>");
  private static final Pattern STRUCK = Pattern.compile("(?i)<(?:del|s|strike)\\b[^<>]*>");
  private static final Pattern LINE_END = Pattern.compile("(?i)<(?:br|/p|/tr|/li|/div|/h[1-6])\\b[^>]*>");
  private static final Pattern CELL_END = Pattern.compile("(?i)</t[dh]>");
  // 영문으로 시작하는 것만 태그. "<5. 23.(토) 휴관>" · "<파랑새>" 는 글자로 남아야 추출기가 "다른 글자가 섞임"으로 판단함
  private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^<>]*>");

  public static Map<Long, String> byIdx(String xml) {
    Map<Long, String> bodies = new HashMap<>();
    if (xml == null) return bodies;
    Matcher item = ITEM.matcher(xml);
    while (item.find()) {
      Matcher description = DESCRIPTION.matcher(item.group(1));
      if (!description.find()) continue;
      // 본문 속 "<idx>" 글자를 행사 번호로 읽지 않게 본문을 뺀 나머지에서 찾음
      Matcher idx = IDX.matcher(item.group(1).substring(0, description.start()) + item.group(1).substring(description.end()));
      if (idx.find()) bodies.put(Long.valueOf(idx.group(1)), toText(description.group(1)));
    }
    return bodies;
  }

  // 한 줄에 한 항목, 표 칸은 "|" 로 구분 (ScheduleInput.body 모양)
  static String toText(String html) {
    String v = HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(CDATA.matcher(html).replaceAll(""))); // 이중 인코딩되어 옴 (&amp;nbsp;)
    v = HIDDEN.matcher(v).replaceAll(" ");
    // 원문 줄바꿈은 브라우저에서 공백 → 줄 경계로 삼으면 라벨 줄과 뒤따르는 조건이 갈라짐
    v = v.replaceAll("\\R", " ");
    v = STRUCK.matcher(v).replaceAll(" 취소선 "); // 지운 날짜를 진행으로 읽지 않게 글자를 남김 → 추출기가 모름으로 판단
    v = LINE_END.matcher(v).replaceAll("\n");
    v = CELL_END.matcher(v).replaceAll(" | ");
    v = TAG.matcher(v).replaceAll(" ").replace(' ', ' ');
    StringBuilder text = new StringBuilder();
    v.lines().map(l -> l.replaceAll("\\s+", " ").strip()).filter(l -> !l.isEmpty())
        .forEach(l -> text.append(l).append('\n'));
    return text.toString().stripTrailing();
  }
}
