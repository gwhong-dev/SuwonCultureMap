package com.suwon.festival.service.transit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.suwon.festival.service.transit.KakaoSubwayClient.SubwayPlace;

// 카카오 카테고리 검색(SW8) 호출 모양 — 가짜 서버, 실제 호출 없음
class KakaoSubwayClientTest {

  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private KakaoSubwayClient client;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    client = new KakaoSubwayClient(restTemplate, "test-kakao-key");
  }

  @Test
  void 좌표_반경_거리순으로_묻고_가까운_순으로_돌려줌() {
    server.expect(requestTo(Matchers.startsWith(KakaoSubwayClient.URL)))
        .andExpect(queryParam("category_group_code", "SW8"))
        .andExpect(queryParam("x", "127.01439"))
        .andExpect(queryParam("y", "37.28188"))
        .andExpect(queryParam("radius", "1500"))
        .andExpect(queryParam("sort", "distance"))
        .andExpect(header("Authorization", "KakaoAK test-kakao-key"))
        .andRespond(withSuccess("""
            {"documents":[
              {"place_name":"수원역 수인분당선","distance":"950"},
              {"place_name":"수원역 1호선","distance":"900"},
              {"place_name":" ","distance":"10"}
            ],"meta":{"is_end":true}}
            """, MediaType.APPLICATION_JSON));

    assertThat(client.findAround(37.28188, 127.01439)).containsExactly(
        new SubwayPlace("수원역 1호선", 900), new SubwayPlace("수원역 수인분당선", 950));
    server.verify();
  }

  @Test
  void 오류면_예외_메시지에_키를_남기지_않음() {
    server.expect(requestTo(Matchers.startsWith(KakaoSubwayClient.URL)))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> client.findAround(37.28188, 127.01439))
        .isInstanceOf(KakaoSubwayClient.SubwayLookupException.class)
        .hasMessageNotContaining("test-kakao-key");
  }

  @Test
  void 키가_없으면_부르지_않음() {
    KakaoSubwayClient noKey = new KakaoSubwayClient(restTemplate, "");
    assertThat(noKey.isConfigured()).isFalse();
    assertThat(noKey.findAround(37.28188, 127.01439)).isEmpty();
    server.verify();
  }
}
