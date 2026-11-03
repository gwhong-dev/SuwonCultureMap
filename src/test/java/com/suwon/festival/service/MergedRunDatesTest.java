package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.EventDate;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;

// 합치기 대표의 진행 날짜 (docs/event-schedule.md 8번 표 3가지 + 캘린더 날짜 검사)
@SpringBootTest(properties = "instagram.sync.enabled=true")
@ActiveProfiles("test")
@Transactional
class MergedRunDatesTest {

  private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
  private static final LocalDate NOV_8 = LocalDate.of(2026, 11, 8);
  private static final LocalDate NOV_15 = LocalDate.of(2026, 11, 15);
  private static final LocalDate DEC_5 = LocalDate.of(2026, 12, 5);

  @Autowired private EventService eventService;
  @Autowired private EventCalendarService calendarService;
  @Autowired private EventRepository eventRepository;
  @Autowired private EventDateRepository eventDateRepository;
  @Autowired private EntityManager em;

  @Test
  void 인스타_대표가_날짜를_알면_숨긴_행_날짜를_합침() {
    Event rep = save("대표", Event.SOURCE_INSTAGRAM, NOV_1);
    Event hidden = save("숨김", Event.SOURCE_INSTAGRAM, NOV_8, NOV_15);
    hide(hidden, rep);

    EventResponse res = response(rep);

    assertThat(res.dates()).containsExactly(NOV_1, NOV_8, NOV_15); // 정렬 · 중복 없음
    assertThat(res.confirmedDates()).isEmpty();
  }

  @Test
  void 재단_대표는_재단_날짜만() {
    Event rep = save("대표", Event.SOURCE_SWCF, NOV_1);
    Event hidden = save("숨김", Event.SOURCE_INSTAGRAM, NOV_8);
    hide(hidden, rep);

    EventResponse res = response(rep);

    assertThat(res.dates()).containsExactly(NOV_1);
    assertThat(res.confirmedDates()).isEmpty();
  }

  @Test
  void 날짜_모르는_대표는_숨긴_행_날짜를_확인된_회차로만() {
    Event rep = save("대표", Event.SOURCE_INSTAGRAM);
    Event hidden = save("숨김", Event.SOURCE_INSTAGRAM, NOV_8);
    hide(hidden, rep);

    EventResponse res = response(rep);

    assertThat(res.scheduleStatus()).isEqualTo(Event.SCHEDULE_UNKNOWN); // "11/8 만 진행"으로 바꾸지 않음
    assertThat(res.dates()).isEmpty();
    assertThat(res.confirmedDates()).containsExactly(NOV_8);
  }

  @Test
  void 날짜_모르는_숨긴_행은_보탤_것이_없음() {
    Event rep = save("대표", Event.SOURCE_INSTAGRAM, NOV_1);
    Event hidden = save("숨김", Event.SOURCE_INSTAGRAM);
    eventDateRepository.saveAndFlush(new EventDate(hidden.getId(), NOV_8)); // 상태가 UNKNOWN 이면 남은 날짜도 안 믿음
    hide(hidden, rep);

    EventResponse res = response(rep);

    assertThat(res.dates()).containsExactly(NOV_1);
    assertThat(res.confirmedDates()).isEmpty();
  }

  @Test
  void 숨긴_행이_없으면_지금과_같음() {
    Event plain = save("일반", Event.SOURCE_SWCF, NOV_1, NOV_8);

    EventResponse res = response(plain);

    assertThat(res.dates()).containsExactly(NOV_1, NOV_8);
    assertThat(res.confirmedDates()).isEmpty();
  }

  @Test
  void 대표_기간_밖의_숨긴_행_날짜는_버림() {
    Event rep = save("대표", Event.SOURCE_INSTAGRAM, NOV_1);
    Event hidden = save("숨김", Event.SOURCE_INSTAGRAM, NOV_8, DEC_5); // 합친 뒤 대표 기간만 바뀐 경우 등
    hide(hidden, rep);

    assertThat(response(rep).dates()).containsExactly(NOV_1, NOV_8); // 대표 자신의 날짜까지 화면에서 버려지지 않게
  }

  @Test
  void 대표_여러_건을_한_번에_조회해도_제_대표에_붙음() {
    Event repA = save("대표A", Event.SOURCE_INSTAGRAM, NOV_1);
    Event repB = save("대표B", Event.SOURCE_INSTAGRAM);
    Event hiddenA = save("숨김A", Event.SOURCE_INSTAGRAM, NOV_8);
    Event hiddenB = save("숨김B", Event.SOURCE_INSTAGRAM, NOV_15);
    hide(hiddenA, repA);
    hide(hiddenB, repB);

    assertThat(response(repA).dates()).containsExactly(NOV_1, NOV_8);
    assertThat(response(repA).confirmedDates()).isEmpty();
    assertThat(response(repB).dates()).isEmpty();
    assertThat(response(repB).confirmedDates()).containsExactly(NOV_15);
  }

  @Test
  void 캘린더는_합친_날짜로_검사() {
    Event rep = save("대표", Event.SOURCE_INSTAGRAM, NOV_1);
    Event hidden = save("숨김", Event.SOURCE_INSTAGRAM, NOV_8);
    hide(hidden, rep);
    Event loaded = eventRepository.findById(rep.getId()).orElseThrow();

    assertThatCode(() -> calendarService.checkRunDate(loaded, NOV_8)).doesNotThrowAnyException();
    assertThatThrownBy(() -> calendarService.checkRunDate(loaded, NOV_15)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void 캘린더는_확인된_회차를_받지_않음() {
    Event rep = save("대표", Event.SOURCE_INSTAGRAM);
    Event hidden = save("숨김", Event.SOURCE_INSTAGRAM, NOV_8);
    hide(hidden, rep);
    Event loaded = eventRepository.findById(rep.getId()).orElseThrow();

    assertThatThrownBy(() -> calendarService.checkRunDate(loaded, NOV_8)).isInstanceOf(ResponseStatusException.class);
  }

  private EventResponse response(Event event) {
    return eventService.getAllEvents().stream().filter(r -> r.id().equals(event.getId())).findFirst().orElseThrow();
  }

  private void hide(Event hidden, Event representative) {
    eventRepository.findById(hidden.getId()).orElseThrow().mergeInto(representative.getId());
    em.flush();
    em.clear();
  }

  // 날짜를 주면 DATES, 안 주면 UNKNOWN. 기간은 11월 전체 (숨긴 행이 대표 기간 안에 있게)
  private Event save(String key, String source, LocalDate... dates) {
    Event event = Event.builder()
        .idx(Event.SOURCE_SWCF.equals(source) ? 990001L : null) // 재단 행사는 시험마다 1건뿐
        .title("가상 행사 " + key).category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 10, 20, 12, 0))
        .startDate(NOV_1).endDate(LocalDate.of(2026, 11, 30))
        .source(source).sourceKey(Event.SOURCE_INSTAGRAM.equals(source) ? "진행날짜#" + key : null)
        .build();
    if (dates.length > 0) event.updateSchedule(Event.SCHEDULE_DATES, "원문", false);
    Event saved = eventRepository.saveAndFlush(event);
    eventDateRepository.saveAllAndFlush(List.of(dates).stream().map(d -> new EventDate(saved.getId(), d)).toList());
    return saved;
  }
}
