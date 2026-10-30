package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.Member;
import com.suwon.festival.service.AuthService;

// 콜백의 state 검증: 맞으면 로그인 진행, 틀리거나 없거나 재사용이면 차단 (외부 API 는 AuthService 를 가짜로 바꿔 호출 안 함)
@SpringBootTest
@ActiveProfiles("test")
class AuthCallbackStateTest {

  @Autowired
  private WebApplicationContext context;

  @MockitoBean
  private AuthService authService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    Member member = mock(Member.class);
    when(member.getId()).thenReturn(1L);
    when(authService.kakaoLogin(anyString(), any())).thenReturn(member);
    when(authService.googleLogin(anyString(), any())).thenReturn(member);
    when(authService.naverLogin(anyString(), anyString(), any())).thenReturn(member);
  }

  private MockHttpServletResponse callback(String provider, MockHttpSession session, String code, String state)
      throws Exception {
    var req = get("/api/auth/" + provider + "/callback").session(session);
    if (code != null) req = req.param("code", code);
    if (state != null) req = req.param("state", state);
    return mockMvc.perform(req).andReturn().getResponse();
  }

  private MockHttpSession sessionWithState(String provider, String state) {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("oauthState:" + provider, state);
    return session;
  }

  @Test
  void validState_logsIn() throws Exception {
    for (String provider : new String[] {"kakao", "google", "naver"}) {
      MockHttpSession session = sessionWithState(provider, "good-state");
      MockHttpServletResponse res = callback(provider, session, "auth-code", "good-state");

      assertThat(res.getStatus()).isEqualTo(302);
      assertThat(res.getHeader("Location")).as(provider).isEqualTo("/");
      assertThat(session.getAttribute("memberId")).as(provider).isEqualTo(1L);
    }
    verify(authService).naverLogin(eq("auth-code"), eq("good-state"), any());
  }

  @Test
  void wrongState_isRejected() throws Exception {
    for (String provider : new String[] {"kakao", "google", "naver"}) {
      MockHttpSession session = sessionWithState(provider, "good-state");
      MockHttpServletResponse res = callback(provider, session, "auth-code", "attacker-state");

      assertThat(res.getHeader("Location")).as(provider).isEqualTo("/login.html?error=state");
      assertThat(session.getAttribute("memberId")).as(provider).isNull();
    }
    verifyNoLogin();
  }

  @Test
  void missingState_isRejected() throws Exception {
    // 세션에 state 가 없는 경우 (로그인 시작 없이 콜백만 강제로 연 경우)
    MockHttpServletResponse res = callback("kakao", new MockHttpSession(), "auth-code", "any");
    assertThat(res.getHeader("Location")).isEqualTo("/login.html?error=state");

    // 콜백에 state 가 없는 경우
    res = callback("google", sessionWithState("google", "good-state"), "auth-code", null);
    assertThat(res.getHeader("Location")).isEqualTo("/login.html?error=state");
    verifyNoLogin();
  }

  @Test
  void state_canBeUsedOnlyOnce() throws Exception {
    MockHttpSession session = sessionWithState("kakao", "good-state");
    callback("kakao", session, "auth-code", "good-state");
    session.removeAttribute("memberId");

    MockHttpServletResponse res = callback("kakao", session, "auth-code-2", "good-state");
    assertThat(res.getHeader("Location")).isEqualTo("/login.html?error=state");
  }

  @Test
  void cancelledConsent_withoutCode_goesBackToLogin() throws Exception {
    MockHttpServletResponse res = callback("naver", sessionWithState("naver", "good-state"), null, "good-state");
    assertThat(res.getHeader("Location")).isEqualTo("/login.html?error=cancelled");
    verifyNoLogin();
  }

  private void verifyNoLogin() {
    verify(authService, never()).kakaoLogin(anyString(), any());
    verify(authService, never()).googleLogin(anyString(), any());
    verify(authService, never()).naverLogin(anyString(), anyString(), any());
  }
}
