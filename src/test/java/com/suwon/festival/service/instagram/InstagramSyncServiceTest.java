package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.web.client.RestTemplate;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.EventDate;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.InstagramMedia;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;
import com.suwon.festival.service.caption.CaptionParser;
import com.suwon.festival.service.instagram.InstagramFetchException.Kind;
import com.suwon.festival.service.place.PlaceCandidate;
import com.suwon.festival.service.place.PlaceGeocoder;
import com.suwon.festival.service.place.PlaceSearchClient;
import com.suwon.festival.service.schedule.EventScheduleWriter;

// 인스타 5단계 흐름: 수집(가짜) → 정리(실제 규칙 파서) → 좌표(가짜 검색) → 저장(H2)
// 캡션은 직접 만든 가상 문장, 외부 호출 없음
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstagramSyncServiceTest {

  @Autowired private InstagramMediaRepository mediaRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private EventDateRepository eventDateRepository;
  @Autowired private StaleBlockRetirer retirer;
  @Autowired private EventScheduleWriter scheduleWriter;

  static final String ROUNDUP = String.join("\n",
      "가상시 11월 행사 모음",
      "",
      "가을 음악회",
      "🗓️ 2026. 11. 5.(목) ~ 11. 7.(토) 19:00~21:00",
      "📍 장소: 가상 아트홀",
      "",
      "겨울 빛 축제",
      "🗓 2026. 12. 28.(월) ~ 1. 3.(일)",
      "📍 가상 광장",
      "",
      "주말 장터",
      "🗓️ 매주 금~일요일",
      "📍 가상 공원");

  static final OffsetDateTime POSTED = OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.UTC);

  // 계정 이름별로 정해 둔 게시물(또는 오류)을 돌려주는 가짜 수집기
  static class FakeClient extends InstagramClient {
    final Map<String, Object> answers = new HashMap<>();
    final List<String> called = new ArrayList<>();
    boolean configured = true;

    FakeClient() {
      super(new RestTemplate(), "test-token", "17840000000000000", "v25.0");
    }

    @Override
    public boolean isConfigured() {
      return configured;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<InstagramPost> fetchRecent(String username, int limit) {
      called.add(username);
      Object a = answers.get(username);
      if (a instanceof InstagramFetchException e) throw e;
      return a == null ? List.of() : (List<InstagramPost>) a;
    }
  }

  // "가상 아트홀"만 수원 안 1건으로 찾아지는 가짜 장소 검색
  static class FakeSearch implements PlaceSearchClient {
    final List<String> queries = new ArrayList<>();

    @Override
    public Page search(String query) {
      queries.add(query);
      if (query.equals("가상 아트홀")) {
        return new Page(List.of(new PlaceCandidate("가상 아트홀", "경기 수원시 팔달구 가상동 1", null, 37.28, 127.01)), true);
      }
      return new Page(List.of(), true);
    }
  }

  private FakeClient client;
  private FakeSearch search;

  @BeforeEach
  void setUp() {
    client = new FakeClient();
    search = new FakeSearch();
  }

  private InstagramSyncService service(String accounts) {
    return service(accounts, "");
  }

  private InstagramSyncService service(String accounts, String tokenExpiresAt) {
    return service(accounts, tokenExpiresAt, Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), InstagramPostProcessor.KST));
  }

  private InstagramSyncService service(String accounts, String tokenExpiresAt, Clock clock) {
    InstagramPostProcessor processor = new InstagramPostProcessor(mediaRepository, blockRepository, eventRepository,
        new PlaceGeocoder(search), new CaptionParser(null), clock, retirer, scheduleWriter);
    return new InstagramSyncService(client, processor, new InstagramTokenExpiry(tokenExpiresAt, clock), new InstagramSwitch(true), accounts, 10);
  }

  private static InstagramPost post(String id, String caption) {
    return new InstagramPost(id, caption, "https://www.instagram.com/p/" + id + "/", POSTED, "IMAGE");
  }

  private List<Event> instagramEvents() {
    return eventRepository.findAll().stream().filter(e -> Event.SOURCE_INSTAGRAM.equals(e.getSource())).toList();
  }

  @Test
  void 토큰_만료가_가까우면_결과에_알림을_싣고_동기화는_그대로_진행() {
    client.answers.put("suwon_city", List.of(post("1790000000000001", ROUNDUP)));

    InstagramSyncResult r = service("suwon_city", "2026-11-03").sync(); // 기준일 10/31 → 3일 남음

    assertThat(r.tokenWarning()).contains("3일 뒤 만료");
    assertThat(r.posts()).isEqualTo(1);
    assertThat(service("suwon_city", "").sync().tokenWarning()).isNull();
  }

  @Test
  void 설정이_비어도_토큰_만료_알림은_전달() {
    client.configured = false;

    InstagramSyncResult r = service("suwon_city", "2026-10-01").sync();

    assertThat(r.configured()).isFalse();
    assertThat(r.tokenWarning()).contains("만료됨");
  }

  @Test
  void 행사의_진행_날짜를_날짜_줄에서_읽어_저장() {
    String caption = String.join("\n",
        "가상시 행사 모음",
        "",
        "하루 음악회",
        "🗓️ 2026. 11. 5.(목) 19:00",
        "📍 가상 아트홀",
        "",
        "회차 강연",
        "🗓️ 2026. 11. 5.(목), 11. 12.(목) 14:00",
        "📍 가상 아트홀",
        "",
        "주말 전시",
        "🗓️ 2026. 11. 6.(금) ~ 11. 15.(일) 매주 토~일",
        "📍 가상 아트홀",
        "",
        "사흘 공연",
        "🗓️ 2026. 11. 5.(목) ~ 11. 7.(토) 19:00",
        "📍 가상 아트홀");
    client.answers.put("suwon_city", List.of(post("1790000000000009", caption)));

    service("suwon_city").sync();

    Map<String, Event> byTitle = new HashMap<>();
    instagramEvents().forEach(e -> byTitle.put(e.getTitle(), e));
    assertThat(byTitle).hasSize(4);
    assertThat(runDates(byTitle.get("하루 음악회"))).containsExactly(LocalDate.of(2026, 11, 5));
    Event lecture = byTitle.get("회차 강연");
    assertThat(lecture.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(runDates(lecture)).containsExactly(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 12));
    assertThat(runDates(byTitle.get("주말 전시"))).containsExactly(
        LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15));
    Event threeDays = byTitle.get("사흘 공연"); // 매일인지 원문에 없음 → 모름 (기간만)
    assertThat(threeDays.getScheduleStatus()).isEqualTo(Event.SCHEDULE_UNKNOWN);
    assertThat(eventDateRepository.findByEventId(threeDays.getId())).isEmpty();
  }

  @Test
  void 글이_고쳐지면_다음_동기화에서_진행_날짜도_바뀜() {
    String id = "1790000000000010";
    client.answers.put("suwon_city", List.of(post(id, "회차 강연\n🗓️ 2026. 11. 5.(목), 11. 12.(목) 14:00\n📍 가상 아트홀")));
    service("suwon_city").sync();

    // 회차가 바뀜 (시작일 · 장소 · 제목이 같아 같은 블록 → 같은 행사를 갱신)
    client.answers.put("suwon_city", List.of(post(id, "회차 강연\n🗓️ 2026. 11. 5.(목), 11. 19.(목) 14:00\n📍 가상 아트홀")));
    service("suwon_city").sync();
    Event lecture = instagramEvents().get(0);
    assertThat(instagramEvents()).hasSize(1);
    assertThat(runDates(lecture)).containsExactly(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 19));

    // 기간으로 바뀌어 요일을 알 수 없게 됨 → 모름, 저장된 날짜도 지움
    client.answers.put("suwon_city", List.of(post(id, "회차 강연\n🗓️ 2026. 11. 5.(목) ~ 11. 19.(목) 14:00\n📍 가상 아트홀")));
    service("suwon_city").sync();
    Event updated = eventRepository.findById(lecture.getId()).orElseThrow();
    assertThat(updated.getScheduleStatus()).isEqualTo(Event.SCHEDULE_UNKNOWN);
    assertThat(eventDateRepository.findByEventId(lecture.getId())).isEmpty();
  }

  private List<LocalDate> runDates(Event event) {
    assertThat(event.getScheduleStatus()).isEqualTo(Event.SCHEDULE_DATES);
    return eventDateRepository.findByEventIdInOrderByRunDate(List.of(event.getId())).stream()
        .map(EventDate::getRunDate).toList();
  }

  @Test
  void 좌표까지_찾은_블록만_행사로_저장하고_나머지는_상태로_남김() {
    client.answers.put("suwon_city", List.of(post("1790000000000001", ROUNDUP)));

    InstagramSyncResult r = service("suwon_city").sync();

    assertThat(r.configured()).isTrue();
    assertThat(r.posts()).isEqualTo(1);
    assertThat(r.newPosts()).isEqualTo(1);
    assertThat(r.eventsSaved()).isEqualTo(1);
    assertThat(r.blocks().get("READY")).isEqualTo(1);
    assertThat(r.blocks().get("NO_COORD")).isEqualTo(1); // 가상 광장: 검색 결과 없음
    assertThat(r.blocks().get("NO_DATE")).isEqualTo(1);  // 매주 금~일: 반복만 있음
    assertThat(r.blocksByAccount().get("suwon_city"))
        .containsEntry("READY", 1).containsEntry("NO_COORD", 1).containsEntry("NO_DATE", 1)
        .doesNotContainKey("PENDING"); // 0 은 뺌

    List<Event> events = instagramEvents();
    assertThat(events).hasSize(1);
    Event e = events.get(0);
    assertThat(e.getTitle()).isEqualTo("가을 음악회");
    assertThat(e.getIdx()).isNull();
    assertThat(e.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(e.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 7));
    assertThat(e.getLatitude()).isEqualTo(37.28);
    assertThat(e.getLink()).isEqualTo("https://www.instagram.com/p/1790000000000001/");
    assertThat(e.getPubDate()).isEqualTo(LocalDateTime.of(2026, 10, 30, 18, 0)); // UTC 09:00 → 한국 18:00
    assertThat(e.getSourceKey()).startsWith("1790000000000001#").hasSizeLessThanOrEqualTo(100);

    InstagramMedia media = mediaRepository.findByMediaId("1790000000000001").orElseThrow();
    assertThat(media.getStatus()).isEqualTo(InstagramMedia.Status.PROCESSED);
    assertThat(media.getPostedAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 18, 0));
    List<InstagramBlock> blocks = blockRepository.findByMedia(media);
    assertThat(blocks).hasSize(3);
    assertThat(blocks).filteredOn(b -> b.getStatus() == InstagramBlock.Status.READY)
        .singleElement().satisfies(b -> assertThat(b.getEvent().getId()).isEqualTo(e.getId()));
    assertThat(blocks).filteredOn(b -> b.getStatus() == InstagramBlock.Status.NO_COORD)
        .singleElement().satisfies(b -> assertThat(b.getReason()).isNotBlank());
  }

  @Test
  void 다시_실행해도_중복_없고_READY_블록은_좌표를_다시_찾지_않음() {
    client.answers.put("suwon_city", List.of(post("1790000000000001", ROUNDUP)));
    InstagramSyncService s = service("suwon_city");

    s.sync();
    assertThat(search.queries).containsExactly("가상 아트홀", "가상 광장");

    InstagramSyncResult second = s.sync();
    assertThat(second.newPosts()).isZero();
    assertThat(second.eventsSaved()).isZero();
    assertThat(second.eventsUpdated()).isEqualTo(1);
    // 두 번째에는 다시 검색 없음: READY 는 키가 같으면 장소도 같음, 못 찾은 곳은 재검색 대기(3일)
    assertThat(search.queries).containsExactly("가상 아트홀", "가상 광장");
    assertThat(second.blocks().get("NO_COORD")).isEqualTo(1);
    assertThat(instagramEvents()).hasSize(1);
    assertThat(blockRepository.findByMedia(mediaRepository.findByMediaId("1790000000000001").orElseThrow()))
        .hasSize(3);
  }

  @Test
  void 못_찾은_장소는_3일_지나야_다시_검색하고_대기_중엔_마지막_검색_시각을_그대로_둠() {
    client.answers.put("suwon_city", List.of(post("1790000000000001", ROUNDUP)));
    Instant t0 = Instant.parse("2026-10-31T03:00:00Z");
    service("suwon_city", "", Clock.fixed(t0, InstagramPostProcessor.KST)).sync();
    InstagramBlock noCoord = noCoordBlock();
    LocalDateTime searchedAt = noCoord.getUpdatedAt();
    String reason = noCoord.getReason();

    // 2일 23시간 뒤: 검색 안 함, 상태 · 이유 · 시각 그대로
    service("suwon_city", "", Clock.fixed(t0.plusSeconds(3 * 86400 - 3600), InstagramPostProcessor.KST)).sync();
    assertThat(search.queries).containsExactly("가상 아트홀", "가상 광장");
    assertThat(noCoordBlock().getUpdatedAt()).isEqualTo(searchedAt);
    assertThat(noCoordBlock().getReason()).isEqualTo(reason);

    // 3일 뒤: 다시 검색
    service("suwon_city", "", Clock.fixed(t0.plusSeconds(3 * 86400), InstagramPostProcessor.KST)).sync();
    assertThat(search.queries).containsExactly("가상 아트홀", "가상 광장", "가상 광장");
    assertThat(noCoordBlock().getUpdatedAt()).isAfter(searchedAt);
  }

  @Test
  void 검색_실패는_영구_4xx만_대기하고_401_403_429_5xx_네트워크는_다음_실행에서_다시() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 31, 12, 0);
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.NO_COORD, "수원 안 검색 결과 없음", now.minusDays(1)), now)).isTrue();
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.NO_COORD, "수원 안 검색 결과 없음", now.minusDays(3)), now)).isFalse();
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.ERROR, "장소 검색 실패: HttpClientErrorException (HTTP 400)", now.minusHours(1)), now)).isTrue();
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.ERROR, "장소 검색 실패: HttpClientErrorException (HTTP 401)", now.minusHours(1)), now)).isFalse();
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.ERROR, "장소 검색 실패: HttpClientErrorException (HTTP 403)", now.minusHours(1)), now)).isFalse();
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.NO_COORD, "x", null), now)).isFalse();
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.ERROR, "장소 검색 실패: HttpClientErrorException (HTTP 429)", now.minusHours(1)), now)).isFalse();
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.ERROR, "장소 검색 실패: HttpServerErrorException (HTTP 503)", now.minusHours(1)), now)).isFalse();
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.ERROR, "장소 검색 실패: ResourceAccessException", now.minusHours(1)), now)).isFalse();
    assertThat(InstagramPostProcessor.waitBeforeRetry(block(InstagramBlock.Status.PENDING, null, now.minusHours(1)), now)).isFalse();
  }

  @Test
  void 영구_4xx_실패_블록은_대기_중_다시_검색하지_않음() {
    search = new FakeSearch() {
      @Override
      public Page search(String query) {
        queries.add(query);
        throw org.springframework.web.client.HttpClientErrorException.create(
            org.springframework.http.HttpStatus.BAD_REQUEST, "x", null, null, null);
      }
    };
    client.answers.put("suwon_city", List.of(post("1790000000000001", ROUNDUP)));
    InstagramSyncService s = service("suwon_city");
    InstagramSyncResult first = s.sync();
    assertThat(first.blocks().get("ERROR")).isEqualTo(2);
    int calls = search.queries.size();

    InstagramSyncResult second = s.sync();
    assertThat(second.blocks().get("ERROR")).isEqualTo(2);
    assertThat(search.queries).hasSize(calls);
  }

  private static InstagramBlock block(InstagramBlock.Status status, String reason, LocalDateTime at) {
    return InstagramBlock.builder().blockKey("k").status(status).reason(reason).updatedAt(at).build();
  }

  private InstagramBlock noCoordBlock() {
    return blockRepository.findByMedia(mediaRepository.findByMediaId("1790000000000001").orElseThrow()).stream()
        .filter(b -> b.getStatus() == InstagramBlock.Status.NO_COORD).findFirst().orElseThrow();
  }

  @Test
  void 계정_하나가_없으면_건너뛰고_다음_계정은_진행() {
    client.answers.put("suwon_media", new InstagramFetchException(Kind.NOT_FOUND, 100));
    client.answers.put("suwon_city", List.of(post("1790000000000001", ROUNDUP)));

    InstagramSyncResult r = service("suwon_media, suwon_city").sync();

    assertThat(client.called).containsExactly("suwon_media", "suwon_city");
    assertThat(r.accounts()).isEqualTo(2);
    assertThat(r.accountsOk()).isEqualTo(1);
    assertThat(r.accountErrors()).singleElement().asString().contains("suwon_media").contains("NOT_FOUND");
    assertThat(r.eventsSaved()).isEqualTo(1);
    assertThat(r.blocksByAccount()).containsOnlyKeys("suwon_city"); // 수집 실패 계정은 없음
  }

  @Test
  void 계정별_블록_수를_계정_순서대로_나눠_담음() {
    client.answers.put("suwon_media", List.of(post("1790000000000003", null))); // 캡션 없음 → 블록 0
    client.answers.put("suwon_city", List.of(post("1790000000000001", ROUNDUP)));

    InstagramSyncResult r = service("suwon_media, suwon_city").sync();

    assertThat(r.blocksByAccount()).containsOnlyKeys("suwon_media", "suwon_city");
    assertThat(r.blocksByAccount().keySet()).containsExactly("suwon_media", "suwon_city");
    assertThat(r.blocksByAccount().get("suwon_media")).isEmpty();
    assertThat(r.blocksByAccount().get("suwon_city")).containsEntry("READY", 1).containsEntry("NO_COORD", 1);
    int sumReady = r.blocksByAccount().values().stream().mapToInt(m -> m.getOrDefault("READY", 0)).sum();
    assertThat(sumReady).isEqualTo(r.blocks().get("READY")); // 계정별 합 = 전체
  }

  @Test
  void 토큰_만료면_남은_계정은_부르지_않음() {
    client.answers.put("sw_culture", new InstagramFetchException(Kind.TOKEN_INVALID, 190));
    client.answers.put("suwon_city", List.of(post("1790000000000001", ROUNDUP)));

    InstagramSyncResult r = service("sw_culture,suwon_city").sync();

    assertThat(client.called).containsExactly("sw_culture");
    assertThat(r.accountsOk()).isZero();
    assertThat(r.accountErrors()).anyMatch(m -> m.contains("건너뜀"));
    assertThat(instagramEvents()).isEmpty();
  }

  @Test
  void 게시물_하나가_실패해도_다음_게시물은_처리() {
    client.answers.put("suwon_city", List.of(post("abc", ROUNDUP), post("1790000000000002", ROUNDUP)));

    InstagramSyncResult r = service("suwon_city").sync();

    assertThat(r.posts()).isEqualTo(2);
    assertThat(r.postErrors()).isEqualTo(1); // id 형식이 다른 게시물
    assertThat(r.eventsSaved()).isEqualTo(1);
    assertThat(mediaRepository.findByMediaId("abc")).isEmpty();
  }

  @Test
  void 캡션이_없는_게시물은_블록_없이_처리_끝() {
    client.answers.put("suwon_city", List.of(post("1790000000000003", null)));

    InstagramSyncResult r = service("suwon_city").sync();

    assertThat(r.postErrors()).isZero();
    InstagramMedia media = mediaRepository.findByMediaId("1790000000000003").orElseThrow();
    assertThat(media.getStatus()).isEqualTo(InstagramMedia.Status.PROCESSED);
    assertThat(blockRepository.findByMedia(media)).isEmpty();
  }

  @Test
  void 공식_계정_목록에_없는_계정은_호출하지_않고_건너뜀() {
    client.answers.put("suwon_city", List.of(post("1790000000000001", ROUNDUP)));

    InstagramSyncResult r = service("some_influencer, @Suwon_City").sync();

    assertThat(client.called).containsExactly("suwon_city"); // @ · 대문자는 정리해서 호출
    assertThat(r.accountErrors()).singleElement().asString().contains("some_influencer").contains("공식 계정 목록");
  }

  @Test
  void 대상이_모두_공식_계정이_아니면_수집하지_않음() {
    InstagramSyncResult r = service("some_influencer").sync();

    assertThat(r.configured()).isFalse();
    assertThat(r.message()).contains("공식 계정 목록", "some_influencer");
    assertThat(client.called).isEmpty();
  }

  @Test
  void 설정이_비어_있으면_호출하지_않음() {
    client.configured = false;
    InstagramSyncResult r = service("suwon_city").sync();
    assertThat(r.configured()).isFalse();
    assertThat(client.called).isEmpty();

    client.configured = true;
    InstagramSyncResult noAccounts = service(" , ").sync();
    assertThat(noAccounts.configured()).isFalse();
    assertThat(noAccounts.message()).contains("계정");
  }
}
