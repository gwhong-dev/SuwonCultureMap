package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

// 인스타 동기화 동시 실행 잠금: 하나가 도는 중이면 두 번째는 아무것도 하지 않고 바로 돌아옴 (외부 호출 · DB 없음)
class InstagramSyncLockTest {

  // 첫 동기화를 스위치 확인 단계에서 붙잡아 두는 가짜 스위치 (꺼짐으로 답해 수집은 하지 않음)
  static class BlockingSwitch extends InstagramSwitch {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicInteger calls = new AtomicInteger();

    BlockingSwitch() {
      super(false);
    }

    @Override
    public boolean isEnabled() {
      calls.incrementAndGet();
      entered.countDown();
      try {
        release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return false;
    }
  }

  private static InstagramSyncService service(InstagramSwitch sw) {
    InstagramClient client = new InstagramClient(new RestTemplate(), "", "", "v25.0");
    return new InstagramSyncService(client, null, new InstagramTokenExpiry("", java.time.Clock.systemUTC()), sw, "", 10);
  }

  @Test
  void 동시에_두_번_부르면_한_번만_실행하고_두_번째는_건너뜀() throws Exception {
    BlockingSwitch sw = new BlockingSwitch();
    InstagramSyncService service = service(sw);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<InstagramSyncResult> first = pool.submit(service::sync);
      assertThat(sw.entered.await(5, TimeUnit.SECONDS)).isTrue();

      InstagramSyncResult second = service.sync(); // 첫 번째가 아직 도는 중

      assertThat(second.skippedAlreadyRunning()).isTrue();
      assertThat(second.message()).contains("이미 실행 중");
      sw.release.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS).skippedAlreadyRunning()).isFalse();
      assertThat(sw.calls.get()).isEqualTo(1); // 실제 실행은 한 번
    } finally {
      sw.release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void 앞_실행이_끝나면_다음_실행은_정상() {
    BlockingSwitch sw = new BlockingSwitch();
    sw.release.countDown(); // 붙잡지 않음
    InstagramSyncService service = service(sw);

    service.sync();
    InstagramSyncResult again = service.sync();

    assertThat(again.skippedAlreadyRunning()).isFalse();
    assertThat(sw.calls.get()).isEqualTo(2);
  }

  @Test
  void 수집_중_예외가_나도_잠금은_풀림() {
    InstagramSwitch failing = new InstagramSwitch(true) {
      int n;

      @Override
      public boolean isEnabled() {
        if (n++ == 0) throw new IllegalStateException("가상 실패");
        return false;
      }
    };
    InstagramSyncService service = service(failing);

    try {
      service.sync();
    } catch (IllegalStateException expected) {
      // 첫 실행은 실패
    }

    assertThat(service.sync().skippedAlreadyRunning()).isFalse();
  }
}
