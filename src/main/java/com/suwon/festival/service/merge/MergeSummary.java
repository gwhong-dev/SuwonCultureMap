package com.suwon.festival.service.merge;

// 동기화 끝에 실행한 합치기 결과 요약 (개수만, 묶음 목록은 GET /api/events/merge/preview)
// ok=false: 합치기만 실패. 동기화 결과는 이미 저장됐고, 다음 동기화 때 처음부터 다시 계산됨
public record MergeSummary(boolean ok, int groupCount, int hiddenCount, int conflictCount) {

  static MergeSummary of(EventMergeResult r) {
    return new MergeSummary(true, r.groupCount(), r.hiddenCount(), r.conflictCount());
  }

  static MergeSummary failed() {
    return new MergeSummary(false, 0, 0, 0);
  }

  // 재단 동기화 응답 문구에 덧붙이는 설명
  public String text() {
    return ok
        ? "같은 행사 묶음 " + groupCount + "개 · 숨김 " + hiddenCount + "개"
        : "같은 행사 합치기 실패 (동기화 결과는 저장됨, 다음 동기화 때 다시 계산)";
  }
}
