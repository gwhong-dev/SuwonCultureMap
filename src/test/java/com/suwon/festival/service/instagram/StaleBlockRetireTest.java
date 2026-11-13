package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.service.caption.CaptionParser;
import com.suwon.festival.service.instagram.InstagramFetchException.Kind;
import com.suwon.festival.service.merge.EventMergeService;
import com.suwon.festival.service.place.PlaceCandidate;
import com.suwon.festival.service.place.PlaceGeocoder;
import com.suwon.festival.service.place.PlaceSearchClient;
import com.suwon.festival.service.schedule.EventScheduleWriter;

import jakarta.persistence.EntityManager;

// 다시 처리한 게시물에서 사라진 블록 내리기 (설계 문서 7번 #13). 캡션은 직접 만든 가상 문장, 외부 호출 없음
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StaleBlockRetireTest {

  private static final String MEDIA = "1790000000000101";
  private static final String ACCOUNT = "suwon_city";
  private static final OffsetDateTime POSTED = OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private InstagramMediaRepository mediaRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private EventMergeService mergeService;
  @Autowired private StaleBlockRetirer retirer;
  @Autowired private EventScheduleWriter scheduleWriter;
  @Autowired private EntityManager em;

  private InstagramSyncServiceTest.FakeClient client;
  private SwitchableSearch search;
  private InstagramSyncService service;

  // "가상 아트홀" · "다른 아트홀" 만 찾아지고, failing 이면 호출 실패
  static class SwitchableSearch implements PlaceSearchClient {
    boolean failing;

    @Override
    public Page search(String query) {
      if (failing) throw new RestClientException("가상 실패");
      if (query.equals("가상 아트홀")) {
        return new Page(List.of(new PlaceCandidate("가상 아트홀", "경기 수원시 팔달구 가상동 1", null, 37.28, 127.01)), true);
      }
      if (query.equals("다른 아트홀")) {
        return new Page(List.of(new PlaceCandidate("다른 아트홀", "경기 수원시 영통구 가상동 2", null, 37.25, 127.07)), true);
      }
      return new Page(List.of(), true);
    }
  }

  @BeforeEach
  void setUp() {
    client = new InstagramSyncServiceTest.FakeClient();
    search = new SwitchableSearch();
    Clock clock = Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), InstagramPostProcessor.KST);
    InstagramPostProcessor processor = new InstagramPostProcessor(mediaRepository, blockRepository, eventRepository,
        new PlaceGeocoder(search), new CaptionParser(null), clock, retirer, scheduleWriter);
    service = new InstagramSyncService(client, processor, new InstagramTokenExpiry("", clock), new InstagramSwitch(true), ACCOUNT, 10);
  }

  private static String block(String title, String date, String place) {
    return title + "\n🗓️ " + date + "\n📍 장소: " + place;
  }

  private static String concert(String title) {
    return block(title, "2026. 11. 5.(목) ~ 11. 7.(토) 19:00~21:00", "가상 아트홀");
  }

  // 제목 · 기간 · 장소가 모두 달라 같은 행사가 아닌 블록
  private static String other(String title) {
    return block(title, "2026. 12. 5.(토) 14:00", "다른 아트홀");
  }

  private InstagramSyncResult syncCaption(String... captions) {
    List<InstagramPost> posts = new ArrayList<>();
    for (int i = 0; i < captions.length; i++) {
      String id = String.valueOf(Long.parseLong(MEDIA) + i);
      posts.add(new InstagramPost(id, captions[i], "https://www.instagram.com/p/" + id + "/", POSTED, "IMAGE"));
    }
    client.answers.put(ACCOUNT, posts);
    InstagramSyncResult r = service.sync();
    em.flush();
    em.clear();
    return r;
  }

  private List<Event> events() {
    return eventRepository.findAll().stream().filter(e -> Event.SOURCE_INSTAGRAM.equals(e.getSource())).toList();
  }

  private Event event(String title) {
    return events().stream().filter(e -> e.getTitle().equals(title)).findFirst().orElseThrow();
  }

  private List<InstagramBlock> blocks(String mediaId) {
    return blockRepository.findByMedia(mediaRepository.findByMediaId(mediaId).orElseThrow());
  }

  private Member member(String name) {
    return memberRepository.save(Member.create());
  }

  private void favorite(Member member, Event event) {
    favoriteRepository.save(Favorite.builder().member(member).event(event).createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build());
    em.flush();
    em.clear();
  }

  @Test
  void 제목이_비슷하게_바뀌면_옛_행사를_내리고_블록은_RETIRED() {
    syncCaption(concert("가을 음악회"));
    Long oldId = event("가을 음악회").getId();

    InstagramSyncResult r = syncCaption(concert("<가을 음악회> 수원"));

    assertThat(r.blocks().get("RETIRED")).isEqualTo(1);
    assertThat(events()).extracting(Event::getTitle).containsExactly("<가을 음악회> 수원");
    assertThat(eventRepository.findById(oldId)).isEmpty();
    assertThat(blocks(MEDIA)).filteredOn(b -> b.getStatus() == InstagramBlock.Status.RETIRED)
        .singleElement().satisfies(b -> {
          assertThat(b.getEvent()).isNull();
          assertThat(b.getReason()).isEqualTo(StaleBlockRetirer.REASON);
        });
    assertThat(blocks(MEDIA)).filteredOn(b -> b.getStatus() == InstagramBlock.Status.READY).hasSize(1);
  }

  @Test
  void 다시_돌려도_RETIRED_는_다시_세지_않고_캡션이_돌아오면_행사가_되살아남() {
    syncCaption(concert("가을 음악회"));
    syncCaption(concert("<가을 음악회> 수원"));

    assertThat(syncCaption(concert("<가을 음악회> 수원")).blocks().get("RETIRED")).isZero();

    InstagramSyncResult back = syncCaption(concert("가을 음악회"));
    assertThat(back.blocks().get("RETIRED")).isEqualTo(1);
    assertThat(events()).extracting(Event::getTitle).containsExactly("가을 음악회");
    assertThat(blocks(MEDIA)).hasSize(2); // 같은 키는 새 줄을 만들지 않음
  }

  @Test
  void 옛_블록이_빠져_같은_게시물이_모음_글로_오판되지_않음() {
    String roundup = concert("가을 음악회") + "\n\n" + other("겨울 음악회");
    syncCaption(roundup, concert("수원 가을 음악회"));
    Long roundupEventId = event("가을 음악회").getId();

    // 개별 글의 제목이 바뀜 → 옛 블록이 남으면 블록 2개로 모음 글 판단, 순위가 같아 id 작은 모음 글 행이 대표가 됨
    syncCaption(roundup, concert("수원 가을 음악회 앙코르"));
    mergeService.recompute();
    em.flush();
    em.clear();

    Event individual = event("수원 가을 음악회 앙코르");
    assertThat(individual.getMergedIntoId()).isNull(); // 개별 글이 대표
    assertThat(eventRepository.findById(roundupEventId).orElseThrow().getMergedIntoId()).isEqualTo(individual.getId());
  }

  @Test
  void 행사_없는_옛_블록은_상태만_RETIRED() {
    // 처음엔 좌표를 못 찾아 NO_COORD(행사 없음), 캡션 수정 뒤 READY → 옛 NO_COORD 가 남으면 블록 2개로 셈
    syncCaption(block("가을 음악회", "2026. 11. 5.(목) 19:00", "없는 아트홀"));

    InstagramSyncResult r = syncCaption(concert("가을 음악회"));

    assertThat(r.blocks().get("RETIRED")).isEqualTo(1);
    assertThat(blocks(MEDIA)).extracting(InstagramBlock::getStatus)
        .containsExactlyInAnyOrder(InstagramBlock.Status.RETIRED, InstagramBlock.Status.READY);
  }

  @Test
  void 찜은_뒤를_잇는_행사로_옮기고_찜한_시점을_유지() {
    syncCaption(concert("가을 음악회"));
    Member member = member("찜회원");
    favorite(member, event("가을 음악회"));

    syncCaption(concert("<가을 음악회> 수원"));

    Event successor = event("<가을 음악회> 수원");
    assertThat(favoriteRepository.findByMemberId(member.getId())).singleElement().satisfies(f -> {
      assertThat(f.getEvent().getId()).isEqualTo(successor.getId());
      assertThat(f.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
    });
  }

  @Test
  void 한_글에서_행사가_통째로_바뀌면_제목이_달라도_찜을_옮김() {
    syncCaption(concert("공연도 즐기고 선물도 받으세요"));
    Member member = member("찜회원");
    favorite(member, event("공연도 즐기고 선물도 받으세요"));

    syncCaption(concert("단풍 콘서트"));

    assertThat(events()).extracting(Event::getTitle).containsExactly("단풍 콘서트");
    assertThat(favoriteRepository.findByMemberId(member.getId())).singleElement()
        .satisfies(f -> assertThat(f.getEvent().getTitle()).isEqualTo("단풍 콘서트"));
  }

  @Test
  void 옮길_곳이_없는_찜이_있으면_지우지_않고_찜이_풀린_뒤_내림() {
    String both = concert("가을 음악회") + "\n\n" + other("겨울 음악회");
    syncCaption(both);
    Member member = member("찜회원");
    favorite(member, event("겨울 음악회"));

    // 겨울 음악회가 글에서 빠짐 → 같은 행사가 없고, 남은 READY 는 그대로 있던 행사 → 찜을 다른 행사로 옮기면 안 됨
    InstagramSyncResult kept = syncCaption(concert("가을 음악회"));

    assertThat(kept.blocks().get("RETIRED")).isZero();
    Event winter = event("겨울 음악회");
    assertThat(favoriteRepository.findByMemberId(member.getId())).singleElement()
        .satisfies(f -> assertThat(f.getEvent().getId()).isEqualTo(winter.getId()));

    favoriteRepository.deleteAll(favoriteRepository.findByMemberId(member.getId()));
    em.flush();
    InstagramSyncResult after = syncCaption(concert("가을 음악회"));

    assertThat(after.blocks().get("RETIRED")).isEqualTo(1);
    assertThat(events()).extracting(Event::getTitle).containsExactly("가을 음악회");
  }

  @Test
  void 옛_행사_2개를_찜한_회원의_찜이_같은_행사로_모이면_1개만_남음() {
    String both = concert("가을 음악회") + "\n\n" + concert("가을 음악회 앙코르");
    syncCaption(both);
    Member member = member("찜회원");
    favorite(member, event("가을 음악회"));
    favorite(member, event("가을 음악회 앙코르"));

    syncCaption(concert("가을 음악회 앙코르 공연"));

    assertThat(events()).extracting(Event::getTitle).containsExactly("가을 음악회 앙코르 공연");
    assertThat(favoriteRepository.findByMemberId(member.getId())).singleElement()
        .satisfies(f -> assertThat(f.getEvent().getTitle()).isEqualTo("가을 음악회 앙코르 공연"));
  }

  @Test
  void 같은_글에_따로_있던_비슷한_행사가_못_읽게_되어도_지우지_않음() {
    syncCaption(concert("가을 음악회") + "\n\n" + concert("가을 음악회 앙코르"));

    String unreadable = concert("가을 음악회") + "\n\n가을 음악회 앙코르\n🗓️ 매주 금~일요일\n📍 가상 아트홀";
    InstagramSyncResult r = syncCaption(unreadable);

    assertThat(r.blocks().get("NO_DATE")).isEqualTo(1);
    assertThat(r.blocks().get("RETIRED")).isZero();
    assertThat(events()).extracting(Event::getTitle).contains("가을 음악회 앙코르");
  }

  @Test
  void 같은_글에_따로_있던_비슷한_행사로_찜을_옮기지_않음() {
    syncCaption(concert("가을 음악회") + "\n\n" + concert("가을 음악회 앙코르"));
    Member member = member("찜회원");
    favorite(member, event("가을 음악회 앙코르"));

    InstagramSyncResult r = syncCaption(concert("가을 음악회"));

    assertThat(r.blocks().get("RETIRED")).isZero();
    assertThat(favoriteRepository.findByMemberId(member.getId())).singleElement()
        .satisfies(f -> assertThat(f.getEvent().getTitle()).isEqualTo("가을 음악회 앙코르"));
  }

  @Test
  void 글을_다른_행사로_재활용하면_찜을_옮기지_않음() {
    syncCaption(concert("가을 음악회"));
    Member member = member("찜회원");
    favorite(member, event("가을 음악회"));

    InstagramSyncResult r = syncCaption(other("겨울 전시"));

    assertThat(r.blocks().get("RETIRED")).isZero();
    assertThat(favoriteRepository.findByMemberId(member.getId())).singleElement()
        .satisfies(f -> assertThat(f.getEvent().getTitle()).isEqualTo("가을 음악회"));
  }

  @Test
  void 옛_제목이_캡션에_그대로_있으면_블록이_안_나와도_지우지_않음() {
    syncCaption(concert("가을 음악회") + "\n\n" + other("겨울 음악회"));

    // 날짜 라벨이 없어져 겨울 음악회 블록이 나오지 않음 → READY 만 있어 "확정"으로 보임
    InstagramSyncResult r = syncCaption(concert("가을 음악회") + "\n\n겨울 음악회\n날짜는 12월 5일\n장소는 다른 아트홀");

    assertThat(r.blocks().get("RETIRED")).isZero();
    assertThat(events()).extracting(Event::getTitle).contains("겨울 음악회");
  }

  @Test
  void 검색_오류_뒤에_READY_가_되어도_찜을_옮기고_내림() {
    syncCaption(concert("공연도 즐기고 선물도 받으세요"));
    Member member = member("찜회원");
    favorite(member, event("공연도 즐기고 선물도 받으세요"));

    search.failing = true;
    assertThat(syncCaption(concert("단풍 콘서트")).blocks().get("RETIRED")).isZero();

    search.failing = false;
    assertThat(syncCaption(concert("단풍 콘서트")).blocks().get("RETIRED")).isEqualTo(1);
    assertThat(events()).extracting(Event::getTitle).containsExactly("단풍 콘서트");
    assertThat(favoriteRepository.findByMemberId(member.getId())).singleElement()
        .satisfies(f -> assertThat(f.getEvent().getTitle()).isEqualTo("단풍 콘서트"));
  }

  @Test
  void 다른_행이_대표로_가리키는_행사도_숨김을_비우고_내림() {
    syncCaption(concert("가을 음악회"), concert("가을 음악회 앙코르"));
    Event rep = event("가을 음악회");
    Event hidden = event("가을 음악회 앙코르");
    hidden.mergeInto(rep.getId());
    em.flush();
    em.clear();

    syncCaption(concert("<가을 음악회> 수원"), concert("가을 음악회 앙코르"));

    assertThat(eventRepository.findById(rep.getId())).isEmpty();
    assertThat(eventRepository.findById(hidden.getId()).orElseThrow().getMergedIntoId()).isNull();
  }

  @Test
  void 장소_검색_오류가_있으면_뒤를_잇는_행사가_없는_옛_행사를_지우지_않음() {
    syncCaption(concert("가을 음악회"));
    Long oldId = event("가을 음악회").getId();

    search.failing = true;
    InstagramSyncResult r = syncCaption(block("단풍 콘서트", "2026. 11. 9.(월) 19:00", "가상 아트홀"));

    assertThat(r.blocks().get("ERROR")).isEqualTo(1);
    assertThat(r.blocks().get("RETIRED")).isZero();
    assertThat(eventRepository.findById(oldId)).isPresent();

    search.failing = false; // 다음 실행에서 정상이면 내려감
    InstagramSyncResult next = syncCaption(block("단풍 콘서트", "2026. 11. 9.(월) 19:00", "가상 아트홀"));
    assertThat(next.blocks().get("RETIRED")).isEqualTo(1);
    assertThat(eventRepository.findById(oldId)).isEmpty();
  }

  @Test
  void 못_읽은_블록이_같이_있으면_뒤를_잇는_행사가_없는_옛_행사를_지우지_않음() {
    String withStale = concert("가을 음악회") + "\n\n" + other("겨울 음악회");
    syncCaption(withStale);

    // 겨울 음악회 날짜 줄이 못 읽는 모양으로 바뀜 → 사라진 건지 못 읽은 건지 모름
    String unreadable = concert("가을 음악회") + "\n\n겨울 음악회\n🗓️ 매주 금~일요일\n📍 다른 아트홀";
    InstagramSyncResult r = syncCaption(unreadable);

    assertThat(r.blocks().get("NO_DATE")).isEqualTo(1);
    assertThat(r.blocks().get("RETIRED")).isZero();
    assertThat(events()).extracting(Event::getTitle).contains("겨울 음악회");
  }

  @Test
  void 못_읽은_블록이_있어도_같은_행사가_READY_면_옛_행사를_내림() {
    String unreadable = "\n\n겨울 음악회\n🗓️ 매주 금~일요일\n📍 다른 아트홀";
    syncCaption(concert("가을 음악회") + unreadable);

    InstagramSyncResult r = syncCaption(concert("<가을 음악회> 수원") + unreadable);

    assertThat(r.blocks().get("RETIRED")).isEqualTo(1);
    assertThat(events()).extracting(Event::getTitle).containsExactly("<가을 음악회> 수원");
  }

  @Test
  void 캡션을_못_받으면_아무것도_내리지_않음() {
    syncCaption(concert("가을 음악회"));

    InstagramSyncResult r = syncCaption((String) null);

    assertThat(r.blocks().get("RETIRED")).isZero();
    assertThat(events()).hasSize(1);
    assertThat(blocks(MEDIA)).singleElement()
        .satisfies(b -> assertThat(b.getStatus()).isEqualTo(InstagramBlock.Status.READY));
  }

  @Test
  void 수집_범위_밖으로_밀려난_게시물의_행사는_그대로() {
    syncCaption(concert("가을 음악회"));
    Long oldId = event("가을 음악회").getId();

    client.answers.put(ACCOUNT, List.of(new InstagramPost("1790000000000999", other("겨울 음악회"),
        "https://www.instagram.com/p/new/", POSTED, "IMAGE")));
    service.sync();
    em.flush();
    em.clear();

    assertThat(eventRepository.findById(oldId)).isPresent();
    assertThat(blocks(MEDIA)).singleElement()
        .satisfies(b -> assertThat(b.getStatus()).isEqualTo(InstagramBlock.Status.READY));
  }

  @Test
  void 수집_실패한_계정의_행사는_그대로() {
    syncCaption(concert("가을 음악회"));

    client.answers.put(ACCOUNT, new InstagramFetchException(Kind.RATE_LIMIT, 4));
    service.sync();

    assertThat(events()).hasSize(1);
  }
  // 고치기 전 상태 흉내: 관광 대상 아닌 블록이 READY 로 행사에 연결돼 있음 (운영 id 231 같은 경우)
  private Event linkAsBeforeFix(String title) {
    InstagramBlock block = blocks(MEDIA).get(0);
    Event event = eventRepository.save(Event.builder().title(title).category("행사").latitude(37.28).longitude(127.01)
        .startDate(java.time.LocalDate.of(2026, 11, 5)).endDate(java.time.LocalDate.of(2026, 11, 5)).place("가상 시청 중회의실")
        .pubDate(LocalDateTime.of(2026, 10, 30, 9, 0)).source(Event.SOURCE_INSTAGRAM)
        .sourceKey(BlockKeys.sourceKey(MEDIA, block.getBlockKey())).build());
    block.update(InstagramBlock.Status.READY, null, event, LocalDateTime.of(2026, 10, 30, 9, 0));
    em.flush();
    em.clear();
    return event;
  }

  private static String forum() {
    return block("제1회 가상 교통정책발전포럼", "2026. 11. 5.(목) 14:00", "가상 시청 중회의실");
  }

  @Test
  void 관광_대상_아님은_행사를_만들지_않고_SKIPPED() {
    InstagramSyncResult r = syncCaption(forum());

    assertThat(r.blocks().get("SKIPPED")).isEqualTo(1);
    assertThat(events()).isEmpty();
    assertThat(blocks(MEDIA)).singleElement().satisfies(b -> assertThat(b.getReason()).isEqualTo("관광 대상 아님: 포럼"));
  }

  @Test
  void 예전에_올라간_관광_대상_아닌_행사는_다음_동기화에_지움() {
    syncCaption(forum());
    Long id = linkAsBeforeFix("제1회 가상 교통정책발전포럼").getId();

    InstagramSyncResult r = syncCaption(forum());

    assertThat(r.blocks().get("SKIPPED")).isEqualTo(1);
    assertThat(eventRepository.findById(id)).isEmpty();
    assertThat(blocks(MEDIA)).singleElement().satisfies(b -> {
      assertThat(b.getStatus()).isEqualTo(InstagramBlock.Status.SKIPPED);
      assertThat(b.getEvent()).isNull();
    });
  }

  @Test
  void 관광_대상_아닌_행사라도_찜이_걸려_있으면_남김() {
    syncCaption(forum());
    Event event = linkAsBeforeFix("제1회 가상 교통정책발전포럼");
    favorite(member("찜회원"), event);

    syncCaption(forum());

    assertThat(eventRepository.findById(event.getId())).isPresent();
    assertThat(blocks(MEDIA)).singleElement().satisfies(b -> {
      assertThat(b.getStatus()).isEqualTo(InstagramBlock.Status.SKIPPED);
      assertThat(b.getEvent()).isNotNull();
    });
  }
}
