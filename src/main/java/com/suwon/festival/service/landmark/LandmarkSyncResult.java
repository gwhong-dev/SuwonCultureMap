package com.suwon.festival.service.landmark;

import java.util.List;

// 명소 동기화 1회 결과
//  - configured: 인증키가 하나라도 있는지 (없으면 아무것도 안 함)
//  - fetched: 받은 곳 수 / created · updated: 새로 저장 · 내용이 바뀐 곳 / skipped: 형식 오류로 건너뛴 곳
//  - geocoded: 이번에 카카오 장소 검색으로 좌표를 보충한 곳 / noCoord: 저장 뒤에도 좌표가 없는 곳
//  - failedFeeds: 받기 · 읽기에 실패한 코드 ("59_C(ResourceAccessException)" 처럼 종류만)
//  - skippedFeeds: 인증키가 없어 받지 않은 코드 (실패로 세지 않음)
public record LandmarkSyncResult(boolean configured, boolean alreadyRunning, int fetched, int created, int updated,
    int skipped, int geocoded, int noCoord, List<String> failedFeeds, List<String> skippedFeeds) {

  public LandmarkSyncResult {
    failedFeeds = failedFeeds == null ? List.of() : List.copyOf(failedFeeds);
    skippedFeeds = skippedFeeds == null ? List.of() : List.copyOf(skippedFeeds);
  }

  public static LandmarkSyncResult notConfigured() {
    return new LandmarkSyncResult(false, false, 0, 0, 0, 0, 0, 0, List.of(), List.of());
  }

  public static LandmarkSyncResult skippedAlreadyRunning() {
    return new LandmarkSyncResult(true, true, 0, 0, 0, 0, 0, 0, List.of(), List.of());
  }

  public boolean ok() {
    return failedFeeds.isEmpty();
  }

  public String text() {
    return "받음 " + fetched + " · 새로 " + created + " · 갱신 " + updated + " · 좌표 보충 " + geocoded
        + " · 좌표 없음 " + noCoord + (skipped > 0 ? " · 건너뜀 " + skipped : "")
        + (failedFeeds.isEmpty() ? "" : " · 실패 " + String.join(", ", failedFeeds))
        + (skippedFeeds.isEmpty() ? "" : " · 키 없음 " + String.join(", ", skippedFeeds));
  }
}
