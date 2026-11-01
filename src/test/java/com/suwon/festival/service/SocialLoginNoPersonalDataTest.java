package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwon.festival.dto.google.GoogleUserResponse;
import com.suwon.festival.dto.kakao.KakaoUserResponse;
import com.suwon.festival.dto.naver.NaverAccount;
import com.suwon.festival.dto.naver.NaverUserResponse;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.LoginMethodRepository;
import com.suwon.festival.repository.MemberRepository;

// 로그인은 소셜에 맡기고 식별값 말고는 저장하지 않음 (V4). 칸과 응답 해석 · 계정 판단 분기를 고정
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SocialLoginNoPersonalDataTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private AuthService authService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private LoginMethodRepository loginMethodRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private WebApplicationContext context;
  @MockitoBean private KakaoAuthService kakaoAuthService;
  @MockitoBean private GoogleAuthService googleAuthService;
  @MockitoBean private NaverAuthService naverAuthService;

  @BeforeEach
  void setUp() {
    when(kakaoAuthService.getAccessToken("code")).thenReturn("token");
    when(googleAuthService.getAccessToken("code")).thenReturn("token");
    when(naverAuthService.getAccessToken("code", "state")).thenReturn("token");
  }

  @Test
  void 이메일_비밀번호_닉네임_칸이_없음() {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS"
            + " WHERE UPPER(TABLE_NAME) IN ('MEMBER', 'LOGIN_METHOD')"
            + " AND UPPER(COLUMN_NAME) IN ('EMAIL', 'PASSWORD', 'NICKNAME')",
        Integer.class);

    assertThat(count).isZero();
  }

  @Test
  void 소셜_응답에_이메일_이름이_있어도_식별값만_읽음() throws Exception {
    KakaoUserResponse kakao = JSON.readValue(
        "{\"id\":741,\"kakao_account\":{\"email\":\"a@example.com\",\"profile\":{\"nickname\":\"가상\"}}}",
        KakaoUserResponse.class);
    GoogleUserResponse google = JSON.readValue(
        "{\"id\":\"g-741\",\"email\":\"a@example.com\",\"name\":\"가상\"}", GoogleUserResponse.class);
    NaverUserResponse naver = JSON.readValue(
        "{\"resultcode\":\"00\",\"response\":{\"id\":\"n-741\",\"email\":\"a@example.com\",\"name\":\"가상\"}}",
        NaverUserResponse.class);

    assertThat(kakao.id).isEqualTo(741L);
    assertThat(google.id).isEqualTo("g-741");
    assertThat(naver.response.id).isEqualTo("n-741");
  }

  @Test
  void 세_제공자_모두_처음_로그인하면_새_회원과_식별값이_저장됨() {
    Member kakao = kakaoLogin(742L, null);
    Member google = googleLogin("소셜시험-구글", null);
    Member naver = naverLogin("소셜시험-네이버", null);

    assertThat(loginMethodRepository.findByProviderAndProviderId("kakao", "742").orElseThrow().getMember().getId())
        .isEqualTo(kakao.getId());
    assertThat(loginMethodRepository.findByProviderAndProviderId("google", "소셜시험-구글")).isPresent();
    assertThat(loginMethodRepository.findByProviderAndProviderId("naver", "소셜시험-네이버")).isPresent();
    assertThat(java.util.Set.of(kakao.getId(), google.getId(), naver.getId())).hasSize(3);
  }

  @Test
  void 같은_소셜_계정으로_다시_로그인하면_같은_회원() {
    Member first = kakaoLogin(743L, null);

    assertThat(kakaoLogin(743L, null).getId()).isEqualTo(first.getId());
  }

  @Test
  void 로그인_상태에서_처음_보는_소셜_계정은_지금_회원에_연동() {
    Member current = kakaoLogin(744L, null);

    assertThat(googleLogin("소셜시험-연동", current.getId()).getId()).isEqualTo(current.getId());
    assertThat(loginMethodRepository.findByMemberId(current.getId())).hasSize(2);
  }

  @Test
  void 다른_회원에_연동된_소셜_계정은_연동_거부() {
    Member other = kakaoLogin(745L, null);
    Member current = kakaoLogin(746L, null);
    assertThat(other.getId()).isNotEqualTo(current.getId());

    assertThatThrownBy(() -> kakaoLogin(745L, current.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 다른 계정");
  }

  @Test
  void 식별값이_없는_응답은_회원을_만들지_않고_거부() {
    long members = memberRepository.count();

    assertThatThrownBy(() -> kakaoLogin(null, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> googleLogin(null, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> googleLogin(" ", null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> naverLogin(null, null)).isInstanceOf(IllegalArgumentException.class);

    assertThat(memberRepository.count()).isEqualTo(members);
    assertThat(loginMethodRepository.findByProviderAndProviderId("kakao", "null")).isEmpty();
  }

  @Test
  void 공개_가입_API는_없음() throws Exception {
    int status = MockMvcBuilders.webAppContextSetup(context).build()
        .perform(MockMvcRequestBuilders.post("/api/members/signup")
            .contentType("application/json").content("{\"nickname\":\"가상\"}"))
        .andReturn().getResponse().getStatus();

    assertThat(status).isEqualTo(404);
  }

  private Member kakaoLogin(Long id, Long currentMemberId) {
    KakaoUserResponse user = new KakaoUserResponse();
    user.id = id;
    when(kakaoAuthService.getUserInfo("token")).thenReturn(user);
    return authService.kakaoLogin("code", currentMemberId);
  }

  private Member googleLogin(String id, Long currentMemberId) {
    GoogleUserResponse user = new GoogleUserResponse();
    user.id = id;
    when(googleAuthService.getUserInfo("token")).thenReturn(user);
    return authService.googleLogin("code", currentMemberId);
  }

  private Member naverLogin(String id, Long currentMemberId) {
    NaverAccount user = new NaverAccount();
    user.id = id;
    when(naverAuthService.getUserInfo("token")).thenReturn(user);
    return authService.naverLogin("code", "state", currentMemberId);
  }
}
