package com.suwon.festival.dto.kakao;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 카카오 로컬 "키워드로 장소 검색" 응답 (필요한 칸만)
// x = 경도(longitude), y = 위도(latitude) — 문자열로 옴
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoLocalSearchResponse {
  public List<Document> documents;
  public Meta meta;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Meta {
    public Boolean is_end; // 마지막 페이지인지 (false 면 결과가 더 있음)
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Document {
    public String place_name;
    public String address_name;
    public String road_address_name;
    public String x;
    public String y;
  }
}
