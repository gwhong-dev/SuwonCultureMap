package com.suwon.festival.service.transit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.suwon.festival.service.transit.GbisStationClient.Route;
import com.suwon.festival.service.transit.GbisStationClient.Station;
import com.suwon.festival.service.transit.KakaoSubwayClient.SubwayPlace;
import com.suwon.festival.service.transit.NearbyTransitService.BusStop;
import com.suwon.festival.service.transit.NearbyTransitService.NearbyTransit;
import com.suwon.festival.service.transit.NearbyTransitService.Subway;

// 오시는 길 합치기 · 자르기 · 캐시 — 외부 호출 없이 가짜 클라이언트(Mockito)로 확인
class NearbyTransitServiceTest {

  // 화성행궁 광장 근처
  private static final double LAT = 37.28188;
  private static final double LNG = 127.01439;

  private GbisStationClient gbis;
  private KakaoSubwayClient kakao;
  private MutableClock clock;
  private NearbyTransitService service;

  @BeforeEach
  void setUp() {
    gbis = mock(GbisStationClient.class);
    kakao = mock(KakaoSubwayClient.class);
    when(gbis.isConfigured()).thenReturn(true);
    when(kakao.isConfigured()).thenReturn(true);
    clock = new MutableClock(Instant.parse("2026-11-12T00:00:00Z"));
    service = new NearbyTransitService(gbis, kakao, clock);
  }

  private static Station station(long id, String name, int distance) {
    return new Station(id, name, "0" + id, distance, LAT, LNG, false);
  }

  private void routes(long stationId, String... names) {
    List<Route> list = java.util.Arrays.stream(names).map(n -> new Route(1L, n, "일반형시내버스", "수원역")).toList();
    when(gbis.findViaRoutes(stationId)).thenReturn(list);
  }

  @Test
  void 같은_이름_정류장은_하나로_합치고_버스_번호는_합집합() {
    // 행궁동 양쪽(길 건너) + 팔달문 1곳
    when(gbis.findAround(LAT, LNG)).thenReturn(List.of(
        station(1, "행궁동", 120),
        station(2, "팔달문", 200),
        station(3, "행궁동", 160)));
    routes(1, "13", "2", "700-2");
    routes(3, "2", "60-1");
    routes(2, "11");

    List<BusStop> stops = service.find(LAT, LNG).busStops();

    assertThat(stops).extracting(BusStop::name).containsExactly("행궁동", "팔달문");
    BusStop first = stops.get(0);
    assertThat(first.distanceMeters()).isEqualTo(120); // 가까운 쪽 거리
    assertThat(first.walkMinutes()).isEqualTo(2);     // 120m / 67m = 1.8 → 올림
    assertThat(first.routes()).containsExactly("2", "13", "60-1", "700-2"); // 중복 빼고 번호 순
  }

  @Test
  void 거리순_3곳까지만_자르고_경유노선도_그만큼만_부름() {
    // 순서가 섞여 와도 가까운 순
    when(gbis.findAround(LAT, LNG)).thenReturn(List.of(
        station(5, "다", 450),
        station(1, "가", 100),
        station(4, "라", 480),
        station(2, "나", 300),
        station(3, "마", 490)));
    when(gbis.findViaRoutes(anyLong())).thenReturn(List.of());

    List<BusStop> stops = service.find(LAT, LNG).busStops();

    assertThat(stops).extracting(BusStop::name).containsExactly("가", "나", "다");
    verify(gbis, times(3)).findViaRoutes(anyLong());
  }

  @Test
  void 같은_이름이_여러_곳이어도_경유노선은_2곳까지만() {
    when(gbis.findAround(LAT, LNG)).thenReturn(List.of(
        station(1, "수원역", 100), station(2, "수원역", 110), station(3, "수원역", 120)));
    when(gbis.findViaRoutes(anyLong())).thenReturn(List.of());

    service.find(LAT, LNG);

    verify(gbis).findViaRoutes(1L);
    verify(gbis).findViaRoutes(2L);
    verify(gbis, never()).findViaRoutes(3L);
  }

