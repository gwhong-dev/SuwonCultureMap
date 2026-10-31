package com.suwon.festival.service.place;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.suwon.festival.dto.kakao.KakaoLocalSearchResponse;

// 카카오 로컬 API "키워드로 장소 검색" 호출
// - 인증: Authorization: KakaoAK {REST 키} (로그인에 쓰는 kakao.rest-api-key 와 같은 키)
// - 수원시청 근처(반경 20km)를 우선으로 정확도순 검색. 수원 밖 결과는 PlaceGeocoder 가 다시 거름
@Component
public class KakaoPlaceSearchClient implements PlaceSearchClient {

  static final String URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
  // 수원시청 (검색 우선 위치)
  static final String CENTER_X = "127.0286";
  static final String CENTER_Y = "37.2636";
  static final int RADIUS_M = 20000; // 카카오 최대값
  static final int SIZE = 15;        // 한 번에 받는 최대 개수

  private final RestTemplate restTemplate;
  private final String restApiKey;

  public KakaoPlaceSearchClient(RestTemplate restTemplate, @Value("${kakao.rest-api-key}") String restApiKey) {
    this.restTemplate = restTemplate;
    this.restApiKey = restApiKey;
  }

  @Override
  public List<PlaceCandidate> search(String query) {
    URI uri = UriComponentsBuilder.fromUriString(URL)
        .queryParam("query", query)
        .queryParam("x", CENTER_X)
        .queryParam("y", CENTER_Y)
        .queryParam("radius", RADIUS_M)
        .queryParam("sort", "accuracy")
        .queryParam("size", SIZE)
        .encode()
        .build()
        .toUri();

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "KakaoAK " + restApiKey);

    KakaoLocalSearchResponse body = restTemplate
        .exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), KakaoLocalSearchResponse.class)
        .getBody();

    List<PlaceCandidate> result = new ArrayList<>();
    if (body == null || body.documents == null) return result;
    for (KakaoLocalSearchResponse.Document d : body.documents) {
      Double lat = parse(d.y);
      Double lng = parse(d.x);
      if (lat == null || lng == null) continue; // 좌표가 이상한 건 버림
      result.add(new PlaceCandidate(d.place_name, d.address_name, d.road_address_name, lat, lng));
    }
    return result;
  }

  private static Double parse(String v) {
    if (v == null) return null;
    try {
      double d = Double.parseDouble(v.strip());
      return Double.isFinite(d) ? d : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
