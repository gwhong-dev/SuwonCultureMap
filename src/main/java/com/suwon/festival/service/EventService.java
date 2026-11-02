package com.suwon.festival.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.EventDate;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

  private final EventRepository eventRepository;
  private final EventDateRepository eventDateRepository;

  public List<EventResponse> getAllEvents() {
    return toResponses(eventRepository.findByMergedIntoIdIsNull());
  }

  // 진행 날짜는 행사마다 쿼리하지 않고 한 번에 읽어 묶음. 숨긴 행의 날짜를 대표에 합치는 것은 설계 11번 7번 PR
  public List<EventResponse> toResponses(List<Event> events) {
    if (events.isEmpty()) return List.of();
    Map<Long, List<LocalDate>> dates = new HashMap<>();
    for (EventDate date : eventDateRepository.findByEventIdInOrderByRunDate(events.stream().map(Event::getId).toList())) {
      dates.computeIfAbsent(date.getEventId(), id -> new ArrayList<>()).add(date.getRunDate());
    }
    return events.stream()
        .map(event -> new EventResponse(
            event.getId(),
            event.getTitle(),
            event.getCategory(),
            event.getLatitude(),
            event.getLongitude(),
            event.getImageUrl(),
            event.getInfo(),
            event.getPubDate(),
            event.getStartDate(),
            event.getEndDate(),
            event.getLink(),
            event.getScheduleStatus(),
            event.getScheduleText(),
            event.isScheduleHolidayNote(),
            dates.getOrDefault(event.getId(), List.of())))
        .toList();
  }
}
