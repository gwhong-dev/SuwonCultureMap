package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.dto.google.GoogleUserResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.LoginMethod;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.LoginMethodRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.service.AuthService;
import com.suwon.festival.service.GoogleAuthService;
import com.suwon.festival.service.KakaoAuthService;

// 회원 탈퇴의 실제 커밋 경로. 서비스 트랜잭션이 빠지면 여기서 실패하도록 @Transactional 을 걸지 않고 직접 지움
@SpringBootTest
@ActiveProfiles("test")
class MemberWithdrawCommitTest {

  @Autowired private WebApplicationContext context;
  @Autowired private MemberRepository memberRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private AuthService authService;
  @MockitoSpyBean private LoginMethodRepository loginMethodRepository;
  @MockitoBean private KakaoAuthService kakaoAuthService;
  @MockitoBean private GoogleAuthService googleAuthService;

  private MockMvc mockMvc;
  private Member member;
  private Event event;
  private final List<Long> createdMemberIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    member = memberRepository.save(Member.create());
    createdMemberIds.add(member.getId());
    event = eventRepository.save(Event.builder()
        .idx(73101L).title("탈퇴커밋 행사").category("공연").latitude(37.4).longitude(126.5)
        .pubDate(LocalDateTime.of(2034, 1, 1, 0, 0))
        .startDate(LocalDate.of(2034, 2, 2)).endDate(LocalDate.of(2034, 2, 2)).build());
    loginMethodRepository.save(login("kakao", "730001", member));
    loginMethodRepository.save(login("google", "탈퇴커밋-구글", member));
    favoriteRepository.save(Favorite.builder()
        .member(member).event(event).createdAt(LocalDateTime.of(2034, 1, 1, 0, 0)).build());
  }

  @AfterEach
  void cleanUp() {
    favoriteRepository.findAll().stream()
        .filter(f -> f.getEvent().getId().equals(event.getId()))
        .forEach(favoriteRepository::delete);
    loginMethodRepository.findAll().stream()
        .filter(l -> l.getProviderId().equals("730001") || l.getProviderId().startsWith("탈퇴커밋"))
        .forEach(loginMethodRepository::delete);
    createdMemberIds.forEach(id -> { if (memberRepository.existsById(id)) memberRepository.deleteById(id); });
    eventRepository.delete(event);
  }

  @Test
  void 실제로_커밋되고_카카오_연결_해제는_카카오_회원번호로만_호출() throws Exception {
    when(kakaoAuthService.unlink("730001")).thenReturn(true);
    MockHttpSession session = sessionOf(member);

    MockHttpServletResponse res = mockMvc.perform(delete("/api/members/me").session(session))
        .andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(memberRepository.findById(member.getId())).isEmpty();
    assertThat(loginMethodRepository.findByMemberId(member.getId())).isEmpty();
    assertThat(favoriteRepository.findByMemberId(member.getId())).isEmpty();
    verify(kakaoAuthService).unlink("730001");
    verify(kakaoAuthService, never()).unlink("탈퇴커밋-구글");
  }

  @Test
  void 카카오_해제에_실패해도_탈퇴는_끝나고_직접_해제를_안내() throws Exception {
    when(kakaoAuthService.unlink("730001")).thenReturn(false);

    MockHttpServletResponse res = mockMvc.perform(delete("/api/members/me").session(sessionOf(member)))
        .andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(memberRepository.findById(member.getId())).isEmpty();
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains("직접 해제");
  }

  @Test
  void 삭제가_실패하면_되돌리고_세션을_유지하고_카카오는_안_끊음() {
    doThrow(new IllegalStateException("가상 삭제 실패")).when(loginMethodRepository).deleteByMemberId(anyLong());
    MockHttpSession session = sessionOf(member);

    assertThatThrownBy(() -> mockMvc.perform(delete("/api/members/me").session(session)))
        .hasRootCauseMessage("가상 삭제 실패");

    assertThat(memberRepository.findById(member.getId())).isPresent();
    assertThat(favoriteRepository.findByMemberId(member.getId())).hasSize(1); // 찜 삭제도 되돌려짐
    assertThat(session.isInvalid()).isFalse();
    verify(kakaoAuthService, never()).unlink(anyString());
  }

  @Test
  void 탈퇴한_회원의_세션이_남아_있어도_소셜_로그인이_새_회원으로_진행() {
    Long deletedId = memberRepository.save(Member.create()).getId();
    memberRepository.deleteById(deletedId);
    GoogleUserResponse googleUser = new GoogleUserResponse();
    googleUser.id = "탈퇴커밋-새구글";
    when(googleAuthService.getAccessToken("code")).thenReturn("token");
    when(googleAuthService.getUserInfo("token")).thenReturn(googleUser);
    Member created = authService.googleLogin("code", deletedId);

    assertThat(created.getId()).isNotEqualTo(deletedId);
    assertThat(memberRepository.findById(created.getId())).isPresent();
    createdMemberIds.add(created.getId());
  }

  private static MockHttpSession sessionOf(Member m) {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("memberId", m.getId());
    return session;
  }

  private static LoginMethod login(String provider, String providerId, Member m) {
    return LoginMethod.builder().provider(provider).providerId(providerId).member(m).build();
  }
}
