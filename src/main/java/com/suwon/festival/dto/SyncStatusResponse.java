package com.suwon.festival.dto;

// GET /api/sync-status: 재단 동기화 마지막 성공 (한국 시간 "yyyy-MM-ddTHH:mm:ss", 없으면 null)
// consecutiveFailures: 재단 마지막 성공 뒤 연속 실패 횟수 (성공이 없으면 실패 전체, 이력 없음 = 0)
//  → .github/workflows/sync-alert.yml 이 읽어 "연속 실패" Issue 를 엶. 화면은 안 씀
// 오류 내용(예외 종류 · 메시지)은 싣지 않음 — 횟수만. 조회 실패면 모두 null
public record SyncStatusResponse(String lastSuccessAt, Integer createdCount, Integer updatedCount,
    Integer consecutiveFailures) {

  public static SyncStatusResponse none() {
    return new SyncStatusResponse(null, null, null, null);
  }
}
