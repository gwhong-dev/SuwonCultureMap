package com.suwon.festival.service.landmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import com.suwon.festival.entity.Landmark;
import com.suwon.festival.entity.SyncLog;
import com.suwon.festival.repository.LandmarkRepository;
import com.suwon.festival.repository.SyncLogRepository;
import com.suwon.festival.service.SyncLogService;
import com.suwon.festival.service.place.PlaceCandidate;
import com.suwon.festival.service.place.PlaceGeocoder;
import com.suwon.festival.service.place.PlaceSearchClient;

// 명소 동기화 — 가짜 재단 응답 · 가짜 장소 검색으로 저장 · 갱신 · 좌표 보충 · 실패 분리 확인 (실제 API 호출 없음)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LandmarkSyncServiceTest {

  @Autowired private LandmarkRepository repository;
  @Autowired private SyncLogRepository syncLogRepository;

  private final LandmarkClient client = mock(LandmarkClient.class);
  private final FakeSearch search = new FakeSearch();
  private LandmarkSyncService service;
  // 명소 서비스와 동기화 이력이 같은 시계를 씀 → 하루 1회(20시간) 규칙을 시각을 옮겨 가며 확인
  private final MovableClock clock = new MovableClock(Instant.parse("2026-11-13T00:00:00Z"));

  static final class MovableClock extends Clock {
    private Instant now;

    MovableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("Asia/Seoul");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private static final String PALACE_XML = """
      <rss><channel><title>가상 행궁</title>
        <item><idx>9101</idx><title>가상당(假想堂)</title>
          <coordinate><lat>127.0130</lat><lng>37.2820</lng></coordinate>
          <description>&lt;p&gt;1789년(정조 13) 창건&lt;br /&gt;&lt;br /&gt;가상당은 시험용 건물이다.&lt;/p&gt;</description>
          <link>http://www.swcf.or.kr/?p=63&amp;idx=9101</link><pubDate>2020-03-27 00:00:16</pubDate></item>
      </channel></rss>
      """;

  // 이름 → 검색 결과 (없으면 빈 결과). 호출 기록
  static final class FakeSearch implements PlaceSearchClient {
    final Map<String, List<PlaceCandidate>> results = new HashMap<>();
    final List<String> calls = new ArrayList<>();

    @Override
    public Page search(String query) {
      calls.add(query);
      return new Page(results.getOrDefault(query, List.of()), true);
    }
  }

  private static PlaceCandidate place(String name, double lat, double lng) {
    return new PlaceCandidate(name, "경기 수원시 팔달구 가상동 1", "경기 수원시 팔달구 가상로 1", lat, lng);
  }

  @BeforeEach
  void setUp() throws Exception {
    when(client.configured(any())).thenReturn(true);
    when(client.fetch(LandmarkFeed.FORTRESS)).thenReturn(LandmarkParserTest.sample());
    when(client.fetch(LandmarkFeed.PALACE)).thenReturn(PALACE_XML);
    service = new LandmarkSyncService(client, repository, new PlaceGeocoder(search),
        new SyncLogService(syncLogRepository, clock), clock);
  }

  private Landmark get(String source, String key) {
    return repository.findBySourceAndSourceKey(source, key).orElseThrow();
  }

  @Test
  void 두_코드를_받아_저장하고_좌표는_위도_경도로() {
    LandmarkSyncResult r = service.sync();

    assertThat(r.ok()).isTrue();
    assertThat(r.fetched()).isEqualTo(5);
    assertThat(r.created()).isEqualTo(5);
    Landmark gate = get(Landmark.SOURCE_FORTRESS, "9001");
    assertThat(gate.getLatitude()).isEqualTo(37.28891080512977);
    assertThat(gate.getLongitude()).isEqualTo(127.01426159649375);
    assertThat(gate.getCoordSource()).isEqualTo(Landmark.COORD_API);
    Landmark hall = get(Landmark.SOURCE_PALACE, "9101");
    assertThat(hall.getKind()).isEqualTo("화성행궁");
    assertThat(hall.getLatitude()).isEqualTo(37.2820);
    assertThat(get(Landmark.SOURCE_FORTRESS, "9003").isNonPublic()).isTrue();
  }

  @Test
  void 좌표_없는_곳은_장소_검색으로_보충_못_찾으면_null() {
    search.results.put("가상연못", List.of(place("가상연못", 37.2879, 127.0184)));

    LandmarkSyncResult r = service.sync();

    Landmark pond = get(Landmark.SOURCE_FORTRESS, "9002");
    assertThat(pond.getLatitude()).isEqualTo(37.2879);
    assertThat(pond.getCoordSource()).isEqualTo(Landmark.COORD_KAKAO);
    Landmark wall = get(Landmark.SOURCE_FORTRESS, "9004");
    assertThat(wall.getLatitude()).isNull();
    assertThat(wall.getCoordSource()).isNull();
    assertThat(r.geocoded()).isEqualTo(1);
    assertThat(r.noCoord()).isEqualTo(1);
    assertThat(search.calls).contains("가상연못", "미복원 가상 성벽", "수원화성 미복원 가상 성벽");
  }

  @Test
  void 이름만으로_못_찾으면_종류를_붙여_정확히_같은_이름만() {
    search.results.put("수원화성 가상연못", List.of(place("수원화성 가상연못", 37.2795, 127.0118)));

    service.sync();

    assertThat(get(Landmark.SOURCE_FORTRESS, "9002").getLatitude()).isEqualTo(37.2795);
  }

  @Test
  void 종류_이름_자체나_범위_밖_결과는_인정하지_않음() {
    // "수원화성 가상연못" 검색에 "수원화성" 만 나옴 → PlaceGeocoder 는 검색어가 품은 이름으로 인정하지만 여기서 거름
    search.results.put("수원화성 가상연못", List.of(place("수원화성", 37.2870, 127.0140)));
    // 같은 이름이지만 수원화성 범위 밖 (수원 안)
    search.results.put("미복원 가상 성벽", List.of(place("미복원 가상 성벽", 37.2600, 127.0500)));

    service.sync();

    assertThat(get(Landmark.SOURCE_FORTRESS, "9002").getLatitude()).isNull();
    assertThat(get(Landmark.SOURCE_FORTRESS, "9004").getLatitude()).isNull();
  }

  @Test
  void 다시_동기화하면_id_는_그대로_내용만_갱신_보충_좌표는_다시_검색_안_함() throws Exception {
    search.results.put("가상연못", List.of(place("가상연못", 37.2879, 127.0184)));
    service.sync();
    Long gateId = get(Landmark.SOURCE_FORTRESS, "9001").getId();
    Long pondId = get(Landmark.SOURCE_FORTRESS, "9002").getId();
    long rows = repository.count();
    search.calls.clear();

    when(client.fetch(LandmarkFeed.FORTRESS))
        .thenReturn(LandmarkParserTest.sample().replace("가상북문(假想北門)", "가상북문(假想北門) 고침"));
    LandmarkSyncResult second = service.sync();

    assertThat(repository.count()).isEqualTo(rows);
    assertThat(second.created()).isZero();
    assertThat(second.updated()).isEqualTo(1);
    assertThat(get(Landmark.SOURCE_FORTRESS, "9001").getId()).isEqualTo(gateId);
    assertThat(get(Landmark.SOURCE_FORTRESS, "9001").getName()).isEqualTo("가상북문(假想北門) 고침");
    assertThat(get(Landmark.SOURCE_FORTRESS, "9002").getId()).isEqualTo(pondId);
    assertThat(get(Landmark.SOURCE_FORTRESS, "9002").getLatitude()).isEqualTo(37.2879);
    assertThat(search.calls).doesNotContain("가상연못"); // 이미 보충한 곳
    assertThat(search.calls).contains("미복원 가상 성벽"); // 아직 좌표 없는 곳은 다시 시도
  }

  @Test
  void 한_코드가_실패해도_다른_코드는_저장_이력은_실패() throws Exception {
    when(client.fetch(LandmarkFeed.FORTRESS)).thenThrow(new ResourceAccessException("가상 연결 실패 openAPIKey=비밀"));

    LandmarkSyncResult r = service.sync();

    assertThat(r.ok()).isFalse();
    assertThat(r.failedFeeds()).containsExactly("59_C(ResourceAccessException)");
    assertThat(repository.findBySourceAndSourceKey(Landmark.SOURCE_PALACE, "9101")).isPresent();
    SyncLog last = syncLogRepository.findAll().stream()
        .filter(l -> l.getSource() == SyncLog.Source.LANDMARK).reduce((a, b) -> b).orElseThrow();
    assertThat(last.getStatus()).isEqualTo(SyncLog.Status.FAIL);
    assertThat(last.getErrorMessage()).contains("59_C").doesNotContain("비밀").doesNotContain("openAPIKey");
  }

  @Test
  void 성공하면_하루_한_번만_실패는_막지_않음() throws Exception {
    when(client.fetch(LandmarkFeed.FORTRESS)).thenThrow(new ResourceAccessException("가상"));
    service.sync();
    assertThat(service.isDue()).isTrue(); // 실패 뒤엔 다시 받음

    doReturn(LandmarkParserTest.sample()).when(client).fetch(LandmarkFeed.FORTRESS);
    service.sync();
    assertThat(service.isDue()).isFalse(); // 성공 직후

    clock.advance(Duration.ofHours(12)); // 같은 날 18시 자동 동기화
    assertThat(service.isDue()).isFalse();
    clock.advance(Duration.ofHours(8)); // 성공 20시간 뒤 (다음 날 아침)
    assertThat(service.isDue()).isTrue();
  }

  @Test
  void 재단이_좌표를_지우면_옛_재단_좌표를_비우고_다시_보충() throws Exception {
    service.sync();
    Landmark gate = get(Landmark.SOURCE_FORTRESS, "9001");
    assertThat(gate.getCoordSource()).isEqualTo(Landmark.COORD_API);

    doReturn(LandmarkParserTest.sample()
        .replace("<lat>127.01426159649375</lat>", "<lat></lat>")
        .replace("<lng>37.28891080512977</lng>", "<lng></lng>")).when(client).fetch(LandmarkFeed.FORTRESS);
    LandmarkSyncResult second = service.sync();

    gate = get(Landmark.SOURCE_FORTRESS, "9001");
    assertThat(gate.getLatitude()).isNull();
    assertThat(gate.getCoordSource()).isNull();
    assertThat(second.updated()).isEqualTo(1);
    assertThat(search.calls).contains("가상북문"); // 다시 보충 시도
  }

  @Test
  void 키가_하나만_있으면_없는_코드는_건너뛰고_성공() {
    when(client.configured(LandmarkFeed.PALACE)).thenReturn(false);

    LandmarkSyncResult r = service.sync();

    assertThat(r.ok()).isTrue();
    assertThat(r.skippedFeeds()).containsExactly("63_C");
    assertThat(r.created()).isEqualTo(4);
    assertThat(service.isDue()).isFalse(); // 성공으로 기록 → 하루 한 번만
  }

  @Test
  void 인증키가_하나도_없으면_아무것도_안_함() {
    when(client.configured(any())).thenReturn(false);

    LandmarkSyncResult r = service.sync();

    assertThat(r.configured()).isFalse();
    assertThat(repository.count()).isZero();
  }
}
