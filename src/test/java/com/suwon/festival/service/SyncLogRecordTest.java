package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.entity.SyncLog;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.SyncLogRepository;
import com.suwon.festival.service.schedule.EventScheduleWriter;

// 재단 동기화가 실행마다 이력 1줄을 남기는지 · 이력 저장이 실패해도 동기화는 그대로인지 (외부 호출 · DB 없음)
class SyncLogRecordTest {

  private static final String EMPTY_RSS = "<rss><channel></channel></rss>"; // 행사 0건
  // 2026-11-10T21:00Z = 한국 시간 11/11 06:00
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T21:00:00Z"), SyncLogService.KST);

  private final RestTemplate restTemplate = mock(RestTemplate.class);
  private final SyncLogRepository syncLogRepository = mock(SyncLogRepository.class);
  private final EventSyncService service = new EventSyncService(restTemplate, mock(EventRepository.class),
      mock(EventScheduleWriter.class), new SyncLogService(syncLogRepository, CLOCK));

  private SyncLog saved() {
    ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
    verify(syncLogRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void 성공하면_SUCCESS_와_개수를_한국_시간으로_남김() throws Exception {
    when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(EMPTY_RSS);

    SyncResult result = service.syncEvents();

    assertThat(result.alreadyRunning()).isFalse();
    SyncLog log = saved();
    assertThat(log.getSource()).isEqualTo(SyncLog.Source.SWCF);
    assertThat(log.getStatus()).isEqualTo(SyncLog.Status.SUCCESS);
    assertThat(log.getStartedAt()).isEqualTo(LocalDateTime.of(2026, 11, 11, 6, 0));
    assertThat(log.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 11, 11, 6, 0));
    assertThat(log.getFetchedCount()).isZero();
    assertThat(log.getCreatedCount()).isZero();
    assertThat(log.getUpdatedCount()).isZero();
    assertThat(log.getErrorMessage()).isNull();
  }

  @Test
  void 실패하면_FAIL_과_예외_종류만_남기고_예외는_그대로_던짐() {
    // 실제 RestTemplate 오류 메시지에는 호출 주소(API 키 포함)가 들어감 → 이력엔 종류만
    when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new ResourceAccessException(
        "I/O error on GET request for \"http://www.swcf.or.kr/openAPI/?CG=29_EV&openAPIKey=test-swcf-key\"",
        new java.net.SocketTimeoutException("Read timed out")));

    assertThatThrownBy(service::syncEvents).isInstanceOf(ResourceAccessException.class);

    SyncLog log = saved();
    assertThat(log.getStatus()).isEqualTo(SyncLog.Status.FAIL);
    assertThat(log.getFetchedCount()).isNull(); // 받기 전에 실패 → 못 셈
    assertThat(log.getCreatedCount()).isNull();
    assertThat(log.getErrorMessage()).isEqualTo("ResourceAccessException ← SocketTimeoutException")
        .doesNotContain("openAPIKey", "test-swcf-key", "http");
  }

  @Test
  void 응답_형식이_틀려도_FAIL_로_남김() {
    when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("<html>점검 중</html>");

    assertThatThrownBy(service::syncEvents).isInstanceOf(Exception.class);

    assertThat(saved().getStatus()).isEqualTo(SyncLog.Status.FAIL);
  }

  @Test
  void 이력_저장이_실패해도_동기화_결과는_그대로() throws Exception {
    when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(EMPTY_RSS);
    when(syncLogRepository.save(any())).thenThrow(new IllegalStateException("DB 끊김"));

    SyncResult result = service.syncEvents();

    assertThat(result).isEqualTo(new SyncResult(0, 0, 0));
  }

  @Test
  void 이미_도는_중이라_건너뛴_실행은_이력을_안_남김() throws Exception {
    // 두 번째 실행을 첫 실행 안(재단 응답 받는 중)에서 부름 → 잠금에 걸려 건너뜀
    SyncResult[] inner = new SyncResult[1];
    when(restTemplate.getForObject(anyString(), eq(String.class))).thenAnswer(inv -> {
      inner[0] = service.syncEvents();
      return EMPTY_RSS;
    });

    service.syncEvents();

    assertThat(inner[0].alreadyRunning()).isTrue();
    verify(syncLogRepository).save(any()); // 바깥 실행 1줄만
  }

  @Test
  void 오류_글은_종류만_500자_이내() {
    assertThat(SyncLogService.errorText(new IllegalStateException("비밀 https://x?key=1"))).isEqualTo("IllegalStateException");
    assertThat(SyncLogService.errorText(null)).isNull();
    assertThat(SyncLogService.truncate("가".repeat(600))).hasSize(SyncLog.ERROR_MAX);
    assertThat(SyncLogService.truncate("짧음")).isEqualTo("짧음");
    verify(syncLogRepository, never()).save(any());
  }

  @Test
  void 실패_원인이_여러_겹이어도_종류만_이어_붙임() {
    Exception e = new IllegalStateException("a", new RuntimeException("b", new java.io.IOException("c")));
    assertThat(SyncLogService.errorText(e)).isEqualTo("IllegalStateException ← RuntimeException ← IOException");
    assertThat(List.of(SyncLogService.errorText(e).split(" ← "))).hasSize(3);
  }
}
