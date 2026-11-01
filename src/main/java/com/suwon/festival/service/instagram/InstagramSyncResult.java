package com.suwon.festival.service.instagram;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.suwon.festival.service.merge.MergeSummary;

// 인스타 동기화 1회 결과 (개수 · 오류 종류만. 캡션 원문 · 토큰은 담지 않음)
//  merge: 동기화 끝에 실행한 같은 행사 합치기 결과 (컨트롤러가 채움)
//  tokenWarning: 장기 토큰 만료 임박 · 만료 알림 (없으면 null)
//  blocks: 블록 상태별 개수 (READY = 지도에 올라감, NO_COORD = 좌표 못 찾음, PENDING = AI 연결 뒤 재처리 …)
public record InstagramSyncResult(
    boolean configured,
    String message,
    int accounts,
    int accountsOk,
    List<String> accountErrors,
    int posts,
    int newPosts,
    int postErrors,
    Map<String, Integer> blocks,
    int eventsSaved,
    int eventsUpdated,
    int duplicateBlocks,
    String tokenWarning,
    MergeSummary merge) {

  public InstagramSyncResult {
    accountErrors = List.copyOf(accountErrors);
    blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks)); // 상태 순서 유지
  }

  static InstagramSyncResult notConfigured(String message, String tokenWarning) {
    return new InstagramSyncResult(false, message, 0, 0, List.of(), 0, 0, 0, Map.of(), 0, 0, 0, tokenWarning, null);
  }

  public InstagramSyncResult withMerge(MergeSummary summary) {
    return new InstagramSyncResult(configured, message, accounts, accountsOk, accountErrors, posts, newPosts,
        postErrors, blocks, eventsSaved, eventsUpdated, duplicateBlocks, tokenWarning, summary);
  }
}
