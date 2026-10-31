package com.suwon.festival.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

// 카카오 장소 검색 호출 모양 — 가짜 서버(MockRestServiceServer)로 확인, 실제 호출 없음
class KakaoPlaceSearchClientTest {

  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private KakaoPlaceSearchClient client;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    client = new KakaoPlaceSearchClient(restTemplate, "test-kakao-key");
  }

  @Test
  void 키는_헤더로_보내고_한글_검색어는_인코딩해서_보냄() {
    server.expect(requestTo(Matchers.startsWith(KakaoPlaceSearchClient.URL)))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "KakaoAK test-kakao-key"))
        .andExpect(request -> {
          var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
          String query = URLDecoder.decode(params.getFirst("query"), StandardCharsets.UTF_8);
          assertThat(query).isEqualTo("가상 아트홀");
          assertThat(params.getFirst("radius")).isEqualTo("20000");
          // 키는 주소(URL)에 넣지 않음 → 로그 · 기록에 안 남게
          assertThat(request.getURI().toString()).doesNotContain("test-kakao-key");
        })
        .andRespond(withSuccess("""
            {"documents":[
              {"place_name":"가상 아트홀","address_name":"경기 수원시 팔달구 가상동 1",
               "road_address_name":"경기 수원시 팔달구 가상로 1","x":"127.01","y":"37.28","id":"1"},
              {"place_name":"좌표 이상","address_name":"경기 수원시","x":"abc","y":""}
            ],"meta":{"total_count":2}}
            """, MediaType.APPLICATION_JSON));

    List<PlaceCandidate> result = client.search("가상 아트홀");

    server.verify();
    assertThat(result).hasSize(1); // 좌표가 숫자가 아닌 건 버림
    PlaceCandidate c = result.get(0);
    assertThat(c.name()).isEqualTo("가상 아트홀");
    assertThat(c.lat()).isEqualTo(37.28); // y = 위도
    assertThat(c.lng()).isEqualTo(127.01); // x = 경도
  }

  @Test
  void 결과가_비어도_빈_목록() {
    server.expect(requestTo(Matchers.startsWith(KakaoPlaceSearchClient.URL)))
        .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
    assertThat(client.search("없는 곳")).isEmpty();
  }

  @Test
  void 서버_오류는_예외로_올라가고_Geocoder가_ERROR로_바꿈() {
    server.expect(requestTo(Matchers.startsWith(KakaoPlaceSearchClient.URL))).andRespond(withServerError());
    var r = new PlaceGeocoder(client).geocode("가상 아트홀");
    assertThat(r.status()).isEqualTo(PlaceGeocoder.Status.ERROR);
    assertThat(r.reason()).doesNotContain("test-kakao-key");
  }
}
