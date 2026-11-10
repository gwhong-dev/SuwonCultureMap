package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.schedule.EventScheduleWriter;

// 재단 동기화 동시 실행 잠금: 하나가 도는 중이면 두 번째는 재단 API 도 부르지 않고 바로 돌아옴 (외부 호출 · DB 없음)
class EventSyncLockTest {

  private static final String EMPTY_RSS = "<rss><channel></channel></rss>"; // 행사 0건

  private final RestTemplate restTemplate = mock(RestTemplate.class);
  private final EventSyncService service =
      new EventSyncService(restTemplate, mock(EventRepository.class), mock(EventScheduleWriter.class));

  @Test
  void 동시에_두_번_부르면_한_번만_실행하고_두_번째는_건너뜀() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(restTemplate.getForObject(anyString(), eq(String.class))).thenAnswer(inv -> {
      entered.countDown();
      release.await(5, TimeUnit.SECONDS); // 첫 동기화를 재단 응답 받는 단계에서 붙잡아 둠
      return EMPTY_RSS;
    });
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<SyncResult> first = pool.submit(service::syncEvents);
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

      SyncResult second = service.syncEvents(); // 첫 번째가 아직 도는 중

      assertThat(second.alreadyRunning()).isTrue();
      release.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS).alreadyRunning()).isFalse();
      verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class)); // 실제 실행은 한 번
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void 앞_실행이_끝나면_다음_실행은_정상() throws Exception {
    when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(EMPTY_RSS);

    service.syncEvents();
    SyncResult again = service.syncEvents();

    assertThat(again.alreadyRunning()).isFalse();
    verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
  }

  @Test
  void 재단_응답이_실패해도_잠금은_풀림() throws Exception {
    when(restTemplate.getForObject(anyString(), any(Class.class)))
        .thenThrow(new IllegalStateException("가상 실패"))
        .thenReturn(EMPTY_RSS);

    assertThatThrownBy(service::syncEvents).hasMessage("가상 실패");

    assertThat(service.syncEvents().alreadyRunning()).isFalse();
  }
}
