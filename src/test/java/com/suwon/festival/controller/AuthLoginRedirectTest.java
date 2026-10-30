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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// /api/auth/{제공자}/login 이 "app.base-url 기준 콜백 주소 + 무작위 state"로 인증 페이지에 보내는지 확인
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

  private String redirectLocation(String path, MockHttpSession session) throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get(path).session(session)).andReturn().getResponse();
    assertThat(res.getStatus()).isEqualTo(302);
    // 인코딩 방식과 상관없이 비교하려고 디코딩
    return URLDecoder.decode(res.getHeader("Location"), StandardCharsets.UTF_8);
  }

  private String stateParam(String location) {
    int i = location.indexOf("state=");
    assertThat(i).as("state 파라미터가 있어야 함").isGreaterThanOrEqualTo(0);
    String rest = location.substring(i + "state=".length());
    int amp = rest.indexOf('&');
    return amp == -1 ? rest : rest.substring(0, amp);
  }

  @Test
  void kakaoLogin_redirectsWithBaseUrlCallback() throws Exception {
    String location = redirectLocation("/api/auth/kakao/login", new MockHttpSession());
    assertThat(location)
        .startsWith("https://kauth.kakao.com/oauth/authorize")
        .contains("client_id=test-kakao-id")
        .contains("redirect_uri=http://localhost:8080/api/auth/kakao/callback")
        .contains("response_type=code");
  }

  @Test
  void googleLogin_redirectsWithBaseUrlCallback() throws Exception {
    String location = redirectLocation("/api/auth/google/login", new MockHttpSession());
    assertThat(location)
        .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
        .contains("client_id=test-google-id")
        .contains("redirect_uri=http://localhost:8080/api/auth/google/callback")
        .contains("scope=email profile");
  }

  @Test
  void naverLogin_redirectsWithBaseUrlCallback() throws Exception {
    String location = redirectLocation("/api/auth/naver/login", new MockHttpSession());
    assertThat(location)
        .startsWith("https://nid.naver.com/oauth2.0/authorize")
        .contains("client_id=test-naver-id")
        .contains("redirect_uri=http://localhost:8080/api/auth/naver/callback")
        .doesNotContain("state=test123");
  }

  @Test
  void login_issuesRandomStateAndStoresItInSession() throws Exception {
    for (String provider : new String[] {"kakao", "google", "naver"}) {
      MockHttpSession s1 = new MockHttpSession();
      MockHttpSession s2 = new MockHttpSession();
      String state1 = stateParam(redirectLocation("/api/auth/" + provider + "/login", s1));
      String state2 = stateParam(redirectLocation("/api/auth/" + provider + "/login", s2));

      assertThat(state1).as(provider + " state 길이").hasSizeGreaterThanOrEqualTo(32);
      assertThat(state1).as(provider + " 요청마다 다른 state").isNotEqualTo(state2);
      assertThat(s1.getAttribute("oauthState:" + provider)).as(provider + " 세션 저장").isEqualTo(state1);
    }
  }
}
