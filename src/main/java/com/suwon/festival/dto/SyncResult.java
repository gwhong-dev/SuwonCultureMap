package com.suwon.festival.dto;

// 행사 동기화 결과: 새로 저장한 개수 + 기존 행사 중 갱신한 개수 + 형식 오류로 건너뛴 개수
// alreadyRunning: 다른 재단 동기화가 도는 중이라 이번 실행은 아무것도 하지 않음
public record SyncResult(int saved, int updated, int skipped, boolean alreadyRunning) {

  public SyncResult(int saved, int updated, int skipped) {
    this(saved, updated, skipped, false);
  }

  public SyncResult(int saved, int updated) {
    this(saved, updated, 0, false);
  }

  public static SyncResult skippedAlreadyRunning() {
    return new SyncResult(0, 0, 0, true);
  }
}
