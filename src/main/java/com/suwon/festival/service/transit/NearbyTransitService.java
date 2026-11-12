package com.suwon.festival.service.transit;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.suwon.festival.service.transit.GbisStationClient.Route;
import com.suwon.festival.service.transit.GbisStationClient.Station;
import com.suwon.festival.service.transit.KakaoSubwayClient.SubwayPlace;

import lombok.extern.slf4j.Slf4j;

// 오시는 길: 장소 좌표 → 가까운 버스 정류장 2~3곳(버스 번호) + 가까운 역 (설계 docs/transit-directions.md 3 · 4번)
// - 정류장: 같은 이름(길 건너 양쪽 등)은 한 곳으로 합침 → 거리는 가장 가까운 쪽, 버스 번호는 합집합
// - 캐시: 좌표 소수 4자리(약 10m) 반올림을 키로, 메모리에 저장 (재시작하면 비움)
//   버스 = 성공 24시간 (경기도 개발계정 하루 1,000회 한도), 역 = 1시간 (카카오 결과 저장 허용 문구 확인 전, 설계 6번)
//   실패(키 오류 · 시간 초과)는 10분만 기억해 같은 장소를 계속 다시 부르지 않음
// - 키가 없거나 외부 호출이 실패하면 그 칸만 빈 목록 → 화면이 조용히 숨김
@Slf4j
@Service
public class NearbyTransitService {

  static final int MAX_STOPS = 3;              // 정류장 2~3곳
  static final int MAX_STATIONS_PER_STOP = 2;  // 같은 이름 정류소 중 경유노선을 부를 수 (길 건너 양쪽) → 장소 1곳 최대 1 + 6회
  static final int MAX_SUBWAYS = 2;            // 역 1~2곳
  static final int WALK_METERS_PER_MINUTE = 67; // 시속 약 4km, 직선거리 기준 추정

  static final Duration BUS_TTL = Duration.ofHours(24);
  static final Duration SUBWAY_TTL = Duration.ofHours(1);
  static final Duration FAIL_TTL = Duration.ofMinutes(10);
  static final int CACHE_CLEAN_SIZE = 500; // 이 수를 넘으면 저장할 때 만료된 것을 치움

  private static final String RESERVED = "(예약)";
  private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");

  // 정류장 하나 (같은 이름 합친 결과). distanceMeters = 가장 가까운 정류소 거리
  public record BusStop(String name, int distanceMeters, int walkMinutes, List<String> routes) {
  }

  // 역 하나. lines = 호선 (카카오 장소명 "수원역 1호선" → 1호선)
  public record Subway(String name, List<String> lines, int distanceMeters) {
  }

  public record NearbyTransit(List<BusStop> busStops, List<Subway> subways) {
    public static final NearbyTransit EMPTY = new NearbyTransit(List.of(), List.of());
  }

  private final GbisStationClient gbis;
  private final KakaoSubwayClient kakao;
  private final Clock clock;
  private final TtlCache<List<BusStop>> busCache = new TtlCache<>();
  private final TtlCache<List<Subway>> subwayCache = new TtlCache<>();

  @Autowired
  public NearbyTransitService(GbisStationClient gbis, KakaoSubwayClient kakao) {
    this(gbis, kakao, Clock.systemUTC());
  }

  NearbyTransitService(GbisStationClient gbis, KakaoSubwayClient kakao, Clock clock) {
    this.gbis = gbis;
    this.kakao = kakao;
    this.clock = clock;
  }

