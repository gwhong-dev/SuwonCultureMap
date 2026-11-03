package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.EventDate;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;

// 인스타 스위치가 꺼졌으면 인스타 숨긴 행의 날짜도 대표에 보이지 않음 (MergedRunDatesTest 는 스위치 켬)
@SpringBootTest(properties = "instagram.sync.enabled=false")
@ActiveProfiles("test")
@Transactional
class MergedRunDatesSwitchOffTest {

  @Autowired private EventService eventService;
  @Autowired private EventRepository eventRepository;
  @Autowired private EventDateRepository eventDateRepository;
  @Autowired private EntityManager em;

  @Test
  void 스위치가_꺼지면_인스타_숨긴_행_날짜를_안_보냄() {
    Event rep = eventRepository.saveAndFlush(event(Event.SOURCE_SWCF, 990002L, null)); // 재단 · 날짜 모름
    Event hidden = event(Event.SOURCE_INSTAGRAM, null, "진행날짜#꺼짐");
    hidden.updateSchedule(Event.SCHEDULE_DATES, "원문", false);
    hidden = eventRepository.saveAndFlush(hidden);
    eventDateRepository.saveAndFlush(new EventDate(hidden.getId(), LocalDate.of(2026, 11, 8)));
    hidden.mergeInto(rep.getId());
    em.flush();
    em.clear();

    EventResponse res = eventService.getAllEvents().stream()
        .filter(r -> r.id().equals(rep.getId())).findFirst().orElseThrow();

    assertThat(res.confirmedDates()).isEmpty();
  }

  private static Event event(String source, Long idx, String sourceKey) {
    return Event.builder()
        .idx(idx).title("가상 행사 " + source).category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 10, 20, 12, 0))
        .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2026, 11, 30))
        .source(source).sourceKey(sourceKey)
        .build();
  }
}
