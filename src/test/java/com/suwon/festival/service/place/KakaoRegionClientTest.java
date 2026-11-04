package com.suwon.festival.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

// 카카오 좌표 → 행정구역 호출 모양 — 가짜 서버(MockRestServiceServer)로 확인, 실제 호출 없음
class KakaoRegionClientTest {

  private MockRestServiceServer server;
  private KakaoRegionClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    client = new KakaoRegionClient(restTemplate, "test-kakao-key");
  }

  @Test
  void x는_경도_y는_위도로_보내고_키는_헤더로_보냄_행정동을_먼저_씀() {
    server.expect(requestTo(Matchers.startsWith(KakaoRegionClient.URL)))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "KakaoAK test-kakao-key"))
        .andExpect(request -> {
          var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
          assertThat(params.getFirst("x")).isEqualTo("127.01");
          assertThat(params.getFirst("y")).isEqualTo("37.28");
          // 키는 주소(URL)에 넣지 않음 → 로그 · 기록에 안 남게
          assertThat(request.getURI().toString()).doesNotContain("test-kakao-key");
        })
        .andRespond(withSuccess("""
            {"meta":{"total_count":2},"documents":[
              {"region_type":"B","region_1depth_name":"경기도","region_2depth_name":"수원시 권선구","code":"1"},
              {"region_type":"H","region_1depth_name":"경기도","region_2depth_name":"수원시 팔달구","code":"2"}
            ]}
            """, MediaType.APPLICATION_JSON));

    RegionLookupClient.Region region = client.lookup(37.28, 127.01);

    server.verify();
    assertThat(region).isEqualTo(new RegionLookupClient.Region("경기도", "수원시 팔달구"));
  }

  @Test
  void 행정동이_없으면_법정동() {
    server.expect(requestTo(Matchers.startsWith(KakaoRegionClient.URL)))
        .andRespond(withSuccess("""
            {"documents":[{"region_type":"B","region_1depth_name":"경기도","region_2depth_name":"수원시 영통구"}]}
            """, MediaType.APPLICATION_JSON));

    assertThat(client.lookup(37.25, 127.07).sigungu()).isEqualTo("수원시 영통구");
  }

  @Test
  void 결과가_없거나_바다라_구가_비면_NONE_또는_빈칸() {
    server.expect(requestTo(Matchers.startsWith(KakaoRegionClient.URL)))
        .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
    assertThat(client.lookup(37.0, 126.0)).isEqualTo(RegionLookupClient.Region.NONE);

    server.reset();
    server.expect(requestTo(Matchers.startsWith(KakaoRegionClient.URL)))
        .andRespond(withSuccess("""
            {"documents":[{"region_type":"H","region_1depth_name":"경기도","region_2depth_name":""}]}
            """, MediaType.APPLICATION_JSON));
    assertThat(client.lookup(37.0, 126.0).sigungu()).isNull();
  }

  @Test
  void 키_오류_같은_응답_오류는_예외로_던짐() {
    server.expect(requestTo(Matchers.startsWith(KakaoRegionClient.URL)))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> client.lookup(37.28, 127.01)).isInstanceOf(RestClientException.class);
  }
}
