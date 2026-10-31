package com.suwon.festival.service.instagram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.suwon.festival.service.caption.CaptionParser.BlockResult;
import com.suwon.festival.service.caption.ParsedEvent;

// 블록 키 (설계 문서 2번 ④): 글 안 순서가 아니라 값으로 만든다 → 캡션이 고쳐져 순서가 바뀌어도 같은 행사는 같은 키
// - 정리된 블록: 제목 + 시작일 + 장소명
// - 못 읽은 블록(제목 · 날짜가 없음): 블록 원문 (원문 자체는 저장하지 않고 해시만)
// - 길이를 고정하려고 SHA-256 앞 32글자(16진수)만 씀 → sourceKey = 게시물 id(최대 64) + "#" + 32 = 97 ≤ 100
public final class BlockKeys {

  static final int HASH_LENGTH = 32;

  private BlockKeys() {
  }

  public static String of(BlockResult r) {
    ParsedEvent e = r.event();
    if (e != null && e.title() != null && e.startDate() != null) {
      return hash("e|" + norm(e.title()) + "|" + e.startDate() + "|" + norm(e.placeName()));
    }
    return hash("t|" + norm(r.block().text()));
  }

  public static String sourceKey(String mediaId, String blockKey) {
    return mediaId + "#" + blockKey;
  }

  // 공백 차이 · 대소문자는 같은 것으로
  static String norm(String s) {
    return s == null ? "" : s.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static String hash(String s) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d).substring(0, HASH_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e); // 모든 JDK 에 있는 알고리즘
    }
  }
}
