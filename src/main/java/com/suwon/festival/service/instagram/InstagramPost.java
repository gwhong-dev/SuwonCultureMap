package com.suwon.festival.service.instagram;

import java.time.OffsetDateTime;

// 수집한 게시물 1개. caption 은 처리하는 동안만 쓰고 DB 에 저장하지 않음 (설계 문서 8번 결정)
public record InstagramPost(String id, String caption, String permalink, OffsetDateTime timestamp, String mediaType) {
}