  @Test
  void 예약_노선은_같은_번호가_있으면_뺌() {
    assertThat(NearbyTransitService.routeNames(List.of("1007(예약)", "1007", "8800(예약)", " ", "2")))
        .containsExactly("2", "1007", "8800(예약)");
  }

  @Test
  void 같은_좌표는_캐시로_한_번만_부르고_24시간_뒤_다시_부름() {
    when(gbis.findAround(anyDouble(), anyDouble())).thenReturn(List.of(station(1, "행궁동", 120)));
    routes(1, "2");
    when(kakao.findAround(anyDouble(), anyDouble())).thenReturn(List.of(new SubwayPlace("화서역 1호선", 1200)));

    NearbyTransit first = service.find(LAT, LNG);
    NearbyTransit again = service.find(LAT + 0.00001, LNG - 0.00001); // 소수 4자리 반올림이 같은 좌표

    assertThat(again).isEqualTo(first);
    verify(gbis, times(1)).findAround(anyDouble(), anyDouble());
    verify(gbis, times(1)).findViaRoutes(anyLong());
    verify(kakao, times(1)).findAround(anyDouble(), anyDouble());

    clock.add(Duration.ofHours(2)); // 역(1시간)만 만료
    service.find(LAT, LNG);
    verify(gbis, times(1)).findAround(anyDouble(), anyDouble());
    verify(kakao, times(2)).findAround(anyDouble(), anyDouble());

    clock.add(Duration.ofHours(23)); // 버스(24시간)도 만료
    service.find(LAT, LNG);
    verify(gbis, times(2)).findAround(anyDouble(), anyDouble());
  }

  @Test
  void 키가_없으면_외부_호출_없이_빈_결과() {
    RestTemplate rest = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build(); // 요청이 오면 실패
    NearbyTransitService noKey = new NearbyTransitService(
        new GbisStationClient(rest, ""), new KakaoSubwayClient(rest, " "), clock);

    assertThat(noKey.find(LAT, LNG)).isEqualTo(NearbyTransit.EMPTY);
    server.verify();
  }

  @Test
  void 버스_호출이_실패하면_버스만_빈_목록이고_10분_뒤_다시_시도() {
    when(gbis.findAround(anyDouble(), anyDouble())).thenThrow(new GbisException("경기도 주변정류소 호출 실패: HTTP 403"));
    when(kakao.findAround(anyDouble(), anyDouble())).thenReturn(List.of(new SubwayPlace("수원역 1호선", 900)));

    NearbyTransit result = service.find(LAT, LNG);
    assertThat(result.busStops()).isEmpty();
    assertThat(result.subways()).extracting(Subway::name).containsExactly("수원역");
    assertThat(result.subwayChecked()).isTrue();

    service.find(LAT, LNG); // 실패도 잠시 기억 → 다시 안 부름
    verify(gbis, times(1)).findAround(anyDouble(), anyDouble());

    clock.add(Duration.ofMinutes(11));
    service.find(LAT, LNG);
    verify(gbis, times(2)).findAround(anyDouble(), anyDouble());
  }

  @Test
  void 예상_못한_예외도_버스만_빈_목록으로_10분_기억() {
    when(gbis.findAround(anyDouble(), anyDouble())).thenThrow(new IllegalStateException("응답 모양이 다름"));

    assertThat(service.find(LAT, LNG).busStops()).isEmpty(); // 500 이 아니라 빈 목록
    service.find(LAT, LNG);
    verify(gbis, times(1)).findAround(anyDouble(), anyDouble());
  }

