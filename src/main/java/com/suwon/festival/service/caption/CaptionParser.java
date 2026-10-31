package com.suwon.festival.service.caption;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 캡션 정리기 (6단계 중 1단계): 캡션 → 행사 블록 → 규칙 파서 → (못 읽으면) AI → 다시 검사
// DB · 네트워크를 쓰지 않는 순수 로직. 저장 · 좌표는 다음 단계에서 붙임
public class CaptionParser {

  public enum Source { RULE, AI, NONE }

  // 블록 1개의 최종 결과
  public record BlockResult(CaptionBlock block, Source source, CandidateStatus status,
      ParsedEvent event, String reason, List<String> warnings) {

    public BlockResult {
      warnings = List.copyOf(warnings);
    }
  }

  private final CaptionAiClient ai; // null 이면 AI 미연결

  public CaptionParser(CaptionAiClient ai) {
    this.ai = ai;
  }

  public List<BlockResult> parse(String caption) {
    List<BlockResult> results = new ArrayList<>();
    for (CaptionBlock block : CaptionBlockSplitter.split(caption)) {
      results.add(parseBlock(block));
    }
    return results;
  }

  BlockResult parseBlock(CaptionBlock block) {
    RuleCaptionParser.Outcome rule = RuleCaptionParser.parse(block);

    if (rule instanceof RuleCaptionParser.Confident c) {
      BlockResult r = checked(block, Source.RULE, c.event());
      if (r.status() == CandidateStatus.NO_DATE && c.note() != null) {
        return new BlockResult(block, r.source(), r.status(), r.event(), c.note(), r.warnings());
      }
      return r;
    }
    if (rule instanceof RuleCaptionParser.Invalid inv) {
      // 날짜 줄 자체가 틀린 경우는 AI 에 넘기지 않음 (AI 가 그럴듯하게 고쳐 쓰는 것 방지)
      return new BlockResult(block, Source.RULE, CandidateStatus.FAILED, null, inv.reason(), List.of());
    }

    String why = ((RuleCaptionParser.Unsure) rule).reason();
    if (ai == null) {
      return new BlockResult(block, Source.NONE, CandidateStatus.PENDING, null, "AI 미연결 (" + why + ")", List.of());
    }
    Optional<ParsedEvent> fromAi;
    try {
      fromAi = ai.parse(block.text());
    } catch (RuntimeException e) {
      // 호출 실패는 다음 실행에서 다시 시도 (예외 내용은 로그 담당 쪽에서, 여기서는 종류만)
      return new BlockResult(block, Source.AI, CandidateStatus.PENDING, null,
          "AI 호출 실패: " + e.getClass().getSimpleName(), List.of());
    }
    if (fromAi.isEmpty()) {
      return new BlockResult(block, Source.AI, CandidateStatus.FAILED, null, "AI도 못 읽음", List.of());
    }
    return checked(block, Source.AI, fromAi.get());
  }

  private static BlockResult checked(CaptionBlock block, Source source, ParsedEvent event) {
    ParsedEventValidator.Result v = ParsedEventValidator.validate(event, block.text());
    return new BlockResult(block, source, v.status(), v.event(), v.reason(), v.warnings());
  }
}
