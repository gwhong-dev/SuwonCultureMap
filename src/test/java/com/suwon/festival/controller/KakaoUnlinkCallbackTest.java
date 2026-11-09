package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
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

// 카카오 연결 끊기 알림(/api/auth/kakao/unlink-callback): 어드민 키 확인 → 카카오 로그인 삭제, 카카오만 쓰던 회원은 탈퇴와 같이 삭제
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "kakao.admin-key=test-admin-key")
@Transactional
class KakaoUnlinkCallbackTest {

  private static final String URL = "/api/auth/kakao/unlink-callback";
  private static final String AUTH = "KakaoAK test-admin-key";

  @Autowired private WebApplicationContext context;
  @Autowired private MemberRepository memberRepository;
  @Autowired private LoginMethodRepository loginMethodRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private EntityManager em;
  @MockitoBean private KakaoAuthService kakaoAuthService; // 실제 카카오 호출 방지 + 해제 API 를 안 부르는지 확인

  private MockMvc mockMvc;
  private Member kakaoOnly;
  private Member kakaoAndGoogle;
  private Member other;
  private Event event;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    kakaoOnly = memberRepository.saveAndFlush(Member.create());
    kakaoAndGoogle = memberRepository.saveAndFlush(Member.create());
    other = memberRepository.saveAndFlush(Member.create());
    event = eventRepository.saveAndFlush(Event.builder()
        .idx(74001L).title("연결끊기시험 행사").category("공연").latitude(37.4).longitude(126.6)
        .pubDate(LocalDateTime.of(2034, 1, 1, 0, 0))
        .startDate(LocalDate.of(2034, 2, 2)).endDate(LocalDate.of(2034, 2, 2)).build());
    saveLogin("kakao", "1001", kakaoOnly);
    saveLogin("kakao", "1002", kakaoAndGoogle);
    saveLogin("google", "g-1002", kakaoAndGoogle);
    saveLogin("kakao", "1003", other);
    saveFavorite(kakaoOnly);
    saveFavorite(kakaoAndGoogle);
    saveFavorite(other);
    em.flush();
    em.clear();
  }

  @Test
  void 카카오만_쓰던_회원은_탈퇴와_같이_모두_지움_POST() throws Exception {
    MockHttpServletResponse res = postCallback(AUTH, "1001");
    em.flush();
    em.clear();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(memberRepository.findById(kakaoOnly.getId())).isEmpty();
    assertThat(loginMethodRepository.findByMemberId(kakaoOnly.getId())).isEmpty();
    assertThat(favoriteRepository.findByMemberId(kakaoOnly.getId())).isEmpty();
    verify(kakaoAuthService, never()).unlink(any()); // 이미 끊겼으니 해제 API 다시 안 부름
  }

  @Test
  void GET_쿼리_방식도_처리() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get(URL).header("Authorization", AUTH)
        .param("app_id", "123").param("user_id", "1001").param("referrer_type", "UNLINK_FROM_APPS"))
        .andReturn().getResponse();
    em.flush();
    em.clear();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(memberRepository.findById(kakaoOnly.getId())).isEmpty();
  }

  @Test
  void 다른_로그인이_남은_회원은_카카오_로그인만_지우고_회원_찜은_그대로() throws Exception {
    MockHttpServletResponse res = postCallback(AUTH, "1002");
    em.flush();
    em.clear();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(memberRepository.findById(kakaoAndGoogle.getId())).isPresent();
    assertThat(loginMethodRepository.findByMemberId(kakaoAndGoogle.getId()))
        .extracting(LoginMethod::getProvider).containsExactly("google");
    assertThat(favoriteRepository.findByMemberId(kakaoAndGoogle.getId())).hasSize(1);
  }

  @Test
  void 다른_회원과_행사는_그대로() throws Exception {
    postCallback(AUTH, "1001");
    em.flush();
    em.clear();

    assertThat(memberRepository.findById(other.getId())).isPresent();
    assertThat(loginMethodRepository.findByMemberId(other.getId())).hasSize(1);
    assertThat(favoriteRepository.findByMemberId(other.getId())).hasSize(1);
    assertThat(eventRepository.findById(event.getId())).isPresent();
  }

  @Test
  void 모르는_카카오_회원이면_아무것도_안_지우고_200() throws Exception {
    long members = memberRepository.count();
    long logins = loginMethodRepository.count();

    MockHttpServletResponse res = postCallback(AUTH, "9999");

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(memberRepository.count()).isEqualTo(members);
    assertThat(loginMethodRepository.count()).isEqualTo(logins);
  }

  @Test
  void 같은_알림이_두_번_와도_200() throws Exception {
    postCallback(AUTH, "1001");
    em.flush();
    em.clear();

    assertThat(postCallback(AUTH, "1001").getStatus()).isEqualTo(200);
  }

  @Test
  void 인증_헤더가_없거나_틀리면_401_이고_아무것도_안_지움() throws Exception {
    long members = memberRepository.count();
    long logins = loginMethodRepository.count();

    assertThat(postCallback(null, "1001").getStatus()).isEqualTo(401);
    assertThat(postCallback("KakaoAK wrong-key", "1001").getStatus()).isEqualTo(401);
    assertThat(postCallback("test-admin-key", "1001").getStatus()).isEqualTo(401); // 접두어 없음
    assertThat(postCallback("Bearer test-admin-key", "1001").getStatus()).isEqualTo(401);
    em.flush();
    em.clear();

    assertThat(memberRepository.count()).isEqualTo(members);
    assertThat(loginMethodRepository.count()).isEqualTo(logins);
  }

  @Test
  void 카카오_회원번호가_없으면_아무것도_안_지우고_200() throws Exception {
    long members = memberRepository.count();

    MockHttpServletResponse res = mockMvc.perform(post(URL).header("Authorization", AUTH)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("app_id", "123"))
        .andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(memberRepository.count()).isEqualTo(members);
  }

  private MockHttpServletResponse postCallback(String authorization, String userId) throws Exception {
    var req = post(URL).contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("app_id", "123").param("user_id", userId).param("referrer_type", "UNLINK_FROM_APPS");
    if (authorization != null) req = req.header("Authorization", authorization);
    return mockMvc.perform(req).andReturn().getResponse();
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
