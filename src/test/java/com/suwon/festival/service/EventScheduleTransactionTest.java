package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.schedule.EventScheduleWriter;
import com.suwon.festival.service.schedule.ScheduleResult;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 운영처럼 바깥 트랜잭션 없이 실행: 날짜 저장이 실패하면 상태 칸도 함께 되돌아가는지
@SpringBootTest
@ActiveProfiles("test")
class EventScheduleTransactionTest {

  private static final LocalDate DAY = LocalDate.of(2026, 5, 16);

  @Autowired private EventScheduleWriter eventScheduleWriter;
  @Autowired private EventRepository eventRepository;
  @MockitoSpyBean private EventDateRepository eventDateRepository;

  private Long eventId;

  @AfterEach
  void cleanUp() {
    if (eventId != null) eventRepository.deleteById(eventId);
  }

  @Test
  void 날짜_저장이_실패하면_상태도_바뀌지_않음() {
    eventId = eventRepository.save(Event.builder()
        .title("가상 행사").category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 10, 30, 18, 0)).build()).getId();
    doThrow(new IllegalStateException("저장 실패")).when(eventDateRepository).saveAll(any());

    assertThatThrownBy(() -> eventScheduleWriter.apply(eventId,
        new ScheduleResult(ScheduleResult.Status.DATES, List.of(DAY), "원문", false, null), null))
        .isInstanceOf(IllegalStateException.class);

    assertThat(eventRepository.findById(eventId).orElseThrow().getScheduleStatus()).isEqualTo("UNKNOWN");
  }
}
