package com.suwon.festival.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.SyncLog;
import com.suwon.festival.repository.SyncLogRepository;

// GET /api/sync-status: 재단 마지막 SUCCESS 만 (실패 · 인스타는 무시), 없으면 null. 로그인 없이 열림
@SpringBootTest
@ActiveProfiles("test")
@Transactional // 다른 시험이 남긴 이력은 지우고 시작, 끝나면 되돌림
class SyncStatusApiTest {

  @Autowired private WebApplicationContext context;
  @Autowired private SyncLogRepository syncLogRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    syncLogRepository.deleteAll();
    syncLogRepository.flush();
  }

  private void save(SyncLog.Source source, SyncLog.Status status, LocalDateTime finishedAt, Integer created,
      Integer updated, String error) {
    syncLogRepository.saveAndFlush(new SyncLog(source, finishedAt.minusMinutes(1), finishedAt, status, 150, created,
        updated, error));
  }

  @Test
  void 이력이_없으면_lastSuccessAt_null() throws Exception {
    mockMvc.perform(get("/api/sync-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastSuccessAt").doesNotExist())
        .andExpect(jsonPath("$.createdCount").doesNotExist())
        .andExpect(jsonPath("$.updatedCount").doesNotExist());
  }

  @Test
  void 실패만_있으면_lastSuccessAt_null() throws Exception {
    save(SyncLog.Source.SWCF, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 11, 6, 0), null, null, "ResourceAccessException");

    mockMvc.perform(get("/api/sync-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastSuccessAt").doesNotExist());
  }

  @Test
  void 재단_마지막_성공_시각과_개수를_돌려줌_실패_정보는_안_보냄() throws Exception {
    save(SyncLog.Source.SWCF, SyncLog.Status.SUCCESS, LocalDateTime.of(2026, 11, 10, 18, 0, 3), 1, 2, null);
    save(SyncLog.Source.SWCF, SyncLog.Status.SUCCESS, LocalDateTime.of(2026, 11, 11, 6, 0, 12, 345_000_000), 3, 7, null);
    // 그 뒤의 재단 실패 · 인스타 성공은 무시
    save(SyncLog.Source.SWCF, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 11, 18, 0), null, null, "IllegalStateException");
    save(SyncLog.Source.INSTAGRAM, SyncLog.Status.SUCCESS, LocalDateTime.of(2026, 11, 11, 18, 1), 9, 9, null);

    mockMvc.perform(get("/api/sync-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastSuccessAt").value("2026-11-11T06:00:12"))
        .andExpect(jsonPath("$.createdCount").value(3))
        .andExpect(jsonPath("$.updatedCount").value(7))
        .andExpect(jsonPath("$.status").doesNotExist())
        .andExpect(jsonPath("$.errorMessage").doesNotExist());
  }

  // ===== consecutiveFailures: 재단 마지막 성공 뒤 연속 실패 횟수 (동기화 실패 알림 워크플로가 읽음) =====

  @Test
  void 이력이_없으면_연속_실패_0() throws Exception {
    mockMvc.perform(get("/api/sync-status"))
        .andExpect(jsonPath("$.consecutiveFailures").value(0));
  }

  @Test
  void 마지막_성공_뒤_재단_실패만_센다_인스타_실패는_무시() throws Exception {
    save(SyncLog.Source.SWCF, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 10, 6, 0), null, null, "A"); // 성공 전 → 안 셈
    save(SyncLog.Source.SWCF, SyncLog.Status.SUCCESS, LocalDateTime.of(2026, 11, 10, 18, 0), 1, 1, null);
    save(SyncLog.Source.SWCF, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 11, 6, 0), null, null, "A");
    save(SyncLog.Source.INSTAGRAM, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 11, 6, 1), null, null, "B");
    save(SyncLog.Source.SWCF, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 11, 18, 0), null, null, "A");

    mockMvc.perform(get("/api/sync-status"))
        .andExpect(jsonPath("$.lastSuccessAt").value("2026-11-10T18:00:00"))
        .andExpect(jsonPath("$.consecutiveFailures").value(2))
        .andExpect(jsonPath("$.errorMessage").doesNotExist());
  }

  @Test
  void 성공이_오면_연속_실패가_끊김() throws Exception {
    save(SyncLog.Source.SWCF, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 10, 6, 0), null, null, "A");
    save(SyncLog.Source.SWCF, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 10, 18, 0), null, null, "A");
    save(SyncLog.Source.SWCF, SyncLog.Status.SUCCESS, LocalDateTime.of(2026, 11, 11, 6, 0), 0, 3, null);

    mockMvc.perform(get("/api/sync-status"))
        .andExpect(jsonPath("$.consecutiveFailures").value(0));
  }

  @Test
  void 성공이_한_번도_없으면_재단_실패_전부를_센다() throws Exception {
    save(SyncLog.Source.SWCF, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 11, 6, 0), null, null, "A");
    save(SyncLog.Source.SWCF, SyncLog.Status.FAIL, LocalDateTime.of(2026, 11, 11, 18, 0), null, null, "A");
    save(SyncLog.Source.INSTAGRAM, SyncLog.Status.SUCCESS, LocalDateTime.of(2026, 11, 11, 18, 1), 1, 1, null);

    mockMvc.perform(get("/api/sync-status"))
        .andExpect(jsonPath("$.lastSuccessAt").doesNotExist())
        .andExpect(jsonPath("$.consecutiveFailures").value(2));
  }
}
