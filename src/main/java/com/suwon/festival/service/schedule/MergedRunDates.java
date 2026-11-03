package com.suwon.festival.service.schedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.EventDate;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.instagram.InstagramSwitch;

import lombok.RequiredArgsConstructor;

// 합치기 대표의 진행 날짜 (설계 docs/event-schedule.md 8번). 저장은 행마다 그대로 두고 조회할 때 합침
// - 대표 DATES · 인스타: 대표 ∪ 숨긴 행의 DATES 날짜
// - 대표 DATES · 재단(그 밖의 출처 포함): 대표 날짜만 (재단 우선)
// - 대표 UNKNOWN: 기간 그대로, 숨긴 행의 DATES 날짜는 confirmedDates 로 표시만
//   숨긴 행 날짜는 "이날은 한다"는 뜻이지 "다른 날은 안 한다"가 아님 → 합쳐 DATES 로 만들면 "그날만"으로 잘못 안내
// 행사 목록과 캘린더 날짜 검사가 같은 날짜를 쓰도록 둘 다 여기를 거침
@Component
@RequiredArgsConstructor
public class MergedRunDates {

  // dates: 화면 · 필터 · 캘린더가 믿는 진행 날짜 / confirmedDates: 숨긴 행에서 확인된 회차 (표시만)
  public record RunDates(List<LocalDate> dates, List<LocalDate> confirmedDates) {
  }

  private final EventRepository eventRepository;
  private final EventDateRepository eventDateRepository;
  private final InstagramSwitch instagramSwitch;

  // 행사마다 쿼리하지 않고 숨긴 행 · 날짜를 한 번씩 읽음. 결과에는 events 의 모든 id 가 있음
  public Map<Long, RunDates> of(List<Event> events) {
    Map<Long, RunDates> result = new HashMap<>();
    if (events.isEmpty()) return result;
    List<Long> ids = events.stream().map(Event::getId).toList();
    // 날짜를 모르는 숨긴 행은 보탤 것이 없음. 인스타 스위치가 꺼졌으면 인스타 숨긴 행은 없는 것으로 봄
    List<Event> hidden = eventRepository.findByMergedIntoIdIn(ids).stream()
        .filter(instagramSwitch::isVisible)
        .filter(e -> Event.SCHEDULE_DATES.equals(e.getScheduleStatus()))
        .toList();

    List<Long> all = new ArrayList<>(ids);
    hidden.forEach(e -> all.add(e.getId()));
    Map<Long, List<LocalDate>> own = new HashMap<>();
    for (EventDate date : eventDateRepository.findByEventIdInOrderByRunDate(all)) {
      own.computeIfAbsent(date.getEventId(), id -> new ArrayList<>()).add(date.getRunDate());
    }
    Map<Long, TreeSet<LocalDate>> fromHidden = new HashMap<>();
    for (Event h : hidden) {
      fromHidden.computeIfAbsent(h.getMergedIntoId(), id -> new TreeSet<>()).addAll(own.getOrDefault(h.getId(), List.of()));
    }

    for (Event event : events) {
      List<LocalDate> mine = own.getOrDefault(event.getId(), List.of());
      TreeSet<LocalDate> extra = fromHidden.getOrDefault(event.getId(), new TreeSet<>());
      // 대표 기간 밖 날짜는 버림 (합친 뒤 대표 기간만 바뀐 경우 등). 화면은 기간 밖 날짜가 하나라도 있으면 날짜 목록 전체를 안 믿음
      extra.removeIf(date -> !withinPeriod(event, date));
      boolean known = Event.SCHEDULE_DATES.equals(event.getScheduleStatus());
      if (known && Event.SOURCE_INSTAGRAM.equals(event.getSource())) {
        extra.addAll(mine);
        result.put(event.getId(), new RunDates(List.copyOf(extra), List.of()));
      } else if (known) {
        result.put(event.getId(), new RunDates(mine, List.of()));
      } else {
        result.put(event.getId(), new RunDates(mine, List.copyOf(extra)));
      }
    }
    return result;
  }

  private static boolean withinPeriod(Event event, LocalDate date) {
    LocalDate start = event.getStartDate();
    if (start == null) return false;
    LocalDate end = event.getEndDate() != null ? event.getEndDate() : start;
    return !date.isBefore(start) && !date.isAfter(end);
  }
}
