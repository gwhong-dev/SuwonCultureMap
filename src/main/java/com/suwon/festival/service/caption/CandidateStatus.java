package com.suwon.festival.service.caption;

// 캡션 정리 단계에서 정해지는 블록 상태 (설계 문서 4번 상태값 중 이 단계 몫)
// NO_COORD · NEEDS_REVIEW · READY 는 다음 단계(좌표 · 저장)에서 정해짐
public enum CandidateStatus {
  PARSED,   // 정리됨 → 다음 단계(좌표 찾기)로
  NO_DATE,  // 날짜를 못 읽음 → 지도 제외, 보류
  PENDING,  // 규칙으로 못 읽었고 AI 미연결 → AI 연결 뒤 다시 처리
  FAILED,   // 형식 · 검사 실패 → 지도 제외
  SKIPPED   // 행사가 아님 (공지 · 소개 등)
}
