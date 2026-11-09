package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.dto.google.GoogleUserResponse;
import com.suwon.festival.dto.kakao.KakaoUserResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.LoginMethod;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.LoginMethodRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.service.EventSyncService;
import com.suwon.festival.service.GoogleAuthService;
import com.suwon.festival.service.KakaoAuthService;
import com.suwon.festival.service.instagram.InstagramClient;
import com.suwon.festival.service.instagram.InstagramPost;
import com.suwon.festival.service.place.PlaceCandidate;
import com.suwon.festival.service.place.PlaceSearchClient;
import com.suwon.festival.service.place.RegionLookupClient;

// spring.jpa.open-in-view=false 에서 모든 API 를 한 번씩 불러 지연 로딩 오류(LazyInitializationException)가 없는지 확인
// ⚠️ @Transactional 을 걸지 않음: 시험 트랜잭션이 요청 내내 DB 세션을 열어 두면 open-in-view 를 켠 것과 같아져 오류를 못 잡음
//    → 저장한 행은 @AfterEach 에서 직접 지움 (Flyway 스크립트에 기본 데이터가 없어 전부 지워도 됨)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "kakao.admin-key=test-admin-key",
    "instagram.sync.enabled=true",
    "instagram.accounts=suwon_city" })
class OpenInViewOffApiTest {

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MemberRepository memberRepository;
  @Autowired private LoginMethodRepository loginMethodRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @MockitoBean private KakaoAuthService kakaoAuthService;
  @MockitoBean private GoogleAuthService googleAuthService;
  @MockitoBean private InstagramClient instagramClient;
  @MockitoBean private PlaceSearchClient placeSearchClient;
  @MockitoBean private RegionLookupClient regionLookupClient;
  @MockitoBean private EventSyncService eventSyncService; // 진행 날짜 미리보기의 재단 XML (외부 호출 막음)

  private MockMvc mvc;
  private Event representative;
  private Event hidden;
  private Member member;

  @BeforeEach
  void setUp() {
    cleanUp();
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
    when(regionLookupClient.lookup(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble()))
        .thenReturn(RegionLookupClient.Region.NONE);
    LocalDate day = LocalDate.now().plusDays(10);
    representative = eventRepository.save(event(990901L, "지연로딩 음악회", day));
    hidden = event(990902L, "지연로딩 음악회 (재게시)", day);
    hidden.mergeInto(representative.getId());
    hidden = eventRepository.save(hidden);
    member = memberRepository.save(Member.create());
    loginMethodRepository.save(LoginMethod.builder().provider("kakao").providerId("990001").member(member).build());
    // 숨긴 행을 찜한 기록 → 목록은 대표로 바꿔 보여줌 (Favorite → Event 지연 로딩 + 대표 찾기)
    favoriteRepository.save(Favorite.builder().member(member).event(hidden).createdAt(LocalDateTime.now()).build());
  }

