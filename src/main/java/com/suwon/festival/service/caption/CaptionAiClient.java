package com.suwon.festival.service.caption;

import java.util.Optional;

// 규칙으로 못 읽은 블록을 AI 에게 맡기는 창구 (설계 문서 5번)
// - 입력은 블록 원문만 (캡션 전체 X)
// - 실제 구현(Gemini / Ollama)은 공급자 결정 뒤 5단계에서 붙임. 테스트는 가짜 구현 사용
// - JSON 형식 · 허용된 칸 검사는 실제 구현이 응답을 ParsedEvent 로 바꿀 때 담당
// - 결과는 CaptionParser 가 ParsedEventValidator 로 다시 검사함 (그대로 믿지 않음)
public interface CaptionAiClient {

  // 못 읽으면 Optional.empty()
  Optional<ParsedEvent> parse(String blockText);
}
