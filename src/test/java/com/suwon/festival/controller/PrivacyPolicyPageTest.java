package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 개인정보 처리방침이 공개되고 로그인 · 메인 · 마이페이지에서 찾을 수 있는지 (법 제30조 · 시행령 제31조)
// 낱말이 있는지만 봄: 내용이 실제 동작과 맞는지는 검증하지 않음
@SpringBootTest
@ActiveProfiles("test")
class PrivacyPolicyPageTest {

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void 로그인_없이_열리고_법정_항목을_담고_있음() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get("/privacy.html")).andReturn().getResponse();

    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains(
        "처리하는 개인정보 항목", "보유 기간", "파기", "제3자 제공", "위탁", "국외 이전",
        "권리", "법정대리인", "자동 수집 장치", "안전성 확보", "개인정보 보호책임자", "삭제 요청", "문의 메일");
  }

  @Test
  void 배포_전_입력_칸이_남아_있지_않음() throws Exception {
    String html = mockMvc.perform(get("/privacy.html")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(html).doesNotContain("배포 전 입력", "to-fill");
  }

  @Test
  void 행사_정보를_출처별로_적고_인스타는_공식_계정만으로_표기() throws Exception {
    String html = mockMvc.perform(get("/privacy.html")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(html).contains("수원문화재단 공개 API", "인스타그램", "공식 계정만", "개인 계정의 게시물은 수집하지 않습니다");
    assertThat(html).contains("뽑은 행사 제목, 분류, 일시", "처리 상태, 좌표와 함께 저장하고 지도에 표시합니다"); // 인스타 저장 항목을 빠짐없이
    // 인스타 사진은 서버에 저장 (docs/instagram-images.md) → 예전 "받지 않습니다" 문구가 남으면 안 됨
    assertThat(html).contains("대표 사진 1장은 받아 서버에 저장", "그 행사 정보를 삭제하면 다음 동기화 때 함께 삭제")
        .doesNotContain("사진은 받지 않습니다");
    assertThat(html).contains("삭제 요청:", "판매하거나 광고에 쓰지 않습니다"); // Meta 약관 3.d · 3.a.iv
    assertThat(html).doesNotContain("현재 수집 중단");
  }

  @Test
  void 최근_본_기록은_기기_브라우저에만_저장한다고_적음() throws Exception {
    // 찜 탭 "최근 본"(recent-views.js): 행사 번호만 localStorage, 서버로 보내지 않음
    String html = mockMvc.perform(get("/privacy.html")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(html).contains("최근 본", "브라우저 저장소(localStorage)에만 저장", "서버로 보내지 않습니다", "모두 지우기");
  }

  @Test
  void 비회원_찜은_기기에_두고_로그인하면_계정에_합친다고_적음() throws Exception {
    // 비회원 찜(guest-favorites.js): 행사 번호만 localStorage, 로그인 때만 서버로 (POST /api/favorites/merge)
    String html = mockMvc.perform(get("/privacy.html")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(html).contains("로그인하지 않고 찜한 행사 번호(최대 100개)", "회원의 찜 목록에 합치고",
        "로그인하지 않으면 서버로 보내지 않습니다");
  }

  @Test
  void 찜_알림은_본_표시만_기기에_두고_메일_푸시는_보내지_않는다고_적음() throws Exception {
    // 찜 알림(favorite-alerts.js): 본 알림 열쇠(행사 번호 · 날짜)만 localStorage
    String html = mockMvc.perform(get("/privacy.html")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(html).contains("찜 알림(내일 시작 · 곧 종료)", "메일이나 푸시 알림은 보내지 않습니다");
  }

  @Test
  void 메인_로그인_마이페이지에_링크가_있음() throws Exception {
    for (String page : new String[] { "/index.html", "/login.html", "/mypage.html" }) {
      String html = mockMvc.perform(get(page)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
      assertThat(html).as(page).contains("href=\"privacy.html\"");
    }
  }

  @Test
  void 접속_기록_보관_문구가_로그_설정과_맞음() throws Exception {
    // 파일 로그: application-prod.properties max-history=14 (ProdSyncProtectionTest)
    // 화면 출력 로그: json-file 은 기간 옵션이 없어 용량만 → 10MB × 3 = 30MB. 14일이라고 적으면 거짓
    String html = mockMvc.perform(get("/privacy.html")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
    String compose = Files.readString(Path.of("docker-compose.prod.yml"), StandardCharsets.UTF_8);

    assertThat(compose).contains("driver: json-file", "max-size: 10m", "max-file: \"3\"");
    // db · app · caddy 모두 같은 용량 제한을 씀 (서비스를 늘리면 logging 도 함께)
    assertThat(compose.split("logging: \\*logging", -1)).hasSize(4);
    assertThat(html).contains("날짜별 로그 파일은 14일이 지나면 자동으로 삭제",
        "기간 기준이 아니라 프로그램별 용량 한도(30MB)를 넘으면");
  }
}
