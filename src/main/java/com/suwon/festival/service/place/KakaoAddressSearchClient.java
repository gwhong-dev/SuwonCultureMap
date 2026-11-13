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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 카카오 로컬 API "주소 검색" 호출 (수원문화DB 행사는 장소 대신 주소가 들어 있음)
// - 인증: KakaoAK {REST 키} (KakaoPlaceSearchClient 와 같은 키)
// - 주소는 URI 변수로 넣고 엄격 인코딩
@Component
public class KakaoAddressSearchClient implements AddressSearchClient {

  static final String URL = "https://dapi.kakao.com/v2/local/search/address.json";
  static final int SIZE = 5;

  private final RestTemplate restTemplate;
  private final String restApiKey;

  public KakaoAddressSearchClient(RestTemplate restTemplate, @Value("${kakao.rest-api-key}") String restApiKey) {
    this.restTemplate = restTemplate;
    this.restApiKey = restApiKey;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Response {
    public List<Document> documents;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
      public String address_name;
      public String x;
      public String y;
    }
  }

  @Override
  public List<PlaceCandidate> search(String address) {
    URI uri = UriComponentsBuilder.fromUriString(URL)
        .queryParam("query", "{query}")
        .queryParam("size", SIZE)
        .encode()
        .buildAndExpand(address)
        .toUri();
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "KakaoAK " + restApiKey);
    Response body = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Response.class).getBody();

    List<PlaceCandidate> result = new ArrayList<>();
    if (body == null || body.documents == null) return result;
    for (Response.Document d : body.documents) {
      Double lat = parse(d.y);
      Double lng = parse(d.x);
      if (lat == null || lng == null) continue;
      result.add(new PlaceCandidate(d.address_name, d.address_name, null, lat, lng));
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