  public NearbyTransit find(double latitude, double longitude) {
    if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) return NearbyTransit.EMPTY;
    String key = cacheKey(latitude, longitude);
    List<BusStop> stops = busCache.get(key, clock.instant(), () -> loadBusStops(latitude, longitude));
    List<Subway> subways = subwayCache.get(key, clock.instant(), () -> loadSubways(latitude, longitude));
    return new NearbyTransit(stops, subways);
  }

  // 좌표 소수 4자리(위도 약 11m · 경도 약 9m) 반올림
  static String cacheKey(double latitude, double longitude) {
    return String.format(Locale.ROOT, "%.4f,%.4f", latitude, longitude);
  }

  // ----- 버스 -----

  private Cached<List<BusStop>> loadBusStops(double latitude, double longitude) {
    if (!gbis.isConfigured()) return new Cached<>(List.of(), FAIL_TTL); // 키를 나중에 넣어도 10분 안에 반영
    try {
      List<StopGroup> groups = groupByName(gbis.findAround(latitude, longitude));
      List<BusStop> stops = new ArrayList<>();
      for (StopGroup g : groups.subList(0, Math.min(MAX_STOPS, groups.size()))) {
        List<String> names = new ArrayList<>();
        for (Station s : g.stations.subList(0, Math.min(MAX_STATIONS_PER_STOP, g.stations.size()))) {
          for (Route r : gbis.findViaRoutes(s.stationId())) names.add(r.name());
        }
        stops.add(new BusStop(g.name, g.distanceMeters(), walkMinutes(g.distanceMeters()), routeNames(names)));
      }
      return new Cached<>(List.copyOf(stops), BUS_TTL);
    } catch (GbisException e) {
      // GbisException 메시지에는 키 · 주소가 없음 (GbisStationClient 참고)
      log.warn("오시는 길 버스 정보 실패 ({}): {}", cacheKey(latitude, longitude), e.getMessage());
      return new Cached<>(List.of(), FAIL_TTL);
    }
  }

  // 같은 이름 정류소 묶음 (가까운 순 유지). 이름이 빈 정류소는 버림
  static List<StopGroup> groupByName(List<Station> stations) {
    Map<String, StopGroup> byName = new LinkedHashMap<>();
    List<Station> sorted = new ArrayList<>(stations);
    sorted.sort(Comparator.comparingInt(Station::distanceMeters));
    for (Station s : sorted) {
      String name = s.name() == null ? "" : s.name().strip();
      if (name.isEmpty()) continue;
      byName.computeIfAbsent(name, StopGroup::new).stations.add(s);
    }
    return new ArrayList<>(byName.values());
  }

  static final class StopGroup {
    final String name;
    final List<Station> stations = new ArrayList<>(); // 가까운 순

    StopGroup(String name) {
      this.name = name;
    }

    int distanceMeters() {
      return stations.get(0).distanceMeters();
    }
  }

  // 버스 번호: 빈 값 · 중복 제거, "1007(예약)" 은 "1007" 이 이미 있으면 뺌, 번호 순(숫자 먼저)
  static List<String> routeNames(List<String> raw) {
    Set<String> names = new LinkedHashSet<>();
    for (String n : raw) {
      if (n != null && !n.isBlank()) names.add(n.strip());
    }
    names.removeIf(n -> n.endsWith(RESERVED) && names.contains(n.substring(0, n.length() - RESERVED.length()).strip()));
    List<String> result = new ArrayList<>(names);
    result.sort(NearbyTransitService::compareRouteNames);
    return List.copyOf(result);
  }

  // 앞 숫자가 있으면 숫자 크기로 (2 < 13 < 60-1 < 700-2), 숫자 없는 이름(마을 · 영문)은 뒤로
  static int compareRouteNames(String a, String b) {
    Matcher ma = LEADING_NUMBER.matcher(a);
    Matcher mb = LEADING_NUMBER.matcher(b);
    boolean na = ma.find();
    boolean nb = mb.find();
    if (na != nb) return na ? -1 : 1;
    if (na) {
      int c = new BigInteger(ma.group(1)).compareTo(new BigInteger(mb.group(1)));
      if (c != 0) return c;
    }
    return a.compareTo(b);
  }

  static int walkMinutes(int distanceMeters) {
    return Math.max(1, (int) Math.ceil(distanceMeters / (double) WALK_METERS_PER_MINUTE));
  }

  // ----- 역 -----

  private Cached<List<Subway>> loadSubways(double latitude, double longitude) {
    if (!kakao.isConfigured()) return new Cached<>(List.of(), FAIL_TTL);
    try {
      return new Cached<>(groupSubways(kakao.findAround(latitude, longitude)), SUBWAY_TTL);
    } catch (RuntimeException e) {
      log.warn("오시는 길 역 정보 실패 ({}): {}", cacheKey(latitude, longitude), e.getMessage());
      return new Cached<>(List.of(), FAIL_TTL);
    }
  }

  // "수원역 1호선" · "수원역 수인분당선" → 수원역 [1호선, 수인분당선]. 가까운 순 최대 2곳
  static List<Subway> groupSubways(List<SubwayPlace> places) {
    Map<String, List<String>> lines = new LinkedHashMap<>();
    Map<String, Integer> distance = new LinkedHashMap<>();
    List<SubwayPlace> sorted = new ArrayList<>(places);
    sorted.sort(Comparator.comparingInt(SubwayPlace::distanceMeters));
    for (SubwayPlace p : sorted) {
      String full = p.placeName().strip();
      int space = full.indexOf(' ');
      String name = space > 0 ? full.substring(0, space) : full;
      String line = space > 0 ? full.substring(space + 1).strip() : "";
      List<String> l = lines.computeIfAbsent(name, k -> new ArrayList<>());
      if (!line.isEmpty() && !l.contains(line)) l.add(line);
      distance.putIfAbsent(name, p.distanceMeters());
    }
    List<Subway> result = new ArrayList<>();
    for (Map.Entry<String, List<String>> e : lines.entrySet()) {
      if (result.size() == MAX_SUBWAYS) break;
      result.add(new Subway(e.getKey(), List.copyOf(e.getValue()), distance.get(e.getKey())));
    }
    return List.copyOf(result);
  }

  // ----- 캐시 -----

  record Cached<T>(T value, Duration ttl) {
  }

  private record Entry<T>(T value, Instant expiresAt) {
  }

  // 키별 값 + 만료 시각. 같은 키를 동시에 처음 부르면 외부 호출이 두 번 날 수 있음 (장소 수가 적어 감수)
  static final class TtlCache<T> {
    private final Map<String, Entry<T>> map = new ConcurrentHashMap<>();

    T get(String key, Instant now, Supplier<Cached<T>> loader) {
      Entry<T> hit = map.get(key);
      if (hit != null && now.isBefore(hit.expiresAt())) return hit.value();
      Cached<T> loaded = loader.get();
      if (map.size() >= CACHE_CLEAN_SIZE) map.values().removeIf(e -> !now.isBefore(e.expiresAt()));
      map.put(key, new Entry<>(loaded.value(), now.plus(loaded.ttl())));
      return loaded.value();
    }

    int size() {
      return map.size();
    }
  }
}
