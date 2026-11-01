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

  private static final Pattern ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
  private static final Pattern IDX = Pattern.compile("<idx>\\s*(\\d+)\\s*</idx>");
  private static final Pattern DESCRIPTION = Pattern.compile("<description>(.*?)</description>", Pattern.DOTALL);

  private static final Pattern LINE_END = Pattern.compile("(?i)<(?:br|/p|/tr|/li|/div|/h[1-6])\\b[^>]*>");
  private static final Pattern CELL_END = Pattern.compile("(?i)</t[dh]>");
  private static final Pattern TAG = Pattern.compile("<[^>]+>");

  public static Map<Long, String> byIdx(String xml) {
    Map<Long, String> bodies = new HashMap<>();
    if (xml == null) return bodies;
    Matcher item = ITEM.matcher(xml);
    while (item.find()) {
      Matcher idx = IDX.matcher(item.group(1));
      Matcher description = DESCRIPTION.matcher(item.group(1));
      if (idx.find() && description.find()) {
        bodies.put(Long.valueOf(idx.group(1)), toText(description.group(1)));
      }
    }
    return bodies;
  }

  // 한 줄에 한 항목, 표 칸은 "|" 로 구분 (ScheduleInput.body 모양)
  static String toText(String html) {
    String v = HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(html)); // 이중 인코딩되어 옴 (&amp;nbsp;)
    v = LINE_END.matcher(v).replaceAll("\n");
    v = CELL_END.matcher(v).replaceAll(" | ");
    v = TAG.matcher(v).replaceAll(" ").replace(' ', ' ');
    StringBuilder text = new StringBuilder();
    v.lines().map(l -> l.replaceAll("\\s+", " ").strip()).filter(l -> !l.isEmpty())
        .forEach(l -> text.append(l).append('\n'));
    return text.toString().stripTrailing();
  }
}
