package com.suwon.festival.service.instagram;

// 수집 실패. 메시지에는 원인 종류와 Graph 오류 번호만 담음 (토큰 · 응답 원문은 담지 않음)
public class InstagramFetchException extends RuntimeException {

  public enum Kind {
    NOT_CONFIGURED,  // 토큰 · 내 계정 ID 설정 없음
    INVALID_INPUT,   // 계정 이름 형식이 틀림
    TOKEN_INVALID,   // code 190: 토큰 만료 · 거부
    NOT_FOUND,       // code 100 / 110: 계정 없음 · 프로페셔널 계정 아님 · ID 틀림
    RATE_LIMIT,      // code 4 / 17 / 32 / 613: 호출 제한
    PERMISSION,      // code 10 / 200~299: 권한 없음
    BAD_RESPONSE,    // 응답 모양이 예상과 다름
    OTHER
  }

  private final Kind kind;
  private final Integer code;

  public InstagramFetchException(Kind kind, Integer code) {
    super(kind + (code == null ? "" : " (Graph code " + code + ")"));
    this.kind = kind;
    this.code = code;
  }

  public Kind kind() {
    return kind;
  }

  public Integer code() {
    return code;
  }
}
