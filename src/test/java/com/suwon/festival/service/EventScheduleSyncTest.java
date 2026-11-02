package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.dto.xml.Coordinate;
import com.suwon.festival.dto.xml.EventItem;
import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.service.schedule.EventScheduleWriter;
import com.suwon.festival.service.schedule.ScheduleReader;
import com.suwon.festival.service.schedule.ScheduleResult;

import jakarta.persistence.EntityManager;

// 재단 동기화가 진행 날짜를 저장하고, 다시 돌려도 달라진 것만 고치는지 (설계 11번 3번 PR)
@SpringBootTest(properties = "instagram.sync.enabled=true") // 인스타 행사 응답도 시험함
@ActiveProfiles("test")
@Transactional
class EventScheduleSyncTest {

  private static final long IDX = 880101L;
  private static final String TWO_DAYS = "행사일정 : 2026. 5. 16.(토) / 9. 19.(토) 총 2회";
  private static final String THREE_DAYS = "행사일정 : 2026. 5. 16.(토) / 6. 13.(토) / 9. 19.(토) 총 3회";
  private static final LocalDate MAY = LocalDate.of(2026, 5, 16);
  private static final LocalDate JUNE = LocalDate.of(2026, 6, 13);
  private static final LocalDate SEPT = LocalDate.of(2026, 9, 19);

  @Autowired private EventSyncService eventSyncService;
  @Autowired private EventService eventService;
  @Autowired private EventRepository eventRepository;
  @Autowired private EventDateRepository eventDateRepository;
  @Autowired private EntityManager em;
  @Autowired private EventScheduleWriter eventScheduleWriter;
  @Autowired private FavoriteService favoriteService;
  @Autowired private MemberRepository memberRepository;

  private static EventItem item(long idx) {
    EventItem it = new EventItem();
    it.idx = idx;
    it.title = "가상 행사";
    it.category = "공연";
    it.coordinate = new Coordinate();
    it.coordinate.lat = 127.01;
    it.coordinate.lng = 37.28;
    it.pubDate = "2026-10-31 12:00:00";
    it.eventDate = new com.suwon.festival.dto.xml.EventDate();
    it.eventDate.sdate = "2026-05-16";
    it.eventDate.edate = "2026-09-19";
    return it;
  }

  private List<LocalDate> storedDates(long idx) {
    Event event = eventRepository.findByIdx(idx).orElseThrow();
    return eventDateRepository.findByEventIdInOrderByRunDate(List.of(event.getId())).stream()
        .map(d -> d.getRunDate()).toList();
  }

  private SyncResult sync(String body) {
    SyncResult result = eventSyncService.syncItems(List.of(item(IDX)), Map.of(IDX, body));
    em.flush();
    em.clear();
    return result;
  }

  @Test
  void 본문의_회차를_날짜로_저장하고_원문을_남김() {
    assertThat(sync(TWO_DAYS).saved()).isEqualTo(1);

    Event event = eventRepository.findByIdx(IDX).orElseThrow();
    assertThat(event.getScheduleStatus()).isEqualTo("DATES");
    assertThat(event.getScheduleText()).contains("5. 16.");
    assertThat(storedDates(IDX)).containsExactly(MAY, SEPT);
  }

  @Test
  void 그대로_다시_동기화하면_갱신_없음() {
    sync(TWO_DAYS);

    SyncResult again = sync(TWO_DAYS);

    assertThat(again.saved()).isZero();
    assertThat(again.updated()).isZero();
    assertThat(storedDates(IDX)).containsExactly(MAY, SEPT);
  }

  @Test
  void 회차가_바뀌면_pubDate_가_같아도_날짜를_고침() {
    sync(TWO_DAYS);

    assertThat(sync(THREE_DAYS).updated()).isEqualTo(1);

    assertThat(storedDates(IDX)).containsExactly(MAY, JUNE, SEPT);
  }

  @Test
  void 못_읽게_되면_저장된_날짜를_지우고_모름으로() {
    sync(TWO_DAYS);

    assertThat(sync("일정은 추후 공지").updated()).isEqualTo(1);

    assertThat(eventRepository.findByIdx(IDX)).get()
        .satisfies(e -> assertThat(e.getScheduleStatus()).isEqualTo("UNKNOWN"));
    assertThat(storedDates(IDX)).isEmpty();
  }

  @Test
  void 본문을_못_받으면_저장된_날짜를_유지() {
    sync(TWO_DAYS);

    SyncResult result = eventSyncService.syncItems(List.of(item(IDX)), Map.of());
    em.flush();
    em.clear();

    assertThat(result.updated()).isZero();
    assertThat(eventRepository.findByIdx(IDX)).get()
        .satisfies(e -> assertThat(e.getScheduleStatus()).isEqualTo("DATES"));
    assertThat(storedDates(IDX)).containsExactly(MAY, SEPT);
  }

