package com.suwon.festival.service.transit;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwon.festival.config.RestTemplateConfig;

// 경기도 정류소 조회 API (data.go.kr 15080666, 경기도 버스정보 GBIS) — 오시는 길 정류장 · 버스 번호
// - 주변정류소: 좌표(x = 경도, y = 위도) → 반경 500m 정류소 + distance(m). 반경은 API 고정값
// - 경유노선: 정류소 번호 → 지나는 노선(버스 번호 · 종류 · 방면)
// - 인증: 주소의 serviceKey (공공데이터포털 "인증키(Decoding)" 를 한 번만 인코딩해 붙임)
//   → 키가 든 주소를 로그 · 예외 메시지에 남기지 않음 (GbisException)
// - 키가 비어 있으면(설정 전) 호출하지 않고 빈 목록 → 화면은 정류장 칸만 숨김
// - 실제 응답 모양(11/11 확인): routeName 이 숫자(1007) · 글자("60-1") 섞임, mobileNo 앞 공백,
//   결과 없음 = resultCode 4, 키 오류 = HTTP 403 + SERVICE_KEY_IS_NOT_REGISTERED_ERROR
@Component
public class GbisStationClient {

  static final String BASE_URL = "https://apis.data.go.kr/6410000/busstationservice/v2";
  static final String AROUND_URL = BASE_URL + "/getBusStationAroundListv2";
  static final String VIA_ROUTE_URL = BASE_URL + "/getBusStationViaRouteListv2";

  // 상세 화면을 열 때 부르므로 오래 기다리지 않음 (설계 문서 6번: 타임아웃 2~3초)
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  private static final int RESULT_OK = 0;
  private static final int RESULT_NO_DATA = 4;

  // 공공데이터포털 게이트웨이 오류 (키 미등록 · 한도 초과 등): 영문 대문자 코드만 뽑아 남김
  // (XML 은 errMsg 가 "SERVICE ERROR" 이고 코드는 returnAuthMsg 에 오기도 함)
  private static final Pattern GATEWAY_ERROR = Pattern.compile(
      "<(?:errMsg|returnAuthMsg)>\\s*([A-Z_]+)\\s*</(?:errMsg|returnAuthMsg)>|\"errMsg\"\\s*:\\s*\"([A-Z_]+)\"");

  private static final ObjectMapper JSON = new ObjectMapper();

  // 주변 정류소 하나. distanceMeters = API 가 준 직선거리(m)
  public record Station(long stationId, String name, String mobileNo, int distanceMeters,
      double latitude, double longitude, boolean centerLane) {
  }

  // 정류소를 지나는 노선 하나. name = 버스 번호, typeName = 일반형시내버스 등, destName = 방면(종점)
  public record Route(long routeId, String name, String typeName, String destName) {
  }

  private final RestTemplate restTemplate;
  private final String serviceKey;

  @Autowired
  public GbisStationClient(@Value("${gg.bus.service-key:}") String serviceKey) {
    this(RestTemplateConfig.create(CONNECT_TIMEOUT, READ_TIMEOUT), serviceKey);
  }

  GbisStationClient(RestTemplate restTemplate, String serviceKey) {
    this.restTemplate = restTemplate;
    this.serviceKey = serviceKey == null ? "" : serviceKey.strip();
  }

  public boolean isConfigured() {
    return !serviceKey.isEmpty();
  }

  // 좌표 주변(500m) 정류소, 가까운 순. 없으면 빈 목록
  public List<Station> findAround(double latitude, double longitude) {
    if (!isConfigured()) return List.of();
    JsonNode body = call("주변정류소", AROUND_URL + "?format=json&x=" + longitude + "&y=" + latitude);
    List<Station> stations = readList(body, "busStationAroundList", n -> new Station(
        n.path("stationId").asLong(),
        text(n, "stationName"),
        text(n, "mobileNo"),
        n.path("distance").asInt(),
        n.path("y").asDouble(),
        n.path("x").asDouble(),
        "Y".equalsIgnoreCase(text(n, "centerYn"))));
    stations.sort(Comparator.comparingInt(Station::distanceMeters));
    return stations;
  }

  // 정류소를 지나는 노선 (API 가 준 순서). 없으면 빈 목록
  public List<Route> findViaRoutes(long stationId) {
    if (!isConfigured()) return List.of();
    JsonNode body = call("경유노선", VIA_ROUTE_URL + "?format=json&stationId=" + stationId);
    return readList(body, "busRouteList", n -> new Route(
        n.path("routeId").asLong(),
        text(n, "routeName"),
        text(n, "routeTypeName"),
        text(n, "routeDestName")));
  }

  // 호출 → msgBody (결과 없음이면 null). 실패는 키 없는 메시지로 GbisException
  private JsonNode call(String what, String urlWithoutKey) {
    URI uri = URI.create(urlWithoutKey + "&serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8));
    byte[] raw;
    try {
      raw = restTemplate.getForObject(uri, byte[].class);
    } catch (HttpStatusCodeException e) {
      throw new GbisException("경기도 " + what + " 호출 실패: HTTP " + e.getStatusCode().value()
          + gatewayCode(e.getResponseBodyAsString(StandardCharsets.UTF_8)));
    } catch (RestClientException e) {
      // 연결 실패 · 시간 초과 등. e.getMessage() 에는 키가 든 주소가 있어 종류만 남김
      throw new GbisException("경기도 " + what + " 호출 실패: " + e.getClass().getSimpleName());
    }
    String text = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);

    JsonNode root;
    try {
      root = JSON.readTree(text);
    } catch (Exception e) {
      root = null;
    }
    JsonNode header = root == null ? null : root.path("response").path("msgHeader");
    if (header == null || !header.has("resultCode")) {
      // 200 이어도 게이트웨이가 XML 오류(한도 초과 등)를 줄 수 있음
      throw new GbisException("경기도 " + what + " 응답 형식이 다름" + gatewayCode(text));
    }
    int code = header.path("resultCode").asInt(-1);
    if (code == RESULT_NO_DATA) return null;
    if (code != RESULT_OK) {
      throw new GbisException("경기도 " + what + " 오류: resultCode " + code + " " + text(header, "resultMessage"));
    }
    return root.path("response").path("msgBody");
  }

  // 목록 칸: 여러 건이면 배열, 한 건이면 객체 하나로 올 수 있어 둘 다 받음
  private static <T> List<T> readList(JsonNode msgBody, String field, Function<JsonNode, T> mapper) {
    List<T> result = new ArrayList<>();
    if (msgBody == null) return result;
    JsonNode list = msgBody.path(field);
    if (list.isArray()) {
      for (JsonNode n : list) result.add(mapper.apply(n));
    } else if (list.isObject()) {
      result.add(mapper.apply(list));
    }
    return result;
  }

  // 숫자로 와도 글자로 (routeName 1007), 앞뒤 공백 제거 (mobileNo " 03058"), 없으면 빈 글자
  private static String text(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isMissingNode() || v.isNull() ? "" : v.asText().strip();
  }

  private static String gatewayCode(String body) {
    if (body == null) return "";
    Matcher m = GATEWAY_ERROR.matcher(body);
    if (!m.find()) return "";
    return " " + (m.group(1) != null ? m.group(1) : m.group(2));
  }
}
