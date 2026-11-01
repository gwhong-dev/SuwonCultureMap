package com.suwon.festival.service.merge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 동기화 끝에 합치기 실행. 동기화는 이미 커밋된 뒤라, 합치기 실패로 동기화 응답까지 실패시키지 않음
// recompute 의 트랜잭션 밖에서 잡아야 커밋 실패까지 잡힘 → 별도 클래스
@Component
@RequiredArgsConstructor
public class EventMergeRunner {

  private static final Logger log = LoggerFactory.getLogger(EventMergeRunner.class);

  private final EventMergeService eventMergeService;

  // synchronized: 합치기 2개가 겹치면 옛 계산이 나중에 저장되어 사슬(A→B→C)이 생길 수 있음. 트랜잭션 밖이라 커밋까지 잠금 안 (서버 1대 기준)
  public synchronized MergeSummary runAfterSync() {
    try {
      return MergeSummary.of(eventMergeService.recompute());
    } catch (RuntimeException e) {
      // 메시지는 남기지 않음 (DB 오류 메시지에 값이 섞일 수 있음) → 예외와 원인의 종류만
      Throwable cause = e.getCause();
      log.warn("같은 행사 합치기 실패 {}{}", e.getClass().getSimpleName(),
          cause == null ? "" : " ← " + cause.getClass().getSimpleName());
      return MergeSummary.failed();
    }
  }
}
