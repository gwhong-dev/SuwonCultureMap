package com.suwon.festival.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 화면 · API · 없는 주소 모두 보안 헤더가 붙는지, HSTS 는 운영 설정에서만 붙는지
@SpringBootTest
@ActiveProfiles("test")
class SecurityHeadersFilterTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private SecurityHeadersFilter filter;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // MockMvc 는 서블릿 필터를 자동으로 끼우지 않으므로 등록된 빈을 그대로 넣음
    mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
  }

  @Test
  void 화면_API_없는주소_모두_보안헤더가_붙음() throws Exception {
    for (String path : new String[] {"/", "/api/events", "/actuator/health", "/no-such-page"}) {
      MockHttpServletResponse res = mockMvc.perform(get(path)).andReturn().getResponse();

      assertThat(res.getHeader("X-Content-Type-Options")).as(path).isEqualTo("nosniff");
      assertThat(res.getHeader("X-Frame-Options")).as(path).isEqualTo("DENY");
      assertThat(res.getHeader("Content-Security-Policy")).as(path).isEqualTo(SecurityHeadersFilter.CSP);
      assertThat(res.getHeader("Referrer-Policy")).as(path).isEqualTo("strict-origin-when-cross-origin");
      assertThat(res.getHeader("Permissions-Policy")).as(path).contains("camera=()");
    }
  }

  @Test
  void CSP는_카카오지도_스크립트를_막지_않음() {
    // script-src · default-src 를 넣으면 카카오 SDK · 인라인 스크립트가 막혀 지도가 안 뜸
    assertThat(SecurityHeadersFilter.CSP).doesNotContain("script-src").doesNotContain("default-src");
  }

  @Test
  void CSP_form_action은_카카오톡_공유_전송만_예외로_허용() {
    // 카카오톡 공유(JS SDK)는 폰 밖에서 sharer.kakao.com 으로 폼 전송 → 그 도메인만 허용, 나머지는 'self' 유지
    assertThat(SecurityHeadersFilter.CSP).contains("form-action 'self' https://sharer.kakao.com");
  }

  @Test
  void 테스트_로컬에서는_HSTS를_붙이지_않음() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get("/")).andReturn().getResponse();

    assertThat(res.getHeader("Strict-Transport-Security")).isNull();
  }

  @Test
  void 운영설정이면_HSTS를_붙임() throws Exception {
    SecurityHeadersFilter prodFilter = new SecurityHeadersFilter(true);
    MockHttpServletResponse res = new MockHttpServletResponse();

    prodFilter.doFilter(new MockHttpServletRequest("GET", "/"), res, new MockFilterChain());

    assertThat(res.getHeader("Strict-Transport-Security")).isEqualTo(SecurityHeadersFilter.HSTS);
  }
}
