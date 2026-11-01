package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.service.EventSyncService;
import com.suwon.festival.service.ScheduledSyncJob;
import com.suwon.festival.service.instagram.InstagramSyncService;

// 운영(prod) 프로필 실제 설정: 수동 동기화 · 미리보기 API 는 없고, 자동 동기화는 켜져 있음 (DB 와 키만 가짜)
@SpringBootTest(properties = {
    "APP_BASE_URL=http://localhost:8080",
    "DB_URL=jdbc:h2:mem:prodprofile;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "DB_USERNAME=sa",
    "DB_PASSWORD=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=none",
    "SWCF_API_KEY_EVENT=test-swcf-key",
    "KAKAO_REST_API_KEY=test-kakao-id",
    "KAKAO_CLIENT_SECRET=test-kakao-secret",
    "GOOGLE_CLIENT_ID=test-google-id",
    "GOOGLE_CLIENT_SECRET=test-google-secret",
    "NAVER_CLIENT_ID=test-naver-id",
    "NAVER_CLIENT_SECRET=test-naver-secret"
})
@ActiveProfiles("prod")
class ProdSyncProtectionTest {

  @Autowired private WebApplicationContext context;
  @MockitoBean private EventSyncService eventSyncService;
  @MockitoBean private InstagramSyncService instagramSyncService;

  @ParameterizedTest
  @CsvSource({
      "POST, /api/events/sync",
      "GET, /api/events/sync/raw",
      "GET, /api/events/sync/parsed",
      "GET, /api/events/merge/preview",
      "GET, /api/events/schedule/preview",
      "POST, /api/instagram/sync"
  })
  void 수동_동기화_API는_운영에_없음(String method, String url) throws Exception {
    int status = MockMvcBuilders.webAppContextSetup(context).build()
        .perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(method), url))
        .andReturn().getResponse().getStatus();

    assertThat(status).isIn(404, 405);
  }

  @Test
  void 행사_조회는_운영에서도_열려_있음() throws Exception {
    int status = MockMvcBuilders.webAppContextSetup(context).build()
        .perform(MockMvcRequestBuilders.get("/api/events"))
        .andReturn().getResponse().getStatus();

    assertThat(status).isEqualTo(200);
  }

  @Test
  void 운영_세션_쿠키는_HTTPS_전용_설정() {
    Environment env = context.getEnvironment();

    assertThat(env.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
    assertThat(env.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
    assertThat(env.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
  }

  @Test
  void 운영에서는_자동_동기화가_켜져_있음() {
    assertThat(context.getBeansOfType(ScheduledSyncJob.class)).hasSize(1);
    assertThat(context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
        .flatMap(holder -> holder.getScheduledTasks().stream())
        .map(scheduled -> scheduled.getTask())
        .filter(CronTask.class::isInstance)
        .map(task -> ((CronTask) task).getExpression()))
        .contains("0 0 6,18 * * *");
  }
}