  // 표를 통째로 비움 → 다른 시험이 남긴 행도 지움 (같은 H2 testdb 공유, 병렬 실행 없음 · 클래스를 넘어 데이터를 기대는 시험 없음)
  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM favorite");
    jdbc.update("DELETE FROM login_method");
    jdbc.update("DELETE FROM member");
    jdbc.update("DELETE FROM instagram_block");
    jdbc.update("DELETE FROM instagram_media");
    jdbc.update("DELETE FROM event_date");
    jdbc.update("UPDATE event SET merged_into_id = NULL");
    jdbc.update("DELETE FROM event");
  }

  @Test
  void open_in_view_가_꺼져_있음() {
    assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
  }

  @Test
  void 시험_자체가_트랜잭션_밖임_지연_로딩이_실제로_막힘() {
    // 이 시험이 의미 있으려면 트랜잭션 밖에서 연관을 읽으면 실패해야 함 (안 그러면 아래 API 시험이 오류를 못 잡음)
    Favorite favorite = favoriteRepository.findByMemberId(member.getId()).get(0);
    assertThatThrownBy(() -> favorite.getEvent().getTitle()).isInstanceOf(LazyInitializationException.class);
  }

  @Test
  void 행사_목록_캘린더_합치기_미리보기() throws Exception {
    mvc.perform(get("/api/events")).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + representative.getId() + ")]").exists());
    mvc.perform(get("/api/events/" + representative.getId() + "/calendar.ics"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/events/" + representative.getId() + "/calendar/google"))
        .andExpect(status().isFound());
    mvc.perform(get("/api/events/merge/preview")).andExpect(status().isOk());
    when(eventSyncService.fetchRawXml()).thenReturn("<rss><channel></channel></rss>");
    mvc.perform(get("/api/events/schedule/preview")).andExpect(status().isOk());
  }

  @Test
  void 찜_목록_추가_삭제_합치기() throws Exception {
    MockHttpSession session = loggedIn();
    mvc.perform(get("/api/favorites").session(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(representative.getId()));

    Long other = eventRepository.save(event(990903L, "지연로딩 전시", LocalDate.now().plusDays(3))).getId();
    mvc.perform(post("/api/favorites/" + other).session(session)).andExpect(status().isOk());
    // 숨긴 행 번호로 다시 찜 → 대표 기준으로 이미 찜 (기존 찜의 Event 를 읽음)
    mvc.perform(post("/api/favorites/" + hidden.getId()).session(session)).andExpect(status().isBadRequest());
    mvc.perform(delete("/api/favorites/" + other).session(session)).andExpect(status().isOk());
    mvc.perform(post("/api/favorites/merge").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventIds\":[" + other + "," + hidden.getId() + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.added").value(1));
    mvc.perform(delete("/api/favorites/" + representative.getId()).session(session)).andExpect(status().isOk());
  }

  @Test
  void 회원_정보_로그아웃_탈퇴() throws Exception {
    MockHttpSession session = loggedIn();
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.linkedProviders[0]").value("kakao"));
    mvc.perform(post("/api/auth/logout").session(loggedIn())).andExpect(status().isOk());
    mvc.perform(delete("/api/members/me").session(session)).andExpect(status().isOk());
    assertThat(memberRepository.findById(member.getId())).isEmpty();
  }

  @Test
  void 소셜_로그인_콜백_기존_회원_LoginMethod_에서_Member() throws Exception {
    KakaoUserResponse kakaoUser = new KakaoUserResponse();
    kakaoUser.id = 990001L;
    when(kakaoAuthService.getAccessToken("code")).thenReturn("token");
    when(kakaoAuthService.getUserInfo("token")).thenReturn(kakaoUser);
    MockHttpSession session = withState("kakao");

    mvc.perform(get("/api/auth/kakao/callback").session(session).param("code", "code").param("state", "s"))
        .andExpect(status().isFound());

    assertThat(session.getAttribute("memberId")).isEqualTo(member.getId());
  }

  @Test
  void 소셜_로그인_콜백_새_회원() throws Exception {
    GoogleUserResponse googleUser = new GoogleUserResponse();
    googleUser.id = "g-990002";
    when(googleAuthService.getAccessToken("code")).thenReturn("token");
    when(googleAuthService.getUserInfo("token")).thenReturn(googleUser);
    MockHttpSession session = withState("google");

    mvc.perform(get("/api/auth/google/callback").session(session).param("code", "code").param("state", "s"))
        .andExpect(status().isFound());

    assertThat(session.getAttribute("memberId")).isNotNull().isNotEqualTo(member.getId());
  }

  @Test
  void 카카오_연결_끊기_콜백() throws Exception {
    mvc.perform(post("/api/auth/kakao/unlink-callback").header("Authorization", "KakaoAK test-admin-key")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("app_id", "123").param("user_id", "990001").param("referrer_type", "UNLINK_FROM_APPS"))
        .andExpect(status().isOk());
    assertThat(memberRepository.findById(member.getId())).isEmpty();
  }

  @Test
  void 인스타_동기화_두_번_블록의_행사_찜_옮기기() throws Exception {
    LocalDate day = LocalDate.now().plusDays(12);
    when(instagramClient.isConfigured()).thenReturn(true);
    when(placeSearchClient.search(anyString())).thenReturn(new PlaceSearchClient.Page(
        List.of(new PlaceCandidate("가상 아트홀", "경기 수원시 팔달구 가상동 1", null, 37.28, 127.01)), true));
    when(instagramClient.fetchRecent(anyString(), anyInt())).thenReturn(List.of(instaPost(caption("가을 음악회", day))));

    mvc.perform(post("/api/instagram/sync")).andExpect(status().isOk());
    Event first = instagramEvent();
    favoriteRepository.save(Favorite.builder().member(member).event(first).createdAt(LocalDateTime.now()).build());

    // 같은 글의 제목이 바뀜 → 옛 블록의 행사(InstagramBlock → Event 지연 로딩)를 내리고 찜을 새 행사로 옮김
    when(instagramClient.fetchRecent(anyString(), anyInt())).thenReturn(List.of(instaPost(caption("가을 음악회 앙상블", day))));
    mvc.perform(post("/api/instagram/sync")).andExpect(status().isOk());

    Event second = instagramEvent();
    assertThat(second.getId()).isNotEqualTo(first.getId());
    assertThat(blockRepository.count()).isEqualTo(2);
    mvc.perform(get("/api/favorites").session(loggedIn())).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + second.getId() + ")]").exists());
  }

  // ===== 도움 =====

  private MockHttpSession loggedIn() {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("memberId", member.getId());
    return session;
  }

  private static MockHttpSession withState(String provider) {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("oauthState:" + provider, "s");
    return session;
  }

  private static Event event(Long idx, String title, LocalDate day) {
    return Event.builder()
        .idx(idx).title(title).category("공연").latitude(37.28).longitude(127.01)
        .info("장소: 가상 아트홀").place("가상 아트홀").pubDate(LocalDateTime.now().minusDays(1))
        .startDate(day).endDate(day).build();
  }

  private static String caption(String title, LocalDate day) {
    String weekday = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
    return String.join("\n", title,
        "🗓️ " + day.getYear() + ". " + day.getMonthValue() + ". " + day.getDayOfMonth() + ".(" + weekday + ") 19:00",
        "📍 가상 아트홀");
  }

  private static InstagramPost instaPost(String caption) {
    return new InstagramPost("1790000000099001", caption, "https://www.instagram.com/p/1790000000099001/",
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1), "IMAGE");
  }

  private Event instagramEvent() {
    return eventRepository.findAll().stream().filter(e -> Event.SOURCE_INSTAGRAM.equals(e.getSource()))
        .reduce((a, b) -> b).orElseThrow();
  }
}
