package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.transit.NearbyTransitService;
import com.suwon.festival.service.transit.NearbyTransitService.BusStop;
import com.suwon.festival.service.transit.NearbyTransitService.NearbyTransit;
import com.suwon.festival.service.transit.NearbyTransitService.Subway;

// /api/events/{id}/transit — 행사 좌표로만 조회 (test 프로필, H2, 외부 호출 없음: 서비스는 가짜)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransitControllerTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private EventRepository eventRepository;

  @MockitoBean
  private NearbyTransitService transitService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  private Event save(Long idx, Double lat, Double lng) {
    return eventRepository.save(Event.builder()
        .idx(idx).title("정조 K 스테이지").category("공연")
        .latitude(lat).longitude(lng)
        .info("장소: 정조테마공연장").pubDate(LocalDateTime.of(2026, 10, 1, 0, 0))
        .startDate(LocalDate.of(2026, 11, 7)).endDate(LocalDate.of(2026, 11, 7))
        .build());
  }

  @Test
  void 행사_좌표로_정류장과_역을_돌려줌() throws Exception {
    Event e = save(910001L, 37.2810212, 127.0162158);
    when(transitService.find(37.2810212, 127.0162158)).thenReturn(new NearbyTransit(
        List.of(new BusStop("행궁동", 120, 2, List.of("2", "13"))),
        List.of(new Subway("수원역", List.of("1호선"), 900))));

    MockHttpServletResponse res = mockMvc.perform(get("/api/events/" + e.getId() + "/transit")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8))
        .contains("\"busStops\":[{\"name\":\"행궁동\",\"distanceMeters\":120,\"walkMinutes\":2,\"routes\":[\"2\",\"13\"]}]")
        .contains("\"subways\":[{\"name\":\"수원역\",\"lines\":[\"1호선\"],\"distanceMeters\":900}]");
  }

  @Test
  void 좌표_없는_행사는_외부를_부르지_않고_빈_결과() throws Exception {
    Event e = save(910002L, 0.0, 0.0); // 좌표 칸은 필수 → 위치 모름 = 0

    MockHttpServletResponse res = mockMvc.perform(get("/api/events/" + e.getId() + "/transit")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("{\"busStops\":[],\"subways\":[]}");
    verify(transitService, never()).find(anyDouble(), anyDouble());
  }

  @Test
  void 없는_행사는_404() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get("/api/events/987654321/transit")).andReturn().getResponse();
    assertThat(res.getStatus()).isEqualTo(404);
  }
}
