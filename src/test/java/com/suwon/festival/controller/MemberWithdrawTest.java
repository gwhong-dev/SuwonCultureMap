package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import jakarta.persistence.EntityManager;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.LoginMethod;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.LoginMethodRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.service.KakaoAuthService;

// DELETE /api/members/me: 회원 탈퇴 시 그 회원의 개인정보를 모두 지움
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberWithdrawTest {

  @Autowired private WebApplicationContext context;
  @Autowired private MemberRepository memberRepository;
  @Autowired private LoginMethodRepository loginMethodRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private EntityManager em;
  @MockitoBean private KakaoAuthService kakaoAuthService; // 실제 카카오 호출 방지

  private MockMvc mockMvc;
  private Member member;
  private Member other;
  private Event event;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    member = memberRepository.saveAndFlush(Member.create());
    other = memberRepository.saveAndFlush(Member.create());
    event = eventRepository.saveAndFlush(Event.builder()
        .idx(73001L).title("탈퇴시험 행사").category("공연").latitude(37.4).longitude(126.6)
        .pubDate(LocalDateTime.of(2034, 1, 1, 0, 0))
        .startDate(LocalDate.of(2034, 2, 2)).endDate(LocalDate.of(2034, 2, 2)).build());
    saveLogin("kakao", "탈퇴시험-1", member);
    saveLogin("google", "탈퇴시험-2", member);
    saveLogin("kakao", "탈퇴시험-3", other);
    saveFavorite(member);
    saveFavorite(other);
    em.flush();
    em.clear();
  }

  @Test
  void 탈퇴하면_회원_로그인수단_찜을_모두_지우고_세션을_끝냄() throws Exception {
    MockHttpSession session = sessionOf(member);

    MockHttpServletResponse res = mockMvc.perform(delete("/api/members/me").session(session))
        .andReturn().getResponse();
    em.flush();
    em.clear();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(memberRepository.findById(member.getId())).isEmpty();
    assertThat(loginMethodRepository.findByMemberId(member.getId())).isEmpty();
    assertThat(favoriteRepository.findByMemberId(member.getId())).isEmpty();
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void 다른_회원과_행사는_그대로() throws Exception {
    mockMvc.perform(delete("/api/members/me").session(sessionOf(member))).andReturn();
    em.flush();
    em.clear();

    assertThat(memberRepository.findById(other.getId())).isPresent();
    assertThat(loginMethodRepository.findByMemberId(other.getId())).hasSize(1);
    assertThat(favoriteRepository.findByMemberId(other.getId())).hasSize(1);
    assertThat(eventRepository.findById(event.getId())).isPresent();
  }

  @Test
  void 로그인하지_않으면_거부하고_아무것도_안_지움() throws Exception {
    long members = memberRepository.count();

    MockHttpServletResponse res = mockMvc.perform(delete("/api/members/me")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(400);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains("로그인이 필요");
    assertThat(memberRepository.count()).isEqualTo(members);
  }

  @Test
  void 다른_브라우저에_남은_세션은_탈퇴_뒤_로그아웃_상태로_보임() throws Exception {
    MockHttpSession otherBrowser = sessionOf(member);
    mockMvc.perform(delete("/api/members/me").session(sessionOf(member))).andReturn();
    em.flush();
    em.clear();

    MockHttpServletResponse res = mockMvc.perform(get("/api/auth/me").session(otherBrowser))
        .andReturn().getResponse();

    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains("\"loggedIn\":false");
    assertThat(otherBrowser.isInvalid()).isTrue();
  }

  @Test
  void 남은_세션으로_다시_탈퇴하면_세션만_끝내고_성공() throws Exception {
    MockHttpSession otherBrowser = sessionOf(member);
    mockMvc.perform(delete("/api/members/me").session(sessionOf(member))).andReturn();
    em.flush();
    em.clear();

    MockHttpServletResponse res = mockMvc.perform(delete("/api/members/me").session(otherBrowser))
        .andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(otherBrowser.isInvalid()).isTrue();
  }

  private static MockHttpSession sessionOf(Member m) {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("memberId", m.getId());
    return session;
  }

  private void saveLogin(String provider, String providerId, Member m) {
    loginMethodRepository.saveAndFlush(LoginMethod.builder()
        .provider(provider).providerId(providerId).member(m).build());
  }

  private void saveFavorite(Member m) {
    favoriteRepository.saveAndFlush(Favorite.builder()
        .member(m).event(event).createdAt(LocalDateTime.of(2034, 1, 1, 0, 0)).build());
  }
}
