package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

// 카카오 연결 해제 호출 모양 — 가짜 서버로 확인, 실제 호출 없음
class KakaoAuthServiceUnlinkTest {

  private MockRestServiceServer server;
  private KakaoAuthService service;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    service = new KakaoAuthService(restTemplate);
    ReflectionTestUtils.setField(service, "adminKey", "test-admin-key");
  }

  @Test
  void 어드민_키와_회원번호로_연결_해제를_요청() {
    server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "KakaoAK test-admin-key"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(content().string("target_id_type=user_id&target_id=123456789"))
        .andRespond(withSuccess("{\"id\":123456789}", MediaType.APPLICATION_JSON));

    assertThat(service.unlink("123456789")).isTrue();
    server.verify();
  }

  @Test
  void 카카오가_오류를_주면_예외_없이_실패로_알림() {
    server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThat(service.unlink("123456789")).isFalse();
    server.verify();
  }

  @Test
  void 어드민_키가_없으면_호출하지_않고_실패로_알림() {
    ReflectionTestUtils.setField(service, "adminKey", "");

    assertThat(service.unlink("123456789")).isFalse();
    server.verify();
  }
}
