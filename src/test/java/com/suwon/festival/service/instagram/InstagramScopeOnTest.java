package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.EventService;

// 스위치를 켜면 코드를 고치지 않아도 수집 · 표시가 그대로 돌아오는지 (다시 켜는 경로 보장)
@SpringBootTest(properties = "instagram.sync.enabled=true")
@ActiveProfiles("test")
@Transactional
class InstagramScopeOnTest {

  @Autowired private InstagramSyncService instagramSyncService;
  @Autowired private EventService eventService;
  @Autowired private EventRepository eventRepository;

  @MockitoBean private InstagramClient client;

  @Test
  void 켜면_인스타_API를_호출함() {
    when(client.isConfigured()).thenReturn(true);
    when(client.fetchRecent(anyString(), anyInt())).thenReturn(List.of());

    assertThat(instagramSyncService.sync().configured()).isTrue();

    verify(client).fetchRecent(anyString(), anyInt());
  }

  @Test
  void 켜면_인스타_행사가_목록에_보임() {
    Event instagram = eventRepository.saveAndFlush(Event.builder()
        .title("가상 행사 인스타").category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 11, 1, 12, 0))
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 5))
        .source(Event.SOURCE_INSTAGRAM).sourceKey("범위축소#켜짐").build());

    assertThat(eventService.getAllEvents()).extracting(EventResponse::id).contains(instagram.getId());
  }
}
