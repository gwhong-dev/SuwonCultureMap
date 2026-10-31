package com.suwon.festival.service.instagram;

import java.time.OffsetDateTime;

// 수집한 게시물 1개. caption 은 처리하는 동안만 쓰고 DB 에 저장하지 않음 (설계 문서 8번 결정)
public record InstagramPost(String id, String caption, String permalink, OffsetDateTime timestamp, String mediaType) {

  // 로그에 찍혀도 캡션 원문이 남지 않게 길이만 표시
  @Override
  public String toString() {
    return "InstagramPost[id=" + id + ", captionLength=" + (caption == null ? 0 : caption.length())
        + ", permalink=" + permalink + ", timestamp=" + timestamp + ", mediaType=" + mediaType + "]";
  }
}
