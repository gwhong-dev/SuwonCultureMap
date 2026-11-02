package com.suwon.festival.service.schedule;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.EventDate;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.caption.CaptionText;

import lombok.RequiredArgsConstructor;

// 추출 결과를 행사 한 건에 저장. 상태 · 원문 · 날짜를 한 트랜잭션으로 묶어 일부만 바뀐 행사가 남지 않게 함
@Service
@RequiredArgsConstructor
public class EventScheduleWriter {

  private static final int TEXT_MAX = 1000; // schedule_text 칸 길이

  private final EventRepository eventRepository;
  private final EventDateRepository eventDateRepository;

  // 달라진 것이 있으면 저장하고 true. 규칙을 고치면 다음 동기화에 전부 반영되도록 pubDate 와 상관없이 매번 비교함
  @Transactional
  public boolean apply(Long eventId, ScheduleResult result, String fallbackText) {
    Event event = eventRepository.findById(eventId).orElseThrow();
    String status = result.status().name();
    String text = CaptionText.limit(result.sourceText() != null ? result.sourceText() : fallbackText, TEXT_MAX);

    List<EventDate> stored = eventDateRepository.findByEventId(eventId);
    Set<LocalDate> wanted = new HashSet<>(result.dates());
    // 남는 날짜는 건드리지 않음 (지우고 다시 넣으면 Hibernate 가 INSERT 를 먼저 보내 기본 키가 겹침)
    List<EventDate> gone = stored.stream().filter(d -> !wanted.contains(d.getRunDate())).toList();
    stored.forEach(d -> wanted.remove(d.getRunDate()));

    boolean changed = !status.equals(event.getScheduleStatus()) || !Objects.equals(text, event.getScheduleText())
        || result.holidayNote() != event.isScheduleHolidayNote() || !gone.isEmpty() || !wanted.isEmpty();
    if (!changed) return false;

    event.updateSchedule(status, text, result.holidayNote());
    eventDateRepository.deleteAll(gone);
    eventDateRepository.saveAll(wanted.stream().map(d -> new EventDate(eventId, d)).toList());
    return true;
  }
}
