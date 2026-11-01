package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.EventSyncService;
import com.suwon.festival.service.instagram.InstagramSyncResult;
import com.suwon.festival.service.instagram.InstagramSyncService;
import com.suwon.festival.service.merge.EventMergeService;

// 동기화 API 끝에 합치기가 실행되는지 (설계 5번). 실제 커밋 경로를 보려고 @Transactional 을 걸지 않고 직접 지움
@SpringBootTest
@ActiveProfiles("test")
class SyncMergeControllerTest {

  private static final String TITLE = "자동합치기시험 공연";

  @Autowired private WebApplicationContext context;
  @Autowired private EventRepository eventRepository;
  @MockitoBean private EventSyncService eventSyncService;
  @MockitoBean private InstagramSyncService instagramSyncService;
  @MockitoSpyBean private EventMergeService eventMergeService;

  @AfterEach
  void cleanUp() {
    List<Event> mine = eventRepository.findAll().stream().filter(e -> TITLE.equals(e.getTitle())).toList();
    mine.forEach(e -> e.mergeInto(null));
    eventRepository.saveAll(mine);
    eventRepository.deleteAll(mine);
  }

  @Test
  void 재단_동기화_끝에_합치고_응답에_결과를_표시() throws Exception {
    Event foundation = eventRepository.save(event().idx(72001L).build());
    Event insta = eventRepository.save(event().source(Event.SOURCE_INSTAGRAM).sourceKey("자동합치기#1").build());
    when(eventSyncService.syncEvents()).thenReturn(new SyncResult(1, 2));

    MockHttpServletResponse res = post("/api/events/sync");

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains("새 행사 1개", "같은 행사 묶음", "숨김");
    assertThat(eventRepository.findById(insta.getId()).orElseThrow().getMergedIntoId()).isEqualTo(foundation.getId());
  }

  @Test
  void 인스타_동기화_끝에_합치고_JSON_에_결과를_포함() throws Exception {
    Event foundation = eventRepository.save(event().idx(72002L).build());
    Event insta = eventRepository.save(event().source(Event.SOURCE_INSTAGRAM).sourceKey("자동합치기#2").build());
    when(instagramSyncService.sync()).thenReturn(syncResult());

    MockHttpServletResponse res = post("/api/instagram/sync");

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8))
        .contains("\"eventsSaved\":3", "\"merge\"", "\"ok\":true", "\"hiddenCount\"");
    assertThat(eventRepository.findById(insta.getId()).orElseThrow().getMergedIntoId()).isEqualTo(foundation.getId());
  }

  @Test
  void 재단_동기화가_중간에_실패해도_합치기는_실행하고_원래_실패를_돌려줌() throws Exception {
    Event foundation = eventRepository.save(event().idx(72003L).build());
    Event insta = eventRepository.save(event().source(Event.SOURCE_INSTAGRAM).sourceKey("자동합치기#3").build());
    when(eventSyncService.syncEvents()).thenThrow(new IllegalStateException("가상 동기화 실패"));

    assertThatThrownBy(() -> post("/api/events/sync")).hasRootCauseMessage("가상 동기화 실패");

    assertThat(eventRepository.findById(insta.getId()).orElseThrow().getMergedIntoId()).isEqualTo(foundation.getId());
  }

  @Test
  void 합치기가_실패해도_재단_동기화는_성공으로_응답하고_실패를_알림() throws Exception {
    when(eventSyncService.syncEvents()).thenReturn(new SyncResult(1, 0));
    doThrow(new IllegalStateException("가상 실패")).when(eventMergeService).recompute();

    MockHttpServletResponse res = post("/api/events/sync");

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains("새 행사 1개", "합치기 실패");
  }

  @Test
  void 합치기가_실패해도_인스타_결과는_돌려주고_실패를_알림() throws Exception {
    when(instagramSyncService.sync()).thenReturn(syncResult());
    doThrow(new IllegalStateException("가상 실패")).when(eventMergeService).recompute();

    MockHttpServletResponse res = post("/api/instagram/sync");

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8))
        .contains("\"eventsSaved\":3", "\"ok\":false")
        .doesNotContain("가상 실패"); // 오류 메시지는 응답에 넣지 않음
  }

  private MockHttpServletResponse post(String url) throws Exception {
    return MockMvcBuilders.webAppContextSetup(context).build()
        .perform(MockMvcRequestBuilders.post(url))
        .andReturn().getResponse();
  }

  private static InstagramSyncResult syncResult() {
    return new InstagramSyncResult(true, null, 1, 1, List.of(), 1, 1, 0, Map.of("READY", 3), 3, 0, 0, null, null);
  }

  private static Event.EventBuilder event() {
    return Event.builder()
        .title(TITLE).category("공연").latitude(37.3).longitude(126.7).place("자동합치기 홀")
        .pubDate(LocalDateTime.of(2033, 1, 1, 0, 0))
        .startDate(LocalDate.of(2033, 2, 2)).endDate(LocalDate.of(2033, 2, 2));
  }
}
