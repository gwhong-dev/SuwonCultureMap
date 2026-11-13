package com.suwon.festival.service.swdb;

import static com.suwon.festival.service.swdb.SwdbHtmlParserTest.detailPage;
import static com.suwon.festival.service.swdb.SwdbHtmlParserTest.listItem;
import static com.suwon.festival.service.swdb.SwdbHtmlParserTest.listPage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.SyncLog;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.SyncLogService;

// 수원문화DB 동기화 — 거르기 · 저장 칸 · 읽는 범위 · 스위치 · 하루 1회 (가짜 화면, 실제 요청 없음)
class SwdbSyncServiceTest {

  static final ZoneId KST = ZoneId.of("Asia/Seoul");
  // 오늘 = 2026-11-13 10:00
  final Clock clock = Clock.fixed(Instant.parse("2026-11-13T01:00:00Z"), KST);

  static class FakeClient implements SwdbClient {
    final Map<Integer, String> pages = new HashMap<>();
    final Map<String, String> details = new HashMap<>();
    final List<String> requested = new ArrayList<>();

    @Override
    public String listPage(int page) {
      requested.add("list " + page);
      return pages.getOrDefault(page, listPage());
    }

    @Override
    public String detailPage(String reqIdx) {
      requested.add("detail " + reqIdx);
      return details.get(reqIdx);
    }

    private static String listPage() {
      return SwdbHtmlParserTest.listPage();
    }
  }

  final FakeClient client = new FakeClient();
  final SwdbPlaceResolver resolver = mock(SwdbPlaceResolver.class);
  final EventRepository repository = mock(EventRepository.class);
  final SyncLogService syncLog = mock(SyncLogService.class);

  SwdbSyncService service(boolean enabled) {
    return new SwdbSyncService(client, resolver, repository, syncLog, enabled, clock);
  }

  @BeforeEach
  void setUp() {
    when(repository.findBySourceAndSourceKey(eq(Event.SOURCE_SWDB), anyString())).thenReturn(Optional.empty());
    when(resolver.resolve(anyString())).thenReturn(new SwdbPlaceResolver.Result(SwdbPlaceResolver.Status.FOUND,
        37.28, 127.01, "수원시 팔달구 가상로 1", null, null));
  }

  static final String ONGOING = "202611041917216321";
  static final String ENDED = "202610191529522061";
  static final String RECRUIT = "202606150000000001";

  void givenFirstPage() {
    client.pages.put(1, listPage(
        listItem(ONGOING, "listcategory2", "공연", "가상 연주회", "경기 수원시 영통구 반달로7번길 40", "2026-11-28"),
        listItem(ENDED, "listcategory1", "전시", "끝난 전시", "경기 수원시 권선구 호매실로 237", "2026-10-29"),
        listItem(RECRUIT, "listcategory3", "교육", "수강생 모집", "가상 교실", "2026-11-20")));
    client.details.put(ONGOING, detailPage("공연", "2026-11-28일 19:30부터 ~ 2026-11-28일 21:00까지",
        "경기 수원시 영통구 반달로7번길 40"));
    client.details.put(ENDED, detailPage("전시", "2026-10-29일 10:00부터 ~ 2026-11-08일 18:00까지",
        "경기 수원시 권선구 호매실로 237"));
  }

  @Test
  void 스위치가_꺼져_있으면_요청을_보내지_않음() {
    givenFirstPage();

    var result = service(false).sync();

    assertThat(result.ran()).isFalse();
    assertThat(client.requested).isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  void 안_끝난_행사만_저장하고_모집_공고는_상세도_안_읽음() {
    givenFirstPage();

    var result = service(true).sync();

    ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
    verify(repository).save(saved.capture());
    Event e = saved.getValue();
    assertThat(e.getSource()).isEqualTo(Event.SOURCE_SWDB);
    assertThat(e.getSourceKey()).isEqualTo(ONGOING);
    assertThat(e.getIdx()).isNull();
    assertThat(e.getTitle()).isEqualTo("가상 연주회");
    assertThat(e.getCategory()).isEqualTo("공연");
    assertThat(e.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 28));
    assertThat(e.getTimeText()).isEqualTo("19:30~21:00");
    assertThat(e.getPlace()).isEqualTo("수원시 팔달구 가상로 1");
    assertThat(e.getLatitude()).isEqualTo(37.28);
    assertThat(e.getLink()).isEqualTo("https://www.swcf.or.kr/swdb/?p=15_view&reqIdx=" + ONGOING + "&page=1");
    assertThat(e.getImageUrl()).isEqualTo("https://www.swcf.or.kr/swdb/_File/bbs//0/" + ONGOING + ".png");
    // 소개글 · 주관(개인 이름) · 문의처(번호)는 저장하지 않음
    assertThat(e.getInfo()).isNull();
    assertThat(e.getHost()).isNull();
    assertThat(e.getContact()).isNull();
    assertThat(e.getPubDate()).isEqualTo(SwdbHtmlParser.registeredAt(ONGOING));

    assertThat(client.requested).doesNotContain("detail " + RECRUIT);
    assertThat(result.saved()).isEqualTo(1);
    assertThat(result.skipped()).containsEntry(SwdbSyncService.Skip.ENDED, 1)
        .containsEntry(SwdbSyncService.Skip.RECRUIT, 1);
    verify(syncLog).success(eq(SyncLog.Source.SWDB), any(), eq(3), eq(1), eq(0), anyString());
  }

