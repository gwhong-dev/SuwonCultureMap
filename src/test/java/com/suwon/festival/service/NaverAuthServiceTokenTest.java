package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

// 네이버 토큰 요청 주소 모양 — 가짜 서버로 확인, 실제 호출 없음
class NaverAuthServiceTokenTest {

  private MockRestServiceServer server;
  private NaverAuthService service;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    service = new NaverAuthService(restTemplate);
    ReflectionTestUtils.setField(service, "clientId", "test-id");
    ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
  }

  @Test
  void code_와_state_의_특수문자를_인코딩해서_보냄() {
    // + 가 그대로 가면 네이버가 공백으로 읽고, & 가 그대로 가면 값이 잘림
    server.expect(requestTo(URI.create("https://nid.naver.com/oauth2.0/token"
            + "?grant_type=authorization_code&client_id=test-id&client_secret=test-secret"
            + "&code=a%2Bb%26c%3Dd&state=s%2F1%3F")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"access_token\":\"tok\"}", MediaType.APPLICATION_JSON));

    assertThat(service.getAccessToken("a+b&c=d", "s/1?")).isEqualTo("tok");
    server.verify();
  }
}
