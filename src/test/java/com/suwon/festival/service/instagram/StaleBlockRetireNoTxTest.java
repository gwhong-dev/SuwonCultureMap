package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.service.place.PlaceCandidate;
import com.suwon.festival.service.place.PlaceGeocoder;

// 실제 스프링 빈 + 테스트 트랜잭션 없음 → 실제 동기화처럼 게시물마다 커밋될 때 FK 순서 · 지연 로딩이 맞는지 확인
@SpringBootTest(properties = { "instagram.sync.enabled=true", "instagram.accounts=gasang_city" })
@ActiveProfiles("test")
class StaleBlockRetireNoTxTest {

  private static final OffsetDateTime POSTED = OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.UTC);
  private static final String DATE_AND_PLACE = "\n🗓️ 2026. 11. 5.(목) ~ 11. 7.(토) 19:00~21:00\n📍 장소: 가상 아트홀";

  @MockitoBean private InstagramClient client;
  @MockitoBean private PlaceGeocoder geocoder;

  @Autowired private InstagramSyncService syncService;
  @Autowired private InstagramMediaRepository mediaRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private MemberRepository memberRepository;

  @BeforeEach
  void setUp() {
    when(client.isConfigured()).thenReturn(true);
    PlaceCandidate hall = new PlaceCandidate("가상 아트홀", "경기 수원시 팔달구 가상동 1", null, 37.28, 127.01);
    when(geocoder.geocode(any())).thenReturn(new PlaceGeocoder.Result(PlaceGeocoder.Status.FOUND, hall, null));
  }

  @AfterEach
  void cleanUp() {
    favoriteRepository.deleteAll();
    memberRepository.deleteAll();
    blockRepository.deleteAll();
    mediaRepository.deleteAll();
    List<Event> instagram = eventRepository.findAll().stream()
        .filter(e -> Event.SOURCE_INSTAGRAM.equals(e.getSource())).toList();
    instagram.forEach(e -> e.mergeInto(null));
    eventRepository.saveAll(instagram);
    eventRepository.deleteAll(instagram);
  }

  private InstagramSyncResult sync(String caption, String secondCaption) {
    when(client.fetchRecent(eq("gasang_city"), anyInt())).thenReturn(List.of(
        new InstagramPost("1790000000000201", caption, "https://www.instagram.com/p/A/", POSTED, "IMAGE"),
        new InstagramPost("1790000000000202", secondCaption, "https://www.instagram.com/p/B/", POSTED, "IMAGE")));
    return syncService.sync();
  }

  private Event event(String title) {
    return eventRepository.findAll().stream().filter(e -> title.equals(e.getTitle())).findFirst().orElseThrow();
  }

  @Test
  void 찜과_숨김이_걸린_옛_행사를_커밋까지_문제없이_내림() {
    sync("가을 음악회" + DATE_AND_PLACE, "가을 음악회 앙코르" + DATE_AND_PLACE);
    Event old = event("가을 음악회");
    Event hidden = event("가을 음악회 앙코르");
    hidden.mergeInto(old.getId());
    eventRepository.save(hidden);
    Member member = memberRepository.save(Member.create());
    favoriteRepository.save(Favorite.builder().member(member).event(old).createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build());

    InstagramSyncResult r = sync("<가을 음악회> 수원" + DATE_AND_PLACE, "가을 음악회 앙코르" + DATE_AND_PLACE);

    assertThat(r.postErrors()).isZero();
    assertThat(r.blocks().get("RETIRED")).isEqualTo(1);
    assertThat(eventRepository.findById(old.getId())).isEmpty();
    assertThat(eventRepository.findById(hidden.getId()).orElseThrow().getMergedIntoId()).isNull();
    assertThat(favoriteRepository.findByMemberId(member.getId())).hasSize(1);
    assertThat(favoriteRepository.existsByMemberIdAndEventId(member.getId(), event("<가을 음악회> 수원").getId())).isTrue();
    assertThat(blockRepository.findAll()).filteredOn(b -> b.getStatus() == InstagramBlock.Status.RETIRED).hasSize(1);
  }

  @Test
  void 찜_2개가_같은_행사로_모여도_unique_충돌_없이_커밋() {
    String both = "가을 음악회" + DATE_AND_PLACE + "\n\n가을 음악회 앙코르" + DATE_AND_PLACE;
    sync(both, "겨울 음악회" + DATE_AND_PLACE);
    Event old = event("가을 음악회");
    Event encore = event("가을 음악회 앙코르");
    Member member = memberRepository.save(Member.create());
    favoriteRepository.save(Favorite.builder().member(member).event(old).createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build());
    favoriteRepository.save(Favorite.builder().member(member).event(encore).createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build());

    InstagramSyncResult r = sync("가을 음악회 앙코르 공연" + DATE_AND_PLACE, "겨울 음악회" + DATE_AND_PLACE);

    assertThat(r.postErrors()).isZero();
    assertThat(eventRepository.findById(old.getId())).isEmpty();
    assertThat(eventRepository.findById(encore.getId())).isEmpty();
    assertThat(favoriteRepository.findByMemberId(member.getId())).hasSize(1);
    assertThat(favoriteRepository.existsByMemberIdAndEventId(member.getId(), event("가을 음악회 앙코르 공연").getId())).isTrue();
  }
}
