package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.service.EventService;
import com.suwon.festival.service.EventSyncService;
import com.suwon.festival.service.FavoriteService;
import com.suwon.festival.service.ScheduledSyncJob;
import com.suwon.festival.service.merge.EventMergeRunner;
import com.suwon.festival.service.place.EventDistrictFiller;
import com.suwon.festival.service.merge.MergeSummary;

import jakarta.persistence.EntityManager;

// 스위치 instagram.sync.enabled 를 설정하지 않은 상태(= 기본값 false) 확인 (docs/decisions 인스타그램 수집 중단 결정)
//  ① 인스타 API 호출 없음 ② 어떤 경로로도 인스타 행사가 나가지 않음 ③ 재단 행사 · DB 의 인스타 행은 그대로
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstagramScopeOffTest {

  @Autowired private WebApplicationContext context;
  @Autowired private InstagramSyncService instagramSyncService;
  @Autowired private EventService eventService;
  @Autowired private FavoriteService favoriteService;
  @Autowired private EventRepository eventRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private EntityManager em;

  @MockitoBean private InstagramClient client;

  private MockMvc mockMvc;
  private Long memberId;
  private Event foundation;
  private Event instagram;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    when(client.isConfigured()).thenReturn(true); // 토큰이 있어도 호출하지 않는지 봐야 함
    memberId = memberRepository.saveAndFlush(Member.create()).getId();
    foundation = eventRepository.saveAndFlush(event("재단").build());
    instagram = eventRepository.saveAndFlush(event("인스타")
        .source(Event.SOURCE_INSTAGRAM).sourceKey("범위축소#1").link("https://www.instagram.com/p/virtual/").build());
    em.clear();
  }

  @Test
  void 동기화해도_인스타_API를_호출하지_않음() {
    InstagramSyncResult result = instagramSyncService.sync();

    verify(client, never()).fetchRecent(anyString(), anyInt());
    assertThat(result.configured()).isFalse();
  }

  @Test
  void 자동_동기화도_인스타_API를_호출하지_않고_재단과_합치기는_실행() throws Exception {
    EventSyncService foundationSync = mock(EventSyncService.class);
    EventMergeRunner mergeRunner = mock(EventMergeRunner.class);
    when(foundationSync.syncEvents()).thenReturn(new SyncResult(0, 0));
    when(mergeRunner.runAfterSync()).thenReturn(new MergeSummary(true, 0, 0, 0));

    new ScheduledSyncJob(foundationSync, instagramSyncService, mergeRunner, mock(EventDistrictFiller.class),
        mock(EventRepository.class), mock(com.suwon.festival.service.landmark.LandmarkSyncService.class)).runScheduled();

    verify(client, never()).fetchRecent(anyString(), anyInt());
    verify(foundationSync).syncEvents();
    verify(mergeRunner).runAfterSync();
  }

  @Test
  void 행사_API_응답에_인스타_행사가_없고_재단_행사는_있음() throws Exception {
    String body = mockMvc.perform(get("/api/events")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(body).contains("가상 행사 재단").doesNotContain("가상 행사 인스타", "instagram.com");
  }

  @Test
  void 합치기_미리보기_API_응답에_인스타_행사가_없음() throws Exception {
    eventRepository.saveAndFlush(event("인스타 묶음").place("가상 홀")
        .source(Event.SOURCE_INSTAGRAM).sourceKey("범위축소#2").build());
    eventRepository.saveAndFlush(event("인스타 묶음").place("가상 홀")
        .source(Event.SOURCE_INSTAGRAM).sourceKey("범위축소#3").build());

    String body = mockMvc.perform(get("/api/events/merge/preview")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(body).contains("\"groupCount\"").doesNotContain("가상 행사 인스타");
  }

  @Test
  void 인스타_행사를_찜했어도_찜_목록에_보이지_않고_찜_기록은_남음() {
    saveFavorite(instagram);
    saveFavorite(foundation);

    assertThat(favoriteService.getFavoriteEvents(memberId)).extracting(EventResponse::id)
        .containsExactly(foundation.getId());
    assertThat(favoriteRepository.existsByMemberIdAndEventId(memberId, instagram.getId())).isTrue();
  }

  @Test
  void 인스타_행사는_id_로_찜할_수_없음() {
    assertThatThrownBy(() -> favoriteService.addFavorite(memberId, instagram.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는 행사");
  }

  @Test
  void 찜해_둔_인스타_행사도_취소할_수_있음() {
    saveFavorite(instagram);

    favoriteService.removeFavorite(memberId, instagram.getId());

    assertThat(favoriteRepository.existsByMemberIdAndEventId(memberId, instagram.getId())).isFalse();
  }

  @Test
  void 인스타_행사는_캘린더로_받을_수_없고_재단_행사는_받을_수_있음() throws Exception {
    assertThat(status("/api/events/" + instagram.getId() + "/calendar.ics")).isEqualTo(404);
    assertThat(status("/api/events/" + instagram.getId() + "/calendar/google")).isEqualTo(404);
    assertThat(status("/api/events/" + foundation.getId() + "/calendar.ics")).isEqualTo(200);
  }

  @Test
  void 재단_대표에_합쳐진_인스타_행이_있어도_재단_행사는_보임() {
    Event hidden = eventRepository.findById(instagram.getId()).orElseThrow();
    hidden.mergeInto(foundation.getId());
    em.flush();
    saveFavorite(hidden);

    assertThat(eventService.getAllEvents()).extracting(EventResponse::id)
        .contains(foundation.getId()).doesNotContain(instagram.getId());
    assertThat(favoriteService.getFavoriteEvents(memberId)).extracting(EventResponse::id)
        .containsExactly(foundation.getId());
  }

  @Test
  void 인스타_행은_DB_에서_지우지_않음() {
    instagramSyncService.sync();
    eventService.getAllEvents();

    assertThat(eventRepository.findById(instagram.getId())).isPresent();
  }

  private int status(String path) throws Exception {
    return mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
  }

  private void saveFavorite(Event event) {
    favoriteRepository.saveAndFlush(Favorite.builder()
        .member(memberRepository.getReferenceById(memberId))
        .event(eventRepository.getReferenceById(event.getId()))
        .createdAt(LocalDateTime.of(2026, 11, 2, 12, 0)).build());
    em.clear();
  }

  private static Event.EventBuilder event(String key) {
    return Event.builder()
        .title("가상 행사 " + key).category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 11, 1, 12, 0))
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 5));
  }
}
