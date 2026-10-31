package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.dto.xml.Coordinate;
import com.suwon.festival.dto.xml.EventItem;
import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;

// 재단 동기화가 인스타 행사(idx 없음)와 섞이지 않는지 — 가짜 재단 행사 목록을 직접 넣음 (실제 API 호출 없음)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventSyncServiceTest {

  @Autowired private EventSyncService eventSyncService;
  @Autowired private EventRepository eventRepository;

  private static EventItem item(Long idx, String title) {
    EventItem it = new EventItem();
    it.idx = idx;
    it.title = title;
    it.category = "공연";
    it.coordinate = new Coordinate();
    it.coordinate.lat = 127.01; // 재단 XML 은 lat/lng 이 뒤바뀌어 옴 (EventSyncService 참고)
    it.coordinate.lng = 37.28;
    it.pubDate = "2026-10-31 12:00:00";
    return it;
  }

  @Test
  void 재단_번호가_없는_행사는_건너뛰고_인스타_행사를_덮어쓰지_않음() {
    Event insta1 = eventRepository.saveAndFlush(instagramEvent("m9#가상1"));
    eventRepository.saveAndFlush(instagramEvent("m9#가상2")); // idx 가 null 인 행이 2개 → findByIdx(null) 이면 터졌을 상황

    SyncResult result = eventSyncService.syncItems(List.of(item(null, "번호 없는 재단 행사"), item(880001L, "가상 재단 행사")));

    assertThat(result.saved()).isEqualTo(1); // 번호 있는 것만 저장
    assertThat(eventRepository.findById(insta1.getId())).get()
        .satisfies(e -> {
          assertThat(e.getTitle()).isEqualTo("가상 인스타 행사"); // 덮어쓰이지 않음
          assertThat(e.getSource()).isEqualTo(Event.SOURCE_INSTAGRAM);
        });
    assertThat(eventRepository.findByIdx(880001L)).get()
        .satisfies(e -> assertThat(e.getSource()).isEqualTo(Event.SOURCE_SWCF));
  }

  private static Event instagramEvent(String sourceKey) {
    return Event.builder()
        .title("가상 인스타 행사").category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 10, 30, 18, 0))
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 5))
        .source(Event.SOURCE_INSTAGRAM).sourceKey(sourceKey)
        .build();
  }
}