  @Test
  void 여러_장소_안내는_info_에만() {
    givenFirstPage();
    when(resolver.resolve(anyString())).thenReturn(new SwdbPlaceResolver.Result(SwdbPlaceResolver.Status.FOUND,
        37.28, 127.01, "여러 장소 · 가상홀 외 2곳", SwdbPlaceResolver.MULTI_NOTE, null));

    service(true).sync();

    ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getPlace()).isEqualTo("여러 장소 · 가상홀 외 2곳");
    assertThat(saved.getValue().getInfo()).isEqualTo(SwdbPlaceResolver.MULTI_NOTE);
  }

  @Test
  void 수원_밖과_좌표_없음은_저장_안_함() {
    givenFirstPage();
    when(resolver.resolve(anyString())).thenReturn(new SwdbPlaceResolver.Result(SwdbPlaceResolver.Status.OUTSIDE,
        null, null, null, null, "수원 밖"));

    var result = service(true).sync();

    verify(repository, never()).save(any());
    assertThat(result.skipped()).containsEntry(SwdbSyncService.Skip.OUTSIDE, 1);
  }

  @Test
  void 기간이_1년을_넘거나_거꾸로면_저장_안_함() {
    client.pages.put(1, listPage(listItem(ONGOING, "listcategory2", "공연", "가상", "가상홀", "2026-11-01")));
    client.details.put(ONGOING, detailPage("공연", "2026-11-01일 10:00부터 ~ 2028-11-01일 10:00까지", "가상홀"));

    var result = service(true).sync();

    verify(repository, never()).save(any());
    assertThat(result.skipped()).containsEntry(SwdbSyncService.Skip.BAD_PERIOD, 1);
  }

  @Test
  void 쪽_전체가_오래된_행사면_다음_쪽을_안_읽음() {
    givenFirstPage();
    client.pages.put(2, listPage(listItem("202301010000000001", "listcategory2", "공연", "옛 공연", "가상홀",
        "2023-01-10")));
    client.pages.put(3, listPage(listItem("202201010000000001", "listcategory2", "공연", "더 옛 공연", "가상홀",
        "2022-01-10")));

    service(true).sync();

    assertThat(client.requested).contains("list 1", "list 2").doesNotContain("list 3")
        .doesNotContain("detail 202301010000000001"); // 오래된 처음 보는 행사는 상세도 안 읽음
  }

  @Test
  void 저장된_진행_중_행사는_갱신() {
    givenFirstPage();
    Event stored = Event.builder().source(Event.SOURCE_SWDB).sourceKey(ONGOING).title("옛 제목").category("공연")
        .latitude(37.0).longitude(127.0).pubDate(SwdbHtmlParser.registeredAt(ONGOING))
        .startDate(LocalDate.of(2026, 11, 28)).endDate(LocalDate.of(2026, 11, 28)).build();
    when(repository.findBySourceAndSourceKey(Event.SOURCE_SWDB, ONGOING)).thenReturn(Optional.of(stored));

    var result = service(true).sync();

    assertThat(result.updated()).isEqualTo(1);
    assertThat(stored.getTitle()).isEqualTo("가상 연주회");
    assertThat(stored.getLatitude()).isEqualTo(37.28);
  }

  @Test
  void 하루_1회_두_번째는_건너뜀() {
    givenFirstPage();
    SwdbSyncService s = service(true);

    assertThat(s.syncIfDue().ran()).isTrue();
    int requests = client.requested.size();
    var second = s.syncIfDue();

    assertThat(second.ran()).isFalse();
    assertThat(client.requested).hasSize(requests);
  }

  @Test
  void 사진은_수원문화DB_주소만() {
    assertThat(SwdbSyncService.imageUrl("/swdb/_File/a.png")).isEqualTo("https://www.swcf.or.kr/swdb/_File/a.png");
    assertThat(SwdbSyncService.imageUrl("https://other.example/a.png")).isNull();
    assertThat(SwdbSyncService.imageUrl(null)).isNull();
  }

  @Test
  void 모집_판정() {
    assertThat(SwdbSyncService.isRecruitment("2026 수원시민예술학교 수강생 모집")).isTrue();
    assertThat(SwdbSyncService.isRecruitment("가상 연주회")).isFalse();
  }
}