  @Test
  void 추출이_실패해도_동기화는_계속되고_저장된_날짜를_유지() {
    sync(TWO_DAYS);

    SyncResult result;
    try (MockedStatic<ScheduleReader> reader = mockStatic(ScheduleReader.class)) {
      reader.when(() -> ScheduleReader.read(any(), any())).thenThrow(new StackOverflowError());
      result = eventSyncService.syncItems(List.of(item(IDX), item(IDX + 1)), Map.of(IDX, TWO_DAYS, IDX + 1, TWO_DAYS));
    }
    em.flush();
    em.clear();

    assertThat(result.saved()).isEqualTo(1); // 뒤 행사도 저장됨
    assertThat(storedDates(IDX)).containsExactly(MAY, SEPT);
    assertThat(eventRepository.findByIdx(IDX + 1)).get()
        .satisfies(e -> assertThat(e.getScheduleStatus()).isEqualTo("UNKNOWN"));
  }

  @Test
  void pubDate_가_같아도_기간이_바뀌면_기간도_고침() {
    sync(TWO_DAYS);
    EventItem changed = item(IDX);
    changed.eventDate.edate = "2026-10-17";

    eventSyncService.syncItems(List.of(changed), Map.of(IDX, "행사일정 : 2026. 5. 16.(토) / 10. 17.(토) 총 2회"));
    em.flush();
    em.clear();

    assertThat(eventRepository.findByIdx(IDX)).get()
        .satisfies(e -> assertThat(e.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 17)));
    assertThat(storedDates(IDX)).containsExactly(MAY, LocalDate.of(2026, 10, 17));
  }

  @Test
  void 공휴일_조건을_저장하고_응답에_넣음() {
    sync(TWO_DAYS);
    Long id = eventRepository.findByIdx(IDX).orElseThrow().getId();

    boolean changed = eventScheduleWriter.apply(id,
        new ScheduleResult(ScheduleResult.Status.DATES, List.of(MAY, SEPT), "매주 금~일, 공휴일", true, null), null);
    em.flush();
    em.clear();

    assertThat(changed).isTrue();
    Event event = eventRepository.findById(id).orElseThrow();
    assertThat(event.isScheduleHolidayNote()).isTrue();
    assertThat(eventService.toResponses(List.of(event)).get(0).scheduleHolidayNote()).isTrue();
  }

  @Test
  void 긴_원문은_글자를_반으로_자르지_않고_1000자_안으로_저장() {
    Event event = eventRepository.saveAndFlush(Event.builder()
        .title("긴 원문").category("공연").latitude(37.28).longitude(127.01)
        .pubDate(java.time.LocalDateTime.of(2026, 10, 30, 18, 0)).build());
    String text = "가".repeat(999) + "😀" + "나";

    eventScheduleWriter.apply(event.getId(),
        new ScheduleResult(ScheduleResult.Status.UNKNOWN, List.of(), null, false, "시험"), text);
    em.flush();
    em.clear();

    assertThat(eventRepository.findById(event.getId()).orElseThrow().getScheduleText()).isEqualTo("가".repeat(999));
  }

  @Test
  void 찜_응답에도_진행_날짜가_들어감() {
    sync(TWO_DAYS);
    Long memberId = memberRepository.saveAndFlush(Member.create()).getId();
    favoriteService.addFavorite(memberId, eventRepository.findByIdx(IDX).orElseThrow().getId());

    assertThat(favoriteService.getFavoriteEvents(memberId)).singleElement()
        .satisfies(e -> assertThat(e.dates()).containsExactly(MAY, SEPT));
  }

  @Test
  void 행사_목록_응답에_진행_날짜가_순서대로_들어감() {
    sync(TWO_DAYS);
    Long id = eventRepository.findByIdx(IDX).orElseThrow().getId();

    EventResponse response = eventService.getAllEvents().stream()
        .filter(e -> e.id().equals(id)).findFirst().orElseThrow();

    assertThat(response.scheduleStatus()).isEqualTo("DATES");
    assertThat(response.scheduleText()).contains("9. 19.");
    assertThat(response.dates()).containsExactly(MAY, SEPT);
  }

  @Test
  void 날짜가_없는_행사의_응답은_모름에_빈_목록() {
    Event saved = eventRepository.saveAndFlush(Event.builder()
        .title("인스타 행사").category("공연").latitude(37.28).longitude(127.01)
        .pubDate(java.time.LocalDateTime.of(2026, 10, 30, 18, 0))
        .source(Event.SOURCE_INSTAGRAM).sourceKey("m#schedule").build());

    EventResponse response = eventService.toResponses(List.of(saved)).get(0);

    assertThat(response.scheduleStatus()).isEqualTo("UNKNOWN");
    assertThat(response.dates()).isEmpty();
  }

  @Test
  void 행사를_지우면_날짜도_같이_지워짐() {
    sync(TWO_DAYS);
    Long id = eventRepository.findByIdx(IDX).orElseThrow().getId();

    em.createNativeQuery("DELETE FROM event WHERE id = :id").setParameter("id", id).executeUpdate();

    assertThat(eventDateRepository.findByEventId(id)).isEmpty();
  }
}
