package com.suwon.festival.service.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.suwon.festival.dto.xml.EventItem;
import com.suwon.festival.service.EventSyncService;

// 본문 자르기 + 미리보기 집계. XML 은 재단 응답과 같은 모양으로 만든 가상 값 (태그가 이스케이프 안 된 description 포함)
class SchedulePreviewServiceTest {

  private static String item(long idx, String sdate, String edate, String details, String description) {
    return "<item><idx>" + idx + "</idx><title>행사 " + idx + " &lt;부제&gt;</title>"
        + "<coordinate><lat>127.01</lat><lng>37.28</lng></coordinate>"
        + "<eventDate><sdate>" + sdate + "</sdate><edate>" + edate + "</edate></eventDate>"
        + "<contentInfo>" + details + "</contentInfo>"
        + "<description>" + description + "</description>"
        + "<link>http://example.test/" + idx + "</link><pubDate>2026-10-31 12:00:00</pubDate></item>";
  }

  private static String info(String name, String value) {
    return "<contentInfoDetail><infoItem>" + name + "</infoItem><infoContent>" + value + "</infoContent></contentInfoDetail>";
  }

  private static String rss(String... items) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><rss version=\"2.0\"><channel><title>행사정보</title>"
        + "<description>행사정보 자료입니다.</description>" + String.join("", items) + "</channel></rss>";
  }

  @Test
  void 본문을_글자로_정리함() {
    String text = ScheduleBodyText.toText(
        "<div class=\"a\"><p>행사일정&nbsp;:  2026. 5. 16.(토)&amp;nbsp;/ 9. 19.(토)</p><br/>"
            + "<table><tr><td>운영일</td><td>3. 21.(토)</td></tr></table><img src=\"x.jpg\"></div>");
    assertThat(text.lines()).containsExactly("행사일정 : 2026. 5. 16.(토) / 9. 19.(토)", "운영일 | 3. 21.(토) |");
  }

  @Test
  void 항목별로_idx_에_연결하고_채널_설명은_무시함() {
    Map<Long, String> bodies = ScheduleBodyText.byIdx(rss(
        item(1, "2026-05-01", "2026-05-02", "", "<p>첫째</p>"),
        item(2, "2026-05-01", "2026-05-02", "", "<div><b>둘째</b></div>")));
    assertThat(bodies).containsOnly(Map.entry(1L, "첫째"), Map.entry(2L, "둘째"));
    assertThat(ScheduleBodyText.byIdx(null)).isEmpty();
  }

  @Test
  void 태그가_섞인_본문이_있어도_다음_항목이_망가지지_않음() throws Exception {
    EventItem parsed = EventSyncService.parseItems(rss(item(1, "2026-05-01", "2026-05-02", "", "<div><p>본문</p></div>"))).get(0);
    assertThat(parsed.link).isEqualTo("http://example.test/1");
    assertThat(parsed.pubDate).isEqualTo("2026-10-31 12:00:00");
  }

  @Test
  void 칸과_본문을_합쳐_결과와_개수를_돌려줌() throws Exception {
    SchedulePreviewResult r = SchedulePreviewService.fromXml(rss(
        item(1, "2026-11-14", "2026-11-14", info("시간", "14:00"), ""),
        item(2, "2026-05-16", "2026-09-19", info("시간", "토요일 14:00 ~ 17:30"), "<p>행사일정 : 2026. 5. 16.(토) / 9. 19.(토) 총 2회</p>"),
        item(3, "2026-05-01", "2026-05-31", info("시간", "18:00~21:30") + info("요일", "기간 중 매주 토요일"), ""),
        item(4, "2026-05-01", "2026-05-31", info("시간", "13:00~18:00"), "<p>안내문</p>")));

    assertThat(r.total()).isEqualTo(4);
    assertThat(r.oneDay()).isEqualTo(1);
    assertThat(r.dates()).isEqualTo(3);
    assertThat(r.unknown()).isEqualTo(1);
    assertThat(r.items()).extracting(SchedulePreviewResult.Item::idx).containsExactly(2L, 3L, 4L);

    SchedulePreviewResult.Item two = r.items().get(0);
    assertThat(two.dates()).containsExactly(LocalDate.of(2026, 5, 16), LocalDate.of(2026, 9, 19));
    assertThat(two.title()).isEqualTo("행사 2 <부제>");
    assertThat(two.onMap()).isTrue();
    assertThat(r.items().get(1).dates()).hasSize(5); // "요일" 칸에서 읽음
    assertThat(r.items().get(2).status()).isEqualTo(ScheduleResult.Status.UNKNOWN);
    assertThat(r.items().get(2).reason()).isNotBlank();
    assertThat(r.items().get(2).timeText()).isEqualTo("13:00~18:00");
  }

  @Test
  void 날짜_칸이_비어도_멈추지_않음() throws Exception {
    SchedulePreviewResult r = SchedulePreviewService.fromXml(rss(item(1, "", "", "", "")));
    assertThat(r.unknown()).isEqualTo(1);
    assertThat(r.items().get(0).status()).isEqualTo(ScheduleResult.Status.UNKNOWN);
  }
}
