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
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.InstagramMedia;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;
import com.suwon.festival.service.caption.CaptionParser;
import com.suwon.festival.service.instagram.InstagramFetchException.Kind;
import com.suwon.festival.service.place.PlaceCandidate;
import com.suwon.festival.service.place.PlaceGeocoder;
import com.suwon.festival.service.place.PlaceSearchClient;

// 인스타 5단계 흐름: 수집(가짜) → 정리(실제 규칙 파서) → 좌표(가짜 검색) → 저장(H2)
// 캡션은 직접 만든 가상 문장, 외부 호출 없음
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstagramSyncServiceTest {

  @Autowired private InstagramMediaRepository mediaRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @Autowired private EventRepository eventRepository;

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
    Clock clock = Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), InstagramPostProcessor.KST);
    InstagramPostProcessor processor = new InstagramPostProcessor(mediaRepository, blockRepository, eventRepository,
        new PlaceGeocoder(search), new CaptionParser(null), clock);
    return new InstagramSyncService(client, processor, accounts, 10);
  }

  private static InstagramPost post(String id, String caption) {
    return new InstagramPost(id, caption, "https://www.instagram.com/p/" + id + "/", POSTED, "IMAGE");
  }

  private List<Event> instagramEvents() {
    return eventRepository.findAll().stream().filter(e -> Event.SOURCE_INSTAGRAM.equals(e.getSource())).toList();
  }

  @Test
  void 좌표까지_찾은_블록만_행사로_저장하고_나머지는_상태로_남김() {
    client.answers.put("gasang_city", List.of(post("1790000000000001", ROUNDUP)));

    InstagramSyncResult r = service("gasang_city").sync();

    assertThat(r.configured()).isTrue();
    assertThat(r.posts()).isEqualTo(1);
    assertThat(r.newPosts()).isEqualTo(1);
    assertThat(r.eventsSaved()).isEqualTo(1);
    assertThat(r.blocks().get("READY")).isEqualTo(1);
    assertThat(r.blocks().get("NO_COORD")).isEqualTo(1); // 가상 광장: 검색 결과 없음
    assertThat(r.blocks().get("NO_DATE")).isEqualTo(1);  // 매주 금~일: 반복만 있음

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
    client.answers.put("gasang_city", List.of(post("1790000000000001", ROUNDUP)));
    InstagramSyncService s = service("gasang_city");

    s.sync();
    assertThat(search.queries).containsExactly("가상 아트홀", "가상 광장");

    InstagramSyncResult second = s.sync();
    assertThat(second.newPosts()).isZero();
    assertThat(second.eventsSaved()).isZero();
    assertThat(second.eventsUpdated()).isEqualTo(1);
    // 두 번째에는 못 찾았던 곳만 다시 검색 (READY 는 키가 같으면 장소도 같음)
    assertThat(search.queries).containsExactly("가상 아트홀", "가상 광장", "가상 광장");
    assertThat(instagramEvents()).hasSize(1);
    assertThat(blockRepository.findByMedia(mediaRepository.findByMediaId("1790000000000001").orElseThrow()))
        .hasSize(3);
  }

  @Test
  void 계정_하나가_없으면_건너뛰고_다음_계정은_진행() {
    client.answers.put("no_such", new InstagramFetchException(Kind.NOT_FOUND, 100));
    client.answers.put("gasang_city", List.of(post("1790000000000001", ROUNDUP)));

    InstagramSyncResult r = service("no_such, gasang_city").sync();

    assertThat(client.called).containsExactly("no_such", "gasang_city");
    assertThat(r.accounts()).isEqualTo(2);
    assertThat(r.accountsOk()).isEqualTo(1);
    assertThat(r.accountErrors()).singleElement().asString().contains("no_such").contains("NOT_FOUND");
    assertThat(r.eventsSaved()).isEqualTo(1);
  }

  @Test
  void 토큰_만료면_남은_계정은_부르지_않음() {
    client.answers.put("first_city", new InstagramFetchException(Kind.TOKEN_INVALID, 190));
    client.answers.put("gasang_city", List.of(post("1790000000000001", ROUNDUP)));

    InstagramSyncResult r = service("first_city,gasang_city").sync();

    assertThat(client.called).containsExactly("first_city");
    assertThat(r.accountsOk()).isZero();
    assertThat(r.accountErrors()).anyMatch(m -> m.contains("건너뜀"));
    assertThat(instagramEvents()).isEmpty();
  }

  @Test
  void 게시물_하나가_실패해도_다음_게시물은_처리() {
    client.answers.put("gasang_city", List.of(post("abc", ROUNDUP), post("1790000000000002", ROUNDUP)));

    InstagramSyncResult r = service("gasang_city").sync();

    assertThat(r.posts()).isEqualTo(2);
    assertThat(r.postErrors()).isEqualTo(1); // id 형식이 다른 게시물
    assertThat(r.eventsSaved()).isEqualTo(1);
    assertThat(mediaRepository.findByMediaId("abc")).isEmpty();
  }

  @Test
  void 캡션이_없는_게시물은_블록_없이_처리_끝() {
    client.answers.put("gasang_city", List.of(post("1790000000000003", null)));

    InstagramSyncResult r = service("gasang_city").sync();

    assertThat(r.postErrors()).isZero();
    InstagramMedia media = mediaRepository.findByMediaId("1790000000000003").orElseThrow();
    assertThat(media.getStatus()).isEqualTo(InstagramMedia.Status.PROCESSED);
    assertThat(blockRepository.findByMedia(media)).isEmpty();
  }

  @Test
  void 설정이_비어_있으면_호출하지_않음() {
    client.configured = false;
    InstagramSyncResult r = service("gasang_city").sync();
    assertThat(r.configured()).isFalse();
    assertThat(client.called).isEmpty();

    client.configured = true;
    InstagramSyncResult noAccounts = service(" , ").sync();
    assertThat(noAccounts.configured()).isFalse();
    assertThat(noAccounts.message()).contains("계정");
  }
}
