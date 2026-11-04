package com.suwon.festival.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.place.RegionLookupClient.Region;

// 좌표 → 구 저장 규칙 (계획 D1) — DB · 카카오는 가짜
class EventDistrictFillerTest {

  private final EventRepository repository = mock(EventRepository.class);
  private final RegionLookupClient client = mock(RegionLookupClient.class);
  private final EventDistrictFiller filler = new EventDistrictFiller(repository, client);

  @Test
  void 수원_구_이름만_남기고_다른_곳은_수원_밖() {
    assertThat(EventDistrictFiller.toDistrict(new Region("경기도", "수원시 팔달구"))).isEqualTo("팔달구");
    assertThat(EventDistrictFiller.toDistrict(new Region("경기도", "수원시 영통구"))).isEqualTo("영통구");
    assertThat(EventDistrictFiller.toDistrict(new Region("경기도", "화성시"))).isEqualTo(Event.DISTRICT_OUTSIDE);
    assertThat(EventDistrictFiller.toDistrict(new Region("경기도", "성남시 분당구"))).isEqualTo(Event.DISTRICT_OUTSIDE);
    assertThat(EventDistrictFiller.toDistrict(new Region("서울특별시", "종로구"))).isEqualTo(Event.DISTRICT_OUTSIDE);
    assertThat(EventDistrictFiller.toDistrict(Region.NONE)).isEqualTo(Event.DISTRICT_OUTSIDE); // 바다 · 결과 없음
  }

  @Test
  void 수원인데_구를_못_읽으면_null_로_굳히지_않음() {
    assertThat(EventDistrictFiller.toDistrict(new Region("경기도", "수원시"))).isNull();
    assertThat(EventDistrictFiller.toDistrict(new Region("경기도", "수원시팔달구"))).isNull();
    assertThat(EventDistrictFiller.toDistrict(new Region("경기도", "수원시 가상구"))).isNull();
  }

  @Test
  void 구를_모르는_행사만_찾아_저장하고_개수를_셈() {
    Event a = event(1L, 37.28, 127.01);
    Event b = event(2L, 37.20, 126.90);
    when(repository.findByDistrictIsNullOrderByIdDesc(any(Pageable.class))).thenReturn(List.of(a, b));
    when(client.lookup(37.28, 127.01)).thenReturn(new Region("경기도", "수원시 팔달구"));
    when(client.lookup(37.20, 126.90)).thenReturn(new Region("경기도", "화성시"));
    when(repository.saveDistrict(anyLong(), anyDouble(), anyDouble(), anyString())).thenReturn(1);

    EventDistrictFiller.Summary summary = filler.fillMissing();

    verify(repository).saveDistrict(1L, 37.28, 127.01, "팔달구");
    verify(repository).saveDistrict(2L, 37.20, 126.90, Event.DISTRICT_OUTSIDE);
    assertThat(summary).isEqualTo(new EventDistrictFiller.Summary(1, 1, 0, false));
  }

  @Test
  void 한_번_실패는_건너뛰고_다음_행사는_계속() {
    Event a = event(1L, 37.28, 127.01);
    Event b = event(2L, 37.29, 127.02);
    when(repository.findByDistrictIsNullOrderByIdDesc(any(Pageable.class))).thenReturn(List.of(a, b));
    when(client.lookup(37.28, 127.01)).thenThrow(new IllegalStateException("가상 실패"));
    when(client.lookup(37.29, 127.02)).thenReturn(new Region("경기도", "수원시 장안구"));
    when(repository.saveDistrict(anyLong(), anyDouble(), anyDouble(), anyString())).thenReturn(1);

    EventDistrictFiller.Summary summary = filler.fillMissing();

    verify(repository, never()).saveDistrict(eq(1L), anyDouble(), anyDouble(), anyString()); // 실패는 비워 둠 → 다음에 다시
    verify(repository).saveDistrict(2L, 37.29, 127.02, "장안구");
    assertThat(summary).isEqualTo(new EventDistrictFiller.Summary(1, 0, 1, false));
  }

  @Test
  void 연속_실패면_키_한도_문제로_보고_멈춤() {
    List<Event> many = IntStream.range(0, 10).mapToObj(i -> event((long) i, 37.2 + i * 0.001, 127.0)).toList();
    when(repository.findByDistrictIsNullOrderByIdDesc(any(Pageable.class))).thenReturn(many);
    when(client.lookup(anyDouble(), anyDouble())).thenThrow(new IllegalStateException("401"));

    EventDistrictFiller.Summary summary = filler.fillMissing();

    verify(client, times(EventDistrictFiller.MAX_CONSECUTIVE_FAILURES)).lookup(anyDouble(), anyDouble());
    assertThat(summary.stopped()).isTrue();
    assertThat(summary.failed()).isEqualTo(EventDistrictFiller.MAX_CONSECUTIVE_FAILURES);
  }

  @Test
  void 모르는_형식은_저장하지_않고_실패로_셈() {
    when(repository.findByDistrictIsNullOrderByIdDesc(any(Pageable.class))).thenReturn(List.of(event(1L, 37.28, 127.01)));
    when(client.lookup(anyDouble(), anyDouble())).thenReturn(new Region("경기도", "수원시"));

    EventDistrictFiller.Summary summary = filler.fillMissing();

    verify(repository, never()).saveDistrict(anyLong(), anyDouble(), anyDouble(), anyString());
    assertThat(summary).isEqualTo(new EventDistrictFiller.Summary(0, 0, 1, false));
  }

  @Test
  void 그사이_좌표가_바뀌어_저장_0건이면_세지_않음() {
    when(repository.findByDistrictIsNullOrderByIdDesc(any(Pageable.class))).thenReturn(List.of(event(1L, 37.28, 127.01)));
    when(client.lookup(anyDouble(), anyDouble())).thenReturn(new Region("경기도", "수원시 팔달구"));
    when(repository.saveDistrict(anyLong(), anyDouble(), anyDouble(), anyString())).thenReturn(0);

    assertThat(filler.fillMissing()).isEqualTo(new EventDistrictFiller.Summary(0, 0, 0, false));
  }

  @Test
  void DB_오류도_밖으로_던지지_않음() {
    when(repository.findByDistrictIsNullOrderByIdDesc(any(Pageable.class))).thenThrow(new IllegalStateException("DB"));

    EventDistrictFiller.Summary summary = filler.fillMissing();

    assertThat(summary.stopped()).isTrue();
  }

  private static Event event(Long id, double lat, double lng) {
    Event e = Event.builder().title("가상 " + id).category("공연").latitude(lat).longitude(lng)
        .pubDate(LocalDateTime.of(2026, 11, 1, 12, 0)).build();
    ReflectionTestUtils.setField(e, "id", id);
    return e;
  }
}
