package com.suwon.festival.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.EventService;
import com.suwon.festival.service.place.RegionLookupClient.Region;

import jakarta.persistence.EntityManager;

// V6 district 칸 저장 · 응답 · 좌표 변경 시 다시 계산 (H2, 카카오는 가짜)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventDistrictStoreTest {

  @Autowired private EventDistrictFiller filler;
  @Autowired private EventRepository eventRepository;
  @Autowired private EventService eventService;
  @Autowired private EntityManager em;
  @MockitoBean private RegionLookupClient client;

  @Test
  void 채운_구가_저장되고_응답에_실림_수원_밖은_null() {
    when(client.lookup(anyDouble(), anyDouble())).thenReturn(new Region("경기도", "화성시"));
    when(client.lookup(37.2801, 127.0101)).thenReturn(new Region("경기도", "수원시 팔달구"));
    Event inSuwon = eventRepository.saveAndFlush(event("구시험 팔달", 37.2801, 127.0101));
    Event outside = eventRepository.saveAndFlush(event("구시험 화성", 37.1001, 126.9001));

    filler.fillMissing();
    em.clear();

    assertThat(eventRepository.findById(inSuwon.getId()).orElseThrow().getDistrict()).isEqualTo("팔달구");
    assertThat(eventRepository.findById(outside.getId()).orElseThrow().getDistrict()).isEqualTo(Event.DISTRICT_OUTSIDE);
    assertThat(response(inSuwon.getId()).district()).isEqualTo("팔달구");
    assertThat(response(outside.getId()).district()).isNull(); // 화면에는 "수원 밖"을 따로 보내지 않음
  }

  @Test
  void 좌표가_바뀌면_구를_비우고_같으면_유지() {
    Event saved = eventRepository.saveAndFlush(event("구시험 이동", 37.2802, 127.0102));
    eventRepository.saveDistrict(saved.getId(), 37.2802, 127.0102, "팔달구");
    em.clear();

    Event same = eventRepository.findById(saved.getId()).orElseThrow();
    same.updateFrom(event("구시험 이동 제목만 바뀜", 37.2802, 127.0102));
    eventRepository.saveAndFlush(same);
    em.clear();
    assertThat(eventRepository.findById(saved.getId()).orElseThrow().getDistrict()).isEqualTo("팔달구");

    Event moved = eventRepository.findById(saved.getId()).orElseThrow();
    moved.updateFrom(event("구시험 이동", 37.2603, 127.0503));
    eventRepository.saveAndFlush(moved);
    em.clear();
    assertThat(eventRepository.findById(saved.getId()).orElseThrow().getDistrict()).isNull();
  }

  @Test
  void 계산하는_동안_좌표가_바뀌었으면_옛_좌표_값으로_저장하지_않음() {
    Event saved = eventRepository.saveAndFlush(event("구시험 경합", 37.2804, 127.0104));

    // 옛 좌표로 구한 값 → 지금 좌표와 다르니 0건
    assertThat(eventRepository.saveDistrict(saved.getId(), 37.9999, 127.9999, "팔달구")).isZero();
    assertThat(eventRepository.saveDistrict(saved.getId(), 37.2804, 127.0104, "팔달구")).isEqualTo(1);
    // 이미 채운 행은 덮어쓰지 않음
    assertThat(eventRepository.saveDistrict(saved.getId(), 37.2804, 127.0104, "권선구")).isZero();
  }

  private EventResponse response(Long id) {
    return eventService.toResponses(eventRepository.findAllById(java.util.List.of(id))).get(0);
  }

  private static Event event(String title, double lat, double lng) {
    return Event.builder().title(title).category("공연").latitude(lat).longitude(lng)
        .pubDate(LocalDateTime.of(2026, 11, 1, 12, 0)).build();
  }
}
