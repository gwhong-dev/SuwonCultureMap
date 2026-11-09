package com.suwon.festival.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.service.instagram.InstagramSwitch;
import com.suwon.festival.service.schedule.MergedRunDates;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

  private final EventRepository eventRepository;
  private final MergedRunDates mergedRunDates;
  private final InstagramBlockRepository instagramBlockRepository;
  private final InstagramSwitch instagramSwitch;

  public List<EventResponse> getAllEvents() {
    return toResponses(eventRepository.findByMergedIntoIdIsNull());
  }

  // 진행 날짜는 숨긴 행 날짜까지 한 번에 읽어 대표에 합침 (MergedRunDates)
  // 행사 목록 · 찜 목록이 모두 여기를 거치므로 인스타 스위치도 여기서 거름
  public List<EventResponse> toResponses(List<Event> allEvents) {
    List<Event> events = allEvents.stream().filter(instagramSwitch::isVisible).toList();
    if (events.isEmpty()) return List.of();
    Map<Long, MergedRunDates.RunDates> runDates = mergedRunDates.of(events);
    Map<Long, String> accounts = instagramAccounts(events);
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
            runDates.get(event.getId()).dates(),
            runDates.get(event.getId()).confirmedDates(),
            event.getPlace(),
            event.getTimeText(),
            event.getFee(),
            event.getContact(),
            event.getHost(),
            event.getSource(),
            accounts.get(event.getId()),
            Event.DISTRICT_OUTSIDE.equals(event.getDistrict()) ? null : event.getDistrict()))
        .toList();
  }

  // 인스타 행사만 블록 → 게시물에서 계정 이름을 한 번에 읽음 (재단 행사만 있으면 쿼리 안 함)
  private Map<Long, String> instagramAccounts(List<Event> events) {
    List<Long> ids = events.stream()
        .filter(event -> Event.SOURCE_INSTAGRAM.equals(event.getSource()))
        .map(Event::getId).toList();
    Map<Long, String> accounts = new HashMap<>();
    if (ids.isEmpty()) return accounts;
    for (Object[] row : instagramBlockRepository.findAccountsByEventIds(ids)) {
      accounts.putIfAbsent((Long) row[0], (String) row[1]);
    }
    return accounts;
  }
}
