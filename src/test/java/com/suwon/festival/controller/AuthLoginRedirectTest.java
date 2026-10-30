package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// /api/auth/{제공자}/login 이 "app.base-url 기준 콜백 주소"로 인증 페이지에 보내는지 확인
//  (PR #8 에서 HTML 하드코딩을 서버 생성 방식으로 바꾼 부분의 회귀 방지)
@SpringBootTest
@ActiveProfiles("test")
class AuthLoginRedirectTest {

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  private String redirectLocation(String path) throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get(path)).andReturn().getResponse();
    assertThat(res.getStatus()).isEqualTo(302);
    // 인코딩 방식과 상관없이 비교하려고 디코딩
    return URLDecoder.decode(res.getHeader("Location"), StandardCharsets.UTF_8);
  }

  @Test
  void kakaoLogin_redirectsWithBaseUrlCallback() throws Exception {
    String location = redirectLocation("/api/auth/kakao/login");
    assertThat(location)
        .startsWith("https://kauth.kakao.com/oauth/authorize")
        .contains("client_id=test-kakao-id")
        .contains("redirect_uri=http://localhost:8080/api/auth/kakao/callback")
        .contains("response_type=code");
  }

  @Test
  void googleLogin_redirectsWithBaseUrlCallback() throws Exception {
    String location = redirectLocation("/api/auth/google/login");
    assertThat(location)
        .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
        .contains("client_id=test-google-id")
        .contains("redirect_uri=http://localhost:8080/api/auth/google/callback")
        .contains("scope=email profile");
  }

  @Test
  void naverLogin_redirectsWithBaseUrlCallback() throws Exception {
    String location = redirectLocation("/api/auth/naver/login");
    assertThat(location)
        .startsWith("https://nid.naver.com/oauth2.0/authorize")
        .contains("client_id=test-naver-id")
        .contains("redirect_uri=http://localhost:8080/api/auth/naver/callback");
  }
}
