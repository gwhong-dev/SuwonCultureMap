package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.suwon.festival.entity.SyncLog;
import com.suwon.festival.repository.SyncLogRepository;
import com.suwon.festival.service.SyncLogService;
import com.suwon.festival.service.instagram.InstagramFetchException.Kind;

// 인스타 동기화도 실행마다 이력 1줄 (출처 INSTAGRAM). 꺼져 있으면 안 남기고, 이력 저장 실패는 동기화에 영향 없음 (외부 호출 · DB 없음)
class InstagramSyncLogTest {

  private final SyncLogRepository syncLogRepository = mock(SyncLogRepository.class);
  private final InstagramSyncServiceTest.FakeClient client = new InstagramSyncServiceTest.FakeClient();

  private InstagramSyncService service(boolean enabled, String accounts) {
    return new InstagramSyncService(client, null, new InstagramTokenExpiry("", java.time.Clock.systemUTC()),
        new InstagramSwitch(enabled), accounts, 10, InstagramImageStore.disabled(), null, null,
        new SyncLogService(syncLogRepository));
  }

  private SyncLog saved() {
    ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
    verify(syncLogRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void 수집이_되면_SUCCESS() {
    client.answers.put("suwon_city", List.of()); // 새 게시물 없음

    InstagramSyncResult result = service(true, "suwon_city").sync();

    assertThat(result.accountsOk()).isEqualTo(1);
    SyncLog log = saved();
    assertThat(log.getSource()).isEqualTo(SyncLog.Source.INSTAGRAM);
    assertThat(log.getStatus()).isEqualTo(SyncLog.Status.SUCCESS);
    assertThat(log.getFetchedCount()).isZero();
    assertThat(log.getCreatedCount()).isZero();
    assertThat(log.getUpdatedCount()).isZero();
    assertThat(log.getErrorMessage()).isNull();
  }

  @Test
  void 모든_계정이_실패하면_FAIL_과_실패_계정_수만() {
    client.answers.put("suwon_city", new InstagramFetchException(Kind.TOKEN_INVALID, 190));

    service(true, "suwon_city").sync();

    SyncLog log = saved();
    assertThat(log.getStatus()).isEqualTo(SyncLog.Status.FAIL);
    assertThat(log.getErrorMessage()).isEqualTo("계정 수집 실패 1/1").doesNotContain("test-token");
  }

  @Test
  void 꺼져_있으면_이력을_안_남김() {
    service(false, "suwon_city").sync();

    verify(syncLogRepository, never()).save(any());
  }

  @Test
  void 이력_저장이_실패해도_동기화_결과는_그대로() {
    client.answers.put("suwon_city", List.of());
    when(syncLogRepository.save(any())).thenThrow(new IllegalStateException("DB 끊김"));

    InstagramSyncResult result = service(true, "suwon_city").sync();

    assertThat(result.configured()).isTrue();
    assertThat(result.accountsOk()).isEqualTo(1);
  }
}
