package com.suwon.festival.service.instagram;

import java.time.OffsetDateTime;

// 수집한 게시물 1개. caption 은 처리하는 동안만 쓰고 DB 에 저장하지 않음 (설계 문서 8번 결정)
// imageUrl: 대표 사진의 원래 주소 (사진 · 묶음 = media_url, 동영상 = thumbnail_url). 시간이 지나면 만료 → InstagramImageStore 가 받아 둠
public record InstagramPost(String id, String caption, String permalink, OffsetDateTime timestamp, String mediaType,
    String imageUrl) {

  public InstagramPost(String id, String caption, String permalink, OffsetDateTime timestamp, String mediaType) {
    this(id, caption, permalink, timestamp, mediaType, null);
  }

  // 로그에 찍혀도 캡션 원문이 남지 않게 길이만 표시
  @Override
  public String toString() {
    return "InstagramPost[id=" + id + ", captionLength=" + (caption == null ? 0 : caption.length())
        + ", permalink=" + permalink + ", timestamp=" + timestamp + ", mediaType=" + mediaType
        + ", imageUrl=" + (imageUrl == null ? "없음" : "있음") + "]"; // 사진 주소엔 서명 값이 들어 있어 남기지 않음
  }
}