  @Test
  void 하루_호출_상한을_넘으면_부르지_않고_다음_날_다시() {
    // 상한 7 = 장소 1곳 몫 (1 + 3 × 2)
    NearbyTransitService limited = new NearbyTransitService(gbis, kakao, clock, NearbyTransitService.CALLS_PER_PLACE);
    when(gbis.findAround(anyDouble(), anyDouble())).thenReturn(List.of(station(1, "행궁동", 120)));
    routes(1, "2");

    assertThat(limited.find(LAT, LNG).busStops()).hasSize(1);
    assertThat(limited.find(37.2657, 127.0001).busStops()).isEmpty(); // 다른 장소: 오늘 몫 다 씀
    verify(gbis, times(1)).findAround(anyDouble(), anyDouble());

    clock.add(Duration.ofHours(24)); // 다음 날 (한국 시간 기준 날짜가 바뀜)
    assertThat(limited.find(37.2657, 127.0001).busStops()).hasSize(1);
    verify(gbis, times(2)).findAround(anyDouble(), anyDouble());
  }

  @Test
  void 역_검색이_실패해도_버스는_그대로() {
    when(gbis.findAround(anyDouble(), anyDouble())).thenReturn(List.of(station(1, "행궁동", 120)));
    routes(1, "2");
    when(kakao.findAround(anyDouble(), anyDouble()))
        .thenThrow(new KakaoSubwayClient.SubwayLookupException("카카오 역 검색 실패: ResourceAccessException"));

    NearbyTransit result = service.find(LAT, LNG);

    assertThat(result.busStops()).hasSize(1);
    assertThat(result.subways()).isEmpty();
    assertThat(result.subwayChecked()).isFalse(); // 실패 → 화면은 역 칸을 숨김 ("없음" 줄 아님)
  }

  @Test
  void 역_검색이_끝났는데_비면_정말_없음으로_표시() {
    when(gbis.findAround(anyDouble(), anyDouble())).thenReturn(List.of());
    when(kakao.findAround(anyDouble(), anyDouble())).thenReturn(List.of());

    NearbyTransit result = service.find(LAT, LNG);

    assertThat(result.subways()).isEmpty();
    assertThat(result.subwayChecked()).isTrue(); // 1.5km 안에 역 없음 → 화면에 "근처 지하철역 없음"
    service.find(LAT, LNG); // 캐시에서도 같은 표시
    assertThat(service.find(LAT, LNG).subwayChecked()).isTrue();
    verify(kakao, times(1)).findAround(anyDouble(), anyDouble());
  }

  @Test
  void 역_키가_없으면_검색_안_함으로_표시() {
    when(kakao.isConfigured()).thenReturn(false);
    when(gbis.findAround(anyDouble(), anyDouble())).thenReturn(List.of());

    assertThat(service.find(LAT, LNG).subwayChecked()).isFalse();
    verify(kakao, never()).findAround(anyDouble(), anyDouble());
  }

  @Test
  void 역은_호선을_묶고_가까운_2곳까지() {
    List<Subway> subways = NearbyTransitService.groupSubways(List.of(
        new SubwayPlace("수원역 수인분당선", 950),
        new SubwayPlace("수원역 1호선", 900),
        new SubwayPlace("매교역 수인분당선", 1100),
        new SubwayPlace("화서역 1호선", 1400)));

    assertThat(subways).containsExactly(
        new Subway("수원역", List.of("1호선", "수인분당선"), 900),
        new Subway("매교역", List.of("수인분당선"), 1100));
  }

  @Test
  void 도보_분은_67미터당_1분_올림_최소_1분() {
    assertThat(NearbyTransitService.walkMinutes(0)).isEqualTo(1);
    assertThat(NearbyTransitService.walkMinutes(67)).isEqualTo(1);
    assertThat(NearbyTransitService.walkMinutes(68)).isEqualTo(2);
    assertThat(NearbyTransitService.walkMinutes(500)).isEqualTo(8);
  }

  // 시험에서 시각을 앞으로 옮기는 시계
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void add(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
