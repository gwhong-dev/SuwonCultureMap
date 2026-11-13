package com.suwon.festival.service.transit;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.suwon.festival.config.RestTemplateConfig;

// 카카오 로컬 "카테고리로 장소 검색" SW8(지하철역) — 오시는 길 "가까운 역"
// - 장소 검색(KakaoPlaceSearchClient)과 같은 REST 키(kakao.rest-api-key), 새 외부 의존 없음
// - 상세 화면을 열 때 부르므로 경기도 정류소와 같은 짧은 타임아웃(2 · 3초)
// - 실패해도 예외를 밖으로 내지 않고 빈 목록 (역 칸만 숨김). 예외 메시지엔 요청 정보를 안 남김
@Component
public class KakaoSubwayClient {

  static final String URL = "https://dapi.kakao.com/v2/local/search/category.json";
  static final String CATEGORY = "SW8";
  static final int RADIUS_M = 1500; // 설계 문서 3번: 반경 1.5km 안
  static final int SIZE = 6;        // 같은 역 다른 호선(수원역 1호선 · 수인분당선)이 따로 와서 넉넉히

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  // 역 한 곳 (호선별 한 건). distanceMeters = 카카오가 준 직선거리
  public record SubwayPlace(String placeName, int distanceMeters) {
  }

  private final RestTemplate restTemplate;
  private final String restApiKey;

  @Autowired
  public KakaoSubwayClient(@Value("${kakao.rest-api-key:}") String restApiKey) {
    this(RestTemplateConfig.create(CONNECT_TIMEOUT, READ_TIMEOUT), restApiKey);
  }

  KakaoSubwayClient(RestTemplate restTemplate, String restApiKey) {
    this.restTemplate = restTemplate;
    this.restApiKey = restApiKey == null ? "" : restApiKey.strip();
  }

  public boolean isConfigured() {
    return !restApiKey.isEmpty();
  }

  // 좌표 주변 지하철역, 가까운 순. 키 없음 · 실패면 SubwayLookupException
  public List<SubwayPlace> findAround(double latitude, double longitude) {
    if (!isConfigured()) return List.of();
    URI uri = UriComponentsBuilder.fromUriString(URL)
        .queryParam("category_group_code", CATEGORY)
        .queryParam("x", longitude)
        .queryParam("y", latitude)
        .queryParam("radius", RADIUS_M)
        .queryParam("sort", "distance")
        .queryParam("size", SIZE)
        .build()
        .toUri();
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "KakaoAK " + restApiKey);

    Response body;
    try {
      body = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Response.class).getBody();
    } catch (RestClientException e) {
      throw new SubwayLookupException("카카오 역 검색 실패: " + e.getClass().getSimpleName());
    }
    // 본문 · documents 가 없으면 '역 없음' 이 아니라 실패 (#268 검증 ⚠️ — 빈 목록이면 화면이 "근처 지하철역 없음" 으로 잘못 보임)
    if (body == null || body.documents == null) throw new SubwayLookupException("카카오 역 검색 실패: 응답 본문 없음");
    List<SubwayPlace> result = new ArrayList<>();
    for (Document d : body.documents) {
      if (d.place_name == null || d.place_name.isBlank()) continue;
      result.add(new SubwayPlace(d.place_name.strip(), parseDistance(d.distance)));
    }
    result.sort(Comparator.comparingInt(SubwayPlace::distanceMeters));
    return result;
  }

  private static int parseDistance(String v) {
    if (v == null) return Integer.MAX_VALUE;
    try {
      return Integer.parseInt(v.strip());
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  public static class SubwayLookupException extends RuntimeException {
    public SubwayLookupException(String message) {
      super(message);
    }
  }

  // 카카오 카테고리 검색 응답 (필요한 칸만). distance 는 x · y 를 줬을 때만, 문자열로 옴
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Response {
    public List<Document> documents;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Document {
    public String place_name;
    public String distance;
  }
}
