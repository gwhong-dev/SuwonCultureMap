package com.suwon.festival.service.place;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.suwon.festival.dto.kakao.KakaoRegionResponse;

// 카카오 로컬 API "좌표로 행정구역정보 받기" 호출
// - 인증: Authorization: KakaoAK {REST 키} (장소 검색 · 로그인과 같은 kakao.rest-api-key). 키는 주소에 넣지 않음
// - x = 경도, y = 위도 (WGS84 기본값)
// - 행정동(H)을 먼저 보고, 없으면 법정동(B). 구 경계는 두 기준이 같음
@Component
public class KakaoRegionClient implements RegionLookupClient {

  static final String URL = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json";

  private final RestTemplate restTemplate;
  private final String restApiKey;

  public KakaoRegionClient(RestTemplate restTemplate, @Value("${kakao.rest-api-key}") String restApiKey) {
    this.restTemplate = restTemplate;
    this.restApiKey = restApiKey;
  }

  @Override
  public Region lookup(double latitude, double longitude) {
    URI uri = UriComponentsBuilder.fromUriString(URL)
        .queryParam("x", longitude)
        .queryParam("y", latitude)
        .build()
        .toUri();

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "KakaoAK " + restApiKey);

    KakaoRegionResponse body = restTemplate
        .exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), KakaoRegionResponse.class)
        .getBody();
    if (body == null || body.documents == null) return Region.NONE;

    KakaoRegionResponse.Document picked = null;
    for (KakaoRegionResponse.Document d : body.documents) {
      if ("H".equals(d.region_type)) {
        picked = d;
        break;
      }
      if (picked == null) picked = d;
    }
    if (picked == null) return Region.NONE;
    return new Region(blankToNull(picked.region_1depth_name), blankToNull(picked.region_2depth_name));
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.strip();
  }
}
